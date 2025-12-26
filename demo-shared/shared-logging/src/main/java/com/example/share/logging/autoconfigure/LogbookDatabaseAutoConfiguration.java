package com.example.share.logging.autoconfigure;

import com.example.share.logging.writer.mapper.HttpExchangeLogMapper;
import com.example.share.logging.writer.repository.HttpExchangeLogPGRepository;
import com.example.share.logging.writer.sink.DatabaseSink;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.util.concurrent.Executor;

@Configuration
@ConditionalOnProperty(prefix = "logbook.database", name = "enable", havingValue = "true")
public class LogbookDatabaseAutoConfiguration {

  @Bean
  public JdbcClient jdbcClient(DataSource dataSource) {
    return JdbcClient.create(dataSource);
  }

  @Bean
  @ConditionalOnMissingBean
  public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();

    // 1. 注册 Java 8 日期时间模块
    mapper.registerModule(new JavaTimeModule());

    // 2. 禁用时间戳序列化 (使用 ISO 8601 字符串)
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // 3. 启用缩进输出 (可选，用于日志可读性)
    mapper.enable(SerializationFeature.INDENT_OUTPUT);

    // 4. 失败时快速失败 (而不是忽略未知字段)
    mapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    // 5. 允许单引号 (兼容性更好)
    mapper.enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES);

    return mapper;
  }

  @Bean
  @ConditionalOnBean(ObjectMapper.class)
  @ConditionalOnMissingBean
  public HttpExchangeLogMapper httpExchangeLogMapper(ObjectMapper objectMapper) {
    return new HttpExchangeLogMapper(objectMapper);
  }

//  @Bean
//  @ConditionalOnMissingBean
//  @ConditionalOnBean(JdbcClient.class) // 确保 JdbcClient 存在
//  public HttpExchangeLogRepository httpExchangeLogRepository(JdbcClient jdbcClient) {
//    return new HttpExchangeLogRepository(jdbcClient);
//  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(JdbcClient.class) // 确保 JdbcClient 存在
  public HttpExchangeLogPGRepository httpExchangeLogPGRepository(JdbcClient jdbcClient) {
    return new HttpExchangeLogPGRepository(jdbcClient);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean({HttpExchangeLogPGRepository.class, HttpExchangeLogMapper.class})
  public DatabaseSink databaseSink(
    HttpExchangeLogPGRepository logRepository,
    HttpExchangeLogMapper httpExchangeLogMapper,
    Executor applicationTaskExecutor
  ) {
    return new DatabaseSink(logRepository, httpExchangeLogMapper,applicationTaskExecutor);
  }
}
