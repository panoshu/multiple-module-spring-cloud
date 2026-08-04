package com.example.file.infrastructure.repository;

import com.example.file.domain.model.aggregate.root.FileAccessLog;
import com.example.file.domain.model.aggregate.valueobject.FileAccessAction;
import com.example.file.domain.repository.FileAccessLogRepository;
import com.example.file.infrastructure.converter.FileAccessLogConverter;
import com.example.file.infrastructure.entity.FileAccessLogDO;
import com.example.file.infrastructure.mapper.FileAccessLogMapper;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.FileAccessLogId;
import com.example.shared.identifier.id.FileId;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.example.file.infrastructure.entity.table.FileAccessLogDOTableDef.FILE_ACCESS_LOG_DO;

/**
 * 文件访问流水仓储实现
 * <p>
 * 流水记录设计为不可变（仅在写入失败时可调用 markFail 标记失败），
 * 因此 {@link #delete(FileAccessLog)} 与 {@link #deleteById(FileAccessLogId)} 抛出
 * {@link UnsupportedOperationException} 以保护审计完整性。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class FileAccessLogRepositoryImpl implements FileAccessLogRepository {

  private final FileAccessLogMapper mapper;
  private final FileAccessLogConverter converter;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  public Optional<FileAccessLog> load(FileAccessLogId id) {
    if (id == null) return Optional.empty();
    FileAccessLogDO aDo = mapper.selectOneById(id.value());
    return Optional.ofNullable(aDo).map(converter::toDomain);
  }

  @Override
  public void save(FileAccessLog accessLog) {
    if (accessLog == null) throw new IllegalArgumentException("log 不能为空");
    FileAccessLogDO aDo = converter.toDO(accessLog);
    // 流水记录只新增不更新（业务语义上每次 APPLY/ACCESS 都产生新记录）
    mapper.insert(aDo);
    log.debug("新增文件访问流水: logId={}, action={}, fileId={}",
      accessLog.id(), accessLog.action(), accessLog.fileId());
    publishDomainEvents(accessLog);
  }

  @Override
  public void delete(FileAccessLog aggregateRoot) {
    throw new UnsupportedOperationException(
      "FileAccessLog 为审计流水，不可删除（logId=" + aggregateRoot.id() + "）");
  }

  @Override
  public void deleteById(FileAccessLogId id) {
    throw new UnsupportedOperationException(
      "FileAccessLog 为审计流水，不可删除（logId=" + id + "）");
  }

  @Override
  public List<FileAccessLog> loadAll() {
    return mapper.selectAll().stream()
      .map(converter::toDomain)
      .toList();
  }

  @Override
  public void streamByAppId(FileAccessLogId id, Consumer<com.example.shared.domain.aggregate.root.AggregateRoot<FileAccessLogId>> processor) {
    if (id == null || processor == null) return;
    load(id).ifPresent(processor);
  }

  @Override
  public List<FileAccessLog> findByFileId(FileId fileId) {
    if (fileId == null) return List.of();
    List<FileAccessLogDO> list = mapper.selectListByQuery(
      QueryWrapper.create()
        .where(FILE_ACCESS_LOG_DO.FILE_ID.eq(fileId.value()))
        .orderBy(FILE_ACCESS_LOG_DO.OCCUR_AT.desc())
    );
    return list.stream().map(converter::toDomain).toList();
  }

  @Override
  public List<FileAccessLog> findByTokenHash(String tokenHash) {
    if (tokenHash == null || tokenHash.isBlank()) return List.of();
    List<FileAccessLogDO> list = mapper.selectListByQuery(
      QueryWrapper.create()
        .where(FILE_ACCESS_LOG_DO.TOKEN_HASH.eq(tokenHash))
        .orderBy(FILE_ACCESS_LOG_DO.OCCUR_AT.asc())
    );
    return list.stream().map(converter::toDomain).toList();
  }

  @Override
  public long countByActionAndTimeRange(FileAccessAction action, LocalDateTime from, LocalDateTime to) {
    if (action == null || from == null || to == null) return 0L;
    QueryWrapper wrapper = QueryWrapper.create()
      .where(FILE_ACCESS_LOG_DO.ACTION.eq(action.name()))
      .and(FILE_ACCESS_LOG_DO.OCCUR_AT.between(from, to));
    return mapper.selectCountByQuery(wrapper);
  }

  private void publishDomainEvents(FileAccessLog accessLog) {
    List<DomainEvent> events = accessLog.domainEvents();
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
    accessLog.clearDomainEvents();
  }
}
