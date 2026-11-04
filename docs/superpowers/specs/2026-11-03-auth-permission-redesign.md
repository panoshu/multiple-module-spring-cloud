# 认证与权限体系重构设计（含统一接口路由模型）

> 日期：2026-11-03
> 状态：**✅ 全部完成**（2026-11-04，A1/A2/B/C/D + D 剩余四项全部落地，全仓 89 模块构建绿，auth 373/ArchUnit 6 测试绿）
> 前置：`2026-07-26-iam-service-design.md`、`2026-07-26-business-core-kernel-public-api-design.md`、`2026-08-30-bff-kernel-api-refactoring.md`、`2026-11-03-channel-architecture-refactoring.md`
> 实测依据：2026-11-03 权限认证体系源码审计（会话契约断链等 7 项发现）

## 实施进度（2026-11-04 第四批更新：全部完成）

| 阶段 | 状态 | 说明 |
|---|---|---|
| A1 契约下沉 | ✅ 完成 | `shared-security-contract`（lib 层）：SessionClaims（原 kernel SessionContext）、ChannelType（原 gateway）、权限注解族（原 auth-api）、PlanIdAware（原 starter）；55+ 消费文件切换 |
| A2 校验契约收编 | ✅ 完成 | PermissionCheckPort/PermissionRegistrationPort 纯契约 + 8 个 DTO 下沉契约层；auth-api 两接口 extends 端口；permission-starter 注入端口并**摘除 auth-api 依赖**（补 spring-webmvc 显式依赖）；DataScopeTest 随迁 |
| B 声明落地 | ✅ 完成 | auth-adapter `SessionClaimsWriter`：登录/选计划/二次授权完成/撤销/关闭五事件 read-modify-write 完整声明到 Token-Session 单键（`SessionClaims.SESSION_CLAIMS_KEY`，channelType 写 ChannelType 枚举名修复 Guard 大小写失配）；网关 `SessionContextInjector` 优先整体透传声明（旧 3 字段路径保留为回退）；新增 `SessionClaimsGuardRoundTripTest`（7 用例：JSON 往返保真 + INTERNET 代办/BRANCH 二次授权/HQ/小写拒绝），kernel application 66/66 测试绿 |
| C 缓存分层 | ✅ 完成 | `HttpExchangePermissionExecutor` 前置 Caffeine（键=账号+计划+权限码，`shared.permission.cache.*` 可配，默认 300s/10000 条，fail-closed 保持：RPC 异常不缓存不降级）；`invalidate(accountId)/invalidateAll()` 预留事件失效接线（Phase D 接线） |
| D 治理（核心） | ✅ 完成 | 新增 `arch-tests` 模块：ArchUnit **七条不变量全部 GREEN**（demo-shared 零业务 api、契约层纯净零传输注解、网关框架零业务 api、SessionClaims/ChannelType 唯一定义、sa-token 位置受限、BFF 零业务规则）。注：ArchUnit 需 1.4.1（1.3.0 在 Java 25 下 jar 导入恒 0）；starter/boot-jar 类对 ArchUnit 不可见，改用锚点类反查 jar 位置导入 |
| D 失效接线 | ✅ 完成（第三批） | 契约层新增 `PermissionChangedEvent`（纯 record：定向 accountId / 全局失效二态）；starter 新增 `PermissionCacheInvalidationListener`（@EventListener → invalidate/invalidateAll，TTL 300s 兜底）；auth 三个发布点：GrantLifecycle（全局，GrantSubject 密封族影响账号不可廉价枚举）、Assignment（定向账号，null 防御升级全局）、权限码注册 upsert（全局，仅 upserted>0 触发） |
| D 注册 TTL 心跳 | ✅ 完成（第三批） | `PermissionRegistrationRefresher`：自管理守护线程按 `shared.permission.registration.refresh-interval-ms`（默认 300s，≤0 禁用）重跑幂等注册（auth upsert 即续期，fail-soft）；Runner 重构出 `registerNow()`；解决 CODEBUDDY 遗留问题"服务下线后注册表陈旧" |
| D AnnuityChannel→Channel | ✅ 完成（第三批） | kernel types 枚举改名（枚举值不变，DB/JSON 序列化不受影响），60 文件 import/引用同步；`toAnnuityChannel` 等方法名语义正确保留 |
| D 包名规整 | ✅ 完成（第四批） | `com.pension.permission → com.example.auth`：auth 五模块（domain/application/infrastructure/types/starter）目录树整体迁移，327 文件包声明/import/yml(@MapperScan、日志级别)同步重写；arch-tests 死规则清理（com.pension 防御条目随包消亡移除）；AuthApplication 迁至 `com.example.auth` 根包（@SpringBootApplication 默认扫描即覆盖全域） |
| 验证（第三批） | ✅ | 全仓 89/89 模块 BUILD SUCCESS；auth-application 44/44、auth-infrastructure 154/154、permission-starter 20/20 测试绿 |

> 附带完成：全仓约 35 个腐化测试修复（跨 kernel/approval/file/annuity/auth 的 UserNo→String 审计位迁移与旧包名引用：shared.identifier.id→core.types、user.valueobject→core.types.identity/contact、credential ValidityPeriod→core.types.temporal）。这批测试腐化是历史重构（类型迁移）未同步测试的存量债，与 SOFA BOM 解析问题叠加导致 CLI 全量构建自 2026-10-29 起从未跑通。


---

## 0. 结论速览

| 问题 | 结论 |
|---|---|
| 会话契约放哪 | 新建 `shared-security-contract`（demo-shared lib 层）：SessionClaims + ChannelType + 权限注解族 + PermissionCheck 纯契约。一处定义，四方（网关框架/kernel/auth/permission-starter）引用 |
| 会话声明怎么流转 | auth 在会话生命周期事件时把**完整声明写入 Token-Session 单键**；网关整体透传 + 签名；消费方验签解析。渠道差异化防线从死代码变实防线 |
| AuthN/AuthZ 怎么分 | 认证只发生在网关（+auth 签发）；授权分两级——功能权限（AOP+分层缓存）在应用层，业务权限（渠道差异规则）在领域层（kernel Guard） |
| businessType 路由在哪层 | **BFF**（网关只做渠道路由，不解析请求体）；服务内部用 kernel 策略注册表 + 配置路由（均已存在，无需新机制） |
| 权限校验性能 | 三层缓存：本地 Caffeine → auth Redis → 权限模型；权限变更事件失效本地缓存 |
| 何时实施 | 设计定稿后分四个阶段（A 契约下沉 / B 声明落地 / C 缓存 / D 治理），见 §7 |

## 1. 现状问题（实测证据摘要）

| 编号 | 问题 | 证据 |
|---|---|---|
| P0-1 | 会话契约断链 | kernel `SessionContext` 24 字段；网关 `buildSessionContext` 仅写 userNo/channelType/planNo；auth 仅往 Token-Session 写 `currentPlanId` 一键 → Guard 渠道差异校验空转 |
| P0-2 | 权限校验无本地缓存 | `@RequirePermission` 每请求远程 RPC auth |
| P1-3 | 契约归属错位三连 | permission-starter→auth-api（8 接口+4 注解+10 DTO）；auth→kernel-types；kernel→permission-starter |
| P1-4 | 渠道枚举多处定义 | gateway `ChannelType` / `core.types.AnnuityChannel` / annuity `NETAPP` 映射 / 裸字符串 |
| P1-5 | SessionContext 位于 kernel api | 会话是横切契约，却要依赖业务编排框架才能获得 |
| P2-6/7 | 命名漂移与治理债 | `com.pension.permission`、角色 hardcode、TTL 硬编码、自注册无下线清理 |

## 2. 设计原则

1. **信任边界唯一**：channel-gateway 是唯一外部认证点。内网服务不做认证，只消费网关注入的**已签名声明**（验签防内网伪造）。认证逻辑零下沉。
2. **单一契约源**：横切契约（会话声明、渠道枚举、权限注解、权限校验契约）只在一处定义，其余全部引用。判据：**框架模块的依赖里不允许出现任何业务服务的 api**。
3. **AuthN 与 AuthZ 分离**：认证（你是谁）= 网关+auth；授权（你能干什么）= 功能权限（应用层 AOP）+ 业务权限（领域层 Guard）。业务规则进领域层，不散落 BFF/网关。
4. **Claims-based 会话**：声明是 auth 在会话生命周期事件时写入的快照，网关只透传不拼装。信任链：auth 写入 → 网关签名转发 → 消费方验签。声明变更走事件刷新，不允许消费方回写。
5. **权限判定分层降级**：本地缓存 → Redis → 权限模型，逐层回源；**fail-closed 语义全程保持**（auth 不可达时拒绝，不放行）。
6. **传输关注点与契约分离**：纯契约接口（无 @HttpExchange）+ 服务端 Controller 实现契约 + 客户端子接口加传输注解——复用 2026-08-30 已验证的纯契约模式，全项目风格一致。

## 3. 目标架构

### 3.1 信任边界与分层

```
外部请求
  │
  ▼
[channel-gateway] ←—— 唯一认证点（渠道 StpLogic 认证、剥离伪造头、声明透传+HMAC 签名）
  │ X-Session-Claims(Base64) + X-Session-Sig + X-Session-Expire + X-Account-Id/Sig
  ▼
[internet-bff / intranet-bff] ←—— 渠道适配 + businessType 路由（ScenarioHandler）+ ViewModel
  │ 调业务服务 api（@HttpExchange 客户端）
  ▼
[业务服务（annuity …）] ←—— 授权决策
  │ ① 功能权限：@RequirePermission AOP（本地缓存→auth）
  │ ② 业务权限：kernel DefaultBusinessAccessGuard（渠道差异规则，领域层）
  │ ③ 数据范围：@DataScope（实时，短 TTL）
  ▼
[auth-service] ←—— 权威：登录/选计划/二次授权/权限模型；会话声明写入 Token-Session
  │
  ▼
[shared-security-contract] ←—— 底部横切契约，被上述全部引用
```

### 3.2 模块与依赖（重构后）

```
demo-shared/shared-lib/shared-security-contract   （新，纯契约，仅 Lombok/校验注解）
├── SessionClaims record（超集 v2，见 §3.3）
├── ChannelType 枚举（唯一渠道定义）
├── SessionSignatureUtils（自 shared-utils/session 迁入，或 utils 保留、contract 引用）
├── 权限注解族：@RequirePermission / @DataScope / @DataScopeDimension / PermissionCategory / PlanIdAware
├── PermissionCheckPort 纯契约接口 + PermissionCheckRequest/Response/DataScope DTO
└── ClaimsHeaders 常量（X-Session-Claims 等头名，唯一处定义）

依赖方向（全部单向向下）：
shared-security-contract ◄ gateway-framework（读声明、写头）
                           ◄ bff-framework（读渠道上下文）
                           ◄ kernel api（SessionContext = 引用/委托 SessionClaims）
                           ◄ auth-api（PermissionCheckApi extends PermissionCheckPort + @HttpExchange）
                           ◄ shared-permission-starter（AOP + 缓存执行器，不再依赖 auth-api）
```

要点：
- `kernel` 的 `SessionContext` 不删除，改为 `SessionClaims` 的薄委托或直接替换 import——**kernel 对会话的依赖降级为对最底层契约的依赖**，"为了拿 SessionContext 而依赖 kernel"的理由消失
- `auth-api` 保留全部业务 HTTP 契约（登录、菜单、二次授权），但权限校验契约 extends 共享纯契约；`shared-permission-starter` 只 import 共享契约
- 网关框架彻底零业务依赖（本轮已达成一半：SessionSignatureUtils 已下沉）

### 3.3 SessionClaims v2（字段超集与生命周期）

字段沿用 kernel SessionContext 24 字段（设计已合理），调整三点：
1. `channelType` 类型改为 `ChannelType` 枚举（序列化值），消除裸字符串
2. `permissionCodes` 保留在声明内（授权快照，供 kernel Guard 与本地判定；权限变更事件负责刷新，见 §5）
3. 增加 `claimsVersion`（契约版本号，为将来字段演进预留）

**写入时机（auth-service 职责，全部走领域事件监听，已有机制扩展）**：

| 事件 | 写入字段 |
|---|---|
| 登录成功 | 身份 + 渠道 +（INTERNET）delegatedPlanNos |
| 选择计划 | 客户/计划/产品/账管人 + businessPermissions 快照 |
| 清除计划 | 清空客户/计划字段 |
| 二次授权完成/撤销（BRANCH） | hasSecondaryAuth / secondaryAuthSessionId / borrowedApproverId |
| 权限规则变更 / 代办委托变更 | 重新计算权限快照并刷新 |

存储形态：Token-Session **单键** `sessionClaims`（完整 JSON），废弃散键 `currentPlanId`。
网关 `SessionContextInjector` 改为：读单键 → Base64 → 签名注入（删除手工拼 3 字段逻辑）。
消费方 `SessionContextResolver`（迁至 contract 层或保留 kernel 实现读契约）：验签 → 反序列化 → fail-closed。

### 3.4 权限校验三层模型

| 层 | 校验内容 | 机制 | 性能 |
|---|---|---|---|
| 网关 | 认证（token 有效性）、渠道合法性、黑名单 | StpLogic + 白名单 + ExcludeRoute | O(1) |
| 应用层（功能权限） | 该账号能否调用此接口（business+action） | `@RequirePermission` AOP → 本地 Caffeine（TTL 5min + 事件失效）→ auth RPC | 常态 0 RPC |
| 领域层（业务权限） | 该账号能否对该客户/计划办理该 businessType（含渠道差异：代办、二次授权） | kernel `DefaultBusinessAccessGuard` 读 SessionClaims | 0 RPC（纯声明判断） |
| 数据范围 | 行级可见性 | `@DataScope` 实时 RPC + 短 TTL（行级必须准） | 按需 RPC |

### 3.5 三层路由模型（统一接口 URL）——回答路由归属

**前端契约**：渠道内统一入口（`/bff/**`），请求体必填 `businessType`，渠道间 URL 分开（/internet/bff/**、/hq/bff/**）。

| 路由层 | 路由依据 | 实现 | 状态 |
|---|---|---|---|
| L1 网关 | **URL 路径前缀**（渠道） | gateway routes（`/internet/**`→internet-bff） | ✅ 已实现 |
| L2 BFF | **请求体 businessType**（业务类型） | `ScenarioHandlerRegistry.handlerFor(businessType)` → 各业务 ScenarioHandler → 对应服务 api | ✅ 已实现 |
| L3 服务内 | **聚合内 businessType + 渠道维度** | ① `BusinessTypeRegistrar`/`SupportedBusinessTypeValidator` 拒绝不归管的类型；② `configGateway.getStepRoute(businessType, channel, …)` 配置路由决定步骤流；③ `StepActionHandlerRegistry` 等策略注册表按 name 装配扩展实现；④ `BusinessExtension` 多态值对象按 businessType 区分数据 | ✅ 已实现（kernel） |

**为什么不在网关做 businessType 路由**：businessType 在请求体内，网关做 body 感知路由需要解析 JSON——破坏 WebFlux 流式转发、性能差、网关职责膨胀；且渠道安全策略（加解密/会话）与业务路由是正交关注点。
**为什么不在业务服务间用 URL 前缀区分**：前端契约稳定性优先（业务类型增减不改 URL）；统一入口的代价（必填 businessType 字段）可接受，且 BFF 已实现。
**服务内新增一个 businessType 的操作**：实现该类型的 handler 扩展 + BusinessExtension 子类 + 配置路由 + BFF 侧新增 ScenarioHandler（加依赖+DB/注册表加行）——与 2026-08-08 BFF 设计的扩展机制一致。

**本阶段是否一起实现**：三层路由**代码均已存在**，无需新实现；本设计只需把它定为**架构不变量**（§8），防止将来有人在网关加 body 路由或在 BFF 写业务规则。

## 4. 契约迁移设计（复用纯契约模式）

与 2026-08-30 方案（kernel 纯契约 + 服务端实现 + 客户端 extends）完全同构：

```
shared-security-contract.PermissionCheckPort（纯接口，无传输注解）
    ↑ implements                        ↑ extends + @HttpExchange
auth PermissionCheckController      auth-api PermissionCheckApi（客户端代理）
（服务端 @RequestMapping）           （供 starter/业务服务注入调用）
```

迁移步骤：注解族与 DTO 平移（改 import）→ `PermissionCheckApi` 改 extends 契约 → `shared-permission-starter` 删除 auth-api 依赖 → kernel `SessionContext` 换 `SessionClaims` → `ChannelType` 三处统一 → annuity `mapChannel` 删除。

## 5. 缓存与失效

- 功能权限本地缓存：Caffeine（key = accountId+planId+permission，TTL 5min 可配）
- 失效链：auth 权限变更 → 领域事件 → 集成事件（RocketMQ/Redis pub-sub）→ 各服务 `PermissionCacheInvalidationListener` → 清本地键。复用 `shared-event-starter` 与 `shared-cache-starter` 的广播失效机制（CacheEvictMessage 模式）
- 声明内 permissionCodes 同一事件触发 Token-Session 快照刷新（auth 内完成）
- fail-closed：缓存与 RPC 均不可用时拒绝请求（保持现状语义）

## 6. 安全基线清单

| 项 | 设计 |
|---|---|
| 伪造防护 | 网关剥离 X-Session-*/X-Account-* 等敏感头（已有）+ 下游验签（已有）保持 |
| 时效 | 声明 TTL 300s（可配）+ 过期拒绝；Token 生命周期 sa-token 管理 |
| 密钥轮换 | X-Session-Sig-Version 头 + 双密钥过渡窗口（新旧并验），解决已知问题 #10 |
| fail-closed | 验签失败=空声明拒绝；RPC 失败=拒绝 |
| 审计 | 登录/选计划/二次授权/权限变更事件全部落审计（shared-logging-starter 管道） |
| 最小披露 | 声明不含口令/凭证类字段；permissionCodes 为码表非数据 |

## 7. 实施阶段（设计定稿后）

| 阶段 | 内容 | 风险 |
|---|---|---|
| A 契约下沉 | 新建 shared-security-contract；注解/DTO/枚举/纯契约平移；五方引用切换；纯机械 | 低（编译器兜底） |
| B 声明落地 | auth 事件监听写完整声明（单键）；网关整体透传；Guard 校验补单测；废弃 currentPlanId | 中（行为变更，需测试） |
| C 缓存分层 | starter 加 Caffeine + 事件失效；DataScope 短 TTL | 中 |
| D 治理 | AnnuityChannel→Channel、auth 包名规整、自注册 TTL 心跳、网关角色库驱动、密钥轮换 | 低 |

每阶段全量构建 + 既有测试绿；A/B/C/D 之间可独立回滚（git 粒度）。

## 8. 架构不变量（ArchUnit 固化，防"在坏设计上反复修改"）

1. `demo-shared` 内任何模块不得依赖 `*-api`（业务服务契约）
2. `shared-security-contract` 零 Spring 传输注解、零业务 artifact 依赖
3. `gateway-framework` 零业务 artifact 依赖；网关模块不得解析请求体做路由
4. `SessionContext/SessionClaims` 只在 contract 层定义；其余处 import 即违规
5. `ChannelType` 只在 contract 层定义（网关/auth/kernel 不得私有定义渠道枚举）
6. 认证代码（sa-token 调用）只允许出现在 gateway-framework 与 auth-service
7. BFF 层不得出现业务规则（domain 零依赖，仅场景编排）
