package com.example.iam.api.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 锁定用户命令
 *
 * <p>对指定用户进行锁定(如登录失败次数超限触发),锁定后无法登录,需通过解锁流程恢复。
 *
 * @author iam-service
 */
public record LockUserCommand(
    /**
     * 用户 ID
     */
    @NotNull(message = "用户ID不能为空")
    Long userId,
    /**
     * 锁定原因
     */
    @NotBlank(message = "锁定原因不能为空")
    String reason,
    /**
     * 操作人 UserNo(审计用)
     */
    @NotBlank(message = "操作人不能为空")
    String operator
) {
}
