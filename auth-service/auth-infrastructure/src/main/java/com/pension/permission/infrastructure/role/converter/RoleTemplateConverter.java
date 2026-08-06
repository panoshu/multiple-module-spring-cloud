package com.pension.permission.infrastructure.role.converter;

import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.id.UserNo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pension.permission.domain.authorization.valueobject.Permission;
import com.pension.permission.domain.role.aggregate.RoleTemplate;
import com.pension.permission.domain.role.enumeration.RoleTemplateScopeDimension;
import com.pension.permission.domain.role.enumeration.RoleTemplateStatus;
import com.pension.permission.infrastructure.role.entity.RoleTemplateDO;
import com.pension.permission.types.RoleCode;
import com.pension.permission.types.RoleTemplateId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

/**
 * 角色权限模板转换器.
 *
 * <p>负责 {@link RoleTemplate} 领域聚合根与 {@link RoleTemplateDO} 持久化对象之间的转换。
 * 需要处理 {@link Permission} 集合的 JSON 序列化与反序列化。</p>
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public abstract class RoleTemplateConverter {

  @Autowired
  protected ObjectMapper objectMapper;

  // ===============================
  // toDO: 领域对象 → DO
  // ===============================

  @Mapping(target = "id", expression = "java(template.id() != null ? template.id().value() : null)")
  @Mapping(target = "roleCode", expression = "java(template.roleCode() != null ? template.roleCode().value() : null)")
  @Mapping(target = "scopeDimension", expression = "java(template.scopeDimension() != null ? template.scopeDimension().name() : null)")
  @Mapping(target = "scopeValue", expression = "java(template.scopeValue())")
  @Mapping(target = "permissions", expression = "java(toPermissionsJson(template.permissions()))")
  @Mapping(target = "status", expression = "java(template.status() != null ? template.status().name() : null)")
  @Mapping(target = "createdBy", expression = "java(template.createdBy() != null ? template.createdBy().value() : null)")
  @Mapping(target = "updatedBy", expression = "java(template.updatedBy() != null ? template.updatedBy().value() : null)")
  @Mapping(target = "createTime", expression = "java(template.createdAt())")
  @Mapping(target = "updateTime", expression = "java(template.updatedAt())")
  @Mapping(target = "version", expression = "java(template.version() != null ? (int) template.version().value() : null)")
  @Mapping(target = "deleted", constant = "false")
  public abstract RoleTemplateDO toDO(RoleTemplate template);

  // ===============================
  // toDomain: DO → 领域对象
  // ===============================

  public RoleTemplate toDomain(RoleTemplateDO doObj) {
    if (doObj == null) {
      return null;
    }

    return RoleTemplate.reconstitute(
      toRoleTemplateId(doObj.getId()),
      toUserNo(doObj.getCreatedBy()),
      toUserNo(doObj.getUpdatedBy()),
      doObj.getCreateTime(),
      doObj.getUpdateTime(),
      toVersion(doObj.getVersion()),
      toRoleCode(doObj.getRoleCode()),
      toScopeDimension(doObj.getScopeDimension()),
      doObj.getScopeValue(),
      toPermissionSet(doObj.getPermissions()),
      toStatus(doObj.getStatus())
    );
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

  @Named("toRoleTemplateId")
  protected RoleTemplateId toRoleTemplateId(String value) {
    return value != null ? new RoleTemplateId(value) : null;
  }

  @Named("toRoleCode")
  protected RoleCode toRoleCode(String value) {
    return value != null ? new RoleCode(value) : null;
  }

  @Named("toScopeDimension")
  protected RoleTemplateScopeDimension toScopeDimension(String name) {
    return name != null ? RoleTemplateScopeDimension.valueOf(name) : null;
  }

  @Named("toStatus")
  protected RoleTemplateStatus toStatus(String name) {
    return name != null ? RoleTemplateStatus.valueOf(name) : null;
  }

  // ===============================
  // Permission 集合 JSON 序列化
  // ===============================

  @Named("toPermissionsJson")
  protected String toPermissionsJson(Set<Permission> permissions) {
    if (permissions == null || permissions.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(permissions);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("序列化权限集合失败", e);
    }
  }

  protected Set<Permission> toPermissionSet(String json) {
    if (json == null || json.isBlank()) {
      return Set.of();
    }
    try {
      Set<Permission> permissions = objectMapper.readValue(json, new TypeReference<>() {});
      return permissions != null ? permissions : Set.of();
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("反序列化权限集合失败", e);
    }
  }
}
