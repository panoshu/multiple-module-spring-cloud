package com.example.iam.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 代办指定操作员 DO。
 *
 * <p>对应表 {@code t_iam_plan_delegation_operator},作为 {@link PlanDelegationDO} 的子表,
 * 仅在代办类型为 {@code SPECIFIC_OPERATORS} 时使用,记录被授权的具体操作员列表。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Data
@Table("t_iam_plan_delegation_operator")
public class PlanDelegationOperatorDO {

    @Id(keyType = KeyType.None)
    private Long id;

    /** 代办关系 ID(FK to t_iam_plan_delegation.id) */
    private Long delegationId;

    /** 操作员 ID(FK to t_iam_user.id) */
    private Long operatorId;

    private String createdBy;

    private String updatedBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @Column(isLogicDelete = true)
    private Boolean deleted;

    @Column(version = true)
    private Integer version;
}
