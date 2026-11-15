package com.example.core.application.engine.job;

import com.example.core.application.business.service.BusinessBatchAppService;
import com.example.core.application.engine.dispatch.ExternalInteractionDispatcher;
import com.example.core.application.engine.service.ExternalInteractionRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;

/**
 * 外部协作恢复兜底任务（spec §19.2）。
 * <p>
 * 周期性执行派发与恢复动作；AFTER_COMMIT 监听器负责「尽快派发」，本任务负责
 * 「兜底扫描」，二者均通过 {@link ExternalInteractionDispatcher}（派发）、
 * {@link ExternalInteractionRecoveryService}（回收/重试/对账）与
 * {@link BusinessBatchAppService}（批次统计重算）落地，
 * 单类动作异常彼此隔离，不阻断其余动作。
 * <p>
 * 调度依赖宿主的 {@code @EnableScheduling}（由 shared-event-starter 提供）；
 * 可通过 {@code core.kernel.recovery.enabled=false} 关闭装配。
 */
@Slf4j
@RequiredArgsConstructor
public class ExternalInteractionRecoveryJob {

  private static final long STALE_MINUTES = 30L;

  private final ExternalInteractionDispatcher dispatcher;
  private final ExternalInteractionRecoveryService recoveryService;
  private final BusinessBatchAppService batchAppService;

  @Scheduled(fixedDelayString = "${core.kernel.recovery.fixed-delay:60000}")
  public void recover() {
    LocalDateTime now = LocalDateTime.now();
    safeRun("派发待派发协作", dispatcher::dispatchPending);
    safeRun("回收滞留派发协作",
      () -> recoveryService.reclaimStaleDispatching(now.minusMinutes(STALE_MINUTES)));
    safeRun("处理到期重试", () -> recoveryService.processDueRetries(now));
    safeRun("UNKNOWN 对账", recoveryService::reconcileUnknown);
    safeRun("重算批次统计", batchAppService::reconcileAllActive);
  }

  private void safeRun(String label, Runnable action) {
    try {
      action.run();
    } catch (Exception e) {
      log.error("外部协作恢复任务 [{}] 执行异常", label, e);
    }
  }
}
