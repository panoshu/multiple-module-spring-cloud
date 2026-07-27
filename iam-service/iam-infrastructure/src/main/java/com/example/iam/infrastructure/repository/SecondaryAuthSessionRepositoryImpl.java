package com.example.iam.infrastructure.repository;

import com.example.iam.domain.authentication.aggregate.root.SecondaryAuthSession;
import com.example.iam.domain.authentication.aggregate.valueobject.SecondaryAuthStatus;
import com.example.iam.domain.authentication.repository.SecondaryAuthSessionRepository;
import com.example.iam.infrastructure.converter.SecondaryAuthSessionConverter;
import com.example.iam.infrastructure.entity.SecondaryAuthSessionDO;
import com.example.iam.infrastructure.mapper.SecondaryAuthSessionMapper;
import com.example.iam.types.SecondaryAuthSessionId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.example.iam.infrastructure.entity.table.SecondaryAuthSessionDOTableDef.SECONDARY_AUTH_SESSION_DO;

/**
 * 二次授权会话聚合根仓储实现。
 *
 * <p>负责 {@link SecondaryAuthSession} 的持久化操作。
 *
 * <p>权限快照 {@code permissionSnapshot} 以 JSON 字符串存储,通过 Converter 完成序列化。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SecondaryAuthSessionRepositoryImpl implements SecondaryAuthSessionRepository {

    private final SecondaryAuthSessionMapper sessionMapper;
    private final SecondaryAuthSessionConverter converter;
    private final DomainEventPublisher eventPublisher;

    @Override
    public Optional<SecondaryAuthSession> load(SecondaryAuthSessionId id) {
        if (id == null) {
            return Optional.empty();
        }
        SecondaryAuthSessionDO sessionDO = sessionMapper.selectOneById(id.value());
        return Optional.ofNullable(converter.toDomain(sessionDO));
    }

    @Override
    public void save(SecondaryAuthSession session) {
        if (session == null) {
            throw new IllegalArgumentException("二次授权会话不能为空");
        }
        SecondaryAuthSessionDO sessionDO = converter.toDO(session);
        boolean isInsert = sessionMapper.selectOneById(session.id().value()) == null;
        if (isInsert) {
            sessionMapper.insert(sessionDO);
            log.debug("新增二次授权会话: sessionId={}, tellerId={}, status={}",
                    session.id(), session.tellerId(), session.status());
        } else {
            sessionMapper.update(sessionDO);
            log.debug("更新二次授权会话: sessionId={}, version={}", session.id(), session.version());
        }
        eventPublisher.publishFor(session);
    }

    @Override
    public void delete(SecondaryAuthSession session) {
        if (session == null) {
            return;
        }
        SecondaryAuthSessionDO sessionDO = sessionMapper.selectOneById(session.id().value());
        if (sessionDO != null) {
            sessionMapper.delete(sessionDO);
        }
        log.debug("删除二次授权会话: sessionId={}", session.id());
    }

    @Override
    public void deleteById(SecondaryAuthSessionId id) {
        if (id == null) {
            return;
        }
        sessionMapper.deleteById(id.value());
        log.debug("根据ID删除二次授权会话: sessionId={}", id);
    }

    @Override
    public List<SecondaryAuthSession> loadAll() {
        List<SecondaryAuthSessionDO> sessionDOs = sessionMapper.selectAll();
        return sessionDOs.stream()
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
    public Optional<SecondaryAuthSession> findEffectiveByTeller(Long tellerId) {
        if (tellerId == null) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now();
        SecondaryAuthSessionDO sessionDO = sessionMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(SECONDARY_AUTH_SESSION_DO.TELLER_ID.eq(tellerId))
                        .and(SECONDARY_AUTH_SESSION_DO.STATUS.eq(SecondaryAuthStatus.AUTHORIZED.name()))
                        .and(SECONDARY_AUTH_SESSION_DO.EXPIRE_AT.ge(now))
        );
        return Optional.ofNullable(converter.toDomain(sessionDO));
    }

    @Override
    public Optional<SecondaryAuthSession> findPendingByTeller(Long tellerId) {
        if (tellerId == null) {
            return Optional.empty();
        }
        SecondaryAuthSessionDO sessionDO = sessionMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(SECONDARY_AUTH_SESSION_DO.TELLER_ID.eq(tellerId))
                        .and(SECONDARY_AUTH_SESSION_DO.STATUS.eq(SecondaryAuthStatus.PENDING.name()))
        );
        return Optional.ofNullable(converter.toDomain(sessionDO));
    }
}
