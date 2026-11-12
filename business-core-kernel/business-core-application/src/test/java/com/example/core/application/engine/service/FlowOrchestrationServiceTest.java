package com.example.core.application.engine.service;

import com.example.core.application.engine.pipeline.StepPipelineExecutor;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.repository.ApplicationRepository;
import com.example.core.domain.engine.aggregate.root.ExternalInteraction;
import com.example.core.domain.engine.aggregate.root.StepExecution;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.aggregate.valueobject.PipelineExecutionResult;
import com.example.core.domain.engine.aggregate.valueobject.StepCode;
import com.example.core.domain.engine.aggregate.valueobject.StepDefinition;
import com.example.core.domain.engine.aggregate.valueobject.config.StepRouteConfig;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InteractionType;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.TaskType;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.ExternalInteractionStatus;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.StepExecutionState;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.StepExecutionStatus;
import com.example.core.domain.engine.aggregate.valueobject.enums.workflow.ApplicationFlowStep;
import com.example.core.domain.engine.gateway.BusinessConfigGateway;
import com.example.core.domain.engine.repository.ExternalInteractionRepository;
import com.example.core.domain.engine.repository.StepExecutionRepository;
import com.example.core.domain.engine.service.registry.BusinessFactExtractorRegistry;
import com.example.core.domain.engine.service.registry.StepActionHandlerRegistry;
import com.example.core.domain.engine.spi.StepActionHandler;
import com.example.core.types.ApplicationId;
import com.example.core.types.Channel;
import com.example.core.types.CustomerNo;
import com.example.core.types.ExternalInteractionId;
import com.example.core.types.OperatingMode;
import com.example.core.types.PlanNo;
import com.example.core.types.ProductNo;
import com.example.core.types.StepExecutionId;
import com.example.shared.identifier.contract.IdService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("FlowOrchestrationService 版本化路由与步骤执行接线")
class FlowOrchestrationServiceTest {

  private static final ApplicationId APP_ID = new ApplicationId("APP-001");

  private StepActionHandlerRegistry handlerRegistry;
  private BusinessConfigGateway configGateway;
  private ApplicationRepository applicationRepository;
  private StepPipelineExecutor pipelineExecutor;
  private StepDefinitionResolver stepDefinitionResolver;
  private StepRouteConfigAssembler stepRouteConfigAssembler;
  private StepExecutionRepository stepExecutionRepository;
  private ExternalInteractionRepository externalInteractionRepository;
  private IdService idService;
  private FlowOrchestrationService service;

  @BeforeEach
  void setUp() {
    handlerRegistry = mock(StepActionHandlerRegistry.class);
    configGateway = mock(BusinessConfigGateway.class);
    applicationRepository = mock(ApplicationRepository.class);
    pipelineExecutor = mock(StepPipelineExecutor.class);
    stepDefinitionResolver = mock(StepDefinitionResolver.class);
    stepRouteConfigAssembler = mock(StepRouteConfigAssembler.class);
    stepExecutionRepository = mock(StepExecutionRepository.class);
    externalInteractionRepository = mock(ExternalInteractionRepository.class);
    idService = mock(IdService.class);
    when(idService.nextLongId(eq(StepExecutionId.class), anyString()))
      .thenReturn(StepExecutionId.of(1L));
    when(idService.nextLongId(eq(ExternalInteractionId.class), anyString()))
      .thenReturn(ExternalInteractionId.of(2L));
    when(stepExecutionRepository.findByApplicationAndStep(any(), any())).thenReturn(List.of());

    PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

    service = new FlowOrchestrationService(
      handlerRegistry, mock(BusinessFactExtractorRegistry.class), configGateway,
      applicationRepository, pipelineExecutor, transactionTemplate,
      stepDefinitionResolver, stepRouteConfigAssembler,
      stepExecutionRepository, idService, externalInteractionRepository);
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
    verify(app, never()).clearDomainEvents();
  }

  @Test
  @DisplayName("advanceStep 创建步骤执行并与申请单在同一事务保存")
  void advanceStep_createsAndSavesStepExecution() {
    stubStep(StepExecutionStatus.SUCCESS);

    service.advanceStep(APP_ID);

    ArgumentCaptor<StepExecution> captor = ArgumentCaptor.forClass(StepExecution.class);
    verify(stepExecutionRepository).save(captor.capture());
    StepExecution saved = captor.getValue();
    assertThat(saved.applicationId()).isEqualTo(APP_ID);
    assertThat(saved.stepCode()).isEqualTo(StepCode.of("FORM_DETAIL_INGESTION"));
    assertThat(saved.attempt()).isEqualTo(1);
    assertThat(saved.requestNo()).isEqualTo("APP-001:FORM_DETAIL_INGESTION:1");
    assertThat(saved.state()).isEqualTo(StepExecutionState.SUCCEEDED);
    verify(applicationRepository).save(any(BusinessApplication.class));
  }

  @Test
  @DisplayName("Handler 挂起时步骤执行进入 WAITING_EXTERNAL")
  void advanceStep_marksWaitingExternalOnSuspend() {
    stubStep(StepExecutionStatus.SUSPEND_ASYNC_WAIT);

    service.advanceStep(APP_ID);

    ArgumentCaptor<StepExecution> captor = ArgumentCaptor.forClass(StepExecution.class);
    verify(stepExecutionRepository).save(captor.capture());
    assertThat(captor.getValue().state()).isEqualTo(StepExecutionState.WAITING_EXTERNAL);
  }

  @Test
  @DisplayName("Handler 挂起时创建外部协作记录并与步骤执行同事务保存")
  void advanceStep_whenSuspend_createsExternalInteraction() {
    stubStep(StepExecutionStatus.SUSPEND_ASYNC_WAIT, "approvalSubmissionHandler");

    service.advanceStep(APP_ID);

    ArgumentCaptor<ExternalInteraction> captor = ArgumentCaptor.forClass(ExternalInteraction.class);
    verify(externalInteractionRepository).save(captor.capture());
    ExternalInteraction saved = captor.getValue();
    assertThat(saved.applicationId()).isEqualTo(APP_ID);
    assertThat(saved.stepExecutionId()).isEqualTo(StepExecutionId.of(1L));
    assertThat(saved.type()).isEqualTo(InteractionType.APPROVAL);
    assertThat(saved.status()).isEqualTo(ExternalInteractionStatus.PENDING_DISPATCH);
    assertThat(saved.requestNo()).isEqualTo("SE-1");
    verify(stepExecutionRepository).save(any(StepExecution.class));
    verify(applicationRepository).save(any(BusinessApplication.class));
  }

  @Test
  @DisplayName("Handler 成功时不创建外部协作记录")
  void advanceStep_whenSuccess_doesNotCreateExternalInteraction() {
    stubStep(StepExecutionStatus.SUCCESS);

    service.advanceStep(APP_ID);

    verifyNoInteractions(externalInteractionRepository);
  }

  @Test
  @DisplayName("Handler 失败时不创建外部协作记录")
  void advanceStep_whenFailed_doesNotCreateExternalInteraction() {
    stubStep(StepExecutionStatus.FAILED);

    service.advanceStep(APP_ID);

    verifyNoInteractions(externalInteractionRepository);
  }

  @Test
  @DisplayName("Handler 失败时步骤执行进入 FAILED")
  void advanceStep_marksFailedOnFailure() {
    stubStep(StepExecutionStatus.FAILED);

    service.advanceStep(APP_ID);

    ArgumentCaptor<StepExecution> captor = ArgumentCaptor.forClass(StepExecution.class);
    verify(stepExecutionRepository).save(captor.capture());
    assertThat(captor.getValue().state()).isEqualTo(StepExecutionState.FAILED);
  }

  @Test
  @DisplayName("isSystemTask 加载申请单并委托定义解析")
  void isSystemTaskLoadsApplicationAndDelegates() {
    BusinessApplication app = mock(BusinessApplication.class);
    when(applicationRepository.loadOrThrow(APP_ID)).thenReturn(app);
    when(stepDefinitionResolver.isSystemTask(app)).thenReturn(true);

    assertThat(service.isSystemTask(APP_ID)).isTrue();
  }

  private void stubStep(StepExecutionStatus status) {
    stubStep(status, "mainHandler");
  }

  private void stubStep(StepExecutionStatus status, String mainProcessor) {
    BusinessApplication app = mock(BusinessApplication.class);
    when(applicationRepository.loadOrThrow(APP_ID)).thenReturn(app);
    when(app.buildConfigQueryContext()).thenReturn(BusinessMetaContext.of(context()));
    when(configGateway.getExtractorConfig(any())).thenReturn(null);

    StepDefinition step = StepDefinition.business(
      StepCode.of("FORM_DETAIL_INGESTION"), TaskType.SYSTEM_TASK, mainProcessor, List.of());
    when(stepDefinitionResolver.resolve(app)).thenReturn(step);
    StepRouteConfig route = new StepRouteConfig(
      ApplicationFlowStep.FORM_DETAIL_INGESTION, ApplicationFlowStep.DATA_VERIFICATION,
      StepRouteConfig.StepTaskType.SYSTEM_TASK, List.of(), mainProcessor, List.of(), List.of());
    when(stepRouteConfigAssembler.toRouteConfig(any(StepDefinition.class))).thenReturn(route);

    StepActionHandler handler = mock(StepActionHandler.class);
    when(handlerRegistry.get(mainProcessor)).thenReturn(handler);
    when(handler.execute(any(), any())).thenReturn(status);
    when(pipelineExecutor.executeChain(any(), any(), any()))
      .thenReturn(mock(PipelineExecutionResult.class));
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
