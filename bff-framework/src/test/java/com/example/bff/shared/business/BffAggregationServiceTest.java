package com.example.bff.shared.business;

import com.example.bff.shared.business.dto.BatchOverviewResponse;
import com.example.bff.shared.business.dto.BffBatchDetailRequest;
import com.example.bff.shared.business.dto.BffBatchOverviewRequest;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.shared.web.core.api.ApiResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BffAggregationServiceTest {

  @Mock
  private ScenarioHandlerRegistry registry;

  @Mock
  private BusinessScenarioHandler handler;

  private BffAggregationService aggregationService;

  @BeforeEach
  void setUp() {
    aggregationService = new BffAggregationService(registry);
  }

  @Test
  @DisplayName("getBatchOverview 委托给对应 handler 的 batchOverview 方法")
  void getBatchOverview_delegatesToHandler() {
    BffBatchOverviewRequest request = new BffBatchOverviewRequest("ANNUITY_OPEN", "batch-123");
    BatchDetailResponse batchDetail = new BatchDetailResponse(
      "batch-123", "ANNUITY_OPEN", "PLAN001", "C001", "客户A",
      "PROCESSING", 10, 5, 3, 2,
      LocalDateTime.now(), LocalDateTime.now(), List.of()
    );
    BatchOverviewResponse overview = BffResponseAssembler.assemble(batchDetail, null, null);

    when(registry.handlerFor("ANNUITY_OPEN")).thenReturn(handler);
    when(handler.batchOverview(request)).thenReturn(ApiResult.success(overview));

    ApiResult<BatchOverviewResponse> result = aggregationService.getBatchOverview(request);

    assertTrue(result.isSuccess());
    assertNotNull(result.data().batchDetail());
    assertNull(result.data().progress());
    assertTrue(result.data().applications().isEmpty());
  }

  @Test
  @DisplayName("getBatchOverview handler 未注册时抛出 BusinessException")
  void getBatchOverview_unsupportedType_throws() {
    BffBatchOverviewRequest request = new BffBatchOverviewRequest("UNKNOWN_TYPE", "batch-999");

    when(registry.handlerFor("UNKNOWN_TYPE"))
      .thenThrow(new com.example.shared.exception.BusinessException(
        com.example.bff.shared.errorcode.BffErrorCode.SCENARIO_NOT_SUPPORTED));

    assertThrows(com.example.shared.exception.BusinessException.class,
      () -> aggregationService.getBatchOverview(request));
  }
}
