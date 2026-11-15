package com.example.approval.adapter.controllers;

import com.example.approval.api.ApprovalInstanceApi;
import com.example.approval.api.dto.ApprovalInstanceDTO;
import com.example.approval.api.dto.ApprovalRecordDTO;
import com.example.approval.api.dto.PendingApprovalDTO;
import com.example.approval.api.request.*;
import com.example.approval.api.response.ApprovalInstanceIdResponse;
import com.example.approval.application.service.ApprovalInstanceService;
import com.example.shared.exception.BusinessException;
import com.example.shared.exception.CommonError;
import com.example.shared.permission.AccountIdResolver;
import com.example.shared.security.annotation.PermissionCategory;
import com.example.shared.security.annotation.RequirePermission;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

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
  private final AccountIdResolver accountIdResolver;

  @Override
  @RequirePermission(business = "APPROVAL_INSTANCE", action = "START", category = PermissionCategory.PLATFORM)
  public ApiResult<ApprovalInstanceIdResponse> start(StartApprovalRequest request) {
    log.info("启动审批: flowId={}, businessNo={}", request.flowId(), request.businessNo());
    var instanceId = approvalInstanceService.startApproval(request);
    return ApiResult.success(new ApprovalInstanceIdResponse(instanceId));
  }

  @Override
  public ApiResult<ApprovalInstanceIdResponse> getByRequestNo(GetByRequestNoRequest request) {
    Optional<com.example.approval.types.ApprovalInstanceId> found =
      approvalInstanceService.findByRequestNo(request.requestNo());
    if (found.isEmpty()) {
      return ApiResult.success(null);
    }
    return ApiResult.success(new ApprovalInstanceIdResponse(found.get()));
  }

  @Override
  @RequirePermission(business = "APPROVAL_INSTANCE", action = "APPROVE", category = PermissionCategory.PLATFORM)
  public ApiResult<Void> approve(ApproveRequest request) {
    String approver = requireSessionAccount();
    log.info("审批通过: instanceId={}, approver={}（取自会话）", request.instanceId(), approver);
    approvalInstanceService.approve(
      new ApproveRequest(request.instanceId(), approver, request.comment()));
    return ApiResult.success();
  }

  @Override
  @RequirePermission(business = "APPROVAL_INSTANCE", action = "REJECT", category = PermissionCategory.PLATFORM)
  public ApiResult<Void> reject(RejectRequest request) {
    String approver = requireSessionAccount();
    log.info("审批驳回: instanceId={}, approver={}（取自会话）", request.instanceId(), approver);
    approvalInstanceService.reject(
      new RejectRequest(request.instanceId(), approver, request.reason()));
    return ApiResult.success();
  }

  @Override
  @RequirePermission(business = "APPROVAL_INSTANCE", action = "TRANSFER", category = PermissionCategory.PLATFORM)
  public ApiResult<Void> transfer(TransferRequest request) {
    String currentApprover = requireSessionAccount();
    log.info("审批转交: instanceId={}, from={}（取自会话）, to={}",
      request.instanceId(), currentApprover, request.targetApprover());
    approvalInstanceService.transfer(new TransferRequest(
      request.instanceId(), currentApprover, request.targetApprover(), request.reason()));
    return ApiResult.success();
  }

  @Override
  @RequirePermission(business = "APPROVAL_INSTANCE", action = "WITHDRAW", category = PermissionCategory.PLATFORM)
  public ApiResult<Void> withdraw(WithdrawRequest request) {
    String initiator = requireSessionAccount();
    log.info("审批撤回: instanceId={}, initiator={}（取自会话）", request.instanceId(), initiator);
    approvalInstanceService.withdraw(
      new WithdrawRequest(request.instanceId(), initiator, request.reason()));
    return ApiResult.success();
  }

  @Override
  @RequirePermission(business = "APPROVAL_INSTANCE", action = "VIEW", category = PermissionCategory.PLATFORM)
  public ApiResult<ApprovalInstanceDTO> get(GetApprovalInstanceRequest request) {
    log.info("查询审批实例: instanceId={}", request.instanceId());
    ApprovalInstanceDTO instanceDTO = approvalInstanceService.getApprovalInstance(request);
    return ApiResult.success(instanceDTO);
  }

  @Override
  @RequirePermission(business = "APPROVAL_INSTANCE", action = "VIEW", category = PermissionCategory.PLATFORM)
  public ApiResult<PageData<PendingApprovalDTO>> listMyPending(ListMyPendingApprovalsRequest request) {
    String approver = requireSessionAccount();
    log.info("查询待审批列表: approver={}（取自会话）", approver);
    ListMyPendingApprovalsRequest trusted =
      new ListMyPendingApprovalsRequest(approver, request.pagination());
    return ApiResult.success(approvalInstanceService.listMyPendingApprovals(trusted));
  }

  @Override
  @RequirePermission(business = "APPROVAL_INSTANCE", action = "VIEW", category = PermissionCategory.PLATFORM)
  public ApiResult<List<ApprovalRecordDTO>> getHistory(GetApprovalHistoryRequest request) {
    log.info("查询审批历史: instanceId={}", request.instanceId());
    List<ApprovalRecordDTO> records = approvalInstanceService.getApprovalHistory(request);
    return ApiResult.success(records);
  }

  /**
   * 从带签名的会话账号解析当前操作人（批次 56/57）。
   * <p>
   * {@link AccountIdResolver} 默认实现不使用 AOP JoinPoint，传 null 取当前请求头；
   * 解析失败或为空一律 fail-closed，绝不回退到请求体中的身份字段。
   */
  private String requireSessionAccount() {
    String account = accountIdResolver.resolve(null);
    if (account == null || account.isBlank()) {
      throw new BusinessException(CommonError.UNAUTHORIZED)
        .withLogDetail("审批操作缺少可信会话账号，拒绝按请求体身份执行");
    }
    return account;
  }
}
