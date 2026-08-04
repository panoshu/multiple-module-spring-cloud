package com.pension.permission.domain.authorization.valueobject.subject;

import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.spi.PlanMembershipLookup;

/**
 * 能力层标记：代表"这条规则跟具体主体无关，是计划/产品/客户本身的业务范围天花板"
 */
public record CapabilitySubject() implements GrantSubject {
  @Override
  public boolean covers(UserNo identity, PlanMembershipLookup membershipLookup) {
    throw new UnsupportedOperationException(
      "能力层Grant不参与按身份的主体匹配，应通过GrantRepository#findActiveCapabilityGrants查询");
  }
}
