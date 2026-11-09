package com.example.core.application.engine.pipeline;

import com.example.core.application.engine.errorcode.CoreAppErrorCode;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.aggregate.valueobject.PipelineExecutionResult;
import com.example.core.domain.engine.aggregate.valueobject.config.StepExtensionConfig;
import com.example.core.domain.engine.gateway.ConditionEvaluationGateway;
import com.example.core.domain.engine.service.registry.ExtensionActionRegistry;
import com.example.core.types.CustomerNo;
import com.example.core.types.OperatingMode;
import com.example.core.types.PlanNo;
import com.example.core.types.ProductNo;
import com.example.shared.exception.SystemException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("StepPipelineExecutor 条件求值异常传播")
class StepPipelineExecutorTest {

  private ExtensionActionRegistry extensionRegistry;
  private ConditionEvaluationGateway conditionEvaluator;
  private StepPipelineExecutor executor;

  @BeforeEach
  void setUp() {
    extensionRegistry = mock(ExtensionActionRegistry.class);
    conditionEvaluator = mock(ConditionEvaluationGateway.class);
    executor = new StepPipelineExecutor(extensionRegistry, conditionEvaluator);
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

  private StepExtensionConfig config() {
    return new StepExtensionConfig(
      "auditAction", 1, "AUDIT", true, false, "#facts['x'] > 1", Map.of());
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
