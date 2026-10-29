package com.pension.permission.domain.permission.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;
import com.pension.permission.domain.authorization.valueobject.ActionCode;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import com.pension.permission.types.PermissionBindingId;

import java.util.Objects;

/**
 * 菜单-权限绑定值对象。
 * <p>一个菜单节点可绑定多个权限点（OR 语义）。
 * actionCode 为 null 表示"整个业务"通配。
 */
public record PermissionBinding(
  PermissionBindingId bindingId,
  BusinessCode businessCode,
  ActionCode actionCode
) implements ValueObject {

  public PermissionBinding {
    Objects.requireNonNull(bindingId, "bindingId");
    Objects.requireNonNull(businessCode, "businessCode");
  }

  /**
   * 本绑定是否覆盖指定的 (business, action) 权限点。
   */
  public boolean covers(BusinessCode business, ActionCode action) {
    if (!this.businessCode.equals(business)) {
      return false;
    }
    return this.actionCode == null || this.actionCode.equals(action);
  }
}
