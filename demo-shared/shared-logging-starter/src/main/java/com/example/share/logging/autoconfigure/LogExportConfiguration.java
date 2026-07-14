package com.example.share.logging.autoconfigure;

import com.example.share.logging.export.dispatcher.LogExporterDispatcher;
import com.example.share.logging.export.exporter.LogExporter;
import com.example.share.logging.export.exporter.impl.DatabaseLogExporter;
import com.example.share.logging.export.exporter.impl.FileLogExporter;
import com.example.share.logging.export.persistence.repository.HttpExchangeLogMySQLRepository;
import com.example.share.logging.export.persistence.repository.HttpExchangeLogPGRepository;
import com.example.share.logging.export.persistence.repository.HttpExchangeLogRepository;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.util.List;

@Configuration
public class LogExportConfiguration {

  // --- 基础设施 ---
  @Bean
  @Primary
  @ConditionalOnMissingBean
  public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    return mapper;
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnClass({DataSource.class, JdbcClient.class})
  public JdbcClient jdbcClient(DataSource dataSource) {
    return JdbcClient.create(dataSource);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(JdbcClient.class)
  @ConditionalOnClass(name = "org.postgresql.Driver")
  public HttpExchangeLogRepository httpExchangeLogPGRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
    return new HttpExchangeLogPGRepository(jdbcClient, objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(JdbcClient.class)
  @ConditionalOnClass(name = "com.mysql.cj.jdbc.Driver")
  public HttpExchangeLogRepository httpExchangeLogMySQLRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
    return new HttpExchangeLogMySQLRepository(jdbcClient, objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(JdbcClient.class)
  @ConditionalOnClass(name = "com.oceanbase.jdbc.Driver")
  public HttpExchangeLogRepository httpExchangeLogOceanBaseRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
    return new HttpExchangeLogMySQLRepository(jdbcClient, objectMapper);
  }

  // --- 具体 Exporters ---
  @Bean
  @ConditionalOnProperty(prefix = "shared.logging", name = "database.enable", havingValue = "true")
  @ConditionalOnBean(HttpExchangeLogRepository.class)
  public DatabaseLogExporter databaseLogExporter(HttpExchangeLogRepository repository) {
    return new DatabaseLogExporter(repository);
  }

  @Bean
  @ConditionalOnProperty(prefix = "shared.logging", name = "file.enable", havingValue = "true", matchIfMissing = true)
  public FileLogExporter fileLogExporter(ObjectMapper objectMapper) {
    return new FileLogExporter(objectMapper);
  }

  // --- 分发器 (Dispatcher) ---
  @Bean
  @ConditionalOnMissingBean
  public LogExporterDispatcher logExporterDispatcher(List<LogExporter> exporters) {
    return new LogExporterDispatcher(exporters);
  }
}
