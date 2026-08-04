package com.example.approval.infrastructure.gateway;

import com.example.approval.domain.gateway.RoleUserGateway;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 角色用户网关实现（伪实现）
 * <p>
 * TODO: 待用户服务API提供后实现
 *
 * @author approval-service
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleUserGatewayImpl implements RoleUserGateway {

  @Override
  public List<UserNo> getUsersByRole(String roleId) {
    // TODO: 待用户服务API提供后实现
    // 实际实现应该调用用户服务API获取角色下的用户列表
    log.warn("getUsersByRole 方法尚未完整实现, roleId={}", roleId);
    return List.of();
  }

  @Override
  public List<UserNo> getUsersInSamePlan(PlanNo planId, UserNo excludeUser) {
    // TODO: 待用户服务API提供后实现
    // 实际实现应该调用用户服务API获取同一计划下的用户列表（排除指定用户）
    log.warn("getUsersInSamePlan 方法尚未完整实现, planId={}, excludeUser={}", planId, excludeUser);
    return List.of();
  }
}
