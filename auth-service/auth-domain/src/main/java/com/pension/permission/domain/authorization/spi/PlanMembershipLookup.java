package com.pension.permission.domain.authorization.spi;

import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.types.RoleCode;


/**
 * 权限域自己定义的小端口：判断某个身份是否是某计划的成员/某角色。
 * 真正的成员关系数据在assignment限界上下文里，这里通过端口解耦，
 * 避免authorization包反向依赖assignment包；组合根(应用层)负责接线实现。
 */
public interface PlanMembershipLookup {
  boolean isMemberOf(UserNo userNo, PlanNo planNo);

  boolean hasRole(UserNo userNo, PlanNo planNo, RoleCode roleCode);
}
