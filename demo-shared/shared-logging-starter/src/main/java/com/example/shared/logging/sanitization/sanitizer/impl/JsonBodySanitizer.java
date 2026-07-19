package com.example.shared.logging.sanitization.sanitizer.impl;

import com.example.shared.logging.core.model.HttpExchangeLog;
import com.example.shared.logging.sanitization.context.SanitizationContext;
import com.example.shared.logging.sanitization.context.SanitizationRule;
import com.example.shared.logging.sanitization.sanitizer.LogSanitizer;
import com.example.shared.logging.sanitization.sanitizer.support.ValueSanitizer;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON 脱敏处理器 - 旗舰版 (Flagship Version)
 * <p>
 * 综合了 Zero-Allocation 思想、现代 API 和严格的路径匹配逻辑。
 */
@Slf4j
public class JsonBodySanitizer implements LogSanitizer {

  private static final int MAX_CONTENT_LENGTH = 1024 * 1024; // 1MB
  private static final String MASK_ERROR_PLACEHOLDER = "{\"error\":\"Log Sanitization Failed\"}";
  // 使用 Builder 模式替代废弃的 disable 方法
  private static final JsonFactory JSON_FACTORY = JsonFactory.builder()
    .disable(JsonFactory.Feature.INTERN_FIELD_NAMES) // 防止 String Pool 爆炸
    .disable(JsonFactory.Feature.CANONICALIZE_FIELD_NAMES)
    .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES) // 增强容错
    .build();
  // 索引：Key=叶子字段名(leafName), Value=以该字段结尾的规则列表
  private final Map<String, List<RulePath>> ruleIndex;
  private final ValueSanitizer valueSanitizer;
  private final boolean enabled;

  public JsonBodySanitizer(SanitizationContext config, ValueSanitizer valueSanitizer) {
    this.valueSanitizer = valueSanitizer;
    this.enabled = config.getGlobalConfig().enable();
    this.ruleIndex = buildRuleIndex(config.getJsonPathRules());

    log.info("Initialized JsonBodySanitizer with {} indexed leaves.", ruleIndex.size());
    log.debug("Json Path rules indexed leaves: {}", ruleIndex.keySet());
  }

  /**
   * 规则预处理
   * 将 $.user.info.password 解析为：
   * leafName = "password"
   * parentSegments = ["info", "user"] (倒序)
   */
  private Map<String, List<RulePath>> buildRuleIndex(Map<String, SanitizationRule> originalRules) {
    if (originalRules == null || originalRules.isEmpty()) {
      return Map.of();
    }

    Map<String, List<RulePath>> ruleIndex = new HashMap<>();

    originalRules.forEach((path, rule) -> {
      if (path == null || path.isBlank()) {
        return;
      }

      // 1. 清理 JsonPath (兼容 $. 和 $.. 以及 [*])
      // 这里我们策略性地移除 [*]，实施"路径扁平化"匹配，这在日志脱敏中通常更实用
      String cleanPath = path.replace("$.", "").replace("$..", "").replace("[*]", "");
      String[] segments = cleanPath.split("\\.");

      if (segments.length == 0) {
        return;
      }

      String leafName = segments[segments.length - 1];
      boolean isDeepScan = path.startsWith("$..");

      // 2. 提取父级路径 (倒序存储)
      // segments: [user, info, password] -> parentSegments: [info, user]
      String[] parentSegments = new String[segments.length - 1];
      for (int i = 0; i < segments.length - 1; i++) {
        parentSegments[i] = segments[segments.length - 2 - i];
      }

      RulePath rulePath = new RulePath(rule, parentSegments, isDeepScan);
      ruleIndex.computeIfAbsent(leafName, k -> new ArrayList<>()).add(rulePath);
    });

    return Map.copyOf(ruleIndex);
  }

  @Override
  public void sanitizeRequest(HttpExchangeLog log) {
    if (log.getRequestContent() != null) {
      log.setRequestContent(safeHandler(log.getContentType(), log.getRequestContent()));
    }
  }

  @Override
  public void sanitizeResponse(HttpExchangeLog log) {
    if (log.getResponseContent() != null) {
      log.setResponseContent(safeHandler(log.getContentType(), log.getResponseContent()));
    }
  }

  private String safeHandler(String contentType, String body) {
    if (!enabled || body == null || body.isEmpty() || !isJson(contentType)) {
      return body;
    }
    if (body.length() > MAX_CONTENT_LENGTH) {
      return "%s... [Truncated]".formatted(body.substring(0, 1024));
    }
    try {
      return doSanitize(body);
    } catch (Exception e) {
      log.error("[Sanitizer] Error processing JSON", e);
      return MASK_ERROR_PLACEHOLDER;
    }
  }

  private String doSanitize(String body) throws Exception {
    boolean modified = false;
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream(body.length());

    try (JsonParser parser = JSON_FACTORY.createParser(body);
         JsonGenerator generator = JSON_FACTORY.createGenerator(outputStream)) {

      // 路径栈：只存 Object 字段名
      // 优化：ArrayList 扩容机制适合大多数 JSON 深度
      List<String> pathStack = new ArrayList<>(16);
      String currentFieldName = null;

      while (!parser.isClosed()) {
        JsonToken token = parser.nextToken();
        if (token == null) {
          break;
        }

        switch (token) {
          case START_OBJECT:
            generator.writeStartObject();
            // 核心修复：如果是字段后的对象，压栈字段名
            // 如果是数组内的匿名对象(currentFieldName==null)，我们保持栈不变
            // 这样 items[*].user.password 在栈中就是 [items, user]，符合扁平化匹配逻辑
            if (currentFieldName != null) {
              pathStack.add(currentFieldName);
              currentFieldName = null; // 消费掉
            }
            break;
          case END_OBJECT:
            generator.writeEndObject();
            if (!pathStack.isEmpty()) {
              pathStack.removeLast();
            }
            break;
          case START_ARRAY:
            generator.writeStartArray();
            // 数组本身的字段名需要压栈
            if (currentFieldName != null) {
              pathStack.add(currentFieldName);
              currentFieldName = null;
            }
            break;
          case END_ARRAY:
            generator.writeEndArray();
            if (!pathStack.isEmpty()) {
              pathStack.removeLast();
            }
            break;
          case FIELD_NAME:
            currentFieldName = parser.currentName();
            generator.writeFieldName(currentFieldName);
            break;

          // 值处理
          case VALUE_STRING:
          case VALUE_NUMBER_INT:
          case VALUE_NUMBER_FLOAT:
          case VALUE_TRUE:
          case VALUE_FALSE:
          case VALUE_NULL:
            // 只有在有字段名的情况下才进行匹配 (数组内的纯值忽略)
            if (currentFieldName != null) {
              SanitizationRule rule = matchRule(pathStack, currentFieldName);
              if (rule != null) {
                String val = parser.getValueAsString();
                if (val == null) {
                  val = "null";
                }
                generator.writeString(valueSanitizer.sanitize(val, rule));
                modified = true;
              } else {
                generator.copyCurrentEvent(parser);
              }
              currentFieldName = null;
            } else {
              generator.copyCurrentEvent(parser);
            }
            break;
          default:
            generator.copyCurrentEvent(parser);
            break;
        }
      }
    }
    return modified ? outputStream.toString(StandardCharsets.UTF_8) : body;
  }

  /**
   * 高性能匹配逻辑
   */
  private SanitizationRule matchRule(List<String> pathStack, String leafName) {
    // 1. 索引命中
    List<RulePath> candidates = ruleIndex.get(leafName);
    if (candidates == null) {
      return null;
    }

    // 2. 遍历候选 (通常只有1个)
    for (RulePath candidate : candidates) {
      if (isPathMatch(pathStack, candidate)) {
        return candidate.rule();
      }
    }
    return null;
  }

  /**
   * 优化后的反向回溯算法
   */
  private boolean isPathMatch(List<String> pathStack, RulePath candidate) {
    String[] parents = candidate.parentSegments();
    int stackSize = pathStack.size();
    int parentCount = parents.length;

    // 1. 深度校验
    if (!candidate.isDeepScan()) {
      // 精确匹配：栈深度必须完全等于父级段数 (leafName 不在栈中，所以是 ==)
      if (stackSize != parentCount) {
        return false;
      }
    } else {
      // 深度匹配：栈深度必须足够容纳规则
      if (stackSize < parentCount) {
        return false;
      }
    }

    // 2. 反向遍历比对 (Performance Hotspot)
    // 规则 parents: [info, user] (倒序)
    // 栈 pathStack: [user, info] (正序)
    // 比较逻辑: parents[0] vs stack[top], parents[1] vs stack[top-1]
    for (int i = 0; i < parentCount; i++) {
      String ruleSegment = parents[i];
      String stackSegment = pathStack.get(stackSize - 1 - i); // 从栈顶向下取

      if (!ruleSegment.equals(stackSegment)) {
        return false;
      }
    }

    return true;
  }

  // 优化：避免 toLowerCase 和 new String
  private boolean isJson(String contentType) {
    if (contentType == null) {
      return false;
    }
    // 简单高效的检查，兼容 application/json, application/vnd+json 等
    int len = contentType.length();
    for (int i = 0; i < len - 3; i++) {
      if ((contentType.charAt(i) == 'j' || contentType.charAt(i) == 'J') &&
        (contentType.charAt(i + 1) == 's' || contentType.charAt(i + 1) == 'S') &&
        (contentType.charAt(i + 2) == 'o' || contentType.charAt(i + 2) == 'O') &&
        (contentType.charAt(i + 3) == 'n' || contentType.charAt(i + 3) == 'N')) {
        return true;
      }
    }
    return false;
  }

  /**
   * Java 17 Record: 自动实现 equals/hashCode/toString，更紧凑
   * parentSegments 不包含 leafName，减少冗余存储和比对
   */
  private record RulePath(SanitizationRule rule, String[] parentSegments, boolean isDeepScan) {
    // Record 紧凑且不可变，非常适合做缓存 Key 或 Value
  }
}
