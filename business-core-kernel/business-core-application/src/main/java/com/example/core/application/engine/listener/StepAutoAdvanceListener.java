package com.example.core.application.engine.listener;

import com.example.core.application.engine.service.FlowOrchestrationService;
import com.example.core.domain.engine.event.StepEnteredEvent;
import com.example.core.domain.engine.gateway.BusinessConfigGateway;
import com.example.core.domain.engine.aggregate.valueobject.config.StepRouteConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/15 18:54
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StepAutoAdvanceListener {

  private final FlowOrchestrationService orchestrationService;
  private final BusinessConfigGateway configQueryGateway;

  /**
   * 监听步骤进入事件，判断是否需要自动流转
   */
  @Async
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onStepEntered(StepEnteredEvent event) {
    // 1. 获取新进入的步骤配置
    StepRouteConfig config = configQueryGateway.getNextStep(null, event.currentStep());

    // 2. 【核心修改】如果是系统任务，引擎自动履约；如果是用户任务，自动停机。
    if (config.taskType() == StepRouteConfig.StepTaskType.SYSTEM_TASK) {
      log.info("新节点 [{}] 为 SYSTEM_TASK，引擎自动推进...", event.currentStep());
      orchestrationService.advanceStep(event.applicationId());
    } else {
      log.info("新节点 [{}] 为 USER_TASK，引擎挂起，等待 BFF 层接口手动唤醒。", event.currentStep());
    }
  }
}
