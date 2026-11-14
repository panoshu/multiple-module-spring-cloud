# 剩余未完成任务清单（设计文档梳理）

> 生成日期：2026-11-14
> 依据：`2026-11-08-business-core-kernel-production-design.md`（§20 阶段规划、§21 验收核对表、§23 台账）、`2026-11-14-remaining-work-priority.md`、`2026-11-14-p2-special-plan.md`、`2026-11-14-role-user-api-design.md`、`2026-11-14-p3-approval-stub-cross-service-design.md`、CODEBUDDY.md Known Gaps，并逐项对照当前代码核实
> 状态：批次 1–62 全部完成并推送；本文件是**当前**剩余工作的唯一权威清单

---

## 0. 口径说明（重要）

设计文档 §23 里共有约 60 行「**仍未处理**」，但它们是**每个批次收尾时的历史快照**（例如 §23.14 记录批次 13 时点的待办），并非当前待办；绝大多数已在后续批次被逐条结清，且每行都带有「已由批次 X 完成」的补注。

判断当前状态应看三处：① 最新批次 §23.69 的「仍未处理」；② §23.46「收口结论与遗留（阶段 5）」；③ `2026-11-14-remaining-work-priority.md` 的推进状态行。**P0/P1 全部清空，P2 专项方案 49/50 + 51/52 已完成，P3-1 立项 53–62 已全部收口**。

---

## 1. 框架能力未完成（可继续推进）

| # | 项 | 来源 | 当前代码核实（2026-11-14） | 建议 |
|---|---|---|---|---|
| 1 | **组织目录真实接入** | §23.59/§23.61/§23.69 | `ProductGateway` 仅 `StubProductGateway`；`PlanReachabilityService.listAssignmentsForPlan` 生产不可用；CUSTOMER/PRODUCT/GLOBAL 继承展开不支持，角色审批仅 `SPECIFIED_ROLE` + PLAN 精确 scope | 需真实组织目录服务，属跨服务新契约，单独立项 |
| 2 | **SAME_PLAN / LEVEL_UP 节点解析** | §23.67/§23.69 | `ApprovalNodeResolver` 两个分支仍是 TODO（回退 `node.approverIds()`）；`RoleUserGatewayImpl.getUsersInSamePlan` 仍为未接线 stub（全仓无消费方） | 与 #1 一并立项；当前框架视角下「无调用方」可接受，但节点语义不完整 |
| 3 | **`Map<String,Object>` 业务扩展字段强类型化** | P2-1 / §23.46 | kernel domain/application 约 30 处 SPI（全仓含测试/局部变量共 72 行命中）：`FormParsingConfig.splitRules`、`StepExtensionConfig.params`、`BusinessMetaContext.extensionFacts`、`ExtensionExecutionResult.mutations`、`StepExtensionAction.execute` 等 | P2 专项方案已判定**降级关闭**：动态 Map 是通用编排引擎承载业务线自定义配置/事实的正确设计，强类型化会把业务 schema 固化进 kernel。仅补设计说明，不改代码 |
| 4 | **多进程 + 真实 Redis 分布式锁 + 真实 DB 并发集成测试** | P2-4 / §23.50 / §21.5 核对表 | 批次 43 的 `ConcurrentAdvanceAcceptanceTest`/`OptimisticLockConcurrencyTest` 是进程内状态化 fake 的确定性并发；无真实 Redis/多 JVM 集成 | **暂缓**：需集成测试环境（Docker + Redis + 多 JVM），核心不变量已有契约测试 |
| 5 | **annuity 自有存储改持久化聚合** | P2-3 / §23.40/§23.42 | `InMemoryAnnuityApplicationExtensionStore` 与 `InMemoryParsedFileReferenceGateway` 仍是 `ConcurrentHashMap` | **wontfix**：annuity 是冻结的早期示例服务，进程内态满足 E2E；仅当转真实业务服务时再做 |
| 6 | **增量事件消费端接线** | §23.33/§23.44 已知边界 | `BusinessBatch.registerSuccess/registerFailure` 全仓**仅测试调用**，无生产调用方；批次 33 决策用「全量重算的绝对赋值幂等」覆盖，未引入 `EventReceiptGateway` 短路 | 阶段 5 待办：若恢复链路改为事件增量驱动，需补消费端与按 eventId 幂等 |
| 7 | ~~扩展动作 `beanName` 存在校验~~ | §23.35 已知边界 | ✅ **已由批次 63 完成（§23.70）**：`ProcessDefinitionPublishService` 注入 `ExtensionActionRegistry` 并在发布期校验全部扩展动作 beanName，缺失即拒绝且不落库 | — |
| 8 | **条件表达式变量/返回类型校验** | §23.35 已知边界 | `ConditionEvaluationGateway.validate` 只保证语法可解析，不校验变量/函数存在、不校验返回类型布尔；三引擎语法不兼容，未做跨引擎归一化 | 小批次，收益有限；切换引擎时需重写 |
| 9 | **`BusinessConfigGateway` 无 kernel 默认实现** | CODEBUDDY.md Known Gaps | 全仓唯一实现是 annuity 的 `JsonBusinessConfigGateway`；kernel 未提供默认（`FileIntegrationGateway` 已有 `FileServiceIntegrationGateway`） | 接入新业务服务时的检查项；目前每个业务服务自行实现，符合 CODEBUDDY.md 定位 |
| 10 | **kernel 侧 PARTIAL 文件端到端统计对账** | §21.3 核对表 ⚠️ | kernel 按「解析任务完成」把 PARTIAL 与 SUCCESS 同样推进一次，失败明细留在 subTasks/invalidCount，未做端到端统计对账 | 阶段 5：需与 file-service PARTIAL 语义对齐后补验收 |
| 11 | **`startApproval` 发起人身份** | §23.64 边界③ | 4 个审批操作已用会话账号覆盖（批次 57），但 `startApproval`（由业务服务/系统发起）未纳入会话身份治理 | 视业务接入方式决定；当前非人工会话操作，列为遗留 |
| 12 | **权限项运行时自注册与端到端 HTTP 验证** | §23.66/§23.67 | `AUTH_ASSIGNMENT/VIEW` 依赖 `PermissionRegistrationRunner` 自注册；`@HttpExchange` 客户端 bean 由 starter 运行时装配，单测不覆盖端到端 HTTP（annuity E2E 不启动 approval-service） | 生产部署前需确认 auth 数据侧已有对应权限项 + 会话 header 透传链路 |
| 13 | **`shared-logging-starter` 脱敏/异步管道 E2E 未启用** | §23.44 已知边界 | MDC 值能否被日志输出依赖宿主日志 pattern，E2E 只保证写入与恢复 | 宿主日志配置联调项 |
| 14 | **annuity 示例 Controller 契约适配** | §23.36 已知差距 | annuity `/advance` 仍以旧字段集合接收 Command；新增 `@NotBlank expectedStep/requestNo` 会使其旧请求被校验拒绝 | annuity 已冻结；真实业务服务接入时统一走 kernel 新入口 |
| 15 | **其余推进类 Command CQE 化** | §23.36 / P2-2 | `submit(ApplicationId)` 与 `StartApplicationCommand`/`RetryStepCommand`/回调类仍为裸参；批次 50 只加了「新增方法必须 CQE」的向前约束，存量约 30 个裸参方法不回溯 | 规格 §21.4 已修订（批次 49），存量不回溯属既定决策 |
| 16 | **§21.4 字面张力（部分机器化）** | §23.46 核对表 / §23.48 | 4 条已机器化、3 条经规格修订+向前约束部分收敛；仍存：domain 依赖 `file.types.FileId` 等 ID 原语、Adapter Converter 构造值对象（`BusinessFile`/`BusinessContext`/`OperatorInfo`）、`business-core-types` 含非 ID 值对象 | 已如实标注、不冒充覆盖；若要彻底收敛需规格再修订或大重构 |
| 17 | **MDC 键常量抽取** | P3-3 / §23.44 | 4 个键仍是 `FlowOrchestrationService` 私有字符串常量，仅编排器 + 其测试读写；shared-web-core 用 `traceId`/配置别名，键不重叠 | **保持记录**：过早抽象，触发条件「多组件读写同组键」未发生 |
| 18 | **`AnnuityEndToEndTest` 仍禁用自动推进** | §23.51 | 保留手动确定性断言；另有 `AutoAdvanceEnabledEndToEndTest`（不 mock 监听器、独立 H2）覆盖生产形态链路 | 决策保留，非待办 |

---

## 2. 已决策不做 / 暂缓（明确不是待办，避免被当作遗漏）

| 项 | 决策 | 出处 |
|---|---|---|
| `Map<String,Object>` 全量强类型化 | 降级关闭（通用编排引擎设计选择） | P2 专项方案 §1、§23.56 |
| annuity 自有存储持久化 | wontfix（冻结示例服务） | P2-3 |
| 多进程真 Redis 集成测试 | 暂缓（需基础设施，核心不变量已覆盖） | P2-4 |
| `EventReceiptGateway` 短路 | 不引入（全量重算幂等已覆盖） | §23.33 |
| MDC 键常量抽取 | 保持记录（过早抽象） | P3-3 |
| 表名统一 / `request_no` 列宽 / `eventId().value()` / annuity 复制 DDL 解耦 / annuity 生产 profile | **已完成** | 批次 36b/40/47 等 |
| `StepDefinition.actionType()` 显式声明 | 核实已完成（纠偏） | 优先级清单第 2 节 |
| P0/P1/P2/P3 清单中的其它项 | 全部收口 | 优先级清单推进状态行 |

---

## 3. 文档/台账欠账（本次已修 + 仍待）

| 项 | 状态 |
|---|---|
| CODEBUDDY.md Known Gaps 称 `listMyPendingApprovals` 抛 UOE、`getApprovalHistory` 返回空 | ✅ **本次已修正**：3 个 service stub 已由批次 53–55 清零；`getUsersInSamePlan` 仍为有意保留的未接线 stub |
| §23.44 已知边界① 称 `reconcileAllActive()` 用 `loadAll()` 全表加载 | ⚠️ **过时**：批次 46 已改为 `findByStatuses` 按状态索引（§23.53）。属历史快照，建议下次触碰该章节时补注 |
| §23.46 末行称「P2 专项方案待用户确认」 | ⚠️ **过时**：方案已采纳且 49/50/51/52 完成。属历史快照 |
| 优先级清单 P2 表格「建议处置」列仍写「先定模型/先做规格决策」 | ⚠️ **过时**：推进状态行已更新为「降级关闭/wontfix/暂缓/部分机器化」，表格正文未同步 |
| 设计文档 §23 约 60 行历史「仍未处理」 | 保持原样（历史快照有价值），但阅读时须以 §0 口径为准 |

---

## 4. 建议下一步优先级

1. ~~**#7 扩展动作 `beanName` 存在校验**~~ → ✅ 已由批次 63 完成（§23.70）
2. **#10 kernel 侧 PARTIAL 端到端统计对账**（§21 核对表唯一 ⚠️，验收完整性）
3. **#6 增量事件消费端接线**（恢复链路语义统一）
4. **#1 / #2 组织目录 + SAME_PLAN/LEVEL_UP**（跨服务新契约，需单独立项与用户确认）
5. **#4 真 Redis 集成测试**（待集成测试环境）
6. **#3 / #5 / #17** 按既有决策保持不做/暂缓

> 结论：**规格 §20 规划的全部批次（1–62）已完成**，当前没有「规格内未完成」任务；剩余均为 §23.46「收口结论与遗留」标注的阶段 5 精修项、跨服务框架外依赖，或已明确决策不做的项。
