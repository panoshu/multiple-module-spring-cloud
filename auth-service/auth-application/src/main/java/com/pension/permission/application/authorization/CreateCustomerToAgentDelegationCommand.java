package com.pension.permission.application.authorization;



import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.valueobject.Permission;

import java.util.Set;

/**
 * 企业在网上渠道自助发起、把自己名下计划的权限委托给某个经办人(可能来自其他企业)
 */
public record CreateCustomerToAgentDelegationCommand(
  CustomerNo servingCustomerId,
  boolean inheritable,
  UserNo delegatedAccount,
  Set<Permission> permissions,
  UserNo createdBy
) {
}
