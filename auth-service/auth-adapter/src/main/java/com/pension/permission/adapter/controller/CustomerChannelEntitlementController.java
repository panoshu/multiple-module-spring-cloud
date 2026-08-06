package com.pension.permission.adapter.controller;

import com.example.shared.identifier.id.UserNo;
import com.example.shared.web.core.api.ApiResult;
import com.pension.permission.adapter.converter.CustomerChannelEntitlementConverter;
import com.pension.permission.api.CustomerChannelEntitlementApi;
import com.pension.permission.api.dto.CustomerChannelEntitlementResponse;
import com.pension.permission.api.dto.DisableChannelRequest;
import com.pension.permission.api.dto.EnableChannelRequest;
import com.pension.permission.api.dto.GetEntitlementRequest;
import com.pension.permission.api.dto.ReplaceChannelsRequest;
import com.pension.permission.application.channel.CustomerChannelEntitlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户渠道开通记录 Controller.
 *
 * <p>实现 {@link CustomerChannelEntitlementApi} 接口，负责请求体 DTO 与 CQE 对象的转换
 * 并委托应用服务处理业务。所有转换通过 {@link CustomerChannelEntitlementConverter} 完成。</p>
 */
@RestController
@RequiredArgsConstructor
public class CustomerChannelEntitlementController implements CustomerChannelEntitlementApi {

  private static final UserNo SYSTEM_OPERATOR = UserNo.of("SYSTEM");

  private final CustomerChannelEntitlementService service;
  private final CustomerChannelEntitlementConverter converter;

  @Override
  public ApiResult<CustomerChannelEntitlementResponse> query(GetEntitlementRequest request) {
    CustomerChannelEntitlementResponse response = service.getEntitlement(converter.toQuery(request));
    return ApiResult.success(response);
  }

  @Override
  public ApiResult<CustomerChannelEntitlementResponse> enable(EnableChannelRequest request) {
    CustomerChannelEntitlementResponse response = service.enable(
      converter.toCommand(request, SYSTEM_OPERATOR));
    return ApiResult.success(response);
  }

  @Override
  public ApiResult<CustomerChannelEntitlementResponse> disable(DisableChannelRequest request) {
    CustomerChannelEntitlementResponse response = service.disable(
      converter.toCommand(request, SYSTEM_OPERATOR));
    return ApiResult.success(response);
  }

  @Override
  public ApiResult<CustomerChannelEntitlementResponse> replace(ReplaceChannelsRequest request) {
    CustomerChannelEntitlementResponse response = service.replace(
      converter.toCommand(request, SYSTEM_OPERATOR));
    return ApiResult.success(response);
  }
}
