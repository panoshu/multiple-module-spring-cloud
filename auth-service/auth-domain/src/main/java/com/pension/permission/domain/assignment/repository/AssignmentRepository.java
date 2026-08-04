package com.pension.permission.domain.assignment.repository;

import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.assignment.aggregate.AgentIdentityAssignment;
import com.pension.permission.types.AssignmentId;

import java.util.List;
import java.util.Optional;

public interface AssignmentRepository {
  Optional<AgentIdentityAssignment> findById(AssignmentId id);

  List<AgentIdentityAssignment> findActiveByAccount(UserNo accountId);

  /**
   * 真实实现应该按scopeDimension+scopeValue建索引；领域层只声明这个查询能力
   */
  List<AgentIdentityAssignment> findAllActive();

  void save(AgentIdentityAssignment assignment);
}
