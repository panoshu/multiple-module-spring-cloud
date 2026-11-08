# 权限体系架构设计文档

> 本文档面向后续 agent 和开发者，提供权限体系的完整架构设计、使用指南和注意事项。
> 读取此文档后即可理解：权限如何建模、前端如何获取可见菜单/按钮、后端如何做权限校验、管理端如何配置。

---

## 一、体系总览

权限体系由三个正交层组成：

| 层 | 职责 | 核心聚合根/值对象 | 变更频率 |
|---|---|---|---|
| **元数据层** | 定义"世界上有哪些权限点"和"菜单怎么组织" | `PermissionItem`、`PermissionMenu` | 低（扫描器自动发现 / 管理员手动维护） |
| **判定层** | 判定"某人某计划能不能做某事" | `Grant`、`RoleTemplate`、`EffectivePermissionService` | 中（管理操作变更） |
| **缓存层** | 前端可见性加速（菜单/按钮显隐） | `SessionPermissionCache`、`MenuNodeCache` | 高（随会话刷新） |

**核心不变式**：缓存层仅影响前端 UI 可见性，后端 API 安全校验始终穿透到判定层实时查询，不信任任何缓存。

---

## 二、元数据层

### 2.1 PermissionItem（权限点元数据）

| 字段 | 类型 | 可变性 | 说明 |
|---|---|---|---|
| `businessCode` | `BusinessCode` | 不可变 | 业务编码（如 `ANNUITY_BATCH`） |
| `actionCode` | `ActionCode` | 不可变 | 操作编码（null 表示整个业务） |
| `category` | `PermissionCategory` | 不可变 | `BUSINESS`（需 planId）或 `PLATFORM`（不需 planId） |
| `source` | `PermissionItemSource` | 不可变 | `API`（扫描器注册）或 `MANUAL`（人工创建） |
| `displayName` | `String` | 可变 | 管理后台补充的显示名称 |
| `categoryGroup` | `String` | 可变 | 扁平分组标识（**已被 PermissionMenu 树替代，仅做向后兼容**） |
| `sortOrder` | `int` | 可变 | 排序序号 |
| `autoRegistered` | `boolean` | 可变 | 当前是否被扫描器标记为活跃 |

**唯一键**：`(businessCode, actionCode)`。

**生命周期**：
1. `@RequirePermission` 注解在启动时被 `PermissionRegistrationRunner` 扫描
2. 通过 `PermissionRegistrationApi` 注册到 auth-service
3. auth-service 的 `PermissionScannerService` 执行 upsert（来源字段覆盖，元数据字段保留）
4. 不再被扫描到的条目标记 `autoRegistered = false`（stale），但 **不删除**

### 2.2 PermissionMenu（菜单节点）

| 字段 | 类型 | 可变性 | 说明 |
|---|---|---|---|
| `menuCode` | `MenuCode` | **不可变** | 冒号分隔稳定标识（如 `annuity:batch:submit`），前端用作 key |
| `menuName` | `String` | 可变 | 显示名称（如"年金管理"） |
| `menuPath` | `String` | 可变 | 物化路径（如 `/年金/缴费/办理`），自动计算 |
| `parentId` | `PermissionMenuId` | 可变 | 父节点 ID，null 表示根 |
| `depth` | `int` | 可变 | 树深度，根=0 |
| `sortOrder` | `int` | 可变 | 同级排序 |
| `icon` | `String` | 可变 | 图标标识 |
| `route` | `String` | 可变 | 前端路由路径 |
| `menuCategory` | `MenuCategory` | 不可变 | `BUSINESS` 或 `PLATFORM`，镜像 `PermissionCategory` |
| `visibility` | `MenuVisibility` | 可变 | 可见性规则 |
| `enabled` | `boolean` | 可变 | 是否启用 |
| `bindings` | `List<PermissionBinding>` | 可变 | 权限绑定（OR 语义） |

**唯一键**：`menuCode`。

**与 PermissionItem 的关系**：生命周期完全分离。`PermissionMenu` 由管理员在后台手动维护树结构和绑定关系；`PermissionItem` 由扫描器自动发现。二者通过 `PermissionBinding.businessCode/actionCode` 关联——菜单"引用"权限点，但不"拥有"权限点。

### 2.3 MenuCode 命名规范

```
<业务域>[:<子域>[:<操作>]]

示例：
  annuity              → 年金业务根菜单
  annuity:batch        → 年金-批次管理
  annuity:batch:submit → 年金-批次-提交
  system:user          → 系统-用户管理
  system:role          → 系统-角色管理
```

规则：全小写、冒号分隔、体现树形层级。创建后不可变，前端应持久化引用此 key。

### 2.4 MenuVisibility 可见性规则

| 规则 | 语义 | 典型场景 |
|---|---|---|
| `ALWAYS` | 始终可见 | 首页、帮助中心 |
| `WITH_PERMISSION` | 用户拥有该节点绑定的任一权限时可见 | "缴费办理"菜单——只有有缴费权限的人看得到 |
| `WITH_ANY_CHILD` | 任一子节点可见时可见 | "年金管理"父菜单——只要任一子菜单可见就显示 |

判定算法（`MenuTreeNode.isVisible`）：

```java
isVisible = switch (visibility) {
  case ALWAYS -> true;
  case WITH_PERMISSION -> hasAnyBindingPermission;
  case WITH_ANY_CHILD -> hasAnyVisibleChild;
};
```

递归方向：**先子后父**。先计算所有子节点的可见性，再根据 `WITH_ANY_CHILD` 向上传播。这样"空父节点"自然消失。

### 2.5 PermissionBinding（菜单-权限绑定值对象）

```java
record PermissionBinding(PermissionBindingId bindingId, BusinessCode businessCode, ActionCode actionCode)
```

- 一个菜单可绑定多个权限点，**OR 语义**——用户拥有任一即视为"有权限"
- `actionCode` 为 null 表示"整个业务"通配
- 持久化在独立表 `t_auth_menu_permission_binding` 中，但生命周期由 `PermissionMenu` 聚合根管理（save 时全量替换）

---

## 三、判定层

### 3.1 核心公式

```
最终允许 = 能力层(计划是否开通) AND 主体层(身份是否被授权)
```

| 层 | BUSINESS 类别 | PLATFORM 类别 |
|---|---|---|
| **能力层** | `checkPlanCapability(planId, business, now)` | 跳过（平台权限无 planId 维度） |
| **主体层** | 持久化 Grant ∪ 实时角色模板虚拟 Grant → DENY 优先 | 仅 GLOBAL 作用域的持久化 Grant ∪ 实时角色模板虚拟 Grant → DENY 优先 |

### 3.2 Grant（授权记录聚合根）

所有类型的授权记录都是 `Grant` 的实例，通过 `grantType` / `origin` / `subject` / `effect` 区分语义：

| grantType | origin | subject | 典型场景 |
|---|---|---|---|
| CAPABILITY | PLAN_CONFIG | PlanSubject(planId) | 计划开通了哪些业务 |
| SUBJECT | HQ_CONFIG | AccountSubject/HQAllSubject | 总部给账号的授权/拒绝 |
| DELEGATE | PLAN_DELEGATE | PlanSubject/SelectiveSubject | 计划间代办 |
| DELEGATE | CUSTOMER_TO_AGENT | CustomerSubject | 跨企业委托给经办 |

`effect`：`ALLOW` 或 `DENY`，**DENY 永远优先**。

### 3.3 RoleTemplate（角色模板聚合根）

角色模板是"某人应该有哪些权限"的静态配方。不直接授权，在判定时按优先级（PLAN > CUSTOMER > PRODUCT > GLOBAL）解析出虚拟 Grant：

```
虚拟Grant = { subject=当前身份, origin=ROLE_TEMPLATE, effect=ALLOW, permissions=模板定义的权限集合 }
```

优势：角色模板修改后，所有引用它的身份分配立刻生效，不需要批量同步。

### 3.4 EffectivePermissionService（权限判定真正入口）

```java
// 业务权限判定（能力层 AND 主体层）
boolean checkPermission(UserNo identity, PlanNo planId, Permission permission, LocalDateTime now)

// 平台权限判定（仅主体层 GLOBAL 匹配）
boolean checkPlatformPermission(UserNo identity, Permission permission, LocalDateTime now)

// 解析可见数据范围
DataScope resolveDataScope(UserNo identity, PlanNo planId, BusinessCode business)
```

---

## 四、缓存层

### 4.1 SessionPermissionCache（会话权限快照）

```java
record SessionPermissionCache(
  Set<Permission> platformPermissions,   // 登录后拉取
  Set<Permission> businessPermissions,   // 选计划后拉取
  PlanNo selectedPlanId,
  LocalDateTime cachedAt,
  LocalDateTime expiresAt                // 默认 5 分钟
)
```

**两区设计**：
- `platformPermissions`：登录后拉取，不随计划切换变化
- `businessPermissions`：选计划后拉取，切换计划时只刷新此区

**存储**：Redis，key `auth:perm:cache:{accountId}`，TTL 5 分钟

**前端 API**：
- `POST /permission-cache/platform` — 获取平台权限集合
- `POST /permission-cache/business` — 获取业务权限集合（需传 planId）

**安全边界**：此缓存仅用于前端 UI 渲染（菜单/按钮显隐），后端 API 校验始终实时查 `EffectivePermissionService`，绝不信任缓存。

### 4.2 MenuNodeCache（菜单树结构缓存）

```java
// Caffeine 本地缓存，key=MenuCategory，value=List<PermissionMenu>
// TTL 30 分钟，菜单变更时由应用服务主动失效
```

**设计思路**：菜单树结构变更远少于权限变更（管理员操作 vs 每次登录），单独缓存可避免重复查库组装树。

**可见树计算**：从前端请求角度，流程为：

```
1. 前端请求 /permission-menu/business-tree 或 /permission-menu/platform-tree
2. 从 MenuNodeCache 加载全量菜单节点（按 category）
3. 从 SessionPermissionCache 或实时计算获取用户权限集合
4. MenuTreeAssembler.assembleVisibleTree(flatMenus, userPermissions, categoryBridge)
5. 返回过滤后的 MenuTreeNode 树
```

时间复杂度 O(n)，n = 全量菜单节点数（约 200 个），毫秒级。

---

## 五、前端集成指南

### 5.1 登录后流程

```
1. 用户登录成功 → 获取 token
2. 调用 POST /permission-cache/platform → 获取平台权限集合
   → 渲染平台菜单（系统管理、帮助中心等）
3. 调用 POST /permission-menu/platform-tree → 获取可见平台菜单树
   → 根据返回的树渲染侧边栏/导航
```

### 5.2 选计划后流程

```
1. 用户选择计划 → 获取 planId
2. 调用 POST /permission-cache/business → 获取业务权限集合
   → 本地存储权限集合，用于按钮显隐判断
3. 调用 POST /permission-menu/business-tree → 获取可见业务菜单树
   → 根据返回的树渲染业务侧边栏
```

### 5.3 菜单树响应格式

```json
{
  "code": "COMMON.0000",
  "data": [
    {
      "menuCode": "annuity",
      "menuName": "年金管理",
      "depth": 0,
      "sortOrder": 1,
      "icon": "annuity",
      "route": null,
      "menuCategory": "BUSINESS",
      "visibility": "WITH_ANY_CHILD",
      "enabled": true,
      "children": [
        {
          "menuCode": "annuity:batch",
          "menuName": "批次管理",
          "depth": 1,
          "sortOrder": 1,
          "icon": "batch",
          "route": "/annuity/batch",
          "menuCategory": "BUSINESS",
          "visibility": "WITH_PERMISSION",
          "enabled": true,
          "children": []
        }
      ]
    }
  ]
}
```

### 5.4 按钮级权限

前端仍使用 `SessionPermissionCache` 中的权限集合做按钮显隐：

```javascript
// 前端工具函数示例
function hasPermission(businessCode, actionCode) {
  return permissions.some(p =>
    p.businessCode === businessCode &&
    (p.actionCode === null || p.actionCode === actionCode)
  );
}

// 使用
if (hasPermission('ANNUITY_BATCH', 'SUBMIT')) {
  showSubmitButton();
}
```

### 5.5 安全提醒

> **前端权限控制仅影响 UI 可见性，不构成安全边界。** 后端 API 必须通过 `@RequirePermission` 注解或显式调用 `PermissionQueryService.checkPermission()` 做实时校验。

---

## 六、后端集成指南

### 6.1 声明式权限校验（推荐）

在 Controller 方法上标注 `@RequirePermission`：

```java
@PostMapping("/submit")
@RequirePermission(business = "ANNUITY_BATCH", action = "SUBMIT", category = PermissionCategory.BUSINESS)
public ApiResult<Void> submit(@RequestBody SubmitCommand cmd) {
    // 业务逻辑
}
```

- 由 `shared-permission-starter` 的 `RequirePermissionAspect` 拦截
- `@Order(1)` 在 `@DataScope` 之前执行
- 自动从会话上下文获取 `identity` 和 `planId`

### 6.2 编程式权限校验

在 Service 层直接调用：

```java
@RequiredArgsConstructor
public class OrderService {
    private final PermissionQueryService permissionQueryService;

    public void processOrder(UserNo identity, PlanNo planId, Order order) {
        permissionQueryService.checkPermission(
            identity, planId, "ORDER_PROCESS", "APPROVE",
            com.pension.permission.domain.authorization.enumeration.PermissionCategory.BUSINESS
        );
        // 业务逻辑
    }
}
```

### 6.3 数据权限（行级过滤）

在 Controller 方法上标注 `@DataScope`：

```java
@PostMapping("/list")
@DataScope(business = "ANNUITY_BATCH")
@RequirePermission(business = "ANNUITY_BATCH", action = "VIEW", category = PermissionCategory.BUSINESS)
public ApiResult<PageData<BatchResponse>> list(@RequestBody BatchQuery query) {
    // DataScopeAspect 会在方法执行前解析数据范围
    // 通过 DataScopeContext 获取，在 Repository 查询时应用
}
```

### 6.4 权限点自动注册

业务微服务只需在方法上标注 `@RequirePermission`，启动时 `PermissionRegistrationRunner` 自动扫描并注册到 auth-service。无需手动配置。

### 6.5 auth-service 内部调用短路

auth-service 的 `auth-adapter` 模块提供 `@Primary` 的 `LocalPermissionExecutor` 和 `LocalDataScopeResolver`，直接调用本地 `PermissionQueryService`，不走 HTTP，减少内部调用开销。

---

## 七、管理端操作指南

### 7.1 权限元数据管理

| API | 说明 |
|---|---|
| `POST /permission-metadata/items` | 列出权限点（扁平） |
| `POST /permission-metadata/items/grouped` | 列出权限点（按 categoryGroup 分组） |

权限点元数据通常由扫描器自动维护，管理员只需补充 `displayName`、`categoryGroup`、`sortOrder`。

### 7.2 菜单树管理

所有菜单管理 API 需 `@RequirePermission(business = "PERMISSION_MENU", action = "MANAGE", category = PLATFORM)`。

| API | 说明 | 关键参数 |
|---|---|---|
| `POST /permission-metadata/menus/list` | 列出所有菜单节点（扁平，含绑定） | `category`（可选过滤） |
| `POST /permission-metadata/menus/get` | 获取单个菜单详情 | `menuId` |
| `POST /permission-metadata/menus/create` | 创建菜单节点 | `menuCode`（不可变）, `parentId`, `visibility` 等 |
| `POST /permission-metadata/menus/update` | 更新显示信息 | `menuId`, `menuName`, `icon`, `route`, `sortOrder` |
| `POST /permission-metadata/menus/move` | 移动节点 | `menuId`, `newParentId`, `sortOrder` |
| `POST /permission-metadata/menus/delete` | 删除节点（需无子节点） | `menuId` |
| `POST /permission-metadata/menus/bindings/replace` | 替换权限绑定（全量） | `menuId`, `bindings[]` |
| `POST /permission-metadata/menus/toggle` | 切换启停 | `menuId` |

### 7.3 菜单创建示例

```
1. 创建根菜单"年金管理"：
   menuCode = "annuity"
   menuName = "年金管理"
   visibility = WITH_ANY_CHILD
   menuCategory = BUSINESS

2. 创建子菜单"批次管理"：
   menuCode = "annuity:batch"
   menuName = "批次管理"
   parentId = <annuity节点的ID>
   visibility = WITH_PERMISSION
   menuCategory = BUSINESS

3. 为"批次管理"绑定权限：
   bindings = [
     { businessCode: "ANNUITY_BATCH", actionCode: "VIEW" },
     { businessCode: "ANNUITY_BATCH", actionCode: "SUBMIT" }
   ]
```

### 7.4 操作后缓存失效

管理端每次写操作（create/update/move/delete/bindings/toggle）都会自动失效对应的 `MenuNodeCache` 分区。下次前端请求可见树时重新从 DB 加载。

---

## 八、数据库设计

### 8.1 t_auth_permission_menu

| 列名 | 类型 | 说明 |
|---|---|---|
| id | VARCHAR(32) | 主键（ULID） |
| menu_code | VARCHAR(128) | 菜单编码（唯一，不可变） |
| menu_name | VARCHAR(128) | 菜单名称 |
| menu_path | VARCHAR(512) | 物化路径 |
| parent_id | VARCHAR(32) | 父节点 ID |
| depth | INT | 树深度 |
| sort_order | INT | 排序序号 |
| icon | VARCHAR(128) | 图标 |
| route | VARCHAR(256) | 前端路由 |
| menu_category | VARCHAR(16) | BUSINESS / PLATFORM |
| visibility | VARCHAR(24) | ALWAYS / WITH_PERMISSION / WITH_ANY_CHILD |
| enabled | BOOLEAN | 是否启用 |
| created_by | VARCHAR(64) | 创建人 |
| create_time | TIMESTAMP | 创建时间 |
| updated_by | VARCHAR(64) | 更新人 |
| update_time | TIMESTAMP | 更新时间 |
| deleted | BOOLEAN | 逻辑删除 |
| version | INT | 乐观锁 |

索引：
- `uk_permission_menu_code` UNIQUE on `(menu_code) WHERE deleted = FALSE`
- `idx_permission_menu_parent` on `(parent_id) WHERE deleted = FALSE`
- `idx_permission_menu_category` on `(menu_category) WHERE deleted = FALSE`
- `idx_permission_menu_path` on `(menu_path) WHERE deleted = FALSE`

### 8.2 t_auth_menu_permission_binding

| 列名 | 类型 | 说明 |
|---|---|---|
| id | VARCHAR(32) | 主键 |
| menu_id | VARCHAR(32) | 菜单 ID |
| business_code | VARCHAR(64) | 业务编码 |
| action_code | VARCHAR(64) | 操作编码（null=整个业务） |
| created_by | VARCHAR(64) | 创建人 |
| create_time | TIMESTAMP | 创建时间 |
| updated_by | VARCHAR(64) | 更新人 |
| update_time | TIMESTAMP | 更新时间 |
| deleted | BOOLEAN | 逻辑删除 |
| version | INT | 乐观锁 |

索引：
- `uk_menu_binding_menu_biz_action` UNIQUE on `(menu_id, business_code, action_code) WHERE deleted = FALSE`
- `idx_menu_binding_menu` on `(menu_id) WHERE deleted = FALSE`

---

## 九、错误码

### 9.1 权限菜单错误码（09xx）

| 错误码 | 含义 |
|---|---|
| `SERVICE.AUTH.0901` | 菜单节点不存在 |
| `SERVICE.AUTH.0902` | 菜单编码已存在 |
| `SERVICE.AUTH.0903` | 菜单存在子节点，无法删除 |
| `SERVICE.AUTH.0904` | 菜单类别无效 |
| `SERVICE.AUTH.0905` | 菜单可见性规则无效 |
| `SERVICE.AUTH.0906` | 存在可见子节点，无法禁用 |

### 9.2 权限元数据错误码（08xx）

| 错误码 | 含义 |
|---|---|
| `SERVICE.AUTH.0801` | 权限点不存在 |
| `SERVICE.AUTH.0802` | 权限点已存在 |
| `SERVICE.AUTH.0803` | 权限类别无效 |

---

## 十、领域事件

| 事件 | 触发时机 | 用途 |
|---|---|---|
| `PermissionItemCreated` | 权限点首次注册 | 审计日志 |
| `PermissionMenuCreated` | 菜单节点创建 | 审计日志、缓存失效 |
| `PermissionMenuMoved` | 菜单节点移动 | 审计日志、子树路径重建 |

---

## 十一、关键设计决策

| 决策 | 原因 |
|---|---|
| `PermissionMenu` 与 `PermissionItem` 分聚合根 | 生命周期不同：菜单由管理员维护，权限点由扫描器自动发现。合并会违反聚合边界。 |
| `PermissionBinding` 作为聚合内值对象 | 绑定没有独立生命周期，仅在菜单节点上下文中存在。独立表是为了查询灵活性。 |
| `MenuCode` 冒号分隔命名 | 反映树形层级，前端可做稳定 key 引用，无需暴露数据库 ID。 |
| `MenuCategory` 镜像 `PermissionCategory` | 避免跨子域耦合，桥接映射是平凡的（同名常量）。 |
| `WITH_ANY_CHILD` 可见性规则 | 常见 UX 模式：父菜单"年金管理"只在有任一可见子菜单时显示，避免空壳节点。 |
| Caffeine 缓存菜单节点 | 菜单变更频率极低（管理员操作），Caffeine 避免每次树组装都查 DB。 |
| 实时计算可见树 | 不将树结构存入 SessionPermissionCache。树组装 O(n)，n ≈ 200，毫秒级完成。 |
| 缓存层不参与安全校验 | 防止缓存与 DB 不一致导致权限泄漏。后端 API 校验始终穿透到判定层。 |
| ULID 作为 `PermissionMenuId` | 与 `GrantId` 模式一致，人类可读、时间有序、无需协调。 |
| `categoryGroup` 字段保留 | 向后兼容。新代码应使用 `PermissionMenu` 树结构，不再依赖 `categoryGroup`。 |

---

## 十二、向后兼容与迁移

### 12.1 categoryGroup 废弃计划

- **当前状态**：`PermissionItem.categoryGroup` 字段仍存在，`listGroupedItems` API 仍可用。
- **推荐方向**：前端逐步迁移到 `/permission-menu/*-tree` API，不再依赖 `/permission-metadata/items/grouped`。
- **最终状态**：`categoryGroup` 字段标记 `@Deprecated`，后续迭代移除 `listGroupedItems` API。

### 12.2 迁移检查清单

- [ ] 前端登录流程增加 `/permission-menu/platform-tree` 调用
- [ ] 前端选计划流程增加 `/permission-menu/business-tree` 调用
- [ ] 管理后台增加菜单树管理页面（CRUD + 绑定）
- [ ] 验证菜单树缓存失效机制正常
- [ ] 确认 `categoryGroup` 相关 API 无新调用方后标记废弃

---

## 十三、文件索引

### auth-types
- `com.pension.permission.types.PermissionMenuId` — 菜单 ID 类型
- `com.pension.permission.types.MenuCode` — 菜单编码类型
- `com.pension.permission.types.PermissionBindingId` — 绑定 ID 类型

### auth-domain
- `com.pension.permission.domain.permission.aggregate.PermissionMenu` — 菜单聚合根
- `com.pension.permission.domain.permission.aggregate.valueobject.PermissionBinding` — 权限绑定值对象
- `com.pension.permission.domain.permission.aggregate.valueobject.MenuTreeNode` — 菜单树节点值对象
- `com.pension.permission.domain.permission.enumeration.MenuCategory` — 菜单类别枚举
- `com.pension.permission.domain.permission.enumeration.MenuVisibility` — 可见性规则枚举
- `com.pension.permission.domain.permission.repository.PermissionMenuRepository` — 仓储端口
- `com.pension.permission.domain.permission.service.MenuTreeAssembler` — 树组装领域服务
- `com.pension.permission.domain.permission.errorcode.PermissionMenuError` — 错误码
- `com.pension.permission.domain.permission.event.PermissionMenuCreated` — 创建事件
- `com.pension.permission.domain.permission.event.PermissionMenuMoved` — 移动事件

### auth-api
- `com.example.auth.api.PermissionMenuApi` — 前端菜单查询 API
- `com.example.auth.api.PermissionMetadataApi` — 权限元数据 + 菜单管理 API
- `com.example.auth.api.dto.MenuTreeNodeResponse` — 可见树节点响应
- `com.example.auth.api.dto.MenuNodeResponse` — 管理端扁平节点响应
- `com.example.auth.api.dto.PermissionBindingResponse` — 绑定响应
- `com.example.auth.api.dto.CreateMenuCommand` — 创建菜单命令
- `com.example.auth.api.dto.UpdateMenuCommand` — 更新菜单命令
- `com.example.auth.api.dto.MoveMenuCommand` — 移动菜单命令
- `com.example.auth.api.dto.DeleteMenuCommand` — 删除菜单命令
- `com.example.auth.api.dto.ReplaceBindingsCommand` — 替换绑定命令
- `com.example.auth.api.dto.BindingItem` — 绑定项
- `com.example.auth.api.dto.ToggleMenuCommand` — 启停切换命令

### auth-application
- `com.pension.permission.application.permission.PermissionMenuApplicationService` — 菜单应用服务
- `com.pension.permission.application.permission.MenuNodeCache` — Caffeine 菜单缓存

### auth-adapter
- `com.example.auth.adapter.permission.PermissionMenuController` — 前端菜单查询 Controller
- `com.example.auth.adapter.permission.PermissionMetadataController` — 权限元数据 + 菜单管理 Controller
- `com.example.auth.adapter.converter.PermissionMenuConverter` — MapStruct 转换器

### auth-infrastructure
- `com.pension.permission.infrastructure.permission.entity.PermissionMenuDO` — 菜单 DO
- `com.pension.permission.infrastructure.permission.entity.MenuPermissionBindingDO` — 绑定 DO
- `com.pension.permission.infrastructure.permission.mapper.PermissionMenuMapper` — MyBatis-Flex Mapper
- `com.pension.permission.infrastructure.permission.mapper.MenuPermissionBindingMapper` — MyBatis-Flex Mapper
- `com.pension.permission.infrastructure.permission.converter.PermissionMenuConverter` — 基础设施层转换器
- `com.pension.permission.infrastructure.permission.repository.PermissionMenuRepositoryImpl` — 仓储实现
