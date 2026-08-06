package com.pension.permission.domain.channel.service;

import com.pension.permission.domain.channel.valueobject.AllPlans;
import com.pension.permission.domain.fixture.ChannelFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HqPlanSelectionStrategy 测试")
class HqPlanSelectionStrategyTest {

  private final HqPlanSelectionStrategy strategy = new HqPlanSelectionStrategy();

  @Test
  @DisplayName("应恒返回 AllPlans（总部可见全部计划）")
  void shouldReturnAllPlans() {
    var identity = ChannelFixtures.directIdentity("user-1");

    var result = strategy.listSelectablePlans(identity);

    assertThat(result).isInstanceOf(AllPlans.class);
  }
}
