package com.example.bff.intranet.application.service;

import com.example.auth.api.CustomerChannelEntitlementApi;
import com.example.auth.api.command.DisableChannelRequest;
import com.example.auth.api.command.EnableChannelRequest;
import com.example.auth.api.command.ReplaceChannelsRequest;
import com.example.auth.api.dto.CustomerChannelEntitlementResponse;
import com.example.auth.api.query.GetEntitlementRequest;
import com.example.shared.web.core.api.ApiResult;
import org.springframework.stereotype.Service;

/**
 * 渠道开通管理服务
 *
 * <p>透明转发到 auth-service 的 CustomerChannelEntitlementApi。
 *
 * @author bff
 */
@Service
public class ChannelManagementService {

  private final CustomerChannelEntitlementApi channelEntitlementApi;

  public ChannelManagementService(CustomerChannelEntitlementApi channelEntitlementApi) {
    this.channelEntitlementApi = channelEntitlementApi;
  }

  public ApiResult<CustomerChannelEntitlementResponse> enable(EnableChannelRequest request) {
    return channelEntitlementApi.enable(request);
  }

  public ApiResult<Void> disable(DisableChannelRequest request) {
    return channelEntitlementApi.disable(request);
  }

  public ApiResult<CustomerChannelEntitlementResponse> replace(ReplaceChannelsRequest request) {
    return channelEntitlementApi.replace(request);
  }

  public ApiResult<CustomerChannelEntitlementResponse> get(GetEntitlementRequest request) {
    return channelEntitlementApi.get(request);
  }
}
