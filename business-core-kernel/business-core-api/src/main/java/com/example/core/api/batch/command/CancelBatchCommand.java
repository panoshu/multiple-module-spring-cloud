package com.example.core.api.batch.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 取消业务批次命令
 *
 * @author panoshu
 */
public record CancelBatchCommand(
    @NotBlank(message = "批次ID不能为空") String batchId,
    String reason
) {
}
