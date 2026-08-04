package com.example.file.infrastructure.repository;

import com.example.file.domain.model.aggregate.root.TemplateConfig;
import com.example.file.domain.model.enums.ConfigStatus;
import com.example.file.domain.repository.TemplateConfigRepository;
import com.example.file.infrastructure.converter.TemplateConfigConverter;
import com.example.file.infrastructure.entity.TemplateConfigDO;
import com.example.file.infrastructure.mapper.TemplateConfigMapper;
import com.example.file.types.BizType;
import com.example.file.types.TemplateConfigId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.event.DomainEvent;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.example.file.infrastructure.entity.table.TemplateConfigDOTableDef.TEMPLATE_CONFIG_DO;

@Component
@RequiredArgsConstructor
public class TemplateConfigRepositoryImpl implements TemplateConfigRepository {

  private final TemplateConfigMapper mapper;
  private final TemplateConfigConverter converter;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  public Optional<TemplateConfig> findById(TemplateConfigId id) {
    TemplateConfigDO aDo = mapper.selectOneById(id.value());
    return Optional.ofNullable(aDo).map(converter::toDomain);
  }

  @Override
  public void save(TemplateConfig config) {
    TemplateConfigDO aDo = converter.toDO(config);
    if (mapper.selectOneById(aDo.getId()) == null) {
      mapper.insert(aDo);
    } else {
      mapper.update(aDo);
    }
    publishDomainEvents(config);
  }

  @Override
  public Optional<TemplateConfig> findActive(BizType bizType) {
    List<TemplateConfigDO> dos = mapper.selectListByQuery(
      QueryWrapper.create()
        .where(TEMPLATE_CONFIG_DO.BIZ_TYPE.eq(bizType.value()))
        .where(TEMPLATE_CONFIG_DO.STATUS.eq(ConfigStatus.ACTIVE.name()))
        .orderBy(TEMPLATE_CONFIG_DO.EFFECTIVE_FROM.desc())
        .limit(1)
    );
    return dos.isEmpty() ? Optional.empty() : Optional.of(converter.toDomain(dos.get(0)));
  }

  @Override
  public Optional<TemplateConfig> findByBizTypeAndVersion(BizType bizType, String version) {
    TemplateConfigDO aDo = mapper.selectOneByQuery(
      QueryWrapper.create()
        .where(TEMPLATE_CONFIG_DO.BIZ_TYPE.eq(bizType.value()))
        .where(TEMPLATE_CONFIG_DO.TEMPLATE_VERSION.eq(version))
    );
    return Optional.ofNullable(aDo).map(converter::toDomain);
  }

  @Override
  public Optional<TemplateConfig> load(TemplateConfigId id) {
    return findById(id);
  }

  @Override
  public void delete(TemplateConfig aggregateRoot) {
    mapper.deleteById(aggregateRoot.id().value());
    publishDomainEvents(aggregateRoot);
  }

  @Override
  public void deleteById(TemplateConfigId id) {
    mapper.deleteById(id.value());
  }

  @Override
  public List<TemplateConfig> loadAll() {
    return mapper.selectAll().stream()
      .map(converter::toDomain)
      .toList();
  }

  @Override
  public void streamByAppId(TemplateConfigId id, Consumer<AggregateRoot<TemplateConfigId>> processor) {
    findById(id).ifPresent(config -> processor.accept(config));
  }

  private void publishDomainEvents(TemplateConfig config) {
    List<DomainEvent> events = config.domainEvents();
    if (events.isEmpty()) {
      return;
    }
    for (DomainEvent event : events) {
      eventPublisher.publishEvent(event);
    }
    config.clearDomainEvents();
  }
}
