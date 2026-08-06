package com.pension.permission.infrastructure.permission.cache;

import com.pension.permission.domain.authorization.event.GrantApproved;
import com.pension.permission.domain.authorization.event.GrantRejected;
import com.pension.permission.domain.authorization.event.GrantRevoked;
import com.pension.permission.domain.permission.spi.PermissionCacheStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 权限缓存失效监听器。
 * <p>监听 Grant 变更领域事件，主动清除相关账号的 SessionPermissionCache。
 * <p>本期实现：
 * <ul>
 *   <li>GrantApproved → 失效缓存（精确失效未实现，依赖 TTL 兜底）</li>
 *   <li>GrantRevoked  → 同上</li>
 *   <li>GrantRejected → 不失效（拒绝未生效的 Grant 不影响现有权限）</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionCacheInvalidator {

  private final PermissionCacheStore cacheStore;

  @EventListener
  public void onGrantApproved(GrantApproved event) {
    log.info("Grant 批准事件触发缓存失效: grantId={}", event.grantId());
    log.warn("Grant 事件触发的精确缓存失效未实现，依赖 TTL 自然失效");
  }

  @EventListener
  public void onGrantRevoked(GrantRevoked event) {
    log.info("Grant 撤销事件触发缓存失效: grantId={}", event.grantId());
    log.warn("Grant 事件触发的精确缓存失效未实现，依赖 TTL 自然失效");
  }

  @EventListener
  public void onGrantRejected(GrantRejected event) {
    log.debug("Grant 拒绝事件，不触发缓存失效: grantId={}", event.grantId());
  }
}
