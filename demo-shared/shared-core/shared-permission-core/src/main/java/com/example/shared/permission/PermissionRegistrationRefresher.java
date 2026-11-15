package com.example.shared.permission;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 权限自注册 TTL 心跳（Phase D 治理项，spec §7）.
 *
 * <p>问题：{@link PermissionRegistrationRunner} 只在启动时上报一次权限点。
 * auth 侧注册表缺乏续期信号——服务长期运行/重启序列错配后，注册表可能陈旧
 * （CODEBUDDY.md demo-shared §十二.7 遗留问题："上报失败仅 WARN，是否可能在 auth 重启后补注册？"）。
 *
 * <p>机制：单线程调度器按 {@code shared.permission.registration.refresh-interval-ms}
 * （默认 300000ms = 5min，&le;0 禁用）重复调用 {@link PermissionRegistrationRunner#registerNow()}。
 * auth 侧 upsert 幂等：内容不变 no-op，变化覆盖——重注册即 TTL 续期。
 * 心跳失败 fail-soft（WARN），下个周期自然重试。
 *
 * <p>自管理线程（不要求宿主服务开启 {@code @EnableScheduling}），
 * {@link DisposableBean} 优雅关停。
 *
 * @author shared-permission-starter
 */
@Slf4j
public class PermissionRegistrationRefresher implements DisposableBean {

  private final PermissionRegistrationRunner runner;
  private final ScheduledExecutorService scheduler;

  public PermissionRegistrationRefresher(PermissionRegistrationRunner runner,
                                         long refreshIntervalMillis) {
    this.runner = runner;
    if (refreshIntervalMillis <= 0) {
      this.scheduler = null;
      log.info("[PermissionRegistrationRefresher] TTL 心跳已禁用 (refresh-interval-ms={})", refreshIntervalMillis);
      return;
    }
    this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
      Thread thread = new Thread(runnable, "permission-registration-ttl");
      thread.setDaemon(true);
      return thread;
    });
    this.scheduler.scheduleWithFixedDelay(
      this::refresh,
      refreshIntervalMillis,
      refreshIntervalMillis,
      TimeUnit.MILLISECONDS);
    log.info("[PermissionRegistrationRefresher] TTL 心跳已启动: interval={}ms", refreshIntervalMillis);
  }

  private void refresh() {
    try {
      runner.registerNow();
    } catch (Exception e) {
      // fail-soft：不中断调度，下个周期重试
      log.warn("[PermissionRegistrationRefresher] 心跳注册失败，将于下个周期重试", e);
    }
  }

  @Override
  @PreDestroy
  public void destroy() {
    if (scheduler != null) {
      scheduler.shutdownNow();
    }
  }
}
