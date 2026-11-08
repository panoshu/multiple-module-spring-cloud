package com.example.file.domain.model.parse.valueobject.config;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

import java.util.List;

/**
 * 模板路由规则（2026-11-08 批次3 A3：上下文路由配置化——设计决策 D2）.
 *
 * <p>"哪个产品/计划/账管人用哪个模板"的规则配置化存于 TemplateConfig——
 * 业务服务不感知模板选择。同 bizType 多条 ACTIVE 配置并存，路由按
 * 规则匹配度+优先级选择.</p>
 *
 * @param conditions 全部命中才匹配（AND 语义；空列表=无条件兜底配置）
 * @param priority   优先级（多条命中取大者；兜底配置建议 0）
 */
public record RoutingRule(
  List<RouteCondition> conditions,
  int priority
) implements ValueObject {

  public RoutingRule {
    conditions = conditions == null ? List.of() : List.copyOf(conditions);
  }

  /**
   * 上下文匹配（AND 语义）.
   *
   * @param context 业务上下文（维度名→值：productNo/planNo/accountManager 等）
   */
  public boolean matches(java.util.Map<String, String> context) {
    if (conditions.isEmpty()) {
      return true; // 无条件=兜底
    }
    if (context == null) {
      return false;
    }
    return conditions.stream().allMatch(c -> c.matches(context.get(c.dimension())));
  }
}
