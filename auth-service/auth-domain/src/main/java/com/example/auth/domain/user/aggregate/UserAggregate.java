package com.example.auth.domain.user.aggregate;

import com.example.core.types.contact.Address;
import com.example.core.types.contact.Email;
import com.example.core.types.contact.Mobile;
import com.example.core.types.contact.PostalCode;
import com.example.core.types.contact.Telephone;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import com.example.core.types.UserNo;
import com.example.core.types.identity.IdentityDocument;
import com.example.core.types.identity.IdentityType;
import com.example.auth.domain.user.enumeration.UserStatus;
import com.example.auth.domain.user.enumeration.UserType;
import com.example.auth.domain.user.errorcode.UserError;
import com.example.auth.domain.user.event.UserActivated;
import com.example.auth.domain.user.event.UserDisabled;
import com.example.auth.domain.user.event.UserFrozen;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * UserAggregate
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/8/1 15:15
 */

@Getter
public class UserAggregate extends AggregateRoot<UserNo> {

  private final UserType userType;

  private IdentityDocument identityDocument;
  private Mobile mobile;
  private Email email;
  private Telephone telephone;
  private Address address;
  private PostalCode postalCode;

  private UserStatus status;

  /**
   * 新建用户
   */
  private UserAggregate(
    UserNo id,
    UserType userType,
    IdentityDocument identityDocument,
    Mobile mobile,
    String createdBy
  ) {
    super(id, createdBy);
    this.userType = Objects.requireNonNull(userType, "userType required");
    this.identityDocument = Objects.requireNonNull(identityDocument, "identityDocument required");
    this.mobile = Objects.requireNonNull(mobile, "mobile required");
    this.status = UserStatus.ACTIVE;
    validateInvariants();
  }

  /**
   * 数据恢复构造
   * ORM使用
   */
  protected UserAggregate(
    UserNo id,
    UserType userType,
    IdentityDocument identityDocument,
    Mobile mobile,
    Email email,
    Telephone telephone,
    Address address,
    PostalCode postalCode,
    UserStatus status,
    String createdBy,
    LocalDateTime createdOn,
    String modifiedBy,
    LocalDateTime modifiedOn,
    Version version
  ) {
    super(id, createdBy, modifiedBy, createdOn, modifiedOn, version);
    this.userType = userType;
    this.identityDocument = identityDocument;
    this.mobile = mobile;
    this.email = email;
    this.telephone = telephone;
    this.address = address;
    this.postalCode = postalCode;
    this.status = status;
    validateInvariants();
  }

  /**
   * 工厂方法
   */
  public static UserAggregate create(
    UserNo id,
    UserType userType,
    IdentityDocument identityDocument,
    Mobile mobile,
    String createdBy
  ) {

    return new UserAggregate(
      id,
      userType,
      identityDocument,
      mobile,
      createdBy
    );
  }

  /**
   * 数据恢复
   */
  public static UserAggregate restore(
    UserNo id,
    UserType userType,
    IdentityDocument identityDocument,
    Mobile mobile,
    Email email,
    Telephone telephone,
    Address address,
    PostalCode postalCode,
    UserStatus status,
    String createdBy,
    LocalDateTime createdOn,
    String modifiedBy,
    LocalDateTime modifiedOn,
    Version version
  ) {

    return new UserAggregate(
      id,
      userType,
      identityDocument,
      mobile,
      email,
      telephone,
      address,
      postalCode,
      status,
      createdBy, createdOn, modifiedBy, modifiedOn, version
    );

  }

  public void freeze(UserNo frozenBy) {
    if (status == UserStatus.FROZEN) {
      throw new DomainException(UserError.USER_ALREADY_FROZEN);
    }
    status = UserStatus.FROZEN;
    registerDomainEvent(UserFrozen.of(id(), frozenBy));
  }

  public void activate(UserNo activatedBy) {
    if (status == UserStatus.ACTIVE) {
      return;
    }
    this.status = UserStatus.ACTIVE;
    registerDomainEvent(UserActivated.of(this.id(), activatedBy));
  }

  public void disable(UserNo disabledBy) {
    if (status == UserStatus.DISABLED) {
      return;
    }
    this.status = UserStatus.DISABLED;
    registerDomainEvent(UserDisabled.of(this.id(), disabledBy));
  }

  public boolean isActive() {
    return status == UserStatus.ACTIVE;
  }

  public void changeIdentityDocument(
    IdentityDocument newDocument
  ) {
    Objects.requireNonNull(
      newDocument,
      "identity document required"
    );
    this.identityDocument = newDocument;
    validateInvariants();
  }

  public void changeMobile(
    Mobile mobile
  ) {
    this.mobile =
      Objects.requireNonNull(
        mobile,
        "mobile required"
      );
    validateInvariants();
  }

  public void changeEmail(
    Email email
  ) {
    this.email = email;
  }

  public void changeTelephone(
    Telephone telephone
  ) {
    this.telephone = telephone;
  }

  public void changeAddress(
    Address address
  ) {
    this.address = address;
  }

  public void changePostalCode(
    PostalCode postalCode
  ) {
    this.postalCode = postalCode;
  }

  @Override
  protected void validateInvariants() {
    if (identityDocument == null) {
      throw new DomainException(UserError.IDENTITY_DOCUMENT_REQUIRED);
    }
    if (identityDocument.type() != IdentityType.ID_CARD) {
      throw new DomainException(UserError.ONLY_ID_CARD_SUPPORTED);
    }

    if (mobile == null) {
      throw new DomainException(UserError.MOBILE_REQUIRED);
    }

    if (status == null) {
      throw new DomainException(UserError.USER_STATUS_REQUIRED);
    }
  }
}
