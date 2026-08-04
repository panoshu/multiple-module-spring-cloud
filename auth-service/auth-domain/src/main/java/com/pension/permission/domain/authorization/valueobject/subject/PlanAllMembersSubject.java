package com.pension.permission.domain.authorization.valueobject.subject;


import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.spi.PlanMembershipLookup;

/**
 * 计划整体代办场景使用：源计划下的全部经办都自动拥有这条Grant的权限
 */
public record PlanAllMembersSubject(PlanNo planNo) implements GrantSubject {
  @Override
  public boolean covers(UserNo identity, PlanMembershipLookup membershipLookup) {
    return membershipLookup.isMemberOf(identity, planNo);
  }
}
