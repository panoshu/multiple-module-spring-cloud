package com.example.auth.infrastructure.channel.config;

import com.example.core.types.Channel;
import com.example.auth.domain.assignment.service.PlanReachabilityService;
import com.example.auth.domain.channel.service.BranchPlanSelectionStrategy;
import com.example.auth.domain.channel.service.HqPlanSelectionStrategy;
import com.example.auth.domain.channel.service.OnlinePlanSelectionStrategy;
import com.example.auth.domain.channel.service.PlanSelectionStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * 渠道层基础设施配置 — 注册 {@link PlanSelectionStrategy} 渠道映射.
 *
 * <p>三种策略按渠道分发：
 * <ul>
 *   <li>{@link Channel#NETAPP} → {@link OnlinePlanSelectionStrategy}（枚举可选计划 + 渠道过滤）</li>
 *   <li>{@link Channel#TELLER} → {@link HqPlanSelectionStrategy}（全量计划，无过滤）</li>
 *   <li>{@link Channel#BANK_BRANCH} → {@link BranchPlanSelectionStrategy}（需二次授权 + 委托 Online 策略）</li>
 * </ul>
 *
 * <p>策略类位于 domain 层，无 Spring 注解，由本配置类统一实例化并注册。</p>
 */
@Configuration
public class ChannelInfrastructureConfig {

  @Bean
  public Map<Channel, PlanSelectionStrategy> planSelectionStrategyMap(
    PlanReachabilityService planReachabilityService
  ) {
    OnlinePlanSelectionStrategy online = new OnlinePlanSelectionStrategy(planReachabilityService);
    HqPlanSelectionStrategy hq = new HqPlanSelectionStrategy();
    BranchPlanSelectionStrategy branch = new BranchPlanSelectionStrategy(online);

    return Map.of(
      Channel.NETAPP, online,
      Channel.TELLER, hq,
      Channel.BANK_BRANCH, branch
    );
  }
}
