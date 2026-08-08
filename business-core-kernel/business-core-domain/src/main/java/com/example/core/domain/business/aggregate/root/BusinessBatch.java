package com.example.core.domain.business.aggregate.root;

import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.enums.status.BatchStatus;
import com.example.core.domain.business.aggregate.valueobject.reference.BusinessFormRef;
import com.example.core.domain.business.errorcode.CoreDomainErrorCode;
import com.example.core.domain.business.event.BatchStatusChangedEvent;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import com.example.shared.identifier.id.BatchId;
import com.example.shared.identifier.id.UserNo;

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
   * 工厂方法:创建新业务批次
   *
   * @param batchId  批次ID
   * @param context  业务上下文
   * @param operator 操作人信息
   * @return 新创建的批次聚合根
   */
  public static BusinessBatch create(BatchId batchId, BusinessContext context, OperatorInfo operator) {
    BusinessBatch batch = new BusinessBatch(batchId, operator.operatorId());
    batch.businessContext = context;
    batch.operatorInfo = operator;
    batch.status = BatchStatus.CREATED;
    return batch;
  }

  /**
   * 从数据库重建聚合根（全参工厂方法）。
   * <p>
   * 供 Repository 实现的 Converter 在 DO → 领域对象转换时调用，绕过业务校验直接装配所有字段。
   * 业务代码禁止使用本方法创建新对象，新建请用 {@link #create}。
   *
   * @return 装配完成的聚合根实例
   */
  public static BusinessBatch reconstitute(
    BatchId batchId,
    UserNo createdBy,
    UserNo updatedBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Version version,
    BusinessContext businessContext,
    OperatorInfo operatorInfo,
    BatchStatus status,
    int totalApplicationCount,
    int successCount,
    int failedCount,
    List<BusinessFormRef> businessFormRefs) {
    BusinessBatch batch = new BusinessBatch(batchId, createdBy, updatedBy, createdAt, updatedAt, version);
    batch.businessContext = businessContext;
    batch.operatorInfo = operatorInfo;
    batch.status = status;
    batch.totalApplicationCount = totalApplicationCount;
    batch.successCount = successCount;
    batch.failedCount = failedCount;
    batch.businessFormRefs = businessFormRefs;
    return batch;
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

  /**
   * 行为:取消批次
   *
   * <p>只有 CREATED 或 PROCESSING 状态的批次才能取消。
   *
   * @param reason 取消原因
   * @throws DomainException 当批次状态不允许取消时
   */
  public void cancel(String reason) {
    if (this.status == null || !this.status.isActive()) {
      throw new DomainException(CoreDomainErrorCode.INVALID_STATUS)
        .withLogDetail("只有未完成/处理中的批次才能取消, BatchId: %s, status: %s".formatted(this.id().value(), this.status));
    }
    BatchStatus oldStatus = this.status;
    this.status = BatchStatus.CANCELLED;
    this.registerDomainEvent(BatchStatusChangedEvent.of(this.id(), oldStatus, BatchStatus.CANCELLED));
  }

  // ============ Accessors ============

  public BusinessContext businessContext() {
    return this.businessContext;
  }

  public OperatorInfo operatorInfo() {
    return this.operatorInfo;
  }

  public BatchStatus status() {
    return this.status;
  }

  public int totalApplicationCount() {
    return this.totalApplicationCount;
  }

  public int successCount() {
    return this.successCount;
  }

  public int failedCount() {
    return this.failedCount;
  }

  public List<BusinessFormRef> businessFormRefs() {
    return this.businessFormRefs;
  }

}
