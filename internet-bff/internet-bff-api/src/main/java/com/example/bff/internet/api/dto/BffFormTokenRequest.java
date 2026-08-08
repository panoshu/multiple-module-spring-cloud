package com.example.bff.internet.api.dto;

import com.example.core.api.form.command.ApplyUploadTokenCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 申请上传 token 请求
 *
 * @author bff
 */
public record BffFormTokenRequest(
    @NotBlank(message = "业务类型不能为空") String businessType,
    @NotBlank(message = "批次ID不能为空") String batchId,
    @NotBlank(message = "文件名不能为空") String fileName,
    @NotNull(message = "文件大小不能为空") @Positive(message = "文件大小必须为正数") Long fileSize,
    @NotBlank(message = "文件类型不能为空") String contentType
) {
    public ApplyUploadTokenCommand toCommand() {
        return new ApplyUploadTokenCommand(batchId, fileName, fileSize, contentType);
    }
}
