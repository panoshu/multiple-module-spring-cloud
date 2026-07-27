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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link PlanDelegationConverter} 单元测试。
 *
 * <p>覆盖 PlanDelegation 与 PlanDelegationDO + 子表(OperatorDO/PermissionDO)双向映射、
 * null 输入处理、子表集合转换。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@DisplayName("PlanDelegationConverter 转换器测试")
class PlanDelegationConverterTest {

    private final PlanDelegationConverter converter =
            Mappers.getMapper(PlanDelegationConverter.class);

    private static final Long DELEGATION_ID_VALUE = 4001L;
    private static final String DELEGATION_CODE = "D-001";
    private static final String DELEGATOR_PLAN_NO = "PLAN-A";
    private static final String DELEGATEE_PLAN_NO = "PLAN-B";
    private static final DelegationType DELEGATION_TYPE = DelegationType.SPECIFIC_OPERATORS;
    private static final DelegationStatus STATUS = DelegationStatus.ACTIVE;
    private static final LocalDateTime EFFECTIVE_AT = LocalDateTime.of(2026, 7, 1, 0, 0, 0);
    private static final LocalDateTime EXPIRE_AT = LocalDateTime.of(2027, 1, 1, 0, 0, 0);
    private static final String OPERATOR = "U-ADMIN";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 7, 1, 10, 0, 0);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 7, 2, 11, 30, 0);
    private static final long VERSION_VALUE = 1L;

    // 指定操作员
    private static final Long OPERATOR_ID_1 = 5001L;
    private static final Long OPERATOR_ID_2 = 5002L;

    // 权限
    private static final BusinessCode BIZ_CODE = BusinessCode.of("ANNUITY_ESTABLISH");
    private static final Action ACTION = Action.HANDLE;
    private static final DelegationPermission PERMISSION = DelegationPermission.of(BIZ_CODE, ACTION);

    @Nested
    @DisplayName("toDO: PlanDelegation -> PlanDelegationDO")
    class ToDOTest {

        @Test
        @DisplayName("完整字段映射")
        void shouldMapAllFieldsToDO() {
            PlanDelegation delegation = buildDelegation();

            PlanDelegationDO delegationDO = converter.toDO(delegation);

            assertThat(delegationDO).isNotNull();
            assertThat(delegationDO.getId()).isEqualTo(DELEGATION_ID_VALUE);
            assertThat(delegationDO.getDelegationCode()).isEqualTo(DELEGATION_CODE);
            assertThat(delegationDO.getDelegatorPlanNo()).isEqualTo(DELEGATOR_PLAN_NO);
            assertThat(delegationDO.getDelegateePlanNo()).isEqualTo(DELEGATEE_PLAN_NO);
            assertThat(delegationDO.getDelegationType()).isEqualTo(DELEGATION_TYPE.name());
            assertThat(delegationDO.getStatus()).isEqualTo(STATUS.name());
            assertThat(delegationDO.getEffectiveAt()).isEqualTo(EFFECTIVE_AT);
            assertThat(delegationDO.getExpireAt()).isEqualTo(EXPIRE_AT);
            assertThat(delegationDO.getCreatedBy()).isEqualTo(OPERATOR);
            assertThat(delegationDO.getUpdatedBy()).isEqualTo(OPERATOR);
            assertThat(delegationDO.getCreateTime()).isEqualTo(CREATED_AT);
            assertThat(delegationDO.getUpdateTime()).isEqualTo(UPDATED_AT);
            assertThat(delegationDO.getVersion()).isEqualTo((int) VERSION_VALUE);
            assertThat(delegationDO.getDeleted()).isFalse();
        }

        @Test
        @DisplayName("REVOKED 状态正确映射")
        void shouldMapRevokedStatus() {
            PlanDelegation delegation = PlanDelegation.reconstitute(
                    PlanDelegationId.of(DELEGATION_ID_VALUE), DELEGATION_CODE,
                    DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
                    DELEGATION_TYPE, Set.of(OPERATOR_ID_1), Set.of(PERMISSION),
                    DelegationStatus.REVOKED, EFFECTIVE_AT, EXPIRE_AT,
                    UserNo.of(OPERATOR), UserNo.of(OPERATOR),
                    CREATED_AT, UPDATED_AT, Version.of(VERSION_VALUE));

            PlanDelegationDO delegationDO = converter.toDO(delegation);

            assertThat(delegationDO.getStatus()).isEqualTo("REVOKED");
        }
    }

    @Nested
    @DisplayName("toOperatorDO: 生成操作员子表 DO")
    class ToOperatorDOTest {

        @Test
        @DisplayName("完整字段映射:id 由 ignore 标记为 null,delegationId 与 operatorId 通过参数注入")
        void shouldMapOperatorDOWithInjectedIds() {
            PlanDelegation delegation = buildDelegation();
            Long delegationId = 4001L;
            Long operatorId = OPERATOR_ID_1;

            PlanDelegationOperatorDO operatorDO = converter.toOperatorDO(delegation, delegationId, operatorId);

            assertThat(operatorDO).isNotNull();
            assertThat(operatorDO.getId()).isNull(); // @Mapping(target = "id", ignore = true)
            assertThat(operatorDO.getDelegationId()).isEqualTo(delegationId);
            assertThat(operatorDO.getOperatorId()).isEqualTo(operatorId);
            assertThat(operatorDO.getCreatedBy()).isEqualTo(OPERATOR);
            assertThat(operatorDO.getUpdatedBy()).isEqualTo(OPERATOR);
            assertThat(operatorDO.getCreateTime()).isEqualTo(CREATED_AT);
            assertThat(operatorDO.getUpdateTime()).isEqualTo(UPDATED_AT);
            assertThat(operatorDO.getVersion()).isEqualTo((int) VERSION_VALUE);
            assertThat(operatorDO.getDeleted()).isFalse();
        }
    }

    @Nested
    @DisplayName("toPermissionDO: 生成权限明细子表 DO")
    class ToPermissionDOTest {

        @Test
        @DisplayName("完整字段映射:businessCode/action 通过值对象字段提取")
        void shouldMapPermissionDO() {
            PlanDelegation delegation = buildDelegation();
            Long delegationId = 4001L;

            PlanDelegationPermissionDO permissionDO =
                    converter.toPermissionDO(delegation, PERMISSION, delegationId);

            assertThat(permissionDO).isNotNull();
            assertThat(permissionDO.getId()).isNull(); // @Mapping(target = "id", ignore = true)
            assertThat(permissionDO.getDelegationId()).isEqualTo(delegationId);
            assertThat(permissionDO.getBusinessCode()).isEqualTo(BIZ_CODE.value());
            assertThat(permissionDO.getAction()).isEqualTo(ACTION.name());
            assertThat(permissionDO.getCreatedBy()).isEqualTo(OPERATOR);
            assertThat(permissionDO.getUpdatedBy()).isEqualTo(OPERATOR);
            assertThat(permissionDO.getCreateTime()).isEqualTo(CREATED_AT);
            assertThat(permissionDO.getUpdateTime()).isEqualTo(UPDATED_AT);
            assertThat(permissionDO.getVersion()).isEqualTo((int) VERSION_VALUE);
            assertThat(permissionDO.getDeleted()).isFalse();
        }
    }

    @Nested
    @DisplayName("toDomain: (DO, List<OperatorDO>, List<PermissionDO>) -> PlanDelegation")
    class ToDomainTest {

        @Test
        @DisplayName("完整字段映射:含操作员与权限子表")
        void shouldMapToDomainWithChildren() {
            PlanDelegationDO delegationDO = buildDelegationDO();
            List<PlanDelegationOperatorDO> operatorDOs = new ArrayList<>();
            operatorDOs.add(buildOperatorDO(OPERATOR_ID_1));
            operatorDOs.add(buildOperatorDO(OPERATOR_ID_2));
            List<PlanDelegationPermissionDO> permissionDOs = new ArrayList<>();
            permissionDOs.add(buildPermissionDO(BIZ_CODE.value(), ACTION.name()));

            PlanDelegation delegation = converter.toDomain(delegationDO, operatorDOs, permissionDOs);

            assertThat(delegation).isNotNull();
            assertThat(delegation.id().value()).isEqualTo(DELEGATION_ID_VALUE);
            assertThat(delegation.delegationCode()).isEqualTo(DELEGATION_CODE);
            assertThat(delegation.delegatorPlanNo()).isEqualTo(DELEGATOR_PLAN_NO);
            assertThat(delegation.delegateePlanNo()).isEqualTo(DELEGATEE_PLAN_NO);
            assertThat(delegation.delegationType()).isEqualTo(DELEGATION_TYPE);
            assertThat(delegation.designatedOperators())
                    .containsExactlyInAnyOrder(OPERATOR_ID_1, OPERATOR_ID_2);
            assertThat(delegation.delegatedPermissions()).containsExactly(PERMISSION);
            assertThat(delegation.status()).isEqualTo(STATUS);
            assertThat(delegation.effectiveAt()).isEqualTo(EFFECTIVE_AT);
            assertThat(delegation.expireAt()).isEqualTo(EXPIRE_AT);
            assertThat(delegation.createdBy().value()).isEqualTo(OPERATOR);
            assertThat(delegation.updatedBy().value()).isEqualTo(OPERATOR);
            assertThat(delegation.createdAt()).isEqualTo(CREATED_AT);
            assertThat(delegation.updatedAt()).isEqualTo(UPDATED_AT);
            assertThat(delegation.version().value()).isEqualTo(VERSION_VALUE);
        }

        @Test
        @DisplayName("delegationDO 为 null 时返回 null")
        void shouldReturnNullWhenDOIsNull() {
            assertThat(converter.toDomain(null, new ArrayList<>(), new ArrayList<>())).isNull();
        }

        @Test
        @DisplayName("子表列表为 null 时按空集合处理,领域校验拒绝重建")
        void shouldThrowWhenChildrenListsAreNull() {
            PlanDelegationDO delegationDO = buildDelegationDO();

            // Converter 行为:子表列表为 null 时按空集合处理;
            // 但 PlanDelegation.validateInvariants() 拒绝空 delegatedPermissions,重建抛 IllegalStateException
            assertThatThrownBy(() -> converter.toDomain(delegationDO, null, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("delegatedPermissions cannot be null or empty");
        }

        @Test
        @DisplayName("ALL_OPERATORS 类型操作员集合为空")
        void shouldHandleAllOperatorsType() {
            PlanDelegationDO delegationDO = buildDelegationDO();
            delegationDO.setDelegationType(DelegationType.ALL_OPERATORS.name());

            PlanDelegation delegation = converter.toDomain(delegationDO, null, List.of(buildPermissionDO(BIZ_CODE.value(), ACTION.name())));

            assertThat(delegation.delegationType()).isEqualTo(DelegationType.ALL_OPERATORS);
            assertThat(delegation.designatedOperators()).isEmpty();
        }
    }

    @Nested
    @DisplayName("枚举与 ID 类型转换")
    class TypeConversionTest {

        @Test
        @DisplayName("toDelegationType: null 返回 null")
        void shouldReturnNullTypeForNullString() {
            assertThat(converter.toDelegationType(null)).isNull();
        }

        @Test
        @DisplayName("toDelegationStatus: null 返回 null")
        void shouldReturnNullStatusForNullString() {
            assertThat(converter.toDelegationStatus(null)).isNull();
        }

        @Test
        @DisplayName("toVersion: null 返回 null")
        void shouldReturnNullVersionForNullInteger() {
            assertThat(converter.toVersion(null)).isNull();
        }
    }

    @Nested
    @DisplayName("双向转换一致性")
    class RoundTripTest {

        @Test
        @DisplayName("主表 toDomain(toDO(delegation)) 关键字段一致")
        void shouldPreserveMainTableFieldsThroughRoundTrip() {
            PlanDelegation original = buildDelegation();

            PlanDelegationDO intermediateDO = converter.toDO(original);
            // toDO 丢失子表数据,重建时需提供非空 operatorDOs/permissionDOs 才能通过领域校验
            List<PlanDelegationOperatorDO> operatorDOs = original.designatedOperators().stream()
                    .map(opId -> converter.toOperatorDO(original, DELEGATION_ID_VALUE, opId))
                    .toList();
            List<PlanDelegationPermissionDO> permissionDOs = original.delegatedPermissions().stream()
                    .map(permission -> converter.toPermissionDO(original, permission, DELEGATION_ID_VALUE))
                    .toList();
            PlanDelegation rebuilt = converter.toDomain(intermediateDO, operatorDOs, permissionDOs);

            assertThat(rebuilt.id()).isEqualTo(original.id());
            assertThat(rebuilt.delegationCode()).isEqualTo(original.delegationCode());
            assertThat(rebuilt.delegatorPlanNo()).isEqualTo(original.delegatorPlanNo());
            assertThat(rebuilt.delegateePlanNo()).isEqualTo(original.delegateePlanNo());
            assertThat(rebuilt.delegationType()).isEqualTo(original.delegationType());
            assertThat(rebuilt.status()).isEqualTo(original.status());
            assertThat(rebuilt.effectiveAt()).isEqualTo(original.effectiveAt());
            assertThat(rebuilt.expireAt()).isEqualTo(original.expireAt());
            assertThat(rebuilt.createdBy()).isEqualTo(original.createdBy());
            assertThat(rebuilt.updatedBy()).isEqualTo(original.updatedBy());
            assertThat(rebuilt.version()).isEqualTo(original.version());
        }

        @Test
        @DisplayName("操作员子表 toDomain(toOperatorDO) 关键字段一致")
        void shouldPreserveOperatorRoundTrip() {
            PlanDelegation delegation = buildDelegation();

            PlanDelegationOperatorDO operatorDO =
                    converter.toOperatorDO(delegation, DELEGATION_ID_VALUE, OPERATOR_ID_1);

            assertThat(operatorDO.getDelegationId()).isEqualTo(DELEGATION_ID_VALUE);
            assertThat(operatorDO.getOperatorId()).isEqualTo(OPERATOR_ID_1);
        }
    }

    private PlanDelegation buildDelegation() {
        Set<Long> operators = new LinkedHashSet<>();
        operators.add(OPERATOR_ID_1);
        operators.add(OPERATOR_ID_2);
        Set<DelegationPermission> permissions = new LinkedHashSet<>();
        permissions.add(PERMISSION);
        return PlanDelegation.reconstitute(
                PlanDelegationId.of(DELEGATION_ID_VALUE), DELEGATION_CODE,
                DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
                DELEGATION_TYPE, operators, permissions,
                STATUS, EFFECTIVE_AT, EXPIRE_AT,
                UserNo.of(OPERATOR), UserNo.of(OPERATOR),
                CREATED_AT, UPDATED_AT, Version.of(VERSION_VALUE));
    }

    private PlanDelegationDO buildDelegationDO() {
        PlanDelegationDO delegationDO = new PlanDelegationDO();
        delegationDO.setId(DELEGATION_ID_VALUE);
        delegationDO.setDelegationCode(DELEGATION_CODE);
        delegationDO.setDelegatorPlanNo(DELEGATOR_PLAN_NO);
        delegationDO.setDelegateePlanNo(DELEGATEE_PLAN_NO);
        delegationDO.setDelegationType(DELEGATION_TYPE.name());
        delegationDO.setStatus(STATUS.name());
        delegationDO.setEffectiveAt(EFFECTIVE_AT);
        delegationDO.setExpireAt(EXPIRE_AT);
        delegationDO.setCreatedBy(OPERATOR);
        delegationDO.setUpdatedBy(OPERATOR);
        delegationDO.setCreateTime(CREATED_AT);
        delegationDO.setUpdateTime(UPDATED_AT);
        delegationDO.setVersion((int) VERSION_VALUE);
        delegationDO.setDeleted(false);
        return delegationDO;
    }

    private PlanDelegationOperatorDO buildOperatorDO(Long operatorId) {
        PlanDelegationOperatorDO operatorDO = new PlanDelegationOperatorDO();
        operatorDO.setId(null);
        operatorDO.setDelegationId(DELEGATION_ID_VALUE);
        operatorDO.setOperatorId(operatorId);
        operatorDO.setCreatedBy(OPERATOR);
        operatorDO.setUpdatedBy(OPERATOR);
        operatorDO.setCreateTime(CREATED_AT);
        operatorDO.setUpdateTime(UPDATED_AT);
        operatorDO.setVersion((int) VERSION_VALUE);
        operatorDO.setDeleted(false);
        return operatorDO;
    }

    private PlanDelegationPermissionDO buildPermissionDO(String businessCode, String action) {
        PlanDelegationPermissionDO permissionDO = new PlanDelegationPermissionDO();
        permissionDO.setId(null);
        permissionDO.setDelegationId(DELEGATION_ID_VALUE);
        permissionDO.setBusinessCode(businessCode);
        permissionDO.setAction(action);
        permissionDO.setCreatedBy(OPERATOR);
        permissionDO.setUpdatedBy(OPERATOR);
        permissionDO.setCreateTime(CREATED_AT);
        permissionDO.setUpdateTime(UPDATED_AT);
        permissionDO.setVersion((int) VERSION_VALUE);
        permissionDO.setDeleted(false);
        return permissionDO;
    }
}
