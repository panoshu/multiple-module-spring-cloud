package com.example.iam.api.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 撤销计划代办命令
 *
 * <p>撤销指定的计划代办关系,撤销后被授权方操作员不再具备代办权限。
 *
 * @author iam-service
 */
public record RevokePlanDelegationCommand(
    /**
     * 代办关系 ID
     */
    @NotNull(message = "代办关系ID不能为空")
    Long delegationId,
    /**
     * 撤销原因(可空)
     */
    String reason,
    /**
     * 操作人 UserNo(审计用)
     */
    @NotBlank(message = "操作人不能为空")
    String operator
) {
}
