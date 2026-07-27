package com.example.iam.infrastructure.repository;

import com.example.iam.domain.authentication.aggregate.entity.LoginFailureRecord;
import com.example.iam.domain.authentication.aggregate.root.LoginLog;
import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.domain.authentication.repository.LoginLogRepository;
import com.example.iam.infrastructure.converter.LoginLogConverter;
import com.example.iam.infrastructure.entity.LoginFailureRecordDO;
import com.example.iam.infrastructure.entity.LoginLogDO;
import com.example.iam.infrastructure.mapper.LoginFailureRecordMapper;
import com.example.iam.infrastructure.mapper.LoginLogMapper;
import com.example.iam.types.LoginLogId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.example.iam.infrastructure.entity.table.LoginFailureRecordDOTableDef.LOGIN_FAILURE_RECORD_DO;
import static com.example.iam.infrastructure.entity.table.LoginLogDOTableDef.LOGIN_LOG_DO;

/**
 * 登录日志聚合根仓储实现。
 *
 * <p>负责 {@link LoginLog}+子实体 {@link LoginFailureRecord} 的持久化操作。
 * 登录日志为追加型聚合,save 方法支持新增/追加失败记录场景。
 *
 * <p>{@link #findRecentFailures} 与 {@link #countRecentFailures} 用于风控判断:
 * 例如"近 5 分钟失败次数是否超过阈值"。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class LoginLogRepositoryImpl implements LoginLogRepository {

    private final LoginLogMapper loginLogMapper;
    private final LoginFailureRecordMapper failureRecordMapper;
    private final LoginLogConverter converter;
    private final DomainEventPublisher eventPublisher;

    @Override
    public Optional<LoginLog> load(LoginLogId id) {
        if (id == null) {
            return Optional.empty();
        }
        LoginLogDO logDO = loginLogMapper.selectOneById(id.value());
        if (logDO == null) {
            return Optional.empty();
        }
        List<LoginFailureRecordDO> recordDOs = failureRecordMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(LOGIN_FAILURE_RECORD_DO.LOGIN_LOG_ID.eq(id.value()))
                        .orderBy(LOGIN_FAILURE_RECORD_DO.FAILURE_TIME.asc())
        );
        return Optional.ofNullable(converter.toDomain(logDO, recordDOs));
    }

    @Override
    public void save(LoginLog loginLog) {
        if (loginLog == null) {
            throw new IllegalArgumentException("登录日志不能为空");
        }
        LoginLogDO logDO = converter.toDO(loginLog);
        boolean isInsert = loginLogMapper.selectOneById(loginLog.id().value()) == null;
        if (isInsert) {
            loginLogMapper.insert(logDO);
            log.debug("新增登录日志: logId={}, loginName={}, success={}",
                    loginLog.id(), loginLog.loginName(), loginLog.isSuccess());
        } else {
            loginLogMapper.update(logDO);
            log.debug("更新登录日志: logId={}, version={}", loginLog.id(), loginLog.version());
        }
        saveFailureRecords(loginLog);
        eventPublisher.publishFor(loginLog);
    }

    @Override
    public void delete(LoginLog loginLog) {
        if (loginLog == null) {
            return;
        }
        failureRecordMapper.deleteByQuery(
                QueryWrapper.create()
                        .where(LOGIN_FAILURE_RECORD_DO.LOGIN_LOG_ID.eq(loginLog.id().value()))
        );
        LoginLogDO logDO = loginLogMapper.selectOneById(loginLog.id().value());
        if (logDO != null) {
            loginLogMapper.delete(logDO);
        }
        log.debug("删除登录日志: logId={}", loginLog.id());
    }

    @Override
    public void deleteById(LoginLogId id) {
        if (id == null) {
            return;
        }
        failureRecordMapper.deleteByQuery(
                QueryWrapper.create().where(LOGIN_FAILURE_RECORD_DO.LOGIN_LOG_ID.eq(id.value()))
        );
        loginLogMapper.deleteById(id.value());
        log.debug("根据ID删除登录日志: logId={}", id);
    }

    @Override
    public List<LoginLog> loadAll() {
        List<LoginLogDO> logDOs = loginLogMapper.selectAll();
        return logDOs.stream()
                .map(logDO -> {
                    List<LoginFailureRecordDO> recordDOs = failureRecordMapper.selectListByQuery(
                            QueryWrapper.create()
                                    .where(LOGIN_FAILURE_RECORD_DO.LOGIN_LOG_ID.eq(logDO.getId()))
                                    .orderBy(LOGIN_FAILURE_RECORD_DO.FAILURE_TIME.asc())
                    );
                    return converter.toDomain(logDO, recordDOs);
                })
                .toList();
    }

    @Override
    public void streamByAppId(LoginLogId id, Consumer<AggregateRoot<LoginLogId>> processor) {
        if (id == null || processor == null) {
            return;
        }
        load(id).ifPresent(processor);
    }

    @Override
    public List<LoginLog> findRecentFailures(Long userId, ChannelType channelType, LocalDateTime since) {
        if (userId == null || channelType == null || since == null) {
            return List.of();
        }
        List<LoginLogDO> logDOs = loginLogMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(LOGIN_LOG_DO.USER_ID.eq(userId))
                        .and(LOGIN_LOG_DO.CHANNEL_TYPE.eq(channelType.name()))
                        .and(LOGIN_LOG_DO.SUCCESS.eq(false))
                        .and(LOGIN_LOG_DO.LOGIN_TIME.ge(since))
                        .orderBy(LOGIN_LOG_DO.LOGIN_TIME.desc())
        );
        return loadWithFailureRecords(logDOs);
    }

    @Override
    public List<LoginLog> findRecentFailuresByLoginName(String loginName, ChannelType channelType, LocalDateTime since) {
        if (loginName == null || channelType == null || since == null) {
            return List.of();
        }
        List<LoginLogDO> logDOs = loginLogMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(LOGIN_LOG_DO.LOGIN_NAME.eq(loginName))
                        .and(LOGIN_LOG_DO.CHANNEL_TYPE.eq(channelType.name()))
                        .and(LOGIN_LOG_DO.SUCCESS.eq(false))
                        .and(LOGIN_LOG_DO.LOGIN_TIME.ge(since))
                        .orderBy(LOGIN_LOG_DO.LOGIN_TIME.desc())
        );
        return loadWithFailureRecords(logDOs);
    }

    @Override
    public int countRecentFailures(Long userId, ChannelType channelType, LocalDateTime since) {
        if (userId == null || channelType == null || since == null) {
            return 0;
        }
        return (int) loginLogMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(LOGIN_LOG_DO.USER_ID.eq(userId))
                        .and(LOGIN_LOG_DO.CHANNEL_TYPE.eq(channelType.name()))
                        .and(LOGIN_LOG_DO.SUCCESS.eq(false))
                        .and(LOGIN_LOG_DO.LOGIN_TIME.ge(since))
        );
    }

    @Override
    public Optional<LoginLog> findLatestByUser(Long userId, ChannelType channelType) {
        if (userId == null || channelType == null) {
            return Optional.empty();
        }
        LoginLogDO logDO = loginLogMapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(LOGIN_LOG_DO.USER_ID.eq(userId))
                        .and(LOGIN_LOG_DO.CHANNEL_TYPE.eq(channelType.name()))
                        .orderBy(LOGIN_LOG_DO.LOGIN_TIME.desc())
                        .limit(1)
        );
        if (logDO == null) {
            return Optional.empty();
        }
        List<LoginFailureRecordDO> recordDOs = failureRecordMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(LOGIN_FAILURE_RECORD_DO.LOGIN_LOG_ID.eq(logDO.getId()))
                        .orderBy(LOGIN_FAILURE_RECORD_DO.FAILURE_TIME.asc())
        );
        return Optional.ofNullable(converter.toDomain(logDO, recordDOs));
    }

    /**
     * 批量加载登录日志及其失败记录。
     */
    private List<LoginLog> loadWithFailureRecords(List<LoginLogDO> logDOs) {
        if (logDOs == null || logDOs.isEmpty()) {
            return List.of();
        }
        return logDOs.stream()
                .map(logDO -> {
                    List<LoginFailureRecordDO> recordDOs = failureRecordMapper.selectListByQuery(
                            QueryWrapper.create()
                                    .where(LOGIN_FAILURE_RECORD_DO.LOGIN_LOG_ID.eq(logDO.getId()))
                                    .orderBy(LOGIN_FAILURE_RECORD_DO.FAILURE_TIME.asc())
                    );
                    return converter.toDomain(logDO, recordDOs);
                })
                .toList();
    }

    /**
     * 保存失败记录(追加模式:仅新增领域对象中尚未持久化的记录)。
     */
    private void saveFailureRecords(LoginLog loginLog) {
        List<LoginFailureRecord> records = loginLog.failureRecords();
        if (records.isEmpty()) {
            return;
        }
        for (LoginFailureRecord record : records) {
            LoginFailureRecordDO existing = failureRecordMapper.selectOneById(record.id().value());
            if (existing != null) {
                continue;
            }
            LoginFailureRecordDO recordDO = converter.toRecordDO(record);
            recordDO.setLoginLogId(loginLog.id().value());
            failureRecordMapper.insert(recordDO);
        }
    }
}
