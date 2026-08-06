package com.pension.permission.infrastructure.channel.converter;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.aggregate.Session;
import com.pension.permission.domain.channel.enumeration.SessionStatus;
import com.pension.permission.domain.channel.valueobject.EffectiveIdentity;
import com.pension.permission.infrastructure.channel.entity.SessionDO;
import com.pension.permission.types.SecondaryAuthSessionId;
import com.pension.permission.types.SessionId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

/**
 * 渠道会话转换器.
 *
 * <p>负责 {@link Session} 领域聚合根与 {@link SessionDO} 持久化对象之间的转换。</p>
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SessionConverter {

  /**
   * 领域对象 → DO.
   */
  @Mapping(target = "id", expression = "java(session.id() != null ? session.id().value() : null)")
  @Mapping(target = "primaryAccountId", expression = "java(session.primaryAccountId() != null ? session.primaryAccountId().value() : null)")
  @Mapping(target = "channel", expression = "java(session.channel() != null ? session.channel().name() : null)")
  @Mapping(target = "effectiveIdentityId", expression = "java(toEffectiveIdentityId(session.effectiveIdentity()))")
  @Mapping(target = "effectiveIdentityActing", expression = "java(toEffectiveIdentityActing(session.effectiveIdentity()))")
  @Mapping(target = "effectiveViaSecondary", expression = "java(toEffectiveViaSecondary(session.effectiveIdentity()))")
  @Mapping(target = "secondaryAuthSessionId", expression = "java(session.secondaryAuthSessionId() != null ? session.secondaryAuthSessionId().value() : null)")
  @Mapping(target = "selectedPlanId", expression = "java(session.selectedPlanId() != null ? session.selectedPlanId().value() : null)")
  @Mapping(target = "expiresAt", expression = "java(session.expiresAt())")
  @Mapping(target = "status", expression = "java(session.status() != null ? session.status().name() : null)")
  @Mapping(target = "createdBy", expression = "java(session.createdBy() != null ? session.createdBy().value() : null)")
  @Mapping(target = "updatedBy", expression = "java(session.updatedBy() != null ? session.updatedBy().value() : null)")
  @Mapping(target = "createTime", expression = "java(session.createdAt())")
  @Mapping(target = "updateTime", expression = "java(session.updatedAt())")
  @Mapping(target = "version", expression = "java(session.version() != null ? (int) session.version().value() : null)")
  @Mapping(target = "deleted", constant = "false")
  SessionDO toDO(Session session);

  /**
   * DO → 领域对象（通过 reconstitute 重建）.
   */
  default Session toDomain(SessionDO doObj) {
    if (doObj == null) {
      return null;
    }

    return Session.reconstitute(
      new SessionId(doObj.getId()),
      toUserNo(doObj.getCreatedBy()),
      toUserNo(doObj.getUpdatedBy()),
      doObj.getCreateTime(),
      doObj.getUpdateTime(),
      toVersion(doObj.getVersion()),

      toUserNo(doObj.getPrimaryAccountId()),
      toAnnuityChannel(doObj.getChannel()),
      toEffectiveIdentity(doObj),

      toPlanNo(doObj.getSelectedPlanId()),
      doObj.getExpiresAt(),
      toSessionStatus(doObj.getStatus()),
      toSecondaryAuthSessionId(doObj.getSecondaryAuthSessionId())
    );
  }

  // ========== 类型转换方法 ==========

  @Named("toUserNo")
  default UserNo toUserNo(String value) {
    return value != null ? UserNo.of(value) : null;
  }

  @Named("toPlanNo")
  default PlanNo toPlanNo(String value) {
    return value != null ? PlanNo.of(value) : null;
  }

  @Named("toVersion")
  default Version toVersion(Integer value) {
    return value != null ? Version.of(value.longValue()) : null;
  }

  @Named("toAnnuityChannel")
  default AnnuityChannel toAnnuityChannel(String name) {
    return name != null ? AnnuityChannel.valueOf(name) : null;
  }

  @Named("toSessionStatus")
  default SessionStatus toSessionStatus(String name) {
    return name != null ? SessionStatus.valueOf(name) : null;
  }

  @Named("toSecondaryAuthSessionId")
  default SecondaryAuthSessionId toSecondaryAuthSessionId(String value) {
    return value != null ? new SecondaryAuthSessionId(value) : null;
  }

  // ========== EffectiveIdentity 转换 ==========

  @Named("toEffectiveIdentityId")
  default String toEffectiveIdentityId(EffectiveIdentity identity) {
    return identity != null && identity.identityAccountId() != null
      ? identity.identityAccountId().value()
      : null;
  }

  @Named("toEffectiveIdentityActing")
  default String toEffectiveIdentityActing(EffectiveIdentity identity) {
    return identity != null && identity.actingAccountId() != null
      ? identity.actingAccountId().value()
      : null;
  }

  @Named("toEffectiveViaSecondary")
  default Boolean toEffectiveViaSecondary(EffectiveIdentity identity) {
    return identity != null ? identity.viaSecondaryAuth() : null;
  }

  default EffectiveIdentity toEffectiveIdentity(SessionDO doObj) {
    if (doObj.getEffectiveIdentityId() == null) {
      return null;
    }
    return new EffectiveIdentity(
      UserNo.of(doObj.getEffectiveIdentityId()),
      UserNo.of(doObj.getEffectiveIdentityActing()),
      Boolean.TRUE.equals(doObj.getEffectiveViaSecondary())
    );
  }
}
