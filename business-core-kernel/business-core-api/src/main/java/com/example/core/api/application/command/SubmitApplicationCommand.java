package com.example.core.api.application.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 提交申请单命令
 *
 * @author panoshu
 */
public record SubmitApplicationCommand(
  @NotBlank(message = "申请单ID不能为空") String applicationId
) {
}
