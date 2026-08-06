package com.pension.permission.domain.authorization.service;

import com.pension.permission.domain.authorization.enumeration.ScopeDimension;
import com.pension.permission.domain.authorization.valueobject.ScopeRule;
import com.pension.permission.domain.fixture.AuthorizationFixtures;
import com.pension.permission.domain.product.ProductGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ScopeMatcher 范围匹配测试")
class ScopeMatcherTest {

  private final ProductGateway gateway = AuthorizationFixtures.mockProductGateway();
  private final ScopeMatcher matcher = new ScopeMatcher(gateway);

  @Nested
  @DisplayName("matches 方法")
  class MatchesTest {

    @Test
    @DisplayName("PLAN 维度规则匹配相同计划应返回 true")
    void shouldMatchWhenPlanEquals() {
      var rule = ScopeRule.of(ScopeDimension.PLAN, "PLAN-001");
      var plan = AuthorizationFixtures.planSnapshot("PLAN-001");

      assertThat(matcher.matches(List.of(rule), plan)).isTrue();
    }

    @Test
    @DisplayName("PLAN 维度规则不匹配不同计划应返回 false")
    void shouldNotMatchWhenPlanDiffers() {
      var rule = ScopeRule.of(ScopeDimension.PLAN, "PLAN-002");
      var plan = AuthorizationFixtures.planSnapshot("PLAN-001");

      assertThat(matcher.matches(List.of(rule), plan)).isFalse();
    }

    @Test
    @DisplayName("空规则列表应返回 true（无约束即匹配）")
    void shouldReturnTrueWhenNoRules() {
      var plan = AuthorizationFixtures.planSnapshot("PLAN-001");
      assertThat(matcher.matches(List.of(), plan)).isTrue();
    }

    @Test
    @DisplayName("多规则 AND 语义：一条不匹配则整体 false")
    void shouldReturnFalseWhenAnyRuleNotMatch() {
      var rule1 = ScopeRule.of(ScopeDimension.PLAN, "PLAN-001");
      var rule2 = ScopeRule.of(ScopeDimension.PLAN, "PLAN-999");
      var plan = AuthorizationFixtures.planSnapshot("PLAN-001");

      assertThat(matcher.matches(List.of(rule1, rule2), plan)).isFalse();
    }
  }
}
