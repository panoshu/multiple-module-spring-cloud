package com.example.iam.api.query;

import jakarta.validation.constraints.NotNull;

/**
 * 二次授权状态查询
 *
 * <p>柜员端通过此查询轮询指定二次授权会话的当前状态。
 *
 * @author iam-service
 */
public record GetSecondaryAuthStatusQuery(
    /**
     * 二次授权会话 ID
     */
    @NotNull(message = "二次授权会话ID不能为空")
    Long sessionId
) {
}
