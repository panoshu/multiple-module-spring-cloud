package com.example.approval.adapter.controllers;

import com.example.approval.api.ApprovalInstanceApi;
import com.example.approval.api.dto.ApprovalInstanceDTO;
import com.example.approval.api.dto.ApprovalRecordDTO;
import com.example.approval.api.dto.PendingApprovalDTO;
import com.example.approval.api.request.*;
import com.example.approval.api.response.ApprovalInstanceIdResponse;
import com.example.approval.application.service.ApprovalInstanceService;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 审批实例适配器
 * 实现 ApprovalInstanceApi 接口，提供审批实例相关的 REST API
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
@Slf4j
@RestController
@RequestMapping("/api/approval/instances")
@RequiredArgsConstructor
public class ApprovalInstanceAdapter implements ApprovalInstanceApi {

  private final ApprovalInstanceService approvalInstanceService;

  @Override
  public ApiResult<ApprovalInstanceIdResponse> start(StartApprovalRequest request) {
    log.info("启动审批: flowId={}, businessNo={}", request.flowId(), request.businessNo());
    var instanceId = approvalInstanceService.startApproval(request);
    return ApiResult.success(new ApprovalInstanceIdResponse(instanceId));
  }

  @Override
  public ApiResult<Void> approve(ApproveRequest request) {
    log.info("审批通过: instanceId={}, approver={}", request.instanceId(), request.approver());
    approvalInstanceService.approve(request);
    return ApiResult.success();
  }

  @Override
  public ApiResult<Void> reject(RejectRequest request) {
    log.info("审批驳回: instanceId={}, approver={}", request.instanceId(), request.approver());
    approvalInstanceService.reject(request);
    return ApiResult.success();
  }

  @Override
  public ApiResult<Void> transfer(TransferRequest request) {
    log.info("审批转交: instanceId={}, from={}, to={}",
      request.instanceId(), request.currentApprover(), request.targetApprover());
    approvalInstanceService.transfer(request);
    return ApiResult.success();
  }

  @Override
  public ApiResult<Void> withdraw(WithdrawRequest request) {
    log.info("审批撤回: instanceId={}, initiator={}", request.instanceId(), request.initiator());
    approvalInstanceService.withdraw(request);
    return ApiResult.success();
  }

  @Override
  public ApiResult<ApprovalInstanceDTO> get(GetApprovalInstanceRequest request) {
    log.info("查询审批实例: instanceId={}", request.instanceId());
    ApprovalInstanceDTO instanceDTO = approvalInstanceService.getApprovalInstance(request);
    return ApiResult.success(instanceDTO);
  }

  @Override
  public ApiResult<PageData<PendingApprovalDTO>> listMyPending(ListMyPendingApprovalsRequest request) {
    log.info("查询待审批列表: approver={}", request.approver());
    PageData<PendingApprovalDTO> pageResult = approvalInstanceService.listMyPendingApprovals(request);
    return ApiResult.success(pageResult);
  }

  @Override
  public ApiResult<List<ApprovalRecordDTO>> getHistory(GetApprovalHistoryRequest request) {
    log.info("查询审批历史: instanceId={}", request.instanceId());
    List<ApprovalRecordDTO> records = approvalInstanceService.getApprovalHistory(request);
    return ApiResult.success(records);
  }
}
