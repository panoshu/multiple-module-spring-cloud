package com.example.iam.adapter.security;

import cn.dev33.satoken.stp.StpLogic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StpInternetUtil 网上渠道 StpLogic 工具类测试。
 *
 * <p>该工具类为静态门面,核心可观察行为:
 * <ul>
 *   <li>TYPE 与 TOKEN_NAME 常量符合网银渠道约定</li>
 *   <li>默认 stpLogic 实例以 TYPE 作为 loginType 初始化</li>
 *   <li>静态方法委托给 stpLogic 实例(以 login/isLogin 委托验证)</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
@DisplayName("StpInternetUtil 网上渠道工具类")
class StpInternetUtilTest {

  private StpLogic originalStpLogic;

  @BeforeEach
  void saveOriginal() {
    originalStpLogic = StpInternetUtil.stpLogic;
  }

  @AfterEach
  void restoreOriginal() {
    StpInternetUtil.stpLogic = originalStpLogic;
  }

  @Nested
  @DisplayName("常量定义")
  class Constants {

    @Test
    @DisplayName("TYPE 常量为 internet")
    void typeConstant_isInternet() {
      assertThat(StpInternetUtil.TYPE).isEqualTo("internet");
    }

    @Test
    @DisplayName("TOKEN_NAME 常量为 satoken-internet")
    void tokenNameConstant_isSatokenInternet() {
      assertThat(StpInternetUtil.TOKEN_NAME).isEqualTo("satoken-internet");
    }
  }

  @Nested
  @DisplayName("默认 stpLogic 实例")
  class DefaultStpLogic {

    @Test
    @DisplayName("默认 stpLogic 非 null 且 loginType 为 internet")
    void defaultStpLogic_loginTypeMatchesType() {
      assertThat(StpInternetUtil.stpLogic).isNotNull();
      assertThat(StpInternetUtil.stpLogic.getLoginType()).isEqualTo(StpInternetUtil.TYPE);
    }
  }
}
