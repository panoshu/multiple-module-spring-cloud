# 用户与权限体系全景说明

本文档结合当前代码实现（`com.pension.permission.domain` / `application` / `infrastructure` 三层），
说明整个用户与权限体系的结构、用户创建时权限如何处理、各渠道登录认证流程，以及一次业务请求 如何完成功能权限与数据权限的校验。

---

## 一、整个用户和权限体系

体系分四个限界上下文，依赖方向单向：`shared → org/identity → authorization → roletemplate → assignment → channel`。

| 限界上下文                            | 核心概念                                                                                                                                                                    | 职责                                                                                             |
|---------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------|
| **identity**（身份与凭证）            | `Account`、`Credential`(sealed: `PasswordCredential`/`UKeyCredential`)、`CredentialOwner`(sealed: `AccountCredentialOwner`/`CustomerCredentialOwner`/`PlanCredentialOwner`) | 账号本身的状态；凭证是独立聚合根，持有者可以是账号、客户或计划                                   |
| **org**（组织与计划，外部数据投影）   | `CustomerSnapshot`/`ProductSnapshot`/`PlanSnapshot`、`OrgDirectory`                                                                                                         | 客户/产品/计划数据的只读投影，主数据在外部系统                                                   |
| **authorization**（权限与授权，核心） | `Grant`（唯一的授权记录聚合根）、`ScopeRule`、`Permission`、`AuthorizationEngine`                                                                                           | 能力层配置、代办、跨企业委托、总部主体授权，全部是一条`Grant`，靠`subject`/`origin`/`effect`区分 |
| **roletemplate**（角色模板）          | `RoleTemplate`、`RoleTemplateResolver`（按PLAN>CUSTOMER>PRODUCT>GLOBAL优先级解析）                                                                                          | "某个角色应该有哪些权限"的静态配方，不跟具体人绑定                                               |
| **assignment**（身份分配）            | `AgentIdentityAssignment`（账号在某个计划/客户/产品范围内被赋予某角色）、`EffectivePermissionService`（判定的真正入口）                                                     | 角色模板到具体人的落地，以及最终的权限判定编排                                                   |
| **channel**（渠道与会话）             | `Session`、`EffectiveIdentity`、`IdentityResolutionService`、`SecondaryAuthService`、`PlanSelectionStrategy`                                                                | 三个渠道(网上/总部/网点)的登录、会话、身份解析                                                   |

**权限判定的核心公式**（`EffectivePermissionService.checkPermission`）：

```
最终允许 = 能力层校验(计划本身是否开通该业务) AND 主体层校验(这个身份有没有被授权)

能力层 = 持久化Grant里 subject_kind=CAPABILITY 的记录，按ScopeRule匹配，DENY优先合并
主体层 = 持久化Grant(HQ_CONFIG主体授权/代办/跨企业委托)
       ∪ 身份分配实时解析出的角色模板权限(不落库的"虚拟Grant")
       两者合并后同样DENY优先
```

这个"两层AND + 实时角色解析 + DENY优先"的模型，是整个权限体系的地基，后面三节都是它在不同场景下的具体应用。

---

## 二、创建用户时权限是怎么处理的

"创建用户"在这套模型里其实对应两个不同粒度的动作，权限处理方式完全不同：

### 1. 创建 `Account`（开户）

`AccountApplicationService`只负责账号本身（类型、手机号、状态）， **不涉及任何权限**。开户这一步不会产生任何`Grant`或权限相关的数据。

### 2. 创建 `AgentIdentityAssignment`（给账号赋予角色，即"经办"这个身份的确立）

这才是权限真正开始起作用的地方，入口是`AssignmentApplicationService.createAssignment` →
`GrantProvisioningService.onAssignmentCreated`：

```
1. 校验(scopeDimension, scopeValue, roleCode)这个组合能不能解析出角色模板
   —— 解析不出直接报错，不允许创建一个"权限模板都配不上"的身份分配
2. 保存AgentIdentityAssignment
3. 登记AssignmentCreated领域事件
```

**关键点：这一步不会创建任何持久化的`Grant`记录。** 这是经过一次重构后的设计——早期版本会在这里把角色模板"物化"成一条
`Grant`
，但这样角色模板一旦被修改，已经创建的身份分配就会和模板脱节，需要额外的批量迁移作业去同步。现在改成"创建时只校验、不生成"，真正的权限内容留到
**判定的那一刻**由`EffectivePermissionService`
实时解析对应的角色模板——好处是角色模板改了，所有引用它的人立刻生效，不需要任何补丁作业；代价是每次判定都要多一次模板解析（成本很低，是索引查询，不是全表扫描）。

角色变更（`onAssignmentRoleChanged`）、离职停用（`onAssignmentDeactivated`）同理——都只是改`AgentIdentityAssignment`
自身的状态，不再需要"撤销再重建Grant"这种联动。

### 3. 需要显式配置的权限（不是"创建用户"触发的，是管理员单独操作）

- **能力层配置**（"计划A能办哪些业务"）：`GrantConfigurationApplicationService.createCapabilityGrant`
- **总部给个别账号的额外授权/DENY例外**：`GrantConfigurationApplicationService.createSubjectGrant`
- **计划间代办**（整体/指定人员）：`DelegationApplicationService.createWholesaleDelegation` / `createSelectiveDelegation`
- **跨企业委托给经办**：`DelegationApplicationService.createCustomerToAgentDelegation`

这四类都会真正落一条`Grant`记录，且都是独立于"创建用户"的管理动作，由`GrantActivationPolicy`决定要不要审批（默认`HQ_CONFIG`
/角色模板不需要，`PLAN_DELEGATE`/`CUSTOMER_TO_AGENT`需要）。

---

## 三、每个渠道的登录认证处理流程

三个渠道的登录认证最终都收口到同一个方法：
`IdentityResolutionService.resolve(CredentialOwner owner, Channel channel, String proof, String phoneNumber)`
。区别只在于"谁调用它、拿到结果之后做什么"。

### `resolve` 内部做的事 (所有渠道共用)

```
1. 按owner查该持有者名下的候选凭证(CredentialRepository.findByOwner)
2. 过滤出适用于当前channel的凭证
3. 逐条交给CredentialAuthenticator校验(内部会先查凭证status，REVOKED的直接拒绝)
4. 只要有一条通过：
   - owner是AccountCredentialOwner(密码/发给个人的UKey) → 账号就是它本身
   - owner是CustomerCredentialOwner/PlanCredentialOwner(企业/计划统一UKey)
     → 用phoneNumber在account表里定位到具体的人，
       并校验这个人确实有一条覆盖该客户/计划的生效AgentIdentityAssignment
       (不做这一步的话，任何人报一个手机号就能冒充该客户的经办)
5. 最后再查一次账号本身是不是ACTIVE(账号被冻结，凭证再有效也不能建立新身份)
```

### 网上渠道（经办登录）

```
经办输入客户/计划级UKey的proof + 手机号
  → SessionApplicationService.openSessionWithCredential(
        owner=CustomerCredentialOwner或PlanCredentialOwner, channel=ONLINE, proof, phoneNumber)
  → IdentityResolutionService.resolve(...) 拿到AccountId
  → LoginTokenService.issueToken(accountId, ONLINE) 签发token(Sa-Token)
  → 建Session(id=token, effectiveIdentity=直接指向该账号)
```

### 总部渠道（运营人员登录）

```
运营人员输入账号+密码
  → openSessionWithCredential(owner=AccountCredentialOwner(accountId), channel=HQ, proof=密码, phoneNumber=null)
  → 走同一个resolve方法，owner是账号级，phoneNumber用不上，密码校验通过就是账号本身
  → 建Session，逻辑跟网上渠道完全一样，只是channel不同
```

### 网点渠道（柜员登录 + 二次授权，两段式）

```
第一段——柜员本人登录：
  柜员账号+密码 → openSessionWithCredential(owner=AccountCredentialOwner(柜员accountId), channel=BRANCH, ...)
  → 建Session，此时primaryAccountId=effectiveIdentity=柜员自己

第二段——二次授权(身份提升)：
  柜员插入客户的UKey，经办报手机号
  → SessionApplicationService.performSecondaryAuth(sessionId, owner=CustomerCredentialOwner, proof, phoneNumber)
  → SecondaryAuthService.elevate(tellerAccountId, owner, proof, phoneNumber)
      → 内部还是调用同一个IdentityResolutionService.resolve(...)
      → 解析出被授权经办的AccountId
      → 包装成 EffectiveIdentity(identityAccountId=经办, actingAccountId=柜员, viaSecondaryAuth=true)
  → session.elevateIdentity(...)，之后这个会话的所有业务操作都按"经办"的权限判定，
    但actingAccountId=柜员这条记录留着，用于审计("这次操作实际是柜员X代经办Y做的")
```

四条链路的共同点： **认证 (校验凭证、定位身份)和渠道差异 (会话怎么建、是否需要二次授权)完全解耦**——
`IdentityResolutionService`不知道也不关心调用它的是哪个渠道的哪个环节，渠道差异全部在`SessionApplicationService`/
`SecondaryAuthService`这一层处理。

---

## 四、用户请求的权限校验流程（功能权限 + 数据权限）

### 功能权限（菜单/按钮/API入口看不看得见）

功能权限不依赖具体请求参数，做法是 **登录/选计划后拉一次"当前身份在当前计划下命中的权限点集合"，前端本地判断可见性**（
`PermissionQueryService`按需批量查询，业务微服务侧用`permission-sdk`的`CachingPermissionClient`
做本地短TTL缓存，避免每次渲染都发请求）。这只是体验层，真正的安全边界在下面的数据权限。

### 数据权限（这一次具体请求，能不能做）

单体内（本项目自己的Controller/Service）调用路径：

```
业务代码 → PermissionQueryService.checkPermission(identity, planId, businessCode, actionCode)
  → EffectivePermissionService.checkPermission(identity, planId, permission, now)
      1) checkPlanCapability(planId, business, now)
         —— 查subject_kind=CAPABILITY的持久化Grant，按ScopeRule(PLAN/PRODUCT/CUSTOMER/
            ACCOUNT_MANAGER/OPERATING_MODE)匹配，DENY优先合并 → 计划本身不支持这个业务，
            后面直接短路返回false，不用再看是谁在操作
      2) checkSubjectGrant(identity, planId, permission, now)
         —— 候选来源A：GrantRepository查该身份可能命中的持久化Grant(HQ_CONFIG主体授权/
            代办/跨企业委托)，再用GrantSubject#covers精确匹配
         —— 候选来源B：AssignmentRepository查该身份当前生效的身份分配，
            实时解析角色模板，构造成不落库的"虚拟Grant"
         —— A∪B按ScopeRule匹配 + 按(business,action)匹配，DENY优先合并
      3) 两步都通过才放行
```

微服务场景下（`permission-sdk`）：

```
业务微服务 → PermissionGuard.require(client, accountId, planId, businessCode, actionCode)
  → CachingPermissionClient(本地短TTL缓存命中就直接返回)
      未命中 → HttpPermissionClient → Permission服务(本项目) → EffectivePermissionService
  → 服务不可达时fail-closed(按拒绝处理，不放行)
```

紧急收权（账号冻结、Grant撤销）不等本地缓存TTL自然过期：`Grant.revoke()`/`Account.freeze()`登记的领域事件经
`OutboxDomainEventPublisher`写入`outbox_event`表，中继进程投递到消息队列广播出去，业务微服务订阅后调用
`CachingPermissionClient.invalidate(accountId)`立即清缓存；`AccountFrozen`还会同步联动
`LoginTokenService.invalidateAllTokensOf`把这个账号的所有登录态踢下线——权限层面收权和登录态层面强制下线两件事同时发生，不留窗口期。
