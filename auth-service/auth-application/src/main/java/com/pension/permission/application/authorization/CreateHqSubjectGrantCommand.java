package com.pension.permission.application.authorization;



import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.enumeration.Effect;
import com.pension.permission.domain.authorization.valueobject.Permission;
import com.pension.permission.domain.authorization.valueobject.ScopeRule;

import java.util.List;
import java.util.Set;

/**
 * 总部给具体账号(运营人员/经办)的个别授权或DENY例外
 */
public record CreateHqSubjectGrantCommand(
  Set<UserNo> accountIds,
  List<ScopeRule> scopeRules,
  Set<Permission> permissions,
  Effect effect,
  UserNo createdBy
) {
}
