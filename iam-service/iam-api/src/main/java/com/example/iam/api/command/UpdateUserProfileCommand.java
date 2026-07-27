package com.example.iam.api.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 更新用户档案命令
 *
 * <p>更新指定用户的档案信息(邮箱、手机号、机构、岗位、扩展属性等)。
 *
 * @author iam-service
 */
public record UpdateUserProfileCommand(
    /**
     * 用户 ID
     */
    @NotNull(message = "用户ID不能为空")
    Long userId,
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
     * 所属网点编号(可选)
     */
    String branchId,
    /**
     * 员工工号(可选)
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
