package com.pension.permission.application.roletemplate;


import com.example.shared.identifier.id.PlanNo;
import com.pension.permission.domain.role.repository.RoleVisibilityRepository;
import com.pension.permission.domain.role.service.RoleVisibilityResolver;
import com.pension.permission.domain.role.valueobject.RoleVisibilityScope;
import com.pension.permission.types.RoleCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * "给这个计划分配经办时，下拉框展示哪些角色"的查询用例，以及可见性开关的配置用例
 */
@Service
@RequiredArgsConstructor
public class RoleVisibilityApplicationService {

  private final RoleVisibilityResolver roleVisibilityResolver;
  private final RoleVisibilityRepository roleVisibilityRepository;

  public List<RoleCode> listSelectableRoles(PlanNo planId) {
    return roleVisibilityResolver.listSelectableRoles(planId);
  }

  @Transactional
  public void setVisibility(SetRoleVisibilityCommand command) {
    roleVisibilityRepository.save(
      new RoleVisibilityScope(command.dimension(), command.value(), command.mode()));
  }
}
