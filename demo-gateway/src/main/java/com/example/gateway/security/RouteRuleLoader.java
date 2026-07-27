package com.example.gateway.security;

import com.example.iam.api.RouteRuleApi;
import com.example.iam.api.query.ListRouteRulesQuery;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import com.example.shared.web.core.dto.PageQuery;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 路由规则加载器 - 从 iam-service 拉取动态鉴权规则并本地缓存。
 *
 * <p>对应设计文档 4.5 节:网关启动后定期从 iam-service 加载启用的 RouteRule,
 * 供 SaTokenGatewayConfiguration 在 SaReactorFilter 中按 priority 倒序匹配。
 *
 * <p>缓存策略:
 * <ul>
 *   <li>使用 Caffeine 本地缓存,TTL 5 分钟</li>
 *   <li>首次调用或缓存失效时同步加载 iam-service 返回的规则</li>
 *   <li>iam-service 不可用时返回上次缓存(降级);无缓存时返回空列表(不拦截)</li>
 * </ul>
 *
 * <p>降级说明:网关层路由规则缺失时,仅依赖渠道前缀做登录校验,
 * 不做路由级权限校验(下游服务仍会做细粒度权限校验,保证安全)。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Component
public class RouteRuleLoader {

  /** 缓存键(单例缓存,使用固定键) */
  private static final String CACHE_KEY = "ENABLED_ROUTE_RULES";

  /** 缓存 TTL */
  private static final Duration CACHE_TTL = Duration.ofMinutes(5);

  /** 单页最大条数(覆盖全部路由规则) */
  private static final int PAGE_SIZE = 100;

  private final RouteRuleApi routeRuleApi;

  private final Cache<String, List<RouteRule>> ruleCache;

  public RouteRuleLoader(RouteRuleApi routeRuleApi) {
    this.routeRuleApi = routeRuleApi;
    this.ruleCache = Caffeine.newBuilder()
        .expireAfterWrite(CACHE_TTL)
        .maximumSize(1)
        .build();
  }

  /**
   * 加载启用的路由规则列表(按 priority 倒序)。
   *
   * <p>优先从本地缓存读取;缓存未命中时调用 iam-service 拉取。
   * iam-service 调用异常时返回上次缓存;无缓存时返回空列表。
   *
   * @return 启用的路由规则列表(按 priority 倒序)
   */
  public List<RouteRule> loadRules() {
    List<RouteRule> cached = ruleCache.getIfPresent(CACHE_KEY);
    if (cached != null) {
      return cached;
    }
    return loadFromRemote();
  }

  /**
   * 强制刷新缓存(供 iam-service 规则变更后主动触发)。
   */
  public void refresh() {
    ruleCache.invalidate(CACHE_KEY);
    loadFromRemote();
  }

  private List<RouteRule> loadFromRemote() {
    try {
      ListRouteRulesQuery query = new ListRouteRulesQuery(
          null,  // routePattern 模糊匹配(空=全部)
          null,  // checkType 过滤(空=全部)
          Boolean.TRUE,  // 仅启用
          PageQuery.firstPage(PAGE_SIZE)
      );
      ApiResult<PageData<com.example.iam.api.dto.RouteRuleDTO>> result = routeRuleApi.list(query);

      List<RouteRule> rules = new ArrayList<>();
      if (result != null && result.isSuccess() && result.data() != null) {
        PageData<com.example.iam.api.dto.RouteRuleDTO> pageData = result.data();
        if (pageData.items() != null) {
          for (com.example.iam.api.dto.RouteRuleDTO dto : pageData.items()) {
            rules.add(RouteRule.from(dto));
          }
        }
        // 若还有更多数据,继续分页加载(路由规则通常较少,大多数场景一次加载完成)
        int loaded = rules.size();
        int total = pageData.totalCount();
        if (pageData.hasMore() && total > loaded) {
          log.warn("[RouteRuleLoader] 路由规则总数 {} 超过单页 {},仅加载前 {} 条,建议扩大 PAGE_SIZE",
              total, PAGE_SIZE, loaded);
        }
      }

      // 按 priority 倒序(数值越大优先级越高)
      rules.sort(Comparator.comparingInt(RouteRule::priority).reversed());
      ruleCache.put(CACHE_KEY, rules);
      log.info("[RouteRuleLoader] 加载路由规则成功: count={}", rules.size());
      return rules;
    } catch (Exception e) {
      log.error("[RouteRuleLoader] 加载路由规则失败,使用缓存或空列表降级", e);
      List<RouteRule> fallback = ruleCache.getIfPresent(CACHE_KEY);
      return fallback != null ? fallback : List.of();
    }
  }
}
