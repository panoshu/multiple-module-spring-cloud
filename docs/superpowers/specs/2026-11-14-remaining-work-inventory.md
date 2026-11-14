# 剩余未完成任务清单（设计文档梳理）

> 生成日期：2026-11-14（2026-11-15 复核更新）
> 依据：`2026-11-08-business-core-kernel-production-design.md`（§20 阶段规划、§21 验收核对表、§23 台账）、`2026-11-14-remaining-work-priority.md`、`2026-11-14-p2-special-plan.md`、`2026-11-14-role-user-api-design.md`、`2026-11-14-p3-approval-stub-cross-service-design.md`、CODEBUDDY.md Known Gaps，并逐项对照当前代码核实
> 状态：批次 1–62 全部完成并推送；组织目录立项（批次 66–69）已全部完成并收口；**2026-11-15 非代码项（文档欠账 / #12 / #13 / #4 静态核实）已收口**，并落盘 `2026-11-15-non-code-items-closure.md` 与 `2026-11-15-architecture-review.md`；本文件是**当前**剩余工作的唯一权威清单

---

## 0. 口径说明（重要）

设计文档 §23 里共有约 60 行「**仍未处理**」，但它们是**每个批次收尾时的历史快照**（例如 §23.14 记录批次 13 时点的待办），并非当前待办；绝大多数已在后续批次被逐条结清，且每行都带有「已由批次 X 完成」的补注。

判断当前状态应看三处：① 最新批次 §23.77（批次 70 复核）的「仍未处理」；② §23.46「收口结论与遗留（阶段 5）」；③ `2026-11-14-remaining-work-priority.md` 的推进状态行。**P0/P1 全部清空，P2 专项方案 49/50 + 51/52 已完成，P3-1 立项 53–62 已全部收口，组织目录立项 66–69 已全部收口，非代码项已于 2026-11-15 静态收口**。

---

## 1. 框架能力未完成（可继续推进）

| # | 项 | 来源 | 当前代码核实（2026-11-14） | 建议 |
|---|---|---|---|---|
| 1 | ~~**组织目录真实接入**~~ | §23.59/§23.61/§23.69 | ✅ **已由批次 66–69 完成（§23.73–§23.76）**：auth-api `PlanDirectoryApi` 契约、auth 供给侧条件注册 `InMemoryProductGateway`、approval 侧 `getUsersInSamePlan`/`getUsersInParentPlan` 接线；零 DDL、Stub 保留兜底 | 立项收口；真实外部主数据系统对接属仓库外另行立项 |
| 2 | ~~**SAME_PLAN / LEVEL_UP 节点解析**~~ | §23.67/§23.69 | ✅ **已由批次 68/69 完成（§23.75/§23.76）**：`ApprovalNodeResolver` 两分支均按计划层级查成员、排除发起人、缺计划/到顶 fail-closed，不再回退 `node.approverIds()` | 立项收口；`TerminalLevel` 多级追溯 / `ancestorsOf` 整链列为后续 |
| 3 | **`Map<String,Object>` 业务扩展字段强类型化** | P2-1 / §23.46 | kernel domain/application 约 30 处 SPI（全仓含测试/局部变量共 72 行命中） | P2 专项方案已判定**降级关闭**：动态 Map 是通用编排引擎承载业务线自定义配置/事实的正确设计。仅补设计说明，不改代码 |
| 4 | **多进程 + 真实 Redis 分布式锁 + 真实 DB 并发集成测试** | P2-4 / §23.50 / §21.5 核对表 | 批次 43 的 `ConcurrentAdvanceAcceptanceTest`/`OptimisticLockConcurrencyTest` 是进程内状态化 fake 的确定性并发 | **暂缓（静态收口完成）**：环境前置条件、4 个用例设计与条件启用形态见 `2026-11-15-non-code-items-closure.md` §5；核心不变量已有契约测试 |
| 5 | **annuity 自有存储改持久化聚合** | P2-3 / §23.40/§23.42 | `InMemoryAnnuityApplicationExtensionStore` 与 `InMemoryParsedFileReferenceGateway` 仍是 `ConcurrentHashMap` | **wontfix**：annuity 是冻结的早期示例服务，进程内态满足 E2E |
| 6 | ~~增量事件消费端接线~~ | §23.33/§23.44 已知边界 | ✅ **批次 65 复核判定「不做」（§23.72）**：`BusinessApplication.complete()/fail()` 不注册任何终态领域事件；批次 33 的 `reconcile` 全量重算已是权威对账 | 若将来改为事件驱动，须先立项新增终态事件 |
| 7 | ~~扩展动作 `beanName` 存在校验~~ | §23.35 已知边界 | ✅ **已由批次 63 完成（§23.70）** | — |
| 8 | ~~条件表达式变量/返回类型校验~~ | §23.35 已知边界 | ✅ **批次 65 复核判定「不做」（§23.72）**：三引擎 `validate` 只做 parse/compile/check，静态无法可靠判定返回类型 | 保持现状 |
| 9 | ~~`BusinessConfigGateway` 无 kernel 默认实现~~ | CODEBUDDY.md Known Gaps | ✅ **批次 65 复核判定「设计选择、非待办」** | 接入新业务服务时的检查项 |
| 10 | ~~kernel 侧 PARTIAL 文件端到端统计对账~~ | §21.3 核对表 ⚠️ | ✅ **已由批次 64 完成（§23.71）**：`PartialParseStatistics` 经 `CallbackCommand.resultRef` 写入 `ExternalInteraction.resultRef` | — |
| 11 | ~~`startApproval` 发起人身份~~ | §23.64 边界③ | ✅ **批次 65 复核判定「不做」（§23.72）**：`StartApprovalRequest.initiator` 由 kernel 服务间调用传入，非人工会话 | 需区分「人工发起 vs 系统发起」后另立项 |
| 12 | **权限项运行时自注册与端到端 HTTP 验证** | §23.66/§23.67 | `AUTH_ASSIGNMENT/VIEW` 依赖 `PermissionScanner`/`PermissionRegistrationRunner` 自注册；`@HttpExchange` 客户端 bean 由 starter 运行时装配 | ✅ **静态核实完成**（`2026-11-15-non-code-items-closure.md` §3）：自注册机制成立；发现 `httpexchange` 配置只在 `application-local.yml` 且主配置无条件 `include: local` 的生产配置缺陷，已落盘部署检查清单；真 HTTP 端到端需部署环境 |
| 13 | **`shared-logging-starter` 脱敏/异步管道 E2E 未启用** | §23.44 已知边界 | MDC 值能否被日志输出依赖宿主日志 pattern | ✅ **静态核实完成**（同文档 §4）：发现全仓仅 integration-service 定义了 `%X{}` pattern 且不含 kernel 4 个流程键——MDC **写入正确但日志不输出**；修复建议与部署检查项已落盘 |
| 14 | **annuity 示例 Controller 契约适配** | §23.36 已知差距 | annuity `/advance` 仍以旧字段集合接收 Command | annuity 已冻结；真实业务服务接入时统一走 kernel 新入口 |
| 15 | **其余推进类 Command CQE 化** | §23.36 / P2-2 | 存量约 30 个裸参方法不回溯 | 规格 §21.4 已修订（批次 49），存量不回溯属既定决策 |
| 16 | **§21.4 字面张力（部分机器化）** | §23.46 核对表 / §23.48 | 4 条已机器化、3 条部分收敛；仍存 domain 依赖 `file.types.FileId`、Adapter Converter 构造值对象 | 已如实标注、不冒充覆盖 |
| 17 | **MDC 键常量抽取** | P3-3 / §23.44 | 4 个键仍是编排器私有字符串常量 | **保持记录**（过早抽象）；日志 pattern 缺口另见 #13 |
| 18 | **`AnnuityEndToEndTest` 仍禁用自动推进** | §23.51 | 另有 `AutoAdvanceEnabledEndToEndTest` 覆盖生产形态链路 | 决策保留，非待办 |

---

## 2. 已决策不做 / 暂缓（明确不是待办，避免被当作遗漏）

| 项 | 决策 | 出处 |
|---|---|---|
| `Map<String,Object>` 全量强类型化 | 降级关闭 | P2 专项方案 §1、§23.56 |
| annuity 自有存储持久化 | wontfix（冻结示例服务） | P2-3 |
| 多进程真 Redis 集成测试 | 暂缓（需基础设施，核心不变量已覆盖） | P2-4 |
| `EventReceiptGateway` 短路 | 不引入 | §23.33 |
| MDC 键常量抽取 | 保持记录（过早抽象） | P3-3 |
| 表名统一 / `request_no` 列宽 / `eventId().value()` / annuity 复制 DDL 解耦 / annuity 生产 profile | **已完成** | 批次 36b/40/47 等 |
| P0/P1/P2/P3 清单中的其它项 | 全部收口 | 优先级清单推进状态行 |

---

## 3. 文档/台账欠账 — ✅ 2026-11-15 全部收口

| 项 | 状态 |
|---|---|
| CODEBUDDY.md Known Gaps 称 `listMyPendingApprovals` 抛 UOE、`getApprovalHistory` 返回空 | ✅ 已修正：3 个 stub 由 53–55 清零；`getUsersInSamePlan` 与 SAME_PLAN/LEVEL_UP 由 68/69 接线 |
| §23.34 已知边界② 称 `reconcileAllActive()` 用 `loadAll()` 全表加载 | ✅ **已补注**：批次 46 已改为 `findByStatuses` 按状态索引（§23.53）；在 §23.34 原条补注「已由批次 46 完成」，历史快照原文保留 |
| 清单 §3 称「§23.46 末行称 P2 专项方案待用户确认」 | ✅ **已核实无需修**：§23.46 末行（约 1704 行）早写明「已采纳并完成 49/50」；清单该描述本身过时，已同步 |
| 优先级清单 P2 表格 / 状态行 / 推进状态行 / P3-1 处置列 / §4 依赖关系 | ✅ **已修正 6 处**（2026-11-15），见 `2026-11-15-non-code-items-closure.md` §2 |
| 主设计 §23.1 阶段 1–4 状态列写「进行中」 | ✅ **已补口径注**：阶段 0–5 已于批次 39-2 全部收口，表格为历史快照 |
| 设计文档 §23 约 60 行历史「仍未处理」 | 保持原样（历史快照有价值），阅读以 §0 口径为准 |

---

## 4. 建议下一步优先级

1. ~~**#7 / #10 / #6 / #8 / #9 / #11**~~ → ✅ 批次 63/64/65 完成或判定不做
2. ~~**#1 / #2 组织目录真实接入 + SAME_PLAN/LEVEL_UP 节点**~~ → ✅ 批次 66–69 完成、立项收口
3. ~~**清单 §3 文档欠账 / #12 / #13 静态核实 / #4 环境判定**~~ → ✅ **2026-11-15 批次 70 静态收口**，见 `2026-11-15-non-code-items-closure.md`
4. **部署前配置与装配动作**（仓库内不可完成，需目标环境）：① 宿主显式引入 `shared-lock-starter` + Redis；② 迁移 `httpexchange` 配置到主配置/Nacos 并移除 `include: local`；③ 统一 MDC 日志 pattern；④ 真 Redis 并发集成与端到端 HTTP 验证 —— 见 `2026-11-15-architecture-review.md` §4.4 / 收口文档 §6
5. **#3 / #5 / #14 / #15 / #16 / #17 / #18**：已决策不做、历史决策保留或文档欠账，均无需改 kernel 代码

> 结论：**规格 §20 规划的全部批次（1–62）已完成**，§21 验收核对表已无 ⚠️/❌；批次 63/64 补齐发布期与对账能力，65 把 4 项「看似待办」核实为设计选择，66–69 完成组织目录立项，70 完成非代码项静态收口与架构复核。**当前 kernel 代码项、组织目录立项、仓库内非代码项均已清空**；剩余仅为需目标环境的部署/装配动作（4 项）与仓库外另行立项项（外部主数据系统对接、`TerminalLevel` 多级追溯等）。
