package com.example.iam.api.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 总部渠道登录命令
 *
 * <p>用于总部员工通过工号 + 密码方式登录 IAM 系统。
 *
 * @author iam-service
 */
public record HqLoginCommand(
    /**
     * 员工工号
     */
    @NotBlank(message = "员工工号不能为空")
    String employeeNo,
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
