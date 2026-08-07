package com.example.auth.api;

import com.example.auth.api.command.DisableChannelRequest;
import com.example.auth.api.command.EnableChannelRequest;
import com.example.auth.api.command.GetEntitlementRequest;
import com.example.auth.api.command.ReplaceChannelsRequest;
import com.example.auth.api.dto.CustomerChannelEntitlementResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Optional;

/**
 * 客户渠道开通管理 API.
 *
 * @author auth-api
 */
@HttpExchange("/customer-channel-entitlement")
public interface CustomerChannelEntitlementApi {

    @PostExchange("/enable")
    ApiResult<CustomerChannelEntitlementResponse> enable(@RequestBody @Valid EnableChannelRequest request);

    @PostExchange("/disable")
    ApiResult<Void> disable(@RequestBody @Valid DisableChannelRequest request);

    @PostExchange("/replace")
    ApiResult<CustomerChannelEntitlementResponse> replace(@RequestBody @Valid ReplaceChannelsRequest request);

    @PostExchange("/get")
    ApiResult<Optional<CustomerChannelEntitlementResponse>> get(@RequestBody @Valid GetEntitlementRequest request);
}
