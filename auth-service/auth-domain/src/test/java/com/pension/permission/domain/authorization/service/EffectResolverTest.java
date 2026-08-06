package com.pension.permission.domain.authorization.service;

import com.pension.permission.domain.fixture.AuthorizationFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EffectResolver 效果解析测试")
class EffectResolverTest {

  private final EffectResolver resolver = new EffectResolver();

  @Nested
  @DisplayName("resolve 方法")
  class ResolveTest {

    @Test
    @DisplayName("空授权列表应返回 false")
    void shouldReturnFalseWhenEmpty() {
      assertThat(resolver.resolve(List.of())).isFalse();
    }

    @Test
    @DisplayName("仅含 ALLOW 授权应返回 true")
    void shouldReturnTrueWhenOnlyAllow() {
      var grant = AuthorizationFixtures.effectiveAllowGrant();
      assertThat(resolver.resolve(List.of(grant))).isTrue();
    }

    @Test
    @DisplayName("含 DENY 授权时应返回 false（DENY 优先）")
    void shouldReturnFalseWhenDenyPresent() {
      var allow = AuthorizationFixtures.effectiveAllowGrant();
      var deny = AuthorizationFixtures.effectiveDenyGrant();
      assertThat(resolver.resolve(List.of(allow, deny))).isFalse();
    }

    @Test
    @DisplayName("仅含 DENY 授权应返回 false")
    void shouldReturnFalseWhenOnlyDeny() {
      var deny = AuthorizationFixtures.effectiveDenyGrant();
      assertThat(resolver.resolve(List.of(deny))).isFalse();
    }
  }
}
