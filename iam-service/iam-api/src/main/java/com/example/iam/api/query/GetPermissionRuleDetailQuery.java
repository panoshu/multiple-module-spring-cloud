package com.example.iam.api.query;

import jakarta.validation.constraints.NotNull;

/**
 * 权限规则详情查询
 *
 * <p>按权限规则 ID 查询单条权限规则的详细信息。
 *
 * @author iam-service
 */
public record GetPermissionRuleDetailQuery(
    /**
     * 权限规则 ID
     */
    @NotNull(message = "规则ID不能为空")
    Long ruleId
) {
}
