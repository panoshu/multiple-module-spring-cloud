package com.example.core.domain.engine.aggregate.valueobject.enums.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExternalInteractionStatus 外部协作状态迁移")
class ExternalInteractionStatusTest {

  @Test
  @DisplayName("PENDING_DISPATCH 仅可派发或取消")
  void pendingDispatchTransitions() {
    ExternalInteractionStatus state = ExternalInteractionStatus.PENDING_DISPATCH;
    assertThat(state.canTransitTo(ExternalInteractionStatus.DISPATCHING)).isTrue();
    assertThat(state.canTransitTo(ExternalInteractionStatus.CANCELLED)).isTrue();
    assertThat(state.canTransitTo(ExternalInteractionStatus.WAITING_CALLBACK)).isFalse();
    assertThat(state.canTransitTo(ExternalInteractionStatus.SUCCEEDED)).isFalse();
    assertThat(state.canTransitTo(null)).isFalse();
  }

  @Test
  @DisplayName("DISPATCHING 可等待回调、确认结果、失败或结果未知")
  void dispatchingTransitions() {
    ExternalInteractionStatus state = ExternalInteractionStatus.DISPATCHING;
    assertThat(state.canTransitTo(ExternalInteractionStatus.WAITING_CALLBACK)).isTrue();
    assertThat(state.canTransitTo(ExternalInteractionStatus.SUCCEEDED)).isTrue();
    assertThat(state.canTransitTo(ExternalInteractionStatus.FAILED)).isTrue();
    assertThat(state.canTransitTo(ExternalInteractionStatus.UNKNOWN)).isTrue();
    assertThat(state.canTransitTo(ExternalInteractionStatus.CANCELLED)).isTrue();
    assertThat(state.canTransitTo(ExternalInteractionStatus.PENDING_DISPATCH)).isFalse();
  }

  @Test
  @DisplayName("WAITING_CALLBACK 可确认结果、失败、结果未知、安排重试或取消")
  void waitingCallbackTransitions() {
    ExternalInteractionStatus state = ExternalInteractionStatus.WAITING_CALLBACK;
    assertThat(state.canTransitTo(ExternalInteractionStatus.SUCCEEDED)).isTrue();
    assertThat(state.canTransitTo(ExternalInteractionStatus.FAILED)).isTrue();
    assertThat(state.canTransitTo(ExternalInteractionStatus.UNKNOWN)).isTrue();
    assertThat(state.canTransitTo(ExternalInteractionStatus.RETRY_WAIT)).isTrue();
    assertThat(state.canTransitTo(ExternalInteractionStatus.CANCELLED)).isTrue();
    assertThat(state.canTransitTo(ExternalInteractionStatus.DISPATCHING)).isFalse();
  }

  @Test
  @DisplayName("FAILED 只能安排重试或取消，RETRY_WAIT 可重新派发")
  void failedAndRetryWaitTransitions() {
    assertThat(ExternalInteractionStatus.FAILED.canTransitTo(ExternalInteractionStatus.RETRY_WAIT)).isTrue();
    assertThat(ExternalInteractionStatus.FAILED.canTransitTo(ExternalInteractionStatus.CANCELLED)).isTrue();
    assertThat(ExternalInteractionStatus.FAILED.canTransitTo(ExternalInteractionStatus.DISPATCHING)).isFalse();
    assertThat(ExternalInteractionStatus.RETRY_WAIT.canTransitTo(ExternalInteractionStatus.DISPATCHING)).isTrue();
    assertThat(ExternalInteractionStatus.RETRY_WAIT.canTransitTo(ExternalInteractionStatus.CANCELLED)).isTrue();
    assertThat(ExternalInteractionStatus.RETRY_WAIT.canTransitTo(ExternalInteractionStatus.SUCCEEDED)).isFalse();
  }

  @Test
  @DisplayName("UNKNOWN 必须先对账确认结果，不得直接重复创建外部任务")
  void unknownMustReconcileBeforeRedispatch() {
    ExternalInteractionStatus state = ExternalInteractionStatus.UNKNOWN;
    assertThat(state.canTransitTo(ExternalInteractionStatus.SUCCEEDED)).isTrue();
    assertThat(state.canTransitTo(ExternalInteractionStatus.FAILED)).isTrue();
    assertThat(state.canTransitTo(ExternalInteractionStatus.CANCELLED)).isTrue();
    assertThat(state.canTransitTo(ExternalInteractionStatus.DISPATCHING)).isFalse();
    assertThat(state.canTransitTo(ExternalInteractionStatus.RETRY_WAIT)).isFalse();
    assertThat(state.canTransitTo(ExternalInteractionStatus.WAITING_CALLBACK)).isFalse();
    assertThat(state.isTerminal()).isFalse();
  }

  @ParameterizedTest
  @EnumSource(value = ExternalInteractionStatus.class, names = {"SUCCEEDED", "CANCELLED"})
  @DisplayName("终态不可再迁移且 isTerminal 为真")
  void terminalStatesCannotTransit(ExternalInteractionStatus state) {
    assertThat(state.isTerminal()).isTrue();
    for (ExternalInteractionStatus target : ExternalInteractionStatus.values()) {
      assertThat(state.canTransitTo(target)).isFalse();
    }
  }
}
