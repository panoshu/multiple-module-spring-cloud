package com.example.share.logging.autoconfigure;

import com.example.share.logging.obfuscate.strategy.ObfuscationStrategyFactory;
import com.example.share.logging.obfuscate.strategy.impl.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zalando.logbook.Logbook;

import java.util.List;

@Configuration
@ConditionalOnClass(Logbook.class)
@ConditionalOnProperty(prefix = "logbook.obfuscate.global", name = "enable", havingValue = "true", matchIfMissing = true)
public class LogbookObfuscationAutoConfiguration {

  @Bean
  public ObfuscationStrategyFactory obfuscationStrategyFactory(
    FullObfuscationStrategy fullStrategy,
    PartialHideStrategy partialHideStrategy,
    KeepFirstLastStrategy keepFirstLastStrategy,
    HashSHA256Strategy hashSha256Strategy,
    PatternRegexStrategy patternRegexStrategy) {

    return new ObfuscationStrategyFactory(List.of(
      fullStrategy,
      partialHideStrategy,
      keepFirstLastStrategy,
      hashSha256Strategy,
      patternRegexStrategy
    ));
  }





  // 内置策略实现
  @Bean
  public FullObfuscationStrategy fullObfuscationStrategy() {
    return new FullObfuscationStrategy();
  }

  @Bean
  public PartialHideStrategy partialHideStrategy() {
    return new PartialHideStrategy();
  }

  @Bean
  public KeepFirstLastStrategy keepFirstLastStrategy() {
    return new KeepFirstLastStrategy();
  }

  @Bean
  public HashSHA256Strategy hashMd5Strategy() {
    return new HashSHA256Strategy();
  }

  @Bean
  public PatternRegexStrategy patternRegexStrategy() {
    return new PatternRegexStrategy();
  }
}
