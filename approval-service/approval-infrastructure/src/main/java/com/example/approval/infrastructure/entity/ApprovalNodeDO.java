package com.example.approval.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批节点DO实体
 *
 * @author approval-service
 */
@Data
@Table("t_approval_node")
public class ApprovalNodeDO {

    /**
     * 节点ID
     */
    @Id(keyType = KeyType.None)
    private String id;

    /**
     * 审批流ID
     */
    private String flowId;

    /**
     * 节点顺序
     */
    private Integer nodeOrder;

    /**
     * 节点类型：SPECIFIED_PLAN-指定方案，SAME_PLAN-同方案，LEVEL_UP-上一级
     */
    private String nodeType;

    /**
     * 指定方案ID（仅当 nodeType 为 SPECIFIED_PLAN 时有效）
     */
    private String specifiedPlanId;

    /**
     * 终止级别（仅当 nodeType 为 LEVEL_UP 时有效）
     */
    private Integer terminalLevel;

    /**
     * 审批人类型：SPECIFIED_USER-指定用户，SPECIFIED_ROLE-指定角色
     */
    private String approverType;

    /**
     * 审批人ID列表（JSON）
     */
    private String approverIds;

    /**
     * 角色ID列表（JSON）
     */
    private String roleIds;

    /**
     * 签批模式：OR_SIGN-或签，AND_SIGN-会签
     */
    private String signMode;

    /**
     * 创建人
     */
    private String createdBy;

    /**
     * 更新人
     */
    private String updatedBy;

    /**
     * 创建时间
     */
    @Column(onInsertValue = "now()")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Column(onInsertValue = "now()", onUpdateValue = "now()")
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