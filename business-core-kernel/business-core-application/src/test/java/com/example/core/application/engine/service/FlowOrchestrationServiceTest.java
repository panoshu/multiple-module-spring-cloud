package com.example.core.application.engine.service;

import com.example.core.api.application.command.AdvanceStepCommand;
import com.example.core.application.engine.pipeline.StepPipelineExecutor;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.enums.status.ApplicationStatus;
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
import com.example.shared.exception.DomainException;
import com.example.shared.exception.SystemException;
import com.example.shared.identifier.contract.IdService;
import com.example.shared.lock.DistributedLock;
import com.example.shared.lock.errorcode.LockErrorDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
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
  private ObjectProvider<DistributedLock> lockProvider;
  private IdService idService;
  private FlowOrchestrationService service;

  @SuppressWarnings("unchecked")
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
    lockProvider = mock(ObjectProvider.class);
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
      stepExecutionRepository, idService, externalInteractionRepository, lockProvider);
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
  @DisplayName("挂起 Handler 登记的派发载荷写入步骤执行检查点")
  void advanceStep_whenSuspend_recordsDispatchPayloadToCheckpoint() {
    stubStep(StepExecutionStatus.SUSPEND_ASYNC_WAIT, "archiveSubmissionHandler");
    StepActionHandler handler = handlerRegistry.get("archiveSubmissionHandler");
    doAnswer(invocation -> {
      BusinessMetaContext ctx = invocation.getArgument(1);
      ctx.registerDispatchPayload("FT-7\u001FTPL-7");
      return StepExecutionStatus.SUSPEND_ASYNC_WAIT;
    }).when(handler).execute(any(), any());

    service.advanceStep(APP_ID);

    ArgumentCaptor<StepExecution> captor = ArgumentCaptor.forClass(StepExecution.class);
    verify(stepExecutionRepository).save(captor.capture());
    assertThat(captor.getValue().checkpointRef()).isEqualTo("FT-7\u001FTPL-7");
  }

  @Test
  @DisplayName("挂起但无派发载荷时不写检查点")
  void advanceStep_whenSuspendWithoutPayload_leavesCheckpointNull() {
    stubStep(StepExecutionStatus.SUSPEND_ASYNC_WAIT, "approvalSubmissionHandler");

    service.advanceStep(APP_ID);

    ArgumentCaptor<StepExecution> captor = ArgumentCaptor.forClass(StepExecution.class);
    verify(stepExecutionRepository).save(captor.capture());
    assertThat(captor.getValue().checkpointRef()).isNull();
  }

  @Test
  @DisplayName("挂起时把申请单置 WAITING_EXTERNAL 并同事务保存")
  void advanceStep_whenSuspend_setsApplicationWaitingExternal() {
    BusinessApplication app = stubStep(StepExecutionStatus.SUSPEND_ASYNC_WAIT, "approvalSubmissionHandler");

    service.advanceStep(APP_ID);

    verify(app).waitForExternal();
    verify(applicationRepository).save(app);
  }

  @Test
  @DisplayName("成功推进时不置 WAITING_EXTERNAL")
  void advanceStep_whenSuccess_doesNotSetWaitingExternal() {
    BusinessApplication app = stubStep(StepExecutionStatus.SUCCESS);

    service.advanceStep(APP_ID);

    verify(app, never()).waitForExternal();
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
  @DisplayName("挂起步骤把 SE-<步骤执行ID> 注入上下文供外部协作幂等")
  void advanceStep_injectsRequestNoIntoContext() {
    stubStep(StepExecutionStatus.SUSPEND_ASYNC_WAIT, "approvalSubmissionHandler");

    service.advanceStep(APP_ID);

    ArgumentCaptor<BusinessMetaContext> captor = ArgumentCaptor.forClass(BusinessMetaContext.class);
    verify(pipelineExecutor).executeChain(any(), captor.capture(), any());
    assertThat(captor.getValue().requestNo()).isEqualTo("SE-1");
  }

  @Test
  @DisplayName("isSystemTask 加载申请单并委托定义解析")
  void isSystemTaskLoadsApplicationAndDelegates() {
    BusinessApplication app = mock(BusinessApplication.class);
    when(applicationRepository.loadOrThrow(APP_ID)).thenReturn(app);
    when(stepDefinitionResolver.isSystemTask(app)).thenReturn(true);

    assertThat(service.isSystemTask(APP_ID)).isTrue();
  }

  @Test
  @DisplayName("存在 DistributedLock 时 advanceStep 在锁内执行且使用申请单锁键")
  void advanceStep_usesDistributedLockWhenAvailable() {
    DistributedLock lock = mock(DistributedLock.class);
    when(lockProvider.getIfAvailable()).thenReturn(lock);
    stubLockAndRun(lock);
    stubStep(StepExecutionStatus.SUCCESS);

    service.advanceStep(APP_ID);

    ArgumentCaptor<Long> waitCaptor = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<Long> leaseCaptor = ArgumentCaptor.forClass(Long.class);
    verify(lock).lockAndRun(eq("kernel:application:APP-001"), waitCaptor.capture(),
      leaseCaptor.capture(), eq(TimeUnit.SECONDS), any());
    assertThat(waitCaptor.getValue()).isEqualTo(3L);
    assertThat(leaseCaptor.getValue()).isEqualTo(10L);
    verify(applicationRepository).save(any(BusinessApplication.class));
  }

  @Test
  @DisplayName("无 DistributedLock bean 时 advanceStep 无锁直通")
  void advanceStep_withoutLockBean_runsDirectly() {
    stubStep(StepExecutionStatus.SUCCESS);

    service.advanceStep(APP_ID);

    verify(lockProvider).getIfAvailable();
    verify(applicationRepository).save(any(BusinessApplication.class));
  }

  @Test
  @DisplayName("审批驳回终止流程也纳入申请单锁")
  void advanceByApprovalResult_rejectedUsesApplicationLock() {
    DistributedLock lock = mock(DistributedLock.class);
    when(lockProvider.getIfAvailable()).thenReturn(lock);
    stubLockAndRun(lock);
    BusinessApplication app = mock(BusinessApplication.class);
    when(applicationRepository.loadOrThrow(any())).thenReturn(app);

    service.advanceByApprovalResult("APP-001", "REJECTED");

    verify(lock).lockAndRun(eq("kernel:application:APP-001"), anyLong(), anyLong(),
      eq(TimeUnit.SECONDS), any());
    verify(app).reject();
    verify(applicationRepository).save(app);
  }

  @Test
  @DisplayName("获取锁失败时异常直接传播，不吞掉")
  void lockAcquisitionFailure_propagates() {
    DistributedLock lock = mock(DistributedLock.class);
    when(lockProvider.getIfAvailable()).thenReturn(lock);
    doThrow(new SystemException(LockErrorDefinition.GET_LOCK_FAILED)).when(lock)
      .lockAndRun(anyString(), anyLong(), anyLong(), any(TimeUnit.class), any());

    assertThatThrownBy(() -> service.advanceStep(APP_ID))
      .isInstanceOf(SystemException.class);
    verifyNoInteractions(applicationRepository);
  }

  @Test
  @DisplayName("completeWaitingStep：WAITING_EXTERNAL 先归位 RUNNING 再按定义推进 nextStep")
  void completeWaitingStep_resumesAndAdvancesNextStep() {
    BusinessApplication app = stubWaitingApp(ApplicationStatus.WAITING_EXTERNAL,
      ApplicationFlowStep.DATA_VERIFICATION);

    service.completeWaitingStep(APP_ID);

    verify(app).retry();
    verify(app).completeCurrentStep(ApplicationFlowStep.DATA_VERIFICATION);
    verify(applicationRepository).save(app);
  }

  @Test
  @DisplayName("completeWaitingStep：不重跑 Handler 管线、不新建步骤执行或协作")
  void completeWaitingStep_doesNotRerunHandlerPipeline() {
    stubWaitingApp(ApplicationStatus.WAITING_EXTERNAL, ApplicationFlowStep.DATA_VERIFICATION);

    service.completeWaitingStep(APP_ID);

    verifyNoInteractions(handlerRegistry, pipelineExecutor, configGateway,
      stepExecutionRepository, externalInteractionRepository, idService);
  }

  @Test
  @DisplayName("completeWaitingStep：nextStep 来自版本化定义解析")
  void completeWaitingStep_resolvesNextStepFromDefinition() {
    BusinessApplication app = stubWaitingApp(ApplicationStatus.RUNNING,
      ApplicationFlowStep.DATA_VERIFICATION);

    service.completeWaitingStep(APP_ID);

    verify(stepDefinitionResolver).resolve(app);
    verify(app, never()).retry(); // RUNNING 无需归位
    verify(app).completeCurrentStep(ApplicationFlowStep.DATA_VERIFICATION);
  }

  @Test
  @DisplayName("completeWaitingStep：终态申请单不推进、不保存")
  void completeWaitingStep_terminalState_doesNotAdvance() {
    BusinessApplication app = stubWaitingApp(ApplicationStatus.COMPLETED,
      ApplicationFlowStep.DATA_VERIFICATION);

    service.completeWaitingStep(APP_ID);

    verify(app, never()).completeCurrentStep(any());
    verify(applicationRepository, never()).save(any());
  }

  @Test
  @DisplayName("completeWaitingStep：后继为空不推进")
  void completeWaitingStep_nullNextStep_doesNotAdvance() {
    BusinessApplication app = stubWaitingApp(ApplicationStatus.WAITING_EXTERNAL, null);

    service.completeWaitingStep(APP_ID);

    verify(app, never()).completeCurrentStep(any());
    verify(applicationRepository, never()).save(any());
  }

  @Test
  @DisplayName("completeWaitingStep：纳入申请单短锁")
  void completeWaitingStep_usesApplicationLock() {
    DistributedLock lock = mock(DistributedLock.class);
    when(lockProvider.getIfAvailable()).thenReturn(lock);
    stubLockAndRun(lock);
    stubWaitingApp(ApplicationStatus.WAITING_EXTERNAL, ApplicationFlowStep.DATA_VERIFICATION);

    service.completeWaitingStep(APP_ID);

    verify(lock).lockAndRun(eq("kernel:application:APP-001"), anyLong(), anyLong(),
      eq(TimeUnit.SECONDS), any());
  }

  private BusinessApplication stubWaitingApp(ApplicationStatus status, ApplicationFlowStep nextStep) {
    BusinessApplication app = mock(BusinessApplication.class);
    when(applicationRepository.loadOrThrow(APP_ID)).thenReturn(app);
    when(app.status()).thenReturn(status);
    StepDefinition step = StepDefinition.business(
      StepCode.of("FORM_DETAIL_INGESTION"), TaskType.SYSTEM_TASK, "mainHandler", List.of());
    when(stepDefinitionResolver.resolve(app)).thenReturn(step);
    StepRouteConfig route = new StepRouteConfig(
      ApplicationFlowStep.FORM_DETAIL_INGESTION, nextStep,
      StepRouteConfig.StepTaskType.SYSTEM_TASK, List.of(), "mainHandler", List.of(), List.of());
    when(stepRouteConfigAssembler.toRouteConfig(step)).thenReturn(route);
    return app;
  }

  @Test
  @DisplayName("advanceApplication：同一 requestNo 已推进过则跳过，不重复执行管线")
  void advanceApplication_whenDuplicateRequestNo_skipsPipeline() {
    when(stepExecutionRepository.findByRequestNo("REQ-1"))
      .thenReturn(Optional.of(mock(StepExecution.class)));

    service.advanceApplication(new AdvanceStepCommand(
      "APP-001", "FORM_DETAIL_INGESTION", "REQ-1", "U-1", Map.of()));

    verify(pipelineExecutor, never()).executeChain(any(), any(), any());
    verify(stepExecutionRepository, never()).save(any());
  }

  @Test
  @DisplayName("advanceApplication：缺 expectedStep 拒绝")
  void advanceApplication_whenExpectedStepMissing_rejects() {
    assertThatThrownBy(() -> service.advanceApplication(new AdvanceStepCommand(
      "APP-001", null, "REQ-1", "U-1", Map.of())))
      .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("advanceApplication：expectedStep 与当前步骤不符拒绝")
  void advanceApplication_whenExpectedStepMismatch_rejects() {
    BusinessApplication app = stubStep(StepExecutionStatus.SUCCESS);
    when(app.currentStep()).thenReturn(ApplicationFlowStep.DATA_VERIFICATION);
    when(stepExecutionRepository.findByRequestNo("REQ-1")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.advanceApplication(new AdvanceStepCommand(
      "APP-001", "FORM_DETAIL_INGESTION", "REQ-1", "U-1", Map.of())))
      .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("advanceApplication：使用 Command 的 requestNo 与 operator 创建步骤执行")
  void advanceApplication_usesCommandRequestNoAndOperator() {
    BusinessApplication app = stubStep(StepExecutionStatus.SUCCESS);
    when(app.currentStep()).thenReturn(ApplicationFlowStep.FORM_DETAIL_INGESTION);
    when(stepExecutionRepository.findByRequestNo("REQ-1")).thenReturn(Optional.empty());

    service.advanceApplication(new AdvanceStepCommand(
      "APP-001", "FORM_DETAIL_INGESTION", "REQ-1", "U-1", Map.of()));

    ArgumentCaptor<StepExecution> captor = ArgumentCaptor.forClass(StepExecution.class);
    verify(stepExecutionRepository).save(captor.capture());
    assertThat(captor.getValue().requestNo()).isEqualTo("REQ-1");
    assertThat(captor.getValue().updatedBy()).isEqualTo("U-1");
  }

  @Test
  @DisplayName("推进期间 MDC 携带流程标识，临界区结束后恢复现场（§14，批次 38-2）")
  void advanceStep_populatesMdcDuringExecutionAndRestores() {
    MDC.clear();
    stubStep(StepExecutionStatus.SUCCESS);
    AtomicReference<Map<String, String>> during = new AtomicReference<>();
    doAnswer(invocation -> {
      during.set(MDC.getCopyOfContextMap());
      return mock(PipelineExecutionResult.class);
    }).when(pipelineExecutor).executeChain(any(), any(), any());

    service.advanceStep(APP_ID);

    assertThat(during.get()).isNotNull();
    assertThat(during.get().get("applicationId")).isEqualTo("APP-001");
    assertThat(during.get().get("stepExecutionId")).isEqualTo("1");
    assertThat(during.get().get("requestNo")).isEqualTo("SE-1");
    assertThat(during.get().get("businessType")).isEqualTo("ACC_PLAN_CREATE");
    assertThat(MDC.get("applicationId")).isNull();
  }

  @Test
  @DisplayName("推进前已有的 MDC 上下文在结束后完整恢复，traceId 不被覆盖")
  void advanceStep_restoresPreExistingMdc() {
    stubStep(StepExecutionStatus.SUCCESS);
    MDC.put("traceId", "TRACE-1");
    try {
      service.advanceStep(APP_ID);

      assertThat(MDC.get("traceId")).isEqualTo("TRACE-1");
      assertThat(MDC.get("applicationId")).isNull();
    } finally {
      MDC.clear();
    }
  }

  private void stubLockAndRun(DistributedLock lock) {
    doAnswer(invocation -> {
      Supplier<?> task = invocation.getArgument(4);
      return task.get();
    }).when(lock).lockAndRun(anyString(), anyLong(), anyLong(), any(TimeUnit.class), any());
  }

  private BusinessApplication stubStep(StepExecutionStatus status) {
    return stubStep(status, "mainHandler");
  }

  private BusinessApplication stubStep(StepExecutionStatus status, String mainProcessor) {
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
    return app;
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
