package com.example.core.api.form.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 申请上传 token 命令
 *
 * @author panoshu
 */
public record ApplyUploadTokenCommand(
  @NotBlank(message = "批次ID不能为空") String batchId,
  @NotBlank(message = "文件名不能为空") String fileName,
  @NotNull(message = "文件大小不能为空") @Positive(message = "文件大小必须为正数") Long fileSize,
  @NotBlank(message = "文件类型不能为空") String contentType
) {
}
