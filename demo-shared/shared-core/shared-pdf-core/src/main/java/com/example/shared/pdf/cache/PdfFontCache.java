package com.example.shared.pdf.cache;

import com.example.shared.pdf.properties.PdfProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 字体缓存管理器
 * 作用：启动时加载字体到内存，避免每次生成 PDF 都进行磁盘 IO / Jar 包解压
 */
@Slf4j
public class PdfFontCache {

  private final PdfProperties properties;
  private final ResourceLoader resourceLoader;

  private final Map<String, byte[]> fontCache = new ConcurrentHashMap<>();

  public PdfFontCache(PdfProperties properties, ResourceLoader resourceLoader) {
    this.properties = properties;
    this.resourceLoader = resourceLoader;
    init();
  }

  public void init() {
    if (properties.getFonts() == null || properties.getFonts().isEmpty()) {
      return;
    }

    log.info("Starting to pre-load PDF fonts...");
    for (PdfProperties.FontConfig fontConfig : properties.getFonts()) {
      try {
        loadFont(fontConfig);
      } catch (Exception e) {
        // 字体加载失败是严重错误，建议中断启动，或者仅记录 Error
        throw new IllegalStateException("Failed to load font: " + fontConfig.getPath(), e);
      }
    }
    log.info("{} fonts loaded：{}", fontCache.size(), fontCache.keySet());
  }

  private void loadFont(PdfProperties.FontConfig font) throws IOException {
    Resource resource = resourceLoader.getResource(font.getPath());
    if (!resource.exists()) {
      throw new IOException("Font file not found: " + font.getPath());
    }

    try (InputStream is = resource.getInputStream()) {
      // 将流一次性读入内存字节数组
      byte[] fontBytes = StreamUtils.copyToByteArray(is);
      fontCache.put(font.getFamilyName(), fontBytes);
      log.debug("Loaded font '{}' from {}, size: {} KB",
        font.getFamilyName(), font.getPath(), fontBytes.length / 1024);
    }
  }

  public Map<String, byte[]> getAllFonts() {
    return fontCache;
  }
}
