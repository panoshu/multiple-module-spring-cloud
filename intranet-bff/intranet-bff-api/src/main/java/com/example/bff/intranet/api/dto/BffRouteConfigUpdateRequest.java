package com.example.bff.intranet.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 路由配置更新请求
 *
 * @author bff
 */
public record BffRouteConfigUpdateRequest(
    @NotNull(message = "ID不能为空") @Positive(message = "ID必须为正数") Long id,
    @NotNull(message = "路由配置不能为空") BffRouteConfigRequest config
) {
}
