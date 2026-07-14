package com.example.shared.id.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "shared.identity")
public class IdProperties {
  // 路由规则
  private Map<String, String> rules;

  // 【新增】启动时校验配置
  private Validation validation = new Validation();

  @Data
  public static class Validation {
    /**
     * 是否开启启动时校验 (默认 true)
     */
    private boolean enabled = true;

    /**
     * 要扫描 ID 定义的包路径列表
     */
    private List<String> packages;
  }
}
