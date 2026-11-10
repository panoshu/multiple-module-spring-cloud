package com.example.core.infrastructure.business.repository;

import com.example.core.domain.engine.aggregate.root.StepExecution;
import com.example.core.domain.engine.aggregate.valueobject.StepCode;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.StepExecutionState;
import com.example.core.domain.engine.repository.StepExecutionRepository;
import com.example.core.infrastructure.business.converter.StepExecutionConverter;
import com.example.core.infrastructure.business.entity.StepExecutionDO;
import com.example.core.infrastructure.business.mapper.StepExecutionMapper;
import com.example.core.types.ApplicationId;
import com.example.core.types.StepExecutionId;
import com.example.shared.domain.event.EventBus;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryWrapper;

import java.util.List;
import java.util.Optional;

import static com.example.core.infrastructure.business.entity.table.StepExecutionDOTableDef.STEP_EXECUTION_DO;

/**
 * 步骤执行仓储实现。
 * <p>
 * 复用 {@link AbstractBusinessRepository} 的通用 CRUD 与乐观锁；StepExecution 不注册领域事件，
 * 故发布环节为空操作。查询语义与 t_step_execution 的唯一索引共同保证幂等与单一运行中执行。
 * <p>
 * 本类<b>不标注 {@code @Repository}</b>，由具体业务服务继承并标注，与其余 kernel 仓储一致。
 */
public class StepExecutionRepositoryImpl
  extends AbstractBusinessRepository<StepExecution, StepExecutionId, StepExecutionDO>
  implements StepExecutionRepository {

  protected final StepExecutionMapper mapper;
  protected final StepExecutionConverter converter;

  public StepExecutionRepositoryImpl(EventBus eventBus,
                                     StepExecutionMapper mapper,
                                     StepExecutionConverter converter) {
    super(eventBus);
    this.mapper = mapper;
    this.converter = converter;
  }

  @Override
  protected BaseMapper<StepExecutionDO> mapper() {
    return mapper;
  }

  @Override
  protected StepExecutionDO toDO(StepExecution aggregate) {
    return converter.toDO(aggregate);
  }

  @Override
  protected StepExecution toDomain(StepExecutionDO aDo) {
    return converter.toDomain(aDo);
  }

  @Override
  protected Object doId(StepExecutionDO aDo) {
    return aDo.getId();
  }

  @Override
  protected Integer doVersion(StepExecutionDO aDo) {
    return aDo.getVersion();
  }

  @Override
  protected void doVersion(StepExecutionDO aDo, Integer version) {
    aDo.setVersion(version);
  }

  @Override
  public Optional<StepExecution> findByRequestNo(String requestNo) {
    if (requestNo == null || requestNo.isBlank()) {
      return Optional.empty();
    }
    StepExecutionDO aDo = mapper.selectOneByQuery(QueryWrapper.create()
      .where(STEP_EXECUTION_DO.REQUEST_NO.eq(requestNo)));
    return Optional.ofNullable(aDo).map(converter::toDomain);
  }

  @Override
  public List<StepExecution> findByApplicationAndStep(ApplicationId applicationId, StepCode stepCode) {
    if (applicationId == null || stepCode == null) {
      return List.of();
    }
    return mapper.selectListByQuery(QueryWrapper.create()
        .where(STEP_EXECUTION_DO.APPLICATION_ID.eq(applicationId.value()))
        .and(STEP_EXECUTION_DO.STEP_CODE.eq(stepCode.value())))
      .stream().map(converter::toDomain).toList();
  }

  @Override
  public Optional<StepExecution> findRunningByApplication(ApplicationId applicationId) {
    if (applicationId == null) {
      return Optional.empty();
    }
    StepExecutionDO aDo = mapper.selectOneByQuery(QueryWrapper.create()
      .where(STEP_EXECUTION_DO.APPLICATION_ID.eq(applicationId.value()))
      .and(STEP_EXECUTION_DO.STATUS.eq(StepExecutionState.RUNNING.name())));
    return Optional.ofNullable(aDo).map(converter::toDomain);
  }
}
