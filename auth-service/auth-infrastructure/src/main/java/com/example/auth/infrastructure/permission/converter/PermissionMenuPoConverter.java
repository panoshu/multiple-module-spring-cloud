package com.example.auth.infrastructure.permission.converter;

import com.example.core.types.UserNo;
import com.example.auth.domain.authorization.valueobject.ActionCode;
import com.example.auth.domain.authorization.valueobject.BusinessCode;
import com.example.auth.domain.permission.aggregate.PermissionMenu;
import com.example.auth.domain.permission.aggregate.valueobject.PermissionBinding;
import com.example.auth.domain.permission.enumeration.MenuCategory;
import com.example.auth.domain.permission.enumeration.MenuVisibility;
import com.example.auth.infrastructure.permission.entity.MenuPermissionBindingDO;
import com.example.auth.infrastructure.permission.entity.PermissionMenuDO;
import com.example.auth.types.MenuCode;
import com.example.auth.types.PermissionBindingId;
import com.example.auth.types.PermissionMenuId;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

/**
 * 权限菜单基础设施层转换器.
 *
 * @author auth-service
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public abstract class PermissionMenuPoConverter {

  public PermissionMenuDO toDO(PermissionMenu menu) {
    if (menu == null) {
      return null;
    }
    PermissionMenuDO doObj = new PermissionMenuDO();
    doObj.setId(menu.id() != null ? menu.id().value() : null);
    doObj.setMenuCode(menu.menuCode() != null ? menu.menuCode().value() : null);
    doObj.setMenuName(menu.menuName());
    doObj.setMenuPath(menu.menuPath());
    doObj.setParentId(menu.parentId() != null ? menu.parentId().value() : null);
    doObj.setDepth(menu.depth());
    doObj.setSortOrder(menu.sortOrder());
    doObj.setIcon(menu.icon());
    doObj.setRoute(menu.route());
    doObj.setMenuCategory(menu.menuCategory() != null ? menu.menuCategory().name() : null);
    doObj.setVisibility(menu.visibility() != null ? menu.visibility().name() : null);
    doObj.setEnabled(menu.enabled());
    doObj.setCreatedBy(menu.createdBy());
    doObj.setUpdatedBy(menu.updatedBy());
    doObj.setCreateTime(menu.createdAt());
    doObj.setUpdateTime(menu.updatedAt());
    doObj.setVersion(menu.version() != null ? (int) menu.version().value() : null);
    doObj.setDeleted(false);
    return doObj;
  }

  public PermissionMenu toDomain(PermissionMenuDO doObj, List<PermissionBinding> bindings) {
    if (doObj == null) {
      return null;
    }
    return PermissionMenu.reconstitute(
      doObj.getId(),
      doObj.getMenuCode(),
      doObj.getMenuName(),
      doObj.getMenuPath(),
      doObj.getParentId(),
      doObj.getDepth() != null ? doObj.getDepth() : 0,
      doObj.getSortOrder() != null ? doObj.getSortOrder() : 0,
      doObj.getIcon(),
      doObj.getRoute(),
      doObj.getMenuCategory() != null ? MenuCategory.valueOf(doObj.getMenuCategory()) : null,
      doObj.getVisibility() != null ? MenuVisibility.valueOf(doObj.getVisibility()) : null,
      doObj.getEnabled() != null ? doObj.getEnabled() : true,
      bindings,
      doObj.getCreatedBy(),
      doObj.getUpdatedBy(),
      doObj.getCreateTime(),
      doObj.getUpdateTime()
    );
  }

  public MenuPermissionBindingDO toBindingDO(PermissionBinding binding, String menuId) {
    if (binding == null) {
      return null;
    }
    MenuPermissionBindingDO doObj = new MenuPermissionBindingDO();
    doObj.setId(binding.bindingId().value());
    doObj.setMenuId(menuId);
    doObj.setBusinessCode(binding.businessCode().value());
    doObj.setActionCode(binding.actionCode() != null ? binding.actionCode().value() : null);
    doObj.setCreatedBy(null);
    doObj.setUpdatedBy(null);
    doObj.setDeleted(false);
    doObj.setVersion(0);
    return doObj;
  }

  public PermissionBinding toBindingDomain(MenuPermissionBindingDO doObj) {
    if (doObj == null) {
      return null;
    }
    return new PermissionBinding(
      new PermissionBindingId(doObj.getId()),
      new BusinessCode(doObj.getBusinessCode()),
      doObj.getActionCode() != null ? new ActionCode(doObj.getActionCode()) : null
    );
  }

  public List<MenuPermissionBindingDO> toBindingDOList(List<PermissionBinding> bindings, String menuId) {
    if (bindings == null) {
      return List.of();
    }
    return bindings.stream().map(b -> toBindingDO(b, menuId)).toList();
  }

  public List<PermissionBinding> toBindingDomainList(List<MenuPermissionBindingDO> doList) {
    if (doList == null) {
      return List.of();
    }
    return doList.stream().map(this::toBindingDomain).toList();
  }

  protected UserNo toUserNo(String value) {
    return value != null ? UserNo.of(value) : null;
  }
}
