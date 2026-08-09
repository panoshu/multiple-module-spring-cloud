package com.example.bff.intranet.api;

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
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * 审批管理 BFF API
 *
 * <p>透明转发到 approval-service，方法参数和返回值复用 approval-api 的 Request/Response 类型。
 *
 * @author bff
 */
@HttpExchange("/management/approval")
public interface BffApprovalApi {

    // ===== 审批流管理（6 个） =====

    @PostExchange("/flows/create")
    ApiResult<ApprovalFlowIdResponse> createFlow(@Valid @RequestBody CreateApprovalFlowRequest request);

    @PostExchange("/flows/update")
    ApiResult<Void> updateFlow(@Valid @RequestBody UpdateApprovalFlowRequest request);

    @PostExchange("/flows/deprecate")
    ApiResult<Void> deprecateFlow(@Valid @RequestBody DeprecateApprovalFlowRequest request);

    @PostExchange("/flows/get")
    ApiResult<ApprovalFlowDTO> getFlow(@Valid @RequestBody GetApprovalFlowRequest request);

    @PostExchange("/flows/list")
    ApiResult<PageData<ApprovalFlowDTO>> listFlows(@Valid @RequestBody ListApprovalFlowsRequest request);

    @PostExchange("/flows/match")
    ApiResult<ApprovalFlowDTO> matchFlow(@Valid @RequestBody MatchApprovalFlowRequest request);

    // ===== 审批实例管理（8 个） =====

    @PostExchange("/instances/start")
    ApiResult<ApprovalInstanceIdResponse> startInstance(@Valid @RequestBody StartApprovalRequest request);

    @PostExchange("/instances/approve")
    ApiResult<Void> approveInstance(@Valid @RequestBody ApproveRequest request);

    @PostExchange("/instances/reject")
    ApiResult<Void> rejectInstance(@Valid @RequestBody RejectRequest request);

    @PostExchange("/instances/transfer")
    ApiResult<Void> transferInstance(@Valid @RequestBody TransferRequest request);

    @PostExchange("/instances/withdraw")
    ApiResult<Void> withdrawInstance(@Valid @RequestBody WithdrawRequest request);

    @PostExchange("/instances/get")
    ApiResult<ApprovalInstanceDTO> getInstance(@Valid @RequestBody GetApprovalInstanceRequest request);

    @PostExchange("/instances/my-pending")
    ApiResult<PageData<PendingApprovalDTO>> listMyPending(@Valid @RequestBody ListMyPendingApprovalsRequest request);

    @PostExchange("/instances/history")
    ApiResult<List<ApprovalRecordDTO>> getHistory(@Valid @RequestBody GetApprovalHistoryRequest request);
}
