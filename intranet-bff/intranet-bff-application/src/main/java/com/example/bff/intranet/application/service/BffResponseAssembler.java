package com.example.bff.intranet.application.service;

import com.example.bff.intranet.api.dto.BatchOverviewResponse;
import com.example.core.api.application.response.ApplicationSummaryResponse;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.core.api.progress.response.BatchProgressResponse;

import java.util.List;

/**
 * BFF 聚合响应组装器
 *
 * @author bff
 */
final class BffResponseAssembler {

    private BffResponseAssembler() {
    }

    /**
     * 组装批次概览响应。
     *
     * @param batchDetail   批次详情（可能为 null）
     * @param progress      批次进度（可能为 null）
     * @param applications  申请单列表（可能为 null）
     */
    static BatchOverviewResponse assemble(
            BatchDetailResponse batchDetail,
            BatchProgressResponse progress,
            List<ApplicationSummaryResponse> applications) {
        return new BatchOverviewResponse(
                batchDetail,
                progress,
                applications != null ? applications : List.of()
        );
    }
}
