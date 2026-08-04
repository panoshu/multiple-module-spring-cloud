package com.pension.permission.application.authorization;

import com.pension.permission.domain.assignment.service.EffectivePermissionService;
import com.pension.permission.domain.authorization.valueobject.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 权限判定查询的应用层入口——前端菜单/按钮的可见性接口、后端网关拦截层，
 * 最终都应该落到这一个方法上，保证前后端用的是同一份判定逻辑(见讨论中的"双保险"设计)。
 * <p>
 * 依赖的是EffectivePermissionService而不是更底层的AuthorizationEngine——
 * 前者在AuthorizationEngine的基础上，额外把身份分配实时解析出的角色模板权限
 * 也纳入了判定，是真正对外应该暴露的"最终判定口径"。
 */
@Service
@RequiredArgsConstructor
public final class PermissionQueryService {

  private final EffectivePermissionService effectivePermissionService;

  public boolean checkPermission(CheckPermissionQuery query) {
    Permission permission = new Permission(query.businessCode(), query.actionCode());
    return effectivePermissionService.checkPermission(
      query.identity(), query.planId(), permission, LocalDateTime.now());
  }
}
