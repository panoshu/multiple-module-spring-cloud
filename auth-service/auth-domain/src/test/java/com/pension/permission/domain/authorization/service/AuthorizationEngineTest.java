package com.pension.permission.domain.authorization.service;

import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.repository.GrantRepository;
import com.pension.permission.domain.authorization.spi.PlanMembershipLookup;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import com.pension.permission.domain.fixture.AuthorizationFixtures;
import com.pension.permission.domain.product.ProductGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("AuthorizationEngine 权限判定测试")
class AuthorizationEngineTest {

  private final GrantRepository grantRepository = AuthorizationFixtures.mockGrantRepository();
  private final ProductGateway productGateway = AuthorizationFixtures.mockProductGateway();
  private final PlanMembershipLookup membershipLookup = AuthorizationFixtures.mockMembershipLookup();
  private final AuthorizationEngine engine =
    new AuthorizationEngine(productGateway, grantRepository, membershipLookup);

  private void stubPlan(String planNo) {
    when(productGateway.requirePlan(PlanNo.of(planNo)))
      .thenReturn(AuthorizationFixtures.planSnapshot(planNo));
  }

  @Nested
  @DisplayName("能力层判定 checkPlanCapability")
  class CheckPlanCapabilityTest {

    @Test
    @DisplayName("命中 EFFECTIVE ALLOW 授权应返回 true")
    void shouldReturnTrueWhenAllowGrantMatched() {
      var planId = PlanNo.of("PLAN-001");
      var business = new BusinessCode("BIZ-001");
      var at = LocalDateTime.now();
      stubPlan("PLAN-001");
      when(grantRepository.findActiveCapabilityGrants(at))
        .thenReturn(List.of(AuthorizationFixtures.effectiveAllowGrant()));

      assertThat(engine.checkPlanCapability(planId, business, at)).isTrue();
    }

    @Test
    @DisplayName("命中 DENY 授权应返回 false")
    void shouldReturnFalseWhenDenyGrantMatched() {
      var planId = PlanNo.of("PLAN-001");
      var business = new BusinessCode("BIZ-001");
      var at = LocalDateTime.now();
      stubPlan("PLAN-001");
      when(grantRepository.findActiveCapabilityGrants(at))
        .thenReturn(List.of(AuthorizationFixtures.effectiveDenyGrant()));

      assertThat(engine.checkPlanCapability(planId, business, at)).isFalse();
    }

    @Test
    @DisplayName("无匹配授权应返回 false")
    void shouldReturnFalseWhenNoGrantMatched() {
      var planId = PlanNo.of("PLAN-001");
      var business = new BusinessCode("BIZ-001");
      var at = LocalDateTime.now();
      stubPlan("PLAN-001");
      when(grantRepository.findActiveCapabilityGrants(at)).thenReturn(List.of());

      assertThat(engine.checkPlanCapability(planId, business, at)).isFalse();
    }
  }

  @Nested
  @DisplayName("最终判定 checkPermission")
  class CheckPermissionTest {

    @Test
    @DisplayName("能力层失败时应直接返回 false（短路）")
    void shouldReturnFalseWhenCapabilityDenied() {
      var identity = UserNo.of("user-1");
      var planId = PlanNo.of("PLAN-001");
      var permission = AuthorizationFixtures.permission("BIZ-001", "ACT-VIEW");
      var at = LocalDateTime.now();
      stubPlan("PLAN-001");
      when(grantRepository.findActiveCapabilityGrants(at)).thenReturn(List.of());

      assertThat(engine.checkPermission(identity, planId, permission, at)).isFalse();
    }
  }
}
