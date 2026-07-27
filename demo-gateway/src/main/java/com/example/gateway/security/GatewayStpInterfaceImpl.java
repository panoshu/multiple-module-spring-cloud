package com.example.gateway.security;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpLogic;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 网关层 sa-token StpInterface 实现 - 从 Token-Session 读取权限与角色。
 *
 * <p>对应设计文档 4.6 节:网关层不调用 iam-service 的 PermissionResolver,
 * 仅从 sa-token 共享的 Token-Session(Redis)读取 iam-service 登录时缓存的权限集合。
 *
 * <p>权限来源:
 * <ul>
 *   <li>iam-service 登录时,将 {@code currentPermissions} 写入 Token-Session</li>
 *   <li>iam-service 切换计划时,更新 {@code currentPlanId} 与 {@code currentPermissions}</li>
 *   <li>网点渠道二次授权时,冻结 {@code currentPermissions} 为授权瞬间快照</li>
 * </ul>
 *
 * <p>失败策略:任何异常返回空权限列表,sa-token 拒绝所有需要权限的操作,保证安全。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayStpInterfaceImpl implements StpInterface {

  /** Token-Session 中存储当前计划 ID 的键(与 iam-service IamStpInterfaceImpl 保持一致) */
  public static final String SESSION_KEY_CURRENT_PLAN_ID = "currentPlanId";

  /** Token-Session 中存储当前权限集合的键 */
  public static final String SESSION_KEY_CURRENT_PERMISSIONS = "currentPermissions";

  private final ChannelAwareSaRouter channelAwareSaRouter;

  @Override
  public List<String> getPermissionList(Object loginId, String loginType) {
    try {
      ChannelType channel = channelAwareSaRouter.getChannelByLoginType(loginType);
      if (channel == null) {
        return List.of();
      }
      StpLogic stpLogic = channelAwareSaRouter.getStpLogic(channel);
      SaSession session = getTokenSessionByLoginId(stpLogic, loginId);
      if (session == null) {
        return List.of();
      }

      // 未选择计划时无权限
      String planId = readString(session, SESSION_KEY_CURRENT_PLAN_ID);
      if (planId == null || planId.isBlank()) {
        return List.of();
      }

      Set<String> permissions = readStringSet(session, SESSION_KEY_CURRENT_PERMISSIONS);
      return permissions != null ? List.copyOf(permissions) : List.of();
    } catch (Exception e) {
      log.error("[GatewayStpInterface] 加载权限失败: loginId={}, loginType={}", loginId, loginType, e);
      return List.of();
    }
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

  /**
   * 获取指定登录 ID 的 Token-Session。
   *
   * <p>sa-token 1.45 未直接提供 getTokenSessionByLoginId,通过
   * getTokenValueByLoginId + getTokenSessionByToken 链式调用实现。
   *
   * @param stpLogic 渠道 StpLogic
   * @param loginId  登录 ID
   * @return Token-Session;用户未登录或无 Token 时返回 null
   */
  private SaSession getTokenSessionByLoginId(StpLogic stpLogic, Object loginId) {
    try {
      String token = stpLogic.getTokenValueByLoginId(loginId);
      if (token == null) {
        return null;
      }
      return stpLogic.getTokenSessionByToken(token);
    } catch (Exception e) {
      log.debug("[GatewayStpInterface] 获取 Token-Session 失败: loginId={}", loginId);
      return null;
    }
  }

  private String readString(SaSession session, String key) {
    Object value = session.get(key);
    return value != null ? value.toString() : null;
  }

  /**
   * 从 SaSession 读取字符串集合。
   *
   * <p>sa-token Redis 序列化可能将 Set 还原为 List,此处统一适配。
   */
  @SuppressWarnings("unchecked")
  private Set<String> readStringSet(SaSession session, String key) {
    Object value = session.get(key);
    if (value == null) {
      return null;
    }
    if (value instanceof Set<?> set) {
      return set.stream().map(Object::toString).collect(Collectors.toSet());
    }
    if (value instanceof List<?> list) {
      return list.stream().map(Object::toString).collect(Collectors.toSet());
    }
    return null;
  }
}
