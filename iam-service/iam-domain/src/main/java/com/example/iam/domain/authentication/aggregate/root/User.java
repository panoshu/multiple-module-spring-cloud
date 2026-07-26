package com.example.iam.domain.authentication.aggregate.root;

import com.example.iam.domain.authentication.aggregate.entity.UserProfile;
import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.domain.authentication.aggregate.valueobject.UserStatus;
import com.example.iam.domain.authentication.errorcode.IamAuthErrorCode;
import com.example.iam.types.UserId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * 用户聚合根(三渠道统一)。
 *
 * <p>承载用户身份、状态与渠道专属档案。三渠道(网上/总部/网点)共用同一 {@code User} 模型,
 * 通过 {@link ChannelType} 区分渠道,通过 {@link UserProfile} 承载渠道差异化字段。
 *
 * <p>状态机参照 {@link UserStatus}:
 * <ul>
 *   <li>{@code create} → ACTIVE</li>
 *   <li>ACTIVE ↔ DISABLED(管理员禁用/启用)</li>
 *   <li>ACTIVE → LOCKED(系统锁定)</li>
 *   <li>LOCKED → ACTIVE(自动/手动解锁)</li>
 *   <li>LOCKED → DISABLED(锁定状态下也可禁用)</li>
 * </ul>
 *
 * <p>注:本聚合暂不注册领域事件,事件在 Task 9 中补齐。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public class User extends AggregateRoot<UserId> {

  private final ChannelType channelType;
  private final String loginName;
  private String displayName;
  private UserStatus status;
  private LocalDateTime lastLoginTime;
  private String lastLoginIp;
  private UserProfile profile;

  private User(UserId id, ChannelType channelType, String loginName,
               String displayName, UserProfile profile, UserNo createdBy) {
    super(id, createdBy);
    this.channelType = Objects.requireNonNull(channelType, "channelType cannot be null");
    if (loginName == null || loginName.isBlank()) {
      throw new DomainException(IamAuthErrorCode.LOGIN_NAME_DUPLICATE)
          .withUserDetail("登录名不能为空");
    }
    this.loginName = loginName;
    this.displayName = displayName;
    this.status = UserStatus.ACTIVE;
    this.profile = profile;
    this.validateInvariants();
  }

  private User(UserId id, ChannelType channelType, String loginName, String displayName,
               UserStatus status, LocalDateTime lastLoginTime, String lastLoginIp,
               UserProfile profile,
               UserNo createdBy, UserNo updatedBy,
               LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    super(id, createdBy, updatedBy, createdAt, updatedAt, version);
    this.channelType = channelType;
    this.loginName = loginName;
    this.displayName = displayName;
    this.status = status;
    this.lastLoginTime = lastLoginTime;
    this.lastLoginIp = lastLoginIp;
    this.profile = profile;
    this.validateInvariants();
  }

  /**
   * 工厂方法:创建新用户(无档案)。
   */
  public static User create(UserId id, ChannelType channelType,
                             String loginName, String displayName, UserNo createdBy) {
    return new User(id, channelType, loginName, displayName, null, createdBy);
  }

  /**
   * 工厂方法:创建新用户(带档案)。
   */
  public static User create(UserId id, ChannelType channelType,
                             String loginName, String displayName,
                             UserProfile profile, UserNo createdBy) {
    return new User(id, channelType, loginName, displayName, profile, createdBy);
  }

  /**
   * 工厂方法:从数据库重建聚合。
   */
  public static User reconstitute(UserId id, ChannelType channelType,
                                   String loginName, String displayName,
                                   UserStatus status, LocalDateTime lastLoginTime, String lastLoginIp,
                                   UserProfile profile,
                                   UserNo createdBy, UserNo updatedBy,
                                   LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    return new User(id, channelType, loginName, displayName, status, lastLoginTime, lastLoginIp,
        profile, createdBy, updatedBy, createdAt, updatedAt, version);
  }

  /**
   * 禁用用户。
   *
   * <p>合法源状态:ACTIVE、LOCKED。终态 DISABLED 不允许重复禁用。
   *
   * @param operator 操作人
   * @param reason   禁用原因(不能为空)
   */
  public void disable(UserNo operator, String reason) {
    if (reason == null || reason.isBlank()) {
      throw new DomainException(IamAuthErrorCode.USER_STATUS_INVALID)
          .withUserDetail("禁用原因不能为空");
    }
    if (!status.canDisable()) {
      throw new DomainException(IamAuthErrorCode.USER_STATUS_INVALID)
          .withUserDetail("当前状态不允许禁用: " + status)
          .withContext("currentStatus", status)
          .withContext("targetStatus", UserStatus.DISABLED);
    }
    this.status = UserStatus.DISABLED;
    markUpdated(operator);
  }

  /**
   * 启用用户。
   *
   * <p>合法源状态:DISABLED、LOCKED。
   *
   * @param operator 操作人
   */
  public void enable(UserNo operator) {
    if (!status.canEnable()) {
      throw new DomainException(IamAuthErrorCode.USER_STATUS_INVALID)
          .withUserDetail("当前状态不允许启用: " + status)
          .withContext("currentStatus", status)
          .withContext("targetStatus", UserStatus.ACTIVE);
    }
    this.status = UserStatus.ACTIVE;
    markUpdated(operator);
  }

  /**
   * 锁定用户(系统触发,如登录失败次数超限)。
   *
   * <p>仅 ACTIVE → LOCKED 合法。
   *
   * @param operator 操作人(系统调用时为系统账号)
   * @param reason  锁定原因
   */
  public void lock(UserNo operator, String reason) {
    if (reason == null || reason.isBlank()) {
      throw new DomainException(IamAuthErrorCode.USER_STATUS_INVALID)
          .withUserDetail("锁定原因不能为空");
    }
    if (!status.canLock()) {
      throw new DomainException(IamAuthErrorCode.USER_STATUS_INVALID)
          .withUserDetail("当前状态不允许锁定: " + status)
          .withContext("currentStatus", status)
          .withContext("targetStatus", UserStatus.LOCKED);
    }
    this.status = UserStatus.LOCKED;
    markUpdated(operator);
  }

  /**
   * 标记登录成功,更新最后登录时间和 IP。
   *
   * @param ip        登录 IP
   * @param loginTime 登录时间
   * @param operator  操作人(用户自身)
   */
  public void markLoginSuccess(String ip, LocalDateTime loginTime, UserNo operator) {
    if (!status.isActive()) {
      throw new DomainException(IamAuthErrorCode.ACCOUNT_LOCKED)
          .withUserDetail("非活跃账号不可记录登录成功: " + status)
          .withContext("currentStatus", status);
    }
    this.lastLoginIp = ip;
    this.lastLoginTime = loginTime;
    markUpdated(operator);
  }

  /**
   * 附加渠道档案(用户初始无档案时使用)。
   *
   * @param profile  渠道档案
   * @param operator 操作人
   */
  public void attachProfile(UserProfile profile, UserNo operator) {
    Objects.requireNonNull(profile, "profile cannot be null");
    if (this.profile != null) {
      throw new DomainException(IamAuthErrorCode.USER_PROFILE_NOT_FOUND)
          .withUserDetail("用户档案已存在,不可重复附加")
          .withContext("userId", id().value());
    }
    this.profile = profile;
    markUpdated(operator);
  }

  /**
   * 更新渠道档案字段(委托给 {@link UserProfile#update})。
   */
  public void updateProfile(String email, String phone,
                            String organization, String position,
                            String branchId, String employeeNo,
                            Map<String, String> extraAttributes,
                            UserNo operator) {
    if (this.profile == null) {
      throw new DomainException(IamAuthErrorCode.USER_PROFILE_NOT_FOUND)
          .withUserDetail("用户档案不存在,不可更新")
          .withContext("userId", id().value());
    }
    this.profile.update(email, phone, organization, position, branchId, employeeNo,
        extraAttributes, operator);
    markUpdated(operator);
  }

  public ChannelType channelType() { return channelType; }
  public String loginName() { return loginName; }
  public String displayName() { return displayName; }
  public UserStatus status() { return status; }
  public LocalDateTime lastLoginTime() { return lastLoginTime; }
  public String lastLoginIp() { return lastLoginIp; }
  public UserProfile profile() { return profile; }

  @Override
  protected void validateInvariants() {
    if (channelType == null) {
      throw new IllegalStateException("User.channelType cannot be null");
    }
    if (loginName == null || loginName.isBlank()) {
      throw new IllegalStateException("User.loginName cannot be null or blank");
    }
    if (status == null) {
      throw new IllegalStateException("User.status cannot be null");
    }
    if (profile != null && !profile.id().equals(this.id())) {
      throw new IllegalStateException("UserProfile.id must match User.id (1:1 relationship)");
    }
  }
}
