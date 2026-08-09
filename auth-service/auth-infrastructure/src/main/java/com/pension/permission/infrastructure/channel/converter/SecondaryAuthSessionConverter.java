package com.pension.permission.infrastructure.channel.converter;

import com.example.shared.contactinfo.Mobile;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pension.permission.domain.authorization.valueobject.Permission;
import com.pension.permission.domain.channel.aggregate.SecondaryAuthSession;
import com.pension.permission.domain.channel.enumeration.SecondaryAuthStatus;
import com.pension.permission.domain.channel.valueobject.EffectiveIdentity;
import com.pension.permission.domain.channel.valueobject.PermissionSnapshot;
import com.pension.permission.domain.channel.valueobject.VerificationCode;
import com.pension.permission.domain.credential.valueobject.owner.CredentialOwner;
import com.pension.permission.domain.credential.valueobject.owner.CustomerCredentialOwner;
import com.pension.permission.domain.credential.valueobject.owner.PlanCredentialOwner;
import com.pension.permission.domain.credential.valueobject.owner.UserCredentialOwner;
import com.pension.permission.infrastructure.channel.entity.SecondaryAuthSessionDO;
import com.pension.permission.types.SecondaryAuthSessionId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 二次授权会话转换器.
 *
 * <p>负责 {@link SecondaryAuthSession} 领域聚合根与 {@link SecondaryAuthSessionDO} 持久化对象之间的转换。
 * 需要处理 {@link CredentialOwner} sealed interface、{@link VerificationCode}、
 * {@link PermissionSnapshot} 等复杂值对象的序列化与反序列化。</p>
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public abstract class SecondaryAuthSessionConverter {

  private static final String OWNER_TYPE_USER = "USER";
  private static final String OWNER_TYPE_CUSTOMER = "CUSTOMER";
  private static final String OWNER_TYPE_PLAN = "PLAN";

  @Autowired
  protected ObjectMapper objectMapper;

  // ===============================
  // toDO: 领域对象 → DO
  // ===============================

  @Mapping(target = "id", expression = "java(session.id() != null ? session.id().value() : null)")
  @Mapping(target = "tellerAccountId", expression = "java(session.tellerAccountId() != null ? session.tellerAccountId().value() : null)")
  @Mapping(target = "approverAccountId", expression = "java(session.approverAccountId() != null ? session.approverAccountId().value() : null)")
  @Mapping(target = "credentialOwnerType", expression = "java(toCredentialOwnerType(session.credentialOwner()))")
  @Mapping(target = "credentialOwnerId", expression = "java(toCredentialOwnerId(session.credentialOwner()))")
  @Mapping(target = "approverMobile", expression = "java(session.approverMobile() != null ? session.approverMobile().value() : null)")
  @Mapping(target = "planId", expression = "java(session.planId() != null ? session.planId().value() : null)")
  @Mapping(target = "verificationCodeHash", expression = "java(toVerificationCodeHash(session.verificationCode()))")
  @Mapping(target = "verificationSentAt", expression = "java(toVerificationSentAt(session.verificationCode()))")
  @Mapping(target = "verificationExpiresAt", expression = "java(toVerificationExpiresAt(session.verificationCode()))")
  @Mapping(target = "verificationRemaining", expression = "java(toVerificationRemaining(session.verificationCode()))")
  @Mapping(target = "effectiveIdentityId", expression = "java(toEffectiveIdentityId(session.effectiveIdentity()))")
  @Mapping(target = "effectiveIdentityActing", expression = "java(toEffectiveIdentityActing(session.effectiveIdentity()))")
  @Mapping(target = "effectiveViaSecondary", expression = "java(toEffectiveViaSecondary(session.effectiveIdentity()))")
  @Mapping(target = "snapshotPermissions", expression = "java(toSnapshotPermissionsJson(session.permissionSnapshot()))")
  @Mapping(target = "snapshotFrozenAt", expression = "java(toSnapshotFrozenAt(session.permissionSnapshot()))")
  @Mapping(target = "snapshotExpiresAt", expression = "java(toSnapshotExpiresAt(session.permissionSnapshot()))")
  @Mapping(target = "status", expression = "java(session.status() != null ? session.status().name() : null)")
  @Mapping(target = "initiatedAt", expression = "java(session.initiatedAt())")
  @Mapping(target = "pendingExpiresAt", expression = "java(session.pendingExpiresAt())")
  @Mapping(target = "authorizedAt", expression = "java(session.authorizedAt())")
  @Mapping(target = "expiresAt", expression = "java(session.expiresAt())")
  @Mapping(target = "revokeReason", expression = "java(session.revokeReason())")
  @Mapping(target = "createdBy", expression = "java(session.createdBy() != null ? session.createdBy().value() : null)")
  @Mapping(target = "updatedBy", expression = "java(session.updatedBy() != null ? session.updatedBy().value() : null)")
  @Mapping(target = "createTime", expression = "java(session.createdAt())")
  @Mapping(target = "updateTime", expression = "java(session.updatedAt())")
  @Mapping(target = "version", expression = "java(session.version() != null ? (int) session.version().value() : null)")
  @Mapping(target = "deleted", constant = "false")
  public abstract SecondaryAuthSessionDO toDO(SecondaryAuthSession session);

  // ===============================
  // toDomain: DO → 领域对象
  // ===============================

  public SecondaryAuthSession toDomain(SecondaryAuthSessionDO doObj) {
    if (doObj == null) {
      return null;
    }

    return SecondaryAuthSession.reconstitute(
      new SecondaryAuthSession.ReconstituteSnapshot(
        new SecondaryAuthSessionId(doObj.getId()),
        toUserNo(doObj.getCreatedBy()),
        toUserNo(doObj.getUpdatedBy()),
        doObj.getCreateTime(),
        doObj.getUpdateTime(),
        toVersion(doObj.getVersion()),

        toUserNo(doObj.getTellerAccountId()),
        toUserNo(doObj.getApproverAccountId()),
        toCredentialOwner(doObj),
        new Mobile(doObj.getApproverMobile()),
        toPlanNo(doObj.getPlanId()),

        toVerificationCode(doObj),
        toEffectiveIdentity(doObj),
        toPermissionSnapshot(doObj),

        toSecondaryAuthStatus(doObj.getStatus()),
        doObj.getInitiatedAt(),
        doObj.getPendingExpiresAt(),
        doObj.getAuthorizedAt(),
        doObj.getExpiresAt(),
        doObj.getRevokeReason()
      )
    );
  }

  // ===============================
  // 基础类型转换
  // ===============================

  @Named("toUserNo")
  protected UserNo toUserNo(String value) {
    return value != null ? UserNo.of(value) : null;
  }

  @Named("toPlanNo")
  protected PlanNo toPlanNo(String value) {
    return value != null ? PlanNo.of(value) : null;
  }

  @Named("toVersion")
  protected Version toVersion(Integer value) {
    return value != null ? Version.of(value.longValue()) : null;
  }

  @Named("toSecondaryAuthStatus")
  protected SecondaryAuthStatus toSecondaryAuthStatus(String name) {
    return name != null ? SecondaryAuthStatus.valueOf(name) : null;
  }

  // ===============================
  // CredentialOwner 转换
  // ===============================

  @Named("toCredentialOwnerType")
  protected String toCredentialOwnerType(CredentialOwner owner) {
    if (owner == null) {
      return null;
    }
    if (owner instanceof UserCredentialOwner) {
      return OWNER_TYPE_USER;
    }
    if (owner instanceof CustomerCredentialOwner) {
      return OWNER_TYPE_CUSTOMER;
    }
    if (owner instanceof PlanCredentialOwner) {
      return OWNER_TYPE_PLAN;
    }
    throw new IllegalStateException("未知的 CredentialOwner 类型: " + owner.getClass().getName());
  }

  @Named("toCredentialOwnerId")
  protected String toCredentialOwnerId(CredentialOwner owner) {
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

  protected CredentialOwner toCredentialOwner(SecondaryAuthSessionDO doObj) {
    String type = doObj.getCredentialOwnerType();
    String id = doObj.getCredentialOwnerId();
    if (type == null || id == null) {
      return null;
    }
    return switch (type) {
      case OWNER_TYPE_USER -> new UserCredentialOwner(UserNo.of(id));
      case OWNER_TYPE_CUSTOMER -> new CustomerCredentialOwner(CustomerNo.of(id));
      case OWNER_TYPE_PLAN -> new PlanCredentialOwner(PlanNo.of(id));
      default -> throw new IllegalStateException("未知的 CredentialOwner 类型: " + type);
    };
  }

  // ===============================
  // VerificationCode 转换
  // ===============================

  @Named("toVerificationCodeHash")
  protected String toVerificationCodeHash(VerificationCode code) {
    return code != null ? code.hashedCode() : null;
  }

  @Named("toVerificationSentAt")
  protected LocalDateTime toVerificationSentAt(VerificationCode code) {
    return code != null ? code.sentAt() : null;
  }

  @Named("toVerificationExpiresAt")
  protected LocalDateTime toVerificationExpiresAt(VerificationCode code) {
    return code != null ? code.expiresAt() : null;
  }

  @Named("toVerificationRemaining")
  protected Integer toVerificationRemaining(VerificationCode code) {
    return code != null ? code.remainingAttempts() : null;
  }

  protected VerificationCode toVerificationCode(SecondaryAuthSessionDO doObj) {
    if (doObj.getVerificationCodeHash() == null) {
      return null;
    }
    return new VerificationCode(
      doObj.getVerificationCodeHash(),
      doObj.getVerificationSentAt(),
      doObj.getVerificationExpiresAt(),
      doObj.getVerificationRemaining() != null ? doObj.getVerificationRemaining() : 0
    );
  }

  // ===============================
  // EffectiveIdentity 转换
  // ===============================

  @Named("toEffectiveIdentityId")
  protected String toEffectiveIdentityId(EffectiveIdentity identity) {
    return identity != null && identity.identityAccountId() != null
      ? identity.identityAccountId().value()
      : null;
  }

  @Named("toEffectiveIdentityActing")
  protected String toEffectiveIdentityActing(EffectiveIdentity identity) {
    return identity != null && identity.actingAccountId() != null
      ? identity.actingAccountId().value()
      : null;
  }

  @Named("toEffectiveViaSecondary")
  protected Boolean toEffectiveViaSecondary(EffectiveIdentity identity) {
    return identity != null ? identity.viaSecondaryAuth() : null;
  }

  protected EffectiveIdentity toEffectiveIdentity(SecondaryAuthSessionDO doObj) {
    if (doObj.getEffectiveIdentityId() == null) {
      return null;
    }
    return new EffectiveIdentity(
      UserNo.of(doObj.getEffectiveIdentityId()),
      UserNo.of(doObj.getEffectiveIdentityActing()),
      Boolean.TRUE.equals(doObj.getEffectiveViaSecondary())
    );
  }

  // ===============================
  // PermissionSnapshot 转换
  // ===============================

  @Named("toSnapshotPermissionsJson")
  protected String toSnapshotPermissionsJson(PermissionSnapshot snapshot) {
    if (snapshot == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(snapshot.permissions());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("序列化权限快照失败", e);
    }
  }

  @Named("toSnapshotFrozenAt")
  protected LocalDateTime toSnapshotFrozenAt(PermissionSnapshot snapshot) {
    return snapshot != null ? snapshot.frozenAt() : null;
  }

  @Named("toSnapshotExpiresAt")
  protected LocalDateTime toSnapshotExpiresAt(PermissionSnapshot snapshot) {
    return snapshot != null ? snapshot.expiresAt() : null;
  }

  protected PermissionSnapshot toPermissionSnapshot(SecondaryAuthSessionDO doObj) {
    if (doObj.getSnapshotPermissions() == null || doObj.getSnapshotPermissions().isBlank()) {
      return null;
    }
    try {
      Set<Permission> permissions = objectMapper.readValue(
        doObj.getSnapshotPermissions(),
        new TypeReference<>() {
        }
      );
      if (permissions == null || permissions.isEmpty()) {
        return null;
      }
      return new PermissionSnapshot(
        permissions,
        doObj.getSnapshotFrozenAt(),
        doObj.getSnapshotExpiresAt()
      );
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("反序列化权限快照失败", e);
    }
  }
}
