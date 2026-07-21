package com.example.annuity.infrastructure.repository;

import com.example.annuity.infrastructure.converter.FormDataConverter;
import com.example.annuity.infrastructure.entity.FormDO;
import com.example.annuity.infrastructure.mapper.FormMapper;
import com.example.core.domain.aggregate.root.BusinessForm;
import com.example.core.domain.repository.FormRepository;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.ApplicationId;
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
import static com.example.annuity.infrastructure.entity.table.FormDOTableDef.FORM_DO;

/**
 * 年金业务表单仓储实现
 * <p>
 * 持久化 {@link BusinessForm} 聚合根到 {@code t_annuity_form} 表，
 * 并通过 {@code t_annuity_application.form_id} 反向查询支持
 * {@link #findByApplicationId(ApplicationId)}。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class FormRepositoryImpl implements FormRepository {

  private final FormMapper mapper;
  private final FormDataConverter converter;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  public Optional<BusinessForm> load(FormId id) {
    if (id == null) {
      return Optional.empty();
    }
    FormDO aDo = mapper.selectOneById(id.value());
    return Optional.ofNullable(aDo).map(converter::toDomain);
  }

  @Override
  public void save(BusinessForm form) {
    if (form == null) {
      throw new IllegalArgumentException("BusinessForm 不能为空");
    }
    FormDO aDo = converter.toDO(form);
    if (mapper.selectOneById(aDo.getId()) == null) {
      mapper.insert(aDo);
      log.debug("新增年金表单: formId={}", form.id());
    } else {
      mapper.update(aDo);
      log.debug("更新年金表单: formId={}, version={}", form.id(), form.version());
    }
    publishDomainEvents(form);
  }

  @Override
  public void delete(BusinessForm form) {
    if (form == null) {
      return;
    }
    mapper.deleteById(form.id().value());
    log.debug("删除年金表单: formId={}", form.id());
  }

  @Override
  public void deleteById(FormId id) {
    if (id == null) {
      return;
    }
    mapper.deleteById(id.value());
    log.debug("根据 ID 删除年金表单: formId={}", id);
  }

  @Override
  public List<BusinessForm> loadAll() {
    return mapper.selectAll().stream()
        .map(converter::toDomain)
        .toList();
  }

  @Override
  public void streamByAppId(FormId id, Consumer<AggregateRoot<FormId>> processor) {
    if (id == null || processor == null) {
      return;
    }
    load(id).ifPresent(processor);
  }

  /**
   * 通过申请单 ID 反查表单：t_annuity_application.form_id → t_annuity_form.id
   */
  @Override
  public Optional<BusinessForm> findByApplicationId(ApplicationId applicationId) {
    if (applicationId == null) {
      return Optional.empty();
    }
    FormDO aDo = mapper.selectOneByQuery(
        QueryWrapper.create()
            .where(FORM_DO.ID.in(
                QueryWrapper.create()
                    .select(APPLICATION_DO.FORM_ID)
                    .from(APPLICATION_DO)
                    .where(APPLICATION_DO.ID.eq(applicationId.value()))
            ))
    );
    return Optional.ofNullable(aDo).map(converter::toDomain);
  }

  /**
   * 发布聚合根内部注册的领域事件
   */
  private void publishDomainEvents(BusinessForm form) {
    List<DomainEvent> events = form.getDomainEvents();
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
    form.clearDomainEvents();
  }
}
