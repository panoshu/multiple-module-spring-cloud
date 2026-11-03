package com.example.gateway.security;

import com.example.gateway.config.GatewayChannelProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChannelAwareSaRouter.matchChannel 测试")
class ChannelAwareSaRouterMatchChannelTest {

  private static final GatewayChannelProperties ALL_CHANNELS =
    new GatewayChannelProperties(List.of(ChannelType.INTERNET, ChannelType.HQ, ChannelType.BRANCH));

  private ChannelAwareSaRouter newRouter() {
    return new ChannelAwareSaRouter(ALL_CHANNELS);
  }

  @Test
  @DisplayName("/internet 前缀匹配 INTERNET 渠道")
  void internetPrefixMatches() {
    ChannelAwareSaRouter router = newRouter();
    assertThat(router.matchChannel("/internet/business/handle")).isEqualTo(ChannelType.INTERNET);
  }

  @Test
  @DisplayName("/hq 前缀匹配 HQ 渠道")
  void hqPrefixMatches() {
    ChannelAwareSaRouter router = newRouter();
    assertThat(router.matchChannel("/hq/users/list")).isEqualTo(ChannelType.HQ);
  }

  @Test
  @DisplayName("/branch 前缀匹配 BRANCH 渠道")
  void branchPrefixMatches() {
    ChannelAwareSaRouter router = newRouter();
    assertThat(router.matchChannel("/branch/auth/login")).isEqualTo(ChannelType.BRANCH);
  }

  @Test
  @DisplayName("非渠道前缀返回 null")
  void nonChannelPrefixReturnsNull() {
    ChannelAwareSaRouter router = newRouter();
    assertThat(router.matchChannel("/admin/users/list")).isNull();
    assertThat(router.matchChannel("/actuator/health")).isNull();
  }

  @Test
  @DisplayName("null/空路径返回 null")
  void nullPathReturnsNull() {
    ChannelAwareSaRouter router = newRouter();
    assertThat(router.matchChannel(null)).isNull();
    assertThat(router.matchChannel("")).isNull();
    assertThat(router.matchChannel("   ")).isNull();
  }

  @Test
  @DisplayName("/internet 不带斜杠后缀时也能匹配")
  void internetExactPathMatches() {
    ChannelAwareSaRouter router = newRouter();
    assertThat(router.matchChannel("/internet")).isEqualTo(ChannelType.INTERNET);
  }

  @Test
  @DisplayName("未启用渠道前缀返回 null(配置驱动)")
  void unconfiguredChannelPathReturnsNull() {
    ChannelAwareSaRouter router = new ChannelAwareSaRouter(
      new GatewayChannelProperties(List.of(ChannelType.INTERNET)));
    assertThat(router.matchChannel("/hq/users/list")).isNull();
    assertThat(router.matchChannel("/branch/auth/login")).isNull();
  }
}
