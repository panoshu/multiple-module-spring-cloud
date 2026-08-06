package com.pension.permission.infrastructure.channel.repository;

import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.identifier.id.UserNo;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import com.pension.permission.domain.channel.aggregate.SecondaryAuthSession;
import com.pension.permission.domain.channel.enumeration.SecondaryAuthStatus;
import com.pension.permission.domain.channel.repository.SecondaryAuthSessionRepository;
import com.pension.permission.infrastructure.channel.converter.SecondaryAuthSessionConverter;
import com.pension.permission.infrastructure.channel.entity.SecondaryAuthSessionDO;
import com.pension.permission.infrastructure.channel.mapper.SecondaryAuthSessionMapper;
import com.pension.permission.types.SecondaryAuthSessionId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.pension.permission.infrastructure.channel.entity.table.SecondaryAuthSessionDOTableDef.SECONDARY_AUTH_SESSION_DO;

/**
 * 二次授权会话仓储实现.
 *
 * <p>负责 {@link SecondaryAuthSession} 聚合根的持久化操作。领域事件不在 Repository 发布，
 * 由 {@code ApplicationService} 在编排时统一发布。</p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SecondaryAuthSessionRepositoryImpl implements SecondaryAuthSessionRepository {

  private final SecondaryAuthSessionMapper secondaryAuthSessionMapper;
  private final SecondaryAuthSessionConverter converter;

  @Override
  public Optional<SecondaryAuthSession> load(SecondaryAuthSessionId id) {
    if (id == null) {
      return Optional.empty();
    }
    SecondaryAuthSessionDO doObj = secondaryAuthSessionMapper.selectOneById(id.value());
    return Optional.ofNullable(converter.toDomain(doObj));
  }

  @Override
  public void save(SecondaryAuthSession session) {
    if (session == null) {
      throw new IllegalArgumentException("SecondaryAuthSession 不能为空");
    }

    SecondaryAuthSessionDO doObj = converter.toDO(session);
    SecondaryAuthSessionDO existing = secondaryAuthSessionMapper.selectOneById(doObj.getId());

    if (existing == null) {
      secondaryAuthSessionMapper.insert(doObj);
      log.debug("新增 SecondaryAuthSession: sessionId={}", session.id());
    } else {
      doObj.setVersion(existing.getVersion());
      secondaryAuthSessionMapper.update(doObj);
      log.debug("更新 SecondaryAuthSession: sessionId={}, version={}", session.id(), session.version());
    }
  }

  @Override
  public void delete(SecondaryAuthSession aggregateRoot) {
    if (aggregateRoot == null) {
      return;
    }
    secondaryAuthSessionMapper.deleteById(aggregateRoot.id().value());
    log.debug("删除 SecondaryAuthSession: sessionId={}", aggregateRoot.id());
  }

  @Override
  public void deleteById(SecondaryAuthSessionId id) {
    if (id == null) {
      return;
    }
    secondaryAuthSessionMapper.deleteById(id.value());
    log.debug("根据 ID 删除 SecondaryAuthSession: sessionId={}", id);
  }

  @Override
  public List<SecondaryAuthSession> loadAll() {
    List<SecondaryAuthSessionDO> doList = secondaryAuthSessionMapper.selectAll();
    return doList.stream()
      .map(converter::toDomain)
      .toList();
  }

  @Override
  public void streamByAppId(SecondaryAuthSessionId id, Consumer<AggregateRoot<SecondaryAuthSessionId>> processor) {
    if (id == null || processor == null) {
      return;
    }
    load(id).ifPresent(processor);
  }

  @Override
  public Optional<SecondaryAuthSession> findActiveByTeller(UserNo tellerAccountId) {
    if (tellerAccountId == null) {
      return Optional.empty();
    }

    SecondaryAuthSessionDO doObj = secondaryAuthSessionMapper.selectOneByQuery(
      QueryWrapper.create()
        .where(SECONDARY_AUTH_SESSION_DO.TELLER_ACCOUNT_ID.eq(tellerAccountId.value()))
        .and(SECONDARY_AUTH_SESSION_DO.STATUS.in(
          SecondaryAuthStatus.PENDING.name(),
          SecondaryAuthStatus.AUTHORIZED.name()
        ))
        .orderBy(SECONDARY_AUTH_SESSION_DO.CREATE_TIME.desc())
    );

    return Optional.ofNullable(converter.toDomain(doObj));
  }

  @Override
  public List<SecondaryAuthSession> findAuthorizedByApprover(UserNo approverAccountId) {
    if (approverAccountId == null) {
      return List.of();
    }

    List<SecondaryAuthSessionDO> doList = secondaryAuthSessionMapper.selectListByQuery(
      QueryWrapper.create()
        .where(SECONDARY_AUTH_SESSION_DO.APPROVER_ACCOUNT_ID.eq(approverAccountId.value()))
        .and(SECONDARY_AUTH_SESSION_DO.STATUS.eq(SecondaryAuthStatus.AUTHORIZED.name()))
        .orderBy(SECONDARY_AUTH_SESSION_DO.CREATE_TIME.desc())
    );

    return doList.stream()
      .map(converter::toDomain)
      .toList();
  }

  @Override
  public List<SecondaryAuthSession> findPendingByApprover(UserNo approverAccountId) {
    if (approverAccountId == null) {
      return List.of();
    }

    List<SecondaryAuthSessionDO> doList = secondaryAuthSessionMapper.selectListByQuery(
      QueryWrapper.create()
        .where(SECONDARY_AUTH_SESSION_DO.APPROVER_ACCOUNT_ID.eq(approverAccountId.value()))
        .and(SECONDARY_AUTH_SESSION_DO.STATUS.eq(SecondaryAuthStatus.PENDING.name()))
        .orderBy(SECONDARY_AUTH_SESSION_DO.CREATE_TIME.desc())
    );

    return doList.stream()
      .map(converter::toDomain)
      .toList();
  }

  @Override
  public List<SecondaryAuthSession> findTimeoutSessions() {
    LocalDateTime now = LocalDateTime.now();

    // PENDING 且 pendingExpiresAt < now
    QueryCondition pendingTimeout = SECONDARY_AUTH_SESSION_DO.STATUS.eq(SecondaryAuthStatus.PENDING.name())
      .and(SECONDARY_AUTH_SESSION_DO.PENDING_EXPIRES_AT.lt(now));

    // AUTHORIZED 且 (expiresAt < now 或 snapshotExpiresAt < now)
    QueryCondition authorizedTimeout = SECONDARY_AUTH_SESSION_DO.STATUS.eq(SecondaryAuthStatus.AUTHORIZED.name())
      .and(
        SECONDARY_AUTH_SESSION_DO.EXPIRES_AT.lt(now)
          .or(SECONDARY_AUTH_SESSION_DO.SNAPSHOT_EXPIRES_AT.lt(now))
      );

    List<SecondaryAuthSessionDO> doList = secondaryAuthSessionMapper.selectListByQuery(
      QueryWrapper.create()
        .where(pendingTimeout.or(authorizedTimeout))
        .orderBy(SECONDARY_AUTH_SESSION_DO.CREATE_TIME.asc())
    );

    return doList.stream()
      .map(converter::toDomain)
      .toList();
  }
}
