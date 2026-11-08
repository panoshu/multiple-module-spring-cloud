package com.example.shared.permission;

import com.example.shared.security.permission.PermissionCheckPort;
import com.example.shared.security.permission.PermissionCheckResponse;
import com.example.shared.security.permission.PermissionCheckRequest;
import com.example.shared.web.core.api.ApiResult;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

/**
 * 业务服务默认权限校验执行器：本地缓存 + HttpExchange 调用 auth-service.
 *
 * <p><b>Phase C 缓存分层</b>（spec 2026-11-03-auth-permission-redesign §5）：
 * <ul>
 *   <li>命中本地 Caffeine（键=账号+计划+权限码，TTL 可配，默认 300s）→ 0 RPC</li>
 *   <li>未命中 → 远程校验 → 结果（允许与拒绝）写缓存</li>
 *   <li><b>fail-closed 保持</b>：RPC 异常不缓存、不上抛降级放行——由调用方拒绝</li>
 *   <li>权限变更失效：TTL 兜底；事件驱动失效通道（auth 集成事件 →
 *       {@link #invalidate(String)}）为 Phase D 待接线项</li>
 * </ul>
 *
 * <p>在 auth-service 中被 {@code LocalPermissionExecutor}（@Primary）覆盖。
 *
 * @author shared-permission-starter
 */
@Slf4j
@Component
@ConditionalOnBean(PermissionCheckPort.class)
@ConditionalOnMissingBean(PermissionExecutor.class)
public class HttpExchangePermissionExecutor implements PermissionExecutor {

  private final PermissionCheckPort permissionCheckApi;
  private final Cache<CacheKey, Boolean> permissionCache;

  public HttpExchangePermissionExecutor(PermissionCheckPort permissionCheckApi,
                                        PermissionCacheProperties cacheProperties) {
    this.permissionCheckApi = permissionCheckApi;
    this.permissionCache = cacheProperties.enabled()
      ? Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(cacheProperties.ttlSeconds()))
        .maximumSize(cacheProperties.maximumSize())
        .build()
      : null;
    if (this.permissionCache != null) {
      log.info("[HttpExchangePermissionExecutor] 权限本地缓存已启用: ttl={}s, maximumSize={}",
        cacheProperties.ttlSeconds(), cacheProperties.maximumSize());
    }
  }

  @Override
  public PermissionCheckResult check(PermissionCheckContext context) {
    CacheKey key = new CacheKey(
      context.accountId(), context.planId(), context.businessCode(), context.actionCode());

    if (permissionCache != null) {
      Boolean cached = permissionCache.getIfPresent(key);
      if (cached != null) {
        return cached ? PermissionCheckResult.allow() : PermissionCheckResult.deny("权限不足");
      }
    }

    PermissionCheckRequest request = new PermissionCheckRequest(
      context.accountId(),
      context.planId(),
      context.businessCode(),
      context.actionCode());
    ApiResult<PermissionCheckResponse> result = permissionCheckApi.check(request);

    if (result == null || !result.isSuccess() || result.data() == null) {
      log.warn("[HttpExchangePermissionExecutor] auth-service 响应异常: result={}", result);
      return PermissionCheckResult.deny("auth-service 响应异常");
    }
    boolean allowed = result.data().allowed();
    if (permissionCache != null) {
      permissionCache.put(key, allowed);
    }
    return allowed ? PermissionCheckResult.allow() : PermissionCheckResult.deny("权限不足");
  }

  /**
   * 失效指定账号的全部本地缓存条目（供权限变更事件监听器调用，Phase D 接线）.
   */
  public void invalidate(String accountId) {
    if (permissionCache != null) {
      permissionCache.asMap().keySet()
        .removeIf(key -> Objects.equals(key.accountId(), accountId));
    }
  }

  /**
   * 失效全部本地缓存（供权限规则全局变更事件调用）.
   */
  public void invalidateAll() {
    if (permissionCache != null) {
      permissionCache.invalidateAll();
    }
  }

  @Override
  public boolean isLocalExecution() {
    return false;
  }

  /**
   * 缓存键：账号 + 计划 + 权限码（业务码 + 动作码）.
   */
  record CacheKey(String accountId, String planId, String businessCode, String actionCode) {
  }
}
