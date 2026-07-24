package com.example.annuity.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 年金业务申请单 DO
 * <p>
 * 对应聚合根 {@code BusinessApplication}，将 {@code BusinessContext} 和 {@code OperatorInfo}
 * 拍平为独立列；{@code businessExtension} 通过 Jackson 多态序列化持久化为 JSON。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
@Data
@Table("t_annuity_application")
public class ApplicationDO {

  @Id(keyType = KeyType.None)
  private String id;

  // ===== 关联 ID =====
  private String batchId;
  private String formId;

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

  // ===== 文件与统计字段 =====
  private String parsedJsonFileId;
  private Integer expectedDetailCount;

  // ===== 业务扩展字段（多态 JSON） =====
  // PostgreSQL 使用 JSONB，MySQL 使用 JSON 或 TEXT
  private String businessExtension;

  // ===== 状态机字段 =====
  private String status;
  private String currentStep;

  private LocalDateTime applyTime;
  private LocalDateTime completeTime;

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
