package com.example.iam.adapter.controller;

import com.example.iam.api.RouteRuleApi;
import com.example.iam.api.command.CreateRouteRuleCommand;
import com.example.iam.api.command.DisableRouteRuleCommand;
import com.example.iam.api.command.EnableRouteRuleCommand;
import com.example.iam.api.dto.IdResponseDTO;
import com.example.iam.api.dto.RouteRuleDTO;
import com.example.iam.api.query.GetRouteRuleDetailQuery;
import com.example.iam.api.query.ListRouteRulesQuery;
import com.example.iam.application.service.RouteRuleAppService;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

/**
 * 路由规则管理 Controller
 *
 * <p>实现 {@link RouteRuleApi} 接口,委托 {@link RouteRuleAppService} 完成网关层动态鉴权
 * 路由规则的创建、启用/禁用与查询编排。本类仅做请求转发,不包含业务逻辑与 DTO 转换。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class RouteRuleController implements RouteRuleApi {

  private final RouteRuleAppService routeRuleAppService;

  @Override
  public ApiResult<IdResponseDTO> create(CreateRouteRuleCommand command) {
    log.info("创建路由规则: routePattern={}, checkType={}",
        command.routePattern(), command.checkType());
    return ApiResult.success(routeRuleAppService.create(command));
  }

  @Override
  public ApiResult<Void> disable(DisableRouteRuleCommand command) {
    log.info("禁用路由规则: ruleId={}", command.ruleId());
    routeRuleAppService.disable(command);
    return ApiResult.success();
  }

  @Override
  public ApiResult<Void> enable(EnableRouteRuleCommand command) {
    log.info("启用路由规则: ruleId={}", command.ruleId());
    routeRuleAppService.enable(command);
    return ApiResult.success();
  }

  @Override
  public ApiResult<PageData<RouteRuleDTO>> list(ListRouteRulesQuery query) {
    log.info("查询路由规则列表: routePattern={}, checkType={}, enabled={}",
        query.routePattern(), query.checkType(), query.enabled());
    return ApiResult.success(routeRuleAppService.list(query));
  }

  @Override
  public ApiResult<RouteRuleDTO> getDetail(GetRouteRuleDetailQuery query) {
    log.info("查询路由规则详情: ruleId={}", query.ruleId());
    return ApiResult.success(routeRuleAppService.getDetail(query));
  }
}
