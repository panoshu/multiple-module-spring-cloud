package com.example.iam.domain.authentication.aggregate.root;

import com.example.iam.domain.authentication.aggregate.entity.LoginFailureRecord;
import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.domain.authentication.errorcode.IamAuthErrorCode;
import com.example.iam.domain.authentication.event.UserLoginFailedEvent;
import com.example.iam.domain.authentication.event.UserLoginSucceededEvent;
import com.example.iam.types.LoginFailureRecordId;
import com.example.iam.types.LoginLogId;
import com.example.iam.types.UserId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 登录日志聚合根。
 *
 * <p>审计每次登录尝试(成功/失败),失败时通过 {@link LoginFailureRecord} 子实体记录具体原因。
 * 一个 LoginLog 可关联多条失败记录(密码错误 + IP 黑名单等并发原因)。
 *
 * <p>本聚合为只读审计聚合:创建后不允许修改业务字段,仅可通过 {@link #addFailureRecord} 追加失败记录。
 * {@link LoginRiskService} 通过 Repository 查询最近失败次数,用于风控判断。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public class LoginLog extends AggregateRoot<LoginLogId> {

  private final Long userId;
  private final String loginName;
  private final ChannelType channelType;
  private final boolean success;
  private final LocalDateTime loginTime;
  private final String loginIp;
  private final String userAgent;
  private List<LoginFailureRecord> failureRecords;

  private LoginLog(LoginLogId id, Long userId, String loginName, ChannelType channelType,
                   boolean success, LocalDateTime loginTime,
                   String loginIp, String userAgent,
                   List<LoginFailureRecord> failureRecords,
                   UserNo createdBy, UserNo updatedBy,
                   LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    super(id, createdBy, updatedBy, createdAt, updatedAt, version);
    this.userId = userId;
    this.loginName = loginName;
    this.channelType = channelType;
    this.success = success;
    this.loginTime = loginTime;
    this.loginIp = loginIp;
    this.userAgent = userAgent;
    this.failureRecords = copyRecords(failureRecords);
    this.validateInvariants();
  }

  /**
   * 工厂方法:创建登录成功日志。
   *
   * @param id          日志 ID
   * @param userId      用户 ID(可空,非用户登录场景)
   * @param loginName   登录名
   * @param channelType 渠道类型
   * @param loginTime   登录时间
   * @param loginIp     登录 IP(可空)
   * @param userAgent   User-Agent(可空)
   * @param operator    操作人(系统账号)
   */
  public static LoginLog createSuccess(LoginLogId id, Long userId, String loginName,
                                        ChannelType channelType, LocalDateTime loginTime,
                                        String loginIp, String userAgent, UserNo operator) {
    validateCommon(userId, loginName, channelType, loginTime);
    LocalDateTime now = LocalDateTime.now();
    LoginLog loginLog = new LoginLog(id, userId, loginName, channelType,
        true, loginTime, loginIp, userAgent,
        List.of(),
        operator, operator, now, now, Version.initial());
    // 登录成功事件需要 UserId,仅在 userId 非空时注册
    if (userId != null) {
      loginLog.registerDomainEvent(UserLoginSucceededEvent.of(
          id, UserId.of(userId), channelType, loginIp, loginTime));
    }
    return loginLog;
  }

  /**
   * 工厂方法:创建登录失败日志,自动挂载一条失败记录。
   *
   * @param id            日志 ID
   * @param userId        用户 ID(可空,用户不存在场景)
   * @param loginName     登录名
   * @param channelType   渠道类型
   * @param loginTime     登录时间
   * @param loginIp       登录 IP(可空)
   * @param userAgent     User-Agent(可空)
   * @param recordId      失败记录 ID
   * @param reason        失败原因代码(如 {@code WRONG_PASSWORD})
   * @param detail        人类可读详情(可空)
   * @param operator      操作人(系统账号)
   */
  public static LoginLog createFailure(LoginLogId id, Long userId, String loginName,
                                        ChannelType channelType, LocalDateTime loginTime,
                                        String loginIp, String userAgent,
                                        LoginFailureRecordId recordId,
                                        String reason, String detail, UserNo operator) {
    validateCommon(userId, loginName, channelType, loginTime);
    Objects.requireNonNull(recordId, "recordId cannot be null");
    if (reason == null || reason.isBlank()) {
      throw new DomainException(IamAuthErrorCode.LOGIN_FAILURE_RECORD_NOT_FOUND)
          .withUserDetail("失败原因不能为空");
    }
    LocalDateTime now = LocalDateTime.now();
    LoginFailureRecord record = new LoginFailureRecord(recordId, reason, detail, loginTime);
    LoginLog loginLog = new LoginLog(id, userId, loginName, channelType,
        false, loginTime, loginIp, userAgent,
        List.of(record),
        operator, operator, now, now, Version.initial());
    loginLog.registerDomainEvent(UserLoginFailedEvent.of(
        id, userId, loginName, channelType, loginIp, reason));
    return loginLog;
  }

  /**
   * 工厂方法:从数据库重建聚合。
   */
  public static LoginLog reconstitute(LoginLogId id, Long userId, String loginName,
                                       ChannelType channelType, boolean success, LocalDateTime loginTime,
                                       String loginIp, String userAgent,
                                       List<LoginFailureRecord> failureRecords,
                                       UserNo createdBy, UserNo updatedBy,
                                       LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    return new LoginLog(id, userId, loginName, channelType,
        success, loginTime, loginIp, userAgent,
        failureRecords,
        createdBy, updatedBy, createdAt, updatedAt, version);
  }

  /**
   * 追加一条失败记录(仅失败日志可追加)。
   *
   * @param recordId 失败记录 ID
   * @param reason   失败原因代码
   * @param detail   人类可读详情(可空)
   * @param operator 操作人
   */
  public void addFailureRecord(LoginFailureRecordId recordId, String reason, String detail, UserNo operator) {
    Objects.requireNonNull(recordId, "recordId cannot be null");
    if (success) {
      throw new DomainException(IamAuthErrorCode.LOGIN_FAILURE_RECORD_NOT_FOUND)
          .withUserDetail("成功日志不允许追加失败记录")
          .withContext("loginLogId", id().value())
          .withContext("success", success);
    }
    if (reason == null || reason.isBlank()) {
      throw new DomainException(IamAuthErrorCode.LOGIN_FAILURE_RECORD_NOT_FOUND)
          .withUserDetail("失败原因不能为空");
    }
    this.failureRecords.add(new LoginFailureRecord(recordId, reason, detail, LocalDateTime.now()));
    markUpdated(operator);
  }

  public Long userId() { return userId; }
  public String loginName() { return loginName; }
  public ChannelType channelType() { return channelType; }
  public boolean isSuccess() { return success; }
  public boolean isFailure() { return !success; }
  public LocalDateTime loginTime() { return loginTime; }
  public String loginIp() { return loginIp; }
  public String userAgent() { return userAgent; }

  /**
   * 返回失败记录的不可变视图。
   */
  public List<LoginFailureRecord> failureRecords() {
    return List.copyOf(failureRecords);
  }

  private static void validateCommon(Long userId, String loginName, ChannelType channelType, LocalDateTime loginTime) {
    if (loginName == null || loginName.isBlank()) {
      throw new DomainException(IamAuthErrorCode.LOGIN_LOG_NOT_FOUND)
          .withUserDetail("登录名不能为空");
    }
    Objects.requireNonNull(channelType, "channelType cannot be null");
    Objects.requireNonNull(loginTime, "loginTime cannot be null");
  }

  private static List<LoginFailureRecord> copyRecords(List<LoginFailureRecord> source) {
    return source == null ? new ArrayList<>() : new ArrayList<>(source);
  }

  @Override
  protected void validateInvariants() {
    if (loginName == null || loginName.isBlank()) {
      throw new IllegalStateException("LoginLog.loginName cannot be null or blank");
    }
    if (channelType == null) {
      throw new IllegalStateException("LoginLog.channelType cannot be null");
    }
    if (loginTime == null) {
      throw new IllegalStateException("LoginLog.loginTime cannot be null");
    }
    if (success && failureRecords != null && !failureRecords.isEmpty()) {
      throw new IllegalStateException("LoginLog with success=true cannot have failure records");
    }
    if (failureRecords == null) {
      throw new IllegalStateException("LoginLog.failureRecords cannot be null");
    }
  }
}
