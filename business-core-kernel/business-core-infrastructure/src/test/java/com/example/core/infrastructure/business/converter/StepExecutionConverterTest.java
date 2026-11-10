package com.example.core.infrastructure.business.converter;

import com.example.core.domain.engine.aggregate.root.StepExecution;
import com.example.core.domain.engine.aggregate.valueobject.DefinitionVersion;
import com.example.core.domain.engine.aggregate.valueobject.StepCode;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.StepExecutionState;
import com.example.core.infrastructure.business.entity.StepExecutionDO;
import com.example.core.types.ApplicationId;
import com.example.core.types.StepExecutionId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("StepExecutionConverter DO 与领域对象映射")
class StepExecutionConverterTest {

  private StepExecutionConverter converter;

  @BeforeEach
  void setUp() {
    converter = new StepExecutionConverterImpl();
  }

  @Test
  @DisplayName("toDO 映射 LONG 主键、强类型值与通用字段")
  void toDO_mapsAllFields() {
    StepExecution execution = pendingExecution();

    StepExecutionDO aDo = converter.toDO(execution);

    assertThat(aDo.getId()).isEqualTo(9001L);
    assertThat(aDo.getApplicationId()).isEqualTo("APP-001");
    assertThat(aDo.getDefinitionVersion()).isEqualTo("v2");
    assertThat(aDo.getStepCode()).isEqualTo("DATA_VERIFICATION");
    assertThat(aDo.getAttempt()).isEqualTo(1);
    assertThat(aDo.getRequestNo()).isEqualTo("REQ-001");
    assertThat(aDo.getStatus()).isEqualTo("PENDING");
    assertThat(aDo.getCreatedBy()).isEqualTo("U-CREATOR");
    assertThat(aDo.getCreateTime()).isEqualTo(execution.createdAt());
    assertThat(aDo.getUpdateTime()).isEqualTo(execution.updatedAt());
    assertThat(aDo.getDeleted()).isFalse();
    assertThat(aDo.getVersion()).isZero();
  }

  @Test
  @DisplayName("往返重建保留状态迁移产生的时间、错误与版本字段")
  void roundTrip_preservesStateTransitions() {
    StepExecution execution = pendingExecution();
    execution.start("U-OPERATOR");
    execution.fail("E-500", "解析失败", "U-OPERATOR");

    StepExecution restored = converter.toDomain(converter.toDO(execution));

    assertThat(restored.id()).isEqualTo(execution.id());
    assertThat(restored.applicationId()).isEqualTo(new ApplicationId("APP-001"));
    assertThat(restored.definitionVersion()).isEqualTo(DefinitionVersion.of("v2"));
    assertThat(restored.stepCode()).isEqualTo(StepCode.of("DATA_VERIFICATION"));
    assertThat(restored.attempt()).isEqualTo(1);
    assertThat(restored.requestNo()).isEqualTo("REQ-001");
    assertThat(restored.state()).isEqualTo(StepExecutionState.FAILED);
    assertThat(restored.startedAt()).isEqualTo(execution.startedAt());
    assertThat(restored.endedAt()).isEqualTo(execution.endedAt());
    assertThat(restored.errorCode()).isEqualTo("E-500");
    assertThat(restored.errorSummary()).isEqualTo("解析失败");
    assertThat(restored.createdBy()).isEqualTo("U-CREATOR");
    assertThat(restored.updatedBy()).isEqualTo("U-OPERATOR");
    assertThat(restored.version()).isEqualTo(execution.version());
  }

  @Test
  @DisplayName("null 输入输出返回 null")
  void nullInput_returnsNull() {
    assertThat(converter.toDO(null)).isNull();
    assertThat(converter.toDomain(null)).isNull();
  }

  private StepExecution pendingExecution() {
    return StepExecution.create(
      StepExecutionId.of(9001L),
      new ApplicationId("APP-001"),
      DefinitionVersion.of("v2"),
      StepCode.of("DATA_VERIFICATION"),
      1, "REQ-001", "U-CREATOR");
  }
}
