package com.example.core.domain.engine.aggregate.root;

import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InteractionType;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.ExternalInteractionStatus;
import com.example.core.domain.engine.event.ExternalInteractionRequestedEvent;
import com.example.core.types.ApplicationId;
import com.example.core.types.ExternalInteractionId;
import com.example.core.types.StepExecutionId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ExternalInteraction 外部协作聚合")
class ExternalInteractionTest {

  private static final String OPERATOR = "U-TEST";
  private static final String REQUEST_NO = "SE-9";

  @Test
  @DisplayName("创建后处于 PENDING_DISPATCH 并从步骤执行 ID 派生稳定 requestNo")
  void create_populatesFields() {
    ExternalInteraction interaction = create();

    assertThat(interaction.id()).isEqualTo(ExternalInteractionId.of(1L));
    assertThat(interaction.applicationId()).isEqualTo(new ApplicationId("APP-1"));
    assertThat(interaction.stepExecutionId()).isEqualTo(StepExecutionId.of(9L));
    assertThat(interaction.type()).isEqualTo(InteractionType.FILE_PARSE);
    assertThat(interaction.requestNo()).isEqualTo(REQUEST_NO);
    assertThat(interaction.status()).isEqualTo(ExternalInteractionStatus.PENDING_DISPATCH);
    assertThat(interaction.externalTaskId()).isNull();
    assertThat(interaction.resultRef()).isNull();
    assertThat(interaction.retryCount()).isZero();
    assertThat(interaction.nextRetryAt()).isNull();
    assertThat(interaction.lastError()).isNull();
    assertThat(interaction.completedAt()).isNull();
    assertThat(interaction.domainEvents()).hasSize(1);
    assertThat(interaction.domainEvents().get(0))
      .isInstanceOf(ExternalInteractionRequestedEvent.class);
  }

  @Test
  @DisplayName("必填领域引用不可为空")
  void create_rejectsNullReferences() {
    assertThatThrownBy(() -> ExternalInteraction.create(
      null, new ApplicationId("APP-1"), StepExecutionId.of(9L),
      InteractionType.FILE_PARSE, OPERATOR)).isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> ExternalInteraction.create(
      ExternalInteractionId.of(1L), null, StepExecutionId.of(9L),
      InteractionType.FILE_PARSE, OPERATOR)).isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> ExternalInteraction.create(
      ExternalInteractionId.of(1L), new ApplicationId("APP-1"), null,
      InteractionType.FILE_PARSE, OPERATOR)).isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> ExternalInteraction.create(
      ExternalInteractionId.of(1L), new ApplicationId("APP-1"), StepExecutionId.of(9L),
      null, OPERATOR)).isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("派发到成功完整链路记录任务 ID、结果引用与完成时间")
  void dispatchToSuccess_recordsLifecycle() {
    ExternalInteraction interaction = create();
    long version = interaction.version().value();

    interaction.markDispatching(OPERATOR);
    interaction.markWaitingCallback("FT-1", OPERATOR);
    interaction.succeed("RESULT-1", OPERATOR);

    assertThat(interaction.status()).isEqualTo(ExternalInteractionStatus.SUCCEEDED);
    assertThat(interaction.externalTaskId()).isEqualTo("FT-1");
    assertThat(interaction.resultRef()).isEqualTo("RESULT-1");
    assertThat(interaction.completedAt()).isNotNull();
    assertThat(interaction.updatedBy()).isEqualTo(OPERATOR);
    assertThat(interaction.version().value()).isGreaterThan(version);
  }

  @Test
  @DisplayName("重试沿用原 requestNo 且递增重试次数")
  void retryKeepsRequestNoStable() {
    ExternalInteraction interaction = create();
    interaction.markDispatching(OPERATOR);
    interaction.markWaitingCallback("FT-1", OPERATOR);
    interaction.fail("外部调用超时", OPERATOR);
    LocalDateTime nextRetryAt = LocalDateTime.of(2026, 11, 12, 10, 0);

    interaction.scheduleRetry(nextRetryAt, OPERATOR);
    interaction.dispatchAgain(OPERATOR);

    assertThat(interaction.requestNo()).isEqualTo(REQUEST_NO);
    assertThat(interaction.retryCount()).isEqualTo(1);
    assertThat(interaction.nextRetryAt()).isEqualTo(nextRetryAt);
    assertThat(interaction.lastError()).isEqualTo("外部调用超时");
    assertThat(interaction.status()).isEqualTo(ExternalInteractionStatus.DISPATCHING);
  }

  @Test
  @DisplayName("UNKNOWN 未经对账不得重新派发，对账确认后可成功")
  void unknownRequiresReconciliationBeforeRedispatch() {
    ExternalInteraction interaction = create();
    interaction.markDispatching(OPERATOR);
    interaction.markUnknown("HTTP 超时，结果未知", OPERATOR);

    assertThat(interaction.status()).isEqualTo(ExternalInteractionStatus.UNKNOWN);
    assertThatThrownBy(() -> interaction.dispatchAgain(OPERATOR))
      .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> interaction.scheduleRetry(LocalDateTime.now(), OPERATOR))
      .isInstanceOf(DomainException.class);

    interaction.confirmSucceededAfterReconciliation("RESULT-1", OPERATOR);

    assertThat(interaction.status()).isEqualTo(ExternalInteractionStatus.SUCCEEDED);
    assertThat(interaction.resultRef()).isEqualTo("RESULT-1");
    assertThat(interaction.completedAt()).isNotNull();
  }

  @Test
  @DisplayName("终态不可再变更")
  void terminalStatesRejectFurtherChanges() {
    ExternalInteraction interaction = create();
    interaction.markDispatching(OPERATOR);
    interaction.succeed("RESULT-1", OPERATOR);

    assertThat(interaction.status().isTerminal()).isTrue();
    assertThatThrownBy(() -> interaction.markDispatching(OPERATOR))
      .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> interaction.cancel(OPERATOR))
      .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("关联优先按 requestNo，其次按外部任务 ID，缺失信息不匹配")
  void matches_prefersRequestNoThenExternalTaskId() {
    ExternalInteraction interaction = create();
    interaction.markDispatching(OPERATOR);
    interaction.markWaitingCallback("FT-1", OPERATOR);

    assertThat(interaction.matches(REQUEST_NO, null)).isTrue();
    assertThat(interaction.matches(null, "FT-1")).isTrue();
    assertThat(interaction.matches("WRONG", "FT-1")).isTrue();
    assertThat(interaction.matches(null, null)).isFalse();
    assertThat(interaction.matches("WRONG", "WRONG")).isFalse();
  }

  @Test
  @DisplayName("取消可从待派发进入终态")
  void cancel_fromPending() {
    ExternalInteraction interaction = create();

    interaction.cancel(OPERATOR);

    assertThat(interaction.status()).isEqualTo(ExternalInteractionStatus.CANCELLED);
    assertThat(interaction.completedAt()).isNotNull();
  }

  @Test
  @DisplayName("reconstitute 装配全部字段")
  void reconstitute_restoresAllFields() {
    LocalDateTime now = LocalDateTime.of(2026, 11, 11, 9, 0);
    ExternalInteraction interaction = ExternalInteraction.reconstitute(
      ExternalInteractionId.of(2L), "U-C", "U-U", now.minusDays(1), now, Version.of(3L),
      new ApplicationId("APP-2"), StepExecutionId.of(10L), InteractionType.APPROVAL,
      "SE-10", "AI-1", ExternalInteractionStatus.WAITING_CALLBACK,
      null, 2, now.plusMinutes(5), "上次超时", null);

    assertThat(interaction.applicationId()).isEqualTo(new ApplicationId("APP-2"));
    assertThat(interaction.type()).isEqualTo(InteractionType.APPROVAL);
    assertThat(interaction.requestNo()).isEqualTo("SE-10");
    assertThat(interaction.externalTaskId()).isEqualTo("AI-1");
    assertThat(interaction.status()).isEqualTo(ExternalInteractionStatus.WAITING_CALLBACK);
    assertThat(interaction.retryCount()).isEqualTo(2);
    assertThat(interaction.nextRetryAt()).isEqualTo(now.plusMinutes(5));
    assertThat(interaction.lastError()).isEqualTo("上次超时");
    assertThat(interaction.version().value()).isEqualTo(3L);
    assertThat(interaction.createdBy()).isEqualTo("U-C");
    assertThat(interaction.updatedBy()).isEqualTo("U-U");
  }

  private ExternalInteraction create() {
    return ExternalInteraction.create(
      ExternalInteractionId.of(1L),
      new ApplicationId("APP-1"),
      StepExecutionId.of(9L),
      InteractionType.FILE_PARSE,
      OPERATOR);
  }
}
