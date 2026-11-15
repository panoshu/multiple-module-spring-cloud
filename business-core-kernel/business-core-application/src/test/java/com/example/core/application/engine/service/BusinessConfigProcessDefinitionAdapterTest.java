package com.example.core.application.engine.service;

import com.example.core.domain.engine.aggregate.valueobject.BusinessTypeCode;
import com.example.core.domain.engine.aggregate.valueobject.DefinitionVersion;
import com.example.core.domain.engine.aggregate.valueobject.ProcessDefinition;
import com.example.core.domain.engine.aggregate.valueobject.StepCode;
import com.example.core.domain.engine.aggregate.valueobject.StepDefinition;
import com.example.core.domain.engine.aggregate.valueobject.StepTransition;
import com.example.core.domain.engine.aggregate.valueobject.config.StepRouteConfig;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.BatchMode;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.StartMode;
import com.example.core.domain.engine.aggregate.valueobject.enums.workflow.ApplicationFlowStep;
import com.example.core.domain.engine.gateway.BusinessConfigGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("BusinessConfigProcessDefinitionAdapter 默认流程定义适配")
class BusinessConfigProcessDefinitionAdapterTest {

  private BusinessConfigGateway configGateway;
  private BusinessConfigProcessDefinitionAdapter adapter;

  @BeforeEach
  void setUp() {
    configGateway = mock(BusinessConfigGateway.class);
    adapter = new BusinessConfigProcessDefinitionAdapter(configGateway);
  }

  @Test
  @DisplayName("默认定义覆盖全部存量步骤并声明批次驱动模式")
  void buildsDefinitionCoveringAllFlowSteps() {
    when(configGateway.getNextStep(any(), eq(ApplicationFlowStep.FORM_DETAIL_INGESTION)))
      .thenReturn(route(ApplicationFlowStep.FORM_DETAIL_INGESTION,
        ApplicationFlowStep.DATA_VERIFICATION, "ingestionHandler"));

    ProcessDefinition definition = adapter.loadLatest(BusinessTypeCode.of("ANNUITY")).orElseThrow();

    assertThat(definition.businessType()).isEqualTo(BusinessTypeCode.of("ANNUITY"));
    assertThat(definition.version()).isEqualTo(DefinitionVersion.initial());
    assertThat(definition.batchMode()).isEqualTo(BatchMode.REQUIRED);
    assertThat(definition.startMode()).isEqualTo(StartMode.FORM_DRIVEN);
    assertThat(definition.steps()).hasSize(ApplicationFlowStep.values().length);
    assertThat(definition.hasStep(StepCode.of("FORM_DETAIL_INGESTION"))).isTrue();
  }

  @Test
  @DisplayName("已配置路由的步骤保留主处理器与后继")
  void keepsConfiguredProcessorAndSuccessor() {
    when(configGateway.getNextStep(any(), eq(ApplicationFlowStep.FORM_DETAIL_INGESTION)))
      .thenReturn(route(ApplicationFlowStep.FORM_DETAIL_INGESTION,
        ApplicationFlowStep.DATA_VERIFICATION, "ingestionHandler"));

    ProcessDefinition definition = adapter.loadLatest(BusinessTypeCode.of("ANNUITY")).orElseThrow();
    StepDefinition step = definition.stepOf(StepCode.of("FORM_DETAIL_INGESTION")).orElseThrow();

    assertThat(step.mainProcessor()).isEqualTo("ingestionHandler");
    assertThat(step.successors()).extracting(StepTransition::targetStep)
      .containsExactly(StepCode.of("DATA_VERIFICATION"));
  }

  @Test
  @DisplayName("未配置路由的步骤作为终点步骤")
  void unconfiguredStepIsTerminal() {
    ProcessDefinition definition = adapter.loadLatest(BusinessTypeCode.of("ANNUITY")).orElseThrow();

    assertThat(definition.isTerminal(StepCode.of("FINAL_RELEASE"))).isTrue();
  }

  @Test
  @DisplayName("历史版本缺失时返回空")
  void unknownVersionReturnsEmpty() {
    assertThat(adapter.load(BusinessTypeCode.of("ANNUITY"), DefinitionVersion.of("v99")))
      .isEmpty();
  }

  @Test
  @DisplayName("按请求的业务类型与版本加载")
  void loadUsesRequestedTypeAndVersion() {
    ProcessDefinition definition = adapter.load(
      BusinessTypeCode.of("LOAN"), DefinitionVersion.initial()).orElseThrow();

    assertThat(definition.businessType()).isEqualTo(BusinessTypeCode.of("LOAN"));
    assertThat(definition.version()).isEqualTo(DefinitionVersion.initial());
  }

  private StepRouteConfig route(ApplicationFlowStep current, ApplicationFlowStep next, String processor) {
    return new StepRouteConfig(current, next, StepRouteConfig.StepTaskType.SYSTEM_TASK,
      List.of(), processor, List.of(), List.of());
  }
}
