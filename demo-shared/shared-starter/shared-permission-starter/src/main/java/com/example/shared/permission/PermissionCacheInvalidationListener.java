package com.example.shared.permission;

import com.example.shared.security.permission.PermissionChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;

/**
 * 权限变更事件 → 本地缓存失效监听器（Phase D 失效链接线，spec §5）.
 *
 * <p>消费 {@link PermissionChangedEvent}：
 * <ul>
 *   <li>定向事件（accountId 非空）→ {@link HttpExchangePermissionExecutor#invalidate(String)}</li>
 *   <li>全局事件（accountId 为空）→ {@link HttpExchangePermissionExecutor#invalidateAll()}</li>
 * </ul>
 *
 * <p>演示环境：auth 进程内 Spring 事件直达；生产环境：MQ 消费者转发调用
 * {@link #onPermissionChanged(PermissionChangedEvent)} 同名方法（与审批结果事件消费模式一致）。
 * 事件丢失时由缓存 TTL（默认 300s）兜底，不放大故障面。
 *
 * <p>auth-service 自身被 {@code LocalPermissionExecutor}（@Primary，无本地缓存）覆盖——
 * 本监听器对其无副作用（条件装配排除无缓存的执行器场景）。
 *
 * @author shared-permission-starter
 */
@Slf4j
public class PermissionCacheInvalidationListener {

  private final HttpExchangePermissionExecutor executor;

  public PermissionCacheInvalidationListener(HttpExchangePermissionExecutor executor) {
    this.executor = executor;
  }

  /**
   * Spring 事件入口（生产环境 MQ 消费者亦可直接调用本方法）.
   */
  @EventListener
  public void onPermissionChanged(PermissionChangedEvent event) {
    if (event.isGlobal()) {
      executor.invalidateAll();
      log.info("[PermissionInvalidation] 全局失效触发: source={}, changedAt={}",
        event.source(), event.changedAtMillis());
      return;
    }
    executor.invalidate(event.accountId());
    log.info("[PermissionInvalidation] 账号失效触发: accountId={}, planId={}, source={}",
      event.accountId(), event.planId(), event.source());
  }
}
