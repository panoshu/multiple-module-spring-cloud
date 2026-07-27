package com.example.iam.api.dto;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 计划代办关系DTO
 *
 * <p>对应计划代办关系聚合根(PlanDelegation)的展示视图,声明"计划 A 授权计划 B 代办某些业务"的关系。
 *
 * @author iam-service
 */
public record PlanDelegationDTO(
    /**
     * 代办关系ID
     */
    Long delegationId,
    /**
     * 代办编码(全局唯一)
     */
    String delegationCode,
    /**
     * 授权方计划编号(出借权限的计划)
     */
    String delegatorPlanNo,
    /**
     * 被授权方计划编号(获得权限的计划)
     */
    String delegateePlanNo,
    /**
     * 代办类型(ALL_OPERATORS/SPECIFIC_OPERATORS)
     */
    String delegationType,
    /**
     * 指定操作员集合(仅 SPECIFIC_OPERATORS 类型时使用)
     */
    Set<Long> designatedOperators,
    /**
     * 授权权限集合(声明被授权方可执行的业务+动作)
     */
    Set<DelegationPermissionDTO> delegatedPermissions,
    /**
     * 状态(ACTIVE/REVOKED/EXPIRED)
     */
    String status,
    /**
     * 生效时间
     */
    LocalDateTime effectiveAt,
    /**
     * 失效时间(可空,表示永久)
     */
    LocalDateTime expireAt,
    /**
     * 创建时间
     */
    LocalDateTime createdAt,
    /**
     * 更新时间
     */
    LocalDateTime updatedAt,
    /**
     * 乐观锁版本号
     */
    Long version
) {
}
