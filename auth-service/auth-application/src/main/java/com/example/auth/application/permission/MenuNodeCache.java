package com.example.auth.application.permission;

import com.example.auth.domain.permission.aggregate.PermissionMenu;
import com.example.auth.domain.permission.enumeration.MenuCategory;
import com.example.shared.cache.core.ICacheTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 菜单节点缓存（AU-03）。
 * <p>通过 {@link ICacheTemplate} 使用 shared-cache 的统一 L1/L2 能力，
 * 不再由 application 层直接持有 Caffeine；TTL 由
 * {@code shared.cache.rules.auth-menu-node.ttl} 配置（默认 30m）。
 * 菜单节点变更时由应用服务主动失效。
 */
@Component
public class MenuNodeCache {

  private static final String CACHE_NAME = "auth-menu-node";

  private final ICacheTemplate cacheTemplate;

  public MenuNodeCache(ICacheTemplate cacheTemplate) {
    this.cacheTemplate = cacheTemplate;
  }

  public List<PermissionMenu> get(MenuCategory category) {
    return cacheTemplate.get(CACHE_NAME, category.name(), List.class)
      .map(value -> (List<PermissionMenu>) value)
      .orElse(null);
  }

  public void put(MenuCategory category, List<PermissionMenu> menus) {
    cacheTemplate.put(CACHE_NAME, category.name(), menus);
  }

  public void invalidate(MenuCategory category) {
    cacheTemplate.evict(CACHE_NAME, category.name());
  }

  public void invalidateAll() {
    cacheTemplate.evictAll(CACHE_NAME);
  }
}
