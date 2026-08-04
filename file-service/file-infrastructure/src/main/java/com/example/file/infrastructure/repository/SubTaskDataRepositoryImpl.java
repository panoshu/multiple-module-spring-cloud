package com.example.file.infrastructure.repository;

import com.example.file.domain.model.aggregate.root.SubTaskData;
import com.example.file.domain.model.enums.SubTaskStatus;
import com.example.file.domain.model.valueobject.FetchPagination;
import com.example.file.domain.model.valueobject.PageInfo;
import com.example.file.domain.model.valueobject.PagedRows;
import com.example.file.domain.model.valueobject.SubTaskSummary;
import com.example.file.domain.repository.SubTaskDataRepository;
import com.example.file.infrastructure.converter.SubTaskDataConverter;
import com.example.file.infrastructure.entity.SubTaskDataDO;
import com.example.file.infrastructure.mapper.SubTaskDataMapper;
import com.example.file.types.FileTaskId;
import com.example.file.types.SubTaskId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.event.DomainEvent;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static com.example.file.infrastructure.entity.table.SubTaskDataDOTableDef.SUB_TASK_DATA_DO;

@Component
@RequiredArgsConstructor
public class SubTaskDataRepositoryImpl implements SubTaskDataRepository {

  private final SubTaskDataMapper mapper;
  private final SubTaskDataConverter converter;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  public Optional<SubTaskData> findById(SubTaskId id) {
    SubTaskDataDO aDo = mapper.selectOneById(id.value());
    return Optional.ofNullable(aDo).map(converter::toDomain);
  }

  @Override
  public void save(SubTaskData subTask) {
    SubTaskDataDO aDo = converter.toDO(subTask);
    if (mapper.selectOneById(aDo.getId()) == null) {
      mapper.insert(aDo);
    } else {
      mapper.update(aDo);
    }
    publishDomainEvents(subTask);
  }

  @Override
  public PagedRows findPagedRows(SubTaskId id, FetchPagination pagination) {
    SubTaskDataDO aDo = mapper.selectOneById(id.value());
    if (aDo == null) {
      return new PagedRows(List.of(), PageInfo.of(pagination.tableCode(), 0, pagination, 0));
    }
    SubTaskData subTask = converter.toDomain(aDo);
    Map<String, List<Map<String, Object>>> tables = subTask.tables();
    List<Map<String, Object>> allRows = tables.getOrDefault(pagination.tableCode(), List.of());

    int totalCount = allRows.size();
    int fromIndex = Math.min(pagination.startPos(), totalCount);
    int toIndex = Math.min(pagination.endPos(), totalCount);
    List<Map<String, Object>> pagedRows = allRows.subList(fromIndex, toIndex);
    int returnedCount = pagedRows.size();

    return new PagedRows(
      pagedRows,
      PageInfo.of(pagination.tableCode(), totalCount, pagination, returnedCount)
    );
  }

  @Override
  public List<SubTaskSummary> findSummariesByTask(FileTaskId taskId) {
    List<SubTaskDataDO> dos = mapper.selectListByQuery(
      QueryWrapper.create()
        .where(SUB_TASK_DATA_DO.FILE_TASK_ID.eq(taskId.value()))
    );
    return dos.stream()
      .map(converter::toDomain)
      .map(SubTaskData::toSummary)
      .toList();
  }

  @Override
  public void markExpiredBefore(LocalDateTime now) {
    SubTaskDataDO updateEntity = new SubTaskDataDO();
    updateEntity.setStatus(SubTaskStatus.EXPIRED.name());
    QueryWrapper queryWrapper = QueryWrapper.create()
      .where(SUB_TASK_DATA_DO.EXPIRES_AT.lt(now))
      .where(SUB_TASK_DATA_DO.STATUS.ne(SubTaskStatus.EXPIRED.name()));
    mapper.updateByQuery(updateEntity, queryWrapper);
  }

  @Override
  public Optional<SubTaskData> load(SubTaskId id) {
    return findById(id);
  }

  @Override
  public void delete(SubTaskData aggregateRoot) {
    mapper.deleteById(aggregateRoot.id().value());
    publishDomainEvents(aggregateRoot);
  }

  @Override
  public void deleteById(SubTaskId id) {
    mapper.deleteById(id.value());
  }

  @Override
  public List<SubTaskData> loadAll() {
    return mapper.selectAll().stream()
      .map(converter::toDomain)
      .toList();
  }

  @Override
  public void streamByAppId(SubTaskId id, Consumer<AggregateRoot<SubTaskId>> processor) {
    findById(id).ifPresent(task -> processor.accept(task));
  }

  private void publishDomainEvents(SubTaskData subTask) {
    List<DomainEvent> events = subTask.domainEvents();
    if (events.isEmpty()) {
      return;
    }
    for (DomainEvent event : events) {
      eventPublisher.publishEvent(event);
    }
    subTask.clearDomainEvents();
  }
}
