package com.example.iam.adapter.security;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpLogic;
import com.example.iam.application.port.ChannelSessionPort;
import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.domain.authentication.errorcode.IamAuthErrorCode;
import com.example.iam.domain.system.errorcode.IamSystemErrorCode;
import com.example.shared.exception.BusinessException;
import com.example.shared.exception.CommonError;
import com.example.shared.exception.SystemException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * sa-token 渠道会话适配器 - 实现 {@link ChannelSessionPort} 端口。
 *
 * <p>封装三渠道(INTERNET/HQ/BRANCH)的 sa-token Token-Session 操作,包括:
 * <ul>
 *   <li>当前登录上下文查询(渠道类型、用户 ID、用户编号)</li>
 *   <li>计划与权限会话管理(设置/清除/查询当前计划及其权限集合)</li>
 *   <li>二次授权会话管理(设置/清除柜员借用的经办人权限)</li>
 *   <li>登录/登出/踢人下线等会话生命周期操作</li>
 * </ul>
 *
 * <p>所有 sa-token 相关的异常均封装为 {@link SystemException},避免领域层感知到具体框架。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SaTokenChannelSessionAdapter implements ChannelSessionPort {

  /** Token-Session 中存储当前计划 ID 的键 */
  public static final String SESSION_KEY_CURRENT_PLAN_ID = "currentPlanId";

  /** Token-Session 中存储当前权限集合的键 */
  public static final String SESSION_KEY_CURRENT_PERMISSIONS = "currentPermissions";

  /** Token-Session 中存储二次授权会话 ID 的键 */
  public static final String SESSION_KEY_SECONDARY_AUTH_ID = "secondaryAuthSessionId";

  /** Token-Session 中存储借用经办人 ID 的键 */
  public static final String SESSION_KEY_BORROWED_APPROVER_ID = "borrowedApproverId";

  private final ChannelContextProvider channelContextProvider;

  // ==================== 当前登录上下文查询 ====================

  @Override
  public ChannelType currentChannelType() {
    return channelContextProvider.currentChannelType();
  }

  @Override
  public Long currentUserId() {
    return channelContextProvider.currentUserId();
  }

  @Override
  public String currentUserNo() {
    return String.valueOf(currentUserId());
  }

  // ==================== 计划与权限会话管理 ====================

  @Override
  public void setCurrentPlan(String planId, Set<String> permissions) {
    if (planId == null || planId.isBlank()) {
      throw new BusinessException(CommonError.BAD_REQUEST)
          .withUserDetail("计划 ID 不能为空");
    }
    try {
      ChannelType channel = currentChannelType();
      SaSession session = currentTokenSession();
      session.set(SESSION_KEY_CURRENT_PLAN_ID, planId);
      session.set(SESSION_KEY_CURRENT_PERMISSIONS,
          permissions != null ? new HashSet<>(permissions) : new HashSet<>());
      log.debug("设置当前计划: channel={}, planId={}, permissionCount={}",
          channel, planId, permissions != null ? permissions.size() : 0);
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new SystemException(IamSystemErrorCode.SA_TOKEN_SESSION_UPDATE_FAILED, e)
          .withUserDetail("设置当前计划失败")
          .withContext("planId", planId);
    }
  }

  @Override
  public void clearCurrentPlan() {
    try {
      ChannelType channel = currentChannelType();
      SaSession session = currentTokenSession();
      session.delete(SESSION_KEY_CURRENT_PLAN_ID);
      session.delete(SESSION_KEY_CURRENT_PERMISSIONS);
      log.debug("清除当前计划: channel={}", channel);
    } catch (Exception e) {
      throw new SystemException(IamSystemErrorCode.SA_TOKEN_SESSION_UPDATE_FAILED, e)
          .withUserDetail("清除当前计划失败");
    }
  }

  @Override
  public String getCurrentPlanId() {
    try {
      SaSession session = currentTokenSession();
      Object value = session.get(SESSION_KEY_CURRENT_PLAN_ID);
      return value != null ? value.toString() : null;
    } catch (Exception e) {
      log.warn("读取当前计划失败", e);
      return null;
    }
  }

  @Override
  public Set<String> getCurrentPermissions() {
    try {
      SaSession session = currentTokenSession();
      Object value = session.get(SESSION_KEY_CURRENT_PERMISSIONS);
      if (value instanceof Set<?> set) {
        Set<String> result = new HashSet<>();
        for (Object o : set) {
          if (o != null) {
            result.add(o.toString());
          }
        }
        return result;
      }
      if (value instanceof java.util.List<?> list) {
        Set<String> result = new HashSet<>();
        for (Object o : list) {
          if (o != null) {
            result.add(o.toString());
          }
        }
        return result;
      }
      return Set.of();
    } catch (Exception e) {
      log.warn("读取当前权限失败", e);
      return Set.of();
    }
  }

  // ==================== 二次授权会话管理 ====================

  @Override
  public void setSecondaryAuthSession(Long sessionId, Long approverId, String planId,
                                       Set<String> permissions) {
    try {
      // 仅网点渠道支持二次授权会话
      ChannelType channel = currentChannelType();
      if (channel != ChannelType.BRANCH) {
        throw new BusinessException(IamAuthErrorCode.SECONDARY_AUTH_STRATEGY_NOT_SUPPORTED)
            .withUserDetail("仅网点渠道支持二次授权会话")
            .withContext("channelType", channel);
      }
      SaSession session = currentTokenSession();
      session.set(SESSION_KEY_SECONDARY_AUTH_ID, sessionId);
      session.set(SESSION_KEY_BORROWED_APPROVER_ID, approverId);
      session.set(SESSION_KEY_CURRENT_PLAN_ID, planId);
      session.set(SESSION_KEY_CURRENT_PERMISSIONS,
          permissions != null ? new HashSet<>(permissions) : new HashSet<>());
      log.info("二次授权会话已设置: tellerId={}, sessionId={}, approverId={}, permissionCount={}",
          currentUserId(), sessionId, approverId,
          permissions != null ? permissions.size() : 0);
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new SystemException(IamSystemErrorCode.SA_TOKEN_SESSION_UPDATE_FAILED, e)
          .withUserDetail("设置二次授权会话失败")
          .withContext("sessionId", sessionId);
    }
  }

  @Override
  public void clearSecondaryAuthSession() {
    try {
      ChannelType channel = currentChannelType();
      if (channel != ChannelType.BRANCH) {
        return;
      }
      SaSession session = currentTokenSession();
      session.delete(SESSION_KEY_SECONDARY_AUTH_ID);
      session.delete(SESSION_KEY_BORROWED_APPROVER_ID);
      session.delete(SESSION_KEY_CURRENT_PLAN_ID);
      session.delete(SESSION_KEY_CURRENT_PERMISSIONS);
      log.info("二次授权会话已清除: tellerId={}", currentUserId());
    } catch (Exception e) {
      throw new SystemException(IamSystemErrorCode.SA_TOKEN_SESSION_UPDATE_FAILED, e)
          .withUserDetail("清除二次授权会话失败");
    }
  }

  // ==================== 会话生命周期操作 ====================

  @Override
  public void kickout(Long userId, ChannelType channelType) {
    if (userId == null || channelType == null) {
      return;
    }
    try {
      switch (channelType) {
        case INTERNET -> StpInternetUtil.kickout(userId);
        case HQ -> StpHqUtil.kickout(userId);
        case BRANCH -> StpBranchUtil.kickout(userId);
      }
      log.info("踢用户下线: userId={}, channel={}", userId, channelType);
    } catch (Exception e) {
      throw new SystemException(IamSystemErrorCode.SA_TOKEN_KICKOUT_FAILED, e)
          .withUserDetail("踢用户下线失败")
          .withContext("userId", userId)
          .withContext("channelType", channelType);
    }
  }

  @Override
  public void login(Long userId, ChannelType channelType) {
    if (userId == null || channelType == null) {
      throw new BusinessException(IamAuthErrorCode.CHANNEL_TYPE_INVALID)
          .withUserDetail("用户 ID 或渠道类型不能为空");
    }
    try {
      switch (channelType) {
        case INTERNET -> StpInternetUtil.login(userId);
        case HQ -> StpHqUtil.login(userId);
        case BRANCH -> StpBranchUtil.login(userId);
      }
      log.info("用户登录: userId={}, channel={}", userId, channelType);
    } catch (Exception e) {
      throw new SystemException(IamSystemErrorCode.SA_TOKEN_SESSION_UPDATE_FAILED, e)
          .withUserDetail("登录失败")
          .withContext("userId", userId)
          .withContext("channelType", channelType);
    }
  }

  @Override
  public void logout(ChannelType channelType) {
    if (channelType == null) {
      return;
    }
    try {
      switch (channelType) {
        case INTERNET -> StpInternetUtil.logout();
        case HQ -> StpHqUtil.logout();
        case BRANCH -> StpBranchUtil.logout();
      }
      log.info("用户登出: channel={}", channelType);
    } catch (Exception e) {
      throw new SystemException(IamSystemErrorCode.SA_TOKEN_SESSION_UPDATE_FAILED, e)
          .withUserDetail("登出失败")
          .withContext("channelType", channelType);
    }
  }

  // ==================== 内部工具方法 ====================

  /**
   * 获取当前渠道对应的 StpLogic。
   */
  private StpLogic currentStpLogic() {
    ChannelType channel = currentChannelType();
    return switch (channel) {
      case INTERNET -> StpInternetUtil.stpLogic;
      case HQ -> StpHqUtil.stpLogic;
      case BRANCH -> StpBranchUtil.stpLogic;
    };
  }

  /**
   * 获取当前请求的 Token-Session。
   *
   * @throws BusinessException 未登录时抛出
   */
  private SaSession currentTokenSession() {
    StpLogic stpLogic = currentStpLogic();
    if (!stpLogic.isLogin()) {
      throw new BusinessException(IamAuthErrorCode.NOT_LOGGED_IN)
          .withUserDetail("当前请求未登录");
    }
    return stpLogic.getTokenSession();
  }
}
