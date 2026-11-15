package com.example.auth.infrastructure.product.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 内存组织目录的配置属性（立项批次 67）。
 * <p>
 * 仅在 {@code auth.org-directory.enabled=true} 时由 {@link OrgDirectoryConfig}
 * 读取并构建 {@code InMemoryProductGateway}；未提供时保持 {@code StubProductGateway}。
 *
 * @author auth-infrastructure
 */
@Data
@ConfigurationProperties(prefix = "auth.org-directory")
public class PlanDirectoryProperties {

  private List<PlanDef> plans = List.of();

  /** 单条计划层级配置。 */
  @Data
  public static class PlanDef {
    private String planNo;
    private String productNo;
    private String customerNo;
    private String parentPlanNo;
    private String name;
  }
}
