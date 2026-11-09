package com.example.core.domain.engine.aggregate.valueobject;

import com.example.core.domain.engine.aggregate.valueobject.config.StepExtensionConfig;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.ActionType;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.BatchMode;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InstanceGranularity;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.StartMode;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.TaskType;
import com.example.shared.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("版本化流程定义领域模型")
class ProcessDefinitionTest {

  @Nested
  @DisplayName("强类型编码值对象")
  class CodeValueObjects {

    @Test
    @DisplayName("拒绝 null 与空白编码")
    void rejectsNullAndBlank() {
      assertThatThrownBy(() -> BusinessTypeCode.of(null)).isInstanceOf(DomainException.class);
      assertThatThrownBy(() -> BusinessTypeCode.of("  ")).isInstanceOf(DomainException.class);
      assertThatThrownBy(() -> StepCode.of(null)).isInstanceOf(DomainException.class);
      assertThatThrownBy(() -> StepCode.of("")).isInstanceOf(DomainException.class);
      assertThatThrownBy(() -> DefinitionVersion.of(null)).isInstanceOf(DomainException.class);
      assertThatThrownBy(() -> DefinitionVersion.of("  ")).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("按值判等")
    void equalityByValue() {
      assertThat(StepCode.of("START")).isEqualTo(StepCode.of("START"));
      assertThat(BusinessTypeCode.of("ANNUITY")).isNotEqualTo(BusinessTypeCode.of("LOAN"));
      assertThat(DefinitionVersion.of("v1").value()).isEqualTo("v1");
    }
  }

  @Nested
  @DisplayName("StepTransition 后继与条件")
  class Transitions {

    @Test
    @DisplayName("后继步骤不可为空")
    void nullTargetRejected() {
      assertThatThrownBy(() -> new StepTransition(null, null))
        .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("null 与空串条件视为无条件")
    void nullOrEmptyConditionIsUnconditional() {
      assertThat(new StepTransition(StepCode.of("B"), null).isUnconditional()).isTrue();
      assertThat(new StepTransition(StepCode.of("B"), "").isUnconditional()).isTrue();
    }

    @Test
    @DisplayName("空白条件表达式显式失败，不静默跳过")
    void blankConditionFailsExplicitly() {
      assertThatThrownBy(() -> new StepTransition(StepCode.of("B"), "   "))
        .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("带条件后继保持条件表达式")
    void conditionalTransition() {
      StepTransition transition = new StepTransition(StepCode.of("B"), "#facts['amount'] > 1");
      assertThat(transition.isUnconditional()).isFalse();
      assertThat(transition.condition()).isEqualTo("#facts['amount'] > 1");
    }
  }

  @Nested
  @DisplayName("RetryPolicy 超时与重试")
  class RetryPolicies {

    @Test
    @DisplayName("默认策略为不重试、无超时")
    void defaults() {
      RetryPolicy policy = RetryPolicy.defaults();
      assertThat(policy.maxAttempts()).isEqualTo(1);
      assertThat(policy.timeout()).isNull();
    }

    @Test
    @DisplayName("重试次数必须大于零")
    void rejectsNonPositiveAttempts() {
      assertThatThrownBy(() -> new RetryPolicy(0, null)).isInstanceOf(DomainException.class);
      assertThatThrownBy(() -> new RetryPolicy(-1, null)).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("接受自定义重试次数与超时")
    void acceptsCustomPolicy() {
      RetryPolicy policy = new RetryPolicy(3, Duration.ofSeconds(30));
      assertThat(policy.maxAttempts()).isEqualTo(3);
      assertThat(policy.timeout()).isEqualTo(Duration.ofSeconds(30));
    }
  }

  @Nested
  @DisplayName("StepDefinition 步骤配置")
  class StepConfigs {

    @Test
    @DisplayName("文件与审批步骤必须携带外部配置引用")
    void externalStepRequiresExternalConfigRef() {
      assertThatThrownBy(() -> StepDefinition.external(StepCode.of("A"), ActionType.FILE_PARSE, null, List.of()))
        .isInstanceOf(DomainException.class);
      assertThatThrownBy(() -> StepDefinition.external(StepCode.of("A"), ActionType.FILE_OUTBOUND, "  ", List.of()))
        .isInstanceOf(DomainException.class);
      assertThatThrownBy(() -> StepDefinition.external(StepCode.of("A"), ActionType.APPROVAL, null, List.of()))
        .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("外部步骤携带配置引用时合法")
    void externalStepWithConfigAccepted() {
      assertThatCode(() -> StepDefinition.external(StepCode.of("A"), ActionType.APPROVAL, "approval-cfg", List.of()))
        .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("业务步骤必须配置主处理器")
    void businessStepRequiresHandler() {
      assertThatThrownBy(() -> StepDefinition.business(StepCode.of("A"), TaskType.SYSTEM_TASK, null, List.of()))
        .isInstanceOf(DomainException.class);
      assertThatThrownBy(() -> StepDefinition.business(StepCode.of("A"), TaskType.USER_TASK, "  ", List.of()))
        .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("扩展链完整保留 StepExtensionConfig 配置对象")
    void keepsFullExtensionConfig() {
      StepExtensionConfig audit = new StepExtensionConfig(
        "auditAction", 1, "AUDIT", false, false, null, Map.of("k", "v"));
      StepDefinition definition = new StepDefinition(
        StepCode.of("A"), TaskType.SYSTEM_TASK, ActionType.BUSINESS, "handler",
        List.of(audit), List.of(), List.of(), List.of(), RetryPolicy.defaults(), null);

      assertThat(definition.preValidations()).containsExactly(audit);
      assertThat(definition.preValidations().get(0).params()).containsEntry("k", "v");
      assertThatThrownBy(() -> definition.preValidations().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("集合字段做防御性复制，保证不可变")
    void defensiveCopyOfCollections() {
      List<StepTransition> successors = new ArrayList<>();
      successors.add(new StepTransition(StepCode.of("B"), null));
      StepDefinition definition = StepDefinition.business(StepCode.of("A"), TaskType.SYSTEM_TASK, "handler", successors);

      successors.clear();

      assertThat(definition.successors()).hasSize(1);
      assertThatThrownBy(() -> definition.successors().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  @DisplayName("ProcessDefinition 发布校验")
  class PublishValidation {

    @Test
    @DisplayName("合法定义可构建并保留全部声明")
    void acceptsValidDefinition() {
      ProcessDefinition definition = validDefinition();

      assertThat(definition.businessType()).isEqualTo(BusinessTypeCode.of("ANNUITY"));
      assertThat(definition.version()).isEqualTo(DefinitionVersion.of("v1"));
      assertThat(definition.startMode()).isEqualTo(StartMode.FORM_DRIVEN);
      assertThat(definition.instanceGranularity()).isEqualTo(InstanceGranularity.PER_SUB_TASK);
      assertThat(definition.batchMode()).isEqualTo(BatchMode.REQUIRED);
      assertThat(definition.requiresBatch()).isTrue();
      assertThat(definition.initialStep()).isEqualTo(StepCode.of("START"));
      assertThat(definition.steps()).hasSize(3);
    }

    @Test
    @DisplayName("步骤列表不可为空")
    void rejectsEmptySteps() {
      assertThatThrownBy(() -> definitionWith(List.of()))
        .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("步骤编码必须唯一")
    void rejectsDuplicateStepCodes() {
      List<StepDefinition> duplicated = List.of(
        StepDefinition.business(StepCode.of("A"), TaskType.SYSTEM_TASK, "h1", List.of()),
        StepDefinition.business(StepCode.of("A"), TaskType.SYSTEM_TASK, "h2", List.of())
      );
      assertThatThrownBy(() -> definitionWith(duplicated))
        .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("初始步骤必须存在于步骤列表")
    void rejectsMissingInitialStep() {
      List<StepDefinition> steps = List.of(
        StepDefinition.business(StepCode.of("A"), TaskType.SYSTEM_TASK, "h", List.of())
      );
      assertThatThrownBy(() -> new ProcessDefinition(
        BusinessTypeCode.of("ANNUITY"), DefinitionVersion.of("v1"),
        StartMode.DIRECT, InstanceGranularity.SINGLE, BatchMode.NONE,
        StepCode.of("MISSING"), steps))
        .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("后继步骤必须存在于步骤列表")
    void rejectsDanglingSuccessor() {
      List<StepDefinition> steps = List.of(
        StepDefinition.business(StepCode.of("A"), TaskType.SYSTEM_TASK, "h",
          List.of(new StepTransition(StepCode.of("GHOST"), null)))
      );
      assertThatThrownBy(() -> definitionWith(steps))
        .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("步骤列表做防御性复制")
    void defensiveCopyOfSteps() {
      List<StepDefinition> steps = new ArrayList<>();
      steps.add(StepDefinition.business(StepCode.of("A"), TaskType.SYSTEM_TASK, "h", List.of()));
      ProcessDefinition definition = definitionWith(steps);

      steps.clear();

      assertThat(definition.steps()).hasSize(1);
      assertThatThrownBy(() -> definition.steps().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  @DisplayName("ProcessDefinition 查询能力")
  class Queries {

    @Test
    @DisplayName("按步骤编码查找步骤")
    void stepOf() {
      ProcessDefinition definition = validDefinition();
      assertThat(definition.hasStep(StepCode.of("APPROVAL"))).isTrue();
      assertThat(definition.hasStep(StepCode.of("UNKNOWN"))).isFalse();
      assertThat(definition.stepOf(StepCode.of("APPROVAL")))
        .isPresent()
        .get()
        .extracting(StepDefinition::actionType)
        .isEqualTo(ActionType.APPROVAL);
    }

    @Test
    @DisplayName("无后继的步骤为终点步骤")
    void isTerminalForLastStep() {
      ProcessDefinition definition = validDefinition();
      assertThat(definition.isTerminal(StepCode.of("DONE"))).isTrue();
      assertThat(definition.isTerminal(StepCode.of("START"))).isFalse();
      assertThat(definition.isTerminal(StepCode.of("UNKNOWN"))).isFalse();
    }
  }

  @Nested
  @DisplayName("枚举定义")
  class Enums {

    @Test
    @DisplayName("batchMode 仅 REQUIRED / NONE")
    void batchModes() {
      assertThat(BatchMode.values()).containsExactly(BatchMode.REQUIRED, BatchMode.NONE);
      assertThat(definitionWith(StartMode.DIRECT, BatchMode.NONE).requiresBatch()).isFalse();
    }

    @Test
    @DisplayName("外部动作类型声明需要外部配置")
    void externalActionTypes() {
      assertThat(ActionType.FILE_PARSE.requiresExternalConfig()).isTrue();
      assertThat(ActionType.FILE_OUTBOUND.requiresExternalConfig()).isTrue();
      assertThat(ActionType.APPROVAL.requiresExternalConfig()).isTrue();
      assertThat(ActionType.BUSINESS.requiresExternalConfig()).isFalse();
      assertThat(ActionType.NONE.requiresExternalConfig()).isFalse();
    }
  }

  private static ProcessDefinition validDefinition() {
    return definitionWith(List.of(
      StepDefinition.business(StepCode.of("START"), TaskType.SYSTEM_TASK, "startHandler",
        List.of(new StepTransition(StepCode.of("APPROVAL"), null))),
      StepDefinition.external(StepCode.of("APPROVAL"), ActionType.APPROVAL, "approval-cfg",
        List.of(new StepTransition(StepCode.of("DONE"), "#facts['ok']"))),
      StepDefinition.business(StepCode.of("DONE"), TaskType.SYSTEM_TASK, "doneHandler", List.of())
    ));
  }

  private static ProcessDefinition definitionWith(List<StepDefinition> steps) {
    return new ProcessDefinition(
      BusinessTypeCode.of("ANNUITY"), DefinitionVersion.of("v1"),
      StartMode.FORM_DRIVEN, InstanceGranularity.PER_SUB_TASK, BatchMode.REQUIRED,
      initialStepOf(steps), steps
    );
  }

  private static ProcessDefinition definitionWith(StartMode startMode, BatchMode batchMode) {
    return new ProcessDefinition(
      BusinessTypeCode.of("ANNUITY"), DefinitionVersion.of("v1"),
      startMode, InstanceGranularity.SINGLE, batchMode,
      StepCode.of("A"),
      List.of(StepDefinition.business(StepCode.of("A"), TaskType.SYSTEM_TASK, "h", List.of()))
    );
  }

  private static StepCode initialStepOf(List<StepDefinition> steps) {
    return steps.isEmpty() ? StepCode.of("START") : steps.get(0).stepCode();
  }
}
