package com.example.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;
import java.util.Map;

/**
 * 报文加密配置。
 *
 * <p>配置前缀：gateway.crypto
 *
 * @author trae
 * @since 1.0
 */
@ConfigurationProperties(prefix = "gateway.crypto")
public record CryptoProperties(
  @DefaultValue("false") boolean enabled,
  String secretKey,
  @DefaultValue List<String> excludePaths,
  @DefaultValue Map<String, FieldConfig> fields
) {

  public CryptoProperties {
    excludePaths = excludePaths != null ? List.copyOf(excludePaths) : List.of();
    fields = fields != null ? Map.copyOf(fields) : Map.of();
  }

  public record FieldConfig(
    @DefaultValue List<String> aliases
  ) {
    public FieldConfig {
      aliases = aliases != null ? List.copyOf(aliases) : List.of();
    }
  }
}