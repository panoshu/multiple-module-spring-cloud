package com.example.shared.id.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * ID 生成器配置属性。
 * <p>
 * 配置前缀为 {@code shared.id}，与 application.yml 中的 {@code shared.id.rules} 保持一致。
 * 路由规则 {@code rules} 用于将业务类型映射到物理数据库序列 Key。
 *
 * @author panoshu
 */
@Data
@ConfigurationProperties(prefix = "shared.id")
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
