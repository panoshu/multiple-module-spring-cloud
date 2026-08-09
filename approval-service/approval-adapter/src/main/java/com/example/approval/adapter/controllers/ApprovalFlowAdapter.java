package com.example.approval.adapter.controllers;

import com.example.approval.api.ApprovalFlowApi;
import com.example.approval.api.dto.ApprovalFlowDTO;
import com.example.approval.api.request.*;
import com.example.approval.api.response.ApprovalFlowIdResponse;
import com.example.approval.application.service.ApprovalFlowService;
import com.example.auth.api.annotation.PermissionCategory;
import com.example.auth.api.annotation.RequirePermission;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审批流适配器
 * 实现 ApprovalFlowApi 接口，提供审批流相关的 REST API
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
@Slf4j
@RestController
@RequestMapping("/api/approval/flows")
@RequiredArgsConstructor
public class ApprovalFlowAdapter implements ApprovalFlowApi {

  private final ApprovalFlowService approvalFlowService;

  @Override
  @RequirePermission(business = "APPROVAL_FLOW", action = "CREATE", category = PermissionCategory.PLATFORM)
  public ApiResult<ApprovalFlowIdResponse> create(CreateApprovalFlowRequest request) {
    log.info("创建审批流: flowName={}", request.flowName());
    var flowId = approvalFlowService.createApprovalFlow(request);
    return ApiResult.success(new ApprovalFlowIdResponse(flowId));
  }

  @Override
  @RequirePermission(business = "APPROVAL_FLOW", action = "UPDATE", category = PermissionCategory.PLATFORM)
  public ApiResult<Void> update(UpdateApprovalFlowRequest request) {
    log.info("更新审批流: flowId={}", request.flowId());
    approvalFlowService.updateApprovalFlow(request);
    return ApiResult.success();
  }

  @Override
  @RequirePermission(business = "APPROVAL_FLOW", action = "DEPRECATE", category = PermissionCategory.PLATFORM)
  public ApiResult<Void> deprecate(DeprecateApprovalFlowRequest request) {
    log.info("废弃审批流: flowId={}", request.flowId());
    approvalFlowService.deprecateApprovalFlow(request);
    return ApiResult.success();
  }

  @Override
  @RequirePermission(business = "APPROVAL_FLOW", action = "VIEW", category = PermissionCategory.PLATFORM)
  public ApiResult<ApprovalFlowDTO> get(GetApprovalFlowRequest request) {
    log.info("查询审批流: flowId={}", request.flowId());
    ApprovalFlowDTO flowDTO = approvalFlowService.getApprovalFlow(request);
    return ApiResult.success(flowDTO);
  }

  @Override
  @RequirePermission(business = "APPROVAL_FLOW", action = "VIEW", category = PermissionCategory.PLATFORM)
  public ApiResult<PageData<ApprovalFlowDTO>> list(ListApprovalFlowsRequest request) {
    log.info("列表查询审批流");
    PageData<ApprovalFlowDTO> pageResult = approvalFlowService.listApprovalFlows(request);
    return ApiResult.success(pageResult);
  }

  @Override
  @RequirePermission(business = "APPROVAL_FLOW", action = "MATCH", category = PermissionCategory.PLATFORM)
  public ApiResult<ApprovalFlowDTO> match(MatchApprovalFlowRequest request) {
    log.info("匹配审批流: businessType={}, accountManagerCode={}",
      request.businessType(), request.accountManagerCode());
    ApprovalFlowDTO flowDTO = approvalFlowService.matchApprovalFlow(request);
    return ApiResult.success(flowDTO);
  }
}
