package com.example.bff.internet.api.dto;

import com.example.core.api.application.query.FindApplicationListQuery;
import com.example.core.api.batch.query.GetBatchDetailQuery;
import com.example.core.api.progress.query.GetBatchProgressQuery;
import jakarta.validation.constraints.NotBlank;

/**
 * 批次概览请求（聚合查询）
 *
 * @author bff
 */
public record BffBatchOverviewRequest(
    @NotBlank(message = "业务类型不能为空") String businessType,
    @NotBlank(message = "批次ID不能为空") String batchId
) {
    public GetBatchDetailQuery toBatchDetailQuery() {
        return new GetBatchDetailQuery(batchId);
    }

    public GetBatchProgressQuery toProgressQuery() {
        return new GetBatchProgressQuery(batchId);
    }

    public FindApplicationListQuery toApplicationListQuery() {
        return new FindApplicationListQuery(batchId, null);
    }
}
