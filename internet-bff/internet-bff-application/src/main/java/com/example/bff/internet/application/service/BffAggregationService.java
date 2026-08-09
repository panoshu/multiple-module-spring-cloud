package com.example.bff.internet.application.service;

import com.example.bff.internet.api.dto.BatchOverviewResponse;
import com.example.bff.internet.api.dto.BffBatchOverviewRequest;
import com.example.bff.shared.registry.KernelApiRegistry;
import com.example.bff.shared.route.BusinessTypeRouter;
import com.example.core.api.application.response.ApplicationSummaryResponse;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.core.api.progress.response.BatchProgressResponse;
import com.example.shared.web.core.api.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
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
@Slf4j
@Service
public class BffAggregationService {

  private final BusinessTypeRouter router;
  private final KernelApiRegistry kernelApiRegistry;
  private final AsyncTaskExecutor taskExecutor;

  public BffAggregationService(BusinessTypeRouter router,
                               KernelApiRegistry kernelApiRegistry,
                               AsyncTaskExecutor taskExecutor) {
    this.router = router;
    this.kernelApiRegistry = kernelApiRegistry;
    this.taskExecutor = taskExecutor;
  }

  /**
   * 获取批次概览：聚合批次详情 + 进度 + 申请单列表。
   *
   * <p>三个调用并发执行，各自独立成功/失败，失败的部分设为 null。
   * 传输/业务异常（下游宕机/超时）同样降级为 null，避免单个下游故障导致整个聚合请求 500；
   * 仅捕获 {@link RuntimeException}，避免吞掉 Error 等不可恢复异常。
   */
  public ApiResult<BatchOverviewResponse> getBatchOverview(BffBatchOverviewRequest request) {
    String serviceName = router.resolveServiceName(request.businessType());

    CompletableFuture<BatchDetailResponse> batchFuture = CompletableFuture.supplyAsync(() -> {
      try {
        ApiResult<BatchDetailResponse> result = kernelApiRegistry.getBatchApi(serviceName)
          .detail(request.toBatchDetailQuery());
        return result.isSuccess() ? result.data() : null;
      } catch (RuntimeException e) {
        log.warn("聚合调用批次详情降级: serviceName={}, batchId={}", serviceName, request.batchId(), e);
        return null;
      }
    }, taskExecutor);
    CompletableFuture<BatchProgressResponse> progressFuture = CompletableFuture.supplyAsync(() -> {
      try {
        ApiResult<BatchProgressResponse> result = kernelApiRegistry.getProgressApi(serviceName)
          .batchProgress(request.toProgressQuery());
        return result.isSuccess() ? result.data() : null;
      } catch (RuntimeException e) {
        log.warn("聚合调用批次进度降级: serviceName={}, batchId={}", serviceName, request.batchId(), e);
        return null;
      }
    }, taskExecutor);
    CompletableFuture<List<ApplicationSummaryResponse>> appsFuture = CompletableFuture.supplyAsync(() -> {
      try {
        ApiResult<List<ApplicationSummaryResponse>> result = kernelApiRegistry.getApplicationApi(serviceName)
          .list(request.toApplicationListQuery());
        return result.isSuccess() ? result.data() : null;
      } catch (RuntimeException e) {
        log.warn("聚合调用申请单列表降级: serviceName={}, batchId={}", serviceName, request.batchId(), e);
        return null;
      }
    }, taskExecutor);

    CompletableFuture.allOf(batchFuture, progressFuture, appsFuture).join();

    BatchOverviewResponse response = BffResponseAssembler.assemble(
      batchFuture.join(),
      progressFuture.join(),
      appsFuture.join()
    );
    return ApiResult.success(response);
  }
}
