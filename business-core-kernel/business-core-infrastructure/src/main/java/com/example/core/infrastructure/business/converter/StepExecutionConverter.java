package com.example.core.infrastructure.business.converter;

import com.example.core.domain.engine.aggregate.root.StepExecution;
import com.example.core.domain.engine.aggregate.valueobject.DefinitionVersion;
import com.example.core.domain.engine.aggregate.valueobject.StepCode;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.StepExecutionState;
import com.example.core.infrastructure.business.entity.StepExecutionDO;
import com.example.core.types.ApplicationId;
import com.example.core.types.StepExecutionId;
import com.example.shared.domain.aggregate.valueobject.Version;
import org.mapstruct.Mapper;

/**
 * 步骤执行 DO ↔ 领域对象转换器。
 * <p>
 * 沿用 kernel 既有模式：MapStruct 不支持 {@code factoryMethod=reconstitute}，
 * 故用普通方法手工装配，通过 {@link StepExecution#reconstitute} 重建聚合根。
 */
@Mapper(componentModel = "spring")
public abstract class StepExecutionConverter {

  public StepExecutionDO toDO(StepExecution execution) {
    if (execution == null) {
      return null;
    }
    StepExecutionDO aDo = new StepExecutionDO();
    aDo.setId(execution.id() != null ? execution.id().value() : null);
    aDo.setApplicationId(execution.applicationId() != null ? execution.applicationId().value() : null);
    aDo.setDefinitionVersion(execution.definitionVersion() != null ? execution.definitionVersion().value() : null);
    aDo.setStepCode(execution.stepCode() != null ? execution.stepCode().value() : null);
    aDo.setAttempt(execution.attempt());
    aDo.setRequestNo(execution.requestNo());
    aDo.setStatus(execution.state() != null ? execution.state().name() : null);
    aDo.setStartedAt(execution.startedAt());
    aDo.setEndedAt(execution.endedAt());
    aDo.setErrorCode(execution.errorCode());
    aDo.setErrorSummary(execution.errorSummary());
    aDo.setCheckpointRef(execution.checkpointRef());
    aDo.setCreatedBy(execution.createdBy());
    aDo.setUpdatedBy(execution.updatedBy());
    aDo.setCreateTime(execution.createdAt());
    aDo.setUpdateTime(execution.updatedAt());
    aDo.setDeleted(false);
    aDo.setVersion(execution.version() != null ? (int) execution.version().value() : 0);
    return aDo;
  }

  public StepExecution toDomain(StepExecutionDO aDo) {
    if (aDo == null) {
      return null;
    }
    return StepExecution.reconstitute(
      StepExecutionId.of(aDo.getId()),
      aDo.getCreatedBy(),
      aDo.getUpdatedBy(),
      aDo.getCreateTime(),
      aDo.getUpdateTime(),
      aDo.getVersion() != null ? Version.of(aDo.getVersion().longValue()) : Version.initial(),
      aDo.getApplicationId() != null ? new ApplicationId(aDo.getApplicationId()) : null,
      aDo.getDefinitionVersion() != null ? DefinitionVersion.of(aDo.getDefinitionVersion()) : null,
      aDo.getStepCode() != null ? StepCode.of(aDo.getStepCode()) : null,
      aDo.getAttempt() != null ? aDo.getAttempt() : 0,
      aDo.getRequestNo(),
      aDo.getStatus() != null ? StepExecutionState.valueOf(aDo.getStatus()) : null,
      aDo.getStartedAt(),
      aDo.getEndedAt(),
      aDo.getErrorCode(),
      aDo.getErrorSummary(),
      aDo.getCheckpointRef()
    );
  }
}
