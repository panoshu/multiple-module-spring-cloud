package com.example.approval.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批实例DO实体
 *
 * @author approval-service
 */
@Data
@Table("t_approval_instance")
public class ApprovalInstanceDO {

    /**
     * 审批实例ID
     */
    @Id(keyType = KeyType.None)
    private String id;

    /**
     * 审批流ID
     */
    private String flowId;

    /**
     * 审批流版本号
     */
    private Integer flowVersion;

    /**
     * 业务申请ID
     */
    private String businessApplicationId;

    /**
     * 业务类型
     */
    private String businessType;

    /**
     * 业务单号
     */
    private String businessNo;

    /**
     * 当前节点顺序
     */
    private Integer currentNodeOrder;

    /**
     * 实例状态：PENDING-待审批，APPROVING-审批中，APPROVED-已通过，REJECTED-已拒绝，WITHDRAWN-已撤回
     */
    private String status;

    /**
     * 发起人方案
     */
    private String initiatorPlan;

    /**
     * 当前方案
     */
    private String currentPlan;

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