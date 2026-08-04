package com.pension.permission.domain.authorization.valueobject.subject;


import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.spi.PlanMembershipLookup;
import com.pension.permission.types.RoleCode;

public record PlanRoleSubject(PlanNo planNo, RoleCode roleCode) implements GrantSubject {
  @Override
  public boolean covers(UserNo identity, PlanMembershipLookup membershipLookup) {
    return membershipLookup.hasRole(identity, planNo, roleCode);
  }
}
