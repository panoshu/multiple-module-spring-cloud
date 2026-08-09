package com.example.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 网关会话签名配置。
 *
 * <p>配置前缀：{@code permission.session}
 *
 * <p>网关在 sa-token 认证通过后，使用 {@code signatureKey} 对用户身份（loginId）
 * 和会话上下文（X-Session-Context）进行 HMAC-SHA256 签名，写入请求头透传给下游业务服务。
 * 业务服务使用同一个 {@code signatureKey} 验证签名，防止请求头被伪造。
 *
 * <p>生产环境必须通过环境变量 {@code PERMISSION_SESSION_SIGNATURE_KEY} 注入，
 * 禁止硬编码在配置文件中。
 *
 * @author shared-permission-starter
 * @since 2026/8/7
 */
@ConfigurationProperties(prefix = "permission.session")
public record GatewaySessionProperties(
    /**
     * HMAC-SHA256 共享密钥（网关与业务服务必须一致）。
     *
     * <p>为空时签名功能不启用（向后兼容，但生产环境必须配置）。
     */
    @DefaultValue("") String signatureKey,

    /**
     * 签名有效期（秒），默认 5 分钟。
     *
     * <p>过期后业务服务拒绝请求，要求重新认证。
     */
    @DefaultValue("300") long ttlSeconds
) {
}