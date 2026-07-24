package com.example.shared.logging.sanitization.sanitizer.impl;

import com.example.shared.json.action.FieldAction;
import com.example.shared.json.matcher.FieldMatcher;
import com.example.shared.json.path.PathMatcher;
import com.example.shared.json.processor.JsonFieldProcessor;
import com.example.shared.logging.core.model.HttpExchangeLog;
import com.example.shared.logging.sanitization.context.SanitizationContext;
import com.example.shared.logging.sanitization.context.SanitizationRule;
import com.example.shared.logging.sanitization.sanitizer.LogSanitizer;
import com.example.shared.logging.sanitization.sanitizer.support.ValueSanitizer;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * JSON 脱敏处理器 — 委托 {@link JsonFieldProcessor} 完成流式解析与字段遍历。
 *
 * <p>职责划分（SRP）：
 * <ul>
 *   <li>JSON 流式解析、字段遍历 → {@link JsonFieldProcessor}（shared-json）</li>
 *   <li>字段路径匹配 → {@link PathMatcher}（shared-json），本类构建索引并提供 {@link FieldMatcher}</li>
 *   <li>值脱敏 → {@link ValueSanitizer}（shared-logging-core），本类提供 {@link FieldAction} 桥接</li>
 *   <li>contentType 判断、body 大小截断、错误占位 → 本类（日志场景特有）</li>
 * </ul>
 *
 * <p>对外接口 {@link LogSanitizer} 不变，仅内部实现替换为委托。
 *
 * @author trae
 * @since 1.0
 */
@Slf4j
public class JsonBodySanitizer implements LogSanitizer {

  private static final int MAX_CONTENT_LENGTH = 1024 * 1024; // 1MB
  private static final int TRUNCATE_KEEP_LENGTH = 1024;
  private static final String MASK_ERROR_PLACEHOLDER = "{\"error\":\"Log Sanitization Failed\"}";

  private final Map<String, List<PathMatcher.RulePathEntry<SanitizationRule>>> ruleIndex;
  private final JsonFieldProcessor processor;
  private final ValueSanitizer valueSanitizer;
  private final boolean enabled;

  public JsonBodySanitizer(SanitizationContext config, ValueSanitizer valueSanitizer) {
    this.valueSanitizer = valueSanitizer;
    this.enabled = config.getGlobalConfig().enable();
    this.ruleIndex = PathMatcher.buildIndex(config.getJsonPathRules());
    this.processor = new JsonFieldProcessor(buildMatcher(ruleIndex));

    log.info("Initialized JsonBodySanitizer with {} indexed leaves.", ruleIndex.size());
    log.debug("Json Path rules indexed leaves: {}", ruleIndex.keySet());
  }

  /**
   * 构建 {@link FieldMatcher}：基于 {@link PathMatcher} 判断字段是否命中脱敏规则。
   *
   * <p>注意：{@link FieldAction} 会再次调用 {@link PathMatcher#match} 获取规则对象，
   * 属于基于索引的 O(1) + O(k) 查找（k 通常为 1），相比 JSON 流式解析开销可忽略。
   */
  private static FieldMatcher buildMatcher(
      Map<String, List<PathMatcher.RulePathEntry<SanitizationRule>>> index) {
    return (fieldName, pathStack) -> PathMatcher.match(index, fieldName, pathStack) != null;
  }

  /**
   * 构建 {@link FieldAction}：查找命中的 {@link SanitizationRule}，委托 {@link ValueSanitizer} 脱敏。
   */
  private FieldAction buildSanitizeAction() {
    return (fieldName, pathStack, value) -> {
      SanitizationRule rule = PathMatcher.match(ruleIndex, fieldName, pathStack);
      if (rule == null) {
        return null;
      }
      return valueSanitizer.sanitize(value, rule);
    };
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
      return "%s... [Truncated]".formatted(body.substring(0, TRUNCATE_KEEP_LENGTH));
    }
    try {
      return processor.processOrThrow(body, buildSanitizeAction());
    } catch (Exception e) {
      log.error("[Sanitizer] Error processing JSON", e);
      return MASK_ERROR_PLACEHOLDER;
    }
  }

  private boolean isJson(String contentType) {
    if (contentType == null) {
      return false;
    }
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
}
