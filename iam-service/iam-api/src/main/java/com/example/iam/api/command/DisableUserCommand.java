package com.example.iam.api.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 禁用用户命令
 *
 * <p>管理员对指定用户进行禁用操作,禁用后用户无法登录。
 *
 * @author iam-service
 */
public record DisableUserCommand(
    /**
     * 用户 ID
     */
    @NotNull(message = "用户ID不能为空")
    Long userId,
    /**
     * 禁用原因
     */
    @NotBlank(message = "禁用原因不能为空")
    String reason,
    /**
     * 操作人 UserNo(审计用)
     */
    @NotBlank(message = "操作人不能为空")
    String operator
) {
}
