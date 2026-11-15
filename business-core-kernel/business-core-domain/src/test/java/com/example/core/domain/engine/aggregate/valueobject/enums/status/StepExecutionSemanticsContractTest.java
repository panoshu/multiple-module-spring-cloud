package com.example.core.domain.engine.aggregate.valueobject.enums.status;

import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.spi.StepActionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §8.2 语义边界契约（批次 32 决策）。
 * <p>
 * 规格 §8.2 的「StepExecution 状态」是持久化状态机，由 {@link StepExecutionState} 承担；
 * 「Handler 返回值只表达领域结果，不直接决定数据库状态」，由 {@link StepExecutionStatus} 承担。
 * 两者职责不同、必须并存，不得把持久化状态并入 Handler 返回值，也不得让 Handler 直接写状态。
 * 本测试把该边界固化为机器可校验的契约，防止后续批次无意中重新制造语义分叉。
 */
@DisplayName("StepExecutionStatus/State 语义边界契约")
class StepExecutionSemanticsContractTest {

  private static final Set<String> HANDLER_OUTCOMES =
    Set.of("SUCCESS", "SUSPEND_ASYNC_WAIT", "FAILED");

  private static final Set<String> SPEC_8_2_STATES = Set.of(
    "PENDING", "RUNNING", "WAITING_EXTERNAL", "SUCCEEDED",
    "FAILED", "RETRY_WAIT", "SKIPPED", "CANCELLED");

  @Test
  @DisplayName("Handler 返回值恰为三种领域结果，不含任何持久化状态")
  void handlerOutcomes_onlyThreeDomainResults() {
    Set<String> names = namesOf(StepExecutionStatus.values());

    assertThat(names).isEqualTo(HANDLER_OUTCOMES);
    assertThat(names).doesNotContain("PENDING", "RUNNING", "WAITING_EXTERNAL",
      "SUCCEEDED", "RETRY_WAIT", "SKIPPED", "CANCELLED");
  }

  @Test
  @DisplayName("持久化状态恰为 §8.2 的八态")
  void persistentStates_matchSpec8_2() {
    assertThat(namesOf(StepExecutionState.values())).isEqualTo(SPEC_8_2_STATES);
  }

  @Test
  @DisplayName("StepActionHandler.execute 返回领域结果而非持久化状态")
  void handlerSpi_returnsOutcomeNotState() throws NoSuchMethodException {
    Method execute = StepActionHandler.class.getDeclaredMethod(
      "execute", BusinessApplication.class, BusinessMetaContext.class);

    assertThat(execute.getReturnType()).isEqualTo(StepExecutionStatus.class);
    assertThat(execute.getReturnType()).isNotEqualTo(StepExecutionState.class);
  }

  @Test
  @DisplayName("两个枚举是独立契约，不得合并或互相替代")
  void twoEnums_areDistinctContracts() {
    assertThat(StepExecutionStatus.class).isNotSameAs(StepExecutionState.class);
    assertThat(namesOf(StepExecutionStatus.values()))
      .isNotEqualTo(namesOf(StepExecutionState.values()));
  }

  private Set<String> namesOf(Enum<?>[] values) {
    return Arrays.stream(values).map(Enum::name).collect(Collectors.toSet());
  }
}
