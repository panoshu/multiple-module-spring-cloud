package com.example.iam.adapter.controller;

import com.example.iam.api.PlanDelegationApi;
import com.example.iam.api.command.CreatePlanDelegationCommand;
import com.example.iam.api.command.RevokePlanDelegationCommand;
import com.example.iam.api.dto.IdResponseDTO;
import com.example.iam.api.dto.PlanDelegationDTO;
import com.example.iam.api.query.GetPlanDelegationDetailQuery;
import com.example.iam.api.query.ListPlanDelegationsQuery;
import com.example.iam.application.service.PlanDelegationAppService;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

/**
 * 计划代办管理 Controller
 *
 * <p>实现 {@link PlanDelegationApi} 接口,委托 {@link PlanDelegationAppService} 完成计划代办的
 * 创建、撤销与查询编排。本类仅做请求转发,不包含业务逻辑与 DTO 转换。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class PlanDelegationController implements PlanDelegationApi {

  private final PlanDelegationAppService planDelegationAppService;

  @Override
  public ApiResult<IdResponseDTO> create(CreatePlanDelegationCommand command) {
    log.info("创建计划代办: delegationCode={}, delegatorPlanNo={}, delegateePlanNo={}",
        command.delegationCode(), command.delegatorPlanNo(), command.delegateePlanNo());
    return ApiResult.success(planDelegationAppService.create(command));
  }

  @Override
  public ApiResult<Void> revoke(RevokePlanDelegationCommand command) {
    log.info("撤销计划代办: delegationId={}, reason={}", command.delegationId(), command.reason());
    planDelegationAppService.revoke(command);
    return ApiResult.success();
  }

  @Override
  public ApiResult<PageData<PlanDelegationDTO>> list(ListPlanDelegationsQuery query) {
    log.info("查询计划代办列表: delegatorPlanNo={}, delegateePlanNo={}, delegationType={}, status={}",
        query.delegatorPlanNo(), query.delegateePlanNo(), query.delegationType(), query.status());
    return ApiResult.success(planDelegationAppService.list(query));
  }

  @Override
  public ApiResult<PlanDelegationDTO> getDetail(GetPlanDelegationDetailQuery query) {
    log.info("查询计划代办详情: delegationId={}", query.delegationId());
    return ApiResult.success(planDelegationAppService.getDetail(query));
  }
}
