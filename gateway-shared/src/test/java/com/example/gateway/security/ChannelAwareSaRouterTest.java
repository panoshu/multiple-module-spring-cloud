package com.example.gateway.security;

import cn.dev33.satoken.stp.StpLogic;
import com.example.gateway.config.GatewayChannelProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ChannelAwareSaRouter} 渠道感知路由器单元测试。
 *
 * <p>覆盖构造函数初始化、{@link ChannelAwareSaRouter#getStpLogic(ChannelType)} 与
 * {@link ChannelAwareSaRouter#getChannelByLoginType(String)} 两个可观察方法。
 *
 * <p>注:{@link ChannelAwareSaRouter#matchAndCheckLogin()} 依赖 SaHolder.getRequest(),
 * 属于 sa-token reactor 运行时上下文,难以在纯单元测试中模拟,本测试不覆盖该方法。
 */
@DisplayName("ChannelAwareSaRouter 渠道感知路由器测试")
class ChannelAwareSaRouterTest {

  private static final GatewayChannelProperties ALL_CHANNELS =
      new GatewayChannelProperties(List.of(ChannelType.INTERNET, ChannelType.HQ, ChannelType.BRANCH));

  private ChannelAwareSaRouter router;

  @BeforeEach
  void setUp() {
    router = new ChannelAwareSaRouter(ALL_CHANNELS);
  }

  @Nested
  @DisplayName("构造函数:初始化已启用渠道 StpLogic")
  class Constructor {

    @Test
    @DisplayName("INTERNET 渠道 StpLogic 的 loginType 与 tokenName 正确")
    void internetStpLogicInitialized() {
      StpLogic logic = router.getStpLogic(ChannelType.INTERNET);
      assertThat(logic).isNotNull();
      assertThat(logic.getLoginType()).isEqualTo("internet");
      assertThat(logic.getConfig().getTokenName()).isEqualTo("satoken-internet");
    }

    @Test
    @DisplayName("HQ 渠道 StpLogic 的 loginType 与 tokenName 正确")
    void hqStpLogicInitialized() {
      StpLogic logic = router.getStpLogic(ChannelType.HQ);
      assertThat(logic).isNotNull();
      assertThat(logic.getLoginType()).isEqualTo("hq");
      assertThat(logic.getConfig().getTokenName()).isEqualTo("satoken-hq");
    }

    @Test
    @DisplayName("BRANCH 渠道 StpLogic 的 loginType 与 tokenName 正确")
    void branchStpLogicInitialized() {
      StpLogic logic = router.getStpLogic(ChannelType.BRANCH);
      assertThat(logic).isNotNull();
      assertThat(logic.getLoginType()).isEqualTo("branch");
      assertThat(logic.getConfig().getTokenName()).isEqualTo("satoken-branch");
    }

    @Test
    @DisplayName("所有已启用渠道 StpLogic 显式开启 Header 读取(前后台分离)")
    void allChannelsReadHeaderEnabled() {
      for (ChannelType channel : List.of(ChannelType.INTERNET, ChannelType.HQ, ChannelType.BRANCH)) {
        StpLogic logic = router.getStpLogic(channel);
        assertThat(logic.getConfig().getIsReadHeader())
          .as("渠道 %s 必须显式开启 Header 读取", channel)
          .isTrue();
      }
    }

    @Test
    @DisplayName("所有已启用渠道 StpLogic 显式关闭 Cookie 读取(避免 CSRF 风险)")
    void allChannelsReadCookieDisabled() {
      for (ChannelType channel : List.of(ChannelType.INTERNET, ChannelType.HQ, ChannelType.BRANCH)) {
        StpLogic logic = router.getStpLogic(channel);
        assertThat(logic.getConfig().getIsReadCookie())
          .as("渠道 %s 必须显式关闭 Cookie 读取", channel)
          .isFalse();
      }
    }

    @Test
    @DisplayName("未启用渠道不注册 StpLogic(配置驱动)")
    void unconfiguredChannelNotRegistered() {
      ChannelAwareSaRouter single = new ChannelAwareSaRouter(
          new GatewayChannelProperties(List.of(ChannelType.INTERNET)));
      assertThat(single.getStpLogic(ChannelType.INTERNET)).isNotNull();
      assertThat(single.getStpLogic(ChannelType.HQ)).isNull();
      assertThat(single.getStpLogic(ChannelType.BRANCH)).isNull();
    }
  }

  @Nested
  @DisplayName("getStpLogic 获取渠道 StpLogic")
  class GetStpLogic {

    @Test
    @DisplayName("INTERNET 返回非 null StpLogic")
    void internetReturnsLogic() {
      assertThat(router.getStpLogic(ChannelType.INTERNET)).isNotNull();
    }

    @Test
    @DisplayName("HQ 返回非 null StpLogic")
    void hqReturnsLogic() {
      assertThat(router.getStpLogic(ChannelType.HQ)).isNotNull();
    }

    @Test
    @DisplayName("BRANCH 返回非 null StpLogic")
    void branchReturnsLogic() {
      assertThat(router.getStpLogic(ChannelType.BRANCH)).isNotNull();
    }

    @Test
    @DisplayName("每个渠道返回独立的 StpLogic 实例")
    void eachChannelReturnsDistinctLogic() {
      StpLogic internet = router.getStpLogic(ChannelType.INTERNET);
      StpLogic hq = router.getStpLogic(ChannelType.HQ);
      StpLogic branch = router.getStpLogic(ChannelType.BRANCH);

      assertThat(internet).isNotSameAs(hq);
      assertThat(internet).isNotSameAs(branch);
      assertThat(hq).isNotSameAs(branch);
    }
  }

  @Nested
  @DisplayName("getChannelByLoginType 根据 loginType 获取渠道")
  class GetChannelByLoginType {

    @Test
    @DisplayName("internet 返回 INTERNET")
    void internetLoginType() {
      assertThat(router.getChannelByLoginType("internet")).isEqualTo(ChannelType.INTERNET);
    }

    @Test
    @DisplayName("hq 返回 HQ")
    void hqLoginType() {
      assertThat(router.getChannelByLoginType("hq")).isEqualTo(ChannelType.HQ);
    }

    @Test
    @DisplayName("branch 返回 BRANCH")
    void branchLoginType() {
      assertThat(router.getChannelByLoginType("branch")).isEqualTo(ChannelType.BRANCH);
    }

    @Test
    @DisplayName("未知 loginType 返回 null")
    void unknownLoginTypeReturnsNull() {
      assertThat(router.getChannelByLoginType("unknown")).isNull();
    }

    @Test
    @DisplayName("null loginType 返回 null")
    void nullLoginTypeReturnsNull() {
      assertThat(router.getChannelByLoginType(null)).isNull();
    }

    @Test
    @DisplayName("大小写敏感:大写 INTERNET 不匹配 internet")
    void caseSensitive() {
      assertThat(router.getChannelByLoginType("INTERNET")).isNull();
    }
  }

  @Nested
  @DisplayName("enabledChannels 返回本网关启用渠道")
  class EnabledChannels {

    @Test
    @DisplayName("配置三渠道时返回三渠道")
    void allChannelsListed() {
      assertThat(router.enabledChannels())
        .containsExactly(ChannelType.INTERNET, ChannelType.HQ, ChannelType.BRANCH);
    }

    @Test
    @DisplayName("配置单渠道时仅返回该渠道")
    void singleChannelListed() {
      ChannelAwareSaRouter single = new ChannelAwareSaRouter(
          new GatewayChannelProperties(List.of(ChannelType.BRANCH)));
      assertThat(single.enabledChannels()).containsExactly(ChannelType.BRANCH);
    }
  }
}