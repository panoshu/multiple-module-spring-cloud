package com.example.gateway.security;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpLogic;
import com.example.gateway.config.GatewaySessionProperties;
import com.example.gateway.order.GatewayFilterOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.auth.api.util.SessionSignatureUtils;
import com.example.auth.api.util.SessionSignatureUtils.SignedPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话上下文注入器 - 将 sa-token 会话信息透传到下游请求头（带签名防伪造）。
 *
 * <p>作为 Spring Cloud Gateway {@link GlobalFilter}，在 sa-token 认证通过后执行：
 * <ol>
 *   <li><b>剥离</b>客户端传入的敏感 header（X-Account-Id、X-Account-Sig、
 *       X-Session-Context、X-Session-Sig、X-Session-Expire），防止外部伪造</li>
 *   <li>从 sa-token Token-Session 读取用户/计划/权限等会话信息</li>
 *   <li>组装为 JSON，Base64 编码后写入 {@code X-Session-Context}</li>
 *   <li>使用 HMAC-SHA256 对 loginId 和 session 上下文签名，写入
 *       {@code X-Account-Id}（payload）、{@code X-Account-Sig}（签名值）、
 *       {@code X-Session-Sig}（session 签名值）、{@code X-Session-Expire}（过期时间戳）</li>
 * </ol>
 *
 * <p>下游业务服务通过 {@link SessionSignatureUtils} 验证签名后取用身份信息，
 * 攻击者没有共享密钥，无法伪造合法签名。
 *
 * <p>当 {@code permission.session.signature-key} 未配置时，向后兼容不签名
 * （仅适用于开发环境，生产环境必须配置）。
 *
 * @author auth-service
 * @since 2026/8/7
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionContextInjector implements GlobalFilter, Ordered {

  /**
   * 透传给下游的会话上下文请求头名（与 kernel SessionContextResolver 保持一致）
   */
  public static final String SESSION_CONTEXT_HEADER = "X-Session-Context";

  /**
   * 透传给下游的账号 ID 请求头名（与 shared-permission-starter DefaultAccountIdResolver 保持一致）
   */
  public static final String ACCOUNT_ID_HEADER = "X-Account-Id";

  /**
   * 账号 ID 签名值请求头名
   */
  public static final String ACCOUNT_SIG_HEADER = "X-Account-Sig";

  /**
   * 会话上下文签名值请求头名
   */
  public static final String SESSION_SIG_HEADER = "X-Session-Sig";

  /**
   * 会话过期时间戳请求头名（epoch 秒）
   */
  public static final String SESSION_EXPIRE_HEADER = "X-Session-Expire";

  /**
   * 需要剥离的敏感请求头（防止客户端伪造）
   */
  private static final List<String> SENSITIVE_HEADERS = List.of(
      ACCOUNT_ID_HEADER,
      ACCOUNT_SIG_HEADER,
      SESSION_CONTEXT_HEADER,
      SESSION_SIG_HEADER,
      SESSION_EXPIRE_HEADER);

  private final ChannelAwareSaRouter channelAwareSaRouter;
  private final ObjectMapper objectMapper;
  private final GatewaySessionProperties sessionProperties;
  private final GatewayProperties gatewayProperties;

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getPath().value();

    // 1. 白名单路径不注入会话头
    if (gatewayProperties.isPublicPath(path)) {
      return chain.filter(exchange);
    }

    // 2. 解析已登录渠道（渠道前缀路径用对应渠道，非渠道前缀路径遍历所有渠道）
    ChannelType channel = ChannelType.fromPath(path);
    ChannelType loginChannel = channel != null ? channel : resolveLoginChannel();
    if (loginChannel == null) {
      return chain.filter(exchange);
    }

    // 3. 从已登录渠道获取 loginId
    StpLogic stpLogic = channelAwareSaRouter.getStpLogic(loginChannel);
    String loginId;
    try {
      loginId = stpLogic.getLoginIdAsString();
    } catch (Exception e) {
      return chain.filter(exchange);
    }
    if (loginId == null || loginId.isBlank()) {
      return chain.filter(exchange);
    }

    // 4. 读取 Token-Session 中的会话数据
    Map<String, Object> sessionContext = buildSessionContext(stpLogic, loginId, loginChannel);
    String encodedContext = encodeSessionContext(sessionContext);

    String signatureKey = sessionProperties.signatureKey();
    long ttlSeconds = sessionProperties.ttlSeconds() > 0
        ? sessionProperties.ttlSeconds()
        : SessionSignatureUtils.DEFAULT_TTL_SECONDS;

    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
        .headers(headers -> SENSITIVE_HEADERS.forEach(headers::remove))
        .header(SESSION_CONTEXT_HEADER, encodedContext)
        .headers(headers -> applySignedHeaders(headers, loginId, encodedContext, signatureKey, ttlSeconds))
        .build();

    ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
    return chain.filter(mutatedExchange);
  }

  /**
   * 遍历所有渠道，返回第一个已登录的渠道类型.
   */
  private ChannelType resolveLoginChannel() {
    for (ChannelType ch : ChannelType.values()) {
      StpLogic stpLogic = channelAwareSaRouter.getStpLogic(ch);
      try {
        if (stpLogic.isLogin()) {
          return ch;
        }
      } catch (Exception ignored) {
        // 该渠道未登录，继续尝试
      }
    }
    return null;
  }

  @Override
  public int getOrder() {
    // 在 AUTH(-200) 之后执行，确保 sa-token 已完成认证
    return GatewayFilterOrder.SESSION_CONTEXT_INJECT.value();
  }

  /**
   * 写入签名后的身份 header。
   *
   * <p>当 signatureKey 为空时（向后兼容），不写入签名，仅保留明文 X-Account-Id。
   * 生产环境必须配置 signatureKey。
   */
  private void applySignedHeaders(
      org.springframework.util.MultiValueMap<String, String> headers,
      String loginId,
      String encodedContext,
      String signatureKey,
      long ttlSeconds) {

    if (signatureKey == null || signatureKey.isEmpty()) {
      // 向后兼容：未配置密钥时仅写入明文（开发环境）
      log.warn("[SessionContextInjector] permission.session.signature-key 未配置,身份信息未签名," +
          "生产环境必须配置此密钥");
      headers.add(ACCOUNT_ID_HEADER, loginId);
      return;
    }

    // 签发 account id 签名
    SignedPayload signed = SessionSignatureUtils.signAccountId(loginId, signatureKey, ttlSeconds);
    headers.add(ACCOUNT_ID_HEADER, signed.payload());
    headers.add(ACCOUNT_SIG_HEADER, signed.signature());

    // 签发 session 上下文签名
    String sessionSig = SessionSignatureUtils.signSessionContext(
        encodedContext, signed.expireAtEpochSecond(), signatureKey);
    headers.add(SESSION_SIG_HEADER, sessionSig);
    headers.add(SESSION_EXPIRE_HEADER, String.valueOf(signed.expireAtEpochSecond()));
  }

  private Map<String, Object> buildSessionContext(StpLogic stpLogic, String loginId, ChannelType channel) {
    Map<String, Object> context = new LinkedHashMap<>();
    context.put("userNo", loginId);
    context.put("channelType", channel != null ? channel.loginType() : "default");

    if (stpLogic == null) {
      return context;
    }

    try {
      String token = stpLogic.getTokenValueByLoginId(loginId);
      if (token != null) {
        SaSession session = stpLogic.getTokenSessionByToken(token);
        if (session != null) {
          // 读取当前计划 ID
          Object planId = session.get(GatewayStpInterfaceImpl.SESSION_KEY_CURRENT_PLAN_ID);
          if (planId != null) {
            context.put("planNo", planId.toString());
          }
        }
      }
    } catch (Exception e) {
      log.debug("[SessionContextInjector] 读取 Token-Session 失败: loginId={}", loginId, e);
    }

    return context;
  }

  private String encodeSessionContext(Map<String, Object> context) {
    try {
      String json = objectMapper.writeValueAsString(context);
      return Base64.getEncoder().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    } catch (Exception e) {
      log.warn("[SessionContextInjector] 序列化会话上下文失败", e);
      return "";
    }
  }

}
