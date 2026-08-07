package com.pension.permission.domain.permission.repository;

import com.example.shared.domain.repository.Repository;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.enumeration.PermissionCategory;
import com.pension.permission.domain.authorization.valueobject.ActionCode;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import com.pension.permission.domain.permission.aggregate.PermissionItem;
import com.pension.permission.types.PermissionItemId;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 权限点元数据仓储端口。基础设施层提供 MyBatis-Flex 实现。
 */
public interface PermissionItemRepository extends Repository<PermissionItem, PermissionItemId> {

  List<PermissionItem> findByCategory(PermissionCategory category);

  Optional<PermissionItem> findByBusinessAndAction(BusinessCode business, ActionCode action);

  Optional<PermissionCategory> findCategory(BusinessCode business, ActionCode action);

  List<PermissionItem> loadAllItems();

  /**
   * 批量 upsert 权限点。
   *
   * @param items   权限点列表
   * @param scanner 扫描者标识
   * @return 实际新增或更新的数量（与现有记录字段无变化时返回 0）
   */
  int upsertAll(List<PermissionItem> items, UserNo scanner);

  void markStaleForUnscanned(Set<PermissionItemId> scannedIds, UserNo scanner);
}
