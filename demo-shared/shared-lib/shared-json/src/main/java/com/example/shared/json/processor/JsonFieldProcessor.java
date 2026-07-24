package com.example.shared.json.processor;

import com.example.shared.json.action.FieldAction;
import com.example.shared.json.matcher.FieldMatcher;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * JSON 字段处理器：流式遍历 JSON，对匹配的字段执行处理动作。
 *
 * <p>核心设计原则：
 * <ul>
 *   <li><b>SRP</b>：仅负责 JSON 流式遍历，字段匹配委托给 {@link FieldMatcher}，值处理委托给 {@link FieldAction}</li>
 *   <li><b>OCP</b>：新增匹配策略或处理动作无需修改此类</li>
 *   <li><b>性能</b>：流式 token 解析（JsonParser/JsonGenerator），零中间树构建，避免大 body 内存爆炸</li>
 *   <li><b>安全</b>：body 字符数上限可配置，超限不处理直接透传，防止 OOM</li>
 * </ul>
 *
 * <p>处理流程：
 * <ol>
 *   <li>校验 body 非空且未超过 {@code maxBodySize}</li>
 *   <li>流式解析 JSON，维护路径栈（仅 Object 字段名，数组内匿名对象不入栈）</li>
 *   <li>遇到叶子值时：{@code matcher.match(fieldName, pathStack)} 判断是否处理</li>
 *   <li>匹配则调用 {@code action.process(fieldName, pathStack, value)}，返回非 null 替换原值，返回 null 保留原值</li>
 *   <li>未修改时返回原 body 引用，避免不必要的字符串创建</li>
 * </ol>
 *
 * <p><b>类型变化行为</b>：当 {@link FieldAction} 返回非 null 值时，原字段的 JSON 类型会被统一替换为字符串。
 * 例如 {@code {"age":18}} 经加密处理后变为 {@code {"age":"密文"}}。
 * 这符合加密/脱敏语义（密文和脱敏值本身就是字符串），但调用方需理解此行为。
 * 如需保留原类型，应通过 {@code action.process} 返回 null 跳过处理。
 *
 * <p>容错策略：
 * <ul>
 *   <li>{@link #process}：JSON 解析失败记录 WARN 并返回原 body，不抛异常</li>
 *   <li>{@link #processOrThrow}：解析失败抛异常，容错策略由调用方决定</li>
 *   <li>action 处理失败：由 action 实现自行处理（如返回 null 保留原值）</li>
 * </ul>
 *
 * <p>线程安全：{@link JsonFieldProcessor} 无可变状态，可在多线程并发使用。
 *
 * @author trae
 * @since 1.0
 */
@Slf4j
public final class JsonFieldProcessor {

  /**
   * 默认 body 字符数上限：1M 字符。
   * <p>注意：此为字符数限制，不是字节数。对于 UTF-8 中文，1M 字符约等于 3MB 字节。
   * 调用方如需按字节限制，应在调用前自行校验或在网关层通过 Spring 的
   * {@code spring.codec.max-in-memory-size} 限制 DataBuffer 大小。
   */
  public static final long DEFAULT_MAX_BODY_SIZE = 1024 * 1024;

  /**
   * 默认 JsonFactory：使用 Jackson 默认配置。
   * <p>默认配置启用字段名缓存（CANONICALIZE_FIELD_NAMES）以提升企业 API 重复字段名的解析性能，
   * 不允许非标准 JSON（如无引号字段名），确保输入严格性。
   */
  private static final JsonFactory DEFAULT_JSON_FACTORY = JsonFactory.builder().build();

  private final FieldMatcher matcher;
  private final JsonFactory jsonFactory;
  private final long maxBodySize;

  /**
   * 构造处理器，使用默认 JsonFactory 和默认 body 大小上限（1MB）。
   *
   * @param matcher 字段匹配器
   */
  public JsonFieldProcessor(FieldMatcher matcher) {
    this(matcher, DEFAULT_JSON_FACTORY, DEFAULT_MAX_BODY_SIZE);
  }

  /**
   * 构造处理器，使用默认 JsonFactory 和自定义 body 字符数上限。
   *
   * @param matcher     字段匹配器
   * @param maxBodySize body 字符数上限（注意：非字节数），超过则不处理
   */
  public JsonFieldProcessor(FieldMatcher matcher, long maxBodySize) {
    this(matcher, DEFAULT_JSON_FACTORY, maxBodySize);
  }

  /**
   * 构造处理器，全参数版。
   *
   * @param matcher     字段匹配器
   * @param jsonFactory JsonFactory 实例（允许调用方自定义配置）
   * @param maxBodySize body 字符数上限（注意：非字节数），超过则不处理
   */
  public JsonFieldProcessor(FieldMatcher matcher, JsonFactory jsonFactory, long maxBodySize) {
    this.matcher = matcher;
    this.jsonFactory = jsonFactory;
    this.maxBodySize = maxBodySize;
  }

  /**
   * 处理 JSON body，对匹配字段执行 action。
   *
   * <p>容错策略：body 超限或解析失败时返回原 body，不抛异常。适合网关等不能因处理失败而中断请求的场景。
   *
   * @param body   JSON 字符串
   * @param action 字段处理动作
   * @return 处理后的 body；未修改时返回原 body 引用；body 为空或超限返回原 body
   */
  public String process(String body, FieldAction action) {
    if (body == null || body.isEmpty()) {
      return body;
    }
    if (body.length() > maxBodySize) {
      log.warn("[JsonFieldProcessor] Body char count {} exceeds max {}, skip processing", body.length(), maxBodySize);
      return body;
    }
    try {
      return doProcess(body, action);
    } catch (Exception e) {
      log.warn("[JsonFieldProcessor] JSON process failed, return original body", e);
      return body;
    }
  }

  /**
   * 处理 JSON body，对匹配字段执行 action，解析失败时抛出异常。
   *
   * <p>容错策略由调用方决定：调用方可以捕获异常后返回占位符、截断 body 或记录错误。
   * 不检查 body 字符数上限，由调用方在调用前自行校验。
   *
   * @param body   JSON 字符串（null 或空则原样返回）
   * @param action 字段处理动作
   * @return 处理后的 body；未修改时返回原 body 引用
   * @throws Exception JSON 解析或写入失败时抛出
   */
  public String processOrThrow(String body, FieldAction action) throws Exception {
    if (body == null || body.isEmpty()) {
      return body;
    }
    return doProcess(body, action);
  }

  private String doProcess(String body, FieldAction action) throws Exception {
    boolean modified = false;
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream(body.length());

    try (JsonParser parser = jsonFactory.createParser(body);
         JsonGenerator generator = jsonFactory.createGenerator(outputStream)) {

      List<String> pathStack = new ArrayList<>(16);
      Deque<Boolean> pushedStack = new ArrayDeque<>(16);
      String currentFieldName = null;

      while (!parser.isClosed()) {
        JsonToken token = parser.nextToken();
        if (token == null) {
          break;
        }

        switch (token) {
          case START_OBJECT -> {
            generator.writeStartObject();
            boolean pushed = currentFieldName != null;
            if (pushed) {
              pathStack.add(currentFieldName);
              currentFieldName = null;
            }
            pushedStack.push(pushed);
          }
          case END_OBJECT -> {
            generator.writeEndObject();
            if (!pushedStack.isEmpty() && pushedStack.pop()) {
              pathStack.removeLast();
            }
          }
          case START_ARRAY -> {
            generator.writeStartArray();
            boolean pushed = currentFieldName != null;
            if (pushed) {
              pathStack.add(currentFieldName);
              currentFieldName = null;
            }
            pushedStack.push(pushed);
          }
          case END_ARRAY -> {
            generator.writeEndArray();
            if (!pushedStack.isEmpty() && pushedStack.pop()) {
              pathStack.removeLast();
            }
          }
          case FIELD_NAME -> {
            currentFieldName = parser.currentName();
            generator.writeFieldName(currentFieldName);
          }
          case VALUE_STRING, VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT, VALUE_TRUE, VALUE_FALSE, VALUE_NULL -> {
            if (currentFieldName != null) {
              String processed = tryProcessField(currentFieldName, pathStack, parser, action);
              if (processed != null) {
                generator.writeString(processed);
                modified = true;
              } else {
                generator.copyCurrentEvent(parser);
              }
              currentFieldName = null;
            } else {
              generator.copyCurrentEvent(parser);
            }
          }
          default -> generator.copyCurrentEvent(parser);
        }
      }
    }
    return modified ? outputStream.toString(StandardCharsets.UTF_8) : body;
  }

  private String tryProcessField(String fieldName, List<String> pathStack, JsonParser parser, FieldAction action) throws java.io.IOException {
    if (!matcher.match(fieldName, pathStack)) {
      return null;
    }
    String value = parser.getValueAsString();
    if (value == null) {
      value = "null";
    }
    return action.process(fieldName, pathStack, value);
  }
}
