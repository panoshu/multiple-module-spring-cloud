package com.example.iam.api.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 网上渠道登录命令
 *
 * <p>用于互联网用户通过登录名 + 密码方式登录 IAM 系统。
 *
 * @author iam-service
 */
public record InternetLoginCommand(
    /**
     * 登录名(用户名/邮箱/手机号)
     */
    @NotBlank(message = "登录名不能为空")
    String loginName,
    /**
     * 登录密码(密文,由前端加密后传输)
     */
    @NotBlank(message = "密码不能为空")
    String password,
    /**
     * 图形验证码(可选,触发风控时必填)
     */
    String captcha,
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
