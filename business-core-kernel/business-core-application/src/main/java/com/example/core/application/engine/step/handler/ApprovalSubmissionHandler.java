package com.example.core.application.engine.step.handler;

import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.StepExecutionStatus;
import com.example.core.domain.engine.spi.StepActionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 通用审批提交步骤处理器（登记意图，批次 31 改为事务外派发）。
 * <p>
 * 仅登记挂起意图并返回 {@link StepExecutionStatus#SUSPEND_ASYNC_WAIT}；
 * 实际调用 approval-service 由 {@code ExternalInteractionDispatcher} +
 * {@code ApprovalDispatchHandler} 在事务外完成（§9.2 阶段 B）。
 * 审批结果事件回调后由 {@code ApprovalResultEventListener} 推进流程。
 *
 * <p><b>配置</b>：{@code mainProcessor: approvalSubmissionHandler}。</p>
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/17 12:14
 */
@Slf4j
@Component
public class ApprovalSubmissionHandler implements StepActionHandler {

  @Override
  public String handlerName() {
    return "approvalSubmissionHandler";
  }

  @Override
  public StepExecutionStatus execute(BusinessApplication app, BusinessMetaContext context) {
    log.info("登记审批提交意图, applicationId={}", app.id());
    return StepExecutionStatus.SUSPEND_ASYNC_WAIT;
  }
}
