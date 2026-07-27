package com.example.iam.api.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 网点渠道登录命令
 *
 * <p>用于网点柜员通过柜员号 + 密码方式登录 IAM 系统。
 *
 * @author iam-service
 */
public record BranchLoginCommand(
    /**
     * 柜员号
     */
    @NotBlank(message = "柜员号不能为空")
    String tellerNo,
    /**
     * 登录密码(密文,由前端加密后传输)
     */
    @NotBlank(message = "密码不能为空")
    String password,
    /**
     * 登录客户端 IP(可选,由网关透传)
     */
    String loginIp,
    /**
     * 登录客户端 User-Agent(可选,用于设备指纹)
     */
    String userAgent
) {
}
