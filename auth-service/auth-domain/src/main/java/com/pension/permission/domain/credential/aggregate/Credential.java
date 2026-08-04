package com.pension.permission.domain.credential.aggregate;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import com.example.shared.identifier.id.UserNo;
import com.example.shared.valueobject.ValidityPeriod;
import com.pension.permission.domain.credential.enumeration.CredentialStatus;
import com.pension.permission.domain.credential.enumeration.CredentialType;
import com.pension.permission.domain.credential.errorcode.CredentialError;
import com.pension.permission.domain.credential.event.CredentialRevoked;
import com.pension.permission.domain.credential.valueobject.owner.CredentialOwner;
import com.pension.permission.types.CredentialId;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

/**
 * 凭证的抽象。密码只是凭证的一种具体实现，日后新增UKey之外的凭证类型
 * （动态口令、生物识别等）只需要新增一个实现类并注册对应的认证策略，
 * 不需要改动账号体系本身——这是身份与凭证域里开闭原则的落地方式。
 * getOwner()返回的是持有者(账号/客户/计划)而不是写死的账号ID——密码天然只会是
 * AccountCredentialOwner，但UKey这类物理凭证在企业年金场景里实际持有者
 * 往往是客户或计划，不是具体某个人。
 * 访问器用JavaBean风格(getXxx)而不是record风格，是因为Credential现在是聚合根
 * (有独立的生命周期状态)，跟Grant/Account/AgentIdentityAssignment这些聚合根
 * 保持统一的写法。
 */
public sealed abstract class Credential
  extends AggregateRoot<CredentialId> permits PasswordCredential, UKeyCredential {


  private final CredentialOwner owner;
  private final Set<AnnuityChannel> applicableChannels;
  protected ValidityPeriod validityPeriod;
  private CredentialStatus status;


  protected Credential(
    CredentialId id,
    CredentialOwner owner,
    Set<AnnuityChannel> applicableChannels,
    ValidityPeriod validityPeriod,
    UserNo createdBy
  ) {

    super(
      id,
      createdBy
    );


    this.owner =
      Objects.requireNonNull(owner);


    this.applicableChannels =
      Set.copyOf(
        Objects.requireNonNull(
          applicableChannels
        )
      );


    this.validityPeriod =
      Objects.requireNonNull(
        validityPeriod
      );


    this.status =
      CredentialStatus.ACTIVE;


    validateInvariants();

  }


  /**
   * 数据恢复
   */
  protected Credential(
    CredentialId id,
    CredentialOwner owner,
    Set<AnnuityChannel> applicableChannels,
    CredentialStatus status,
    ValidityPeriod validityPeriod,
    UserNo createdBy,
    LocalDateTime createdAt,
    UserNo updatedBy,
    LocalDateTime updatedAt,
    Version version
  ) {

    super(
      id,
      createdBy,
      updatedBy,
      createdAt,
      updatedAt,
      version
    );


    this.owner =
      Objects.requireNonNull(owner);


    this.applicableChannels =
      Set.copyOf(
        applicableChannels
      );


    this.status =
      Objects.requireNonNull(status);


    this.validityPeriod =
      Objects.requireNonNull(validityPeriod);


    validateInvariants();

  }


  public abstract CredentialType type();


  public CredentialOwner owner() {

    return owner;

  }


  public CredentialStatus status() {

    return status;

  }


  public Set<AnnuityChannel> applicableChannels() {

    return applicableChannels;

  }


  public ValidityPeriod validityPeriod() {

    return validityPeriod;

  }


  /**
   * 当前凭证是否可使用
   */
  public boolean usable(
    LocalDateTime now
  ) {

    return status == CredentialStatus.ACTIVE
      &&
      validityPeriod.isEffective(now);

  }


  /**
   * 撤销凭证
   */
  public void revoke(
    UserNo operator
  ) {

    if (status == CredentialStatus.REVOKED) {

      return;

    }


    this.status =
      CredentialStatus.REVOKED;


    markUpdated(operator);


    registerDomainEvent(
      CredentialRevoked.of(
        id(),
        operator
      )
    );

  }


  /**
   * 禁用
   */
  public void disable(
    UserNo operator
  ) {

    this.status =
      CredentialStatus.DISABLED;


    markUpdated(operator);

  }


  /**
   * 启用
   */
  public void activate(
    UserNo operator
  ) {

    if (!validityPeriod.isEffective(LocalDateTime.now())) {

      throw new DomainException(
        CredentialError.CREDENTIAL_EXPIRED
      );

    }


    this.status =
      CredentialStatus.ACTIVE;


    markUpdated(operator);

  }


  protected void ensureActive() {

    if (status != CredentialStatus.ACTIVE) {

      throw new DomainException(
        CredentialError.CREDENTIAL_NOT_ACTIVE
      );

    }

  }


  /**
   * 重置有效期
   */
  protected void renewValidity(
    LocalDateTime now,
    Duration duration
  ) {

    this.validityPeriod =
      validityPeriod.renew(
        now,
        duration
      );

  }


  @Override
  protected void validateInvariants() {


    Objects.requireNonNull(
      owner
    );


    Objects.requireNonNull(
      status
    );


    Objects.requireNonNull(
      validityPeriod
    );


    if (applicableChannels == null) {

      throw new DomainException(
        CredentialError.CHANNEL_REQUIRED
      );

    }

  }

}
