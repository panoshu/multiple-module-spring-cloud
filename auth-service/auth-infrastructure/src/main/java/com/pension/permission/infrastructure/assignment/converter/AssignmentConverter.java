package com.pension.permission.infrastructure.assignment.converter;

import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.assignment.aggregate.AgentIdentityAssignment;
import com.pension.permission.domain.assignment.enumeration.AssignmentStatus;
import com.pension.permission.infrastructure.assignment.entity.AssignmentDO;
import com.pension.permission.types.AssignmentId;
import com.pension.permission.types.AssignmentScopeDimension;
import com.pension.permission.types.RoleCode;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

/**
 * 账号身份分配转换器.
 *
 * <p>负责 {@link AgentIdentityAssignment} 领域聚合根与 {@link AssignmentDO} 持久化对象之间的转换。</p>
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AssignmentConverter {

  /**
   * 领域对象 → DO.
   */
  @Mapping(target = "id", expression = "java(assignment.id() != null ? assignment.id().value() : null)")
  @Mapping(target = "userNo", expression = "java(assignment.userNo() != null ? assignment.userNo().value() : null)")
  @Mapping(target = "roleCode", expression = "java(assignment.roleCode() != null ? assignment.roleCode().value() : null)")
  @Mapping(target = "scopeDimension", expression = "java(assignment.scopeDimension() != null ? assignment.scopeDimension().name() : null)")
  @Mapping(target = "scopeValue", expression = "java(assignment.scopeValue())")
  @Mapping(target = "inheritable", expression = "java(assignment.isInheritable())")
  @Mapping(target = "status", expression = "java(assignment.isActive() ? com.pension.permission.domain.assignment.enumeration.AssignmentStatus.ACTIVE.name() : com.pension.permission.domain.assignment.enumeration.AssignmentStatus.DEACTIVATED.name())")
  @Mapping(target = "createdBy", expression = "java(assignment.createdBy() != null ? assignment.createdBy().value() : null)")
  @Mapping(target = "updatedBy", expression = "java(assignment.updatedBy() != null ? assignment.updatedBy().value() : null)")
  @Mapping(target = "createTime", expression = "java(assignment.createdAt())")
  @Mapping(target = "updateTime", expression = "java(assignment.updatedAt())")
  @Mapping(target = "version", expression = "java(assignment.version() != null ? (int) assignment.version().value() : null)")
  @Mapping(target = "deleted", constant = "false")
  AssignmentDO toDO(AgentIdentityAssignment assignment);

  /**
   * DO → 领域对象（通过 reconstitute 重建）.
   */
  default AgentIdentityAssignment toDomain(AssignmentDO doObj) {
    if (doObj == null) {
      return null;
    }

    return AgentIdentityAssignment.reconstitute(
      toAssignmentId(doObj.getId()),
      toUserNo(doObj.getCreatedBy()),
      toUserNo(doObj.getUpdatedBy()),
      doObj.getCreateTime(),
      doObj.getUpdateTime(),
      toVersion(doObj.getVersion()),

      toUserNo(doObj.getUserNo()),
      toRoleCode(doObj.getRoleCode()),
      toScopeDimension(doObj.getScopeDimension()),
      doObj.getScopeValue(),
      Boolean.TRUE.equals(doObj.getInheritable()),
      toAssignmentStatus(doObj.getStatus())
    );
  }

  // ========== 类型转换方法 ==========

  @Named("toAssignmentId")
  default AssignmentId toAssignmentId(String value) {
    return value != null ? new AssignmentId(value) : null;
  }

  @Named("toUserNo")
  default UserNo toUserNo(String value) {
    return value != null ? UserNo.of(value) : null;
  }

  @Named("toRoleCode")
  default RoleCode toRoleCode(String value) {
    return value != null ? new RoleCode(value) : null;
  }

  @Named("toVersion")
  default Version toVersion(Integer value) {
    return value != null ? Version.of(value.longValue()) : null;
  }

  @Named("toScopeDimension")
  default AssignmentScopeDimension toScopeDimension(String name) {
    return name != null ? AssignmentScopeDimension.valueOf(name) : null;
  }

  @Named("toAssignmentStatus")
  default AssignmentStatus toAssignmentStatus(String name) {
    return name != null ? AssignmentStatus.valueOf(name) : null;
  }
}
