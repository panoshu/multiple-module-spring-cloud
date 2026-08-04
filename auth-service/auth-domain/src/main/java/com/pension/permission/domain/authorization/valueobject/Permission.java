package com.pension.permission.domain.authorization.valueobject;

import java.util.Objects;

/**
 * 最小可判定权限单元：业务 + 操作。actionCode 为 null 表示"整个业务"，
 * 用于能力层这种只关心业务开不开通、不区分具体操作的场景。
 */
public record Permission(BusinessCode businessCode, ActionCode actionCode) {

  public Permission {
    Objects.requireNonNull(businessCode, "businessCode");
  }

  public static Permission wholeBusiness(BusinessCode businessCode) {
    return new Permission(businessCode, null);
  }

  /**
   * 本权限是否覆盖(business, action)——actionCode为null时代表该业务下的任意操作都覆盖
   */
  public boolean covers(BusinessCode business, ActionCode action) {
    if (!this.businessCode.equals(business)) {
      return false;
    }
    return this.actionCode == null || this.actionCode.equals(action);
  }
}
