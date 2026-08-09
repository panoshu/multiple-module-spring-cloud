package com.example.bff.intranet.api.dto;

import com.example.core.api.batch.query.GetBatchDetailQuery;
import jakarta.validation.constraints.NotBlank;

/**
 * 批次详情请求
 *
 * @author bff
 */
public record BffBatchDetailRequest(
    @NotBlank(message = "业务类型不能为空") String businessType,
    @NotBlank(message = "批次ID不能为空") String batchId
) {
    public GetBatchDetailQuery toQuery() {
        return new GetBatchDetailQuery(batchId);
    }
}
