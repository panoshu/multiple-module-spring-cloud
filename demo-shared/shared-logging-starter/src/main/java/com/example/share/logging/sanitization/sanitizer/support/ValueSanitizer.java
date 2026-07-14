package com.example.share.logging.sanitization.sanitizer.support;

import com.example.share.logging.sanitization.context.SanitizationRule;
import com.example.share.logging.sanitization.strategy.SanitizationStrategyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ValueSanitizer {

  private final SanitizationStrategyFactory sanitizationStrategyFactory;

  public String sanitize(String originalValue, SanitizationRule config) {
    // 1. 快速检查
    if (originalValue == null || originalValue.isBlank()) {
      return originalValue;
    }
    if (config == null) {
      log.trace("Skipping obfuscation: null config");
      return originalValue;
    }

    // 2. 尝试脱敏
    try {
      if (log.isTraceEnabled()) {
        log.trace("Applying strategy [{}] to value of length {}",
          config.strategy(), originalValue.length());
      }

      String result = sanitizationStrategyFactory.sanitize(
        config.strategy(),
        originalValue,
        config.validatedParams()
      );

      // 仅在 Trace 级别记录结果长度变化，避免直接打印敏感内容
      if (log.isTraceEnabled()) {
        log.trace("Obfuscation success. Length change: {} -> {}",
          originalValue.length(), result.length());
      }
      return result;

    } catch (Exception e) {
      // 3. 异常降级与记录
      // 生产环境关键日志：记录哪个策略、哪个配置出错了
      log.warn("Obfuscation failed! Strategy: [{}], Replacement: [{}]. Error: {}",
        config.strategy(), config.replacement(), e.getMessage());

      // 降级：返回安全掩码或原配置的 replacement
      return "******(Error)";
    }
  }
}
