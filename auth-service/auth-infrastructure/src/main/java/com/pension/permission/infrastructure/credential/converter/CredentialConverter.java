package com.pension.permission.infrastructure.credential.converter;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.example.shared.valueobject.ValidityPeriod;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pension.permission.domain.credential.aggregate.Credential;
import com.pension.permission.domain.credential.aggregate.PasswordCredential;
import com.pension.permission.domain.credential.aggregate.UKeyCredential;
import com.pension.permission.domain.credential.enumeration.CredentialStatus;
import com.pension.permission.domain.credential.enumeration.CredentialType;
import com.pension.permission.domain.credential.valueobject.owner.CredentialOwner;
import com.pension.permission.domain.credential.valueobject.owner.CustomerCredentialOwner;
import com.pension.permission.domain.credential.valueobject.owner.PlanCredentialOwner;
import com.pension.permission.domain.credential.valueobject.owner.UserCredentialOwner;
import com.pension.permission.infrastructure.credential.entity.CredentialDO;
import com.pension.permission.types.CredentialId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 凭证转换器.
 *
 * <p>负责 {@link Credential} 领域聚合根与 {@link CredentialDO} 持久化对象之间的转换。
 * 需要处理 {@link CredentialOwner} sealed interface、{@link ValidityPeriod} record、
 * {@link Set}&lt;{@link AnnuityChannel}&gt; JSON 序列化等复杂值对象的转换，
 * 并根据 credentialType 分派到对应子类的 reconstitute 方法。</p>
 *
 * @author auth-service
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public abstract class CredentialConverter {

  @Autowired
  protected ObjectMapper objectMapper;

  // ===============================
  // toDO: 领域对象 → DO
  // ===============================

  @Mapping(target = "id", expression = "java(credential.id() != null ? credential.id().value() : null)")
  @Mapping(target = "credentialType", expression = "java(credential.type() != null ? credential.type().name() : null)")
  @Mapping(target = "ownerType", expression = "java(toOwnerType(credential.owner()))")
  @Mapping(target = "ownerId", expression = "java(toOwnerId(credential.owner()))")
  @Mapping(target = "applicableChannels", expression = "java(toChannelsJson(credential.applicableChannels()))")
  @Mapping(target = "validityStart", expression = "java(toValidityStart(credential.validityPeriod()))")
  @Mapping(target = "validityEnd", expression = "java(toValidityEnd(credential.validityPeriod()))")
  @Mapping(target = "status", expression = "java(credential.status() != null ? credential.status().name() : null)")
  @Mapping(target = "userNo", expression = "java(toUserNoValue(credential))")
  @Mapping(target = "passwordHash", expression = "java(toPasswordHashValue(credential))")
  @Mapping(target = "keySerial", expression = "java(toKeySerialValue(credential))")
  @Mapping(target = "createdBy", expression = "java(credential.createdBy() != null ? credential.createdBy().value() : null)")
  @Mapping(target = "updatedBy", expression = "java(credential.updatedBy() != null ? credential.updatedBy().value() : null)")
  @Mapping(target = "createTime", expression = "java(credential.createdAt())")
  @Mapping(target = "updateTime", expression = "java(credential.updatedAt())")
  @Mapping(target = "version", expression = "java(credential.version() != null ? (int) credential.version().value() : null)")
  @Mapping(target = "deleted", constant = "false")
  public abstract CredentialDO toDO(Credential credential);

  // ===============================
  // toDomain: DO → 领域对象
  // ===============================

  public Credential toDomain(CredentialDO doObj) {
    if (doObj == null) {
      return null;
    }

    CredentialType type = CredentialType.valueOf(doObj.getCredentialType());
    CredentialOwner owner = toCredentialOwner(doObj.getOwnerType(), doObj.getOwnerId());
    Set<AnnuityChannel> channels = toChannels(doObj.getApplicableChannels());
    CredentialStatus status = CredentialStatus.valueOf(doObj.getStatus());
    ValidityPeriod validityPeriod = toValidityPeriod(doObj.getValidityStart(), doObj.getValidityEnd());
    UserNo createdBy = toUserNo(doObj.getCreatedBy());
    UserNo updatedBy = toUserNo(doObj.getUpdatedBy());
    Version version = toVersion(doObj.getVersion());

    return switch (type) {
      case PASSWORD -> PasswordCredential.reconstitute(
        new CredentialId(doObj.getId()),
        owner,
        channels,
        status,
        validityPeriod,
        createdBy,
        doObj.getCreateTime(),
        updatedBy,
        doObj.getUpdateTime(),
        version,
        toUserNo(doObj.getUserNo()),
        doObj.getPasswordHash()
      );
      case U_KEY -> UKeyCredential.reconstitute(
        new CredentialId(doObj.getId()),
        owner,
        channels,
        status,
        validityPeriod,
        createdBy,
        doObj.getCreateTime(),
        updatedBy,
        doObj.getUpdateTime(),
        version,
        doObj.getKeySerial()
      );
      default -> throw new IllegalStateException("不支持的凭证类型: " + type);
    };
  }

  // ===============================
  // CredentialOwner 转换
  // ===============================

  /**
   * 将 CredentialOwner 转换为持有者类型字符串（子类 SimpleName），供 Repository 查询使用。
   */
  public String toOwnerType(CredentialOwner owner) {
    return owner != null ? owner.getClass().getSimpleName() : null;
  }

  /**
   * 将 CredentialOwner 转换为持有者ID字符串，供 Repository 查询使用。
   */
  public String toOwnerId(CredentialOwner owner) {
    if (owner == null) {
      return null;
    }
    if (owner instanceof UserCredentialOwner uco) {
      return uco.userNo().value();
    }
    if (owner instanceof CustomerCredentialOwner cco) {
      return cco.customerNo().value();
    }
    if (owner instanceof PlanCredentialOwner pco) {
      return pco.planNo().value();
    }
    throw new IllegalStateException("未知的 CredentialOwner 类型: " + owner.getClass().getName());
  }

  protected CredentialOwner toCredentialOwner(String ownerType, String ownerId) {
    if (ownerType == null || ownerId == null) {
      return null;
    }
    return switch (ownerType) {
      case "UserCredentialOwner" -> new UserCredentialOwner(UserNo.of(ownerId));
      case "CustomerCredentialOwner" -> new CustomerCredentialOwner(CustomerNo.of(ownerId));
      case "PlanCredentialOwner" -> new PlanCredentialOwner(PlanNo.of(ownerId));
      default -> throw new IllegalStateException("未知的 CredentialOwner 类型: " + ownerType);
    };
  }

  // ===============================
  // AnnuityChannel 集合转换
  // ===============================

  @Named("toChannelsJson")
  protected String toChannelsJson(Set<AnnuityChannel> channels) {
    if (channels == null || channels.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(channels);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("序列化适用渠道失败", e);
    }
  }

  protected Set<AnnuityChannel> toChannels(String json) {
    if (json == null || json.isBlank()) {
      return Set.of();
    }
    try {
      Set<AnnuityChannel> channels = objectMapper.readValue(json, new TypeReference<>() {
      });
      return channels != null ? channels : Set.of();
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("反序列化适用渠道失败", e);
    }
  }

  // ===============================
  // ValidityPeriod 转换
  // ===============================

  @Named("toValidityStart")
  protected LocalDateTime toValidityStart(ValidityPeriod period) {
    return period != null ? period.start() : null;
  }

  @Named("toValidityEnd")
  protected LocalDateTime toValidityEnd(ValidityPeriod period) {
    return period != null ? period.end() : null;
  }

  protected ValidityPeriod toValidityPeriod(LocalDateTime start, LocalDateTime end) {
    return new ValidityPeriod(start, end);
  }

  // ===============================
  // 基础类型转换
  // ===============================

  @Named("toUserNo")
  protected UserNo toUserNo(String value) {
    return value != null ? UserNo.of(value) : null;
  }

  @Named("toVersion")
  protected Version toVersion(Integer value) {
    return value != null ? Version.of(value.longValue()) : null;
  }

  // ===============================
  // 子类专属字段提取
  // ===============================

  @Named("toUserNoValue")
  protected String toUserNoValue(Credential credential) {
    if (credential instanceof PasswordCredential pc) {
      return pc.userNo() != null ? pc.userNo().value() : null;
    }
    return null;
  }

  @Named("toPasswordHashValue")
  protected String toPasswordHashValue(Credential credential) {
    if (credential instanceof PasswordCredential pc) {
      return pc.passwordHash();
    }
    return null;
  }

  @Named("toKeySerialValue")
  protected String toKeySerialValue(Credential credential) {
    if (credential instanceof UKeyCredential uc) {
      return uc.keySerial();
    }
    return null;
  }
}
