package com.example.iam.adapter.controller;

import com.example.iam.api.BusinessDefinitionApi;
import com.example.iam.api.command.CreateBusinessDefinitionCommand;
import com.example.iam.api.command.DisableBusinessDefinitionCommand;
import com.example.iam.api.command.EnableBusinessDefinitionCommand;
import com.example.iam.api.dto.BusinessDefinitionDTO;
import com.example.iam.api.dto.IdResponseDTO;
import com.example.iam.api.query.ListBusinessDefinitionsQuery;
import com.example.iam.application.service.BusinessDefinitionAppService;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

/**
 * 业务定义管理 Controller
 *
 * <p>实现 {@link BusinessDefinitionApi} 接口,委托 {@link BusinessDefinitionAppService}
 * 完成业务定义的创建、启用/禁用与查询编排。本类仅做请求转发,不包含业务逻辑与 DTO 转换。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class BusinessDefinitionController implements BusinessDefinitionApi {

  private final BusinessDefinitionAppService businessDefinitionAppService;

  @Override
  public ApiResult<IdResponseDTO> create(CreateBusinessDefinitionCommand command) {
    log.info("创建业务定义: businessCode={}, businessName={}",
        command.businessCode(), command.businessName());
    return ApiResult.success(businessDefinitionAppService.create(command));
  }

  @Override
  public ApiResult<Void> disable(DisableBusinessDefinitionCommand command) {
    log.info("禁用业务定义: definitionId={}", command.definitionId());
    businessDefinitionAppService.disable(command);
    return ApiResult.success();
  }

  @Override
  public ApiResult<Void> enable(EnableBusinessDefinitionCommand command) {
    log.info("启用业务定义: definitionId={}", command.definitionId());
    businessDefinitionAppService.enable(command);
    return ApiResult.success();
  }

  @Override
  public ApiResult<PageData<BusinessDefinitionDTO>> list(ListBusinessDefinitionsQuery query) {
    log.info("查询业务定义列表: businessCode={}, businessName={}, active={}",
        query.businessCode(), query.businessName(), query.active());
    return ApiResult.success(businessDefinitionAppService.list(query));
  }
}
