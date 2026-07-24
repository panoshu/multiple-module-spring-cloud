package com.example.file.domain.errorcode;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * {@link FileErrorCodes} 错误码契约测试。
 * <p>
 * 验证错误码符合 {@code 08-错误码规范.md}：
 * <ul>
 *   <li>层级字符串格式 SERVICE.FILE.XXXX</li>
 *   <li>码段 SERVICE.FILE.0001-SERVICE.FILE.0099（file-service）</li>
 *   <li>消息禁止 {} 占位符和方括号前缀</li>
 *   <li>各枚举的 code 唯一</li>
 * </ul>
 *
 * @author Trae
 * @since 2026/07/24
 */
@DisplayName("FileErrorCodes 错误码契约测试")
class FileErrorCodesTest {

  @ParameterizedTest(name = "{0} 的 code 必须匹配层级字符串格式 SERVICE.FILE.XXXX")
  @EnumSource(FileErrorCodes.class)
  @DisplayName("所有错误码必须匹配层级字符串格式 SERVICE.FILE.XXXX")
  void codeShouldBeFiveDigits(FileErrorCodes error) {
    assertThat(error.code())
        .as("%s 的 code 必须匹配层级字符串格式 SERVICE.FILE.XXXX", error.name())
        .matches("^SERVICE\\.FILE\\.\\d{4}$");
  }

  @ParameterizedTest(name = "{0} 的 code 应以 SERVICE.FILE. 为前缀")
  @EnumSource(FileErrorCodes.class)
  @DisplayName("所有 code 应落在 file-service 码段 SERVICE.FILE.XXXX")
  void codeShouldBeInFileSegment(FileErrorCodes error) {
    assertThat(error.code())
        .as("%s 的 code 应以 SERVICE.FILE. 为前缀", error.name())
        .startsWith("SERVICE.FILE.");
  }

  @ParameterizedTest(name = "{0} 的 message 禁止使用占位符和方括号前缀")
  @EnumSource(FileErrorCodes.class)
  @DisplayName("所有消息禁止使用 {} 占位符和方括号前缀")
  void messageShouldNotContainPlaceholderOrBracket(FileErrorCodes error) {
    assertThat(error.message()).doesNotContain("{}");
    assertThat(error.message()).doesNotStartWith("[");
  }

  @ParameterizedTest(name = "{0} 的 message 不应为空串")
  @EnumSource(FileErrorCodes.class)
  @DisplayName("所有错误码的 message 都不应为空串")
  void messageShouldNotBeEmpty(FileErrorCodes error) {
    assertThat(error.message()).isNotBlank();
  }

  @Test
  @DisplayName("各枚举的 code 应唯一")
  void codeShouldBeUnique() {
    long distinctCount = Arrays.stream(FileErrorCodes.values())
        .map(FileErrorCodes::code)
        .distinct()
        .count();
    assertThat(distinctCount).isEqualTo(FileErrorCodes.values().length);
  }

  @Test
  @DisplayName("EXCEL_EXPORT_FAILED 应存在且码值为 SERVICE.FILE.0009")
  void excelExportFailedShouldExist() {
    assertThat(FileErrorCodes.EXCEL_EXPORT_FAILED.code()).isEqualTo("SERVICE.FILE.0009");
    assertThat(FileErrorCodes.EXCEL_EXPORT_FAILED.message()).isEqualTo("Excel 模板填充失败");
  }
}
