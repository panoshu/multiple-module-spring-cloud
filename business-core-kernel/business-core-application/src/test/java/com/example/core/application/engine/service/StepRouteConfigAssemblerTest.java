package com.example.core.application.engine.service;

import com.example.core.domain.engine.aggregate.valueobject.RetryPolicy;
import com.example.core.domain.engine.aggregate.valueobject.StepCode;
import com.example.core.domain.engine.aggregate.valueobject.StepDefinition;
import com.example.core.domain.engine.aggregate.valueobject.StepTransition;
import com.example.core.domain.engine.aggregate.valueobject.config.StepExtensionConfig;
import com.example.core.domain.engine.aggregate.valueobject.config.StepRouteConfig;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.ActionType;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.TaskType;
import com.example.core.domain.engine.aggregate.valueobject.enums.workflow.ApplicationFlowStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StepRouteConfigAssembler 定义到执行配置适配")
class StepRouteConfigAssemblerTest {

  private final StepRouteConfigAssembler assembler = new StepRouteConfigAssembler();

  @Test
  @DisplayName("扩展链、主处理器、后继与任务类型无损还原")
  void assemblesRouteConfigWithoutLoss() {
    StepExtensionConfig audit = new StepExtensionConfig(
      "auditAction", 1, "AUDIT", false, true, "#facts['x']", Map.of("k", "v"));
    StepExtensionConfig detail = new StepExtensionConfig(
      "detailAction", 2, "ENRICHMENT", true, false, null, Map.of());
    StepDefinition definition = new StepDefinition(
      StepCode.of("DATA_VERIFICATION"), TaskType.USER_TASK, ActionType.BUSINESS, "mainHandler",
      List.of(audit), List.of(detail), List.of(),
      List.of(new StepTransition(StepCode.of("MATERIAL_PREPARATION"), null)),
      RetryPolicy.defaults(), null);

    StepRouteConfig route = assembler.toRouteConfig(definition);

    assertThat(route.currentStep()).isEqualTo(ApplicationFlowStep.DATA_VERIFICATION);
    assertThat(route.nextStep()).isEqualTo(ApplicationFlowStep.MATERIAL_PREPARATION);
    assertThat(route.taskType()).isEqualTo(StepRouteConfig.StepTaskType.USER_TASK);
    assertThat(route.mainProcessor()).isEqualTo("mainHandler");
    assertThat(route.preValidations()).containsExactly(audit);
    assertThat(route.detailProcessors()).containsExactly(detail);
    assertThat(route.sideEffects()).isEmpty();
  }

  @Test
  @DisplayName("无后继的终点步骤 nextStep 为空")
  void terminalStepHasNoNextStep() {
    StepDefinition definition = StepDefinition.business(
      StepCode.of("COMPLETED"), TaskType.SYSTEM_TASK, "handler", List.of());

    StepRouteConfig route = assembler.toRouteConfig(definition);

    assertThat(route.nextStep()).isNull();
    assertThat(route.currentStep()).isEqualTo(ApplicationFlowStep.COMPLETED);
  }
}
