package com.example.iam.api.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 经办人确认二次授权命令
 *
 * <p>经办人对柜员发起的二次授权请求进行确认(需校验经办人密码)。
 *
 * @author iam-service
 */
public record ConfirmSecondaryAuthCommand(
    /**
     * 二次授权会话 ID
     */
    @NotNull(message = "二次授权会话ID不能为空")
    Long sessionId,
    /**
     * 经办人密码(密文,用于身份再次校验)
     */
    @NotBlank(message = "经办人密码不能为空")
    String approverPassword
) {
}
