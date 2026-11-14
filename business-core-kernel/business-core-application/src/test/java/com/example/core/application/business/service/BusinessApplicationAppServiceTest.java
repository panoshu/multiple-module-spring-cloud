package com.example.core.application.business.service;

import com.example.core.api.application.command.AdvanceStepCommand;
import com.example.core.application.engine.service.FlowOrchestrationService;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.repository.ApplicationRepository;
import com.example.core.types.ApplicationId;
import com.example.core.types.BatchId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * BusinessApplicationAppService 单元测试
 *
 * @author panoshu
 */
class BusinessApplicationAppServiceTest {

  private FlowOrchestrationService flowOrchestrationService;
  private ApplicationRepository applicationRepository;
  private BusinessApplicationAppService appService;

  @BeforeEach
  void setUp() {
    flowOrchestrationService = mock(FlowOrchestrationService.class);
    applicationRepository = mock(ApplicationRepository.class);
    appService = new BusinessApplicationAppService(flowOrchestrationService, applicationRepository);
  }

  @Test
  void should_find_applications_by_batch_id() {
    BatchId batchId = new BatchId("BATCH001");
    BusinessApplication app = mock(BusinessApplication.class);
    when(applicationRepository.findByBatchId(batchId)).thenReturn(List.of(app));

    List<BusinessApplication> result = appService.findByBatchId(batchId);

    assertThat(result).hasSize(1);
    verify(applicationRepository).findByBatchId(batchId);
  }

  @Test
  void should_load_or_throw_application() {
    ApplicationId appId = new ApplicationId("APP001");
    BusinessApplication app = mock(BusinessApplication.class);
    when(applicationRepository.loadOrThrow(appId)).thenReturn(app);

    BusinessApplication result = appService.loadOrThrow(appId);

    assertThat(result).isSameAs(app);
    verify(applicationRepository).loadOrThrow(appId);
  }

  @Test
  void should_advance_step_via_orchestration() {
    ApplicationId appId = new ApplicationId("APP001");

    appService.advanceStep(appId);

    verify(flowOrchestrationService).advanceStep(appId);
  }

  @Test
  void should_advance_application_via_cqe() {
    AdvanceStepCommand command = new AdvanceStepCommand(
      "APP001", "FORM_DETAIL_INGESTION", "REQ-1", "U-1", Map.of());

    appService.advanceApplication(command);

    verify(flowOrchestrationService).advanceApplication(command);
  }

  @Test
  void should_submit_via_orchestration() {
    ApplicationId appId = new ApplicationId("APP001");

    appService.submit(appId);

    verify(flowOrchestrationService).advanceStep(appId);
  }
}
