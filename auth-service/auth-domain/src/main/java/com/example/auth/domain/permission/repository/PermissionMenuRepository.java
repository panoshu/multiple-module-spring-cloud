package com.example.auth.domain.permission.repository;

import com.example.shared.domain.repository.Repository;
import com.example.auth.domain.permission.aggregate.PermissionMenu;
import com.example.auth.domain.permission.enumeration.MenuCategory;
import com.example.auth.types.MenuCode;
import com.example.auth.types.PermissionMenuId;

import java.util.List;
import java.util.Optional;

/**
 * 权限菜单仓储端口。基础设施层提供 MyBatis-Flex 实现。
 */
public interface PermissionMenuRepository extends Repository<PermissionMenu, PermissionMenuId> {

  /**
   * 按菜单类别查询所有菜单节点（扁平列表）。
   */
  List<PermissionMenu> findByCategory(MenuCategory category);

  /**
   * 按菜单编码查询。
   */
  Optional<PermissionMenu> findByMenuCode(MenuCode menuCode);

  /**
   * 查询指定父节点下的所有子节点。
   */
  List<PermissionMenu> findByParentId(PermissionMenuId parentId);

  /**
   * 加载全部菜单节点（扁平列表）。
   */
  List<PermissionMenu> loadAllMenus();

  /**
   * 检查菜单编码是否已存在。
   */
  boolean existsByMenuCode(MenuCode menuCode);

  /**
   * 统计指定父节点下的子节点数量。
   */
  int countByParentId(PermissionMenuId parentId);

  /**
   * 按路径前缀查询所有后代菜单节点（不含自身）。
   * <p>用于菜单移动时批量更新子树的 depth 和 menuPath。
   */
  List<PermissionMenu> findByPathPrefix(String pathPrefix);
}
