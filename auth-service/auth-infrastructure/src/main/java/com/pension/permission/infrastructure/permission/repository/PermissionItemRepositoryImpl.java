package com.pension.permission.infrastructure.permission.repository;

import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.identifier.id.UserNo;
import com.mybatisflex.core.query.QueryWrapper;
import com.pension.permission.domain.authorization.enumeration.PermissionCategory;
import com.pension.permission.domain.authorization.valueobject.ActionCode;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import com.pension.permission.domain.permission.aggregate.PermissionItem;
import com.pension.permission.domain.permission.repository.PermissionItemRepository;
import com.pension.permission.infrastructure.permission.converter.PermissionItemConverter;
import com.pension.permission.infrastructure.permission.entity.PermissionItemDO;
import com.pension.permission.infrastructure.permission.mapper.PermissionItemMapper;
import com.pension.permission.types.PermissionItemId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.pension.permission.infrastructure.permission.entity.table.PermissionItemDOTableDef.PERMISSION_ITEM_DO;

/**
 * 权限点元数据仓储实现。
 * <p>基于 MyBatis-Flex 持久化 {@link PermissionItem} 聚合根，
 * 提供 upsert 与 stale 标记能力以支持 {@code PermissionScanner} 自动发现流程。
 *
 * <p>注意：{@link PermissionItem#reconstitute} 重建的对象 version 为 null，
 * 因此不能对其调用 {@code markStale/markUpdated}（会触发 NPE）。
 * 本实现通过重新 reconstitute 一个 autoRegistered=false 的对象来标记 stale，
 * 由 {@link #save} 在更新时从数据库现有记录补全 version 以支持乐观锁。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PermissionItemRepositoryImpl implements PermissionItemRepository {

  private final PermissionItemMapper mapper;
  private final PermissionItemConverter converter;

  @Override
  public Optional<PermissionItem> load(PermissionItemId id) {
    if (id == null) {
      return Optional.empty();
    }
    PermissionItemDO doObj = mapper.selectOneById(id.value());
    return Optional.ofNullable(converter.toDomain(doObj));
  }

  @Override
  public void save(PermissionItem item) {
    if (item == null) {
      throw new IllegalArgumentException("PermissionItem 不能为空");
    }
    PermissionItemDO doObj = converter.toDO(item);
    PermissionItemDO existing = mapper.selectOneById(doObj.getId());
    if (existing == null) {
      mapper.insert(doObj);
    } else {
      doObj.setVersion(existing.getVersion());
      mapper.update(doObj);
    }
  }

  @Override
  public void delete(PermissionItem aggregateRoot) {
    if (aggregateRoot == null) {
      return;
    }
    mapper.deleteById(aggregateRoot.id().value());
  }

  @Override
  public void deleteById(PermissionItemId id) {
    if (id == null) {
      return;
    }
    mapper.deleteById(id.value());
  }

  @Override
  public List<PermissionItem> loadAll() {
    return mapper.selectAll().stream().map(converter::toDomain).toList();
  }

  @Override
  public void streamByAppId(PermissionItemId id, Consumer<AggregateRoot<PermissionItemId>> processor) {
    if (id == null || processor == null) {
      return;
    }
    load(id).ifPresent(processor);
  }

  @Override
  public List<PermissionItem> findByCategory(PermissionCategory category) {
    QueryWrapper query = QueryWrapper.create()
      .where(PERMISSION_ITEM_DO.CATEGORY.eq(category.name()))
      .and(PERMISSION_ITEM_DO.DELETED.eq(false));
    return mapper.selectListByQuery(query).stream()
      .map(converter::toDomain)
      .toList();
  }

  @Override
  public Optional<PermissionItem> findByBusinessAndAction(BusinessCode business, ActionCode action) {
    QueryWrapper query = QueryWrapper.create()
      .where(PERMISSION_ITEM_DO.BUSINESS_CODE.eq(business.value()))
      .and(PERMISSION_ITEM_DO.DELETED.eq(false));
    if (action == null) {
      query.and(PERMISSION_ITEM_DO.ACTION_CODE.isNull());
    } else {
      query.and(PERMISSION_ITEM_DO.ACTION_CODE.eq(action.value()));
    }
    PermissionItemDO doObj = mapper.selectOneByQuery(query);
    return Optional.ofNullable(converter.toDomain(doObj));
  }

  @Override
  public Optional<PermissionCategory> findCategory(BusinessCode business, ActionCode action) {
    return findByBusinessAndAction(business, action)
      .map(PermissionItem::category);
  }

  @Override
  public List<PermissionItem> loadAllItems() {
    return loadAll();
  }

  @Override
  public void upsertAll(List<PermissionItem> items, UserNo scanner) {
    for (PermissionItem item : items) {
      Optional<PermissionItem> existing = findByBusinessAndAction(item.businessCode(), item.actionCode());
      if (existing.isEmpty()) {
        save(item);
        log.debug("新增权限点: business={}, action={}", item.businessCode().value(),
          item.actionCode() != null ? item.actionCode().value() : "(whole)");
      } else {
        PermissionItem persisted = existing.get();
        PermissionItem merged = PermissionItem.reconstitute(
          persisted.id().value(),
          item.businessCode().value(),
          item.actionCode() != null ? item.actionCode().value() : null,
          item.category(),
          item.source(),
          item.controller(), item.method(), item.httpMethod(), item.path(),
          persisted.displayName(), persisted.categoryGroup(), persisted.sortOrder(),
          persisted.autoRegistered(),
          persisted.createdBy(), scanner,
          persisted.createdAt(), LocalDateTime.now());
        save(merged);
        log.debug("更新权限点来源字段: business={}, action={}", item.businessCode().value(),
          item.actionCode() != null ? item.actionCode().value() : "(whole)");
      }
    }
  }

  @Override
  public void markStaleForUnscanned(Set<PermissionItemId> scannedIds, UserNo scanner) {
    QueryWrapper query = QueryWrapper.create()
      .where(PERMISSION_ITEM_DO.AUTO_REGISTERED.eq(true))
      .and(PERMISSION_ITEM_DO.DELETED.eq(false));
    if (scannedIds != null && !scannedIds.isEmpty()) {
      Set<String> idValues = scannedIds.stream().map(PermissionItemId::value).collect(Collectors.toSet());
      query.and(PERMISSION_ITEM_DO.ID.notIn(idValues));
    }
    List<PermissionItemDO> staleList = mapper.selectListByQuery(query);
    for (PermissionItemDO staleDo : staleList) {
      PermissionItem stale = converter.toDomain(staleDo);
      PermissionItem marked = PermissionItem.reconstitute(
        stale.id().value(),
        stale.businessCode().value(),
        stale.actionCode() != null ? stale.actionCode().value() : null,
        stale.category(),
        stale.source(),
        stale.controller(), stale.method(), stale.httpMethod(), stale.path(),
        stale.displayName(), stale.categoryGroup(), stale.sortOrder(),
        false,
        stale.createdBy(), scanner,
        stale.createdAt(), LocalDateTime.now());
      save(marked);
      log.warn("权限点标记为 stale: id={}, business={}", staleDo.getId(), staleDo.getBusinessCode());
    }
  }
}
