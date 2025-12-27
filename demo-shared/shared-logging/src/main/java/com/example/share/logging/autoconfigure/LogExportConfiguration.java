package com.example.share.logging.autoconfigure;

import com.example.share.logging.export.dispatcher.LogExporterDispatcher;
import com.example.share.logging.export.exporter.LogExporter;
import com.example.share.logging.export.exporter.impl.DatabaseLogExporter;
import com.example.share.logging.export.exporter.impl.FileLogExporter;
import com.example.share.logging.export.persistence.repository.HttpExchangeLogPGRepository;
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
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.util.List;

@Configuration
public class LogExportConfiguration {

  // --- 基础设施 ---
  @Bean
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
  @ConditionalOnClass(DataSource.class)
  public JdbcClient jdbcClient(DataSource dataSource) {
    return JdbcClient.create(dataSource);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(JdbcClient.class)
  public HttpExchangeLogPGRepository httpExchangeLogPGRepository(JdbcClient jdbcClient) {
    return new HttpExchangeLogPGRepository(jdbcClient);
  }

  // --- 具体 Exporters ---
  @Bean
  @ConditionalOnProperty(name = "logbook.database.enable", havingValue = "true")
  @ConditionalOnBean(HttpExchangeLogPGRepository.class)
  public DatabaseLogExporter databaseLogExporter(HttpExchangeLogPGRepository repository) {
    return new DatabaseLogExporter(repository);
  }

  @Bean
  @ConditionalOnProperty(name = "logbook.file.enable", havingValue = "true", matchIfMissing = true)
  public FileLogExporter fileLogExporter(ObjectMapper objectMapper) {
    return new FileLogExporter(objectMapper);
  }

  // --- 分发器 (Dispatcher) ---
  // 关键点：将 List<LogExporter> 封装为一个 LogExporterDispatcher
  @Bean
  @ConditionalOnMissingBean
  public LogExporterDispatcher logExporterDispatcher(List<LogExporter> exporters) {
    return new LogExporterDispatcher(exporters);
  }
}
