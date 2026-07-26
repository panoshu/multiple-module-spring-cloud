package com.example.iam.domain.authorization.service;

import com.example.iam.domain.authorization.aggregate.root.PermissionRule;
import com.example.iam.domain.authorization.aggregate.root.PlanDelegation;
import com.example.iam.domain.authorization.aggregate.valueobject.Action;
import com.example.iam.domain.authorization.aggregate.valueobject.BusinessCode;
import com.example.iam.domain.authorization.aggregate.valueobject.DelegationPermission;
import com.example.iam.domain.authorization.aggregate.valueobject.DelegationType;
import com.example.iam.domain.authorization.aggregate.valueobject.OperationMode;
import com.example.iam.domain.authorization.aggregate.valueobject.OverrideMode;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionCode;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionSnapshot;
import com.example.iam.domain.authorization.aggregate.valueobject.PlanMetadata;
import com.example.iam.domain.authorization.aggregate.valueobject.SubjectType;
import com.example.iam.domain.authorization.gateway.PlanMetadataGateway;
import com.example.iam.domain.authorization.repository.PermissionRuleRepository;
import com.example.iam.domain.authorization.repository.PlanDelegationRepository;
import com.example.iam.types.PermissionRuleId;
import com.example.iam.types.PlanDelegationId;
import com.example.iam.types.UserId;
import com.example.shared.exception.BusinessException;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultPermissionResolver 权限计算")
class DefaultPermissionResolverTest {

  private static final UserNo OPERATOR = UserNo.of("U-ADMIN");
  private static final UserId USER_ID = UserId.of(1001L);
  private static final String PLAN_NO = "PLAN001";
  private static final String CUSTOMER_NO = "CUST001";
  private static final String PRODUCT_NO = "PROD001";
  private static final String AM_CODE = "AM001";

  private static final BusinessCode ANNUITY_ESTABLISH = BusinessCode.of("ANNUITY_ESTABLISH");
  private static final BusinessCode ANNUITY_PAYMENT = BusinessCode.of("ANNUITY_PAYMENT");

  @Mock
  private PlanMetadataGateway planMetadataGateway;
  @Mock
  private PermissionRuleRepository permissionRuleRepository;
  @Mock
  private PlanDelegationRepository planDelegationRepository;

  private DefaultPermissionResolver resolver;

  @BeforeEach
  void setUp() {
    // 使用真实的 PriorityOverrideStrategy 而非 mock,确保端到端组合逻辑被测试
    var strategy = new com.example.iam.domain.authorization.strategy.PriorityOverrideStrategy();
    resolver = new DefaultPermissionResolver(
        planMetadataGateway, permissionRuleRepository, planDelegationRepository, strategy);
  }

  private PlanMetadata planMetadata() {
    return new PlanMetadata(PLAN_NO, CUSTOMER_NO, PRODUCT_NO,
        OperationMode.SINGLE_TRUSTEE, AM_CODE);
  }

  private PermissionRule createAddRule(SubjectType subjectType, BusinessCode businessCode,
                                        Set<Action> actions) {
    return PermissionRule.create(
        PermissionRuleId.of(System.nanoTime()),
        "RULE-" + System.nanoTime(),
        "测试规则",
        subjectType,
        switch (subjectType) {
          case CUSTOMER -> CUSTOMER_NO;
          case OPERATION_MODE -> OperationMode.SINGLE_TRUSTEE.name();
          case PRODUCT -> PRODUCT_NO;
          case PLAN -> PLAN_NO;
          case ACCOUNT_MANAGER -> AM_CODE;
        },
        businessCode,
        actions,
        false,
        OverrideMode.ADD,
        null,
        null, null,
        OPERATOR);
  }

  private PlanDelegation createDelegation(String delegateePlanNo, Set<Long> operators,
                                           Set<DelegationPermission> permissions) {
    return PlanDelegation.create(
        PlanDelegationId.of(System.nanoTime()),
        "DLG-" + System.nanoTime(),
        "DELEGATOR_PLAN",
        delegateePlanNo,
        DelegationType.SPECIFIC_OPERATORS,
        operators,
        permissions,
        null, null,
        OPERATOR);
  }

  @Test
  @DisplayName("计划不存在时抛 BusinessException")
  void resolve_planNotFound_throwsBusinessException() {
    when(planMetadataGateway.findByPlanNo(PLAN_NO)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> resolver.resolve(USER_ID, PLAN_NO))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  @DisplayName("无规则无代办时返回空权限快照")
  void resolve_noRulesNoDelegations_returnsEmptySnapshot() {
    when(planMetadataGateway.findByPlanNo(PLAN_NO)).thenReturn(Optional.of(planMetadata()));
    when(permissionRuleRepository.findEffectiveRulesForContext(
        CUSTOMER_NO, OperationMode.SINGLE_TRUSTEE.name(),
        PRODUCT_NO, PLAN_NO, AM_CODE))
        .thenReturn(List.of());
    when(planDelegationRepository.findEffectiveByDelegatee(PLAN_NO))
        .thenReturn(List.of());

    PermissionSnapshot snapshot = resolver.resolve(USER_ID, PLAN_NO);

    assertThat(snapshot.userId()).isEqualTo(USER_ID);
    assertThat(snapshot.planId()).isEqualTo(PLAN_NO);
    assertThat(snapshot.permissions()).isEmpty();
    assertThat(snapshot.calculatedAt()).isNotNull();
  }

  @Test
  @DisplayName("单条客户级 ADD 规则被正确计算为权限快照")
  void resolve_singleCustomerAddRule_returnsPermissions() {
    PermissionRule rule = createAddRule(
        SubjectType.CUSTOMER, ANNUITY_ESTABLISH, Set.of(Action.HANDLE));
    when(planMetadataGateway.findByPlanNo(PLAN_NO)).thenReturn(Optional.of(planMetadata()));
    when(permissionRuleRepository.findEffectiveRulesForContext(
        eq(CUSTOMER_NO), eq(OperationMode.SINGLE_TRUSTEE.name()),
        eq(PRODUCT_NO), eq(PLAN_NO), eq(AM_CODE)))
        .thenReturn(List.of(rule));
    when(planDelegationRepository.findEffectiveByDelegatee(PLAN_NO))
        .thenReturn(List.of());

    PermissionSnapshot snapshot = resolver.resolve(USER_ID, PLAN_NO);

    assertThat(snapshot.permissions()).containsExactly(
        PermissionCode.of("ANNUITY_ESTABLISH.HANDLE"));
  }

  @Test
  @DisplayName("多维度规则按优先级算法合并")
  void resolve_multipleDimensionRules_appliedByPriorityAlgorithm() {
    // 客户级 ADD:授予 HANDLE
    PermissionRule customerAdd = createAddRule(
        SubjectType.CUSTOMER, ANNUITY_ESTABLISH, Set.of(Action.HANDLE));
    // 计划级 REMOVE:移除 HANDLE(高层级覆盖低层级)
    PermissionRule planRemove = PermissionRule.create(
        PermissionRuleId.of(System.nanoTime()),
        "RULE-PLAN-REMOVE",
        "计划移除规则",
        SubjectType.PLAN, PLAN_NO,
        ANNUITY_ESTABLISH, Set.of(Action.HANDLE),
        false, OverrideMode.REMOVE, null,
        null, null, OPERATOR);

    when(planMetadataGateway.findByPlanNo(PLAN_NO)).thenReturn(Optional.of(planMetadata()));
    when(permissionRuleRepository.findEffectiveRulesForContext(
        any(), any(), any(), any(), any()))
        .thenReturn(List.of(customerAdd, planRemove));
    when(planDelegationRepository.findEffectiveByDelegatee(PLAN_NO))
        .thenReturn(List.of());

    PermissionSnapshot snapshot = resolver.resolve(USER_ID, PLAN_NO);

    // 计划级 REMOVE 覆盖客户级 ADD,最终不含 HANDLE
    assertThat(snapshot.permissions()).doesNotContain(
        PermissionCode.of("ANNUITY_ESTABLISH.HANDLE"));
    assertThat(snapshot.permissions()).isEmpty();
  }

  @Test
  @DisplayName("代办权限被合并到最终快照")
  void resolve_delegationPermissions_mergedIntoSnapshot() {
    // 客户级规则:ANNUITY_ESTABLISH.HANDLE
    PermissionRule rule = createAddRule(
        SubjectType.CUSTOMER, ANNUITY_ESTABLISH, Set.of(Action.HANDLE));
    // 代办关系:授权当前用户 ANNUITY_PAYMENT.AUDIT
    PlanDelegation delegation = createDelegation(
        PLAN_NO,
        Set.of(USER_ID.longValue()),
        Set.of(new DelegationPermission(ANNUITY_PAYMENT, Action.AUDIT)));

    when(planMetadataGateway.findByPlanNo(PLAN_NO)).thenReturn(Optional.of(planMetadata()));
    when(permissionRuleRepository.findEffectiveRulesForContext(
        any(), any(), any(), any(), any()))
        .thenReturn(List.of(rule));
    when(planDelegationRepository.findEffectiveByDelegatee(PLAN_NO))
        .thenReturn(List.of(delegation));

    PermissionSnapshot snapshot = resolver.resolve(USER_ID, PLAN_NO);

    assertThat(snapshot.permissions()).containsExactlyInAnyOrder(
        PermissionCode.of("ANNUITY_ESTABLISH.HANDLE"),
        PermissionCode.of("ANNUITY_PAYMENT.AUDIT"));
  }

  @Test
  @DisplayName("ALL_OPERATORS 代办授权所有操作员")
  void resolve_allOperatorsDelegation_authorizesAllUsers() {
    PermissionRule rule = createAddRule(
        SubjectType.CUSTOMER, ANNUITY_ESTABLISH, Set.of(Action.HANDLE));
    PlanDelegation allOpsDelegation = PlanDelegation.create(
        PlanDelegationId.of(System.nanoTime()),
        "DLG-ALL",
        "DELEGATOR_PLAN",
        PLAN_NO,
        DelegationType.ALL_OPERATORS,
        Set.of(),
        Set.of(new DelegationPermission(ANNUITY_PAYMENT, Action.AUDIT)),
        null, null, OPERATOR);

    when(planMetadataGateway.findByPlanNo(PLAN_NO)).thenReturn(Optional.of(planMetadata()));
    when(permissionRuleRepository.findEffectiveRulesForContext(
        any(), any(), any(), any(), any()))
        .thenReturn(List.of(rule));
    when(planDelegationRepository.findEffectiveByDelegatee(PLAN_NO))
        .thenReturn(List.of(allOpsDelegation));

    PermissionSnapshot snapshot = resolver.resolve(USER_ID, PLAN_NO);

    assertThat(snapshot.permissions()).contains(
        PermissionCode.of("ANNUITY_PAYMENT.AUDIT"));
  }

  @Test
  @DisplayName("SPECIFIC_OPERATORS 代办不授权未指定操作员")
  void resolve_specificOperatorsDelegation_excludesUnlistedUser() {
    PermissionRule rule = createAddRule(
        SubjectType.CUSTOMER, ANNUITY_ESTABLISH, Set.of(Action.HANDLE));
    // 代办关系指定操作员 9999L,不包含当前用户 1001L
    PlanDelegation delegation = createDelegation(
        PLAN_NO,
        Set.of(9999L),
        Set.of(new DelegationPermission(ANNUITY_PAYMENT, Action.AUDIT)));

    when(planMetadataGateway.findByPlanNo(PLAN_NO)).thenReturn(Optional.of(planMetadata()));
    when(permissionRuleRepository.findEffectiveRulesForContext(
        any(), any(), any(), any(), any()))
        .thenReturn(List.of(rule));
    when(planDelegationRepository.findEffectiveByDelegatee(PLAN_NO))
        .thenReturn(List.of(delegation));

    PermissionSnapshot snapshot = resolver.resolve(USER_ID, PLAN_NO);

    // 当前用户未被指定,代办权限不应被合并
    assertThat(snapshot.permissions()).doesNotContain(
        PermissionCode.of("ANNUITY_PAYMENT.AUDIT"));
    assertThat(snapshot.permissions()).containsExactly(
        PermissionCode.of("ANNUITY_ESTABLISH.HANDLE"));
  }

  @Test
  @DisplayName("多条代办关系合并所有授权的代办权限")
  void resolve_multipleDelegations_mergeAllAuthorizedPermissions() {
    PermissionRule rule = createAddRule(
        SubjectType.CUSTOMER, ANNUITY_ESTABLISH, Set.of(Action.HANDLE));
    PlanDelegation delegation1 = createDelegation(
        PLAN_NO,
        Set.of(USER_ID.longValue()),
        Set.of(new DelegationPermission(ANNUITY_PAYMENT, Action.AUDIT)));
    PlanDelegation delegation2 = createDelegation(
        PLAN_NO,
        Set.of(USER_ID.longValue()),
        Set.of(new DelegationPermission(ANNUITY_PAYMENT, Action.QUERY)));

    when(planMetadataGateway.findByPlanNo(PLAN_NO)).thenReturn(Optional.of(planMetadata()));
    when(permissionRuleRepository.findEffectiveRulesForContext(
        any(), any(), any(), any(), any()))
        .thenReturn(List.of(rule));
    when(planDelegationRepository.findEffectiveByDelegatee(PLAN_NO))
        .thenReturn(List.of(delegation1, delegation2));

    PermissionSnapshot snapshot = resolver.resolve(USER_ID, PLAN_NO);

    assertThat(snapshot.permissions()).contains(
        PermissionCode.of("ANNUITY_PAYMENT.AUDIT"),
        PermissionCode.of("ANNUITY_PAYMENT.QUERY"));
  }

  @Test
  @DisplayName("快照计算时间戳在调用时生成")
  void resolve_calculatedAtSetAtCallTime() {
    when(planMetadataGateway.findByPlanNo(PLAN_NO)).thenReturn(Optional.of(planMetadata()));
    when(permissionRuleRepository.findEffectiveRulesForContext(
        any(), any(), any(), any(), any()))
        .thenReturn(List.of());
    when(planDelegationRepository.findEffectiveByDelegatee(PLAN_NO))
        .thenReturn(List.of());

    LocalDateTime before = LocalDateTime.now();
    PermissionSnapshot snapshot = resolver.resolve(USER_ID, PLAN_NO);
    LocalDateTime after = LocalDateTime.now();

    assertThat(snapshot.calculatedAt()).isBetween(before, after);
  }

  @Test
  @DisplayName("返回快照的权限集合不可变")
  void resolve_returnedPermissionsAreImmutable() {
    when(planMetadataGateway.findByPlanNo(PLAN_NO)).thenReturn(Optional.of(planMetadata()));
    when(permissionRuleRepository.findEffectiveRulesForContext(
        any(), any(), any(), any(), any()))
        .thenReturn(List.of());
    when(planDelegationRepository.findEffectiveByDelegatee(PLAN_NO))
        .thenReturn(List.of());

    PermissionSnapshot snapshot = resolver.resolve(USER_ID, PLAN_NO);

    assertThatThrownBy(() -> snapshot.permissions()
        .add(PermissionCode.of("ANNUITY_PAYMENT.AUDIT")))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
