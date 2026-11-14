# 非代码项收口与部署联调静态核实

> 生成日期：2026-11-15
> 依据：`2026-11-14-remaining-work-inventory.md` §3/§4、`2026-11-08-business-core-kernel-production-design.md` §23.34/§23.44/§23.46/§23.66/§23.67、当前代码与配置逐项核实
> 性质：静态核实 + 文档纠偏（**零生产代码改动**）
> 分支：`refactor/business-core-kernel-production`
> 配套：`2026-11-15-architecture-review.md`（架构复核总报告）

---

## 1. 结论先行

| 项 | 处置 | 结论 |
|---|---|---|
| 清单 §3 文档欠账（3 条） | ✅ 已核实并修正 | 2 条确为过时并补注/改表；1 条经核实**已不成立**（主设计 §23.46 末行早已写明「已采纳并完成 49/50」），清单对该条的描述本身过时，已同步纠偏 |
| 优先级清单过时描述（6 处） | ✅ 已修正 | 状态行、推进状态行、P2 专项方案说明、P2 前置条件列、P3-1 处置列、§4 依赖关系 |
| **#12 权限自注册 + 端到端 HTTP** | ✅ 静态核实完成 | 权限项自注册机制**成立**；但 `@HttpExchange` 客户端装配存在**生产 profile 配置缺陷**（§3.3）；运行时 HTTP 验证需部署环境，已转成 §6 检查清单 |
| **#13 MDC 日志 pattern** | ✅ 静态核实完成 | **发现真实缺口**：全仓仅 `integration-service-starter` 定义了 `%X{}` pattern，kernel 写入的 4 个流程 MDC 键在所有宿主日志中均不输出（§4） |
| **#4 真 Redis + 多 JVM 集成** | ✅ 环境判定完成 | 需 Docker + Redis + 多 JVM；已落盘测试用例设计与启用方式，仓库内不可执行（§5） |
| 新发现：`shared-lock-starter` 无宿主引入 | ⚠️ 落盘为部署检查项 | 四个业务服务与 annuity 的 pom 均未显式依赖它，生产无 `DistributedLock` bean，kernel 走「无锁直通」、仅靠乐观锁兜底（§6、架构复核报告 §4.3） |
| 新发现：`httpexchange` 配置只在 `application-local.yml` | ⚠️ 落盘为部署检查项 | 主 `application.yml` 无条件 `include: local`，与批次 47 对 annuity 的治理结论冲突（§3.3） |

> **口径**：#12/#13 在清单里被定义为「部署联调检查项」——其仓库内可完成部分就是**静态核实 + 落盘结论 + 可执行检查清单**；真 HTTP 端到端与日志输出验证必须运行多服务 + Nacos/Redis，属部署期动作，不能在本仓库内伪造通过。本文把它从「待验证」改为「静态收口 + 明确的部署前置动作」。

---

## 2. 文档欠账纠偏（已完成，零代码改动）

| # | 位置 | 原描述 | 核实结论 | 处置 |
|---|---|---|---|---|
| 1 | `2026-11-08-...production-design.md` §23.34 已知边界②（约 1521 行）、§23.44 口径引用 | `reconcileAllActive()` 用 `loadAll()` 全表加载，生产数据量大时可改按状态索引 | **已过时**：批次 46（§23.53）已改为 `BusinessBatchRepositoryImpl.findByStatuses(CREATED, PROCESSING)` 走 `idx_business_batch_status`，`BusinessBatchAppService.reconcileAllActive()` 不再全表扫描 | 在 §23.34 该条补注「已由批次 46 完成」，保留历史快照原文 |
| 2 | `2026-11-14-remaining-work-priority.md` P2 表格「前置条件」列、第 57 行、状态行、推进状态行、P3-1 处置列、§4 依赖关系 | 「四项均待用户确认，确认前不开工」「先定模型/先做规格决策」「角色审批与 startApproval 仍为遗留」「P2-* 均需先出专项方案并确认」 | **已过时**：P2 专项方案已采纳且 49/50/51/52 完成；角色审批立项 58–62 收口、组织目录立项 66–69 收口；`startApproval` 发起人身份由批次 65 判定「不做」（§23.72）；P0/P1 全清 | 逐处改为「已决策 / 已收口 / 已判定不做」，推进状态行补到批次 69 |
| 3 | 主设计 §23.46 末行 | 清单 §3 称「§23.46 末行称『P2 专项方案待用户确认』」 | **不成立**：§23.46 末行（约 1704 行）现写的是「P2 专项方案……已采纳并完成 49/50」，并指向剩余清单口径；清单 §3 该行描述本身是更早快照 | 清单 §3 该行改为「✅ 已核实：无需修，主设计早已同步」 |
| 4 | 主设计 §23.1 阶段进度总览（约 1096–1099 行） | 阶段 1/2/3/4 状态列写「进行中」，与阶段 5「完成」及 §23.46「阶段 0–5 全部收口」矛盾 | 属逐阶段收口时的历史快照 | 在表格后补一行口径注（不改历史行），与清单 §0 口径一致 |
| 5 | 清单 §3 表格 + §4 优先级 + 结论句 | 仍把 #12/#13/§3 列为「仍待」 | 本次已收口 | 同步为「✅ 已静态收口（见本文）」 |

---

## 3. #12 权限项运行时自注册与端到端 HTTP（静态核实）

### 3.1 权限项自注册：机制成立 ✅

| 环节 | 证据 | 结论 |
|---|---|---|
| auth 侧本地扫描 | `auth-infrastructure/.../permission/PermissionScanner.java:31-35` 是 `ApplicationRunner`，启动时调 `PermissionScannerService.scanLocal(handlerMapping, ...)` 扫描本地 Controller 的 `@RequirePermission` 并 upsert 到 `t_auth_permission_item` | `AUTH_ASSIGNMENT/VIEW` 会随 auth-service 启动自动注册 |
| 权限标注存在 | `auth-adapter/.../RoleUserQueryController.java:31`、`PlanDirectoryController.java:30,37` 均为 `@RequirePermission(business="AUTH_ASSIGNMENT", action="VIEW", category=PLATFORM)` | 批次 59/67 新增的两个端点都在扫描范围内 |
| 其它服务上报 | `shared-permission-starter/.../PermissionRegistrationRunner.java:42-43` 以 `@ConditionalOnBean(PermissionRegistrationPort.class)` + `@ConditionalOnExpression("'${spring.application.name}' != 'auth-service'")` 装配，扫描本服务 `@RequirePermission` 并经 `PermissionRegistrationApi` 上报 auth；`PermissionRegistrationRefresher` 定时续期 | 非 auth 服务的权限点上报链路完整，fail-soft 不阻断启动 |

> 结论：清单 #12 前半句「需确认 auth 数据侧已有对应权限项」在**代码机制层面成立**，无需额外数据初始化脚本；部署时只需确认 auth 启动时 `PermissionScanner` 未被条件排除、DB 可写。

### 3.2 `@HttpExchange` 客户端装配：代码侧成立 ✅

- `approval-infrastructure/pom.xml:26`、`auth-api` 契约包 `com.example.auth.api` 已被 `RoleUserGatewayImpl`（批次 60/68/69）、`PlanDirectoryController` 依赖。
- `approval-starter / file-starter / annuity-starter / integration-service-starter` 的 `application-local.yml` 均配置 `httpexchange.clients.com.example.auth.api.url: lb://auth-service`（如 `approval-starter/src/main/resources/application-local.yml:17-20`），`httpexchange-spring-boot-autoconfigure` 会为该包下的 `@HttpExchange` 接口创建代理 Bean。
- `intranet-bff-starter/application-local.yml:13-15` 另配 `com.example.approval.api → lb://approval-service` 与 `com.example.auth.api → lb://auth-service`。

### 3.3 缺陷：客户端装配依赖 local profile，与主配置无条件 `include: local` 冲突 ⚠️

| 事实 | 位置 |
|---|---|
| `httpexchange` 客户端映射**只写在 `application-local.yml`**，主 `application.yml` 无此段 | approval / file / annuity / integration 四个 starter |
| 主 `application.yml` **无条件** `spring.profiles.include: local`（或 `active: local`） | `approval-starter/application.yml:25-26`、`file-starter/application.yml:25-26`、`auth-starter/application.yml:25-26`、`integration-service-starter/application.yml:14-15`、两个 BFF `:12-13` |
| 对照：批次 47（§23.54）已把 **annuity** 主配置的 `include: local` 移除，并补 `ProductionProfileIsolationTest`，理由是 prod 也含 local 会让 `KernelProductionReadinessValidator` 跳过 fail-fast、会话验签密钥用 `dev-` 占位静默降级 | 主设计 §23.54 |

**两个方向都有问题**：

1. 若按批次 47 的治理把其它服务主配置的 `include: local` 也移除 → `httpexchange` 客户端映射不加载 → `PermissionRegistrationPort` / `RoleUserQueryApi` / `PlanDirectoryApi` 代理 Bean 不装配 → `PermissionRegistrationRunner` 不触发、`RoleUserGatewayImpl` 构造注入失败 → **服务启动失败或审批人 fail-closed 全空**。
2. 若保留 `include: local` → 生产沿用本地 DB 账号/Nacos 地址/`dev-session-signature-key-2026` 等占位配置，正是批次 47 在 annuity 修掉的问题。

**修复方向（未擅自改，需确认环境配置策略）**：把 `httpexchange.clients` 与 `permission.session.signature-key` 的**结构**移到各服务主 `application.yml`（或用 Nacos 配置中心下发），把 DB/Nacos 地址与密钥改为环境变量占位；再移除主配置的 `include: local`，并仿 `ProductionProfileIsolationTest` 为每个服务补一条「prod profile 不加载 local 且客户端 bean 仍装配」的启动契约测试。此项影响 6+ 服务的生产启动，属需方案确认的配置变更，本轮只落盘。

### 3.4 会话 header 透传：代码侧成立 ✅

`intranet-bff-starter/application.yml` 的 `trace.context.mapping` 已配置 `AccountId: X-Account-Id`、`AccountSig: X-Account-Sig`（批次 56/57 同步），approval-adapter 的 `AccountIdResolver.requireSessionAccount()` 以签名会话账号覆盖请求体身份并 fail-closed。运行时仍需网关签发签名 + BFF 透传链路联调。

### 3.5 运行时验证步骤（部署期，仓库内不可完成）

1. 启动 Nacos + auth + file + approval + gateway；确认各服务 `PermissionScanner` / `PermissionRegistrationRunner` 日志有上报成功记录。
2. 查 `t_auth_permission_item` 存在 `AUTH_ASSIGNMENT/VIEW/PLATFORM` 两条（`/role-users/by-roles`、`/plan-directory/members`、`/plan-directory/parent`）。
3. 从 approval 侧发一次真实 HTTP 调 `PlanDirectoryApi.listMembers`，确认代理 Bean 装配、鉴权通过、响应 `ApiResult.success`。
4. 用带签名 `X-Account-Id` 的请求走一次审批操作，确认服务以会话账号为准而非请求体字段。

---

## 4. #13 `shared-logging-starter` 脱敏/异步管道与 MDC pattern（静态核实）

### 4.1 现状

- kernel 侧写入正确：`FlowOrchestrationService.withApplicationLock` 在推进期间写入 `applicationId` / `stepExecutionId` / `requestNo` / `businessType`，`finally` 恢复现场；`traceId` 由 `shared-web-starter` 的 `BizContextPropagationFilter` 注入（主设计 §23.44，1617 行）。有 2 项单测断言 4 键齐全且结束后清理。
- 脱敏配置在各服务 `application-local.yml` 的 `shared.logging.obfuscate`（最小满足 `@NotNull/@NotEmpty`），`global.enable: false`。

### 4.2 真实缺口 ⚠️

全仓 `%X{}`（MDC 输出）**只出现在 `integration-service-starter/src/main/resources/logback-spring.xml:8-14`**，且输出的键是 `BizId/BatchId/OrderId/UserId`（来自 `trace.context.mapping`），**不含 kernel 的 4 个流程键**，也不含 `traceId`。

其余服务（kernel 作为库无 starter 日志配置；approval / auth / file / annuity 的 `application.yml` 只有 `logging.logback.rollingpolicy`）均使用 Spring Boot 默认 console/file pattern，默认 pattern 不输出 MDC。因此：

> kernel 写入的 `applicationId` / `stepExecutionId` / `requestNo` / `businessType` 在生产日志中**不可见**，§14「审计追踪可关联」只体现在 DB 表（`t_business_step_execution` / `t_business_external_interaction`）与事件载荷，不体现在日志行。

### 4.3 修复建议（未擅自改，需确认日志规范）

- 在 `shared-logging-starter` 提供一段公共 logback 片段（含 `traceId` + 4 个流程键 + `BizId/BatchId/OrderId/UserId`），各服务 `logback-spring.xml` include；或统一在各服务主配置声明 `logging.pattern.console/file`。
- 为 `integration-service` 现有 pattern 补 `traceId` 与 4 个流程键，避免两套口径。
- 补一条部署期验证：触发一次流程推进，grep 日志行应同时出现 `traceId`、`applicationId`、`stepExecutionId`、`requestNo`。

---

## 5. #4 真 Redis + 多 JVM + 真实 DB 并发集成测试（环境判定）

### 5.1 现状

- `ConcurrentAdvanceAcceptanceTest`（批次 43-1，进程内 `InProcessDistributedLock` + 状态化仓储 + 双线程）与 `OptimisticLockConcurrencyTest`（批次 43-2，fake mapper 模拟 CAS）覆盖的是**确定性并发不变量**，不是真实分布式环境（主设计 §23.50 / §21.5 表 1688 行已如实标注）。
- `shared-lock-starter` 的 `DistributedLockAutoConfiguration:56-59` 提供 `Lock4jDistributedLock`（lock4j `LockTemplate` + Lettuce Redis），但见 §6：无任何宿主引入该 starter。

### 5.2 环境前置条件

| 前置 | 说明 |
|---|---|
| Redis | 单节点即可（`lock4j-redis-template-spring-boot-starter` 需 `RedisTemplate` executor） |
| 多 JVM | 至少 2 个进程同时 `advanceStep` 同一 `applicationId`，验证跨进程互斥 |
| 真实 DB | PostgreSQL/MySQL 双库之一，验证 `version` CAS 与 `request_no` 唯一索引在真实并发下的行为 |
| 宿主 pom | 宿主显式依赖 `shared-lock-starter`（见 §6），否则锁不生效、测试无意义 |
| 编排 | Docker Compose / CI job，控制两进程同时发起点火 |

### 5.3 测试用例设计（建议，不在本仓库执行）

1. 两进程以同一 `applicationId`、不同 `requestNo` 并发 `advanceApplication`，断言只有 1 次跃迁、另一个命中幂等短路或乐观锁冲突。
2. 同一 `requestNo` 跨进程重复投递，断言 `t_business_event_receipt` 回执 + `t_business_step_execution` 唯一约束共同短路，不重复落库。
3. 持锁进程崩溃（kill -9）后，另一进程在 lease 到期后仍能推进，验证 `DISPATCHING` 滞留回收 + 对账闭环。
4. Redis 不可用降级：断言服务不启动失败（弱依赖），回退到乐观锁路径且并发不变量仍成立。

### 5.4 启用方式（建议形态）

以 `@Tag("integration")` + `@EnabledIfEnvironmentVariable(named = "KERNEL_REDIS_INTEGRATION", matches = "true")` 的条件测试类落地，默认 `mvn test` 跳过；CI 在带 Redis 的 job 里显式启用。这样框架就绪、待环境即可跑，不污染日常构建。

> 判定：**#4 仍为「待环境」暂缓项**，仓库内不可完成；本节把它从一句「待环境」细化为可执行的前置条件 + 用例设计 + 启用形态。

---

## 6. 部署前检查清单（可执行）

| # | 检查项 | 现状（静态核实） | 部署前动作 | 通过标准 |
|---|---|---|---|---|
| 1 | `shared-lock-starter` 装配 | ⚠️ 无任何服务 pom 显式依赖，父 pom 仅在 `dependencyManagement`（`pom.xml:276`）；kernel 只依赖 `shared-lock` 接口（`business-core-application/pom.xml:24-28`），`FlowOrchestrationService:86,138` 用 `ObjectProvider.getIfAvailable()` 无锁直通 | 在每个承载 kernel 的宿主 starter pom 显式加 `shared-lock-starter`，并配置 Redis；或明确接受「仅乐观锁」并写入运行手册 | 启动日志出现 `Initialized Lock4jDistributedLock`；`advanceStep_usesDistributedLockWhenAvailable` 语义在真实上下文生效 |
| 2 | `httpexchange` 客户端配置位置 | ⚠️ 只在 `application-local.yml`，主配置无条件 `include: local` | 按 §3.3 迁移到主配置/Nacos 并移除 `include: local`，补 prod profile 隔离测试 | prod profile 启动成功且 `PlanDirectoryApi` / `RoleUserQueryApi` / `PermissionRegistrationPort` Bean 存在 |
| 3 | 权限项自注册 | ✅ 机制成立 | 确认 auth 启动日志 `PermissionScanner` 成功、DB 可写 | `t_auth_permission_item` 含 3 个新端点权限项 |
| 4 | 会话签名密钥 | ⚠️ local 为 `dev-session-signature-key-2026` 占位 | 用环境变量 `PERMISSION_SESSION_SIGNATURE_KEY` 注入生产密钥 | `KernelProductionReadinessValidator` fail-fast 通过，无 `dev-` 前缀 |
| 5 | MDC 日志 pattern | ⚠️ 仅 integration-service 有 `%X{}`，不含 kernel 4 键 | 按 §4.3 统一 pattern | 日志行可 grep 到 `applicationId`/`stepExecutionId`/`requestNo`/`traceId` |
| 6 | 脱敏/异步管道 | ⚠️ `global.enable: false`，E2E 未启用 | 按日志规范决定是否开启并验证 `AsyncAppender` | 敏感字段脱敏生效、异步不丢日志 |
| 7 | 真 Redis 并发集成 | ⚠️ 待环境 | 按 §5 准备 Docker + Redis + 多 JVM，启用条件测试 | 4 个用例全绿 |

---

## 7. 仓库内不可完成项（明确边界）

- 真实 HTTP 端到端（需 Nacos + 多服务同时运行）、真实 Redis/多 JVM 并发、日志输出目视验证 —— 均为运行时/部署期动作，本仓库只能静态核实 + 落盘检查清单。
- 外部组织目录主数据系统对接、`TerminalLevel` 多级追溯、`ancestorsOf` 整链 —— 已在 `2026-11-14-organization-directory-design.md` §7 列为仓库外另行立项，不属于非代码项。
- 上述配置/依赖缺陷（§6 #1/#2/#4/#5）属**需方案确认的变更**，本轮只落盘不擅改，避免未确认就改动 6+ 服务的生产启动配置。

---

## 8. 维护约定

- 本文件是 #12/#13/#4 与清单 §3 文档欠账的**收口记录**；后续部署联调结果应回填 §6 检查清单的「通过标准」列。
- 若决定执行 §6 中的配置/依赖变更，须单独立项（多服务 BREAKING 配置变更），并按「先写失败测试 → 再改配置 → 全仓验证」推进。
- 历史快照类描述（§23 的「仍未处理」、阶段表「进行中」）保持原样，只在原处补注，阅读以 `2026-11-14-remaining-work-inventory.md` §0 口径为准。
