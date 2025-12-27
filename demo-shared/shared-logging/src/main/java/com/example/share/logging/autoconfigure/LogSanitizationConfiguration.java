package com.example.share.logging.autoconfigure;

import com.example.share.logging.sanitization.context.support.ConfigurationValidator;
import com.example.share.logging.sanitization.properties.SanitizationProperties;
import com.example.share.logging.sanitization.context.support.RuleBuilder;
import com.example.share.logging.sanitization.context.SanitizationContext;
import com.example.share.logging.sanitization.strategy.validator.StrategyValidator;
import com.example.share.logging.sanitization.strategy.validator.StrategyValidatorFactory;
import com.example.share.logging.sanitization.engine.SanitizationEngine;
import com.example.share.logging.sanitization.sanitizer.LogSanitizer;
import com.example.share.logging.sanitization.sanitizer.impl.HeaderSanitizer;
import com.example.share.logging.sanitization.sanitizer.impl.JsonBodySanitizer;
import com.example.share.logging.sanitization.sanitizer.impl.QueryParamSanitizer;
import com.example.share.logging.sanitization.strategy.SanitizationStrategy;
import com.example.share.logging.sanitization.strategy.SanitizationStrategyFactory;
import com.example.share.logging.sanitization.strategy.impl.*;
import com.example.share.logging.sanitization.sanitizer.support.ValueSanitizer;
import com.example.share.logging.sanitization.strategy.validator.impl.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.util.List;

@Configuration
@EnableConfigurationProperties(SanitizationProperties.class)
@ConditionalOnProperty(prefix = "logbook.obfuscate.global", name = "enable", havingValue = "true", matchIfMissing = true)
public class LogSanitizationConfiguration {

  // --- 1. 验证器 (Validators) ---
  @Bean @ConditionalOnMissingBean public FullValidator fullValidator() { return new FullValidator(); }
  @Bean @ConditionalOnMissingBean public PartialHideValidator partialHideValidator() { return new PartialHideValidator(); }
  @Bean @ConditionalOnMissingBean public KeepFirstLastValidator keepFirstLastValidator() { return new KeepFirstLastValidator(); }
  @Bean @ConditionalOnMissingBean public HashSHA256Validator hashSHA256Validator() { return new HashSHA256Validator(); }
  @Bean @ConditionalOnMissingBean public PatternRegexValidator patternRegexValidator() { return new PatternRegexValidator(); }

  @Bean
  @ConditionalOnMissingBean
  public StrategyValidatorFactory strategyValidatorFactory(List<StrategyValidator> validators) {
    return new StrategyValidatorFactory(validators);
  }

  @Bean
  @ConditionalOnMissingBean
  public ConfigurationValidator configurationValidator(StrategyValidatorFactory factory) {
    return new ConfigurationValidator(factory);
  }

  // --- 2. 策略实现 (Strategies) ---
  // 注意：实现类名最好也改为 FullSanitizationStrategy 等
  @Bean @ConditionalOnMissingBean public FullSanitizationStrategy fullStrategy() { return new FullSanitizationStrategy(); }
  @Bean @ConditionalOnMissingBean public PartialHideStrategy partialHideStrategy() { return new PartialHideStrategy(); }
  @Bean @ConditionalOnMissingBean public KeepFirstLastStrategy keepFirstLastStrategy() { return new KeepFirstLastStrategy(); }
  @Bean @ConditionalOnMissingBean public HashSHA256Strategy hashSHA256Strategy() { return new HashSHA256Strategy(); }
  @Bean @ConditionalOnMissingBean public PatternRegexStrategy patternRegexStrategy() { return new PatternRegexStrategy(); }

  @Bean
  @ConditionalOnMissingBean
  public SanitizationStrategyFactory strategyFactory(List<SanitizationStrategy> strategies) {
    return new SanitizationStrategyFactory(strategies);
  }

  // --- 3. 配置解析与工具 (Context & Tools) ---
  @Bean
  @ConditionalOnMissingBean
  public RuleBuilder ruleBuilder() { return new RuleBuilder(); }

  @Bean
  @ConditionalOnMissingBean
  public SanitizationContext sanitizationContext(SanitizationProperties properties,
                                                 ConfigurationValidator validator,
                                                 RuleBuilder ruleBuilder) {
    return new SanitizationContext(properties, validator, ruleBuilder);
  }

  @Bean
  @ConditionalOnMissingBean
  public ValueSanitizer valueSanitizer(SanitizationStrategyFactory factory) {
    return new ValueSanitizer(factory);
  }

  // --- 4. 净化器 (Sanitizers) ---
  @Bean
  @ConditionalOnMissingBean
  @Order(1)
  public JsonBodySanitizer jsonBodySanitizer(SanitizationContext ctx, ValueSanitizer valueSanitizer) {
    return new JsonBodySanitizer(ctx, valueSanitizer);
  }

  @Bean
  @ConditionalOnMissingBean
  @Order(2)
  public HeaderSanitizer headerSanitizer(SanitizationContext ctx, ValueSanitizer valueSanitizer, ObjectMapper objectMapper) {
    return new HeaderSanitizer(ctx, valueSanitizer, objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  @Order(3)
  public QueryParamSanitizer queryParamSanitizer(SanitizationContext ctx, ValueSanitizer valueSanitizer) {
    return new QueryParamSanitizer(ctx, valueSanitizer);
  }

  // --- 5. 引擎 (Engine) ---
  @Bean
  @ConditionalOnMissingBean
  public SanitizationEngine sanitizationEngine(List<LogSanitizer> sanitizers) {
    return new SanitizationEngine(sanitizers);
  }
}
