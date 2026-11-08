package com.example.approval.api;

import com.example.approval.api.dto.ApprovalInstanceDTO;
import com.example.approval.api.dto.ApprovalRecordDTO;
import com.example.approval.api.dto.PendingApprovalDTO;
import com.example.approval.api.request.*;
import com.example.approval.api.response.ApprovalInstanceIdResponse;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * 审批实例API接口
 *
 * @author approval-service
 */
@HttpExchange("/api/approval/instances")
public interface ApprovalInstanceApi {

  /**
   * 启动审批
   *
   * @param request 启动请求
   * @return 审批实例ID
   */
  @PostExchange("/start")
  ApiResult<ApprovalInstanceIdResponse> start(@RequestBody @Valid StartApprovalRequest request);

  /**
   * 审批通过
   *
   * @param request 通过请求
   * @return 操作结果
   */
  @PostExchange("/approve")
  ApiResult<Void> approve(@RequestBody @Valid ApproveRequest request);

  /**
   * 审批驳回
   *
   * @param request 驳回请求
   * @return 操作结果
   */
  @PostExchange("/reject")
  ApiResult<Void> reject(@RequestBody @Valid RejectRequest request);

  /**
   * 审批转交
   *
   * @param request 转交请求
   * @return 操作结果
   */
  @PostExchange("/transfer")
  ApiResult<Void> transfer(@RequestBody @Valid TransferRequest request);

  /**
   * 发起人撤回
   *
   * @param request 撤回请求
   * @return 操作结果
   */
  @PostExchange("/withdraw")
  ApiResult<Void> withdraw(@RequestBody @Valid WithdrawRequest request);

  /**
   * 查询审批实例
   *
   * @param request 查询请求
   * @return 审批实例详情
   */
  @PostExchange("/get")
  ApiResult<ApprovalInstanceDTO> get(@RequestBody @Valid GetApprovalInstanceRequest request);

  /**
   * 待审批列表
   *
   * @param request 列表请求
   * @return 待审批分页列表
   */
  @PostExchange("/my-pending")
  ApiResult<PageData<PendingApprovalDTO>> listMyPending(@RequestBody @Valid ListMyPendingApprovalsRequest request);

  /**
   * 审批历史
   *
   * @param request 历史请求
   * @return 审批记录列表
   */
  @PostExchange("/history")
  ApiResult<List<ApprovalRecordDTO>> getHistory(@RequestBody @Valid GetApprovalHistoryRequest request);
}
