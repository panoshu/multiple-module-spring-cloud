# 生产级业务流程内核：剩余实现重构与推进计划

> 日期：2026-11-13
> 工作分支：`refactor/business-core-kernel-production`
> 关联规格：`2026-11-08-business-core-kernel-production-design.md`（§23 台账记录至批次 25）
> 状态：待评审确认后按批次编码；每批遵循「先写失败测试 → 实现 → 跑测试 → 全仓验证 → 提交推送」
> 基线提交：`3a5a7386`（批次 25 完成等待步骤门面）

---

## 0. 文档目的

本计划回答三个问题：

1. **派发载荷持久化方式怎么选**（阶段 B 事务外派发的核心决策）；
2. `FILE_*` 网关返回 `void`、拿不到 `externalTaskId` 怎么补；
3. 当前实现与规格的差异有哪些、剩余工作如何拆小批次推进。

约束（已确认，不再变更）：

- **冻结 `annuity-service`**：不为 kernel 改造修改其业务代码、DDL 复制脚本、Repository 子类、注册类；仅作只读消费者用于编译与 E2E 验证。
- **小步提交（§22）**：每批 ≤ 6–8 个文件，可独立验证、独立提交推送；不产出一个 15+ 文件的「超级批次」。
- **领域层纯净**：domain 不依赖 Spring/DB/JSON 注解；application 不依赖 file/approval DTO。
- **不追求跨系统强一致**：持久化状态机 + 本地短事务 + Outbox + 消费幂等 + 乐观锁/短锁。

---

## 1. 核心决策摘要（先看这里）

| 决策点 | 结论 | 理由 |
|---|---|---|
| 派发载荷持久化 | **复用 `StepExecution.checkpoint_ref` 存紧凑 JSON**，按交互类型差异化：可重建参数不持久化 | 该列已存在于 kernel 双库与 annuity 三份 DDL，**零 DDL、零 annuity 改动**；语义即「检查点/恢复点」，与挂起点恢复载荷吻合 |
| 静态配置引用 | 用 `StepDefinition.externalConfigRef` 承载 | 字段已存在（spec §7.1 要求），无需新增 |
| `FILE_*` 网关 `externalTaskId` | SPI 新增**带返回值的 default 方法**，kernel 实现返回真实 taskId，annuity Mock 不重写返回 null | `invokeUpload`/`outboundApi.generate` 已拿到 taskId 却丢弃，改动面小；default 兼容 annuity，零 annuity 改动 |
| 推进方式 | 阶段 B 拆成 **6 个小批次**（26–31），阶段 4 拆 4 个（32–35），阶段 5 拆 4 个（36–39） | 每批可独立验证、独立提交，回归面可控 |

---

## 2. 派发载荷持久化方案（详细）

### 2.1 问题本质

4 个挂起 Handler 目前在 `advanceStep` 的 `transactionTemplate` **事务内直调外部网关**（`triggerAsyncParsing` / `triggerOutboundGeneration` / `startApproval`），随后返回 `SUSPEND_ASYNC_WAIT`。阶段 B 要求把外部调用移到**事务外**（AFTER_COMMIT 监听器 / 恢复任务）。

移出事务后，派发器必须能**重建调用参数**。参数分两类：

| 参数 | 来源 | 可否从事务外重建 |
|---|---|---|
| `formId`、`sourceFileId` | `app.formId()` / `app.requireFileForParsing()` | 可（load app） |
| `parseTemplateId`、`splitRules` | `configGateway.getFormParsingConfig(context)` | 可（context 由 app 重建） |
| `startApproval(app, requestNo)` | `app` + 协作 `requestNo` | 可 |
| `outboundFileTaskId`、`outboundTargetTemplateId` | `BusinessMetaContext.extensionFacts`（业务 SPI 注入） | **不可可靠重建** |
| `supplementalData` | 当前恒为 `Map.of()` | 无需持久化 |

关键事实：当前唯一的 `BusinessFactExtractor` 实现（`AnnuityFactExtractor`）注入的是 `businessType/customerNo/productNo/planNo/planType/initialContribution/hasForeignInvestment`，**并不注入 `outboundFileTaskId` / `outboundTargetTemplateId`**。即出站链路的凭证来源本身缺失（`OutboundGenerationHandler.resolveRequiredFact` 在真实业务里会抛「业务上下文缺少出站声明」）。

### 2.2 候选方案对比

| 方案 | 改动面 | 是否违反冻结 | 可恢复性 | 评价 |
|---|---|---|---|---|
| A. `ExternalInteraction` 新增 `dispatch_payload` 列 | DO/Converter/聚合 + 5 份 DDL（含 annuity 3 份） | **违反** | 完整 | 最贴合 spec，但与冻结冲突，且为示例服务做无效适配 |
| B. **复用 `StepExecution.checkpoint_ref`（推荐）** | 仅 domain 加 1 个行为方法 + application 读写 | **不违反** | 完整（紧凑 JSON） | 列已存在、语义吻合、零 DDL、零 annuity |
| C. 纯内存领域事件传递参数 | 不加列、不改 DDL | 不违反 | **崩溃不可恢复** | 违反 spec §9.2「不能只依赖内存监听」 |
| D. 参数全部由 app+配置重建，不持久化 | 最小 | 不违反 | 部分（出站凭证丢失） | 出站参数不可重建，不满足 §9.2 可恢复要求 |

**推荐方案 B**：用 `StepExecution.checkpoint_ref`（`VARCHAR(255)`）存**挂起点恢复载荷**的紧凑 JSON。它是 spec §7.2 明确允许的「可选的检查点或结果引用」，§16.4 也允许检查点用 JSON。

### 2.3 按交互类型的差异化载荷

| 类型 | 是否需持久化 | 载荷内容 | 理由 |
|---|---|---|---|
| `FILE_PARSE` | **不持久化** | 无 | formId/sourceFileId/parseTemplateId/splitRules 均可从 app + `configGateway` 重建 |
| `APPROVAL` | **不持久化** | 无 | `startApproval(app, requestNo)` 参数全可重建 |
| `FILE_OUTBOUND` | **持久化到 `checkpoint_ref`** | `{"fileTaskId":"...","targetTemplateId":"..."}`（紧凑 JSON） | 两个凭证不可重建，且都是短 ID，远小于 255 长度 |

> `supplementalData` 当前恒为空 `Map.of()`，不纳入载荷；若未来业务需要，再评估 255 长度是否升级为独立列（届时单独评审 annuity 处置）。

### 2.4 需要新增的（最小）结构

- `StepExecution` 增加**一个领域行为**：`recordCheckpoint(String checkpointRef)`（或语义更准的 `recordDispatchPayload(String)`），内部校验长度 ≤ 255、调 `markUpdated`。domain 无框架依赖，仅字符串操作。
- 派发器读载荷路径：`ExternalInteraction.stepExecutionId()` → `stepExecutionRepository.load(...)` → `checkpointRef()` → 解析 JSON。
- 序列化用**手工拼接/解析紧凑 JSON**（避免在 application/domain 引入 Jackson 依赖；可放在 infrastructure 提供一个小工具，或由 application 注入已有 `ObjectMapper`）。**不要在 domain 用 JSON 注解**。

### 2.5 为什么不推荐直接复用 `ExternalInteraction.result_ref`

`result_ref` 语义是「外部结果引用」（回调成功后写入），把派发载荷塞进去会污染状态语义、且回调时会被覆盖。`checkpoint_ref` 才是「恢复点」的正确载体。

---

## 3. `FILE_*` 网关 `externalTaskId` 补全方案

### 3.1 现状

- `FileServiceIntegrationGateway.invokeUpload(...)` 已拿到 `result.data().fileTaskId()` 但 `void` 丢弃；
- `triggerOutboundGeneration(...)` 已拿到 `outboundApi.generate(...)` 的 `OutboundFileIdResponse.fileTaskId()` 但 `void` 丢弃；
- `ApprovalIntegrationGateway.startApproval(...)` 已返回 `String instanceId`（无需改）。

### 3.2 约束

`ExternalInteraction.markWaitingCallback(externalTaskId, operator)` **要求 externalTaskId 非空**。而 SPI 现有 `void` 方法被 annuity 的 `MockFileIntegrationGateway` 实现，**直接改签名会让 annuity 编译失败**（违反冻结）。

### 3.3 方案：SPI 加 default 带返回值重载

- 在 `FileIntegrationGateway` 新增：
  - `default String triggerAsyncParsingAndGetTaskId(...)` → 默认调用旧 `triggerAsyncParsing(...)` 并返回 `null`；
  - `default String triggerOutboundGenerationAndGetTaskId(...)` → 默认调用旧 `triggerOutboundGeneration(...)` 并返回 `null`。
- kernel 的 `FileServiceIntegrationGateway` **覆盖**这两个 default 方法，返回真实 taskId。
- annuity 的 `MockFileIntegrationGateway` **不重写** → 返回 `null`。

### 3.4 拿不到 taskId 时的降级（重要）

派发器在 `externalTaskId == null` 时**不能** `markWaitingCallback`（会抛不变量异常）。处理：

- 协作保持 `DISPATCHING`，不强行置 `WAITING_CALLBACK`；
- 由既有 `ExternalInteractionRecoveryService.reclaimStaleDispatching` 把滞留 `DISPATCHING` 回收为 `UNKNOWN`，再走对账；
- 这正好覆盖 annuity 示例「只 mock、不返回 taskId」的能力边界，**不破坏 E2E**（E2E 主流程不经 SUSPEND）。

---

## 4. 当前实现与规格的差异分析

### 4.1 已实现且与规格一致（无需再动）

- 三类核心概念：`ProcessDefinition` / `StepExecution` / `ExternalInteraction` 领域模型 + 持久化 + 运行时接线。
- 宏观状态机：`ApplicationStatus` 九态 + 合法迁移表 + 终态约束（§8.1）；`StepExecutionState`（§8.2）、`ExternalInteractionStatus` 八态（§8.3）、`BusinessForm`/`BusinessBatch` 生命周期（§8.4/§8.5）。
- 可靠事件：Repository→`EventBus` 统一发布（§13.2）、Outbox 序列化修复（批次 8）、发布失败事务回滚（§9.1）。
- 幂等：`EventReceipt`（§7.4）+ 3 个入站监听器短路（§9.2 阶段 C 第 2 步）。
- 并发三层：`DistributedLock` 短锁（§9.3 第一层）+ 乐观锁 + 唯一约束。
- `IdService` 号段接入 + `t_id_generator` DDL（§16.3）。
- 版本化定义存储与发布（§7.1/§19.1，组合式）。
- 恢复机制四类动作 + `@Scheduled` 兜底 + AFTER_COMMIT 尽快认领（§19.2 部分）。
- 阶段 C 回调关联（`CallbackCorrelationService`）+ 完成等待步骤门面（`completeWaitingStep`）。
- file/approval 两侧 requestNo 幂等闭环 + 按 requestNo 查询 API（§11.4/§12.3）。

### 4.2 已实现但有偏差 / 过渡形态（需后续收敛）

| # | 偏差 | 现状 | 规格要求 | 收敛批次 |
|---|---|---|---|---|
| D1 | 表名 | `t_step_execution` | `t_business_step_execution`（§16.1） | 36 |
| D2 | `request_no` 列宽 | kernel 侧两表仍 `VARCHAR(64)`（`t_step_execution` 已在批次 14 扩 128，`t_business_external_interaction` 仍 64） | 统一 128（评审 #7） | 36 |
| D3 | 命名 | `StepExecutionStatus`（Handler 返回值）vs `StepExecutionState`（持久化） | §8.2 本身区分「Handler 返回值」与持久化状态；批次 32 评估后决策**保留两者、不重命名公开 SPI**，以显式边界 + `StepExecutionSemanticsContractTest` 契约测试收敛（见设计文档 §23.33） | 32（已收敛） |
| D4 | `requestNo` 来源 | 编排器内部派生（`app:step:attempt` / `SE-<stepExecutionId>`） | §15.1 由推进 Command 携带 | 35（CQE 化） |
| D5 | `InteractionType` 推断 | 按 `mainProcessor` 名关键字推断 | `StepDefinition.actionType()` 显式声明（§7.1） | 31 |
| D6 | 出站凭证来源 | `extensionFacts`（无 extractor 注入） | 应由入站 FILE_PARSE 协作的 `externalTaskId` 或定义配置提供 | 30 |
| D7 | 申请单等待态 | 挂起时未置 `WAITING_EXTERNAL` | 外部受理后进入 `WAITING_EXTERNAL`（§9.2 阶段 B 第 4 步） | 31 |
| D8 | 遗留字段 | `BusinessApplication.planMaterials/packageFile/businessExtension/parsedJsonFileId` 仍在聚合 | §6.2 迁移到可选材料能力/业务服务聚合 | 37 |
| D9 | 领域事件数量 | 仅 6 个（`ApplicationSpawnedEvent` 等） | §13.1 列 14 个（缺 StepStarted/StepCompleted/ApplicationCompleted/ExternalInteractionCompleted 等） | 按需，阶段 4/5 |
| D10 | 应用服务拆分 | 未按 §17 建 `StepExecutionAppService`/`ExternalInteractionAppService`/`EventCallbackAppService`/`ProcessDefinitionAppService` | 单类单变化原因 | 阶段 4 拆分 |
| D11 | 锁释放时机 | 锁早于外层 `@Transactional` 提交释放 | §9.1 提交后释放 | 阶段 5 精修 |
| D12 | `FileIntegrationGateway` 暴露 `file-types.FileId` | SPI 仍引用 file 类型 | §11.1 用 kernel `FileRef`/字符串 | 阶段 4 |
| D13 | `PROCESSING` 存量状态 | 仍兼容归一 | 阶段 5 清理移除 | 38 |

### 4.3 尚未实现（本计划主体）

- **阶段 B 事务外派发器**（§9.2 阶段 B）：认领 → DISPATCHING → 事务外调网关 → 存 externalTaskId → WAITING_CALLBACK/UNKNOWN。
- 部分成功 → `RETRY_WAIT`（评审 #8 默认策略未接线）。
- 重试退避策略（§18.2）。
- 多实例恢复任务行认领（§19.2「优先数据库行认领或唯一状态更新」）。
- 批次统计对账重算（§6.4/§19.2）。
- 监控指标（§19.3）。
- MDC 统一携带 `applicationId/stepExecutionId/requestNo/businessType/traceId`（§14）。
- `shared-cache-starter` 多级缓存接入（§14/§19.1）。
- 条件表达式语法预校验（§7.1/§23.4）。
- 扩展动作 beanName 存在校验（§19.1）。
- CQE 化 / Adapter Converter 化（§15/§17）。
- starter 显式接入与生产配置缺失 fail-fast（§14）。
- §21.3 外部协作验收测试、§21.5 最终验收。

---

## 5. 推进计划（拆小批次）

> 每批给出：目标、改动文件、红灯测试点、实现要点、验证命令、规模估计。
> 通用验证基线：目标模块测试 + `mvn test -pl business-core-kernel/business-core-infrastructure -am` + `mvn test -pl business-core-kernel/business-core-application -am` + `mvn test -pl arch-tests -am -Dtest=ArchitectureInvariantTest` + `mvn test-compile -DskipTests` + annuity E2E（`AnnuityEndToEndTest`）+ `git diff --check`。

### 阶段 B：事务外派发（批次 26–31）

#### 批次 26 — StepExecution 增加检查点写入行为（纯领域）
- 目标：让 `StepExecution.checkpointRef` 可被业务写入，作为挂起点恢复载荷。
- 改动：`StepExecution.java`（新增 `recordDispatchPayload(String)`，长度 ≤255 校验 + `markUpdated`）+ `StepExecutionTest.java`。
- 红灯测试：写入后 `checkpointRef()` 返回载荷；超长抛 `DomainException`；调用后版本/操作人更新。
- 实现要点：domain 无框架依赖，仅字符串处理；不改 DDL。
- 规模：2 文件。

#### 批次 27 — FILE_* 网关补 externalTaskId（SPI default + kernel 实现）
- 目标：`FileIntegrationGateway` 能返回真实 fileTaskId，annuity Mock 零改动。
- 改动：`FileIntegrationGateway.java`（2 个 default 带返回值方法）+ `FileServiceIntegrationGateway.java`（覆盖返回 taskId）+ `FileServiceIntegrationGatewayTest`（新增/调整）。
- 红灯测试：default 方法默认返回 null；kernel 实现返回真实 taskId。
- 实现要点：`invokeUpload` 改返回 `result.data().fileTaskId()`；`triggerOutboundGeneration` 返回 `OutboundFileIdResponse.fileTaskId()`；旧 `void` 方法保留。
- 规模：3–4 文件。

#### 批次 28 — 派发器骨架（ExternalInteractionDispatcher）
- 目标：新增应用层 `ExternalInteractionDispatcher`，认领 PENDING_DISPATCH → DISPATCHING →（本批先只到 DISPATCHING，不调网关）。
- 改动：`ExternalInteractionDispatcher.java`（新）+ `ExternalInteractionDispatcherTest.java`（新）+ `ExternalInteractionRecoveryService`（复用认领，不重复实现）。
- 红灯测试：认领后状态 DISPATCHING；单条失败隔离；空表 no-op。
- 实现要点：与批次 17 的 `claimPendingDispatch` 区分职责——本类是「真正派发」入口，下一批接网关；不重复造扫描逻辑。
- 规模：3–4 文件。

#### 批次 29 — FILE_PARSE 改为登记意图 + 派发器接线（可重建参数）
- 目标：`DefaultFormParsingHandler` 不再事务内直调网关，改为只返回挂起；派发器在事务外重建参数并调用。
- 改动：`DefaultFormParsingHandler.java`、`ExternalInteractionDispatcher.java`（接 FILE_PARSE）、`FlowOrchestrationService`（SUSPEND 分支参数准备）+ 测试。
- 红灯测试：Handler 不再调用 `fileIntegrationGateway`；派发器调用了网关并推进到 WAITING_CALLBACK（taskId 非空时）。
- 实现要点：FILE_PARSE 参数从 app + `configGateway` 重建，不持久化载荷。
- 规模：5–6 文件。

#### 批次 30 — FILE_OUTBOUND 载荷持久化 + 出站参数重建
- 目标：出站参数不再依赖 `extensionFacts`，改从**入站 FILE_PARSE 协作的 `externalTaskId`**（或定义配置 `externalConfigRef`）重建；不可重建的写入 `checkpoint_ref`。
- 改动：`OutboundGenerationHandler.java`、`ArchiveSubmissionHandler.java`、`ExternalInteractionDispatcher.java`（接 FILE_OUTBOUND）+ `StepExecution`（读载荷）+ 测试。
- 红灯测试：FILE_OUTBOUND 载荷写入 `checkpoint_ref`；派发器读载荷调网关；拿不到 taskId 时停留 DISPATCHING（不抛不变量异常）。
- 实现要点：解决 D6「凭证来源缺失」——优先从入站协作的 externalTaskId 取，而非 extensionFacts。
- 规模：6–7 文件。

#### 批次 31 — 申请单 WAITING_EXTERNAL 接线 + 派发后推进
- 目标：外部受理后把申请单置 `WAITING_EXTERNAL`（§9.2 阶段 B 第 4 步），并让 `completeWaitingStep` 的归位逻辑有真实数据来源。
- 改动：`FlowOrchestrationService`（SUSPEND/派发后 `app.waitForExternal()`）、`ExternalInteractionDispatcher`（受理后推进协作 + 置等待态）+ 测试。
- 红灯测试：派发成功后 `app.status()==WAITING_EXTERNAL`、协作 `WAITING_CALLBACK`；回调 `APPLIED` 时门面归位推进。
- 实现要点：解决 D7； InteractionType 仍暂按 mainProcessor 推断，D5 的 actionType 显式声明在阶段 4 收敛。
- 规模：4–5 文件。

### 阶段 4：收敛可选能力与公共 API（批次 32–35）

#### 批次 32 — `StepExecutionStatus`（Handler 返回值）与 `StepExecutionState` 统一决策
- 目标：明确两者职责边界，必要时将 Handler 返回值改为独立结果对象或收敛命名，消除 §8.2 语义分叉。
- 改动：评估后小步重命名/引入 `StepHandlerResult` + 受影响 Handler/编排器 + 测试。
- 红灯测试：命名统一后行为不变。
- 规模：4–6 文件（取决于决策）。

#### 批次 33 — 批次统计对账重算（§6.4/§19.2）
- 目标：提供按批次下全部申请重算计数与终态的对账能力，消费端按 eventId 幂等。
- 改动：`BusinessBatchAppService`/`BusinessBatch`（重算）+ 恢复任务接入 + 测试。
- 红灯测试：手工造成计数偏差后，重算恢复一致；重复事件不重复计数。
- 规模：4–6 文件。
- 状态：已完成（设计文档 §23.34）。「消费端按 eventId 幂等」以全量重算的绝对赋值幂等性覆盖；增量事件消费端与 `EventReceiptGateway` 短路留阶段 5。

#### 批次 34 — 条件表达式语法预校验（§7.1/§23.4）
- 目标：`ConditionEvaluationGateway` 增加 `validate`，SpEL/Aviator/QLExpress 三实现支持，定义发布时校验全部步骤条件。
- 改动：`ConditionEvaluationGateway` + 3 实现 + `ProcessDefinitionPublishService` + 测试。
- 红灯测试：非法表达式发布被拒绝；合法表达式通过。
- 规模：5–6 文件。
- 状态：已完成（设计文档 §23.35）。`validate` 只做语法检查，不求值；跨引擎表达式语法不归一化。

#### 批次 35 — CQE 化与推进 Command 携带 requestNo（§15.1/§17）
- 目标：推进类 Command 统一携带 `applicationId/expectedStep/requestNo/operator`，`requestNo` 不再仅由编排器内部派生。
- 改动：`business-core-api` Command + `FlowOrchestrationService` 入口 + Adapter Converter + 测试。
- 红灯测试：同一 requestNo 重复推进只执行一次；缺 expectedStep 拒绝。
- 规模：6–8 文件（可能再拆两批）。
- 状态：已完成（设计文档 §23.36）。采用新增 `advanceApplication(CQE)` 入口而非改 `advanceStep(ApplicationId)` 签名，避免改 annuity 与重载歧义；其余推进类 Command 的 CQE 化留阶段 5。

### 阶段 5：清理与生产验收（批次 36–39）

#### 批次 36 — 表名与列宽统一 + eventId().value()
- 目标：`t_step_execution` → `t_business_step_execution`（DO/TableDef/5 份 DDL），`request_no` 统一 128；`JdbcEventStore` 等 `eventId().toString()` 改 `value()`（含存量双读兼容）。
- 改动：DO/DDL/表名 + `shared-event-core` 若干 + 测试。
- 红灯测试：重命名后读写正常；event_id 写入为 ULID 字符串。
- 规模：6–8 文件（注意 annuity 复制脚本处置需单独确认，或只改 kernel 双库）。
- 状态：**部分完成**（设计文档 §23.37）。已落地列宽 128 + `eventId().value()` 统一（零 annuity 改动）；表名统一因必须改 annuity 4 文件、与冻结约束冲突，暂缓待开放点 3 确认。

#### 批次 37 — 遗留字段迁移（§6.2）
- 目标：`planMaterials`/`packageFile` 迁移到可选材料能力，`BusinessExtension`/`parsedJsonFileId` 迁出核心聚合。
- 改动：`BusinessApplication` + 业务服务自有聚合 + Converter + 测试。
- 红灯测试：核心聚合不再持有这些字段，行为不变。
- 规模：6–8 文件（可能拆两批）。

#### 批次 38 — starter 显式接入与生产 fail-fast（§14）
- 目标：显式接入 web/cache/logging/permission，生产配置缺失 fail-fast；MDC 统一携带。
- 改动：各 starter 依赖与自动配置 + MDC + 测试。
- 规模：4–6 文件。

#### 批次 39 — §21.3 外部协作验收测试 + §21.5 最终验收
- 目标：补相同 requestNo 重复推进、并发推进、乐观锁冲突、重复/乱序事件、超时 UNKNOWN、响应丢失对账、服务重启恢复、文件部分成功等契约测试。
- 改动：新增契约/集成测试（不改生产代码）。
- 规模：4–8 测试文件。

---

## 6. 全局约束与验收基线

- **annuity 冻结**：批次 26–31 均零 annuity 改动；批次 36 若涉及 annuity 复制脚本需先单独确认（或只改 kernel 双库并记录漂移）。
- **TDD**：每批先写失败测试，再实现，再跑测试；不把红灯状态提交入库。
- **验证命令（每批必跑）**：
  - `mvn test -pl business-core-kernel/business-core-application -am`
  - `mvn test -pl business-core-kernel/business-core-infrastructure -am`
  - `mvn test -pl arch-tests -am -Dtest=ArchitectureInvariantTest -Dsurefire.failIfNoSpecifiedTests=false`
  - `mvn test-compile -DskipTests`
  - `mvn test -pl annuity-service/annuity-starter -am -Dtest=AnnuityEndToEndTest -Dsurefire.failIfNoSpecifiedTests=false`
  - `git diff --check`
- **提交规范**：Conventional Commits（`feat(core-*)` / `fix(...)`），scope 按 09-提交信息规范；多文件写 Body。
- **E2E 零回归信号**：`Failed to save event` = 0、异步 NPE = 0、回执短路日志正常。

---

## 7. 风险与回滚

| 风险 | 缓解 |
|---|---|
| 出站参数来源缺失导致 FILE_OUTBOUND 派发失败 | 批次 30 先从入站协作重建，失败则停留 DISPATCHING 并分类，不盲目推进 |
| 拿不到 taskId 导致无法 WAITING_CALLBACK | 保持 DISPATCHING → 恢复任务回收 UNKNOWN → 对账，不绕过不变量 |
| 改 SPI 破坏 annuity Mock | 一律用 default 方法/重载，annuity 不重写即零改动 |
| 批次过大回归 | 每批 ≤6–8 文件、独立提交推送，发现问题可单批 `git revert` |

---

## 8. 待你确认的开放点

1. 是否采纳「复用 `StepExecution.checkpoint_ref`」作为派发载荷载体（方案 B）？
2. 阶段 B 是否按 26–31 的细分推进，还是进一步合并/调整顺序？
3. 批次 36 的 annuity 复制脚本是否允许临时解冻同步表名/列宽，还是只改 kernel 双库并记录漂移？（**仍待确认**：批次 36 已按零 annuity 子集推进，表名统一需改 annuity 3 份 DDL + E2E 断言共 4 文件，否则 E2E 必然失败；当前保留 `t_step_execution` 并记录漂移。）

