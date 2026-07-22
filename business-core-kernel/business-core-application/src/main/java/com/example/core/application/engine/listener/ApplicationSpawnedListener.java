package com.example.core.application.engine.listener;

import com.example.core.application.engine.service.FlowOrchestrationService;
import com.example.core.domain.business.event.ApplicationSpawnedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * ApplicationSpawnedListener
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/17 13:42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationSpawnedListener {

  private final FlowOrchestrationService orchestrationService;

  /**
   * 监听申请单裂变孵化事件。
   * <p>
   * 核心注解解析：
   * 1. @TransactionalEventListener(phase = AFTER_COMMIT):
   * 【致命级保护】：必须等 BusinessFormAppService 的保存事务彻底 COMMIT 之后，这个监听器才会被触发！
   * 如果不加这个，异步线程可能去查库时，申请单还没插到数据库里，报“找不到记录”。
   * 2. @Async:
   * 交由 Spring 内部的线程池（如 TaskExecutor）并发执行。每个申请单的 advanceStep 都在独立的线程和全新独立的事务中运行。
   */
  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onApplicationSpawned(ApplicationSpawnedEvent event) {
    log.info("监听到新生申请单 {}，将其推入流程编排引擎...", event.applicationId());

    try {
      // 点火启动：引擎接管，开始跑 FORM_DETAIL_INGESTION 等步骤
      orchestrationService.advanceStep(event.applicationId());
    } catch (Exception e) {
      // 完美隔离：这里的任何失败，都只会影响这一个申请单，绝对不会拉着 Form 和其他申请单一起回滚！
      log.error("新生申请单 {} 推入引擎执行失败，需要人工干预/重试补偿", event.applicationId(), e);
    }
  }
}
