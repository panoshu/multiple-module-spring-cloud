package com.example.shared.permission;

import com.example.auth.api.util.SessionSignatureUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 默认账号 ID 解析器：从当前请求的 {@code X-Account-Id} 请求头取（带签名验证）。
 *
 * <p>网关在 sa-token 校验通过后，用 HMAC-SHA256 对 {@code loginId + 过期时间戳} 签名，
 * 写入 {@code X-Account-Id}（payload，格式 {@code loginId:expireEpochSecond}）和
 * {@code X-Account-Sig}（签名值）。
 *
 * <p>验签失败或 header 缺失时返回 null，切面将按 fail-closed 原则拒绝请求。
 *
 * @author shared-permission-starter
 */
@Slf4j
public class DefaultAccountIdResolver implements AccountIdResolver {

  public static final String ACCOUNT_ID_HEADER = "X-Account-Id";
  public static final String ACCOUNT_SIG_HEADER = "X-Account-Sig";

  private final String signatureKey;

  public DefaultAccountIdResolver(String signatureKey) {
    this.signatureKey = signatureKey;
  }

  @Override
  public String resolve(ProceedingJoinPoint joinPoint) {
    HttpServletRequest request = currentRequest();
    if (request == null) {
      return null;
    }

    String accountIdPayload = request.getHeader(ACCOUNT_ID_HEADER);
    if (accountIdPayload == null || accountIdPayload.isBlank()) {
      return null;
    }

    if (signatureKey == null || signatureKey.isBlank()) {
      // 未配置密钥，信任网关透传
      return extractLoginId(accountIdPayload);
    }

    // 配置密钥，验签
    String signature = request.getHeader(ACCOUNT_SIG_HEADER);
    if (signature == null || signature.isBlank()) {
      log.warn("[AccountIdResolver] X-Account-Sig 缺失");
      return null;
    }
    return SessionSignatureUtils.verifyAccountId(accountIdPayload, signature, signatureKey);
  }

  private String extractLoginId(String payload) {
    int idx = payload.lastIndexOf(SessionSignatureUtils.PAYLOAD_SEPARATOR);
    return idx > 0 ? payload.substring(0, idx) : payload;
  }

  private HttpServletRequest currentRequest() {
    RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
    if (attrs instanceof ServletRequestAttributes servletAttrs) {
      return servletAttrs.getRequest();
    }
    return null;
  }
}
