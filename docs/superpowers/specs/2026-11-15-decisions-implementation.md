# 批次 71：三项用户决策落地记录

> 生成日期：2026-11-15
> 依据：用户对本轮架构复核的三条决策；`2026-11-15-non-code-items-closure.md`、`2026-11-15-architecture-review.md` 为首次复核快照
> 分支：`refactor/business-core-kernel-production`
> 性质：代码 + 配置改动（承接复核结论的落地批次）

---

## 1. 决策与结论

| # | 用户决策 | 判断 / 动作 | 结论 |
|---|---|---|---|
| 1 | kernel 是否需要分布式锁；需要就引入 `shared-lock-starter` 并按推荐用法正常使用 | **需要**（理由见 §2）。已在 `business-core-infrastructure` 引入 starter、保留 application 层纯接口依赖、更新两处注释、测试上下文排除无 Redis 的锁自动配置、新增装配契约测试 | ✅ 落地 |
| 2 | `httpexchange` 配置需要保留，服务间调用会用到 | **不改任何配置**：各服务 `application-local.yml` 的 `httpexchange.clients` 与主配置的 `spring.profiles.include: local` 全部原样保留；首次复核中列为「配置缺陷」的条目改判为「按用户决策保留」 | ✅ 保留 |
| 3 | MDC 需配置为本项目的实际 ID，现有 `logback-spring.xml` 的键是示例 | 把示例键 `BizId/BatchId/OrderId/UserId` 换成项目实际写入的 `traceId/applicationId/stepExecutionId/requestNo/businessType`；改 integration-service 现有文件，并为承载 kernel 的 annuity-starter 新增 `logback-spring.xml` | ✅ 落地 |

---

## 2. 决策 1：为什么 kernel 需要分布式锁

- `FlowOrchestrationService.advanceStep` 是「加载聚合 → 校验/解析路由 → 执行 Handler（可能触发文件解析、审批启动等**外部副作用**）→ 保存 + 发事件」的临界区。
- 乐观锁（`version` CAS）只保证**保存时不覆盖新状态**，不能阻止两个实例都走完 Handler、都发出外部请求；`requestNo` / `EventReceipt` 是**事后**幂等兜底，但外部副作用（重复派发、重复建单）已经发生。
- §1 决策 7 明确「持久化状态机 + … + 乐观锁/短分布式锁」双层，§9.3 把锁定义为第一层；`ConcurrentAdvanceAcceptanceTest` / `OptimisticLockConcurrencyTest` 的确定性并发验收也以「存在锁」为前提。
- 因此锁是**正确性必需**（防临界区重复副作用），不是可选优化。原「弱依赖 + 宿主自行引入」的落法导致生产上下文无 Bean、永远无锁直通，属装配缺口。

### 为什么仍保留 `ObjectProvider` 弱依赖

`DistributedLock` 端口契约明确允许两种用法：强依赖（正确性必需 → 装配缺失启动失败）与弱依赖（单飞保护/防重 → 装配缺失相关组件自然不装配）。kernel 有多层兜底（乐观锁、`requestNo`、`EventReceipt`、唯一约束），锁是「第一层防重」，且无 Redis 的测试上下文必须能启动。故保留弱依赖，但由 **kernel infrastructure 自带 starter** 保证生产上下文必有 Bean —— 既满足推荐用法（`lockAndRun` 模板方法保证释放），又不破坏测试自包含。

---

## 3. 改动清单

| 文件 | 改动 |
|---|---|
| `business-core-kernel/business-core-infrastructure/pom.xml` | 新增 `shared-lock-starter`（技术底座装配层，与 `shared-id/event/logging/client-starter` 一致） |
| `business-core-kernel/business-core-application/pom.xml` | 注释改为「实现由 infrastructure 引入的 starter 装配」；**保留** `shared-lock` 纯接口依赖 |
| `FlowOrchestrationService.java` | `withApplicationLock` Javadoc 更新为「生产装配 Redis 锁 / 测试可排除 / 回退乐观锁」 |
| `annuity-service/annuity-starter/src/test/resources/application-test.yml` | `spring.autoconfigure.exclude` 增 `DistributedLockAutoConfiguration`（无 Redis 测试上下文），保持 E2E 自包含 |
| `business-core-infrastructure/src/test/.../lock/DistributedLockAssemblyTest.java` | 新增 2 项：`ApplicationContextRunner` 验证 starter 注册唯一 `DistributedLock` Bean、实现为 `Lock4jDistributedLock`（非本地降级） |
| `integration-service-starter/src/main/resources/logback-spring.xml` | `MDC_CONTEXT_FIELDS` 改为项目实际 ID |
| `annuity-service/annuity-starter/src/main/resources/logback-spring.xml` | 新增：覆盖 Spring Boot 默认 pattern 输出 5 个实际 MDC 键，复用官方 `base.xml` / `rollingpolicy` |

> **未改动**：任何 `httpexchange` 配置、任何服务主 `application.yml` 的 `include: local`、`shared-lock-starter` 自身代码、annuity 业务代码与 DDL。

---

## 4. 推荐用法核对

| 约定 | 现状 |
|---|---|
| `DistributedLock.lockAndRun(key, waitTime, leaseTime, unit, task)` 模板方法 | `FlowOrchestrationService.java:142` 已使用；内部 try-with-resources 保证释放，获取失败抛 `SystemException(GET_LOCK_FAILED)` |
| 锁参数 | 等待 3s、租期 10s、无看门狗，**不覆盖远程 IO 或长批量处理**（§9.3） |
| 锁 key | 业务语义前缀 + `applicationId`，实现层负责前缀隔离 |
| 唯一 Redis 实现 | `Lock4jDistributedLock`（`RedisTemplate` executor / Lettuce），契约 v3 明确**不做本地静默降级** |
| 启动期校验 | `shared.lock.connection-check` 默认 true，生产 PING 失败即 fail-fast；测试上下文排除自动配置 |
| 分层 | application 只依赖 `shared-lock` 接口；starter 实现放在 infrastructure（依赖倒置） |

---

## 5. 验证

- `DistributedLockAssemblyTest` 2 项全绿，日志出现 `Initialized Lock4jDistributedLock (RedisTemplate executor, Lettuce)` —— 证明生产上下文会注册真实 Redis 锁 Bean。
- kernel infrastructure **92**、application **226**、arch-tests **17**、annuity E2E **8** 全绿；全仓 `mvn test-compile`、`git diff --check` 通过。
- annuity E2E 日志中 `applicationId:APP-` 命中 **418 行**，证明 MDC 流程 ID 已在宿主日志实际输出（此前默认 pattern 完全不输出）。
- `httpexchange` 配置零改动，服务间 `@HttpExchange` 客户端链路保持原样。

---

## 6. 结论更新（覆盖首次复核快照的对应结论）

| 首次复核结论 | 批次 71 后 |
|---|---|
| 「`shared-lock-starter` 无宿主引入，生产无锁直通，仅乐观锁兜底」 | ✅ 已由 kernel infrastructure 引入并装配，生产有 Redis 分布式锁 |
| 「§21.5 第 7 条（通用能力经 shared starter）名义达成」 | ✅ 运行时达成，装配契约测试锁定 |
| 「MDC 写入正确但全仓日志不输出」 | ✅ 已修：annuity 宿主与 integration-service 输出 5 个实际 ID |
| 「`httpexchange` 配置只在 local + 主配置 `include: local` 是缺陷」 | ⏸ **按用户决策保留**：服务间调用依赖该配置。若将来做生产 profile 隔离，须同步把 `httpexchange.clients` 段提升到主配置/Nacos，**不能删除** |
| 「#4 真 Redis + 多 JVM 集成待环境」 | 不变（仍待 Docker + Redis + 多 JVM） |

---

## 7. 仍待部署期完成

1. 生产 Redis 可达（starter 启动期 PING 校验会 fail-fast，不可达即启动失败）。
2. 端到端 HTTP 验证（需 Nacos + 多服务同时运行）。
3. #4 真 Redis + 多 JVM 并发集成测试（环境前置条件与用例设计见 `2026-11-15-non-code-items-closure.md` §5）。
4. 会话签名密钥用环境变量注入（local 仍是 `dev-` 占位）。
5. 其余服务若也要输出流程 MDC，复制 annuity 的 `logback-spring.xml` 片段即可（它们只写 `traceId`，不写 kernel 的 4 个流程键）。
