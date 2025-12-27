package com.example.share.logging.autoconfigure;

import com.example.share.logging.core.api.LogProcessor;
import com.example.share.logging.integration.logbook.LogbookMapper; // 原 HttpExchangeLogMapper
import com.example.share.logging.integration.logbook.LogbookSinkAdapter; // 原 UnifiedAsyncSink
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.Sink;

@Configuration
@ConditionalOnClass(Logbook.class)
public class LogbookIntegrationConfiguration {

  @Bean
  @ConditionalOnBean(ObjectMapper.class)
  @ConditionalOnMissingBean
  public LogbookMapper logbookMapper(ObjectMapper objectMapper) {
    return new LogbookMapper(objectMapper);
  }

  // 将 Sink 暴露给 Logbook 框架
  @Bean
  @Primary
  public Sink logbookSink(LogbookMapper mapper, LogProcessor logProcessor) {
    // LogbookSinkAdapter 实现了 Sink 接口
    // 它只负责转换数据，然后把逻辑委托给 LogProcessor (AsyncLogPipeline)
    return new LogbookSinkAdapter(mapper, logProcessor);
  }
}
