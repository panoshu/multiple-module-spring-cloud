package com.example.auth.infrastructure.permission.repository;

import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.core.types.UserNo;
import com.mybatisflex.core.query.QueryWrapper;
import com.example.auth.domain.permission.aggregate.PermissionMenu;
import com.example.auth.domain.permission.aggregate.valueobject.PermissionBinding;
import com.example.auth.domain.permission.enumeration.MenuCategory;
import com.example.auth.domain.permission.repository.PermissionMenuRepository;
import com.example.auth.infrastructure.permission.converter.PermissionMenuPoConverter;
import com.example.auth.infrastructure.permission.entity.MenuPermissionBindingDO;
import com.example.auth.infrastructure.permission.entity.PermissionMenuDO;
import com.example.auth.infrastructure.permission.mapper.MenuPermissionBindingMapper;
import com.example.auth.infrastructure.permission.mapper.PermissionMenuMapper;
import com.example.auth.types.MenuCode;
import com.example.auth.types.PermissionMenuId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.example.auth.infrastructure.permission.entity.table.PermissionMenuDOTableDef.PERMISSION_MENU_DO;
import static com.example.auth.infrastructure.permission.entity.table.MenuPermissionBindingDOTableDef.MENU_PERMISSION_BINDING_DO;

/**
 * 权限菜单仓储实现.
 *
 * @author auth-service
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PermissionMenuRepositoryImpl implements PermissionMenuRepository {

  private final PermissionMenuMapper menuMapper;
  private final MenuPermissionBindingMapper bindingMapper;
  private final PermissionMenuPoConverter converter;

  @Override
  public Optional<PermissionMenu> load(PermissionMenuId id) {
    if (id == null) {
      return Optional.empty();
    }
    PermissionMenuDO doObj = menuMapper.selectOneById(id.value());
    if (doObj == null) {
      return Optional.empty();
    }
    List<PermissionBinding> bindings = loadBindingsByMenuId(id.value());
    return Optional.of(converter.toDomain(doObj, bindings));
  }

  @Override
  @Transactional
  public void save(PermissionMenu menu) {
    if (menu == null) {
      throw new IllegalArgumentException("PermissionMenu 不能为空");
    }
    PermissionMenuDO doObj = converter.toDO(menu);
    PermissionMenuDO existing = menuMapper.selectOneById(doObj.getId());

    if (existing == null) {
      menuMapper.insert(doObj);
    } else {
      doObj.setVersion(existing.getVersion());
      menuMapper.update(doObj);
    }

    // 全量替换绑定
    String menuId = menu.id().value();
    bindingMapper.deleteByQuery(QueryWrapper.create()
      .where(MENU_PERMISSION_BINDING_DO.MENU_ID.eq(menuId)));

    List<MenuPermissionBindingDO> bindingDOs = converter.toBindingDOList(menu.bindings(), menuId);
    for (MenuPermissionBindingDO bindingDO : bindingDOs) {
      bindingMapper.insert(bindingDO);
    }

    // 发布领域事件
    menu.domainEvents().forEach(event -> log.debug("领域事件待发布: {}", event));
  }

  @Override
  public void delete(PermissionMenu aggregateRoot) {
    if (aggregateRoot == null) {
      return;
    }
    deleteById(aggregateRoot.id());
  }

  @Override
  public void deleteById(PermissionMenuId id) {
    if (id == null) {
      return;
    }
    // 先删绑定，再删菜单
    bindingMapper.deleteByQuery(QueryWrapper.create()
      .where(MENU_PERMISSION_BINDING_DO.MENU_ID.eq(id.value())));
    menuMapper.deleteById(id.value());
  }

  @Override
  public List<PermissionMenu> loadAll() {
    return menuMapper.selectAll().stream()
      .map(doObj -> converter.toDomain(doObj, loadBindingsByMenuId(doObj.getId())))
      .toList();
  }

  @Override
  public void streamByAppId(PermissionMenuId id, Consumer<AggregateRoot<PermissionMenuId>> processor) {
    if (id == null || processor == null) {
      return;
    }
    load(id).ifPresent(processor);
  }

  @Override
  public List<PermissionMenu> findByCategory(MenuCategory category) {
    QueryWrapper query = QueryWrapper.create()
      .where(PERMISSION_MENU_DO.MENU_CATEGORY.eq(category.name()))
      .and(PERMISSION_MENU_DO.DELETED.eq(false));
    return menuMapper.selectListByQuery(query).stream()
      .map(doObj -> converter.toDomain(doObj, loadBindingsByMenuId(doObj.getId())))
      .toList();
  }

  @Override
  public Optional<PermissionMenu> findByMenuCode(MenuCode menuCode) {
    QueryWrapper query = QueryWrapper.create()
      .where(PERMISSION_MENU_DO.MENU_CODE.eq(menuCode.value()))
      .and(PERMISSION_MENU_DO.DELETED.eq(false));
    PermissionMenuDO doObj = menuMapper.selectOneByQuery(query);
    if (doObj == null) {
      return Optional.empty();
    }
    return Optional.of(converter.toDomain(doObj, loadBindingsByMenuId(doObj.getId())));
  }

  @Override
  public List<PermissionMenu> findByParentId(PermissionMenuId parentId) {
    QueryWrapper query = QueryWrapper.create()
      .where(PERMISSION_MENU_DO.PARENT_ID.eq(parentId.value()))
      .and(PERMISSION_MENU_DO.DELETED.eq(false));
    return menuMapper.selectListByQuery(query).stream()
      .map(doObj -> converter.toDomain(doObj, loadBindingsByMenuId(doObj.getId())))
      .toList();
  }

  @Override
  public List<PermissionMenu> loadAllMenus() {
    return loadAll();
  }

  @Override
  public boolean existsByMenuCode(MenuCode menuCode) {
    QueryWrapper query = QueryWrapper.create()
      .where(PERMISSION_MENU_DO.MENU_CODE.eq(menuCode.value()))
      .and(PERMISSION_MENU_DO.DELETED.eq(false));
    return menuMapper.selectCountByQuery(query) > 0;
  }

  @Override
  public int countByParentId(PermissionMenuId parentId) {
    QueryWrapper query = QueryWrapper.create()
      .where(PERMISSION_MENU_DO.PARENT_ID.eq(parentId.value()))
      .and(PERMISSION_MENU_DO.DELETED.eq(false));
    return (int) menuMapper.selectCountByQuery(query);
  }

  @Override
  public List<PermissionMenu> findByPathPrefix(String pathPrefix) {
    QueryWrapper query = QueryWrapper.create()
      .where(PERMISSION_MENU_DO.MENU_PATH.likeRight(pathPrefix + "/"))
      .and(PERMISSION_MENU_DO.DELETED.eq(false));
    return menuMapper.selectListByQuery(query).stream()
      .map(doObj -> converter.toDomain(doObj, loadBindingsByMenuId(doObj.getId())))
      .toList();
  }

  private List<PermissionBinding> loadBindingsByMenuId(String menuId) {
    QueryWrapper query = QueryWrapper.create()
      .where(MENU_PERMISSION_BINDING_DO.MENU_ID.eq(menuId))
      .and(MENU_PERMISSION_BINDING_DO.DELETED.eq(false));
    return converter.toBindingDomainList(bindingMapper.selectListByQuery(query));
  }
}
