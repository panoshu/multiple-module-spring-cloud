package com.example.shared.pdf.autoconfigure;

import com.example.shared.pdf.cache.PdfFontCache;
import com.example.shared.pdf.properties.PdfProperties;
import com.example.shared.pdf.service.PdfService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ResourceLoader;
import org.thymeleaf.TemplateEngine;

@AutoConfiguration
@EnableConfigurationProperties(PdfProperties.class)
@Import(TemplateEngineConfiguration.class)
public class SharedPdfAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public PdfFontCache pdfFontCache(PdfProperties properties, ResourceLoader resourceLoader) {
    return new PdfFontCache(properties, resourceLoader);
  }

  @Bean
  @ConditionalOnMissingBean
  public PdfService pdfService(
    PdfProperties properties,
    TemplateEngine pdfTemplateEngine,
    PdfFontCache fontCache,
    ResourceLoader resourceLoader) {
    return new PdfService(properties, pdfTemplateEngine, fontCache, resourceLoader);
  }
}
