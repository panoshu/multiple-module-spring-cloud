package com.example.iam.api.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 禁用权限规则命令
 *
 * <p>将指定权限规则置为禁用状态,禁用后规则不再参与权限计算。
 *
 * @author iam-service
 */
public record DisablePermissionRuleCommand(
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
