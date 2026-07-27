package com.example.iam.api;

import com.example.iam.api.command.ClearCurrentPlanCommand;
import com.example.iam.api.command.SelectPlanCommand;
import com.example.iam.api.dto.PlanPermissionDTO;
import com.example.iam.api.dto.SelectablePlanDTO;
import com.example.iam.api.query.GetCurrentPlanQuery;
import com.example.iam.api.query.ListSelectablePlansQuery;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 计划选择 API
 *
 * <p>三渠道共用的计划查询、选择、查询当前计划与清除当前计划接口,通过路径前缀区分渠道。
 *
 * @author iam-service
 */
@HttpExchange("/iam/plan")
public interface PlanSelectionApi {

    /**
     * 查询可选计划列表
     *
     * @param query 查询条件
     * @return 可选计划分页列表
     */
    @PostExchange("/selectable")
    ApiResult<PageData<SelectablePlanDTO>> listSelectablePlans(@RequestBody @Valid ListSelectablePlansQuery query);

    /**
     * 选择计划
     *
     * @param command 选择命令
     * @return 计划权限信息
     */
    @PostExchange("/select")
    ApiResult<PlanPermissionDTO> selectPlan(@RequestBody @Valid SelectPlanCommand command);

    /**
     * 查询当前计划
     *
     * @param query 查询条件
     * @return 计划权限信息
     */
    @PostExchange("/current")
    ApiResult<PlanPermissionDTO> getCurrentPlan(@RequestBody @Valid GetCurrentPlanQuery query);

    /**
     * 清除当前计划
     *
     * @param command 清除命令
     * @return 操作结果
     */
    @PostExchange("/clear")
    ApiResult<Void> clearCurrentPlan(@RequestBody @Valid ClearCurrentPlanCommand command);
}
