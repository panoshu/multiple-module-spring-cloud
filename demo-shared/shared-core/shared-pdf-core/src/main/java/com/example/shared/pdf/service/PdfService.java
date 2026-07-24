package com.example.shared.pdf.service;

import com.example.shared.pdf.cache.PdfFontCache;
import com.example.shared.pdf.model.BasePdfContext;
import com.example.shared.pdf.model.PdfMetadata;
import com.example.shared.pdf.properties.PdfProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhtmltopdf.css.parser.CSSPrimitiveValue;
import com.openhtmltopdf.css.parser.property.PageSize;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StopWatch;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.*;
import java.util.Locale;
import java.util.Map;

@Slf4j
public class PdfService {

  private final PdfProperties properties;
  private final TemplateEngine templateEngine;
  private final PdfFontCache fontCache;
  private final ResourceLoader resourceLoader;
  private final String cachedBaseUri;

  private final ObjectMapper objectMapper = new ObjectMapper();

  public PdfService(PdfProperties properties, TemplateEngine templateEngine, PdfFontCache fontCache, ResourceLoader resourceLoader) {
    this.properties = properties;
    this.templateEngine = templateEngine;
    this.fontCache = fontCache;
    this.resourceLoader = resourceLoader;
    this.cachedBaseUri = this.resolveBaseUri(properties.getBaseUri());
    log.info("PDF Base URI resolved and cached: {}", this.cachedBaseUri);
  }

  public byte[] generatePdf(BasePdfContext pdfContext) {
    try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
      generatePdfStream(pdfContext, os);
      return os.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException("PDF generation failed", e);
    }
  }

  public byte[] generatePdfFromHtml(String htmlContent) {
    String producer = properties.getDefaultMetadata().getCreator();
    if (producer == null) {
      producer = "OpenHTMLtoPDF";
    }

    try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
      writePdfToStream(htmlContent, producer, os);
      return os.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException("PDF generation failed", e);
    }
  }

  public void generatePdfStream(BasePdfContext pdfContext, OutputStream outputStream) {
    StopWatch stopWatch = new StopWatch();
    stopWatch.start();

    PdfMetadata finalMeta = properties.getDefaultMetadata().merge(pdfContext.getMetadata());
    String htmlContent = renderTemplate(pdfContext, finalMeta);

    String producer = finalMeta.getCreator() != null ? finalMeta.getCreator() : "OpenHTMLtoPDF";
    writePdfToStream(htmlContent, producer, outputStream);

    stopWatch.stop();
    log.info("PDF generated from template '{}'  in {} ms",
      pdfContext.getTemplateName(), stopWatch.getTotalTimeMillis());
  }

  private String renderTemplate(BasePdfContext pdfContext, PdfMetadata finalMeta) {
    Context thymeleafContext = new Context();
    thymeleafContext.setLocale(Locale.CHINA);

    Map<String, Object> dataMap = objectMapper.convertValue(
      pdfContext,
      new TypeReference<>() {
      }
    );
    thymeleafContext.setVariable("pdfMeta", finalMeta);
    thymeleafContext.setVariables(dataMap);

    return templateEngine.process(pdfContext.getTemplateName(), thymeleafContext);
  }

  private void writePdfToStream(String htmlContent, String producer, OutputStream outputStream) {
    try {
      PdfRendererBuilder builder = new PdfRendererBuilder();
      builder.withProducer(producer);

      // A4 Layout
      builder.useDefaultPageSize(
        PageSize.A4.getPageWidth().getFloatValue(CSSPrimitiveValue.CSS_MM),
        PageSize.A4.getPageHeight().getFloatValue(CSSPrimitiveValue.CSS_MM),
        PdfRendererBuilder.PageSizeUnits.MM
      );

      // Fonts
      fontCache.getAllFonts().forEach((familyName, bytes) ->
        builder.useFont(() -> new ByteArrayInputStream(bytes), familyName));

      // BaseURI & Content
      builder.withHtmlContent(htmlContent, this.cachedBaseUri);

      // 直接写入传入的流
      builder.toStream(outputStream);
      builder.run();

    } catch (Exception e) {
      throw new RuntimeException("PDF generation to stream failed", e);
    }
  }

  /**
   * 计算资源 Base URL
   * 1. 支持自动处理中文/空格编码
   * 2. 强制校验必须为目录
   */
  private String resolveBaseUri(String configuredUri) {
    // 1. 默认值处理
    if (configuredUri == null || configuredUri.trim().isEmpty()) {
      configuredUri = "classpath:/";
      log.info("PDF Base URI not configured, using default: {}", configuredUri);
    }

    // 2. 补全结尾斜杠 (标准的目录 URL 应该以 / 结尾)
    if (!configuredUri.endsWith("/")) {
      configuredUri += "/";
    }

    try {
      Resource resource = resourceLoader.getResource(configuredUri);
      if (!resource.exists()) {
        throw new FileNotFoundException("Configured pdf base-uri does not exist: " + configuredUri);
      }

      // 3. 区分处理：文件系统 vs Jar包/网络
      if (resource.isFile()) {
        File file = resource.getFile();

        // 强制校验：必须是目录
        if (!file.isDirectory()) {
          throw new IllegalArgumentException("Configured pdf base-uri must be a directory, but found a file: " + file.getAbsolutePath());
        }

        // 4. 文件系统路径：转 File -> URI -> URL 以确保中文/空格编码正确
        return file.toURI().toURL().toExternalForm();
      } else {
        // 5. Jar 包内路径：直接使用 URL (Jar 内路径通常是标准的，无法用 File API 判断目录)
        return resource.getURL().toExternalForm();
      }

    } catch (Exception e) {
      // 包装异常，提供清晰的报错信息
      throw new IllegalStateException("Invalid shared.pdf.base-uri configuration: " + configuredUri, e);
    }
  }
}
