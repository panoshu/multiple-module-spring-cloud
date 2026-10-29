# 登录流程数据流转与服务调用链路

## 一、全局架构概览

```
┌──────────┐     ┌───────────────────┐     ┌──────────────┐     ┌──────────────────┐
│  Client   │────>│  Gateway (Filter) │────>│  auth-service │────>│  Business Service │
└──────────┘     └───────────────────┘     └──────────────┘     └──────────────────┘
                  - SaReactorFilter(-200)   - SessionController   - SessionContextResolver
                  - SessionContextInjector  - SessionAppService    - AccountIdResolver
                    (-150)                 - IdentityResService   - PermissionExecutor
                                           - LoginTokenService
                                           - Session (Aggregate)
                                           - SecondaryAuthAppSvc
```

### 渠道与 StpLogic 映射

| 渠道 | `AnnuityChannel` 枚举 | 网关路径前缀 | Token Header | StpLogic loginType | 网关实例 |
|---|---|---|---|---|---|
| 网上 | `NETAPP("01")` | `/internet` | `satoken-internet` | `internet` | internet-gateway:18081 |
| 总部 | `TELLER("02")` | `/hq` | `satoken-hq` | `hq` | intranet-gateway:18082 |
| 网点 | `BANK_BRANCH("05")` | `/branch` | `satoken-branch` | `branch` | intranet-gateway:18082 |

---

## 二、网关过滤器链

所有请求经过网关时按以下顺序执行：

| Order | 过滤器 | 职责 |
|---|---|---|
| -300 | `IP_BLOCK` | IP 黑名单拦截 |
| -250 | `EXCLUDE_ROUTE` | 路由排除/阻断 |
| -200 | `SaReactorFilter` (AUTH) | sa-token 登录校验 |
| -150 | `SessionContextInjector` | 注入会话上下文到下游请求头 |
| -100 | `RATE_LIMIT` | 限流 |
| -50 | `TENANT_RESOLVE` | 租户解析 |
| -10 | `CRYPTO` | 加解密 |
| 0 | `REQUEST_LOG` | 请求日志 |
| 200 | `RESPONSE_REWRITE` | 响应重写 |

---

## 三、网上渠道 (INTERNET) 登录流程

### 3.1 完整调用链路

```
Client
  │ POST /internet/auth/login
  │ Body: { credentialType, ownerType, ownerId, channel, proof, phoneNumber }
  │
  ▼
internet-gateway (port 18081)
  │ [SaReactorFilter @-200]
  │   ├── isPublicPath("/internet/auth/login") → true → 放行
  │
  ▼
auth-service → SessionController.login()
  │
  │ 1. AnnuityChannel.valueOf(request.channel())  → NETAPP
  │ 2. resolveOwner(ownerType, ownerId)           → CredentialOwner
  │ 3. new Mobile(phoneNumber)                     → Mobile (nullable)
  │ 4. new OpenSessionWithCredentialCommand(owner, channel, proof, phone)
  │
  ▼
SessionApplicationService.openSessionWithCredential()
  │
  │ 5. identityResolutionService.resolve(owner, channel, proof, phone)
  │    ┌──────────────────────────────────────────────────────────────────┐
  │    │ IdentityResolutionService.resolve()                             │
  │    │   ├── credentialRepository.findByOwner(owner) → List<Credential>│
  │    │   ├── .filter(c -> c.applicableChannels().contains(channel))    │
  │    │   ├── .anyMatch(c -> credentialAuthenticator.authenticate(c, proof))│
  │    │   │       ┌─────────────────────────────────────────────────┐   │
  │    │   │       │ CredentialAuthenticator.authenticate()          │   │
  │    │   │       │  sealed match:                                  │   │
  │    │   │       │   PasswordCredential → PasswordAuthProvider     │   │
  │    │   │       │   UKeyCredential   → UKeyAuthProvider          │   │
  │    │   │       │  校验 proof (密码哈希/UKey签名)                 │   │
  │    │   │       └─────────────────────────────────────────────────┘   │
  │    │   │                                                             │
  │    │   │ if owner instanceof UserCredentialOwner:                    │
  │    │   │   return Optional.of(userNo)   // accountId = userNo       │
  │    │   │                                                             │
  │    │   │ if owner instanceof CustomerCredentialOwner|PlanCredentialOwner:│
  │    │   │   return locateByPhone(mobile, owner):                     │
  │    │   │     ├── accountRepository.findByMobile(mobile) → UserAggregate│
  │    │   │     ├── .map(UserAggregate::id) → UserNo                   │
  │    │   │     └── .filter(accountId -> belongsToOwner(accountId, owner))│
  │    │   │              └── AgentIdentityAssignment 校验归属关系       │
  │    │   │                                                             │
  │    │   └── .flatMap(accountRepository::load)                         │
  │    │       .filter(UserAggregate::isActive)  // 冻结账号不可登录      │
  │    │       .map(UserAggregate::id)                                 │
  │    └──────────────────────────────────────────────────────────────────┘
  │
  │ 6. buildSession(accountId, channel)
  │    ┌──────────────────────────────────────────────────────────────────┐
  │    │ buildSession()                                                  │
  │    │   ├── loginTokenService.issueToken(accountId, NETAPP)           │
  │    │   │       ┌─────────────────────────────────────────────────┐   │
  │    │   │       │ SaTokenLoginTokenService.issueToken()           │   │
  │    │   │       │  1. resolveStpLogic(NETAPP) → INTERNET StpLogic│   │
  │    │   │       │  2. stpLogic.login(accountId.value())          │   │
  │    │   │       │  3. stpLogic.getTokenValue() → token字符串     │   │
  │    │   │       │  返回: tokenValue                               │   │
  │    │   │       └─────────────────────────────────────────────────┘   │
  │    │   │                                                             │
  │    │   │ sessionId = new SessionId(token)  // Session.id == token值 │
  │    │   │                                                             │
  │    │   │ Session.create(                                             │
  │    │   │   sessionId,                                                │
  │    │   │   accountId,                     // creator                 │
  │    │   │   accountId,                     // primaryAccountId        │
  │    │   │   NETAPP,                        // channel                 │
  │    │   │   EffectiveIdentity.direct(accountId), // 直属身份          │
  │    │   │   Duration.ofHours(8)            // 超时                    │
  │    │   │ )                                                           │
  │    │   │                                                             │
  │    │   │ sessionRepository.save(session)                             │
  │    │   │       ┌─────────────────────────────────────────────────┐   │
  │    │   │       │ SessionRepositoryImpl.save() (Redis)            │   │
  │    │   │       │  Key1: auth:session:id:{token} → Session JSON   │   │
  │    │   │       │  Key2: auth:session:account:{accountId}:        │   │
  │    │   │       │        channel:NETAPP → token (索引)            │   │
  │    │   │       │  TTL = expiresAt - now                          │   │
  │    │   │       └─────────────────────────────────────────────────┘   │
  │    │   │                                                             │
  │    │   └── session.domainEvents().forEach(eventBus::publish)         │
  │    └──────────────────────────────────────────────────────────────────┘
  │
  │ 7. loginTokenService.getTokenName(NETAPP)  → "satoken-internet"
  │ 8. loginTokenService.verifyToken(tokenValue) → Optional<UserNo>
  │
  ▼
返回 LoginResponse:
  {
    token:     "<sa-token-tokenValue>",
    tokenName: "satoken-internet",
    accountId: "<userNo>",
    channel:   "NETAPP"
  }
```

### 3.2 数据流转表

| 步骤 | 输入数据 | 转换操作 | 输出数据 |
|---|---|---|---|
| 1 | `LoginRequest` (credentialType, ownerType, ownerId, channel, proof, phoneNumber) | `resolveOwner()` 类型映射 | `CredentialOwner` (UserCredentialOwner / CustomerCredentialOwner / PlanCredentialOwner) |
| 2 | `CredentialOwner` + channel + proof | `IdentityResolutionService.resolve()` | `Optional<UserNo>` (accountId) |
| 2a | owner | `CredentialRepository.findByOwner()` | `List<Credential>` |
| 2b | Credential + channel | `.filter(applicableChannels.contains)` | 过滤后的 `List<Credential>` |
| 2c | Credential + proof | `CredentialAuthenticator.authenticate()` | `boolean` (认证结果) |
| 2d | UserCredentialOwner | 直接提取 `userNo` | `accountId = userNo` |
| 2d' | CustomerCredentialOwner/PlanCredentialOwner + Mobile | `locateByPhone()` → `belongsToOwner()` | `accountId` (经 AgentIdentityAssignment 校验) |
| 2e | accountId | `AccountRepository.load()` → `isActive()` | 验证账号可用 |
| 3 | accountId + channel | `LoginTokenService.issueToken()` | `String tokenValue` |
| 4 | tokenValue | `new SessionId(token)` | Session 主键 (与 token 值相同) |
| 5 | sessionId + accountId + channel | `Session.create()` | `Session` 聚合根 |
| 6 | Session | `SessionRepository.save()` → Redis | 持久化 (2个 Key) |
| 7 | tokenValue + channel | `getTokenName()` + `verifyToken()` | `LoginResponse` |

### 3.3 后续请求的会话上下文注入

登录成功后，客户端在后续请求中携带 `satoken-internet: <token>` Header：

```
Client
  │ GET /internet/some-api
  │ Header: satoken-internet: <token>
  │
  ▼
internet-gateway
  │
  │ [SaReactorFilter @-200]
  │   ├── isPublicPath → false
  │   ├── ChannelAwareSaRouter.matchChannel("/internet/...") → INTERNET
  │   └── INTERNET StpLogic.checkLogin() → 校验 token 有效性 ✓
  │
  │ [SessionContextInjector @-150]
  │   ├── 剥离客户端伪造的敏感 Header (X-Account-Id, X-Account-Sig, etc.)
  │   ├── stpLogic.getLoginIdAsString() → loginId (== accountId)
  │   ├── buildSessionContext():
  │   │     ├── context["userNo"] = loginId
  │   │     ├── context["channelType"] = "internet"
  │   │     └── Token-Session.get("currentPlanId") → context["planNo"] (如有)
  │   ├── encodedContext = Base64(JSON(context))
  │   ├── SessionSignatureUtils.signAccountId(loginId, key, ttl) → SignedPayload
  │   └── SessionSignatureUtils.signSessionContext(encodedContext, expireAt, key) → signature
  │
  ▼
注入下游 Header:
  X-Account-Id:      "loginId:expireAtEpochSecond"
  X-Account-Sig:     HMAC-SHA256 hex
  X-Session-Context:  Base64({"userNo":"...","channelType":"internet","planNo":"..."})
  X-Session-Sig:      HMAC-SHA256 hex
  X-Session-Expire:   "1234567890"
  │
  ▼
Business Service
  │ SessionContextResolver.optional(request)
  │   ├── 验证 X-Session-Sig 签名
  │   ├── Base64 解码 → JSON → SessionContext record
  │   └── 返回 SessionContext(userNo, channelType, planNo, ...)
  │
  │ DefaultAccountIdResolver.resolve()
  │   ├── 读取 X-Account-Id + X-Account-Sig
  │   └── SessionSignatureUtils.verifyAccountId() → loginId
  │
  │ @RequirePermission → RequirePermissionAspect
  │   → AccountIdResolver.resolve() → loginId
  │   → PlanIdResolver.resolve() → planNo (from SessionContext)
  │   → PermissionExecutor.execute(loginId, planNo, permission)
  │     → PermissionQueryService → 权限校验
```

---

## 四、总部渠道 (HQ) 登录流程

### 4.1 完整调用链路

```
Client
  │ POST /hq/auth/login
  │ Body: { credentialType, ownerType, ownerId, channel:"TELLER", proof, phoneNumber }
  │
  ▼
intranet-gateway (port 18082)
  │ [SaReactorFilter @-200]
  │   ├── isPublicPath("/hq/auth/login") → true → 放行
  │
  ▼
auth-service → SessionController.login()
  │
  │ 1~4. 同网上渠道 (转换 LoginRequest → OpenSessionWithCredentialCommand)
  │
  ▼
SessionApplicationService.openSessionWithCredential()
  │
  │ 5. identityResolutionService.resolve() — 同网上渠道
  │
  │ 6. buildSession(accountId, TELLER)
  │    ├── loginTokenService.issueToken(accountId, TELLER)
  │    │   └── HQ StpLogic.login() → tokenValue
  │    │       tokenName = "satoken-hq"
  │    │
  │    ├── Session.create(sessionId, accountId, accountId, TELLER,
  │    │                   EffectiveIdentity.direct(accountId), 8h)
  │    │
  │    └── sessionRepository.save() → Redis
  │
  ▼
返回 LoginResponse:
  {
    token:     "<sa-token-tokenValue>",
    tokenName: "satoken-hq",
    accountId: "<userNo>",
    channel:   "TELLER"
  }
```

### 4.2 HQ 渠道特有差异

| 差异点 | 网上渠道 | 总部渠道 |
|---|---|---|
| 网关 | internet-gateway:18081 | intranet-gateway:18082 |
| StpLogic loginType | `internet` | `hq` |
| Token Header | `satoken-internet` | `satoken-hq` |
| 方案选择策略 | `OnlinePlanSelectionStrategy` | `HqPlanSelectionStrategy` |
| 可选方案范围 | `EnumeratedPlans` (枚举列表 + 渠道过滤) | `AllPlans` (全量，无过滤) |

### 4.3 方案选择流程 (HQ 特有)

```
Client
  │ POST /hq/auth/selectable-plans
  │ Header: satoken-hq: <token>
  │
  ▼
SessionController.listSelectablePlans()
  │ currentSessionId() → 读取 satoken-hq Header → SessionId
  │
  ▼
SessionApplicationService.listSelectablePlans(sessionId)
  │ load Session → session.channel() = TELLER
  │ strategiesByChannel.get(TELLER) → HqPlanSelectionStrategy
  │
  ▼
HqPlanSelectionStrategy.listSelectablePlans(identity)
  │ return new AllPlans()  // 不做任何枚举，权限由 AuthorizationEngine 控制
  │
  ▼
filterByCustomerChannel(AllPlans, TELLER)
  │ AllPlans 不做渠道过滤 → 直接返回 AllPlans
  │
  ▼
SessionController → switch(AllPlans) → List.of("ALL")
  │
  ▼
返回 SelectablePlansResponse: { plans: ["ALL"] }
```

---

## 五、网点渠道 (BRANCH) 登录流程

网点渠道登录分两个阶段：**柜员登录** + **二次授权**。

### 5.1 阶段一：柜员登录

```
Client
  │ POST /branch/auth/login
  │ Body: { credentialType, ownerType, ownerId, channel:"BANK_BRANCH", proof, phoneNumber }
  │
  ▼
intranet-gateway (port 18082)
  │ [SaReactorFilter @-200]
  │   ├── isPublicPath("/branch/auth/login") → true → 放行
  │
  ▼
auth-service → SessionController.login()
  │
  │ 转换流程同网上渠道，区别在于:
  │   - channel = BANK_BRANCH
  │   - 使用 BRANCH StpLogic
  │
  ▼
SessionApplicationService.openSessionWithCredential()
  │
  │ buildSession(accountId, BANK_BRANCH)
  │   ├── loginTokenService.issueToken(accountId, BANK_BRANCH)
  │   │   └── BRANCH StpLogic.login() → tokenValue
  │   │       tokenName = "satoken-branch"
  │   │
  │   ├── Session.create(sessionId, accountId, accountId, BANK_BRANCH,
  │   │                   EffectiveIdentity.direct(accountId), 8h)
  │   │   // 此时 effectiveIdentity = (identityAccountId=accountId, actingAccountId=accountId, viaSecondaryAuth=false)
  │   │
  │   └── sessionRepository.save() → Redis
  │
  ▼
返回 LoginResponse:
  {
    token:     "<sa-token-tokenValue>",
    tokenName: "satoken-branch",
    accountId: "<userNo>",
    channel:   "BANK_BRANCH"
  }
```

**此时柜员已登录但无法执行业务操作** — `BranchPlanSelectionStrategy` 要求 `viaSecondaryAuth == true` 才能选择方案。

### 5.2 阶段二：二次授权

#### 5.2.1 发起二次授权

```
Teller Client
  │ POST /branch/auth/secondary-auth/initiate
  │ Header: satoken-branch: <teller-token>
  │ Body: { tellerAccountId, credentialOwner, approverAccountId, approverMobile, planId }
  │
  ▼
intranet-gateway
  │ [SaReactorFilter @-200]
  │   ├── isPublicPath → false → checkLogin → BRANCH StpLogic 校验 ✓
  │
  ▼
SecondaryAuthAppService.initiate(InitiateSecondaryAuthCommand)
  │
  │ 1. sessionRepository.findActiveByTeller(tellerAccountId)
  │    └── 检查是否已有进行中的二次授权 → 无则继续
  │
  │ 2. if (planId != null):
  │    channelAccessPolicy.requireEnabledForPlan(planId, BANK_BRANCH)
  │    ┌──────────────────────────────────────────────────────────────┐
  │    │ ChannelAccessPolicy.requireEnabledForPlan()                  │
  │    │   ├── productGateway.findPlan(planId) → plan.customerNo()   │
  │    │   └── entitlementRepository.findByCustomer(customerNo)       │
  │    │       .map(entitlement -> entitlement.isEnabled(BANK_BRANCH))│
  │    │       .orElse(false)  // 默认: 无记录则所有渠道禁用          │
  │    └──────────────────────────────────────────────────────────────┘
  │
  │ 3. generateCode() → rawCode (6位数字)
  │    codeHasher.hash(rawCode) → hashedCode
  │    VerificationCode.of(hashedCode, now, pendingTimeout, maxAttempts)
  │
  │ 4. idService.nextId(SecondaryAuthSessionId.class) → sessionId
  │
  │ 5. SecondaryAuthSession.initiate(InitiateContext):
  │    ┌──────────────────────────────────────────────────────────────┐
  │    │ SecondaryAuthSession 创建                                    │
  │    │  字段:                                                       │
  │    │   - tellerAccountId: 柜员ID                                  │
  │    │   - credentialOwner: 凭证所有者                               │
  │    │   - approverAccountId: 授权人ID                               │
  │    │   - approverMobile: 授权人手机号                               │
  │    │   - planId: 目标方案ID (可空)                                  │
  │    │   - verificationCode: 验证码 (哈希值 + 过期时间 + 剩余尝试次数) │
  │    │   - status: PENDING                                          │
  │    │   - effectiveIdentity: null (尚未授权)                        │
  │    └──────────────────────────────────────────────────────────────┘
  │
  │ 6. sessionRepository.save(secondaryAuthSession)
  │ 7. 发布领域事件
  │
  ▼
返回 SecondaryAuthSessionId
  → 同时发送验证码短信给授权人
```

#### 5.2.2 确认二次授权

```
Teller Client
  │ POST /branch/auth/secondary-auth/confirm
  │ Header: satoken-branch: <teller-token>
  │ Body: { sessionId, rawCode, snapshot, operator }
  │
  ▼
SecondaryAuthAppService.confirm(ConfirmSecondaryAuthCommand)
  │
  │ 1. sessionRepository.loadOrThrow(sessionId) → SecondaryAuthSession
  │
  │ 2. 构造 EffectiveIdentity:
  │    new EffectiveIdentity(
  │      approverAccountId,    // identityAccountId = 授权人 (权限判定身份)
  │      tellerAccountId,      // actingAccountId = 柜员 (实际操作者)
  │      true                  // viaSecondaryAuth = true
  │    )
  │
  │ 3. secondaryAuthSession.authorize(rawCode, snapshot, identity, codeHasher, operator)
  │    ┌──────────────────────────────────────────────────────────────┐
  │    │ SecondaryAuthSession.authorize()                             │
  │    │   ├── 校验 status == PENDING                                 │
  │    │   ├── 校验 verificationCode 非空且未耗尽                     │
  │    │   ├── codeHasher.verify(rawCode, hashedCode) → 不匹配则扣减  │
  │    │   │   尝试次数; 耗尽则状态→REJECTED + 抛异常                  │
  │    │   ├── 清除 verificationCode (一次性使用)                      │
  │    │   ├── 冻结 permissionSnapshot                                │
  │    │   ├── 设置 effectiveIdentity = 传入的 identity               │
  │    │   ├── status → AUTHORIZED                                    │
  │    │   ├── authorizedAt = now                                     │
  │    │   └── 注册 SecondaryAuthCompleted 领域事件                    │
  │    └──────────────────────────────────────────────────────────────┘
  │
  │ 4. sessionRepository.save(secondaryAuthSession)
  │ 5. 发布领域事件 (包含 SecondaryAuthCompleted)
  │
  ▼
SecondaryAuthCompleted 事件传播:
  │
  ▼
SessionApplicationService.onSecondaryAuthCompleted()  [@TransactionalEventListener(AFTER_COMMIT)]
  │
  │ 1. sessionRepository.findActiveByPrimaryAccountIdAndChannel(
  │      tellerAccountId, BANK_BRANCH) → teller 的活跃 Session
  │
  │ 2. session.applySecondaryAuth(sessionId, effectiveIdentity, tellerAccountId)
  │    ┌──────────────────────────────────────────────────────────────┐
  │    │ Session.applySecondaryAuth()                                 │
  │    │   ├── 校验 channel == BANK_BRANCH                            │
  │    │   ├── 校验 secondaryAuthSessionId == null (无已绑定授权)      │
  │    │   ├── secondaryAuthSessionId = sessionId                     │
  │    │   ├── effectiveIdentity = 授权人的 EffectiveIdentity          │
  │    │   │   (identityAccountId=approver, actingAccountId=teller,   │
  │    │   │    viaSecondaryAuth=true)                                │
  │    │   └── 注册 SessionIdentityElevated 领域事件                   │
  │    └──────────────────────────────────────────────────────────────┘
  │
  │ 3. sessionRepository.save(session)
  │ 4. 发布领域事件
  │
  ▼
柜员身份已提升，可执行业务操作
```

#### 5.2.3 二次授权后的方案选择

```
Teller Client
  │ POST /branch/auth/selectable-plans
  │ Header: satoken-branch: <teller-token>
  │
  ▼
SessionController.listSelectablePlans()
  │ currentSessionId() → 读取 satoken-branch Header → SessionId
  │
  ▼
SessionApplicationService.listSelectablePlans(sessionId)
  │ load Session → session.channel() = BANK_BRANCH
  │ session.effectiveIdentity() = EffectiveIdentity(approver, teller, viaSecondaryAuth=true)
  │
  │ strategiesByChannel.get(BANK_BRANCH) → BranchPlanSelectionStrategy
  │
  ▼
BranchPlanSelectionStrategy.listSelectablePlans(identity)
  │ identity.viaSecondaryAuth() == true ✓ → 继续
  │ delegate.listSelectablePlans(identity)
  │   └── OnlinePlanSelectionStrategy.listSelectablePlans(identity)
  │       └── PlanReachabilityService.listSelectablePlans(identity.identityAccountId())
  │           ┌──────────────────────────────────────────────────────────────┐
  │           │ PlanReachabilityService.listSelectablePlans(approverAccountId)│
  │           │   ├── assignmentRepository.findActiveByAccount(approverAccountId)│
  │           │   │   → List<AgentIdentityAssignment>                       │
  │           │   │                                                         │
  │           │   │ 对每个 assignment:                                      │
  │           │   │   PLAN scope     → plans.add(scopeValue)               │
  │           │   │   CUSTOMER scope → orgDirectory.plansOfCustomer(...)   │
  │           │   │   PRODUCT scope  → orgDirectory.plansOfProduct(...)    │
  │           │   │                                                         │
  │           │   └── 去重 → List<PlanNo>                                  │
  │           └──────────────────────────────────────────────────────────────┘
  │       → EnumeratedPlans
  │
  ▼
filterByCustomerChannel(EnumeratedPlans, BANK_BRANCH)
  │ channelAccessPolicy.filterPlansByChannel(plans, BANK_BRANCH)
  │   → 过滤掉未开通 BANK_BRANCH 渠道的方案
  │
  ▼
SessionController → EnumeratedPlans → List<String>
  │
  ▼
返回 SelectablePlansResponse: { plans: ["plan-001", "plan-002", ...] }
```

#### 5.2.4 撤销二次授权

```
Approver/Manager
  │ POST /branch/auth/secondary-auth/revoke
  │ Body: { sessionId, operator, reason }
  │
  ▼
SecondaryAuthAppService.revoke()
  │ secondaryAuthSession.revoke(operator, reason)
  │   → status → REVOKED
  │   → 注册 SecondaryAuthRevoked 领域事件
  │
  ▼
SessionApplicationService.onSecondaryAuthRevoked()  [@TransactionalEventListener(AFTER_COMMIT)]
  │ sessionRepository.findActiveByPrimaryAccountIdAndChannel(tellerAccountId, BANK_BRANCH)
  │   .ifPresent(session -> {
  │     session.clearSecondaryAuth(operator)
  │       ├── secondaryAuthSessionId = null
  │       ├── effectiveIdentity = EffectiveIdentity.direct(primaryAccountId)
  │       │   // 回退为柜员自身身份: (identityAccountId=teller, actingAccountId=teller, viaSecondaryAuth=false)
  │       └── 不会再发布额外领域事件
  │     sessionRepository.save(session)
  │   })
```

---

## 六、Session 聚合根状态变迁

### 6.1 Session 状态

```
ACTIVE (创建) ──selectPlan()──→ ACTIVE (selectedPlanId 已设置)
ACTIVE (创建) ──applySecondaryAuth()──→ ACTIVE (effectiveIdentity 已提升)
ACTIVE (effectiveIdentity 已提升) ──clearSecondaryAuth()──→ ACTIVE (回退为直属身份)
ACTIVE ──close()──→ CLOSED
ACTIVE ──(超时)──→ EXPIRED
```

### 6.2 SecondaryAuthSession 状态

```
PENDING (initiate) ──authorize()──→ AUTHORIZED ──revoke()──→ REVOKED
PENDING ──(验证码耗尽)──→ REJECTED
PENDING ──(超时)──→ EXPIRED
AUTHORIZED ──close()──→ CLOSED
AUTHORIZED ──(会话超时)──→ EXPIRED
```

---

## 七、EffectiveIdentity 数据流

| 场景 | identityAccountId | actingAccountId | viaSecondaryAuth |
|---|---|---|---|
| 网上/总部直接登录 | accountId | accountId | `false` |
| 网点柜员登录 | accountId | accountId | `false` |
| 二次授权后 | **approverAccountId** | **tellerAccountId** | `true` |
| 撤销二次授权后 | accountId (柜员自身) | accountId (柜员自身) | `false` |

- `identityAccountId`: 权限判定使用的身份 (who the system treats as the operator)
- `actingAccountId`: 实际操作者身份 (who actually performed the action, 用于审计)
- `viaSecondaryAuth`: 是否经由二次授权获得身份 (Branch 渠道方案选择前置条件)

---

## 八、Redis 数据模型

### 8.1 Session 存储

| Redis Key | Value | TTL |
|---|---|---|
| `auth:session:id:{token}` | Session JSON | `expiresAt - now` |
| `auth:session:account:{accountId}:channel:{channel}` | token 字符串 (索引) | `expiresAt - now` |

**Session JSON 包含的字段**:
- `id` (== sa-token tokenValue)
- `primaryAccountId`
- `channel` (NETAPP / TELLER / BANK_BRANCH)
- `effectiveIdentity` (identityAccountId, actingAccountId, viaSecondaryAuth)
- `selectedPlanId`
- `secondaryAuthSessionId` (仅 BANK_BRANCH 渠道)
- `status` (ACTIVE / CLOSED / EXPIRED)
- `expiresAt`

### 8.2 sa-token 存储

sa-token 框架自行管理 Redis 中的 token 存储:
- `satoken:login:session:{loginId}` — 用户 session 数据
- `satoken:login:token:{tokenValue}` — token 到 loginId 的映射

---

## 九、签名机制

### 9.1 网关签名 (HMAC-SHA256)

**签名密钥**: `permission.session.signature-key` (生产环境通过环境变量 `PERMISSION_SESSION_SIGNATURE_KEY` 注入)

#### X-Account-Id 签名

```
payload = "loginId:expireAtEpochSecond"
signature = HMAC-SHA256(payload, secretKey) → hex string
```

#### X-Session-Context 签名

```
payload = "base64EncodedContext:expireAtEpochSecond"
signature = HMAC-SHA256(payload, secretKey) → hex string
```

### 9.2 下游验证

```
SessionContextResolver / AccountIdResolver:
  1. 读取 X-Account-Id, X-Account-Sig
  2. 重构 payload → HMAC-SHA256 计算 → 常量时间比对签名
  3. 检查 expireAtEpochSecond 未过期
  4. 提取 loginId
```

**常量时间比较**: 使用 `constantTimeEquals()` 防止时序攻击。

---

## 十、完整服务间调用关系图

```
┌─────────────┐
│   Client     │
└──────┬───────┘
       │ HTTP (带 token header)
       ▼
┌──────────────────────┐
│  internet-gateway     │  或 intranet-gateway
│  :18081 / :18082      │
│                       │
│  SaReactorFilter      │──→ sa-token Redis (校验 token)
│  SessionContextInjector│──→ sa-token Token-Session (读取 planId)
│                       │     + HMAC-SHA256 签名
└──────┬───────────────┘
       │ HTTP (带 X-Account-Id, X-Session-Context 签名头)
       ▼
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  auth-service │     │  internet-bff│     │ intranet-bff │
│              │     │              │     │              │
│ SessionCtrl  │     │ 路由聚合      │     │ 路由聚合      │
│ SessionAppSvc│     └──────┬───────┘     └──────┬───────┘
│ IdentityRes  │            │                     │
│ LoginTokenSvc│            │ HTTP (带签名头)      │
│ SecAuthAppSvc│            ▼                     ▼
│ PlanSelect   │     ┌──────────────────────────────┐
└──────┬───────┘     │   downstream business svc    │
       │              │  (annuity-service, etc.)     │
       │ Redis        │                              │
       ├─────────────→│  SessionContextResolver       │
       │              │  AccountIdResolver            │
       │              │  @RequirePermission → PermExec│
       │              └──────────────────────────────┘
       │
       ▼
┌──────────────┐
│  Redis       │
│              │
│ Session数据   │
│ sa-token数据  │
└──────────────┘
```

---

## 十一、网关白名单配置

| 网关 | 白名单路径 |
|---|---|
| internet-gateway | `/internet/auth/login` |
| intranet-gateway | `/hq/auth/login` |
| intranet-gateway | `/branch/auth/login` |
| intranet-gateway | `/branch/auth/secondary-auth/initiate` |
| intranet-gateway | `/branch/auth/secondary-auth/confirm` |
| intranet-gateway | `/branch/auth/secondary-auth/status/**` |

> **注意**: 二次授权的 initiate/confirm/status 路径在当前白名单中，意味着这些路径**不需要登录即可访问**。如果这些端点需要柜员先登录才能调用，应将它们从白名单中移除，确保 `satoken-branch` 校验生效。

---

## 十二、已修复的待完善项

以下三个问题已在本次迭代中修复：

### 12.1 SecondaryAuth REST 端点（已修复）

新增 `SecondaryAuthApi` 接口和 `SecondaryAuthController`，暴露以下端点：

| 端点 | 方法 | 说明 |
|---|---|---|
| `POST /auth/secondary-auth/initiate` | 发起二次授权 | 返回 `InitiateSecondaryAuthResponse(sessionId)` |
| `POST /auth/secondary-auth/confirm` | 确认二次授权 | 传入验证码 |
| `POST /auth/secondary-auth/resend-code` | 重发验证码 | |
| `POST /auth/secondary-auth/revoke` | 撤销二次授权 | |
| `POST /auth/secondary-auth/close` | 关闭二次授权会话 | |

**新增文件**：
- `auth-api/.../SecondaryAuthApi.java` — `@HttpExchange("/auth/secondary-auth")` 接口定义
- `auth-api/.../command/InitiateSecondaryAuthRequest.java` — 发起请求 DTO
- `auth-api/.../command/ConfirmSecondaryAuthRequest.java` — 确认请求 DTO
- `auth-api/.../command/ResendCodeRequest.java` — 重发验证码请求 DTO
- `auth-api/.../command/RevokeSecondaryAuthRequest.java` — 撤销请求 DTO
- `auth-api/.../command/CloseSecondaryAuthRequest.java` — 关闭请求 DTO
- `auth-api/.../dto/InitiateSecondaryAuthResponse.java` — 发起响应 DTO
- `auth-adapter/.../SecondaryAuthController.java` — Controller 实现

### 12.2 PlanSelectionStrategy Bean 注册（已修复）

新增 `ChannelInfrastructureConfig` 配置类，注册 `Map<AnnuityChannel, PlanSelectionStrategy>` Bean：

```
NETAPP       → OnlinePlanSelectionStrategy (依赖 PlanReachabilityService)
TELLER       → HqPlanSelectionStrategy (无依赖)
BANK_BRANCH  → BranchPlanSelectionStrategy (委托 OnlinePlanSelectionStrategy)
```

**新增文件**：`auth-infrastructure/.../ChannelInfrastructureConfig.java`

### 12.3 currentPlanId 写入 sa-token Token-Session（已修复）

新增 `SessionPlanSelectedEventListener` 监听 `SessionPlanSelected` 事件，
遍历 `channelStpLogicMap` 找到持有该 token 的 StpLogic，将 `planId` 写入
`SaSession` 的 `currentPlanId` 键，使网关 `SessionContextInjector` 能在后续请求中
读取并注入到 `X-Session-Context` Header。

**新增文件**：`auth-infrastructure/.../SessionPlanSelectedEventListener.java`

**注意**：该常量键 `SESSION_KEY_CURRENT_PLAN_ID = "currentPlanId"` 与网关
`GatewayStpInterfaceImpl.SESSION_KEY_CURRENT_PLAN_ID` 保持一致，但未直接引用
（因 auth-infrastructure 不依赖 gateway-shared 模块）。

---

## 十三、接口验证 curl 命令

以下 curl 命令用于验证各渠道登录接口的完整链路。需先启动 Redis、Nacos、auth-service 和对应网关。

### 前置条件

| 依赖 | 说明 |
|---|---|
| Redis | sa-token 存储 + Session 存储 |
| Nacos | 服务注册发现 |
| auth-service | 认证服务，需先注册到 Nacos |
| internet-gateway | 端口 18081，仅 INTERNET 渠道 |
| intranet-gateway | 端口 18082，HQ + BRANCH 渠道 |

### 变量说明

以下命令中使用以下占位符，实际测试时需替换为真实数据：

| 占位符 | 含义 | 示例值 |
|---|---|---|
| `{PASSWORD}` | 用户密码明文 | `test123` |
| `{USER_NO}` | 用户编号 (ownerType=USER 时的 ownerId) | `U00001` |
| `{CUSTOMER_NO}` | 客户编号 (ownerType=CUSTOMER 时的 ownerId) | `C00001` |
| `{PLAN_NO}` | 计划编号 (ownerType=PLAN 时的 ownerId) | `P00001` |
| `{PHONE}` | 经办手机号 (客户/计划级凭证登录时必填) | `13800138000` |
| `{APPROVER_ID}` | 授权人账号 ID | `U00002` |
| `{APPROVER_MOBILE}` | 授权人手机号 | `13900139000` |
| `{PLAN_ID}` | 目标计划 ID | `P00001` |
| `{TOKEN}` | 登录返回的 token 值 | (登录响应中获取) |
| `{SESSION_ID}` | 二次授权会话 ID | (initiate 响应中获取) |

---

### 13.1 网上渠道 (INTERNET)

#### 13.1.1 账号级密码登录

```bash
curl -s -X POST http://localhost:18081/internet/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "credentialType": "PASSWORD",
    "ownerType": "USER",
    "ownerId": "{USER_NO}",
    "channel": "NETAPP",
    "proof": "{PASSWORD}"
  }' | jq .
```

**预期响应**：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "token": "<sa-token-tokenValue>",
    "tokenName": "satoken-internet",
    "accountId": "<userNo>",
    "channel": "NETAPP"
  }
}
```

#### 13.1.2 客户级密码登录（需经办手机号）

```bash
curl -s -X POST http://localhost:18081/internet/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "credentialType": "PASSWORD",
    "ownerType": "CUSTOMER",
    "ownerId": "{CUSTOMER_NO}",
    "channel": "NETAPP",
    "proof": "{PASSWORD}",
    "phoneNumber": "{PHONE}"
  }' | jq .
```

#### 13.1.3 账号级 UKey 登录

```bash
curl -s -X POST http://localhost:18081/internet/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "credentialType": "U_KEY",
    "ownerType": "USER",
    "ownerId": "{USER_NO}",
    "channel": "NETAPP",
    "proof": "{UKEY_SIGNATURE}"
  }' | jq .
```

#### 13.1.4 登录后 — 查询可选方案

```bash
TOKEN="<从登录响应中获取的token>"

curl -s -X POST http://localhost:18081/internet/auth/selectable-plans \
  -H "Content-Type: application/json" \
  -H "satoken-internet: ${TOKEN}" | jq .
```

**预期响应**：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "plans": ["plan-001", "plan-002"]
  }
}
```

> HQ 渠道返回 `["ALL"]`（AllPlans 策略），网上/网点渠道返回枚举的计划编号列表。

#### 13.1.5 登录后 — 选择方案

```bash
TOKEN="<从登录响应中获取的token>"

curl -s -X POST http://localhost:18081/internet/auth/select-plan \
  -H "Content-Type: application/json" \
  -H "satoken-internet: ${TOKEN}" \
  -d '{
    "planId": "{PLAN_ID}"
  }' | jq .
```

**预期响应**：

```json
{
  "code": "0",
  "message": "success",
  "data": null
}
```

#### 13.1.6 登录后 — 登出

```bash
TOKEN="<从登录响应中获取的token>"

curl -s -X POST http://localhost:18081/internet/auth/logout \
  -H "Content-Type: application/json" \
  -H "satoken-internet: ${TOKEN}" | jq .
```

---

### 13.2 总部渠道 (HQ)

#### 13.2.1 账号级密码登录

```bash
curl -s -X POST http://localhost:18082/hq/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "credentialType": "PASSWORD",
    "ownerType": "USER",
    "ownerId": "{USER_NO}",
    "channel": "TELLER",
    "proof": "{PASSWORD}"
  }' | jq .
```

**预期响应**：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "token": "<sa-token-tokenValue>",
    "tokenName": "satoken-hq",
    "accountId": "<userNo>",
    "channel": "TELLER"
  }
}
```

#### 13.2.2 登录后 — 查询可选方案（HQ 返回 ALL）

```bash
TOKEN="<从登录响应中获取的token>"

curl -s -X POST http://localhost:18082/hq/auth/selectable-plans \
  -H "Content-Type: application/json" \
  -H "satoken-hq: ${TOKEN}" | jq .
```

**预期响应**：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "plans": ["ALL"]
  }
}
```

#### 13.2.3 登录后 — 选择方案

```bash
TOKEN="<从登录响应中获取的token>"

curl -s -X POST http://localhost:18082/hq/auth/select-plan \
  -H "Content-Type: application/json" \
  -H "satoken-hq: ${TOKEN}" \
  -d '{
    "planId": "{PLAN_ID}"
  }' | jq .
```

#### 13.2.4 登录后 — 登出

```bash
TOKEN="<从登录响应中获取的token>"

curl -s -X POST http://localhost:18082/hq/auth/logout \
  -H "Content-Type: application/json" \
  -H "satoken-hq: ${TOKEN}" | jq .
```

---

### 13.3 网点渠道 (BRANCH) — 完整二次授权流程

#### 13.3.1 柜员密码登录

```bash
curl -s -X POST http://localhost:18082/branch/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "credentialType": "PASSWORD",
    "ownerType": "USER",
    "ownerId": "{USER_NO}",
    "channel": "BANK_BRANCH",
    "proof": "{PASSWORD}"
  }' | jq .
```

**预期响应**：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "token": "<sa-token-tokenValue>",
    "tokenName": "satoken-branch",
    "accountId": "<userNo>",
    "channel": "BANK_BRANCH"
  }
}
```

> 此时柜员已登录但 `viaSecondaryAuth=false`，无法选择方案（BranchPlanSelectionStrategy 拒绝）。

#### 13.3.2 柜员登录后 — 查询可选方案（应失败）

```bash
TOKEN="<从柜员登录响应中获取的token>"

curl -s -X POST http://localhost:18082/branch/auth/selectable-plans \
  -H "Content-Type: application/json" \
  -H "satoken-branch: ${TOKEN}" | jq .
```

**预期响应**：业务异常 — `BranchPlanSelectionStrategy` 要求 `viaSecondaryAuth == true`。

#### 13.3.3 发起二次授权

> **注意**：当前 `intranet-gateway` 白名单包含 `/branch/auth/secondary-auth/initiate`，
> 该路径不经过 `SaReactorFilter` 认证。如需校验柜员已登录，应从白名单移除该路径。

```bash
TOKEN="<从柜员登录响应中获取的token>"

curl -s -X POST http://localhost:18082/branch/auth/secondary-auth/initiate \
  -H "Content-Type: application/json" \
  -H "satoken-branch: ${TOKEN}" \
  -d '{
    "tellerAccountId": "{USER_NO}",
    "ownerType": "PLAN",
    "ownerId": "{PLAN_NO}",
    "approverAccountId": "{APPROVER_ID}",
    "approverMobile": "{APPROVER_MOBILE}",
    "planId": "{PLAN_ID}"
  }' | jq .
```

**预期响应**：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "sessionId": "<secondary-auth-session-id>"
  }
}
```

#### 13.3.4 确认二次授权（输入验证码）

```bash
TOKEN="<从柜员登录响应中获取的token>"
SESSION_ID="<从initiate响应中获取的sessionId>"

curl -s -X POST http://localhost:18082/branch/auth/secondary-auth/confirm \
  -H "Content-Type: application/json" \
  -H "satoken-branch: ${TOKEN}" \
  -d '{
    "sessionId": "${SESSION_ID}",
    "rawCode": "<验证码>"
  }' | jq .
```

**预期响应**：

```json
{
  "code": "0",
  "message": "success",
  "data": null
}
```

> 验证码由 `SecondaryAuthAppService.initiate()` 生成并通过短信发送给授权人。
> 测试环境可通过日志或 Redis 查看。

#### 13.3.5 二次授权后 — 查询可选方案

```bash
TOKEN="<从柜员登录响应中获取的token>"

curl -s -X POST http://localhost:18082/branch/auth/selectable-plans \
  -H "Content-Type: application/json" \
  -H "satoken-branch: ${TOKEN}" | jq .
```

**预期响应**：返回枚举的计划编号列表（经 `BranchPlanSelectionStrategy` → 委托 `OnlinePlanSelectionStrategy`）。

#### 13.3.6 二次授权后 — 选择方案

```bash
TOKEN="<从柜员登录响应中获取的token>"

curl -s -X POST http://localhost:18082/branch/auth/select-plan \
  -H "Content-Type: application/json" \
  -H "satoken-branch: ${TOKEN}" \
  -d '{
    "planId": "{PLAN_ID}"
  }' | jq .
```

#### 13.3.7 重发验证码

```bash
TOKEN="<从柜员登录响应中获取的token>"
SESSION_ID="<从initiate响应中获取的sessionId>"

curl -s -X POST http://localhost:18082/branch/auth/secondary-auth/resend-code \
  -H "Content-Type: application/json" \
  -H "satoken-branch: ${TOKEN}" \
  -d '{
    "sessionId": "${SESSION_ID}"
  }' | jq .
```

#### 13.3.8 撤销二次授权

```bash
TOKEN="<从柜员登录响应中获取的token>"
SESSION_ID="<从initiate响应中获取的sessionId>"

curl -s -X POST http://localhost:18082/branch/auth/secondary-auth/revoke \
  -H "Content-Type: application/json" \
  -H "satoken-branch: ${TOKEN}" \
  -d '{
    "sessionId": "${SESSION_ID}",
    "reason": "测试撤销"
  }' | jq .
```

> 撤销后柜员的 `effectiveIdentity` 回退为直属身份（`viaSecondaryAuth=false`），
> 再次查询可选方案将被 `BranchPlanSelectionStrategy` 拒绝。

#### 13.3.9 关闭二次授权会话

```bash
TOKEN="<从柜员登录响应中获取的token>"
SESSION_ID="<从initiate响应中获取的sessionId>"

curl -s -X POST http://localhost:18082/branch/auth/secondary-auth/close \
  -H "Content-Type: application/json" \
  -H "satoken-branch: ${TOKEN}" \
  -d '{
    "sessionId": "${SESSION_ID}"
  }' | jq .
```

#### 13.3.10 柜员登出

```bash
TOKEN="<从柜员登录响应中获取的token>"

curl -s -X POST http://localhost:18082/branch/auth/logout \
  -H "Content-Type: application/json" \
  -H "satoken-branch: ${TOKEN}" | jq .
```

---

### 13.4 快速验证脚本

将以下脚本保存为 `test-login.sh`，一键验证网上渠道登录 → 选方案 → 登出流程：

```bash
#!/bin/bash
set -e

GW_INTERNET="http://localhost:18081"
GW_INTRANET="http://localhost:18082"
USER_NO="${1:-U00001}"
PASSWORD="${2:-test123}"

echo "=== 1. 网上渠道登录 ==="
LOGIN_RESP=$(curl -s -X POST "${GW_INTERNET}/internet/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"credentialType\":\"PASSWORD\",\"ownerType\":\"USER\",\"ownerId\":\"${USER_NO}\",\"channel\":\"NETAPP\",\"proof\":\"${PASSWORD}\"}")
echo "${LOGIN_RESP}" | jq .

TOKEN=$(echo "${LOGIN_RESP}" | jq -r '.data.token')
TOKEN_NAME=$(echo "${LOGIN_RESP}" | jq -r '.data.tokenName')
echo "Token: ${TOKEN}"
echo "TokenName: ${TOKEN_NAME}"

echo ""
echo "=== 2. 查询可选方案 ==="
curl -s -X POST "${GW_INTERNET}/internet/auth/selectable-plans" \
  -H "Content-Type: application/json" \
  -H "${TOKEN_NAME}: ${TOKEN}" | jq .

echo ""
echo "=== 3. 登出 ==="
curl -s -X POST "${GW_INTERNET}/internet/auth/logout" \
  -H "Content-Type: application/json" \
  -H "${TOKEN_NAME}: ${TOKEN}" | jq .

echo ""
echo "=== 4. 总部渠道登录 ==="
HQ_RESP=$(curl -s -X POST "${GW_INTRANET}/hq/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"credentialType\":\"PASSWORD\",\"ownerType\":\"USER\",\"ownerId\":\"${USER_NO}\",\"channel\":\"TELLER\",\"proof\":\"${PASSWORD}\"}")
echo "${HQ_RESP}" | jq .

HQ_TOKEN=$(echo "${HQ_RESP}" | jq -r '.data.token')
HQ_TOKEN_NAME=$(echo "${HQ_RESP}" | jq -r '.data.tokenName')

echo ""
echo "=== 5. HQ 查询可选方案（预期返回 ALL）==="
curl -s -X POST "${GW_INTRANET}/hq/auth/selectable-plans" \
  -H "Content-Type: application/json" \
  -H "${HQ_TOKEN_NAME}: ${HQ_TOKEN}" | jq .

echo ""
echo "=== 6. HQ 登出 ==="
curl -s -X POST "${GW_INTRANET}/hq/auth/logout" \
  -H "Content-Type: application/json" \
  -H "${HQ_TOKEN_NAME}: ${HQ_TOKEN}" | jq .

echo ""
echo "=== 7. 网点渠道登录 ==="
BR_RESP=$(curl -s -X POST "${GW_INTRANET}/branch/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"credentialType\":\"PASSWORD\",\"ownerType\":\"USER\",\"ownerId\":\"${USER_NO}\",\"channel\":\"BANK_BRANCH\",\"proof\":\"${PASSWORD}\"}")
echo "${BR_RESP}" | jq .

BR_TOKEN=$(echo "${BR_RESP}" | jq -r '.data.token')
BR_TOKEN_NAME=$(echo "${BR_RESP}" | jq -r '.data.tokenName')

echo ""
echo "=== 8. 网点查询可选方案（预期失败，未二次授权）==="
curl -s -X POST "${GW_INTRANET}/branch/auth/selectable-plans" \
  -H "Content-Type: application/json" \
  -H "${BR_TOKEN_NAME}: ${BR_TOKEN}" | jq .

echo ""
echo "=== 9. 网点登出 ==="
curl -s -X POST "${GW_INTRANET}/branch/auth/logout" \
  -H "Content-Type: application/json" \
  -H "${BR_TOKEN_NAME}: ${BR_TOKEN}" | jq .

echo ""
echo "=== 全部验证完成 ==="
```

---

### 13.5 接口覆盖矩阵

| 编号 | 渠道 | 接口 | Token Header | 需登录 | 白名单 |
|---|---|---|---|---|---|
| 1 | INTERNET | `POST /internet/auth/login` | — | 否 | 是 |
| 2 | INTERNET | `POST /internet/auth/selectable-plans` | `satoken-internet` | 是 | 否 |
| 3 | INTERNET | `POST /internet/auth/select-plan` | `satoken-internet` | 是 | 否 |
| 4 | INTERNET | `POST /internet/auth/logout` | `satoken-internet` | 是 | 否 |
| 5 | HQ | `POST /hq/auth/login` | — | 否 | 是 |
| 6 | HQ | `POST /hq/auth/selectable-plans` | `satoken-hq` | 是 | 否 |
| 7 | HQ | `POST /hq/auth/select-plan` | `satoken-hq` | 是 | 否 |
| 8 | HQ | `POST /hq/auth/logout` | `satoken-hq` | 是 | 否 |
| 9 | BRANCH | `POST /branch/auth/login` | — | 否 | 是 |
| 10 | BRANCH | `POST /branch/auth/secondary-auth/initiate` | `satoken-branch` | 否(白名单) | 是 |
| 11 | BRANCH | `POST /branch/auth/secondary-auth/confirm` | `satoken-branch` | 否(白名单) | 是 |
| 12 | BRANCH | `POST /branch/auth/secondary-auth/resend-code` | `satoken-branch` | 是 | 否 |
| 13 | BRANCH | `POST /branch/auth/secondary-auth/revoke` | `satoken-branch` | 是 | 否 |
| 14 | BRANCH | `POST /branch/auth/secondary-auth/close` | `satoken-branch` | 是 | 否 |
| 15 | BRANCH | `POST /branch/auth/selectable-plans` | `satoken-branch` | 是 | 否 |
| 16 | BRANCH | `POST /branch/auth/select-plan` | `satoken-branch` | 是 | 否 |
| 17 | BRANCH | `POST /branch/auth/logout` | `satoken-branch` | 是 | 否 |
