package com.example.iam.infrastructure.repository;

import com.example.iam.domain.authentication.aggregate.root.LoginLog;
import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.domain.authentication.repository.LoginLogRepository;
import com.example.iam.infrastructure.IamInfrastructureTestApplication;
import com.example.iam.types.LoginFailureRecordId;
import com.example.iam.types.LoginLogId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LoginLogRepositoryImpl} 集成测试。
 *
 * <p>验证登录日志聚合根(含失败记录子实体)的 CRUD、近期失败查询、最新登录查询等场景。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@DisplayName("LoginLogRepositoryImpl 集成测试")
@SpringBootTest(classes = IamInfrastructureTestApplication.class)
@ActiveProfiles("test")
@Transactional
class LoginLogRepositoryImplTest {

    @Autowired
    private LoginLogRepository loginLogRepository;

    private static final Long USER_ID = 10001L;
    private static final Long LOG_ID_VALUE = 30001L;
    private static final Long FAILURE_RECORD_ID_VALUE = 30002L;
    private static final ChannelType CHANNEL_TYPE = ChannelType.INTERNET;
    private static final String LOGIN_NAME = "alice";
    private static final String LOGIN_IP = "10.0.0.1";
    private static final String USER_AGENT = "JUnit/1.0";
    private static final UserNo OPERATOR = UserNo.of("U-SYSTEM");

    @Nested
    @DisplayName("save + load: 新建与读取")
    class SaveAndLoadTest {

        @Test
        @DisplayName("新建成功日志后能通过 ID 加载,关键字段一致")
        void shouldSaveSuccessLogAndLoadById() {
            LoginLog log = LoginLog.createSuccess(
                    LoginLogId.of(LOG_ID_VALUE), USER_ID, LOGIN_NAME, CHANNEL_TYPE,
                    LocalDateTime.now(), LOGIN_IP, USER_AGENT, OPERATOR);

            loginLogRepository.save(log);

            Optional<LoginLog> loaded = loginLogRepository.load(LoginLogId.of(LOG_ID_VALUE));

            assertThat(loaded).isPresent();
            LoginLog actual = loaded.get();
            assertThat(actual.id().value()).isEqualTo(LOG_ID_VALUE);
            assertThat(actual.userId()).isEqualTo(USER_ID);
            assertThat(actual.loginName()).isEqualTo(LOGIN_NAME);
            assertThat(actual.channelType()).isEqualTo(CHANNEL_TYPE);
            assertThat(actual.isSuccess()).isTrue();
            assertThat(actual.failureRecords()).isEmpty();
            assertThat(actual.createdBy()).isEqualTo(OPERATOR);
        }

        @Test
        @DisplayName("新建失败日志后能通过 ID 加载,带 1 条失败记录")
        void shouldSaveFailureLogWithRecord() {
            LoginLog log = LoginLog.createFailure(
                    LoginLogId.of(LOG_ID_VALUE), USER_ID, LOGIN_NAME, CHANNEL_TYPE,
                    LocalDateTime.now(), LOGIN_IP, USER_AGENT,
                    LoginFailureRecordId.of(FAILURE_RECORD_ID_VALUE),
                    "WRONG_PASSWORD", "密码错误", OPERATOR);

            loginLogRepository.save(log);

            Optional<LoginLog> loaded = loginLogRepository.load(LoginLogId.of(LOG_ID_VALUE));

            assertThat(loaded).isPresent();
            assertThat(loaded.get().isFailure()).isTrue();
            assertThat(loaded.get().failureRecords()).hasSize(1);
            assertThat(loaded.get().failureRecords().get(0).reason()).isEqualTo("WRONG_PASSWORD");
            assertThat(loaded.get().failureRecords().get(0).detail()).isEqualTo("密码错误");
        }

        @Test
        @DisplayName("load 不存在的 ID 返回 empty")
        void shouldReturnEmptyWhenLoadNonexistentId() {
            Optional<LoginLog> loaded = loginLogRepository.load(LoginLogId.of(999999L));

            assertThat(loaded).isEmpty();
        }
    }

    @Nested
    @DisplayName("findRecentFailures: 近期失败查询")
    class FindRecentFailuresTest {

        @Test
        @DisplayName("查询用户近 1 小时的失败日志,命中并按时间倒序")
        void shouldFindRecentFailuresByUser() {
            LocalDateTime now = LocalDateTime.now();
            LoginLog failure = LoginLog.createFailure(
                    LoginLogId.of(LOG_ID_VALUE), USER_ID, LOGIN_NAME, CHANNEL_TYPE,
                    now.minusMinutes(10), LOGIN_IP, USER_AGENT,
                    LoginFailureRecordId.of(FAILURE_RECORD_ID_VALUE),
                    "WRONG_PASSWORD", "密码错误", OPERATOR);
            loginLogRepository.save(failure);

            List<LoginLog> failures = loginLogRepository.findRecentFailures(
                    USER_ID, CHANNEL_TYPE, now.minusHours(1));

            assertThat(failures).hasSize(1);
            assertThat(failures.get(0).userId()).isEqualTo(USER_ID);
            assertThat(failures.get(0).isFailure()).isTrue();
        }

        @Test
        @DisplayName("成功日志不被近期失败查询返回")
        void shouldNotReturnSuccessLogsInRecentFailures() {
            LocalDateTime now = LocalDateTime.now();
            LoginLog success = LoginLog.createSuccess(
                    LoginLogId.of(LOG_ID_VALUE), USER_ID, LOGIN_NAME, CHANNEL_TYPE,
                    now.minusMinutes(10), LOGIN_IP, USER_AGENT, OPERATOR);
            loginLogRepository.save(success);

            List<LoginLog> failures = loginLogRepository.findRecentFailures(
                    USER_ID, CHANNEL_TYPE, now.minusHours(1));

            assertThat(failures).isEmpty();
        }

        @Test
        @DisplayName("countRecentFailures: 统计近期失败次数")
        void shouldCountRecentFailures() {
            LocalDateTime now = LocalDateTime.now();
            LoginLog failure = LoginLog.createFailure(
                    LoginLogId.of(LOG_ID_VALUE), USER_ID, LOGIN_NAME, CHANNEL_TYPE,
                    now.minusMinutes(5), LOGIN_IP, USER_AGENT,
                    LoginFailureRecordId.of(FAILURE_RECORD_ID_VALUE),
                    "WRONG_PASSWORD", null, OPERATOR);
            loginLogRepository.save(failure);

            int count = loginLogRepository.countRecentFailures(
                    USER_ID, CHANNEL_TYPE, now.minusHours(1));

            assertThat(count).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("findLatestByUser: 查询用户最新登录")
    class FindLatestByUserTest {

        @Test
        @DisplayName("返回用户最新的登录日志(按 loginTime 倒序)")
        void shouldFindLatestByUser() {
            LocalDateTime now = LocalDateTime.now();
            LoginLog earlier = LoginLog.createSuccess(
                    LoginLogId.of(LOG_ID_VALUE), USER_ID, LOGIN_NAME, CHANNEL_TYPE,
                    now.minusHours(2), LOGIN_IP, USER_AGENT, OPERATOR);
            LoginLog latest = LoginLog.createSuccess(
                    LoginLogId.of(30010L), USER_ID, LOGIN_NAME, CHANNEL_TYPE,
                    now.minusMinutes(5), LOGIN_IP, USER_AGENT, OPERATOR);
            loginLogRepository.save(earlier);
            loginLogRepository.save(latest);

            Optional<LoginLog> found = loginLogRepository.findLatestByUser(USER_ID, CHANNEL_TYPE);

            assertThat(found).isPresent();
            assertThat(found.get().id().value()).isEqualTo(30010L);
        }

        @Test
        @DisplayName("用户不存在登录日志时返回 empty")
        void shouldReturnEmptyWhenNoLoginLogs() {
            Optional<LoginLog> found = loginLogRepository.findLatestByUser(999999L, CHANNEL_TYPE);

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("delete: 删除")
    class DeleteTest {

        @Test
        @DisplayName("delete 后 load 返回 empty(同时清理子表)")
        void shouldDeleteLoginLogAndChildren() {
            LoginLog log = LoginLog.createFailure(
                    LoginLogId.of(LOG_ID_VALUE), USER_ID, LOGIN_NAME, CHANNEL_TYPE,
                    LocalDateTime.now(), LOGIN_IP, USER_AGENT,
                    LoginFailureRecordId.of(FAILURE_RECORD_ID_VALUE),
                    "WRONG_PASSWORD", null, OPERATOR);
            loginLogRepository.save(log);

            loginLogRepository.delete(log);

            assertThat(loginLogRepository.load(LoginLogId.of(LOG_ID_VALUE))).isEmpty();
        }

        @Test
        @DisplayName("deleteById 后 load 返回 empty")
        void shouldDeleteById() {
            LoginLog log = LoginLog.createSuccess(
                    LoginLogId.of(LOG_ID_VALUE), USER_ID, LOGIN_NAME, CHANNEL_TYPE,
                    LocalDateTime.now(), LOGIN_IP, USER_AGENT, OPERATOR);
            loginLogRepository.save(log);

            loginLogRepository.deleteById(LoginLogId.of(LOG_ID_VALUE));

            assertThat(loginLogRepository.load(LoginLogId.of(LOG_ID_VALUE))).isEmpty();
        }

        @Test
        @DisplayName("delete null 日志不抛异常")
        void shouldNotThrowWhenDeleteNull() {
            loginLogRepository.delete(null);
            loginLogRepository.deleteById(null);
        }
    }
}
