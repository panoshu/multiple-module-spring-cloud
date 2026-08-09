package com.example.bff.intranet.api.dto;

import com.example.bff.shared.route.BffRouteConfig;
import com.example.bff.shared.route.ChannelScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 路由配置请求
 *
 * @author bff
 */
public record BffRouteConfigRequest(
    @NotBlank(message = "业务类型不能为空") String businessType,
    @NotBlank(message = "服务名不能为空") String serviceName,
    @NotNull(message = "渠道范围不能为空") ChannelScope channelScope
) {
    public BffRouteConfig toRouteConfig() {
        return new BffRouteConfig(businessType, serviceName, channelScope);
    }
}
