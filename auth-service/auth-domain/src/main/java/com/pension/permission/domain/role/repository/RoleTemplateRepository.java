package com.pension.permission.domain.role.repository;


import com.example.shared.domain.repository.Repository;
import com.pension.permission.domain.role.aggregate.RoleTemplate;
import com.pension.permission.domain.role.enumeration.RoleTemplateStatus;
import com.pension.permission.types.RoleCode;
import com.pension.permission.types.RoleTemplateId;

import java.util.List;

/**
 * 角色权限模板 Repository.
 *
 * <p>继承泛型 {@link Repository} 接口获得标准 CRUD 能力（load/save/delete/loadAll 等），
 * 同时保留按业务语义查询的自定义方法。</p>
 */
public interface RoleTemplateRepository extends Repository<RoleTemplate, RoleTemplateId> {

  /**
   * 按角色编码查询匹配的模板集合.
   *
   * @param roleCode 角色编码
   * @param status   模板状态过滤；为 null 时不按状态过滤
   * @return 匹配的模板列表
   */
  List<RoleTemplate> findByRoleCode(RoleCode roleCode, RoleTemplateStatus status);

  /**
   * 系统中已定义的全部角色编码，供"计划下可选角色"这类遍历式查询使用.
   */
  List<RoleCode> findAllRoleCodes();
}
