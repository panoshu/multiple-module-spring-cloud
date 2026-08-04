# 设计文档：登录会话集成方案与网关/业务服务鉴权架构

## 0. 文档说明

本文档聚焦企业年金权限体系从"领域模型"落地到"实际部署架构"时的两个具体问题：

1. 登录/会话管理是否引入 Sa-Token，如果引入，怎么通过防腐层与现有权限模型解耦
2. 微服务化部署后，网关与各业务服务应该如何分工完成功能级 / 数据级鉴权

前置假设：权限领域模型（`Account` / `Grant` / `AuthorizationEngine` / `RoleTemplate` / `AgentIdentityAssignment` 等）已按
DDD 分层完成 domain / application / infrastructure 三层实现，本文档不重复其内部设计，只讨论它与登录会话组件、网关、业务服务之间的边界与协作方式。

---

## 1. 核心设计原则：认证与授权分离

|            | 认证 AuthN                                  | 授权 AuthZ                                                                              |
|------------|---------------------------------------------|-----------------------------------------------------------------------------------------|
| 回答的问题 | 你是谁？你的登录态是否有效？                | 你能做什么？                                                                            |
| 复杂度来源 | 通用问题（token、会话、多端登录、踢人下线） | 本项目特有的业务规则（两层 AND 校验、DENY 优先、多维度 ScopeRule、代办/委托、角色模板） |
| 建议归属   | 可以外包给成熟组件                          | 必须是自研的 `Grant` / `AuthorizationEngine`，任何组件都不能替代或绕过                  |

**基本约束：任何触达业务权限判定的地方，必须经过 `AuthorizationEngine`；不允许被别的组件（包括 Sa-Token
自带的权限注解）简化替代。** 这是本设计所有后续讨论的前提，也是为什么 Sa-Token
只能进"登录会话"这一侧、不能进"权限判定"那一侧的根本原因。

---

## 2. Sa-Token 集成设计

### 2.1 集成范围界定

| 能力                                               | 是否交给 Sa-Token              | 说明                                                                                       |
|----------------------------------------------------|--------------------------------|--------------------------------------------------------------------------------------------|
| 登录态签发 / 校验（token 机制）                    | ✅ 是                          | 复用其成熟实现，不重复造轮子                                                               |
| 强制下线 / 踢人                                    | ✅ 是                          | 账号冻结时联动踢下线                                                                       |
| 多端登录并发策略                                   | ✅ 是                          | 网上渠道、网点渠道可以配不同的并发登录数                                                   |
| 会话存储（内存 / Redis）                           | ✅ 是                          | 用其内置的持久层抽象即可                                                                   |
| `@SaCheckPermission` / `@SaCheckRole` 业务权限判定 | ❌ **禁止使用**                | 表达能力覆盖不了两层 AND + DENY 优先 + 多维度 ScopeRule 模型，用了等于把之前的设计推倒重来 |
| 网点二次授权（身份提升）                           | ❌ 自行实现                    | `EffectiveIdentity` 是本项目特有概念，Sa-Token 没有对应机制                                |
| 有效权限快照缓存                                   | ❌ 不用 Sa-Token 的 Session 存 | 生命周期和失效触发条件跟登录态完全不同，见 2.4                                             |

### 2.2 防腐层端口定义

登录态是一种技术能力抽象，不是业务概念，端口放在应用层的 `port` 包（与 `UnitOfWork`、`DomainEventPublisher` 同级），不放进
domain 层：

```java
package com.pension.permission.application.port;

public interface LoginTokenService {
    /** 登录成功后签发token */
    String issueToken(AccountId accountId, Channel channel);

    /** 网关/服务侧校验token，返回对应账号；无效或已失效返回empty */
    Optional<AccountId> verifyToken(String token);

    /** 登出 / 强制下线 */
    void invalidateToken(String token);

    /** 账号冻结联动：把这个账号名下所有登录态都踢下线 */
    void invalidateAllTokensOf(AccountId accountId);
}
```

### 2.3 基础设施层适配器

```java
package com.pension.permission.infrastructure.satoken;

public final class SaTokenLoginTokenService implements LoginTokenService {

    @Override
    public String issueToken(AccountId accountId, Channel channel) {
        // channel作为设备类型区分，不同渠道可以配不同的并发登录/踢人策略
        StpUtil.login(accountId.value(), channel.name());
        return StpUtil.getTokenValue();
    }

    @Override
    public Optional<AccountId> verifyToken(String token) {
        try {
            Object loginId = StpUtil.getLoginIdByToken(token);
            return loginId == null ? Optional.empty() : Optional.of(new AccountId(loginId.toString()));
        } catch (NotLoginException e) {
            return Optional.empty();
        }
    }

    @Override
    public void invalidateToken(String token) {
        StpUtil.logoutByTokenValue(token);
    }

    @Override
    public void invalidateAllTokensOf(AccountId accountId) {
        StpUtil.kickout(accountId.value());
    }
}
```

这是唯一允许出现 `StpUtil` 字样的地方。domain 层、application 层的其余代码全部只依赖 `LoginTokenService` 接口，编译期就切断了对
Sa-Token 具体 API 的依赖——以后要换掉 Sa-Token（换成自建 JWT 方案，或换另一个框架），只需要新写一个 `LoginTokenService`
实现，域模型和用例代码零改动。

### 2.4 与现有领域模型的对接方式

**Token 与 `Session`/`EffectiveIdentity` 是两条独立的轨道，不能合并：**

- Token（Sa-Token 管）回答"这次 HTTP 请求，操作者的登录态是否有效、对应哪个账号"
- `EffectiveIdentity`（我们自己的模型）回答"这次业务操作，实际应该按谁的权限去判定"——两者在网点二次授权场景下会分叉：柜员的
  token 证明"这是柜员本人在操作"，`EffectiveIdentity` 证明"这次操作要按被授权经办的权限执行"

对接方式：

1. `SessionApplicationService.openSession`：登录成功后调用 `loginTokenService.issueToken(...)` 拿到 token，与我们自己的
   `Session` 聚合（含 `channel`、`effectiveIdentity`、`selectedPlanId`）分开维护，两者通过 `sessionId` 关联，token
   只负责"这次请求是否合法"，业务上下文全部在 `Session` 里
2. 网点二次授权：`SecondaryAuthService.elevate(...)` 产出的 `EffectiveIdentity` 完全是我们自己的概念，跟 Sa-Token
   的登录态无关，不需要、也不应该体现在 token 里
3. **账号冻结要双向联动**：`AccountApplicationService.freeze()` 触发 `AccountFrozen` 领域事件的同时，事件的订阅方（或应用服务本身）要联动调用
   `loginTokenService.invalidateAllTokensOf(accountId)`——保证"权限层面收权"（Grant
   撤销触发的快照失效）和"登录态层面强制下线"同步发生，只做一半会出现"权限已经收了，但这个人还能带着旧 token 继续操作直到
   token 过期"的窗口期

### 2.5 风险提示与规范约束

- **严禁在 Controller 层出现 `@SaCheckPermission` / `@SaCheckRole` 注解做业务权限门禁**——建议在 CI /
  代码规范检查里加一条静态规则直接禁止项目里出现这两个注解，防止后续迭代中不知情的开发者"顺手"这么写，绕开
  `AuthorizationEngine`
- Sa-Token 的 Session 存储只放"登录态相关"的数据（token、登录设备、登录时间），不要把"有效权限快照"这类缓存也塞进同一个
  Session
  对象里——两者失效触发条件不同（登录态失效="下线"，权限快照失效="收权"），混在一起会导致"明明账号还在线，权限却是过期数据"或反过来的问题

---

## 3. 网关与业务服务的鉴权架构

### 3.1 两类鉴权的边界

|                      | 功能级鉴权                                          | 数据级鉴权                                                       |
|----------------------|-----------------------------------------------------|------------------------------------------------------------------|
| 回答的问题           | 这个身份能不能看到/点这个菜单、按钮、API入口        | 这一次具体请求，这个身份在这个计划上，能不能对这个业务做这个操作 |
| 是否依赖具体请求参数 | 不依赖（跟身份绑定，跟请求内容无关）                | 依赖（planId / businessCode / actionCode 都来自请求本身）        |
| 适合发生的位置       | 前端本地判断（拉一次"有效权限快照"缓存） + 后端兜底 | 只能在懂业务语义的服务内部，请求处理路径上                       |

### 3.2 总体架构

```
客户端(网上/总部/网点)
      │  携带 token
      ▼
┌─────────────────────────────────────────┐
│  API 网关                                 │
│  - 校验token(委托给Sa-Token封装的AuthN模块) │
│  - 粗粒度路由(渠道白名单/限流/黑白名单IP)    │
│  - 不做、也不能做业务级数据鉴权              │
└─────────────────────────────────────────┘
      │  转发请求 + 透传身份信息(AccountId/Channel/SessionId)
      ▼
┌─────────────────────────────────────────┐
│  业务微服务(缴费/待遇领取/转移...)          │
│  - 从请求里解析出 (identity, planId,       │
│    businessCode, actionCode)              │
│  - 通过 Permission SDK 发起数据级鉴权检查   │
└─────────────────────────────────────────┘
      │  checkPermission(identity, planId, business, action)
      ▼
┌─────────────────────────────────────────┐
│  Permission 服务                          │
│  - AuthorizationEngine + Grant仓储         │
│  - 能力层/主体层两层AND校验                 │
└─────────────────────────────────────────┘
      ▲
      │  GrantRevoked / AccountFrozen 等事件(经MQ广播)
      │
┌─────────────────────────────────────────┐
│  Outbox中继进程                           │
│  - 轮询outbox_event表，投递到消息队列       │
└─────────────────────────────────────────┘
```

### 3.3 网关的职责边界

网关只做两件事：

1. **认证**：校验 token（通过 `LoginTokenService` 端口，底层是 Sa-Token），无效则直接拒绝，不透传到后面的服务
2. **粗粒度路由与防护**：按渠道做路由隔离（比如网点渠道的路由只对来自专线网络的请求开放）、限流、基础的黑白名单

**网关不做数据级鉴权**，原因很直接：数据级鉴权需要知道"这个请求里的哪个字段是 planId、这次操作对应哪个
businessCode/actionCode"——这是业务语义，网关如果要理解这些，就必须为每个业务服务的每个接口维护一份参数映射规则，业务服务改个字段网关就要跟着改，属于典型的耦合陷阱。网关应该对业务请求体"无感"，只认
token 和路由规则。

### 3.4 业务服务侧：Permission SDK

为了让每个业务团队不需要各自摸索怎么调用 Permission 服务，统一提供一个 SDK，核心是两层封装：

**第一层：远程调用客户端**

```java
public interface PermissionClient {
    boolean checkPermission(AccountId identity, PlanId planId, BusinessCode business, ActionCode action);

    /** 一次请求需要判断多个权限点时用，避免N次网络调用 */
    Map<Permission, Boolean> checkPermissions(AccountId identity, PlanId planId, Set<Permission> permissions);
}
```

**第二层：拦截器/注解，降低业务代码的使用门槛**

```java
@RequirePermission(business = "CONTRIBUTION", action = "SUBMIT")
public ResponseEntity<?> submitContribution(@RequestBody ContributionRequest req) {
    // 方法体不需要出现任何鉴权代码，AOP切面在进入方法前已经做完检查
}
```

`planNo` 等参数从请求对象里怎么取，由每个业务服务实现一个很薄的 `PermissionContextResolver`（把自己的 DTO 映射成 `planNo`/
`business`/`action`）交给切面调用，SDK 本身不关心每个服务的 DTO 长什么样。这样业务开发者的使用体验跟直接用 Sa-Token
注解基本一致，但实际走的是我们自己的判定引擎，不会有能力缺口。

### 3.5 性能：本地缓存 + 事件驱动失效

如果每次业务请求都同步远程调用 Permission 服务，链路会多一跳网络延迟，量大时 Permission 服务也会成为瓶颈。SDK 内置 **本地短
TTL 缓存**（例如几秒到几十秒），缓存 key 是 `(identity, planId)`
对应的已解析权限集合——这正是之前设计的"有效权限快照"在微服务场景下的落地形态。

**紧急撤销场景不能靠 TTL 兜底**：账号冻结、代办关系紧急撤销这类操作，要求"立刻生效"，不能等本地缓存自然过期。做法是复用已经设计好的
outbox 机制：

1. `GrantRevoked` / `AccountFrozen` 等事件已经在同一个数据库事务里写入 `outbox_event` 表（已实现）
2. 一个独立的中继进程轮询该表，把待发布事件投递到消息队列（Kafka / RocketMQ 等）
3. 所有引入了 Permission SDK 的业务服务订阅这个 topic，收到事件后立即清除本地缓存里对应身份的 key，不等 TTL

### 3.6 降级策略

Permission 服务不可达时，SDK 应该 **fail-closed（默认拒绝）**，而不是
fail-open（默认放行）——企业年金属于金融合规场景，"判定服务挂了就放行"是不可接受的风险敞口。可以在拦截器层面把这个策略做成显式配置项，但默认值必须是拒绝。

### 3.7 与 Sa-Token 集成部分的衔接

网关校验 token 用的 `LoginTokenService`（第2节的端口）解析出 `AccountId` 之后，通过请求头（如 `X-Account-Id`、`X-Channel`、
`X-Session-Id`）透传给下游业务服务；业务服务侧不需要再理解 token/Sa-Token 的存在，只需要读这几个header——认证的复杂度完全被网关这一层吸收掉了，业务服务只需要面向
`AccountId` + `Channel` + `SessionId` 编程，这也是"AuthN 和 AuthZ 分离"这个核心原则在整条链路上的体现。

---

## 4. 待确认 / 后续事项

- Permission SDK 的本地缓存 TTL 具体取值，需要结合业务服务的实际QPS和 Permission 服务的容量做压测后确定
- outbox 中继进程投递到消息队列这一环，目前还没有选定具体的 MQ 产品和投递可靠性保证方式（至少一次 vs 恰好一次）
- `LoginTokenService` 的 `invalidateAllTokensOf` 与 `AccountFrozen`
  事件之间，是同步调用还是也走事件订阅，需要根据账号冻结操作对"实时性"和"事务边界"的要求来定
- 是否需要在网关层也接入 Permission SDK
  做一层"粗粒度但快速"的预检查（比如提前拒绝明显不在该渠道范围内的请求），目前的设计倾向于不做，避免网关变重，但如果发现业务服务被大量明显无效的请求打到，可以重新评估
