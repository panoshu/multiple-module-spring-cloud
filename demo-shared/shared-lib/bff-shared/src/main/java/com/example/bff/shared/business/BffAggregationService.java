package com.example.bff.shared.business;

import com.example.bff.shared.business.dto.BatchOverviewResponse;
import com.example.bff.shared.business.dto.BffBatchOverviewRequest;
import com.example.bff.shared.registry.KernelApiRegistry;
import com.example.bff.shared.route.BusinessTypeRouter;
import com.example.core.api.application.query.FindApplicationListQuery;
import com.example.core.api.application.response.ApplicationSummaryResponse;
import com.example.core.api.batch.query.GetBatchDetailQuery;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.core.api.progress.query.GetBatchProgressQuery;
import com.example.core.api.progress.response.BatchProgressResponse;
import com.example.shared.web.core.api.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * BFF 聚合编排服务（互联网/内网共用）
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
   * <p>三个调用并发执行。降级策略：
   * <ul>
   *   <li>批次详情（主实体）返回业务错误 → 直接传播该错误，不进行聚合</li>
   *   <li>进度/申请单（补充数据）返回业务错误 → 降级为 null，聚合仍返回成功</li>
   *   <li>任意调用抛出传输异常（超时/连接失败等） → 降级为 null，聚合仍返回成功</li>
   * </ul>
   * 仅捕获 {@link RuntimeException}，避免吞掉 Error 等不可恢复异常。
   */
  public ApiResult<BatchOverviewResponse> getBatchOverview(BffBatchOverviewRequest request) {
    String serviceName = router.resolveServiceName(request.businessType());

    CompletableFuture<BatchDetailResponse> batchFuture = CompletableFuture.supplyAsync(() -> {
      ApiResult<BatchDetailResponse> result = kernelApiRegistry.getBatchApi(serviceName)
        .detail(new GetBatchDetailQuery(request.batchId()));
      if (result.isSuccess()) {
        return result.data();
      }
      // 主实体业务错误：封装为异常由 join() 抛出，在 allOf 之后统一处理
      throw new BffAggregationException(result.code(), result.message());
    }, taskExecutor).exceptionally(ex -> {
      if (ex.getCause() instanceof BffAggregationException) {
        // 业务错误不降级，重新抛出让调用方处理
        throw (BffAggregationException) ex.getCause();
      }
      log.warn("聚合调用批次详情降级: serviceName={}, batchId={}", serviceName, request.batchId(), ex.getCause());
      return null;
    });

    CompletableFuture<BatchProgressResponse> progressFuture = CompletableFuture.supplyAsync(() -> {
      try {
        ApiResult<BatchProgressResponse> result = kernelApiRegistry.getProgressApi(serviceName)
          .batchProgress(new GetBatchProgressQuery(request.batchId()));
        return result.isSuccess() ? result.data() : null;
      } catch (RuntimeException e) {
        log.warn("聚合调用批次进度降级: serviceName={}, batchId={}", serviceName, request.batchId(), e);
        return null;
      }
    }, taskExecutor);

    CompletableFuture<List<ApplicationSummaryResponse>> appsFuture = CompletableFuture.supplyAsync(() -> {
      try {
        ApiResult<List<ApplicationSummaryResponse>> result = kernelApiRegistry.getApplicationApi(serviceName)
          .list(new FindApplicationListQuery(request.batchId(), null));
        return result.isSuccess() ? result.data() : null;
      } catch (RuntimeException e) {
        log.warn("聚合调用申请单列表降级: serviceName={}, batchId={}", serviceName, request.batchId(), e);
        return null;
      }
    }, taskExecutor);

    try {
      CompletableFuture.allOf(batchFuture, progressFuture, appsFuture).join();
    } catch (Exception e) {
      if (e.getCause() instanceof BffAggregationException bae) {
        return ApiResult.failure(bae.code, bae.message);
      }
      throw e;
    }

    BatchOverviewResponse response = BffResponseAssembler.assemble(
      batchFuture.join(),
      progressFuture.join(),
      appsFuture.join()
    );
    return ApiResult.success(response);
  }
}
