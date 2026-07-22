package com.example.core.application.engine.step.handler;

import com.example.approval.api.ApprovalFlowApi;
import com.example.approval.api.ApprovalInstanceApi;
import com.example.approval.api.dto.ApprovalFlowDTO;
import com.example.approval.api.request.MatchApprovalFlowRequest;
import com.example.approval.api.request.StartApprovalRequest;
import com.example.approval.api.response.ApprovalInstanceIdResponse;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.StepExecutionStatus;
import com.example.core.domain.engine.spi.StepActionHandler;
import com.example.shared.web.core.api.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 通用步骤处理器：调用 approval-service 发起审批
 * <p>
 * <b>【职责边界】</b>
 * <p>本处理器属于核心编排域 (kernel)，是一个开箱即用的标准化组件。
 * 通过 {@link ApprovalFlowApi} 匹配审批流，再通过 {@link ApprovalInstanceApi} 启动审批实例，
 * 并将流程挂起等待审批回调唤醒。
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

  private final ApprovalFlowApi approvalFlowApi;
  private final ApprovalInstanceApi approvalInstanceApi;

  @Override
  public String handlerName() {
    return "approvalSubmissionHandler";
  }

  @Override
  public StepExecutionStatus execute(BusinessApplication app, BusinessMetaContext context) {
    log.info("开始派发审批任务, applicationId={}", app.id());

    // 1. 匹配审批流
    ApprovalFlowDTO matchedFlow = matchApprovalFlow(app, context);
    if (matchedFlow == null) {
      log.error("匹配审批流失败, applicationId={}", app.id());
      return StepExecutionStatus.FAILED;
    }

    // 2. 启动审批实例
    String businessNo = app.id().value();
    String businessType = resolveBusinessType(context);
    String initiator = app.createdBy().value();
    StartApprovalRequest startReq = new StartApprovalRequest(
      matchedFlow.flowId(),
      businessNo,
      businessType,
      initiator
    );

    ApiResult<ApprovalInstanceIdResponse> startResult;
    try {
      startResult = approvalInstanceApi.start(startReq);
    } catch (Exception e) {
      log.error("调用 approval-service 启动审批实例异常, applicationId={}", app.id(), e);
      return StepExecutionStatus.FAILED;
    }

    if (startResult == null || !startResult.isSuccess() || startResult.data() == null) {
      log.error("启动审批实例失败, applicationId={}, result={}", app.id(), startResult);
      return StepExecutionStatus.FAILED;
    }

    log.info("已成功派发审批任务, applicationId={}, flowId={}, instanceId={}",
      app.id(), matchedFlow.flowId(), startResult.data().instanceId());

    // 3. 挂起引擎，等待 approval-service 完成审批后通过事件回调唤醒
    return StepExecutionStatus.SUSPEND_ASYNC_WAIT;
  }

  /**
   * 调用 approval-service 匹配审批流，返回匹配到的审批流 DTO；失败返回 null
   */
  private ApprovalFlowDTO matchApprovalFlow(BusinessApplication app, BusinessMetaContext context) {
    MatchApprovalFlowRequest matchReq = new MatchApprovalFlowRequest(
      resolveBusinessType(context),
      resolveAccountManagerCode(context),
      null
    );

    ApiResult<ApprovalFlowDTO> matchResult;
    try {
      matchResult = approvalFlowApi.match(matchReq);
    } catch (Exception e) {
      log.error("调用 approval-service 匹配审批流异常, applicationId={}", app.id(), e);
      return null;
    }

    if (matchResult == null || !matchResult.isSuccess() || matchResult.data() == null) {
      log.error("匹配审批流失败, applicationId={}, result={}", app.id(), matchResult);
      return null;
    }
    return matchResult.data();
  }

  private String resolveBusinessType(BusinessMetaContext context) {
    return context.businessType() != null ? context.businessType().name() : null;
  }

  private String resolveAccountManagerCode(BusinessMetaContext context) {
    return context.accountManager() != null ? context.accountManager().getValue() : null;
  }
}
