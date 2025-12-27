package com.example.share.logging.autoconfigure;

import com.example.share.logging.core.api.LogProcessor;
import com.example.share.logging.core.pipeline.AsyncLogPipeline;
import com.example.share.logging.export.dispatcher.LogExporterDispatcher;
import com.example.share.logging.sanitization.engine.SanitizationEngine;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
}
