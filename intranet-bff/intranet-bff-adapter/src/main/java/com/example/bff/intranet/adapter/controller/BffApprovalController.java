package com.example.bff.intranet.adapter.controller;

import com.example.approval.api.dto.ApprovalFlowDTO;
import com.example.approval.api.dto.ApprovalInstanceDTO;
import com.example.approval.api.dto.ApprovalRecordDTO;
import com.example.approval.api.dto.PendingApprovalDTO;
import com.example.approval.api.request.ApproveRequest;
import com.example.approval.api.request.CreateApprovalFlowRequest;
import com.example.approval.api.request.DeprecateApprovalFlowRequest;
import com.example.approval.api.request.GetApprovalFlowRequest;
import com.example.approval.api.request.GetApprovalHistoryRequest;
import com.example.approval.api.request.GetApprovalInstanceRequest;
import com.example.approval.api.request.ListApprovalFlowsRequest;
import com.example.approval.api.request.ListMyPendingApprovalsRequest;
import com.example.approval.api.request.MatchApprovalFlowRequest;
import com.example.approval.api.request.RejectRequest;
import com.example.approval.api.request.StartApprovalRequest;
import com.example.approval.api.request.TransferRequest;
import com.example.approval.api.request.UpdateApprovalFlowRequest;
import com.example.approval.api.request.WithdrawRequest;
import com.example.approval.api.response.ApprovalFlowIdResponse;
import com.example.approval.api.response.ApprovalInstanceIdResponse;
import com.example.bff.intranet.api.BffApprovalApi;
import com.example.bff.intranet.application.service.ApprovalManagementService;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 审批管理 Controller
 *
 * <p>透明转发到 approval-service，每个方法直接委托给 {@link ApprovalManagementService}。
 *
 * @author bff
 */
@RestController
public class BffApprovalController implements BffApprovalApi {

    private final ApprovalManagementService approvalManagementService;

    public BffApprovalController(ApprovalManagementService approvalManagementService) {
        this.approvalManagementService = approvalManagementService;
    }

    @Override
    public ApiResult<ApprovalFlowIdResponse> createFlow(CreateApprovalFlowRequest request) {
        return approvalManagementService.createFlow(request);
    }

    @Override
    public ApiResult<Void> updateFlow(UpdateApprovalFlowRequest request) {
        return approvalManagementService.updateFlow(request);
    }

    @Override
    public ApiResult<Void> deprecateFlow(DeprecateApprovalFlowRequest request) {
        return approvalManagementService.deprecateFlow(request);
    }

    @Override
    public ApiResult<ApprovalFlowDTO> getFlow(GetApprovalFlowRequest request) {
        return approvalManagementService.getFlow(request);
    }

    @Override
    public ApiResult<PageData<ApprovalFlowDTO>> listFlows(ListApprovalFlowsRequest request) {
        return approvalManagementService.listFlows(request);
    }

    @Override
    public ApiResult<ApprovalFlowDTO> matchFlow(MatchApprovalFlowRequest request) {
        return approvalManagementService.matchFlow(request);
    }

    @Override
    public ApiResult<ApprovalInstanceIdResponse> startInstance(StartApprovalRequest request) {
        return approvalManagementService.startInstance(request);
    }

    @Override
    public ApiResult<Void> approveInstance(ApproveRequest request) {
        return approvalManagementService.approveInstance(request);
    }

    @Override
    public ApiResult<Void> rejectInstance(RejectRequest request) {
        return approvalManagementService.rejectInstance(request);
    }

    @Override
    public ApiResult<Void> transferInstance(TransferRequest request) {
        return approvalManagementService.transferInstance(request);
    }

    @Override
    public ApiResult<Void> withdrawInstance(WithdrawRequest request) {
        return approvalManagementService.withdrawInstance(request);
    }

    @Override
    public ApiResult<ApprovalInstanceDTO> getInstance(GetApprovalInstanceRequest request) {
        return approvalManagementService.getInstance(request);
    }

    @Override
    public ApiResult<PageData<PendingApprovalDTO>> listMyPending(ListMyPendingApprovalsRequest request) {
        return approvalManagementService.listMyPending(request);
    }

    @Override
    public ApiResult<List<ApprovalRecordDTO>> getHistory(GetApprovalHistoryRequest request) {
        return approvalManagementService.getHistory(request);
    }
}
