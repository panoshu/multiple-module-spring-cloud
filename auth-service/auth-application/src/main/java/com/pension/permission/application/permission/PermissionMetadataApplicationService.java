package com.pension.permission.application.permission;

import com.pension.permission.domain.authorization.enumeration.PermissionCategory;
import com.pension.permission.domain.permission.aggregate.PermissionItem;
import com.pension.permission.domain.permission.repository.PermissionItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 权限元数据查询应用服务。
 * <p>供权限配置页面调用，提供权限点列表查询（扁平 / 分组两种视图）。
 */
@Service
@RequiredArgsConstructor
public class PermissionMetadataApplicationService {

  private final PermissionItemRepository permissionItemRepository;

  /**
   * 查询权限点列表（扁平）。
   */
  public List<PermissionItem> listItems(PermissionCategory category) {
    if (category == null) {
      return permissionItemRepository.loadAllItems();
    }
    return permissionItemRepository.findByCategory(category);
  }

  /**
   * 查询权限点列表（按 categoryGroup 分组）。
   */
  public Map<String, List<PermissionItem>> listGroupedItems(PermissionCategory category) {
    List<PermissionItem> items = listItems(category);
    return items.stream()
      .collect(Collectors.groupingBy(
        item -> item.categoryGroup() != null ? item.categoryGroup() : "(未分组)"));
  }
}
