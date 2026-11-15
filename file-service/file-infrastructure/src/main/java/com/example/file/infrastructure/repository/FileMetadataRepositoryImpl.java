package com.example.file.infrastructure.repository;

import com.example.file.domain.model.storage.aggregate.FileMetadata;
import com.example.file.domain.model.storage.valueobject.FileUsage;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.file.infrastructure.converter.FileMetadataConverter;
import com.example.file.infrastructure.entity.FileMetadataDO;
import com.example.file.infrastructure.mapper.FileMetadataMapper;
import com.example.shared.domain.event.DomainEvent;
import com.example.file.types.FileId;
import com.example.core.types.UserNo;
import com.example.shared.exception.CommonError;
import com.example.shared.exception.SystemException;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
    if (id == null) {
      return Optional.empty();
    }
    FileMetadataDO aDo = mapper.selectOneById(id.value());
    return Optional.ofNullable(aDo).map(converter::toDomain);
  }

  @Override
  public void save(FileMetadata file) {
    if (file == null) {
      throw new IllegalArgumentException("file 不能为空");
    }
    FileMetadataDO aDo = converter.toDO(file);
    FileMetadataDO existing = mapper.selectOneById(aDo.getId());
    if (existing == null) {
      mapper.insert(aDo);
      log.debug("新增文件元数据: fileId={}", file.id());
    } else {
      // 领域方法（如 completeUpload）可能已调用 markUpdated 提前递增版本；
      // MyBatis-Flex 用 DO.version 作为 UPDATE 的 WHERE 条件，必须回退为数据库当前版本，
      // 否则 WHERE version = 旧值+1 匹配 0 行且静默丢失更新（文件永远停在 PENDING_UPLOAD）。
      int persistedVersion = existing.getVersion() != null ? existing.getVersion() : 0;
      long domainVersion = file.version() != null ? file.version().value() : 0L;
      if (domainVersion != persistedVersion && domainVersion != persistedVersion + 1L) {
        throw new SystemException(CommonError.CONCURRENCY_ERROR)
          .withLogDetail("文件元数据版本冲突: fileId=" + file.id()
            + ", domainVersion=" + domainVersion + ", persistedVersion=" + persistedVersion);
      }
      aDo.setVersion(persistedVersion);
      if (mapper.update(aDo) != 1) {
        throw new SystemException(CommonError.CONCURRENCY_ERROR)
          .withLogDetail("文件元数据更新失败（版本冲突）: fileId=" + file.id());
      }
      if (domainVersion == persistedVersion) {
        file.syncVersionAfterUpdate();
      }
      log.debug("更新文件元数据: fileId={}, version={}", file.id(), file.version());
    }
    publishDomainEvents(file);
  }

  @Override
  public void delete(FileMetadata file) {
    if (file == null) {
      return;
    }
    file.markDeleted(UserNo.of(file.updatedBy() != null ? file.updatedBy() : file.createdBy()));
    save(file);
  }

  @Override
  public void deleteById(FileId id) {
    if (id == null) {
      return;
    }
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
  public List<FileMetadata> findByBusinessBatchId(String businessBatchId) {
    if (businessBatchId == null) {
      return List.of();
    }
    List<FileMetadataDO> list = mapper.selectListByQuery(
      QueryWrapper.create().where(FILE_METADATA_DO.BUSINESS_BATCH_ID.eq(businessBatchId))
    );
    return list.stream().map(converter::toDomain).toList();
  }

  @Override
  public List<FileMetadata> findByUsageAndBizType(FileUsage usage, String bizType) {
    QueryWrapper wrapper = QueryWrapper.create();
    if (usage != null) {
      wrapper.and(FILE_METADATA_DO.USAGE.eq(usage.name()));
    }
    if (bizType != null) {
      wrapper.and(FILE_METADATA_DO.BIZ_TYPE.eq(bizType));
    }
    List<FileMetadataDO> list = mapper.selectListByQuery(wrapper);
    return list.stream().map(converter::toDomain).toList();
  }

  @Override
  public List<FileMetadata> findExpiredBefore(LocalDateTime before) {
    if (before == null) {
      return List.of();
    }
    List<FileMetadataDO> list = mapper.selectListByQuery(
      QueryWrapper.create()
        .where(FILE_METADATA_DO.EXPIRES_AT.lt(before))
        .and(FILE_METADATA_DO.STATUS.ne("DELETED"))
    );
    return list.stream().map(converter::toDomain).toList();
  }

  private void publishDomainEvents(FileMetadata file) {
    List<DomainEvent> events = file.domainEvents();
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
    file.clearDomainEvents();
  }
}
