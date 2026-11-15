package com.example.file.infrastructure.repository;

import com.example.file.domain.model.parse.aggregate.ParseTask;
import com.example.file.domain.repository.ParseTaskRepository;
import com.example.file.infrastructure.converter.ParseTaskConverter;
import com.example.file.infrastructure.entity.ParseTaskDO;
import com.example.file.infrastructure.mapper.ParseTaskMapper;
import com.example.file.types.FileTaskId;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.domain.event.EventBus;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.example.file.infrastructure.entity.table.ParseTaskDOTableDef.PARSE_TASK_DO;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ParseTaskRepositoryImpl implements ParseTaskRepository {

  private final ParseTaskMapper mapper;
  private final ParseTaskConverter converter;
  private final EventBus eventBus;

  @Override
  public Optional<ParseTask> findById(FileTaskId id) {
    ParseTaskDO aDo = mapper.selectOneById(id.value());
    return Optional.ofNullable(aDo).map(converter::toDomain);
  }

  @Override
  public Optional<ParseTask> findByRequestNo(String requestNo) {
    if (requestNo == null || requestNo.isBlank()) {
      return Optional.empty();
    }
    ParseTaskDO aDo = mapper.selectOneByQuery(
      QueryWrapper.create().where(PARSE_TASK_DO.REQUEST_NO.eq(requestNo)));
    return Optional.ofNullable(aDo).map(converter::toDomain);
  }

  @Override
  public void save(ParseTask task) {
    ParseTaskDO aDo = converter.toDO(task);
    if (mapper.selectOneById(aDo.getId()) == null) {
      mapper.insert(aDo);
    } else {
      mapper.update(aDo);
      task.syncVersionAfterUpdate();
    }
    publishDomainEvents(task);
  }

  @Override
  public Optional<ParseTask> load(FileTaskId id) {
    return findById(id);
  }

  @Override
  public void delete(ParseTask aggregateRoot) {
    mapper.deleteById(aggregateRoot.id().value());
    publishDomainEvents(aggregateRoot);
  }

  @Override
  public void deleteById(FileTaskId id) {
    mapper.deleteById(id.value());
  }

  @Override
  public List<ParseTask> loadAll() {
    return mapper.selectAll().stream()
      .map(converter::toDomain)
      .toList();
  }

  private void publishDomainEvents(ParseTask task) {
    List<DomainEvent> events = task.domainEvents();
    if (events.isEmpty()) {
      return;
    }
    for (DomainEvent event : events) {
      // 直接走 EventBus：Outbox 落库 + 本地 Spring 分发 + Redis 远端分发。
      // 不能再先用 Spring publisher 发布再经 FileDomainEventBridge 二次入总线——
      // SpringEventDispatcher 会把同一领域事件再次本地发布，形成自激循环/重复 eventId。
      eventBus.publish(event);
    }
    task.clearDomainEvents();
  }
}
