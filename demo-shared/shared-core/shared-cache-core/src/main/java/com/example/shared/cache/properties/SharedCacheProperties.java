package com.example.shared.cache.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "shared.cache")
public record SharedCacheProperties(
  @DefaultValue("MULTI") CacheMode mode,
  @DefaultValue CacheRule defaultRule,
  Map<String, CacheRule> rules,

  @DefaultValue L1Config l1
) {
  public SharedCacheProperties {
    if (rules == null) {
      rules = new HashMap<>();
    }
  }

  public enum CacheMode {L1_ONLY, L2_ONLY, MULTI}

  /**
   * S-06：仅保留实际生效的 ttl/mode/L1 容量；serializerType、topic、allowedPackages、
   * cacheNullValues、useKeyPrefix 均无消费者，已删除，避免配置项误导。
   */
  public record CacheRule(
    @DefaultValue("1h") Duration ttl,
    CacheMode mode,
    @DefaultValue("100") int l1InitialCapacity,
    @DefaultValue("1000") int l1MaximumSize
  ) {
  }

  public record L1Config(
    @DefaultValue("100") int initialCapacity,
    @DefaultValue("1000") int maxSize
  ) {
  }
}
