package com.example.approval.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 节点执行记录DO实体
 *
 * @author approval-service
 */
@Data
@Table("t_approval_node_execution")
public class ApprovalNodeExecutionDO {

    /**
     * 执行ID
     */
    @Id(keyType = KeyType.None)
    private String id;

    /**
     * 审批实例ID
     */
    private String instanceId;

    /**
     * 节点ID
     */
    private String nodeId;

    /**
     * 节点顺序
     */
    private Integer nodeOrder;

    /**
     * 执行状态：PENDING-待执行，APPROVED-已通过，REJECTED-已拒绝，SKIPPED-已跳过
     */
    private String status;

    /**
     * 开始时间
     */
    private LocalDateTime startedAt;

    /**
     * 完成时间
     */
    private LocalDateTime completedAt;

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