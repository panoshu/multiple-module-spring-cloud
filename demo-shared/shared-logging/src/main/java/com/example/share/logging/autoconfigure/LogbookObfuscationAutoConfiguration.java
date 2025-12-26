package com.example.share.logging.autoconfigure;

import com.example.share.logging.obfuscate.config.ConfigurationValidator;
import com.example.share.logging.obfuscate.config.ObfuscateConfig;
import com.example.share.logging.obfuscate.config.ObfuscateProperties;
import com.example.share.logging.obfuscate.config.RuleBuilder;
import com.example.share.logging.obfuscate.config.validator.StrategyValidator;
import com.example.share.logging.obfuscate.config.validator.StrategyValidatorFactory;
import com.example.share.logging.obfuscate.config.validator.impl.*;
import com.example.share.logging.obfuscate.filter.HeaderObfuscationFilter;
import com.example.share.logging.obfuscate.filter.JsonBodyObfuscationFilter;
import com.example.share.logging.obfuscate.filter.QueryParamObfuscationFilter;
import com.example.share.logging.obfuscate.service.ValueObfuscate;
import com.example.share.logging.obfuscate.strategy.ObfuscationStrategy;
import com.example.share.logging.obfuscate.strategy.ObfuscationStrategyFactory;
import com.example.share.logging.obfuscate.strategy.impl.*;
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
  @ConditionalOnMissingBean
  public FullValidator fullValidator() { return new FullValidator(); }
  @Bean
  @ConditionalOnMissingBean
  public PartialHideValidator partialHideValidator() { return new PartialHideValidator(); }
  @Bean
  @ConditionalOnMissingBean
  public KeepFirstLastValidator keepFirstLastValidator() { return new KeepFirstLastValidator(); }
  @Bean
  @ConditionalOnMissingBean
  public HashSHA256Validator hashSHA256Validator() { return new HashSHA256Validator(); }
  @Bean
  @ConditionalOnMissingBean
  public PatternRegexValidator patternRegexValidator() { return new PatternRegexValidator(); }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnClass(StrategyValidator.class)
  public StrategyValidatorFactory strategyValidatorFactory(List<StrategyValidator> strategyValidators) {
    return new StrategyValidatorFactory(strategyValidators);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnClass(StrategyValidatorFactory.class)
  public ConfigurationValidator configurationValidator(StrategyValidatorFactory factory) {
    return new ConfigurationValidator(factory);
  }

  // 2. 策略实现
  @Bean
  @ConditionalOnMissingBean
  public FullObfuscationStrategy fullObfuscationStrategy() { return new FullObfuscationStrategy(); }
  @Bean
  @ConditionalOnMissingBean
  public PartialHideStrategy partialHideStrategy() { return new PartialHideStrategy(); }
  @Bean
  @ConditionalOnMissingBean
  public KeepFirstLastStrategy keepFirstLastStrategy() { return new KeepFirstLastStrategy(); }
  @Bean
  @ConditionalOnMissingBean
  public HashSHA256Strategy hashSHA256Strategy() { return new HashSHA256Strategy(); }
  @Bean
  @ConditionalOnMissingBean
  public PatternRegexStrategy patternRegexStrategy() { return new PatternRegexStrategy(); }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnClass(ObfuscationStrategy.class)
  public ObfuscationStrategyFactory obfuscationStrategyFactory(
    List<ObfuscationStrategy>  obfuscationStrategies) {
    return new ObfuscationStrategyFactory(obfuscationStrategies);
  }

  @Bean
  @ConditionalOnMissingBean
  public RuleBuilder ruleBuilder() { return new RuleBuilder(); }

  @Bean
  @ConditionalOnMissingBean
  public ObfuscateConfig obfuscateConfig(ObfuscateProperties properties,
                                         ConfigurationValidator validator,
                                         RuleBuilder ruleBuilder) {
    return new ObfuscateConfig(properties, validator, ruleBuilder);
  }

  @Bean
  @ConditionalOnMissingBean
  public ValueObfuscate valueObfuscate(ObfuscationStrategyFactory factory) {
    return new ValueObfuscate(factory);
  }

  @Bean
  @ConditionalOnMissingBean
  public JsonBodyObfuscationFilter jsonBodyObfuscationFilter(ObfuscateConfig config, ValueObfuscate valueObfuscate) {
    return new JsonBodyObfuscationFilter(config, valueObfuscate);
  }

  @Bean
  @ConditionalOnMissingBean
  public HeaderObfuscationFilter headerObfuscationFilter(ObfuscateConfig config, ValueObfuscate valueObfuscate) {
    return new HeaderObfuscationFilter(config, valueObfuscate);
  }

  @Bean
  @ConditionalOnMissingBean
  public QueryParamObfuscationFilter queryParamObfuscationFilter(ObfuscateConfig config, ValueObfuscate valueObfuscate) {
    return new QueryParamObfuscationFilter(config, valueObfuscate);
  }
}
