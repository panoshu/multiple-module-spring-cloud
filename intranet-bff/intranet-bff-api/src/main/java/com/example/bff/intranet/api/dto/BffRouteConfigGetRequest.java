package com.example.bff.intranet.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 路由配置查询请求
 *
 * @author bff
 */
public record BffRouteConfigGetRequest(
    @NotNull(message = "ID不能为空") @Positive(message = "ID必须为正数") Long id
) {
}
