package com.example.bff.intranet.api.dto;

/**
 * 支持的业务类型响应
 *
 * @param businessType 业务类型
 * @param serviceName  目标服务名
 * @param channelScope 渠道范围
 * @author bff
 */
public record BffBusinessTypeResponse(
  String businessType,
  String serviceName,
  String channelScope
) {
}
