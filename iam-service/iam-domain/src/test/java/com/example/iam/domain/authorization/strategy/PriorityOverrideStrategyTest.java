package com.example.iam.domain.authorization.strategy;

import com.example.iam.domain.authorization.aggregate.root.PermissionRule;
import com.example.iam.domain.authorization.aggregate.valueobject.Action;
import com.example.iam.domain.authorization.aggregate.valueobject.BusinessCode;
import com.example.iam.domain.authorization.aggregate.valueobject.OperationMode;
import com.example.iam.domain.authorization.aggregate.valueobject.OverrideMode;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionCode;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionCombinationContext;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionMatchContext;
import com.example.iam.domain.authorization.aggregate.valueobject.SubjectType;
import com.example.iam.types.PermissionRuleId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PriorityOverrideStrategy 优先级覆盖策略")
class PriorityOverrideStrategyTest {

  private static final UserNo OPERATOR = UserNo.of("U-ADMIN");
  private static final BusinessCode ANNUITY_ESTABLISH = BusinessCode.of("ANNUITY_ESTABLISH");
  private static final BusinessCode ANNUITY_CONTRIBUTION = BusinessCode.of("ANNUITY_CONTRIBUTION");
  private static final BusinessCode ANNUITY_PAYMENT = BusinessCode.of("ANNUITY_PAYMENT");

  private final PriorityOverrideStrategy strategy = new PriorityOverrideStrategy();

  private PermissionMatchContext matchContext() {
    return new PermissionMatchContext(
        "CUST001", OperationMode.SINGLE_TRUSTEE,
        "PROD001", "PLAN001", "AM001");
  }

  private PermissionRule createRule(SubjectType subjectType, BusinessCode businessCode,
                                     Set<Action> actions, OverrideMode mode, Integer priority) {
    return PermissionRule.create(
        PermissionRuleId.of(System.nanoTime()),
        "RULE-" + subjectType.name() + "-" + businessCode.value(),
        "测试规则",
        subjectType,
        subjectType.name() + "-001",
        businessCode,
        actions,
        false,
        mode,
        priority,
        null, null,
        OPERATOR);
  }

  @Test
  @DisplayName("name 返回策略标识")
  void name_returnsStrategyIdentifier() {
    assertThat(strategy.name()).isEqualTo("priorityOverrideStrategy");
  }

  @Test
  @DisplayName("空规则集合返回空集")
  void combine_emptyRules_returnsEmptySet() {
    PermissionCombinationContext context =
        PermissionCombinationContext.empty(matchContext());

    Set<PermissionCode> result = strategy.combine(context);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("单条 ADD 规则返回规则权限码集合")
  void combine_singleAddRule_returnsRulePermissionCodes() {
    PermissionRule rule = createRule(
        SubjectType.CUSTOMER, ANNUITY_ESTABLISH,
        Set.of(Action.HANDLE, Action.QUERY), OverrideMode.ADD, null);

    PermissionCombinationContext context =
        PermissionCombinationContext.of(List.of(rule), Set.of(), matchContext());

    Set<PermissionCode> result = strategy.combine(context);

    assertThat(result).containsExactlyInAnyOrder(
        PermissionCode.of("ANNUITY_ESTABLISH.HANDLE"),
        PermissionCode.of("ANNUITY_ESTABLISH.QUERY"));
  }

  @Test
  @DisplayName("多条 ADD 规则取并集")
  void combine_multipleAddRules_returnsUnion() {
    PermissionRule rule1 = createRule(
        SubjectType.CUSTOMER, ANNUITY_ESTABLISH,
        Set.of(Action.HANDLE), OverrideMode.ADD, null);
    PermissionRule rule2 = createRule(
        SubjectType.PRODUCT, ANNUITY_CONTRIBUTION,
        Set.of(Action.QUERY), OverrideMode.ADD, null);

    PermissionCombinationContext context =
        PermissionCombinationContext.of(List.of(rule1, rule2), Set.of(), matchContext());

    Set<PermissionCode> result = strategy.combine(context);

    assertThat(result).containsExactlyInAnyOrder(
        PermissionCode.of("ANNUITY_ESTABLISH.HANDLE"),
        PermissionCode.of("ANNUITY_CONTRIBUTION.QUERY"));
  }

  @Test
  @DisplayName("高层级 REMOVE 规则移除低层级 ADD 规则授予的权限")
  void combine_highPriorityRemove_removesLowPriorityAdd() {
    // 客户级 ADD:授予 ANNUITY_ESTABLISH.HANDLE
    PermissionRule customerAdd = createRule(
        SubjectType.CUSTOMER, ANNUITY_ESTABLISH,
        Set.of(Action.HANDLE), OverrideMode.ADD, null);
    // 计划级 REMOVE:移除 ANNUITY_ESTABLISH.HANDLE
    PermissionRule planRemove = createRule(
        SubjectType.PLAN, ANNUITY_ESTABLISH,
        Set.of(Action.HANDLE), OverrideMode.REMOVE, null);

    // 乱序传入,策略内部应按优先级排序
    PermissionCombinationContext context =
        PermissionCombinationContext.of(List.of(planRemove, customerAdd), Set.of(), matchContext());

    Set<PermissionCode> result = strategy.combine(context);

    assertThat(result).doesNotContain(PermissionCode.of("ANNUITY_ESTABLISH.HANDLE"));
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("REMOVE 不存在的权限为 no-op")
  void combine_removeNonExistent_isNoOp() {
    PermissionRule removeRule = createRule(
        SubjectType.PLAN, ANNUITY_ESTABLISH,
        Set.of(Action.HANDLE), OverrideMode.REMOVE, null);

    PermissionCombinationContext context =
        PermissionCombinationContext.of(List.of(removeRule), Set.of(), matchContext());

    Set<PermissionCode> result = strategy.combine(context);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("ADD 后 REMOVE 再 ADD 同一权限,最终包含该权限")
  void combine_addRemoveAdd_finalAddWins() {
    PermissionRule customerAdd = createRule(
        SubjectType.CUSTOMER, ANNUITY_ESTABLISH,
        Set.of(Action.HANDLE), OverrideMode.ADD, null);
    PermissionRule productRemove = createRule(
        SubjectType.PRODUCT, ANNUITY_ESTABLISH,
        Set.of(Action.HANDLE), OverrideMode.REMOVE, null);
    PermissionRule accountManagerAdd = createRule(
        SubjectType.ACCOUNT_MANAGER, ANNUITY_ESTABLISH,
        Set.of(Action.HANDLE), OverrideMode.ADD, null);

    PermissionCombinationContext context = PermissionCombinationContext.of(
        List.of(customerAdd, productRemove, accountManagerAdd), Set.of(), matchContext());

    Set<PermissionCode> result = strategy.combine(context);

    assertThat(result).containsExactly(PermissionCode.of("ANNUITY_ESTABLISH.HANDLE"));
  }

  @Test
  @DisplayName("代办权限码被合并到最终集合")
  void combine_delegationPermissions_areMerged() {
    PermissionRule rule = createRule(
        SubjectType.CUSTOMER, ANNUITY_ESTABLISH,
        Set.of(Action.HANDLE), OverrideMode.ADD, null);

    Set<PermissionCode> delegation = Set.of(
        PermissionCode.of("ANNUITY_PAYMENT.AUDIT"));

    PermissionCombinationContext context =
        PermissionCombinationContext.of(List.of(rule), delegation, matchContext());

    Set<PermissionCode> result = strategy.combine(context);

    assertThat(result).containsExactlyInAnyOrder(
        PermissionCode.of("ANNUITY_ESTABLISH.HANDLE"),
        PermissionCode.of("ANNUITY_PAYMENT.AUDIT"));
  }

  @Test
  @DisplayName("显式 priority 覆盖 SubjectType 默认优先级")
  void combine_explicitPriority_overridesSubjectTypePriority() {
    // 显式将客户级优先级设为 99(高于账管人级 5),REMOVE 应最后应用
    PermissionRule customerAdd = createRule(
        SubjectType.CUSTOMER, ANNUITY_ESTABLISH,
        Set.of(Action.HANDLE), OverrideMode.ADD, null);
    PermissionRule accountManagerRemove = createRule(
        SubjectType.ACCOUNT_MANAGER, ANNUITY_ESTABLISH,
        Set.of(Action.HANDLE), OverrideMode.REMOVE, null);
    // 给账管人级显式设低优先级 1,低于客户级默认 1 但相同
    // 改为:给客户级 ADD 显式 priority=10,账管人级 REMOVE 显式 priority=1
    PermissionRule customerAddHigh = createRule(
        SubjectType.CUSTOMER, ANNUITY_ESTABLISH,
        Set.of(Action.HANDLE), OverrideMode.ADD, 10);
    PermissionRule accountManagerRemoveLow = createRule(
        SubjectType.ACCOUNT_MANAGER, ANNUITY_ESTABLISH,
        Set.of(Action.HANDLE), OverrideMode.REMOVE, 1);

    PermissionCombinationContext context = PermissionCombinationContext.of(
        List.of(customerAddHigh, accountManagerRemoveLow), Set.of(), matchContext());

    Set<PermissionCode> result = strategy.combine(context);

    // 高优先级 ADD 在后应用,但 REMOVE 在前应用(无效果),所以最终包含 HANDLE
    assertThat(result).contains(PermissionCode.of("ANNUITY_ESTABLISH.HANDLE"));

    // 用未显式 priority 的情况对比:账户管理级(5) > 客户级(1),REMOVE 应在后并移除
    PermissionCombinationContext context2 = PermissionCombinationContext.of(
        List.of(customerAdd, accountManagerRemove), Set.of(), matchContext());
    Set<PermissionCode> result2 = strategy.combine(context2);
    assertThat(result2).doesNotContain(PermissionCode.of("ANNUITY_ESTABLISH.HANDLE"));
  }

  @Test
  @DisplayName("不同业务的 ADD/REMOVE 互不影响")
  void combine_differentBusinesses_areIndependent() {
    PermissionRule addEstablish = createRule(
        SubjectType.CUSTOMER, ANNUITY_ESTABLISH,
        Set.of(Action.HANDLE), OverrideMode.ADD, null);
    PermissionRule removeContribution = createRule(
        SubjectType.PLAN, ANNUITY_CONTRIBUTION,
        Set.of(Action.QUERY), OverrideMode.REMOVE, null);

    PermissionCombinationContext context = PermissionCombinationContext.of(
        List.of(addEstablish, removeContribution), Set.of(), matchContext());

    Set<PermissionCode> result = strategy.combine(context);

    assertThat(result).containsExactly(PermissionCode.of("ANNUITY_ESTABLISH.HANDLE"));
  }

  @Test
  @DisplayName("返回集合不可变")
  void combine_resultIsImmutable() {
    PermissionRule rule = createRule(
        SubjectType.CUSTOMER, ANNUITY_ESTABLISH,
        Set.of(Action.HANDLE), OverrideMode.ADD, null);

    PermissionCombinationContext context =
        PermissionCombinationContext.of(List.of(rule), Set.of(), matchContext());

    Set<PermissionCode> result = strategy.combine(context);

    assertThatThrownBy(() -> result.add(PermissionCode.of("ANNUITY_PAYMENT.AUDIT")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("null context 抛 NullPointerException")
  void combine_nullContext_throwsNpe() {
    assertThatThrownBy(() -> strategy.combine(null))
        .isInstanceOf(NullPointerException.class);
  }
}
