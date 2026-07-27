package com.example.iam.api.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 启用权限规则命令
 *
 * <p>将指定权限规则置为启用状态,启用后规则重新参与权限计算。
 *
 * @author iam-service
 */
public record EnablePermissionRuleCommand(
    /**
     * 权限规则 ID
     */
    @NotNull(message = "规则ID不能为空")
    Long ruleId,
    /**
     * 操作人 UserNo(审计用)
     */
    @NotBlank(message = "操作人不能为空")
    String operator
) {
}
