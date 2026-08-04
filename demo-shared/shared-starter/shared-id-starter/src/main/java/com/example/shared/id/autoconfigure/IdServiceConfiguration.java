package com.example.shared.id.autoconfigure;

import com.example.shared.id.api.GlobalIdGenerator;
import com.example.shared.id.handler.IdTypeHandler;
import com.example.shared.id.handler.LongTypeHandler;
import com.example.shared.id.handler.StringTypeHandler;
import com.example.shared.id.metadata.AnnotationIdMetadataResolver;
import com.example.shared.id.metadata.IdMetadataResolver;
import com.example.shared.id.properties.IdProperties;
import com.example.shared.id.strategy.IdGenerationStrategy;
import com.example.shared.id.validator.IdDefinitionStartupValidator;
import com.example.shared.identifier.contract.IdService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@EnableConfigurationProperties(IdProperties.class)
public class IdServiceConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public IdMetadataResolver idMetadataResolver() {
    return new AnnotationIdMetadataResolver();
  }

  @Bean
  @ConditionalOnMissingBean
  public IdDefinitionStartupValidator idDefinitionStartupValidator(
    IdProperties properties,
    IdMetadataResolver metadataResolver) {
    return new IdDefinitionStartupValidator(properties, metadataResolver);
  }

  // 1. 注册 String 处理器
  @Bean
  @ConditionalOnMissingBean
  public StringTypeHandler stringTypeHandler() {
    return new StringTypeHandler();
  }

  // 2. 注册 Long 处理器
  @Bean
  @ConditionalOnMissingBean
  public LongTypeHandler longTypeHandler() {
    return new LongTypeHandler();
  }

  // 3. 全局生成器 (参数中增加 handlers 列表)
  @Bean
  @ConditionalOnMissingBean(IdService.class)
  public IdService idService(
    List<IdGenerationStrategy> strategies,
    IdMetadataResolver metadataResolver,
    List<IdTypeHandler> handlers) {
    return new GlobalIdGenerator(strategies, metadataResolver, handlers);
  }
}
