package com.example.shared.permission;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 权限校验本地缓存配置（Phase C）.
 *
 * @author shared-permission-starter
 */
@ConfigurationProperties(prefix = "shared.permission.cache")
public record PermissionCacheProperties(
  boolean enabled,
  long ttlSeconds,
  long maximumSize
) {

  public PermissionCacheProperties {
    if (ttlSeconds <= 0) {
      ttlSeconds = 300;
    }
    if (maximumSize <= 0) {
      maximumSize = 10_000;
    }
  }

  /**
   * 默认：启用，TTL 300 秒（与 auth 权限双区缓存节奏一致），容量 10000.
   */
  public static PermissionCacheProperties defaults() {
    return new PermissionCacheProperties(true, 300, 10_000);
  }
}
