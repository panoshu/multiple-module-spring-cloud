package com.example.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * {@link CommonError} 通用错误码契约测试。
 * <p>
 * 验证通用错误码符合 {@code 08-错误码规范.md} 的要求：
 * <ul>
 *   <li>码段分配：通用码段 0XXXX，00000 表示成功，00001-00099 为通用错误</li>
 *   <li>4xx 类错误使用 00001-00049 段</li>
 *   <li>5xx 类错误使用 00050-00099 段</li>
 *   <li>错误码为 5 位纯数字</li>
 *   <li>消息内容禁止使用 {} 占位符</li>
 *   <li>UNKNOWN_ERROR 常量必须存在（修正历史拼写错误 UNKNOW_ERROR）</li>
 * </ul>
 *
 * @author Trae
 * @since 2026/07/24
 */
@DisplayName("CommonError 通用错误码契约测试")
class CommonErrorTest {

  private static final String CODE_PATTERN = "^\\d{5}$";

  @Nested
  @DisplayName("错误码格式")
  class CodeFormat {

    @ParameterizedTest(name = "{0} 的 code 必须为 5 位纯数字")
    @EnumSource(CommonError.class)
    @DisplayName("所有错误码必须为 5 位纯数字")
    void codeShouldBeFiveDigits(CommonError error) {
      assertThat(error.code())
          .as("%s 的 code 必须匹配 5 位纯数字", error.name())
          .matches(CODE_PATTERN);
    }

    @ParameterizedTest(name = "{0} 的 message 禁止使用占位符")
    @EnumSource(CommonError.class)
    @DisplayName("所有消息禁止使用占位符")
    void messageShouldNotContainPlaceholder(CommonError error) {
      assertThat(error.message())
          .as("%s 的 message 禁止包含占位符", error.name())
          .doesNotContain("{}");
    }

    @ParameterizedTest(name = "{0} 的 message 禁止使用方括号前缀")
    @EnumSource(CommonError.class)
    @DisplayName("所有消息禁止使用方括号前缀")
    void messageShouldNotContainBracketPrefix(CommonError error) {
      assertThat(error.message())
          .as("%s 的 message 禁止以方括号开头", error.name())
          .doesNotStartWith("[");
    }
  }

  @Nested
  @DisplayName("码段分配")
  class CodeSegmentAllocation {

    @Test
    @DisplayName("SUCCESS 必须为 00000")
    void successCodeShouldBeZero() {
      assertThat(CommonError.SUCCESS.code()).isEqualTo("00000");
    }

    @Test
    @DisplayName("4xx 系列错误码应落在 00001-00049 段")
    void clientErrorCodesShouldBeIn4xxSegment() {
      assertThat(CommonError.BAD_REQUEST.code()).isEqualTo("00001");
      assertThat(CommonError.UNAUTHORIZED.code()).isEqualTo("00002");
      assertThat(CommonError.FORBIDDEN.code()).isEqualTo("00003");
      assertThat(CommonError.NOT_FOUND.code()).isEqualTo("00004");
      assertThat(CommonError.METHOD_NOT_ALLOWED.code()).isEqualTo("00005");
      assertThat(CommonError.TOO_MANY_REQUESTS.code()).isEqualTo("00006");
    }

    @Test
    @DisplayName("5xx 系列错误码应落在 00050-00099 段")
    void serverErrorCodesShouldBeIn5xxSegment() {
      assertThat(CommonError.INTERNAL_SERVER_ERROR.code()).isEqualTo("00050");
      assertThat(CommonError.SERVICE_DEGRADATION.code()).isEqualTo("00051");
      assertThat(CommonError.REMOTE_SERVICE_ERROR.code()).isEqualTo("00052");
      assertThat(CommonError.NETWORK_ERROR.code()).isEqualTo("00053");
      assertThat(CommonError.CONCURRENCY_ERROR.code()).isEqualTo("00054");
      assertThat(CommonError.TIMEOUT_ERROR.code()).isEqualTo("00055");
      assertThat(CommonError.UNKNOWN_ERROR.code()).isEqualTo("00099");
    }
  }

  @Nested
  @DisplayName("历史拼写修正")
  class SpellingFix {

    @Test
    @DisplayName("UNKNOWN_ERROR 常量必须存在，修正历史 UNKNOW_ERROR 拼写错误")
    void unknownErrorConstantShouldExist() {
      assertThat(CommonError.UNKNOWN_ERROR)
          .as("UNKNOW_ERROR 已被修正为 UNKNOWN_ERROR")
          .isNotNull();
      assertThat(CommonError.UNKNOWN_ERROR.code()).isEqualTo("00099");
    }
  }

  @Nested
  @DisplayName("ErrorDefinition 契约")
  class ErrorDefinitionContract {

    @Test
    @DisplayName("errorInfo 应为 [code] message 格式")
    void errorInfoShouldFollowPattern() {
      String errorInfo = CommonError.BAD_REQUEST.errorInfo();
      assertThat(errorInfo).isEqualTo("[00001] 请求参数错误");
    }

    @Test
    @DisplayName("message() 必须返回构造时传入的消息，不能返回空字符串")
    void messageShouldNotBeEmpty() {
      for (CommonError error : CommonError.values()) {
        assertThat(error.message())
            .as("%s 的 message 不能为空", error.name())
            .isNotBlank();
      }
    }
  }
}
