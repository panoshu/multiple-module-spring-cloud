package com.example.auth.domain.permission.aggregate;

import com.example.shared.exception.CommonError;
import com.example.shared.exception.DomainException;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.core.types.UserNo;
import com.example.auth.domain.authorization.valueobject.ActionCode;
import com.example.auth.domain.authorization.valueobject.BusinessCode;
import com.example.auth.domain.permission.aggregate.valueobject.PermissionBinding;
import com.example.auth.domain.permission.enumeration.MenuCategory;
import com.example.auth.domain.permission.enumeration.MenuVisibility;
import com.example.auth.domain.permission.event.PermissionMenuCreated;
import com.example.auth.domain.permission.event.PermissionMenuMoved;
import com.example.auth.types.MenuCode;
import com.example.auth.types.PermissionBindingId;
import com.example.auth.types.PermissionMenuId;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 权限菜单聚合根。
 * <p>管理端维护的树形菜单节点，与自动扫描的 {@link PermissionItem} 生命周期分离。
 * 每个节点可选地绑定若干权限点，通过 {@link MenuVisibility} 规则控制可见性。
 */
public class PermissionMenu extends AggregateRoot<PermissionMenuId> {

  private final MenuCode menuCode;
  private String menuName;
  private String menuPath;
  private PermissionMenuId parentId;
  private int depth;
  private int sortOrder;
  private String icon;
  private String route;
  private final MenuCategory menuCategory;
  private MenuVisibility visibility;
  private boolean enabled;
  private List<PermissionBinding> bindings;

  private PermissionMenu(
    PermissionMenuId id, UserNo creator, MenuCode menuCode,
    String menuName, PermissionMenuId parentId, int depth,
    int sortOrder, String icon, String route,
    MenuCategory menuCategory, MenuVisibility visibility
  ) {
    super(id, creator.value());
    this.menuCode = Objects.requireNonNull(menuCode, "menuCode");
    this.menuName = Objects.requireNonNull(menuName, "menuName");
    this.parentId = parentId;
    this.depth = depth;
    this.sortOrder = sortOrder;
    this.icon = icon;
    this.route = route;
    this.menuCategory = Objects.requireNonNull(menuCategory, "menuCategory");
    this.visibility = Objects.requireNonNull(visibility, "visibility");
    this.enabled = true;
    this.bindings = new ArrayList<>();
    this.validateInvariants();
    this.registerDomainEvent(PermissionMenuCreated.of(this.id(), this.menuCode, creator));
  }

  private PermissionMenu(
    PermissionMenuId id, String createdBy, String updatedBy,
    LocalDateTime createdAt, LocalDateTime updatedAt, Version version,
    MenuCode menuCode, String menuName, String menuPath,
    PermissionMenuId parentId, int depth, int sortOrder,
    String icon, String route, MenuCategory menuCategory,
    MenuVisibility visibility, boolean enabled,
    List<PermissionBinding> bindings
  ) {
    super(id, createdBy, updatedBy, createdAt, updatedAt, version);
    this.menuCode = menuCode;
    this.menuName = menuName;
    this.menuPath = menuPath;
    this.parentId = parentId;
    this.depth = depth;
    this.sortOrder = sortOrder;
    this.icon = icon;
    this.route = route;
    this.menuCategory = menuCategory;
    this.visibility = visibility;
    this.enabled = enabled;
    this.bindings = bindings != null ? new ArrayList<>(bindings) : new ArrayList<>();
    this.validateInvariants();
  }

  public static PermissionMenu create(
    MenuCode menuCode, String menuName, PermissionMenuId parentId,
    int depth, int sortOrder, String icon, String route,
    MenuCategory menuCategory, MenuVisibility visibility, UserNo creator
  ) {
    return new PermissionMenu(
      new PermissionMenuId(java.util.UUID.randomUUID().toString()),
      creator, menuCode, menuName, parentId, depth,
      sortOrder, icon, route, menuCategory, visibility);
  }

  public static PermissionMenu reconstitute(
    String id, String menuCode, String menuName, String menuPath,
    String parentId, int depth, int sortOrder, String icon, String route,
    MenuCategory menuCategory, MenuVisibility visibility, boolean enabled,
    List<PermissionBinding> bindings,
    String createdBy, String updatedBy,
    LocalDateTime createdAt, LocalDateTime updatedAt
  ) {
    return new PermissionMenu(
      new PermissionMenuId(id), createdBy, updatedBy,
      createdAt, updatedAt, null,
      new MenuCode(menuCode), menuName, menuPath,
      parentId != null ? new PermissionMenuId(parentId) : null,
      depth, sortOrder, icon, route,
      menuCategory, visibility, enabled, bindings);
  }

  public void updateDisplayInfo(String menuName, String icon, String route, int sortOrder, UserNo updater) {
    this.menuName = Objects.requireNonNull(menuName, "menuName");
    this.icon = icon;
    this.route = route;
    this.sortOrder = sortOrder;
    this.markUpdated(updater.value());
  }

  public void updateVisibility(MenuVisibility visibility, UserNo updater) {
    this.visibility = Objects.requireNonNull(visibility, "visibility");
    this.markUpdated(updater.value());
  }

  public void moveTo(PermissionMenuId newParentId, int newDepth, String newMenuPath, int newSortOrder, UserNo updater) {
    this.parentId = newParentId;
    this.depth = newDepth;
    this.menuPath = newMenuPath;
    this.sortOrder = newSortOrder;
    this.markUpdated(updater.value());
    this.registerDomainEvent(PermissionMenuMoved.of(this.id(), this.menuCode, newParentId, updater));
  }

  public void replaceBindings(List<PermissionBinding> newBindings, UserNo updater) {
    this.bindings = new ArrayList<>(Objects.requireNonNull(newBindings, "newBindings"));
    this.markUpdated(updater.value());
  }

  public void addBinding(BusinessCode businessCode, ActionCode actionCode, UserNo updater) {
    PermissionBinding binding = new PermissionBinding(
      new PermissionBindingId(java.util.UUID.randomUUID().toString()),
      businessCode, actionCode);
    this.bindings.add(binding);
    this.markUpdated(updater.value());
  }

  public void removeBinding(PermissionBindingId bindingId, UserNo updater) {
    this.bindings.removeIf(b -> b.bindingId().equals(bindingId));
    this.markUpdated(updater.value());
  }

  public void toggleEnabled(UserNo updater) {
    this.enabled = !this.enabled;
    this.markUpdated(updater.value());
  }

  /**
   * 调整子树节点的路径和深度。
   * <p>在父节点移动后由应用服务调用，维护子树的一致性。
   *
   * @param oldPathPrefix 父节点移动前的 menuPath
   * @param newPathPrefix 父节点移动后的 menuPath
   * @param depthDelta   深度偏移量（新深度 - 旧深度）
   * @param updater      操作人
   */
  public void adjustPathAndDepth(String oldPathPrefix, String newPathPrefix, int depthDelta, UserNo updater) {
    Objects.requireNonNull(oldPathPrefix, "oldPathPrefix");
    Objects.requireNonNull(newPathPrefix, "newPathPrefix");
    if (this.menuPath == null || !this.menuPath.startsWith(oldPathPrefix)) {
      throw new DomainException(CommonError.BAD_REQUEST)
        .withUserDetail("menuPath 必须以 oldPathPrefix 开头")
        .withLogDetail(this.menuPath + " vs " + oldPathPrefix);
    }
    this.menuPath = newPathPrefix + this.menuPath.substring(oldPathPrefix.length());
    this.depth = this.depth + depthDelta;
    this.markUpdated(updater.value());
    this.validateInvariants();
  }

  public MenuCode menuCode() {
    return menuCode;
  }

  public String menuName() {
    return menuName;
  }

  public String menuPath() {
    return menuPath;
  }

  public PermissionMenuId parentId() {
    return parentId;
  }

  public int depth() {
    return depth;
  }

  public int sortOrder() {
    return sortOrder;
  }

  public String icon() {
    return icon;
  }

  public String route() {
    return route;
  }

  public MenuCategory menuCategory() {
    return menuCategory;
  }

  public MenuVisibility visibility() {
    return visibility;
  }

  public boolean enabled() {
    return enabled;
  }

  public List<PermissionBinding> bindings() {
    return Collections.unmodifiableList(bindings);
  }

  @Override
  protected void validateInvariants() {
    if (this.menuCode == null) {
      throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("menuCode cannot be null");
    }
    if (this.menuName == null || this.menuName.isBlank()) {
      throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("menuName cannot be blank");
    }
    if (this.menuCategory == null) {
      throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("menuCategory cannot be null");
    }
    if (this.visibility == null) {
      throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("visibility cannot be null");
    }
    if (this.depth < 0) {
      throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("depth cannot be negative");
    }
  }
}
