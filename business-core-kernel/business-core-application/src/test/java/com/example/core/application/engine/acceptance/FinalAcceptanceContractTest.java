package com.example.core.application.engine.acceptance;

import com.example.core.application.engine.callback.CallbackCommand;
import com.example.core.application.engine.callback.CallbackCorrelationService;
import com.example.core.application.engine.callback.CallbackOutcome;
import com.example.core.application.engine.callback.CorrelationClassification;
import com.example.core.application.engine.service.CompositeProcessDefinitionGateway;
import com.example.core.application.engine.service.InMemoryProcessDefinitionStore;
import com.example.core.application.engine.service.ProcessDefinitionPublishService;
import com.example.core.application.engine.service.StepDefinitionResolver;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.engine.aggregate.root.ExternalInteraction;
import com.example.core.domain.engine.aggregate.root.StepExecution;
import com.example.core.domain.engine.aggregate.valueobject.BusinessTypeCode;
import com.example.core.domain.engine.aggregate.valueobject.DefinitionVersion;
import com.example.core.domain.engine.aggregate.valueobject.ProcessDefinition;
import com.example.core.domain.engine.aggregate.valueobject.StepCode;
import com.example.core.domain.engine.aggregate.valueobject.StepDefinition;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.BatchMode;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InstanceGranularity;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InteractionType;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.StartMode;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.TaskType;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.ExternalInteractionStatus;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.StepExecutionState;
import com.example.core.domain.engine.aggregate.valueobject.enums.workflow.ApplicationFlowStep;
import com.example.core.domain.engine.gateway.ConditionEvaluationGateway;
import com.example.core.domain.engine.gateway.ProcessDefinitionGateway;
import com.example.core.domain.engine.repository.ExternalInteractionRepository;
import com.example.core.domain.engine.repository.StepExecutionRepository;
import com.example.core.domain.engine.service.registry.StepActionHandlerRegistry;
import com.example.core.types.StepExecutionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.example.core.application.engine.acceptance.ExternalCollaborationFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * §21.5 最终验收契约（批次 39-2）。
 * <p>
 * 对「新业务不改 kernel / 定义升级不影响在途 / 执行历史可审计 / 重复回调不重复跃迁」
 * 四条最终验收标准补可执行契约。纯新增测试，不改生产代码、不改 annuity。
 * <p>
 * 逐条覆盖关系见设计文档 §23.46 的最终验收核对表；多节点真并发、§21.4 架构不变量
 * 全部机器化、E2E 不禁用自动推进等偏差在该表如实标注，不以本类冒充已覆盖。
 */
@DisplayName("§21.5 最终验收契约")
class FinalAcceptanceContractTest {

  @Test
  @DisplayName("新业务无需修改 kernel：发布全新业务类型定义 + 注册 handler 即可接入")
  void newBusiness_registersDefinitionAndSpiOnly() {
    InMemoryProcessDefinitionStore store = new InMemoryProcessDefinitionStore();
    StepActionHandlerRegistry registry = mock(StepActionHandlerRegistry.class);
    when(registry.contains("newLineHandler")).thenReturn(true);
    ConditionEvaluationGateway evaluator = mock(ConditionEvaluationGateway.class);
    ProcessDefinitionPublishService publish =
      new ProcessDefinitionPublishService(store, registry, evaluator,
        mock(com.example.core.domain.engine.service.registry.ExtensionActionRegistry.class));

    BusinessTypeCode type = BusinessTypeCode.of("NEW_BUSINESS_LINE");
    ProcessDefinition definition = definition(type, "v1", "newLineHandler", TaskType.SYSTEM_TASK);

    publish.publish(definition);

    assertThat(store.load(type, DefinitionVersion.of("v1"))).contains(definition);
    assertThat(store.loadLatest(type)).contains(definition);
  }

  @Test
  @DisplayName("定义升级不影响在途实例：latest 已是 v2，v1 实例仍按 v1 定义解析")
  void definitionUpgrade_doesNotAffectInFlightInstance() {
    BusinessTypeCode type = BusinessTypeCode.of("ACC_PLAN_CREATE");
    InMemoryProcessDefinitionStore store = new InMemoryProcessDefinitionStore();
    ProcessDefinitionGateway fallback = mock(ProcessDefinitionGateway.class);
    CompositeProcessDefinitionGateway gateway = new CompositeProcessDefinitionGateway(store, fallback);
    StepDefinitionResolver resolver = new StepDefinitionResolver(gateway);

    store.publish(definition(type, "v1", "handler", TaskType.SYSTEM_TASK));
    ProcessDefinition v2 = definition(type, "v2", "handler", TaskType.USER_TASK);
    store.publish(v2);

    BusinessContext context = mock(BusinessContext.class);
    when(context.businessType()).thenReturn(BusinessType.ACC_PLAN_CREATE);
    BusinessApplication inFlight = mock(BusinessApplication.class);
    when(inFlight.businessContext()).thenReturn(context);
    when(inFlight.definitionVersion()).thenReturn(DefinitionVersion.of("v1"));
    when(inFlight.currentStep()).thenReturn(ApplicationFlowStep.FORM_DETAIL_INGESTION);

    assertThat(gateway.loadLatest(type)).contains(v2);
    assertThat(resolver.resolve(inFlight).taskType()).isEqualTo(TaskType.SYSTEM_TASK);
    assertThat(resolver.isSystemTask(inFlight)).isTrue();
  }

  @Test
  @DisplayName("步骤执行历史可审计：每次尝试独立记录，状态/时间/错误码/检查点留痕")
  void stepExecutionHistory_isAuditable() {
    StepExecution first = StepExecution.create(
      StepExecutionId.of(1L), APP_ID, DefinitionVersion.of("v1"),
      StepCode.of("FORM_DETAIL_INGESTION"), 1, "SE-1", "U-1");
    long initialVersion = first.version().value();

    first.start("U-1");
    first.waitForExternal("U-1");
    first.recordDispatchPayload("{\"fileTaskId\":\"FT-1\"}", "U-1");
    first.fail("EXT_TIMEOUT", "外部超时", "U-1");

    assertThat(first.state()).isEqualTo(StepExecutionState.FAILED);
    assertThat(first.startedAt()).isNotNull();
    assertThat(first.endedAt()).isNotNull();
    assertThat(first.errorCode()).isEqualTo("EXT_TIMEOUT");
    assertThat(first.errorSummary()).isEqualTo("外部超时");
    assertThat(first.checkpointRef()).contains("FT-1");
    assertThat(first.version().value()).isGreaterThan(initialVersion);

    StepExecution retry = StepExecution.retryFrom(StepExecutionId.of(2L), first, "U-1");

    assertThat(retry.attempt()).isEqualTo(2);
    assertThat(retry.requestNo()).isEqualTo("SE-1");
    assertThat(retry.state()).isEqualTo(StepExecutionState.PENDING);
    // 原记录不被覆盖，历史可审计
    assertThat(first.attempt()).isEqualTo(1);
    assertThat(first.state()).isEqualTo(StepExecutionState.FAILED);
  }

  @Test
  @DisplayName("重复回调不重复跃迁：同一协作二次成功回调按 OUT_OF_ORDER 拒绝")
  void repeatedCallback_doesNotDoubleTransition() {
    ExternalInteractionRepository interactionRepo = mock(ExternalInteractionRepository.class);
    StepExecutionRepository stepRepo = mock(StepExecutionRepository.class);
    CallbackCorrelationService service = new CallbackCorrelationService(interactionRepo, stepRepo);

    ExternalInteraction interaction = interaction(1L, InteractionType.FILE_PARSE, REQUEST_NO,
      TASK_ID, ExternalInteractionStatus.WAITING_CALLBACK);
    StepExecution execution = waitingExternal();
    when(interactionRepo.findByRequestNo(REQUEST_NO)).thenReturn(Optional.of(interaction));
    when(stepRepo.load(STEP_EXEC_ID)).thenReturn(Optional.of(execution));

    CallbackCommand command = new CallbackCommand(
      REQUEST_NO, TASK_ID, InteractionType.FILE_PARSE, CallbackOutcome.SUCCEEDED,
      "RESULT-1", OPERATOR);

    assertThat(service.correlate(command).classification())
      .isEqualTo(CorrelationClassification.APPLIED);
    assertThat(service.correlate(command).classification())
      .isEqualTo(CorrelationClassification.OUT_OF_ORDER);

    assertThat(interaction.status()).isEqualTo(ExternalInteractionStatus.SUCCEEDED);
    verify(interactionRepo, times(1)).save(interaction);
    verify(stepRepo, times(1)).save(execution);
  }

  private ProcessDefinition definition(BusinessTypeCode type, String version,
                                       String handler, TaskType taskType) {
    StepDefinition step = StepDefinition.business(
      StepCode.of("FORM_DETAIL_INGESTION"), taskType, handler, List.of());
    return new ProcessDefinition(
      type, DefinitionVersion.of(version),
      StartMode.FORM_DRIVEN, InstanceGranularity.PER_SUB_TASK, BatchMode.REQUIRED,
      StepCode.of("FORM_DETAIL_INGESTION"), List.of(step));
  }
}
