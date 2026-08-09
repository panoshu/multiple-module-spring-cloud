package com.example.bff.intranet.adapter.controller;

import com.example.approval.api.dto.ApprovalFlowDTO;
import com.example.approval.api.request.CreateApprovalFlowRequest;
import com.example.approval.api.request.DeprecateApprovalFlowRequest;
import com.example.approval.api.request.GetApprovalFlowRequest;
import com.example.approval.api.request.ListApprovalFlowsRequest;
import com.example.approval.api.request.MatchApprovalFlowRequest;
import com.example.approval.api.request.UpdateApprovalFlowRequest;
import com.example.approval.api.response.ApprovalFlowIdResponse;
import com.example.bff.intranet.api.BffApprovalFlowApi;
import com.example.bff.intranet.application.service.ApprovalManagementService;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审批流管理 Controller
 *
 * <p>透明转发到 approval-service，实现 {@link BffApprovalFlowApi}。
 *
 * @author bff
 */
@RestController
public class BffApprovalFlowController implements BffApprovalFlowApi {

    private final ApprovalManagementService approvalManagementService;

    public BffApprovalFlowController(ApprovalManagementService approvalManagementService) {
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
}