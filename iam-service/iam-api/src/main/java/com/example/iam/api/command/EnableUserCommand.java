package com.example.iam.api.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 启用用户命令
 *
 * <p>管理员对已禁用的用户进行启用操作,恢复登录能力。
 *
 * @author iam-service
 */
public record EnableUserCommand(
    /**
     * 用户 ID
     */
    @NotNull(message = "用户ID不能为空")
    Long userId,
    /**
     * 操作人 UserNo(审计用)
     */
    @NotBlank(message = "操作人不能为空")
    String operator
) {
}
