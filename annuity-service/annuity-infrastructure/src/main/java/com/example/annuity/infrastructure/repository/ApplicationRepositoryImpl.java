package com.example.annuity.infrastructure.repository;

import com.example.annuity.infrastructure.converter.ApplicationDataConverter;
import com.example.annuity.infrastructure.entity.ApplicationDO;
import com.example.annuity.infrastructure.mapper.ApplicationMapper;
import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.repository.ApplicationRepository;
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

import static com.example.annuity.infrastructure.entity.table.ApplicationDOTableDef.APPLICATION_DO;

/**
 * 年金业务申请单仓储实现
 * <p>
 * 持久化 {@link BusinessApplication} 聚合根到 {@code t_annuity_application} 表，
 * 并覆写 {@link #findByFileTaskId(String)} 以支持 file-service 解析完成回调反查。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ApplicationRepositoryImpl implements ApplicationRepository {

  private final ApplicationMapper mapper;
  private final ApplicationDataConverter converter;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  public Optional<BusinessApplication> load(ApplicationId id) {
    if (id == null) {
      return Optional.empty();
    }
    ApplicationDO aDo = mapper.selectOneById(id.value());
    return Optional.ofNullable(aDo).map(converter::toDomain);
  }

  @Override
  public void save(BusinessApplication app) {
    if (app == null) {
      throw new IllegalArgumentException("BusinessApplication 不能为空");
    }
    ApplicationDO aDo = converter.toDO(app);
    if (mapper.selectOneById(aDo.getId()) == null) {
      mapper.insert(aDo);
      log.debug("新增年金申请单: applicationId={}", app.id());
    } else {
      mapper.update(aDo);
      log.debug("更新年金申请单: applicationId={}, version={}", app.id(), app.version());
    }
    publishDomainEvents(app);
  }

  @Override
  public void delete(BusinessApplication app) {
    if (app == null) {
      return;
    }
    mapper.deleteById(app.id().value());
    log.debug("删除年金申请单: applicationId={}", app.id());
  }

  @Override
  public void deleteById(ApplicationId id) {
    if (id == null) {
      return;
    }
    mapper.deleteById(id.value());
    log.debug("根据 ID 删除年金申请单: applicationId={}", id);
  }

  @Override
  public List<BusinessApplication> loadAll() {
    return mapper.selectAll().stream()
        .map(converter::toDomain)
        .toList();
  }

  @Override
  public void streamByAppId(ApplicationId id, Consumer<AggregateRoot<ApplicationId>> processor) {
    if (id == null || processor == null) {
      return;
    }
    load(id).ifPresent(processor);
  }

  /**
   * 通过文件任务 ID 反查业务申请单。
   * <p>
   * 实现 kernel 默认抛出 {@link UnsupportedOperationException} 的接口，通过
   * {@code parsed_json_file_id} 列反查。file-service 解析完成后，
   * {@code FileParsedEventDTO.fileTaskId} 即为申请单创建时分配的 {@code parsedJsonFileId}。
   */
  @Override
  public Optional<BusinessApplication> findByFileTaskId(String fileTaskId) {
    if (fileTaskId == null || fileTaskId.isBlank()) {
      return Optional.empty();
    }
    ApplicationDO aDo = mapper.selectOneByQuery(
        QueryWrapper.create().where(APPLICATION_DO.PARSED_JSON_FILE_ID.eq(fileTaskId))
    );
    return Optional.ofNullable(aDo).map(converter::toDomain);
  }

  /**
   * 发布聚合根内部注册的领域事件
   */
  private void publishDomainEvents(BusinessApplication app) {
    List<DomainEvent> events = app.getDomainEvents();
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
    app.clearDomainEvents();
  }
}
