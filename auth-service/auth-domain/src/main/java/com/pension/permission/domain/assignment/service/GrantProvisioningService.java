package com.pension.permission.domain.assignment.service;

import com.example.shared.domain.annotation.DomainService;
import com.pension.permission.domain.assignment.aggregate.AgentIdentityAssignment;
import com.pension.permission.domain.assignment.repository.AssignmentRepository;
import com.pension.permission.domain.role.service.RoleTemplateResolver;
import com.pension.permission.types.RoleCode;
import lombok.RequiredArgsConstructor;

/**
 * 身份分配的生命周期编排。角色模板派生的权限不再物化成Grant——这个服务现在只做两件事：
 * 1) 创建/变更角色时，校验一下这个(scopeDimension, scopeValue, roleCode)组合确实能解析出
 * 一份角色模板(配置错误在这里就报错，而不是等经办发现自己什么权限都没有才暴露问题)
 * 2) 保存AgentIdentityAssignment本身
 * 真正"这个身份现在有哪些权限"的问题，交给EffectivePermissionService在判定的那一刻实时解析——
 * 角色变更/离职不再需要撤销/重建任何Grant，只是更新AgentIdentityAssignment，
 * 下一次判定自然反映最新状态。
 */
@DomainService
@RequiredArgsConstructor
public final class GrantProvisioningService {

  private final RoleTemplateResolver roleTemplateResolver;
  private final AssignmentRepository assignmentRepository;

  public void onAssignmentCreated(AgentIdentityAssignment assignment) {
    // 创建时先确认这个(scopeDimension, scopeValue, roleCode)组合能解析出角色模板，
    // 配置错误在这里就报错，而不是等经办发现自己什么权限都没有才暴露问题。
    roleTemplateResolver.resolve(
      assignment.scopeDimension(), assignment.scopeValue(), assignment.roleCode());
    assignmentRepository.save(assignment);
  }

  public void onAssignmentRoleChanged(AgentIdentityAssignment assignment, RoleCode newRoleCode) {
    // 先按新角色试解析一次模板，确认配置有效，再真正变更，避免把assignment改成一个
    // "解析不出模板"的非法状态
    roleTemplateResolver.resolve(assignment.scopeDimension(), assignment.scopeValue(), newRoleCode);
    assignment.changeRole(newRoleCode);
    assignmentRepository.save(assignment);
  }

  public void onAssignmentDeactivated(AgentIdentityAssignment assignment) {
    assignment.deactivate();
    assignmentRepository.save(assignment);
  }
}
