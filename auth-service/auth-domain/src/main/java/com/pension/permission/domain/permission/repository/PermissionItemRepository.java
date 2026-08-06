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

  void upsertAll(List<PermissionItem> items, UserNo scanner);

  void markStaleForUnscanned(Set<PermissionItemId> scannedIds, UserNo scanner);
}
