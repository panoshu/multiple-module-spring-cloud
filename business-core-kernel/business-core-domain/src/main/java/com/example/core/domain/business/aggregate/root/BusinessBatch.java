package com.example.core.domain.business.aggregate.root;

import com.example.core.domain.business.errorcode.CoreDomainErrorCode;
import com.example.core.domain.business.event.BatchStatusChangedEvent;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.reference.BusinessFormRef;
import com.example.core.domain.business.aggregate.valueobject.enums.status.BatchStatus;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.exception.DomainException;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.UserNo;
import com.example.shared.domain.aggregate.valueobject.Version;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 业务批次聚合根
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/9 12:30
 */

public class BusinessBatch extends AggregateRoot<BatchId> {

  List<BusinessFormRef> businessFormRefs;
  private BusinessContext businessContext;
  private OperatorInfo operatorInfo;
  private BatchStatus status;
  private int totalApplicationCount = 0;
  private int successCount = 0;
  private int failedCount = 0;

  protected BusinessBatch(BatchId batchId, UserNo userNo) {
    super(batchId, userNo);
    this.validateInvariants();
  }

  protected BusinessBatch(BatchId batchId, UserNo createdBy, UserNo updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    super(batchId, createdBy, updatedBy, createdAt, updatedAt, version);
    this.validateInvariants();
  }

  /**
   * 行为：标记为处理中
   */
  public void markAsProcessing() {
    if (this.status != BatchStatus.CREATED) {
      throw new DomainException(CoreDomainErrorCode.INVALID_STATUS)
        .withLogDetail("只有刚创建的批次才能开始处理, BatchId: %s, status: %s".formatted(this.id().toString(), this.status.name()));
    }
    this.status = BatchStatus.PROCESSING;
  }

  /**
   * 行为：重新计算并更新批次状态 (由申请单完成/失败事件触发)
   */
  public void recalculateStatus() {
    if (successCount + failedCount < totalApplicationCount) {
      return;
    }

    BatchStatus targetStatus = BatchStatus.determine(failedCount, totalApplicationCount);

    if (this.status != targetStatus) {
      BatchStatus oldStatus = this.status;
      this.status = targetStatus;

      this.registerDomainEvent(BatchStatusChangedEvent.of(this.id(), oldStatus, targetStatus));
    }
  }

  // 统计行为由事件监听器调用
  public void incrementTotalApplicationCount(int count) {
    this.totalApplicationCount += count;
    recalculateStatus();
  }

  public void incrementSuccessCount(int count) {
    this.successCount += count;
    recalculateStatus();
  }

  public void incrementFailedCount(int count) {
    this.failedCount += count;
    recalculateStatus();
  }

  @Override
  protected void validateInvariants() {

    if (totalApplicationCount < 0) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("总申请数不能为负, BatchId: %s, total count: %d".formatted(this.id().toString(), this.totalApplicationCount));
    }
    if (successCount < 0 || failedCount < 0) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("成功/失败计数不能为负");
    }

    int processed = successCount + failedCount;
    if (processed > totalApplicationCount) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("业务不变量违反: 已处理数(%d) > 总申请数(%d)".formatted(processed, totalApplicationCount));
    }


    validateStatusConsistency();
  }


  private void validateStatusConsistency() {
    if (isTerminalStatus(this.status)) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("Batch %s is terminal but counts updated".formatted(this.id().toString()));
    }
  }

  private boolean isTerminalStatus(BatchStatus status) {
    return status != null && status.isTerminal();
  }

}
