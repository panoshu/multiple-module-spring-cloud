package com.example.shared.pdf.cache;

import com.example.shared.pdf.properties.PdfProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PdfFontCacheTest {

  @Mock
  private PdfProperties properties;
  @Mock
  private ResourceLoader resourceLoader;
  @Mock
  private Resource resource;

  @InjectMocks
  private PdfFontCache fontCache;

  @Test
  @DisplayName("初始化：正常加载字体文件")
  void init_ShouldLoadFonts_WhenConfigIsValid() throws Exception {
    // Arrange
    PdfProperties.FontConfig conf = new PdfProperties.FontConfig();
    conf.setFamilyName("SimSun");
    conf.setPath("classpath:/fonts/simsun.ttf");
    when(properties.getFonts()).thenReturn(List.of(conf));

    when(resourceLoader.getResource("classpath:/fonts/simsun.ttf")).thenReturn(resource);
    when(resource.exists()).thenReturn(true);
    // 模拟文件内容
    when(resource.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

    // Act
    fontCache.init();

    // Assert
    assertEquals(1, fontCache.getAllFonts().size());
    assertArrayEquals(new byte[]{1, 2, 3}, fontCache.getAllFonts().get("SimSun"));
  }

  @Test
  @DisplayName("初始化：当字体配置为空时，应跳过加载且不报错")
  void init_ShouldDoNothing_WhenConfigIsEmpty() {
    // Arrange
    when(properties.getFonts()).thenReturn(null); // 或 Collections.emptyList()

    // Act
    fontCache.init();

    // Assert
    assertTrue(fontCache.getAllFonts().isEmpty());
    // 验证没有调用 resourceLoader
    verify(resourceLoader, never()).getResource(anyString());
  }

  @Test
  @DisplayName("初始化：当字体文件不存在时，应抛出 IllegalStateException (阻断启动)")
  void init_ShouldThrow_WhenFontFileMissing() {
    // Arrange
    PdfProperties.FontConfig conf = new PdfProperties.FontConfig();
    conf.setPath("missing.ttf");
    when(properties.getFonts()).thenReturn(List.of(conf));

    when(resourceLoader.getResource(anyString())).thenReturn(resource);
    when(resource.exists()).thenReturn(false);

    // Act & Assert
    IllegalStateException ex = assertThrows(IllegalStateException.class, () -> fontCache.init());
    assertTrue(ex.getMessage().contains("Failed to load font"));
  }

  @Test
  @DisplayName("初始化：当读取流发生 IO 异常时，应抛出 IllegalStateException")
  void init_ShouldThrow_WhenIoExceptionOccurs() throws Exception {
    // Arrange
    PdfProperties.FontConfig conf = new PdfProperties.FontConfig();
    conf.setPath("corrupted.ttf");
    when(properties.getFonts()).thenReturn(List.of(conf));

    when(resourceLoader.getResource(anyString())).thenReturn(resource);
    when(resource.exists()).thenReturn(true);
    when(resource.getInputStream()).thenThrow(new IOException("Disk error"));

    // Act & Assert
    IllegalStateException ex = assertThrows(IllegalStateException.class, () -> fontCache.init());
    assertEquals("Disk error", ex.getCause().getMessage());
  }
}
