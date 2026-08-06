package com.pension.permission.domain.permission.aggregate;

import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.enumeration.PermissionCategory;
import com.pension.permission.domain.authorization.valueobject.ActionCode;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import com.pension.permission.domain.permission.enumeration.PermissionItemSource;
import com.pension.permission.domain.permission.event.PermissionItemCreated;
import com.pension.permission.types.PermissionItemId;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 权限点元数据聚合根。
 * <p>每个 @RequirePermission 注解对应一个 PermissionItem，唯一键为 (businessCode, actionCode)。
 * 元数据字段（displayName/categoryGroup/sortOrder）可由管理后台补充，
 * 扫描器仅覆盖来源字段（controller/method/httpMethod/path），不覆盖人工补充的字段。
 */
public class PermissionItem extends AggregateRoot<PermissionItemId> {

  private final BusinessCode businessCode;
  private final ActionCode actionCode;
  private final PermissionCategory category;
  private final PermissionItemSource source;
  private final String controller;
  private final String method;
  private final String httpMethod;
  private final String path;

  private String displayName;
  private String description;
  private String categoryGroup;
  private int sortOrder;
  private boolean autoRegistered;

  private PermissionItem(
    PermissionItemId id, UserNo creator, BusinessCode businessCode, ActionCode actionCode,
    PermissionCategory category, PermissionItemSource source, String controller,
    String method, String httpMethod, String path
  ) {
    super(id, creator);
    this.businessCode = Objects.requireNonNull(businessCode, "businessCode");
    this.actionCode = actionCode;
    this.category = Objects.requireNonNull(category, "category");
    this.source = Objects.requireNonNull(source, "source");
    this.controller = controller;
    this.method = method;
    this.httpMethod = httpMethod;
    this.path = path;
    this.autoRegistered = (source == PermissionItemSource.API);
    this.validateInvariants();
    this.registerDomainEvent(PermissionItemCreated.of(this.id(), creator));
  }

  private PermissionItem(
    PermissionItemId id, UserNo createdBy, UserNo updatedBy,
    LocalDateTime createdAt, LocalDateTime updatedAt, Version version,
    BusinessCode businessCode, ActionCode actionCode, PermissionCategory category,
    PermissionItemSource source, String controller, String method, String httpMethod,
    String path, String displayName, String description, String categoryGroup,
    int sortOrder, boolean autoRegistered
  ) {
    super(id, createdBy, updatedBy, createdAt, updatedAt, version);
    this.businessCode = businessCode;
    this.actionCode = actionCode;
    this.category = category;
    this.source = source;
    this.controller = controller;
    this.method = method;
    this.httpMethod = httpMethod;
    this.path = path;
    this.displayName = displayName;
    this.description = description;
    this.categoryGroup = categoryGroup;
    this.sortOrder = sortOrder;
    this.autoRegistered = autoRegistered;
    this.validateInvariants();
  }

  public static PermissionItem create(
    String businessCode, String actionCode, PermissionCategory category,
    PermissionItemSource source, String controller, String method,
    String httpMethod, String path, UserNo creator
  ) {
    return new PermissionItem(
      new PermissionItemId(java.util.UUID.randomUUID().toString()),
      creator,
      new BusinessCode(businessCode),
      actionCode == null || actionCode.isEmpty() ? null : new ActionCode(actionCode),
      category, source, controller, method, httpMethod, path);
  }

  public static PermissionItem reconstitute(
    String id, String businessCode, String actionCode, PermissionCategory category,
    PermissionItemSource source, String controller, String method, String httpMethod,
    String path, String displayName, String categoryGroup, int sortOrder,
    boolean autoRegistered, UserNo createdBy, UserNo updatedBy,
    LocalDateTime createdAt, LocalDateTime updatedAt
  ) {
    return new PermissionItem(
      new PermissionItemId(id), createdBy, updatedBy, createdAt, updatedAt, null,
      new BusinessCode(businessCode),
      actionCode == null ? null : new ActionCode(actionCode),
      category, source, controller, method, httpMethod, path,
      displayName, null, categoryGroup, sortOrder, autoRegistered);
  }

  public void updateMetadata(String displayName, String categoryGroup, int sortOrder, UserNo updater) {
    this.displayName = displayName;
    this.categoryGroup = categoryGroup;
    this.sortOrder = sortOrder;
    this.markUpdated(updater);
  }

  public void markStale(UserNo scanner) {
    this.autoRegistered = false;
    this.markUpdated(scanner);
  }

  public BusinessCode businessCode() { return businessCode; }
  public ActionCode actionCode() { return actionCode; }
  public PermissionCategory category() { return category; }
  public PermissionItemSource source() { return source; }
  public String controller() { return controller; }
  public String method() { return method; }
  public String httpMethod() { return httpMethod; }
  public String path() { return path; }
  public String displayName() { return displayName; }
  public String description() { return description; }
  public String categoryGroup() { return categoryGroup; }
  public int sortOrder() { return sortOrder; }
  public boolean autoRegistered() { return autoRegistered; }

  @Override
  protected void validateInvariants() {
    if (this.businessCode == null) {
      throw new IllegalArgumentException("businessCode cannot be null");
    }
    if (this.category == null) {
      throw new IllegalArgumentException("category cannot be null");
    }
    if (this.source == null) {
      throw new IllegalArgumentException("source cannot be null");
    }
  }
}
