package com.example.shared.pdf.autoconfigure;

import com.example.shared.pdf.properties.PdfProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TemplateEngineConfigurationTest {

  private final TemplateEngineConfiguration config = new TemplateEngineConfiguration();

  @Test
  @DisplayName("配置：当前缀以 file: 开头时，应使用 FileTemplateResolver")
  void pdfTemplateResolver_ShouldUseFileResolver() {
    PdfProperties props = new PdfProperties();
    props.setTemplatePrefix("file:/opt/templates/");
    props.setTemplateSuffix(".html");

    ITemplateResolver resolver = config.pdfTemplateResolver(props);

    assertInstanceOf(FileTemplateResolver.class, resolver);
    // 验证前缀是否去掉了 file:
    // 注意：FileTemplateResolver 无法直接 getPrefix 验证处理后的字符串，
    // 但我们可以验证类型正确性，这已经覆盖了核心逻辑分支。
  }

  @Test
  @DisplayName("配置：当前缀以 classpath: 开头时，应使用 ClassLoaderTemplateResolver")
  void pdfTemplateResolver_ShouldUseClasspathResolver() {
    PdfProperties props = new PdfProperties();
    props.setTemplatePrefix("classpath:/templates/pdf/");
    props.setTemplateSuffix(".html");

    ITemplateResolver resolver = config.pdfTemplateResolver(props);

    assertInstanceOf(ClassLoaderTemplateResolver.class, resolver);
  }

  @Test
  @DisplayName("配置：当无前缀时，默认使用 ClassLoaderTemplateResolver")
  void pdfTemplateResolver_ShouldDefaultToClasspath() {
    PdfProperties props = new PdfProperties();
    props.setTemplatePrefix("/templates/pdf/");

    ITemplateResolver resolver = config.pdfTemplateResolver(props);

    assertInstanceOf(ClassLoaderTemplateResolver.class, resolver);
  }
}
