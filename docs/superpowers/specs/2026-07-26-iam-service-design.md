# iam-service 用户与权限服务设计

**日期**: 2026-07-26
**状态**: 设计已完成,Spec 自审通过,待用户确认
**分支**: 待创建
**Spec 自审**: 已完成(占位符/矛盾/范围/歧义检查通过,修复 5 处问题)

---

## 1. 背景与目标

### 1.1 业务背景

本项目需要为企业年金业务办理场景提供用户认证与权限管理能力。年金业务办理涉及多渠道、多维度权限控制:

- **多渠道接入**: 网上渠道(经办人)、总部渠道(运营人员)、网点渠道(银行柜员)
- **上下文相关权限**: 用户办理业务前必须先选择"计划",权限基于计划上下文动态计算
- **多维度规则叠加**: 客户、运作模式、产品、计划、账管人五个维度的权限规则按优先级覆盖
- **计划间代办关系**: 计划 A 可授权给计划 B 代办,支持全部经办或指定经办
- **网点二次授权**: 网点柜员需经办人二次授权后,借用经办人权限办理业务
- **外部数据依赖**: 客户、产品、计划等数据在外部系统维护,通过防腐层查询

### 1.2 设计目标

1. **基于 sa-token 实现多渠道认证**: 三套渠道独立 Token,互不干扰
2. **上下文感知的权限计算**: 选择计划时计算并缓存权限,支持计划切换
3. **灵活可扩展的权限模型**: 支持新增权限维度、新增凭据类型、新增登录方式
4. **符合 DDD 设计规范**: 限界上下文清晰、聚合边界合理、领域事件驱动
5. **符合项目架构规范**: 7 层 DDD + 六边形架构,遵循项目所有规则约束

### 1.3 设计原则

- **开闭原则**: 凭据验证、二次授权、权限组合算法均为 SPI 扩展点
- **依赖倒置**: 防腐层 Gateway 接口定义在领域层,实现在基础设施层
- **最终一致性**: 跨聚合的一致性通过领域事件 + 异步处理实现
- **配置驱动**: 优先级顺序、默认策略、Token 有效期等均通过 YAML 配置

### 1.4 范围与非范围

**在范围内**:

- 三套渠道用户体系(统一模型 + 渠道档案)
- 凭据多类型扩展(密码、UKEY 等)
- 二次授权会话(网点渠道)
- 多维度权限规则(客户/运作模式/产品/计划/账管人)
- 优先级覆盖算法(可扩展 SPI)
- 计划代办关系(两种类型)
- sa-token 多 StpLogic 集成
- 领域事件 + 集成事件
- 数据库双 DDL(PostgreSQL + MySQL)
- 防腐层 Gateway(接口定义 + Mock 实现)
- 错误码体系

**不在范围内**:

- 具体编码实现(使用 writing-plans 拆解)
- 单元测试设计(TDD 阶段)
- 外部系统接口对接(等外部接口提供后再编码)
- 网关动态路由规则管理界面(运维功能)
- 用户密码策略(强度、过期等配置项)
- 审计日志存储格式(使用现有 shared-logging)
- 通知服务实现(使用现有通知基础设施)
- 完整的 API DTO 列表(编码阶段定义)

---

## 2. 总体架构

### 2.1 服务定位

新建 `iam-service` 作为独立的用户与权限服务,承载认证(authentication)和授权(authorization)两个限界上下文。

**选择单服务而非拆分认证/授权两服务的理由**:

1. 复杂度匹配当前业务规模,避免过度设计
2. 用户办理业务的工作流天然适合 Token-Session 缓存计划级权限
3. 限界上下文已隔离,未来可平滑拆分为独立服务

### 2.2 限界上下文划分

```
┌────────────────────────────────────────────────────────────────────┐
│                          iam-service                                │
│                                                                    │
│  ┌──────────────────────────────┐  ┌──────────────────────────────┐│
│  │   authentication 限界上下文   │  │   authorization 限界上下文    ││
│  │   (认证域)                    │  │   (授权域)                    ││
│  │                              │  │                              ││
│  │  - User 聚合根                │  │  - PermissionRule 聚合根      ││
│  │  - Credential 聚合根          │  │  - PlanDelegation 聚合根      ││
│  │  - SecondaryAuthSession 聚合根│  │  - BusinessDefinition 实体    ││
│  │  - LoginLog 聚合根            │  │  - RouteRule 实体             ││
│  │                              │  │                              ││
│  │  领域服务:                    │  │  领域服务:                    ││
│  │  - CredentialValidator (SPI) │  │  - PermissionResolver         ││
│  │  - SecondaryAuthStrategy(SPI)│  │  - BusinessRegistryService   ││
│  │  - LoginRiskService          │  │  - PermissionCacheManager     ││
│  │                              │  │  - PermissionCombinationStrategy(SPI)│
│  └──────────────────────────────┘  └──────────────────────────────┘│
│                                                                    │
│  ┌──────────────────────────────────────────────────────────────┐│
│  │   system 系统层(横切关注点)                                   ││
│  │   - sa-token 集成                                             ││
│  │   - 防腐层 Gateway 实现                                       ││
│  │   - 事件订阅与发布                                             ││
│  └──────────────────────────────────────────────────────────────┘│
└────────────────────────────────────────────────────────────────────┘
```

### 2.3 模块划分(7 层 DDD)

```
iam-service/
├── iam-types/                  # ID 类型定义(UserId, CredentialId 等)
├── iam-domain/                 # 领域层
│   ├── authentication/         # 认证限界上下文
│   │   ├── aggregate/
│   │   │   ├── root/           # User, Credential, SecondaryAuthSession, LoginLog
│   │   │   ├── entity/         # UserProfile, LoginFailureRecord
│   │   │   └── valueobject/    # ChannelType, CredentialType, UserStatus 等
│   │   ├── event/              # UserCreatedEvent, UserDisabledEvent 等
│   │   ├── repository/         # UserRepository, CredentialRepository 等(接口)
│   │   ├── service/            # CredentialValidator, SecondaryAuthStrategy(SPI)
│   │   ├── gateway/            # (认证域不涉及外部防腐层)
│   │   └── errorcode/          # IamAuthErrorCode
│   ├── authorization/          # 授权限界上下文
│   │   ├── aggregate/
│   │   │   ├── root/           # PermissionRule, PlanDelegation
│   │   │   ├── entity/         # BusinessDefinition, DelegationOperator, DelegationPermission
│   │   │   └── valueobject/    # SubjectType, OverrideMode, Action, PlanMetadata 等
│   │   ├── event/              # PermissionRuleCreatedEvent 等
│   │   ├── repository/         # PermissionRuleRepository 等(接口)
│   │   ├── service/            # PermissionResolver, BusinessRegistryService, PermissionCombinationStrategy
│   │   ├── gateway/            # PlanMetadataGateway, CustomerGateway, ProductGateway
│   │   └── errorcode/          # IamAuthzErrorCode
│   └── system/                 # 系统层错误码
│       └── errorcode/          # IamSystemErrorCode
├── iam-api/                    # API 接口定义
│   ├── command/                # Command 对象
│   ├── query/                  # Query 对象
│   ├── dto/                    # Response DTO
│   └── integration_event/      # 集成事件 DTO
├── iam-application/            # 应用服务层
│   └── service/                # PlanSelectionAppService, UserAppService 等
├── iam-adapter/                # 适配器层
│   ├── controller/             # Controller
│   ├── converter/              # DTO Converter(MapStruct)
│   └── security/               # sa-token StpLogic, StpInterface 实现
├── iam-infrastructure/         # 基础设施层
│   ├── repository/             # Repository 实现
│   ├── entity/                 # DO 实体
│   ├── mapper/                 # MyBatis-Flex Mapper
│   ├── converter/              # Entity Converter(DO ↔ 领域对象)
│   └── gateway/                # Gateway 实现 + 外部 API
└── iam-starter/                # 启动模块
```

### 2.4 sa-token 集成总览

```
┌────────────────────────────────────────────────────────────────────┐
│                        前端请求(三套渠道)                          │
└────────────┬─────────────────────┬─────────────────────┬────────────┘
             │                     │                     │
        Internet              HQ Portal             Branch Portal
   (Header: satoken-internet)  (Header: satoken-hq)  (Header: satoken-branch)
             │                     │                     │
             ▼                     ▼                     ▼
┌────────────────────────────────────────────────────────────────────┐
│              demo-gateway (WebFlux) - SaReactorFilter              │
│  1. 根据路径前缀识别渠道:/internet/**, /hq/**, /branch/**         │
│  2. 调用对应 StpLogic.checkLogin()                                 │
│  3. 路由级权限校验:动态加载路由规则                                │
│  4. 透传 Token Header 到下游服务                                   │
└────────────────────────────┬───────────────────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────────┐
│              iam-service (Servlet) - 业务服务                      │
│  - 三套 StpLogic(每渠道一套)                                       │
│  - IamStpInterfaceImpl(实现 StpInterface,根据 loginType 路由)      │
└────────────────────────────────────────────────────────────────────┘
```

---

## 3. 聚合设计

### 3.1 聚合总览

| 限界上下文 | 聚合根 | 类型 | 核心职责 |
|---|---|---|---|
| authentication | User | 聚合根 | 用户身份与状态(三渠道统一) |
| authentication | Credential | 聚合根 | 多类型凭据管理 |
| authentication | SecondaryAuthSession | 聚合根 | 网点二次授权会话 + 权限快照 |
| authentication | LoginLog | 聚合根 | 登录流水审计 |
| authorization | PermissionRule | 聚合根 | 多维度权限规则 |
| authorization | PlanDelegation | 聚合根 | 计划间代办关系 |
| authorization | BusinessDefinition | 实体(非聚合根) | 业务元数据定义 |
| authorization | RouteRule | 实体(非聚合根) | 网关路由权限规则 |

### 3.2 聚合独立性论证

#### 3.2.1 Credential 作为独立聚合的论证

**判断依据**: 凭据类型多且可扩展(密码、UKEY、动态令牌),若放在 User 聚合内,每新增一种凭据类型 User 都要膨胀。

**设计约束**:

1. Credential 持有 `ownerId` 引用(不持有 User 对象)
2. 用户状态变更通过领域事件传播:`UserDisabledEvent` → Credential 聚合监听 → 标记失效
3. 登录验证时双重校验:User 状态(走缓存)+ Credential 有效性
4. 创建场景的事务设计:同一事务内 save User + save Credential

**与 User 的一致性**: 业务可容忍的最终一致性(用户禁用后几秒内凭据失效,业务可接受)。

#### 3.2.2 SecondaryAuthSession 作为独立聚合的论证

**判断依据**:

1. **独立不变量**: 一个柜员同一时刻只能有一个 ACTIVE 会话、撤销权限约束、快照一致性
2. **独立状态机**: PENDING → AUTHORIZED → CLOSED/EXPIRED/REVOKED 等多状态流转
3. **独立事务边界**: 撤销操作不涉及其他聚合的修改

**与 Credential 的差异**: Credential 因扩展性独立,Session 因状态机复杂性独立。

**权限快照一致性**: 授权瞬间冻结经办人权限快照,之后经办人权限变更不影响已授权会话,避免"授权后权限被收回但柜员仍在使用旧权限"的安全漏洞。

#### 3.2.3 BusinessDefinition 不作为聚合根的论证

**判断依据**:

1. 业务和动作的定义本质是**元数据**,不是业务对象
2. 业务定义的"创建"是配置行为,不是业务行为
3. 没有复杂的不变量需要聚合根保护
4. 符合开闭原则——新增业务类型不需要修改聚合,只需新增配置

**实现方式**: 通过 `BusinessRegistryService` 领域服务查询,业务定义存储在数据库。

### 3.3 聚合关系图

```
┌─────────────────────────────────────────────────────────────────┐
│                    authentication 限界上下文                      │
│                                                                  │
│  ┌──────────┐    ┌────────────┐    ┌──────────────────────┐    │
│  │   User   │    │ Credential │    │ SecondaryAuthSession │    │
│  │ (聚合根)  │←───│  (聚合根)   │    │      (聚合根)         │    │
│  └────┬─────┘    └────────────┘    └──────────┬───────────┘    │
│       │                                        │                │
│       │  (ID引用)                              │ (ID引用)        │
│       ▼                                        ▼                │
└───────┼────────────────────────────────────────┼────────────────┘
        │                                        │
┌───────┼────────────────────────────────────────┼────────────────┐
│       │           authorization 限界上下文       │                │
│       │                                        │                │
│       │     ┌──────────────────┐  ┌────────────┴──────────┐   │
│       └────►│  PermissionRule  │  │    PlanDelegation     │   │
│             │     (聚合根)      │  │       (聚合根)         │   │
│             └──────────────────┘  └───────────────────────┘   │
│                       │                                        │
│                       │ (查询)                                 │
│                       ▼                                        │
│             ┌──────────────────┐                               │
│             │ BusinessRegistry │  (领域服务,非聚合)             │
│             │    Service       │                               │
│             └──────────────────┘                               │
└──────────────────────────────────────────────────────────────── ┘
```

**关系说明**:

- 聚合间通过 **ID 引用**(如 PermissionRule.subjectId 存的是 CustomerNo 字符串)
- PermissionRule 和 PlanDelegation 都依赖 BusinessRegistry 校验业务和动作的合法性
- SecondaryAuthSession 在授权瞬间从 PermissionResolver 获取权限快照

### 3.4 关键聚合字段与行为

#### 3.4.1 User 聚合根

**核心字段**:

- `id: UserId` - 用户ID
- `channelType: ChannelType` - 渠道类型(INTERNET/HQ/BRANCH)
- `loginName: String` - 登录名
- `displayName: String` - 显示名
- `status: UserStatus` - 用户状态(ACTIVE/DISABLED/LOCKED)
- `lastLoginTime: LocalDateTime` - 最后登录时间
- `lastLoginIp: String` - 最后登录IP
- `profile: UserProfile` - 渠道专属档案(实体)

**核心行为**:

- `create(...)` - 创建用户(工厂方法,注册 UserCreatedEvent)
- `disable(operator, reason)` - 禁用用户(注册 UserDisabledEvent)
- `enable(operator)` - 启用用户
- `lock(operator, reason)` - 锁定用户
- `markLoginSuccess(ip, time)` - 标记登录成功
- `validateInvariants()` - 不变量校验

#### 3.4.2 Credential 聚合根

**核心字段**:

- `id: CredentialId`
- `ownerType: String` - 归属类型(INTERNET_USER/HQ_USER/BRANCH_USER)
- `ownerId: Long` - 归属ID(用户ID)
- `credentialType: CredentialType` - 凭据类型(PASSWORD/UKEY/DYNAMIC_TOKEN)
- `secretHash: String` - 凭据密文(BCrypt 哈希)
- `salt: String` - 盐值(可选)
- `auxData: Map<String, Object>` - 附加数据(UKEY 公钥等)
- `status: CredentialStatus` - 状态(ACTIVE/EXPIRED/REVOKED)
- `expireTime: LocalDateTime` - 过期时间(可空表示永久)

**核心行为**:

- `create(...)` - 创建凭据(工厂方法,注册 CredentialCreatedEvent)
- `verify(plainSecret, validator)` - 验证凭据(委托给 CredentialValidator 策略)
- `change(newSecret, operator)` - 修改凭据(注册 CredentialChangedEvent)
- `markExpired()` - 标记过期
- `markRevoked(operator)` - 撤销凭据

#### 3.4.3 SecondaryAuthSession 聚合根

**核心字段**:

- `id: SecondaryAuthSessionId`
- `tellerId: Long` - 柜员ID
- `approverId: Long` - 经办人ID
- `customerNo: CustomerNo` - 客户编号
- `planId: PlanId` - 计划ID
- `permissionSnapshot: Set<PermissionCode>` - 权限快照
- `status: SecondaryAuthStatus` - 状态
- `initiatedAt: LocalDateTime` - 发起时间
- `authorizedAt: LocalDateTime` - 授权时间
- `expireAt: LocalDateTime` - 过期时间
- `revokeReason: String` - 撤销原因

**状态机**:

```
PENDING(待授权)
   │
   ├── 经办人确认 → AUTHORIZED(已授权)
   │                  │
   │                  ├── 超时 → EXPIRED
   │                  ├── 撤销 → REVOKED
   │                  └── 柜员登出 → CLOSED
   │
   └── 经办人拒绝 → REJECTED
```

**核心行为**:

- `initiate(...)` - 发起二次授权(PENDING 状态)
- `authorize(approver, snapshot, expireAt)` - 完成授权(冻结快照)
- `revoke(operator, reason)` - 撤销授权
- `isEffectiveAt(moment)` - 判断会话是否生效
- `authorizes(operatorId)` - 判断是否授权指定操作员

#### 3.4.4 PermissionRule 聚合根

**核心字段**:

- `id: PermissionRuleId`
- `ruleCode: String` - 规则编码
- `ruleName: String` - 规则名称
- `subjectType: SubjectType` - 主体维度(决定优先级)
- `subjectId: String` - 主体标识
- `businessCode: BusinessCode` - 业务编码
- `allowedActions: Set<Action>` - 授权动作集合
- `inheritToChildren: boolean` - 是否继承给下属企业
- `overrideMode: OverrideMode` - 覆盖模式(ADD/REMOVE)
- `priority: Integer` - 优先级(可空)
- `status: RuleStatus` - 状态(ACTIVE/DISABLED)
- `effectiveAt: LocalDateTime` - 生效时间
- `expireAt: LocalDateTime` - 失效时间(可空)

**核心行为**:

- `create(...)` - 创建规则
- `disable(operator)` - 禁用规则
- `enable(operator)` - 启用规则
- `isEffectiveAt(moment)` - 判断规则是否生效
- `matches(context)` - 判断规则是否适用于指定上下文

#### 3.4.5 PlanDelegation 聚合根

**核心字段**:

- `id: PlanDelegationId`
- `delegationCode: String` - 代办编码
- `delegatorPlanNo: String` - 授权方计划编号
- `delegateePlanNo: String` - 被授权方计划编号
- `delegationType: DelegationType` - 代办类型
- `designatedOperators: Set<Long>` - 指定操作员(仅 SPECIFIC_OPERATORS)
- `delegatedPermissions: Set<DelegatedPermission>` - 授权权限
- `status: DelegationStatus` - 状态
- `effectiveAt: LocalDateTime` - 生效时间
- `expireAt: LocalDateTime` - 失效时间

**两种代办类型**:

- `ALL_OPERATORS`: 计划A授权给计划B代办,A下所有经办都拥有B的授权
- `SPECIFIC_OPERATORS`: 计划A指定其下部分经办拥有指定计划的指定业务的指定权限

**核心行为**:

- `create(...)` - 创建代办关系
- `activate(operator)` - 激活
- `revoke(operator, reason)` - 撤销
- `authorizes(operatorId)` - 判断是否授权指定操作员
- `permissionsFor(operatorId, businessCode)` - 获取代办授权的权限码

### 3.5 关键值对象

```java
// 主体维度(决定优先级)
public enum SubjectType {
    CUSTOMER(1),           // 客户级(最低优先级)
    OPERATION_MODE(2),     // 运作模式级
    PRODUCT(3),            // 产品级
    PLAN(4),               // 计划级
    ACCOUNT_MANAGER(5);    // 账管人级(最高优先级)
}

// 覆盖模式
public enum OverrideMode {
    ADD,      // 扩展:向低层级权限集合添加
    REMOVE    // 收紧:从低层级权限集合移除
}

// 业务动作(可扩展)
public enum Action {
    HANDLE,   // 办理
    QUERY,    // 查询
    AUDIT     // 审核
    // 未来可扩展:EXPORT, IMPORT, APPROVE 等
}

// 凭据类型
public enum CredentialType {
    PASSWORD,        // 密码
    UKEY,            // UKEY
    DYNAMIC_TOKEN    // 动态令牌
}

// 渠道类型
public enum ChannelType {
    INTERNET,   // 网上渠道(经办人)
    HQ,         // 总部渠道(运营人员)
    BRANCH      // 网点渠道(银行柜员)
}

// 代办类型
public enum DelegationType {
    ALL_OPERATORS,        // 全部经办
    SPECIFIC_OPERATORS    // 指定经办
}

// 运作模式
public enum OperationMode {
    SINGLE_TRUSTEE,                 // 单受托产品
    SINGLE_ACCOUNT_MANAGER,         // 单账管产品
    TRUSTEE_AND_ACCOUNT_MANAGER     // 受托+账管产品
}
```

### 3.6 扩展点(SPI)

#### 3.6.1 CredentialValidator(凭据验证策略)

```java
public interface CredentialValidator {
    /** 支持的凭据类型 */
    CredentialType supports();

    /** 验证凭据 */
    boolean validate(String plainSecret, Credential credential);
}
```

**默认实现**: `PasswordCredentialValidator`(BCrypt)、`UkeyCredentialValidator`(RSA)

#### 3.6.2 SecondaryAuthStrategy(二次授权策略)

```java
public interface SecondaryAuthStrategy {
    /** 支持的授权类型 */
    String supports();

    /** 发起授权 */
    SecondaryAuthSession initiate(SecondaryAuthContext context);

    /** 完成授权 */
    SecondaryAuthSession authorize(SecondaryAuthSession session, AuthorizeCommand command);
}
```

**默认实现**: `DefaultSecondaryAuthStrategy`(经办人确认模式)

#### 3.6.3 PermissionCombinationStrategy(权限组合策略)

```java
public interface PermissionCombinationStrategy {
    /** 策略名称 */
    String name();

    /** 将多个维度的规则组合为最终权限 */
    Set<PermissionCode> combine(PermissionCombinationContext context);
}
```

**默认实现**: `PriorityOverrideStrategy`(优先级覆盖策略)

**配置驱动**:

```yaml
iam:
  permission:
    combination-strategy: priorityOverrideStrategy  # 默认策略 Bean 名称
    subject-priority:                        # 优先级顺序(高 → 低)
      - ACCOUNT_MANAGER
      - PLAN
      - PRODUCT
      - OPERATION_MODE
      - CUSTOMER
```

**设计要点**: 优先级顺序通过 YAML 配置,而非硬编码在策略实现中。算法可扩展(新增策略实现 SPI),顺序可配置(业务规则调整不改代码)。

### 3.7 PermissionResolver 权限计算流程

```java
public interface PermissionResolver {
    /** 计算指定用户在指定计划上下文下的权限快照 */
    PermissionSnapshot resolve(UserId userId, PlanId planId);
}

public record PermissionSnapshot(
    UserId userId,
    PlanId planId,
    Set<PermissionCode> permissions,    // 如 {"business1.handle", "business2.query"}
    LocalDateTime calculatedAt
) implements ValueObject {}
```

**计算流程**:

```
用户选择计划 planId
   │
   ▼
PermissionResolver.resolve(userId, planId)
   │
   ├── 1. 通过防腐层查询 planId → (customerNo, productNo, operationMode, accountManagerCode)
   ├── 2. 加载所有适用规则(按优先级排序):
   │       客户级规则 → 运作模式级 → 产品级 → 计划级 → 账管人级
   ├── 3. 应用优先级覆盖算法:
   │       初始权限集 = 客户级规则授权
   │       逐层应用:高层级规则可 ADD(扩展)或 REMOVE(收紧)
   ├── 4. 加载代办关系:
   │       若 planId 被其他计划代办,合并代办授权的业务和动作
   │       若 userId 被指定为代办操作员,合并指定业务和动作
   ├── 5. 调用 PermissionCombinationStrategy.combine(context) 计算最终权限
   └── 6. 输出 PermissionSnapshot(含权限码集合 + 计算时间戳)
```

**关键约束**:
- 代办关系作为独立步骤处理,不放在组合策略内部,因为代办是独立的业务关注点,不是组合算法的一部分。
- `resolve` 返回 `PermissionSnapshot` 值对象(包含权限集合 + 计算时间戳),供调用方决定如何缓存。
- 网点渠道在二次授权瞬间调用 `resolve` 冻结快照;其他渠道在 `IamStpInterfaceImpl.getPermissionList` 缓存未命中时调用。

---

## 4. sa-token 多 StpLogic 集成

### 4.1 三套 StpLogic 工具类

每个渠道独立的 StpLogic 实例,Token 互不干扰。

| 渠道 | 工具类 | loginType | Token Header | 默认有效期 |
|---|---|---|---|---|
| 网上渠道 | `StpInternetUtil` | `internet` | `satoken-internet` | 30 天 |
| 总部渠道 | `StpHqUtil` | `hq` | `satoken-hq` | 8 小时 |
| 网点渠道 | `StpBranchUtil` | `branch` | `satoken-branch` | 8 小时 |

**工具类结构**(以 `StpInternetUtil` 为例):

```java
public class StpInternetUtil {
    public static final String TYPE = "internet";
    public static StpLogic stpLogic = new StpLogic(TYPE);

    // 登录相关
    public static void login(Object id) { stpLogic.login(id); }
    public static void logout() { stpLogic.logout(); }

    // 会话查询
    public static Object getLoginId() { return stpLogic.getLoginId(); }
    public static boolean isLogin() { return stpLogic.isLogin(); }
    public static void checkLogin() { stpLogic.checkLogin(); }

    // Token 与 Session
    public static String getTokenValue() { return stpLogic.getTokenValue(); }
    public static SaSession getSession() { return stpLogic.getSession(); }
    public static SaSession getTokenSession() { return stpLogic.getTokenSession(); }

    // 权限校验
    public static List<String> getPermissionList() { return stpLogic.getPermissionList(); }
    public static void checkPermission(String permission) { stpLogic.checkPermission(permission); }

    // 踢人下线
    public static void kickout(Object loginId) { stpLogic.kickout(loginId); }
}
```

`StpBranchUtil` 额外提供网点渠道专属方法:

```java
// 二次授权状态查询
public static boolean hasSecondaryAuth() {
    return getTokenSession().get("secondaryAuthSessionId") != null;
}
public static Long getSecondaryAuthSessionId() { ... }
public static Long getBorrowedApproverId() { ... }
```

### 4.2 配置

#### 4.2.1 iam-service application.yml

```yaml
sa-token:
  timeout: 2592000              # token 有效期:30 天(默认,渠道级可覆盖)
  active-timeout: 1800          # token 最低活跃频率:30 分钟
  is-concurrent: true           # 是否允许同一账号多地同时登录(全局,渠道级可覆盖)
  is-share: false               # 多人登录同一账号时是否共用一个 token
  token-style: tik              # token 风格
  is-log: false
  is-read-header: true          # 前后台分离:从 Header 读取
  is-read-cookie: false

iam:
  security:
    channels:
      internet:
        timeout: 2592000        # 30 天
        active-timeout: 1800
        is-concurrent: false    # 同端互斥(防止账号被盗用)
      hq:
        timeout: 28800          # 8 小时
        active-timeout: 1800
        is-concurrent: true     # 允许同账号多端
      branch:
        timeout: 28800          # 8 小时
        active-timeout: 1800
        is-concurrent: false

    secondary-auth:
      session-timeout: 7200     # 二次授权会话有效期:2 小时
      pending-timeout: 300      # 待授权会话过期时间:5 分钟
      max-pending-per-teller: 1 # 同一柜员同时只能有一个待授权会话

    permission:
      combination-strategy: priorityOverrideStrategy
      subject-priority:
        - ACCOUNT_MANAGER
        - PLAN
        - PRODUCT
        - OPERATION_MODE
        - CUSTOMER
      cache-timeout: 1800       # 权限缓存有效期:30 分钟
      cache-null: true          # 缓存空值防穿透
```

**配置优先级说明**: 渠道级配置(`iam.security.channels.xxx`)覆盖全局 sa-token 配置,在 StpLogic 初始化时通过 `SaLoginModel` 指定。

#### 4.2.2 demo-gateway application.yml

```yaml
sa-token:
  is-read-header: true
  is-read-cookie: false
  is-log: false

iam:
  gateway:
    route-rule-cache-timeout: 600
    public-paths:
      - /actuator/**
      - /internet/auth/login
      - /hq/auth/login
      - /branch/auth/login
      - /branch/auth/secondary-auth/initiate
      - /branch/auth/secondary-auth/confirm
      - /branch/auth/secondary-auth/status/**
```

### 4.3 登录流程

#### 4.3.1 网上渠道登录流程

```
经办人(HR)
   │
   │  POST /internet/auth/login
   │  { loginName, password, captcha }
   │
   ▼
InternetLoginController.login()
   ├── 1. 加载 InternetUser(by loginName)
   ├── 2. 加载 Credential(PASSWORD 类型)
   ├── 3. PasswordCredentialValidator.validate(plainPassword, credential)
   ├── 4. 记录登录日志(LoginLog.create + LoginEvent)
   ├── 5. 更新用户最后登录时间/IP
   └── 6. StpInternetUtil.login(userId, SaLoginModel
         ├── setDevice("INTERNET_WEB")
         ├── setTimeout(channelConfig.internet.timeout)
         └── setIsConcurrent(false))

后续业务请求:
   │
   │  GET /internet/business/xxx
   │  Header: satoken-internet: {token}
   │
   ▼
Gateway: StpInternetUtil.checkLogin()
   │
   ▼
IamStpInterfaceImpl.getPermissionList(loginId, "internet")
   ├── 1. 从 Token-Session 读取 currentPlanId(若为空,返回空权限)
   ├── 2. 从 Token-Session 读取 currentPermissions 缓存(命中则返回)
   └── 3. 缓存未命中 → PermissionResolver.resolve(userId, planId)
         └── 计算并缓存到 Token-Session
```

#### 4.3.2 总部渠道登录流程

与网上渠道类似,差异点:

- 用户为运营人员,登录名为员工编号
- **总部用户可选择任何计划办理业务**(无需校验计划归属)
- 选择计划后,权限计算逻辑与网上渠道相同
- 但权限规则可能不同(总部有特殊权限规则,通过 `subjectType=ACCOUNT_MANAGER` 等高优先级维度配置)

#### 4.3.3 网点渠道登录流程(含二次授权)

```
网点柜员                    经办人(HR)
   │                          │
   │ 1. POST /branch/auth/login
   │    { tellerNo, password }
   │ ▼
   │ BranchLoginController.login()
   │ ├── 校验柜员账号密码
   │ └── StpBranchUtil.login(tellerId)
   │     └── Token-Session 标记: secondaryAuthStatus=PENDING
   │
   │ 2. 柜员尝试选择计划办理业务
   │    GET /branch/business/xxx?planId=P1
   │    │
   │    ▼
   │    Gateway 校验: hasSecondaryAuth() == false → 拒绝
   │    返回: 需要二次授权(SECONDARY_AUTH_REQUIRED)
   │
   │ 3. 柜员发起二次授权请求
   │    POST /branch/auth/secondary-auth/initiate
   │    { approverLoginName, approverCustomerId, planId }
   │    │
   │    ▼
   │    SecondaryAuthController.initiate()
   │    ├── 查询经办人 InternetUser
   │    ├── 创建 SecondaryAuthSession(status=PENDING)
   │    └── 返回: sessionId + 待经办人确认
   │
   │ 4. 系统通知经办人(短信/邮件/推送)  ──────────►  经办人收到通知
   │                                                    │
   │                                                    │ 5. 经办人登录网上渠道
   │                                                    │    POST /internet/auth/login
   │                                                    │
   │                                                    │ 6. 经办人确认授权
   │                                                       POST /internet/auth/secondary-auth/confirm
   │                                                       { sessionId, approverPassword }
   │                                                       │
   │                                                       ▼
   │                                                       SecondaryAuthController.confirm()
   │                                                       ├── 校验经办人身份和密码
   │                                                       ├── 加载经办人权限快照
   │                                                       │   PermissionSnapshot snapshot =
   │                                                       │     permissionResolver.resolve(approverId, planId)
   │                                                       ├── 更新 SecondaryAuthSession
   │                                                       │   .authorize(approverId, snapshot, expireAt)
   │                                                       └── 发布 SecondaryAuthCompletedEvent
   │                                                          │
   │ 7. 柜员轮询或推送获取授权结果  ◄─────────────────────┘
   │    GET /branch/auth/secondary-auth/status/{sessionId}
   │    │
   │    ▼
   │    返回: AUTHORIZED
   │    ├── (注意:柜员 Token-Session 已在步骤 6 经 SecondaryAuthCompletedEvent
   │    │   异步订阅者更新,柜员轮询仅查询状态,不写会话)
   │    └── 柜员现在可以办理业务
   │
   │ 7a. SecondaryAuthCompletedEvent 异步处理(步骤 6 触发后并行)
   │    IamDomainEventListener.onSecondaryAuthCompleted(event):
   │    ├── 校验柜员在线状态
   │    └── StpBranchUtil.getTokenSessionByLoginId(tellerId).set:
   │        ├── secondaryAuthSessionId = sessionId
   │        ├── borrowedApproverId = approverId
   │        ├── currentPlanId = planId
   │        └── currentPermissions = snapshot.permissions()
   │
   │ 8. 柜员办理业务(使用借用的权限)
   │    GET /branch/business/xxx
   │    │
   │    ▼
   │    IamStpInterfaceImpl.getPermissionList(tellerId, "branch")
   │    └── 从 Token-Session 直接返回 currentPermissions
   │        (使用授权时的快照,不重新计算)
   │
   │ 9. 经办人撤销授权(可选)
   │    POST /internet/auth/secondary-auth/revoke/{sessionId}
   │    ├── SecondaryAuthSession.revoke()
   │    ├── 发布 SecondaryAuthRevokedEvent
   │    └── StpBranchUtil.kickout(tellerId)  // 踢柜员下线
```

### 4.4 计划切换 API

#### 4.4.1 API 接口定义

```java
@HttpExchange("/iam/plan")
public interface PlanSelectionApi {

    /** 查询当前用户可选择的计划列表 */
    @GetExchange("/selectable")
    ApiResult<List<SelectablePlanResponse>> listSelectablePlans(String keyword);

    /** 选择当前办理计划 */
    @PostExchange("/select")
    ApiResult<PlanPermissionResponse> selectPlan(@RequestBody SelectPlanCommand command);

    /** 查询当前已选计划及权限 */
    @GetExchange("/current")
    ApiResult<PlanPermissionResponse> getCurrentPlan();

    /** 清除当前计划选择 */
    @PostExchange("/clear")
    ApiResult<Void> clearCurrentPlan();
}
```

**不同渠道可选计划范围**:

- 网上渠道: 可选择所属客户的所有计划
- 总部渠道: 可选择任何计划
- 网点渠道: 仅可选择已二次授权的计划

#### 4.4.2 渠道上下文抽象

`ChannelContext` 是贯穿网关和应用层的渠道抽象,既承载会话状态,也封装渠道分派的权限校验:

```java
public record ChannelContext(
    ChannelType channelType,
    Long userId,
    SaSession tokenSession,
    boolean hasSecondaryAuth,
    Long secondaryAuthPlanId
) {
    /** 渠道分派的权限校验(供网关 SaReactorFilter 调用) */
    public void checkPermission(String permission) {
        switch (channelType) {
            case INTERNET -> StpInternetUtil.checkPermission(permission);
            case HQ      -> StpHqUtil.checkPermission(permission);
            case BRANCH  -> StpBranchUtil.checkPermission(permission);
        }
    }

    /** 渠道分派的角色校验 */
    public void checkRole(String role) {
        switch (channelType) {
            case INTERNET -> StpInternetUtil.checkRole(role);
            case HQ      -> StpHqUtil.checkRole(role);
            case BRANCH  -> StpBranchUtil.checkRole(role);
        }
    }
}

@Component
public class ChannelContextProvider {
    public ChannelContext currentContext() {
        if (StpInternetUtil.isLogin()) { ... }
        if (StpHqUtil.isLogin()) { ... }
        if (StpBranchUtil.isLogin()) { ... }
        throw new BusinessException(IamAuthErrorCode.NOT_LOGGED_IN);
    }
}
```

### 4.5 网关层动态鉴权

```java
@Configuration
public class SaTokenGatewayConfiguration {

    @Bean
    @Order(-100)
    public SaReactorFilter saReactorFilter(ChannelAwareSaRouter channelAwareSaRouter,
                                           RouteRuleLoader routeRuleLoader) {
        return new SaReactorFilter()
            .addInclude("/**")
            .addExclude(
                "/actuator/**",
                "/favicon.ico",
                "/internet/auth/login",
                "/hq/auth/login",
                "/branch/auth/login",
                "/branch/auth/secondary-auth/initiate",
                "/branch/auth/secondary-auth/confirm",
                "/branch/auth/secondary-auth/status/**"
            )
            .setAuth(obj -> {
                // 1. 渠道识别 + 登录校验(基于路径前缀分派到对应 StpLogic)
                ChannelContext ctx = channelAwareSaRouter.matchAndCheckLogin();
                // 2. 动态路由权限校验:使用 ChannelContext 的渠道分派方法
                List<RouteRule> rules = routeRuleLoader.loadRules();
                for (RouteRule rule : rules) {
                    SaRouter.match(rule.routePattern()).check(r -> {
                        switch (rule.checkType()) {
                            case "login" -> { /* 已在步骤1校验 */ }
                            case "permission" -> ctx.checkPermission(rule.checkValue());
                            case "role" -> ctx.checkRole(rule.checkValue());
                            case "anonymous" -> { /* 不校验 */ }
                        }
                    });
                }
            })
            .setError(e -> { ... });
    }
}
```

> **避免使用默认 `StpUtil`**:sa-token 的 `StpUtil` 是默认 `StpLogic` 的快捷方式,无法识别多渠道。所有权限/角色校验必须通过 `ChannelContext`(见 4.4.2)分派到对应渠道的 `StpLogic`。

### 4.6 StpInterface 实现

```java
@Component
public class IamStpInterfaceImpl implements StpInterface {

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        try {
            SaSession session = getTokenSession(loginType);
            if (session == null) return List.of();

            // 1. 获取当前计划
            String planId = (String) session.get("currentPlanId");
            if (planId == null) return List.of();  // 未选择计划,无权限

            // 2. 网点渠道:直接返回二次授权快照
            if ("branch".equals(loginType)) {
                Set<String> snapshot = (Set<String>) session.get("currentPermissions");
                return snapshot != null ? List.copyOf(snapshot) : List.of();
            }

            // 3. 其他渠道:从缓存或重新计算
            Set<String> cached = (Set<String>) session.get("currentPermissions");
            if (cached != null) return List.copyOf(cached);

            // 4. 缓存未命中,调用 PermissionResolver 计算
            PermissionSnapshot snapshot = permissionResolver.resolve(
                UserId.of(Long.parseLong(loginId.toString())),
                PlanId.of(planId)
            );
            session.set("currentPermissions", snapshot.permissions());
            return List.copyOf(snapshot.permissions());
        } catch (Exception e) {
            log.error("[StpInterface] 加载权限失败", e);
            return List.of();  // 失败时返回空权限,拒绝所有操作
        }
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return switch (loginType) {
            case "internet" -> List.of("operator");
            case "hq" -> List.of("staff");
            case "branch" -> List.of("teller");
            default -> List.of();
        };
    }
}
```

### 4.7 服务间调用 Token 传递

```java
@Component
public class FeignTokenInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate template) {
        ChannelContext context = channelContextProvider.currentContext();
        String tokenValue = switch (context.channelType()) {
            case INTERNET -> StpInternetUtil.stpLogic.getTokenValue();
            case HQ -> StpHqUtil.stpLogic.getTokenValue();
            case BRANCH -> StpBranchUtil.stpLogic.getTokenValue();
        };
        if (tokenValue != null) {
            String tokenName = "satoken-" + context.channelType().name().toLowerCase();
            template.header(tokenName, tokenValue);
        }
    }
}
```

---

## 5. 领域事件设计

### 5.1 事件清单

#### 5.1.1 认证域事件

| 事件 | 发布者 | 触发时机 |
|---|---|---|
| `UserCreatedEvent` | User 聚合 | 创建用户 |
| `UserDisabledEvent` | User 聚合 | 禁用用户 |
| `UserEnabledEvent` | User 聚合 | 启用用户 |
| `UserLoginSucceededEvent` | LoginLog 聚合 | 登录成功 |
| `UserLoginFailedEvent` | LoginLog 聚合 | 登录失败 |
| `CredentialCreatedEvent` | Credential 聚合 | 创建凭据 |
| `CredentialChangedEvent` | Credential 聚合 | 修改凭据 |
| `CredentialExpiredEvent` | Credential 聚合 | 凭据过期 |
| `SecondaryAuthInitiatedEvent` | SecondaryAuthSession | 发起二次授权 |
| `SecondaryAuthCompletedEvent` | SecondaryAuthSession | 完成二次授权 |
| `SecondaryAuthRevokedEvent` | SecondaryAuthSession | 撤销二次授权 |
| `SecondaryAuthExpiredEvent` | SecondaryAuthSession | 二次授权会话过期 |

#### 5.1.2 授权域事件

| 事件 | 发布者 | 触发时机 |
|---|---|---|
| `PermissionRuleCreatedEvent` | PermissionRule | 创建权限规则 |
| `PermissionRuleDisabledEvent` | PermissionRule | 禁用权限规则 |
| `PermissionRuleEnabledEvent` | PermissionRule | 启用权限规则 |
| `PlanDelegationCreatedEvent` | PlanDelegation | 创建计划代办 |
| `PlanDelegationRevokedEvent` | PlanDelegation | 撤销计划代办 |
| `PlanDelegationActivatedEvent` | PlanDelegation | 激活计划代办 |

### 5.2 事件处理策略

```
领域事件
   │
   ├── 同步处理(同一事务内)
   │   └── 聚合内部状态变更(如初始化默认凭据)
   │
   └── 异步处理(AFTER_COMMIT)
       ├── 权限缓存失效
       ├── sa-token 会话操作(kickout)
       ├── 集成事件发布到 MQ
       ├── 通知外部系统
       └── 审计日志
```

### 5.3 事件订阅者

```java
@Component
public class IamDomainEventListener {

    // 同步处理:用户创建完成 - 初始化默认凭据
    @EventListener
    public void onUserCreated(UserCreatedEvent event) { ... }

    // 异步处理:用户禁用 - 连锁反应
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserDisabled(UserDisabledEvent event) {
        // 1. 标记所有凭据失效
        credentialRepository.markAllExpiredByOwner(...);
        // 2. 撤销所有进行中的二次授权会话
        secondaryAuthSessionRepository.revokeAllByTeller(...);
        // 3. 踢人下线
        saTokenSessionManager.kickout(...);
        // 4. 清除权限缓存
        permissionCacheManager.evictByUser(...);
        // 5. 审计日志
        auditLogService.recordUserDisabled(event);
    }

    // 异步处理:凭据变更 - 踢人下线
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCredentialChanged(CredentialChangedEvent event) { ... }

    // 异步处理:二次授权完成 - 更新柜员会话
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSecondaryAuthCompleted(SecondaryAuthCompletedEvent event) { ... }

    // 异步处理:权限规则变更 - 清除缓存
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPermissionRuleCreated(PermissionRuleCreatedEvent event) { ... }
}
```

### 5.4 集成事件(跨服务发布)

部分领域事件需要发布到 RocketMQ,供其他服务消费:

| 集成事件 | MQ Topic | 消费者 |
|---|---|---|
| `UserDisabledIntegrationEvent` | `iam.user.disabled` | approval-service、file-service、business-core-kernel |
| `SecondaryAuthCompletedIntegrationEvent` | `iam.secondary-auth.completed` | business-core-kernel、audit-service |
| `PermissionRuleChangedIntegrationEvent` | `iam.permission.rule.changed` | 所有业务服务(清除本地缓存) |
| `PlanDelegationChangedIntegrationEvent` | `iam.plan-delegation.changed` | 所有业务服务 |

**转换流程**: 领域事件 → `IntegrationEventConverter` 转换 → 集成事件 DTO → `EventBus.publishIntegrationEvent()` → RocketMQ

### 5.5 完整事件流图

```
┌──────────────────────────────────────────────────────────────────────┐
│                       应用服务(事务边界)                              │
│  1. 加载聚合根                                                        │
│  2. 调用聚合根行为方法(注册领域事件到聚合根内部)                       │
│  3. repository.save(aggregate)                                       │
│     └── save 时发布领域事件到 Spring 事件总线                         │
│  4. 提交事务                                                          │
└────────────────────────────┬─────────────────────────────────────────┘
                             │
                             │ 事务提交后
                             ▼
┌──────────────────────────────────────────────────────────────────────┐
│                  AFTER_COMMIT 事件订阅者(异步)                         │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  IamDomainEventListener(内部事件处理)                        │   │
│  │  - 标记凭据失效 / 撤销二次授权会话 / 踢人下线 / 清除缓存     │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  CrossContextEventListener(跨上下文初始化)                    │   │
│  │  - UserCreatedEvent → DefaultPermissionInitializer          │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  IntegrationEventPublisher(跨服务发布)                        │   │
│  │  - 领域事件 → 集成事件 DTO → RocketMQ                          │   │
│  └──────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────┘
                             │
                             ▼
┌──────────────────────────────────────────────────────────────────────┐
│                          RocketMQ                                    │
│  Topic: iam.user.disabled                                            │
│  Topic: iam.secondary-auth.completed                                │
│  Topic: iam.permission.rule.changed                                  │
│  Topic: iam.plan-delegation.changed                                  │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 6. 数据库设计

### 6.1 表结构总览

共 13 张表,使用 `t_iam_` 前缀:

| 限界上下文 | 表名 | 关联聚合 |
|---|---|---|
| authentication | `t_iam_user` | User 聚合根 |
| authentication | `t_iam_user_profile` | User (UserProfile 实体) |
| authentication | `t_iam_credential` | Credential 聚合根 |
| authentication | `t_iam_secondary_auth_session` | SecondaryAuthSession 聚合根 |
| authentication | `t_iam_login_log` | LoginLog 聚合根 |
| authentication | `t_iam_login_failure_record` | LoginLog (LoginFailureRecord 实体) |
| authorization | `t_iam_permission_rule` | PermissionRule 聚合根 |
| authorization | `t_iam_plan_delegation` | PlanDelegation 聚合根 |
| authorization | `t_iam_plan_delegation_operator` | PlanDelegation (DelegationOperator 实体) |
| authorization | `t_iam_plan_delegation_permission` | PlanDelegation (DelegationPermission 实体) |
| authorization | `t_iam_business_definition` | BusinessDefinition 实体 |
| authorization | `t_iam_business_action` | BusinessDefinition (BusinessAction 实体) |
| authorization | `t_iam_route_rule` | RouteRule 实体 |

### 6.2 ER 关系图

```
┌─────────────────────┐       ┌─────────────────────┐
│  t_iam_user         │ 1   1 │ t_iam_user_profile  │
│  (用户主表)          ├───────┤  (渠道专属档案)      │
│  - id (PK)          │       │  - user_id (FK,UK)   │
│  - channel_type     │       └─────────────────────┘
│  - login_name (UK)  │
│  - status           │       ┌─────────────────────┐
│  - ...              │ 1   N │ t_iam_credential    │
│                     ├───────┤  (凭据)              │
└─────────────────────┘       │  - owner_type       │
                              │  - owner_id (FK)    │
                              └─────────────────────┘
                              ┌─────────────────────┐
                              │t_iam_login_log      │
                              │  (登录日志)          │
                              │  - user_id (FK)     │
                              └─────────────────────┘
                              ┌─────────────────────┐
                              │t_iam_login_failure  │
                              │  _record            │
                              │  (登录失败记录)      │
                              │  - login_name       │
                              └─────────────────────┘

┌──────────────────────────────┐
│t_iam_secondary_auth_session  │
│  (二次授权会话)               │
│  - teller_id (FK to user)    │
│  - approver_id (FK to user) │
│  - plan_id                  │
│  - permission_snapshot(JSON)│
└──────────────────────────────┘

┌──────────────────────────┐       ┌────────────────────────────┐
│t_iam_permission_rule     │       │t_iam_business_definition   │
│  (权限规则)               │       │  (业务定义)                 │
│  - id (PK)               │       │  - id (PK)                 │
│  - subject_type          │       │  - business_code (UK)      │
│  - subject_id            │       │  - name                    │
│  - business_code (FK)    ├──────►│  - supported_actions(JSON) │
│  - allowed_actions(JSON) │       └───────────┬────────────────┘
│  - override_mode         │                   │ 1   N
│  - inherit_to_children   │                   ▼
│  - priority              │       ┌────────────────────────────┐
└──────────────────────────┘       │t_iam_business_action       │
                                    │  (业务动作明细)             │
                                    │  - definition_id (FK)      │
                                    │  - action                  │
                                    │  - description             │
                                    └────────────────────────────┘

                                    ┌────────────────────────────┐
                                    │t_iam_route_rule            │
                                    │  (路由权限规则)              │
                                    │  - route_pattern           │
                                    │  - check_type              │
                                    │  - check_value             │
                                    └────────────────────────────┘

┌──────────────────────────────┐       ┌──────────────────────────────┐
│t_iam_plan_delegation         │ 1   N │t_iam_plan_delegation_operator│
│  (计划代办关系)               ├───────┤  (代办指定操作员)             │
│  - id (PK)                   │       │  - delegation_id (FK)        │
│  - delegator_plan_no         │       │  - operator_id (FK to user)  │
│  - delegatee_plan_no         │       └──────────────────────────────┘
│  - delegation_type           │
│  - status                    │       ┌──────────────────────────────┐
│                              │ 1   N │t_iam_plan_delegation_permission│
│                              ├───────┤  (代办授权权限明细)            │
└──────────────────────────────┘       │  - delegation_id (FK)        │
                                        │  - business_code             │
                                        │  - action                    │
                                        └──────────────────────────────┘
```

### 6.3 关键表 DDL

#### 6.3.1 t_iam_user(PostgreSQL)

```sql
CREATE TABLE t_iam_user (
    id              BIGINT       NOT NULL,
    channel_type    VARCHAR(16)  NOT NULL,                    -- 渠道类型 INTERNET/HQ/BRANCH
    login_name      VARCHAR(64)  NOT NULL,                    -- 登录名
    display_name    VARCHAR(128),                              -- 显示名
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',    -- 用户状态 ACTIVE/DISABLED/LOCKED
    last_login_time TIMESTAMP,                                 -- 最后登录时间
    last_login_ip   VARCHAR(64),                                -- 最后登录IP
    created_by      VARCHAR(64)  NOT NULL,
    create_time     TIMESTAMP    NOT NULL,
    updated_by      VARCHAR(64)  NOT NULL,
    update_time     TIMESTAMP    NOT NULL,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    version         INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

-- 部分索引:按渠道+登录名唯一(软删除后允许重建同名)
CREATE UNIQUE INDEX uk_iam_user_channel_login
    ON t_iam_user (channel_type, login_name)
    WHERE deleted = FALSE;

CREATE INDEX idx_iam_user_status
    ON t_iam_user (status) WHERE deleted = FALSE;
```

#### 6.3.2 t_iam_user(MySQL)

```sql
CREATE TABLE t_iam_user (
    id              BIGINT       NOT NULL                  COMMENT '用户ID',
    channel_type    VARCHAR(16)  NOT NULL                  COMMENT '渠道类型',
    login_name      VARCHAR(64)  NOT NULL                  COMMENT '登录名',
    display_name    VARCHAR(128)                           COMMENT '显示名',
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '用户状态',
    last_login_time DATETIME                               COMMENT '最后登录时间',
    last_login_ip   VARCHAR(64)                            COMMENT '最后登录IP',
    created_by      VARCHAR(64)  NOT NULL,
    create_time     DATETIME     NOT NULL,
    updated_by      VARCHAR(64)  NOT NULL,
    update_time     DATETIME     NOT NULL,
    deleted         TINYINT(1)   NOT NULL DEFAULT 0,
    version         INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_iam_user_status (status, deleted),
    KEY idx_iam_user_channel (channel_type, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM用户主表';

-- MySQL 不支持部分索引,使用 deleted 字段参与唯一索引
CREATE UNIQUE INDEX uk_iam_user_channel_login
    ON t_iam_user (channel_type, login_name, deleted);
```

> 完整 DDL 见实现阶段,核心表结构如上,其他表遵循相同的设计模式。

### 6.4 JSON 字段处理

| 字段 | PG 类型 | MySQL 类型 | Java 类型 |
|---|---|---|---|
| `permission_snapshot` | JSONB | JSON | `Set<String>` |
| `allowed_actions` | JSONB | JSON | `Set<String>` |
| `supported_actions` | JSONB | JSON | `Set<String>` |
| `aux_data` | JSONB | JSON | `Map<String, Object>` |

MyBatis-Flex 自动处理 JSON 类型序列化/反序列化。

### 6.5 软删除处理

- **PostgreSQL**: 使用部分索引(`WHERE deleted = FALSE`)保证软删除后可重建唯一记录
- **MySQL**: 将 `deleted` 字段加入唯一索引(`uk_xxx (field, deleted)`)

### 6.6 权限计算查询优化

权限计算核心查询(一次查询获取所有维度的规则):

```sql
SELECT * FROM t_iam_permission_rule
WHERE deleted = FALSE
  AND status = 'ACTIVE'
  AND effective_at <= NOW()
  AND (expire_at IS NULL OR expire_at > NOW())
  AND (
    (subject_type = 'CUSTOMER'         AND subject_id = #{customerNo})
    OR (subject_type = 'OPERATION_MODE' AND subject_id = #{operationMode})
    OR (subject_type = 'PRODUCT'        AND subject_id = #{productNo})
    OR (subject_type = 'PLAN'           AND subject_id = #{planNo})
    OR (subject_type = 'ACCOUNT_MANAGER' AND subject_id = #{accountManagerCode})
  )
ORDER BY priority DESC, subject_type DESC;
```

通过复合索引 `idx_iam_perm_rule_subject` 和 `idx_iam_perm_rule_subject_biz` 优化。

### 6.7 与 sa-token Redis 的关系

```
数据库(持久层)                    sa-token Redis(会话层)
- 用户、凭据、权限规则              - Token - Session 会话数据
- 所有变更通过应用层管理时间戳      - currentPlanId: 当前选择的计划
                                  - currentPermissions: 缓存的权限码集合
                                  - secondaryAuthSessionId: 网点二次授权会话ID
                                  - borrowedApproverId: 借用的经办人ID
```

**职责分离**:

- 数据库存储**业务数据**
- Redis 存储**会话状态和权限缓存**
- 数据库变更 → 通过领域事件触发 Redis 缓存失效
- Redis 缓存未命中 → 从数据库重新计算

### 6.8 初始化数据

```sql
-- 业务定义初始化
INSERT INTO t_iam_business_definition (id, business_code, business_name, description, supported_actions, status, ...)
VALUES
(1, 'ANNUITY_ESTABLISH', '年金计划设立', '企业年金计划设立业务', '["HANDLE","QUERY","AUDIT"]', 'ACTIVE', ...),
(2, 'ANNUITY_CONTRIBUTION', '年金缴费', '企业年金缴费业务', '["HANDLE","QUERY"]', 'ACTIVE', ...),
(3, 'ANNUITY_PAYMENT', '年金支付', '企业年金支付业务', '["HANDLE","QUERY","AUDIT"]', 'ACTIVE', ...);

-- 网关路由权限规则初始化
INSERT INTO t_iam_route_rule (id, route_pattern, check_type, check_value, description, enabled, priority, ...)
VALUES
(1, '/internet/**', 'login', NULL, '网上渠道登录校验', TRUE, 100, ...),
(2, '/hq/**', 'login', NULL, '总部渠道登录校验', TRUE, 100, ...),
(3, '/branch/**', 'login', NULL, '网点渠道登录校验', TRUE, 100, ...);
```

---

## 7. 防腐层 Gateway 设计

### 7.1 设计原则

防腐层(ACL)隔离外部系统的数据模型与内部领域模型,避免外部概念污染本服务。

```
领域层(domain.gateway 接口)        基础设施层(infrastructure.gateway 实现)
┌──────────────────────┐           ┌─────────────────────────────────┐
│ PlanMetadataGateway   │◄──────────┤ PlanMetadataGatewayImpl         │
├──────────────────────┤           ├─────────────────────────────────┤
│ CustomerGateway      │◄──────────┤ CustomerGatewayImpl             │
├──────────────────────┤           ├─────────────────────────────────┤
│ ProductGateway       │◄──────────┤ ProductGatewayImpl              │
├──────────────────────┤           ├─────────────────────────────────┤
│ OrganizationGateway  │◄──────────┤ OrganizationGatewayImpl         │
└──────────────────────┘           └─────────────────────────────────┘
                                              │
                                              ▼
                                   外部系统 API(Retrofit @HttpExchange)
```

### 7.2 Gateway 接口定义(领域层)

#### 7.2.1 PlanMetadataGateway

```java
public interface PlanMetadataGateway {

    /** 按计划编号加载计划元数据 */
    Optional<PlanMetadata> load(PlanNo planNo);

    /** 按客户编号查询其名下所有计划 */
    List<PlanMetadata> findByCustomer(CustomerNo customerNo);

    /** 按产品编号查询该产品下所有计划 */
    List<PlanMetadata> findByProduct(ProductNo productNo);

    /** 查询指定客户的所有下属子客户的计划(集团客户场景) */
    List<PlanMetadata> findSubPlansByGroupParent(CustomerNo parentCustomerNo);

    /** 查询计划的所有上级计划链(用于权限继承判断) */
    List<PlanMetadata> findAncestorPlans(PlanNo planNo);
}
```

#### 7.2.2 CustomerGateway

```java
public interface CustomerGateway {

    /** 按客户编号加载客户信息 */
    Optional<CustomerInfo> load(CustomerNo customerNo);

    /** 批量查询客户信息 */
    List<CustomerInfo> loadBatch(List<CustomerNo> customerNos);

    /** 查询集团客户的下属子公司列表 */
    List<CustomerInfo> findSubsidiaries(CustomerNo groupCustomerNo);

    /** 查询客户的上级集团 */
    Optional<CustomerInfo> findParentGroup(CustomerNo customerNo);

    /** 查询客户的所有上级集团链(递归向上) */
    List<CustomerInfo> findAncestorGroups(CustomerNo customerNo);
}
```

#### 7.2.3 ProductGateway

```java
public interface ProductGateway {

    /** 按产品编号加载产品信息 */
    Optional<ProductInfo> load(ProductNo productNo);

    record ProductInfo(
        ProductNo productNo,
        String productName,
        OperationMode operationMode,
        AccountManagerCode accountManagerCode
    ) {}
}
```

#### 7.2.4 OrganizationGateway(可选)

```java
public interface OrganizationGateway {

    /** 查询用户的组织架构信息 */
    Optional<OrganizationInfo> load(UserNo userNo);

    /** 查询用户的所有上级部门链 */
    List<OrganizationInfo> findAncestorDepartments(UserNo userNo);
}
```

### 7.3 领域模型(领域层)

```java
// 计划元数据值对象
public record PlanMetadata(
    PlanNo planNo,
    String planName,
    CustomerNo customerNo,
    String customerName,
    ProductNo productNo,
    String productName,
    OperationMode operationMode,
    AccountManagerCode accountManagerCode,
    PlanNo parentPlanNo,                    // 父计划编号(集团客户场景,可空)
    LocalDateTime dataSnapshotAt            // 数据快照时间
) implements ValueObject {
    public boolean isSubPlan() { return parentPlanNo != null; }
}

// 客户信息值对象
public record CustomerInfo(
    CustomerNo customerNo,
    String customerName,
    CustomerType customerType,
    CustomerNo parentCustomerNo,
    List<CustomerNo> subCustomerNos,
    LocalDateTime dataSnapshotAt
) implements ValueObject {
    public boolean isGroup() { return customerType == CustomerType.GROUP; }
    public boolean isSubsidiary() { return parentCustomerNo != null; }
}
```

### 7.4 Gateway 实现(基础设施层)

**当前实现为 Mock 模式**,返回固定内容,等后续拿到外部系统接口再详细编码。

```java
@Component
public class PlanMetadataGatewayImpl implements PlanMetadataGateway {

    private final ExternalPlanApi externalPlanApi;
    private final PlanMetadataConverter converter;

    @Override
    public Optional<PlanMetadata> load(PlanNo planNo) {
        log.info("[PlanMetadataGateway] Mock 加载计划元数据: planNo={}", planNo.value());

        // TODO: 接入外部系统后替换为真实调用
        // ApiResult<ExternalPlanResponse> result = externalPlanApi.getByPlanNo(planNo.value());
        // if (result == null || !result.isSuccess() || result.getData() == null) {
        //     return Optional.empty();
        // }
        // return Optional.of(converter.toPlanMetadata(result.getData()));

        return Optional.of(buildMockPlanMetadata(planNo));
    }

    // ... 其他方法类似
}
```

**Mock 模式配置开关**:

```yaml
iam:
  external:
    annuity-core:
      mock-enabled: true              # true 时使用 Mock 实现
      base-url: http://annuity-core-service/api/v1
      connect-timeout: 5000
      read-timeout: 10000
```

### 7.5 外部 API 接口定义(基础设施层)

```java
@HttpExchange("/external/plan")
public interface ExternalPlanApi {
    @GetExchange("/get")
    ApiResult<ExternalPlanResponse> getByPlanNo(String planNo);

    @GetExchange("/list-by-customer")
    ApiResult<List<ExternalPlanResponse>> listByCustomerNo(String customerNo);

    // ... 其他方法
}
```

外部响应 DTO 定义在 infrastructure 层,不暴露给领域层:

```java
public record ExternalPlanResponse(
    String planNo,
    String planName,
    String customerNo,
    String customerName,
    String productNo,
    String productName,
    String operationMode,
    String accountManagerCode,
    String parentPlanNo,
    LocalDateTime dataSnapshotAt
) {}
```

### 7.6 Converter 转换器(基础设施层)

```java
@Component
public class PlanMetadataConverter {

    public PlanMetadata toPlanMetadata(ExternalPlanResponse response) {
        if (response == null) return null;
        return new PlanMetadata(
            PlanNo.of(response.planNo()),
            response.planName(),
            CustomerNo.of(response.customerNo()),
            response.customerName(),
            ProductNo.of(response.productNo()),
            response.productName(),
            parseOperationMode(response.operationMode()),
            new AccountManagerCode(response.accountManagerCode()),
            response.parentPlanNo() != null ? PlanNo.of(response.parentPlanNo()) : null,
            response.dataSnapshotAt()
        );
    }

    private OperationMode parseOperationMode(String mode) {
        return switch (mode.toUpperCase()) {
            case "SINGLE_TRUSTEE" -> OperationMode.SINGLE_TRUSTEE;
            case "SINGLE_ACCOUNT_MANAGER" -> OperationMode.SINGLE_ACCOUNT_MANAGER;
            case "TRUSTEE_AND_ACCOUNT_MANAGER" -> OperationMode.TRUSTEE_AND_ACCOUNT_MANAGER;
            default -> throw new IllegalArgumentException("未知的运作模式: " + mode);
        };
    }
}
```

---

## 8. 错误码体系

### 8.1 模块缩写

新增 `IAM` 缩写到 SERVICE 域:

| 域 | 模块缩写 | 模块 | 错误码区间 |
|---|---|---|---|
| SERVICE | IAM | iam-service | SERVICE.IAM.0001-9999 |

### 8.2 错误码分组

```
SERVICE.IAM.0001-0099   认证域(authentication)
  ├── 0001-0019  用户管理
  ├── 0020-0039  凭据管理
  ├── 0040-0059  登录相关
  └── 0060-0079  二次授权

SERVICE.IAM.0100-0199   授权域(authorization)
  ├── 0100-0119  权限规则
  ├── 0120-0139  计划代办
  ├── 0140-0159  业务定义
  ├── 0160-0179  路由规则
  └── 0180-0199  防腐层查询

SERVICE.IAM.0200-0299   系统层(system)
  ├── 0200-0219  权限计算
  ├── 0220-0239  sa-token 集成
  ├── 0240-0259  外部系统调用
  └── 0260-0299  配置错误
```

### 8.3 枚举类划分

按限界上下文分为三个错误码枚举类:

| 枚举类 | 所属域 | 包路径 |
|---|---|---|
| `IamAuthErrorCode` | 认证域 | `iam.domain.authentication.errorcode` |
| `IamAuthzErrorCode` | 授权域 | `iam.domain.authorization.errorcode` |
| `IamSystemErrorCode` | 系统层 | `iam.domain.system.errorcode` |

### 8.4 认证域错误码(IamAuthErrorCode)

| 错误码 | 常量名 | 含义 | 异常类型 |
|---|---|---|---|
| SERVICE.IAM.0001 | USER_NOT_FOUND | 用户不存在 | BusinessException |
| SERVICE.IAM.0002 | USER_ALREADY_EXISTS | 用户已存在 | BusinessException |
| SERVICE.IAM.0003 | USER_STATUS_INVALID | 用户状态不允许此操作 | DomainException |
| SERVICE.IAM.0004 | ACCOUNT_DISABLED | 账号已禁用 | BusinessException |
| SERVICE.IAM.0005 | ACCOUNT_LOCKED | 账号已锁定 | BusinessException |
| SERVICE.IAM.0006 | LOGIN_NAME_DUPLICATE | 登录名重复 | BusinessException |
| SERVICE.IAM.0007 | CHANNEL_TYPE_INVALID | 渠道类型无效 | DomainException |
| SERVICE.IAM.0008 | USER_PROFILE_NOT_FOUND | 用户档案不存在 | BusinessException |
| SERVICE.IAM.0009 | NOT_LOGGED_IN | 当前请求未登录 | BusinessException |
| SERVICE.IAM.0010 | USER_PROFILE_INCOMPLETE | 用户档案信息不完整 | BusinessException |
| SERVICE.IAM.0020 | CREDENTIAL_INVALID | 凭据无效 | BusinessException |
| SERVICE.IAM.0021 | CREDENTIAL_EXPIRED | 凭据已过期 | DomainException |
| SERVICE.IAM.0022 | CREDENTIAL_NOT_FOUND | 凭据不存在 | BusinessException |
| SERVICE.IAM.0023 | CREDENTIAL_TYPE_NOT_SUPPORTED | 不支持的凭据类型 | DomainException |
| SERVICE.IAM.0024 | CREDENTIAL_VALIDATION_FAILED | 凭据校验失败 | BusinessException |
| SERVICE.IAM.0025 | CREDENTIAL_REVOKED | 凭据已撤销 | DomainException |
| SERVICE.IAM.0026 | CREDENTIAL_TYPE_DUPLICATE | 同类型凭据已存在 | BusinessException |
| SERVICE.IAM.0027 | CREDENTIAL_OWNER_MISMATCH | 凭据归属不匹配 | DomainException |
| SERVICE.IAM.0040 | LOGIN_FAIL_LIMIT_EXCEEDED | 登录失败次数超限 | BusinessException |
| SERVICE.IAM.0041 | LOGIN_NAME_OR_PASSWORD_ERROR | 登录名或密码错误 | BusinessException |
| SERVICE.IAM.0042 | CAPTCHA_INVALID | 验证码无效 | BusinessException |
| SERVICE.IAM.0043 | LOGIN_LOG_NOT_FOUND | 登录日志不存在 | BusinessException |
| SERVICE.IAM.0044 | LOGIN_FAILURE_RECORD_NOT_FOUND | 登录失败记录不存在 | BusinessException |
| SERVICE.IAM.0060 | SECONDARY_AUTH_SESSION_NOT_FOUND | 二次授权会话不存在 | BusinessException |
| SERVICE.IAM.0061 | SECONDARY_AUTH_SESSION_EXPIRED | 二次授权会话已过期 | BusinessException |
| SERVICE.IAM.0062 | SECONDARY_AUTH_SESSION_COMPLETED | 二次授权会话已完成 | BusinessException |
| SERVICE.IAM.0063 | SECONDARY_AUTH_SESSION_REVOKED | 二次授权会话已撤销 | BusinessException |
| SERVICE.IAM.0064 | SECONDARY_AUTH_STRATEGY_NOT_SUPPORTED | 不支持的二次授权策略 | DomainException |
| SERVICE.IAM.0065 | SECONDARY_AUTH_REQUIRED | 需要完成二次授权 | BusinessException |
| SERVICE.IAM.0066 | SECONDARY_AUTH_PENDING | 已有待处理的二次授权请求 | BusinessException |
| SERVICE.IAM.0067 | SECONDARY_AUTH_APPROVER_MISMATCH | 二次授权经办人不匹配 | BusinessException |
| SERVICE.IAM.0068 | SECONDARY_AUTH_PERMISSION_SNAPSHOT_MISSING | 权限快照缺失 | SystemException |
| SERVICE.IAM.0069 | NOT_BRANCH_USER_CANNOT_SWITCH_BACK | 当前身份非柜员,无法切换回柜员 | BusinessException |
| SERVICE.IAM.0070 | SECONDARY_AUTH_APPROVER_INVALID | 二次授权经办人无效 | BusinessException |
| SERVICE.IAM.0071 | SECONDARY_AUTH_PLAN_MISMATCH | 二次授权计划不匹配 | BusinessException |

### 8.5 授权域错误码(IamAuthzErrorCode)

| 错误码 | 常量名 | 含义 | 异常类型 |
|---|---|---|---|
| SERVICE.IAM.0100 | PERMISSION_RULE_NOT_FOUND | 权限规则不存在 | BusinessException |
| SERVICE.IAM.0101 | PERMISSION_RULE_CODE_DUPLICATE | 规则编码重复 | BusinessException |
| SERVICE.IAM.0102 | PERMISSION_RULE_STATUS_INVALID | 规则状态不允许此操作 | DomainException |
| SERVICE.IAM.0103 | SUBJECT_TYPE_INVALID | 主体类型无效 | DomainException |
| SERVICE.IAM.0104 | SUBJECT_ID_REQUIRED | 主体标识不能为空 | DomainException |
| SERVICE.IAM.0105 | OVERRIDE_MODE_INVALID | 覆盖模式无效 | DomainException |
| SERVICE.IAM.0106 | ACTION_EMPTY | 动作集合不能为空 | DomainException |
| SERVICE.IAM.0107 | BUSINESS_CODE_INVALID | 业务编码无效 | DomainException |
| SERVICE.IAM.0108 | PRIORITY_INVALID | 优先级无效 | DomainException |
| SERVICE.IAM.0109 | RULE_EFFECTIVE_PERIOD_INVALID | 规则生效时间区间无效 | DomainException |
| SERVICE.IAM.0120 | PLAN_DELEGATION_NOT_FOUND | 计划代办关系不存在 | BusinessException |
| SERVICE.IAM.0121 | PLAN_DELEGATION_CODE_DUPLICATE | 代办编码重复 | BusinessException |
| SERVICE.IAM.0122 | PLAN_DELEGATION_STATUS_INVALID | 代办状态不允许此操作 | DomainException |
| SERVICE.IAM.0123 | PLAN_DELEGATION_DUPLICATE | 代办关系已存在 | BusinessException |
| SERVICE.IAM.0124 | PLAN_DELEGATION_SELF_DELEGATION | 授权方和被授权方不能相同 | DomainException |
| SERVICE.IAM.0125 | DELEGATION_TYPE_INVALID | 代办类型无效 | DomainException |
| SERVICE.IAM.0126 | DELEGATION_OPERATOR_NOT_SPECIFIED | 未指定代办操作员 | DomainException |
| SERVICE.IAM.0127 | DELEGATION_PERMISSION_EMPTY | 代办权限不能为空 | DomainException |
| SERVICE.IAM.0128 | DELEGATION_OPERATOR_DUPLICATE | 代办操作员重复指定 | DomainException |
| SERVICE.IAM.0129 | DELEGATION_PERMISSION_DUPLICATE | 代办权限重复指定 | DomainException |
| SERVICE.IAM.0140 | BUSINESS_DEFINITION_NOT_FOUND | 业务定义不存在 | BusinessException |
| SERVICE.IAM.0141 | BUSINESS_CODE_DUPLICATE | 业务编码重复 | BusinessException |
| SERVICE.IAM.0142 | BUSINESS_DEFINITION_STATUS_INVALID | 业务定义状态不允许此操作 | DomainException |
| SERVICE.IAM.0143 | BUSINESS_ACTION_NOT_SUPPORTED | 业务不支持该动作 | DomainException |
| SERVICE.IAM.0144 | BUSINESS_ACTION_DUPLICATE | 业务动作重复 | DomainException |
| SERVICE.IAM.0160 | ROUTE_RULE_NOT_FOUND | 路由规则不存在 | BusinessException |
| SERVICE.IAM.0161 | ROUTE_PATTERN_DUPLICATE | 路由匹配模式重复 | BusinessException |
| SERVICE.IAM.0162 | ROUTE_RULE_CHECK_TYPE_INVALID | 路由校验类型无效 | DomainException |
| SERVICE.IAM.0163 | ROUTE_RULE_PRIORITY_INVALID | 路由规则优先级无效 | DomainException |
| SERVICE.IAM.0180 | PLAN_NOT_FOUND | 计划不存在 | BusinessException |
| SERVICE.IAM.0181 | PLAN_NOT_SELECTABLE | 计划不可选择 | BusinessException |
| SERVICE.IAM.0182 | PLAN_NOT_AUTHORIZED | 计划未授权 | BusinessException |
| SERVICE.IAM.0183 | CUSTOMER_NOT_FOUND | 客户不存在 | BusinessException |
| SERVICE.IAM.0184 | PRODUCT_NOT_FOUND | 产品不存在 | BusinessException |
| SERVICE.IAM.0185 | OPERATION_MODE_INVALID | 运作模式无效 | DomainException |
| SERVICE.IAM.0186 | ACCOUNT_MANAGER_CODE_INVALID | 账管人编号无效 | DomainException |
| SERVICE.IAM.0187 | CUSTOMER_TYPE_INVALID | 客户类型无效 | DomainException |
| SERVICE.IAM.0188 | NO_SELECTABLE_PLAN | 无可选计划 | BusinessException |

### 8.6 系统层错误码(IamSystemErrorCode)

| 错误码 | 常量名 | 含义 | 异常类型 |
|---|---|---|---|
| SERVICE.IAM.0200 | PERMISSION_CALCULATION_FAILED | 权限计算失败 | SystemException |
| SERVICE.IAM.0201 | PERMISSION_CACHE_EVICT_FAILED | 权限缓存失效失败 | SystemException |
| SERVICE.IAM.0202 | PERMISSION_STRATEGY_NOT_FOUND | 权限组合策略未找到 | SystemException |
| SERVICE.IAM.0203 | PERMISSION_SNAPSHOT_BUILD_FAILED | 权限快照构建失败 | SystemException |
| SERVICE.IAM.0204 | PERMISSION_SNAPSHOT_EXPIRED | 权限快照已过期 | SystemException |
| SERVICE.IAM.0205 | PERMISSION_CONTEXT_INVALID | 权限计算上下文无效 | SystemException |
| SERVICE.IAM.0206 | PERMISSION_RULE_LOAD_FAILED | 权限规则加载失败 | SystemException |
| SERVICE.IAM.0220 | SA_TOKEN_SESSION_UPDATE_FAILED | sa-token 会话更新失败 | SystemException |
| SERVICE.IAM.0221 | SA_TOKEN_KICKOUT_FAILED | 踢人下线失败 | SystemException |
| SERVICE.IAM.0222 | SA_TOKEN_CONFIG_INVALID | sa-token 配置无效 | SystemException |
| SERVICE.IAM.0223 | SA_TOKEN_STP_LOGIC_NOT_FOUND | sa-token StpLogic 未找到 | SystemException |
| SERVICE.IAM.0224 | SA_TOKEN_CHANNEL_NOT_RECOGNIZED | 无法识别当前请求渠道 | SystemException |
| SERVICE.IAM.0225 | SA_TOKEN_PERMISSION_LOAD_FAILED | sa-token 权限加载失败 | SystemException |
| SERVICE.IAM.0240 | EXTERNAL_API_CALL_FAILED | 外部系统调用失败 | SystemException |
| SERVICE.IAM.0241 | EXTERNAL_API_TIMEOUT | 外部系统调用超时 | SystemException |
| SERVICE.IAM.0242 | EXTERNAL_API_RESPONSE_INVALID | 外部系统响应无效 | SystemException |
| SERVICE.IAM.0243 | EXTERNAL_API_UNAVAILABLE | 外部系统不可用 | SystemException |
| SERVICE.IAM.0244 | PLAN_METADATA_LOAD_FAILED | 计划元数据加载失败 | SystemException |
| SERVICE.IAM.0245 | CUSTOMER_INFO_LOAD_FAILED | 客户信息加载失败 | SystemException |
| SERVICE.IAM.0246 | PRODUCT_INFO_LOAD_FAILED | 产品信息加载失败 | SystemException |
| SERVICE.IAM.0247 | ORGANIZATION_INFO_LOAD_FAILED | 组织架构信息加载失败 | SystemException |
| SERVICE.IAM.0248 | EXTERNAL_API_DESERIALIZE_FAILED | 外部系统响应反序列化失败 | SystemException |
| SERVICE.IAM.0260 | CONFIG_INVALID | 配置无效 | SystemException |
| SERVICE.IAM.0261 | CHANNEL_CONFIG_NOT_FOUND | 渠道配置未找到 | SystemException |
| SERVICE.IAM.0262 | PERMISSION_CONFIG_INVALID | 权限配置无效 | SystemException |
| SERVICE.IAM.0263 | SECONDARY_AUTH_CONFIG_INVALID | 二次授权配置无效 | SystemException |
| SERVICE.IAM.0264 | EXTERNAL_API_CONFIG_INVALID | 外部系统 API 配置无效 | SystemException |
| SERVICE.IAM.0265 | BUSINESS_REGISTRY_NOT_INITIALIZED | 业务注册表未初始化 | SystemException |

### 8.7 错误码统计

| 枚举类 | 码段 | 已用码值数 | 预留码值数 | 总容量 |
|---|---|---|---|---|
| IamAuthErrorCode | 0001-0079 | 35 | 44 | 79 |
| IamAuthzErrorCode | 0100-0199 | 38 | 61 | 99 |
| IamSystemErrorCode | 0200-0299 | 28 | 71 | 99 |
| **合计** | 0001-0299 | **101** | **176** | **277** |

### 8.8 错误码规范文档更新

需要在 `08-错误码规范.md` 中新增 IAM 模块的码段分配:

```markdown
### SERVICE 域

| 模块缩写 | 模块 |
|---------|------|
| APPROVAL | approval-service |
| FILE | file-service |
| INTEGRATION | integration-service |
| ANNUITY | annuity-service |
| IAM | iam-service |    <!-- 新增 -->
```

---

## 9. 依赖配置

### 9.1 根 pom.xml 新增

```xml
<properties>
    <sa-token.version>1.45.0</sa-token.version>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- 2nd Dependencies -->
        ...
        <!-- iam-service 模块 -->
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>iam-types</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>iam-domain</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>iam-api</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>iam-application</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>iam-adapter</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>iam-infrastructure</artifactId>
            <version>${project.version}</version>
        </dependency>
        <!-- sa-token 依赖 -->
        <dependency>
            <groupId>cn.dev33</groupId>
            <artifactId>sa-token-spring-boot3-starter</artifactId>
            <version>${sa-token.version}</version>
        </dependency>
        <dependency>
            <groupId>cn.dev33</groupId>
            <artifactId>sa-token-reactor-spring-boot3-starter</artifactId>
            <version>${sa-token.version}</version>
        </dependency>
        <dependency>
            <groupId>cn.dev33</groupId>
            <artifactId>sa-token-redis-jackson</artifactId>
            <version>${sa-token.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 9.2 模块依赖关系

```
iam-types       → shared-types
iam-domain      → shared-domain + iam-types
iam-api         → shared-api + iam-types
iam-application → iam-api + iam-domain + shared-*starter
iam-adapter     → iam-api + iam-application + sa-token-spring-boot3-starter
iam-infrastructure → iam-domain + shared-*starter
iam-starter     → iam-adapter + iam-infrastructure + sa-token-redis-jackson
```

---

## 10. 关键设计要点总结

| 设计点 | 方案 | 理由 |
|---|---|---|
| 服务架构 | 单服务 iam-service + 两个限界上下文 | 复杂度匹配,未来可平滑拆分 |
| 用户模型 | 统一 User 聚合 + UserProfile 实体(渠道档案表) | 三渠道统一管理,差异通过档案表表达 |
| Credential 独立聚合 | 因扩展性独立(多凭据类型) | 避免 User 膨胀,独立审计 |
| SecondaryAuthSession 独立聚合 | 因状态机复杂性独立 | 独立不变量 + 独立事务边界 |
| 权限快照 | 授权瞬间冻结 | 避免授权后权限变更导致安全漏洞 |
| BusinessDefinition 非聚合根 | 配置驱动的元数据 | 符合开闭原则,新增业务类型不改聚合 |
| sa-token 多 StpLogic | 每渠道独立 StpLogic | Token 互不干扰,便于独立管理 |
| Token Header 命名 | `satoken-{channel}` | 避免冲突,便于识别 |
| 权限缓存位置 | sa-token Token-Session | 随会话生命周期管理,无需手动维护 |
| 权限计算时机 | 选择计划时 | 避免每次请求都计算,性能优化 |
| 网点渠道权限 | 使用快照不重算 | 保证授权后权限一致性,安全考虑 |
| 计划切换 API | 统一接口三渠道共用 | 减少代码重复,便于维护 |
| 优先级算法 SPI | 算法可扩展,顺序可配置 | 符合开闭原则,业务规则调整不改代码 |
| 防腐层 Mock 实现 | 当前返回固定内容 | 等外部接口提供后再编码 |
| 软删除唯一索引 | PG 部分索引 / MySQL 联合唯一索引 | 兼容两种数据库 |
| JSON 字段 | PG 用 JSONB,MySQL 用 JSON | 充分利用各自优势 |
| 跨聚合一致性 | 领域事件 + 异步处理 | 最终一致性,业务可容忍 |

---

## 11. 待确认事项与假设

以下为设计过程中采用的默认假设,如需调整请在审查时指出:

| 事项 | 假设默认值 |
|---|---|
| 凭据加密算法 | BCrypt(密码)、RSA(UKEY) |
| Token 有效期 | 配置化,默认 30 天(internet)/ 8 小时(hq/branch) |
| 二次授权会话有效期 | 配置化,默认 2 小时 |
| 待授权会话过期时间 | 配置化,默认 5 分钟 |
| 登录失败锁定阈值 | 配置化,默认 5 次 |
| 权限缓存有效期 | 配置化,默认 30 分钟 |
| 同一柜员待授权会话数 | 1 个(互斥) |
| 网上渠道同端互斥 | 是(防止账号被盗用) |
| 总部渠道多端登录 | 允许 |
| 业务定义管理界面 | 不在本次范围(仅表结构和领域服务接口) |
| 路由规则管理界面 | 不在本次范围(仅表结构和加载机制) |

---

## 12. Spec 自审记录

本次自审发现并修复了以下 5 处问题:

| 编号 | 类型 | 位置 | 问题 | 修复方式 |
|---|---|---|---|---|
| 1 | 矛盾 | 第 8.7 节 | 错误码统计数字与实际枚举数不符(写 92,实 101) | 重新统计:35/38/28,合计 101 |
| 2 | 矛盾 | 第 6.2 节 | ER 图缺失 `t_iam_business_action` 表(6.1 列出 13 张,ER 图仅 12 张) | 补全 ER 图,展示与 `t_iam_business_definition` 的 1:N 关系 |
| 3 | 矛盾 | 第 4.5 节 | 网关权限校验使用默认 `StpUtil.checkPermission`,与 4.1 节定义的三套 StpLogic 矛盾 | 改为通过 `ChannelContext.checkPermission` 渠道分派 |
| 4 | 歧义 | 第 3.7 节 | `PermissionResolver.resolve` 返回类型不明确(描述说"权限码集合",4.6 节使用 `PermissionSnapshot`) | 明确返回 `PermissionSnapshot` 值对象,补充接口签名 |
| 5 | 歧义 | 第 4.3.3 节第 7 步 | 柜员 Token-Session 更新时机不明(轮询接口是否写会话?) | 拆分为步骤 7(查询)+ 7a(异步事件更新会话),明确职责 |

**遗留说明**:
- 现有 `IamAuthErrorCode.java` 实现文件包含 11 个错误码,使用非结构化编号(0001-0015 跳号)。本设计文档定义了 35 个错误码的结构化编号方案(0001-0019 用户、0020-0039 凭据、0040-0059 登录、0060-0079 二次授权)。实施阶段需将现有文件重构以对齐本设计。
- 第 6.3 节仅展示 `t_iam_user` 表的完整 DDL 作为双数据库范式参考,其余 12 张表 DDL 在实施阶段编写,遵循相同的设计模式(软删除、版本号、双 DB 兼容)。

---

## 13. 下一步

1. **用户确认设计文档**: 审查并确认上述设计
2. **使用 writing-plans 技能拆解实施任务**: 将设计转化为可执行的实施计划
3. **按计划编码实现**: 遵循 TDD,先写测试再实现

---

**设计文档版本**: v1.0
**最后更新**: 2026-07-26
**Spec 自审状态**: 已完成(占位符、矛盾、范围、歧义检查均通过,修复 5 处问题)
