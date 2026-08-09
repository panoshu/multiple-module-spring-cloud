package com.example.bff.intranet.application.service;

import com.example.bff.intranet.api.dto.BatchOverviewResponse;
import com.example.bff.intranet.api.dto.BffBatchOverviewRequest;
import com.example.bff.shared.registry.KernelApiRegistry;
import com.example.bff.shared.route.BusinessTypeRouter;
import com.example.core.api.application.BusinessApplicationApi;
import com.example.core.api.application.response.ApplicationSummaryResponse;
import com.example.core.api.batch.BusinessBatchApi;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.core.api.progress.BusinessProgressApi;
import com.example.core.api.progress.response.BatchProgressResponse;
import com.example.shared.web.core.api.ApiResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.AsyncTaskExecutor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BffAggregationServiceTest {

    @Mock
    private BusinessTypeRouter router;
    @Mock
    private KernelApiRegistry kernelApiRegistry;
    @Mock
    private AsyncTaskExecutor taskExecutor;
    @Mock
    private BusinessBatchApi batchApi;
    @Mock
    private BusinessProgressApi progressApi;
    @Mock
    private BusinessApplicationApi applicationApi;

    @InjectMocks
    private BffAggregationService aggregationService;

    /**
     * 令注入的 AsyncTaskExecutor 同步执行提交的 Runnable，
     * 使 CompletableFuture.supplyAsync(supplier, taskExecutor) 在当前线程内完成。
     */
    @BeforeEach
    void stubExecutorSynchronous() {
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(taskExecutor).execute(any(Runnable.class));
    }

    @Test
    @DisplayName("getBatchOverview 聚合批次详情/进度/申请单列表")
    void getBatchOverview_aggregatesThreeApis() {
        BffBatchOverviewRequest request = new BffBatchOverviewRequest("ACC_PLAN_CREATE", "batch-123");

        when(router.resolveServiceName("ACC_PLAN_CREATE")).thenReturn("annuity-service");
        when(kernelApiRegistry.getBatchApi("annuity-service")).thenReturn(batchApi);
        when(kernelApiRegistry.getProgressApi("annuity-service")).thenReturn(progressApi);
        when(kernelApiRegistry.getApplicationApi("annuity-service")).thenReturn(applicationApi);

        BatchDetailResponse batchDetail = new BatchDetailResponse(
                "batch-123", "ACC_PLAN_CREATE", "PLAN001", "C001", "客户A",
                "PROCESSING", 10, 5, 3, 2,
                LocalDateTime.now(), LocalDateTime.now(), List.of()
        );
        BatchProgressResponse progress = new BatchProgressResponse(
                "batch-123", "PROCESSING", 5, 3, 2, 0
        );
        List<ApplicationSummaryResponse> applications = List.of(
                new ApplicationSummaryResponse("app-1", "batch-123", "SUBMITTED", "STEP1",
                        LocalDateTime.now(), LocalDateTime.now())
        );

        when(batchApi.detail(any())).thenReturn(ApiResult.success(batchDetail));
        when(progressApi.batchProgress(any())).thenReturn(ApiResult.success(progress));
        when(applicationApi.list(any())).thenReturn(ApiResult.success(applications));

        ApiResult<BatchOverviewResponse> result = aggregationService.getBatchOverview(request);

        assertTrue(result.isSuccess());
        assertEquals(batchDetail, result.data().batchDetail());
        assertEquals(progress, result.data().progress());
        assertEquals(applications, result.data().applications());
    }

    @Test
    @DisplayName("getBatchOverview 下游返回失败时聚合结果仍包含成功部分")
    void getBatchOverview_partialFailure() {
        BffBatchOverviewRequest request = new BffBatchOverviewRequest("ACC_PLAN_CREATE", "batch-456");

        when(router.resolveServiceName("ACC_PLAN_CREATE")).thenReturn("annuity-service");
        when(kernelApiRegistry.getBatchApi("annuity-service")).thenReturn(batchApi);
        when(kernelApiRegistry.getProgressApi("annuity-service")).thenReturn(progressApi);
        when(kernelApiRegistry.getApplicationApi("annuity-service")).thenReturn(applicationApi);

        BatchDetailResponse batchDetail = new BatchDetailResponse(
                "batch-456", "ACC_PLAN_CREATE", "PLAN001", "C001", "客户A",
                "PROCESSING", 10, 5, 3, 2,
                LocalDateTime.now(), LocalDateTime.now(), List.of()
        );

        when(batchApi.detail(any())).thenReturn(ApiResult.success(batchDetail));
        when(progressApi.batchProgress(any())).thenReturn(ApiResult.failure("SERVICE.BFF.0002", "下游服务调用失败"));
        when(applicationApi.list(any())).thenReturn(ApiResult.success(List.of()));

        ApiResult<BatchOverviewResponse> result = aggregationService.getBatchOverview(request);

        assertTrue(result.isSuccess());
        assertNotNull(result.data().batchDetail());
        assertNull(result.data().progress());
        assertTrue(result.data().applications().isEmpty());
    }

    @Test
    @DisplayName("getBatchOverview 下游抛运行时异常时该部分降级为 null")
    void getBatchOverview_downstreamExceptionDegrades() {
        BffBatchOverviewRequest request = new BffBatchOverviewRequest("ACC_PLAN_CREATE", "batch-789");

        when(router.resolveServiceName("ACC_PLAN_CREATE")).thenReturn("annuity-service");
        when(kernelApiRegistry.getBatchApi("annuity-service")).thenReturn(batchApi);
        when(kernelApiRegistry.getProgressApi("annuity-service")).thenReturn(progressApi);
        when(kernelApiRegistry.getApplicationApi("annuity-service")).thenReturn(applicationApi);

        when(batchApi.detail(any())).thenThrow(new IllegalStateException("下游连接超时"));
        when(progressApi.batchProgress(any())).thenReturn(ApiResult.success(
                new BatchProgressResponse("batch-789", "PROCESSING", 5, 3, 2, 0)));
        when(applicationApi.list(any())).thenReturn(ApiResult.success(List.of()));

        ApiResult<BatchOverviewResponse> result = aggregationService.getBatchOverview(request);

        assertTrue(result.isSuccess());
        assertNull(result.data().batchDetail());
        assertEquals(5, result.data().progress().totalApplicationCount());
        assertTrue(result.data().applications().isEmpty());
    }
}
