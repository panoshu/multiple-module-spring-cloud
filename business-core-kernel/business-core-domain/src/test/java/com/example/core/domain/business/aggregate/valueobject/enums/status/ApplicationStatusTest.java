package com.example.core.domain.business.aggregate.valueobject.enums.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApplicationStatus 状态迁移规则")
class ApplicationStatusTest {

  @Test
  @DisplayName("DRAFT 仅可启动或取消")
  void draftTransitions() {
    assertThat(ApplicationStatus.DRAFT.canTransitTo(ApplicationStatus.RUNNING)).isTrue();
    assertThat(ApplicationStatus.DRAFT.canTransitTo(ApplicationStatus.CANCELLED)).isTrue();
    assertThat(ApplicationStatus.DRAFT.canTransitTo(ApplicationStatus.COMPLETED)).isFalse();
    assertThat(ApplicationStatus.DRAFT.canTransitTo(null)).isFalse();
  }

  @Test
  @DisplayName("RUNNING 可挂起、自迁移、完成、失败或取消")
  void runningTransitions() {
    ApplicationStatus running = ApplicationStatus.RUNNING;
    assertThat(running.canTransitTo(ApplicationStatus.WAITING_USER)).isTrue();
    assertThat(running.canTransitTo(ApplicationStatus.WAITING_EXTERNAL)).isTrue();
    assertThat(running.canTransitTo(ApplicationStatus.RUNNING)).isTrue();
    assertThat(running.canTransitTo(ApplicationStatus.COMPLETED)).isTrue();
    assertThat(running.canTransitTo(ApplicationStatus.FAILED)).isTrue();
    assertThat(running.canTransitTo(ApplicationStatus.CANCELLED)).isTrue();
    assertThat(running.canTransitTo(ApplicationStatus.REJECTED)).isFalse();
    assertThat(running.canTransitTo(ApplicationStatus.WITHDRAWN)).isFalse();
  }

  @Test
  @DisplayName("WAITING_USER 可恢复、取消或撤回")
  void waitingUserTransitions() {
    ApplicationStatus waiting = ApplicationStatus.WAITING_USER;
    assertThat(waiting.canTransitTo(ApplicationStatus.RUNNING)).isTrue();
    assertThat(waiting.canTransitTo(ApplicationStatus.CANCELLED)).isTrue();
    assertThat(waiting.canTransitTo(ApplicationStatus.WITHDRAWN)).isTrue();
    assertThat(waiting.canTransitTo(ApplicationStatus.COMPLETED)).isFalse();
  }

  @Test
  @DisplayName("WAITING_EXTERNAL 可恢复、失败、驳回、撤回或取消")
  void waitingExternalTransitions() {
    ApplicationStatus waiting = ApplicationStatus.WAITING_EXTERNAL;
    assertThat(waiting.canTransitTo(ApplicationStatus.RUNNING)).isTrue();
    assertThat(waiting.canTransitTo(ApplicationStatus.FAILED)).isTrue();
    assertThat(waiting.canTransitTo(ApplicationStatus.REJECTED)).isTrue();
    assertThat(waiting.canTransitTo(ApplicationStatus.WITHDRAWN)).isTrue();
    assertThat(waiting.canTransitTo(ApplicationStatus.CANCELLED)).isTrue();
    assertThat(waiting.canTransitTo(ApplicationStatus.COMPLETED)).isFalse();
  }

  @Test
  @DisplayName("FAILED 仅可重试或取消")
  void failedTransitions() {
    ApplicationStatus failed = ApplicationStatus.FAILED;
    assertThat(failed.canTransitTo(ApplicationStatus.RUNNING)).isTrue();
    assertThat(failed.canTransitTo(ApplicationStatus.CANCELLED)).isTrue();
    assertThat(failed.canTransitTo(ApplicationStatus.COMPLETED)).isFalse();
    assertThat(failed.canTransitTo(ApplicationStatus.FAILED)).isFalse();
  }

  @ParameterizedTest
  @EnumSource(value = ApplicationStatus.class, names = {"COMPLETED", "REJECTED", "WITHDRAWN", "CANCELLED"})
  @DisplayName("终态不可再迁移到任何状态")
  void terminalStatesCannotTransit(ApplicationStatus status) {
    assertThat(status.isTerminal()).isTrue();
    for (ApplicationStatus target : ApplicationStatus.values()) {
      assertThat(status.canTransitTo(target)).isFalse();
    }
  }

  @Test
  @DisplayName("存量 PROCESSING 归一为 RUNNING 并沿用其迁移规则")
  void legacyProcessingNormalized() {
    assertThat(ApplicationStatus.PROCESSING.normalized()).isEqualTo(ApplicationStatus.RUNNING);
    assertThat(ApplicationStatus.PROCESSING.canTransitTo(ApplicationStatus.COMPLETED)).isTrue();
    assertThat(ApplicationStatus.PROCESSING.isTerminal()).isFalse();
  }
}
