package com.example.iam.domain.authorization.repository;

import com.example.iam.domain.authorization.aggregate.root.RouteRule;
import com.example.iam.types.RouteRuleId;
import com.example.shared.domain.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 路由规则聚合根仓储接口。
 *
 * <p>定义路由规则的查询语义,实现位于 {@code iam-infrastructure} 层。
 * demo-gateway 启动时通过本接口加载所有启用的路由规则用于请求路径匹配鉴权。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public interface RouteRuleRepository extends Repository<RouteRule, RouteRuleId> {

  /**
   * 按路由匹配模式查找规则(用于唯一性校验)。
   *
   * @param routePattern 路由匹配模式(Ant 风格)
   * @return 路由规则(可能为空)
   */
  Optional<RouteRule> findByRoutePattern(String routePattern);

  /**
   * 查询所有启用的路由规则(网关启动时加载使用)。
   *
   * <p>返回结果按 priority 倒序排列,以便网关按优先级匹配。
   *
   * @return 启用的路由规则列表
   */
  List<RouteRule> findAllEnabled();

  /**
   * 查询所有路由规则(管理后台使用)。
   *
   * @return 路由规则列表
   */
  List<RouteRule> findAll();
}
