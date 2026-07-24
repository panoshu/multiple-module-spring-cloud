package com.example.shared.lock.errorcode;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * {@link LockErrorDefinition} 错误码契约测试。
 * <p>
 * 验证错误码符合 {@code 08-错误码规范.md}：
 * <ul>
 *   <li>层级字符串格式 SHARED.LOCK.XXXX</li>
 *   <li>码段 SHARED.LOCK.0001-SHARED.LOCK.0099（shared-lock）</li>
 *   <li>消息禁止 {} 占位符和方括号前缀</li>
 *   <li>message() 返回实际消息而非空串</li>
 *   <li>各枚举的 code 唯一</li>
 * </ul>
 *
 * @author Trae
 * @since 2026/07/24
 */
@DisplayName("LockErrorDefinition 错误码契约测试")
class LockErrorDefinitionTest {

  @ParameterizedTest(name = "{0} 的 code 必须匹配层级字符串格式 SHARED.LOCK.XXXX")
  @EnumSource(LockErrorDefinition.class)
  @DisplayName("所有错误码必须匹配层级字符串格式 SHARED.LOCK.XXXX")
  void codeShouldBeFiveDigits(LockErrorDefinition error) {
    assertThat(error.code())
        .as("%s 的 code 必须匹配层级字符串格式 SHARED.LOCK.XXXX", error.name())
        .matches("^SHARED\\.LOCK\\.\\d{4}$");
  }

  @ParameterizedTest(name = "{0} 的 code 应以 SHARED.LOCK. 为前缀")
  @EnumSource(LockErrorDefinition.class)
  @DisplayName("所有 code 应落在 shared-lock 码段 SHARED.LOCK.XXXX")
  void codeShouldBeInLockSegment(LockErrorDefinition error) {
    assertThat(error.code())
        .as("%s 的 code 应以 SHARED.LOCK. 为前缀", error.name())
        .startsWith("SHARED.LOCK.");
  }

  @ParameterizedTest(name = "{0} 的 message 禁止使用占位符和方括号前缀")
  @EnumSource(LockErrorDefinition.class)
  @DisplayName("所有消息禁止使用 {} 占位符和方括号前缀")
  void messageShouldNotContainPlaceholderOrBracket(LockErrorDefinition error) {
    assertThat(error.message())
        .as("%s 的 message 禁止包含 {} 占位符", error.name())
        .doesNotContain("{}");
    assertThat(error.message())
        .as("%s 的 message 禁止以方括号开头", error.name())
        .doesNotStartWith("[");
  }

  @ParameterizedTest(name = "{0} 的 message 不应为空串")
  @EnumSource(LockErrorDefinition.class)
  @DisplayName("所有错误码的 message 都不应为空串")
  void messageShouldNotBeEmpty(LockErrorDefinition error) {
    assertThat(error.message())
        .as("%s 的 message 不应为空", error.name())
        .isNotBlank();
  }

  @Test
  @DisplayName("各枚举的 code 应唯一")
  void codeShouldBeUnique() {
    long distinctCount = Arrays.stream(LockErrorDefinition.values())
        .map(LockErrorDefinition::code)
        .distinct()
        .count();
    assertThat(distinctCount).isEqualTo(LockErrorDefinition.values().length);
  }

  @Test
  @DisplayName("GET_LOCK_FAILED 应存在且码值为 SHARED.LOCK.0001")
  void getLockFailedShouldExist() {
    assertThat(LockErrorDefinition.GET_LOCK_FAILED.code()).isEqualTo("SHARED.LOCK.0001");
    assertThat(LockErrorDefinition.GET_LOCK_FAILED.message()).isEqualTo("获取锁失败");
  }
}
