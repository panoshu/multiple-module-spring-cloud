package com.example.core.domain.engine.aggregate.root;

import com.example.core.domain.engine.aggregate.valueobject.DefinitionVersion;
import com.example.core.domain.engine.aggregate.valueobject.StepCode;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.StepExecutionState;
import com.example.core.types.ApplicationId;
import com.example.core.types.StepExecutionId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("StepExecution 步骤执行聚合")
class StepExecutionTest {

  private static final String OPERATOR = "U-TEST";

  @Test
  @DisplayName("创建后处于 PENDING 并保存全部核心字段")
  void create_populatesFields() {
    StepExecution execution = create(1, "REQ-1");

    assertThat(execution.id()).isEqualTo(StepExecutionId.of(1L));
    assertThat(execution.applicationId()).isEqualTo(new ApplicationId("APP-1"));
    assertThat(execution.definitionVersion()).isEqualTo(DefinitionVersion.of("v1"));
    assertThat(execution.stepCode()).isEqualTo(StepCode.of("DATA_VERIFICATION"));
    assertThat(execution.attempt()).isEqualTo(1);
    assertThat(execution.requestNo()).isEqualTo("REQ-1");
    assertThat(execution.state()).isEqualTo(StepExecutionState.PENDING);
    assertThat(execution.startedAt()).isNull();
    assertThat(execution.endedAt()).isNull();
    assertThat(execution.domainEvents()).isEmpty();
  }

  @Test
  @DisplayName("尝试次数必须大于零")
  void create_rejectsNonPositiveAttempt() {
    assertThatThrownBy(() -> create(0, "REQ-1")).isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> create(-1, "REQ-1")).isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("请求号不可为空")
  void create_rejectsBlankRequestNo() {
    assertThatThrownBy(() -> create(1, null)).isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> create(1, "  ")).isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("必填领域引用不可为空")
  void create_rejectsNullReferences() {
    assertThatThrownBy(() -> StepExecution.create(
      null, new ApplicationId("APP-1"), DefinitionVersion.of("v1"),
      StepCode.of("S"), 1, "REQ-1", OPERATOR)).isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> StepExecution.create(
      StepExecutionId.of(2L), null, DefinitionVersion.of("v1"),
      StepCode.of("S"), 1, "REQ-1", OPERATOR)).isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> StepExecution.create(
      StepExecutionId.of(2L), new ApplicationId("APP-1"), null,
      StepCode.of("S"), 1, "REQ-1", OPERATOR)).isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> StepExecution.create(
      StepExecutionId.of(2L), new ApplicationId("APP-1"), DefinitionVersion.of("v1"),
      null, 1, "REQ-1", OPERATOR)).isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("启动进入 RUNNING 并记录开始时间与版本")
  void start_movesToRunning() {
    StepExecution execution = create(1, "REQ-1");
    long version = execution.version().value();

    execution.start(OPERATOR);

    assertThat(execution.state()).isEqualTo(StepExecutionState.RUNNING);
    assertThat(execution.startedAt()).isNotNull();
    assertThat(execution.endedAt()).isNull();
    assertThat(execution.version().value()).isEqualTo(version + 1);
    assertThat(execution.updatedBy()).isEqualTo(OPERATOR);
  }

  @Test
  @DisplayName("等待外部、成功、失败、重试等待、跳过、取消按迁移表生效")
  void stateTransitionsFollowTable() {
    StepExecution execution = create(1, "REQ-1");
    execution.start(OPERATOR);
    execution.waitForExternal(OPERATOR);
    assertThat(execution.state()).isEqualTo(StepExecutionState.WAITING_EXTERNAL);

    execution.fail("E-500", "外部调用失败", OPERATOR);
    assertThat(execution.state()).isEqualTo(StepExecutionState.FAILED);
    assertThat(execution.errorCode()).isEqualTo("E-500");
    assertThat(execution.errorSummary()).isEqualTo("外部调用失败");
    assertThat(execution.endedAt()).isNotNull();

    execution.retry(OPERATOR);
    assertThat(execution.state()).isEqualTo(StepExecutionState.RUNNING);
    execution.succeed(OPERATOR);
    assertThat(execution.state()).isEqualTo(StepExecutionState.SUCCEEDED);
  }

  @Test
  @DisplayName("终态不可再迁移")
  void terminalStateCannotTransit() {
    StepExecution succeeded = create(1, "REQ-1");
    succeeded.start(OPERATOR);
    succeeded.succeed(OPERATOR);

    assertThatThrownBy(() -> succeeded.fail("E", "err", OPERATOR))
      .isInstanceOf(DomainException.class);

    StepExecution cancelled = create(2, "REQ-2");
    cancelled.cancel(OPERATOR);
    assertThatThrownBy(() -> cancelled.start(OPERATOR))
      .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("非法迁移显式失败")
  void illegalTransitionThrows() {
    StepExecution execution = create(1, "REQ-1");

    assertThatThrownBy(() -> execution.succeed(OPERATOR))
      .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("重试生成新的 attempt 且沿用原请求号")
  void retryFrom_createsNextAttempt() {
    StepExecution first = create(1, "REQ-STABLE");
    first.start(OPERATOR);
    first.fail("E-TIMEOUT", "超时", OPERATOR);

    StepExecution retry = StepExecution.retryFrom(StepExecutionId.of(99L), first, OPERATOR);

    assertThat(retry.id()).isEqualTo(StepExecutionId.of(99L));
    assertThat(retry.attempt()).isEqualTo(2);
    assertThat(retry.requestNo()).isEqualTo("REQ-STABLE");
    assertThat(retry.applicationId()).isEqualTo(first.applicationId());
    assertThat(retry.stepCode()).isEqualTo(first.stepCode());
    assertThat(retry.definitionVersion()).isEqualTo(first.definitionVersion());
    assertThat(retry.state()).isEqualTo(StepExecutionState.PENDING);
    assertThat(first.attempt()).isEqualTo(1);
  }

  @Test
  @DisplayName("终态记录不可作为重试来源")
  void retryFrom_rejectsTerminalSource() {
    StepExecution succeeded = create(1, "REQ-1");
    succeeded.start(OPERATOR);
    succeeded.succeed(OPERATOR);

    assertThatThrownBy(() -> StepExecution.retryFrom(StepExecutionId.of(2L), succeeded, OPERATOR))
      .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("重建装配全部字段且不触发业务事件")
  void reconstitute_preservesAllFields() {
    LocalDateTime now = LocalDateTime.now();
    StepExecution execution = StepExecution.reconstitute(
      StepExecutionId.of(7L), "U-C", "U-U", now.minusDays(1), now, Version.of(3L),
      new ApplicationId("APP-7"), DefinitionVersion.of("v2"), StepCode.of("APPROVAL"),
      4, "REQ-7", StepExecutionState.WAITING_EXTERNAL,
      now.minusMinutes(5), null, "E-1", "等待回调", "CHECKPOINT-1"
    );

    assertThat(execution.attempt()).isEqualTo(4);
    assertThat(execution.requestNo()).isEqualTo("REQ-7");
    assertThat(execution.state()).isEqualTo(StepExecutionState.WAITING_EXTERNAL);
    assertThat(execution.errorCode()).isEqualTo("E-1");
    assertThat(execution.checkpointRef()).isEqualTo("CHECKPOINT-1");
    assertThat(execution.version().value()).isEqualTo(3L);
    assertThat(execution.domainEvents()).isEmpty();
  }

  private StepExecution create(int attempt, String requestNo) {
    return StepExecution.create(
      StepExecutionId.of(1L), new ApplicationId("APP-1"), DefinitionVersion.of("v1"),
      StepCode.of("DATA_VERIFICATION"), attempt, requestNo, OPERATOR
    );
  }
}
