package com.example.auth.domain.authorization.valueobject.subject;


import com.example.core.types.PlanNo;
import com.example.core.types.UserNo;
import com.example.auth.domain.authorization.spi.PlanMembershipLookup;
import com.example.auth.types.RoleCode;

public record PlanRoleSubject(PlanNo planNo, RoleCode roleCode) implements GrantSubject {
  @Override
  public boolean covers(UserNo identity, PlanMembershipLookup membershipLookup) {
    return membershipLookup.hasRole(identity, planNo, roleCode);
  }
}
