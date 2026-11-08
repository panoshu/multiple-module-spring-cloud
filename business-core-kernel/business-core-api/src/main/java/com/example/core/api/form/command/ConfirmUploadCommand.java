package com.example.core.api.form.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 确认上传命令
 *
 * <p>前端直传文件服务后,携带文件服务返回的 fileId 回调本接口。
 *
 * @author panoshu
 */
public record ConfirmUploadCommand(
  @NotBlank(message = "批次ID不能为空") String batchId,
  @NotBlank(message = "表单ID不能为空") String formId,
  @NotBlank(message = "文件ID不能为空") String fileId,
  @NotBlank(message = "文件名不能为空") String fileName,
  String fileMd5
) {
}
