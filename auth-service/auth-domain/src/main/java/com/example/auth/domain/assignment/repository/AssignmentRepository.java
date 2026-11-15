package com.example.auth.domain.assignment.repository;

import com.example.shared.domain.repository.Repository;
import com.example.core.types.UserNo;
import com.example.auth.domain.assignment.aggregate.AgentIdentityAssignment;
import com.example.auth.types.AssignmentId;

import java.util.List;

/**
 * 账号身份分配 Repository.
 *
 * <p>继承泛型 {@link Repository} 接口获得标准 CRUD 能力（load/save/delete/loadAll 等），
 * 同时保留按业务语义查询的自定义方法。</p>
 */
public interface AssignmentRepository extends Repository<AgentIdentityAssignment, AssignmentId> {

  /**
   * 查询账号当前生效的身份分配.
   *
   * @param accountId 账号
   * @return 生效的身份分配列表
   */
  List<AgentIdentityAssignment> findActiveByAccount(UserNo accountId);

  /**
   * 查询系统中所有生效的身份分配.
   *
   * <p>真实实现应该按 scopeDimension+scopeValue 建索引；领域层只声明这个查询能力。</p>
   */
  List<AgentIdentityAssignment> findAllActive();

  /**
   * 按角色批量查询指定 PLAN 范围内生效的身份分配（批次 59）。
   * <p>
   * 固定 {@code scopeDimension=PLAN} 精确匹配，避免依赖尚未接入的组织目录做
   * CUSTOMER/PRODUCT 继承展开；批量 {@code roleCodes} 规避消费侧逐实例调用。
   *
   * @param roleCodes 角色编码列表（空集合返回空列表）
   * @param planNo    计划号（PLAN 维度的 scope 取值）
   * @return 生效的身份分配列表
   */
  List<AgentIdentityAssignment> findActiveByRoleCodesAndPlan(List<String> roleCodes, String planNo);
}
