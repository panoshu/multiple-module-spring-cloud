# 剩余工作优先级清单

> 生成日期：2026-11-14
> 依据：`2026-11-08-business-core-kernel-production-design.md` §23.46 / §23.51、`2026-11-13-business-core-kernel-remaining-plan.md`、CODEBUDDY.md Known Gaps，并对每一项做了当前代码核实
> 状态：批次 1–44 全部收口，§23.46 四项偏差全部有处置。本清单只排「剩余工作」，不含已完成项
> 用途：后续排期决策依据，不是实施计划；具体批次仍需按「不要超级计划」逐项拆小

---

## 1. 评估口径

| 维度 | 说明 |
|---|---|
| 生产正确性风险 | 是否会导致重复跃迁、状态错乱、静默降级、数据丢失 |
| 影响面 | 改动文件数、是否跨服务、是否触及 annuity 示例服务 |
| 投入产出比 | 改动量与收益是否匹配 |
| 依赖关系 | 是否阻塞其它项、是否需先做规格/方案决策 |
| 冻结约束 | annuity 业务实现已冻结（用户明确「只是早期示例服务」），涉及 annuity 的项默认降级 |

优先级定义：**P0** 上线前必做（正确性/安全）；**P1** 近期做（改动小、收益明确）；**P2** 需专项设计（大工程/规格决策）；**P3** 仅记录，不排期。

---

## 2. 先纠偏：核实后应从待办移除或改写的条目

| 台账条目 | 核实结论 | 处置 |
|---|---|---|
| `StepDefinition.actionType()` 显式声明 | **已完成**。`StepDefinition` 已有 `ActionType actionType` 字段与构造器校验；`business()` 工厂传 `BUSINESS`、`external()` 接收参数；`BusinessConfigProcessDefinitionAdapter` 按 `mainProcessor` 推导 `BUSINESS/NONE` | 从 §23.46 遗留清单移除，避免误导排期 |
| 增量事件消费端按 eventId 幂等 | **主体已完成**。`ApprovalResultEventListener`（3 个入口）、`FileParsedEventListener`、`OutboundFileGeneratedEventListener` 均调 `EventReceiptGateway.tryConsume` | 改写为「消费入口一致性收口」，并入 P0-1 |
| `business-core-api` 是空模块 / adapter 无代码 | **描述过时**。core-api 现有 38 个 Java 文件、core-adapter 17 个 | 更新 CODEBUDDY.md Known Gaps（P3） |
| annuity 复制 DDL 解耦 | **已完成**（批次 40，§23.47） | 已在 §23.46 标注，无需再排 |

---

## 3. 优先级清单

> **推进状态**：P0-1 ✅ 已修复（批次 45，§23.52）；P0-2 ✅ 已完成（批次 47，§23.54，用户解冻 annuity）；P0-3 ✅ 已核查并机器化（批次 45）；P1-1 / P1-2 ✅ 已完成（批次 46，§23.53）；P1-3 ✅ 已完成（批次 45）——**P0/P1 全部清空**；批次 48 修正 P0-2 评估时发现的 fail-fast 开关语义缺陷（§23.55，清单外配套项）；**P2 专项方案已采纳且 49→50 全部完成**（见 `2026-11-14-p2-special-plan.md`）：批次 49 §21.4 规格修订（§23.56）、批次 50 向前 ArchUnit 约束（§23.57）；P2-1 降级关闭、P2-3 wontfix、P2-4 暂缓；**批次 51 回补 §21.4 第 4 条 Adapter 构造边界**（§23.58，部分机器化、字面张力如实标注）；**批次 52 完成 P3-2 复核（属性命名空间统一，§23.59）**；P3 仅余 P3-1 / P3-3 记录项。

### P0 — 上线前必做（正确性 / 安全）

| # | 项 | 依据（本次核实） | 影响面 | 建议处置 |
|---|---|---|---|---|
| P0-1 | **【新发现·台账未记录】重复出站事件监听器** | `OutboundFileEventListener` 与 `OutboundFileGeneratedEventListener` 同为 `@Component` + `@EventListener`，监听同一 `OutboundFileGeneratedEventDTO`。旧类 `handleOutboundFileGenerated` **无 `tryConsume`、绕过 `CallbackCorrelationService`**，直接 `advanceByOutboundFile`；新类有回执 + 关联 + `completeWaitingStep`。同一事件被双份处理，重复事件必然重复推进；`grep` 确认零测试覆盖 | 2 个类 + 引用其名的注释/Mock 网关，规模小但语义重 | 拆 1–2 个小批次：① 明确唯一入口，退役或改造旧 listener（补 `tryConsume` 或改为委托新入口）；② 补契约测试断言同一事件只推进一次、重复 eventId 被短路。先红后绿 |
| P0-2 | **annuity 生产 profile 治理** | `annuity-starter/src/main/resources/application.yml:26` 有 `spring.profiles.include: local`；`KernelProductionReadinessValidator` 的 `NON_PRODUCTION_PROFILES = {test, local, dev}`，因此 prod 环境也含 `local` → 启动跳过 fail-fast，会话验签密钥缺失/为 `dev-` 占位时静默降级 | 1 行配置；需验证不破坏 `@ActiveProfiles("test")` 的 H2 E2E | 改为按环境注入（prod 不 include local），或显式设 `core.kernel.production.fail-fast=false` 并注明仅示例服务；补一条「prod profile 触发校验」的测试 ✅ 已由批次 47 完成（§23.54） |
| P0-3 | **RocketMQ 消费入口一致性** | 3 个 listener 的 `handleXxx` 注释写明「生产由 RocketMQ 消费者直接调用」，但仓库内未发现对应 consumer；若外部消费者直调 `handleXxx`，回执与关联的覆盖需逐一核对 | 需先定位生产消费端，可能在其它仓库 | 与 P0-1 合并核查：列出所有 `handleXxx` 入口，确认每条都经 `tryConsume`；无 consumer 则记为「待接入时强制约束」 |

### P1 — 近期做（改动小、收益明确）

| # | 项 | 依据 | 影响面 | 建议处置 |
|---|---|---|---|---|
| P1-1 | **批次扫描按状态索引查询** | `BusinessBatchAppService.reconcileAllActive()` → `batchRepository.loadAll()` → `AbstractBusinessRepository.loadAll()` = `mapper().selectAll()` 全表扫描；恢复 Job 每次触发都扫全表，批次表增长后线性劣化。DDL 已有 `idx_business_batch_status` | 2–3 文件（仓储接口/实现/应用服务） | 新增 `findByStatuses(...)` 走索引，`reconcileAllActive` 改用它；补一条断言「只查活跃状态」的测试 |
| P1-2 | **恢复任务事务边界精修** | `ExternalInteractionRecoveryService` 四个动作**均无 `@Transactional`**，每条 `repository.save` 各自提交；`ExternalInteractionRecoveryJob.safeRun` 只做动作级隔离，批量动作中途异常时已处理/未处理记录的一致性依赖单条 save 而非事务 | kernel application 层 1–2 文件 | 按单条加事务包装（与「单条失败隔离」对齐），补一条「中途异常不影响已成功记录」的测试；注意不要引入跨批长事务 |
| P1-3 | **台账/规范文档过时条目清理** | 见第 2 节：`actionType`、`business-core-api/adapter` 空模块等描述与代码不符 | 纯文档 | 更新 §23.46 遗留清单与 CODEBUDDY.md Known Gaps；零代码改动 |

### P2 — 需专项设计（大工程 / 规格决策，勿直接开工）

> **专项方案**：见 `2026-11-14-p2-special-plan.md`（已对本表四项逐项核实并给出决策）。结论摘要：**建议做 P2-2**（修订 §21.4 + 向前 ArchUnit 约束，拆批次 49/50）；**P2-1 建议降级关闭**（动态 Map 是通用编排引擎的正确设计，备选仅做类型安全读取包装）；**P2-3 建议 wontfix**（annuity 冻结）；**P2-4 建议暂缓**（需容器化基础设施）。四项均待用户确认，确认前不开工。

| # | 项 | 依据 | 为何不排近期 | 前置条件 |
|---|---|---|---|---|
| P2-1 | **`Map<String,Object>` 业务扩展字段强类型化** | 遍布 12+ 处 SPI：`FileIntegrationGateway` 参数、`FormParsingConfig.splitRules`、`StepExtensionConfig.params`、`BusinessMetaContext.extensionFacts`、`ExtensionExecutionResult.mutations`、`StepExtensionAction.execute`、`BusinessFactExtractor.extractBusinessFacts` 等 | 架构级弱类型设计，跨 domain/application 两层，逐处迁移会波及全部 SPI 实现与 annuity 消费者 | 先定 Params/值对象模型与迁移兼容策略，再拆多批 |
| P2-2 | **§21.4 未机器化 3 条** | ① ApplicationService 入参全部 CQE 化：20+ 裸参方法；② types 层只含 ID：现状含 `BankAccount`/`Address`/`ValidityPeriod` 等值对象，需拆分到 domain；③ application 零事件 DTO 依赖：现状消费 `file.api.event.*`，需跨服务契约重构 | 每条都需规格修订或大范围改造，批次 41 已如实标注「与现状有张力，不硬凑绿灯」 | 先做规格决策（修订 §21.4 或立项重构） |
| P2-3 | **annuity 自有存储改持久化聚合** | `AnnuityApplicationExtensionStore`、`InMemoryParsedFileReferenceGateway` 均为 `ConcurrentHashMap` 进程内态，重启丢失 | annuity 是冻结的早期示例服务；用户明确不为其持续适配 | 仅当 annuity 转为真实业务服务时再做 |
| P2-4 | **多进程 + 真实 Redis 锁 + 真实 DB 并发集成测试** | 批次 43 是进程内状态化 fake 的确定性并发（§23.50 已如实标注边界） | 需真实 Redis + 多 JVM 编排，投入大；核心不变量已有契约测试 | 有集成测试环境后再排 |

### P3 — 仅记录，不排期（批次 52 逐项核实后重排）

> 批次 52 复核结论：P3-2 原记「无实际问题」**不成立**，已发现并修复真实不一致；P3-1 / P3-3 维持记录。

| # | 项 | 核实结论 | 处置 |
|---|---|---|---|
| P3-1 | approval-service stub | 实际 **3 个**（清单原写 2 个）：`ApprovalFlowService.listApprovalFlows`、`ApprovalInstanceService.listMyPendingApprovals` 抛 `UnsupportedOperationException`，`getApprovalHistory` 返回 `List.of()`；属其它服务独立待办（第 5 节明确排除），实现需 approval 领域/仓储/分页契约 | **保持记录，不推进**；清单描述纠偏为 3 个 |
| P3-2 | kernel `@ConditionalOnProperty` 清单复核 | 原记「无实际问题」**不成立**：5 个 Controller 用遗留 `core-kernel.controllers.enabled`，validator（`core.kernel.production.fail-fast`）/ recovery（`core.kernel.recovery.enabled`）用点分隔前缀，同模块两套命名空间；全仓无 yml 显式配置这些键 | ✅ **已由批次 52 修复并机器化**（§23.59）：统一为 `core.kernel.controllers.enabled`，新增 ArchUnit 契约禁止遗留前缀，同步 4 处文档引用 |
| P3-3 | MDC 键常量抽取 | kernel 4 键（`applicationId`/`stepExecutionId`/`requestNo`/`businessType`）仅编排器 + 其测试读写；shared-web-core 用 `traceId` 与配置别名，键不重叠，「多组件读写同组键」未发生 | **保持记录**（§23.44 已知边界：过早抽象），触发条件不变 |

---

## 4. 依赖关系与建议推进顺序

```
P0-1（重复 listener） ──┬──> P0-3（消费入口一致性，合并核查）
                        └──> 可独立先做，收益最高且改动最小

P0-2（prod profile） ── 独立于 P0-1，但会动 annuity 配置，需单独验证 E2E

P1-3（文档纠偏） ── 可随任意批次顺带完成，零风险

P1-1（索引扫描） ── 独立于 P1-2，两者互不阻塞
P1-2（事务边界） ── 建议放在 P1-1 之后，两者都改恢复链路，便于合并验证

P2-* ── 均需先出专项方案并与用户确认，不依赖 P0/P1 完成
```

**第一批（批次 45，已完成）**：P0-1（含 P0-3 核查）+ P1-3 文档纠偏。新增 ArchUnit 契约测试先红（唯一性 2、旧类缺回执）后绿，退役旧 `OutboundFileEventListener` 与死代码 `advanceByOutboundFile`，更新 CODEBUDDY.md Known Gaps 与 §23.46 遗留清单。

**第二批（批次 46，已完成）**：P1-1（`findByStatuses` 按状态索引查询替代全表 `loadAll`）+ P1-2（恢复服务每条记录包独立事务，外部查询留事务外），合并验证全绿。详见设计文档 §23.53。

**P0-2 单独安排**：它触及 annuity 配置，与冻结约束相关，建议先与用户确认「是否值得为示例服务动配置」再动手。

---

## 5. 不在本清单范围

- 批次 1–44 已完成的全部工作（含 §23.46 四项偏差的处置）。
- 其它业务服务（auth / file / approval / integration / BFF / gateway）自身的独立待办，除非与 kernel 契约直接相关（如 P0-3 的生产消费端）。
- 需要新增第三方基础设施（Redis 集群、MQ、Nacos）才能验证的项，统一归 P2-4。

---

## 6. 维护约定

- 每完成一项，在本文件对应行标注批次号与提交号，并同步更新设计文档 §23.46 遗留清单。
- 若核实发现某项已完成或描述过时（如第 2 节的 `actionType`），直接移入第 2 节纠偏表，不保留在待办里。
- 新发现的生产缺陷（如 P0-1）先记录再排期，不在「制定清单」任务里顺手修，保持分析与实施分离。
