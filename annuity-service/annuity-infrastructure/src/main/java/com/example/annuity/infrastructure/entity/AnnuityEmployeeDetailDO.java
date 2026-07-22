package com.example.annuity.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 年金员工明细 DO
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@Data
@Table("t_annuity_employee_detail")
public class AnnuityEmployeeDetailDO {

  @Id(keyType = KeyType.None)
  private String id;
  private String batchId;
  private String employeeName;
  private String idCardNo;
  private Integer age;
  private Long monthlySalary;
  private Long monthlyContribution;
  private String detailStatus;
  private String anomalyReason;
  private String materials;
  private LocalDateTime verifiedAt;
  private LocalDateTime materialPreparedAt;

  @Column(onInsertValue = "now()")
  private LocalDateTime createTime;
  @Column(onInsertValue = "now()", onUpdateValue = "now()")
  private LocalDateTime updateTime;
  private String createdBy;
  private String updatedBy;
  @Column(isLogicDelete = true)
  private Boolean deleted;
  @Column(version = true)
  private Integer version;
}
