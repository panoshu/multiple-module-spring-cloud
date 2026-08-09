package com.example.bff.intranet.api;

import com.example.auth.api.command.DisableChannelRequest;
import com.example.auth.api.command.EnableChannelRequest;
import com.example.auth.api.command.ReplaceChannelsRequest;
import com.example.auth.api.dto.CustomerChannelEntitlementResponse;
import com.example.auth.api.query.GetEntitlementRequest;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 客户渠道开通管理 BFF API
 *
 * <p>透明转发到 auth-service 的 CustomerChannelEntitlementApi。
 *
 * @author bff
 */
@HttpExchange("/management/channels")
public interface BffChannelApi {

  @PostExchange("/enable")
  ApiResult<CustomerChannelEntitlementResponse> enable(@Valid @RequestBody EnableChannelRequest request);

  @PostExchange("/disable")
  ApiResult<Void> disable(@Valid @RequestBody DisableChannelRequest request);

  @PostExchange("/replace")
  ApiResult<CustomerChannelEntitlementResponse> replace(@Valid @RequestBody ReplaceChannelsRequest request);

  @PostExchange("/get")
  ApiResult<CustomerChannelEntitlementResponse> get(@Valid @RequestBody GetEntitlementRequest request);
}
