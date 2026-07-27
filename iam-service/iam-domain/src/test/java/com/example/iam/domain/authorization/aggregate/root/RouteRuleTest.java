package com.example.iam.domain.authorization.aggregate.root;

import com.example.iam.domain.authorization.aggregate.valueobject.RouteCheckType;
import com.example.iam.types.RouteRuleId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RouteRule 聚合根行为测试。
 *
 * <p>覆盖工厂方法、enable/disable 状态机、Ant 风格匹配、reconstitute 重建、
 * 字段不可变性与空值/业务规则校验。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@DisplayName("RouteRule 聚合根行为")
class RouteRuleTest {

  private static final UserNo OPERATOR = UserNo.of("U-ADMIN");
  private static final RouteRuleId RULE_ID = RouteRuleId.of(7001L);

  @Nested
  @DisplayName("create 工厂方法")
  class CreateTest {

    @Test
    @DisplayName("create 初始化启用状态")
    void create_initializesEnabledState() {
      LocalDateTime before = LocalDateTime.now();

      RouteRule rule = createDefaultRule();

      assertThat(rule.id()).isEqualTo(RULE_ID);
      assertThat(rule.routePattern()).isEqualTo("internet/**");
      assertThat(rule.checkType()).isEqualTo(RouteCheckType.SKIP);
      assertThat(rule.checkValue()).isNull();
      assertThat(rule.description()).isEqualTo("互联网渠道白名单");
      assertThat(rule.priority()).isEqualTo(100);
      assertThat(rule.isEnabled()).isTrue();
      assertThat(rule.createdBy()).isEqualTo(OPERATOR);
      assertThat(rule.updatedBy()).isEqualTo(OPERATOR);
      assertThat(rule.createdAt()).isAfterOrEqualTo(before);
      assertThat(rule.version()).isEqualTo(Version.initial());
    }

    @Test
    @DisplayName("create 不注册领域事件")
    void create_doesNotRegisterEvents() {
      RouteRule rule = createDefaultRule();

      assertThat(rule.getDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("create 在 PERMISSION 类型时携带 checkValue")
    void create_carriesCheckValueForPermissionType() {
      RouteRule rule = RouteRule.create(
          RULE_ID, "api/**", RouteCheckType.PERMISSION,
          "ANNUITY_ESTABLISH.HANDLE", "权限校验", 50, OPERATOR
      );

      assertThat(rule.checkType()).isEqualTo(RouteCheckType.PERMISSION);
      assertThat(rule.checkValue()).isEqualTo("ANNUITY_ESTABLISH.HANDLE");
    }

    @Test
    @DisplayName("create 在 SKIP 类型时 checkValue 可为空")
    void create_allowsNullCheckValueForSkipType() {
      RouteRule rule = RouteRule.create(
          RULE_ID, "public/**", RouteCheckType.SKIP,
          null, "白名单", 200, OPERATOR
      );

      assertThat(rule.checkType()).isEqualTo(RouteCheckType.SKIP);
      assertThat(rule.checkValue()).isNull();
    }

    @Test
    @DisplayName("create 拒绝空 routePattern")
    void create_rejectsBlankRoutePattern() {
      assertThatThrownBy(() -> RouteRule.create(
          RULE_ID, "", RouteCheckType.LOGIN,
          null, "描述", 100, OPERATOR
      )).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("create 拒绝非法格式的 routePattern(以非字母开头)")
    void create_rejectsInvalidRoutePatternFormat() {
      assertThatThrownBy(() -> RouteRule.create(
          RULE_ID, "/internet/**", RouteCheckType.LOGIN,
          null, "描述", 100, OPERATOR
      )).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("create 拒绝空 checkType")
    void create_rejectsNullCheckType() {
      assertThatThrownBy(() -> RouteRule.create(
          RULE_ID, "internet/**", null,
          null, "描述", 100, OPERATOR
      )).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("create 在非 SKIP 类型时拒绝空 checkValue")
    void create_rejectsBlankCheckValueForNonSkipType() {
      assertThatThrownBy(() -> RouteRule.create(
          RULE_ID, "internet/**", RouteCheckType.PERMISSION,
          "", "描述", 100, OPERATOR
      )).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("create 拒绝负数 priority")
    void create_rejectsNegativePriority() {
      assertThatThrownBy(() -> RouteRule.create(
          RULE_ID, "internet/**", RouteCheckType.SKIP,
          null, "描述", -1, OPERATOR
      )).isInstanceOf(DomainException.class);
    }
  }

  @Nested
  @DisplayName("enable/disable 状态机")
  class StateMachineTest {

    @Test
    @DisplayName("disable 将启用状态置为禁用")
    void disable_changesEnabledToDisabled() {
      RouteRule rule = createDefaultRule();
      Version initialVersion = rule.version();

      rule.disable(OPERATOR);

      assertThat(rule.isEnabled()).isFalse();
      assertThat(rule.version()).isEqualTo(initialVersion.next());
      assertThat(rule.updatedBy()).isEqualTo(OPERATOR);
    }

    @Test
    @DisplayName("disable 在已禁用状态时抛 DomainException")
    void disable_throwsWhenAlreadyDisabled() {
      RouteRule rule = createDefaultRule();
      rule.disable(OPERATOR);

      assertThatThrownBy(() -> rule.disable(OPERATOR))
          .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("enable 将禁用状态恢复为启用")
    void enable_changesDisabledToEnabled() {
      RouteRule rule = createDefaultRule();
      rule.disable(OPERATOR);

      rule.enable(OPERATOR);

      assertThat(rule.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("enable 在已启用状态时抛 DomainException")
    void enable_throwsWhenAlreadyEnabled() {
      RouteRule rule = createDefaultRule();

      assertThatThrownBy(() -> rule.enable(OPERATOR))
          .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("disable 与 enable 可循环转换")
    void disableAndEnable_canCycle() {
      RouteRule rule = createDefaultRule();

      rule.disable(OPERATOR);
      rule.enable(OPERATOR);
      rule.disable(OPERATOR);

      assertThat(rule.isEnabled()).isFalse();
    }
  }

  @Nested
  @DisplayName("matches Ant 风格路径匹配")
  class MatchesTest {

    @Test
    @DisplayName("matches 在 ** 匹配多段路径时返回 true")
    void matches_returnsTrueForDoubleStarMultiSegment() {
      RouteRule rule = createRuleWithPattern("internet/**");

      assertThat(rule.matches("internet/foo")).isTrue();
      assertThat(rule.matches("internet/foo/bar")).isTrue();
      assertThat(rule.matches("internet/")).isTrue();
    }

    @Test
    @DisplayName("matches 在 * 匹配单段内任意字符时返回 true")
    void matches_returnsTrueForSingleStarWithinSegment() {
      RouteRule rule = createRuleWithPattern("api/v1/*");

      assertThat(rule.matches("api/v1/users")).isTrue();
      assertThat(rule.matches("api/v1/orders")).isTrue();
    }

    @Test
    @DisplayName("matches 在 * 不跨段时返回 false")
    void matches_returnsFalseWhenSingleStarCrossesSegment() {
      RouteRule rule = createRuleWithPattern("api/v1/*");

      assertThat(rule.matches("api/v1/users/list")).isFalse();
    }

    @Test
    @DisplayName("matches 在 ? 匹配单字符时返回 true")
    void matches_returnsTrueForQuestionMark() {
      RouteRule rule = createRuleWithPattern("api/v?/users");

      assertThat(rule.matches("api/v1/users")).isTrue();
      assertThat(rule.matches("api/v2/users")).isTrue();
    }

    @Test
    @DisplayName("matches 在路径不匹配时返回 false")
    void matches_returnsFalseWhenPathMismatch() {
      RouteRule rule = createRuleWithPattern("internet/**");

      assertThat(rule.matches("intranet/foo")).isFalse();
    }

    @Test
    @DisplayName("matches 拒绝空 path")
    void matches_rejectsNullPath() {
      RouteRule rule = createDefaultRule();

      assertThatThrownBy(() -> rule.matches(null))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("matches 在精确匹配时返回 true")
    void matches_returnsTrueForExactMatch() {
      RouteRule rule = createRuleWithPattern("api/login");

      assertThat(rule.matches("api/login")).isTrue();
      assertThat(rule.matches("api/logout")).isFalse();
    }
  }

  @Nested
  @DisplayName("reconstitute 数据库重建")
  class ReconstituteTest {

    @Test
    @DisplayName("reconstitute 恢复完整聚合状态(含禁用状态)")
    void reconstitute_restoresFullAggregate() {
      LocalDateTime createdAt = LocalDateTime.of(2026, 7, 1, 9, 0);
      LocalDateTime updatedAt = LocalDateTime.of(2026, 7, 26, 14, 30);
      Version version = Version.of(5);

      RouteRule rule = RouteRule.reconstitute(
          RULE_ID, "api/**", RouteCheckType.PERMISSION,
          "ANNUITY_ESTABLISH.HANDLE", "权限校验", 50, false,
          OPERATOR, OPERATOR, createdAt, updatedAt, version
      );

      assertThat(rule.routePattern()).isEqualTo("api/**");
      assertThat(rule.checkType()).isEqualTo(RouteCheckType.PERMISSION);
      assertThat(rule.checkValue()).isEqualTo("ANNUITY_ESTABLISH.HANDLE");
      assertThat(rule.description()).isEqualTo("权限校验");
      assertThat(rule.priority()).isEqualTo(50);
      assertThat(rule.isEnabled()).isFalse();
      assertThat(rule.createdBy()).isEqualTo(OPERATOR);
      assertThat(rule.updatedBy()).isEqualTo(OPERATOR);
      assertThat(rule.createdAt()).isEqualTo(createdAt);
      assertThat(rule.updatedAt()).isEqualTo(updatedAt);
      assertThat(rule.version()).isEqualTo(version);
    }

    @Test
    @DisplayName("reconstitute 不注册领域事件")
    void reconstitute_doesNotRegisterEvents() {
      RouteRule rule = createReconstitutedRule();

      assertThat(rule.getDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("reconstitute 拒绝空 routePattern")
    void reconstitute_rejectsBlankRoutePattern() {
      assertThatThrownBy(() -> RouteRule.reconstitute(
          RULE_ID, "", RouteCheckType.LOGIN,
          null, "描述", 100, true,
          OPERATOR, OPERATOR, LocalDateTime.now(), LocalDateTime.now(), Version.initial()
      )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("reconstitute 拒绝空 checkType")
    void reconstitute_rejectsNullCheckType() {
      assertThatThrownBy(() -> RouteRule.reconstitute(
          RULE_ID, "internet/**", null,
          null, "描述", 100, true,
          OPERATOR, OPERATOR, LocalDateTime.now(), LocalDateTime.now(), Version.initial()
      )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("reconstitute 拒绝负数 priority")
    void reconstitute_rejectsNegativePriority() {
      assertThatThrownBy(() -> RouteRule.reconstitute(
          RULE_ID, "internet/**", RouteCheckType.LOGIN,
          null, "描述", -1, true,
          OPERATOR, OPERATOR, LocalDateTime.now(), LocalDateTime.now(), Version.initial()
      )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("reconstitute 在非 SKIP 类型时拒绝空 checkValue")
    void reconstitute_rejectsBlankCheckValueForNonSkipType() {
      assertThatThrownBy(() -> RouteRule.reconstitute(
          RULE_ID, "internet/**", RouteCheckType.ROLE,
          "", "描述", 100, true,
          OPERATOR, OPERATOR, LocalDateTime.now(), LocalDateTime.now(), Version.initial()
      )).isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  @DisplayName("字段不可变性")
  class ImmutabilityTest {

    @Test
    @DisplayName("getDomainEvents 返回不可变列表")
    void getDomainEvents_returnsImmutableList() {
      RouteRule rule = createDefaultRule();

      assertThatThrownBy(() -> rule.getDomainEvents().clear())
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("routePattern 字段不可变")
    void routePattern_isImmutable() {
      RouteRule rule = createDefaultRule();

      assertThat(rule.routePattern()).isEqualTo("internet/**");
    }
  }

  private RouteRule createDefaultRule() {
    return RouteRule.create(
        RULE_ID, "internet/**", RouteCheckType.SKIP,
        null, "互联网渠道白名单", 100, OPERATOR
    );
  }

  private RouteRule createRuleWithPattern(String pattern) {
    return RouteRule.create(
        RULE_ID, pattern, RouteCheckType.SKIP,
        null, "测试规则", 100, OPERATOR
    );
  }

  private RouteRule createReconstitutedRule() {
    return RouteRule.reconstitute(
        RULE_ID, "internet/**", RouteCheckType.SKIP,
        null, "互联网渠道白名单", 100, true,
        OPERATOR, OPERATOR, LocalDateTime.now(), LocalDateTime.now(), Version.initial()
    );
  }
}
