package com.example.core.infrastructure.business.converter;

import com.example.core.domain.engine.aggregate.root.ExternalInteraction;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InteractionType;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.ExternalInteractionStatus;
import com.example.core.infrastructure.business.entity.ExternalInteractionDO;
import com.example.core.types.ApplicationId;
import com.example.core.types.ExternalInteractionId;
import com.example.core.types.StepExecutionId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExternalInteractionConverter DO 与领域对象映射")
class ExternalInteractionConverterTest {

  private static final String OPERATOR = "U-OPERATOR";

  private ExternalInteractionConverter converter;

  @BeforeEach
  void setUp() {
    converter = new ExternalInteractionConverterImpl();
  }

  @Test
  @DisplayName("toDO 映射两个 LONG 主键、枚举值与通用字段")
  void toDO_mapsAllFields() {
    ExternalInteraction interaction = pendingInteraction();

    ExternalInteractionDO aDo = converter.toDO(interaction);

    assertThat(aDo.getId()).isEqualTo(7001L);
    assertThat(aDo.getApplicationId()).isEqualTo("APP-001");
    assertThat(aDo.getStepExecutionId()).isEqualTo(9001L);
    assertThat(aDo.getInteractionType()).isEqualTo("FILE_PARSE");
    assertThat(aDo.getRequestNo()).isEqualTo("SE-9001");
    assertThat(aDo.getStatus()).isEqualTo("PENDING_DISPATCH");
    assertThat(aDo.getExternalTaskId()).isNull();
    assertThat(aDo.getResultRef()).isNull();
    assertThat(aDo.getRetryCount()).isZero();
    assertThat(aDo.getCreatedBy()).isEqualTo("U-CREATOR");
    assertThat(aDo.getCreateTime()).isEqualTo(interaction.createdAt());
    assertThat(aDo.getUpdateTime()).isEqualTo(interaction.updatedAt());
    assertThat(aDo.getDeleted()).isFalse();
    assertThat(aDo.getVersion()).isZero();
  }

  @Test
  @DisplayName("往返重建保留完整生命周期字段")
  void roundTrip_preservesFullLifecycle() {
    ExternalInteraction interaction = pendingInteraction();
    LocalDateTime retryAt = LocalDateTime.of(2026, 11, 12, 10, 0);
    interaction.markDispatching(OPERATOR);
    interaction.markWaitingCallback("FT-1", OPERATOR);
    interaction.fail("外部调用超时", OPERATOR);
    interaction.scheduleRetry(retryAt, OPERATOR);
    interaction.dispatchAgain(OPERATOR);
    interaction.markWaitingCallback("FT-2", OPERATOR);
    interaction.succeed("RESULT-1", OPERATOR);

    ExternalInteraction restored = converter.toDomain(converter.toDO(interaction));

    assertThat(restored.id()).isEqualTo(interaction.id());
    assertThat(restored.applicationId()).isEqualTo(new ApplicationId("APP-001"));
    assertThat(restored.stepExecutionId()).isEqualTo(StepExecutionId.of(9001L));
    assertThat(restored.type()).isEqualTo(InteractionType.FILE_PARSE);
    assertThat(restored.requestNo()).isEqualTo("SE-9001");
    assertThat(restored.externalTaskId()).isEqualTo("FT-2");
    assertThat(restored.status()).isEqualTo(ExternalInteractionStatus.SUCCEEDED);
    assertThat(restored.resultRef()).isEqualTo("RESULT-1");
    assertThat(restored.retryCount()).isEqualTo(1);
    assertThat(restored.nextRetryAt()).isEqualTo(retryAt);
    assertThat(restored.lastError()).isEqualTo("外部调用超时");
    assertThat(restored.completedAt()).isEqualTo(interaction.completedAt());
    assertThat(restored.createdBy()).isEqualTo("U-CREATOR");
    assertThat(restored.updatedBy()).isEqualTo(OPERATOR);
    assertThat(restored.version()).isEqualTo(interaction.version());
  }

  @Test
  @DisplayName("null 输入输出返回 null")
  void nullInput_returnsNull() {
    assertThat(converter.toDO(null)).isNull();
    assertThat(converter.toDomain(null)).isNull();
  }

  private ExternalInteraction pendingInteraction() {
    return ExternalInteraction.create(
      ExternalInteractionId.of(7001L),
      new ApplicationId("APP-001"),
      StepExecutionId.of(9001L),
      InteractionType.FILE_PARSE,
      "U-CREATOR");
  }
}
