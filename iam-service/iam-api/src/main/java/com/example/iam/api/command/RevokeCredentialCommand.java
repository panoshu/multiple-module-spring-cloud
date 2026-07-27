package com.example.iam.api.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 撤销凭据命令
 *
 * <p>撤销指定凭据,撤销后该凭据无法用于登录。
 *
 * @author iam-service
 */
public record RevokeCredentialCommand(
    /**
     * 凭据 ID
     */
    @NotNull(message = "凭据ID不能为空")
    Long credentialId,
    /**
     * 操作人 UserNo(审计用)
     */
    @NotBlank(message = "操作人不能为空")
    String operator
) {
}
