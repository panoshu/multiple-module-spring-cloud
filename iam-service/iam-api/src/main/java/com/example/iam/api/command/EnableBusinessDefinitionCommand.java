package com.example.iam.api.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 启用业务定义命令
 *
 * <p>将指定业务定义置为启用状态,启用后该业务可重新用于权限规则与代办配置。
 *
 * @author iam-service
 */
public record EnableBusinessDefinitionCommand(
    /**
     * 业务定义 ID
     */
    @NotNull(message = "业务定义ID不能为空")
    Long definitionId,
    /**
     * 操作人 UserNo(审计用)
     */
    @NotBlank(message = "操作人不能为空")
    String operator
) {
}
