package com.example.gateway.security;

import com.example.iam.api.dto.RouteRuleDTO;

/**
 * 路由权限规则 - 网关本地值对象。
 *
 * <p>对应设计文档 4.5 节:网关层动态鉴权的配置单元。
 * 从 {@link RouteRuleDTO} 转换而来,仅保留网关校验所需字段,
 * 屏蔽 iam-service 内部聚合根细节(如 ruleId/version/createdAt 等)。
 *
 * <p>网关启动后由 {@link RouteRuleLoader} 定期从 iam-service 拉取并缓存,
 * {@link SaTokenGatewayConfiguration} 在 SaReactorFilter 中按 priority 倒序匹配。
 *
 * @param routePattern 路由匹配模式(Ant 风格,如 /internet/**)
 * @param checkType    校验类型(LOGIN/PERMISSION/ROLE/CHANNEL/SKIP)
 * @param checkValue   校验值(权限码/角色名/渠道名,SKIP 时为空)
 * @param priority     优先级(数值越大优先级越高,匹配时按优先级倒序)
 * @author iam-service
 * @since 2026/7/26
 */
public record RouteRule(
    String routePattern,
    String checkType,
    String checkValue,
    int priority
) {

  /**
   * 从 iam-api 的 DTO 转换为网关本地值对象。
   *
   * <p>仅转换启用(enabled=true)的规则,禁用规则在加载阶段已过滤。
   *
   * @param dto iam-api 返回的路由规则 DTO
   * @return 网关本地值对象
   */
  public static RouteRule from(RouteRuleDTO dto) {
    return new RouteRule(
        dto.routePattern(),
        dto.checkType(),
        dto.checkValue(),
        dto.priority()
    );
  }
}
