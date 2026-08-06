# 权限元数据注册与统一 Grant 体系改造设计

> 日期：2026-08-06
> 范围：auth-service（com.pension.permission）+ permission-sdk
> 状态：设计待评审

## 一、背景与目标

### 1.1 问题背景

当前 auth-service 的权限模型聚焦于**业务权限**判定（`Permission(businessCode, actionCode)` + 两层 AND + DENY 优先），存在两个缺口：

1. **权限点没有元数据注册机制**：API 上的 `@RequirePermission` 注解声明了权限点，但没有自动发现和注册到元数据表的机制。新增 API 后，权限配置页面无法自动看到新的权限点，需要人工同步。

2. **平台管理功能无法纳入统一体系**：系统管理、用户管理、角色分配、计划/客户配置等平台管理功能天然没有 `planId`，而当前判定链路 `checkPermission(identity, planId, permission)` 强依赖 `planId`（能力层需要 PlanSnapshot，主体层 ScopeMatcher 全部从 PlanSnapshot 取值匹配）。这类功能只能走 Sa-Token 原生 `checkPermission`，与 Grant 体系割裂。

### 1.2 设计目标

- **统一权限模型**：业务权限和平台管理权限都纳入 Grant 体系，共用 Grant / RoleTemplate / ScopeRule / EffectResolver
- **权限点自动注册**：`@RequirePermission` 注解在启动时自动扫描注册到 `permission_item` 元数据表，新增 API 零配置可见
- **权限分区管理**：SessionPermissionCache 分平台权限和业务权限两区，选计划前后权限可见性正确流转
- **不破坏现有安全原则**：DENY 优先、两层 AND、角色模板实时解析等现有机制保持不变

### 1.3 非目标

- 不重构 `Grant` 聚合根结构（复用现有字段，仅新增枚举值）
- 不修改 `AuthorizationEngine` 的两层 AND 判定逻辑（新增平行的平台权限判定路径）
- 不改造网点二次授权的 `PermissionSnapshot`（保持现有 30 秒 TTL 操作授权快照）
- 不实现前端菜单/按钮配置管理界面（仅提供后端权限点集合接口）
- 不引入权限分组/层级树（本期权限点扁平管理，分组后续迭代）

## 二、现状分析

### 2.1 当前权限判定链路

```
checkPermission(identity, planId, permission, at)
  ├─ 能力层：checkPlanCapability(planId, business)
  │   └─ 需要 PlanSnapshot，判断"这个计划开没开通这个业务"
  └─ 主体层：checkSubjectGrant(identity, planId, permission)
      ├─ 拉取 Grant + 解析角色模板虚拟 Grant
      ├─ ScopeMatcher.matches(scopeRules, PlanSnapshot)  ← 全部维度从 PlanSnapshot 取值
      └─ EffectResolver.resolve(matched)  ← DENY 优先
```

**对 planId 的 5 个硬绑定点**：

| # | 绑定点 | 位置 |
|---|--------|------|
| 1 | `checkPermission` 入参 | [EffectivePermissionService:108](file:///d:/WorkSpace/Trae/multiple-module-spring-cloud/auth-service/auth-domain/src/main/java/com/pension/permission/domain/assignment/service/EffectivePermissionService.java) |
| 2 | 能力层需要 PlanSnapshot | `checkPlanCapability` |
| 3 | 主体层 `requirePlan(planId)` | `checkSubjectGrant` 内 |
| 4 | ScopeMatcher 全部维度依赖 PlanSnapshot | [ScopeMatcher](file:///d:/WorkSpace/Trae/multiple-module-spring-cloud/auth-service/auth-domain/src/main/java/com/pension/permission/domain/authorization/service/ScopeMatcher.java) |
| 5 | `ScopeDimension` 枚举无 GLOBAL | [ScopeDimension.java](file:///d:/WorkSpace/Trae/multiple-module-spring-cloud/auth-service/auth-domain/src/main/java/com/pension/permission/domain/authorization/enumeration/ScopeDimension.java) |

### 2.2 维度枚举不一致

| 枚举 | 包 | 值 | 有 GLOBAL |
|------|----|----|-----------|
| `ScopeDimension` | authorization | PLAN, PRODUCT, CUSTOMER, ACCOUNT_MANAGER, OPERATING_MODE | ❌ |
| `RoleTemplateScopeDimension` | role | GLOBAL, CUSTOMER, PRODUCT, PLAN | ✅ |
| `AssignmentScopeDimension` | types | PLAN, CUSTOMER, PRODUCT, GLOBAL | ✅ |

角色模板和身份分配已支持 GLOBAL，但 Grant 的 ScopeRule 还不支持。`EffectivePermissionService.toVirtualGrant` 遇到 GLOBAL 时抛 `UNSUPPORTED_SCOPE_DIMENSION`。

### 2.3 当前权限点声明

[RequirePermission](file:///d:/WorkSpace/Trae/multiple-module-spring-cloud/auth-service/permission-sdk/src/main/java/com/pension/permission/sdk/RequirePermission.java) 注解已存在，标注在 API 方法上，但**没有自动发现机制**，也没有元数据表存储。

## 三、方案概览：三层架构

```
┌─────────────────────────────────────────────────────────┐
│  L1 权限元数据层（新增）                                   │
│  permission_item 表 + @RequirePermission 自动发现          │
│  字段：business + action + category + displayName + ...   │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│  L2 权限判定层（改造）                                     │
│  checkPermission(identity, planId, permission)          │
│    → 能力层(PLAN) + 主体层(ScopeRule匹配PlanSnapshot)    │
│  checkPlatformPermission(identity, permission)           │
│    → 仅主体层(GLOBAL规则恒命中，跳过能力层)               │
│  统一走 Grant + RoleTemplate + DENY优先                  │
└────────────────────────┬────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────┐
│  L3 权限缓存层（新增）                                     │
│  SessionPermissionCache:                                │
│    platformPermissions + businessPermissions + planId   │
│  登录后拉平台权限，选计划后拉业务权限                       │
│  Grant 变更时主动失效                                     │
└─────────────────────────────────────────────────────────┘
```

## 四、详细设计

### 4.1 权限元数据层

#### 4.1.1 PermissionCategory 枚举

新增枚举区分两类权限，存于元数据表，**不进入 Permission 值对象**。

```java
// auth-domain/.../authorization/enumeration/PermissionCategory.java
package com.pension.permission.domain.authorization.enumeration;

public enum PermissionCategory {
  BUSINESS,   // 业务权限：依赖 planId，走能力层+主体层
  PLATFORM    // 平台管理权限：不依赖 planId，仅走主体层
}
```

#### 4.1.2 @RequirePermission 注解扩展

在现有注解上新增 `category` 字段：

```java
// permission-sdk/.../RequirePermission.java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
  String business();
  String action() default "";
  PermissionCategory category() default PermissionCategory.BUSINESS;
}
```

平台管理 API 标注示例：
```java
@RequirePermission(business = "USER_MANAGE", action = "FREEZE", category = PermissionCategory.PLATFORM)
public ApiResult<Void> freezeUser(@Valid @RequestBody FreezeUserRequest request) { ... }
```

#### 4.1.3 PermissionItem 元数据表

```sql
-- schema-pg.sql
CREATE TABLE t_auth_permission_item (
  id              BIGINT       PRIMARY KEY,
  business_code   VARCHAR(64)  NOT NULL,
  action_code     VARCHAR(64),
  category        VARCHAR(16)  NOT NULL,  -- BUSINESS / PLATFORM
  source          VARCHAR(16)  NOT NULL,  -- API / MANUAL
  controller      VARCHAR(255),
  method          VARCHAR(255),
  http_method     VARCHAR(16),
  path            VARCHAR(512),
  display_name    VARCHAR(128),
  description     VARCHAR(512),
  category_group  VARCHAR(64),            -- 分组：用户管理/计划配置/缴费业务...
  sort_order      INT          DEFAULT 0,
  auto_registered BOOLEAN      DEFAULT TRUE,
  created_at      TIMESTAMP    NOT NULL,
  updated_at      TIMESTAMP    NOT NULL,
  UNIQUE (business_code, action_code)
);
CREATE INDEX idx_permission_item_category ON t_auth_permission_item(category);
CREATE INDEX idx_permission_item_group ON t_auth_permission_item(category_group);
```

#### 4.1.4 PermissionScanner 自动发现

启动时扫描所有 `@RequirePermission` 注解，upsert 到 `permission_item` 表：

```java
// auth-infrastructure/.../permission/PermissionScanner.java
@Component
public class PermissionScanner implements ApplicationRunner {
  @Override
  public void run(ApplicationArguments args) {
    Map<String, PermissionItemDO> existing = loadExisting();
    List<PermissionItemDO> discovered = scanAnnotations();
    upsertAll(discovered, existing);
    markStale(discovered, existing);  // 标记已删除的注解对应的记录
  }
}
```

**扫描逻辑**：
- 扫描 `RequestMappingHandlerMapping` 中所有 Controller 方法
- 提取 `@RequirePermission` 注解，组装 `PermissionItemDO`
- upsert：按 `(business_code, action_code)` 唯一键，存在则更新 controller/method/path，不存在则插入
- `auto_registered=true` 的记录若本次扫描未发现，标记为 stale（display_name 仍保留，不删除）

#### 4.1.5 元数据查询接口

```java
// auth-api/.../api/PermissionMetadataApi.java
@HttpExchange("/permission-metadata")
public interface PermissionMetadataApi {
  @GetExchange("/items")
  ApiResult<List<PermissionItemResponse>> listItems(@RequestParam(required = false) String category);

  @GetExchange("/items/grouped")
  ApiResult<List<PermissionGroupResponse>> listGroupedItems(@RequestParam(required = false) String category);
}
```

权限配置页面调用 `listGroupedItems` 拿到按 `category_group` 分组的权限点树，渲染勾选界面。

### 4.2 权限判定层改造

#### 4.2.1 ScopeDimension 增加 GLOBAL

```java
// auth-domain/.../authorization/enumeration/ScopeDimension.java
public enum ScopeDimension {
  PLAN, PRODUCT, CUSTOMER, ACCOUNT_MANAGER, OPERATING_MODE,
  GLOBAL  // 新增：全局范围，不绑定任何具体资源
}
```

#### 4.2.2 ScopeMatcher 支持 GLOBAL

```java
// auth-domain/.../authorization/service/ScopeMatcher.java
public boolean matches(List<ScopeRule> rules, PlanSnapshot plan) {
  if (rules.isEmpty()) {
    return true;  // 无规则=全局匹配（向后兼容）
  }
  for (ScopeRule rule : rules) {
    if (rule.dimension() == ScopeDimension.GLOBAL) {
      continue;  // GLOBAL 规则恒命中，跳过校验
    }
    if (plan == null) {
      return false;  // 非GLOBAL规则但没PlanSnapshot，不匹配
    }
    if (!matchesRule(rule, plan)) {
      return false;
    }
  }
  return true;
}
```

**关键**：`rules.isEmpty() || 全部是GLOBAL` 时不需要 PlanSnapshot 就能匹配。这使得平台管理 Grant（scopeRules 为空或全 GLOBAL）的主体层判定不需要 PlanSnapshot。

#### 4.2.3 EffectivePermissionService 新增平台权限判定

```java
// auth-domain/.../assignment/service/EffectivePermissionService.java

/**
 * 平台管理权限判定：跳过能力层，主体层用GLOBAL规则匹配。
 * 不依赖 planId。
 */
public boolean checkPlatformPermission(UserNo identity, Permission permission, LocalDateTime at) {
  List<Grant> persistedMatched = grantRepository.findCandidateSubjectGrants(identity, at).stream()
    .filter(g -> g.isActiveAt(at))
    .filter(g -> g.subject().covers(identity, membershipLookup))
    .filter(g -> isGlobalScope(g.scopeRules()))  // 仅匹配GLOBAL/空规则
    .filter(g -> g.grants(permission))
    .toList();

  List<Grant> liveMatched = resolveLiveGlobalRoleTemplateGrants(identity, at);

  List<Grant> matched = Stream.concat(persistedMatched.stream(), liveMatched.stream())
    .filter(g -> g.grants(permission))
    .toList();

  return effectResolver.resolve(matched);
}

private boolean isGlobalScope(List<ScopeRule> rules) {
  return rules.isEmpty() || rules.stream().allMatch(r -> r.dimension() == ScopeDimension.GLOBAL);
}

/**
 * 解析GLOBAL范围的角色模板虚拟Grant（scopeRules为空）
 */
private List<Grant> resolveLiveGlobalRoleTemplateGrants(UserNo identity, LocalDateTime at) {
  return assignmentRepository.findActiveByAccount(identity).stream()
    .filter(a -> a.scopeDimension() == AssignmentScopeDimension.GLOBAL)
    .map(a -> toGlobalVirtualGrant(identity, a, at))
    .toList();
}

private Grant toGlobalVirtualGrant(UserNo identity, AgentIdentityAssignment assignment, LocalDateTime at) {
  RoleTemplate template = roleTemplateResolver.resolveOrThrow(
    assignment.scopeDimension(), assignment.scopeValue(), assignment.roleCode());

  // GLOBAL范围虚拟Grant，scopeRules为空，sourcePlanNo/targetPlanNo为null
  GrantSubject subject = new UserListSubject(Set.of(identity));
  return Grant.create(
    idService.nextId(GrantId.class), identity, subject,
    List.of(),  // 空scopeRules，表示全局
    template.permissions(), GrantType.BASE,
    GrantOrigin.ROLE_TEMPLATE, Effect.ALLOW, GrantStatus.EFFECTIVE, ValidityPeriod.sinceNow(),
    null, null  // GLOBAL不绑定计划
  );
}
```

#### 4.2.4 toVirtualGrant 修复 GLOBAL 分支

现有 `toVirtualGrant` 在 `AssignmentScopeDimension.GLOBAL` 时抛异常，改造为生成空 scopeRules：

```java
private Grant toVirtualGrant(UserNo identity, AgentIdentityAssignment assignment, LocalDateTime at) {
  RoleTemplate template = roleTemplateResolver.resolveOrThrow(
    assignment.scopeDimension(), assignment.scopeValue(), assignment.roleCode());

  List<ScopeRule> scopeRules = switch (assignment.scopeDimension()) {
    case PLAN -> List.of(new ScopeRule(ScopeDimension.PLAN, assignment.scopeValue(), assignment.isInheritable()));
    case CUSTOMER -> List.of(new ScopeRule(ScopeDimension.CUSTOMER, assignment.scopeValue(), assignment.isInheritable()));
    case PRODUCT -> List.of(new ScopeRule(ScopeDimension.PRODUCT, assignment.scopeValue(), assignment.isInheritable()));
    case GLOBAL -> List.of();  // 全局角色，空scopeRules
  };
  GrantSubject subject = new UserListSubject(Set.of(identity));

  PlanNo planNo = assignment.scopeDimension() == AssignmentScopeDimension.GLOBAL
    ? null : PlanNo.of(assignment.scopeValue());

  return Grant.create(
    idService.nextId(GrantId.class), identity, subject, scopeRules, template.permissions(),
    GrantType.BASE, GrantOrigin.ROLE_TEMPLATE, Effect.ALLOW, GrantStatus.EFFECTIVE,
    ValidityPeriod.sinceNow(), planNo, planNo
  );
}
```

#### 4.2.5 判定入口分流

应用层根据权限点元数据的 `category` 决定调用哪个判定方法：

```java
// auth-application/.../authorization/PermissionQueryService.java
public boolean check(UserNo identity, PlanNo planId, BusinessCode business, ActionCode action) {
  Permission permission = new Permission(business, action);
  PermissionCategory category = permissionItemRepository.findCategory(business, action);

  return switch (category) {
    case BUSINESS -> effectivePermissionService.checkPermission(identity, planId, permission, LocalDateTime.now());
    case PLATFORM -> effectivePermissionService.checkPlatformPermission(identity, permission, LocalDateTime.now());
  };
}
```

### 4.3 权限缓存层

#### 4.3.1 SessionPermissionCache 值对象

```java
// auth-domain/.../channel/valueobject/SessionPermissionCache.java
public record SessionPermissionCache(
  Set<Permission> platformPermissions,  // 平台管理权限（登录后拉取，不随计划变化）
  Set<Permission> businessPermissions,   // 当前计划下的业务权限（选计划后拉取）
  PlanNo selectedPlanId,                 // 业务权限对应的计划（平台权限为null）
  LocalDateTime cachedAt,
  LocalDateTime expiresAt
) {
  public boolean contains(Permission permission, PermissionCategory category) {
    return switch (category) {
      case PLATFORM -> platformPermissions.contains(permission);
      case BUSINESS -> selectedPlanId != null && businessPermissions.contains(permission);
    };
  }

  public boolean isExpired() {
    return LocalDateTime.now().isAfter(expiresAt);
  }
}
```

#### 4.3.2 拉取接口

后端提供两个原子接口，**组合策略由前端决定**：

```java
// auth-api/.../api/PermissionCacheApi.java
@HttpExchange("/permission-cache")
public interface PermissionCacheApi {
  @GetExchange("/platform")
  ApiResult<Set<PermissionResponse>> getPlatformPermissions();

  @GetExchange("/business")
  ApiResult<Set<PermissionResponse>> getBusinessPermissions(@RequestParam PlanNo planId);
}
```

**各渠道拉取策略（前端控制）**：

| 渠道 | 登录后 | 选计划后 |
|------|--------|---------|
| 网上渠道 | 默认选计划，并发调两个接口 | 切换计划时只调 `/business` |
| 网点渠道 | 二次授权后拉 `/platform` | 选计划后拉 `/business` |
| 总部渠道 | 拉 `/platform` | 用户主动选计划后拉 `/business` |

#### 4.3.3 缓存计算逻辑

```java
// auth-application/.../authorization/PermissionCacheService.java
@Service
public class PermissionCacheService {

  /**
   * 计算平台权限点集合
   */
  public Set<Permission> computePlatformPermissions(UserNo identity) {
    Set<Permission> result = new HashSet<>();
    List<PermissionItem> items = permissionItemRepository.findByCategory(PermissionCategory.PLATFORM);
    for (PermissionItem item : items) {
      Permission perm = new Permission(item.businessCode(), item.actionCode());
      if (effectivePermissionService.checkPlatformPermission(identity, perm, LocalDateTime.now())) {
        result.add(perm);
      }
    }
    return result;
  }

  /**
   * 计算业务权限点集合
   */
  public Set<Permission> computeBusinessPermissions(UserNo identity, PlanNo planId) {
    Set<Permission> result = new HashSet<>();
    List<PermissionItem> items = permissionItemRepository.findByCategory(PermissionCategory.BUSINESS);
    for (PermissionItem item : items) {
      Permission perm = new Permission(item.businessCode(), item.actionCode());
      if (effectivePermissionService.checkPermission(identity, planId, perm, LocalDateTime.now())) {
        result.add(perm);
      }
    }
    return result;
  }
}
```

#### 4.3.4 缓存存储与失效

**存储**：Redis，key 为 `session:perm:{accountId}`，TTL 默认 5 分钟。

**失效**：通过领域事件驱动，Grant 变更时主动清除相关账号的缓存：

```
GrantApproved   → 失效 subject 涉及账号的缓存
GrantRevoked    → 失效 subject 涉及账号的缓存
GrantExpired    → 失效 subject 涉及账号的缓存
RoleTemplateChanged  → 失效所有引用该模板的账号的缓存
AssignmentChanged    → 失效 assignment.accountId 的缓存
```

失效逻辑通过监听领域事件实现（基础设施层）：

```java
// auth-infrastructure/.../permission/PermissionCacheInvalidator.java
@Component
public class PermissionCacheInvalidator {
  @EventListener
  public void onGrantApproved(GrantApproved event) {
    cache.evict(event.subjectAccountIds());
  }
  // ... 其他事件
}
```

## 五、数据库设计

### 5.1 新增表

#### t_auth_permission_item（权限点元数据）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| business_code | VARCHAR(64) | 业务编码 |
| action_code | VARCHAR(64) | 操作编码（null=整个业务） |
| category | VARCHAR(16) | BUSINESS / PLATFORM |
| source | VARCHAR(16) | API / MANUAL |
| controller | VARCHAR(255) | 来源Controller类名 |
| method | VARCHAR(255) | 来源方法名 |
| http_method | VARCHAR(16) | HTTP方法 |
| path | VARCHAR(512) | 请求路径 |
| display_name | VARCHAR(128) | 显示名称（管理后台补充） |
| description | VARCHAR(512) | 描述（管理后台补充） |
| category_group | VARCHAR(64) | 分组（管理后台补充） |
| sort_order | INT | 排序 |
| auto_registered | BOOLEAN | 是否自动注册 |
| created_at | TIMESTAMP | 创建时间 |
| updated_at | TIMESTAMP | 更新时间 |

唯一索引：`uk_permission_item_biz_action (business_code, action_code)`
普通索引：`idx_permission_item_category (category)`, `idx_permission_item_group (category_group)`

### 5.2 现有表改造

无需改造。Grant / Role / Assignment 表结构完全不变——平台管理 Grant 的 `source_plan_no`/`target_plan_no` 为 null，`scope_rules` 为空 JSON 数组。

## 六、与现有 PermissionSnapshot 的关系

| 维度 | SessionPermissionCache（新增） | PermissionSnapshot（现有） |
|------|-------------------------------|---------------------------|
| 层级 | Session 级 | SecondaryAuthSession 级 |
| 用途 | 功能可见性（菜单/按钮显不显示） | 操作授权（这次操作能不能做） |
| TTL | 5 分钟 | 30 秒 |
| 内容 | 平台权限 + 业务权限分区 | 扁平的权限集合 |
| 适用渠道 | 所有渠道 | 仅网点渠道 |
| 存储 | Redis | SecondaryAuthSession 内 |

两者不冲突：SessionPermissionCache 是"粗粒度可见性缓存"，PermissionSnapshot 是"细粒度操作授权快照"。真正的权限校验始终在后端实时做，这两个都是缓存层优化。

## 七、改造点清单

### 7.1 auth-types
- 无变更

### 7.2 auth-domain
- 新增 `PermissionCategory` 枚举
- 修改 `ScopeDimension` 增加 `GLOBAL`
- 修改 `ScopeMatcher.matches` 支持 GLOBAL 规则
- 新增 `SessionPermissionCache` 值对象
- 修改 `EffectivePermissionService`：
  - 新增 `checkPlatformPermission` 方法
  - 修复 `toVirtualGrant` 的 GLOBAL 分支
  - 新增 `resolveLiveGlobalRoleTemplateGrants` / `toGlobalVirtualGrant`
- 新增 `PermissionItem` 领域对象（值对象，非聚合根）

### 7.3 auth-api
- 扩展 `RequirePermission` 注解增加 `category` 字段（permission-sdk）
- 新增 `PermissionMetadataApi` 接口
- 新增 `PermissionCacheApi` 接口
- 新增对应 DTO（PermissionItemResponse / PermissionResponse / PermissionGroupResponse）

### 7.4 auth-application
- 新增 `PermissionCacheService`（缓存计算）
- 新增 `PermissionMetadataApplicationService`（元数据查询）
- 修改 `PermissionQueryService` 增加 `category` 分流判定

### 7.5 auth-infrastructure
- 新增 `PermissionScanner`（注解自动发现）
- 新增 `PermissionItemRepositoryImpl` / `PermissionItemMapper` / `PermissionItemDO` / `PermissionItemConverter`
- 新增 `PermissionCacheInvalidator`（领域事件监听失效缓存）
- 新增 `PermissionCacheStore`（Redis 存储实现）
- 新增 `permission_item` 表 DDL（schema-pg.sql / schema-mysql.sql）

### 7.6 permission-sdk
- `RequirePermission` 注解增加 `category` 字段
- `PermissionGuard` 切面支持按 category 分流调用

## 八、关键决策记录

| # | 决策 | 理由 |
|---|------|------|
| 1 | 平台管理权限纳入 Grant 体系 | 用户要求统一模型，避免两套体系割裂 |
| 2 | 通过 `ScopeDimension.GLOBAL` + 空 scopeRules 支持平台权限 | 最小改动复用 ScopeMatcher，不破坏现有判定逻辑 |
| 3 | `PermissionCategory` 存于元数据表，不进 Permission 值对象 | 类别是元数据属性，不改变 Permission 的值语义 |
| 4 | 判定入口分流（checkPermission / checkPlatformPermission） | 平台权限无能力层概念，强行合并会引入无效的 PlanSnapshot 依赖 |
| 5 | SessionPermissionCache 分区存储（platformPermissions + businessPermissions） | 选计划时只需重拉业务权限，平台权限不变 |
| 6 | 权限点由代码注解决定（单一事实来源），元数据由后台补充 | 避免后台随意新增权限点导致与代码脱节 |
| 7 | 缓存拉取时机由前端控制 | 后端只提供原子接口，组合策略交给前端适配各渠道差异 |
| 8 | Grant 结构不变，sourcePlanNo/targetPlanNo 为 null 表示全局 | 避免改造聚合根，复用现有字段 |
| 9 | 平台管理 Grant 的 origin 复用 HQ_CONFIG | 语义一致：都是总部配置的授权 |
| 10 | 平台管理权限的 subject 用 UserListSubject | 当前已够用，角色模板实时解析也走 UserListSubject |

## 九、安全边界

### 9.1 前端 Cache 不作为安全边界

SessionPermissionCache 仅用于前端可见性判定（菜单/按钮显不显示），**后端 API 实际安全校验始终实时查 Grant**。前端 Cache 被篡改不影响安全性。

### 9.2 未标注 @RequirePermission 的接口

**目标策略**：未标注注解的 API 接口默认拒绝（返回 403）。

**过渡方案**：本期采用"告警不阻断"模式——PermissionScanner 输出未声明注解的接口列表到启动日志，不阻断请求。待各服务逐步补齐注解后，通过配置开关切换为强制模式。

**白名单**：登录、登出、健康检查等公共接口通过配置排除校验。

**启动报告**：PermissionScanner 在扫描完成后输出未声明注解的接口列表，便于排查漏配。

### 9.3 平台管理权限的 DENY 优先

平台管理权限同样适用 DENY 优先——如果某账号有 GLOBAL 范围的 DENY Grant，即使有 ALLOW Grant 也拒绝。这保证紧急收权能力对平台管理权限同样有效。
