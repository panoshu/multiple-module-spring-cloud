package com.example.auth.domain.authorization.valueobject.subject;

import com.example.core.types.UserNo;
import com.example.auth.domain.authorization.spi.PlanMembershipLookup;

import java.util.Set;

public record UserListSubject(Set<UserNo> accountIds) implements GrantSubject {
  @Override
  public boolean covers(UserNo identity, PlanMembershipLookup membershipLookup) {
    return accountIds.contains(identity);
  }
}
