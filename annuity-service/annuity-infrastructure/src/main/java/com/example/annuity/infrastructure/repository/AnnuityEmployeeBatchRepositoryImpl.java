package com.example.annuity.infrastructure.repository;

import com.example.annuity.domain.aggregate.entity.AnnuityEmployeeDetail;
import com.example.annuity.domain.aggregate.root.AnnuityEmployeeBatch;
import com.example.annuity.domain.repository.AnnuityEmployeeBatchRepository;
import com.example.annuity.infrastructure.converter.AnnuityEmployeeBatchDataConverter;
import com.example.annuity.infrastructure.converter.AnnuityEmployeeDetailDataConverter;
import com.example.annuity.infrastructure.entity.AnnuityEmployeeBatchDO;
import com.example.annuity.infrastructure.entity.AnnuityEmployeeDetailDO;
import com.example.annuity.infrastructure.mapper.AnnuityEmployeeBatchMapper;
import com.example.annuity.infrastructure.mapper.AnnuityEmployeeDetailMapper;
import com.example.annuity.types.AnnuityEmployeeBatchId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.ApplicationId;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.example.annuity.infrastructure.entity.table.AnnuityEmployeeBatchDOTableDef.ANNUITY_EMPLOYEE_BATCH_DO;
import static com.example.annuity.infrastructure.entity.table.AnnuityEmployeeDetailDOTableDef.ANNUITY_EMPLOYEE_DETAIL_DO;

/**
 * 年金员工明细批次仓储实现
 * <p>
 * 持久化 {@link AnnuityEmployeeBatch} 聚合根到 {@code t_annuity_employee_batch} 表，
 * 明细持久化到 {@code t_annuity_employee_detail} 表。load/findByApplicationId 时
 * 通过 {@link AnnuityEmployeeBatch#attachDetail} 回填明细集合。
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AnnuityEmployeeBatchRepositoryImpl implements AnnuityEmployeeBatchRepository {

  private final AnnuityEmployeeBatchMapper batchMapper;
  private final AnnuityEmployeeDetailMapper detailMapper;
  private final AnnuityEmployeeBatchDataConverter batchConverter;
  private final AnnuityEmployeeDetailDataConverter detailConverter;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  public Optional<AnnuityEmployeeBatch> load(AnnuityEmployeeBatchId id) {
    if (id == null) {
      return Optional.empty();
    }
    AnnuityEmployeeBatchDO batchDO = batchMapper.selectOneById(id.value());
    if (batchDO == null) {
      return Optional.empty();
    }
    return Optional.of(loadWithDetails(batchDO));
  }

  @Override
  public Optional<AnnuityEmployeeBatch> findByApplicationId(ApplicationId applicationId) {
    if (applicationId == null) {
      return Optional.empty();
    }
    AnnuityEmployeeBatchDO batchDO = batchMapper.selectOneByQuery(
        QueryWrapper.create()
            .where(ANNUITY_EMPLOYEE_BATCH_DO.APPLICATION_ID.eq(applicationId.value()))
    );
    if (batchDO == null) {
      return Optional.empty();
    }
    return Optional.of(loadWithDetails(batchDO));
  }

  @Override
  public void save(AnnuityEmployeeBatch batch) {
    if (batch == null) {
      throw new IllegalArgumentException("AnnuityEmployeeBatch cannot be null");
    }
    AnnuityEmployeeBatchDO batchDO = batchConverter.toDO(batch);
    AnnuityEmployeeBatchDO existingBatch = batchMapper.selectOneById(batchDO.getId());
    if (existingBatch == null) {
      batchMapper.insert(batchDO);
      log.debug("新增年金员工批次: batchId={}", batch.id());
    } else {
      // 领域层 Entity.markUpdated() 每次调用都递增 version（一个事务内可能多次），
      // 但 MyBatis-Flex @Column(version=true) 期望 DO 持有 DB 当前版本用于 WHERE 匹配且仅 +1。
      // 若直接用领域版本，WHERE version=N 不匹配 DB 的旧版本，导致 Updates:0 静默失败。
      batchDO.setVersion(existingBatch.getVersion());
      batchMapper.update(batchDO);
      log.debug("更新年金员工批次: batchId={}, version={}", batch.id(), batch.version());
    }

    for (AnnuityEmployeeDetail detail : batch.details()) {
      AnnuityEmployeeDetailDO detailDO = detailConverter.toDO(detail);
      AnnuityEmployeeDetailDO existingDetail = detailMapper.selectOneById(detailDO.getId());
      if (existingDetail == null) {
        detailMapper.insert(detailDO);
      } else {
        // 同 batch：重置为 DB 当前版本，确保乐观锁 WHERE 子句匹配。
        detailDO.setVersion(existingDetail.getVersion());
        detailMapper.update(detailDO);
      }
    }

    publishDomainEvents(batch);
  }

  @Override
  public void delete(AnnuityEmployeeBatch batch) {
    if (batch == null) {
      return;
    }
    detailMapper.deleteByQuery(
        QueryWrapper.create()
            .where(ANNUITY_EMPLOYEE_DETAIL_DO.BATCH_ID.eq(batch.id().value()))
    );
    batchMapper.deleteById(batch.id().value());
    log.debug("删除年金员工批次及其明细: batchId={}", batch.id());
  }

  @Override
  public void deleteById(AnnuityEmployeeBatchId id) {
    if (id == null) {
      return;
    }
    detailMapper.deleteByQuery(
        QueryWrapper.create()
            .where(ANNUITY_EMPLOYEE_DETAIL_DO.BATCH_ID.eq(id.value()))
    );
    batchMapper.deleteById(id.value());
    log.debug("根据 ID 删除年金员工批次及其明细: batchId={}", id);
  }

  @Override
  public List<AnnuityEmployeeBatch> loadAll() {
    return batchMapper.selectAll().stream()
        .map(this::loadWithDetails)
        .toList();
  }

  @Override
  public void streamByAppId(AnnuityEmployeeBatchId id, Consumer<AggregateRoot<AnnuityEmployeeBatchId>> processor) {
    if (id == null || processor == null) {
      return;
    }
    load(id).ifPresent(processor);
  }

  /**
   * 加载批次并回填明细集合
   */
  private AnnuityEmployeeBatch loadWithDetails(AnnuityEmployeeBatchDO batchDO) {
    AnnuityEmployeeBatch batch = batchConverter.toDomain(batchDO);
    List<AnnuityEmployeeDetailDO> detailDOs = detailMapper.selectListByQuery(
        QueryWrapper.create()
            .where(ANNUITY_EMPLOYEE_DETAIL_DO.BATCH_ID.eq(batch.id().value()))
            .orderBy(ANNUITY_EMPLOYEE_DETAIL_DO.ID.asc())
    );
    for (AnnuityEmployeeDetailDO detailDO : detailDOs) {
      batch.attachDetail(detailConverter.toDomain(detailDO));
    }
    return batch;
  }

  /**
   * 发布聚合根内部注册的领域事件
   */
  private void publishDomainEvents(AnnuityEmployeeBatch batch) {
    List<DomainEvent> events = batch.getDomainEvents();
    if (events.isEmpty()) {
      return;
    }
    for (DomainEvent event : events) {
      try {
        eventPublisher.publishEvent(event);
        log.debug("发布领域事件: eventId={}, type={}",
            event.eventId(), event.getClass().getSimpleName());
      } catch (Exception e) {
        log.error("发布领域事件失败: eventId={}, type={}",
            event.eventId(), event.getClass().getSimpleName(), e);
      }
    }
    batch.clearDomainEvents();
  }
}
