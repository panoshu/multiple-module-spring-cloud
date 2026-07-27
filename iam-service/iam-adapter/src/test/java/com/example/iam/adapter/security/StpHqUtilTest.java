package com.example.iam.adapter.security;

import cn.dev33.satoken.stp.StpLogic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * StpHqUtil 总部渠道 StpLogic 工具类测试。
 *
 * <p>该工具类为静态门面,核心可观察行为:
 * <ul>
 *   <li>TYPE 与 TOKEN_NAME 常量符合总行渠道约定</li>
 *   <li>默认 stpLogic 实例以 TYPE 作为 loginType 初始化</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
@DisplayName("StpHqUtil 总部渠道工具类")
class StpHqUtilTest {

  private StpLogic originalStpLogic;

  @BeforeEach
  void saveOriginal() {
    originalStpLogic = StpHqUtil.stpLogic;
  }

  @AfterEach
  void restoreOriginal() {
    StpHqUtil.stpLogic = originalStpLogic;
  }

  @Nested
  @DisplayName("常量定义")
  class Constants {

    @Test
    @DisplayName("TYPE 常量为 hq")
    void typeConstant_isHq() {
      assertThat(StpHqUtil.TYPE).isEqualTo("hq");
    }

    @Test
    @DisplayName("TOKEN_NAME 常量为 satoken-hq")
    void tokenNameConstant_isSatokenHq() {
      assertThat(StpHqUtil.TOKEN_NAME).isEqualTo("satoken-hq");
    }
  }

  @Nested
  @DisplayName("默认 stpLogic 实例")
  class DefaultStpLogic {

    @Test
    @DisplayName("默认 stpLogic 非 null 且 loginType 为 hq")
    void defaultStpLogic_loginTypeMatchesType() {
      assertThat(StpHqUtil.stpLogic).isNotNull();
      assertThat(StpHqUtil.stpLogic.getLoginType()).isEqualTo(StpHqUtil.TYPE);
    }
  }
}
