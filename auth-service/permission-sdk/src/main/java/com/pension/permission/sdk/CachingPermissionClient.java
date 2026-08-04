package com.pension.permission.sdk;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 装饰器：给任意PermissionClient实现加一层本地短TTL缓存，避免同一个身份+计划+业务的组合
 * 在短时间内被高频请求反复打到Permission服务。
 * <p>
 * 紧急撤销场景(账号冻结/Grant撤销)不能靠TTL兜底：Permission服务通过outbox+消息队列
 * 广播GrantRevoked/AccountFrozen等事件，业务服务订阅后应该调用invalidate(accountId)
 * 立即清掉该身份的所有缓存项，不等TTL自然过期——具体订阅哪个MQ、用什么客户端，
 * 由各业务服务自己决定，这个类只负责被动接受失效通知。
 */
public final class CachingPermissionClient implements PermissionClient {

  private final PermissionClient delegate;
  private final Duration ttl;
  private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

  public CachingPermissionClient(PermissionClient delegate, Duration ttl) {
    this.delegate = delegate;
    this.ttl = ttl;
  }

  @Override
  public boolean checkPermission(String accountId, String planId, String businessCode, String actionCode) {
    String key = cacheKey(accountId, planId, businessCode, actionCode);
    Instant now = Instant.now();

    CacheEntry cached = cache.get(key);
    if (cached != null && cached.expiresAt().isAfter(now)) {
      return cached.allowed();
    }

    boolean allowed = delegate.checkPermission(accountId, planId, businessCode, actionCode);
    cache.put(key, new CacheEntry(allowed, now.plus(ttl)));
    return allowed;
  }

  /**
   * 收到该身份相关的撤销类事件后调用，清掉这个身份名下的全部缓存项(不管是哪个计划/业务)
   */
  public void invalidate(String accountId) {
    String prefix = accountId + "|";
    cache.keySet().removeIf(k -> k.startsWith(prefix));
  }

  /**
   * 极端情况下(比如怀疑缓存跟服务端不一致)整体清空
   */
  public void invalidateAll() {
    cache.clear();
  }

  private String cacheKey(String accountId, String planId, String businessCode, String actionCode) {
    return accountId + "|" + planId + "|" + businessCode + "|" + (actionCode == null ? "" : actionCode);
  }

  private record CacheEntry(boolean allowed, Instant expiresAt) {
  }
}
