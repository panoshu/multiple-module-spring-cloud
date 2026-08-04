package com.pension.permission.domain.role.repository;

import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.PlanNo;
import com.pension.permission.domain.role.valueobject.RoleVisibilityScope;

import java.util.Optional;

public interface RoleVisibilityRepository {
  Optional<RoleVisibilityScope> findByPlan(PlanNo planNo);

  Optional<RoleVisibilityScope> findByCustomer(CustomerNo customerNo);

  /**
   * scope.dimension()只应为PLAN或CUSTOMER，按(dimension,value)做upsert
   */
  void save(RoleVisibilityScope scope);
}
