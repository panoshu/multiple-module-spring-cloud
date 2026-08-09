package com.example.bff.intranet.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 路由配置删除请求
 *
 * @author bff
 */
public record BffRouteConfigDeleteRequest(
    @NotNull(message = "ID不能为空") @Positive(message = "ID必须为正数") Long id
) {
}
