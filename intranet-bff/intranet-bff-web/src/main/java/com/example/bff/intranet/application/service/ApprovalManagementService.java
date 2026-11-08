package com.example.bff.intranet.application.service;

import com.example.approval.api.ApprovalFlowApi;
import com.example.approval.api.ApprovalInstanceApi;
import com.example.approval.api.dto.ApprovalFlowDTO;
import com.example.approval.api.dto.ApprovalInstanceDTO;
import com.example.approval.api.dto.ApprovalRecordDTO;
import com.example.approval.api.dto.PendingApprovalDTO;
import com.example.approval.api.request.*;
import com.example.approval.api.response.ApprovalFlowIdResponse;
import com.example.approval.api.response.ApprovalInstanceIdResponse;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 审批管理服务
 *
 * <p>透明转发到 approval-service。{@link ApprovalFlowApi} 和 {@link ApprovalInstanceApi}
 * 由 {@code httpexchange-spring-boot-autoconfigure} 根据 yaml 配置自动创建代理 Bean。
 *
 * @author bff
 */
@Service
public class ApprovalManagementService {

  private final ApprovalFlowApi approvalFlowApi;
  private final ApprovalInstanceApi approvalInstanceApi;

  public ApprovalManagementService(ApprovalFlowApi approvalFlowApi,
                                   ApprovalInstanceApi approvalInstanceApi) {
    this.approvalFlowApi = approvalFlowApi;
    this.approvalInstanceApi = approvalInstanceApi;
  }

  // ===== 审批流管理（6 个） =====

  public ApiResult<ApprovalFlowIdResponse> createFlow(CreateApprovalFlowRequest request) {
    return approvalFlowApi.create(request);
  }

  public ApiResult<Void> updateFlow(UpdateApprovalFlowRequest request) {
    return approvalFlowApi.update(request);
  }

  public ApiResult<Void> deprecateFlow(DeprecateApprovalFlowRequest request) {
    return approvalFlowApi.deprecate(request);
  }

  public ApiResult<ApprovalFlowDTO> getFlow(GetApprovalFlowRequest request) {
    return approvalFlowApi.get(request);
  }

  public ApiResult<PageData<ApprovalFlowDTO>> listFlows(ListApprovalFlowsRequest request) {
    return approvalFlowApi.list(request);
  }

  public ApiResult<ApprovalFlowDTO> matchFlow(MatchApprovalFlowRequest request) {
    return approvalFlowApi.match(request);
  }

  // ===== 审批实例管理（8 个） =====

  public ApiResult<ApprovalInstanceIdResponse> startInstance(StartApprovalRequest request) {
    return approvalInstanceApi.start(request);
  }

  public ApiResult<Void> approveInstance(ApproveRequest request) {
    return approvalInstanceApi.approve(request);
  }

  public ApiResult<Void> rejectInstance(RejectRequest request) {
    return approvalInstanceApi.reject(request);
  }

  public ApiResult<Void> transferInstance(TransferRequest request) {
    return approvalInstanceApi.transfer(request);
  }

  public ApiResult<Void> withdrawInstance(WithdrawRequest request) {
    return approvalInstanceApi.withdraw(request);
  }

  public ApiResult<ApprovalInstanceDTO> getInstance(GetApprovalInstanceRequest request) {
    return approvalInstanceApi.get(request);
  }

  public ApiResult<PageData<PendingApprovalDTO>> listMyPending(ListMyPendingApprovalsRequest request) {
    return approvalInstanceApi.listMyPending(request);
  }

  public ApiResult<List<ApprovalRecordDTO>> getHistory(GetApprovalHistoryRequest request) {
    return approvalInstanceApi.getHistory(request);
  }
}
