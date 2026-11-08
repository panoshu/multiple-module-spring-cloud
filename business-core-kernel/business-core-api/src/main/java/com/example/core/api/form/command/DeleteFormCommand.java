package com.example.core.api.form.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 删除表单命令
 *
 * @author panoshu
 */
public record DeleteFormCommand(
  @NotBlank(message = "批次ID不能为空") String batchId,
  @NotBlank(message = "表单ID不能为空") String formId
) {
}
