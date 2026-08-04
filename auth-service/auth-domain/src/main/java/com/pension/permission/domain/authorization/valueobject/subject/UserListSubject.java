package com.pension.permission.domain.authorization.valueobject.subject;

import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.spi.PlanMembershipLookup;

import java.util.Set;

public record UserListSubject(Set<UserNo> accountIds) implements GrantSubject {
  @Override
  public boolean covers(UserNo identity, PlanMembershipLookup membershipLookup) {
    return accountIds.contains(identity);
  }
}
