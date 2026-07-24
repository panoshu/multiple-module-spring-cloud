package com.example.shared.cache.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * {@link CacheErrorDefinition} 错误码契约测试。
 * <p>
 * 验证错误码符合 {@code 08-错误码规范.md}：
 * <ul>
 *   <li>5 位纯数字</li>
 *   <li>码段 13001-13099（shared-cache-starter）</li>
 *   <li>消息禁止 {} 占位符和方括号前缀</li>
 *   <li>message() 返回实际消息而非空串</li>
 *   <li>各枚举的 code 唯一</li>
 * </ul>
 *
 * @author Trae
 * @since 2026/07/24
 */
@DisplayName("CacheErrorDefinition 错误码契约测试")
class CacheErrorDefinitionTest {

  @ParameterizedTest(name = "{0} 的 code 必须为 5 位纯数字")
  @EnumSource(CacheErrorDefinition.class)
  @DisplayName("所有错误码必须为 5 位纯数字")
  void codeShouldBeFiveDigits(CacheErrorDefinition error) {
    assertThat(error.code())
        .as("%s 的 code 必须为 5 位纯数字", error.name())
        .matches("\\d{5}");
  }

  @ParameterizedTest(name = "{0} 的 code 应在 13001-13099 码段")
  @EnumSource(CacheErrorDefinition.class)
  @DisplayName("所有 code 应落在 shared-cache-starter 码段 13001-13099")
  void codeShouldBeInCacheSegment(CacheErrorDefinition error) {
    int code = Integer.parseInt(error.code());
    assertThat(code)
        .as("%s 的 code 应在 13001-13099 区间", error.name())
        .isBetween(13001, 13099);
  }

  @ParameterizedTest(name = "{0} 的 message 禁止使用占位符和方括号前缀")
  @EnumSource(CacheErrorDefinition.class)
  @DisplayName("所有消息禁止使用 {} 占位符和方括号前缀")
  void messageShouldNotContainPlaceholderOrBracket(CacheErrorDefinition error) {
    assertThat(error.message())
        .as("%s 的 message 禁止包含 {} 占位符", error.name())
        .doesNotContain("{}");
    assertThat(error.message())
        .as("%s 的 message 禁止以方括号开头", error.name())
        .doesNotStartWith("[");
  }

  @ParameterizedTest(name = "{0} 的 message 不应为空串")
  @EnumSource(CacheErrorDefinition.class)
  @DisplayName("所有错误码的 message 都不应为空串")
  void messageShouldNotBeEmpty(CacheErrorDefinition error) {
    assertThat(error.message())
        .as("%s 的 message 不应为空", error.name())
        .isNotBlank();
  }

  @Test
  @DisplayName("各枚举的 code 应唯一")
  void codeShouldBeUnique() {
    long distinctCount = Arrays.stream(CacheErrorDefinition.values())
        .map(CacheErrorDefinition::code)
        .distinct()
        .count();
    assertThat(distinctCount).isEqualTo(CacheErrorDefinition.values().length);
  }

  @Test
  @DisplayName("GET_LOCK_FAILED 应存在且码值为 13001")
  void getLockFailedShouldExist() {
    assertThat(CacheErrorDefinition.GET_LOCK_FAILED.code()).isEqualTo("13001");
    assertThat(CacheErrorDefinition.GET_LOCK_FAILED.message()).isEqualTo("获取锁失败");
  }
}
