package com.example.bff.intranet.adapter.controller;

import com.example.approval.api.dto.ApprovalInstanceDTO;
import com.example.approval.api.dto.ApprovalRecordDTO;
import com.example.approval.api.dto.PendingApprovalDTO;
import com.example.approval.api.request.ApproveRequest;
import com.example.approval.api.request.GetApprovalHistoryRequest;
import com.example.approval.api.request.GetApprovalInstanceRequest;
import com.example.approval.api.request.ListMyPendingApprovalsRequest;
import com.example.approval.api.request.RejectRequest;
import com.example.approval.api.request.StartApprovalRequest;
import com.example.approval.api.request.TransferRequest;
import com.example.approval.api.request.WithdrawRequest;
import com.example.approval.api.response.ApprovalInstanceIdResponse;
import com.example.bff.intranet.api.BffApprovalInstanceOperationApi;
import com.example.bff.intranet.api.BffApprovalInstanceQueryApi;
import com.example.bff.intranet.application.service.ApprovalManagementService;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 审批实例管理 Controller
 *
 * <p>透明转发到 approval-service，同时实现操作类接口 {@link BffApprovalInstanceOperationApi}
 * 与查询类接口 {@link BffApprovalInstanceQueryApi}。
 *
 * @author bff
 */
@RestController
public class BffApprovalInstanceController
        implements BffApprovalInstanceOperationApi, BffApprovalInstanceQueryApi {

    private final ApprovalManagementService approvalManagementService;

    public BffApprovalInstanceController(ApprovalManagementService approvalManagementService) {
        this.approvalManagementService = approvalManagementService;
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