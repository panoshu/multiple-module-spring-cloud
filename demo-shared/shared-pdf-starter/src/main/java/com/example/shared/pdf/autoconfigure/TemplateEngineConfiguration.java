package com.example.shared.pdf.autoconfigure;

import com.example.shared.pdf.properties.PdfProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.AbstractConfigurableTemplateResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

@Slf4j
@Configuration
@EnableConfigurationProperties(PdfProperties.class)
public class TemplateEngineConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public ITemplateResolver pdfTemplateResolver(PdfProperties properties) {
    String rawPrefix = properties.getTemplatePrefix();
    AbstractConfigurableTemplateResolver resolver;

    // 1. 策略选择：文件系统 vs Classpath
    if (rawPrefix != null && rawPrefix.startsWith("file:")) {
      resolver = new FileTemplateResolver();
      String path = rawPrefix.substring("file:".length());
      resolver.setPrefix(path);
      log.info("PDF Template Resolver using FILE system: {}", path);
    } else {
      resolver = new ClassLoaderTemplateResolver();
      String path = rawPrefix;
      if (path != null && path.startsWith("classpath:")) {
        path = path.substring("classpath:".length());
      }
      resolver.setPrefix(path);
      log.info("PDF Template Resolver using CLASSPATH: {}", path);
    }

    // 2. 通用配置 (优化：提取到外层，消除重复)
    resolver.setSuffix(properties.getTemplateSuffix());
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCharacterEncoding("UTF-8");
    resolver.setCacheable(true);

    return resolver;
  }

  @Bean
  @ConditionalOnMissingBean
  public TemplateEngine pdfTemplateEngine(ITemplateResolver pdfTemplateResolver) {
    TemplateEngine engine = new TemplateEngine();
    engine.setTemplateResolver(pdfTemplateResolver);
    return engine;
  }
}
