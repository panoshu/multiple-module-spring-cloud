package com.example.core.application.engine.listener;

import com.example.core.application.engine.service.FlowOrchestrationService;
import com.example.core.domain.engine.event.StepEnteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 监听步骤进入事件，按版本化流程定义判断是否需要自动流转。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StepAutoAdvanceListener {

  private final FlowOrchestrationService orchestrationService;

  /**
   * 系统任务自动履约，用户任务挂起等待 BFF 层手动唤醒。
   */
  @Async
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onStepEntered(StepEnteredEvent event) {
    if (orchestrationService.isSystemTask(event.applicationId())) {
      log.info("新节点 [{}] 为 SYSTEM_TASK，引擎自动推进...", event.currentStep());
      orchestrationService.advanceStep(event.applicationId());
    } else {
      log.info("新节点 [{}] 为 USER_TASK，引擎挂起，等待 BFF 层接口手动唤醒。", event.currentStep());
    }
  }
}
