package com.pension.permission.domain.channel.aggregate;


import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.enumeration.SessionStatus;
import com.pension.permission.domain.channel.errorcode.SecondaryAuthErrorCode;
import com.pension.permission.domain.channel.event.*;
import com.pension.permission.domain.channel.valueobject.EffectiveIdentity;
import com.pension.permission.types.SecondaryAuthSessionId;
import com.pension.permission.types.SessionId;

import java.time.Duration;
import java.time.LocalDateTime;

public class Session extends AggregateRoot<SessionId> {

  private final UserNo primaryAccountId;

  private final AnnuityChannel channel;
  /**
   * 会话过期时间
   */
  private final LocalDateTime expiresAt;
  /**
   * 当前有效身份
   */
  private EffectiveIdentity effectiveIdentity;
  /**
   * 当前绑定的二次授权会话 ID（仅网点渠道柜员有效）
   */
  private SecondaryAuthSessionId secondaryAuthSessionId;
  /**
   * 当前选择办理的计划
   */
  private PlanNo selectedPlanId;
  private SessionStatus status;


  /**
   * ===============================
   * 新建 Session
   * ===============================
   * 用户认证成功后创建。
   */
  private Session(
    SessionId id,
    UserNo creator,
    UserNo primaryAccountId,
    AnnuityChannel channel,
    EffectiveIdentity effectiveIdentity,
    LocalDateTime expiresAt
  ) {

    super(id, creator);

    this.primaryAccountId = primaryAccountId;
    this.channel = channel;
    this.effectiveIdentity = effectiveIdentity;
    this.expiresAt = expiresAt;
    this.status = SessionStatus.ACTIVE;

    validateInvariants();

    registerDomainEvent(
      SessionCreated.of(
        id,
        primaryAccountId,
        channel,
        expiresAt,
        creator
      )
    );
  }


  /**
   * ===============================
   * Session 重建
   * ===============================
   * Repository / SessionStore 恢复使用。
   * 不产生领域事件。
   */
  private Session(
    SessionId id,

    UserNo createdBy,
    UserNo updatedBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Version version,

    UserNo primaryAccountId,
    AnnuityChannel channel,
    EffectiveIdentity effectiveIdentity,
    PlanNo selectedPlanId,
    LocalDateTime expiresAt,
    SessionStatus status,
    SecondaryAuthSessionId secondaryAuthSessionId
  ) {

    super(
      id,
      createdBy,
      updatedBy,
      createdAt,
      updatedAt,
      version
    );

    this.primaryAccountId = primaryAccountId;
    this.channel = channel;
    this.effectiveIdentity = effectiveIdentity;
    this.selectedPlanId = selectedPlanId;
    this.expiresAt = expiresAt;
    this.status = status;
    this.secondaryAuthSessionId = secondaryAuthSessionId;

    validateInvariants();
  }


  /**
   * ===============================
   * 创建工厂方法
   * ===============================
   */
  public static Session create(
    SessionId id,
    UserNo creator,
    UserNo primaryAccountId,
    AnnuityChannel channel,
    EffectiveIdentity effectiveIdentity,
    Duration sessionTimeout
  ) {

    LocalDateTime now = LocalDateTime.now();

    return new Session(
      id,
      creator,
      primaryAccountId,
      channel,
      effectiveIdentity,
      now.plus(sessionTimeout)
    );
  }


  /**
   * ===============================
   * 重建工厂方法
   * ===============================
   */
  public static Session reconstitute(
    SessionId id,
    UserNo createdBy,
    UserNo updatedBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Version version,

    UserNo primaryAccountId,
    AnnuityChannel channel,
    EffectiveIdentity effectiveIdentity,
    PlanNo selectedPlanId,
    LocalDateTime expiresAt,
    SessionStatus status,
    SecondaryAuthSessionId secondaryAuthSessionId
  ) {

    return new Session(
      id,
      createdBy,
      updatedBy,
      createdAt,
      updatedAt,
      version,

      primaryAccountId,
      channel,
      effectiveIdentity,
      selectedPlanId,
      expiresAt,
      status,
      secondaryAuthSessionId
    );
  }


  // ===============================
  // 领域行为
  // ===============================

  public UserNo primaryAccountId() {
    return this.primaryAccountId;
  }

  public AnnuityChannel channel() {
    return this.channel;
  }

  public EffectiveIdentity effectiveIdentity() {
    return this.effectiveIdentity;
  }

  /**
   * 应用二次授权结果.
   *
   * <p>监听 SecondaryAuthCompleted 事件后调用，将柜员会话与二次授权会话绑定。
   * 仅网点渠道允许调用此方法。</p>
   *
   * @param sessionId 二次授权会话 ID
   * @param identity  有效身份
   * @param operator  操作人
   */
  public void applySecondaryAuth(
    SecondaryAuthSessionId sessionId,
    EffectiveIdentity identity,
    UserNo operator
  ) {
    if (this.channel != AnnuityChannel.BANK_BRANCH) {
      throw new DomainException(SecondaryAuthErrorCode.CHANNEL_NOT_SUPPORTED);
    }
    if (this.secondaryAuthSessionId != null) {
      throw new DomainException(SecondaryAuthErrorCode.ACTIVE_SESSION_EXISTS);
    }
    EffectiveIdentity previousIdentity = this.effectiveIdentity;
    this.secondaryAuthSessionId = sessionId;
    this.effectiveIdentity = identity;
    markUpdated(operator);
    registerDomainEvent(
      SessionIdentityElevated.of(
        id(),
        this.primaryAccountId,
        previousIdentity,
        identity,
        operator
      )
    );
  }

  /**
   * 清除二次授权引用.
   *
   * <p>监听 SecondaryAuthRevoked 事件后调用。不产生独立事件，撤销事件由 SecondaryAuthSession 发起。</p>
   *
   * @param operator 操作人
   */
  public void clearSecondaryAuth(UserNo operator) {
    if (this.channel != AnnuityChannel.BANK_BRANCH) {
      return;
    }
    this.secondaryAuthSessionId = null;
    this.effectiveIdentity = EffectiveIdentity.direct(this.primaryAccountId);
    markUpdated(operator);
  }

  /**
   * 获取当前绑定的二次授权会话 ID.
   */
  public SecondaryAuthSessionId secondaryAuthSessionId() {
    return secondaryAuthSessionId;
  }

  /**
   * 获取当前选择办理的计划 ID.
   */
  public PlanNo selectedPlanId() {
    return selectedPlanId;
  }

  /**
   * 获取会话状态.
   */
  public SessionStatus status() {
    return status;
  }

  /**
   * 获取会话过期时间.
   */
  public LocalDateTime expiresAt() {
    return expiresAt;
  }


  /**
   * 选择当前办理计划
   */
  public void selectPlan(
    PlanNo planId,
    UserNo operator
  ) {

    if (planId == null) {
      throw new IllegalArgumentException(
        "PlanNo cannot be null."
      );
    }

    this.selectedPlanId = planId;

    registerDomainEvent(
      SessionPlanSelected.of(
        id(),
        primaryAccountId,
        planId,
        operator
      )
    );
  }

  public void expire(
    UserNo operator
  ) {

    if (status == SessionStatus.CLOSED) {
      return;
    }

    if (!isExpired(LocalDateTime.now())) {
      throw new IllegalStateException(
        "Session is not expired."
      );
    }

    status = SessionStatus.EXPIRED;

    registerDomainEvent(
      SessionExpired.of(
        id(),
        primaryAccountId,
        operator
      )
    );
  }


  public boolean isExpired(
    LocalDateTime now
  ) {

    return !now.isBefore(expiresAt);
  }


  @Override
  protected void validateInvariants() {

    if (primaryAccountId == null) {
      throw new IllegalStateException(
        "Primary account id cannot be null."
      );
    }


    if (channel == null) {
      throw new IllegalStateException(
        "Channel cannot be null."
      );
    }


    if (expiresAt == null) {
      throw new IllegalStateException(
        "Expire time cannot be null."
      );
    }


    if (effectiveIdentity == null) {
      throw new IllegalStateException(
        "Effective identity cannot be null."
      );
    }


    if (status == null) {
      throw new IllegalStateException(
        "Status cannot be null."
      );
    }
  }

  public void close(UserNo operator) {

    if (status == SessionStatus.CLOSED) {
      return;
    }


    this.status = SessionStatus.CLOSED;


    registerDomainEvent(
      SessionClosed.of(
        this.id(),
        this.primaryAccountId,
        operator
      )
    );
  }
}
