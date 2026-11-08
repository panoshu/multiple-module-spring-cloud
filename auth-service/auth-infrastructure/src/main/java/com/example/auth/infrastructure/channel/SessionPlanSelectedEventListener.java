package com.example.auth.infrastructure.channel;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpLogic;
import com.example.core.types.Channel;
import com.example.auth.domain.channel.event.SessionPlanSelected;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * {@link SessionPlanSelected} 事件监听器 — 将选中的计划 ID 写入 sa-token Token-Session.
 *
 * <p>网关侧 {@code SessionContextInjector} 从 Token-Session 读取
 * {@code SESSION_KEY_CURRENT_PLAN_ID} 键值（同网关 {@code GatewayStpInterfaceImpl.SESSION_KEY_CURRENT_PLAN_ID}），
 * 注入到下游请求的 {@code X-Session-Context} Header 中。
 * 本监听器负责在方案选择事件发生后，将 planId 写入 Token-Session，
 * 使得后续请求能携带正确的 planNo。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionPlanSelectedEventListener {

  /**
   * Token-Session 中存储当前计划 ID 的键.
   *
   * <p>必须与网关 {@code GatewayStpInterfaceImpl.SESSION_KEY_CURRENT_PLAN_ID} 保持一致。
   * 此处不直接引用网关类（跨模块依赖），而是使用相同的字符串常量。</p>
   */
  private static final String SESSION_KEY_CURRENT_PLAN_ID = "currentPlanId";

  private final Map<Channel, StpLogic> channelStpLogicMap;

  @EventListener
  public void onSessionPlanSelected(SessionPlanSelected event) {
    String tokenValue = event.sessionId().value();
    String planId = event.planId().value();

    for (Map.Entry<Channel, StpLogic> entry : channelStpLogicMap.entrySet()) {
      try {
        StpLogic stpLogic = entry.getValue();
        Object loginId = stpLogic.getLoginIdByToken(tokenValue);
        if (loginId != null) {
          SaSession tokenSession = stpLogic.getTokenSessionByToken(tokenValue);
          tokenSession.set(SESSION_KEY_CURRENT_PLAN_ID, planId);
          log.info("已将 planId={} 写入 Token-Session: channel={}, accountId={}",
            planId, entry.getKey(), loginId);
          return;
        }
      } catch (NotLoginException ignored) {
        // 该渠道不认识此 token，继续尝试
      }
    }

    log.warn("未找到 token 对应的 StpLogic，planId 未写入 Token-Session: sessionId={}", event.sessionId().value());
  }
}
