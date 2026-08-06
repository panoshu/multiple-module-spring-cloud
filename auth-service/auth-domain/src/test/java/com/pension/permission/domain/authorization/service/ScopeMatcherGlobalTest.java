package com.pension.permission.domain.authorization.service;

import com.pension.permission.domain.authorization.enumeration.ScopeDimension;
import com.pension.permission.domain.authorization.valueobject.ScopeRule;
import com.pension.permission.domain.fixture.AuthorizationFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScopeMatcherGlobalTest {

  private final ScopeMatcher matcher = new ScopeMatcher(AuthorizationFixtures.mockProductGateway());

  @Test
  void empty_rules_should_match_any_plan() {
    boolean result = matcher.matches(List.of(), AuthorizationFixtures.planSnapshot("PLAN-001"));
    assertThat(result).isTrue();
  }

  @Test
  void empty_rules_should_match_even_when_plan_is_null() {
    boolean result = matcher.matches(List.of(), null);
    assertThat(result).isTrue();
  }

  @Test
  void global_rule_should_match_when_plan_is_null() {
    ScopeRule globalRule = new ScopeRule(ScopeDimension.GLOBAL, "GLOBAL", false);
    boolean result = matcher.matches(List.of(globalRule), null);
    assertThat(result).isTrue();
  }

  @Test
  void mixed_global_and_plan_rules_should_check_plan_part() {
    ScopeRule globalRule = new ScopeRule(ScopeDimension.GLOBAL, "GLOBAL", false);
    ScopeRule planRule = ScopeRule.of(ScopeDimension.PLAN, "PLAN-001");
    boolean result = matcher.matches(List.of(globalRule, planRule), AuthorizationFixtures.planSnapshot("PLAN-001"));
    assertThat(result).isTrue();
  }

  @Test
  void non_global_rule_should_fail_when_plan_is_null() {
    ScopeRule planRule = ScopeRule.of(ScopeDimension.PLAN, "PLAN-001");
    boolean result = matcher.matches(List.of(planRule), null);
    assertThat(result).isFalse();
  }
}
