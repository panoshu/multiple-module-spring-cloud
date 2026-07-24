package com.example.annuity.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 年金业务表单 DO
 * <p>
 * 对应聚合根 {@code BusinessForm}，将 {@code BusinessContext} 和 {@code OperatorInfo}
 * 拍平为独立列；表单关联的申请单引用（{@code applicationRefs}）通过 {@code t_annuity_application}
 * 反向查询组装，不在本表持久化。表单文件信息拍平为 fileId/fileName/fileSize 三列。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
@Data
@Table("t_annuity_form")
public class FormDO {

  @Id(keyType = KeyType.None)
  private String id;

  // ===== 关联 ID =====
  private String batchId;

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

  // ===== 表单文件字段 =====
  private String formFileId;
  private String formFileName;
  private Long formFileSize;

  // ===== 表单状态字段 =====
  private String formStatus;

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
