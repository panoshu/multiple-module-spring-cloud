package com.example.shared.permission;

import com.example.auth.api.util.SessionSignatureUtils;

/**
 * 会话上下文签名验证 SPI。
 *
 * <p>用于验证网关签发的 {@code X-Session-Context} 签名，防止请求头被伪造。
 * <p>
 * 默认实现 {@link DefaultSessionContextSignatureVerifier} 使用
 * {@link SessionSignatureUtils} 进行 HMAC-SHA256 验签。
 *
 * <p>kernel 的 {@code SessionContextResolver} 通过 Spring 注入此接口
 * （{@code @Autowired(required = false)}），如果 Bean 存在则验签，
 * 不存在则跳过（兼容测试场景或未引入 shared-permission-starter 的场景）。
 *
 * @author shared-permission-starter
 * @since 2026/8/7
 */
public interface SessionContextSignatureVerifier {

  /**
   * 验证 session 上下文签名 + 过期时间。
   *
   * @param sessionContextBase64 Base64 编码的 session 上下文 JSON（{@code X-Session-Context} header 值）
   * @param signature            签名值（{@code X-Session-Sig} header 值）
   * @param expireAtEpochSecond  过期时间戳（{@code X-Session-Expire} header 值）
   * @return true=签名正确且未过期；false=签名错误或已过期
   */
  boolean verify(String sessionContextBase64, String signature, long expireAtEpochSecond);
}
