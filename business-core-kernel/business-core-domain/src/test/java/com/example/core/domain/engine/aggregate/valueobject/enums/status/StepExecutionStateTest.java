package com.example.core.domain.engine.aggregate.valueobject.enums.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StepExecutionState 步骤执行状态迁移")
class StepExecutionStateTest {

  @Test
  @DisplayName("PENDING 可启动、跳过或取消")
  void pendingTransitions() {
    StepExecutionState state = StepExecutionState.PENDING;
    assertThat(state.canTransitTo(StepExecutionState.RUNNING)).isTrue();
    assertThat(state.canTransitTo(StepExecutionState.SKIPPED)).isTrue();
    assertThat(state.canTransitTo(StepExecutionState.CANCELLED)).isTrue();
    assertThat(state.canTransitTo(StepExecutionState.SUCCEEDED)).isFalse();
    assertThat(state.canTransitTo(null)).isFalse();
  }

  @Test
  @DisplayName("RUNNING 可等待外部、成功、失败、重试等待或取消")
  void runningTransitions() {
    StepExecutionState state = StepExecutionState.RUNNING;
    assertThat(state.canTransitTo(StepExecutionState.WAITING_EXTERNAL)).isTrue();
    assertThat(state.canTransitTo(StepExecutionState.SUCCEEDED)).isTrue();
    assertThat(state.canTransitTo(StepExecutionState.FAILED)).isTrue();
    assertThat(state.canTransitTo(StepExecutionState.RETRY_WAIT)).isTrue();
    assertThat(state.canTransitTo(StepExecutionState.CANCELLED)).isTrue();
    assertThat(state.canTransitTo(StepExecutionState.PENDING)).isFalse();
  }

  @Test
  @DisplayName("WAITING_EXTERNAL 可恢复、成功、失败或取消")
  void waitingExternalTransitions() {
    StepExecutionState state = StepExecutionState.WAITING_EXTERNAL;
    assertThat(state.canTransitTo(StepExecutionState.RUNNING)).isTrue();
    assertThat(state.canTransitTo(StepExecutionState.SUCCEEDED)).isTrue();
    assertThat(state.canTransitTo(StepExecutionState.FAILED)).isTrue();
    assertThat(state.canTransitTo(StepExecutionState.CANCELLED)).isTrue();
    assertThat(state.canTransitTo(StepExecutionState.RETRY_WAIT)).isFalse();
  }

  @Test
  @DisplayName("RETRY_WAIT 与 FAILED 均可恢复运行或取消")
  void recoverableTransitions() {
    for (StepExecutionState state : new StepExecutionState[]{
      StepExecutionState.RETRY_WAIT, StepExecutionState.FAILED}) {
      assertThat(state.canTransitTo(StepExecutionState.RUNNING)).isTrue();
      assertThat(state.canTransitTo(StepExecutionState.CANCELLED)).isTrue();
      assertThat(state.canTransitTo(StepExecutionState.SUCCEEDED)).isFalse();
    }
  }

  @ParameterizedTest
  @EnumSource(value = StepExecutionState.class, names = {"SUCCEEDED", "SKIPPED", "CANCELLED"})
  @DisplayName("终态不可再迁移且 isTerminal 为真")
  void terminalStatesCannotTransit(StepExecutionState state) {
    assertThat(state.isTerminal()).isTrue();
    for (StepExecutionState target : StepExecutionState.values()) {
      assertThat(state.canTransitTo(target)).isFalse();
    }
  }

  @Test
  @DisplayName("失败态不是终态，可显式重试")
  void failedIsNotTerminal() {
    assertThat(StepExecutionState.FAILED.isTerminal()).isFalse();
    assertThat(StepExecutionState.FAILED.canTransitTo(StepExecutionState.RUNNING)).isTrue();
  }
}
