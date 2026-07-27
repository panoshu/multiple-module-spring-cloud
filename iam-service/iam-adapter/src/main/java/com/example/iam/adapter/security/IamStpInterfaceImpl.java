package com.example.iam.adapter.security;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpInterface;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionCode;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionSnapshot;
import com.example.iam.domain.authorization.service.PermissionResolver;
import com.example.iam.types.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * sa-token StpInterface 实现 - 基于 IAM 权限体系提供权限与角色查询。
 *
 * <p>sa-token 在权限校验({@code checkPermission})时回调本实现获取权限列表,
 * 通过 {@code loginType} 区分渠道,从对应渠道的 Token-Session 中读取缓存的权限集合。
 *
 * <p>权限计算流程:
 * <ol>
 *   <li>从 Token-Session 读取 {@code currentPlanId}(若为空,返回空权限)</li>
 *   <li>网点渠道:直接返回二次授权时冻结的权限快照({@code currentPermissions})</li>
 *   <li>其他渠道:从 {@code currentPermissions} 读取缓存,未命中时调用
 *       {@link PermissionResolver#resolve} 重新计算并写入缓存</li>
 * </ol>
 *
 * <p>失败时返回空权限列表,sa-token 会拒绝所有需要权限的操作,保证安全。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IamStpInterfaceImpl implements StpInterface {

  /** Token-Session 中存储当前计划 ID 的键 */
  public static final String SESSION_KEY_CURRENT_PLAN_ID = "currentPlanId";

  /** Token-Session 中存储当前权限集合的键 */
  public static final String SESSION_KEY_CURRENT_PERMISSIONS = "currentPermissions";

  private final PermissionResolver permissionResolver;

  @Override
  public List<String> getPermissionList(Object loginId, String loginType) {
    try {
      SaSession session = getTokenSession(loginId, loginType);
      if (session == null) {
        return List.of();
      }

      // 1. 获取当前计划(未选择计划时无权限)
      String planId = readString(session, SESSION_KEY_CURRENT_PLAN_ID);
      if (planId == null || planId.isBlank()) {
        return List.of();
      }

      // 2. 网点渠道:直接返回二次授权时冻结的快照
      if (StpBranchUtil.TYPE.equals(loginType)) {
        Set<String> snapshot = readStringSet(session, SESSION_KEY_CURRENT_PERMISSIONS);
        return snapshot != null ? List.copyOf(snapshot) : List.of();
      }

      // 3. 其他渠道:从缓存或重新计算
      Set<String> cached = readStringSet(session, SESSION_KEY_CURRENT_PERMISSIONS);
      if (cached != null) {
        return List.copyOf(cached);
      }

      // 4. 缓存未命中,调用 PermissionResolver 计算并缓存
      Long userId = parseLong(loginId);
      if (userId == null) {
        return List.of();
      }
      PermissionSnapshot snapshot = permissionResolver.resolve(UserId.of(userId), planId);
      Set<String> permissions = snapshot.permissions().stream()
          .map(PermissionCode::value)
          .collect(Collectors.toSet());
      session.set(SESSION_KEY_CURRENT_PERMISSIONS, permissions);
      return List.copyOf(permissions);
    } catch (Exception e) {
      log.error("[StpInterface] 加载权限失败: loginId={}, loginType={}", loginId, loginType, e);
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
   * 获取指定渠道登录用户的 Token-Session。
   *
   * <p>使用 StpXxxUtil.getTokenSessionByLoginId 链式调用获取:
   * {@code getTokenValueByLoginId} + {@code getTokenSessionByToken}。
   *
   * @param loginId   登录 ID
   * @param loginType 渠道类型标识
   * @return Token-Session(用户未登录时返回 null)
   */
  private SaSession getTokenSession(Object loginId, String loginType) {
    try {
      return switch (loginType) {
        case "internet" -> StpInternetUtil.getTokenSessionByLoginId(loginId);
        case "hq" -> StpHqUtil.getTokenSessionByLoginId(loginId);
        case "branch" -> StpBranchUtil.getTokenSessionByLoginId(loginId);
        default -> null;
      };
    } catch (Exception e) {
      log.debug("[StpInterface] 获取 Token-Session 失败(用户可能未登录): loginId={}, loginType={}",
          loginId, loginType);
      return null;
    }
  }

  /**
   * 从 SaSession 中读取字符串值。
   */
  private String readString(SaSession session, String key) {
    Object value = session.get(key);
    return value != null ? value.toString() : null;
  }

  /**
   * 从 SaSession 中读取字符串集合。
   *
   * <p>sa-token 的 Redis 序列化可能将 Set 还原为 List,此处统一适配。
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

  /**
   * 将 loginId 解析为 Long(失败返回 null)。
   */
  private Long parseLong(Object loginId) {
    if (loginId == null) {
      return null;
    }
    if (loginId instanceof Number num) {
      return num.longValue();
    }
    try {
      return Long.parseLong(loginId.toString());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
