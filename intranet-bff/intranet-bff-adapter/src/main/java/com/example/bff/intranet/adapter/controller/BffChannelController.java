package com.example.bff.intranet.adapter.controller;

import com.example.auth.api.command.DisableChannelRequest;
import com.example.auth.api.command.EnableChannelRequest;
import com.example.auth.api.command.ReplaceChannelsRequest;
import com.example.auth.api.dto.CustomerChannelEntitlementResponse;
import com.example.auth.api.query.GetEntitlementRequest;
import com.example.bff.intranet.api.BffChannelApi;
import com.example.bff.intranet.application.service.ChannelManagementService;
import com.example.shared.web.core.api.ApiResult;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 渠道开通管理 Controller
 *
 * <p>透明转发到 auth-service 的 CustomerChannelEntitlementApi。
 *
 * @author bff
 */
@RestController
public class BffChannelController implements BffChannelApi {

    private final ChannelManagementService channelManagementService;

    public BffChannelController(ChannelManagementService channelManagementService) {
        this.channelManagementService = channelManagementService;
    }

    @Override
    public ApiResult<CustomerChannelEntitlementResponse> enable(EnableChannelRequest request) {
        return channelManagementService.enable(request);
    }

    @Override
    public ApiResult<Void> disable(DisableChannelRequest request) {
        return channelManagementService.disable(request);
    }

    @Override
    public ApiResult<CustomerChannelEntitlementResponse> replace(ReplaceChannelsRequest request) {
        return channelManagementService.replace(request);
    }

    @Override
    public ApiResult<Optional<CustomerChannelEntitlementResponse>> get(GetEntitlementRequest request) {
        return channelManagementService.get(request);
    }
}
