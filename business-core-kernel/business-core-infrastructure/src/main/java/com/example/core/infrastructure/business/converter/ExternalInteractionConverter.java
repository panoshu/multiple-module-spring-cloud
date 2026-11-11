package com.example.core.infrastructure.business.converter;

import com.example.core.domain.engine.aggregate.root.ExternalInteraction;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InteractionType;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.ExternalInteractionStatus;
import com.example.core.infrastructure.business.entity.ExternalInteractionDO;
import com.example.core.types.ApplicationId;
import com.example.core.types.ExternalInteractionId;
import com.example.core.types.StepExecutionId;
import com.example.shared.domain.aggregate.valueobject.Version;
import org.mapstruct.Mapper;

/**
 * 外部协作 DO ↔ 领域对象转换器。
 * <p>
 * 沿用 kernel 既有模式：MapStruct 不支持 {@code factoryMethod=reconstitute}，
 * 故用普通方法手工装配，通过 {@link ExternalInteraction#reconstitute} 重建聚合根。
 */
@Mapper(componentModel = "spring")
public abstract class ExternalInteractionConverter {

  public ExternalInteractionDO toDO(ExternalInteraction interaction) {
    if (interaction == null) {
      return null;
    }
    ExternalInteractionDO aDo = new ExternalInteractionDO();
    aDo.setId(interaction.id() != null ? interaction.id().value() : null);
    aDo.setApplicationId(interaction.applicationId() != null ? interaction.applicationId().value() : null);
    aDo.setStepExecutionId(interaction.stepExecutionId() != null ? interaction.stepExecutionId().value() : null);
    aDo.setInteractionType(interaction.type() != null ? interaction.type().name() : null);
    aDo.setRequestNo(interaction.requestNo());
    aDo.setExternalTaskId(interaction.externalTaskId());
    aDo.setStatus(interaction.status() != null ? interaction.status().name() : null);
    aDo.setResultRef(interaction.resultRef());
    aDo.setRetryCount(interaction.retryCount());
    aDo.setNextRetryTime(interaction.nextRetryAt());
    aDo.setLastError(interaction.lastError());
    aDo.setCompletedAt(interaction.completedAt());
    aDo.setCreatedBy(interaction.createdBy());
    aDo.setUpdatedBy(interaction.updatedBy());
    aDo.setCreateTime(interaction.createdAt());
    aDo.setUpdateTime(interaction.updatedAt());
    aDo.setDeleted(false);
    aDo.setVersion(interaction.version() != null ? (int) interaction.version().value() : 0);
    return aDo;
  }

  public ExternalInteraction toDomain(ExternalInteractionDO aDo) {
    if (aDo == null) {
      return null;
    }
    return ExternalInteraction.reconstitute(
      ExternalInteractionId.of(aDo.getId()),
      aDo.getCreatedBy(),
      aDo.getUpdatedBy(),
      aDo.getCreateTime(),
      aDo.getUpdateTime(),
      aDo.getVersion() != null ? Version.of(aDo.getVersion().longValue()) : Version.initial(),
      aDo.getApplicationId() != null ? new ApplicationId(aDo.getApplicationId()) : null,
      aDo.getStepExecutionId() != null ? StepExecutionId.of(aDo.getStepExecutionId()) : null,
      aDo.getInteractionType() != null ? InteractionType.valueOf(aDo.getInteractionType()) : null,
      aDo.getRequestNo(),
      aDo.getExternalTaskId(),
      aDo.getStatus() != null ? ExternalInteractionStatus.valueOf(aDo.getStatus()) : null,
      aDo.getResultRef(),
      aDo.getRetryCount() != null ? aDo.getRetryCount() : 0,
      aDo.getNextRetryTime(),
      aDo.getLastError(),
      aDo.getCompletedAt()
    );
  }
}
