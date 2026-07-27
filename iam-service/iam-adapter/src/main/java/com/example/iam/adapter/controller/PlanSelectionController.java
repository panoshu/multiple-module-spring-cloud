package com.example.iam.adapter.controller;

import com.example.iam.api.PlanSelectionApi;
import com.example.iam.api.command.ClearCurrentPlanCommand;
import com.example.iam.api.command.SelectPlanCommand;
import com.example.iam.api.dto.PlanPermissionDTO;
import com.example.iam.api.dto.SelectablePlanDTO;
import com.example.iam.api.query.GetCurrentPlanQuery;
import com.example.iam.api.query.ListSelectablePlansQuery;
import com.example.iam.application.service.PlanSelectionAppService;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

/**
 * 计划选择 Controller
 *
 * <p>实现 {@link PlanSelectionApi} 接口,委托 {@link PlanSelectionAppService} 完成计划选择、
 * 当前计划查询与清除等编排。本类仅做请求转发,不包含业务逻辑与 DTO 转换。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class PlanSelectionController implements PlanSelectionApi {

  private final PlanSelectionAppService planSelectionAppService;

  @Override
  public ApiResult<PageData<SelectablePlanDTO>> listSelectablePlans(ListSelectablePlansQuery query) {
    log.info("查询可选计划列表: keyword={}", query.keyword());
    return ApiResult.success(planSelectionAppService.listSelectablePlans(query));
  }

  @Override
  public ApiResult<PlanPermissionDTO> selectPlan(SelectPlanCommand command) {
    log.info("选择计划: planId={}, customerNo={}", command.planId(), command.customerNo());
    return ApiResult.success(planSelectionAppService.selectPlan(command));
  }

  @Override
  public ApiResult<PlanPermissionDTO> getCurrentPlan(GetCurrentPlanQuery query) {
    log.info("查询当前计划");
    return ApiResult.success(planSelectionAppService.getCurrentPlan(query));
  }

  @Override
  public ApiResult<Void> clearCurrentPlan(ClearCurrentPlanCommand command) {
    log.info("清除当前计划");
    planSelectionAppService.clearCurrentPlan(command);
    return ApiResult.success();
  }
}
