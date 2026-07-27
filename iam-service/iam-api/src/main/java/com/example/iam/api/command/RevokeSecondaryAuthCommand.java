package com.example.iam.api.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 撤销二次授权命令
 *
 * <p>柜员或经办人在二次授权会话完成前主动撤销,需填写撤销原因。
 *
 * @author iam-service
 */
public record RevokeSecondaryAuthCommand(
    /**
     * 二次授权会话 ID
     */
    @NotNull(message = "二次授权会话ID不能为空")
    Long sessionId,
    /**
     * 撤销原因
     */
    @NotBlank(message = "撤销原因不能为空")
    String reason
) {
}
