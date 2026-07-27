package com.example.iam.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 代办授权权限明细 DO。
 *
 * <p>对应表 {@code t_iam_plan_delegation_permission},作为 {@link PlanDelegationDO} 的子表,
 * 声明被授权方可执行的业务+动作。每条记录由 {@code delegationId + businessCode + action} 唯一标识。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Data
@Table("t_iam_plan_delegation_permission")
public class PlanDelegationPermissionDO {

    @Id(keyType = KeyType.None)
    private Long id;

    /** 代办关系 ID(FK to t_iam_plan_delegation.id) */
    private Long delegationId;

    /** 业务编码 */
    private String businessCode;

    /** 业务动作:HANDLE/QUERY/AUDIT */
    private String action;

    private String createdBy;

    private String updatedBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @Column(isLogicDelete = true)
    private Boolean deleted;

    @Column(version = true)
    private Integer version;
}
