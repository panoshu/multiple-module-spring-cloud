package com.example.bff.shared.route;

/**
 * BFF 路由配置值对象
 *
 * @param businessType 业务类型
 * @param serviceName  目标服务名
 * @param channelScope 渠道范围
 *
 * @author bff
 */
public record BffRouteConfig(
    String businessType,
    String serviceName,
    ChannelScope channelScope
) {
}
