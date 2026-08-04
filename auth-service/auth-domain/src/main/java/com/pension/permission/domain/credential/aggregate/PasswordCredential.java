package com.pension.permission.domain.credential.aggregate;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.exception.DomainException;
import com.example.shared.identifier.id.UserNo;
import com.example.shared.valueobject.ValidityPeriod;
import com.pension.permission.domain.credential.enumeration.CredentialType;
import com.pension.permission.domain.credential.errorcode.CredentialError;
import com.pension.permission.domain.credential.event.PasswordChanged;
import com.pension.permission.domain.credential.valueobject.owner.UserCredentialOwner;
import com.pension.permission.types.CredentialId;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

/**
 * 密码天然是"人证客户身份"，持有者只会是具体某个账号，所以accountId是直接字段而不是CredentialOwner
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/8/1 20:27
 */
public final class PasswordCredential
  extends Credential {


  private static final Duration DEFAULT_VALIDITY =
    Duration.ofDays(180);


  private final UserNo userNo;


  private String passwordHash;


  private PasswordCredential(
    CredentialId id,
    UserNo userNo,
    String passwordHash,
    Set<AnnuityChannel> channels,
    UserNo operator,
    Clock clock
  ) {

    super(
      id,
      new UserCredentialOwner(userNo),
      channels,
      ValidityPeriod.of(
        LocalDateTime.now(clock),
        DEFAULT_VALIDITY
      ),
      operator
    );


    this.userNo =
      Objects.requireNonNull(userNo);


    this.passwordHash =
      Objects.requireNonNull(passwordHash);


  }


  public static PasswordCredential create(
    CredentialId id,
    UserNo userNo,
    String passwordHash,
    Set<AnnuityChannel> channels,
    UserNo operator,
    Clock clock
  ) {

    return new PasswordCredential(
      id,
      userNo,
      passwordHash,
      channels,
      operator,
      clock
    );

  }


  @Override
  public CredentialType type() {

    return CredentialType.PASSWORD;

  }


  /**
   * 修改密码
   */
  public void rotatePassword(
    String newPasswordHash,
    UserNo operator,
    Clock clock
  ) {

    ensureActive();


    if (passwordHash.equals(newPasswordHash)) {

      throw new DomainException(
        CredentialError.PASSWORD_SAME_AS_OLD
      );

    }


    this.passwordHash =
      newPasswordHash;


    renewValidity(
      LocalDateTime.now(clock),
      DEFAULT_VALIDITY
    );


    markUpdated(operator);


    registerDomainEvent(
      PasswordChanged.of(
        id(),
        operator
      )
    );

  }


  @Override
  protected void validateInvariants() {

    super.validateInvariants();


    Objects.requireNonNull(
      userNo
    );


    if (passwordHash == null
      ||
      passwordHash.isBlank()) {

      throw new DomainException(
        CredentialError.PASSWORD_HASH_REQUIRED
      );

    }

  }

}
