package com.example.shared.permission;

/**
 * 权限本地缓存失效端口（S-05）。
 * <p>
 * 失效监听器只依赖本端口，不依赖具体的 {@link HttpExchangePermissionExecutor}；
 * 其他执行器/缓存实现（本地缓存、无缓存、测试替身）均可复用同一条失效链。
 *
 * @author shared-permission-core
 */
public interface PermissionCachePort {

  /**
   * 失效指定账号的缓存。
   *
   * @param accountId 账号 ID
   */
  void invalidate(String accountId);

  /**
   * 失效全部权限缓存。
   */
  void invalidateAll();
}
