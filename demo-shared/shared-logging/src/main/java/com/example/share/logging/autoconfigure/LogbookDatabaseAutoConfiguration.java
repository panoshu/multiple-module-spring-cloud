package com.example.share.logging.autoconfigure;

import com.example.share.logging.obfuscate.filter.HeaderObfuscationFilter;
import com.example.share.logging.obfuscate.filter.JsonBodyObfuscationFilter;
import com.example.share.logging.obfuscate.filter.QueryParamObfuscationFilter;
import com.example.share.logging.sink.repository.HttpExchangeLogRepository;
import com.example.share.logging.sink.writer.DatabaseSink;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.zalando.logbook.Logbook;

import java.util.UUID;

@Configuration
@ConditionalOnProperty(prefix = "logbook.database", name = "enabled", havingValue = "true")
@EnableScheduling // 启用定时任务
public class LogbookDatabaseAutoConfiguration {

  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  @Bean
  public DatabaseSink databaseSink(
    HttpExchangeLogRepository logRepository,
    ObjectMapper objectMapper) {
    return new DatabaseSink(logRepository, objectMapper);
  }

  @Bean
  @ConditionalOnProperty(prefix = "logbook.database", name = "enabled", havingValue = "true")
  public Logbook logbook(
    DatabaseSink sink,
    // ✅ 修复1：注入已有的过滤器Bean，而不是手动创建
    JsonBodyObfuscationFilter bodyFilter,
    HeaderObfuscationFilter headerFilter,
    QueryParamObfuscationFilter queryFilter) {

    return Logbook.builder()
      // ✅ 使用注入的过滤器Bean
      .headerFilter(headerFilter)
      .queryFilter(queryFilter)
      .bodyFilter(bodyFilter)

      // 使用 Sink
      .sink(sink)

      // ✅ 修复3：正确的correlationId用法
      .correlationId(request -> UUID.randomUUID().toString())

      // ✅ 修复2：移除CustomCondition，使用原生配置
      .build();
  }
}
