package com.example.shared.domain.errorcode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SharedDomainErrorCode} 单元测试。
 * <p>
 * 验证：
 * <ul>
 *   <li>message() 返回构造时传入的消息（而非空字符串）</li>
 *   <li>code() 返回符合 {@code 08-错误码规范.md} 的 5 位纯数字，且落在 shared-domain 码段 12001-12099</li>
 *   <li>消息不含 {} 占位符和方括号前缀</li>
 * </ul>
 *
 * @author panoshu
 * @since 2026/7/24
 */
@DisplayName("SharedDomainErrorCode 测试")
class SharedDomainErrorCodeTest {

    @Test
    @DisplayName("message() 应返回构造的消息文本")
    void message_should_return_defined_text() {
        assertThat(SharedDomainErrorCode.ENTITY_NOT_FOUND.message()).isNotBlank();
        assertThat(SharedDomainErrorCode.INVALID_DATA.message()).isNotBlank();
        assertThat(SharedDomainErrorCode.INVALID_OPERATION.message()).isNotBlank();
    }

    @Test
    @DisplayName("code() 应返回 5 位纯数字")
    void code_should_be_5_digit_number() {
        for (SharedDomainErrorCode error : SharedDomainErrorCode.values()) {
            assertThat(error.code())
                .as("%s 的 code 必须为 5 位纯数字", error.name())
                .matches("\\d{5}");
        }
    }

    @Test
    @DisplayName("code 应落在 shared-domain 码段 12001-12099")
    void code_should_be_in_shared_domain_segment() {
        for (SharedDomainErrorCode error : SharedDomainErrorCode.values()) {
            int code = Integer.parseInt(error.code());
            assertThat(code)
                .as("%s 的 code 应在 12001-12099 区间", error.name())
                .isBetween(12001, 12099);
        }
    }

    @Test
    @DisplayName("消息禁止包含 {} 占位符")
    void message_should_not_contain_placeholder() {
        for (SharedDomainErrorCode error : SharedDomainErrorCode.values()) {
            assertThat(error.message())
                .as("%s 的消息禁止包含 {} 占位符", error.name())
                .doesNotContain("{}");
        }
    }

    @Test
    @DisplayName("消息禁止使用方括号前缀")
    void message_should_not_contain_bracket_prefix() {
        for (SharedDomainErrorCode error : SharedDomainErrorCode.values()) {
            assertThat(error.message())
                .as("%s 的消息禁止以方括号开头", error.name())
                .doesNotStartWith("[");
        }
    }

    @Test
    @DisplayName("各枚举的 code 应唯一")
    void code_should_be_unique() {
        long distinctCount = java.util.Arrays.stream(SharedDomainErrorCode.values())
            .map(SharedDomainErrorCode::code)
            .distinct()
            .count();
        assertThat(distinctCount).isEqualTo(SharedDomainErrorCode.values().length);
    }
}
