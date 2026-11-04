package com.example.auth.infrastructure.permission.cache;

import com.example.core.types.UserNo;
import com.example.shared.security.permission.PermissionChangedEvent;
import com.example.auth.domain.permission.spi.PermissionCacheStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * auth 进程内的权限失效处理器（P0 一致性收口：H2 发端 + H3）.
 *
 * <p>监听 {@link PermissionChangedEvent}（auth 应用服务在 Grant 审批/身份分配/权限码注册后发布），
 * 一个监听器做两件事：
 * <ul>
 *   <li><b>H3 本地收口</b>：清除 auth 自身的 Redis 双区权限缓存
 *       （{@code auth:perm:cache:{accountId}}，前端权限集/菜单树的数据源）——
 *       修复"后端 check 立即拒绝、前端菜单还亮着最长 5min"的前后端不一致窗口</li>
 *   <li><b>H2 跨服务中继</b>：把事件序列化广播到 Redis pub/sub topic
 *       {@code event.PermissionChangedEvent}（与 shared-event-core 的
 *       RedisEventDispatcher topic 约定一致）——各业务服务的
 *       {@code PermissionInvalidationRedisSubscriber} 订阅后清本地 Caffeine</li>
 * </ul>
 *
 * <p>失效操作天然幂等（清缓存无副作用），无需去重/防回声；事件丢失时各端 TTL 兜底.
 *
 * @author auth-infrastructure
 */
@Slf4j
@Component
public class PermissionInvalidationListener {

  /**
   * Redis pub/sub topic（与 RedisEventDispatcher 的 event.{SimpleClassName} 约定一致）.
   */
  public static final String INVALIDATION_TOPIC = "event.PermissionChangedEvent";

  private final PermissionCacheStore cacheStore;
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  public PermissionInvalidationListener(PermissionCacheStore cacheStore,
                                        StringRedisTemplate redisTemplate,
                                        ObjectMapper objectMapper) {
    this.cacheStore = cacheStore;
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
  }

  @EventListener
  public void onPermissionChanged(PermissionChangedEvent event) {
    evictLocal(event);
    relay(event);
  }

  private void evictLocal(PermissionChangedEvent event) {
    try {
      if (event.isGlobal()) {
        cacheStore.clearAll();
        log.info("[auth失效] 全局清除权限缓存: source={}", event.source());
      } else {
        cacheStore.evict(UserNo.of(event.accountId()));
        log.info("[auth失效] 定向清除权限缓存: accountId={}, planId={}",
          event.accountId(), event.planId());
      }
    } catch (UnsupportedOperationException e) {
      log.warn("[auth失效] 当前缓存实现不支持全局清空，回退 TTL 兜底: {}", e.getMessage());
    } catch (Exception e) {
      // fail-soft：本地清除失败不影响中继；TTL 5min 兜底
      log.warn("[auth失效] 本地缓存清除失败，回退 TTL 兜底", e);
    }
  }

  private void relay(PermissionChangedEvent event) {
    try {
      String json = objectMapper.writeValueAsString(event);
      redisTemplate.convertAndSend(INVALIDATION_TOPIC, json);
      log.debug("[auth失效] 已广播至 {}: {}", INVALIDATION_TOPIC, json);
    } catch (Exception e) {
      // fail-soft：广播失败（如 Redis 抖动）回退各服务 TTL 300s 兜底
      log.warn("[auth失效] 跨服务广播失败，各服务回退 TTL 兜底", e);
    }
  }
}
