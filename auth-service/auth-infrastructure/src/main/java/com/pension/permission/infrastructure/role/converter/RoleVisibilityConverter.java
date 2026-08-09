package com.pension.permission.infrastructure.role.converter;

import com.pension.permission.domain.role.enumeration.RoleTemplateScopeDimension;
import com.pension.permission.domain.role.enumeration.RoleVisibilityMode;
import com.pension.permission.domain.role.valueobject.RoleVisibilityScope;
import com.pension.permission.infrastructure.role.entity.RoleVisibilityDO;
import org.mapstruct.*;

/**
 * 角色可见性范围转换器.
 *
 * <p>负责 {@link RoleVisibilityScope} 值对象与 {@link RoleVisibilityDO} 持久化对象之间的转换。
 * 由于 {@code RoleVisibilityScope} 是值对象（record），不携带 id/createdBy/createdAt/version
 * 等基类字段，这些通用字段由 {@code RoleVisibilityRepositoryImpl} 在持久化时设置。</p>
 */
@Mapper(
  componentModel = MappingConstants.ComponentModel.SPRING,
  unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface RoleVisibilityConverter {

  /**
   * 值对象 → DO.
   *
   * <p>仅映射 dimension/scopeValue/mode 三个业务字段；
   * id/createdBy/createTime/updatedBy/updateTime/version/deleted 由 RepositoryImpl 设置。</p>
   */
  @Mapping(target = "dimension", expression = "java(scope.dimension() != null ? scope.dimension().name() : null)")
  @Mapping(target = "scopeValue", source = "value")
  @Mapping(target = "mode", expression = "java(scope.mode() != null ? scope.mode().name() : null)")
  RoleVisibilityDO toDO(RoleVisibilityScope scope);

  /**
   * DO → 值对象.
   */
  default RoleVisibilityScope toDomain(RoleVisibilityDO doObj) {
    if (doObj == null) {
      return null;
    }
    return new RoleVisibilityScope(
      toDimension(doObj.getDimension()),
      doObj.getScopeValue(),
      toMode(doObj.getMode())
    );
  }

  // ========== 枚举转换 ==========

  @Named("toDimension")
  default RoleTemplateScopeDimension toDimension(String name) {
    return name != null ? RoleTemplateScopeDimension.valueOf(name) : null;
  }

  @Named("toMode")
  default RoleVisibilityMode toMode(String name) {
    return name != null ? RoleVisibilityMode.valueOf(name) : null;
  }
}
