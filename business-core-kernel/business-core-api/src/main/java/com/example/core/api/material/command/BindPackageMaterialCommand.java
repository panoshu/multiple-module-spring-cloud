package com.example.core.api.material.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 打包绑定材料命令
 *
 * @author panoshu
 */
public record BindPackageMaterialCommand(
    @NotBlank(message = "申请单ID不能为空") String applicationId,
    @NotBlank(message = "文件ID不能为空") String fileId,
    @NotBlank(message = "文件名不能为空") String fileName
) {
}
