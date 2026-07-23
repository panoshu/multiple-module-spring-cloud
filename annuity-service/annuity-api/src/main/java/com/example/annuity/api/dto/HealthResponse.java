package com.example.annuity.api.dto;

/**
 * 服务健康检查响应
 *
 * @param service   服务名称
 * @param status    服务状态
 * @param timestamp 检查时间
 * @author annuity-service
 */
public record HealthResponse(
    String service,
    String status,
    String timestamp
) {
}
