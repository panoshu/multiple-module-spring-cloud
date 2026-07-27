package com.example.iam.api.query;

import jakarta.validation.constraints.NotNull;

/**
 * 用户详情查询
 *
 * <p>根据用户 ID 查询单个用户的详细信息(含档案、当前凭据摘要等)。
 *
 * @author iam-service
 */
public record GetUserDetailQuery(
    /**
     * 用户 ID
     */
    @NotNull(message = "用户ID不能为空")
    Long userId
) {
}
