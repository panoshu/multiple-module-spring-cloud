package com.pension.permission.domain.channel.service;

import com.pension.permission.domain.assignment.service.PlanReachabilityService;
import com.pension.permission.domain.channel.valueobject.EnumeratedPlans;
import com.pension.permission.domain.fixture.ChannelFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("OnlinePlanSelectionStrategy 测试")
class OnlinePlanSelectionStrategyTest {

  private final PlanReachabilityService reachabilityService = mock(PlanReachabilityService.class);
  private final OnlinePlanSelectionStrategy strategy =
    new OnlinePlanSelectionStrategy(reachabilityService);

  @Test
  @DisplayName("应返回 EnumeratedPlans，委托 PlanReachabilityService")
  void shouldReturnEnumeratedPlans() {
    var identity = ChannelFixtures.directIdentity("user-1");
    when(reachabilityService.listSelectablePlans(identity.identityAccountId()))
      .thenReturn(List.of());

    var result = strategy.listSelectablePlans(identity);

    assertThat(result).isInstanceOf(EnumeratedPlans.class);
  }
}
