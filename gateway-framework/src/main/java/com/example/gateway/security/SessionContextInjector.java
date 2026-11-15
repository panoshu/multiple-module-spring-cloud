package com.example.gateway.security;

import cn.dev33.satoken.stp.StpLogic;
import com.example.gateway.config.GatewaySessionProperties;
import com.example.gateway.order.GatewayFilterOrder;
import com.example.shared.security.ChannelType;
import com.example.shared.security.session.SessionSignatureUtils;
import com.example.shared.security.session.SessionSignatureUtils.SignedPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.List;

/**
 * 会话上下文注入器 - 将 sa-token 会话信息透传到下游请求头（带签名防伪造）。
 *
 * <p>作为 Spring Cloud Gateway {@link GlobalFilter}：
 * <ol>
 *   <li><b>剥离</b>客户端传入的敏感 header（X-Account-Id、X-Account-Sig、
 *       X-Session-Context、X-Session-Sig、X-Session-Expire），防止外部伪造</li>
 *   <li>从 Redis 直读 sa-token 的 token → loginId 映射与 Token-Session 数据</li>
 *   <li>组装为 JSON，Base64 编码后写入 {@code X-Session-Context}</li>
 *   <li>使用 HMAC-SHA256 对 loginId 和 session 上下文签名，写入
 *       {@code X-Account-Id}（payload）、{@code X-Account-Sig}（签名值）、
 *       {@code X-Session-Sig}（session 签名值）、{@code X-Session-Expire}（过期时间戳）</li>
 * </ol>
 *
 * <p><b>为何直读 Redis 而不调用 StpLogic API</b>（批次 74 端到端发现）：sa-token 1.45 的
 * reactor 模式下 {@code SaTokenContextForThreadLocal} 仅在 {@code SaReactorFilter} 的
 * setAuth 回调期间绑定；本过滤器作为 SCG GlobalFilter 在其后执行，任何
 * {@code StpLogic.getLoginIdAsString()} 调用都会抛
 * {@code SaTokenContext 上下文尚未初始化}。sa-token 的 key 拼接方法
 * （{@link StpLogic#splicingKeyTokenValue}/{@link StpLogic#splicingKeyTokenSession}）
 * 是纯字符串拼接、无上下文依赖，配合 ReactiveStringRedisTemplate 直读存储即可在
 * 任意过滤器阶段工作，且与 auth-service 写入的存储天然共享（同一 Redis）。
 *
 * <p>下游业务服务通过 {@link SessionSignatureUtils} 验证签名后取用身份信息，
 * 攻击者没有共享密钥，无法伪造合法签名。
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
  private final ReactiveStringRedisTemplate reactiveRedis;

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getPath().value();

    // 1. 白名单路径不注入会话头
    if (gatewayProperties.isPublicPath(path)) {
      return chain.filter(exchange);
    }

    // 2. 渠道前缀路径用对应渠道；非渠道前缀路径遍历本网关启用渠道
    ChannelType channel = ChannelType.fromPath(path);
    if (channel != null) {
      return injectAndContinue(exchange, chain, channel, path);
    }
    return findLoggedInChannel(exchange)
      .flatMap(found -> injectAndContinue(exchange, chain, found, path).thenReturn(true))
      .switchIfEmpty(Mono.defer(() -> {
        log.warn("[SessionContextInjector] 未识别到已登录渠道，跳过会话注入: path={}", path);
        return chain.filter(exchange).thenReturn(false);
      }))
      .then();
  }

  /**
   * 按渠道完成会话读取与签名注入，随后放行。
   */
  private Mono<Void> injectAndContinue(
    ServerWebExchange exchange, GatewayFilterChain chain, ChannelType channel, String path) {
    StpLogic stpLogic = channelAwareSaRouter.getStpLogic(channel);
    if (stpLogic == null) {
      log.warn("[SessionContextInjector] 渠道 {} 未注册 StpLogic，跳过会话注入", channel);
      return chain.filter(exchange);
    }
    String token = exchange.getRequest().getHeaders().getFirst(stpLogic.getTokenName());
    if (token == null || token.isBlank()) {
      log.warn("[SessionContextInjector] 请求未携带渠道 token，跳过会话注入: path={}, tokenName={}",
        path, stpLogic.getTokenName());
      return chain.filter(exchange);
    }

    String loginIdKey = stpLogic.splicingKeyTokenValue(token);
    String sessionKey = stpLogic.splicingKeyTokenSession(token);

    log.debug("[SessionContextInjector] 读取会话: loginIdKey={}, tokenName={}", loginIdKey, stpLogic.getTokenName());
    Mono<ServerHttpRequest> mutatedRequest = reactiveRedis.opsForValue().get(loginIdKey)
      .doOnNext(loginId -> log.debug("[SessionContextInjector] loginId 命中: {}", loginId))
      .flatMap(loginId -> reactiveRedis.opsForValue().get(sessionKey)
        .map(sessionJson -> buildSessionContextJson(sessionJson, loginId, channel))
        .defaultIfEmpty(fallbackContext(loginId, channel))
        .map(contextJson -> buildMutatedRequest(exchange, loginId, contextJson)));

    return mutatedRequest
      .flatMap(request -> chain.filter(exchange.mutate().request(request).build())
        .thenReturn(true))
      .switchIfEmpty(Mono.defer(() -> {
        log.warn("[SessionContextInjector] token 无效或已过期，跳过会话注入: path={}, token={}",
          path, token);
        return chain.filter(exchange).thenReturn(false);
      }))
      .onErrorResume(e -> {
        log.warn("[SessionContextInjector] 会话读取异常，跳过会话注入: path={}", path, e);
        return chain.filter(exchange).thenReturn(false);
      })
      .then();
  }

  /**
   * 构建注入签名头后的请求：先剥离客户端伪造的敏感头，再写入带签名的身份头。
   */
  private ServerHttpRequest buildMutatedRequest(
    ServerWebExchange exchange, String loginId, String contextJson) {
    String encodedContext = Base64.getEncoder()
      .encodeToString(contextJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    String signatureKey = sessionProperties.signatureKey();
    long ttlSeconds = sessionProperties.ttlSeconds() > 0
      ? sessionProperties.ttlSeconds()
      : SessionSignatureUtils.DEFAULT_TTL_SECONDS;

    return exchange.getRequest().mutate()
      .headers(headers -> SENSITIVE_HEADERS.forEach(headers::remove))
      .headers(headers -> applySignedHeaders(
        headers, loginId, encodedContext, signatureKey, ttlSeconds))
      .build();
  }

  /**
   * 遍历本网关启用渠道，返回第一个在 Redis 中有有效登录态的渠道类型.
   */
  private Mono<ChannelType> findLoggedInChannel(ServerWebExchange exchange) {
    var channels = channelAwareSaRouter.enabledChannels().stream().toList();
    return Mono.defer(() -> {
      var result = Mono.<ChannelType>empty();
      for (ChannelType ch : channels) {
        StpLogic stpLogic = channelAwareSaRouter.getStpLogic(ch);
        if (stpLogic == null) {
          continue;
        }
        String token = exchange.getRequest().getHeaders().getFirst(stpLogic.getTokenName());
        if (token == null || token.isBlank()) {
          continue;
        }
        String loginIdKey = stpLogic.splicingKeyTokenValue(token);
        result = result.switchIfEmpty(reactiveRedis.opsForValue().get(loginIdKey)
          .map(v -> ch));
      }
      return result;
    });
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

    // 透传会话上下文（Base64 JSON），下游 SessionContextResolver 验签后还原
    headers.add(SESSION_CONTEXT_HEADER, encodedContext);

    // 签发 session 上下文签名
    String sessionSig = SessionSignatureUtils.signSessionContext(
      encodedContext, signed.expireAtEpochSecond(), signatureKey);
    headers.add(SESSION_SIG_HEADER, sessionSig);
    headers.add(SESSION_EXPIRE_HEADER, String.valueOf(signed.expireAtEpochSecond()));
  }

  /**
   * 从 Redis 直读的 Token-Session JSON 中提取会话上下文：优先整体透传 auth 写入的
   * 完整声明（SessionClaims 单键），缺失时回退到旧的极简上下文。
   *
   * <p>channelType 使用 {@link ChannelType} 枚举名（INTERNET/HQ/BRANCH），
   * 与 kernel DefaultBusinessAccessGuard 的匹配语义一致。
   */
  private String buildSessionContextJson(String sessionJson, String loginId, ChannelType channel) {
    String claims = extractSessionClaims(sessionJson);
    if (claims != null && !claims.isBlank()) {
      return claims;
    }
    return fallbackContext(loginId, channel);
  }

  /**
   * 从 sa-token 的 Token-Session JSON（Jackson GenericJackson2 序列化）中提取
   * {@code dataMap.sessionClaims}。
   */
  private String extractSessionClaims(String sessionJson) {
    if (sessionJson == null || sessionJson.isBlank()) {
      return null;
    }
    try {
      JsonNode root = objectMapper.readTree(sessionJson);
      JsonNode claims = root.path("dataMap").path("sessionClaims");
      return claims.isTextual() ? claims.asText() : null;
    } catch (Exception e) {
      log.warn("[SessionContextInjector] 解析 Token-Session JSON 失败", e);
      return null;
    }
  }

  /**
   * 回退：兼容未升级会话的极简上下文。
   */
  private String fallbackContext(String loginId, ChannelType channel) {
    try {
      var context = new java.util.LinkedHashMap<String, Object>();
      context.put("userNo", loginId);
      context.put("channelType", channel != null ? channel.name() : "default");
      return objectMapper.writeValueAsString(context);
    } catch (Exception e) {
      log.warn("[SessionContextInjector] 序列化会话上下文失败", e);
      return "{}";
    }
  }

  @Override
  public int getOrder() {
    // 在 AUTH(-200) 之后执行，确保 sa-token 已完成认证
    return GatewayFilterOrder.SESSION_CONTEXT_INJECT.value();
  }

}
