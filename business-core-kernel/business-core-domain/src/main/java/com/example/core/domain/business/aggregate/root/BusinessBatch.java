package com.example.core.domain.business.aggregate.root;

import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.enums.status.BatchStatus;
import com.example.core.domain.business.aggregate.valueobject.reference.BusinessFormRef;
import com.example.core.domain.business.errorcode.CoreDomainErrorCode;
import com.example.core.domain.business.event.BatchStatusChangedEvent;
import com.example.core.types.BatchId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 业务批次聚合根。
 *
 * <p>批次先于申请创建，一个批次可以接收一个或多个申请。批次只通过 ID 与表单关联，
 * 申请与批次的关联由 BusinessApplication.batchId 保存。</p>
 */
public class BusinessBatch extends AggregateRoot<BatchId> {

  List<BusinessFormRef> businessFormRefs;
  private BusinessContext businessContext;
  private OperatorInfo operatorInfo;
  private BatchStatus status;
  private int totalApplicationCount;
  private int successCount;
  private int failedCount;

  protected BusinessBatch(BatchId batchId, String userNo) {
    super(batchId, userNo);
  }

  protected BusinessBatch(BatchId batchId, String createdBy, String updatedBy,
                          LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    super(batchId, createdBy, updatedBy, createdAt, updatedAt, version);
  }

  public static BusinessBatch create(BatchId batchId, BusinessContext context, OperatorInfo operator) {
    BusinessBatch batch = new BusinessBatch(batchId, operator.operatorId());
    batch.businessContext = context;
    batch.operatorInfo = operator;
    batch.status = BatchStatus.CREATED;
    batch.businessFormRefs = List.of();
    batch.validateInvariants();
    return batch;
  }

  /**
   * 从数据库重建聚合根，字段装配完成后再校验不变量。
   */
  public static BusinessBatch reconstitute(
    BatchId batchId,
    String createdBy,
    String updatedBy,
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
    batch.validateInvariants();
    return batch;
  }

  /**
   * 接收一个或多个新申请。零申请的新批次保持 CREATED，首次接收后进入 PROCESSING。
   */
  public void receiveApplications(int count, OperatorInfo operator) {
    String operatorId = requireOperator(operator);
    if (count <= 0) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("接收申请数量必须大于零");
    }
    if (this.status != BatchStatus.CREATED && this.status != BatchStatus.PROCESSING) {
      throw new DomainException(CoreDomainErrorCode.INVALID_STATUS)
        .withLogDetail("只有新建或处理中的批次才能接收申请, BatchId: %s, status: %s"
          .formatted(this.id().value(), this.status));
    }
    if (count > Integer.MAX_VALUE - this.totalApplicationCount) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("批次申请总数超出支持范围");
    }

    this.totalApplicationCount += count;
    transitTo(BatchStatus.PROCESSING);
    this.markUpdated(operatorId);
  }

  public void registerSuccess(OperatorInfo operator) {
    String operatorId = requireOperator(operator);
    requirePendingApplication();
    this.successCount++;
    recalculateStatus();
    this.markUpdated(operatorId);
  }

  public void registerFailure(OperatorInfo operator) {
    String operatorId = requireOperator(operator);
    requirePendingApplication();
    this.failedCount++;
    recalculateStatus();
    this.markUpdated(operatorId);
  }

  /**
   * 按批次下全部申请的真实终态重算计数与批次终态（spec §6.4 对账能力）。
   * <p>
   * 采用绝对赋值而非增量，重复触发（重复事件或重复调度）不会重复计数；
   * 计数未变化时直接返回，不递增乐观锁版本、不重复发布状态变更事件。
   * 已取消批次属人工终态，对账只修正计数、不复活状态。
   *
   * @return 是否发生变更；false 表示计数已一致，调用方无需持久化
   */
  public boolean reconcile(int totalCount, int reconciledSuccess, int reconciledFailure,
                           OperatorInfo operator) {
    String operatorId = requireOperator(operator);
    long processed = (long) reconciledSuccess + reconciledFailure;
    if (totalCount < 0 || reconciledSuccess < 0 || reconciledFailure < 0
      || processed > totalCount) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("对账计数非法: total=%d, success=%d, failed=%d"
          .formatted(totalCount, reconciledSuccess, reconciledFailure));
    }
    if (this.totalApplicationCount == totalCount
      && this.successCount == reconciledSuccess
      && this.failedCount == reconciledFailure) {
      return false;
    }

    this.totalApplicationCount = totalCount;
    this.successCount = reconciledSuccess;
    this.failedCount = reconciledFailure;
    if (this.status != BatchStatus.CANCELLED) {
      transitTo(determineStatusFromCounts());
    }
    this.markUpdated(operatorId);
    validateInvariants();
    return true;
  }

  private BatchStatus determineStatusFromCounts() {
    if (this.totalApplicationCount == 0) {
      return BatchStatus.CREATED;
    }
    if (processedCount() < this.totalApplicationCount) {
      return BatchStatus.PROCESSING;
    }
    return BatchStatus.determine(this.failedCount, this.totalApplicationCount);
  }

  private void requirePendingApplication() {
    if (this.status != BatchStatus.PROCESSING || processedCount() >= this.totalApplicationCount) {
      throw new DomainException(CoreDomainErrorCode.INVALID_STATUS)
        .withLogDetail("批次没有可登记终态结果的申请, BatchId: %s, status: %s"
          .formatted(this.id().value(), this.status));
    }
  }

  private void recalculateStatus() {
    if (this.totalApplicationCount == 0 || processedCount() < this.totalApplicationCount) {
      return;
    }
    transitTo(BatchStatus.determine(this.failedCount, this.totalApplicationCount));
  }

  private void transitTo(BatchStatus targetStatus) {
    if (this.status == targetStatus) {
      return;
    }
    BatchStatus oldStatus = this.status;
    this.status = targetStatus;
    this.registerDomainEvent(BatchStatusChangedEvent.of(this.id(), oldStatus, targetStatus));
  }

  private String requireOperator(OperatorInfo operator) {
    if (operator == null || operator.operatorId() == null) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("批次操作人不能为空");
    }
    return operator.operatorId();
  }

  /**
   * 只有新建或处理中的批次才能取消。
   */
  public void cancel(String reason) {
    if (this.status == null || !this.status.isActive()) {
      throw new DomainException(CoreDomainErrorCode.INVALID_STATUS)
        .withLogDetail("只有未完成/处理中的批次才能取消, BatchId: %s, status: %s"
          .formatted(this.id().value(), this.status));
    }
    transitTo(BatchStatus.CANCELLED);
  }

  @Override
  protected void validateInvariants() {
    if (this.status == null) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("批次状态不能为空");
    }
    if (this.totalApplicationCount < 0 || this.successCount < 0 || this.failedCount < 0) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("批次申请计数不能为负");
    }
    if (processedCount() > this.totalApplicationCount) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("已处理申请数不能大于总申请数");
    }
    validateStatusConsistency();
  }

  private void validateStatusConsistency() {
    int processed = processedCount();
    boolean valid = switch (this.status) {
      case CREATED -> this.totalApplicationCount == 0 && processed == 0;
      case PROCESSING -> this.totalApplicationCount > 0 && processed < this.totalApplicationCount;
      case COMPLETED -> this.totalApplicationCount > 0 && this.successCount == this.totalApplicationCount
        && this.failedCount == 0;
      case FAILED -> this.totalApplicationCount > 0 && this.failedCount == this.totalApplicationCount
        && this.successCount == 0;
      case PARTIAL_FAILED -> this.totalApplicationCount > 0 && processed == this.totalApplicationCount
        && this.successCount > 0 && this.failedCount > 0;
      case CANCELLED -> true;
    };
    if (!valid) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("批次状态与申请计数不一致, BatchId: %s, status: %s"
          .formatted(this.id().value(), this.status));
    }
  }

  private int processedCount() {
    return this.successCount + this.failedCount;
  }

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
