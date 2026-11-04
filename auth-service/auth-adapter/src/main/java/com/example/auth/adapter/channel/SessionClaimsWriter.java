package com.example.auth.adapter.channel;

import cn.dev33.satoken.stp.StpLogic;
import com.example.core.types.Channel;
import com.example.shared.security.ChannelType;
import com.example.shared.security.SessionClaims;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.example.auth.domain.channel.event.SecondaryAuthClosed;
import com.example.auth.domain.channel.event.SecondaryAuthCompleted;
import com.example.auth.domain.channel.event.SecondaryAuthRevoked;
import com.example.auth.domain.channel.event.SessionCreated;
import com.example.auth.domain.channel.event.SessionPlanSelected;
import com.example.auth.domain.channel.valueobject.PermissionSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 会话声明写入器 — 认证与权限体系重构 Phase B 的核心组件。
 *
 * <p>信任链：auth 在会话生命周期事件发生时，把完整 {@link SessionClaims} 声明
 * 以 JSON 形式写入 sa-token Token-Session 的单键 {@link SessionClaims#SESSION_CLAIMS_KEY}；
 * 网关 {@code SessionContextInjector} 整体读取透传（签名后注入 X-Session-Context）；
 * 下游 kernel 验签解析后由 {@code DefaultBusinessAccessGuard} 做渠道差异化校验。
 *
 * <p>写入时机（与 spec 2026-11-03-auth-permission-redesign §3.3 一致）：
 * <ul>
 *   <li>登录（SessionCreated）→ 身份 + 渠道</li>
 *   <li>选择计划（SessionPlanSelected）→ 计划字段</li>
 *   <li>二次授权完成（SecondaryAuthCompleted）→ BRANCH 提权字段 + 权限快照</li>
 *   <li>二次授权撤销/关闭 → 清空提权字段</li>
 * </ul>
 *
 * <p>声明为 read-modify-write 合并（事件携带局部数据），channelType 写入
 * {@link ChannelType} 枚举名（INTERNET/HQ/BRANCH，与 Guard 匹配语义一致）。
 *
 * <p>渠道词表映射：Channel（业务渠道）→ ChannelType（逻辑渠道）
 * NETAPP→INTERNET、TELLER→HQ、BANK_BRANCH→BRANCH。Channel→Channel 的
 * 统一改名列入 Phase D 治理。
 *
 * @author auth-adapter
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionClaimsWriter {

  private final Map<Channel, StpLogic> channelStpLogicMap;
  private final ObjectMapper objectMapper;

  /**
   * 登录成功 → 写入身份 + 渠道.
   */
  @EventListener
  public void onSessionCreated(SessionCreated event) {
    merge(event.sessionId().value(), claims -> {
      claims.put("userNo", event.primaryAccountId().value());
      claims.put("channelType", channelName(event.channel()));
    });
  }

  /**
   * 选择计划 → 写入计划字段（幂等覆盖）.
   */
  @EventListener
  public void onSessionPlanSelected(SessionPlanSelected event) {
    merge(event.sessionId().value(), claims -> {
      claims.put("userNo", event.primaryAccountId().value());
      claims.put("planNo", event.planId().value());
    });
  }

  /**
   * 二次授权完成（BRANCH）→ 提权字段 + 权限快照.
   */
  @EventListener
  public void onSecondaryAuthCompleted(SecondaryAuthCompleted event) {
    merge(event.sessionId().value(), claims -> {
      claims.put("hasSecondaryAuth", true);
      claims.put("secondaryAuthSessionId", event.sessionId().value());
      claims.put("borrowedApproverId", event.approverAccountId().value());
      PermissionSnapshot snapshot = event.permissionSnapshot();
      if (snapshot != null && snapshot.permissions() != null) {
        claims.set("permissionCodes", objectMapper.valueToTree(permissionCodes(snapshot)));
      }
    });
  }

  /**
   * 二次授权撤销 → 清空提权字段.
   */
  @EventListener
  public void onSecondaryAuthRevoked(SecondaryAuthRevoked event) {
    merge(event.sessionId().value(), claims -> {
      claims.put("hasSecondaryAuth", false);
      claims.remove("secondaryAuthSessionId");
      claims.remove("borrowedApproverId");
    });
  }

  /**
   * 二次授权关闭 → 清空提权字段.
   */
  @EventListener
  public void onSecondaryAuthClosed(SecondaryAuthClosed event) {
    merge(event.sessionId().value(), claims -> {
      claims.put("hasSecondaryAuth", false);
      claims.remove("secondaryAuthSessionId");
      claims.remove("borrowedApproverId");
    });
  }

  /**
   * 定位 token 所属渠道的 Token-Session，读取既有声明 JSON 并合并写入.
   */
  private void merge(String sessionId, java.util.function.Consumer<ObjectNode> merger) {
    for (Map.Entry<Channel, StpLogic> entry : channelStpLogicMap.entrySet()) {
      try {
        StpLogic stpLogic = entry.getValue();
        Object loginId = stpLogic.getLoginIdByToken(sessionId);
        if (loginId == null) {
          continue;
        }
        cn.dev33.satoken.session.SaSession tokenSession = stpLogic.getTokenSessionByToken(sessionId);
        Object existing = tokenSession.get(SessionClaims.SESSION_CLAIMS_KEY);
        ObjectNode claims = existing instanceof String json && !json.isBlank()
          ? (ObjectNode) objectMapper.readTree(json)
          : objectMapper.createObjectNode();
        merger.accept(claims);
        tokenSession.set(SessionClaims.SESSION_CLAIMS_KEY, objectMapper.writeValueAsString(claims));
        log.info("会话声明已更新: sessionId={}, channel={}, keys={}",
          sessionId, entry.getKey(), claims.fieldNames().next() != null ? "..." : "empty");
        return;
      } catch (Exception ignored) {
        // 该渠道不认识此 token，继续尝试
      }
    }
    log.warn("未找到 token 对应的 StpLogic，会话声明未写入: sessionId={}", sessionId);
  }

  /**
   * 权限快照 → 权限码集合（businessCode_actionCode 约定，与 Guard 的 BUSINESS_{type}_HANDLE 匹配）.
   */
  private Set<String> permissionCodes(PermissionSnapshot snapshot) {
    return snapshot.permissions().stream()
      .map(p -> p.actionCode() == null || p.actionCode().value() == null
        ? p.businessCode().value()
        : p.businessCode().value() + "_" + p.actionCode().value())
      .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * 业务渠道词表 → 逻辑渠道（ChannelType 枚举名，Guard 匹配语义）.
   */
  private String channelName(Channel channel) {
    return switch (channel) {
      case NETAPP -> ChannelType.INTERNET.name();
      case TELLER -> ChannelType.HQ.name();
      case BANK_BRANCH -> ChannelType.BRANCH.name();
      default -> channel.name();
    };
  }
}
