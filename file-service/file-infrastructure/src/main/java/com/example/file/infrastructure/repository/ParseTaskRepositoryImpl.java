package com.example.file.infrastructure.repository;

import com.example.file.domain.model.aggregate.root.ParseTask;
import com.example.file.domain.repository.ParseTaskRepository;
import com.example.file.infrastructure.converter.ParseTaskConverter;
import com.example.file.infrastructure.entity.ParseTaskDO;
import com.example.file.infrastructure.mapper.ParseTaskMapper;
import com.example.file.types.FileTaskId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class ParseTaskRepositoryImpl implements ParseTaskRepository {

  private final ParseTaskMapper mapper;
  private final ParseTaskConverter converter;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  public Optional<ParseTask> findById(FileTaskId id) {
    ParseTaskDO aDo = mapper.selectOneById(id.value());
    return Optional.ofNullable(aDo).map(converter::toDomain);
  }

  @Override
  public void save(ParseTask task) {
    ParseTaskDO aDo = converter.toDO(task);
    if (mapper.selectOneById(aDo.getId()) == null) {
      mapper.insert(aDo);
    } else {
      mapper.update(aDo);
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

  @Override
  public void streamByAppId(FileTaskId id, Consumer<AggregateRoot<FileTaskId>> processor) {
    findById(id).ifPresent(task -> processor.accept(task));
  }

  private void publishDomainEvents(ParseTask task) {
    List<DomainEvent> events = task.getDomainEvents();
    if (events.isEmpty()) {
      return;
    }
    for (DomainEvent event : events) {
      eventPublisher.publishEvent(event);
    }
    task.clearDomainEvents();
  }
}
