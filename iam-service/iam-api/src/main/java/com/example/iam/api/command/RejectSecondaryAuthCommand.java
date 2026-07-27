package com.example.iam.api.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 经办人拒绝二次授权命令
 *
 * <p>经办人对柜员发起的二次授权请求进行拒绝,需填写拒绝原因。
 *
 * @author iam-service
 */
public record RejectSecondaryAuthCommand(
    /**
     * 二次授权会话 ID
     */
    @NotNull(message = "二次授权会话ID不能为空")
    Long sessionId,
    /**
     * 拒绝原因
     */
    @NotBlank(message = "拒绝原因不能为空")
    String reason
) {
}
