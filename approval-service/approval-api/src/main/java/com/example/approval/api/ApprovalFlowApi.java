package com.example.approval.api;

import com.example.approval.api.dto.ApprovalFlowDTO;
import com.example.approval.api.request.CreateApprovalFlowRequest;
import com.example.approval.api.request.DeprecateApprovalFlowRequest;
import com.example.approval.api.request.GetApprovalFlowRequest;
import com.example.approval.api.request.ListApprovalFlowsRequest;
import com.example.approval.api.request.MatchApprovalFlowRequest;
import com.example.approval.api.request.UpdateApprovalFlowRequest;
import com.example.approval.api.response.ApprovalFlowIdResponse;
import com.example.shared.primitives.page.PageInfo;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 审批流API接口
 *
 * @author approval-service
 */
@HttpExchange("/api/approval/flows")
public interface ApprovalFlowApi {

    /**
     * 创建审批流
     *
     * @param request 创建请求
     * @return 审批流ID
     */
    @PostExchange("/create")
    ApiResult<ApprovalFlowIdResponse> create(@RequestBody @Valid CreateApprovalFlowRequest request);

    /**
     * 更新审批流
     *
     * @param request 更新请求
     * @return 操作结果
     */
    @PostExchange("/update")
    ApiResult<Void> update(@RequestBody @Valid UpdateApprovalFlowRequest request);

    /**
     * 废弃审批流
     *
     * @param request 废弃请求
     * @return 操作结果
     */
    @PostExchange("/deprecate")
    ApiResult<Void> deprecate(@RequestBody @Valid DeprecateApprovalFlowRequest request);

    /**
     * 查询审批流
     *
     * @param request 查询请求
     * @return 审批流详情
     */
    @PostExchange("/get")
    ApiResult<ApprovalFlowDTO> get(@RequestBody @Valid GetApprovalFlowRequest request);

    /**
     * 列表查询审批流
     *
     * @param request 列表查询请求
     * @return 审批流分页列表
     */
    @PostExchange("/list")
    ApiResult<PageInfo<ApprovalFlowDTO>> list(@RequestBody @Valid ListApprovalFlowsRequest request);

    /**
     * 匹配审批流
     *
     * @param request 匹配请求
     * @return 匹配的审批流
     */
    @PostExchange("/match")
    ApiResult<ApprovalFlowDTO> match(@RequestBody @Valid MatchApprovalFlowRequest request);
}