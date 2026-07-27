package com.example.iam.api.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 登出命令
 *
 * <p>三渠道(网上/总部/网点)通用的登出命令,服务端根据当前 sa-token 上下文销毁会话。
 *
 * @author iam-service
 */
public record LogoutCommand(
    /**
     * 渠道类型(INTERNET/HQ/BRANCH)
     */
    @NotBlank(message = "渠道类型不能为空")
    String channelType
) {
}
