package com.example.iam.infrastructure.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 计划代办关系 DO。
 *
 * <p>对应表 {@code t_iam_plan_delegation},声明"计划 A 授权计划 B 代办某些业务"的关系。
 * 指定操作员与授权权限明细通过子表 {@link PlanDelegationOperatorDO} 与
 * {@link PlanDelegationPermissionDO} 承载。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Data
@Table("t_iam_plan_delegation")
public class PlanDelegationDO {

    @Id(keyType = KeyType.None)
    private Long id;

    /** 代办编码(全局唯一) */
    private String delegationCode;

    /** 授权方计划编号(出借权限的计划) */
    private String delegatorPlanNo;

    /** 被授权方计划编号(获得权限的计划) */
    private String delegateePlanNo;

    /** 代办类型:ALL_OPERATORS/SPECIFIC_OPERATORS */
    private String delegationType;

    /** 状态:ACTIVE/REVOKED/EXPIRED */
    private String status;

    /** 生效时间 */
    private LocalDateTime effectiveAt;

    /** 失效时间(可空,表示永久) */
    private LocalDateTime expireAt;

    private String createdBy;

    private String updatedBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @Column(isLogicDelete = true)
    private Boolean deleted;

    @Column(version = true)
    private Integer version;
}
