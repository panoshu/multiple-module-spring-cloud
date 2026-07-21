package com.example.annuity.infrastructure.repository;

import com.example.annuity.infrastructure.converter.BatchDataConverter;
import com.example.annuity.infrastructure.entity.BatchDO;
import com.example.annuity.infrastructure.mapper.BatchMapper;
import com.example.core.domain.aggregate.root.BusinessBatch;
import com.example.core.domain.repository.BatchRepository;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.FormId;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.example.annuity.infrastructure.entity.table.ApplicationDOTableDef.APPLICATION_DO;
import static com.example.annuity.infrastructure.entity.table.BatchDOTableDef.BATCH_DO;
import static com.example.annuity.infrastructure.entity.table.FormDOTableDef.FORM_DO;

/**
 * 年金业务批次仓储实现
 * <p>
 * 持久化 {@link BusinessBatch} 聚合根到 {@code t_annuity_batch} 表，
 * 并通过 {@code t_annuity_form} / {@code t_annuity_application} 反向查询支持
 * {@link #findByFormId(FormId)} 和 {@link #findByApplicationId(ApplicationId)}。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class BatchRepositoryImpl implements BatchRepository {

  private final BatchMapper mapper;
  private final BatchDataConverter converter;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  public Optional<BusinessBatch> load(BatchId id) {
    if (id == null) {
      return Optional.empty();
    }
    BatchDO aDo = mapper.selectOneById(id.value());
    return Optional.ofNullable(aDo).map(converter::toDomain);
  }

  @Override
  public void save(BusinessBatch batch) {
    if (batch == null) {
      throw new IllegalArgumentException("BusinessBatch 不能为空");
    }
    BatchDO aDo = converter.toDO(batch);
    if (mapper.selectOneById(aDo.getId()) == null) {
      mapper.insert(aDo);
      log.debug("新增年金批次: batchId={}", batch.id());
    } else {
      mapper.update(aDo);
      log.debug("更新年金批次: batchId={}, version={}", batch.id(), batch.version());
    }
    publishDomainEvents(batch);
  }

  @Override
  public void delete(BusinessBatch batch) {
    if (batch == null) {
      return;
    }
    mapper.deleteById(batch.id().value());
    log.debug("删除年金批次: batchId={}", batch.id());
  }

  @Override
  public void deleteById(BatchId id) {
    if (id == null) {
      return;
    }
    mapper.deleteById(id.value());
    log.debug("根据 ID 删除年金批次: batchId={}", id);
  }

  @Override
  public List<BusinessBatch> loadAll() {
    return mapper.selectAll().stream()
        .map(converter::toDomain)
        .toList();
  }

  @Override
  public void streamByAppId(BatchId id, Consumer<AggregateRoot<BatchId>> processor) {
    if (id == null || processor == null) {
      return;
    }
    load(id).ifPresent(processor);
  }

  /**
   * 通过表单 ID 反查批次：t_annuity_form.batch_id → t_annuity_batch.id
   */
  @Override
  public Optional<BusinessBatch> findByFormId(FormId formId) {
    if (formId == null) {
      return Optional.empty();
    }
    BatchDO aDo = mapper.selectOneByQuery(
        QueryWrapper.create()
            .where(BATCH_DO.ID.in(
                QueryWrapper.create()
                    .select(FORM_DO.BATCH_ID)
                    .from(FORM_DO)
                    .where(FORM_DO.ID.eq(formId.value()))
            ))
    );
    return Optional.ofNullable(aDo).map(converter::toDomain);
  }

  /**
   * 通过申请单 ID 反查批次：t_annuity_application.batch_id → t_annuity_batch.id
   */
  @Override
  public Optional<BusinessBatch> findByApplicationId(ApplicationId applicationId) {
    if (applicationId == null) {
      return Optional.empty();
    }
    BatchDO aDo = mapper.selectOneByQuery(
        QueryWrapper.create()
            .where(BATCH_DO.ID.in(
                QueryWrapper.create()
                    .select(APPLICATION_DO.BATCH_ID)
                    .from(APPLICATION_DO)
                    .where(APPLICATION_DO.ID.eq(applicationId.value()))
            ))
    );
    return Optional.ofNullable(aDo).map(converter::toDomain);
  }

  /**
   * 发布聚合根内部注册的领域事件
   */
  private void publishDomainEvents(BusinessBatch batch) {
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
