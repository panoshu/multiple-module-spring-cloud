package com.example.iam.api.command;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * 创建用户命令
 *
 * <p>管理员通过此命令在不同渠道(网上/总部/网点)下创建用户。
 *
 * @author iam-service
 */
public record CreateUserCommand(
    /**
     * 渠道类型(INTERNET/HQ/BRANCH)
     */
    @NotBlank(message = "渠道类型不能为空")
    String channelType,
    /**
     * 登录名(渠道内唯一)
     */
    @NotBlank(message = "登录名不能为空")
    String loginName,
    /**
     * 显示名称(可选)
     */
    String displayName,
    /**
     * 邮箱(可选)
     */
    String email,
    /**
     * 手机号(可选)
     */
    String phone,
    /**
     * 所属机构(可选)
     */
    String organization,
    /**
     * 岗位(可选)
     */
    String position,
    /**
     * 所属网点编号(可选,网点渠道用户必填)
     */
    String branchId,
    /**
     * 员工工号(可选,总部/网点渠道用户必填)
     */
    String employeeNo,
    /**
     * 扩展属性(可选,业务自定义字段)
     */
    Map<String, String> extraAttributes,
    /**
     * 操作人 UserNo(审计用)
     */
    @NotBlank(message = "操作人不能为空")
    String operator
) {
}
