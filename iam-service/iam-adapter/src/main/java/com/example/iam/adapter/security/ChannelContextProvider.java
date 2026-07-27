package com.example.iam.adapter.security;

import cn.dev33.satoken.session.SaSession;
import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.domain.authentication.errorcode.IamAuthErrorCode;
import com.example.shared.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * 渠道上下文提供者 - 根据当前请求的 Token 识别渠道并构建 {@link ChannelContext}。
 *
 * <p>识别逻辑:按 INTERNET → HQ → BRANCH 顺序检查 StpLogic 是否已登录,
 * 命中后构建对应渠道的上下文,包含:
 * <ul>
 *   <li>当前用户 ID(loginId)</li>
 *   <li>是否已二次授权(仅 BRANCH 渠道有意义)</li>
 *   <li>二次授权会话 ID(仅 BRANCH 渠道有意义)</li>
 *   <li>当前已选计划 ID(从 Token-Session 读取)</li>
 * </ul>
 *
 * <p>无任何渠道登录时抛出 {@link IamAuthErrorCode#NOT_LOGGED_IN} 业务异常。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Component
public class ChannelContextProvider {

  /** Token-Session 中存储当前计划 ID 的键 */
  public static final String SESSION_KEY_CURRENT_PLAN_ID = "currentPlanId";

  /**
   * 获取当前请求的渠道上下文。
   *
   * @return 渠道上下文(必非空)
   * @throws BusinessException 未登录时抛出
   */
  public ChannelContext currentContext() {
    if (StpInternetUtil.isLogin()) {
      return buildContext(ChannelType.INTERNET,
          StpInternetUtil.getLoginIdAsLong(),
          false, null);
    }
    if (StpHqUtil.isLogin()) {
      return buildContext(ChannelType.HQ,
          StpHqUtil.getLoginIdAsLong(),
          false, null);
    }
    if (StpBranchUtil.isLogin()) {
      return buildContext(ChannelType.BRANCH,
          StpBranchUtil.getLoginIdAsLong(),
          StpBranchUtil.hasSecondaryAuth(),
          StpBranchUtil.getSecondaryAuthSessionId());
    }
    throw new BusinessException(IamAuthErrorCode.NOT_LOGGED_IN)
        .withUserDetail("当前请求未登录");
  }

  /**
   * 获取当前登录用户 ID(任意渠道)。
   *
   * @return 用户 ID
   * @throws BusinessException 未登录时抛出
   */
  public Long currentUserId() {
    return currentContext().userId();
  }

  /**
   * 获取当前登录渠道类型。
   *
   * @return 渠道类型
   * @throws BusinessException 未登录时抛出
   */
  public ChannelType currentChannelType() {
    return currentContext().channelType();
  }

  /**
   * 构建渠道上下文(读取 Token-Session 中的计划信息)。
   */
  private ChannelContext buildContext(ChannelType channelType, Long userId,
                                       boolean hasSecondaryAuth, Long secondaryAuthSessionId) {
    SaSession tokenSession = switch (channelType) {
      case INTERNET -> StpInternetUtil.getTokenSession();
      case HQ -> StpHqUtil.getTokenSession();
      case BRANCH -> StpBranchUtil.getTokenSession();
    };
    String currentPlanId = null;
    if (tokenSession != null) {
      Object planId = tokenSession.get(SESSION_KEY_CURRENT_PLAN_ID);
      currentPlanId = planId != null ? planId.toString() : null;
    }
    return new ChannelContext(channelType, userId, hasSecondaryAuth,
        secondaryAuthSessionId, currentPlanId);
  }
}
