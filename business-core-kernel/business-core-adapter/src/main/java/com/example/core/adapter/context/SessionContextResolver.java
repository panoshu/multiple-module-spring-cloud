package com.example.core.adapter.context;

import com.example.core.api.context.SessionContext;
import com.example.shared.exception.BusinessException;
import com.example.shared.exception.CommonError;
import com.example.shared.permission.SessionContextSignatureVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.util.Base64;
import java.util.Optional;

/**
 * 会话上下文解析器（带签名验证）。
 *
 * <p>从 HTTP 请求的 {@code X-Session-Context} header 解析 {@link SessionContext}，
 * 并通过 {@link SessionContextSignatureVerifier} 验证签名，防止请求头被伪造。
 *
 * <p>header 内容为 Base64 编码的 JSON，由 gateway 从 sa-token Token-Session 读取后写入，
 * 同时写入 {@code X-Session-Sig}（签名值）和 {@code X-Session-Expire}（过期时间戳）。
 *
 * <p>验签流程：
 * <ol>
 *   <li>读取 {@code X-Session-Context}、{@code X-Session-Sig}、{@code X-Session-Expire} header</li>
 *   <li>通过 {@link SessionContextSignatureVerifier} 验证签名 + 过期</li>
 *   <li>验签通过后解析 Base64 JSON 为 {@link SessionContext}</li>
 *   <li>验签失败返回 empty（调用方按 fail-closed 处理）</li>
 * </ol>
 *
 * <p>{@link SessionContextSignatureVerifier} 通过 Spring 注入（可选）：
 * <ul>
 *   <li>引入 shared-permission-starter 时自动装配验签器</li>
 *   <li>未引入时（测试场景）不验签，直接解析 header</li>
 * </ul>
 *
 * <p>kernel 不直接依赖 sa-token，通过本组件与 sa-token 解耦，保持可独立测试。
 *
 * @author panoshu
 */
@Slf4j
@Component
public class SessionContextResolver {

  private static final String SESSION_HEADER = "X-Session-Context";
  private static final String SESSION_SIG_HEADER = "X-Session-Sig";
  private static final String SESSION_EXPIRE_HEADER = "X-Session-Expire";

  private final ObjectMapper objectMapper;

  /**
   * 会话上下文签名验证器（可选注入）。
   *
   * <p>由 shared-permission-starter 自动装配。未引入时为 null（测试场景跳过验签）。
   */
  private final SessionContextSignatureVerifier signatureVerifier;

  @Autowired
  public SessionContextResolver(ObjectMapper objectMapper,
                                ObjectProvider<SessionContextSignatureVerifier> signatureVerifierProvider) {
    this.objectMapper = objectMapper;
    this.signatureVerifier = signatureVerifierProvider.getIfAvailable();
  }

  /**
   * 测试专用构造函数（不验签）。
   */
  public SessionContextResolver(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.signatureVerifier = null;
  }

  /**
   * 解析会话上下文，header 缺失或验签失败时返回 empty。
   */
  public Optional<SessionContext> optional() {
    HttpServletRequest request = currentRequest();
    if (request == null) {
      return Optional.empty();
    }
    return optional(request);
  }

  /**
   * 解析会话上下文，header 缺失时抛 BusinessException。
   */
  public SessionContext require() {
    return optional()
      .orElseThrow(() -> new BusinessException(CommonError.UNAUTHORIZED)
        .withUserDetail("会话上下文缺失，请重新登录")
        .withLogDetail("X-Session-Context header 缺失或验签失败"));
  }

  /**
   * 从指定请求解析会话上下文，header 缺失或验签失败时返回 empty。
   */
  public Optional<SessionContext> optional(HttpServletRequest request) {
    if (request == null) {
      return Optional.empty();
    }
    String header = request.getHeader(SESSION_HEADER);
    if (header == null || header.isBlank()) {
      return Optional.empty();
    }

    // 验签：当验签器存在时，验证签名 + 过期时间
    if (!verifySignature(request, header)) {
      log.warn("[SessionContextResolver] X-Session-Context 签名验证失败，疑似伪造请求");
      return Optional.empty();
    }

    try {
      byte[] decoded = Base64.getDecoder().decode(header);
      return Optional.of(objectMapper.readValue(decoded, SessionContext.class));
    } catch (IOException | IllegalArgumentException e) {
      log.warn("[SessionContextResolver] 解析 X-Session-Context header 失败: {}", e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * 从指定请求解析，缺失时抛异常。
   */
  public SessionContext require(HttpServletRequest request) {
    return optional(request)
      .orElseThrow(() -> new BusinessException(CommonError.UNAUTHORIZED)
        .withUserDetail("会话上下文缺失，请重新登录")
        .withLogDetail("X-Session-Context header 缺失或验签失败"));
  }

  /**
   * 验证 session 上下文签名。
   *
   * <p>当验签器不存在时（测试场景或未引入 shared-permission-starter）跳过验签。
   *
   * @return true=验签通过或无需验签；false=验签失败
   */
  private boolean verifySignature(HttpServletRequest request, String sessionContextBase64) {
    if (signatureVerifier == null) {
      // 未引入 shared-permission-starter（测试场景），跳过验签
      return true;
    }

    String signature = request.getHeader(SESSION_SIG_HEADER);
    String expireStr = request.getHeader(SESSION_EXPIRE_HEADER);

    if (signature == null || signature.isBlank() || expireStr == null || expireStr.isBlank()) {
      log.warn("[SessionContextResolver] X-Session-Sig 或 X-Session-Expire header 缺失，疑似伪造请求");
      return false;
    }

    long expireAt;
    try {
      expireAt = Long.parseLong(expireStr);
    } catch (NumberFormatException e) {
      log.warn("[SessionContextResolver] X-Session-Expire 格式非法: {}", expireStr);
      return false;
    }

    return signatureVerifier.verify(sessionContextBase64, signature, expireAt);
  }

  private HttpServletRequest currentRequest() {
    RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
    if (attrs instanceof ServletRequestAttributes servletAttrs) {
      return servletAttrs.getRequest();
    }
    return null;
  }
}
