package com.example.bff.internet.api.dto;

import com.example.core.api.application.command.SubmitApplicationCommand;
import jakarta.validation.constraints.NotBlank;

/**
 * 提交申请单请求
 *
 * @author bff
 */
public record BffSubmitRequest(
    @NotBlank(message = "业务类型不能为空") String businessType,
    @NotBlank(message = "申请单ID不能为空") String applicationId
) {
    public SubmitApplicationCommand toCommand() {
        return new SubmitApplicationCommand(applicationId);
    }
}
