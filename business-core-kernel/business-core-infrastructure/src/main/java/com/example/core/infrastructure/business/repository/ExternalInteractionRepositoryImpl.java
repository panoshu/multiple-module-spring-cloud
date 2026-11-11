package com.example.core.infrastructure.business.repository;

import com.example.core.domain.engine.aggregate.root.ExternalInteraction;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InteractionType;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.ExternalInteractionStatus;
import com.example.core.domain.engine.repository.ExternalInteractionRepository;
import com.example.core.infrastructure.business.converter.ExternalInteractionConverter;
import com.example.core.infrastructure.business.entity.ExternalInteractionDO;
import com.example.core.infrastructure.business.mapper.ExternalInteractionMapper;
import com.example.core.types.ApplicationId;
import com.example.core.types.ExternalInteractionId;
import com.example.shared.domain.event.EventBus;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryWrapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.example.core.infrastructure.business.entity.table.ExternalInteractionDOTableDef.EXTERNAL_INTERACTION_DO;

/**
 * 外部协作仓储实现。
 * <p>
 * 复用 {@link AbstractBusinessRepository} 的通用 CRUD 与乐观锁；ExternalInteraction 不注册领域事件，
 * 故发布环节为空操作。查询与 t_business_external_interaction 的索引一一对应。
 * <p>
 * 本类<b>不标注 {@code @Repository}</b>，由具体业务服务继承并标注，与其余 kernel 仓储一致。
 */
public class ExternalInteractionRepositoryImpl
  extends AbstractBusinessRepository<ExternalInteraction, ExternalInteractionId, ExternalInteractionDO>
  implements ExternalInteractionRepository {

  protected final ExternalInteractionMapper mapper;
  protected final ExternalInteractionConverter converter;

  public ExternalInteractionRepositoryImpl(EventBus eventBus,
                                            ExternalInteractionMapper mapper,
                                            ExternalInteractionConverter converter) {
    super(eventBus);
    this.mapper = mapper;
    this.converter = converter;
  }

  @Override
  protected BaseMapper<ExternalInteractionDO> mapper() {
    return mapper;
  }

  @Override
  protected ExternalInteractionDO toDO(ExternalInteraction aggregate) {
    return converter.toDO(aggregate);
  }

  @Override
  protected ExternalInteraction toDomain(ExternalInteractionDO aDo) {
    return converter.toDomain(aDo);
  }

  @Override
  protected Object doId(ExternalInteractionDO aDo) {
    return aDo.getId();
  }

  @Override
  protected Integer doVersion(ExternalInteractionDO aDo) {
    return aDo.getVersion();
  }

  @Override
  protected void doVersion(ExternalInteractionDO aDo, Integer version) {
    aDo.setVersion(version);
  }

  @Override
  public Optional<ExternalInteraction> findByRequestNo(String requestNo) {
    if (requestNo == null || requestNo.isBlank()) {
      return Optional.empty();
    }
    ExternalInteractionDO aDo = mapper.selectOneByQuery(QueryWrapper.create()
      .where(EXTERNAL_INTERACTION_DO.REQUEST_NO.eq(requestNo)));
    return Optional.ofNullable(aDo).map(converter::toDomain);
  }

  @Override
  public Optional<ExternalInteraction> findByExternalTaskId(InteractionType type, String externalTaskId) {
    if (type == null || externalTaskId == null || externalTaskId.isBlank()) {
      return Optional.empty();
    }
    ExternalInteractionDO aDo = mapper.selectOneByQuery(QueryWrapper.create()
      .where(EXTERNAL_INTERACTION_DO.INTERACTION_TYPE.eq(type.name()))
      .and(EXTERNAL_INTERACTION_DO.EXTERNAL_TASK_ID.eq(externalTaskId)));
    return Optional.ofNullable(aDo).map(converter::toDomain);
  }

  @Override
  public List<ExternalInteraction> findPendingRetry(LocalDateTime retryBefore) {
    if (retryBefore == null) {
      return List.of();
    }
    return mapper.selectListByQuery(QueryWrapper.create()
        .where(EXTERNAL_INTERACTION_DO.STATUS.eq(ExternalInteractionStatus.RETRY_WAIT.name()))
        .and(EXTERNAL_INTERACTION_DO.NEXT_RETRY_TIME.le(retryBefore)))
      .stream().map(converter::toDomain).toList();
  }

  @Override
  public List<ExternalInteraction> findByApplication(ApplicationId applicationId) {
    if (applicationId == null) {
      return List.of();
    }
    return mapper.selectListByQuery(QueryWrapper.create()
        .where(EXTERNAL_INTERACTION_DO.APPLICATION_ID.eq(applicationId.value())))
      .stream().map(converter::toDomain).toList();
  }
}
