package com.example.bff.intranet.api;

import com.example.approval.api.dto.ApprovalFlowDTO;
import com.example.approval.api.request.*;
import com.example.approval.api.response.ApprovalFlowIdResponse;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 审批流管理 BFF API
 *
 * <p>透明转发到 approval-service，方法参数和返回值复用 approval-api 的 Request/Response 类型。
 *
 * @author bff
 */
@HttpExchange("/management/approval/flows")
public interface BffApprovalFlowApi {

  @PostExchange("/create")
  ApiResult<ApprovalFlowIdResponse> createFlow(@Valid @RequestBody CreateApprovalFlowRequest request);

  @PostExchange("/update")
  ApiResult<Void> updateFlow(@Valid @RequestBody UpdateApprovalFlowRequest request);

  @PostExchange("/deprecate")
  ApiResult<Void> deprecateFlow(@Valid @RequestBody DeprecateApprovalFlowRequest request);

  @PostExchange("/get")
  ApiResult<ApprovalFlowDTO> getFlow(@Valid @RequestBody GetApprovalFlowRequest request);

  @PostExchange("/list")
  ApiResult<PageData<ApprovalFlowDTO>> listFlows(@Valid @RequestBody ListApprovalFlowsRequest request);

  @PostExchange("/match")
  ApiResult<ApprovalFlowDTO> matchFlow(@Valid @RequestBody MatchApprovalFlowRequest request);
}
