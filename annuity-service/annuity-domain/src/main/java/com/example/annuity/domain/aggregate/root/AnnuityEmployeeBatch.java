package com.example.annuity.domain.aggregate.root;

import com.example.annuity.domain.aggregate.entity.AnnuityEmployeeDetail;
import com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeBatchStatus;
import com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeDetailStatus;
import com.example.annuity.types.AnnuityEmployeeBatchId;
import com.example.annuity.types.AnnuityEmployeeDetailId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 年金员工明细批次聚合根
 * <p>
 * 承载某次年金申请的员工明细批次,管理明细集合的一致性边界。
 * 通过 {@link ApplicationId} 引用 kernel 的 BusinessApplication(ID 引用,不直接持有对象)。
 *
 * @author annuity-service
 * @since 2026/7/22
 */
public class AnnuityEmployeeBatch extends AggregateRoot<AnnuityEmployeeBatchId> {

  private ApplicationId applicationId;
  private List<AnnuityEmployeeDetail> details;
  private AnnuityEmployeeBatchStatus status;
  private int totalEmployeeCount;
  private int processedCount;
  private int anomalyCount;

  /**
   * 业务创建构造器
   */
  private AnnuityEmployeeBatch(AnnuityEmployeeBatchId id, ApplicationId applicationId,
                              int totalEmployeeCount, UserNo createdBy) {
    super(id, createdBy);
    this.applicationId = Objects.requireNonNull(applicationId, "applicationId cannot be null");
    if (totalEmployeeCount < 0) {
      throw new IllegalArgumentException("totalEmployeeCount cannot be negative");
    }
    this.totalEmployeeCount = totalEmployeeCount;
    this.details = new ArrayList<>();
    this.status = AnnuityEmployeeBatchStatus.PENDING;
  }

  /**
   * 数据库重建构造器
   */
  public AnnuityEmployeeBatch(AnnuityEmployeeBatchId id, ApplicationId applicationId,
                              List<AnnuityEmployeeDetail> details, AnnuityEmployeeBatchStatus status,
                              int totalEmployeeCount, int processedCount, int anomalyCount,
                              UserNo createdBy, UserNo updatedBy,
                              LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    super(id, createdBy, updatedBy, createdAt, updatedAt, version);
    this.applicationId = applicationId;
    this.details = details == null ? new ArrayList<>() : new ArrayList<>(details);
    this.status = status;
    this.totalEmployeeCount = totalEmployeeCount;
    this.processedCount = processedCount;
    this.anomalyCount = anomalyCount;
  }

  /**
   * 工厂方法:创建新批次
   */
  public static AnnuityEmployeeBatch create(AnnuityEmployeeBatchId id, ApplicationId applicationId,
                                           int totalEmployeeCount, UserNo createdBy) {
    return new AnnuityEmployeeBatch(id, applicationId, totalEmployeeCount, createdBy);
  }

  /**
   * 添加明细到批次
   */
  public void addDetail(AnnuityEmployeeDetail detail) {
    if (this.status != AnnuityEmployeeBatchStatus.PENDING
        && this.status != AnnuityEmployeeBatchStatus.PROCESSING) {
      throw new IllegalStateException("批次已终态,无法添加明细: " + this.status);
    }
    this.details.add(detail);
    if (this.status == AnnuityEmployeeBatchStatus.PENDING) {
      this.status = AnnuityEmployeeBatchStatus.PROCESSING;
    }
  }

  /**
   * 内部方法:从数据库重建时挂载明细(不触发状态变更,不触发事件)
   */
  public void attachDetail(AnnuityEmployeeDetail detail) {
    this.details.add(detail);
  }

  /**
   * 标记明细已处理
   */
  public void markDetailProcessed(AnnuityEmployeeDetailId detailId, UserNo operator) {
    AnnuityEmployeeDetail detail = findDetailOrThrow(detailId);
    if (detail.status() != AnnuityEmployeeDetailStatus.PENDING) {
      throw new IllegalStateException("明细非 PENDING 状态,不可重复处理: " + detailId);
    }
    detail.verify(operator);
    this.processedCount++;
    this.markUpdated(operator);
  }

  /**
   * 标记明细异常
   */
  public void markDetailAnomaly(AnnuityEmployeeDetailId detailId, String reason) {
    AnnuityEmployeeDetail detail = findDetailOrThrow(detailId);
    if (detail.status() == AnnuityEmployeeDetailStatus.ANOMALY) {
      throw new IllegalStateException("明细已标记为异常,不可重复标记: " + detailId);
    }
    detail.markAnomaly(reason);
    this.anomalyCount++;
  }

  /**
   * 完成批次
   */
  public void complete() {
    if (!isAllProcessed()) {
      throw new IllegalStateException("尚有明细未全部处理,无法完成批次");
    }
    this.status = AnnuityEmployeeBatchStatus.COMPLETED;
  }

  /**
   * 批次失败
   */
  public void fail(String reason) {
    this.status = AnnuityEmployeeBatchStatus.FAILED;
  }

  /**
   * 是否所有明细已处理(已处理 + 异常 == 总数)
   */
  public boolean isAllProcessed() {
    return processedCount + anomalyCount >= totalEmployeeCount;
  }

  /**
   * 返回待处理明细(PENDING 状态)
   */
  public List<AnnuityEmployeeDetail> pendingDetails() {
    return details.stream()
        .filter(d -> d.status() == AnnuityEmployeeDetailStatus.PENDING)
        .toList();
  }

  /**
   * 返回已核查明细(VERIFIED 状态)
   */
  public List<AnnuityEmployeeDetail> verifiedDetails() {
    return details.stream()
        .filter(d -> d.status() == AnnuityEmployeeDetailStatus.VERIFIED)
        .toList();
  }

  /**
   * 查找明细
   */
  public Optional<AnnuityEmployeeDetail> findDetail(AnnuityEmployeeDetailId detailId) {
    return details.stream().filter(d -> d.id().equals(detailId)).findFirst();
  }

  private AnnuityEmployeeDetail findDetailOrThrow(AnnuityEmployeeDetailId detailId) {
    return findDetail(detailId)
        .orElseThrow(() -> new IllegalArgumentException("明细不存在: " + detailId));
  }

  public ApplicationId applicationId() { return applicationId; }
  public List<AnnuityEmployeeDetail> details() { return List.copyOf(details); }
  public AnnuityEmployeeBatchStatus status() { return status; }
  public int totalEmployeeCount() { return totalEmployeeCount; }
  public int processedCount() { return processedCount; }
  public int anomalyCount() { return anomalyCount; }

  @Override
  protected void validateInvariants() {
    if (applicationId == null) {
      throw new IllegalStateException("applicationId cannot be null");
    }
  }
}
