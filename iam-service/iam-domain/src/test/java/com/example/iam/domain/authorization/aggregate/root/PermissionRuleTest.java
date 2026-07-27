package com.example.iam.domain.authorization.aggregate.root;

import com.example.iam.domain.authorization.aggregate.valueobject.Action;
import com.example.iam.domain.authorization.aggregate.valueobject.BusinessCode;
import com.example.iam.domain.authorization.aggregate.valueobject.OperationMode;
import com.example.iam.domain.authorization.aggregate.valueobject.OverrideMode;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionCode;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionMatchContext;
import com.example.iam.domain.authorization.aggregate.valueobject.RuleStatus;
import com.example.iam.domain.authorization.aggregate.valueobject.SubjectType;
import com.example.iam.domain.authorization.event.PermissionRuleCreatedEvent;
import com.example.iam.domain.authorization.event.PermissionRuleDisabledEvent;
import com.example.iam.domain.authorization.event.PermissionRuleEnabledEvent;
import com.example.iam.types.PermissionRuleId;
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
 * PermissionRule 聚合根行为测试。
 *
 * <p>覆盖工厂方法、状态机(enable/disable)、生效判断、匹配逻辑、权限码生成、
 * 优先级、reconstitute 重建、字段不可变性与空值/业务规则校验。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@DisplayName("PermissionRule 聚合根行为")
class PermissionRuleTest {

  private static final UserNo OPERATOR = UserNo.of("U-ADMIN");
  private static final PermissionRuleId RULE_ID = PermissionRuleId.of(5001L);
  private static final BusinessCode BUSINESS_CODE = BusinessCode.of("ANNUITY_ESTABLISH");

  @Nested
  @DisplayName("create 工厂方法")
  class CreateTest {

    @Test
    @DisplayName("create 初始化 ACTIVE 状态并设置默认生效时间")
    void create_initializesActiveStatusAndDefaultEffectiveAt() {
      LocalDateTime before = LocalDateTime.now();

      PermissionRule rule = createDefaultRule();

      assertThat(rule.id()).isEqualTo(RULE_ID);
      assertThat(rule.ruleCode()).isEqualTo("PR-001");
      assertThat(rule.ruleName()).isEqualTo("年金设立权限");
      assertThat(rule.subjectType()).isEqualTo(SubjectType.CUSTOMER);
      assertThat(rule.subjectId()).isEqualTo("CUST-001");
      assertThat(rule.businessCode()).isEqualTo(BUSINESS_CODE);
      assertThat(rule.allowedActions()).containsExactly(Action.HANDLE);
      assertThat(rule.isInheritToChildren()).isFalse();
      assertThat(rule.overrideMode()).isEqualTo(OverrideMode.ADD);
      assertThat(rule.priority()).isNull();
      assertThat(rule.status()).isEqualTo(RuleStatus.ACTIVE);
      assertThat(rule.effectiveAt()).isAfterOrEqualTo(before);
      assertThat(rule.expireAt()).isNull();
      assertThat(rule.createdBy()).isEqualTo(OPERATOR);
      assertThat(rule.updatedBy()).isEqualTo(OPERATOR);
      assertThat(rule.version()).isEqualTo(Version.initial());
    }

    @Test
    @DisplayName("create 注册 PermissionRuleCreatedEvent 事件")
    void create_registersCreatedEvent() {
      PermissionRule rule = createDefaultRule();

      assertThat(rule.getDomainEvents())
          .hasSize(1)
          .first()
          .isInstanceOf(PermissionRuleCreatedEvent.class);
      PermissionRuleCreatedEvent event = (PermissionRuleCreatedEvent) rule.getDomainEvents().get(0);
      assertThat(event.ruleId()).isEqualTo(RULE_ID);
      assertThat(event.ruleCode()).isEqualTo("PR-001");
      assertThat(event.subjectType()).isEqualTo(SubjectType.CUSTOMER);
      assertThat(event.subjectId()).isEqualTo("CUST-001");
      assertThat(event.businessCode()).isEqualTo(BUSINESS_CODE);
      assertThat(event.overrideMode()).isEqualTo(OverrideMode.ADD);
      assertThat(event.operator()).isEqualTo(OPERATOR);
    }

    @Test
    @DisplayName("create 在 effectiveAt 显式指定时使用指定值")
    void create_usesExplicitEffectiveAt() {
      LocalDateTime effective = LocalDateTime.of(2026, 7, 26, 9, 0);

      PermissionRule rule = PermissionRule.create(
          RULE_ID, "PR-001", "年金设立权限",
          SubjectType.CUSTOMER, "CUST-001",
          BUSINESS_CODE, Set.of(Action.HANDLE),
          false, OverrideMode.ADD, null,
          effective, null, OPERATOR
      );

      assertThat(rule.effectiveAt()).isEqualTo(effective);
    }

    @Test
    @DisplayName("create 拒绝空 ruleCode")
    void create_rejectsBlankRuleCode() {
      assertThatThrownBy(() -> PermissionRule.create(
          RULE_ID, "", "年金设立权限",
          SubjectType.CUSTOMER, "CUST-001",
          BUSINESS_CODE, Set.of(Action.HANDLE),
          false, OverrideMode.ADD, null,
          null, null, OPERATOR
      )).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("create 拒绝空 ruleName")
    void create_rejectsBlankRuleName() {
      assertThatThrownBy(() -> PermissionRule.create(
          RULE_ID, "PR-001", "  ",
          SubjectType.CUSTOMER, "CUST-001",
          BUSINESS_CODE, Set.of(Action.HANDLE),
          false, OverrideMode.ADD, null,
          null, null, OPERATOR
      )).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("create 拒绝空 subjectType")
    void create_rejectsNullSubjectType() {
      assertThatThrownBy(() -> PermissionRule.create(
          RULE_ID, "PR-001", "年金设立权限",
          null, "CUST-001",
          BUSINESS_CODE, Set.of(Action.HANDLE),
          false, OverrideMode.ADD, null,
          null, null, OPERATOR
      )).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("create 拒绝空 subjectId")
    void create_rejectsBlankSubjectId() {
      assertThatThrownBy(() -> PermissionRule.create(
          RULE_ID, "PR-001", "年金设立权限",
          SubjectType.CUSTOMER, "",
          BUSINESS_CODE, Set.of(Action.HANDLE),
          false, OverrideMode.ADD, null,
          null, null, OPERATOR
      )).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("create 拒绝空 businessCode")
    void create_rejectsNullBusinessCode() {
      assertThatThrownBy(() -> PermissionRule.create(
          RULE_ID, "PR-001", "年金设立权限",
          SubjectType.CUSTOMER, "CUST-001",
          null, Set.of(Action.HANDLE),
          false, OverrideMode.ADD, null,
          null, null, OPERATOR
      )).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("create 拒绝空 allowedActions 集合")
    void create_rejectsEmptyActions() {
      assertThatThrownBy(() -> PermissionRule.create(
          RULE_ID, "PR-001", "年金设立权限",
          SubjectType.CUSTOMER, "CUST-001",
          BUSINESS_CODE, Set.of(),
          false, OverrideMode.ADD, null,
          null, null, OPERATOR
      )).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("create 拒绝空 allowedActions 引用")
    void create_rejectsNullActions() {
      assertThatThrownBy(() -> PermissionRule.create(
          RULE_ID, "PR-001", "年金设立权限",
          SubjectType.CUSTOMER, "CUST-001",
          BUSINESS_CODE, null,
          false, OverrideMode.ADD, null,
          null, null, OPERATOR
      )).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("create 拒绝空 overrideMode")
    void create_rejectsNullOverrideMode() {
      assertThatThrownBy(() -> PermissionRule.create(
          RULE_ID, "PR-001", "年金设立权限",
          SubjectType.CUSTOMER, "CUST-001",
          BUSINESS_CODE, Set.of(Action.HANDLE),
          false, null, null,
          null, null, OPERATOR
      )).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("create 拒绝 expireAt 不晚于 effectiveAt")
    void create_rejectsInvalidEffectivePeriod() {
      LocalDateTime effective = LocalDateTime.of(2026, 7, 26, 9, 0);
      LocalDateTime expire = LocalDateTime.of(2026, 7, 26, 8, 0);
      assertThatThrownBy(() -> PermissionRule.create(
          RULE_ID, "PR-001", "年金设立权限",
          SubjectType.CUSTOMER, "CUST-001",
          BUSINESS_CODE, Set.of(Action.HANDLE),
          false, OverrideMode.ADD, null,
          effective, expire, OPERATOR
      )).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("create 拒绝负数 priority")
    void create_rejectsNegativePriority() {
      assertThatThrownBy(() -> PermissionRule.create(
          RULE_ID, "PR-001", "年金设立权限",
          SubjectType.CUSTOMER, "CUST-001",
          BUSINESS_CODE, Set.of(Action.HANDLE),
          false, OverrideMode.ADD, -1,
          null, null, OPERATOR
      )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("create 拒绝非 CUSTOMER 类型设置 inheritToChildren=true")
    void create_rejectsInheritToChildrenForNonCustomer() {
      assertThatThrownBy(() -> PermissionRule.create(
          RULE_ID, "PR-001", "年金设立权限",
          SubjectType.PRODUCT, "PROD-001",
          BUSINESS_CODE, Set.of(Action.HANDLE),
          true, OverrideMode.ADD, null,
          null, null, OPERATOR
      )).isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  @DisplayName("enable/disable 状态机")
  class StateMachineTest {

    @Test
    @DisplayName("disable 将 ACTIVE 状态置为 DISABLED 并注册事件")
    void disable_changesActiveToDisabled() {
      PermissionRule rule = createDefaultRule();
      Version initialVersion = rule.version();

      rule.disable(OPERATOR);

      assertThat(rule.status()).isEqualTo(RuleStatus.DISABLED);
      assertThat(rule.version()).isEqualTo(initialVersion.next());
      assertThat(rule.updatedBy()).isEqualTo(OPERATOR);
      assertThat(rule.getDomainEvents())
          .filteredOn(e -> e instanceof PermissionRuleDisabledEvent)
          .hasSize(1);
      PermissionRuleDisabledEvent event = (PermissionRuleDisabledEvent)
          rule.getDomainEvents().stream()
              .filter(e -> e instanceof PermissionRuleDisabledEvent)
              .findFirst().orElseThrow();
      assertThat(event.ruleId()).isEqualTo(RULE_ID);
      assertThat(event.ruleCode()).isEqualTo("PR-001");
      assertThat(event.operator()).isEqualTo(OPERATOR);
    }

    @Test
    @DisplayName("disable 在 DISABLED 状态时抛 DomainException")
    void disable_throwsWhenAlreadyDisabled() {
      PermissionRule rule = createDefaultRule();
      rule.disable(OPERATOR);

      assertThatThrownBy(() -> rule.disable(OPERATOR))
          .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("enable 将 DISABLED 状态恢复为 ACTIVE 并注册事件")
    void enable_changesDisabledToActive() {
      PermissionRule rule = createDefaultRule();
      rule.disable(OPERATOR);
      rule.clearDomainEvents();

      rule.enable(OPERATOR);

      assertThat(rule.status()).isEqualTo(RuleStatus.ACTIVE);
      assertThat(rule.getDomainEvents())
          .filteredOn(e -> e instanceof PermissionRuleEnabledEvent)
          .hasSize(1);
      PermissionRuleEnabledEvent event = (PermissionRuleEnabledEvent)
          rule.getDomainEvents().stream()
              .filter(e -> e instanceof PermissionRuleEnabledEvent)
              .findFirst().orElseThrow();
      assertThat(event.ruleId()).isEqualTo(RULE_ID);
      assertThat(event.ruleCode()).isEqualTo("PR-001");
    }

    @Test
    @DisplayName("enable 在 ACTIVE 状态时抛 DomainException")
    void enable_throwsWhenAlreadyActive() {
      PermissionRule rule = createDefaultRule();

      assertThatThrownBy(() -> rule.enable(OPERATOR))
          .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("disable 与 enable 可循环转换")
    void disableAndEnable_canCycle() {
      PermissionRule rule = createDefaultRule();

      rule.disable(OPERATOR);
      rule.enable(OPERATOR);
      rule.disable(OPERATOR);

      assertThat(rule.status()).isEqualTo(RuleStatus.DISABLED);
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
      PermissionRule rule = createRuleWithPeriod(effective, expire);

      assertThat(rule.isEffectiveAt(LocalDateTime.now())).isTrue();
    }

    @Test
    @DisplayName("isEffectiveAt 在生效时间之前返回 false")
    void isEffectiveAt_returnsFalseBeforeEffective() {
      LocalDateTime effective = LocalDateTime.now().plusDays(1);
      LocalDateTime expire = LocalDateTime.now().plusDays(2);
      PermissionRule rule = createRuleWithPeriod(effective, expire);

      assertThat(rule.isEffectiveAt(LocalDateTime.now())).isFalse();
    }

    @Test
    @DisplayName("isEffectiveAt 在失效时间之后返回 false")
    void isEffectiveAt_returnsFalseAfterExpire() {
      LocalDateTime effective = LocalDateTime.now().minusDays(2);
      LocalDateTime expire = LocalDateTime.now().minusDays(1);
      PermissionRule rule = createRuleWithPeriod(effective, expire);

      assertThat(rule.isEffectiveAt(LocalDateTime.now())).isFalse();
    }

    @Test
    @DisplayName("isEffectiveAt 在 DISABLED 状态时返回 false")
    void isEffectiveAt_returnsFalseWhenDisabled() {
      PermissionRule rule = createDefaultRule();
      rule.disable(OPERATOR);

      assertThat(rule.isEffectiveAt(LocalDateTime.now())).isFalse();
    }

    @Test
    @DisplayName("isEffectiveAt 在 expireAt 为 null 时永久生效")
    void isEffectiveAt_returnsTrueWhenPermanent() {
      PermissionRule rule = createDefaultRule();

      assertThat(rule.isEffectiveAt(LocalDateTime.now().plusYears(10))).isTrue();
    }

    @Test
    @DisplayName("isEffectiveAt 在 moment 为 null 时按当前时刻判断")
    void isEffectiveAt_usesNowWhenMomentIsNull() {
      PermissionRule rule = createDefaultRule();

      assertThat(rule.isEffectiveAt(null)).isTrue();
    }

    @Test
    @DisplayName("isEffectiveAt 在 expireAt 时刻等于判断时刻时返回 false(半开区间)")
    void isEffectiveAt_returnsFalseAtExpireBoundary() {
      LocalDateTime effective = LocalDateTime.now().minusDays(1);
      LocalDateTime expire = LocalDateTime.now();
      PermissionRule rule = createRuleWithPeriod(effective, expire);

      assertThat(rule.isEffectiveAt(expire)).isFalse();
    }
  }

  @Nested
  @DisplayName("matches 主体匹配")
  class MatchesTest {

    @Test
    @DisplayName("matches 在主体维度匹配时返回 true")
    void matches_returnsTrueWhenSubjectMatches() {
      PermissionRule rule = createDefaultRule();
      PermissionMatchContext context = new PermissionMatchContext(
          "CUST-001", OperationMode.SINGLE_TRUSTEE, "PROD-001", "PLAN-001", "AM-001"
      );

      assertThat(rule.matches(context)).isTrue();
    }

    @Test
    @DisplayName("matches 在主体维度不匹配时返回 false")
    void matches_returnsFalseWhenSubjectMismatch() {
      PermissionRule rule = createDefaultRule();
      PermissionMatchContext context = new PermissionMatchContext(
          "CUST-OTHER", OperationMode.SINGLE_TRUSTEE, "PROD-001", "PLAN-001", "AM-001"
      );

      assertThat(rule.matches(context)).isFalse();
    }

    @Test
    @DisplayName("matches 按 OPERATION_MODE 维度匹配")
    void matches_matchesByOperationMode() {
      PermissionRule rule = PermissionRule.create(
          RULE_ID, "PR-001", "年金设立权限",
          SubjectType.OPERATION_MODE, OperationMode.SINGLE_TRUSTEE.name(),
          BUSINESS_CODE, Set.of(Action.HANDLE),
          false, OverrideMode.ADD, null,
          null, null, OPERATOR
      );
      PermissionMatchContext context = new PermissionMatchContext(
          "CUST-001", OperationMode.SINGLE_TRUSTEE, "PROD-001", "PLAN-001", "AM-001"
      );

      assertThat(rule.matches(context)).isTrue();
    }

    @Test
    @DisplayName("matches 按 PLAN 维度匹配")
    void matches_matchesByPlan() {
      PermissionRule rule = PermissionRule.create(
          RULE_ID, "PR-001", "年金设立权限",
          SubjectType.PLAN, "PLAN-001",
          BUSINESS_CODE, Set.of(Action.HANDLE),
          false, OverrideMode.ADD, null,
          null, null, OPERATOR
      );
      PermissionMatchContext context = new PermissionMatchContext(
          "CUST-001", OperationMode.SINGLE_TRUSTEE, "PROD-001", "PLAN-001", "AM-001"
      );

      assertThat(rule.matches(context)).isTrue();
    }

    @Test
    @DisplayName("matches 拒绝空 context")
    void matches_rejectsNullContext() {
      PermissionRule rule = createDefaultRule();

      assertThatThrownBy(() -> rule.matches(null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("permissionCodes 与 effectivePriority")
  class PermissionCodeAndPriorityTest {

    @Test
    @DisplayName("permissionCodes 生成 businessCode.action 格式的权限码")
    void permissionCodes_generatesExpectedCodes() {
      PermissionRule rule = PermissionRule.create(
          RULE_ID, "PR-001", "年金设立权限",
          SubjectType.CUSTOMER, "CUST-001",
          BUSINESS_CODE, Set.of(Action.HANDLE, Action.QUERY),
          false, OverrideMode.ADD, null,
          null, null, OPERATOR
      );

      assertThat(rule.permissionCodes())
          .containsExactlyInAnyOrder(
              PermissionCode.of("ANNUITY_ESTABLISH.HANDLE"),
              PermissionCode.of("ANNUITY_ESTABLISH.QUERY")
          );
    }

    @Test
    @DisplayName("permissionCodes 返回不可变集合")
    void permissionCodes_returnsImmutableSet() {
      PermissionRule rule = createDefaultRule();

      Set<PermissionCode> codes = rule.permissionCodes();

      assertThatThrownBy(() -> codes.add(PermissionCode.of("X.Y")))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("effectivePriority 在 priority 为空时回退到 subjectType.priority")
    void effectivePriority_fallsBackToSubjectTypePriority() {
      PermissionRule rule = createDefaultRule();

      assertThat(rule.effectivePriority()).isEqualTo(SubjectType.CUSTOMER.priority());
    }

    @Test
    @DisplayName("effectivePriority 在 priority 显式设置时使用显式值")
    void effectivePriority_usesExplicitPriority() {
      PermissionRule rule = PermissionRule.create(
          RULE_ID, "PR-001", "年金设立权限",
          SubjectType.CUSTOMER, "CUST-001",
          BUSINESS_CODE, Set.of(Action.HANDLE),
          false, OverrideMode.ADD, 99,
          null, null, OPERATOR
      );

      assertThat(rule.effectivePriority()).isEqualTo(99);
    }

    @Test
    @DisplayName("effectivePriority 在 ACCOUNT_MANAGER 维度回退到最高优先级")
    void effectivePriority_fallsBackToAccountManagerPriority() {
      PermissionRule rule = PermissionRule.create(
          RULE_ID, "PR-001", "年金设立权限",
          SubjectType.ACCOUNT_MANAGER, "AM-001",
          BUSINESS_CODE, Set.of(Action.HANDLE),
          false, OverrideMode.ADD, null,
          null, null, OPERATOR
      );

      assertThat(rule.effectivePriority()).isEqualTo(SubjectType.ACCOUNT_MANAGER.priority());
    }
  }

  @Nested
  @DisplayName("reconstitute 数据库重建")
  class ReconstituteTest {

    @Test
    @DisplayName("reconstitute 恢复完整聚合状态(含 DISABLED 状态)")
    void reconstitute_restoresFullAggregate() {
      LocalDateTime createdAt = LocalDateTime.of(2026, 7, 1, 9, 0);
      LocalDateTime updatedAt = LocalDateTime.of(2026, 7, 26, 14, 30);
      LocalDateTime effective = LocalDateTime.of(2026, 7, 1, 0, 0);
      LocalDateTime expire = LocalDateTime.of(2027, 1, 1, 0, 0);
      Version version = Version.of(5);

      PermissionRule rule = PermissionRule.reconstitute(
          RULE_ID, "PR-001", "年金设立权限",
          SubjectType.PLAN, "PLAN-001",
          BUSINESS_CODE, Set.of(Action.HANDLE, Action.QUERY),
          false, OverrideMode.REMOVE, 10,
          RuleStatus.DISABLED, effective, expire,
          OPERATOR, OPERATOR, createdAt, updatedAt, version
      );

      assertThat(rule.ruleCode()).isEqualTo("PR-001");
      assertThat(rule.ruleName()).isEqualTo("年金设立权限");
      assertThat(rule.subjectType()).isEqualTo(SubjectType.PLAN);
      assertThat(rule.subjectId()).isEqualTo("PLAN-001");
      assertThat(rule.businessCode()).isEqualTo(BUSINESS_CODE);
      assertThat(rule.allowedActions()).containsExactlyInAnyOrder(Action.HANDLE, Action.QUERY);
      assertThat(rule.isInheritToChildren()).isFalse();
      assertThat(rule.overrideMode()).isEqualTo(OverrideMode.REMOVE);
      assertThat(rule.priority()).isEqualTo(10);
      assertThat(rule.status()).isEqualTo(RuleStatus.DISABLED);
      assertThat(rule.effectiveAt()).isEqualTo(effective);
      assertThat(rule.expireAt()).isEqualTo(expire);
      assertThat(rule.createdBy()).isEqualTo(OPERATOR);
      assertThat(rule.updatedBy()).isEqualTo(OPERATOR);
      assertThat(rule.createdAt()).isEqualTo(createdAt);
      assertThat(rule.updatedAt()).isEqualTo(updatedAt);
      assertThat(rule.version()).isEqualTo(version);
    }

    @Test
    @DisplayName("reconstitute 不注册领域事件")
    void reconstitute_doesNotRegisterEvents() {
      PermissionRule rule = createReconstitutedRule();

      assertThat(rule.getDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("reconstitute 拒绝 inheritToChildren=true 且非 CUSTOMER 类型")
    void reconstitute_rejectsInheritToChildrenForNonCustomer() {
      assertThatThrownBy(() -> PermissionRule.reconstitute(
          RULE_ID, "PR-001", "年金设立权限",
          SubjectType.PRODUCT, "PROD-001",
          BUSINESS_CODE, Set.of(Action.HANDLE),
          true, OverrideMode.ADD, null,
          RuleStatus.ACTIVE, LocalDateTime.now(), null,
          OPERATOR, OPERATOR, LocalDateTime.now(), LocalDateTime.now(), Version.initial()
      )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("reconstitute 拒绝空 status")
    void reconstitute_rejectsNullStatus() {
      assertThatThrownBy(() -> PermissionRule.reconstitute(
          RULE_ID, "PR-001", "年金设立权限",
          SubjectType.CUSTOMER, "CUST-001",
          BUSINESS_CODE, Set.of(Action.HANDLE),
          false, OverrideMode.ADD, null,
          null, LocalDateTime.now(), null,
          OPERATOR, OPERATOR, LocalDateTime.now(), LocalDateTime.now(), Version.initial()
      )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("reconstitute 拒绝空 effectiveAt")
    void reconstitute_rejectsNullEffectiveAt() {
      assertThatThrownBy(() -> PermissionRule.reconstitute(
          RULE_ID, "PR-001", "年金设立权限",
          SubjectType.CUSTOMER, "CUST-001",
          BUSINESS_CODE, Set.of(Action.HANDLE),
          false, OverrideMode.ADD, null,
          RuleStatus.ACTIVE, null, null,
          OPERATOR, OPERATOR, LocalDateTime.now(), LocalDateTime.now(), Version.initial()
      )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("reconstitute 拒绝 expireAt 不晚于 effectiveAt")
    void reconstitute_rejectsInvalidPeriod() {
      LocalDateTime effective = LocalDateTime.of(2026, 7, 26, 9, 0);
      LocalDateTime expire = LocalDateTime.of(2026, 7, 26, 8, 0);
      assertThatThrownBy(() -> PermissionRule.reconstitute(
          RULE_ID, "PR-001", "年金设立权限",
          SubjectType.CUSTOMER, "CUST-001",
          BUSINESS_CODE, Set.of(Action.HANDLE),
          false, OverrideMode.ADD, null,
          RuleStatus.ACTIVE, effective, expire,
          OPERATOR, OPERATOR, LocalDateTime.now(), LocalDateTime.now(), Version.initial()
      )).isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  @DisplayName("字段不可变性")
  class ImmutabilityTest {

    @Test
    @DisplayName("allowedActions 返回不可变集合")
    void allowedActions_returnsImmutableSet() {
      PermissionRule rule = createDefaultRule();

      Set<Action> actions = rule.allowedActions();

      assertThatThrownBy(() -> actions.add(Action.QUERY))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("allowedActions 内部状态不受外部集合修改影响")
    void allowedActions_isolatedFromExternalMutations() {
      Set<Action> source = new HashSet<>();
      source.add(Action.HANDLE);
      PermissionRule rule = PermissionRule.create(
          RULE_ID, "PR-001", "年金设立权限",
          SubjectType.CUSTOMER, "CUST-001",
          BUSINESS_CODE, source,
          false, OverrideMode.ADD, null,
          null, null, OPERATOR
      );
      source.add(Action.QUERY);

      assertThat(rule.allowedActions()).containsExactly(Action.HANDLE);
    }

    @Test
    @DisplayName("getDomainEvents 返回不可变列表")
    void getDomainEvents_returnsImmutableList() {
      PermissionRule rule = createDefaultRule();

      assertThatThrownBy(() -> rule.getDomainEvents().clear())
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("clearDomainEvents 清空已注册事件")
    void clearDomainEvents_clearsEvents() {
      PermissionRule rule = createDefaultRule();
      assertThat(rule.getDomainEvents()).hasSize(1);

      rule.clearDomainEvents();

      assertThat(rule.getDomainEvents()).isEmpty();
    }
  }

  private PermissionRule createDefaultRule() {
    return PermissionRule.create(
        RULE_ID, "PR-001", "年金设立权限",
        SubjectType.CUSTOMER, "CUST-001",
        BUSINESS_CODE, Set.of(Action.HANDLE),
        false, OverrideMode.ADD, null,
        null, null, OPERATOR
    );
  }

  private PermissionRule createRuleWithPeriod(LocalDateTime effective, LocalDateTime expire) {
    return PermissionRule.create(
        RULE_ID, "PR-001", "年金设立权限",
        SubjectType.CUSTOMER, "CUST-001",
        BUSINESS_CODE, Set.of(Action.HANDLE),
        false, OverrideMode.ADD, null,
        effective, expire, OPERATOR
    );
  }

  private PermissionRule createReconstitutedRule() {
    return PermissionRule.reconstitute(
        RULE_ID, "PR-001", "年金设立权限",
        SubjectType.CUSTOMER, "CUST-001",
        BUSINESS_CODE, Set.of(Action.HANDLE),
        false, OverrideMode.ADD, null,
        RuleStatus.ACTIVE, LocalDateTime.now(), null,
        OPERATOR, OPERATOR, LocalDateTime.now(), LocalDateTime.now(), Version.initial()
    );
  }
}
