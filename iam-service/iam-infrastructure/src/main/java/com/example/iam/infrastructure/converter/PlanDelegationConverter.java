package com.example.iam.infrastructure.converter;

import com.example.iam.domain.authorization.aggregate.root.PlanDelegation;
import com.example.iam.domain.authorization.aggregate.valueobject.Action;
import com.example.iam.domain.authorization.aggregate.valueobject.BusinessCode;
import com.example.iam.domain.authorization.aggregate.valueobject.DelegationPermission;
import com.example.iam.domain.authorization.aggregate.valueobject.DelegationStatus;
import com.example.iam.domain.authorization.aggregate.valueobject.DelegationType;
import com.example.iam.infrastructure.entity.PlanDelegationDO;
import com.example.iam.infrastructure.entity.PlanDelegationOperatorDO;
import com.example.iam.infrastructure.entity.PlanDelegationPermissionDO;
import com.example.iam.types.PlanDelegationId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 计划代办关系聚合根转换器。
 *
 * <p>负责 {@link PlanDelegation} 与 {@link PlanDelegationDO}+子表之间的转换。
 * 子表 {@code t_iam_plan_delegation_operator} 记录指定操作员,
 * 子表 {@code t_iam_plan_delegation_permission} 记录授权权限明细。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Mapper(componentModel = "spring")
public interface PlanDelegationConverter {

    @Mapping(target = "id", expression = "java(delegation.id() != null ? delegation.id().value() : null)")
    @Mapping(target = "delegationCode", expression = "java(delegation.delegationCode())")
    @Mapping(target = "delegatorPlanNo", expression = "java(delegation.delegatorPlanNo())")
    @Mapping(target = "delegateePlanNo", expression = "java(delegation.delegateePlanNo())")
    @Mapping(target = "delegationType", expression = "java(delegation.delegationType() != null ? delegation.delegationType().name() : null)")
    @Mapping(target = "status", expression = "java(delegation.status() != null ? delegation.status().name() : null)")
    @Mapping(target = "effectiveAt", expression = "java(delegation.effectiveAt())")
    @Mapping(target = "expireAt", expression = "java(delegation.expireAt())")
    @Mapping(target = "createdBy", expression = "java(delegation.createdBy() != null ? delegation.createdBy().value() : null)")
    @Mapping(target = "updatedBy", expression = "java(delegation.updatedBy() != null ? delegation.updatedBy().value() : null)")
    @Mapping(target = "createTime", expression = "java(delegation.createdAt())")
    @Mapping(target = "updateTime", expression = "java(delegation.updatedAt())")
    @Mapping(target = "version", expression = "java(delegation.version() != null ? (int) delegation.version().value() : null)")
    @Mapping(target = "deleted", constant = "false")
    PlanDelegationDO toDO(PlanDelegation delegation);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "delegationId", expression = "java(delegationId)")
    @Mapping(target = "operatorId", expression = "java(operatorId)")
    @Mapping(target = "createdBy", expression = "java(delegation.createdBy() != null ? delegation.createdBy().value() : null)")
    @Mapping(target = "updatedBy", expression = "java(delegation.updatedBy() != null ? delegation.updatedBy().value() : null)")
    @Mapping(target = "createTime", expression = "java(delegation.createdAt())")
    @Mapping(target = "updateTime", expression = "java(delegation.updatedAt())")
    @Mapping(target = "version", expression = "java(delegation.version() != null ? (int) delegation.version().value() : null)")
    @Mapping(target = "deleted", constant = "false")
    PlanDelegationOperatorDO toOperatorDO(PlanDelegation delegation, Long delegationId, Long operatorId);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "delegationId", expression = "java(delegationId)")
    @Mapping(target = "businessCode", expression = "java(permission.businessCode() != null ? permission.businessCode().value() : null)")
    @Mapping(target = "action", expression = "java(permission.action() != null ? permission.action().name() : null)")
    @Mapping(target = "createdBy", expression = "java(delegation.createdBy() != null ? delegation.createdBy().value() : null)")
    @Mapping(target = "updatedBy", expression = "java(delegation.updatedBy() != null ? delegation.updatedBy().value() : null)")
    @Mapping(target = "createTime", expression = "java(delegation.createdAt())")
    @Mapping(target = "updateTime", expression = "java(delegation.updatedAt())")
    @Mapping(target = "version", expression = "java(delegation.version() != null ? (int) delegation.version().value() : null)")
    @Mapping(target = "deleted", constant = "false")
    PlanDelegationPermissionDO toPermissionDO(PlanDelegation delegation, DelegationPermission permission, Long delegationId);

    default PlanDelegation toDomain(PlanDelegationDO delegationDO,
                                      List<PlanDelegationOperatorDO> operatorDOs,
                                      List<PlanDelegationPermissionDO> permissionDOs) {
        if (delegationDO == null) {
            return null;
        }
        Set<Long> operators = new HashSet<>();
        if (operatorDOs != null) {
            for (PlanDelegationOperatorDO operatorDO : operatorDOs) {
                operators.add(operatorDO.getOperatorId());
            }
        }
        Set<DelegationPermission> permissions = new HashSet<>();
        if (permissionDOs != null) {
            for (PlanDelegationPermissionDO permissionDO : permissionDOs) {
                permissions.add(DelegationPermission.of(
                        BusinessCode.of(permissionDO.getBusinessCode()),
                        Action.valueOf(permissionDO.getAction())
                ));
            }
        }
        return PlanDelegation.reconstitute(
                PlanDelegationId.of(delegationDO.getId()),
                delegationDO.getDelegationCode(),
                delegationDO.getDelegatorPlanNo(),
                delegationDO.getDelegateePlanNo(),
                toDelegationType(delegationDO.getDelegationType()),
                operators,
                permissions,
                toDelegationStatus(delegationDO.getStatus()),
                delegationDO.getEffectiveAt(),
                delegationDO.getExpireAt(),
                toUserNo(delegationDO.getCreatedBy()),
                toUserNo(delegationDO.getUpdatedBy()),
                delegationDO.getCreateTime(),
                delegationDO.getUpdateTime(),
                toVersion(delegationDO.getVersion())
        );
    }

    @Named("toDelegationType")
    default DelegationType toDelegationType(String delegationType) {
        return delegationType != null ? DelegationType.valueOf(delegationType) : null;
    }

    @Named("toDelegationStatus")
    default DelegationStatus toDelegationStatus(String status) {
        return status != null ? DelegationStatus.valueOf(status) : null;
    }

    @Named("toUserNo")
    default UserNo toUserNo(String userNo) {
        return userNo != null ? UserNo.of(userNo) : null;
    }

    @Named("toVersion")
    default Version toVersion(Integer version) {
        return version != null ? Version.of(version) : null;
    }
}
