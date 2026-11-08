package com.example.approval.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批记录DO实体
 *
 * @author approval-service
 */
@Data
@Table("t_approval_record")
public class ApprovalRecordDO {

  /**
   * 审批记录ID
   */
  @Id(keyType = KeyType.None)
  private String id;

  /**
   * 节点执行ID
   */
  private String executionId;

  /**
   * 审批人ID
   */
  private String approverId;

  /**
   * 审批动作：APPROVE-通过，REJECT-拒绝，TRANSFER-转交
   */
  private String action;

  /**
   * 审批意见
   */
  private String opinion;

  /**
   * 驳回目标（JSON）
   */
  private String rejectTarget;

  /**
   * 转交目标用户
   */
  private String transferTo;

  /**
   * 操作时间
   */
  private LocalDateTime operatedAt;

  /**
   * 创建人
   */
  private String createdBy;

  /**
   * 更新人
   */
  private String updatedBy;

  /**
   * 创建时间（由应用层通过 Converter 从领域对象映射，不使用 ORM 自动管理）
   */
  private LocalDateTime createTime;

  /**
   * 更新时间（由应用层通过 Converter 从领域对象映射，不使用 ORM 自动管理）
   */
  private LocalDateTime updateTime;

  /**
   * 删除标记
   */
  @Column(isLogicDelete = true)
  private Boolean deleted;

  /**
   * 乐观锁版本号
   */
  @Column(version = true)
  private Integer version;
}
