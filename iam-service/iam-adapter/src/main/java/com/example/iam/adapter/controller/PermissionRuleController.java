package com.example.iam.adapter.controller;

import com.example.iam.api.PermissionRuleApi;
import com.example.iam.api.command.CreatePermissionRuleCommand;
import com.example.iam.api.command.DisablePermissionRuleCommand;
import com.example.iam.api.command.EnablePermissionRuleCommand;
import com.example.iam.api.dto.IdResponseDTO;
import com.example.iam.api.dto.PermissionRuleDTO;
import com.example.iam.api.query.GetPermissionRuleDetailQuery;
import com.example.iam.api.query.ListPermissionRulesQuery;
import com.example.iam.application.service.PermissionRuleAppService;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

/**
 * 权限规则管理 Controller
 *
 * <p>实现 {@link PermissionRuleApi} 接口,委托 {@link PermissionRuleAppService} 完成权限规则的
 * 创建、启用/禁用与查询编排。本类仅做请求转发,不包含业务逻辑与 DTO 转换。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class PermissionRuleController implements PermissionRuleApi {

  private final PermissionRuleAppService permissionRuleAppService;

  @Override
  public ApiResult<IdResponseDTO> create(CreatePermissionRuleCommand command) {
    log.info("创建权限规则: ruleCode={}, businessCode={}",
        command.ruleCode(), command.businessCode());
    return ApiResult.success(permissionRuleAppService.create(command));
  }

  @Override
  public ApiResult<Void> disable(DisablePermissionRuleCommand command) {
    log.info("禁用权限规则: ruleId={}", command.ruleId());
    permissionRuleAppService.disable(command);
    return ApiResult.success();
  }

  @Override
  public ApiResult<Void> enable(EnablePermissionRuleCommand command) {
    log.info("启用权限规则: ruleId={}", command.ruleId());
    permissionRuleAppService.enable(command);
    return ApiResult.success();
  }

  @Override
  public ApiResult<PageData<PermissionRuleDTO>> list(ListPermissionRulesQuery query) {
    log.info("查询权限规则列表: ruleCode={}, subjectType={}, businessCode={}, status={}",
        query.ruleCode(), query.subjectType(), query.businessCode(), query.status());
    return ApiResult.success(permissionRuleAppService.list(query));
  }

  @Override
  public ApiResult<PermissionRuleDTO> getDetail(GetPermissionRuleDetailQuery query) {
    log.info("查询权限规则详情: ruleId={}", query.ruleId());
    return ApiResult.success(permissionRuleAppService.getDetail(query));
  }
}
