package com.example.core.application.engine.service;

import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.engine.aggregate.valueobject.BusinessTypeCode;
import com.example.core.domain.engine.aggregate.valueobject.DefinitionVersion;
import com.example.core.domain.engine.aggregate.valueobject.ProcessDefinition;
import com.example.core.domain.engine.aggregate.valueobject.StepCode;
import com.example.core.domain.engine.aggregate.valueobject.StepDefinition;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.BatchMode;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InstanceGranularity;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.StartMode;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.TaskType;
import com.example.core.domain.engine.aggregate.valueobject.enums.workflow.ApplicationFlowStep;
import com.example.core.domain.engine.gateway.ProcessDefinitionGateway;
import com.example.core.types.Channel;
import com.example.core.types.CustomerNo;
import com.example.core.types.OperatingMode;
import com.example.core.types.PlanNo;
import com.example.core.types.ProductNo;
import com.example.shared.exception.SystemException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("StepDefinitionResolver 版本化步骤解析")
class StepDefinitionResolverTest {

  private ProcessDefinitionGateway gateway;
  private StepDefinitionResolver resolver;
  private BusinessApplication app;

  @BeforeEach
  void setUp() {
    gateway = mock(ProcessDefinitionGateway.class);
    resolver = new StepDefinitionResolver(gateway);
    app = mock(BusinessApplication.class);
    when(app.businessContext()).thenReturn(context());
    when(app.definitionVersion()).thenReturn(DefinitionVersion.of("v2"));
    when(app.currentStep()).thenReturn(ApplicationFlowStep.DATA_VERIFICATION);
  }

  @Test
  @DisplayName("按业务类型与实例固定版本加载定义")
  void resolvesStepUsingPinnedVersion() {
    when(gateway.load(BusinessTypeCode.of("ACC_PLAN_CREATE"), DefinitionVersion.of("v2")))
      .thenReturn(Optional.of(definitionWith(TaskType.SYSTEM_TASK)));

    StepDefinition step = resolver.resolve(app);

    assertThat(step.stepCode()).isEqualTo(StepCode.of("DATA_VERIFICATION"));
  }

  @Test
  @DisplayName("定义缺失时显式失败")
  void missingDefinitionThrows() {
    when(gateway.load(BusinessTypeCode.of("ACC_PLAN_CREATE"), DefinitionVersion.of("v2")))
      .thenReturn(Optional.empty());

    assertThatThrownBy(() -> resolver.resolve(app))
      .isInstanceOf(SystemException.class);
  }

  @Test
  @DisplayName("当前步骤不在定义中时显式失败")
  void stepNotInDefinitionThrows() {
    when(app.currentStep()).thenReturn(ApplicationFlowStep.ARCHIVE);
    when(gateway.load(BusinessTypeCode.of("ACC_PLAN_CREATE"), DefinitionVersion.of("v2")))
      .thenReturn(Optional.of(definitionWith(TaskType.SYSTEM_TASK)));

    assertThatThrownBy(() -> resolver.resolve(app))
      .isInstanceOf(SystemException.class);
  }

  @Test
  @DisplayName("isSystemTask 反映定义的任务类型")
  void isSystemTaskReflectsTaskType() {
    when(gateway.load(BusinessTypeCode.of("ACC_PLAN_CREATE"), DefinitionVersion.of("v2")))
      .thenReturn(Optional.of(definitionWith(TaskType.USER_TASK)));

    assertThat(resolver.isSystemTask(app)).isFalse();
  }

  private ProcessDefinition definitionWith(TaskType taskType) {
    StepDefinition step = StepDefinition.business(
      StepCode.of("DATA_VERIFICATION"), taskType, "handler", List.of());
    return new ProcessDefinition(
      BusinessTypeCode.of("ACC_PLAN_CREATE"), DefinitionVersion.of("v2"),
      StartMode.FORM_DRIVEN, InstanceGranularity.PER_SUB_TASK, BatchMode.REQUIRED,
      StepCode.of("DATA_VERIFICATION"), List.of(step)
    );
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
