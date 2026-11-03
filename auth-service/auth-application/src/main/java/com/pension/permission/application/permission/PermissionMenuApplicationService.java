package com.pension.permission.application.permission;

import com.example.shared.exception.BusinessException;
import com.example.core.types.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.enumeration.PermissionCategory;
import com.pension.permission.domain.authorization.valueobject.ActionCode;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import com.pension.permission.domain.authorization.valueobject.Permission;
import com.pension.permission.domain.permission.aggregate.PermissionMenu;
import com.pension.permission.domain.permission.aggregate.valueobject.MenuTreeNode;
import com.pension.permission.domain.permission.aggregate.valueobject.PermissionBinding;
import com.pension.permission.domain.permission.enumeration.MenuCategory;
import com.pension.permission.domain.permission.enumeration.MenuVisibility;
import com.pension.permission.domain.permission.errorcode.PermissionMenuError;
import com.pension.permission.domain.permission.repository.PermissionMenuRepository;
import com.pension.permission.domain.permission.service.MenuTreeAssembler;
import com.pension.permission.types.MenuCode;
import com.pension.permission.types.PermissionBindingId;
import com.pension.permission.types.PermissionMenuId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 权限菜单应用服务。
 * <p>管理端 CRUD + 前端可见树查询。
 */
@Service
@RequiredArgsConstructor
public class PermissionMenuApplicationService {

  private final PermissionMenuRepository menuRepository;
  private final PermissionCacheService permissionCacheService;
  private final MenuNodeCache menuNodeCache;

  // ── 前端可见树查询 ──

  /**
   * 获取用户可见的业务菜单树。
   */
  public List<MenuTreeNode> getBusinessTree(UserNo identity, PlanNo planId) {
    List<PermissionMenu> allMenus = loadMenusFromCache(MenuCategory.BUSINESS);
    Set<Permission> userPermissions = permissionCacheService.computeBusinessPermissions(identity, planId);
    return MenuTreeAssembler.assembleVisibleTree(allMenus, userPermissions, MenuCategory::toPermissionCategory);
  }

  /**
   * 获取用户可见的平台菜单树。
   */
  public List<MenuTreeNode> getPlatformTree(UserNo identity) {
    List<PermissionMenu> allMenus = loadMenusFromCache(MenuCategory.PLATFORM);
    Set<Permission> userPermissions = permissionCacheService.computePlatformPermissions(identity);
    return MenuTreeAssembler.assembleVisibleTree(allMenus, userPermissions, MenuCategory::toPermissionCategory);
  }

  // ── 管理端 CRUD ──

  /**
   * 查询菜单节点列表（扁平，含绑定）。
   */
  public List<PermissionMenu> listMenus(MenuCategory category) {
    if (category == null) {
      return menuRepository.loadAllMenus();
    }
    return menuRepository.findByCategory(category);
  }

  /**
   * 查询单个菜单节点。
   */
  public Optional<PermissionMenu> getMenu(PermissionMenuId menuId) {
    return menuRepository.load(menuId);
  }

  /**
   * 创建菜单节点。
   */
  @Transactional
  public PermissionMenuId createMenu(
    MenuCode menuCode, String menuName, PermissionMenuId parentId,
    int sortOrder, String icon, String route,
    MenuCategory menuCategory, MenuVisibility visibility, UserNo creator
  ) {
    if (menuRepository.existsByMenuCode(menuCode)) {
      throw new BusinessException(PermissionMenuError.DUPLICATE_MENU_CODE);
    }

    // 解析深度和物化路径（需查父节点）
    int depth = 0;
    String menuPath = "/" + menuName;
    if (parentId != null) {
      PermissionMenu parent = menuRepository.load(parentId)
        .orElseThrow(() -> new BusinessException(PermissionMenuError.MENU_NOT_FOUND));
      depth = parent.depth() + 1;
      menuPath = parent.menuPath() + "/" + menuName;
    }

    PermissionMenu menu = PermissionMenu.create(
      menuCode, menuName, parentId, depth, sortOrder,
      icon, route, menuCategory, visibility, creator);
    menuRepository.save(menu);

    invalidateCache(menuCategory);
    return menu.id();
  }

  /**
   * 更新菜单显示信息。
   */
  @Transactional
  public void updateMenu(PermissionMenuId menuId, String menuName, String icon,
                          String route, int sortOrder, UserNo updater) {
    PermissionMenu menu = menuRepository.load(menuId)
      .orElseThrow(() -> new BusinessException(PermissionMenuError.MENU_NOT_FOUND));
    menu.updateDisplayInfo(menuName, icon, route, sortOrder, updater);
    menuRepository.save(menu);

    invalidateCache(menu.menuCategory());
  }

  /**
   * 移动菜单节点到新父节点。
   * <p>同时更新所有后代节点的 depth 和 menuPath，并防止循环引用。
   */
  @Transactional
  public void moveMenu(PermissionMenuId menuId, PermissionMenuId newParentId, int sortOrder, UserNo updater) {
    PermissionMenu menu = menuRepository.load(menuId)
      .orElseThrow(() -> new BusinessException(PermissionMenuError.MENU_NOT_FOUND));

    // 记录旧路径和深度，用于子树更新
    String oldPathPrefix = menu.menuPath();
    int oldDepth = menu.depth();

    int newDepth = 0;
    String newMenuPath = "/" + menu.menuName();
    if (newParentId != null) {
      // 防止将节点移动到自身
      if (newParentId.equals(menuId)) {
        throw new BusinessException(PermissionMenuError.CIRCULAR_MOVE);
      }

      PermissionMenu newParent = menuRepository.load(newParentId)
        .orElseThrow(() -> new BusinessException(PermissionMenuError.MENU_NOT_FOUND));

      // 防止循环引用：新父节点不能是当前节点的后代
      if (oldPathPrefix != null && newParent.menuPath() != null
        && newParent.menuPath().startsWith(oldPathPrefix + "/")) {
        throw new BusinessException(PermissionMenuError.CIRCULAR_MOVE);
      }

      newDepth = newParent.depth() + 1;
      newMenuPath = newParent.menuPath() + "/" + menu.menuName();
    }

    menu.moveTo(newParentId, newDepth, newMenuPath, sortOrder, updater);
    menuRepository.save(menu);

    // 更新子树的 depth 和 menuPath
    if (oldPathPrefix != null && (oldDepth != newDepth || !oldPathPrefix.equals(newMenuPath))) {
      int depthDelta = newDepth - oldDepth;
      List<PermissionMenu> descendants = menuRepository.findByPathPrefix(oldPathPrefix);
      for (PermissionMenu descendant : descendants) {
        descendant.adjustPathAndDepth(oldPathPrefix, newMenuPath, depthDelta, updater);
        menuRepository.save(descendant);
      }
    }

    invalidateCache(menu.menuCategory());
  }

  /**
   * 删除菜单节点（需无子节点）。
   */
  @Transactional
  public void deleteMenu(PermissionMenuId menuId, UserNo deleter) {
    PermissionMenu menu = menuRepository.load(menuId)
      .orElseThrow(() -> new BusinessException(PermissionMenuError.MENU_NOT_FOUND));

    int childCount = menuRepository.countByParentId(menuId);
    if (childCount > 0) {
      throw new BusinessException(PermissionMenuError.MENU_HAS_CHILDREN);
    }

    menuRepository.delete(menu);
    invalidateCache(menu.menuCategory());
  }

  /**
   * 替换菜单的权限绑定（全量替换）。
   */
  @Transactional
  public void replaceBindings(PermissionMenuId menuId, List<PermissionBinding> newBindings, UserNo updater) {
    PermissionMenu menu = menuRepository.load(menuId)
      .orElseThrow(() -> new BusinessException(PermissionMenuError.MENU_NOT_FOUND));
    menu.replaceBindings(newBindings, updater);
    menuRepository.save(menu);

    invalidateCache(menu.menuCategory());
  }

  /**
   * 切换菜单启停。
   */
  @Transactional
  public void toggleMenu(PermissionMenuId menuId, UserNo updater) {
    PermissionMenu menu = menuRepository.load(menuId)
      .orElseThrow(() -> new BusinessException(PermissionMenuError.MENU_NOT_FOUND));
    menu.toggleEnabled(updater);
    menuRepository.save(menu);

    invalidateCache(menu.menuCategory());
  }

  // ── 内部辅助 ──

  private List<PermissionMenu> loadMenusFromCache(MenuCategory category) {
    List<PermissionMenu> cached = menuNodeCache.get(category);
    if (cached != null) {
      return cached;
    }
    List<PermissionMenu> menus = menuRepository.findByCategory(category);
    menuNodeCache.put(category, menus);
    return menus;
  }

  private void invalidateCache(MenuCategory category) {
    menuNodeCache.invalidate(category);
  }
}
