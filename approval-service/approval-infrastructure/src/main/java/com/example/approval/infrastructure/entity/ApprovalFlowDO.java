package com.example.approval.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批流DO实体
 *
 * @author approval-service
 */
@Data
@Table("t_approval_flow")
public class ApprovalFlowDO {

    /**
     * 审批流ID
     */
    @Id(keyType = KeyType.None)
    private String id;

    /**
     * 审批流名称
     */
    private String flowName;

    /**
     * 业务类型
     */
    private String businessType;

    /**
     * 匹配规则（JSON）
     */
    private String matchRules;

    /**
     * 版本号
     */
    private Integer flowVersion;

    /**
     * 状态：ACTIVE-有效，DEPRECATED-已废弃
     */
    private String status;

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