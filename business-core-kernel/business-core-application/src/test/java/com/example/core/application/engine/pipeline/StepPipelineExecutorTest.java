package com.example.core.application.engine.pipeline;

import com.example.core.application.engine.errorcode.CoreAppErrorCode;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.aggregate.valueobject.ExtensionExecutionResult;
import com.example.core.domain.engine.aggregate.valueobject.PipelineExecutionResult;
import com.example.core.domain.engine.aggregate.valueobject.config.StepExtensionConfig;
import com.example.core.domain.engine.gateway.ConditionEvaluationGateway;
import com.example.core.domain.engine.service.registry.ExtensionActionRegistry;
import com.example.core.domain.engine.spi.StepExtensionAction;
import com.example.core.types.CustomerNo;
import com.example.core.types.OperatingMode;
import com.example.core.types.PlanNo;
import com.example.core.types.ProductNo;
import com.example.shared.exception.SystemException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("StepPipelineExecutor 管道执行")
class StepPipelineExecutorTest {

  private ExtensionActionRegistry extensionRegistry;
  private ConditionEvaluationGateway conditionEvaluator;
  private StepPipelineExecutor executor;

  @BeforeEach
  void setUp() {
    extensionRegistry = mock(ExtensionActionRegistry.class);
    conditionEvaluator = mock(ConditionEvaluationGateway.class);
    // 同步执行器：让异步派发在当前线程完成，便于断言入参与执行线程
    executor = new StepPipelineExecutor(extensionRegistry, conditionEvaluator, Runnable::run);
  }

  @Test
  @DisplayName("条件求值异常向上传播,动作不执行也不记为 SKIPPED")
  void conditionErrorPropagates() {
    BusinessApplication app = mock(BusinessApplication.class);
    when(conditionEvaluator.evaluate(any(), any(BusinessMetaContext.class)))
      .thenThrow(new SystemException(CoreAppErrorCode.INVALID_DATA));

    assertThatThrownBy(() -> executor.executeChain(app, context(), List.of(config())))
      .isInstanceOf(SystemException.class);
    verify(extensionRegistry, never()).get(any());
  }

  @Test
  @DisplayName("条件求值为 false 时仍按 SKIPPED 跳过,不改变原有语义")
  void conditionFalseStillSkips() {
    BusinessApplication app = mock(BusinessApplication.class);
    when(conditionEvaluator.evaluate(any(), any(BusinessMetaContext.class))).thenReturn(false);

    PipelineExecutionResult result = executor.executeChain(app, context(), List.of(config()));

    assertThat(result.actionRecords()).hasSize(1);
    assertThat(result.actionRecords().get(0).status())
      .isEqualTo(PipelineExecutionResult.ActionStatus.SKIPPED);
    verify(extensionRegistry, never()).get(any());
  }

  @Test
  @DisplayName("异步动作应收到非 null 的 app 引用，避免只读访问触发 NPE")
  void asyncAction_should_receive_application_not_null() {
    BusinessApplication app = mock(BusinessApplication.class);
    StepExtensionAction action = mockAsyncAction();

    PipelineExecutionResult result = executor.executeChain(app, context(), List.of(asyncConfig()));

    assertThat(result.actionRecords()).hasSize(1);
    assertThat(result.actionRecords().get(0).status())
      .isEqualTo(PipelineExecutionResult.ActionStatus.ASYNC_DISPATCHED);
    ArgumentCaptor<BusinessApplication> captor = ArgumentCaptor.forClass(BusinessApplication.class);
    verify(action).execute(captor.capture(), any(), any());
    assertThat(captor.getValue()).isSameAs(app);
  }

  @Test
  @DisplayName("异步动作异常应被隔离，不中断主链路")
  void asyncAction_failure_should_be_isolated() {
    BusinessApplication app = mock(BusinessApplication.class);
    StepExtensionAction action = mock(StepExtensionAction.class);
    when(action.actionName()).thenReturn("notifyAction");
    when(action.execute(any(), any(), any())).thenThrow(new RuntimeException("boom"));
    when(extensionRegistry.get("notifyAction")).thenReturn(action);
    when(conditionEvaluator.evaluate(any(), any(BusinessMetaContext.class))).thenReturn(true);

    assertThatCode(() -> executor.executeChain(app, context(), List.of(asyncConfig())))
      .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("异步动作应由注入的 Executor 执行，不占用 ForkJoinPool.commonPool")
  void asyncAction_should_run_on_injected_executor() {
    BusinessApplication app = mock(BusinessApplication.class);
    StepExtensionAction action = mock(StepExtensionAction.class);
    AtomicReference<Thread> executionThread = new AtomicReference<>();
    when(action.actionName()).thenReturn("notifyAction");
    when(action.execute(any(), any(), any())).thenAnswer(invocation -> {
      executionThread.set(Thread.currentThread());
      return ExtensionExecutionResult.success();
    });
    when(extensionRegistry.get("notifyAction")).thenReturn(action);
    when(conditionEvaluator.evaluate(any(), any(BusinessMetaContext.class))).thenReturn(true);

    executor.executeChain(app, context(), List.of(asyncConfig()));

    assertThat(executionThread.get()).isSameAs(Thread.currentThread());
  }

  private StepExtensionAction mockAsyncAction() {
    StepExtensionAction action = mock(StepExtensionAction.class);
    when(action.actionName()).thenReturn("notifyAction");
    when(action.execute(any(), any(), any())).thenReturn(ExtensionExecutionResult.success());
    when(extensionRegistry.get("notifyAction")).thenReturn(action);
    when(conditionEvaluator.evaluate(any(), any(BusinessMetaContext.class))).thenReturn(true);
    return action;
  }

  private StepExtensionConfig config() {
    return new StepExtensionConfig(
      "auditAction", 1, "AUDIT", true, false, "#facts['x'] > 1", Map.of());
  }

  private StepExtensionConfig asyncConfig() {
    return new StepExtensionConfig(
      "notifyAction", 1, "NOTIFICATION", false, true, null, Map.of());
  }

  private BusinessMetaContext context() {
    return new BusinessMetaContext(
      CustomerNo.of("C-001"),
      ProductNo.of("P-001"),
      OperatingMode.TRUSTEE_ONLY,
      PlanNo.of("PL-001"),
      BusinessType.ACC_PLAN_CREATE,
      AccountManager.CJP,
      Map.of()
    );
  }
}
