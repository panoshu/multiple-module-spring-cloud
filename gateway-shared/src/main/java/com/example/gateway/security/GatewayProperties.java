package com.example.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.AntPathMatcher;

import java.util.List;

/**
 * 网关层白名单配置.
 *
 * <p>配置前缀：{@code auth.gateway}
 *
 * <p>对应设计文档 3.5 节：消费 {@code auth.gateway.public-paths} 配置，
 * 替代 SaReactorFilter 中的硬编码 {@code addExclude}。
 *
 * @author demo-gateway
 * @since 2026/8/7
 */
@ConfigurationProperties(prefix = "auth.gateway")
public record GatewayProperties(
    /**
     * 公共白名单路径模式（Ant 风格，如 /actuator/**）。
     *
     * <p>白名单内路径跳过登录校验。
     */
    List<String> publicPaths
) {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    public GatewayProperties {
        if (publicPaths == null) {
            publicPaths = List.of();
        }
    }

    /**
     * 判断路径是否在白名单中。
     *
     * @param path 请求路径
     * @return true 表示白名单路径（跳过登录校验）
     */
    public boolean isPublicPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        for (String pattern : publicPaths) {
            if (MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}