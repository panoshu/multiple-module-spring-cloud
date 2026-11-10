package com.example.core.application.engine.service;

import com.example.core.application.engine.pipeline.StepPipelineExecutor;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.repository.ApplicationRepository;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.aggregate.valueobject.PipelineExecutionResult;
import com.example.core.domain.engine.aggregate.valueobject.StepCode;
import com.example.core.domain.engine.aggregate.valueobject.StepDefinition;
import com.example.core.domain.engine.aggregate.valueobject.config.StepRouteConfig;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.TaskType;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.StepExecutionStatus;
import com.example.core.domain.engine.aggregate.valueobject.enums.workflow.ApplicationFlowStep;
import com.example.core.domain.engine.gateway.BusinessConfigGateway;
import com.example.core.domain.engine.service.registry.BusinessFactExtractorRegistry;
import com.example.core.domain.engine.service.registry.StepActionHandlerRegistry;
import com.example.core.domain.engine.spi.StepActionHandler;
import com.example.core.types.ApplicationId;
import com.example.core.types.Channel;
import com.example.core.types.CustomerNo;
import com.example.core.types.OperatingMode;
import com.example.core.types.PlanNo;
import com.example.core.types.ProductNo;
import com.example.shared.domain.event.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("FlowOrchestrationService 版本化路由")
class FlowOrchestrationServiceTest {

  private static final ApplicationId APP_ID = new ApplicationId("APP-001");

  private StepActionHandlerRegistry handlerRegistry;
  private BusinessConfigGateway configGateway;
  private ApplicationRepository applicationRepository;
  private StepPipelineExecutor pipelineExecutor;
  private StepDefinitionResolver stepDefinitionResolver;
  private StepRouteConfigAssembler stepRouteConfigAssembler;
  private FlowOrchestrationService service;

  @BeforeEach
  void setUp() {
    handlerRegistry = mock(StepActionHandlerRegistry.class);
    configGateway = mock(BusinessConfigGateway.class);
    applicationRepository = mock(ApplicationRepository.class);
    pipelineExecutor = mock(StepPipelineExecutor.class);
    stepDefinitionResolver = mock(StepDefinitionResolver.class);
    stepRouteConfigAssembler = mock(StepRouteConfigAssembler.class);

    PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

    service = new FlowOrchestrationService(
      handlerRegistry, mock(BusinessFactExtractorRegistry.class), configGateway,
      applicationRepository, pipelineExecutor, mock(EventBus.class), transactionTemplate,
      stepDefinitionResolver, stepRouteConfigAssembler);
  }

  @Test
  @DisplayName("advanceStep 从版本化定义解析路由，不再查询 BusinessConfigGateway.getNextStep")
  void advanceStep_resolvesRouteFromDefinition() {
    BusinessApplication app = mock(BusinessApplication.class);
    when(applicationRepository.loadOrThrow(APP_ID)).thenReturn(app);
    when(app.buildConfigQueryContext()).thenReturn(BusinessMetaContext.of(context()));
    when(configGateway.getExtractorConfig(any())).thenReturn(null);
    when(app.domainEvents()).thenReturn(List.of());

    StepDefinition step = StepDefinition.business(
      StepCode.of("FORM_DETAIL_INGESTION"), TaskType.SYSTEM_TASK, "mainHandler", List.of());
    when(stepDefinitionResolver.resolve(app)).thenReturn(step);
    StepRouteConfig route = new StepRouteConfig(
      ApplicationFlowStep.FORM_DETAIL_INGESTION, ApplicationFlowStep.DATA_VERIFICATION,
      StepRouteConfig.StepTaskType.SYSTEM_TASK, List.of(), "mainHandler", List.of(), List.of());
    when(stepRouteConfigAssembler.toRouteConfig(step)).thenReturn(route);

    StepActionHandler handler = mock(StepActionHandler.class);
    when(handlerRegistry.get("mainHandler")).thenReturn(handler);
    when(handler.execute(any(), any())).thenReturn(StepExecutionStatus.SUCCESS);
    when(pipelineExecutor.executeChain(any(), any(), any()))
      .thenReturn(mock(PipelineExecutionResult.class));

    service.advanceStep(APP_ID);

    verify(configGateway, never()).getNextStep(any(), any());
    verify(stepDefinitionResolver).resolve(app);
    verify(stepRouteConfigAssembler).toRouteConfig(step);
    verify(app).completeCurrentStep(ApplicationFlowStep.DATA_VERIFICATION);
    verify(applicationRepository).save(app);
  }

  @Test
  @DisplayName("isSystemTask 加载申请单并委托定义解析")
  void isSystemTaskLoadsApplicationAndDelegates() {
    BusinessApplication app = mock(BusinessApplication.class);
    when(applicationRepository.loadOrThrow(APP_ID)).thenReturn(app);
    when(stepDefinitionResolver.isSystemTask(app)).thenReturn(true);

    assertThat(service.isSystemTask(APP_ID)).isTrue();
  }

  private BusinessContext context() {
    return new BusinessContext(
      BusinessType.ACC_PLAN_CREATE,
      CustomerNo.of("C-001"), "客户",
      ProductNo.of("P-001"), "产品",
      PlanNo.of("PL-001"), "方案",
      OperatingMode.TRUSTEE_ONLY, AccountManager.CJP
    );
  }
}
