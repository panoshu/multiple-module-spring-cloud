package com.example.bff.shared.business;

import com.example.bff.shared.business.dto.BatchOverviewResponse;
import com.example.bff.shared.business.dto.BffBatchOverviewRequest;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

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
  @DisplayName("getBatchOverview 补充数据返回业务错误时降级为 null，聚合仍成功")
  void getBatchOverview_supplementaryBusinessError_degradesToNull() {
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
    when(progressApi.batchProgress(any())).thenReturn(ApiResult.failure("SERVICE.ANNUITY.0006", "批次不存在"));
    when(applicationApi.list(any())).thenReturn(ApiResult.success(List.of()));

    ApiResult<BatchOverviewResponse> result = aggregationService.getBatchOverview(request);

    assertTrue(result.isSuccess());
    assertNotNull(result.data().batchDetail());
    assertNull(result.data().progress());
    assertTrue(result.data().applications().isEmpty());
  }

  @Test
  @DisplayName("getBatchOverview 主实体返回业务错误时传播错误码，不降级")
  void getBatchOverview_primaryBusinessError_propagatesError() {
    BffBatchOverviewRequest request = new BffBatchOverviewRequest("ACC_PLAN_CREATE", "batch-404");

    when(router.resolveServiceName("ACC_PLAN_CREATE")).thenReturn("annuity-service");
    when(kernelApiRegistry.getBatchApi("annuity-service")).thenReturn(batchApi);
    when(kernelApiRegistry.getProgressApi("annuity-service")).thenReturn(progressApi);
    when(kernelApiRegistry.getApplicationApi("annuity-service")).thenReturn(applicationApi);

    when(batchApi.detail(any())).thenReturn(ApiResult.failure("SERVICE.ANNUITY.0006", "批次不存在"));
    when(progressApi.batchProgress(any())).thenReturn(ApiResult.success(
      new BatchProgressResponse("batch-404", "PROCESSING", 0, 0, 0, 0)));
    when(applicationApi.list(any())).thenReturn(ApiResult.success(List.of()));

    ApiResult<BatchOverviewResponse> result = aggregationService.getBatchOverview(request);

    assertTrue(result.isFailure());
    assertEquals("SERVICE.ANNUITY.0006", result.code());
    assertEquals("批次不存在", result.message());
  }

  @Test
  @DisplayName("getBatchOverview 下游抛传输异常时聚合降级为 null 而非 500")
  void getBatchOverview_transportExceptionDegradesToNull() {
    BffBatchOverviewRequest request = new BffBatchOverviewRequest("ACC_PLAN_CREATE", "batch-789");

    when(router.resolveServiceName("ACC_PLAN_CREATE")).thenReturn("annuity-service");
    when(kernelApiRegistry.getBatchApi("annuity-service")).thenReturn(batchApi);
    when(kernelApiRegistry.getProgressApi("annuity-service")).thenReturn(progressApi);
    when(kernelApiRegistry.getApplicationApi("annuity-service")).thenReturn(applicationApi);

    BatchDetailResponse batchDetail = new BatchDetailResponse(
      "batch-789", "ACC_PLAN_CREATE", "PLAN001", "C001", "客户A",
      "PROCESSING", 10, 5, 3, 2,
      LocalDateTime.now(), LocalDateTime.now(), List.of()
    );

    when(batchApi.detail(any())).thenReturn(ApiResult.success(batchDetail));
    when(progressApi.batchProgress(any())).thenThrow(new RuntimeException("下游超时"));
    when(applicationApi.list(any())).thenReturn(ApiResult.success(List.of()));

    ApiResult<BatchOverviewResponse> result = aggregationService.getBatchOverview(request);

    assertTrue(result.isSuccess());
    assertNotNull(result.data().batchDetail());
    assertNull(result.data().progress());
    assertTrue(result.data().applications().isEmpty());
  }
}
