package com.example.auth.domain.role.repository;

import com.example.core.types.CustomerNo;
import com.example.core.types.PlanNo;
import com.example.auth.domain.role.valueobject.RoleVisibilityScope;

import java.util.Optional;

public interface RoleVisibilityRepository {
  Optional<RoleVisibilityScope> findByPlan(PlanNo planNo);

  Optional<RoleVisibilityScope> findByCustomer(CustomerNo customerNo);

  /**
   * scope.dimension()只应为PLAN或CUSTOMER，按(dimension,value)做upsert
   */
  void save(RoleVisibilityScope scope);
}
