package com.example.core.api.batch.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 取消业务批次命令
 *
 * @author panoshu
 */
public record CancelBatchCommand(
    @NotNull(message = "批次ID不能为空") Long batchId,
    String reason
) {
}
