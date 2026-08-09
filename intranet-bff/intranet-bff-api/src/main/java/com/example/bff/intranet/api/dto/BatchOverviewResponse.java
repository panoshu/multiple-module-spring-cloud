package com.example.bff.intranet.api.dto;

import com.example.core.api.application.response.ApplicationSummaryResponse;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.core.api.progress.response.BatchProgressResponse;

import java.util.List;

/**
 * 批次概览聚合响应
 *
 * <p>聚合批次详情、进度、申请单列表三个维度的数据。
 *
 * @param batchDetail  批次详情
 * @param progress     批次进度
 * @param applications 申请单列表
 * @author bff
 */
public record BatchOverviewResponse(
  BatchDetailResponse batchDetail,
  BatchProgressResponse progress,
  List<ApplicationSummaryResponse> applications
) {
}
