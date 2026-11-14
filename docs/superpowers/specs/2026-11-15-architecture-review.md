# kernel 架构定位与跨服务 / shared starter 使用复核报告

> 生成日期：2026-11-15
> 依据：`2026-11-08-business-core-kernel-production-design.md`（§1/§2/§3/§4/§21.5/§23.46）、`.codebuddy/rules/01-架构与依赖规则.md`、`.codebuddy/rules/04-代码编写约束.md`、CODEBUDDY.md、当前代码与 pom 逐项核实
> 性质：架构复核（**零生产代码改动**）；配套 `2026-11-15-non-code-items-closure.md`
> 分支：`refactor/business-core-kernel-production`

---

## 1. 结论先行

| 复核维度 | 结论 | 关键依据 |
|---|---|---|
| **kernel 架构定位是否满足最初诉求** | ✅ **满足**。§21.5 最终验收 8 条全部有可执行测试（§23.46 逐条核对表）；§1 决策 9 条、§4.1 目标 9 条基本逐条落地；§2 列出的 9 类生产问题均有对应能力 | 版本化 `ProcessDefinition` + `StepExecution` + `ExternalInteraction` + Outbox/EventStore + `EventReceipt` + 乐观锁/短锁 + requestNo 幂等 + 恢复 Job；阶段 0–5 全部收口 |
| **如实标注的偏差** | 3 条（不冒充已覆盖）：① 分布式锁是**弱依赖可选注入**，且无任何宿主引入 `shared-lock-starter`；② §21.4 架构不变量**部分机器化**（存量裸参、types 含非 ID 值对象、Adapter Converter 构造值对象）；③ 真多进程 Redis 集成测试待环境 | 本文 §2.4 / §4.3、主设计 §23.46 偏差列 |
| **approval / auth / file 与 kernel 交互是否符合架构设计** | ✅ **方向合规**：跨服务只依赖 API 模块，已由 `KernelLayerArchitectureTest` 机器化；kernel 出站经防腐层网关，入站事件经适配器 + 回执幂等。⚠️ 2 处需知悉：`auth-service` 不在原设计范围（后期立项演进）、`startApproval` 发起人是服务间可信调用（批次 65 判定不做会话覆盖） | 本文 §3 |
| **shared starter 能力是否被四服务正确使用** | ⚠️ **部分正确**：id / event / web / logging / permission 使用正确；**cache 在 kernel 引入未用**、在 auth/file 正确使用；**lock 存在装配缺口**（四个服务与 annuity 均未引入 starter）；client 仅 kernel 使用 | 本文 §4 |

一句话：**最初诉求在代码与验收层面达成；剩余问题不在 kernel 领域模型，而在「shared 底座的宿主装配」与「部署配置」**（starter 依赖、profile 隔离、日志 pattern），已全部落盘为可执行检查清单。

---

## 2. kernel 架构定位与最初诉求对照

### 2.1 最初诉求还原（五组原文的收敛）

| 来源 | 诉求 |
|---|---|
| §1 设计结论摘要 L17-19 | kernel 定义为「面向相似业务办理场景的、持久化的长流程控制内核」：统一负责流程定义、流程状态、步骤执行、外部协作、可靠事件、并发幂等和技术基础设施；业务服务只提供业务规则、业务数据和业务扩展实现 |
| §1 核心决策 9 条 L23-31 | ① 三聚合保留但重新定位；② `BusinessApplication` 直接演化为流程实例聚合根、不新增 `ProcessInstance`；③ `BusinessForm` 可选；④ `BusinessBatch` 可选；⑤ 新增 `ProcessDefinition`/`StepExecution`/`ExternalInteraction`；⑥ 保留六模块、不引入 BPMN、不新增独立流程微服务；⑦ 持久化状态机 + 本地短事务 + Outbox + 消费幂等 + 乐观锁/短分布式锁；⑧ file/approval 经防腐层接入、外部 DTO 不进 domain/application 核心模型；⑨ 统一复用 demo-shared 的 id/event/cache/lock/web/logging/permission/client，不重复建底座 |
| §2 背景 9 类问题 L37-47 | 定义无版本、无步骤执行记录、无外部协作记录、任意跳转无状态机、事务/副作用边界不清、Spring 事件与 EventBus 分叉、重复推进/回调/重启无系统化、业务类型/上下文字段封闭、表单解析/材料/批次统计无闭环 |
| §3.2 最低要求 L74-76 | 「持久化状态机、定义版本、步骤执行记录、外部协作记录、可靠事件、幂等消费、短事务和恢复机制」 |
| §3.3 上下文职责 L80-86 | kernel 负责流程定义引用/实例/步骤执行/外部协作/通用 API/可靠事件/并发幂等/审计追踪；**不应负责**业务明细、文件内容、审批节点规则、具体业务校验算法 |
| §4.1 目标 9 条 L94-102 | 新业务只加定义 + SPI 不改 kernel、重启可恢复、并发只一次跃迁、重复事件不重复推进、文件/审批任务可关联可追踪可恢复可对账、定义升级不影响在途、事务边界明确、通用能力统一复用 shared、保留六模块不建 BPMN |
| §21.5 最终验收 8 条 L1065-1072 | 上述目标的可执行验收口径 |

> **范围注记**：§1 头部 L9 明确原设计范围是 `business-core-kernel` / `demo-shared` / `file-service` / `approval-service`，`annuity-service`「仅作为早期示例，不作为模型来源或兼容约束」。**auth-service 不在原范围内**，它是后续 P3-1 / 组织目录立项时为权限与角色契约引入的（见 §3.4）。

### 2.2 当前实现盘点（六层，作为库被宿主引入）

| 层 | 核心职责与代表类 |
|---|---|
| `business-core-types` | 强类型 ID（`ApplicationId`/`BatchId`/`FormId`/`StepExecutionId`/`ExternalInteractionId`/`UserNo`/`PlanNo` 等，`@IdDefinition` 声明 ULID/SEGMENT 策略） |
| `business-core-domain` | 纯领域层（Lombok 外无框架注解）：`BusinessApplication` 流程实例聚合根、`BusinessForm`/`BusinessBatch` 可选聚合、`ProcessDefinition`/`StepDefinition`/`StepTransition`、`StepExecution`、`ExternalInteraction`、状态机、`DomainEvent`、SPI 接口（`Repository`/`Gateway`/`StepActionHandler`/`StepExtensionAction`/`BusinessFactExtractor`）、错误码 |
| `business-core-api` | `@HttpExchange` 协议、CQE、DTO（`BusinessFormApi`/`BusinessApplicationApi`/`BusinessBatchApi`/`BusinessProgressApi`/`MaterialApi`）——**只定义协议，无实现逻辑** |
| `business-core-application` | 编排：`BusinessFormAppService`（confirmUpload → triggerParsing → handleParsingResult）、`FlowOrchestrationService.advanceStep/advanceApplication/completeWaitingStep`、`StepPipelineExecutor`、`ProcessDefinitionPublishService`、`CallbackCorrelationService`、`ExternalInteractionRecoveryService`、批量基类（Cursor/Streaming/JsonStream）、监听器（ApplicationSpawned/StepAutoAdvance/FileParsed/ApprovalResult/OutboundFileGenerated） |
| `business-core-adapter` | Controller 实现 API 接口 + MapStruct Converter + `CoreKernelAutoConfiguration` + `KernelProductionReadinessValidator` + `@ConditionalOnProperty(core.kernel.controllers.enabled)` |
| `business-core-infrastructure` | Repository 实现、DO/Mapper/Converter、`JdbcEventStore`/`JdbcEventReceiptGateway`、三套 DDL（PG/MySQL/H2）、`SpelConditionEvaluationGateway`/Aviator/QLExpress、`FileServiceIntegrationGateway`/`ApprovalServiceIntegrationGateway` |

无 `starter` 层——kernel 作为库由宿主（当前是 annuity 示例服务）装配，符合 §1 决策 6「不新增独立流程微服务」。

### 2.3 逐条对照（§21.5 最终验收 8 条，附 §23.46 核对表证据）

| # | 验收条 | 现状 | 证据 | 判定 |
|---|---|---|---|---|
| 1 | 新业务只加定义 + SPI，不改 kernel | 版本化定义存储（`CompositeProcessDefinitionGateway` 读历史版本）+ 发布期校验（handler/条件/扩展 beanName）+ SPI 注册 | `FinalAcceptanceContractTest.newBusiness_registersDefinitionAndSpiOnly`、`ProcessDefinitionPublishServiceTest`（§23.70 补 beanName 校验） | ✅ |
| 2 | 服务重启后流程可恢复 | `PENDING_DISPATCH` 重启恢复、`DISPATCHING` 滞留回收 UNKNOWN + 对账、`@Scheduled` 兜底 | `ExternalCollaborationAcceptanceTest.restart_*`、`ExternalInteractionRecoveryJobTest` | ✅ |
| 3 | 并发推进和重复回调不造成重复跃迁 | `requestNo` 幂等 + `EventReceiptGateway.tryConsume` + `OUT_OF_ORDER` 拒绝 + 乐观锁 CAS + 短 `DistributedLock`（弱依赖） | `FinalAcceptanceContractTest.repeatedCallback_*`、`ConcurrentAdvanceAcceptanceTest`、`OptimisticLockConcurrencyTest`、§23.52 唯一监听入口 | ✅（真多进程 Redis 待环境，见 §2.4-③） |
| 4 | 文件/审批任务可追踪、可重试、可对账 | `ExternalInteraction`（`requestNo`/`externalTaskId`/`resultRef`）+ `StepExecution` 每次 attempt 留痕 + PARTIAL 统计入 `resultRef`（批次 64） | `GatewayRequestNoContractTest`、`FileGatewayTaskIdContractTest`、`PartialParseStatistics` | ✅ |
| 5 | 流程定义升级不影响在途实例 | `definitionVersion` 持久化 + 历史版本读取 | `FinalAcceptanceContractTest.definitionUpgrade_*` | ✅ |
| 6 | 状态、执行历史和事件可审计 | 步骤执行/协作/Outbox/回执四张表 + MDC 流程标识（写入正确） | `FinalAcceptanceContractTest.stepExecutionHistory_isAuditable`、§23.44 | ✅（**日志 pattern 不输出 MDC**，见 §2.4-④ / 收口文档 §4） |
| 7 | 通用技术能力全部通过 shared starter 提供 | pom 显式依赖已由批次 38-1 收口；`ArchitectureInvariantTest` 不变量 8 禁止业务自建 ID/缓存底座 | `KernelProductionReadinessValidatorTest`、§23.43 | ⚠️ 名义达成，但 **lock starter 无宿主引入、kernel cache 引入未用**（§4.3） |
| 8 | 端到端测试不预置实例、不禁用自动推进 | 独立 `AutoAdvanceEnabledEndToEndTest`（不 mock 监听器、独立 H2、正常入口建单、自动级联至 USER_TASK） | §23.51 | ✅ |

### 2.4 §1 决策 9 条 / §4.1 目标 9 条的综合判定与偏差

**已达成**：决策 1–6（三聚合重定位、不新增 ProcessInstance、可选聚合、三类核心概念、六模块/BPMN 边界）、决策 8（防腐层 + ArchUnit 机器化）、目标 1/2/3/4/5/6/7/9 均有对应实现与验收测试；§2 的 9 类生产问题均有对应能力（版本化定义、步骤执行记录、外部协作记录、宏观状态机、事务边界、Repository→EventBus 统一、幂等/恢复、业务类型开放、表单解析闭环）。

**如实标注的偏差（不冒充已覆盖）**：

1. **决策 7 / 9 的 lock 部分**：可靠性模型采用「乐观锁 + 短分布式锁」双层，但 `DistributedLock` 是 `ObjectProvider.getIfAvailable()` 弱依赖（`FlowOrchestrationService.java:86,138`），且**没有任何宿主 pom 引入 `shared-lock-starter`**（全仓仅父 pom `pom.xml:276` 的 dependencyManagement 声明）。生产默认走「无锁直通」，多实例并发只靠 DB 乐观锁兜底 —— 不变量仍成立，但与设计描述的「短分布式锁」有装配落差。详见 §4.3 / 收口文档 §6 #1。
2. **§21.4 架构不变量部分机器化**：4 条已 ArchUnit 固化；「新增/修改写操作入口必须 CQE」是**向前约束**（存量约 30 个裸参方法不回溯）、types 层仍含非 ID 值对象、Adapter Converter 构造值对象属必要行为。主设计 §23.46 已如实标注，属规格修订后的既定决策，不是缺陷。
3. **真多进程 Redis + 真实 DB 集成测试待环境**（§21.5 表 1688 行已标注），见收口文档 §5。
4. **MDC 值写入正确但日志不输出**：kernel 写入 4 个流程键，全仓仅 integration-service 定义了 `%X{}` pattern 且不含这 4 个键，见收口文档 §4。
5. **`Map<String,Object>` 强类型化降级关闭**、annuity 进程内态 wontfix —— P2 专项既定决策，不改代码。

> **判定**：kernel 的**领域模型、编排语义、可靠性能力与验收覆盖**满足最初诉求；剩余偏差集中在「shared 底座的宿主装配」与「部署配置」，不在 kernel 代码本身。

---

## 3. approval / auth / file 与 kernel 交互的架构合规性

### 3.1 依赖矩阵（pom 逐项核实）

| 服务/模块 | 依赖的跨服务 API | 方向 | 规则 01 判定 |
|---|---|---|---|
| `business-core-infrastructure` | `file-api`、`approval-api`（`pom.xml:22,26`） | kernel → file/approval（出站） | ✅ 只依赖 API 模块 |
| `business-core-application` | `file-api`、`approval-api`（`pom.xml:33,37`，供通用 Handler/事件 DTO） | kernel → 外部契约 | ✅ |
| `business-core-adapter` | `file-api`、`business-core-api`（`pom.xml:17,21`） | 入站 | ✅ |
| `approval-infrastructure` | `auth-api`（`pom.xml:26`，批次 60） | approval → auth | ✅ |
| `annuity-api` | `business-core-api`（`pom.xml:20`） | 业务服务 → kernel 契约 | ✅ |
| `bff-framework` / `intranet-bff-*` | `business-core-api` / `approval-api` / `auth-api` | 渠道层 → API | ✅ |
| `channel-gateway` | `auth-api`（`pom.xml:31`） | 网关 → API | ✅ |
| `auth-domain` / `auth-api` / `approval-domain` / `file-domain` | `business-core-types`（共享 ID 原语，非 API 模块） | 共享原语 | ✅ types 层允许（规则 01 明确 types 可被共享） |
| `file-service` 全模块 | 不依赖 kernel | — | ✅ 无反向依赖 |
| `auth-service` 全模块 | 不依赖 kernel 的 api/application/domain | — | ✅ 只共享 types |

`KernelLayerArchitectureTest.kernelDependsOnBusinessServicesOnlyThroughApiAndTypes` 已把「跨服务只经 api + types、禁止 domain/application/adapter/infrastructure」机器化；`KernelForwardConstraintTest` 另禁 Adapter 构造领域实体、types 层放聚合根/实体/领域服务。

### 3.2 交互链路（4 条）

1. **kernel → file/approval（出站，防腐层）**：domain 层 `FileIntegrationGateway` / `ApprovalIntegrationGateway` 是 SPI；infrastructure 层 `FileServiceIntegrationGateway`（`business-core-infrastructure/.../gateway/FileServiceIntegrationGateway.java:62`，`@ConditionalOnProperty("core.integration.file.base-url")`）注入 `FileAccessApi`/`ParsedDataApi`/`FileTaskApi`/`OutboundApi`；`ApprovalServiceIntegrationGateway.java:38` 注入 `ApprovalFlowApi`/`ApprovalInstanceApi`（`matchApprovalFlow` → `startApproval` → `queryApprovalStatus`）。**外部 API DTO 只出现在 infrastructure 实现**，未进入 domain/application 核心模型 —— 符合 §1 决策 8。
2. **file/approval → kernel（入站事件）**：file `FileParsedEvent`/`OutboundFileGeneratedEvent`、approval `ApprovalInstanceCreated/Approved/Rejected/Withdrawn` 转成 `*.api.event.*` DTO，经 `FileParsedEventListener` / `OutboundFileGeneratedEventListener` / `ApprovalResultEventListener` 消费；**每个入口统一经 `EventReceiptGateway.tryConsume` + `CallbackCorrelationService` 关联**，批次 45 消除了重复出站监听器（`EventListenerUniquenessTest` 机器化）。
3. **requestNo 幂等契约**：kernel 生成 `SE-<stepExecutionId>`（`BusinessMetaContext.REQUEST_NO_FACT` 同源）→ 随 `StartApprovalRequest` / file 任务请求下发 → 两侧聚合/事件/Converter/DDL 回传真实 `requestNo` → `getByRequestNo` 查询 + `CallbackCorrelationService` 按 requestNo 关联协作、按 `externalTaskId` 查外部系统。`ApprovalRequestNoContractTest` / `GatewayRequestNoContractTest` / `FileGatewayTaskIdContractTest` 锁定形状。
4. **approval → auth（角色/组织目录）**：`RoleUserGatewayImpl` 注入 `RoleUserQueryApi`（`/by-roles`）、`PlanDirectoryApi`（`/members`、`/parent`），实现 `SPECIFIED_ROLE` / `SAME_PLAN` / `LEVEL_UP` 节点解析；auth 侧 `PlanDirectoryController` / `RoleUserQueryController` 以 `@RequirePermission(AUTH_ASSIGNMENT/VIEW/PLATFORM)` 保护。approval 只依赖 `auth-api`，未碰 auth 实现层。

### 3.3 权限与 Web 契约的公共链路（非点对点交互）

kernel adapter 与四个服务的 Controller 统一用 `com.example.shared.web.core.api.ApiResult` 包装返回、`@RequirePermission` 标注、`GlobalExceptionHandler`（`shared-web-core`）映射异常 → HTTP 状态；`shared-permission-starter` 的 `PermissionRegistrationRunner` / `PermissionCheckApi` 经 httpexchange 调 auth-service 做实时鉴权与权限点上报。即 **kernel/approval/file 不直接依赖 auth 实现，权限能力经 shared 底座间接接入**，符合 §1 决策 9。

### 3.4 偏差与风险（如实标注）

| # | 事项 | 判定 |
|---|---|---|
| 1 | **auth-service 不在 §1 原设计范围** | 属范围演进而非违规：原范围只含 kernel/demo-shared/file/approval；auth 因 P3-1 权限与 58–62 角色契约、66–69 组织目录立项进入交互图。交互仍严格只经 `auth-api`，符合规则 01 |
| 2 | **`startApproval` 发起人身份** | `StartApprovalRequest.initiator` 由 kernel **服务间调用**传入（`application.createdBy()`），不是人工会话；批次 65（§23.72）判定「不做」会话账号覆盖，否则无会话 header 的服务间调用会被 `requireSessionAccount()` fail-closed 拒绝、破坏 E2E。属已决策的设计选择 |
| 3 | **httpexchange 客户端配置只在 `application-local.yml`，主配置无条件 `include: local`** | ⚠️ 真实配置缺陷：移除 `include: local`（对齐批次 47 对 annuity 的治理）会导致客户端 Bean 不装配、服务启动失败；保留则生产沿用本地占位配置。详见收口文档 §3.3 / §6 #2 |
| 4 | **出站事件生产消费端** | 批次 45 已统一唯一 `@EventListener` 入口；RocketMQ 生产消费者若在其它仓库直调 `handleXxx`，仍需按「强制经 `tryConsume`」约束（P0-3 已记为接入时检查项） |
| 5 | **端到端 HTTP 未验证** | 单测不启动多服务，annuity E2E 不启动 approval/auth；属部署期检查项（收口文档 §3.5） |

> **结论**：四个服务与 kernel 的交互方向、依赖层次、DTO 边界、幂等契约均符合架构设计，且核心不变量已机器化；剩余是部署配置与运行时验证，不是架构违约。

---

## 4. shared starter 能力使用矩阵

### 4.1 能力清单（能力接口在 shared-lib/shared-core，自动配置在 shared-starter）

| starter | 核心能力 | 代表类 |
|---|---|---|
| `shared-id-starter` | `IdService.nextId(...)`、`@IdDefinition`（ULID / UUID_V7 / SEGMENT 号段）、`t_id_generator` DDL | `IdAutoConfiguration`、`IdServiceConfiguration`、`SegmentIdConfiguration`、`StatelessIdConfiguration` |
| `shared-event-starter` | `EventBus`、Outbox `JdbcEventStore`（自带 `JavaTimeModule`）、`EventReceiptGateway`、`AFTER_COMMIT` 派发 | shared-event-core / starter |
| `shared-cache-starter` | `ICacheTemplate`（JetCache 2.8 两级缓存、防击穿、广播失效）、`shared.cache.rules` | `JetCacheSharedCacheTemplate`、`SharedCacheAutoConfiguration`；接口 `shared-cache-core/ICacheTemplate.java:21` |
| `shared-lock-starter` | `DistributedLock.tryLock(key, wait, lease)` / `LockHandle`、`Lock4jDistributedLock`（lock4j + Lettuce） | `DistributedLockAutoConfiguration:56-59`、`Lock4jDistributedLock`；接口 `shared-lib/shared-lock/DistributedLock.java:30` |
| `shared-web-starter` | `ApiResult`、`GlobalExceptionHandler`、`BizContextPropagationFilter`（traceId/MDC）、`BizContextAspect`、链路拦截器 | `shared-web-core/handler/GlobalExceptionHandler.java`、`trace/filter/BizContextPropagationFilter.java`、`WebAutoConfiguration`、`BizTraceAutoConfiguration` |
| `shared-logging-starter` | 脱敏、异步管道、`SanitizationProperties` | `shared-logging-starter/application.yml` |
| `shared-permission-starter` | `@RequirePermission`、`@DataScope`、权限点上报/续期 | `PermissionRegistrationRunner`、`PermissionRegistrationRefresher`、`PermissionAutoConfiguration` |
| `shared-client-starter` | Retrofit + Logbook HTTP 客户端 | shared-client-core / starter |

### 4.2 使用矩阵（四服务：pom 依赖 / 代码使用 / 判定）

| starter | kernel | approval-service | auth-service | file-service |
|---|---|---|---|---|
| **id** | ✅ infra + application 依赖；`IdService.nextId(BatchId/ApplicationId/StepExecutionId)`、`@IdDefinition` 齐全（`FlowOrchestrationService:31,84`、`BusinessBatchAppService:12,49,60`、types 各 ID） | ✅ `IdServiceApprovalIdGenerator` + types `@IdDefinition`（`approval-infrastructure/pom.xml:34`） | ⚠️ infra pom 引入 `shared-id-starter`，但代码未见 `IdService` 直接调用（ID 多由 `@IdDefinition` + 共享 types 表达） | ✅ 多个 UseCase 用 `IdService`（`ParseFileUseCase`/`UploadFileUseCase`/`ApplyUploadTokenUseCase`/`GenerateOutboundFileUseCase`/`StoreFileUseCase`），types `@IdDefinition` |
| **event** | ✅ infra 依赖 + `JdbcEventStore`/`JdbcEventReceiptGateway`/`EventBus` 全链路 | ✅ application 依赖（`approval-application/pom.xml:26`） | ✅ infra 依赖（`auth-infrastructure/pom.xml:38`） | ✅ application 依赖（`file-application/pom.xml:27`） |
| **cache** | ⚠️ `business-core-adapter/pom.xml:60` 引入 `shared-cache-starter`，但 **kernel 全仓代码零 `ICacheTemplate` 使用**（已 grep 确认） | ❌ 未引入、未使用 | ✅ `RedisPermissionCacheStore` 构造器强依赖 `ICacheTemplate`（`auth-infrastructure/.../permission/cache/RedisPermissionCacheStore.java:38-40`），主配置 `shared.cache.rules.auth-perm-cache` | ✅ `TemplateConfigRepositoryImpl` 用 `ObjectProvider<ICacheTemplate>` 弱依赖（`file-infrastructure/.../repository/TemplateConfigRepositoryImpl.java:34,52,62`），`file-infrastructure/pom.xml:31` 引入 |
| **lock** | ⚠️ 只依赖 `shared-lock` **接口**（`business-core-application/pom.xml:24-28`），`FlowOrchestrationService` 用 `ObjectProvider.getIfAvailable()` 弱依赖；**无宿主引入 starter** | ❌ 未引入 | ❌ 未引入 | ❌ 未引入 |
| **web** | ✅ adapter 依赖 + `ApiResult` 在 5 个 Controller 与 api 接口广泛使用 + `BizContextPropagationFilter` 提供 traceId | ✅ starter 依赖（`approval-starter/pom.xml:27`）+ `ApiResult` | ✅ starter 依赖（`auth-starter/pom.xml:27`）+ `ApiResult` | ✅ starter 依赖（`file-starter/pom.xml:25`）+ `ApiResult` |
| **logging** | ✅ adapter + infra 依赖，脱敏配置齐备 | ✅ application + infra 依赖 | ✅ infra 依赖 | ✅ application + infra 依赖 |
| **permission** | ✅ adapter 依赖，5 个 Controller 全标注 `@RequirePermission` | ✅ adapter 依赖 + 标注 + `AccountIdResolver` 会话覆盖 | ✅ adapter 依赖 + `PermissionScanner` | ✅ adapter 依赖 + 标注 |
| **client** | ✅ infra 依赖（`business-core-infrastructure/pom.xml:30`，供文件流式下载/HTTP 客户端） | ❌ 未引入 | ❌ 未引入 | ❌ 未引入 |

> 补充：`ArchitectureInvariantTest` 不变量 8（`arch-tests/.../ArchitectureInvariantTest.java:168,199`）已机器化「业务不得绕过 `IdService` / `ICacheTemplate` 门面自建底座」，与 §1 决策 9 对齐。

### 4.3 关键发现（按严重度）

1. **【高】`shared-lock-starter` 装配缺口**：全仓 `<artifactId>shared-lock-starter</artifactId>` 只出现在父 pom `dependencyManagement`（`pom.xml:276`）与 starter 自身 pom；**kernel、approval、auth、file、annuity 的 dependencies 均未声明**。因此生产上下文无 `DistributedLock` Bean，`FlowOrchestrationService.withApplicationLock` 走「无锁直通」（`FlowOrchestrationServiceTest.advanceStep_noLockWhenUnavailable` 覆盖该路径），多实例并发只靠 `version` 乐观锁。设计把它表述为「弱依赖可选注入」（§9.3 第一层），但 §1 决策 7 与 §21.5 第 3/7 条隐含需要宿主装配；**部署前必须在宿主 pom 显式引入并配 Redis**（收口文档 §6 #1）。
2. **【中】kernel `shared-cache-starter` 引入未用**：`business-core-adapter/pom.xml:60` 有依赖但代码零 `ICacheTemplate` 命中。若用途是「随 kernel 传递 jetcache 配置给宿主」，应在 pom 注释说明；否则属冗余依赖，建议确认后移除或补上真实用途（如流程定义/配置缓存）。
3. **【中】auth `shared-id-starter` 引入但代码未见 `IdService` 直接调用**：其 ID 生成可能全部由 `@IdDefinition` + 共享 `business-core-types` 完成，需确认是「预留」还是「漏接线」；不影响正确性，但影响矩阵准确性。
4. **【中】MDC 输出缺口**：`shared-web-starter` 负责写入 `traceId`，kernel 负责写入 4 个流程键，但**输出依赖宿主 logback pattern**，全仓仅 integration-service 有 `%X{}`，见收口文档 §4。
5. **【低】`shared-client-starter` 只 kernel 使用**：其余服务用 `httpexchange` 代理即可，符合「按需要引入」，非缺陷。

### 4.4 建议

- 宿主（至少承载 kernel 的服务）显式加 `shared-lock-starter`，并补一条启动契约测试「`DistributedLock` Bean 存在」（仿 `KernelProductionReadinessValidatorTest`），把 §21.5 第 7 条从「名义达成」变成「运行时达成」。
- 明确 kernel `shared-cache-starter` 去留：补用途说明或移除，避免「引入未用」被误读为已使用。
- 统一日志 pattern（收口文档 §4.3），让流程 MDC 键真正进入日志。
- 上述均为**部署/装配层**动作，不需要改 kernel 领域模型。

---

## 5. 总体结论与后续建议

1. **最初诉求已达成**：kernel 已被重定位为「持久化长流程控制内核」，三阶段编排、SPI 扩展、批量基类、防腐层网关、可靠事件/Outbox/回执、版本化定义、乐观锁/幂等/恢复、requestNo 对账均已落地并由 §21.1–§21.5 的验收测试覆盖；阶段 0–5 全部收口，§21 核对表无 ❌。
2. **架构合规性成立**：跨服务只依赖 API 模块（ArchUnit 机器化）、外部 DTO 止于 infrastructure/入站适配器、事件入口唯一且回执幂等、权限经 shared 底座间接接入；auth 是范围演进而非违约。
3. **剩余问题不在 kernel 代码，而在装配与部署**：`shared-lock-starter` 无宿主引入（高）、kernel cache 引入未用（中）、`httpexchange` 配置与 `include: local` 冲突（高）、MDC 日志 pattern 缺口（中）、真 Redis 集成与端到端 HTTP 待环境。
4. **建议后续顺序**（均需先与用户确认目标环境策略，不擅改）：① 宿主装配 `shared-lock-starter` + Redis；② 迁移 `httpexchange` 配置到主配置/Nacos 并移除 `include: local`，补 prod profile 隔离测试；③ 统一日志 pattern；④ 具备环境后启用真 Redis 并发集成测试与端到端 HTTP 验证。

> 本文与 `2026-11-15-non-code-items-closure.md` 共同构成本轮复核的落盘成果；分析结论以当前代码与配置为准，后续如有变更须回填。
