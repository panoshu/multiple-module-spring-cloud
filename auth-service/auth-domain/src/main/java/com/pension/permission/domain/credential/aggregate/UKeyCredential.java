package com.pension.permission.domain.credential.aggregate;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.exception.DomainException;
import com.example.shared.identifier.id.UserNo;
import com.example.shared.valueobject.ValidityPeriod;
import com.pension.permission.domain.credential.enumeration.CredentialType;
import com.pension.permission.domain.credential.errorcode.CredentialError;
import com.pension.permission.domain.credential.event.UKeyRotated;
import com.pension.permission.domain.credential.valueobject.owner.CredentialOwner;
import com.pension.permission.types.CredentialId;

import java.time.Duration;
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
    Set<AnnuityChannel> channels,
    ValidityPeriod validityPeriod,
    UserNo operator
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


  }


  public static UKeyCredential create(
    CredentialId id,
    CredentialOwner owner,
    String keySerial,
    Set<AnnuityChannel> channels,
    ValidityPeriod validityPeriod,
    UserNo operator
  ) {

    return new UKeyCredential(
      id,
      owner,
      keySerial,
      channels,
      validityPeriod,
      operator
    );

  }


  @Override
  public CredentialType type() {

    return CredentialType.U_KEY;

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


    markUpdated(operator);


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


    markUpdated(operator);

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
