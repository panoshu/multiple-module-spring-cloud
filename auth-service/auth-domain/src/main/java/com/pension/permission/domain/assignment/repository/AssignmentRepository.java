package com.pension.permission.domain.assignment.repository;

import com.example.shared.domain.repository.Repository;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.assignment.aggregate.AgentIdentityAssignment;
import com.pension.permission.types.AssignmentId;

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
}
