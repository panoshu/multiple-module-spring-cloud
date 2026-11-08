package com.example.file.domain.service;

import com.example.file.domain.model.parse.aggregate.TemplateConfig;
import com.example.file.domain.model.parse.valueobject.config.RoutingRule;
import com.example.shared.domain.annotation.DomainService;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 模板路由服务（2026-11-08 批次3 A3：上下文路由配置化——设计决策 D2）.
 *
 * <p>同 bizType 的 ACTIVE 配置集合按路由规则匹配上下文（产品/计划/账管人等）
 * 选择目标配置——"哪个产品用哪个模板"的规则留在 file 域，业务服务不感知.</p>
 *
 * <p><b>选择算法</b>：候选中规则匹配者按 priority 降序取首个；无匹配规则命中时
 * 取无条件兜底配置（conditions 空者）；都没有则 empty（调用方 fail-fast）.</p>
 *
 * @author file-service
 */
@DomainService
public class TemplateRoutingService {

  /**
   * 路由选择.
   *
   * @param candidates 同 bizType 的 ACTIVE 配置集（含各自的 routingRules）
   * @param context    业务上下文（维度名→值）
   */
  public Optional<TemplateConfig> route(List<TemplateConfig> candidates, Map<String, String> context) {
    if (candidates == null || candidates.isEmpty()) {
      return Optional.empty();
    }

    Optional<TemplateConfig> bestMatched = candidates.stream()
      .filter(c -> matchesExactly(c.routingRules(), context))
      .max(Comparator.comparingInt(c -> topPriority(c.routingRules())));
    if (bestMatched.isPresent()) {
      return bestMatched;
    }

    // 兜底：无条件规则（空 conditions）的配置
    return candidates.stream()
      .filter(c -> c.routingRules().stream().anyMatch(r -> r.conditions().isEmpty()))
      .findFirst();
  }

  private boolean matchesExactly(List<RoutingRule> rules, Map<String, String> context) {
    return rules.stream()
      .filter(r -> !r.conditions().isEmpty())
      .anyMatch(r -> r.matches(context));
  }

  private int topPriority(List<RoutingRule> rules) {
    return rules.stream().mapToInt(RoutingRule::priority).max().orElse(0);
  }
}
