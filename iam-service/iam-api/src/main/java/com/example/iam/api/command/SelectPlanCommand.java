package com.example.iam.api.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 选择当前办理计划命令
 *
 * <p>用户在登录后通过此命令选定当前会话要办理业务的计划。
 * 网点渠道用户必须同时指定客户编号;网上/总部渠道可省略客户编号。
 *
 * @author iam-service
 */
public record SelectPlanCommand(
    /**
     * 计划编号
     */
    @NotBlank(message = "计划编号不能为空")
    String planId,
    /**
     * 客户编号(可选,网点渠道必填)
     */
    String customerNo
) {
}
