package com.example.gateway.security;

import com.example.iam.api.RouteRuleApi;
import com.example.iam.api.dto.RouteRuleDTO;
import com.example.iam.api.query.ListRouteRulesQuery;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link RouteRuleLoader} 路由规则加载器单元测试。
 *
 * <p>覆盖 {@link RouteRuleLoader#loadRules()} 的缓存命中/未命中、远程调用异常降级、
 * ApiResult 失败/data 为 null/items 为 null 等场景,以及 {@link RouteRuleLoader#refresh()} 强制刷新。
 *
 * <p>降级路径(loadFromRemote 异常时返回上次缓存)需通过反射调用 private loadFromRemote,
 * 因 loadRules 在缓存有值时不会调用 loadFromRemote,无法通过公共 API 触发该降级场景。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RouteRuleLoader 路由规则加载器测试")
class RouteRuleLoaderTest {

  private static final String CACHE_KEY = "ENABLED_ROUTE_RULES";

  @Mock
  private RouteRuleApi routeRuleApi;

  private RouteRuleLoader loader;

  @BeforeEach
  void setUp() {
    loader = new RouteRuleLoader(routeRuleApi);
  }

  private ApiResult<PageData<RouteRuleDTO>> successResult(List<RouteRuleDTO> items) {
    return ApiResult.success(new PageData<>(items.size(), 0, items.size(), false, items));
  }

  @SuppressWarnings("unchecked")
  private Cache<String, List<RouteRule>> getCache() throws Exception {
    Field field = RouteRuleLoader.class.getDeclaredField("ruleCache");
    field.setAccessible(true);
    return (Cache<String, List<RouteRule>>) field.get(loader);
  }

  @SuppressWarnings("unchecked")
  private List<RouteRule> invokeLoadFromRemote() throws Exception {
    Method method = RouteRuleLoader.class.getDeclaredMethod("loadFromRemote");
    method.setAccessible(true);
    return (List<RouteRule>) method.invoke(loader);
  }

  @Nested
  @DisplayName("loadRules 加载路由规则")
  class LoadRules {

    @Test
    @DisplayName("缓存未命中: 调用 routeRuleApi.list 并返回按 priority 倒序排序的列表")
    void cacheMissCallsApiAndSortsByPriorityDesc() {
      RouteRuleDTO dto1 = new RouteRuleDTO(1L, "/a/**", "LOGIN", "", null, 10, true, null, null, 1L);
      RouteRuleDTO dto2 = new RouteRuleDTO(2L, "/b/**", "PERMISSION", "biz:handle", null, 100, true, null, null, 1L);
      RouteRuleDTO dto3 = new RouteRuleDTO(3L, "/c/**", "ROLE", "admin", null, 50, true, null, null, 1L);
      when(routeRuleApi.list(any(ListRouteRulesQuery.class)))
        .thenReturn(successResult(List.of(dto1, dto2, dto3)));

      List<RouteRule> rules = loader.loadRules();

      assertThat(rules).hasSize(3);
      assertThat(rules.get(0).routePattern()).isEqualTo("/b/**");
      assertThat(rules.get(0).priority()).isEqualTo(100);
      assertThat(rules.get(1).routePattern()).isEqualTo("/c/**");
      assertThat(rules.get(1).priority()).isEqualTo(50);
      assertThat(rules.get(2).routePattern()).isEqualTo("/a/**");
      assertThat(rules.get(2).priority()).isEqualTo(10);
      verify(routeRuleApi, times(1)).list(any(ListRouteRulesQuery.class));
    }

    @Test
    @DisplayName("缓存命中: 不调用 routeRuleApi.list")
    void cacheHitDoesNotCallApi() {
      RouteRuleDTO dto = new RouteRuleDTO(1L, "/a/**", "LOGIN", "", null, 10, true, null, null, 1L);
      when(routeRuleApi.list(any(ListRouteRulesQuery.class)))
        .thenReturn(successResult(List.of(dto)));

      List<RouteRule> firstCall = loader.loadRules();
      assertThat(firstCall).hasSize(1);

      List<RouteRule> secondCall = loader.loadRules();
      assertThat(secondCall).hasSize(1);
      assertThat(secondCall).isEqualTo(firstCall);

      verify(routeRuleApi, times(1)).list(any(ListRouteRulesQuery.class));
    }

    @Test
    @DisplayName("ApiResult 失败: 返回空列表")
    void apiResultFailureReturnsEmpty() {
      when(routeRuleApi.list(any(ListRouteRulesQuery.class)))
        .thenReturn(ApiResult.failure("SERVICE.IAM.0001", "iam 不可用"));

      List<RouteRule> rules = loader.loadRules();

      assertThat(rules).isEmpty();
    }

    @Test
    @DisplayName("ApiResult.data 为 null: 返回空列表")
    void apiResultDataNullReturnsEmpty() {
      when(routeRuleApi.list(any(ListRouteRulesQuery.class)))
        .thenReturn(ApiResult.success(null));

      List<RouteRule> rules = loader.loadRules();

      assertThat(rules).isEmpty();
    }

    @Test
    @DisplayName("pageData.items 为 null: 返回空列表")
    void pageDataItemsNullReturnsEmpty() {
      PageData<RouteRuleDTO> pageData = new PageData<>(0, 0, 0, false, null);
      when(routeRuleApi.list(any(ListRouteRulesQuery.class)))
        .thenReturn(ApiResult.success(pageData));

      List<RouteRule> rules = loader.loadRules();

      assertThat(rules).isEmpty();
    }

    @Test
    @DisplayName("远程调用异常且无缓存: 返回空列表(降级)")
    void remoteExceptionNoCacheReturnsEmpty() {
      when(routeRuleApi.list(any(ListRouteRulesQuery.class)))
        .thenThrow(new RuntimeException("iam-service down"));

      List<RouteRule> rules = loader.loadRules();

      assertThat(rules).isEmpty();
    }

    @Test
    @DisplayName("远程调用异常且有缓存: 返回上次缓存(降级)")
    void remoteExceptionWithCacheReturnsCached() throws Exception {
      RouteRuleDTO dto = new RouteRuleDTO(1L, "/a/**", "LOGIN", "", null, 10, true, null, null, 1L);
      List<RouteRule> cachedRules = List.of(RouteRule.from(dto));
      getCache().put(CACHE_KEY, cachedRules);

      when(routeRuleApi.list(any(ListRouteRulesQuery.class)))
        .thenThrow(new RuntimeException("iam-service down"));

      List<RouteRule> result = invokeLoadFromRemote();

      assertThat(result).isEqualTo(cachedRules);
    }
  }

  @Nested
  @DisplayName("refresh 强制刷新缓存")
  class Refresh {

    @Test
    @DisplayName("refresh 后再次 loadRules 会重新调用 api")
    void refreshTriggersRemoteReload() {
      RouteRuleDTO dto1 = new RouteRuleDTO(1L, "/a/**", "LOGIN", "", null, 10, true, null, null, 1L);
      when(routeRuleApi.list(any(ListRouteRulesQuery.class)))
        .thenReturn(successResult(List.of(dto1)));

      loader.loadRules();
      verify(routeRuleApi, times(1)).list(any(ListRouteRulesQuery.class));

      loader.refresh();
      loader.loadRules();
      verify(routeRuleApi, times(2)).list(any(ListRouteRulesQuery.class));
    }
  }
}
