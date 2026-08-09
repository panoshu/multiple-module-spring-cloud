package com.example.gateway.security;

import cn.dev33.satoken.stp.StpInterface;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 网关层 sa-token StpInterface 实现.
 *
 * <p>网关层仅做登录校验，不做细粒度权限校验。业务服务通过
 * {@code shared-permission-starter} 的 {@code @RequirePermission} 注解
 * 调用 auth-service 的 {@code PermissionCheckApi} 实时校验权限。
 *
 * <p>因此 {@link #getPermissionList(Object, String)} 返回空列表，
 * sa-token 的权限注解（如 {@code @SaCheckPermission}）在网关层不可用，
 * 所有细粒度权限校验由下游业务服务负责。
 *
 * @author auth-service
 * @since 2026/8/7
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayStpInterfaceImpl implements StpInterface {

  /**
   * Token-Session 中存储当前计划 ID 的键（与 auth-service 保持一致）
   */
  public static final String SESSION_KEY_CURRENT_PLAN_ID = "currentPlanId";

  private final ChannelAwareSaRouter channelAwareSaRouter;

  @Override
  public List<String> getPermissionList(Object loginId, String loginType) {
    // 网关层不做权限校验，业务服务通过 @RequirePermission 实时调用 auth-service 校验
    return List.of();
  }

  @Override
  public List<String> getRoleList(Object loginId, String loginType) {
    return switch (loginType) {
      case "internet" -> List.of("operator");
      case "hq" -> List.of("staff");
      case "branch" -> List.of("teller");
      default -> List.of();
    };
  }
}