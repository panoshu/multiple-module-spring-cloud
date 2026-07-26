package com.example.core.api.application.command;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * 推进申请单命令
 *
 * <p>{@code actionPayload} 为可选的业务参数,当前版本暂不消费,保留供后续扩展。
 *
 * @author panoshu
 */
public record AdvanceStepCommand(
    @NotBlank(message = "申请单ID不能为空") String applicationId,
    Map<String, Object> actionPayload
) {
}
