package com.example.file.infrastructure.bootstrap;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 演示模板配置播种参数（仅本地/联调启用）.
 *
 * <p>file-service 的模板配置正常由管理端通过 {@code TemplateConfigApi} 维护；
 * 本播种器用于本地联调/自动化集成测试在缺少管理端时快速准备可解析的
 * {@code FORM_DETAIL} 配置，避免测试依赖手工 SQL。</p>
 *
 * @author file-service
 */
@Data
@ConfigurationProperties(prefix = "file.demo.template-seed")
public class TemplateSeedProperties {

  /**
   * 是否启用播种（默认关闭，生产环境禁止开启）.
   */
  private boolean enabled = false;

  /**
   * 目标业务类型.
   */
  private String bizType = "FORM_DETAIL";

  /**
   * 配置版本.
   */
  private String version = "1.0";

  /**
   * 业务基线 YAML 的 classpath 路径.
   */
  private String baseline = "classpath:/templates/form_detail_base.yaml";

  /**
   * 源模板 YAML 的 classpath 路径（多个用英文逗号分隔）.
   */
  private String sourceTemplates = "classpath:/templates/form_detail_source_annuity.yaml";

  /**
   * 创建人.
   */
  private String operator = "SYSTEM";
}
