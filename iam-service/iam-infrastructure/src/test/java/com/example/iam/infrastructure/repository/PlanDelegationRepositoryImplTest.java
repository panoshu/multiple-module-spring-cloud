package com.example.iam.infrastructure.repository;

import com.example.iam.domain.authorization.aggregate.root.PlanDelegation;
import com.example.iam.domain.authorization.aggregate.valueobject.Action;
import com.example.iam.domain.authorization.aggregate.valueobject.BusinessCode;
import com.example.iam.domain.authorization.aggregate.valueobject.DelegationPermission;
import com.example.iam.domain.authorization.aggregate.valueobject.DelegationStatus;
import com.example.iam.domain.authorization.aggregate.valueobject.DelegationType;
import com.example.iam.domain.authorization.repository.PlanDelegationRepository;
import com.example.iam.infrastructure.IamInfrastructureTestApplication;
import com.example.iam.types.PlanDelegationId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PlanDelegationRepositoryImpl} 集成测试。
 *
 * <p>验证计划代办关系聚合根(含 operator/permission 子表)的 CRUD、
 * 按代办编码查询、按授权方/被授权方查询生效关系、子表全量替换等场景。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@DisplayName("PlanDelegationRepositoryImpl 集成测试")
@SpringBootTest(classes = IamInfrastructureTestApplication.class)
@ActiveProfiles("test")
@Transactional
class PlanDelegationRepositoryImplTest {

    @Autowired
    private PlanDelegationRepository delegationRepository;

    private static final Long DELEGATION_ID_VALUE = 60001L;
    private static final String DELEGATION_CODE = "DLG-001";
    private static final String DELEGATOR_PLAN_NO = "PLAN-A";
    private static final String DELEGATEE_PLAN_NO = "PLAN-B";
    private static final Set<Long> DESIGNATED_OPERATORS = Set.of(70001L, 70002L);
    private static final Set<DelegationPermission> PERMISSIONS = Set.of(
            DelegationPermission.of(BusinessCode.of("ANNUITY_ESTABLISH"), Action.HANDLE),
            DelegationPermission.of(BusinessCode.of("ANNUITY_ESTABLISH"), Action.QUERY));
    private static final UserNo OPERATOR = UserNo.of("U-ADMIN-001");

    @Nested
    @DisplayName("save + load: 新建与读取")
    class SaveAndLoadTest {

        @Test
        @DisplayName("新建 SPECIFIC_OPERATORS 代办关系后能通过 ID 加载,含子表数据")
        void shouldSaveNewDelegationAndLoadById() {
            PlanDelegation delegation = PlanDelegation.create(
                    PlanDelegationId.of(DELEGATION_ID_VALUE), DELEGATION_CODE,
                    DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
                    DelegationType.SPECIFIC_OPERATORS, DESIGNATED_OPERATORS,
                    PERMISSIONS,
                    LocalDateTime.now().minusMinutes(1), null, OPERATOR);

            delegationRepository.save(delegation);

            Optional<PlanDelegation> loaded = delegationRepository
                    .load(PlanDelegationId.of(DELEGATION_ID_VALUE));

            assertThat(loaded).isPresent();
            PlanDelegation actual = loaded.get();
            assertThat(actual.id().value()).isEqualTo(DELEGATION_ID_VALUE);
            assertThat(actual.delegationCode()).isEqualTo(DELEGATION_CODE);
            assertThat(actual.delegatorPlanNo()).isEqualTo(DELEGATOR_PLAN_NO);
            assertThat(actual.delegateePlanNo()).isEqualTo(DELEGATEE_PLAN_NO);
            assertThat(actual.delegationType()).isEqualTo(DelegationType.SPECIFIC_OPERATORS);
            assertThat(actual.status()).isEqualTo(DelegationStatus.ACTIVE);
            assertThat(actual.designatedOperators())
                    .containsExactlyInAnyOrderElementsOf(DESIGNATED_OPERATORS);
            assertThat(actual.delegatedPermissions())
                    .containsExactlyInAnyOrderElementsOf(PERMISSIONS);
        }

        @Test
        @DisplayName("新建 ALL_OPERATORS 代办关系时 designatedOperators 为空")
        void shouldSaveAllOperatorsDelegation() {
            PlanDelegation delegation = PlanDelegation.create(
                    PlanDelegationId.of(DELEGATION_ID_VALUE), DELEGATION_CODE,
                    DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
                    DelegationType.ALL_OPERATORS, Set.of(),
                    PERMISSIONS,
                    LocalDateTime.now().minusMinutes(1), null, OPERATOR);

            delegationRepository.save(delegation);

            Optional<PlanDelegation> loaded = delegationRepository
                    .load(PlanDelegationId.of(DELEGATION_ID_VALUE));

            assertThat(loaded).isPresent();
            assertThat(loaded.get().delegationType()).isEqualTo(DelegationType.ALL_OPERATORS);
            assertThat(loaded.get().designatedOperators()).isEmpty();
            assertThat(loaded.get().delegatedPermissions()).hasSize(PERMISSIONS.size());
        }

        @Test
        @DisplayName("load 不存在的 ID 返回 empty")
        void shouldReturnEmptyWhenLoadNonexistentId() {
            Optional<PlanDelegation> loaded = delegationRepository
                    .load(PlanDelegationId.of(999999L));

            assertThat(loaded).isEmpty();
        }
    }

    @Nested
    @DisplayName("save: 子表全量替换")
    class UpdateChildrenTest {

        @Test
        @DisplayName("更新 designatedOperators 子表后,数据库与领域对象一致")
        void shouldReplaceOperatorsOnUpdate() {
            PlanDelegation delegation = PlanDelegation.create(
                    PlanDelegationId.of(DELEGATION_ID_VALUE), DELEGATION_CODE,
                    DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
                    DelegationType.SPECIFIC_OPERATORS, Set.of(70001L),
                    PERMISSIONS,
                    LocalDateTime.now().minusMinutes(1), null, OPERATOR);
            delegationRepository.save(delegation);

            PlanDelegation reloaded = delegationRepository
                    .load(PlanDelegationId.of(DELEGATION_ID_VALUE)).orElseThrow();
            reloaded.revoke(OPERATOR, "测试撤销后重建");
            delegationRepository.save(reloaded);

            PlanDelegation newDelegation = PlanDelegation.create(
                    PlanDelegationId.of(60002L), "DLG-002",
                    DELEGATOR_PLAN_NO, "PLAN-C",
                    DelegationType.SPECIFIC_OPERATORS, Set.of(70001L, 70003L),
                    PERMISSIONS,
                    LocalDateTime.now().minusMinutes(1), null, OPERATOR);
            delegationRepository.save(newDelegation);

            Optional<PlanDelegation> second = delegationRepository
                    .load(PlanDelegationId.of(60002L));
            assertThat(second).isPresent();
            assertThat(second.get().designatedOperators())
                    .containsExactlyInAnyOrder(70001L, 70003L);
        }
    }

    @Nested
    @DisplayName("findByDelegationCode: 按代办编码查询")
    class FindByDelegationCodeTest {

        @Test
        @DisplayName("按 delegationCode 命中已存在代办关系")
        void shouldFindByDelegationCode() {
            PlanDelegation delegation = PlanDelegation.create(
                    PlanDelegationId.of(DELEGATION_ID_VALUE), DELEGATION_CODE,
                    DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
                    DelegationType.ALL_OPERATORS, Set.of(),
                    PERMISSIONS,
                    LocalDateTime.now().minusMinutes(1), null, OPERATOR);
            delegationRepository.save(delegation);

            Optional<PlanDelegation> found = delegationRepository.findByDelegationCode(DELEGATION_CODE);

            assertThat(found).isPresent();
            assertThat(found.get().delegationCode()).isEqualTo(DELEGATION_CODE);
            assertThat(found.get().delegatedPermissions()).hasSize(PERMISSIONS.size());
        }

        @Test
        @DisplayName("existsByDelegationCode: 存在返回 true,不存在返回 false")
        void shouldCheckExistenceByDelegationCode() {
            PlanDelegation delegation = PlanDelegation.create(
                    PlanDelegationId.of(DELEGATION_ID_VALUE), DELEGATION_CODE,
                    DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
                    DelegationType.ALL_OPERATORS, Set.of(),
                    PERMISSIONS,
                    LocalDateTime.now().minusMinutes(1), null, OPERATOR);
            delegationRepository.save(delegation);

            assertThat(delegationRepository.existsByDelegationCode(DELEGATION_CODE)).isTrue();
            assertThat(delegationRepository.existsByDelegationCode("NONEXISTENT")).isFalse();
        }
    }

    @Nested
    @DisplayName("findEffectiveByDelegatee: 按被授权方查询生效关系")
    class FindEffectiveByDelegateeTest {

        @Test
        @DisplayName("返回被授权方 ACTIVE 且生效的代办关系")
        void shouldFindEffectiveByDelegatee() {
            PlanDelegation delegation = PlanDelegation.create(
                    PlanDelegationId.of(DELEGATION_ID_VALUE), DELEGATION_CODE,
                    DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
                    DelegationType.ALL_OPERATORS, Set.of(),
                    PERMISSIONS,
                    LocalDateTime.now().minusMinutes(1), null, OPERATOR);
            delegationRepository.save(delegation);

            List<PlanDelegation> effective = delegationRepository
                    .findEffectiveByDelegatee(DELEGATEE_PLAN_NO);

            assertThat(effective).hasSize(1);
            assertThat(effective.get(0).delegateePlanNo()).isEqualTo(DELEGATEE_PLAN_NO);
            assertThat(effective.get(0).status()).isEqualTo(DelegationStatus.ACTIVE);
        }

        @Test
        @DisplayName("已撤销的代办关系不被返回")
        void shouldNotReturnRevokedDelegation() {
            PlanDelegation delegation = PlanDelegation.create(
                    PlanDelegationId.of(DELEGATION_ID_VALUE), DELEGATION_CODE,
                    DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
                    DelegationType.ALL_OPERATORS, Set.of(),
                    PERMISSIONS,
                    LocalDateTime.now().minusMinutes(1), null, OPERATOR);
            delegation.revoke(OPERATOR, "测试撤销");
            delegationRepository.save(delegation);

            List<PlanDelegation> effective = delegationRepository
                    .findEffectiveByDelegatee(DELEGATEE_PLAN_NO);

            assertThat(effective).isEmpty();
        }
    }

    @Nested
    @DisplayName("delete: 软删除(含子表)")
    class DeleteTest {

        @Test
        @DisplayName("delete 后 load 返回 empty(含子表清理)")
        void shouldSoftDeleteDelegationAndChildren() {
            PlanDelegation delegation = PlanDelegation.create(
                    PlanDelegationId.of(DELEGATION_ID_VALUE), DELEGATION_CODE,
                    DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
                    DelegationType.SPECIFIC_OPERATORS, DESIGNATED_OPERATORS,
                    PERMISSIONS,
                    LocalDateTime.now().minusMinutes(1), null, OPERATOR);
            delegationRepository.save(delegation);

            delegationRepository.delete(delegation);

            assertThat(delegationRepository.load(PlanDelegationId.of(DELEGATION_ID_VALUE))).isEmpty();
        }

        @Test
        @DisplayName("deleteById 后 load 返回 empty")
        void shouldSoftDeleteById() {
            PlanDelegation delegation = PlanDelegation.create(
                    PlanDelegationId.of(DELEGATION_ID_VALUE), DELEGATION_CODE,
                    DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
                    DelegationType.SPECIFIC_OPERATORS, DESIGNATED_OPERATORS,
                    PERMISSIONS,
                    LocalDateTime.now().minusMinutes(1), null, OPERATOR);
            delegationRepository.save(delegation);

            delegationRepository.deleteById(PlanDelegationId.of(DELEGATION_ID_VALUE));

            assertThat(delegationRepository.load(PlanDelegationId.of(DELEGATION_ID_VALUE))).isEmpty();
            assertThat(delegationRepository.existsByDelegationCode(DELEGATION_CODE)).isFalse();
        }

        @Test
        @DisplayName("delete null 代办关系不抛异常")
        void shouldNotThrowWhenDeleteNull() {
            delegationRepository.delete(null);
            delegationRepository.deleteById(null);
        }
    }
}
