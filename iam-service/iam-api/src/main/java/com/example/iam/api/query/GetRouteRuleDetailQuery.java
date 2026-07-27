package com.example.iam.api.query;

import jakarta.validation.constraints.NotNull;

/**
 * 路由规则详情查询
 *
 * <p>按路由规则 ID 查询单条路由规则的详细信息。
 *
 * @author iam-service
 */
public record GetRouteRuleDetailQuery(
    /**
     * 路由规则 ID
     */
    @NotNull(message = "规则ID不能为空")
    Long ruleId
) {
}
