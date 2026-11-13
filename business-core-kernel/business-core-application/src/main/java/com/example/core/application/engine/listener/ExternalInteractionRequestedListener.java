package com.example.core.application.engine.listener;

import com.example.core.application.engine.dispatch.ExternalInteractionDispatcher;
import com.example.core.domain.engine.event.ExternalInteractionRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 外部协作登记后尽快派发（spec §9.2 阶段 B / §19.2）。
 * <p>
 * 领域事件经 EventBus 的 Spring 本地分发器投递，AFTER_COMMIT 确保协作记录
 * 已随业务状态一起提交后才派发；{@code @Async} 使派发脱离原事务上下文，
 * 与 {@code ExternalInteractionRecoveryJob} 的兜底扫描形成双通道。
 * 统一委托 {@link ExternalInteractionDispatcher#dispatchPending()}（含认领与事务外调用），
 * 不再单独调用仅认领的 {@code claimPendingDispatch}，避免竞争 PENDING_DISPATCH。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalInteractionRequestedListener {

  private final ExternalInteractionDispatcher dispatcher;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onInteractionRequested(ExternalInteractionRequestedEvent event) {
    log.info("外部协作已登记，尽快派发待派发协作: interactionId={}, requestNo={}",
      event.interactionId(), event.requestNo());
    dispatcher.dispatchPending();
  }
}
