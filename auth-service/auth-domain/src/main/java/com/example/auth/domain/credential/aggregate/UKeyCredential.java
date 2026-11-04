package com.example.auth.domain.credential.aggregate;

import com.example.core.types.Channel;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import com.example.core.types.UserNo;
import com.example.core.types.temporal.ValidityPeriod;
import com.example.auth.domain.credential.enumeration.CredentialStatus;
import com.example.auth.domain.credential.enumeration.CredentialType;
import com.example.auth.domain.credential.errorcode.CredentialError;
import com.example.auth.domain.credential.event.UKeyRotated;
import com.example.auth.domain.credential.valueobject.owner.CredentialOwner;
import com.example.auth.types.CredentialId;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

/**
 * UKeyCredential
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/8/1 20:27
 */
public final class UKeyCredential
  extends Credential {


  private String keySerial;


  private UKeyCredential(
    CredentialId id,
    CredentialOwner owner,
    String keySerial,
    Set<Channel> channels,
    ValidityPeriod validityPeriod,
    String operator
  ) {

    super(
      id,
      owner,
      channels,
      validityPeriod,
      operator
    );


    this.keySerial =
      Objects.requireNonNull(
        keySerial
      );


    validateInvariants();

  }


  private UKeyCredential(
    CredentialId id,
    CredentialOwner owner,
    Set<Channel> applicableChannels,
    CredentialStatus status,
    ValidityPeriod validityPeriod,
    String createdBy,
    LocalDateTime createdAt,
    String updatedBy,
    LocalDateTime updatedAt,
    Version version,
    String keySerial
  ) {

    super(
      id,
      owner,
      applicableChannels,
      status,
      validityPeriod,
      createdBy,
      createdAt,
      updatedBy,
      updatedAt,
      version
    );

    this.keySerial =
      Objects.requireNonNull(keySerial);

    validateInvariants();

  }

  public static UKeyCredential create(
    CredentialId id,
    CredentialOwner owner,
    String keySerial,
    Set<Channel> channels,
    ValidityPeriod validityPeriod,
    UserNo operator
  ) {

    return new UKeyCredential(
      id,
      owner,
      keySerial,
      channels,
      validityPeriod,
      operator.value()
    );

  }

  /**
   * Repository 重建入口，不产生领域事件.
   */
  public static UKeyCredential reconstitute(
    CredentialId id,
    CredentialOwner owner,
    Set<Channel> applicableChannels,
    CredentialStatus status,
    ValidityPeriod validityPeriod,
    String createdBy,
    LocalDateTime createdAt,
    String updatedBy,
    LocalDateTime updatedAt,
    Version version,
    String keySerial
  ) {

    return new UKeyCredential(
      id,
      owner,
      applicableChannels,
      status,
      validityPeriod,
      createdBy,
      createdAt,
      updatedBy,
      updatedAt,
      version,
      keySerial
    );

  }

  @Override
  public CredentialType type() {

    return CredentialType.U_KEY;

  }


  /**
   * 暴露 UKey 序列号给 Converter 重建使用（只读）.
   */
  public String keySerial() {

    return keySerial;

  }


  /**
   * 更换UKey
   */
  public void rotate(
    String newSerial,
    UserNo operator
  ) {

    ensureActive();


    if (newSerial.equals(keySerial)) {

      throw new DomainException(
        CredentialError.U_KEY_ALREADY_BOUND
      );

    }


    this.keySerial =
      newSerial;


    markUpdated(operator.value());


    registerDomainEvent(
      UKeyRotated.of(
        id(),
        operator
      )
    );

  }


  /**
   * 延长有效期
   */
  public void extendValidity(
    Duration duration,
    UserNo operator
  ) {

    ensureActive();


    this.validityPeriod =
      validityPeriod.extend(duration);


    markUpdated(operator.value());

  }


  @Override
  protected void validateInvariants() {

    super.validateInvariants();


    if (keySerial == null
      ||
      keySerial.isBlank()) {

      throw new DomainException(
        CredentialError.U_KEY_SERIAL_REQUIRED
      );

    }

  }

}
