# iam-service 用户与权限服务设计文档

> 创建日期：2026-07-25
> 状态：Spec 已确认，待制定实现计划
> 基于：sa-token v1.45.0 + Spring Boot 3.5.14 + Spring Cloud 2025.0.2

---

## 一、背景与目标

### 1.1 背景

本项目（multiple-module-spring-cloud）是企业年金业务办理平台，业务场景较为特殊，需要支持多个业务办理渠道，且权限逻辑复杂：

- **网上渠道**：客户的 HR（经办人）通过互联网访问，每个计划下设置多个不同角色的经办人
- **总部渠道**：本公司运营人员通过内网访问
- **网点渠道**：合作银行柜员通过专线网络访问，需经办人二次授权后才能办理业务

业务权限需要按照客户、运作模式、产品编号、计划编号、账管人编号等多维度设置；计划之间可以设置代办关系（计划级代办 / 经办人级代办）；每个业务下面可以设置办理/查询/审核等各种权限位。

### 1.2 目标

新建 `iam-service`（Identity and Access Management Service），承载全功能用户与权限管理能力：

- 多渠道用户认证（网上/总部/网点）
- 三层权限模型（RBAC + PBAC + 数据权限）
- 多维度业务授权（客户/运作模式/产品/计划/账管人）
- 计划级与经办人级代办关系
- 网点柜员二次授权（身份切换）
- 操作审计（谁替谁代办）
- 动态路由鉴权
- 完整的开闭原则扩展点

### 1.3 范围边界

- **本服务管理**：用户、凭据、角色、权限、业务授权、代办关系、二次授权会话、审计记录、路由规则
- **外部系统管理（通过防腐层 Gateway 调用）**：客户/企业信息、产品信息、计划信息、账管人信息

---

## 二、整体架构

### 2.1 架构方案选型

采用 **方案 A：sa-token 多账号体系（三套 StpLogic）+ 业务权限自定义网关**

- 三套独立 StpLogic：`StpInternetUtil` / `StpHqUtil` / `StpBranchUtil`，每套独立 token-name、独立 Redis 命名空间、独立 StpInterface 实现
- 身份切换用 sa-token 原生 `switchTo`
- 业务权限（PBAC）独立于 sa-token，自定义 `BizPermissionEvaluator` 计算
- 网关统一鉴权 + 业务服务细粒度注解鉴权

### 2.2 三层权限模型

| 层 | 模型 | 管什么 | 示例 | 实现载体 |
|---|---|---|---|---|
| 第1层 UI权限 | RBAC | 菜单/按钮/UI 资源可见性 | `menu:user` `button:add` | sa-token 标准 `StpInterface.getPermissionList()` |
| 第2层 业务权限 | PBAC | 哪些主体能办哪些业务的哪些动作 | `PLAN:P001:BIZ:APPLY_A:apply` | `BizAuthGrant` 聚合根 + `BizPermissionEvaluator` |
| 第3层数据权限 | 基于关系 | 用户能操作哪些数据对象 | 经办人只能选自己客户的计划 | 用户-计划-角色绑定关系 + 渠道规则（隐式） |

**第2层与第3层协作**：用户选计划办理业务时
1. 第3层判定"该用户能否选这个计划"（数据范围）
2. 通过后第2层判定"该计划下能办哪些业务+动作"（业务权限）
3. 前端按钮可见性由第1层控制

### 2.3 权限码格式

**A. 静态权限码（UI层，存数据库 t_iam_permission）**：
```
菜单：menu:user、menu:role
按钮：button:user:add、button:user:disable
API： api:user:query
角色：admin、operator、teller（角色标识）
```

**B. 动态业务权限码（PBAC层，运行时拼接）**：
```
格式：PLAN:{planNo}:BIZ:{bizCode}:{action}
示例：PLAN:P001:BIZ:APPLY_A:apply
     PLAN:P002:BIZ:AUDIT_B:audit
```

由 `BizPermissionEvaluator` 在用户**选择计划时**动态生成，存入 sa-token 的 `Token-Session`。复用 sa-token 原生 `StpUtil.hasPermission()` 校验。

---

## 三、限界上下文与子域划分

iam-service 内部划分 6 个子域（限界上下文）：

```
iam-service
├── ① 认证上下文 (Authentication Context)
│   ├── 聚合根：InternetUser / HqUser / BranchUser（三套账号，渠道可扩展）
│   ├── 聚合根：Credential（凭据，类型可扩展：PASSWORD/UKIE/OTP/...）
│   ├── 聚合根：SecondaryAuthSession（二次授权会话，临时带有效期）
│   ├── 策略接口：CredentialValidator（凭据验证策略，按类型分发）
│   ├── 策略接口：SecondaryAuthStrategy（二次授权策略，开闭原则扩展点）
│   └── 领域服务：LoginService + SecondaryAuthService + IdentitySwitchService
│
├── ② RBAC 上下文 (UI Permission Context)
│   ├── 聚合根：Role / Permission / UserRoleAssignment
│   └── 领域服务：RoleService + MenuPermissionEvaluator
│
├── ③ 业务授权上下文 (Biz Authorization Context - PBAC)
│   ├── 聚合根：BizAuthGrant（主体类型枚举可扩展）+ BizOperation
│   ├── 策略接口：BizAuthInheritancePolicy（下属企业继承策略，可扩展）
│   └── 领域服务：BizAuthGrantService + BizPermissionEvaluator（核心计算引擎）
│
├── ④ 代办关系上下文 (Agency Context)
│   ├── 聚合根：PlanAgency（计划级代办）+ UserAgency（经办人级代办）
│   └── 领域服务：AgencyService + AgencyPermissionResolver
│
├── ⑤ 操作审计上下文 (Operation Audit Context)
│   ├── 聚合根：OperationAuditRecord（append-only）
│   └── 领域服务：AuditService
│
└── ⑥ 路由鉴权上下文 (Route Auth Context)
    ├── 聚合根：RouteRule
    └── 领域服务：RouteRuleService + RouteRuleLoader
```

**子域间依赖关系**（单向）：
- ⑥路由鉴权 → ①认证 + ②RBAC + ③PBAC
- ①认证内的二次授权 → 身份切换
- ③PBAC → ④代办（代办关系会扩大业务权限范围）
- ②RBAC ←→ ③PBAC 完全独立

**领域事件 vs 应用层 Hook**：
- **领域事件**（domain 层定义，事后通知，不可中断）：用于审计/日志/通知等副作用
- **应用层 Hook 策略接口**（application 层定义，流程内扩展点，可中断）：用于校验/数据准备等需要影响流程的动作

---

## 四、聚合根与领域服务

### 4.1 认证上下文

**聚合根**：

```java
// 三套账号聚合根（独立ID类型，避免冲突）
InternetUser(InternetUserId, CustomerNo, LoginName, Status, CreatedAt, ...)
HqUser(HqUserId, StaffNo, LoginName, Status, CreatedAt, ...)
BranchUser(BranchUserId, BankCode, TellerNo, LoginName, Status, CreatedAt, ...)

// 凭据聚合根（一个账号可有多条凭据，类型可扩展）
Credential(CredentialId, ownerType, ownerId, credentialType, secret, Status, ...)
  - 方法：verify(input)              // 委托给 CredentialValidator 策略
  - 方法：changeSecret(newSecret, validator)
  - 业务规则：同一账号同一类型凭据只能有一条有效

// 二次授权会话（临时聚合根，带有效期）
SecondaryAuthSession(SessionId, branchUserId, internetUserId, strategyType, 
                     expiresAt, status, actingThroughRef)
  - 方法：isExpired()
  - 方法：markUsed()
  - 领域事件：SecondaryAuthCompletedEvent
```

**领域服务**：

```java
LoginService
  - login(channel, loginName, credentialInput, hook: LoginHook): LoginResult
    流程：1) preHook.preLogin()  2) 查找账号  3) 验证凭据 
         4) StpXxxUtil.login()  5) 注册 UserLoggedInEvent 
         6) postHook.postLoginSuccess()  7) 返回TokenInfo
    失败：注册 UserLoginFailedEvent + postHook.postLoginFailure()

SecondaryAuthService  
  - initiate(branchUserId, internetUserId, strategyType, input): SecondaryAuthSession
  - complete(sessionId, input): boolean     // 委托给 SecondaryAuthStrategy
  - revoke(sessionId)

IdentitySwitchService
  - switchTo(internetUserId): void          // StpBranchUtil.switchTo + 写入 SaSession 标记
  - switchBack(): void
```

**应用层 Hook 接口**：

```java
LoginHook {
    void preLogin(LoginContext ctx)        // 可抛异常中断
    void postLoginSuccess(LoginResult r)   // 不能中断
    void postLoginFailure(LoginFailure r)  // 不能中断
}
// 默认实现：NoOpLoginHook
// 可扩展：CaptchaLoginHook / IpCheckLoginHook / LoginNotificationHook
```

**领域事件**：

```
UserLoggedInEvent(userId, channel, loginTime, ip, userAgent)
UserLoginFailedEvent(loginName, channel, failTime, reason, ip)
UserLoggedOutEvent(userId, channel, logoutTime)
CredentialChangedEvent(userId, credentialType, changedBy, changedTime)
SecondaryAuthCompletedEvent(sessionId, branchUserId, internetUserId, strategyType, time)
```

### 4.2 RBAC 上下文

```java
Permission(PermissionId, code, name, type(MENU/BUTTON/API), parentId, sort)
Role(RoleId, code, name, scopeType(GLOBAL/PLAN/PRODUCT), scopeRef, status)
  - 业务规则：scopeType=GLOBAL 时 scopeRef 为空；PLAN/PRODUCT 时必填
  - 方法：assignPermission(permissionId)
  - 方法：revokePermission(permissionId)

UserRoleAssignment(AssignmentId, userId, userIdType, roleId, scopeType, scopeRef, status)
  - 一个用户多个角色 → 多条记录
  - 业务规则：用户角色绑定的 scopeType 必须与角色 scopeType 匹配

领域服务：
RoleService
  - createRole / updateRole / disableRole
  - assignPermissionToRole / revokePermissionFromRole
UserRoleService  
  - assignRole(userId, roleId, scope) / revokeRole
  - 注册 RoleAssignedEvent
MenuPermissionEvaluator
  - evaluate(userId, userIdType): Set<PermissionCode>  // 仅返回菜单/UI权限
```

### 4.3 业务授权上下文（PBAC 核心）

```java
BizOperation(BizOperationId, bizCode, actionCode, name, description)
  - 例如：BizOperation("BIZ_A", "APPLY", "业务A办理")
  - 例如：BizOperation("BIZ_A", "QUERY", "业务A查询")

BizAuthGrant(GrantId, subjectType, subjectId, bizCode, actionCodes, 
             inheritToSubordinates, status, validFrom, validTo)
  - 主体类型枚举：CUSTOMER / OPERATION_MODE / PRODUCT / PLAN / ACCOUNT_MANAGER
  - 业务规则：inheritToSubordinates=true 时，下属企业自动继承（仅 CUSTOMER 主体适用）
  - 方法：enable() / disable() / extendActions(actionCodes)
  - 领域事件：BizAuthGrantCreatedEvent / BizAuthGrantChangedEvent / BizAuthGrantRevokedEvent

领域服务：
BizAuthGrantService
  - createGrant / updateGrant / revokeGrant
  - queryGrants(subjectType, subjectId): List<BizAuthGrant>
  
BizPermissionEvaluator（核心计算引擎）
  - evaluate(userId, userIdType, planId): Set<BizPermissionCode>
    流程：
    1) 通过 PlanGateway 查询 planId 的属性（客户/产品/运作模式/账管人）
    2) 查询所有匹配主体的 BizAuthGrant：
       - CUSTOMER 主体匹配 plan.customerNo（含下属继承）
       - OPERATION_MODE 主体匹配 plan.operationMode
       - PRODUCT 主体匹配 plan.productNo
       - PLAN 主体精确匹配 planId
       - ACCOUNT_MANAGER 主体匹配 plan.accountManagerCode
    3) 对每条 grant：将 (bizCode, actionCodes) 拼接成权限码 PLAN:{planNo}:BIZ:{bizCode}:{action}
    4) 调用 AgencyPermissionResolver 注入代办关系带来的额外权限
    5) 返回最终权限码集合
```

### 4.4 代办关系上下文

```java
PlanAgency(PlanAgencyId, sourcePlanId, targetPlanId, grantedBizCodes, 
           grantedActions, status, validFrom, validTo)
  - 业务规则：sourcePlanId ≠ targetPlanId，不能自代办
  - 业务规则：不能形成代办环（A→B→A）
  - 领域事件：PlanAgencyCreatedEvent / PlanAgencyRevokedEvent

UserAgency(UserAgencyId, sourceUserId, sourceUserType, targetPlanId, 
           grantedBizCodes, grantedActions, status, validFrom, validTo)
  - 业务规则：sourceUser 必须是 INTERNET 用户
  - 领域事件：UserAgencyCreatedEvent / UserAgencyRevokedEvent

领域服务：
AgencyService
  - createPlanAgency / revokePlanAgency
  - createUserAgency / revokeUserAgency

AgencyPermissionResolver
  - resolve(userId, userIdType, currentPlanId): Set<BizPermissionCode>
    流程：
    1) 若 userId 是 INTERNET 经办人：
       a) 查询以 currentPlanId 为 source 的 PlanAgency → 取得 target 计划的授权
       b) 查询以 userId 为 source 的 UserAgency → 取得 target 计划的授权
       c) 对每条代办关系拼接权限码 PLAN:{targetPlanNo}:BIZ:{bizCode}:{action}
    2) 若 userId 是 BRANCH 柜员（已二次授权）：
       a) 取出 actingAs internetUserId，按上面流程计算
    3) HQ 用户：无代办关系，返回空集（HQ直接全权）
    返回：代办关系带来的额外权限码集合
```

### 4.5 操作审计上下文

```java
OperationAuditRecord(RecordId, timestamp, channel, 
                     actualUserId, actualUserType,
                     actingAsUserId, actingAsUserType,
                     actingThroughType,  // DIRECT/PLAN_AGENCY/USER_AGENCY/SECONDARY_AUTH
                     actingThroughRef,   // 对应聚合根ID
                     onBehalfOfSubject,  // 被代理的主体（如目标计划ID）
                     operation,          // 操作类型（如 BIZ:APPLY_A:apply）
                     resource,           // 资源标识
                     result,             // SUCCESS/FAILURE
                     ipAddress, userAgent)
  - append-only 聚合根，不修改不删除
  - 由审计切面/AOP 切面在业务操作前后自动写入

领域服务：
AuditService
  - record(record): void
  - query(filter): List<OperationAuditRecord>
```

### 4.6 路由鉴权上下文

```java
RouteRule(RuleId, routePattern, checkType(LOGIN/PERMISSION/ROLE/ANONYMOUS), 
          checkValue, channel, priority, enabled, description)
  - 业务规则：channel 用于按渠道差异化鉴权
  - 方法：enable() / disable()

领域服务：
RouteRuleService
  - createRule / updateRule / deleteRule / toggleRule
  - 注册 RouteRuleChangedEvent（用于清除缓存）

RouteRuleLoader
  - loadEnabledRules(): List<RouteRule>  // 带 Redis 缓存
  - clearCache(): void
```

---

## 五、防腐层 Gateway 接口

所有 Gateway 接口定义在 `iam-domain/gateway/`，实现在 `iam-infrastructure/`，通过 Retrofit 调用外部系统。

```java
// 客户/企业信息查询
CustomerGateway {
    CustomerInfo loadCustomer(CustomerNo customerNo);
    List<CustomerNo> loadSubCustomers(CustomerNo parentCustomerNo);
    Optional<CustomerNo> loadParentCustomer(CustomerNo customerNo);
}

// 产品信息查询
ProductGateway {
    ProductInfo loadProduct(ProductNo productNo);
    List<ProductInfo> loadProducts(List<ProductNo> productNos);
}

// 计划信息查询（核心，权限计算必用）
PlanGateway {
    PlanInfo loadPlan(PlanNo planNo);
    List<PlanInfo> loadPlans(List<PlanNo> planNos);
    List<PlanNo> loadPlanNosByCustomer(CustomerNo customerNo);
    List<PlanNo> loadPlanNosByGroup(CustomerNo groupCustomerNo);
}

// 账管人信息查询
AccountManagerGateway {
    AccountManagerInfo loadAccountManager(AccountManagerCode code);
}

// 通知/推送（可选）
NotificationGateway {
    void notifyPermissionChanged(userId, userIdType);
    void notifyLoginEvent(LoginEvent event);
}
```

**防腐层 DTO**（在 `iam-domain/gateway/dto/`，与 iam-service 自身 API DTO 隔离）：

```java
record CustomerInfo(CustomerNo customerNo, String name, CustomerType type, 
                    OperationMode operationMode, Optional<CustomerNo> parentCustomerNo)
record ProductInfo(ProductNo productNo, String name, OperationMode operationMode, 
                   AccountManagerCode accountManagerCode)
record PlanInfo(PlanNo planNo, String name, CustomerNo customerNo, 
                ProductNo productNo, OperationMode operationMode, 
                AccountManagerCode accountManagerCode)
record AccountManagerInfo(AccountManagerCode code, String name, String bankCode)
```

**实现要点**：
- 实现在 `iam-infrastructure/gateway/`，标注 `@Component`
- 通过 Retrofit 调用外部系统 HTTP API
- 配置外部系统 BaseURL 在 `application.yml`
- 调用失败抛 `SystemException(SERVICE.IAM.0071, ...)`
- 加入 `shared-client-starter` 的 Logbook 日志记录
- 可加 Caffeine 本地缓存（如 PlanInfo 这种基础数据，1分钟TTL）

---

## 六、API 设计

按项目约束（`@HttpExchange` + 仅 GET/POST + `ApiResult<T>` + DTO 转换走 MapStruct）。

### 6.1 认证 API（按渠道分接口）

```java
@HttpExchange("/internet/auth")
public interface InternetAuthApi {
    @PostExchange("/login")
    ApiResult<LoginResponse> login(@Valid @RequestBody InternetLoginRequest request);

    @PostExchange("/logout")
    ApiResult<Void> logout();

    @GetExchange("/current")
    ApiResult<InternetUserResponse> currentUser();

    @PostExchange("/change-credential")
    ApiResult<Void> changeCredential(@Valid @RequestBody ChangeCredentialRequest request);
}

@HttpExchange("/hq/auth")
public interface HqAuthApi {
    @PostExchange("/login")
    ApiResult<LoginResponse> login(@Valid @RequestBody HqLoginRequest request);

    @PostExchange("/logout")
    ApiResult<Void> logout();

    @GetExchange("/current")
    ApiResult<HqUserResponse> currentUser();
}

@HttpExchange("/branch/auth")
public interface BranchAuthApi {
    @PostExchange("/login")
    ApiResult<LoginResponse> login(@Valid @RequestBody BranchLoginRequest request);

    @PostExchange("/secondary-auth/initiate")
    ApiResult<SecondaryAuthResponse> initiateSecondaryAuth(
        @Valid @RequestBody InitiateSecondaryAuthRequest request);

    @PostExchange("/secondary-auth/complete")
    ApiResult<Void> completeSecondaryAuth(
        @Valid @RequestBody CompleteSecondaryAuthRequest request);

    @PostExchange("/secondary-auth/revoke")
    ApiResult<Void> revokeSecondaryAuth(@RequestBody RevokeSecondaryAuthRequest request);

    @PostExchange("/switch-back")
    ApiResult<Void> switchBackToTeller();
}
```

### 6.2 计划上下文 API（核心，触发权限重算）

```java
@HttpExchange("/context/plan")
public interface PlanContextApi {
    @GetExchange("/list")
    ApiResult<List<PlanSummaryResponse>> listAvailablePlans();

    @PostExchange("/select")
    ApiResult<PlanContextResponse> selectPlan(@Valid @RequestBody SelectPlanRequest request);

    @PostExchange("/switch")
    ApiResult<PlanContextResponse> switchPlan(@Valid @RequestBody SwitchPlanRequest request);

    @GetExchange("/permissions")
    ApiResult<List<String>> currentPlanPermissions();

    @GetExchange("/check")
    ApiResult<PermissionCheckResponse> checkPermission(@Valid @RequestBody PermissionCheckRequest request);
}
```

### 6.3 RBAC 管理 API

```java
@HttpExchange("/admin/role")
public interface RoleApi {
    @PostExchange("/create")
    ApiResult<RoleResponse> create(@Valid @RequestBody CreateRoleRequest request);
    @PostExchange("/update")
    ApiResult<Void> update(@Valid @RequestBody UpdateRoleRequest request);
    @PostExchange("/disable")
    ApiResult<Void> disable(@Valid @RequestBody DisableRoleRequest request);
    @GetExchange("/list")
    ApiResult<PageData<RoleResponse>> list(@Valid @RequestBody RoleQueryRequest request);
    @PostExchange("/permission/assign")
    ApiResult<Void> assignPermission(@Valid @RequestBody AssignPermissionRequest request);
    @PostExchange("/permission/revoke")
    ApiResult<Void> revokePermission(@Valid @RequestBody RevokePermissionRequest request);
}

@HttpExchange("/admin/permission")
public interface PermissionApi {
    @PostExchange("/create")
    ApiResult<PermissionResponse> create(@Valid @RequestBody CreatePermissionRequest request);
    @GetExchange("/tree")
    ApiResult<List<PermissionTreeNode>> tree();
    @GetExchange("/list")
    ApiResult<List<PermissionResponse>> list(@Valid @RequestBody PermissionQueryRequest request);
}

@HttpExchange("/admin/user-role")
public interface UserRoleApi {
    @PostExchange("/assign")
    ApiResult<Void> assignRole(@Valid @RequestBody AssignRoleRequest request);
    @PostExchange("/revoke")
    ApiResult<Void> revokeRole(@Valid @RequestBody RevokeRoleRequest request);
    @GetExchange("/list")
    ApiResult<List<UserRoleResponse>> listUserRoles(@Valid @RequestBody UserRoleQueryRequest request);
}
```

### 6.4 业务授权 API（PBAC 核心）

```java
@HttpExchange("/admin/biz-grant")
public interface BizAuthGrantApi {
    @PostExchange("/create")
    ApiResult<BizAuthGrantResponse> create(@Valid @RequestBody CreateBizAuthGrantRequest request);
    @PostExchange("/update")
    ApiResult<Void> update(@Valid @RequestBody UpdateBizAuthGrantRequest request);
    @PostExchange("/revoke")
    ApiResult<Void> revoke(@Valid @RequestBody RevokeBizAuthGrantRequest request);
    @GetExchange("/list")
    ApiResult<PageData<BizAuthGrantResponse>> list(@Valid @RequestBody BizAuthGrantQueryRequest request);
    @GetExchange("/evaluate")
    ApiResult<EvaluateResultResponse> evaluate(@Valid @RequestBody EvaluateRequest request);
}

@HttpExchange("/admin/biz-operation")
public interface BizOperationApi {
    @PostExchange("/create")
    ApiResult<BizOperationResponse> create(@Valid @RequestBody CreateBizOperationRequest request);
    @GetExchange("/list")
    ApiResult<List<BizOperationResponse>> list(@Valid @RequestBody BizOperationQueryRequest request);
}
```

### 6.5 代办关系 API

```java
@HttpExchange("/admin/plan-agency")
public interface PlanAgencyApi {
    @PostExchange("/create")
    ApiResult<PlanAgencyResponse> create(@Valid @RequestBody CreatePlanAgencyRequest request);
    @PostExchange("/revoke")
    ApiResult<Void> revoke(@Valid @RequestBody RevokePlanAgencyRequest request);
    @GetExchange("/list")
    ApiResult<PageData<PlanAgencyResponse>> list(@Valid @RequestBody PlanAgencyQueryRequest request);
}

@HttpExchange("/admin/user-agency")
public interface UserAgencyApi {
    @PostExchange("/create")
    ApiResult<UserAgencyResponse> create(@Valid @RequestBody CreateUserAgencyRequest request);
    @PostExchange("/revoke")
    ApiResult<Void> revoke(@Valid @RequestBody RevokeUserAgencyRequest request);
    @GetExchange("/list")
    ApiResult<PageData<UserAgencyResponse>> list(@Valid @RequestBody UserAgencyQueryRequest request);
}
```

### 6.6 路由规则 API

```java
@HttpExchange("/admin/route-rule")
public interface RouteRuleApi {
    @PostExchange("/create")
    ApiResult<RouteRuleResponse> create(@Valid @RequestBody CreateRouteRuleRequest request);
    @PostExchange("/update")
    ApiResult<Void> update(@Valid @RequestBody UpdateRouteRuleRequest request);
    @PostExchange("/toggle")
    ApiResult<Void> toggle(@Valid @RequestBody ToggleRouteRuleRequest request);
    @PostExchange("/clear-cache")
    ApiResult<Void> clearCache();
    @GetExchange("/list")
    ApiResult<PageData<RouteRuleResponse>> list(@Valid @RequestBody RouteRuleQueryRequest request);
}
```

### 6.7 用户管理 API（按渠道分）

```java
@HttpExchange("/admin/internet-user")
public interface InternetUserApi {
    @PostExchange("/create")
    ApiResult<InternetUserResponse> create(@Valid @RequestBody CreateInternetUserRequest request);
    @PostExchange("/update")
    ApiResult<Void> update(@Valid @RequestBody UpdateInternetUserRequest request);
    @PostExchange("/disable")
    ApiResult<Void> disable(@Valid @RequestBody DisableUserRequest request);
    @PostExchange("/reset-credential")
    ApiResult<Void> resetCredential(@Valid @RequestBody ResetCredentialRequest request);
    @GetExchange("/list")
    ApiResult<PageData<InternetUserResponse>> list(@Valid @RequestBody InternetUserQueryRequest request);
}
// HqUserApi / BranchUserApi 类似
```

### 6.8 审计 API

```java
@HttpExchange("/admin/audit")
public interface AuditApi {
    @GetExchange("/list")
    ApiResult<PageData<OperationAuditRecordResponse>> list(@Valid @RequestBody AuditQueryRequest request);
    @GetExchange("/detail")
    ApiResult<OperationAuditRecordResponse> detail(@Valid @RequestBody AuditDetailRequest request);
}
```

### 6.9 给其他业务服务调用的内部 API

```java
@HttpExchange("/internal/permission")
public interface InternalPermissionApi {
    @GetExchange("/check")
    ApiResult<PermissionCheckResponse> checkPermission(@Valid @RequestBody InternalPermissionCheckRequest request);

    @GetExchange("/user-permissions")
    ApiResult<List<String>> getUserPermissions(@Valid @RequestBody UserPermissionQueryRequest request);

    @PostExchange("/audit/record")
    ApiResult<Void> recordAudit(@Valid @RequestBody RecordAuditRequest request);
}
```

---

## 七、数据库表结构

按 06-数据库规范设计，PostgreSQL 为首选，同时提供 MySQL 兼容 DDL。所有表含通用字段（id/created_by/create_time/updated_by/update_time/deleted/version）。共 15 张表。

### 7.1 认证上下文（7张表）

```sql
-- 网上渠道经办人账号
CREATE TABLE t_iam_internet_user (
    id              BIGINT PRIMARY KEY,
    user_no         VARCHAR(32) NOT NULL,
    customer_no     VARCHAR(32) NOT NULL,
    login_name      VARCHAR(64) NOT NULL,
    display_name    VARCHAR(128),
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    last_login_time TIMESTAMP,
    last_login_ip   VARCHAR(64),
    created_by      VARCHAR(64),
    create_time     TIMESTAMP,
    updated_by      VARCHAR(64),
    update_time     TIMESTAMP,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    version         INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_internet_user_user_no UNIQUE (user_no),
    CONSTRAINT uk_internet_user_login_name UNIQUE (login_name)
);
CREATE INDEX idx_internet_user_customer ON t_iam_internet_user(customer_no);

-- 总部渠道运营人员账号
CREATE TABLE t_iam_hq_user (
    id              BIGINT PRIMARY KEY,
    staff_no        VARCHAR(32) NOT NULL,
    login_name      VARCHAR(64) NOT NULL,
    display_name    VARCHAR(128),
    department      VARCHAR(128),
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    last_login_time TIMESTAMP,
    last_login_ip   VARCHAR(64),
    created_by      VARCHAR(64),
    create_time     TIMESTAMP,
    updated_by      VARCHAR(64),
    update_time     TIMESTAMP,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    version         INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_hq_user_staff_no UNIQUE (staff_no),
    CONSTRAINT uk_hq_user_login_name UNIQUE (login_name)
);

-- 网点渠道柜员账号
CREATE TABLE t_iam_branch_user (
    id              BIGINT PRIMARY KEY,
    teller_no       VARCHAR(32) NOT NULL,
    bank_code       VARCHAR(16) NOT NULL,
    branch_code     VARCHAR(32) NOT NULL,
    login_name      VARCHAR(64) NOT NULL,
    display_name    VARCHAR(128),
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    last_login_time TIMESTAMP,
    last_login_ip   VARCHAR(64),
    created_by      VARCHAR(64),
    create_time     TIMESTAMP,
    updated_by      VARCHAR(64),
    update_time     TIMESTAMP,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    version         INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_branch_user_teller UNIQUE (bank_code, teller_no),
    CONSTRAINT uk_branch_user_login_name UNIQUE (login_name)
);
CREATE INDEX idx_branch_user_bank ON t_iam_branch_user(bank_code);

-- 凭据表（一个账号可有多条凭据，类型可扩展）
CREATE TABLE t_iam_credential (
    id              BIGINT PRIMARY KEY,
    owner_type      VARCHAR(16) NOT NULL,
    owner_id        BIGINT NOT NULL,
    credential_type VARCHAR(16) NOT NULL,
    secret          VARCHAR(512) NOT NULL,
    salt            VARCHAR(64),
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    last_changed_at TIMESTAMP,
    created_by      VARCHAR(64),
    create_time     TIMESTAMP,
    updated_by      VARCHAR(64),
    update_time     TIMESTAMP,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    version         INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_credential_owner_type UNIQUE (owner_type, owner_id, credential_type)
);
CREATE INDEX idx_credential_owner ON t_iam_credential(owner_type, owner_id);

-- 二次授权会话表
CREATE TABLE t_iam_secondary_auth_session (
    id                  BIGINT PRIMARY KEY,
    session_no          VARCHAR(32) NOT NULL,
    branch_user_id      BIGINT NOT NULL,
    internet_user_id    BIGINT NOT NULL,
    strategy_type       VARCHAR(32) NOT NULL,
    status              VARCHAR(16) NOT NULL,
    expires_at          TIMESTAMP NOT NULL,
    completed_at        TIMESTAMP,
    acting_through_ref  VARCHAR(64),
    created_by          VARCHAR(64),
    create_time         TIMESTAMP,
    updated_by          VARCHAR(64),
    update_time         TIMESTAMP,
    deleted             BOOLEAN NOT NULL DEFAULT FALSE,
    version             INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_secondary_auth_session_no UNIQUE (session_no)
);
CREATE INDEX idx_secondary_auth_branch ON t_iam_secondary_auth_session(branch_user_id);
CREATE INDEX idx_secondary_auth_expires ON t_iam_secondary_auth_session(expires_at);

-- 登录日志流水
CREATE TABLE t_iam_login_log (
    id              BIGINT PRIMARY KEY,
    user_type       VARCHAR(16) NOT NULL,
    user_id         BIGINT,
    login_name      VARCHAR(64) NOT NULL,
    channel         VARCHAR(16) NOT NULL,
    login_result    VARCHAR(16) NOT NULL,
    fail_reason     VARCHAR(64),
    login_time      TIMESTAMP NOT NULL,
    ip_address      VARCHAR(64),
    user_agent      VARCHAR(256),
    created_by      VARCHAR(64),
    create_time     TIMESTAMP,
    updated_by      VARCHAR(64),
    update_time     TIMESTAMP,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    version         INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_login_log_user ON t_iam_login_log(user_type, user_id);
CREATE INDEX idx_login_log_time ON t_iam_login_log(login_time);
```

### 7.2 RBAC 上下文（4张表）

```sql
-- 权限定义
CREATE TABLE t_iam_permission (
    id              BIGINT PRIMARY KEY,
    permission_code VARCHAR(128) NOT NULL,
    permission_name VARCHAR(128) NOT NULL,
    resource_type   VARCHAR(16) NOT NULL,
    parent_id       BIGINT,
    sort_order      INT DEFAULT 0,
    description     VARCHAR(256),
    created_by      VARCHAR(64),
    create_time     TIMESTAMP,
    updated_by      VARCHAR(64),
    update_time     TIMESTAMP,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    version         INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_permission_code UNIQUE (permission_code)
);
CREATE INDEX idx_permission_parent ON t_iam_permission(parent_id);

-- 角色定义
CREATE TABLE t_iam_role (
    id              BIGINT PRIMARY KEY,
    role_code       VARCHAR(64) NOT NULL,
    role_name       VARCHAR(128) NOT NULL,
    scope_type      VARCHAR(16) NOT NULL,
    description     VARCHAR(256),
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_by      VARCHAR(64),
    create_time     TIMESTAMP,
    updated_by      VARCHAR(64),
    update_time     TIMESTAMP,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    version         INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_role_code UNIQUE (role_code)
);

-- 角色-权限关联
CREATE TABLE t_iam_role_permission (
    id              BIGINT PRIMARY KEY,
    role_id         BIGINT NOT NULL,
    permission_id   BIGINT NOT NULL,
    created_by      VARCHAR(64),
    create_time     TIMESTAMP,
    updated_by      VARCHAR(64),
    update_time     TIMESTAMP,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    version         INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_role_permission UNIQUE (role_id, permission_id)
);
CREATE INDEX idx_role_permission_role ON t_iam_role_permission(role_id);

-- 用户-角色绑定
CREATE TABLE t_iam_user_role (
    id              BIGINT PRIMARY KEY,
    user_type       VARCHAR(16) NOT NULL,
    user_id         BIGINT NOT NULL,
    role_id         BIGINT NOT NULL,
    scope_type      VARCHAR(16) NOT NULL,
    scope_ref       VARCHAR(64),
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_by      VARCHAR(64),
    create_time     TIMESTAMP,
    updated_by      VARCHAR(64),
    update_time     TIMESTAMP,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    version         INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_role UNIQUE (user_type, user_id, role_id, scope_type, scope_ref)
);
CREATE INDEX idx_user_role_user ON t_iam_user_role(user_type, user_id);
CREATE INDEX idx_user_role_role ON t_iam_user_role(role_id);
```

### 7.3 业务授权上下文（2张表）

```sql
-- 业务+动作定义
CREATE TABLE t_iam_biz_operation (
    id              BIGINT PRIMARY KEY,
    biz_code        VARCHAR(32) NOT NULL,
    action_code     VARCHAR(32) NOT NULL,
    biz_name        VARCHAR(128) NOT NULL,
    action_name     VARCHAR(64) NOT NULL,
    description     VARCHAR(256),
    created_by      VARCHAR(64),
    create_time     TIMESTAMP,
    updated_by      VARCHAR(64),
    update_time     TIMESTAMP,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    version         INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_biz_operation UNIQUE (biz_code, action_code)
);

-- 业务授权记录（核心表）
CREATE TABLE t_iam_biz_auth_grant (
    id                          BIGINT PRIMARY KEY,
    grant_no                    VARCHAR(32) NOT NULL,
    subject_type                VARCHAR(32) NOT NULL,
    subject_id                  VARCHAR(64) NOT NULL,
    biz_code                    VARCHAR(32) NOT NULL,
    action_codes                VARCHAR(128) NOT NULL,
    inherit_to_subordinates     BOOLEAN NOT NULL DEFAULT FALSE,
    status                      VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    valid_from                  TIMESTAMP,
    valid_to                    TIMESTAMP,
    created_by                  VARCHAR(64),
    create_time                 TIMESTAMP,
    updated_by                  VARCHAR(64),
    update_time                 TIMESTAMP,
    deleted                     BOOLEAN NOT NULL DEFAULT FALSE,
    version                     INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_biz_auth_grant_no UNIQUE (grant_no)
);
CREATE INDEX idx_biz_auth_grant_subject ON t_iam_biz_auth_grant(subject_type, subject_id);
CREATE INDEX idx_biz_auth_grant_biz ON t_iam_biz_auth_grant(biz_code);
```

### 7.4 代办关系上下文（2张表）

```sql
-- 计划级代办
CREATE TABLE t_iam_plan_agency (
    id                  BIGINT PRIMARY KEY,
    agency_no           VARCHAR(32) NOT NULL,
    source_plan_no      VARCHAR(32) NOT NULL,
    target_plan_no      VARCHAR(32) NOT NULL,
    granted_biz_codes   VARCHAR(256) NOT NULL,
    granted_actions     VARCHAR(128) NOT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    valid_from          TIMESTAMP,
    valid_to            TIMESTAMP,
    created_by          VARCHAR(64),
    create_time         TIMESTAMP,
    updated_by          VARCHAR(64),
    update_time         TIMESTAMP,
    deleted             BOOLEAN NOT NULL DEFAULT FALSE,
    version             INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_plan_agency_no UNIQUE (agency_no),
    CONSTRAINT uk_plan_agency_pair UNIQUE (source_plan_no, target_plan_no)
);
CREATE INDEX idx_plan_agency_source ON t_iam_plan_agency(source_plan_no);
CREATE INDEX idx_plan_agency_target ON t_iam_plan_agency(target_plan_no);

-- 经办人级代办
CREATE TABLE t_iam_user_agency (
    id                  BIGINT PRIMARY KEY,
    agency_no           VARCHAR(32) NOT NULL,
    source_user_type    VARCHAR(16) NOT NULL DEFAULT 'INTERNET_USER',
    source_user_id      BIGINT NOT NULL,
    target_plan_no      VARCHAR(32) NOT NULL,
    granted_biz_codes   VARCHAR(256) NOT NULL,
    granted_actions     VARCHAR(128) NOT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    valid_from          TIMESTAMP,
    valid_to            TIMESTAMP,
    created_by          VARCHAR(64),
    create_time         TIMESTAMP,
    updated_by          VARCHAR(64),
    update_time         TIMESTAMP,
    deleted             BOOLEAN NOT NULL DEFAULT FALSE,
    version             INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_agency_no UNIQUE (agency_no),
    CONSTRAINT uk_user_agency_unique UNIQUE (source_user_type, source_user_id, target_plan_no)
);
CREATE INDEX idx_user_agency_source ON t_iam_user_agency(source_user_type, source_user_id);
CREATE INDEX idx_user_agency_target ON t_iam_user_agency(target_plan_no);
```

### 7.5 操作审计上下文（1张表）

```sql
CREATE TABLE t_iam_operation_audit (
    id                  BIGINT PRIMARY KEY,
    audit_no            VARCHAR(32) NOT NULL,
    audit_time          TIMESTAMP NOT NULL,
    channel             VARCHAR(16) NOT NULL,
    actual_user_type    VARCHAR(16) NOT NULL,
    actual_user_id      BIGINT NOT NULL,
    acting_as_user_type VARCHAR(16),
    acting_as_user_id   BIGINT,
    acting_through_type VARCHAR(16) NOT NULL DEFAULT 'DIRECT',
    acting_through_ref  VARCHAR(64),
    on_behalf_of_subject VARCHAR(128),
    operation           VARCHAR(64) NOT NULL,
    resource            VARCHAR(256),
    result              VARCHAR(16) NOT NULL,
    fail_reason         VARCHAR(256),
    ip_address          VARCHAR(64),
    user_agent          VARCHAR(256),
    created_by          VARCHAR(64),
    create_time         TIMESTAMP,
    updated_by          VARCHAR(64),
    update_time         TIMESTAMP,
    deleted             BOOLEAN NOT NULL DEFAULT FALSE,
    version             INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_audit_no UNIQUE (audit_no)
);
CREATE INDEX idx_audit_time ON t_iam_operation_audit(audit_time);
CREATE INDEX idx_audit_actual_user ON t_iam_operation_audit(actual_user_type, actual_user_id);
CREATE INDEX idx_audit_acting_as ON t_iam_operation_audit(acting_as_user_type, acting_as_user_id);
CREATE INDEX idx_audit_operation ON t_iam_operation_audit(operation);
```

### 7.6 路由鉴权上下文（1张表）

```sql
CREATE TABLE t_iam_route_rule (
    id              BIGINT PRIMARY KEY,
    rule_no         VARCHAR(32) NOT NULL,
    route_pattern   VARCHAR(256) NOT NULL,
    check_type      VARCHAR(16) NOT NULL,
    check_value     VARCHAR(256),
    channel         VARCHAR(16),
    priority        INT NOT NULL DEFAULT 0,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    description     VARCHAR(256),
    created_by      VARCHAR(64),
    create_time     TIMESTAMP,
    updated_by      VARCHAR(64),
    update_time     TIMESTAMP,
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    version         INT NOT NULL DEFAULT 0,
    CONSTRAINT uk_route_rule_no UNIQUE (rule_no)
);
CREATE INDEX idx_route_rule_enabled ON t_iam_route_rule(enabled, priority);
```

### 7.7 表汇总

| 子域 | 表名 | 用途 |
|---|---|---|
| 认证 | t_iam_internet_user / t_iam_hq_user / t_iam_branch_user | 三套账号 |
| 认证 | t_iam_credential | 凭据 |
| 认证 | t_iam_secondary_auth_session | 二次授权会话 |
| 认证 | t_iam_login_log | 登录日志流水 |
| RBAC | t_iam_permission / t_iam_role / t_iam_role_permission | 权限角色定义 |
| RBAC | t_iam_user_role | 用户-角色绑定 |
| PBAC | t_iam_biz_operation / t_iam_biz_auth_grant | 业务授权 |
| 代办 | t_iam_plan_agency / t_iam_user_agency | 代办关系 |
| 审计 | t_iam_operation_audit | 操作审计 |
| 路由 | t_iam_route_rule | 路由规则 |

共 15 张表。所有表使用 `shared-id-starter` 生成 BIGINT 主键，时间戳由应用层管理。

---

## 八、网关集成与缓存策略

### 8.1 网关集成（demo-gateway）

网关层引入 `sa-token-reactor-spring-boot3-starter`，统一处理登录校验和路由级权限校验。

```java
@Configuration
@AllArgsConstructor
public class SaTokenGatewayConfigure {
    private final RouteRuleLoader routeRuleLoader;

    @Bean
    @Order(-100)
    public SaReactorFilter saReactorFilter() {
        return new SaReactorFilter()
            .addInclude("/**")
            .addExclude("/internet/auth/login", "/hq/auth/login", "/branch/auth/login", 
                        "/favicon.ico", "/actuator/**")
            .setAuth(obj -> {
                // 1. 全局登录校验（识别渠道）
                SaRouter.match("/**").check(r -> {
                    ChannelType channel = ChannelResolver.resolve();
                    switch (channel) {
                        case INTERNET -> StpInternetUtil.checkLogin();
                        case HQ       -> StpHqUtil.checkLogin();
                        case BRANCH   -> StpBranchUtil.checkLogin();
                    }
                });
                
                // 2. 动态路由规则校验
                List<RouteRule> rules = routeRuleLoader.loadEnabledRules();
                for (RouteRule rule : rules) {
                    SaRouter.match(rule.routePattern()).check(r -> {
                        switch (rule.checkType()) {
                            case "permission" -> ChannelResolver.currentStp().checkPermission(rule.checkValue());
                            case "role"       -> ChannelResolver.currentStp().checkRole(rule.checkValue());
                            case "login"      -> ChannelResolver.currentStp().checkLogin();
                            case "anonymous"  -> { /* 不校验 */ }
                        }
                    });
                }
            })
            .setError(e -> {
                if (e instanceof NotLoginException) return SaResult.error("未登录").setCode(401);
                if (e instanceof NotPermissionException) return SaResult.error("无权限").setCode(403);
                if (e instanceof NotRoleException) return SaResult.error("无角色").setCode(403);
                return SaResult.error(e.getMessage());
            });
    }
}
```

**三套 StpLogic 工具类**（iam-api 模块提供）：

```java
public class StpInternetUtil {
    public static final String TYPE = "internet";
    public static StpLogic stpLogic = new StpLogic(TYPE);
    public static void login(Object id) { stpLogic.login(id); }
    public static void checkLogin() { stpLogic.checkLogin(); }
    public static Object getLoginId() { return stpLogic.getLoginId(); }
    public static void logout() { stpLogic.logout(); }
    // ... 其他委托方法
}
// StpHqUtil / StpBranchUtil 类似
```

### 8.2 业务服务集成

业务服务（如 annuity-service）：
- 引入 `sa-token-spring-boot3-starter`（Servlet 环境）
- 引入 `iam-api` 模块，使用 `InternalPermissionApi` 做 @HttpExchange 调用
- 实现 `StpInterface`，从 iam-service 拉取权限码（带 Redis 缓存）
- 方法级鉴权用 sa-token 标准 `@SaCheckPermission` 注解
- 业务权限校验用自定义 `@SaCheckBiz("BIZ:APPLY_A:apply")` 注解 + AOP 切面

```java
@Component
@AllArgsConstructor
public class StpInterfaceImpl implements StpInterface {
    private final InternalPermissionApi internalPermissionApi;
    private final StringRedisTemplate redisTemplate;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        String key = "iam:permission:%s:%s".formatted(loginType, loginId);
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) return parseList(cached);
        
        List<String> permissions = internalPermissionApi
            .getUserPermissions(new UserPermissionQueryRequest(loginType, loginId.toString()))
            .getData();
        
        redisTemplate.opsForValue().set(key, toJson(permissions), Duration.ofMinutes(30));
        return permissions;
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        // 类似实现
    }
}
```

**自定义业务权限注解**：

```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SaCheckBiz {
    String value();  // 如 "BIZ:APPLY_A:apply"
}

// AOP 切面
@Aspect
@Component
@AllArgsConstructor
public class SaCheckBizAspect {
    @Around("@annotation(saCheckBiz)")
    public Object around(ProceedingJoinPoint pjp, SaCheckBiz saCheckBiz) throws Throwable {
        String currentPlanNo = (String) StpXxxUtil.getTokenSession().get("currentPlanNo");
        String fullCode = "PLAN:" + currentPlanNo + ":" + saCheckBiz.value();
        StpXxxUtil.checkPermission(fullCode);
        return pjp.proceed();
    }
}
```

### 8.3 缓存策略

| 缓存对象 | 缓存层 | TTL | 失效方式 |
|---|---|---|---|
| 路由规则 RouteRule | Redis (网关侧) | 10 分钟 | 主动 `clearCache()` |
| 用户菜单权限 | Redis (业务侧) | 30 分钟 | 权限变更时主动失效 |
| 用户业务权限码 | Token-Session | 随会话 | 切换计划时重算 |
| PlanInfo/ProductInfo | Caffeine (iam 内) | 1 分钟 | TTL 自然失效 |
| 用户可选计划列表 | Account-Session | 30 分钟 | 权限变更时主动失效 |
| 二次授权会话状态 | Redis (sa-token) | 跟随 Token | 完成后标记 + TTL |

**缓存一致性原则**：
- 权限/角色变更时，通过 `NotificationGateway` 推送失效事件，业务侧订阅清除缓存
- 或采用简单方案：权限变更后等待 30 分钟自然失效（适合权限变更不频繁场景）

---

## 九、错误码分配

新增模块缩写 `IAM`，归入 `SERVICE` 域。需在 `08-错误码规范.md` 的 SERVICE 域分配表中追加 `IAM | iam-service` 一行。

| 错误码 | 含义 |
|---|---|
| SERVICE.IAM.0001 | 用户不存在 |
| SERVICE.IAM.0002 | 凭据无效 |
| SERVICE.IAM.0003 | 凭据已过期 |
| SERVICE.IAM.0004 | 账号已禁用 |
| SERVICE.IAM.0005 | 账号已锁定 |
| SERVICE.IAM.0006 | 登录失败次数超限 |
| SERVICE.IAM.0011 | 二次授权会话不存在 |
| SERVICE.IAM.0012 | 二次授权会话已过期 |
| SERVICE.IAM.0013 | 二次授权会话已完成 |
| SERVICE.IAM.0014 | 不支持的二次授权策略 |
| SERVICE.IAM.0015 | 当前身份非柜员，无法切换回柜员 |
| SERVICE.IAM.0021 | 角色不存在 |
| SERVICE.IAM.0022 | 角色已禁用 |
| SERVICE.IAM.0023 | 角色 scope 与绑定 scope 不匹配 |
| SERVICE.IAM.0024 | 权限码已存在 |
| SERVICE.IAM.0031 | 业务授权记录不存在 |
| SERVICE.IAM.0032 | 主体类型不支持 |
| SERVICE.IAM.0033 | 主体ID与主体类型不匹配 |
| SERVICE.IAM.0034 | 业务/动作不存在 |
| SERVICE.IAM.0041 | 计划代办关系不存在 |
| SERVICE.IAM.0042 | 计划代办关系形成环 |
| SERVICE.IAM.0043 | 经办人代办关系不存在 |
| SERVICE.IAM.0051 | 路由规则不存在 |
| SERVICE.IAM.0052 | 路由规则配置无效 |
| SERVICE.IAM.0061 | 当前计划不可选（数据权限校验失败） |
| SERVICE.IAM.0062 | 业务权限不足 |
| SERVICE.IAM.0071 | 外部系统调用失败 |
| SERVICE.IAM.0072 | 外部系统返回数据无效 |
| SERVICE.IAM.0099 | 未知错误 |

---

## 十、Maven 模块结构

```
iam-service/
├── iam-types/                  # 强类型ID（InternetUserId/HqUserId/BranchUserId/...）
├── iam-domain/                 # 6 子域聚合根 + 领域服务 + Gateway 接口 + 领域事件
├── iam-api/                    # @HttpExchange 接口 + DTO + StpXxxUtil
├── iam-application/            # 应用服务编排 + Hook 接口 + Listener
├── iam-adapter/                # Controller + MapStruct Converter
├── iam-infrastructure/         # Repository 实现 + Gateway 实现 + MyBatis-Flex
└── iam-starter/                # 启动类 + application.yml + schema-pg.sql + schema-mysql.sql
```

**父 pom.xml 调整**：

- `properties` 增加 `sa-token.version=1.45.0`
- `dependencyManagement` 增加 `iam-api` 依赖声明（在 `<!-- 2nd Dependencies-->` 部分）
- `modules` 增加 `iam-service`

**各业务服务调用 iam**：
- `dependencyManagement` 增加 `iam-types` `iam-domain` `iam-api` 等
- 需要使用 `@HttpExchange` 调用 iam 的服务，引入 `io.github.danielliu1123:httpexchange-spring-boot-autoconfigure`
- 业务服务引入 sa-token `sa-token-spring-boot3-starter` + `sa-token-redis-jackson`

---

## 十一、开闭原则扩展点清单

| 扩展点 | 策略接口 | 当前实现 | 未来可扩展 |
|---|---|---|---|
| 凭据类型 | `CredentialValidator` | PasswordCredentialValidator | UKeyValidator / OTPValidator / 证书Validator |
| 二次授权方式 | `SecondaryAuthStrategy` | CredentialSecondaryAuthStrategy | AuthorizationCodeStrategy / ScanStrategy |
| 主体类型 | `SubjectType` 枚举 + `BizAuthSubjectResolver` | CUSTOMER/OPERATION_MODE/PRODUCT/PLAN/ACCOUNT_MANAGER | 新增主体类型 |
| 渠道 | `ChannelType` 枚举 + `ChannelLoginStrategy` | INTERNET/HQ/BRANCH | 新增渠道 |
| 权限位 | `BizAction` 枚举 | APPLY/QUERY/AUDIT | 新增动作 |
| 继承策略 | `BizAuthInheritancePolicy` | DefaultInheritancePolicy（下属企业默认继承） | 客户定制继承策略 |
| 登录流程 | `LoginHook` | NoOpLoginHook | CaptchaLoginHook / IpCheckLoginHook / LoginNotificationHook |

---

## 十二、领域事件清单

### 12.1 认证上下文事件

```
UserLoggedInEvent(userId, channel, loginTime, ip, userAgent)
UserLoginFailedEvent(loginName, channel, failTime, reason, ip)
UserLoggedOutEvent(userId, channel, logoutTime)
CredentialChangedEvent(userId, credentialType, changedBy, changedTime)
SecondaryAuthCompletedEvent(sessionId, branchUserId, internetUserId, strategyType, time)
SecondaryAuthRevokedEvent(sessionId, revokedBy, revokeTime)
```

### 12.2 RBAC 上下文事件

```
RoleCreatedEvent(roleId, roleCode, scopeType, scopeRef, createdBy, createdAt)
RoleUpdatedEvent(roleId, updatedBy, updatedAt)
RoleDisabledEvent(roleId, disabledBy, disabledAt)
RoleAssignedEvent(userType, userId, roleId, scopeType, scopeRef, assignedBy, assignedAt)
RoleRevokedEvent(userType, userId, roleId, scopeType, scopeRef, revokedBy, revokedAt)
PermissionAssignedEvent(roleId, permissionId, assignedBy, assignedAt)
```

### 12.3 业务授权上下文事件

```
BizAuthGrantCreatedEvent(grantId, subjectType, subjectId, bizCode, actionCodes, createdBy, createdAt)
BizAuthGrantChangedEvent(grantId, changes, updatedBy, updatedAt)
BizAuthGrantRevokedEvent(grantId, revokedBy, revokedAt)
```

### 12.4 代办关系上下文事件

```
PlanAgencyCreatedEvent(agencyId, sourcePlanNo, targetPlanNo, grantedBizCodes, createdBy, createdAt)
PlanAgencyRevokedEvent(agencyId, revokedBy, revokedAt)
UserAgencyCreatedEvent(agencyId, sourceUserId, targetPlanNo, grantedBizCodes, createdBy, createdAt)
UserAgencyRevokedEvent(agencyId, revokedBy, revokedAt)
```

### 12.5 路由鉴权上下文事件

```
RouteRuleCreatedEvent(ruleId, routePattern, checkType, createdBy, createdAt)
RouteRuleUpdatedEvent(ruleId, changes, updatedBy, updatedAt)
RouteRuleToggledEvent(ruleId, enabled, toggledBy, toggledAt)
```

---

## 十三、关键设计权衡记录

### 13.1 为何不采用方案 B（单一 sa-token + 渠道前缀）

- 三套账号 ID 类型独立，单一 sa-token 体系需要"渠道前缀 + ID"合成字符串，Long → "INTERNET:10001" 类型转换易出错
- 多套 StpLogic 是 sa-token 官方推荐做法（文档第 21 章）
- 渠道物理隔离更彻底，权限缓存命名空间不冲突

### 13.2 为何业务权限码采用 `PLAN:{planNo}:BIZ:{bizCode}:{action}` 格式

- 复用 sa-token 原生 `StpUtil.hasPermission()` 能力，与"基于 sa-token 实现"初衷契合
- 权限码自解释，便于审计和调试
- 权限码存 Token-Session（每用户每会话一份），不进数据库，无容量问题
- 替代方案 `BIZ:{bizCode}:{action}` + 数据范围校验需要自定义鉴权逻辑，与 sa-token 解耦太深

### 13.3 为何权限计算时机选择"选择计划时动态拼接"

- 登录时全量预计算会导致权限码过多（用户数×计划数×业务数×动作数），内存压力大
- 鉴权时实时查询性能差
- 选择计划时动态拼接是性能与实时性的平衡，且符合业务流程（先选计划再办业务）

### 13.4 为何二次授权合并非独立上下文

- 二次授权本质是"在已登录基础上的二次认证"，是动作而非持久化领域
- sa-token 已有"二级认证"概念（`@SaCheckSafe`），二次授权就是带策略的二级认证
- 二次授权策略作为认证上下文内的策略接口，会话作为认证上下文内的临时聚合根

### 13.5 为何操作审计独立上下文

- 审计是横切关注点，独立于业务领域
- append-only 模型，不修改不删除
- 需要记录"谁替谁代办"完整链路，涉及多个子域信息

---

## 十四、待办事项与未决问题

### 14.1 实现阶段需细化

1. **`shared-crypto-starter` 集成**：凭据 secret 字段加密存储，密钥通过环境变量注入
2. **密码加密策略**：默认采用 BCrypt（项目 user_profile 偏好），通过 `CredentialValidator` 抽象支持其他算法
3. **登录失败锁定策略**：连续失败 N 次后锁定账号 M 分钟，需在 LoginService 中实现
4. **二次授权会话清理**：定时任务清理过期会话（应用层管理时间戳，参考 user_profile 偏好）
5. **权限变更通知机制**：通过 `NotificationGateway` 推送 + 业务侧订阅，或采用简单 30 分钟自然失效
6. **`@SaCheckBiz` AOP 实现**：在 iam-api 模块提供注解定义，业务服务引入后通过 BeanPostProcessor 自动注册切面

### 14.2 跨服务集成待确认

1. **外部系统 API 文档**：CustomerGateway/ProductGateway/PlanGateway/AccountManagerGateway 对应的外部系统接口契约待提供
2. **网关 RouteRuleLoader 实现**：网关侧如何调用 iam-service（Feign 还是直接 HTTP），需在网关集成阶段确认
3. **业务服务集成次序**：annuity-service 是第一个集成 iam 的业务服务，作为参考实现

### 14.3 后续演进方向

1. **SSO 集成**：未来如有第三套前端系统（如移动端），可基于 sa-token SSO 模式二扩展
2. **OAuth2 Server**：如需对外提供 API 给第三方系统，可基于 sa-token OAuth2 扩展
3. **权限审计报表**：基于 t_iam_operation_audit 表做操作链路分析
4. **权限模拟器**：在管理后台提供"以某用户视角查看权限"的调试工具

---

## 十五、附录

### 15.1 关键概念术语表

| 术语 | 含义 |
|---|---|
| 渠道 (Channel) | 用户访问系统的入口，分 INTERNET/HQ/BRANCH 三种 |
| 经办人 (Internet User) | 客户企业的 HR，通过网上渠道办理业务 |
| 运营人员 (HQ User) | 本公司员工，通过总部渠道办理业务 |
| 柜员 (Branch User) | 合作银行员工，通过网点渠道办理业务 |
| 凭据 (Credential) | 用户登录的凭证，类型可扩展（密码/UKey/OTP/证书） |
| 二次授权 (Secondary Auth) | 网点柜员登录后，需经办人凭据再次验证才能办理业务的认证流程 |
| 身份切换 (Identity Switch) | 柜员二次授权后，临时切换为经办人身份办理业务 |
| 主体 (Subject) | 业务授权的对象类型，分 CUSTOMER/OPERATION_MODE/PRODUCT/PLAN/ACCOUNT_MANAGER 五种 |
| 业务权限码 | 格式 `PLAN:{planNo}:BIZ:{bizCode}:{action}`，运行时动态生成 |
| 计划级代办 (Plan Agency) | 计划 A 授权给计划 B 代办，A 下所有经办人拥有 B 的授权业务和权限 |
| 经办人级代办 (User Agency) | 指定经办人代办指定计划的指定业务和权限 |
| 三层权限模型 | RBAC(UI) + PBAC(业务) + 数据权限(隐式) |
| Hook (应用层) | 流程内扩展点，可中断流程 |
| 领域事件 | 事后通知，不可中断流程 |

### 15.2 sa-token 集成依赖清单

**iam-starter/pom.xml**：
```xml
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-spring-boot3-starter</artifactId>
</dependency>
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-redis-jackson</artifactId>
</dependency>
```

**demo-gateway/pom.xml**：
```xml
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-reactor-spring-boot3-starter</artifactId>
</dependency>
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-redis-jackson</artifactId>
</dependency>
```

**业务服务（如 annuity-starter）**：
```xml
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-spring-boot3-starter</artifactId>
</dependency>
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-redis-jackson</artifactId>
</dependency>
<dependency>
    <groupId>com.example</groupId>
    <artifactId>iam-api</artifactId>
</dependency>
<dependency>
    <groupId>io.github.danielliu1123</groupId>
    <artifactId>httpexchange-spring-boot-autoconfigure</artifactId>
</dependency>
```
