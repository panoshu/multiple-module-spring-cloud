package com.example.file.infrastructure.repository;

import com.example.file.domain.model.aggregate.root.FileMetadata;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.file.infrastructure.converter.FileMetadataConverter;
import com.example.file.infrastructure.entity.FileMetadataDO;
import com.example.file.infrastructure.mapper.FileMetadataMapper;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.FileId;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.example.file.infrastructure.entity.table.FileMetadataDOTableDef.FILE_METADATA_DO;

@Slf4j
@Repository
@RequiredArgsConstructor
public class FileMetadataRepositoryImpl implements FileMetadataRepository {

    private final FileMetadataMapper mapper;
    private final FileMetadataConverter converter;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Optional<FileMetadata> load(FileId id) {
        if (id == null) return Optional.empty();
        FileMetadataDO aDo = mapper.selectOneById(id.value());
        return Optional.ofNullable(aDo).map(converter::toDomain);
    }

    @Override
    public void save(FileMetadata file) {
        if (file == null) throw new IllegalArgumentException("file 不能为空");
        FileMetadataDO aDo = converter.toDO(file);
        if (mapper.selectOneById(aDo.getId()) == null) {
            mapper.insert(aDo);
            log.debug("新增文件元数据: fileId={}", file.id());
        } else {
            mapper.update(aDo);
            log.debug("更新文件元数据: fileId={}, version={}", file.id(), file.version());
        }
        publishDomainEvents(file);
    }

    @Override
    public void delete(FileMetadata file) {
        if (file == null) return;
        file.markDeleted(file.updatedBy() != null ? file.updatedBy() : file.createdBy());
        save(file);
    }

    @Override
    public void deleteById(FileId id) {
        if (id == null) return;
        FileMetadataDO aDo = mapper.selectOneById(id.value());
        if (aDo != null) {
            FileMetadata file = converter.toDomain(aDo);
            delete(file);
        }
    }

    @Override
    public List<FileMetadata> loadAll() {
        return mapper.selectAll().stream()
            .map(converter::toDomain)
            .toList();
    }

    @Override
    public void streamByAppId(FileId id, Consumer<AggregateRoot<FileId>> processor) {
        if (id == null || processor == null) return;
        load(id).ifPresent(processor);
    }

    @Override
    public List<FileMetadata> findByBusinessBatchId(String businessBatchId) {
        if (businessBatchId == null) return List.of();
        List<FileMetadataDO> list = mapper.selectListByQuery(
            QueryWrapper.create().where(FILE_METADATA_DO.BUSINESS_BATCH_ID.eq(businessBatchId))
        );
        return list.stream().map(converter::toDomain).toList();
    }

    @Override
    public List<FileMetadata> findByUsageAndBizType(FileUsage usage, String bizType) {
        QueryWrapper wrapper = QueryWrapper.create();
        if (usage != null) wrapper.and(FILE_METADATA_DO.USAGE.eq(usage.name()));
        if (bizType != null) wrapper.and(FILE_METADATA_DO.BIZ_TYPE.eq(bizType));
        List<FileMetadataDO> list = mapper.selectListByQuery(wrapper);
        return list.stream().map(converter::toDomain).toList();
    }

    @Override
    public List<FileMetadata> findExpiredBefore(LocalDateTime before) {
        if (before == null) return List.of();
        List<FileMetadataDO> list = mapper.selectListByQuery(
            QueryWrapper.create()
                .where(FILE_METADATA_DO.EXPIRES_AT.lt(before))
                .and(FILE_METADATA_DO.STATUS.ne("DELETED"))
        );
        return list.stream().map(converter::toDomain).toList();
    }

    private void publishDomainEvents(FileMetadata file) {
        List<DomainEvent> events = file.getDomainEvents();
        if (events.isEmpty()) return;
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
        file.clearDomainEvents();
    }
}
