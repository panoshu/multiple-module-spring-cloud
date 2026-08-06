package com.pension.permission.domain.channel.service;

import com.pension.permission.domain.assignment.service.PlanReachabilityService;
import com.pension.permission.domain.fixture.ChannelFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("BranchPlanSelectionStrategy 测试")
class BranchPlanSelectionStrategyTest {

  private final OnlinePlanSelectionStrategy delegate =
    new OnlinePlanSelectionStrategy(mock(PlanReachabilityService.class));
  private final BranchPlanSelectionStrategy strategy =
    new BranchPlanSelectionStrategy(delegate);

  @Test
  @DisplayName("viaSecondaryAuth=false 时应抛 IllegalStateException")
  void shouldThrowWhenNotViaSecondaryAuth() {
    var identity = ChannelFixtures.directIdentity("user-1");

    assertThatThrownBy(() -> strategy.listSelectablePlans(identity))
      .isInstanceOf(IllegalStateException.class);
  }
}
