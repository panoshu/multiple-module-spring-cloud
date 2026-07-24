package com.example.shared.pdf.service;

import com.example.shared.pdf.cache.PdfFontCache;
import com.example.shared.pdf.model.BasePdfContext;
import com.example.shared.pdf.model.PdfMetadata;
import com.example.shared.pdf.properties.PdfProperties;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PdfServiceTest {

  @Mock
  private PdfProperties properties;
  @Mock
  private TemplateEngine templateEngine;
  @Mock
  private PdfFontCache fontCache;
  @Mock
  private ResourceLoader resourceLoader;
  @Mock
  private Resource resource;
  @Mock
  private File mockFile;

  @Test
  @DisplayName("初始化：当配置的BaseURI是文件而非目录时，应抛出异常")
  void constructor_ShouldThrow_WhenBaseUriIsFile() throws Exception {
    // Arrange
    when(properties.getBaseUri()).thenReturn("file:/data/test.txt");
    when(resourceLoader.getResource(anyString())).thenReturn(resource);
    when(resource.exists()).thenReturn(true);
    when(resource.isFile()).thenReturn(true);
    when(resource.getFile()).thenReturn(mockFile);
    when(mockFile.isDirectory()).thenReturn(false); // 关键：不是目录
    when(mockFile.getAbsolutePath()).thenReturn("/data/test.txt");

    // Act & Assert
    IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
      new PdfService(properties, templateEngine, fontCache, resourceLoader));

    // 验证根因是否为 IllegalArgumentException
    assertInstanceOf(IllegalArgumentException.class, ex.getCause());
    // 可选：验证异常信息包含路径
    assertTrue(ex.getCause().getMessage().contains("/data/test.txt"));

  }

  @Test
  @DisplayName("生成PDF：验证Context数据注入逻辑(pdfMeta和Map转换)")
  void generatePdf_ShouldInjectCorrectContextData() throws Exception {
    // 1. Arrange: 初始化 Service
    setupMockForConstructor();
    PdfService service = new PdfService(properties, templateEngine, fontCache, resourceLoader);

    // Mock 依赖
    when(properties.getDefaultMetadata()).thenReturn(PdfMetadata.builder().author("Default").build());
    when(fontCache.getAllFonts()).thenReturn(Collections.emptyMap());
    when(templateEngine.process(eq("test-tpl"), any(Context.class))).thenReturn("<html></html>");

    // 2. Act: 准备请求数据
    TestContext req = TestContext.builder()
      .orderNo("ORD-001")
      .metadata(PdfMetadata.builder().title("Override").build())
      .build();

    service.generatePdf(req);

    // 3. Assert: 捕获传递给模板引擎的 Context
    ArgumentCaptor<Context> captor = ArgumentCaptor.forClass(Context.class);
    verify(templateEngine).process(eq("test-tpl"), captor.capture());

    Context usedContext = captor.getValue();

    // 验证 pdfMeta 变量 (合并后的结果)
    PdfMetadata meta = (PdfMetadata) usedContext.getVariable("pdfMeta");
    assertNotNull(meta);
    assertEquals("Override", meta.getTitle()); // 覆盖的
    assertEquals("Default", meta.getAuthor()); // 默认的

    // 验证业务数据是否被扁平化放入 Context (通过 convertValue)
    assertEquals("ORD-001", usedContext.getVariable("orderNo"));
    // 验证 JsonIgnore 生效，metadata 字段不应作为普通变量存在
    assertNull(usedContext.getVariable("metadata"));
  }

  @Test
  @DisplayName("流式生成：应正确写入OutputStream")
  void generatePdfStream_ShouldWriteToStream() throws Exception {
    // Arrange
    setupMockForConstructor();
    PdfService service = new PdfService(properties, templateEngine, fontCache, resourceLoader);

    when(properties.getDefaultMetadata()).thenReturn(new PdfMetadata());
    when(fontCache.getAllFonts()).thenReturn(Collections.emptyMap());
    when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html><body>Stream Test</body></html>");

    TestContext req = TestContext.builder().build();
    ByteArrayOutputStream os = new ByteArrayOutputStream();

    // Act
    service.generatePdfStream(req, os);

    // Assert
    byte[] bytes = os.toByteArray();
    assertTrue(bytes.length > 0);
    // 简单验证 PDF 头
    String header = new String(bytes, 0, 4);
    assertEquals("%PDF", header);
  }

  // 辅助方法：模拟构造函数所需的资源加载
  private void setupMockForConstructor() throws Exception {
    when(properties.getBaseUri()).thenReturn("classpath:/");
    when(resourceLoader.getResource(anyString())).thenReturn(resource);
    when(resource.exists()).thenReturn(true);
    // 模拟 classpath 路径 (非文件系统)
    when(resource.isFile()).thenReturn(false);
    when(resource.getURL()).thenReturn(URI.create("file:/tmp/").toURL());
  }

  // 测试用的上下文子类
  @Getter
  @SuperBuilder
  @EqualsAndHashCode(callSuper = true)
  static class TestContext extends BasePdfContext {
    private String orderNo;

    @Override
    public String getTemplateName() {
      return "test-tpl";
    }
  }
}
