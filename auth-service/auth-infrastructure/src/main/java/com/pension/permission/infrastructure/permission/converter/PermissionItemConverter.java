package com.pension.permission.infrastructure.permission.converter;

import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.enumeration.PermissionCategory;
import com.pension.permission.domain.permission.aggregate.PermissionItem;
import com.pension.permission.domain.permission.enumeration.PermissionItemSource;
import com.pension.permission.infrastructure.permission.entity.PermissionItemDO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * 权限点元数据转换器。
 * <p>处理领域对象与 DO 之间的转换，注意 actionCode 可为 null（表示整个业务）。
 *
 * @author auth-service
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public abstract class PermissionItemConverter {

  /**
   * 领域对象 → DO.
   */
  public PermissionItemDO toDO(PermissionItem item) {
    if (item == null) {
      return null;
    }
    PermissionItemDO doObj = new PermissionItemDO();
    doObj.setId(item.id() != null ? item.id().value() : null);
    doObj.setBusinessCode(item.businessCode() != null ? item.businessCode().value() : null);
    doObj.setActionCode(item.actionCode() != null ? item.actionCode().value() : null);
    doObj.setCategory(item.category() != null ? item.category().name() : null);
    doObj.setSource(item.source() != null ? item.source().name() : null);
    doObj.setController(item.controller());
    doObj.setMethod(item.method());
    doObj.setHttpMethod(item.httpMethod());
    doObj.setPath(item.path());
    doObj.setDisplayName(item.displayName());
    doObj.setDescription(item.description());
    doObj.setCategoryGroup(item.categoryGroup());
    doObj.setSortOrder(item.sortOrder());
    doObj.setAutoRegistered(item.autoRegistered());
    doObj.setCreatedBy(item.createdBy() != null ? item.createdBy().value() : null);
    doObj.setUpdatedBy(item.updatedBy() != null ? item.updatedBy().value() : null);
    doObj.setCreateTime(item.createdAt());
    doObj.setUpdateTime(item.updatedAt());
    doObj.setVersion(item.version() != null ? (int) item.version().value() : null);
    doObj.setDeleted(false);
    return doObj;
  }

  /**
   * DO → 领域对象（通过 reconstitute 重建）.
   * <p>注意：reconstitute 不接受 version 与 description，因此 DO 中的这两列在重建时不参与
   * （description 由管理后台维护、version 由乐观锁机制在更新时使用）。
   */
  public PermissionItem toDomain(PermissionItemDO doObj) {
    if (doObj == null) {
      return null;
    }
    return PermissionItem.reconstitute(
      doObj.getId(),
      doObj.getBusinessCode(),
      doObj.getActionCode(),
      doObj.getCategory() != null ? PermissionCategory.valueOf(doObj.getCategory()) : null,
      doObj.getSource() != null ? PermissionItemSource.valueOf(doObj.getSource()) : null,
      doObj.getController(),
      doObj.getMethod(),
      doObj.getHttpMethod(),
      doObj.getPath(),
      doObj.getDisplayName(),
      doObj.getCategoryGroup(),
      doObj.getSortOrder() != null ? doObj.getSortOrder() : 0,
      doObj.getAutoRegistered() != null ? doObj.getAutoRegistered() : false,
      toUserNo(doObj.getCreatedBy()),
      toUserNo(doObj.getUpdatedBy()),
      doObj.getCreateTime(),
      doObj.getUpdateTime()
    );
  }

  /**
   * 字符串 → UserNo，null 安全。
   */
  protected UserNo toUserNo(String value) {
    return value != null ? UserNo.of(value) : null;
  }
}
