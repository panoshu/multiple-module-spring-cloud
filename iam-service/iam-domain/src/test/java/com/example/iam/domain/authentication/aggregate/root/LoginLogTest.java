package com.example.iam.domain.authentication.aggregate.root;

import com.example.iam.domain.authentication.aggregate.entity.LoginFailureRecord;
import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.domain.authentication.errorcode.IamAuthErrorCode;
import com.example.iam.types.LoginFailureRecordId;
import com.example.iam.types.LoginLogId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LoginLog 聚合根行为测试。
 *
 * <p>覆盖:
 * <ul>
 *   <li>工厂方法 {@code createSuccess} 创建成功日志(无失败记录)</li>
 *   <li>工厂方法 {@code createFailure} 创建失败日志(自动挂载一条失败记录)</li>
 *   <li>{@code addFailureRecord} 追加失败记录(多重失败原因场景)</li>
 *   <li>失败记录集合不可变性</li>
 *   <li>{@code reconstitute} 数据库重建</li>
 *   <li>参数校验(空登录名/空渠道类型等)</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
@DisplayName("LoginLog 聚合根行为")
class LoginLogTest {

  private static final UserNo SYSTEM = UserNo.of("U-SYSTEM");
  private static final LoginLogId LOG_ID = LoginLogId.of(5001L);
  private static final LoginFailureRecordId RECORD_ID = LoginFailureRecordId.of(6001L);
  private static final Long USER_ID = 1001L;
  private static final String LOGIN_NAME = "user1";
  private static final String LOGIN_IP = "192.168.1.100";
  private static final String USER_AGENT = "Mozilla/5.0";

  @Test
  @DisplayName("createSuccess 工厂方法初始化成功日志")
  void createSuccess_initializesSuccessLog() {
    LocalDateTime before = LocalDateTime.now();

    LoginLog log = LoginLog.createSuccess(
        LOG_ID, USER_ID, LOGIN_NAME, ChannelType.INTERNET,
        LocalDateTime.now(), LOGIN_IP, USER_AGENT, SYSTEM
    );

    assertThat(log.id()).isEqualTo(LOG_ID);
    assertThat(log.userId()).isEqualTo(USER_ID);
    assertThat(log.loginName()).isEqualTo(LOGIN_NAME);
    assertThat(log.channelType()).isEqualTo(ChannelType.INTERNET);
    assertThat(log.isSuccess()).isTrue();
    assertThat(log.isFailure()).isFalse();
    assertThat(log.loginTime()).isAfterOrEqualTo(before);
    assertThat(log.loginIp()).isEqualTo(LOGIN_IP);
    assertThat(log.userAgent()).isEqualTo(USER_AGENT);
    assertThat(log.failureRecords()).isEmpty();
    assertThat(log.createdBy()).isEqualTo(SYSTEM);
  }

  @Test
  @DisplayName("createSuccess 允许 userId 为 null(用户不存在场景)")
  void createSuccess_allowsNullUserId() {
    LoginLog log = LoginLog.createSuccess(
        LOG_ID, null, LOGIN_NAME, ChannelType.INTERNET,
        LocalDateTime.now(), LOGIN_IP, USER_AGENT, SYSTEM
    );

    assertThat(log.userId()).isNull();
    assertThat(log.isSuccess()).isTrue();
  }

  @Test
  @DisplayName("createSuccess 拒绝空 loginName")
  void createSuccess_rejectsBlankLoginName() {
    assertThatThrownBy(() -> LoginLog.createSuccess(
        LOG_ID, USER_ID, "", ChannelType.INTERNET,
        LocalDateTime.now(), LOGIN_IP, USER_AGENT, SYSTEM
    )).isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("createSuccess 拒绝空 channelType")
  void createSuccess_rejectsNullChannelType() {
    assertThatThrownBy(() -> LoginLog.createSuccess(
        LOG_ID, USER_ID, LOGIN_NAME, null,
        LocalDateTime.now(), LOGIN_IP, USER_AGENT, SYSTEM
    )).isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("createFailure 工厂方法初始化失败日志并挂载一条失败记录")
  void createFailure_initializesFailureLogWithRecord() {
    LoginLog log = LoginLog.createFailure(
        LOG_ID, USER_ID, LOGIN_NAME, ChannelType.INTERNET,
        LocalDateTime.now(), LOGIN_IP, USER_AGENT,
        RECORD_ID, "WRONG_PASSWORD", "密码错误", SYSTEM
    );

    assertThat(log.isFailure()).isTrue();
    assertThat(log.isSuccess()).isFalse();
    assertThat(log.failureRecords()).hasSize(1);

    LoginFailureRecord record = log.failureRecords().get(0);
    assertThat(record.reason()).isEqualTo("WRONG_PASSWORD");
    assertThat(record.detail()).isEqualTo("密码错误");
    assertThat(record.failureTime()).isNotNull();
  }

  @Test
  @DisplayName("createFailure 允许 userId 为 null(用户不存在场景)")
  void createFailure_allowsNullUserId() {
    LoginLog log = LoginLog.createFailure(
        LOG_ID, null, LOGIN_NAME, ChannelType.INTERNET,
        LocalDateTime.now(), LOGIN_IP, USER_AGENT,
        RECORD_ID, "USER_NOT_FOUND", "用户不存在", SYSTEM
    );

    assertThat(log.userId()).isNull();
    assertThat(log.isFailure()).isTrue();
    assertThat(log.failureRecords()).hasSize(1);
    assertThat(log.failureRecords().get(0).reason()).isEqualTo("USER_NOT_FOUND");
  }

  @Test
  @DisplayName("createFailure 拒绝空失败原因")
  void createFailure_rejectsBlankReason() {
    assertThatThrownBy(() -> LoginLog.createFailure(
        LOG_ID, USER_ID, LOGIN_NAME, ChannelType.INTERNET,
        LocalDateTime.now(), LOGIN_IP, USER_AGENT,
        RECORD_ID, "", "详情", SYSTEM
    )).isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("createFailure 允许空详情")
  void createFailure_allowsNullDetail() {
    LoginLog log = LoginLog.createFailure(
        LOG_ID, USER_ID, LOGIN_NAME, ChannelType.INTERNET,
        LocalDateTime.now(), LOGIN_IP, USER_AGENT,
        RECORD_ID, "WRONG_PASSWORD", null, SYSTEM
    );

    assertThat(log.failureRecords()).hasSize(1);
    assertThat(log.failureRecords().get(0).detail()).isNull();
  }

  @Test
  @DisplayName("addFailureRecord 在失败日志上追加记录")
  void addFailureRecord_appendsRecordToFailureLog() {
    LoginLog log = createFailureLog();
    int initialSize = log.failureRecords().size();

    log.addFailureRecord(LoginFailureRecordId.of(7001L), "IP_BLACKLISTED", "IP 已被加入黑名单", SYSTEM);

    assertThat(log.failureRecords()).hasSize(initialSize + 1);
    assertThat(log.failureRecords().get(initialSize).reason()).isEqualTo("IP_BLACKLISTED");
    assertThat(log.updatedBy()).isEqualTo(SYSTEM);
  }

  @Test
  @DisplayName("addFailureRecord 在成功日志上抛 DomainException")
  void addFailureRecord_throwsWhenSuccessLog() {
    LoginLog log = createSuccessLog();

    assertThatThrownBy(() -> log.addFailureRecord(
        LoginFailureRecordId.of(7001L), "WRONG_PASSWORD", "密码错误", SYSTEM
    )).isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("addFailureRecord 拒绝空原因")
  void addFailureRecord_rejectsBlankReason() {
    LoginLog log = createFailureLog();

    assertThatThrownBy(() -> log.addFailureRecord(
        LoginFailureRecordId.of(7001L), "", "详情", SYSTEM
    )).isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("failureRecords 返回不可变集合")
  void failureRecords_returnsImmutableList() {
    LoginLog log = createFailureLog();

    List<LoginFailureRecord> records = log.failureRecords();

    assertThatThrownBy(() -> records.add(new LoginFailureRecord(
        LoginFailureRecordId.of(7001L), "REASON", "detail", LocalDateTime.now()
    ))).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("reconstitute 从数据库状态恢复成功日志")
  void reconstitute_restoresSuccessLog() {
    LocalDateTime loginTime = LocalDateTime.of(2026, 7, 26, 9, 0);
    Version version = Version.of(1);

    LoginLog log = LoginLog.reconstitute(
        LOG_ID, USER_ID, LOGIN_NAME, ChannelType.HQ,
        true, loginTime, LOGIN_IP, USER_AGENT,
        List.of(),
        SYSTEM, SYSTEM, loginTime, loginTime, version
    );

    assertThat(log.isSuccess()).isTrue();
    assertThat(log.channelType()).isEqualTo(ChannelType.HQ);
    assertThat(log.failureRecords()).isEmpty();
    assertThat(log.version()).isEqualTo(version);
  }

  @Test
  @DisplayName("reconstitute 从数据库状态恢复失败日志(含多条失败记录)")
  void reconstitute_restoresFailureLogWithMultipleRecords() {
    LocalDateTime loginTime = LocalDateTime.of(2026, 7, 26, 10, 30);
    LocalDateTime failureTime1 = LocalDateTime.of(2026, 7, 26, 10, 30);
    LocalDateTime failureTime2 = LocalDateTime.of(2026, 7, 26, 10, 31);
    Version version = Version.of(2);

    List<LoginFailureRecord> records = List.of(
        new LoginFailureRecord(LoginFailureRecordId.of(7001L), "WRONG_PASSWORD", "密码错误", failureTime1),
        new LoginFailureRecord(LoginFailureRecordId.of(7002L), "IP_BLACKLISTED", "IP 黑名单", failureTime2)
    );

    LoginLog log = LoginLog.reconstitute(
        LOG_ID, USER_ID, LOGIN_NAME, ChannelType.BRANCH,
        false, loginTime, LOGIN_IP, USER_AGENT,
        records,
        SYSTEM, SYSTEM, loginTime, loginTime, version
    );

    assertThat(log.isFailure()).isTrue();
    assertThat(log.failureRecords()).hasSize(2);
    assertThat(log.failureRecords().get(0).reason()).isEqualTo("WRONG_PASSWORD");
    assertThat(log.failureRecords().get(1).reason()).isEqualTo("IP_BLACKLISTED");
  }

  @Test
  @DisplayName("reconstitute 在 success=true 但包含失败记录时抛 IllegalStateException")
  void reconstitute_throwsWhenSuccessButHasFailureRecords() {
    LocalDateTime loginTime = LocalDateTime.now();
    List<LoginFailureRecord> records = List.of(
        new LoginFailureRecord(LoginFailureRecordId.of(7001L), "WRONG_PASSWORD", "密码错误", loginTime)
    );

    assertThatThrownBy(() -> LoginLog.reconstitute(
        LOG_ID, USER_ID, LOGIN_NAME, ChannelType.INTERNET,
        true, loginTime, LOGIN_IP, USER_AGENT,
        records,
        SYSTEM, SYSTEM, loginTime, loginTime, Version.initial()
    )).isInstanceOf(IllegalStateException.class);
  }

  private LoginLog createSuccessLog() {
    return LoginLog.createSuccess(
        LOG_ID, USER_ID, LOGIN_NAME, ChannelType.INTERNET,
        LocalDateTime.now(), LOGIN_IP, USER_AGENT, SYSTEM
    );
  }

  private LoginLog createFailureLog() {
    return LoginLog.createFailure(
        LOG_ID, USER_ID, LOGIN_NAME, ChannelType.INTERNET,
        LocalDateTime.now(), LOGIN_IP, USER_AGENT,
        RECORD_ID, "WRONG_PASSWORD", "密码错误", SYSTEM
    );
  }
}
