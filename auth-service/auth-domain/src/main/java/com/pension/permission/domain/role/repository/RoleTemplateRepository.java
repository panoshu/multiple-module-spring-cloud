package com.pension.permission.domain.role.repository;


import com.pension.permission.domain.role.aggregate.RoleTemplate;
import com.pension.permission.domain.role.enumeration.RoleTemplateStatus;
import com.pension.permission.types.RoleCode;

import java.util.List;

public interface RoleTemplateRepository {
  List<RoleTemplate> findByRoleCode(RoleCode roleCode, RoleTemplateStatus status);

  /**
   * 系统中已定义的全部角色编码，供"计划下可选角色"这类遍历式查询使用
   */
  List<RoleCode> findAllRoleCodes();

  void save(RoleTemplate roleTemplate);
}
