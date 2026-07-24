package com.example.shared.json.processor;

import com.example.shared.json.action.FieldAction;
import com.example.shared.json.matcher.FieldMatcher;
import com.example.shared.json.matcher.JsonPathFieldMatcher;
import com.example.shared.json.matcher.SimpleFieldMatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link JsonFieldProcessor} 单元测试。
 *
 * @author trae
 * @since 1.0
 */
@DisplayName("JsonFieldProcessor 测试")
class JsonFieldProcessorTest {

  private static final FieldAction UPPERCASE = (field, pathStack, value) -> value.toUpperCase();
  private static final FieldAction APPEND_PROCESSED = (field, pathStack, value) -> value + "_PROCESSED";
  private static final FieldAction RETURN_NULL = (field, pathStack, value) -> null;

  @Nested
  @DisplayName("SimpleFieldMatcher 场景")
  class SimpleMatcherTest {

    @Test
    @DisplayName("匹配字段名应被处理")
    void shouldProcessMatchedField() {
      FieldMatcher matcher = new SimpleFieldMatcher(Set.of("password"));
      JsonFieldProcessor processor = new JsonFieldProcessor(matcher);

      String body = "{\"username\":\"alice\",\"password\":\"secret\"}";
      String result = processor.process(body, UPPERCASE);

      assertThat(result).contains("\"password\":\"SECRET\"");
      assertThat(result).contains("\"username\":\"alice\"");
    }

    @Test
    @DisplayName("嵌套对象中匹配字段应被处理")
    void shouldProcessNestedMatchedField() {
      FieldMatcher matcher = new SimpleFieldMatcher(Set.of("password"));
      JsonFieldProcessor processor = new JsonFieldProcessor(matcher);

      String body = "{\"user\":{\"name\":\"alice\",\"password\":\"secret\"}}";
      String result = processor.process(body, UPPERCASE);

      assertThat(result).contains("\"password\":\"SECRET\"");
    }

    @Test
    @DisplayName("数组内对象中匹配字段应被处理")
    void shouldProcessArrayElementMatchedField() {
      FieldMatcher matcher = new SimpleFieldMatcher(Set.of("password"));
      JsonFieldProcessor processor = new JsonFieldProcessor(matcher);

      String body = "{\"users\":[{\"password\":\"a\"},{\"password\":\"b\"}]}";
      String result = processor.process(body, UPPERCASE);

      assertThat(result).contains("\"password\":\"A\"");
      assertThat(result).contains("\"password\":\"B\"");
    }

    @Test
    @DisplayName("不匹配字段应保留原值")
    void shouldKeepUnmatchedField() {
      FieldMatcher matcher = new SimpleFieldMatcher(Set.of("password"));
      JsonFieldProcessor processor = new JsonFieldProcessor(matcher);

      String body = "{\"username\":\"alice\"}";
      String result = processor.process(body, UPPERCASE);

      assertThat(result).isEqualTo(body);
    }

    @Test
    @DisplayName("action 返回 null 应保留原值")
    void shouldKeepOriginalWhenActionReturnNull() {
      FieldMatcher matcher = new SimpleFieldMatcher(Set.of("password"));
      JsonFieldProcessor processor = new JsonFieldProcessor(matcher);

      String body = "{\"password\":\"secret\"}";
      String result = processor.process(body, RETURN_NULL);

      assertThat(result).isEqualTo(body);
    }

    @Test
    @DisplayName("未修改时应返回原 body 引用")
    void shouldReturnOriginalReferenceWhenNotModified() {
      FieldMatcher matcher = new SimpleFieldMatcher(Set.of("nonexistent"));
      JsonFieldProcessor processor = new JsonFieldProcessor(matcher);

      String body = "{\"username\":\"alice\"}";
      String result = processor.process(body, UPPERCASE);

      assertThat(result).isSameAs(body);
    }
  }

  @Nested
  @DisplayName("JsonPathFieldMatcher 场景")
  class JsonPathMatcherTest {

    @Test
    @DisplayName("精确路径匹配：$.user.password 应匹配 user 下的 password")
    void shouldMatchExactPath() {
      FieldMatcher matcher = new JsonPathFieldMatcher(Set.of("$.user.password"));
      JsonFieldProcessor processor = new JsonFieldProcessor(matcher);

      String body = "{\"user\":{\"password\":\"secret\"},\"admin\":{\"password\":\"root\"}}";
      String result = processor.process(body, UPPERCASE);

      assertThat(result).contains("\"user\":{\"password\":\"SECRET\"");
      assertThat(result).contains("\"admin\":{\"password\":\"root\"");
    }

    @Test
    @DisplayName("深度扫描：$..password 应匹配任意层级的 password")
    void shouldMatchDeepScan() {
      FieldMatcher matcher = new JsonPathFieldMatcher(Set.of("$..password"));
      JsonFieldProcessor processor = new JsonFieldProcessor(matcher);

      String body = "{\"user\":{\"password\":\"a\"},\"admin\":{\"password\":\"b\"}}";
      String result = processor.process(body, UPPERCASE);

      assertThat(result).contains("\"password\":\"A\"");
      assertThat(result).contains("\"password\":\"B\"");
    }

    @Test
    @DisplayName("数组扁平化：$.items[*].password 应匹配数组元素中的 password")
    void shouldMatchArrayFlatPath() {
      FieldMatcher matcher = new JsonPathFieldMatcher(Set.of("$.items[*].password"));
      JsonFieldProcessor processor = new JsonFieldProcessor(matcher);

      String body = "{\"items\":[{\"password\":\"a\"},{\"password\":\"b\"}]}";
      String result = processor.process(body, UPPERCASE);

      assertThat(result).contains("\"password\":\"A\"");
      assertThat(result).contains("\"password\":\"B\"");
    }

    @Test
    @DisplayName("多层嵌套精确路径匹配")
    void shouldMatchDeepNestedExactPath() {
      FieldMatcher matcher = new JsonPathFieldMatcher(Set.of("$.user.info.password"));
      JsonFieldProcessor processor = new JsonFieldProcessor(matcher);

      String body = "{\"user\":{\"info\":{\"password\":\"deep\"},\"password\":\"shallow\"}}";
      String result = processor.process(body, UPPERCASE);

      assertThat(result).contains("\"info\":{\"password\":\"DEEP\"");
      assertThat(result).contains("\"password\":\"shallow\"");
    }
  }

  @Nested
  @DisplayName("边界与容错")
  class EdgeCaseTest {

    @Test
    @DisplayName("null body 应直接返回 null")
    void shouldReturnNullForNullBody() {
      FieldMatcher matcher = new SimpleFieldMatcher(Set.of("password"));
      JsonFieldProcessor processor = new JsonFieldProcessor(matcher);

      assertThat(processor.process(null, UPPERCASE)).isNull();
    }

    @Test
    @DisplayName("空 body 应直接返回空")
    void shouldReturnEmptyForEmptyBody() {
      FieldMatcher matcher = new SimpleFieldMatcher(Set.of("password"));
      JsonFieldProcessor processor = new JsonFieldProcessor(matcher);

      assertThat(processor.process("", UPPERCASE)).isEmpty();
    }

    @Test
    @DisplayName("非法 JSON 应返回原 body")
    void shouldReturnOriginalForInvalidJson() {
      FieldMatcher matcher = new SimpleFieldMatcher(Set.of("password"));
      JsonFieldProcessor processor = new JsonFieldProcessor(matcher);

      String body = "{invalid json}";
      String result = processor.process(body, UPPERCASE);

      assertThat(result).isEqualTo(body);
    }

    @Test
    @DisplayName("body 超过大小限制应不处理")
    void shouldSkipWhenBodyExceedsMaxSize() {
      FieldMatcher matcher = new SimpleFieldMatcher(Set.of("password"));
      JsonFieldProcessor processor = new JsonFieldProcessor(matcher, 10);

      String body = "{\"password\":\"secret\"}";
      String result = processor.process(body, UPPERCASE);

      assertThat(result).isEqualTo(body);
    }

    @Test
    @DisplayName("数字类型值应能被处理")
    void shouldProcessNumberValue() {
      FieldMatcher matcher = new SimpleFieldMatcher(Set.of("age"));
      JsonFieldProcessor processor = new JsonFieldProcessor(matcher);

      String body = "{\"age\":30}";
      String result = processor.process(body, APPEND_PROCESSED);

      assertThat(result).contains("\"age\":\"30_PROCESSED\"");
    }

    @Test
    @DisplayName("布尔类型值应能被处理")
    void shouldProcessBooleanValue() {
      FieldMatcher matcher = new SimpleFieldMatcher(Set.of("active"));
      JsonFieldProcessor processor = new JsonFieldProcessor(matcher);

      String body = "{\"active\":true}";
      String result = processor.process(body, APPEND_PROCESSED);

      assertThat(result).contains("\"active\":\"true_PROCESSED\"");
    }

    @Test
    @DisplayName("null 值字段应能被处理")
    void shouldProcessNullValue() {
      FieldMatcher matcher = new SimpleFieldMatcher(Set.of("data"));
      JsonFieldProcessor processor = new JsonFieldProcessor(matcher);

      String body = "{\"data\":null}";
      String result = processor.process(body, APPEND_PROCESSED);

      assertThat(result).contains("\"data\":\"null_PROCESSED\"");
    }

    @Test
    @DisplayName("数组内纯值（无字段名）不应被处理")
    void shouldNotProcessArrayPrimitiveValue() {
      FieldMatcher matcher = new SimpleFieldMatcher(Set.of("a", "b", "c"));
      JsonFieldProcessor processor = new JsonFieldProcessor(matcher);

      String body = "[\"a\",\"b\",\"c\"]";
      String result = processor.process(body, UPPERCASE);

      assertThat(result).isEqualTo(body);
    }

    @Test
    @DisplayName("空对象应正常处理")
    void shouldProcessEmptyObject() {
      FieldMatcher matcher = new SimpleFieldMatcher(Set.of("password"));
      JsonFieldProcessor processor = new JsonFieldProcessor(matcher);

      String body = "{}";
      String result = processor.process(body, UPPERCASE);

      assertThat(result).isEqualTo(body);
    }

    @Test
    @DisplayName("空数组应正常处理")
    void shouldProcessEmptyArray() {
      FieldMatcher matcher = new SimpleFieldMatcher(Set.of("password"));
      JsonFieldProcessor processor = new JsonFieldProcessor(matcher);

      String body = "{\"items\":[]}";
      String result = processor.process(body, UPPERCASE);

      assertThat(result).isEqualTo(body);
    }

    @Test
    @DisplayName("复杂嵌套 JSON 应正确处理")
    void shouldProcessComplexNestedJson() {
      FieldMatcher matcher = new SimpleFieldMatcher(Set.of("password", "phone"));
      JsonFieldProcessor processor = new JsonFieldProcessor(matcher);

      String body = "{\"users\":[{\"name\":\"alice\",\"password\":\"p1\",\"phone\":\"111\"},{\"name\":\"bob\",\"password\":\"p2\"}]}";
      String result = processor.process(body, UPPERCASE);

      assertThat(result).contains("\"password\":\"P1\"");
      assertThat(result).contains("\"password\":\"P2\"");
      assertThat(result).contains("\"name\":\"alice\"");
      assertThat(result).contains("\"name\":\"bob\"");
    }
  }

  @Nested
  @DisplayName("processOrThrow 场景")
  class ProcessOrThrowTest {

    @Test
    @DisplayName("正常 body 应返回处理结果")
    void shouldReturnProcessedResult() throws Exception {
      FieldMatcher matcher = new SimpleFieldMatcher(Set.of("password"));
      JsonFieldProcessor processor = new JsonFieldProcessor(matcher);

      String body = "{\"password\":\"secret\"}";
      String result = processor.processOrThrow(body, UPPERCASE);

      assertThat(result).contains("\"password\":\"SECRET\"");
    }

    @Test
    @DisplayName("null body 应直接返回 null")
    void shouldReturnNullForNullBody() throws Exception {
      FieldMatcher matcher = new SimpleFieldMatcher(Set.of("password"));
      JsonFieldProcessor processor = new JsonFieldProcessor(matcher);

      assertThat(processor.processOrThrow(null, UPPERCASE)).isNull();
    }

    @Test
    @DisplayName("空 body 应直接返回空")
    void shouldReturnEmptyForEmptyBody() throws Exception {
      FieldMatcher matcher = new SimpleFieldMatcher(Set.of("password"));
      JsonFieldProcessor processor = new JsonFieldProcessor(matcher);

      assertThat(processor.processOrThrow("", UPPERCASE)).isEmpty();
    }

    @Test
    @DisplayName("非法 JSON 应抛出异常")
    void shouldThrowForInvalidJson() {
      FieldMatcher matcher = new SimpleFieldMatcher(Set.of("password"));
      JsonFieldProcessor processor = new JsonFieldProcessor(matcher);

      String body = "{invalid json}";
      assertThatThrownBy(() -> processor.processOrThrow(body, UPPERCASE))
        .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("未修改时应返回原 body 引用")
    void shouldReturnOriginalReferenceWhenNotModified() throws Exception {
      FieldMatcher matcher = new SimpleFieldMatcher(Set.of("nonexistent"));
      JsonFieldProcessor processor = new JsonFieldProcessor(matcher);

      String body = "{\"username\":\"alice\"}";
      String result = processor.processOrThrow(body, UPPERCASE);

      assertThat(result).isSameAs(body);
    }
  }
}
