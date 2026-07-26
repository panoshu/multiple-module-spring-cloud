package com.example.core.api.material.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 逐个绑定材料命令
 *
 * @author panoshu
 */
public record BindIndividualMaterialCommand(
    @NotBlank(message = "申请单ID不能为空") String applicationId,
    @NotBlank(message = "材料编码不能为空") String materialCode,
    @NotBlank(message = "文件ID不能为空") String fileId,
    @NotBlank(message = "文件名不能为空") String fileName
) {
}
