package com.example.core.infrastructure.engine.gateway;

import com.example.approval.api.ApprovalFlowApi;
import com.example.approval.api.ApprovalInstanceApi;
import com.example.approval.api.dto.ApprovalFlowDTO;
import com.example.approval.api.dto.ApprovalInstanceDTO;
import com.example.approval.api.request.GetApprovalInstanceRequest;
import com.example.approval.api.request.MatchApprovalFlowRequest;
import com.example.approval.api.request.StartApprovalRequest;
import com.example.approval.api.response.ApprovalInstanceIdResponse;
import com.example.approval.types.ApprovalInstanceId;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.gateway.ApprovalIntegrationGateway;
import com.example.shared.web.core.api.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ApprovalIntegrationGateway 默认实现：调用 approval-service 完成审批集成
 * <p>
 * 本实现属于核心编排域 (kernel) 的基础设施层，通过 {@link ApprovalFlowApi} 和 {@link ApprovalInstanceApi}
 * 这两个 @HttpExchange 接口向 approval-service 发起远程调用。
 *
 * <b>【处理流程】</b>
 * <ol>
 * <li>{@link #startApproval(BusinessApplication)}：先匹配审批流，再启动审批实例，返回实例 ID；</li>
 * <li>{@link #queryApprovalStatus(String)}：根据实例 ID 查询审批状态，返回状态字符串。</li>
 * </ol>
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/14 23:34
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalServiceIntegrationGateway implements ApprovalIntegrationGateway {

  private static final String UNKNOWN_STATUS = "UNKNOWN";

  private final ApprovalFlowApi approvalFlowApi;
  private final ApprovalInstanceApi approvalInstanceApi;

  @Override
  public String startApproval(BusinessApplication application) {
    BusinessMetaContext context = application.buildConfigQueryContext();

    // 1. 匹配审批流
    ApprovalFlowDTO matchedFlow = matchApprovalFlow(application, context);
    if (matchedFlow == null) {
      throw new IllegalStateException("匹配审批流失败, applicationId=" + application.id());
    }

    // 2. 启动审批实例
    StartApprovalRequest startReq = new StartApprovalRequest(
      matchedFlow.flowId(),
      application.id().value(),
      resolveBusinessType(context),
      application.createdBy().value()
    );

    ApiResult<ApprovalInstanceIdResponse> result = approvalInstanceApi.start(startReq);
    if (result == null || !result.isSuccess() || result.data() == null) {
      throw new IllegalStateException("启动审批实例失败, applicationId=" + application.id());
    }

    log.info("已启动审批实例, applicationId={}, flowId={}, instanceId={}",
      application.id(), matchedFlow.flowId(), result.data().instanceId());
    return result.data().instanceId().toString();
  }

  @Override
  public String queryApprovalStatus(String instanceId) {
    ApprovalInstanceId parsedId = parseInstanceId(instanceId);
    if (parsedId == null) {
      return UNKNOWN_STATUS;
    }

    GetApprovalInstanceRequest request = new GetApprovalInstanceRequest(parsedId);
    ApiResult<ApprovalInstanceDTO> result = approvalInstanceApi.get(request);
    if (result == null || !result.isSuccess() || result.data() == null) {
      log.warn("查询审批实例状态失败, instanceId={}, result={}", instanceId, result);
      return UNKNOWN_STATUS;
    }
    return result.data().status();
  }

  /**
   * 调用 approval-service 匹配审批流，返回匹配到的审批流 DTO；失败返回 null
   */
  private ApprovalFlowDTO matchApprovalFlow(BusinessApplication application, BusinessMetaContext context) {
    MatchApprovalFlowRequest matchReq = new MatchApprovalFlowRequest(
      resolveBusinessType(context),
      resolveAccountManagerCode(context),
      null
    );

    ApiResult<ApprovalFlowDTO> result = approvalFlowApi.match(matchReq);
    if (result == null || !result.isSuccess() || result.data() == null) {
      log.error("匹配审批流失败, applicationId={}, result={}", application.id(), result);
      return null;
    }
    return result.data();
  }

  /**
   * 将字符串实例 ID 解析为 ApprovalInstanceId；非法格式返回 null
   */
  private ApprovalInstanceId parseInstanceId(String instanceId) {
    if (instanceId == null || instanceId.isBlank()) {
      return null;
    }
    try {
      return ApprovalInstanceId.of(Long.parseLong(instanceId));
    } catch (NumberFormatException e) {
      log.warn("审批实例 ID 格式非法, instanceId={}", instanceId);
      return null;
    }
  }

  private String resolveBusinessType(BusinessMetaContext context) {
    return context.businessType() != null ? context.businessType().name() : null;
  }

  private String resolveAccountManagerCode(BusinessMetaContext context) {
    return context.accountManager() != null ? context.accountManager().getValue() : null;
  }
}
