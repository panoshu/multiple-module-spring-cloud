package com.example.bff.intranet.api;

import com.example.approval.api.dto.ApprovalInstanceDTO;
import com.example.approval.api.dto.ApprovalRecordDTO;
import com.example.approval.api.dto.PendingApprovalDTO;
import com.example.approval.api.request.GetApprovalHistoryRequest;
import com.example.approval.api.request.GetApprovalInstanceRequest;
import com.example.approval.api.request.ListMyPendingApprovalsRequest;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * 审批实例查询管理 BFF API
 *
 * <p>透明转发到 approval-service，方法参数和返回值复用 approval-api 的 Request/Response 类型。
 *
 * <p>按 ISP 拆分：本接口仅含审批实例的查询类操作，写操作见 {@link BffApprovalInstanceOperationApi}。
 *
 * @author bff
 */
@HttpExchange("/management/approval/instances")
public interface BffApprovalInstanceQueryApi {

  @PostExchange("/get")
  ApiResult<ApprovalInstanceDTO> getInstance(@Valid @RequestBody GetApprovalInstanceRequest request);

  @PostExchange("/my-pending")
  ApiResult<PageData<PendingApprovalDTO>> listMyPending(@Valid @RequestBody ListMyPendingApprovalsRequest request);

  @PostExchange("/history")
  ApiResult<List<ApprovalRecordDTO>> getHistory(@Valid @RequestBody GetApprovalHistoryRequest request);
}
