package com.example.shared.cache.core;

import com.example.shared.lock.DistributedLockFactory;
import com.example.shared.cache.properties.SharedCacheProperties;
import com.example.shared.cache.properties.SharedCacheProperties.CacheRule;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.cache.Cache;
import org.springframework.cache.support.AbstractCacheManager;

import java.util.Collection;
import java.util.Collections;

@Slf4j
public class DynamicCacheManager extends AbstractCacheManager {

  private final SharedCacheProperties properties;
  private final ICacheFactory l1Factory;
  private final ICacheFactory l2Factory;
  private final CacheSyncPolicy syncPolicy;
  private final DistributedLockFactory lockFactory;

  public DynamicCacheManager(SharedCacheProperties properties,
                             ICacheFactory l1Factory,
                             ICacheFactory l2Factory,
                             CacheSyncPolicy syncPolicy,
                             DistributedLockFactory lockFactory) {
    this.properties = properties;
    this.l1Factory = l1Factory;
    this.l2Factory = l2Factory;
    this.syncPolicy = syncPolicy;
    this.lockFactory = lockFactory;
  }

  @Override
  @NonNull
  protected Collection<? extends Cache> loadCaches() {
    return Collections.emptyList();
  }

  @Override
  protected Cache getMissingCache(@NonNull String name) {
    // 1. Resolve Rule
    CacheRule rule = properties.rules().get(name);
    if (rule == null) {
      rule = properties.defaultRule();
    }

    // 2. Resolve Mode
    SharedCacheProperties.CacheMode mode = rule.mode() != null ? rule.mode() : properties.mode();

    log.info("Creating Cache [{}] mode={}", name, mode);

    // 3. Create Cache based on Mode
    if (mode == SharedCacheProperties.CacheMode.L1_ONLY) {
      return l1Factory.createL1Cache(name, rule, properties);
    } else if (mode == SharedCacheProperties.CacheMode.L2_ONLY) {
      if (l2Factory == null) {
        throw new IllegalStateException("L2_ONLY requested but Redis is not configured");
      }
      return l2Factory.createL2Cache(name, rule, properties);
    } else { // MULTI
      if (l2Factory == null) {
        log.warn("MULTI mode requested for [{}] but Redis missing. Downgrading to L1_ONLY", name);
        return l1Factory.createL1Cache(name, rule, properties);
      }
      Cache l1 = l1Factory.createL1Cache(name, rule, properties);
      Cache l2 = l2Factory.createL2Cache(name, rule, properties);
      return new LayeredCache(name, l1, l2, syncPolicy, rule.cacheNullValues(), lockFactory);
    }
  }
}
