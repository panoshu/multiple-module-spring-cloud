package com.example.annuity.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 年金员工批次 DO
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@Data
@Table("t_annuity_employee_batch")
public class AnnuityEmployeeBatchDO {

  @Id(keyType = KeyType.None)
  private String id;
  private String applicationId;
  private String batchStatus;
  private Integer totalEmployeeCount;
  private Integer processedCount;
  private Integer anomalyCount;

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
