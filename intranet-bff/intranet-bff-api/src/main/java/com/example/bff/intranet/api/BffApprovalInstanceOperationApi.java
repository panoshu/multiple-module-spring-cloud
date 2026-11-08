package com.example.bff.intranet.api;

import com.example.approval.api.request.*;
import com.example.approval.api.response.ApprovalInstanceIdResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 审批实例操作管理 BFF API
 *
 * <p>透明转发到 approval-service，方法参数和返回值复用 approval-api 的 Request/Response 类型。
 *
 * <p>按 ISP 拆分：本接口仅含审批实例的写操作，查询类操作见 {@link BffApprovalInstanceQueryApi}。
 *
 * @author bff
 */
@HttpExchange("/management/approval/instances")
public interface BffApprovalInstanceOperationApi {

  @PostExchange("/start")
  ApiResult<ApprovalInstanceIdResponse> startInstance(@Valid @RequestBody StartApprovalRequest request);

  @PostExchange("/approve")
  ApiResult<Void> approveInstance(@Valid @RequestBody ApproveRequest request);

  @PostExchange("/reject")
  ApiResult<Void> rejectInstance(@Valid @RequestBody RejectRequest request);

  @PostExchange("/transfer")
  ApiResult<Void> transferInstance(@Valid @RequestBody TransferRequest request);

  @PostExchange("/withdraw")
  ApiResult<Void> withdrawInstance(@Valid @RequestBody WithdrawRequest request);
}
