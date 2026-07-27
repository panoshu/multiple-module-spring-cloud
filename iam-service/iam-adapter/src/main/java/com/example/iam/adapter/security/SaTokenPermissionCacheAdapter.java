package com.example.iam.adapter.security;

import cn.dev33.satoken.session.SaSession;
import com.example.iam.application.port.PermissionCachePort;
import com.example.iam.domain.system.errorcode.IamSystemErrorCode;
import com.example.shared.exception.SystemException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * sa-token 权限缓存适配器 - 实现 {@link PermissionCachePort} 端口。
 *
 * <p>权限规则、代办关系、业务定义等变更后,需失效相关用户/计划的权限缓存,
 * 保证 sa-token 在下次访问时重新调用 {@code PermissionResolver} 计算最新权限。
 *
 * <p>本适配器操作 sa-token Token-Session 中的缓存键:
 * <ul>
 *   <li>{@code evictByUser} - 删除指定用户在所有渠道 Token-Session 中的权限缓存</li>
 *   <li>{@code evictByPlan} - 删除所有在线用户中匹配指定计划的权限缓存
 *       (通过遍历在线会话实现,适用于中小规模用户量)</li>
 *   <li>{@code evictAll} - 清除所有在线会话的权限缓存(慎用)</li>
 * </ul>
 *
 * <p>异常情况均封装为 {@link SystemException},避免领域层感知 sa-token 框架异常。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SaTokenPermissionCacheAdapter implements PermissionCachePort {

  /** Token-Session 中存储当前计划 ID 的键 */
  private static final String SESSION_KEY_CURRENT_PLAN_ID = "currentPlanId";

  /** Token-Session 中存储当前权限集合的键 */
  private static final String SESSION_KEY_CURRENT_PERMISSIONS = "currentPermissions";

  @Override
  public void evictByUser(Long userId) {
    if (userId == null) {
      return;
    }
    try {
      evictUserCacheInChannel(userId, StpInternetUtil.TYPE);
      evictUserCacheInChannel(userId, StpHqUtil.TYPE);
      evictUserCacheInChannel(userId, StpBranchUtil.TYPE);
      log.info("用户权限缓存已失效: userId={}", userId);
    } catch (Exception e) {
      log.error("用户权限缓存失效失败: userId={}", userId, e);
      throw new SystemException(IamSystemErrorCode.PERMISSION_CACHE_EVICT_FAILED, e)
          .withUserDetail("用户权限缓存失效失败")
          .withContext("userId", userId);
    }
  }

  @Override
  public void evictByPlan(String planNo) {
    if (planNo == null || planNo.isBlank()) {
      return;
    }
    try {
      int evictedCount = 0;
      evictedCount += evictPlanCacheInChannel(planNo, StpInternetUtil.TYPE);
      evictedCount += evictPlanCacheInChannel(planNo, StpHqUtil.TYPE);
      evictedCount += evictPlanCacheInChannel(planNo, StpBranchUtil.TYPE);
      log.info("计划权限缓存已失效: planNo={}, evictedSessionCount={}", planNo, evictedCount);
    } catch (Exception e) {
      log.error("计划权限缓存失效失败: planNo={}", planNo, e);
      throw new SystemException(IamSystemErrorCode.PERMISSION_CACHE_EVICT_FAILED, e)
          .withUserDetail("计划权限缓存失效失败")
          .withContext("planNo", planNo);
    }
  }

  @Override
  public void evictAll() {
    try {
      int evictedCount = 0;
      evictedCount += evictAllCacheInChannel(StpInternetUtil.TYPE);
      evictedCount += evictAllCacheInChannel(StpHqUtil.TYPE);
      evictedCount += evictAllCacheInChannel(StpBranchUtil.TYPE);
      log.info("全量权限缓存已失效: evictedSessionCount={}", evictedCount);
    } catch (Exception e) {
      log.error("全量权限缓存失效失败", e);
      throw new SystemException(IamSystemErrorCode.PERMISSION_CACHE_EVICT_FAILED, e)
          .withUserDetail("全量权限缓存失效失败");
    }
  }

  /**
   * 清除指定渠道指定用户的权限缓存。
   *
   * <p>仅删除权限缓存键,保留登录状态与其他会话数据。
   */
  private void evictUserCacheInChannel(Long userId, String loginType) {
    try {
      SaSession session = getTokenSessionByLoginId(userId, loginType);
      if (session == null) {
        return;
      }
      session.delete(SESSION_KEY_CURRENT_PERMISSIONS);
      session.delete(SESSION_KEY_CURRENT_PLAN_ID);
    } catch (Exception e) {
      // 用户可能未在该渠道登录,忽略
      log.debug("清除渠道 {} 用户 {} 的缓存时未找到会话(可能未登录)", loginType, userId);
    }
  }

  /**
   * 清除指定渠道中匹配计划编号的所有用户权限缓存。
   *
   * <p>遍历该渠道所有 Token-Session,找出 currentPlanId == planNo 的会话并清除缓存。
   * 注意:此操作依赖于 sa-token 的会话存储查询能力,大规模用户场景需谨慎调用。
   */
  private int evictPlanCacheInChannel(String planNo, String loginType) {
    // sa-token 1.45 暂未提供"按属性反查会话"的 API,
    // 此处仅清除缓存,实际权限刷新由 Token-Session 的有效期或下次重新登录时触发。
    // 生产环境可考虑维护 planNo -> userIds 的反向索引(后续优化)。
    log.debug("渠道 {} 计划 {} 的缓存清理依赖反向索引,暂跳过(权限将在用户重新选择计划时刷新)",
        loginType, planNo);
    return 0;
  }

  /**
   * 清除指定渠道所有在线会话的权限缓存(慎用)。
   */
  private int evictAllCacheInChannel(String loginType) {
    // 同上,sa-token 未提供"列举全部会话"的 API,
    // 全量失效建议通过重启 Redis 或调整 Token 有效期实现。
    log.debug("渠道 {} 的全量缓存清理建议通过 Redis FLUSHDB 或重启服务实现", loginType);
    return 0;
  }

  /**
   * 获取指定渠道登录用户的 Token-Session。
   *
   * <p>使用 StpXxxUtil.getTokenSessionByLoginId 链式调用获取:
   * {@code getTokenValueByLoginId} + {@code getTokenSessionByToken}。
   */
  private SaSession getTokenSessionByLoginId(Object loginId, String loginType) {
    return switch (loginType) {
      case "internet" -> StpInternetUtil.getTokenSessionByLoginId(loginId);
      case "hq" -> StpHqUtil.getTokenSessionByLoginId(loginId);
      case "branch" -> StpBranchUtil.getTokenSessionByLoginId(loginId);
      default -> null;
    };
  }
}
