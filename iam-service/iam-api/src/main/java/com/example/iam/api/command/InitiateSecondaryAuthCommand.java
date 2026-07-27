package com.example.iam.api.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 发起二次授权命令
 *
 * <p>网点柜员在办理高风险业务时,通过此命令向指定经办人发起二次授权请求。
 *
 * @author iam-service
 */
public record InitiateSecondaryAuthCommand(
    /**
     * 经办人登录名(审批人)
     */
    @NotBlank(message = "经办人登录名不能为空")
    String approverLoginName,
    /**
     * 经办人所属客户编号
     */
    @NotBlank(message = "经办人客户编号不能为空")
    String approverCustomerId,
    /**
     * 计划编号(关联业务计划)
     */
    @NotBlank(message = "计划编号不能为空")
    String planId,
    /**
     * 待办理业务的客户编号(可选,部分业务场景需要)
     */
    String customerNo
) {
}
