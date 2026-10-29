package com.pension.permission.application.permission;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.pension.permission.domain.permission.aggregate.PermissionMenu;
import com.pension.permission.domain.permission.enumeration.MenuCategory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 菜单节点 Caffeine 本地缓存。
 * <p>缓存全量菜单节点（按类别），菜单节点变更时由应用服务主动失效。
 * 有效期 30 分钟，避免频繁查库。
 */
@Component
public class MenuNodeCache {

  private final Cache<MenuCategory, List<PermissionMenu>> cache = Caffeine.newBuilder()
    .expireAfterWrite(Duration.ofMinutes(30))
    .maximumSize(10)
    .build();

  public List<PermissionMenu> get(MenuCategory category) {
    return cache.getIfPresent(category);
  }

  public void put(MenuCategory category, List<PermissionMenu> menus) {
    cache.put(category, menus);
  }

  public void invalidate(MenuCategory category) {
    cache.invalidate(category);
  }

  public void invalidateAll() {
    cache.invalidateAll();
  }
}
