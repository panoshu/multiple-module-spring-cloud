package com.example.bff.intranet.api.dto;

/**
 * BFF 系统信息响应
 *
 * @param channelScope 渠道范围（INTRANET）
 * @param serviceName  服务名（spring.application.name）
 * @param port         服务端口
 * @param contextPath  上下文路径
 *
 * @author bff
 */
public record BffSystemInfoResponse(
    String channelScope,
    String serviceName,
    String port,
    String contextPath
) {
}
