package com.example.core.application.engine.listener;

import com.example.core.application.engine.service.ExternalInteractionRecoveryService;
import com.example.core.domain.engine.event.ExternalInteractionRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 外部协作登记后尽快认领（spec §9.2 阶段 B / §19.2）。
 * <p>
 * 领域事件经 EventBus 的 Spring 本地分发器投递，AFTER_COMMIT 确保协作记录
 * 已随业务状态一起提交后才认领；{@code @Async} 使认领脱离原事务上下文，
 * 与 {@code ExternalInteractionRecoveryJob} 的兜底扫描形成双通道。
 * 本监听器只推进协作状态，不重复触发外部调用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalInteractionRequestedListener {

  private final ExternalInteractionRecoveryService recoveryService;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onInteractionRequested(ExternalInteractionRequestedEvent event) {
    log.info("外部协作已登记，尽快认领待派发协作: interactionId={}, requestNo={}",
      event.interactionId(), event.requestNo());
    recoveryService.claimPendingDispatch();
  }
}
