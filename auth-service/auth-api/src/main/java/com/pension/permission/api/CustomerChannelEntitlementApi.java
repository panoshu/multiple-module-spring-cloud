package com.pension.permission.api;

import com.example.shared.web.core.api.ApiResult;
import com.pension.permission.api.dto.CustomerChannelEntitlementResponse;
import com.pension.permission.api.dto.DisableChannelRequest;
import com.pension.permission.api.dto.EnableChannelRequest;
import com.pension.permission.api.dto.GetEntitlementRequest;
import com.pension.permission.api.dto.ReplaceChannelsRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 客户渠道开通记录 API.
 *
 * <p>管理客户开通的登录渠道（网上/网点等），包括查询、开通、关闭、批量替换。</p>
 */
@HttpExchange("/customer-channel-entitlement")
public interface CustomerChannelEntitlementApi {

  @PostExchange("/query")
  ApiResult<CustomerChannelEntitlementResponse> query(@RequestBody @Valid GetEntitlementRequest request);

  @PostExchange("/enable")
  ApiResult<CustomerChannelEntitlementResponse> enable(@RequestBody @Valid EnableChannelRequest request);

  @PostExchange("/disable")
  ApiResult<CustomerChannelEntitlementResponse> disable(@RequestBody @Valid DisableChannelRequest request);

  @PostExchange("/replace")
  ApiResult<CustomerChannelEntitlementResponse> replace(@RequestBody @Valid ReplaceChannelsRequest request);
}
