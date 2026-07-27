package com.example.iam.domain.authorization.aggregate.root;

import com.example.iam.domain.authorization.aggregate.valueobject.Action;
import com.example.iam.domain.authorization.aggregate.valueobject.BusinessCode;
import com.example.iam.domain.authorization.aggregate.valueobject.DelegationPermission;
import com.example.iam.domain.authorization.aggregate.valueobject.DelegationStatus;
import com.example.iam.domain.authorization.aggregate.valueobject.DelegationType;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionCode;
import com.example.iam.domain.authorization.event.PlanDelegationActivatedEvent;
import com.example.iam.domain.authorization.event.PlanDelegationCreatedEvent;
import com.example.iam.domain.authorization.event.PlanDelegationRevokedEvent;
import com.example.iam.types.PlanDelegationId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PlanDelegation 聚合根行为测试。
 *
 * <p>覆盖工厂方法、状态机(revoke/markExpired)、生效判断、授权判断、权限码生成、
 * reconstitute 重建、字段不可变性与空值/业务规则校验。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@DisplayName("PlanDelegation 聚合根行为")
class PlanDelegationTest {

  private static final UserNo OPERATOR = UserNo.of("U-ADMIN");
  private static final PlanDelegationId DELEGATION_ID = PlanDelegationId.of(6001L);
  private static final String DELEGATOR_PLAN_NO = "PLAN-A";
  private static final String DELEGATEE_PLAN_NO = "PLAN-B";
  private static final BusinessCode BUSINESS_CODE = BusinessCode.of("ANNUITY_ESTABLISH");
  private static final DelegationPermission PERMISSION_HANDLE =
      DelegationPermission.of(BUSINESS_CODE, Action.HANDLE);
  private static final DelegationPermission PERMISSION_QUERY =
      DelegationPermission.of(BUSINESS_CODE, Action.QUERY);

  @Nested
  @DisplayName("create 工厂方法")
  class CreateTest {

    @Test
    @DisplayName("create 初始化 ACTIVE 状态并注册 Created 与 Activated 事件")
    void create_initializesActiveStatusAndRegistersEvents() {
      LocalDateTime before = LocalDateTime.now();

      PlanDelegation delegation = createDefaultDelegation();

      assertThat(delegation.id()).isEqualTo(DELEGATION_ID);
      assertThat(delegation.delegationCode()).isEqualTo("DLG-001");
      assertThat(delegation.delegatorPlanNo()).isEqualTo(DELEGATOR_PLAN_NO);
      assertThat(delegation.delegateePlanNo()).isEqualTo(DELEGATEE_PLAN_NO);
      assertThat(delegation.delegationType()).isEqualTo(DelegationType.ALL_OPERATORS);
      assertThat(delegation.designatedOperators()).isEmpty();
      assertThat(delegation.delegatedPermissions()).containsExactly(PERMISSION_HANDLE);
      assertThat(delegation.status()).isEqualTo(DelegationStatus.ACTIVE);
      assertThat(delegation.effectiveAt()).isAfterOrEqualTo(before);
      assertThat(delegation.expireAt()).isNull();
      assertThat(delegation.createdBy()).isEqualTo(OPERATOR);
      assertThat(delegation.version()).isEqualTo(Version.initial());

      assertThat(delegation.getDomainEvents())
          .hasSize(2)
          .satisfies(events -> {
            assertThat(events).first().isInstanceOf(PlanDelegationCreatedEvent.class);
            assertThat(events).last().isInstanceOf(PlanDelegationActivatedEvent.class);
          });
      PlanDelegationCreatedEvent createdEvent = (PlanDelegationCreatedEvent) delegation.getDomainEvents().get(0);
      assertThat(createdEvent.delegationId()).isEqualTo(DELEGATION_ID);
      assertThat(createdEvent.delegationCode()).isEqualTo("DLG-001");
      assertThat(createdEvent.delegatorPlanNo()).isEqualTo(DELEGATOR_PLAN_NO);
      assertThat(createdEvent.delegateePlanNo()).isEqualTo(DELEGATEE_PLAN_NO);
      assertThat(createdEvent.delegationType()).isEqualTo(DelegationType.ALL_OPERATORS);
      PlanDelegationActivatedEvent activatedEvent =
          (PlanDelegationActivatedEvent) delegation.getDomainEvents().get(1);
      assertThat(activatedEvent.delegateePlanNo()).isEqualTo(DELEGATEE_PLAN_NO);
    }

    @Test
    @DisplayName("create 在 SPECIFIC_OPERATORS 类型时使用指定操作员")
    void create_usesDesignatedOperatorsForSpecificType() {
      Set<Long> operators = Set.of(1001L, 1002L);

      PlanDelegation delegation = PlanDelegation.create(
          DELEGATION_ID, "DLG-001",
          DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
          DelegationType.SPECIFIC_OPERATORS, operators,
          Set.of(PERMISSION_HANDLE),
          null, null, OPERATOR
      );

      assertThat(delegation.designatedOperators()).containsExactlyInAnyOrder(1001L, 1002L);
    }

    @Test
    @DisplayName("create 在 ALL_OPERATORS 类型时忽略指定操作员")
    void create_ignoresOperatorsForAllOperatorsType() {
      PlanDelegation delegation = PlanDelegation.create(
          DELEGATION_ID, "DLG-001",
          DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
          DelegationType.ALL_OPERATORS, Set.of(1001L),
          Set.of(PERMISSION_HANDLE),
          null, null, OPERATOR
      );

      assertThat(delegation.designatedOperators()).isEmpty();
    }

    @Test
    @DisplayName("create 在 effectiveAt 显式指定时使用指定值")
    void create_usesExplicitEffectiveAt() {
      LocalDateTime effective = LocalDateTime.of(2026, 8, 1, 0, 0);

      PlanDelegation delegation = PlanDelegation.create(
          DELEGATION_ID, "DLG-001",
          DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
          DelegationType.ALL_OPERATORS, Set.of(),
          Set.of(PERMISSION_HANDLE),
          effective, null, OPERATOR
      );

      assertThat(delegation.effectiveAt()).isEqualTo(effective);
    }

    @Test
    @DisplayName("create 拒绝空 delegationCode")
    void create_rejectsBlankDelegationCode() {
      assertThatThrownBy(() -> PlanDelegation.create(
          DELEGATION_ID, "",
          DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
          DelegationType.ALL_OPERATORS, Set.of(),
          Set.of(PERMISSION_HANDLE),
          null, null, OPERATOR
      )).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("create 拒绝空 delegatorPlanNo")
    void create_rejectsBlankDelegatorPlanNo() {
      assertThatThrownBy(() -> PlanDelegation.create(
          DELEGATION_ID, "DLG-001",
          "", DELEGATEE_PLAN_NO,
          DelegationType.ALL_OPERATORS, Set.of(),
          Set.of(PERMISSION_HANDLE),
          null, null, OPERATOR
      )).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("create 拒绝空 delegateePlanNo")
    void create_rejectsBlankDelegateePlanNo() {
      assertThatThrownBy(() -> PlanDelegation.create(
          DELEGATION_ID, "DLG-001",
          DELEGATOR_PLAN_NO, "  ",
          DelegationType.ALL_OPERATORS, Set.of(),
          Set.of(PERMISSION_HANDLE),
          null, null, OPERATOR
      )).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("create 拒绝授权方和被授权方相同")
    void create_rejectsSelfDelegation() {
      assertThatThrownBy(() -> PlanDelegation.create(
          DELEGATION_ID, "DLG-001",
          DELEGATOR_PLAN_NO, DELEGATOR_PLAN_NO,
          DelegationType.ALL_OPERATORS, Set.of(),
          Set.of(PERMISSION_HANDLE),
          null, null, OPERATOR
      )).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("create 拒绝空 delegationType")
    void create_rejectsNullDelegationType() {
      assertThatThrownBy(() -> PlanDelegation.create(
          DELEGATION_ID, "DLG-001",
          DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
          null, Set.of(),
          Set.of(PERMISSION_HANDLE),
          null, null, OPERATOR
      )).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("create 在 SPECIFIC_OPERATORS 类型时拒绝空操作员列表")
    void create_rejectsEmptyOperatorsForSpecificType() {
      assertThatThrownBy(() -> PlanDelegation.create(
          DELEGATION_ID, "DLG-001",
          DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
          DelegationType.SPECIFIC_OPERATORS, Set.of(),
          Set.of(PERMISSION_HANDLE),
          null, null, OPERATOR
      )).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("create 拒绝空 delegatedPermissions 集合")
    void create_rejectsEmptyPermissions() {
      assertThatThrownBy(() -> PlanDelegation.create(
          DELEGATION_ID, "DLG-001",
          DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
          DelegationType.ALL_OPERATORS, Set.of(),
          Set.of(),
          null, null, OPERATOR
      )).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("create 拒绝空 delegatedPermissions 引用")
    void create_rejectsNullPermissions() {
      assertThatThrownBy(() -> PlanDelegation.create(
          DELEGATION_ID, "DLG-001",
          DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
          DelegationType.ALL_OPERATORS, Set.of(),
          null,
          null, null, OPERATOR
      )).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("create 拒绝 expireAt 不晚于 effectiveAt")
    void create_rejectsInvalidPeriod() {
      LocalDateTime effective = LocalDateTime.of(2026, 7, 26, 9, 0);
      LocalDateTime expire = LocalDateTime.of(2026, 7, 26, 8, 0);
      assertThatThrownBy(() -> PlanDelegation.create(
          DELEGATION_ID, "DLG-001",
          DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
          DelegationType.ALL_OPERATORS, Set.of(),
          Set.of(PERMISSION_HANDLE),
          effective, expire, OPERATOR
      )).isInstanceOf(DomainException.class);
    }
  }

  @Nested
  @DisplayName("revoke/markExpired 状态机")
  class StateMachineTest {

    @Test
    @DisplayName("revoke 将 ACTIVE 状态置为 REVOKED 并注册事件")
    void revoke_changesActiveToRevoked() {
      PlanDelegation delegation = createDefaultDelegation();
      Version initialVersion = delegation.version();

      delegation.revoke(OPERATOR, "业务调整");

      assertThat(delegation.status()).isEqualTo(DelegationStatus.REVOKED);
      assertThat(delegation.version()).isEqualTo(initialVersion.next());
      assertThat(delegation.updatedBy()).isEqualTo(OPERATOR);
      assertThat(delegation.getDomainEvents())
          .filteredOn(e -> e instanceof PlanDelegationRevokedEvent)
          .hasSize(1);
      PlanDelegationRevokedEvent event = (PlanDelegationRevokedEvent)
          delegation.getDomainEvents().stream()
              .filter(e -> e instanceof PlanDelegationRevokedEvent)
              .findFirst().orElseThrow();
      assertThat(event.delegationId()).isEqualTo(DELEGATION_ID);
      assertThat(event.delegationCode()).isEqualTo("DLG-001");
      assertThat(event.reason()).isEqualTo("业务调整");
    }

    @Test
    @DisplayName("revoke 在 REVOKED 终态时抛 DomainException")
    void revoke_throwsWhenAlreadyRevoked() {
      PlanDelegation delegation = createDefaultDelegation();
      delegation.revoke(OPERATOR, "首次撤销");

      assertThatThrownBy(() -> delegation.revoke(OPERATOR, "再次撤销"))
          .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("revoke 在 EXPIRED 终态时抛 DomainException")
    void revoke_throwsWhenExpired() {
      PlanDelegation delegation = createDefaultDelegation();
      delegation.markExpired(OPERATOR);

      assertThatThrownBy(() -> delegation.revoke(OPERATOR, "尝试撤销"))
          .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("revoke 接受空原因")
    void revoke_acceptsNullReason() {
      PlanDelegation delegation = createDefaultDelegation();

      delegation.revoke(OPERATOR, null);

      assertThat(delegation.status()).isEqualTo(DelegationStatus.REVOKED);
    }

    @Test
    @DisplayName("markExpired 将 ACTIVE 状态置为 EXPIRED")
    void markExpired_changesActiveToExpired() {
      PlanDelegation delegation = createDefaultDelegation();

      delegation.markExpired(OPERATOR);

      assertThat(delegation.status()).isEqualTo(DelegationStatus.EXPIRED);
    }

    @Test
    @DisplayName("markExpired 在 REVOKED 终态时抛 DomainException")
    void markExpired_throwsWhenRevoked() {
      PlanDelegation delegation = createDefaultDelegation();
      delegation.revoke(OPERATOR, "撤销");

      assertThatThrownBy(() -> delegation.markExpired(OPERATOR))
          .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("markExpired 在 EXPIRED 终态时抛 DomainException")
    void markExpired_throwsWhenAlreadyExpired() {
      PlanDelegation delegation = createDefaultDelegation();
      delegation.markExpired(OPERATOR);

      assertThatThrownBy(() -> delegation.markExpired(OPERATOR))
          .isInstanceOf(DomainException.class);
    }
  }

  @Nested
  @DisplayName("isEffectiveAt 生效判断")
  class IsEffectiveAtTest {

    @Test
    @DisplayName("isEffectiveAt 在 ACTIVE 且时间窗口内返回 true")
    void isEffectiveAt_returnsTrueWhenActiveAndWithinWindow() {
      LocalDateTime effective = LocalDateTime.now().minusDays(1);
      LocalDateTime expire = LocalDateTime.now().plusDays(1);
      PlanDelegation delegation = createDelegationWithPeriod(effective, expire);

      assertThat(delegation.isEffectiveAt(LocalDateTime.now())).isTrue();
    }

    @Test
    @DisplayName("isEffectiveAt 在生效时间之前返回 false")
    void isEffectiveAt_returnsFalseBeforeEffective() {
      LocalDateTime effective = LocalDateTime.now().plusDays(1);
      LocalDateTime expire = LocalDateTime.now().plusDays(2);
      PlanDelegation delegation = createDelegationWithPeriod(effective, expire);

      assertThat(delegation.isEffectiveAt(LocalDateTime.now())).isFalse();
    }

    @Test
    @DisplayName("isEffectiveAt 在失效时间之后返回 false")
    void isEffectiveAt_returnsFalseAfterExpire() {
      LocalDateTime effective = LocalDateTime.now().minusDays(2);
      LocalDateTime expire = LocalDateTime.now().minusDays(1);
      PlanDelegation delegation = createDelegationWithPeriod(effective, expire);

      assertThat(delegation.isEffectiveAt(LocalDateTime.now())).isFalse();
    }

    @Test
    @DisplayName("isEffectiveAt 在 REVOKED 状态时返回 false")
    void isEffectiveAt_returnsFalseWhenRevoked() {
      PlanDelegation delegation = createDefaultDelegation();
      delegation.revoke(OPERATOR, "撤销");

      assertThat(delegation.isEffectiveAt(LocalDateTime.now())).isFalse();
    }

    @Test
    @DisplayName("isEffectiveAt 在 EXPIRED 状态时返回 false")
    void isEffectiveAt_returnsFalseWhenExpired() {
      PlanDelegation delegation = createDefaultDelegation();
      delegation.markExpired(OPERATOR);

      assertThat(delegation.isEffectiveAt(LocalDateTime.now())).isFalse();
    }

    @Test
    @DisplayName("isEffectiveAt 在 expireAt 为 null 时永久生效")
    void isEffectiveAt_returnsTrueWhenPermanent() {
      PlanDelegation delegation = createDefaultDelegation();

      assertThat(delegation.isEffectiveAt(LocalDateTime.now().plusYears(10))).isTrue();
    }
  }

  @Nested
  @DisplayName("authorizes 授权判断")
  class AuthorizesTest {

    @Test
    @DisplayName("authorizes 在 ALL_OPERATORS 类型时对所有操作员返回 true")
    void authorizes_returnsTrueForAllOperatorsType() {
      PlanDelegation delegation = createDefaultDelegation();

      assertThat(delegation.authorizes(1001L)).isTrue();
      assertThat(delegation.authorizes(9999L)).isTrue();
    }

    @Test
    @DisplayName("authorizes 在 SPECIFIC_OPERATORS 类型时仅对指定操作员返回 true")
    void authorizes_returnsTrueOnlyForDesignatedOperators() {
      PlanDelegation delegation = createSpecificDelegation(Set.of(1001L, 1002L));

      assertThat(delegation.authorizes(1001L)).isTrue();
      assertThat(delegation.authorizes(1002L)).isTrue();
      assertThat(delegation.authorizes(9999L)).isFalse();
    }

    @Test
    @DisplayName("authorizes 拒绝空 operatorId")
    void authorizes_rejectsNullOperatorId() {
      PlanDelegation delegation = createDefaultDelegation();

      assertThatThrownBy(() -> delegation.authorizes(null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("permissionCodesFor 权限码生成")
  class PermissionCodesForTest {

    @Test
    @DisplayName("permissionCodesFor 在授权时返回所有权限码")
    void permissionCodesFor_returnsAllCodesWhenAuthorized() {
      PlanDelegation delegation = PlanDelegation.create(
          DELEGATION_ID, "DLG-001",
          DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
          DelegationType.ALL_OPERATORS, Set.of(),
          Set.of(PERMISSION_HANDLE, PERMISSION_QUERY),
          null, null, OPERATOR
      );

      Set<PermissionCode> codes = delegation.permissionCodesFor(1001L);

      assertThat(codes).containsExactlyInAnyOrder(
          PermissionCode.of("ANNUITY_ESTABLISH.HANDLE"),
          PermissionCode.of("ANNUITY_ESTABLISH.QUERY")
      );
    }

    @Test
    @DisplayName("permissionCodesFor 在未授权时返回空集合")
    void permissionCodesFor_returnsEmptyWhenNotAuthorized() {
      PlanDelegation delegation = createSpecificDelegation(Set.of(1001L));

      Set<PermissionCode> codes = delegation.permissionCodesFor(9999L);

      assertThat(codes).isEmpty();
    }

    @Test
    @DisplayName("permissionCodesFor 在指定业务时按业务过滤")
    void permissionCodesFor_filtersByBusinessCode() {
      BusinessCode otherBusiness = BusinessCode.of("ANNUITY_PAYMENT");
      DelegationPermission otherPermission = DelegationPermission.of(otherBusiness, Action.HANDLE);
      PlanDelegation delegation = PlanDelegation.create(
          DELEGATION_ID, "DLG-001",
          DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
          DelegationType.ALL_OPERATORS, Set.of(),
          Set.of(PERMISSION_HANDLE, otherPermission),
          null, null, OPERATOR
      );

      Set<PermissionCode> codes = delegation.permissionCodesFor(1001L, BUSINESS_CODE);

      assertThat(codes).containsExactly(PermissionCode.of("ANNUITY_ESTABLISH.HANDLE"));
    }

    @Test
    @DisplayName("permissionCodesFor 返回不可变集合")
    void permissionCodesFor_returnsImmutableSet() {
      PlanDelegation delegation = createDefaultDelegation();

      Set<PermissionCode> codes = delegation.permissionCodesFor(1001L);

      assertThatThrownBy(() -> codes.add(PermissionCode.of("X.Y")))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("permissionCodesFor 拒绝空 operatorId")
    void permissionCodesFor_rejectsNullOperatorId() {
      PlanDelegation delegation = createDefaultDelegation();

      assertThatThrownBy(() -> delegation.permissionCodesFor(null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("permissionCodesFor(双参) 拒绝空 businessCode")
    void permissionCodesFor_rejectsNullBusinessCode() {
      PlanDelegation delegation = createDefaultDelegation();

      assertThatThrownBy(() -> delegation.permissionCodesFor(1001L, null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("containsPermission 权限包含判断")
  class ContainsPermissionTest {

    @Test
    @DisplayName("containsPermission 在包含指定业务+动作时返回 true")
    void containsPermission_returnsTrueWhenMatched() {
      PlanDelegation delegation = createDefaultDelegation();

      assertThat(delegation.containsPermission(BUSINESS_CODE, Action.HANDLE)).isTrue();
    }

    @Test
    @DisplayName("containsPermission 在不包含时返回 false")
    void containsPermission_returnsFalseWhenNotMatched() {
      PlanDelegation delegation = createDefaultDelegation();

      assertThat(delegation.containsPermission(BUSINESS_CODE, Action.QUERY)).isFalse();
    }

    @Test
    @DisplayName("containsPermission 拒绝空 businessCode")
    void containsPermission_rejectsNullBusinessCode() {
      PlanDelegation delegation = createDefaultDelegation();

      assertThatThrownBy(() -> delegation.containsPermission(null, Action.HANDLE))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("containsPermission 拒绝空 action")
    void containsPermission_rejectsNullAction() {
      PlanDelegation delegation = createDefaultDelegation();

      assertThatThrownBy(() -> delegation.containsPermission(BUSINESS_CODE, null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("reconstitute 数据库重建")
  class ReconstituteTest {

    @Test
    @DisplayName("reconstitute 恢复完整聚合状态")
    void reconstitute_restoresFullAggregate() {
      LocalDateTime createdAt = LocalDateTime.of(2026, 7, 1, 9, 0);
      LocalDateTime updatedAt = LocalDateTime.of(2026, 7, 26, 14, 30);
      LocalDateTime effective = LocalDateTime.of(2026, 7, 1, 0, 0);
      LocalDateTime expire = LocalDateTime.of(2027, 1, 1, 0, 0);
      Version version = Version.of(5);

      PlanDelegation delegation = PlanDelegation.reconstitute(
          DELEGATION_ID, "DLG-001",
          DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
          DelegationType.SPECIFIC_OPERATORS, Set.of(1001L, 1002L),
          Set.of(PERMISSION_HANDLE, PERMISSION_QUERY),
          DelegationStatus.ACTIVE, effective, expire,
          OPERATOR, OPERATOR, createdAt, updatedAt, version
      );

      assertThat(delegation.delegationCode()).isEqualTo("DLG-001");
      assertThat(delegation.delegatorPlanNo()).isEqualTo(DELEGATOR_PLAN_NO);
      assertThat(delegation.delegateePlanNo()).isEqualTo(DELEGATEE_PLAN_NO);
      assertThat(delegation.delegationType()).isEqualTo(DelegationType.SPECIFIC_OPERATORS);
      assertThat(delegation.designatedOperators()).containsExactlyInAnyOrder(1001L, 1002L);
      assertThat(delegation.delegatedPermissions()).containsExactlyInAnyOrder(PERMISSION_HANDLE, PERMISSION_QUERY);
      assertThat(delegation.status()).isEqualTo(DelegationStatus.ACTIVE);
      assertThat(delegation.effectiveAt()).isEqualTo(effective);
      assertThat(delegation.expireAt()).isEqualTo(expire);
      assertThat(delegation.createdBy()).isEqualTo(OPERATOR);
      assertThat(delegation.updatedBy()).isEqualTo(OPERATOR);
      assertThat(delegation.createdAt()).isEqualTo(createdAt);
      assertThat(delegation.updatedAt()).isEqualTo(updatedAt);
      assertThat(delegation.version()).isEqualTo(version);
    }

    @Test
    @DisplayName("reconstitute 不注册领域事件")
    void reconstitute_doesNotRegisterEvents() {
      PlanDelegation delegation = createReconstitutedDelegation();

      assertThat(delegation.getDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("reconstitute 拒绝授权方和被授权方相同")
    void reconstitute_rejectsSelfDelegation() {
      assertThatThrownBy(() -> PlanDelegation.reconstitute(
          DELEGATION_ID, "DLG-001",
          DELEGATOR_PLAN_NO, DELEGATOR_PLAN_NO,
          DelegationType.ALL_OPERATORS, Set.of(),
          Set.of(PERMISSION_HANDLE),
          DelegationStatus.ACTIVE, LocalDateTime.now(), null,
          OPERATOR, OPERATOR, LocalDateTime.now(), LocalDateTime.now(), Version.initial()
      )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("reconstitute 拒绝空 status")
    void reconstitute_rejectsNullStatus() {
      assertThatThrownBy(() -> PlanDelegation.reconstitute(
          DELEGATION_ID, "DLG-001",
          DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
          DelegationType.ALL_OPERATORS, Set.of(),
          Set.of(PERMISSION_HANDLE),
          null, LocalDateTime.now(), null,
          OPERATOR, OPERATOR, LocalDateTime.now(), LocalDateTime.now(), Version.initial()
      )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("reconstitute 拒绝空 effectiveAt")
    void reconstitute_rejectsNullEffectiveAt() {
      assertThatThrownBy(() -> PlanDelegation.reconstitute(
          DELEGATION_ID, "DLG-001",
          DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
          DelegationType.ALL_OPERATORS, Set.of(),
          Set.of(PERMISSION_HANDLE),
          DelegationStatus.ACTIVE, null, null,
          OPERATOR, OPERATOR, LocalDateTime.now(), LocalDateTime.now(), Version.initial()
      )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("reconstitute 拒绝 SPECIFIC_OPERATORS 类型空操作员列表")
    void reconstitute_rejectsEmptyOperatorsForSpecificType() {
      assertThatThrownBy(() -> PlanDelegation.reconstitute(
          DELEGATION_ID, "DLG-001",
          DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
          DelegationType.SPECIFIC_OPERATORS, Set.of(),
          Set.of(PERMISSION_HANDLE),
          DelegationStatus.ACTIVE, LocalDateTime.now(), null,
          OPERATOR, OPERATOR, LocalDateTime.now(), LocalDateTime.now(), Version.initial()
      )).isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  @DisplayName("字段不可变性")
  class ImmutabilityTest {

    @Test
    @DisplayName("designatedOperators 返回不可变集合")
    void designatedOperators_returnsImmutableSet() {
      PlanDelegation delegation = createSpecificDelegation(Set.of(1001L));

      Set<Long> operators = delegation.designatedOperators();

      assertThatThrownBy(() -> operators.add(2002L))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("designatedOperators 内部状态不受外部集合修改影响")
    void designatedOperators_isolatedFromExternalMutations() {
      Set<Long> source = new HashSet<>();
      source.add(1001L);
      PlanDelegation delegation = PlanDelegation.create(
          DELEGATION_ID, "DLG-001",
          DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
          DelegationType.SPECIFIC_OPERATORS, source,
          Set.of(PERMISSION_HANDLE),
          null, null, OPERATOR
      );
      source.add(9999L);

      assertThat(delegation.designatedOperators()).containsExactly(1001L);
    }

    @Test
    @DisplayName("delegatedPermissions 返回不可变集合")
    void delegatedPermissions_returnsImmutableSet() {
      PlanDelegation delegation = createDefaultDelegation();

      Set<DelegationPermission> permissions = delegation.delegatedPermissions();

      assertThatThrownBy(() -> permissions.add(PERMISSION_QUERY))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("delegatedPermissions 内部状态不受外部集合修改影响")
    void delegatedPermissions_isolatedFromExternalMutations() {
      Set<DelegationPermission> source = new HashSet<>();
      source.add(PERMISSION_HANDLE);
      PlanDelegation delegation = PlanDelegation.create(
          DELEGATION_ID, "DLG-001",
          DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
          DelegationType.ALL_OPERATORS, Set.of(),
          source,
          null, null, OPERATOR
      );
      source.add(PERMISSION_QUERY);

      assertThat(delegation.delegatedPermissions()).containsExactly(PERMISSION_HANDLE);
    }
  }

  private PlanDelegation createDefaultDelegation() {
    return PlanDelegation.create(
        DELEGATION_ID, "DLG-001",
        DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
        DelegationType.ALL_OPERATORS, Set.of(),
        Set.of(PERMISSION_HANDLE),
        null, null, OPERATOR
    );
  }

  private PlanDelegation createSpecificDelegation(Set<Long> operators) {
    return PlanDelegation.create(
        DELEGATION_ID, "DLG-001",
        DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
        DelegationType.SPECIFIC_OPERATORS, operators,
        Set.of(PERMISSION_HANDLE),
        null, null, OPERATOR
    );
  }

  private PlanDelegation createDelegationWithPeriod(LocalDateTime effective, LocalDateTime expire) {
    return PlanDelegation.create(
        DELEGATION_ID, "DLG-001",
        DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
        DelegationType.ALL_OPERATORS, Set.of(),
        Set.of(PERMISSION_HANDLE),
        effective, expire, OPERATOR
    );
  }

  private PlanDelegation createReconstitutedDelegation() {
    return PlanDelegation.reconstitute(
        DELEGATION_ID, "DLG-001",
        DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
        DelegationType.ALL_OPERATORS, Set.of(),
        Set.of(PERMISSION_HANDLE),
        DelegationStatus.ACTIVE, LocalDateTime.now(), null,
        OPERATOR, OPERATOR, LocalDateTime.now(), LocalDateTime.now(), Version.initial()
    );
  }
}
