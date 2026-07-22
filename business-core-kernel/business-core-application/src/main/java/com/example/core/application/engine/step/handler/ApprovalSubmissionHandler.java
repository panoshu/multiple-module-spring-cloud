package com.example.core.application.engine.step.handler;

import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.StepExecutionStatus;
import com.example.core.domain.engine.gateway.ApprovalIntegrationGateway;
import com.example.core.domain.engine.spi.StepActionHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 通用步骤处理器：调用 approval-service 发起审批
 * <p>
 * <b>【职责边界】</b>
 * <p>本处理器属于核心编排域 (kernel)，是一个开箱即用的标准化组件。
 * 通过 {@link ApprovalIntegrationGateway} 防腐层发起审批，并将流程挂起等待审批回调唤醒。
 *
 * <b>【配置中心 JSON 配置】</b>
 * <p>将本步骤的 {@code mainProcessor} 配置为 {@code "approvalSubmissionHandler"} 即可启用。
 *
 * <b>【异步唤醒机制】</b>
 * <p>本处理器返回 {@link StepExecutionStatus#SUSPEND_ASYNC_WAIT} 后，引擎会挂起并提交事务。
 * approval-service 完成审批后通过集成事件回调，由监听器驱动引擎进入下一节点。
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/17 12:14
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalSubmissionHandler implements StepActionHandler {

  private final ApprovalIntegrationGateway approvalGateway;

  @Override
  public String handlerName() {
    return "approvalSubmissionHandler";
  }

  @Override
  public StepExecutionStatus execute(BusinessApplication app, BusinessMetaContext context) {
    log.info("开始派发审批任务, applicationId={}", app.id());

    try {
      String instanceId = approvalGateway.startApproval(app);
      log.info("已成功派发审批任务, applicationId={}, instanceId={}", app.id(), instanceId);
      return StepExecutionStatus.SUSPEND_ASYNC_WAIT;
    } catch (Exception e) {
      log.error("派发审批任务失败, applicationId={}", app.id(), e);
      return StepExecutionStatus.FAILED;
    }
  }
}
