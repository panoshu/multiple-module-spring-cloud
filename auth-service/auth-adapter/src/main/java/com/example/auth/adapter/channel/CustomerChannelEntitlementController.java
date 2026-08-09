package com.example.auth.adapter.channel;

import com.example.auth.api.CustomerChannelEntitlementApi;
import com.example.auth.api.annotation.PermissionCategory;
import com.example.auth.api.annotation.RequirePermission;
import com.example.auth.api.command.DisableChannelRequest;
import com.example.auth.api.command.EnableChannelRequest;
import com.example.auth.api.query.GetEntitlementRequest;
import com.example.auth.api.command.ReplaceChannelsRequest;
import com.example.auth.api.dto.CustomerChannelEntitlementResponse;
import com.example.auth.adapter.converter.CustomerChannelEntitlementConverter;
import com.example.shared.identifier.id.UserNo;
import com.example.shared.web.core.api.ApiResult;
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
  @RequirePermission(business = "CHANNEL_ENTITLEMENT", action = "VIEW", category = PermissionCategory.PLATFORM)
  public ApiResult<CustomerChannelEntitlementResponse> get(GetEntitlementRequest request) {
    CustomerChannelEntitlementResponse response = service.getEntitlement(converter.toQuery(request));
    return ApiResult.success(response);
  }

  @Override
  @RequirePermission(business = "CHANNEL_ENTITLEMENT", action = "ENABLE", category = PermissionCategory.PLATFORM)
  public ApiResult<CustomerChannelEntitlementResponse> enable(EnableChannelRequest request) {
    CustomerChannelEntitlementResponse response = service.enable(
      converter.toCommand(request, SYSTEM_OPERATOR));
    return ApiResult.success(response);
  }

  @Override
  @RequirePermission(business = "CHANNEL_ENTITLEMENT", action = "DISABLE", category = PermissionCategory.PLATFORM)
  public ApiResult<Void> disable(DisableChannelRequest request) {
    service.disable(converter.toCommand(request, SYSTEM_OPERATOR));
    return ApiResult.success(null);
  }

  @Override
  @RequirePermission(business = "CHANNEL_ENTITLEMENT", action = "REPLACE", category = PermissionCategory.PLATFORM)
  public ApiResult<CustomerChannelEntitlementResponse> replace(ReplaceChannelsRequest request) {
    CustomerChannelEntitlementResponse response = service.replace(
      converter.toCommand(request, SYSTEM_OPERATOR));
    return ApiResult.success(response);
  }
}
