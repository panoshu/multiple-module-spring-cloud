package com.example.iam.infrastructure.converter;

import com.example.iam.domain.authentication.aggregate.entity.LoginFailureRecord;
import com.example.iam.domain.authentication.aggregate.root.LoginLog;
import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.infrastructure.entity.LoginFailureRecordDO;
import com.example.iam.infrastructure.entity.LoginLogDO;
import com.example.iam.types.LoginFailureRecordId;
import com.example.iam.types.LoginLogId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LoginLogConverter} 单元测试。
 *
 * <p>覆盖 LoginLog/LoginFailureRecord 与 LoginLogDO/LoginFailureRecordDO 双向映射、
 * 子实体集合转换、null 输入处理。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@DisplayName("LoginLogConverter 转换器测试")
class LoginLogConverterTest {

    private final LoginLogConverter converter = Mappers.getMapper(LoginLogConverter.class);

    private static final Long LOG_ID_VALUE = 8001L;
    private static final Long USER_ID = 5001L;
    private static final String LOGIN_NAME = "alice";
    private static final ChannelType CHANNEL_TYPE = ChannelType.INTERNET;
    private static final LocalDateTime LOGIN_TIME = LocalDateTime.of(2026, 7, 2, 9, 30, 0);
    private static final String LOGIN_IP = "10.0.0.1";
    private static final String USER_AGENT = "Mozilla/5.0";
    private static final String OPERATOR = "U-SYSTEM";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 7, 2, 9, 30, 0);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 7, 2, 9, 30, 0);
    private static final long VERSION_VALUE = 0L;

    // 失败记录常量
    private static final Long RECORD_ID_VALUE = 9001L;
    private static final String REASON = "WRONG_PASSWORD";
    private static final String DETAIL = "密码错误,剩余 2 次";
    private static final LocalDateTime FAILURE_TIME = LocalDateTime.of(2026, 7, 2, 9, 30, 0);

    @Nested
    @DisplayName("toDO: LoginLog -> LoginLogDO")
    class ToDOTest {

        @Test
        @DisplayName("成功日志完整字段映射")
        void shouldMapSuccessLogToDO() {
            LoginLog log = buildSuccessLog();

            LoginLogDO logDO = converter.toDO(log);

            assertThat(logDO).isNotNull();
            assertThat(logDO.getId()).isEqualTo(LOG_ID_VALUE);
            assertThat(logDO.getUserId()).isEqualTo(USER_ID);
            assertThat(logDO.getLoginName()).isEqualTo(LOGIN_NAME);
            assertThat(logDO.getChannelType()).isEqualTo(CHANNEL_TYPE.name());
            assertThat(logDO.getSuccess()).isTrue();
            assertThat(logDO.getLoginTime()).isEqualTo(LOGIN_TIME);
            assertThat(logDO.getLoginIp()).isEqualTo(LOGIN_IP);
            assertThat(logDO.getUserAgent()).isEqualTo(USER_AGENT);
            assertThat(logDO.getCreatedBy()).isEqualTo(OPERATOR);
            assertThat(logDO.getUpdatedBy()).isEqualTo(OPERATOR);
            assertThat(logDO.getCreateTime()).isEqualTo(CREATED_AT);
            assertThat(logDO.getUpdateTime()).isEqualTo(UPDATED_AT);
            assertThat(logDO.getVersion()).isEqualTo((int) VERSION_VALUE);
            assertThat(logDO.getDeleted()).isFalse();
        }

        @Test
        @DisplayName("失败日志 success=false 正确映射")
        void shouldMapFailureLogToDO() {
            LoginLog log = buildFailureLog();

            LoginLogDO logDO = converter.toDO(log);

            assertThat(logDO.getSuccess()).isFalse();
        }
    }

    @Nested
    @DisplayName("toRecordDO: LoginFailureRecord -> LoginFailureRecordDO")
    class ToRecordDOTest {

        @Test
        @DisplayName("完整字段映射:loginLogId 字段被忽略")
        void shouldMapAllFieldsToRecordDO() {
            LoginFailureRecord record = buildFailureRecord();

            LoginFailureRecordDO recordDO = converter.toRecordDO(record);

            assertThat(recordDO).isNotNull();
            assertThat(recordDO.getId()).isEqualTo(RECORD_ID_VALUE);
            assertThat(recordDO.getLoginLogId()).isNull(); // @Mapping(target = "loginLogId", ignore = true)
            assertThat(recordDO.getReason()).isEqualTo(REASON);
            assertThat(recordDO.getDetail()).isEqualTo(DETAIL);
            assertThat(recordDO.getFailureTime()).isEqualTo(FAILURE_TIME);
            assertThat(recordDO.getCreatedBy()).isEqualTo(OPERATOR);
            assertThat(recordDO.getUpdatedBy()).isEqualTo(OPERATOR);
            assertThat(recordDO.getCreateTime()).isEqualTo(CREATED_AT);
            assertThat(recordDO.getUpdateTime()).isEqualTo(UPDATED_AT);
            assertThat(recordDO.getVersion()).isEqualTo((int) VERSION_VALUE);
            assertThat(recordDO.getDeleted()).isFalse();
        }
    }

    @Nested
    @DisplayName("toDomain: (LoginLogDO, List<LoginFailureRecordDO>) -> LoginLog")
    class ToDomainTest {

        @Test
        @DisplayName("成功日志完整字段映射:无失败记录")
        void shouldMapSuccessLogToDomain() {
            LoginLogDO logDO = buildSuccessLogDO();

            LoginLog log = converter.toDomain(logDO, null);

            assertThat(log).isNotNull();
            assertThat(log.id().value()).isEqualTo(LOG_ID_VALUE);
            assertThat(log.userId()).isEqualTo(USER_ID);
            assertThat(log.loginName()).isEqualTo(LOGIN_NAME);
            assertThat(log.channelType()).isEqualTo(CHANNEL_TYPE);
            assertThat(log.isSuccess()).isTrue();
            assertThat(log.loginTime()).isEqualTo(LOGIN_TIME);
            assertThat(log.loginIp()).isEqualTo(LOGIN_IP);
            assertThat(log.userAgent()).isEqualTo(USER_AGENT);
            assertThat(log.failureRecords()).isEmpty();
            assertThat(log.createdBy().value()).isEqualTo(OPERATOR);
            assertThat(log.updatedBy().value()).isEqualTo(OPERATOR);
            assertThat(log.createdAt()).isEqualTo(CREATED_AT);
            assertThat(log.updatedAt()).isEqualTo(UPDATED_AT);
            assertThat(log.version().value()).isEqualTo(VERSION_VALUE);
        }

        @Test
        @DisplayName("失败日志含子记录列表")
        void shouldMapFailureLogWithRecords() {
            LoginLogDO logDO = buildFailureLogDO();
            List<LoginFailureRecordDO> recordDOs = new ArrayList<>();
            recordDOs.add(buildRecordDO());

            LoginLog log = converter.toDomain(logDO, recordDOs);

            assertThat(log.isFailure()).isTrue();
            assertThat(log.failureRecords()).hasSize(1);
            LoginFailureRecord record = log.failureRecords().get(0);
            assertThat(record.id().value()).isEqualTo(RECORD_ID_VALUE);
            assertThat(record.reason()).isEqualTo(REASON);
            assertThat(record.detail()).isEqualTo(DETAIL);
            assertThat(record.failureTime()).isEqualTo(FAILURE_TIME);
        }

        @Test
        @DisplayName("logDO 为 null 时返回 null")
        void shouldReturnNullWhenLogDOIsNull() {
            assertThat(converter.toDomain(null, new ArrayList<>())).isNull();
        }

        @Test
        @DisplayName("success 字段为 null 时按 false 处理")
        void shouldTreatNullSuccessAsFalse() {
            LoginLogDO logDO = buildSuccessLogDO();
            logDO.setSuccess(null);

            LoginLog log = converter.toDomain(logDO, null);

            assertThat(log.isFailure()).isTrue();
        }
    }

    @Nested
    @DisplayName("toRecordDomain: LoginFailureRecordDO -> LoginFailureRecord")
    class ToRecordDomainTest {

        @Test
        @DisplayName("完整字段映射")
        void shouldMapRecordDOToDomain() {
            LoginFailureRecordDO recordDO = buildRecordDO();

            LoginFailureRecord record = converter.toRecordDomain(recordDO);

            assertThat(record).isNotNull();
            assertThat(record.id().value()).isEqualTo(RECORD_ID_VALUE);
            assertThat(record.reason()).isEqualTo(REASON);
            assertThat(record.detail()).isEqualTo(DETAIL);
            assertThat(record.failureTime()).isEqualTo(FAILURE_TIME);
            assertThat(record.createdBy().value()).isEqualTo(OPERATOR);
            assertThat(record.updatedBy().value()).isEqualTo(OPERATOR);
            assertThat(record.createdAt()).isEqualTo(CREATED_AT);
            assertThat(record.updatedAt()).isEqualTo(UPDATED_AT);
            assertThat(record.version().value()).isEqualTo(VERSION_VALUE);
        }

        @Test
        @DisplayName("DO 为 null 时返回 null")
        void shouldReturnNullWhenRecordDOIsNull() {
            assertThat(converter.toRecordDomain(null)).isNull();
        }
    }

    @Nested
    @DisplayName("枚举与 ID 类型转换")
    class TypeConversionTest {

        @Test
        @DisplayName("toChannelType: null 返回 null")
        void shouldReturnNullChannelTypeForNullString() {
            assertThat(converter.toChannelType(null)).isNull();
        }

        @Test
        @DisplayName("toUserNo: null 返回 null")
        void shouldReturnNullUserNoForNullString() {
            assertThat(converter.toUserNo(null)).isNull();
        }

        @Test
        @DisplayName("toVersion: null 返回 null")
        void shouldReturnNullVersionForNullInteger() {
            assertThat(converter.toVersion(null)).isNull();
        }
    }

    @Nested
    @DisplayName("双向转换一致性")
    class RoundTripTest {

        @Test
        @DisplayName("成功日志 toDomain(toDO(log)) 关键字段一致")
        void shouldPreserveSuccessLogFieldsThroughRoundTrip() {
            LoginLog original = buildSuccessLog();

            LoginLogDO intermediateDO = converter.toDO(original);
            LoginLog rebuilt = converter.toDomain(intermediateDO, null);

            assertThat(rebuilt.id()).isEqualTo(original.id());
            assertThat(rebuilt.userId()).isEqualTo(original.userId());
            assertThat(rebuilt.loginName()).isEqualTo(original.loginName());
            assertThat(rebuilt.channelType()).isEqualTo(original.channelType());
            assertThat(rebuilt.isSuccess()).isEqualTo(original.isSuccess());
            assertThat(rebuilt.loginTime()).isEqualTo(original.loginTime());
            assertThat(rebuilt.loginIp()).isEqualTo(original.loginIp());
            assertThat(rebuilt.userAgent()).isEqualTo(original.userAgent());
            assertThat(rebuilt.createdBy()).isEqualTo(original.createdBy());
            assertThat(rebuilt.updatedBy()).isEqualTo(original.updatedBy());
            assertThat(rebuilt.version()).isEqualTo(original.version());
        }

        @Test
        @DisplayName("失败记录 toRecordDomain(toRecordDO(record)) 关键字段一致")
        void shouldPreserveRecordFieldsThroughRoundTrip() {
            LoginFailureRecord original = buildFailureRecord();

            LoginFailureRecordDO intermediateDO = converter.toRecordDO(original);
            LoginFailureRecord rebuilt = converter.toRecordDomain(intermediateDO);

            assertThat(rebuilt.id()).isEqualTo(original.id());
            assertThat(rebuilt.reason()).isEqualTo(original.reason());
            assertThat(rebuilt.detail()).isEqualTo(original.detail());
            assertThat(rebuilt.failureTime()).isEqualTo(original.failureTime());
            assertThat(rebuilt.version()).isEqualTo(original.version());
        }
    }

    private LoginLog buildSuccessLog() {
        return LoginLog.reconstitute(
                LoginLogId.of(LOG_ID_VALUE), USER_ID, LOGIN_NAME, CHANNEL_TYPE,
                true, LOGIN_TIME, LOGIN_IP, USER_AGENT,
                List.of(),
                UserNo.of(OPERATOR), UserNo.of(OPERATOR),
                CREATED_AT, UPDATED_AT, Version.of(VERSION_VALUE));
    }

    private LoginLog buildFailureLog() {
        return LoginLog.reconstitute(
                LoginLogId.of(LOG_ID_VALUE), USER_ID, LOGIN_NAME, CHANNEL_TYPE,
                false, LOGIN_TIME, LOGIN_IP, USER_AGENT,
                List.of(buildFailureRecord()),
                UserNo.of(OPERATOR), UserNo.of(OPERATOR),
                CREATED_AT, UPDATED_AT, Version.of(VERSION_VALUE));
    }

    private LoginFailureRecord buildFailureRecord() {
        return new LoginFailureRecord(
                LoginFailureRecordId.of(RECORD_ID_VALUE),
                REASON, DETAIL, FAILURE_TIME,
                UserNo.of(OPERATOR), UserNo.of(OPERATOR),
                CREATED_AT, UPDATED_AT, Version.of(VERSION_VALUE));
    }

    private LoginLogDO buildSuccessLogDO() {
        LoginLogDO logDO = new LoginLogDO();
        logDO.setId(LOG_ID_VALUE);
        logDO.setUserId(USER_ID);
        logDO.setLoginName(LOGIN_NAME);
        logDO.setChannelType(CHANNEL_TYPE.name());
        logDO.setSuccess(true);
        logDO.setLoginTime(LOGIN_TIME);
        logDO.setLoginIp(LOGIN_IP);
        logDO.setUserAgent(USER_AGENT);
        logDO.setCreatedBy(OPERATOR);
        logDO.setUpdatedBy(OPERATOR);
        logDO.setCreateTime(CREATED_AT);
        logDO.setUpdateTime(UPDATED_AT);
        logDO.setVersion((int) VERSION_VALUE);
        logDO.setDeleted(false);
        return logDO;
    }

    private LoginLogDO buildFailureLogDO() {
        LoginLogDO logDO = buildSuccessLogDO();
        logDO.setSuccess(false);
        return logDO;
    }

    private LoginFailureRecordDO buildRecordDO() {
        LoginFailureRecordDO recordDO = new LoginFailureRecordDO();
        recordDO.setId(RECORD_ID_VALUE);
        recordDO.setLoginLogId(LOG_ID_VALUE);
        recordDO.setReason(REASON);
        recordDO.setDetail(DETAIL);
        recordDO.setFailureTime(FAILURE_TIME);
        recordDO.setCreatedBy(OPERATOR);
        recordDO.setUpdatedBy(OPERATOR);
        recordDO.setCreateTime(CREATED_AT);
        recordDO.setUpdateTime(UPDATED_AT);
        recordDO.setVersion((int) VERSION_VALUE);
        recordDO.setDeleted(false);
        return recordDO;
    }
}
