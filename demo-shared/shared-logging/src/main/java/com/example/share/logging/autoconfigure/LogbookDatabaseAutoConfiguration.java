package com.example.share.logging.autoconfigure;

import com.example.share.logging.writer.mapper.HttpExchangeLogMapper;
import com.example.share.logging.writer.repository.HttpExchangeLogRepository;
import com.example.share.logging.writer.sink.DatabaseSink;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "logbook.database", name = "enabled", havingValue = "true")
public class LogbookDatabaseAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  @Bean
  @ConditionalOnProperty(prefix = "logbook.database", name = "enabled", havingValue = "true")
  public HttpExchangeLogMapper httpExchangeLogMapper(ObjectMapper objectMapper) {
    return new HttpExchangeLogMapper(objectMapper);
  }

  @Bean
  @ConditionalOnProperty(prefix = "logbook.database", name = "enabled", havingValue = "true")
  public DatabaseSink databaseSink(
    HttpExchangeLogRepository logRepository,
    HttpExchangeLogMapper httpExchangeLogMapper) {
    return new DatabaseSink(logRepository, httpExchangeLogMapper);
  }
}
