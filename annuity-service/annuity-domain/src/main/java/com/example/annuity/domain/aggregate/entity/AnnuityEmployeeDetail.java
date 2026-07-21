package com.example.annuity.domain.aggregate.entity;

import com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeDetailStatus;
import com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeMaterial;
import com.example.annuity.types.AnnuityEmployeeBatchId;
import com.example.annuity.types.AnnuityEmployeeDetailId;
import com.example.shared.domain.aggregate.entity.Entity;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 年金员工明细实体
 * <p>
 * 作为 {@code AnnuityEmployeeBatch} 聚合根的内部实体,承载单个员工的年金明细数据。
 * 外部只能通过聚合根操作本实体。
 *
 * @author annuity-service
 * @since 2026/7/22
 */
public class AnnuityEmployeeDetail extends Entity<AnnuityEmployeeDetailId> {

  private AnnuityEmployeeBatchId batchId;
  private String employeeName;
  private String idCardNo;
  private Integer age;
  private Long monthlySalary;
  private Long monthlyContribution;
  private AnnuityEmployeeDetailStatus status;
  private String anomalyReason;
  private List<AnnuityEmployeeMaterial> materials;
  private LocalDateTime verifiedAt;
  private LocalDateTime materialPreparedAt;

  /**
   * 业务创建构造器
   */
  public AnnuityEmployeeDetail(AnnuityEmployeeDetailId id, AnnuityEmployeeBatchId batchId,
                               String employeeName, String idCardNo, Integer age,
                               Long monthlySalary, Long monthlyContribution, UserNo createdBy) {
    super(id, createdBy);
    this.batchId = Objects.requireNonNull(batchId, "batchId cannot be null");
    this.employeeName = Objects.requireNonNull(employeeName, "employeeName cannot be null");
    this.idCardNo = Objects.requireNonNull(idCardNo, "idCardNo cannot be null");
    this.age = age;
    this.monthlySalary = monthlySalary;
    this.monthlyContribution = monthlyContribution;
    this.status = AnnuityEmployeeDetailStatus.PENDING;
    this.materials = new ArrayList<>();
  }

  /**
   * 数据库重建构造器
   */
  public AnnuityEmployeeDetail(AnnuityEmployeeDetailId id, AnnuityEmployeeBatchId batchId,
                               String employeeName, String idCardNo, Integer age,
                               Long monthlySalary, Long monthlyContribution,
                               AnnuityEmployeeDetailStatus status, String anomalyReason,
                               List<AnnuityEmployeeMaterial> materials,
                               LocalDateTime verifiedAt, LocalDateTime materialPreparedAt,
                               UserNo createdBy, UserNo updatedBy,
                               LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    super(id, createdBy, updatedBy, createdAt, updatedAt, version);
    this.batchId = batchId;
    this.employeeName = employeeName;
    this.idCardNo = idCardNo;
    this.age = age;
    this.monthlySalary = monthlySalary;
    this.monthlyContribution = monthlyContribution;
    this.status = status;
    this.anomalyReason = anomalyReason;
    this.materials = materials == null ? new ArrayList<>() : new ArrayList<>(materials);
    this.verifiedAt = verifiedAt;
    this.materialPreparedAt = materialPreparedAt;
  }

  /**
   * 标记明细已核查
   */
  public void verify(UserNo operator) {
    if (this.status != AnnuityEmployeeDetailStatus.PENDING) {
      throw new IllegalStateException("仅 PENDING 状态可核查,当前: " + this.status);
    }
    this.status = AnnuityEmployeeDetailStatus.VERIFIED;
    this.verifiedAt = LocalDateTime.now();
    markUpdated(operator);
  }

  /**
   * 标记明细异常
   */
  public void markAnomaly(String reason) {
    this.status = AnnuityEmployeeDetailStatus.ANOMALY;
    this.anomalyReason = reason;
  }

  /**
   * 挂载材料清单
   */
  public void assignMaterials(List<AnnuityEmployeeMaterial> materials) {
    if (this.status != AnnuityEmployeeDetailStatus.VERIFIED) {
      throw new IllegalStateException("未核查状态不可分配材料,当前: " + this.status);
    }
    this.materials = new ArrayList<>(materials);
    this.materialPreparedAt = LocalDateTime.now();
    this.status = AnnuityEmployeeDetailStatus.MATERIAL_READY;
  }

  /**
   * 判断必传材料是否全部已上传
   */
  public boolean isMaterialSatisfied() {
    return materials.stream()
        .filter(AnnuityEmployeeMaterial::required)
        .allMatch(AnnuityEmployeeMaterial::uploaded);
  }

  public AnnuityEmployeeBatchId batchId() { return batchId; }
  public String employeeName() { return employeeName; }
  public String idCardNo() { return idCardNo; }
  public Integer age() { return age; }
  public Long monthlySalary() { return monthlySalary; }
  public Long monthlyContribution() { return monthlyContribution; }
  public AnnuityEmployeeDetailStatus status() { return status; }
  public String anomalyReason() { return anomalyReason; }
  public List<AnnuityEmployeeMaterial> materials() { return List.copyOf(materials); }
  public LocalDateTime verifiedAt() { return verifiedAt; }
  public LocalDateTime materialPreparedAt() { return materialPreparedAt; }

  @Override
  protected void validateInvariants() {
    if (batchId == null || employeeName == null || idCardNo == null) {
      throw new IllegalStateException("AnnuityEmployeeDetail 不变式校验失败");
    }
  }
}
