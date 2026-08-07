package com.example.gateway.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChannelAwareSaRouter.matchChannel 测试")
class ChannelAwareSaRouterMatchChannelTest {

    @Test
    @DisplayName("/internet 前缀匹配 INTERNET 渠道")
    void internetPrefixMatches() {
        ChannelAwareSaRouter router = new ChannelAwareSaRouter();
        assertThat(router.matchChannel("/internet/business/handle")).isEqualTo(ChannelType.INTERNET);
    }

    @Test
    @DisplayName("/hq 前缀匹配 HQ 渠道")
    void hqPrefixMatches() {
        ChannelAwareSaRouter router = new ChannelAwareSaRouter();
        assertThat(router.matchChannel("/hq/users/list")).isEqualTo(ChannelType.HQ);
    }

    @Test
    @DisplayName("/branch 前缀匹配 BRANCH 渠道")
    void branchPrefixMatches() {
        ChannelAwareSaRouter router = new ChannelAwareSaRouter();
        assertThat(router.matchChannel("/branch/auth/login")).isEqualTo(ChannelType.BRANCH);
    }

    @Test
    @DisplayName("非渠道前缀返回 null")
    void nonChannelPrefixReturnsNull() {
        ChannelAwareSaRouter router = new ChannelAwareSaRouter();
        assertThat(router.matchChannel("/admin/users/list")).isNull();
        assertThat(router.matchChannel("/actuator/health")).isNull();
    }

    @Test
    @DisplayName("null/空路径返回 null")
    void nullPathReturnsNull() {
        ChannelAwareSaRouter router = new ChannelAwareSaRouter();
        assertThat(router.matchChannel(null)).isNull();
        assertThat(router.matchChannel("")).isNull();
        assertThat(router.matchChannel("   ")).isNull();
    }

    @Test
    @DisplayName("/internet 不带斜杠后缀时也能匹配")
    void internetExactPathMatches() {
        ChannelAwareSaRouter router = new ChannelAwareSaRouter();
        assertThat(router.matchChannel("/internet")).isEqualTo(ChannelType.INTERNET);
    }
}
