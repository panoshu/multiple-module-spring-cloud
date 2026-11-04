package com.example.bff.internet.application;

import com.example.annuity.api.kernel.AnnuityApplicationApi;
import com.example.annuity.api.kernel.AnnuityBatchApi;
import com.example.annuity.api.kernel.AnnuityFormApi;
import com.example.annuity.api.kernel.AnnuityMaterialApi;
import com.example.annuity.api.kernel.AnnuityProgressApi;
import com.example.bff.shared.business.BffAggregationException;
import com.example.bff.shared.business.dto.BatchOverviewResponse;
import com.example.bff.shared.business.dto.BffApplicationDetailRequest;
import com.example.bff.shared.business.dto.BffBatchDetailRequest;
import com.example.bff.shared.business.dto.BffBatchOverviewRequest;
import com.example.bff.shared.business.dto.BffCreateBatchRequest;
import com.example.bff.shared.business.dto.BffFormTokenRequest;
import com.example.bff.shared.business.dto.BffListMaterialsRequest;
import com.example.bff.shared.business.dto.BffSubmitRequest;
import com.example.core.api.application.command.SubmitApplicationCommand;
import com.example.core.api.application.query.FindApplicationListQuery;
import com.example.core.api.application.query.GetApplicationDetailQuery;
import com.example.core.api.application.response.ApplicationDetailResponse;
import com.example.core.api.application.response.ApplicationSummaryResponse;
import com.example.core.api.application.response.SubmitResponse;
import com.example.core.api.batch.command.CreateBatchCommand;
import com.example.core.api.batch.query.GetBatchDetailQuery;
import com.example.core.api.batch.response.BatchCreatedResponse;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.core.api.form.command.ApplyUploadTokenCommand;
import com.example.core.api.form.response.UploadTokenResponse;
import com.example.core.api.material.query.ListMaterialsQuery;
import com.example.core.api.material.response.MaterialItemResponse;
import com.example.core.api.progress.query.GetBatchProgressQuery;
import com.example.core.api.progress.response.BatchProgressResponse;
import com.example.shared.web.core.api.ApiResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AnnuityScenarioHandler 单元测试
 */
class AnnuityScenarioHandlerTest {

  private AnnuityBatchApi batchApi;
  private AnnuityFormApi formApi;
  private AnnuityApplicationApi applicationApi;
  private AnnuityMaterialApi materialApi;
  private AnnuityProgressApi progressApi;
  private Executor asyncExecutor;
  private AnnuityScenarioHandler handler;

  @BeforeEach
  void setUp() {
    batchApi = mock(AnnuityBatchApi.class);
    formApi = mock(AnnuityFormApi.class);
    applicationApi = mock(AnnuityApplicationApi.class);
    materialApi = mock(AnnuityMaterialApi.class);
    progressApi = mock(AnnuityProgressApi.class);
    // 使用直接执行器，让 CompletableFuture 在同一线程完成
    asyncExecutor = Runnable::run;
    handler = new AnnuityScenarioHandler(
      batchApi, formApi, applicationApi, materialApi, progressApi, asyncExecutor
    );
  }

  @Test
  @DisplayName("supportedTypes 返回 ANNUITY_OPEN 和 ANNUITY_CHANGE")
  void supportedTypes_returnsAnnuityTypes() {
    assertEquals(Set.of("ANNUITY_OPEN", "ANNUITY_CHANGE"), handler.supportedTypes());
  }

  @Nested
  @DisplayName("createBatch")
  class CreateBatch {

    @Test
    @DisplayName("BFF Request 正确映射到 CreateBatchCommand 并委托 batchApi")
    void createBatch_mapsAndDelegates() {
      BffCreateBatchRequest request = new BffCreateBatchRequest("ANNUITY_OPEN", "P001", "备注");
      BatchCreatedResponse response = new BatchCreatedResponse("batch-1", "CREATED", LocalDateTime.now());
      when(batchApi.create(any(CreateBatchCommand.class))).thenReturn(ApiResult.success(response));

      ApiResult<BatchCreatedResponse> result = handler.createBatch(request);

      assertTrue(result.isSuccess());
      assertEquals("batch-1", result.data().batchId());

      ArgumentCaptor<CreateBatchCommand> captor = ArgumentCaptor.forClass(CreateBatchCommand.class);
      verify(batchApi).create(captor.capture());
      CreateBatchCommand cmd = captor.getValue();
      assertEquals("ANNUITY_OPEN", cmd.businessType());
      assertEquals("P001", cmd.planNo());
      assertEquals("备注", cmd.operatorRemark());
    }
  }

  @Nested
  @DisplayName("batchDetail")
  class BatchDetail {

    @Test
    @DisplayName("BFF Request 正确映射到 GetBatchDetailQuery（丢弃 businessType）并委托 batchApi")
    void batchDetail_dropsBusinessType() {
      BffBatchDetailRequest request = new BffBatchDetailRequest("ANNUITY_OPEN", "batch-1");
      BatchDetailResponse response = new BatchDetailResponse(
        "batch-1", "ANNUITY_OPEN", "P001", "C001", "客户", "PROCESSING",
        10, 5, 3, 2, LocalDateTime.now(), LocalDateTime.now(), List.of()
      );
      when(batchApi.detail(any(GetBatchDetailQuery.class))).thenReturn(ApiResult.success(response));

      ApiResult<BatchDetailResponse> result = handler.batchDetail(request);

      assertTrue(result.isSuccess());
      assertEquals("batch-1", result.data().batchId());

      ArgumentCaptor<GetBatchDetailQuery> captor = ArgumentCaptor.forClass(GetBatchDetailQuery.class);
      verify(batchApi).detail(captor.capture());
      assertEquals("batch-1", captor.getValue().batchId());
    }
  }

  @Nested
  @DisplayName("applyUploadToken")
  class ApplyUploadToken {

    @Test
    @DisplayName("BFF Request 正确映射到 ApplyUploadTokenCommand（丢弃 businessType）并委托 formApi")
    void applyUploadToken_dropsBusinessType() {
      BffFormTokenRequest request = new BffFormTokenRequest("ANNUITY_OPEN", "batch-1", "test.xlsx", 1024L, "application/vnd.ms-excel");
      UploadTokenResponse response = new UploadTokenResponse("token-1", LocalDateTime.now().plusMinutes(30), "http://upload.url");
      when(formApi.applyUploadToken(any(ApplyUploadTokenCommand.class))).thenReturn(ApiResult.success(response));

      ApiResult<UploadTokenResponse> result = handler.applyUploadToken(request);

      assertTrue(result.isSuccess());
      assertEquals("token-1", result.data().token());

      ArgumentCaptor<ApplyUploadTokenCommand> captor = ArgumentCaptor.forClass(ApplyUploadTokenCommand.class);
      verify(formApi).applyUploadToken(captor.capture());
      ApplyUploadTokenCommand cmd = captor.getValue();
      assertEquals("batch-1", cmd.batchId());
      assertEquals("test.xlsx", cmd.fileName());
      assertEquals(1024L, cmd.fileSize());
      assertEquals("application/vnd.ms-excel", cmd.contentType());
    }
  }

  @Nested
  @DisplayName("submitApplication")
  class SubmitApplication {

    @Test
    @DisplayName("BFF Request 正确映射到 SubmitApplicationCommand（丢弃 businessType）并委托 applicationApi")
    void submitApplication_dropsBusinessType() {
      BffSubmitRequest request = new BffSubmitRequest("ANNUITY_OPEN", "app-1");
      SubmitResponse response = new SubmitResponse("app-1", true, "approval-1");
      when(applicationApi.submit(any(SubmitApplicationCommand.class))).thenReturn(ApiResult.success(response));

      ApiResult<SubmitResponse> result = handler.submitApplication(request);

      assertTrue(result.isSuccess());
      assertEquals("app-1", result.data().applicationId());

      ArgumentCaptor<SubmitApplicationCommand> captor = ArgumentCaptor.forClass(SubmitApplicationCommand.class);
      verify(applicationApi).submit(captor.capture());
      assertEquals("app-1", captor.getValue().applicationId());
    }
  }

  @Nested
  @DisplayName("applicationDetail")
  class ApplicationDetail {

    @Test
    @DisplayName("BFF Request 正确映射到 GetApplicationDetailQuery（丢弃 businessType）并委托 applicationApi")
    void applicationDetail_dropsBusinessType() {
      BffApplicationDetailRequest request = new BffApplicationDetailRequest("ANNUITY_OPEN", "app-1");
      ApplicationDetailResponse response = new ApplicationDetailResponse(
        "app-1", "batch-1", "SUBMITTED", "REVIEW", null, null,
        LocalDateTime.now(), null, LocalDateTime.now(), LocalDateTime.now()
      );
      when(applicationApi.detail(any(GetApplicationDetailQuery.class))).thenReturn(ApiResult.success(response));

      ApiResult<ApplicationDetailResponse> result = handler.applicationDetail(request);

      assertTrue(result.isSuccess());
      assertEquals("app-1", result.data().applicationId());

      ArgumentCaptor<GetApplicationDetailQuery> captor = ArgumentCaptor.forClass(GetApplicationDetailQuery.class);
      verify(applicationApi).detail(captor.capture());
      assertEquals("app-1", captor.getValue().applicationId());
    }
  }

  @Nested
  @DisplayName("listMaterials")
  class ListMaterials {

    @Test
    @DisplayName("BFF Request 正确映射到 ListMaterialsQuery（丢弃 businessType）并委托 materialApi")
    void listMaterials_dropsBusinessType() {
      BffListMaterialsRequest request = new BffListMaterialsRequest("ANNUITY_OPEN", "app-1");
      List<MaterialItemResponse> materials = List.of(
        new MaterialItemResponse("M001", "身份证", "REQUIRED", "必须", null, null, List.of())
      );
      when(materialApi.list(any(ListMaterialsQuery.class))).thenReturn(ApiResult.success(materials));

      ApiResult<List<MaterialItemResponse>> result = handler.listMaterials(request);

      assertTrue(result.isSuccess());
      assertEquals(1, result.data().size());

      ArgumentCaptor<ListMaterialsQuery> captor = ArgumentCaptor.forClass(ListMaterialsQuery.class);
      verify(materialApi).list(captor.capture());
      assertEquals("app-1", captor.getValue().applicationId());
    }
  }

  @Nested
  @DisplayName("batchOverview 聚合")
  class BatchOverview {

    @Test
    @DisplayName("三个 API 均成功时，返回完整聚合响应")
    void batchOverview_allSuccess_returnsFullResponse() {
      BffBatchOverviewRequest request = new BffBatchOverviewRequest("ANNUITY_OPEN", "batch-1");

      BatchDetailResponse detail = new BatchDetailResponse(
        "batch-1", "ANNUITY_OPEN", "P001", "C001", "客户", "PROCESSING",
        10, 5, 3, 2, LocalDateTime.now(), LocalDateTime.now(), List.of()
      );
      BatchProgressResponse progress = new BatchProgressResponse("batch-1", "PROCESSING", 10, 5, 2, 3);
      List<ApplicationSummaryResponse> applications = List.of(
        new ApplicationSummaryResponse("app-1", "batch-1", "SUBMITTED", "REVIEW", LocalDateTime.now(), LocalDateTime.now())
      );

      when(batchApi.detail(any(GetBatchDetailQuery.class))).thenReturn(ApiResult.success(detail));
      when(progressApi.batchProgress(any(GetBatchProgressQuery.class))).thenReturn(ApiResult.success(progress));
      when(applicationApi.list(any(FindApplicationListQuery.class))).thenReturn(ApiResult.success(applications));

      ApiResult<BatchOverviewResponse> result = handler.batchOverview(request);

      assertTrue(result.isSuccess());
      assertNotNull(result.data().batchDetail());
      assertNotNull(result.data().progress());
      assertEquals(1, result.data().applications().size());
    }

    @Test
    @DisplayName("主实体（batch detail）失败时，抛出 CompletionException 包装的 BffAggregationException")
    void batchOverview_primaryFails_throwsAggregationException() {
      BffBatchOverviewRequest request = new BffBatchOverviewRequest("ANNUITY_OPEN", "batch-1");

      when(batchApi.detail(any(GetBatchDetailQuery.class)))
        .thenReturn(ApiResult.failure("SERVICE.ANNUITY.0001", "批次不存在"));

      CompletionException ex = assertThrows(CompletionException.class,
        () -> handler.batchOverview(request));

      assertInstanceOf(BffAggregationException.class, ex.getCause());
      assertEquals("SERVICE.ANNUITY.0001", ((BffAggregationException) ex.getCause()).code);
    }

    @Test
    @DisplayName("辅助 API（progress）失败时，降级为 null，主数据仍返回")
    void batchOverview_progressFails_degradesGracefully() {
      BffBatchOverviewRequest request = new BffBatchOverviewRequest("ANNUITY_OPEN", "batch-1");

      BatchDetailResponse detail = new BatchDetailResponse(
        "batch-1", "ANNUITY_OPEN", "P001", "C001", "客户", "PROCESSING",
        10, 5, 3, 2, LocalDateTime.now(), LocalDateTime.now(), List.of()
      );
      when(batchApi.detail(any(GetBatchDetailQuery.class))).thenReturn(ApiResult.success(detail));
      when(progressApi.batchProgress(any(GetBatchProgressQuery.class)))
        .thenThrow(new RuntimeException("progress 服务不可用"));
      when(applicationApi.list(any(FindApplicationListQuery.class)))
        .thenReturn(ApiResult.success(List.of()));

      ApiResult<BatchOverviewResponse> result = handler.batchOverview(request);

      assertTrue(result.isSuccess());
      assertNotNull(result.data().batchDetail());
      assertNull(result.data().progress());
      assertTrue(result.data().applications().isEmpty());
    }

    @Test
    @DisplayName("辅助 API（applications）失败时，降级为空列表，主数据仍返回")
    void batchOverview_applicationsFails_degradesGracefully() {
      BffBatchOverviewRequest request = new BffBatchOverviewRequest("ANNUITY_OPEN", "batch-1");

      BatchDetailResponse detail = new BatchDetailResponse(
        "batch-1", "ANNUITY_OPEN", "P001", "C001", "客户", "PROCESSING",
        10, 5, 3, 2, LocalDateTime.now(), LocalDateTime.now(), List.of()
      );
      BatchProgressResponse progress = new BatchProgressResponse("batch-1", "PROCESSING", 10, 5, 2, 3);
      when(batchApi.detail(any(GetBatchDetailQuery.class))).thenReturn(ApiResult.success(detail));
      when(progressApi.batchProgress(any(GetBatchProgressQuery.class))).thenReturn(ApiResult.success(progress));
      when(applicationApi.list(any(FindApplicationListQuery.class)))
        .thenThrow(new RuntimeException("application 服务不可用"));

      ApiResult<BatchOverviewResponse> result = handler.batchOverview(request);

      assertTrue(result.isSuccess());
      assertNotNull(result.data().batchDetail());
      assertNotNull(result.data().progress());
      assertTrue(result.data().applications().isEmpty());
    }
  }
}
