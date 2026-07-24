package com.example.shared.pdf.properties;

import com.example.shared.pdf.model.PdfMetadata;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "shared.pdf")
public class PdfProperties {

  /**
   * 模板类路径前缀，默认 classpath:/templates/pdf/
   */
  private String templatePrefix = "/templates/pdf/";

  /**
   * 模板后缀，默认 .html
   */
  private String templateSuffix = ".html";

  /**
   * 图片/资源的基础路径 (用于解析HTML中的相对路径图片)
   * 如果为空，默认使用类路径根目录
   */
  private String baseUri = "";

  @NestedConfigurationProperty
  private PdfMetadata defaultMetadata = new PdfMetadata();

  /**
   * 字体配置列表
   */
  private List<FontConfig> fonts = new ArrayList<>();

  @Data
  public static class FontConfig {
    /**
     * 字体名称 (对应 CSS 中的 font-family)
     * 例如: "SimSun", "Arial"
     */
    private String familyName;

    /**
     * 字体文件路径 (支持 classpath: 前缀)
     * 例如: "classpath:/fonts/SimSun.ttf"
     */
    private String path;
  }
}
