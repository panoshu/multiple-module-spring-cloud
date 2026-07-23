package com.example.annuity.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 跨服务调用文件服务请求
 *
 * @param fileTaskId 文件任务ID
 * @author annuity-service
 */
public record LinkFileRequest(
    @NotBlank(message = "文件任务ID不能为空")
    String fileTaskId
) {
}
