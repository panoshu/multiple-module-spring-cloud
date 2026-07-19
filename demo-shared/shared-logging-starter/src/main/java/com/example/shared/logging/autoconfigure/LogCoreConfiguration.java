package com.example.shared.logging.autoconfigure;

import com.example.shared.logging.aspect.MethodTimingAspect;
import com.example.shared.logging.core.api.LogProcessor;
import com.example.shared.logging.core.pipeline.AsyncLogPipeline;
import com.example.shared.logging.export.dispatcher.LogExporterDispatcher;
import com.example.shared.logging.sanitization.engine.SanitizationEngine;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;

@Configuration
public class LogCoreConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public LogProcessor logProcessor(
    SanitizationEngine sanitizationEngine,
    LogExporterDispatcher logExporterDispatcher,
    @Qualifier("applicationTaskExecutor") Executor taskExecutor) {

    return new AsyncLogPipeline(sanitizationEngine, logExporterDispatcher, taskExecutor);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(prefix = "shared.logging", name = "timing.enable", havingValue = "true")
  public MethodTimingAspect methodTimingAspect() {
    return new MethodTimingAspect();
  }
}
