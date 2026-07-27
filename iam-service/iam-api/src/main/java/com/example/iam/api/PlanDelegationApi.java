package com.example.iam.api;

import com.example.iam.api.command.CreatePlanDelegationCommand;
import com.example.iam.api.command.RevokePlanDelegationCommand;
import com.example.iam.api.dto.IdResponseDTO;
import com.example.iam.api.dto.PlanDelegationDTO;
import com.example.iam.api.query.GetPlanDelegationDetailQuery;
import com.example.iam.api.query.ListPlanDelegationsQuery;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 计划代办管理 API
 *
 * <p>提供计划代办的创建、撤销以及列表/详情查询接口。
 *
 * @author iam-service
 */
@HttpExchange("/iam/plan-delegations")
public interface PlanDelegationApi {

    /**
     * 创建计划代办
     *
     * @param command 创建命令
     * @return 计划代办 ID
     */
    @PostExchange("/create")
    ApiResult<IdResponseDTO> create(@RequestBody @Valid CreatePlanDelegationCommand command);

    /**
     * 撤销计划代办
     *
     * @param command 撤销命令
     * @return 操作结果
     */
    @PostExchange("/revoke")
    ApiResult<Void> revoke(@RequestBody @Valid RevokePlanDelegationCommand command);

    /**
     * 计划代办列表查询
     *
     * @param query 查询条件
     * @return 计划代办分页列表
     */
    @PostExchange("/list")
    ApiResult<PageData<PlanDelegationDTO>> list(@RequestBody @Valid ListPlanDelegationsQuery query);

    /**
     * 计划代办详情查询
     *
     * @param query 查询条件
     * @return 计划代办详情
     */
    @PostExchange("/detail")
    ApiResult<PlanDelegationDTO> getDetail(@RequestBody @Valid GetPlanDelegationDetailQuery query);
}
