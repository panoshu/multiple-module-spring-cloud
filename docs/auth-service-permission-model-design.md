# auth-service 权限模型设计文档

> **文档状态**：已确认，待实施  
> **创建日期**：2026-07-28  
> **作者**：auth-service 设计团队

---

## 一、服务定位与边界

### 1.1 服务命名

新建 `auth-service`，负责**身份认证与访问管理（IAM）**，基于 Sa-Token 框架实现。

### 1.2 模块划分

遵循项目 DDD + 六边形架构分层规范：

```
auth-service/
  ├── auth-types/            # ID原语：UserNo, RoleId, CredentialId, DelegationId
  ├── auth-domain/           # 领域模型：用户聚合、凭据实体、角色、权限、业务范围、代办关系
  ├── auth-api/              # 对外接口：登录、用户管理、权限查询、菜单获取
  ├── auth-application/      # 应用服务：登录流程编排、权限判定编排
  ├── auth-adapter/          # Controller：REST接口
  ├── auth-infrastructure/   # Repository实现、防腐层网关实现、Sa-Token集成
  └── auth-starter/          # 启动类、配置
```

### 1.3 职责边界

**拥有的能力：**
- 用户管理（用户账号、状态、渠道归属）
- 凭据管理（密码、UKEY、可扩展其他凭据类型）
- 角色管理（角色定义、角色-权限映射）
- 业务可用性配置（按客户/产品/计划/运作模式/账管人维度）
- 计划角色分配（计划-经办人-角色）
- 代办关系管理（计划级代办、经办人级代办）
- 登录认证（Sa-Token 集成、多渠道登录）
- 权限判定引擎（两层串联 + 代办 + 继承）
- 菜单构建（按用户权限过滤菜单树）

**不拥有（通过防腐层网关查回使用）：**
- 客户信息（客户编号、名称、层级关系）→ `CustomerGateway`
- 产品信息（产品编号、运作模式、账管人）→ `ProductGateway`
- 计划信息（计划编号、归属客户/产品）→ `PlanGateway`
- 业务定义（业务类型、可配置的操作）→ `BusinessCatalogGateway`

### 1.4 设计原则

1. **客户/产品/计划数据零维护**：外部系统维护，auth-service 只查询使用
2. **权限判定服务化**：其他服务通过 `auth-api` 调用权限判定接口
3. **JWT + Redis 认证**：Access Token (JWT) 做无状态认证，微服务本地验证签名零网络调用；Refresh Token + Redis 管理会话；权限判定由自定义 `PermissionEngine` 处理
4. **凭据策略可扩展**：密码、UKEY 等凭据类型通过策略模式抽象，新增凭据类型不改现有代码（OCP）
5. **渠道策略可扩展**：新增渠道不改现有代码（OCP）

---

## 二、核心领域模型

### 2.1 聚合根全景

| 聚合根 | 职责 | 关键实体/值对象 |
|--------|------|----------------|
| User | 用户账号管理 | Credential（凭据实体） |
| Role | 角色定义与权限映射 | RolePermission（角色权限实体） |
| BusinessScope | 业务可用性配置 | — |
| PlanRoleAssignment | 计划-经办人-角色分配 | — |
| Delegation | 代办关系 | DelegatedPermission（代办权限项，值对象） |
| SecondaryAuth | 网点渠道二次授权 | — |
| Menu | 菜单管理（树形结构） | — |

### 2.2 防腐层网关接口

| 网关接口 | 职责 |
|----------|------|
| CustomerGateway | 查询客户信息、客户层级关系（集团-子公司） |
| ProductGateway | 查询产品信息、运作模式、账管人 |
| PlanGateway | 查询计划信息、归属客户/产品 |
| BusinessCatalogGateway | 查询业务类型定义、可配置的操作类型 |

### 2.3 User 聚合根

```java
public class User extends AggregateRoot<UserNo> {
    private UserNo id;
    private String username;
    private String realName;
    private UserStatus status;          // ACTIVE / LOCKED / DISABLED
    private Channel channel;            // NETAPP / CJ_TELLER / BANK_BRANCH
    private CustomerNo customerNo;      // 网上渠道用户所属客户（总部/网点渠道为null）
    private List<Credential> credentials;
    
    public void login(CredentialType type, String rawCredential);
    public void lock();
    public void unlock();
    public void addCredential(Credential credential);
    public void revokeCredential(CredentialId id);
}
```

### 2.4 Credential 实体（凭据策略模式）

```java
public class Credential extends Entity<CredentialId> {
    private CredentialId id;
    private CredentialType type;        // PASSWORD / UKEY / ...（可扩展）
    private String credentialValue;     // 加密存储
    private CredentialStatus status;    // ACTIVE / EXPIRED / REVOKED
    
    public boolean verify(String rawInput);  // 委托给 CredentialValidator 策略
}
```

凭据校验策略：

| 策略实现 | 校验方式 |
|----------|----------|
| PasswordValidator | BCrypt 等哈希校验 |
| UKeyValidator | UKEY 证书校验 |
| （可扩展） | 新增凭据类型只需实现 CredentialValidator 接口 |

### 2.5 Role 聚合根

```java
public class Role extends AggregateRoot<RoleId> {
    private RoleId id;
    private String roleCode;            // 如 HANDLER_ADMIN / HANDLER_OPERATOR
    private String roleName;
    private Channel channel;            // 角色适用的渠道（null=通用）
    private List<RolePermission> permissions;
    
    public void grantPermission(BusinessType bizType, Operation operation);
    public void revokePermission(BusinessType bizType, Operation operation);
}

public class RolePermission extends Entity<RolePermissionId> {
    private BusinessType businessType;  // 如 CONTRIBUTION（缴费）
    private Operation operation;        // HANDLE / QUERY / AUDIT
}
```

### 2.6 BusinessScope 聚合根

```java
public class BusinessScope extends AggregateRoot<ScopeId> {
    private ScopeId id;
    private ScopeType scopeType;        // CUSTOMER / PRODUCT / PLAN / 
                                        // OPERATION_MODE / ACCOUNT_MANAGER
    private String scopeTarget;         // 对应维度的编号
    private List<BusinessType> allowedBusinesses;  // 允许的业务类型列表
    private boolean inheritable;        // 客户级配置：下属企业是否继承
    private ScopeStatus status;         // ACTIVE / DISABLED
}
```

### 2.7 PlanRoleAssignment 聚合根

```java
public class PlanRoleAssignment extends AggregateRoot<AssignmentId> {
    private AssignmentId id;
    private PlanNo planNo;              // 计划编号（外部引用）
    private UserNo userNo;              // 经办人
    private RoleId roleId;              // 分配的角色
    private AssignmentStatus status;    // ACTIVE / REVOKED
    private LocalDateTime assignedAt;
    
    public void revoke();
}
```

### 2.8 Delegation 聚合根

```java
public class Delegation extends AggregateRoot<DelegationId> {
    private DelegationId id;
    private DelegationType type;        // PLAN_LEVEL / HANDLER_LEVEL
    private PlanNo sourcePlanNo;        // 授权方计划
    private List<PlanNo> targetPlanNos; // 被授权的计划列表
    private List<DelegatedPermission> delegatedPermissions;
    private List<UserNo> designatedHandlers;    // 仅 HANDLER_LEVEL
    private DelegationStatus status;    // ACTIVE / EXPIRED / REVOKED
    private LocalDateTime expiresAt;
}

public record DelegatedPermission(
    BusinessType businessType,
    Operation operation
) implements ValueObject {}
```

### 2.9 SecondaryAuth 聚合根（网点渠道专用）

```java
public class SecondaryAuth extends AggregateRoot<AuthId> {
    private AuthId id;
    private UserNo tellerUserNo;        // 网点柜员
    private UserNo handlerUserNo;       // 授权经办人
    private CustomerNo customerNo;      // 经办人所属客户
    private AuthStatus status;          // PENDING / AUTHORIZED / EXPIRED / REVOKED
    private LocalDateTime authorizedAt;
    private LocalDateTime expiresAt;
    
    public void authorize(UserNo handler);
    public boolean isValid();
}
```

---

## 三、权限判定引擎

### 3.1 认证体系：JWT + Redis

认证层采用 **Access Token (JWT) + Refresh Token (Redis)** 双 Token 模型，权限判定由自定义 `PermissionEngine` 处理。

**为什么不用 Sa-Token：**
Sa-Token 的权限模型是扁平 `List<String>`，无法表达「用户×计划×业务×操作」的多维权限，权限能力被完全架空。剩余的认证基础设施（Token 生成、会话存储、踢人下线）用 JWT + Redis 实现更轻量，且微服务可本地验证 JWT 签名，无需网络调用。

**Access Token (JWT)：**
- 短有效期（30分钟），HS256 签名
- Payload 包含：`userId`, `channel`, `customerNo`, `impersonatedUserId`（网点二次授权场景）, `jti`（Token唯一ID）
- 微服务本地验证签名，零网络调用
- 网关层解析 Payload 后通过 Header 透传用户信息给下游服务

**Refresh Token (Redis)：**
- 长有效期（7天），UUID 格式
- Redis Key: `refresh:{userId}:{deviceId}` → Value: refreshToken
- 用于刷新 Access Token，过期后需重新登录
- 踢人下线 = 删除 Refresh Token + 将 Access Token 的 jti 加入 Redis 黑名单

**Redis 会话管理：**
- Key: `session:{userId}` → Redis Hash，包含 channel, customerNo, loginTime, secondaryAuthId 等
- 网点二次授权信息存储在会话中
- 账号封禁：Key `disabled:{userId}` → 标记，网关层检查

**踢人下线 / 账号封禁：**
- 踢人下线：删除 `refresh:{userId}:*`（所有设备），将当前 Access Token 的 jti 加入 `blacklist:{jti}`（TTL = Access Token 剩余有效期）
- 账号封禁：设置 `disabled:{userId}`，网关层检查拦截

**与权限引擎的关系：**
```
请求 → 网关(验证JWT签名 + 检查黑名单/封禁) → 业务服务(从Header获取用户信息)
  → 业务服务(调auth-api → PermissionEngine.check())
```

### 3.2 判定流水线

权限判定采用**两层串联 + 代办 + 继承**模型：

```
输入：PermissionContext(userId, channel, planNo, businessType, operation)

Step 1: 渠道计划访问检查
  └─ ChannelStrategy.canAccessPlan(userId, planNo)

Step 2: 业务可用性判定（Layer 1 - 分层覆盖）
  └─ BusinessScopeResolver.resolve(planNo, businessType)
     优先级：计划级 > 产品级 > 客户级（含继承）> 运作模式级 > 账管人级

Step 3: 用户授权判定（Layer 2 - 角色权限）
  └─ RolePermissionChecker.check(userId, planNo, businessType, operation)

Step 4: 代办关系判定（如果 Step 3 不通过）
  └─ DelegationEvaluator.evaluate(userId, planNo, businessType, operation)

最终结果 = Step1 AND Step2 AND (Step3 OR Step4)
```

### 3.3 权限判定接口

```java
public interface PermissionEngine {
    
    // 判定单个权限
    PermissionResult check(PermissionContext context);
    
    // 批量查询用户在某计划下的所有权限（用于菜单构建）
    List<PermissionItem> queryPermissions(UserNo userId, Channel channel, PlanNo planNo);
    
    // 查询用户可访问的计划列表
    List<PlanNo> queryAccessiblePlans(UserNo userId, Channel channel);
}
```

### 3.4 缓存策略

三级缓存：

| 层级 | 存储 | 内容 | TTL |
|------|------|------|-----|
| L1 | Redis Session | 用户基本信息、权限快照、二次授权状态 | 随会话有效期 |
| L2 | Redis (共享) | BusinessScope配置、Delegation关系、PlanRoleAssignment | 5~15分钟 + 主动失效 |
| L3 | 数据库 | 所有权限配置数据 | 持久化 |

缓存失效策略：
- 配置变更时通过领域事件主动清除对应缓存
- 代办关系变更时清除相关计划缓存
- 角色权限变更时清除所有使用该角色的缓存

---

## 四、渠道策略与登录流程

### 4.1 渠道策略接口

```java
public interface ChannelStrategy {
    LoginResult login(LoginRequest request);
    List<PlanNo> getAccessiblePlans(UserNo userId, ChannelContext context);
    boolean canAccessPlan(UserNo userId, PlanNo planNo, ChannelContext context);
}
```

### 4.2 各渠道策略

| 渠道 | 登录方式 | 可选计划范围 | 特殊逻辑 |
|------|----------|-------------|----------|
| NETAPP（网上渠道） | 账号+密码 | 自己客户的计划 | — |
| CJ_TELLER（总部渠道） | 账号+密码 | 所有计划 | — |
| BANK_BRANCH（网点渠道） | 柜员账号+密码 → 经办人二次授权 | 授权经办人客户的计划 | 二次授权，柜员以经办人身份操作 |

### 4.3 网点渠道二次授权流程

1. 柜员登录（账号+密码）→ 获得 Access Token + Refresh Token（仅能访问公开接口）
2. 柜员输入经办人账号 → 通知经办人
3. 经办人输入凭据（密码/UKEY）→ 校验通过
4. 创建 SecondaryAuth，更新 Redis Session（写入 impersonatedUserId），签发新的 Access Token（包含 impersonatedUserId）
5. 柜员办理业务时，PermissionContext.userId = 经办人userId（从 JWT payload 的 impersonatedUserId 解析），channel = BANK_BRANCH
6. 授权过期或柜员主动退出 → 清除 SecondaryAuth，签发不含 impersonatedUserId 的新 Access Token

---

## 五、代办关系与继承机制

### 5.1 两种代办类型

**PLAN_LEVEL（计划级代办）：**
- 计划A授权计划B，计划A下所有经办人获得计划B的指定业务+权限
- 示例：P001授权P002，P001下所有经办人可在P002上办理缴费(办理+查询)和待遇查询

**HANDLER_LEVEL（经办人级代办）：**
- 计划A指定部分经办人代办指定计划的指定业务+权限
- 示例：P001指定经办人U001/U002/U003在P002/P003上办理缴费(办理)和待遇(审核)
- 经办人U004/U005不享有此代办权限

### 5.2 代办在权限判定中的位置

当用户在计划P001上的直接角色权限（Step 3）不通过时，系统检查代办关系（Step 4）：
1. 查询用户所属的所有其他计划
2. 对每个用户所属计划 P_other，检查是否存在有效的 Delegation
3. PLAN_LEVEL：检查 source=P_other, target=P001 的代办，验证 delegatedPermissions
4. HANDLER_LEVEL：检查 source=P_other, target=P001, designatedHandlers 包含 userId 的代办

### 5.3 集团客户继承机制

- 客户级 BusinessScope 配置可标记 `inheritable=true`
- 子企业没有自己的配置时，沿客户层级树向上查找可继承的配置
- 子企业有自己的配置时，覆盖继承的配置（分层覆盖原则）
- `inheritable=false` 时，配置只对当前客户生效，不向下继承

---

## 六、菜单关联与 API 设计

### 6.1 菜单与权限的关联

菜单通过 `(business_type, required_operation)` 与权限体系关联：
- 菜单的 `business_type` 对应业务类型（如 CONTRIBUTION）
- 菜单的 `required_operation` 对应操作（HANDLE/QUERY/AUDIT）
- `required_operation=null` 表示查看菜单本身不需要操作权限
- 菜单的 `channel_mask` 控制哪些渠道可见此菜单（位掩码）

### 6.2 菜单构建流程

1. 前端选择计划 P001，请求菜单树
2. 后端查询用户在 P001 下的所有权限
3. 查询所有可见菜单
4. 按渠道过滤（channel_mask）
5. 按权限过滤（菜单的 business_type+required_operation 是否在用户权限列表中）
6. 构建树形结构，过滤掉没有可见子菜单的目录
7. 返回菜单树

### 6.3 auth-api 接口定义

```java
@HttpExchange("/auth")
public interface AuthApi {
    
    // 权限校验（其他服务调用）
    @PostExchange("/permission/check")
    ApiResult<PermissionResult> checkPermission(@Valid @RequestBody PermissionCheckRequest request);
    
    // 查询用户在某计划下的所有权限
    @PostExchange("/permission/query")
    ApiResult<List<PermissionItem>> queryPermissions(@Valid @RequestBody PermissionQueryRequest request);
    
    // 查询用户可访问的计划列表
    @PostExchange("/plan/accessible")
    ApiResult<List<PlanInfo>> queryAccessiblePlans(@Valid @RequestBody AccessiblePlanQuery request);
    
    // 获取用户菜单树
    @GetExchange("/menu/tree")
    ApiResult<List<MenuNode>> getMenuTree(@RequestParam String planNo);
}
```

### 6.4 业务服务集成方式

- **方式1（推荐）**：业务服务通过 auth-api Feign 调用做权限校验
- **方式2**：网关层统一拦截，调用 auth-api 做路由级权限校验
- 推荐组合使用：网关层做登录校验 + 粗粒度路由鉴权，服务内做细粒度业务鉴权

### 6.5 权限码命名规范

格式：`{业务类型}:{操作}`

| 权限码 | 含义 |
|--------|------|
| CONTRIBUTION:HANDLE | 缴费办理 |
| CONTRIBUTION:QUERY | 缴费查询 |
| CONTRIBUTION:AUDIT | 缴费审核 |
| PAYMENT:HANDLE | 待遇支付办理 |
| PAYMENT:QUERY | 待遇支付查询 |
| TRANSFER:HANDLE | 转移办理 |

---

## 七、数据库表结构

### 7.1 表总览

| 表名 | 说明 |
|------|------|
| t_auth_user | 用户表 |
| t_auth_credential | 凭据表 |
| t_auth_role | 角色表 |
| t_auth_role_permission | 角色权限表 |
| t_auth_business_scope | 业务可用性配置表 |
| t_auth_plan_role_assignment | 计划角色分配表 |
| t_auth_delegation | 代办关系表 |
| t_auth_secondary_auth | 二次授权表（网点渠道） |
| t_auth_menu | 菜单表 |

### 7.2 PostgreSQL DDL

```sql
-- =============================================================================
-- 1. 用户表
-- =============================================================================
CREATE TABLE IF NOT EXISTS t_auth_user (
    id              VARCHAR(64)   NOT NULL,
    username        VARCHAR(64)   NOT NULL,
    real_name       VARCHAR(128),
    status          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    channel         VARCHAR(20)   NOT NULL,
    customer_no     VARCHAR(64),
    phone           VARCHAR(20),
    email           VARCHAR(128),
    last_login_time TIMESTAMP,
    created_by      VARCHAR(64)   NOT NULL,
    updated_by      VARCHAR(64),
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN       DEFAULT FALSE,
    version         INT           DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_auth_user_username ON t_auth_user(username) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_auth_user_customer_no ON t_auth_user(customer_no);
CREATE INDEX IF NOT EXISTS idx_auth_user_channel ON t_auth_user(channel);

-- =============================================================================
-- 2. 凭据表
-- =============================================================================
CREATE TABLE IF NOT EXISTS t_auth_credential (
    id                VARCHAR(64)   NOT NULL,
    user_id           VARCHAR(64)   NOT NULL,
    credential_type   VARCHAR(20)   NOT NULL,
    credential_value  VARCHAR(512)  NOT NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    expires_at        TIMESTAMP,
    created_by        VARCHAR(64)   NOT NULL,
    updated_by        VARCHAR(64),
    create_time       TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    deleted           BOOLEAN       DEFAULT FALSE,
    version           INT           DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_auth_credential_user_id ON t_auth_credential(user_id);

-- =============================================================================
-- 3. 角色表
-- =============================================================================
CREATE TABLE IF NOT EXISTS t_auth_role (
    id            VARCHAR(64)   NOT NULL,
    role_code     VARCHAR(64)   NOT NULL,
    role_name     VARCHAR(128)  NOT NULL,
    channel       VARCHAR(20),
    description   VARCHAR(255),
    status        VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_by    VARCHAR(64)   NOT NULL,
    updated_by    VARCHAR(64),
    create_time   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    deleted       BOOLEAN       DEFAULT FALSE,
    version       INT           DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_auth_role_code ON t_auth_role(role_code) WHERE deleted = FALSE;

-- =============================================================================
-- 4. 角色权限表
-- =============================================================================
CREATE TABLE IF NOT EXISTS t_auth_role_permission (
    id              VARCHAR(64)   NOT NULL,
    role_id         VARCHAR(64)   NOT NULL,
    business_type   VARCHAR(64)   NOT NULL,
    operation       VARCHAR(20)   NOT NULL,
    created_by      VARCHAR(64)   NOT NULL,
    updated_by      VARCHAR(64),
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN       DEFAULT FALSE,
    version         INT           DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_auth_role_perm_role_id ON t_auth_role_permission(role_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_auth_role_perm ON t_auth_role_permission(role_id, business_type, operation) WHERE deleted = FALSE;

-- =============================================================================
-- 5. 业务可用性配置表
-- =============================================================================
CREATE TABLE IF NOT EXISTS t_auth_business_scope (
    id                  VARCHAR(64)   NOT NULL,
    scope_type          VARCHAR(30)   NOT NULL,
    scope_target        VARCHAR(64)   NOT NULL,
    allowed_businesses  JSONB         NOT NULL,
    inheritable         BOOLEAN       DEFAULT FALSE,
    status              VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_by          VARCHAR(64)   NOT NULL,
    updated_by          VARCHAR(64),
    create_time         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    deleted             BOOLEAN       DEFAULT FALSE,
    version             INT           DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_auth_scope ON t_auth_business_scope(scope_type, scope_target) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_auth_scope_type ON t_auth_business_scope(scope_type);

-- =============================================================================
-- 6. 计划角色分配表
-- =============================================================================
CREATE TABLE IF NOT EXISTS t_auth_plan_role_assignment (
    id              VARCHAR(64)   NOT NULL,
    plan_no         VARCHAR(64)   NOT NULL,
    user_id         VARCHAR(64)   NOT NULL,
    role_id         VARCHAR(64)   NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    assigned_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(64)   NOT NULL,
    updated_by      VARCHAR(64),
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN       DEFAULT FALSE,
    version         INT           DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_auth_pra_plan_no ON t_auth_plan_role_assignment(plan_no);
CREATE INDEX IF NOT EXISTS idx_auth_pra_user_id ON t_auth_plan_role_assignment(user_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_auth_pra ON t_auth_plan_role_assignment(plan_no, user_id, role_id) WHERE deleted = FALSE;

-- =============================================================================
-- 7. 代办关系表
-- =============================================================================
CREATE TABLE IF NOT EXISTS t_auth_delegation (
    id                      VARCHAR(64)   NOT NULL,
    type                    VARCHAR(20)   NOT NULL,
    source_plan_no          VARCHAR(64)   NOT NULL,
    target_plan_nos         JSONB         NOT NULL,
    delegated_permissions   JSONB         NOT NULL,
    designated_handlers     JSONB,
    status                  VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    expires_at              TIMESTAMP,
    created_by              VARCHAR(64)   NOT NULL,
    updated_by              VARCHAR(64),
    create_time             TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time             TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    deleted                 BOOLEAN       DEFAULT FALSE,
    version                 INT           DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_auth_delegation_source ON t_auth_delegation(source_plan_no);
CREATE INDEX IF NOT EXISTS idx_auth_delegation_status ON t_auth_delegation(status);

-- =============================================================================
-- 8. 二次授权表（网点渠道专用）
-- =============================================================================
CREATE TABLE IF NOT EXISTS t_auth_secondary_auth (
    id                VARCHAR(64)   NOT NULL,
    teller_user_id    VARCHAR(64)   NOT NULL,
    handler_user_id   VARCHAR(64)   NOT NULL,
    customer_no       VARCHAR(64)   NOT NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    authorized_at     TIMESTAMP,
    expires_at        TIMESTAMP,
    created_by        VARCHAR(64)   NOT NULL,
    updated_by        VARCHAR(64),
    create_time       TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    deleted           BOOLEAN       DEFAULT FALSE,
    version           INT           DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_auth_sa_teller ON t_auth_secondary_auth(teller_user_id);
CREATE INDEX IF NOT EXISTS idx_auth_sa_status ON t_auth_secondary_auth(status);

-- =============================================================================
-- 9. 菜单表
-- =============================================================================
CREATE TABLE IF NOT EXISTS t_auth_menu (
    id                  VARCHAR(64)   NOT NULL,
    parent_id           VARCHAR(64),
    menu_name           VARCHAR(128)  NOT NULL,
    menu_type           VARCHAR(20)   NOT NULL,
    route_path          VARCHAR(255),
    component_path      VARCHAR(255),
    icon                VARCHAR(64),
    sort_order          INT           DEFAULT 0,
    visible             BOOLEAN       DEFAULT TRUE,
    business_type       VARCHAR(64),
    required_operation  VARCHAR(20),
    channel_mask        INT           DEFAULT 7,
    status              VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_by          VARCHAR(64)   NOT NULL,
    updated_by          VARCHAR(64),
    create_time         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    deleted             BOOLEAN       DEFAULT FALSE,
    version             INT           DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_auth_menu_parent_id ON t_auth_menu(parent_id);
CREATE INDEX IF NOT EXISTS idx_auth_menu_sort ON t_auth_menu(sort_order);
```

### 7.3 ER 关系

```
t_auth_user ──1:N──► t_auth_credential
     │
     │ 1:N
     ▼
t_auth_plan_role_assignment ──N:1──► t_auth_role ──1:N──► t_auth_role_permission

t_auth_business_scope (独立配置)
t_auth_delegation (独立配置)
t_auth_secondary_auth (独立配置)
t_auth_menu (树形结构，通过 business_type+required_operation 关联权限)
```

---

## 八、扩展性设计

### 8.1 凭据类型扩展

新增凭据类型（如指纹、人脸识别）只需：
1. 实现 CredentialValidator 接口
2. 注册到 CredentialValidatorRegistry
3. 不修改现有代码（OCP）

### 8.2 渠道类型扩展

新增渠道（如微信渠道、区域中心渠道）只需：
1. 实现 ChannelStrategy 接口
2. 注册到 ChannelStrategyRegistry
3. 不修改现有代码（OCP）

### 8.3 业务可用性维度扩展

新增 BusinessScope 的 scope_type 只需：
1. 在 ScopeType 枚举中新增值
2. 在 BusinessScopeResolver 中添加对应的解析逻辑
3. 不影响现有维度的解析

### 8.4 代办类型扩展

如需新增代办类型（如临时代办、跨客户代办），只需：
1. 在 DelegationType 中新增类型
2. 在 DelegationEvaluator 中添加对应的判定逻辑
3. 不影响现有代办类型的判定
