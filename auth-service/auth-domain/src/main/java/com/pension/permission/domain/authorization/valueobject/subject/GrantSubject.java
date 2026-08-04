package com.pension.permission.domain.authorization.valueobject.subject;

import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.spi.PlanMembershipLookup;

/**
 * Grant的主体。除了直接指定具体用户，还可以是"某计划全体经办"或"某计划某角色"，
 * 这样"计划整体代办"和"计划指定人员代办"复用的是同一套结构，只是subject形态不同。
 */
public sealed interface GrantSubject
  permits CapabilitySubject, UserListSubject, PlanAllMembersSubject, PlanRoleSubject {

  /**
   * 该主体是否覆盖给定身份。CapabilitySubject(能力层，无主体)不参与这个判断，
   * 能力层的Grant查询走单独的repository方法，不经过这里。
   */
  boolean covers(UserNo identity, PlanMembershipLookup membershipLookup);
}
