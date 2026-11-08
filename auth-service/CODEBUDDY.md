# auth-service 模块文档

## 一句话职责

基于 Sa-Token 的多渠道认证授权服务，提供登录、会话管理、权限校验（双层模型）、数据范围隔离和菜单可见性计算。

## 入口清单

| 入口 | 类型 | 位置 | 说明 |
|------|------|------|------|
| `SessionController` | HTTP | `auth-adapter/.../channel/SessionController.java:38` | 登录/登出/选计划/查询可选计划 |
| `PermissionCacheController` | HTTP | `auth-adapter/.../permission/PermissionCacheController.java:29` | 前端获取平台/业务权限集合 |
| `PermissionMenuController` | HTTP | `auth-adapter/.../permission/PermissionMenuController.java:26` | 前端获取菜单可见性树 |
| `PermissionCheckController` | HTTP(内部) | `auth-adapter/.../permission/PermissionCheckController.java` | 后端服务权限校验（内部 API） |
| `LocalPermissionExecutor` | 本地短路 | `auth-adapter/.../permission/LocalPermissionExecutor.java:29` | auth-service 内部权限校验不走 HttpExchange |
| `SessionContextInjector` | Gateway Filter | `gateway-shared/.../security/SessionContextInjector.java:51` | 网关注入签名会话上下文 |
| `RequirePermissionAspect` | AOP | `shared-permission-starter/.../RequirePermissionAspect.java:27` | @RequirePermission 注解拦截 |
| `DataScopeAspect` | AOP | `shared-permission-starter/.../DataScopeAspect.java:28` | @DataScope 注解拦截 |
| `PermissionRegistrationRunner` | ApplicationRunner | `shared-permission-starter/.../PermissionRegistrationRunner.java:44` | 启动时扫描并注册权限元数据 |

## 职责清单

1. **多渠道认证**：三套独立 StpLogic（internet/hq/branch），独立 token header，路径前缀路由
2. **统一身份解析**：`IdentityResolutionService` 将四种登录场景（互联网用户、总部员工、网点柜员、二次认证）归一为 `resolve()` 方法
3. **会话生命周期**：登录建 Session → 选择 Plan → 二次认证提权 → 登出销毁，Session.id = token 值（无映射表）
4. **双层权限模型**：能力层（Plan 级别）+ 主体层（Grant 级别），BUSINESS 权限两层都过，PLATFORM 权限仅 GLOBAL 作用域
5. **虚拟授权（Virtual Grant）**：角色模板在查询时展开为 effect=ALLOW 的虚拟 Grant，与持久化 Grant 统一走 ScopeMatcher+EffectResolver 管线，无需回填
6. **权限缓存双区**：`platformPermissions`（登录时加载，跨 Plan 稳定）+ `businessPermissions`（选 Plan 后加载，切换刷新），Redis TTL 5min
7. **菜单可见性**：全量菜单项与用户权限集求交集，按树结构组装返回前端
8. **数据范围隔离**：`@DataScope` AOP 解析行级可见范围（visiblePlans/visibleCustomers/excluded），存入 ThreadLocal
9. **权限自注册**：各业务服务启动时扫描 `@RequirePermission` 注解，向 auth-service 报告权限元数据
10. **网关会话注入**：`SessionContextInjector` 剥离客户端伪造头，从 Sa-Token Token-Session 读取会话，构建 HMAC-SHA256 签名头传递下游

## 主流程

### 流程 1：登录

```
POST /auth/login
  → SessionController.login()                              [auth-adapter:38]
  → SessionApplicationService.openSessionWithCredential()  [auth-application:45]
    → IdentityResolutionService.resolve()                  [auth-domain:41]
    → SaTokenLoginTokenService.issueToken()                [auth-infrastructure:23]
    → Session.create() → registerDomainEvent()
    → sessionRepository.save()
    → PermissionCacheService.computeAndSave()              [auth-application:33]
  ← LoginResponse(token, tokenName, accountId, channel)
```

### 流程 2：前端获取平台权限（登录后立即）

```
GET /permission-cache/platform?accountId=xxx
  → PermissionCacheController.getPlatformPermissions()     [auth-adapter:29]
  → PermissionCacheService.computePlatformPermissions()    [auth-application:33]
    → 遍历所有 PLATFORM 类型 PermissionItem
    → EffectivePermissionService.checkPlatformPermission() [auth-domain:49]
      → AuthorizationEngine.checkSubjectGrant()            [auth-domain:22]
        → ScopeMatcher.match(GLOBAL) + EffectResolver.resolve()
  ← Set<Permission>
```

### 流程 3：前端获取业务权限（选 Plan 后）

```
GET /permission-cache/business?accountId=xxx&planId=yyy
  → PermissionCacheController.getBusinessPermissions()     [auth-adapter:29]
  → PermissionCacheService.computeBusinessPermissions()    [auth-application:33]
    → 遍历所有 BUSINESS 类型 PermissionItem
    → EffectivePermissionService.checkPermission()         [auth-domain:49]
      → AuthorizationEngine.checkPlanCapability()          [auth-domain:22]
        → ScopeMatcher.match(planId) + EffectResolver.resolve()
      → AND AuthorizationEngine.checkSubjectGrant()        [auth-domain:22]
        → ScopeMatcher.match(subjectScopes) + EffectResolver.resolve()
  ← Set<Permission>
```

### 流程 4：前端获取菜单树

```
GET /permission-menu/business-tree?planId=yyy
  → PermissionMenuController.getBusinessTree()             [auth-adapter:26]
  → PermissionMenuApplicationService.getBusinessTree()     [auth-application:35]
    → 全量菜单项 ∩ 用户权限集 → MenuTreeAssembler.assembleVisibleTree()
  ← MenuTreeNode (树形)

GET /permission-menu/platform-tree
  → 同上，PLATFORM 菜单
```

### 流程 5：后端服务权限校验

```
@RequirePermission(business="ORDER", action="CREATE", category=BUSINESS)
  → RequirePermissionAspect                                   [shared-permission-starter:27]
    → AccountIdResolver 从签名头提取 accountId
    → PlanIdResolver 从请求/会话提取 planId
    → PermissionExecutor.execute()
      ├─ 业务服务: HttpExchangePermissionExecutor           [shared-permission-starter:25]
      │    → POST /internal/permissions/check (HttpExchange)
      └─ auth-service: LocalPermissionExecutor              [auth-adapter:29]
           → PermissionQueryService.checkPermission()       [auth-application:30]
             → EffectivePermissionService.checkPermission() [auth-domain:49]
```

### 流程 6：数据范围解析

```
@DataScope(resource="ORDER")
  → DataScopeAspect                                           [shared-permission-starter:28]
    → DataScopeResolver → PermissionCheckApi.resolveDataScope()
    → 写入 DataScopeContext ThreadLocal
  ← 后续业务代码从 ThreadLocal 读取 visiblePlans / visibleCustomers / excluded
```

## 数据与依赖

### 核心领域对象

| 对象 | 类型 | 所在层 | 说明 |
|------|------|--------|------|
| `Session` | AggregateRoot | domain | 会话聚合根，id = token 值 |
| `SessionPermissionCache` | ValueObject(record) | domain | 双区权限缓存快照 |
| `Grant` | Entity | domain | 授权记录（ALLOW/DENY，含 Scope） |
| `PermissionItem` | Entity | domain | 权限定义项（BUSINESS/PLATFORM） |
| `RoleTemplate` | Entity | domain | 角色模板，查询时展开为虚拟 Grant |
| `AnnuityChannel` | ValueObject(enum) | domain | 渠道枚举（INTERNET/HQ/BRANCH） |

### SPI 网关接口（domain 层定义，infrastructure 层实现）

| SPI 接口 | 实现 | 说明 |
|----------|------|------|
| `LoginTokenService` | `SaTokenLoginTokenService` | Sa-Token 三渠道 login/logout |
| `PermissionCacheStore` | Redis 实现 | `auth:perm:cache:{accountId}` |
| `GrantRepository` | MyBatis-Flex | Grant 持久化 |
| `PermissionItemRepository` | MyBatis-Flex | 权限定义持久化 |

### 外部依赖

- **Redis/Redisson**：权限缓存、Sa-Token 会话存储
- **Sa-Token**：多 StpLogic 实例、Token-Session
- **shared-permission-starter**：`@RequirePermission`/`@DataScope` AOP、权限自注册、HttpExchange 调用
- **gateway-shared**：`SessionContextInjector`、`ChannelAwareSaRouter`、粗粒度角色映射

## 历史痕迹与待确认项

| 项目 | 状态 | 说明 |
|------|------|------|
| 虚拟 Grant 模式 | ✅ 已实现 | 角色模板查询时展开，无需回填，但前端无法区分 "来自角色" 还是 "来自直接授权" |
| 权限缓存 TTL | ✅ 5min | `PermissionCacheService` 硬编码，未外部化配置 |
| Gateway 粗粒度角色 | ⚠️ 简化实现 | `GatewayStpInterfaceImpl` 返回固定角色映射（internet→operator, hq→staff, branch→teller），非数据库驱动 |
| 二次认证提权 | ✅ 已实现 | 通过 `SecondaryAuthCompleted`/`SecondaryAuthRevoked` 事件监听 |
| 权限自注册 | ✅ 已实现 | `PermissionRegistrationRunner` 启动时注册，但未处理服务下线后的清理 |
| 数据范围 DENY 减法 | ✅ 已实现 | `PermissionQueryService.resolveDataScope()` 对 DENY 集合做防御性减法 |
| auth-service 内部短路 | ✅ 已实现 | `LocalPermissionExecutor` 标注 `@Primary`，避免 auth-service 自调 HttpExchange 循环 |
