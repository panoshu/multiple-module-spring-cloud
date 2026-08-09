package com.example.gateway.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ChannelType} 渠道类型枚举单元测试。
 *
 * <p>覆盖三个枚举常量的取值,以及 {@link ChannelType#fromPath(String)} 的路径识别逻辑。
 */
@DisplayName("ChannelType 渠道类型枚举测试")
class ChannelTypeTest {

  @Nested
  @DisplayName("枚举常量取值")
  class EnumConstants {

    @Test
    @DisplayName("INTERNET: loginType=internet, tokenHeader=satoken-internet, pathPrefix=/internet")
    void internetConstants() {
      assertThat(ChannelType.INTERNET.loginType()).isEqualTo("internet");
      assertThat(ChannelType.INTERNET.tokenHeader()).isEqualTo("satoken-internet");
      assertThat(ChannelType.INTERNET.pathPrefix()).isEqualTo("/internet");
    }

    @Test
    @DisplayName("HQ: loginType=hq, tokenHeader=satoken-hq, pathPrefix=/hq")
    void hqConstants() {
      assertThat(ChannelType.HQ.loginType()).isEqualTo("hq");
      assertThat(ChannelType.HQ.tokenHeader()).isEqualTo("satoken-hq");
      assertThat(ChannelType.HQ.pathPrefix()).isEqualTo("/hq");
    }

    @Test
    @DisplayName("BRANCH: loginType=branch, tokenHeader=satoken-branch, pathPrefix=/branch")
    void branchConstants() {
      assertThat(ChannelType.BRANCH.loginType()).isEqualTo("branch");
      assertThat(ChannelType.BRANCH.tokenHeader()).isEqualTo("satoken-branch");
      assertThat(ChannelType.BRANCH.pathPrefix()).isEqualTo("/branch");
    }
  }

  @Nested
  @DisplayName("fromPath 根据请求路径识别渠道")
  class FromPath {

    @Test
    @DisplayName("null 路径返回 null")
    void nullPathReturnsNull() {
      assertThat(ChannelType.fromPath(null)).isNull();
    }

    @Test
    @DisplayName("空字符串返回 null")
    void emptyPathReturnsNull() {
      assertThat(ChannelType.fromPath("")).isNull();
    }

    @Test
    @DisplayName("纯空白字符串返回 null")
    void blankPathReturnsNull() {
      assertThat(ChannelType.fromPath("   ")).isNull();
    }

    @Test
    @DisplayName("/internet 精确匹配返回 INTERNET")
    void internetExactMatch() {
      assertThat(ChannelType.fromPath("/internet")).isEqualTo(ChannelType.INTERNET);
    }

    @Test
    @DisplayName("/internet/business/handle 前缀匹配返回 INTERNET")
    void internetWithSubPath() {
      assertThat(ChannelType.fromPath("/internet/business/handle"))
        .isEqualTo(ChannelType.INTERNET);
    }

    @Test
    @DisplayName("/hq 精确匹配返回 HQ")
    void hqExactMatch() {
      assertThat(ChannelType.fromPath("/hq")).isEqualTo(ChannelType.HQ);
    }

    @Test
    @DisplayName("/hq/admin/users 前缀匹配返回 HQ")
    void hqWithSubPath() {
      assertThat(ChannelType.fromPath("/hq/admin/users")).isEqualTo(ChannelType.HQ);
    }

    @Test
    @DisplayName("/branch 精确匹配返回 BRANCH")
    void branchExactMatch() {
      assertThat(ChannelType.fromPath("/branch")).isEqualTo(ChannelType.BRANCH);
    }

    @Test
    @DisplayName("/branch/teller/ops 前缀匹配返回 BRANCH")
    void branchWithSubPath() {
      assertThat(ChannelType.fromPath("/branch/teller/ops")).isEqualTo(ChannelType.BRANCH);
    }

    @Test
    @DisplayName("/actuator/health 不匹配任何渠道返回 null")
    void actuatorPathReturnsNull() {
      assertThat(ChannelType.fromPath("/actuator/health")).isNull();
    }

    @Test
    @DisplayName("/internetX 前缀相似但不带斜杠,不应误匹配")
    void internetSimilarNoSlashDoesNotMatch() {
      assertThat(ChannelType.fromPath("/internetX")).isNull();
    }

    @Test
    @DisplayName("/internets 不应误匹配 INTERNET")
    void internetSimilarPluralDoesNotMatch() {
      assertThat(ChannelType.fromPath("/internets")).isNull();
    }

    @Test
    @DisplayName("/favicon.ico 不匹配任何渠道返回 null")
    void faviconPathReturnsNull() {
      assertThat(ChannelType.fromPath("/favicon.ico")).isNull();
    }
  }
}