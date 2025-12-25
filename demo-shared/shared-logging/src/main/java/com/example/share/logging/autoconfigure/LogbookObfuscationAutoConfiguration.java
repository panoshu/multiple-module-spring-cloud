package com.example.share.logging.autoconfigure;

import com.example.share.logging.obfuscate.config.ConfigurationValidator;
import com.example.share.logging.obfuscate.config.ObfuscateConfig;
import com.example.share.logging.obfuscate.config.ObfuscateProperties;
import com.example.share.logging.obfuscate.config.RuleBuilder;
import com.example.share.logging.obfuscate.config.validator.StrategyValidatorFactory;
import com.example.share.logging.obfuscate.config.validator.impl.*;
import com.example.share.logging.obfuscate.filter.HeaderObfuscationFilter;
import com.example.share.logging.obfuscate.filter.JsonBodyObfuscationFilter;
import com.example.share.logging.obfuscate.filter.QueryParamObfuscationFilter;
import com.example.share.logging.obfuscate.strategy.ObfuscationStrategyFactory;
import com.example.share.logging.obfuscate.strategy.impl.*;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zalando.logbook.Logbook;

import java.util.List;

@Configuration
@ConditionalOnClass(Logbook.class)
@EnableConfigurationProperties(ObfuscateProperties.class)
@ConditionalOnProperty(prefix = "logbook.obfuscate.global", name = "enable", havingValue = "true", matchIfMissing = true)
public class LogbookObfuscationAutoConfiguration {

  // 1. 验证器相关
  @Bean
  public FullValidator fullValidator() { return new FullValidator(); }
  @Bean
  public PartialHideValidator partialHideValidator() { return new PartialHideValidator(); }
  @Bean
  public KeepFirstLastValidator keepFirstLastValidator() { return new KeepFirstLastValidator(); }
  @Bean
  public HashSHA256Validator hashSHA256Validator() { return new HashSHA256Validator(); }
  @Bean
  public PatternRegexValidator patternRegexValidator() { return new PatternRegexValidator(); }

  @Bean
  public StrategyValidatorFactory strategyValidatorFactory(ListableBeanFactory beanFactory) {
    return new StrategyValidatorFactory(beanFactory);
  }

  @Bean
  public ConfigurationValidator configurationValidator(StrategyValidatorFactory factory) {
    return new ConfigurationValidator(factory);
  }

  // 2. 策略实现
  @Bean
  public FullObfuscationStrategy fullObfuscationStrategy() { return new FullObfuscationStrategy(); }
  @Bean
  public PartialHideStrategy partialHideStrategy() { return new PartialHideStrategy(); }
  @Bean
  public KeepFirstLastStrategy keepFirstLastStrategy() { return new KeepFirstLastStrategy(); }
  @Bean
  public HashSHA256Strategy hashSHA256Strategy() { return new HashSHA256Strategy(); }
  @Bean
  public PatternRegexStrategy patternRegexStrategy() { return new PatternRegexStrategy(); }

  @Bean
  @ConditionalOnMissingBean
  public ObfuscationStrategyFactory obfuscationStrategyFactory(
    FullObfuscationStrategy full, PartialHideStrategy partial,
    KeepFirstLastStrategy keep, HashSHA256Strategy hash, PatternRegexStrategy regex) {
    return new ObfuscationStrategyFactory(List.of(full, partial, keep, hash, regex));
  }

  // 3. 核心配置与过滤器
  @Bean
  public RuleBuilder ruleBuilder() { return new RuleBuilder(); }

  @Bean
  public ObfuscateConfig obfuscateConfig(ObfuscateProperties properties,
                                         ConfigurationValidator validator,
                                         RuleBuilder ruleBuilder) {
    return new ObfuscateConfig(properties, validator, ruleBuilder);
  }

  @Bean
  @ConditionalOnMissingBean
  public JsonBodyObfuscationFilter jsonBodyObfuscationFilter(ObfuscateConfig config, ObfuscationStrategyFactory factory) {
    return new JsonBodyObfuscationFilter(config, factory);
  }

  @Bean
  @ConditionalOnMissingBean
  public HeaderObfuscationFilter headerObfuscationFilter(ObfuscateConfig config, ObfuscationStrategyFactory factory) {
    return new HeaderObfuscationFilter(config, factory);
  }

  @Bean
  @ConditionalOnMissingBean
  public QueryParamObfuscationFilter queryParamObfuscationFilter(ObfuscateConfig config, ObfuscationStrategyFactory factory) {
    return new QueryParamObfuscationFilter(config, factory);
  }
}
