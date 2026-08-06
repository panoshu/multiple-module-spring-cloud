package com.pension.permission.application.assignment;


import com.example.shared.domain.event.EventBus;
import com.example.shared.identifier.contract.IdService;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.assignment.aggregate.AgentIdentityAssignment;
import com.pension.permission.domain.assignment.repository.AssignmentRepository;
import com.pension.permission.domain.assignment.service.GrantProvisioningService;
import com.pension.permission.domain.assignment.service.PlanReachabilityService;
import com.pension.permission.types.AssignmentId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 身份分配用例的编排入口：创建/变更角色/停用都通过GrantProvisioningService，
 * 这一层只负责事务边界和事件发布，不包含业务判断逻辑(那些都在领域层)。
 * <p>
 * 注意：GrantProvisioningService现在不再产生/撤销任何Grant(角色模板派生的权限已经
 * 改成实时解析，见EffectivePermissionService)，所以这里只需要发布assignment自己的事件。
 */
@Service
@RequiredArgsConstructor
public class AssignmentApplicationService {

  private final AssignmentRepository assignmentRepository;
  private final GrantProvisioningService grantProvisioningService;
  private final PlanReachabilityService planReachabilityService;
  private final EventBus  eventBus;
  private final IdService idService;


  @Transactional
  public AssignmentId createAssignment(CreateAssignmentCommand command) {

    AgentIdentityAssignment assignment =AgentIdentityAssignment.create(
      idService.nextId(AssignmentId.class),
      command.operator(),
      command.accountId(),
      command.roleCode(),
      command.scopeDimension(),
      command.scopeValue(),
      command.inheritable()
    );

    grantProvisioningService.onAssignmentCreated(assignment);
    assignment.domainEvents().forEach(eventBus::publish);
    return assignment.id();
  }

  public void changeRole(ChangeAssignmentRoleCommand command) {

    AgentIdentityAssignment assignment = assignmentRepository.load(command.assignmentId())
      .orElseThrow(() -> new IllegalArgumentException(
        "身份分配不存在: " + command.assignmentId().value()));
    grantProvisioningService.onAssignmentRoleChanged(assignment, command.newRoleCode());
    assignment.domainEvents().forEach(eventBus::publish);

  }

  @Transactional
  public void deactivate(DeactivateAssignmentCommand command) {
    AgentIdentityAssignment assignment = assignmentRepository.load(command.assignmentId())
      .orElseThrow(() -> new IllegalArgumentException(
        "身份分配不存在: " + command.assignmentId().value()));

    grantProvisioningService.onAssignmentDeactivated(assignment);
    assignment.domainEvents().forEach(eventBus::publish);

  }

  /**
   * 网上渠道"我可以选哪些计划"查询
   */
  public List<PlanNo> listSelectablePlans(UserNo accountId) {
    return planReachabilityService.listSelectablePlans(accountId);
  }

  /**
   * 管理台"这个计划下有哪些经办"查询
   */
  public List<AgentIdentityAssignment> listAssignmentsForPlan(PlanNo planId) {
    return planReachabilityService.listAssignmentsForPlan(planId);
  }
}
