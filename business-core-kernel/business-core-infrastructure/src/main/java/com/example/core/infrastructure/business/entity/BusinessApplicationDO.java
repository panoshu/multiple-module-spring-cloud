package com.example.core.infrastructure.business.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务申请单 DO
 * <p>
 * 对应聚合根 {@code BusinessApplication}，将 {@code BusinessContext} 和 {@code OperatorInfo}
 * 拍平为独立列。业务扩展大对象已迁出核心聚合（§6.2），由业务服务自有存储承载。
 * <p>
 * {@code planMaterials}/{@code packageFile} 为流程内内存态字段，不持久化到本表。
 *
 * @author core-kernel
 * @since 2026/8/8
 */
@Data
@Table("t_business_application")
public class BusinessApplicationDO {

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

  // ===== 状态机字段 =====
  private String status;
  private String currentStep;
  private String definitionVersion;

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
