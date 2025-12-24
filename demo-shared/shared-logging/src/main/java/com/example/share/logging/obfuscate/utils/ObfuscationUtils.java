package com.example.share.logging.obfuscate.utils;


import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 脱敏工具类，提取重复逻辑
 */

@Slf4j
public class ObfuscationUtils {

  /**
   * 合并策略参数：字段参数 + 策略配置参数 + 全局replacement
   */
  public static Map<String, Object> mergeParams(
    ObfuscateProperties1.FieldConfig1 config,
    String globalReplacement,
    ObfuscateProperties1 properties) {

    Map<String, Object> mergedParams = new HashMap<>(config.params());

    mergedParams.putIfAbsent("replacement", globalReplacement);

    // 从策略配置中获取默认参数
    if (properties != null && properties.strategies() != null) {
      ObfuscateProperties1.StrategyConfig1 strategyConfig1 = properties.strategies().get(config.strategy().getValue());
      if (strategyConfig1 != null && strategyConfig1.params() != null) {
        strategyConfig1.params().forEach(mergedParams::putIfAbsent);
      }
    }

    return Map.copyOf(mergedParams);
  }

  public static int convertToInt(Object value, int defaultValue) {
    return switch (value) {
      case Number n -> Math.max(n.intValue(), 0);
      case String s -> {
        try {
          yield Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
          log.warn("Failed to parse integer parameter: '{}'", s);
          yield defaultValue;
        }
      }
      case null, default -> defaultValue;
    };
  }

  public static int safeParseInt(@NonNull Object value) {
    return switch (value) {
      case Number n -> n.intValue();
      case String s -> Integer.parseInt(s.trim());
      default -> throw new IllegalStateException("Unexpected value: " + value);
    };
  }
}
