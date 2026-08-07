package com.example.gateway.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GatewayProperties 白名单匹配测试")
class GatewayPropertiesTest {

    @Test
    @DisplayName("精确路径匹配")
    void exactPathMatches() {
        GatewayProperties properties = new GatewayProperties(List.of("/actuator"));
        assertThat(properties.isPublicPath("/actuator")).isTrue();
    }

    @Test
    @DisplayName("Ant 模式 ** 匹配子路径")
    void antPatternMatchesSubpaths() {
        GatewayProperties properties = new GatewayProperties(List.of("/actuator/**"));
        assertThat(properties.isPublicPath("/actuator/health")).isTrue();
        assertThat(properties.isPublicPath("/actuator/info/details")).isTrue();
    }

    @Test
    @DisplayName("非白名单路径返回 false")
    void nonWhitelistedPathReturnsFalse() {
        GatewayProperties properties = new GatewayProperties(List.of("/actuator/**"));
        assertThat(properties.isPublicPath("/admin/users/list")).isFalse();
    }

    @Test
    @DisplayName("空白名单时所有路径都返回 false")
    void emptyWhitelistReturnsFalse() {
        GatewayProperties properties = new GatewayProperties(List.of());
        assertThat(properties.isPublicPath("/any/path")).isFalse();
    }

    @Test
    @DisplayName("null 白名单时所有路径都返回 false")
    void nullWhitelistReturnsFalse() {
        GatewayProperties properties = new GatewayProperties(null);
        assertThat(properties.isPublicPath("/any/path")).isFalse();
    }
}
