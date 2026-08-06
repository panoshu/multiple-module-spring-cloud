package com.pension.permission.infrastructure.user.converter;

import com.example.shared.contactinfo.Address;
import com.example.shared.contactinfo.Email;
import com.example.shared.contactinfo.Mobile;
import com.example.shared.contactinfo.PostalCode;
import com.example.shared.contactinfo.Telephone;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.id.UserNo;
import com.example.shared.identity.DocumentNumber;
import com.example.shared.identity.IdentityDocument;
import com.example.shared.identity.IdentityType;
import com.pension.permission.domain.user.aggregate.UserAggregate;
import com.pension.permission.domain.user.enumeration.UserStatus;
import com.pension.permission.domain.user.enumeration.UserType;
import com.pension.permission.infrastructure.user.entity.UserDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

/**
 * 用户转换器.
 *
 * <p>负责 {@link UserAggregate} 领域聚合根与 {@link UserDO} 持久化对象之间的转换。</p>
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserConverter {

  /**
   * 领域对象 → DO.
   */
  @Mapping(target = "id", expression = "java(user.id() != null ? user.id().value() : null)")
  @Mapping(target = "userType", expression = "java(user.getUserType() != null ? user.getUserType().name() : null)")
  @Mapping(target = "identityType", expression = "java(toIdentityTypeName(user.getIdentityDocument()))")
  @Mapping(target = "identityNumber", expression = "java(toIdentityNumberValue(user.getIdentityDocument()))")
  @Mapping(target = "mobile", expression = "java(user.getMobile() != null ? user.getMobile().value() : null)")
  @Mapping(target = "email", expression = "java(user.getEmail() != null ? user.getEmail().value() : null)")
  @Mapping(target = "telephoneAreaCode", expression = "java(user.getTelephone() != null ? user.getTelephone().areaCode() : null)")
  @Mapping(target = "telephoneNumber", expression = "java(user.getTelephone() != null ? user.getTelephone().number() : null)")
  @Mapping(target = "telephoneExtension", expression = "java(user.getTelephone() != null ? user.getTelephone().extension() : null)")
  @Mapping(target = "addressCountry", expression = "java(user.getAddress() != null ? user.getAddress().country() : null)")
  @Mapping(target = "addressProvince", expression = "java(user.getAddress() != null ? user.getAddress().province() : null)")
  @Mapping(target = "addressCity", expression = "java(user.getAddress() != null ? user.getAddress().city() : null)")
  @Mapping(target = "addressDistrict", expression = "java(user.getAddress() != null ? user.getAddress().district() : null)")
  @Mapping(target = "addressDetail", expression = "java(user.getAddress() != null ? user.getAddress().detail() : null)")
  @Mapping(target = "postalCode", expression = "java(user.getPostalCode() != null ? user.getPostalCode().value() : null)")
  @Mapping(target = "status", expression = "java(user.getStatus() != null ? user.getStatus().name() : null)")
  @Mapping(target = "createdBy", expression = "java(user.createdBy() != null ? user.createdBy().value() : null)")
  @Mapping(target = "updatedBy", expression = "java(user.updatedBy() != null ? user.updatedBy().value() : null)")
  @Mapping(target = "createTime", expression = "java(user.createdAt())")
  @Mapping(target = "updateTime", expression = "java(user.updatedAt())")
  @Mapping(target = "version", expression = "java(user.version() != null ? (int) user.version().value() : null)")
  @Mapping(target = "deleted", constant = "false")
  UserDO toDO(UserAggregate user);

  /**
   * DO → 领域对象（通过 restore 重建）.
   */
  default UserAggregate toDomain(UserDO doObj) {
    if (doObj == null) {
      return null;
    }

    return UserAggregate.restore(
      UserNo.of(doObj.getId()),
      toUserType(doObj.getUserType()),
      toIdentityDocument(doObj.getIdentityType(), doObj.getIdentityNumber()),
      toMobile(doObj.getMobile()),
      toEmail(doObj.getEmail()),
      toTelephone(doObj),
      toAddress(doObj),
      toPostalCode(doObj.getPostalCode()),
      toUserStatus(doObj.getStatus()),
      toUserNo(doObj.getCreatedBy()),
      doObj.getCreateTime(),
      toUserNo(doObj.getUpdatedBy()),
      doObj.getUpdateTime(),
      toVersion(doObj.getVersion())
    );
  }

  // ========== 类型转换方法 ==========

  @Named("toUserNo")
  default UserNo toUserNo(String value) {
    return value != null ? UserNo.of(value) : null;
  }

  @Named("toVersion")
  default Version toVersion(Integer value) {
    return value != null ? Version.of(value.longValue()) : null;
  }

  @Named("toUserType")
  default UserType toUserType(String name) {
    return name != null ? UserType.valueOf(name) : null;
  }

  @Named("toUserStatus")
  default UserStatus toUserStatus(String name) {
    return name != null ? UserStatus.valueOf(name) : null;
  }

  @Named("toIdentityTypeName")
  default String toIdentityTypeName(IdentityDocument document) {
    return document != null && document.type() != null ? document.type().name() : null;
  }

  @Named("toIdentityNumberValue")
  default String toIdentityNumberValue(IdentityDocument document) {
    return document != null && document.number() != null ? document.number().value() : null;
  }

  default IdentityDocument toIdentityDocument(String typeName, String numberValue) {
    if (typeName == null || numberValue == null) {
      return null;
    }
    return new IdentityDocument(IdentityType.valueOf(typeName), new DocumentNumber(numberValue));
  }

  default Mobile toMobile(String value) {
    return value != null ? new Mobile(value) : null;
  }

  default Email toEmail(String value) {
    return value != null ? new Email(value) : null;
  }

  default PostalCode toPostalCode(String value) {
    return value != null ? new PostalCode(value) : null;
  }

  default Telephone toTelephone(UserDO doObj) {
    if (doObj.getTelephoneAreaCode() == null || doObj.getTelephoneNumber() == null) {
      return null;
    }
    return new Telephone(
      doObj.getTelephoneAreaCode(),
      doObj.getTelephoneNumber(),
      doObj.getTelephoneExtension()
    );
  }

  default Address toAddress(UserDO doObj) {
    if (doObj.getAddressCountry() == null || doObj.getAddressDetail() == null) {
      return null;
    }
    return new Address(
      doObj.getAddressCountry(),
      doObj.getAddressProvince(),
      doObj.getAddressCity(),
      doObj.getAddressDistrict(),
      doObj.getAddressDetail()
    );
  }
}
