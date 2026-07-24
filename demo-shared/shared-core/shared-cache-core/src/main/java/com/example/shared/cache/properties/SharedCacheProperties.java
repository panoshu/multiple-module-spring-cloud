package com.example.shared.cache.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "shared.cache")
public record SharedCacheProperties(
  @DefaultValue("MULTI") CacheMode mode,
  @DefaultValue("JACKSON") SerializerType serializerType,
  @DefaultValue("shared:cache:evict:topic") String topic,
  @DefaultValue CacheRule defaultRule,
  Map<String, CacheRule> rules,

  // 【修正1】补回 allowedPackages，用于 Jackson 白名单
  @DefaultValue({"java.util.", "java.lang.", "com.example."}) List<String> allowedPackages,

  @DefaultValue L1Config l1
) {
  public SharedCacheProperties {
    if (rules == null) {
      rules = new HashMap<>();
    }
  }

  public enum CacheMode {L1_ONLY, L2_ONLY, MULTI}

  public enum SerializerType {JACKSON, FURY}

  public record CacheRule(
    @DefaultValue("1h") Duration ttl,
    CacheMode mode,
    @DefaultValue("100") int l1InitialCapacity,
    @DefaultValue("1000") int l1MaximumSize,
    @DefaultValue("true") boolean cacheNullValues,
    @DefaultValue("true") boolean useKeyPrefix
  ) {
  }

  public record L1Config(
    @DefaultValue("100") int initialCapacity,
    @DefaultValue("1000") int maxSize
  ) {
  }
}
