package com.example.bff.intranet.api.dto;

import com.example.bff.shared.route.ChannelScope;

/**
 * 路由配置响应
 *
 * @param id           路由配置 ID
 * @param businessType 业务类型
 * @param serviceName  目标服务名
 * @param channelScope 渠道范围
 * @author bff
 */
public record BffRouteConfigResponse(
  Long id,
  String businessType,
  String serviceName,
  ChannelScope channelScope
) {
}
