package com.example.bff.internet.application.service;

import com.example.bff.internet.api.dto.BatchOverviewResponse;
import com.example.bff.internet.api.dto.BffBatchOverviewRequest;
import com.example.bff.shared.registry.KernelApiRegistry;
import com.example.bff.shared.route.BusinessTypeRouter;
import com.example.core.api.application.response.ApplicationSummaryResponse;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.core.api.progress.response.BatchProgressResponse;
import com.example.shared.web.core.api.ApiResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * BFF 聚合编排服务
 *
 * <p>并发调用多个 kernel API，聚合为单个响应。
 *
 * @author bff
 */
@Service
public class BffAggregationService {

    private final BusinessTypeRouter router;
    private final KernelApiRegistry kernelApiRegistry;

    public BffAggregationService(BusinessTypeRouter router, KernelApiRegistry kernelApiRegistry) {
        this.router = router;
        this.kernelApiRegistry = kernelApiRegistry;
    }

    /**
     * 获取批次概览：聚合批次详情 + 进度 + 申请单列表。
     *
     * <p>三个调用并发执行，各自独立成功/失败，失败的部分设为 null。
     */
    public ApiResult<BatchOverviewResponse> getBatchOverview(BffBatchOverviewRequest request) {
        String serviceName = router.resolveServiceName(request.businessType());

        CompletableFuture<BatchDetailResponse> batchFuture = CompletableFuture.supplyAsync(() -> {
            ApiResult<BatchDetailResponse> result = kernelApiRegistry.getBatchApi(serviceName)
                    .detail(request.toBatchDetailQuery());
            return result.isSuccess() ? result.data() : null;
        });
        CompletableFuture<BatchProgressResponse> progressFuture = CompletableFuture.supplyAsync(() -> {
            ApiResult<BatchProgressResponse> result = kernelApiRegistry.getProgressApi(serviceName)
                    .batchProgress(request.toProgressQuery());
            return result.isSuccess() ? result.data() : null;
        });
        CompletableFuture<List<ApplicationSummaryResponse>> appsFuture = CompletableFuture.supplyAsync(() -> {
            ApiResult<List<ApplicationSummaryResponse>> result = kernelApiRegistry.getApplicationApi(serviceName)
                    .list(request.toApplicationListQuery());
            return result.isSuccess() ? result.data() : null;
        });

        CompletableFuture.allOf(batchFuture, progressFuture, appsFuture).join();

        BatchOverviewResponse response = BffResponseAssembler.assemble(
                batchFuture.join(),
                progressFuture.join(),
                appsFuture.join()
        );
        return ApiResult.success(response);
    }
}
