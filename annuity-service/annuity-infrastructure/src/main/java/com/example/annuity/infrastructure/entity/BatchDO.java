package com.example.annuity.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 年金业务批次 DO
 * <p>
 * 对应聚合根 {@code BusinessBatch}，将 {@code BusinessContext} 和 {@code OperatorInfo}
 * 拍平为独立列；批次内关联的表单引用（{@code businessFormRefs}）通过 {@code t_annuity_form}
 * 反向查询组装，不在本表持久化。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
@Data
@Table("t_annuity_batch")
public class BatchDO {

  @Id(keyType = KeyType.None)
  private String id;

  // ===== BusinessContext 拍平字段 =====
  private String businessType;
  private String customerNo;
  private String customerName;
  private String productNo;
  private String productName;
  private String planNo;
  private String planName;
  private String operationModel;
  private String accountManager;

  // ===== OperatorInfo 拍平字段 =====
  private String channel;
  private String operatorId;
  private String operatorName;
  private Boolean isProxy;

  // ===== 批次状态字段 =====
  private String status;
  private Integer totalApplicationCount;
  private Integer successCount;
  private Integer failedCount;

  // ===== 通用字段 =====
  private String createdBy;
  private String updatedBy;

  // createTime/updateTime 由应用层通过 Converter 从领域对象映射，不使用 ORM 自动管理
  private LocalDateTime createTime;

  private LocalDateTime updateTime;

  @Column(isLogicDelete = true)
  private Boolean deleted;

  @Column(version = true)
  private Integer version;
}
