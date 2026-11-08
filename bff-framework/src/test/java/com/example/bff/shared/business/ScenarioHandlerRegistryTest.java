package com.example.bff.shared.business;

import com.example.bff.shared.business.dto.BffApplicationDetailRequest;
import com.example.bff.shared.business.dto.BffBatchDetailRequest;
import com.example.bff.shared.business.dto.BffBatchOverviewRequest;
import com.example.bff.shared.business.dto.BffCreateBatchRequest;
import com.example.bff.shared.business.dto.BffFormTokenRequest;
import com.example.bff.shared.business.dto.BffListMaterialsRequest;
import com.example.bff.shared.business.dto.BffSubmitRequest;
import com.example.bff.shared.business.dto.BatchOverviewResponse;
import com.example.bff.shared.errorcode.BffErrorCode;
import com.example.core.api.application.response.ApplicationDetailResponse;
import com.example.core.api.application.response.SubmitResponse;
import com.example.core.api.batch.response.BatchCreatedResponse;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.core.api.form.response.UploadTokenResponse;
import com.example.core.api.material.response.MaterialItemResponse;
import com.example.shared.exception.BusinessException;
import com.example.shared.web.core.api.ApiResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioHandlerRegistryTest {

  private static BusinessScenarioHandler stubHandler(String... types) {
    Set<String> typeSet = Set.of(types);
    return new BusinessScenarioHandler() {
      @Override public Set<String> supportedTypes() { return typeSet; }
      @Override public ApiResult<BatchCreatedResponse> createBatch(BffCreateBatchRequest r) { return ApiResult.success(null); }
      @Override public ApiResult<BatchDetailResponse> batchDetail(BffBatchDetailRequest r) { return ApiResult.success(null); }
      @Override public ApiResult<UploadTokenResponse> applyUploadToken(BffFormTokenRequest r) { return ApiResult.success(null); }
      @Override public ApiResult<SubmitResponse> submitApplication(BffSubmitRequest r) { return ApiResult.success(null); }
      @Override public ApiResult<ApplicationDetailResponse> applicationDetail(BffApplicationDetailRequest r) { return ApiResult.success(null); }
      @Override public ApiResult<List<MaterialItemResponse>> listMaterials(BffListMaterialsRequest r) { return ApiResult.success(null); }
    };
  }

  @Test
  @DisplayName("注册空列表时 handlerFor 抛出 BusinessException")
  void handlerFor_emptyRegistry_throws() {
    ScenarioHandlerRegistry registry = new ScenarioHandlerRegistry(List.of());

    BusinessException ex = assertThrows(BusinessException.class,
      () -> registry.handlerFor("ANNUITY_OPEN"));

    assertEquals(BffErrorCode.SCENARIO_NOT_SUPPORTED.getCode(), ex.code());
  }

  @Test
  @DisplayName("注册单个 handler 后可按类型查找")
  void handlerFor_singleHandler_found() {
    BusinessScenarioHandler handler = stubHandler("ANNUITY_OPEN");
    ScenarioHandlerRegistry registry = new ScenarioHandlerRegistry(List.of(handler));

    assertSame(handler, registry.handlerFor("ANNUITY_OPEN"));
  }

  @Test
  @DisplayName("查找未注册的类型时抛出 BusinessException")
  void handlerFor_unregisteredType_throws() {
    BusinessScenarioHandler handler = stubHandler("ANNUITY_OPEN");
    ScenarioHandlerRegistry registry = new ScenarioHandlerRegistry(List.of(handler));

    assertThrows(BusinessException.class,
      () -> registry.handlerFor("LOAN_APPLY"));
  }

  @Test
  @DisplayName("注册多个 handler 后按类型正确路由")
  void handlerFor_multipleHandlers_routesCorrectly() {
    BusinessScenarioHandler annuityHandler = stubHandler("ANNUITY_OPEN");
    BusinessScenarioHandler loanHandler = stubHandler("LOAN_APPLY");
    ScenarioHandlerRegistry registry = new ScenarioHandlerRegistry(
      List.of(annuityHandler, loanHandler));

    assertSame(annuityHandler, registry.handlerFor("ANNUITY_OPEN"));
    assertSame(loanHandler, registry.handlerFor("LOAN_APPLY"));
  }

  @Test
  @DisplayName("一个 handler 支持多个 businessType 时均可路由到同一实例")
  void handlerFor_multiTypeHandler_routesAll() {
    BusinessScenarioHandler handler = stubHandler("ANNUITY_OPEN", "ANNUITY_CHANGE");
    ScenarioHandlerRegistry registry = new ScenarioHandlerRegistry(List.of(handler));

    assertSame(handler, registry.handlerFor("ANNUITY_OPEN"));
    assertSame(handler, registry.handlerFor("ANNUITY_CHANGE"));
  }

  @Test
  @DisplayName("batchOverview 默认实现回退到 batchDetail 并组装响应")
  void batchOverview_defaultImpl_fallsBackToBatchDetail() {
    BatchDetailResponse detail = new BatchDetailResponse(
      "batch-1", "ANNUITY_OPEN", "P001", "C001", "客户",
      "PROCESSING", 10, 5, 3, 2,
      LocalDateTime.now(), LocalDateTime.now(), List.of()
    );
    BusinessScenarioHandler handler = new BusinessScenarioHandler() {
      @Override public Set<String> supportedTypes() { return Set.of("ANNUITY_OPEN"); }
      @Override public ApiResult<BatchCreatedResponse> createBatch(BffCreateBatchRequest r) { return null; }
      @Override public ApiResult<BatchDetailResponse> batchDetail(BffBatchDetailRequest r) { return ApiResult.success(detail); }
      @Override public ApiResult<UploadTokenResponse> applyUploadToken(BffFormTokenRequest r) { return null; }
      @Override public ApiResult<SubmitResponse> submitApplication(BffSubmitRequest r) { return null; }
      @Override public ApiResult<ApplicationDetailResponse> applicationDetail(BffApplicationDetailRequest r) { return null; }
      @Override public ApiResult<List<MaterialItemResponse>> listMaterials(BffListMaterialsRequest r) { return null; }
    };

    ApiResult<BatchOverviewResponse> result =
      handler.batchOverview(new BffBatchOverviewRequest("ANNUITY_OPEN", "batch-1"));

    assertTrue(result.isSuccess());
    assertNotNull(result.data().batchDetail());
    assertNull(result.data().progress());
    assertTrue(result.data().applications().isEmpty());
  }
}
