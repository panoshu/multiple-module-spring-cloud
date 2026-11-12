# 生产级业务流程内核详细设计

> 状态：设计已确认，分阶段实施中（实施进度见 §23）
>
> 日期：2026-11-08
>
> 工作分支：`refactor/business-core-kernel-production`
>
> 范围：`business-core-kernel`、`demo-shared`、`file-service`、`approval-service`
>
> 说明：`annuity-service` 仅作为早期示例，不作为本设计的模型来源或兼容约束。

---

## 1. 设计结论摘要

本设计将 `business-core-kernel` 定义为：

> 面向相似业务办理场景的、持久化的长流程控制内核。kernel 统一负责流程定义、流程状态、步骤执行、外部协作、可靠事件、并发幂等和技术基础设施；业务服务只提供业务规则、业务数据和业务扩展实现。

核心决策如下：

1. **保留 `BusinessApplication` / `BusinessForm` / `BusinessBatch`，但必须重新定位，不能继续按当前职责无序膨胀。**
2. `BusinessApplication` 直接演化为流程实例聚合根，**不再新增一个与其重复的 `ProcessInstance` 聚合根**。
3. `BusinessForm` 是可选的表单输入与文件协作聚合，不是所有业务流程的必经模型。
4. `BusinessBatch` 是可选的批次分组与统计聚合，不是所有流程实例的强制父聚合。
5. 新增 `ProcessDefinition`、`StepExecution`、`ExternalInteraction` 三类核心概念，补齐配置版本、执行历史、外部任务关联和恢复能力。
6. 保留现有六个 Maven 模块，不引入 BPMN，不新增独立流程微服务。
7. 流程可靠性采用“持久化状态机 + 本地短事务 + Outbox + 消费幂等 + 乐观锁/短分布式锁”，不承诺跨系统强一致或虚假的 exactly-once。
8. `file-service` 和 `approval-service` 通过防腐层接入。外部 API DTO 只允许出现在 infrastructure 或入站事件适配器，不进入 domain 和 application 核心模型。
9. 统一使用 `demo-shared` 的 id、event、cache、lock、web、logging、permission、client 等能力，不在 kernel 重复建设技术底座。

---

## 2. 背景与当前问题

当前 kernel 已具备六边形分层、三个业务聚合、SPI、策略注册、条件求值和公共 Handler，但主要生产问题不是“缺少类”，而是缺少长流程所需的运行语义：

- 流程定义没有版本，在途实例可能受配置变更影响；
- 流程只有当前步骤，没有独立的步骤执行记录；
- 文件、审批调用没有统一的外部协作记录和恢复模型；
- 任意步骤跳转缺少领域状态机约束；
- 本地事务边界与外部副作用边界不清晰；
- Spring 本地事件发布与 `shared-event-starter` 的 EventBus 发布发生分叉；
- 重复推进、重复回调、响应丢失和服务重启缺少系统化处理；
- 业务类型、步骤编码和部分上下文字段仍然封闭或带有特定业务语义；
- 表单解析链路、材料持久化和批次统计没有形成可靠闭环。

因此，本次重构不以零散修补为目标，而以建立完整的生产级流程控制模型为目标。

---

## 3. 第一性原理与职责边界

### 3.1 为什么需要 kernel

不同业务类型会变化的是业务数据、业务规则、步骤组合和外部输入输出；不应重复变化的是流程推进、状态持久化、并发控制、回调幂等、可靠事件、审计追踪和通用协作方式。

kernel 的本质是：

> 固化控制逻辑，隔离业务变化，而不是共享尽可能多的业务字段。

### 3.2 长流程的基本约束

文件解析、人工审批等步骤可能持续数秒到数天，因此必须接受：

1. 流程状态必须持久化，服务重启后可以继续。
2. 外部文件和审批副作用不能随本地数据库事务回滚。
3. HTTP 超时不等于外部任务未创建，响应结果可能处于未知状态。
4. MQ、Redis Pub/Sub 和 HTTP 重试都可能造成重复通知。
5. 多实例并发推进同一流程时，必须防止重复执行和状态覆盖。
6. 配置发布后不能悄然改变在途实例的执行路径。

由此得到生产级内核的最低要求：

> 持久化状态机、定义版本、步骤执行记录、外部协作记录、可靠事件、幂等消费、短事务和恢复机制。

### 3.3 上下文职责

| 上下文 | 应负责 | 不应负责 |
|---|---|---|
| business-core-kernel | 流程定义引用、流程实例、步骤执行、外部协作、通用 API、可靠事件、并发幂等、审计追踪 | 业务明细、文件内容、审批节点规则、具体业务校验算法 |
| 业务服务 | 业务聚合、业务规则、业务 Handler、事实提取、业务数据持久化、文件标准数据到业务数据的映射 | 自建流程引擎、ID/事件/锁/缓存等底座、直接操作文件存储、自建审批状态机 |
| file-service | 文件元数据、上传下载、模板解析、拆分校验、标准数据、出站文件、文件任务事件 | 业务流程状态、业务聚合、业务审批规则 |
| approval-service | 审批流定义与版本、审批流匹配、审批实例、节点执行、审批结果事件 | 业务流程推进、业务数据持久化 |
| demo-shared | ID、事件、缓存、锁、Web、日志、权限、客户端等通用技术能力 | 业务流程规则和业务模型 |

---

## 4. 目标与非目标

### 4.1 目标

- 新业务类型只通过流程定义和业务 SPI 接入，不修改 kernel。
- 流程实例在服务重启后可恢复。
- 同一流程并发推进只产生一次有效状态跃迁。
- 同一外部事件重复投递不会造成重复推进。
- 文件和审批任务可关联、可追踪、可恢复、可对账。
- 流程定义升级不影响在途实例。
- 流程状态、步骤执行和 Outbox 事件具备明确事务边界。
- 通用 API、权限、异常、日志、缓存和 ID 统一复用 shared 能力。
- 保留六个 Maven 模块和现有轻量 SPI，不建设通用 BPMN 平台。

### 4.2 非目标

- 不实现可视化流程设计器。
- 不支持任意 BPMN 语义和复杂规则平台。
- 不通过 XA/2PC 追求跨服务强一致。
- 不让 kernel 持有业务服务的全部业务数据。
- 不为了兼容示例服务而保留错误的流程语义。
- 不在本次流程重构中强制迁移所有存量字符串主键。

---

## 5. 总体架构

```text
业务服务 API / BFF
        │ CQE
        ▼
business-core-adapter
  Controller / MapStruct / Permission / Session
        │
        ▼
business-core-application
  流程启动、推进、回调、重试、查询编排
  ┌───────────────────────────────────────────┐
  │ FlowOrchestrationService                  │
  │ StepExecutionAppService                   │
  │ ExternalInteractionAppService             │
  │ EventCallbackAppService                   │
  └───────────────────────────────────────────┘
        │
        ▼
business-core-domain
  BusinessApplication（流程实例）
  BusinessForm（可选表单）
  BusinessBatch（可选批次）
  ProcessDefinition（不可变流程定义）
  StepExecution（步骤执行）
  ExternalInteraction（外部协作）
  SPI / Repository / Gateway / DomainEvent
        │
        ▼
business-core-infrastructure
  Repository / DO / Mapper / MapStruct
  file-service ACL / approval-service ACL
  shared id/event/cache/lock/logging/client
```

### 5.1 六个 Maven 模块职责

| 模块 | 目标职责 |
|---|---|
| `business-core-types` | 仅保留 ID 类型和跨层共享标识，不承载非 ID 值对象 |
| `business-core-domain` | 聚合、值对象、状态机、领域事件、SPI、Repository 和 Gateway 接口 |
| `business-core-api` | 对外 API、Command/Query/Response，不包含领域对象 |
| `business-core-application` | CQE 用例编排、事务、并发、回调和恢复，不包含外部服务 DTO |
| `business-core-adapter` | Controller、入站事件适配、MapStruct、会话和权限入口 |
| `business-core-infrastructure` | 持久化、外部服务防腐层、shared starter 集成和自动配置支撑 |

依赖约束：

- `types → domain → api → application → adapter`；
- `domain → infrastructure`；
- domain 不依赖 Spring、数据库、JSON 注解、file/approval 类型；
- application 不依赖 `file-api`、`approval-api`；
- file/approval DTO 仅在 infrastructure 或明确的入站事件适配器中使用。

---

## 6. 存量聚合保留决策

### 6.1 决策总表

| 聚合 | 是否保留 | 新定位 | 必须剥离或调整 |
|---|---|---|---|
| `BusinessApplication` | 保留 | 流程实例聚合根，维护当前流程一致性状态 | 任意步骤跳转、业务明细、材料字段、业务扩展大对象、空不变量 |
| `BusinessForm` | 保留为可选聚合 | 表单输入及文件协作生命周期 | 文件存储/解析内部规则、所有流程强制经过表单、断开的裂变逻辑 |
| `BusinessBatch` | 保留为可选聚合 | 批次分组、进度统计和批次终态 | 统计无事件来源、所有流程强制关联批次、错误的状态不变量 |

### 6.2 BusinessApplication：保留为流程实例

不再新增 `ProcessInstance` 聚合，避免同一概念双模型并存。

建议保留字段：

- `ApplicationId id`；
- `BatchId batchId`，可空；
- `FormId formId`，可空；
- `BusinessTypeCode businessType`；
- `BusinessNo businessNo`；
- 通用业务上下文的最小集合；
- `DefinitionVersion definitionVersion`；
- `StepCode currentStep`；
- `ApplicationStatus status`；
- 创建、完成、更新时间和乐观锁版本。

建议移除或迁移：

- `planMaterials`、`packageFile` 等材料状态，迁移到可选材料能力；
- `BusinessExtension` 多态业务大对象，迁移到业务服务自有聚合，通过 `applicationId` 关联；
- `parsedJsonFileId` 等单次外部任务关联，迁移到 `ExternalInteraction`；
- `Map<String,Object>` 形式的持久化业务扩展字段，不作为 kernel 的长期数据容器。

核心行为改为：

- `start()`；
- `waitForUser()`；
- `waitForExternal()`；
- `completeCurrentStep(nextStep)`；
- `fail()` / `retry()`；
- `reject()` / `withdraw()` / `cancel()`；
- `complete()`。

不再向外部公开无约束的 `transit(nextStep)`。

### 6.3 BusinessForm：保留为可选输入聚合

适用于“上传表单后启动一个或多个流程实例”的业务。

职责：

- 保存表单与业务上下文、批次和文件引用的关系；
- 维护 `WAITING_UPLOAD → UPLOADED → PARSING → PARSED` 等生命周期；
- 关联文件解析外部协作；
- 根据流程定义的实例粒度创建流程实例。

不负责：

- 文件内容存储；
- Excel 解析、模板识别和拆分校验；
- 文件服务内部子任务状态；
- 业务明细落库规则。

流程定义需要声明：

- `startMode = DIRECT | FORM_DRIVEN`；
- 表单驱动时的 `instanceGranularity = SINGLE | PER_SUB_TASK`；
- `batchMode = REQUIRED | NONE`。

`BusinessApplication` 的创建工厂必须接收并保存 `formId` 和与 `batchMode` 匹配的 `batchId`。当 `batchMode=REQUIRED` 时，`batchId` 必填且必须指向有效批次，禁止绕过批次直接创建申请；当 `batchMode=NONE` 时，申请不强制关联批次。

### 6.4 BusinessBatch：保留为可选统计聚合

适用于确有批次语义的业务，不作为所有流程的强制父级；但在 `batchMode=REQUIRED` 的业务中，批次是申请的强制前置聚合。

职责：

- 维护批次基本信息；
- 维护流程实例总数、成功数、失败数；
- 根据统计计算批次终态；
- 提供批次进度查询。

批次与申请的关系及创建顺序：

- 一个 `BusinessBatch` 在其生命周期内可以关联一个或多个 `BusinessApplication`；新批次创建后允许暂时没有申请；
- `BusinessApplication` 只通过 `batchId` 引用批次，`BusinessBatch` 不持有申请对象列表，避免形成大聚合；
- 批次驱动业务必须先创建 `BusinessBatch(CREATED)`，再在该批次下创建申请；
- 创建申请前必须校验批次存在，且处于 `CREATED` 或 `PROCESSING` 状态；批次进入完成、失败或取消类终态后禁止新增申请；
- 首个申请创建后，批次由 `CREATED` 转为 `PROCESSING`；一个申请只能归属一个批次，归属确定后不得随意变更；
- 表单按子任务裂变为多个申请时，这些申请可以归属同一批次，并分别保存各自的申请 ID 和表单关联。

统计更新规则：

- 由流程实例创建、完成、失败等事件驱动；
- 消费端按事件 ID 幂等更新；
- 与流程实例之间采用最终一致，不跨聚合强行保持同事务；
- 提供按流程实例重算批次的对账能力，防止事件异常造成永久偏差。

---

## 7. 新增核心领域模型

### 7.1 ProcessDefinition：不可变流程定义

`ProcessDefinition` 是配置模型/值对象，不是流程实例的一部分。

核心字段：

- `BusinessTypeCode businessType`；
- `DefinitionVersion version`；
- `StartMode startMode`；
- `InstanceGranularity instanceGranularity`；
- `BatchMode batchMode`：`REQUIRED | NONE`，决定申请是否必须归属已存在且可接收申请的批次；
- `StepDefinition initialStep`；
- `List<StepDefinition> steps`。

每个 `StepDefinition` 包含：

- `StepCode stepCode`；
- `TaskType taskType`：`SYSTEM_TASK | USER_TASK`；
- `ActionType actionType`：`BUSINESS | FILE_PARSE | FILE_OUTBOUND | APPROVAL | NONE`；
- 主 Handler；
- 前置校验、明细处理、后置动作；
- 合法后继步骤及条件；
- 超时、重试和失败策略；
- 文件/审批步骤所需配置引用。

发布约束：

- 已发布定义不可变，修改必须产生新版本；
- 历史版本必须可按 `businessType + version` 读取；
- 流程实例启动时固定定义版本；
- 启动时校验步骤编码唯一、初始步骤存在、后继步骤存在、Handler 存在、外部步骤配置完整；
- 条件表达式错误按配置错误处理，不能静默转成 `false` 跳过关键校验。

`ProcessDefinitionGateway` 替代当前过宽的 `BusinessConfigGateway` 中流程定义职责。材料规则、表单规则等其他配置不得继续全部塞入同一 Gateway。

### 7.2 StepExecution：步骤执行聚合

每次步骤尝试都有独立记录，不将完整执行历史塞入 `BusinessApplication`。

核心字段：

- `StepExecutionId id`；
- `ApplicationId applicationId`；
- `DefinitionVersion definitionVersion`；
- `StepCode stepCode`；
- `int attempt`；
- `String requestNo`；
- `StepExecutionStatus status`；
- 开始、结束时间；
- 错误码、错误摘要；
- 可选的检查点或结果引用。

唯一性约束：

- `(application_id, step_code, attempt)` 唯一；
- `request_no` 唯一；
- 同一流程同一时刻最多一个运行中的步骤执行。

### 7.3 ExternalInteraction：外部协作聚合

统一管理文件和审批交互。

核心字段：

- `ExternalInteractionId id`；
- `ApplicationId applicationId`；
- `StepExecutionId stepExecutionId`；
- `InteractionType type`：`FILE_PARSE | FILE_OUTBOUND | APPROVAL`；
- `String requestNo`；
- `String externalTaskId`；
- `ExternalInteractionStatus status`；
- 外部结果引用；
- 重试次数、最后错误、下次重试时间；
- 创建、更新、完成时间。

关联规则：

- `requestNo` 默认使用 `stepExecutionId` 的稳定字符串表示；
- 同一 `requestNo` 重试不得重新生成；
- `externalTaskId` 保存 fileTaskId、approvalInstanceId 等；
- 外部事件优先按 `requestNo` 关联，其次按 `externalTaskId` 关联；
- 缺少任一关联信息时不得盲目推进流程。

### 7.4 EventReceipt：事件消费回执

`EventReceipt` 是基础设施持久化模型，不一定建模为领域聚合。

用途：

- 记录 `eventId + consumer`；
- 防止文件、审批和内部流程事件重复消费；
- 保存首次处理时间、处理结果和必要错误摘要。

唯一约束：`(event_id, consumer)`。

---

## 8. 状态机设计

### 8.1 BusinessApplication 状态

建议状态：

- `DRAFT`
- `RUNNING`
- `WAITING_USER`
- `WAITING_EXTERNAL`
- `FAILED`
- `COMPLETED`
- `REJECTED`
- `WITHDRAWN`
- `CANCELLED`

主要合法迁移：

| 当前状态 | 允许目标状态 | 触发行为 |
|---|---|---|
| DRAFT | RUNNING / CANCELLED | 启动 / 取消 |
| RUNNING | WAITING_USER / WAITING_EXTERNAL / RUNNING / COMPLETED / FAILED / CANCELLED | 步骤推进或失败 |
| WAITING_USER | RUNNING / CANCELLED / WITHDRAWN | 用户提交 / 取消 / 撤回 |
| WAITING_EXTERNAL | RUNNING / FAILED / REJECTED / WITHDRAWN / CANCELLED | 外部回调或协作失败 |
| FAILED | RUNNING / CANCELLED | 显式重试 / 取消 |
| COMPLETED / REJECTED / WITHDRAWN / CANCELLED | 无 | 终态 |

约束：

- `REJECTED` 只能由审批拒绝或明确的业务拒绝行为进入；
- `WITHDRAWN` 只能由合法撤回行为进入；
- 重试必须创建新的 `StepExecution.attempt`，不能覆盖历史；
- 完成步骤时必须校验 `expectedStep` 和定义版本；
- 所有更新行为调用 `markUpdated(operator)` 并校验不变量。

### 8.2 StepExecution 状态

- `PENDING`
- `RUNNING`
- `WAITING_EXTERNAL`
- `SUCCEEDED`
- `FAILED`
- `RETRY_WAIT`
- `SKIPPED`
- `CANCELLED`

Handler 返回值只表达领域结果，不直接决定数据库状态。应用服务根据结果、异常类型和重试策略完成状态迁移。

### 8.3 ExternalInteraction 状态

- `PENDING_DISPATCH`
- `DISPATCHING`
- `WAITING_CALLBACK`
- `SUCCEEDED`
- `FAILED`
- `RETRY_WAIT`
- `UNKNOWN`
- `CANCELLED`

`UNKNOWN` 用于“请求已发出但结果无法确定”的场景，例如 HTTP 超时。进入 `UNKNOWN` 后先查询或对账，不能直接重复创建外部任务。

### 8.4 BusinessForm 状态

- `WAITING_UPLOAD`
- `UPLOADED`
- `PARSING`
- `PARSED`
- `PARSE_FAILED`
- `DELETED`

只有合法状态才能确认上传、发起解析、处理解析结果或删除。重复确认上传和重复解析回调必须幂等。

### 8.5 BusinessBatch 状态

沿用：

- `CREATED`
- `PROCESSING`
- `COMPLETED`
- `PARTIAL_FAILED`
- `FAILED`
- `CANCELLED`

批次生命周期与统计不变量：

- 新批次以 `CREATED` 启动，允许 `totalApplicationCount=0`；零申请批次保持 `CREATED`，不能按“全部申请均已完成”误判为 `COMPLETED`；
- 首个申请创建成功后批次进入 `PROCESSING`，该状态变化必须幂等；
- 只有 `CREATED` 或 `PROCESSING` 批次可以接收新申请，终态批次禁止新增申请；
- 所有计数非负，且 `successCount + failedCount <= totalApplicationCount`；
- 只有 `totalApplicationCount > 0` 且全部申请到达终态后，才能根据成功和失败计数进入 `COMPLETED`、`PARTIAL_FAILED` 或 `FAILED`；
- 全部成功对应 `COMPLETED`，全部失败对应 `FAILED`，成功与失败并存对应 `PARTIAL_FAILED`；仍在处理中的批次不得进入上述终态；
- 申请创建事件只增加总数一次，申请终态事件只增加成功或失败计数一次，重复事件通过消费回执去重；
- `CREATED` 或 `PROCESSING` 批次可以取消；批次取消与流程实例取消通过明确的应用用例协同，不能只改一个状态字段；
- 计数和状态允许与流程实例最终一致，但必须提供按批次下全部申请重算计数和终态的对账能力。

---

## 9. 步骤执行与事务设计

### 9.1 同步业务步骤

推荐执行顺序：

1. Adapter 将请求转换为 `AdvanceApplicationCommand`。
2. 应用服务获取 `kernel:application:{applicationId}` 短锁。
3. 加载流程实例，校验当前步骤、定义版本、实例状态和数据库版本。
4. 根据 `requestNo` 检查是否为重复请求。
5. 创建 `StepExecution`。
6. 执行同步 Handler 和同步扩展链。
7. 根据执行结果更新流程实例和步骤执行。
8. 在同一本地事务中保存：
   - `BusinessApplication`；
   - `StepExecution`；
   - 聚合领域事件对应的 Outbox 记录。
9. 提交事务后释放锁，由 shared-event 执行后续分发。

约束：

- 同步 Handler 不得调用 file/approval 等远程服务；
- 同步 Handler 可以调用本业务服务 Repository，与主流程共用本地数据源事务；
- 跨数据源场景不引入 XA，业务 Handler 必须幂等并定义补偿或恢复方式；
- 长时间批量处理不得持有流程锁和单个长事务，应采用分页、检查点或可恢复步骤。

### 9.2 文件/审批异步步骤

#### 阶段 A：创建协作任务

本地事务中：

1. 创建 `StepExecution`；
2. 创建 `ExternalInteraction(PENDING_DISPATCH)`；
3. 保存流程实例和 Outbox；
4. 提交事务。

此时尚未调用外部服务，崩溃后可从 `PENDING_DISPATCH` 恢复。

#### 阶段 B：事务外派发

1. 本地 `AFTER_COMMIT` 监听器或恢复任务认领待派发协作；
2. 将协作标记为 `DISPATCHING`；
3. 在事务外调用 file-service 或 approval-service；
4. 外部明确受理后，保存 `externalTaskId`，协作进入 `WAITING_CALLBACK`，流程实例进入 `WAITING_EXTERNAL`；
5. 外部明确拒绝时，进入 `FAILED` 或 `RETRY_WAIT`；
6. 网络超时时进入 `UNKNOWN`，启动查询/对账，不直接重建任务。

事件用于触发尽快执行，`ExternalInteraction` 表用于保证最终可恢复，不能只依赖内存监听。

#### 阶段 C：回调推进

1. 入站适配器将外部 DTO 转为 kernel Command；
2. 按 `eventId + consumer` 写入消费回执，重复事件直接返回成功；
3. 获取流程实例短锁；
4. 按 `requestNo` 或 `externalTaskId` 加载协作记录；
5. 校验协作类型、业务类型、当前步骤和等待状态；
6. 更新协作及步骤执行；
7. 推进或终止流程实例；
8. 同事务保存状态、回执和 Outbox。

乱序或不符合当前状态的回调不丢弃，应记录并按“可重试、可对账或人工处理”分类处理。

### 9.3 并发控制

采用三层保护：

1. `DistributedLock`：减少同实例并发执行，锁只覆盖短临界区；
2. 数据库乐观锁：最终防止旧版本覆盖新版本；
3. 唯一约束：防止重复 `requestNo`、重复步骤尝试和重复事件回执。

文件、审批等待期间不得持锁。当前 shared-lock 无看门狗自动续期，长任务必须显式拆分临界区。

---

## 10. SPI 与流程管线调整

### 10.1 保留的 SPI

- `StepActionHandler`：同步业务主处理；
- `StepExtensionAction`：同步前置、明细和后置扩展；
- `BusinessFactExtractor`：提取业务事实，返回不可变结果；
- `ConditionEvaluationGateway`：条件求值；
- `ProcessDefinitionGateway`：按业务类型和版本加载流程定义；
- `FileIntegrationGateway`：文件协作防腐层；
- `ApprovalIntegrationGateway`：审批协作防腐层。

### 10.2 SPI 约束

- 业务 SPI 不暴露 file/approval DTO；
- `StepActionHandler` 和 `StepExtensionAction` 不再承担异步线程派发；
- 异步通知优先建模为领域事件，由 shared-event 在提交后分发；
- `BusinessMetaContext` 不再作为可跨线程共享并随意修改的 Map 容器；
- 上下文输入和扩展输出分离，每次步骤执行的输入输出可追踪；
- 关键校验中的表达式错误必须显式失败，不能作为条件不满足静默跳过。

### 10.3 StepPipelineExecutor 定位

保留为轻量同步扩展链执行器，负责：

- 顺序执行；
- 条件过滤；
- 结果收集；
- critical/non-critical 错误处理。

移除：

- `CompletableFuture.runAsync` 形式的异步副作用；
- 无持久化记录的线程派发；
- 跨线程共享并修改同一上下文。

文件、审批步骤由专用系统 Handler 创建 `ExternalInteraction`，而不是直接完成远程调用。

---

## 11. file-service 集成设计

### 11.1 kernel 侧端口

`FileIntegrationGateway` 使用 kernel 自己的请求和结果模型，覆盖：

- 申请上传/下载凭证；
- 创建文件解析任务；
- 查询文件任务；
- 分页读取标准解析数据；
- 创建出站文件任务。

接口中不再暴露 `file-types.FileId`，使用 kernel 的 `FileRef` 或字符串值对象。

### 11.2 文件解析流程

1. `BusinessForm` 确认上传；
2. kernel 创建 `FILE_PARSE` 类型的 `ExternalInteraction`；
3. file-service 完成解析、拆分和校验；
4. kernel 消费解析完成事件并保存标准数据引用；
5. 根据流程定义创建一个或多个 `BusinessApplication`；
6. 业务 Handler 通过标准数据读取端口获取数据并映射为业务实体；
7. 流程进入后续业务步骤。

file-service 返回的是标准数据，不负责创建业务聚合；kernel 负责流程实例创建，业务服务负责业务数据落库。

### 11.3 文件出站流程

1. 当前步骤声明 `FILE_OUTBOUND`；
2. kernel 创建外部协作并提交目标模板和业务数据引用；
3. file-service 生成文件并发布完成事件；
4. kernel 保存文件结果引用并推进流程；
5. 后续外部系统提交由对应集成上下文负责，不混入 kernel 文件状态机。

### 11.4 文件契约补强

生产环境需要保证：

- 创建任务请求携带稳定 `clientRequestNo/requestNo`；
- 完成事件携带 `requestNo`，或提供按 `requestNo` 查询任务的 API；
- 创建任务在服务端按 `requestNo` 幂等；
- 事件具有稳定 `eventId`；
- 明确失败事件与部分成功语义。

---

## 12. approval-service 集成设计

### 12.1 kernel 侧端口

`ApprovalIntegrationGateway` 覆盖：

- 匹配并启动审批；
- 查询审批实例；
- 保存审批实例关联；
- 将通过、驳回、撤回结果转换为 kernel 内部结果。

审批流匹配由 approval-service 负责，kernel 只提供业务类型和审批匹配所需上下文。

### 12.2 审批流程

1. 当前步骤声明 `APPROVAL`；
2. kernel 创建 `APPROVAL` 类型的 `ExternalInteraction`；
3. approval-service 匹配审批流并创建审批实例；
4. kernel 保存 `approvalInstanceId` 并进入 `WAITING_EXTERNAL`；
5. 审批通过：完成当前步骤并推进；
6. 审批驳回：按流程定义进入 `REJECTED` 或指定回退步骤；
7. 审批撤回：进入 `WITHDRAWN`；
8. 重复审批结果事件按事件回执幂等处理。

### 12.3 审批契约补强

当前 `StartApprovalRequest` 缺少独立请求幂等标识。生产接入需要：

- 增加 `requestNo`，或建立等价的唯一业务约束；
- 支持按 `requestNo` 查询，解决响应丢失后的对账；
- 审批结果事件携带 `requestNo` 或确保 `businessNo` 与流程实例一一对应；
- 对同一流程实例的同一审批步骤禁止创建多个有效审批实例。

---

## 13. 事件设计

### 13.1 主要领域事件

- `ApplicationStartedEvent`；
- `StepStartedEvent`；
- `StepCompletedEvent`；
- `StepFailedEvent`；
- `ApplicationWaitingEvent`；
- `ApplicationCompletedEvent`；
- `ApplicationRejectedEvent`；
- `ApplicationWithdrawnEvent`；
- `FormUploadedEvent`；
- `FormParsedEvent`；
- `BatchStatusChangedEvent`；
- `ExternalInteractionRequestedEvent`；
- `ExternalInteractionCompletedEvent`。

领域事件使用过去时态、record、`DomainEvent` 和静态 `of()` 工厂。

### 13.2 发布所有权

统一为：

1. 聚合注册领域事件；
2. Repository 在同一应用事务中保存聚合；
3. Repository 通过 `EventBus` 发布事件；
4. shared-event 负责事件落库、本地分发、远程投递和恢复；
5. Repository 清理聚合事件队列。

应用服务不再重复执行 `eventBus.publish()`，也不再由 Repository 直接使用 `ApplicationEventPublisher` 抢先清空事件。

### 13.3 集成事件

- 领域事件到集成事件的 Converter 放在 infrastructure；
- file/approval 入站 DTO 在 adapter/infrastructure 转换为 kernel Command；
- application 只消费内部 Command 和领域对象；
- 所有外部消费者使用 `eventId + consumer` 幂等。

---

## 14. demo-shared 能力接入

| Starter | kernel 使用方式 | 生产约束 |
|---|---|---|
| `shared-id-starter` | 生成流程、步骤、协作等强类型 ID | 重试沿用原 requestNo，不重新生成 |
| `shared-event-starter` | Outbox、事件分发、恢复任务 | 状态与事件同事务，消费端幂等 |
| `shared-cache-starter` | 缓存不可变流程定义和只读规则 | key 包含业务类型和版本，不缓存流程当前状态 |
| `shared-lock-starter` | 流程推进和回调的短临界区互斥 | 不覆盖远程 IO；生产缺少 Redis 时 fail-fast |
| `shared-web-starter` | 通用 API、统一异常、链路追踪 | 由 adapter 显式依赖，不依赖宿主偶然提供 |
| `shared-logging-starter` | 步骤耗时、HTTP 日志、脱敏、MDC | 日志不替代 StepExecution 审计 |
| `shared-permission-starter` | 功能权限、数据范围、会话验签 | 生产缺少验签器或密钥时启动失败 |
| `shared-client-starter` | file/approval HTTP 客户端、异常和日志 | 外部 DTO 只在防腐层出现 |

建议在 MDC 中统一携带：

- `applicationId`；
- `stepExecutionId`；
- `requestNo`；
- `businessType`；
- `traceId`。

---

## 15. 对外用例与 API 边界

### 15.1 Command

- `StartApplicationCommand`；
- `AdvanceApplicationCommand`；
- `RetryStepCommand`；
- `CancelApplicationCommand`；
- `ConfirmFormUploadCommand`；
- `DeleteFormCommand`；
- `HandleFileParsedCommand`；
- `HandleOutboundGeneratedCommand`；
- `HandleApprovalResultCommand`。

推进类 Command 至少包含：

- `applicationId`；
- `expectedStep`；
- `requestNo`；
- `operator`；
- 必要的业务输入引用或业务载荷。

### 15.2 Query

- `GetApplicationDetailQuery`；
- `FindApplicationListQuery`；
- `GetBatchProgressQuery`；
- `GetFormStatusQuery`；
- `GetStepExecutionHistoryQuery`。

### 15.3 接入约束

- ApplicationService 入参统一使用 CQE；
- Adapter 不直接构造领域对象，统一通过 MapStruct Converter；
- API 层不暴露聚合根、Repository、DO 或外部服务 DTO；
- 功能权限通过 `@RequirePermission`；
- 当前用户是否有权操作指定业务对象由业务访问守卫和数据范围共同校验；
- 外部事件回调不伪造用户 Session，使用受信事件通道和关联信息校验。

---

## 16. 持久化设计

### 16.1 表清单

#### 存量表调整

- `t_business_application`
  - 增加 `definition_version`、`business_no` 等流程字段；
  - 保留 `batch_id`、`form_id` 并确保创建时正确写入；
  - 外部任务关联逐步迁移到外部协作表；
  - 材料和业务扩展数据逐步迁出核心表。

- `t_business_form`
  - 保留表单生命周期及业务关联；
  - 文件任务关联优先通过 `ExternalInteraction` 查询。

- `t_business_batch`
  - 保留批次统计；
  - 增加必要的事件对账和重建查询支持。

#### 新增表

`t_business_step_execution`：

- 主键及通用字段；
- `application_id`、`definition_version`、`step_code`、`attempt`；
- `request_no`、`status`；
- 开始/结束时间、错误码、错误摘要、结果引用；
- 唯一索引 `(application_id, step_code, attempt)`；
- 唯一索引 `request_no`；
- 普通索引 `(application_id, status)`。

`t_business_external_interaction`：

- 主键及通用字段；
- `application_id`、`step_execution_id`、`interaction_type`；
- `request_no`、`external_task_id`、`status`；
- 重试次数、下次重试时间、最后错误、结果引用；
- 唯一索引 `request_no`；
- 外部任务 ID 非空时的外部类型与任务 ID 唯一约束；
- 索引 `(status, next_retry_time)`、`application_id`。

`t_business_event_receipt`：

- 主键及通用字段；
- `event_id`、`consumer`、处理状态、处理时间、错误摘要；
- 唯一索引 `(event_id, consumer)`。

### 16.2 通用字段

所有业务表包含：

- `created_by`、`create_time`；
- `updated_by`、`update_time`；
- `deleted`、`version`。

时间戳由应用/领域行为维护，不使用 ORM 自动填充。

### 16.3 主键决策

当前三个存量聚合使用字符串强类型 ID，而项目数据库规范要求新表主键使用 BIGINT。为控制本次流程重构范围：

- 存量三表暂保留现有字符串 ID，作为明确的兼容例外；
- 新增步骤执行、外部协作和事件回执表使用 BIGINT 主键，由 `IdService` 生成；
- `requestNo`、业务编号和外部任务 ID 使用独立字符串列；
- 存量 VARCHAR 主键是否迁移另行专项评审，不在本次流程重构中隐式变更。

### 16.4 JSON 使用边界

JSONB/JSON 只用于：

- 非关系化的执行结果摘要；
- 检查点；
- 必要的不可变配置快照。

需要查询、关联、唯一约束或状态机控制的字段必须独立成列，不能全部塞入 JSON。

---

## 17. 建议包结构

```text
com.example.core.domain
├── business
│   ├── aggregate.root
│   │   ├── BusinessApplication
│   │   ├── BusinessForm
│   │   └── BusinessBatch
│   ├── aggregate.valueobject
│   ├── event
│   └── repository
└── engine
    ├── aggregate.root
    │   ├── StepExecution
    │   └── ExternalInteraction
    ├── aggregate.valueobject
    │   ├── ProcessDefinition
    │   ├── StepDefinition
    │   ├── BusinessTypeCode
    │   ├── StepCode
    │   └── DefinitionVersion
    ├── repository
    ├── gateway
    ├── spi
    ├── service
    └── event
```

Application 层按用例拆分：

- `FlowOrchestrationService`：流程推进门面，不承载全部实现；
- `StepExecutionAppService`：步骤执行与重试；
- `ExternalInteractionAppService`：外部派发、回调和对账；
- `EventCallbackAppService`：事件回执与回调编排；
- `BusinessFormAppService`：可选表单用例；
- `BusinessBatchAppService`：可选批次用例；
- `ProcessDefinitionAppService`：定义加载、校验和缓存。

单个类只承担一个变化原因，流程门面不重新演化为 God Class。

---

## 18. 错误处理分类

### 18.1 业务失败

例如业务校验不通过、审批驳回：

- 不重试；
- 记录明确业务错误码；
- 按流程定义进入失败、驳回或指定回退步骤；
- 对用户返回可理解的业务提示。

### 18.2 可重试系统失败

例如暂时性网络错误、数据库暂时不可用：

- 进入 `RETRY_WAIT`；
- 保存重试次数和下次重试时间；
- 使用退避策略；
- 超过上限后进入人工处理或对账。

### 18.3 结果未知

例如请求已发出后超时：

- 进入 `UNKNOWN`；
- 先按 `requestNo` 查询外部任务；
- 未确认前不得直接重复创建任务。

### 18.4 并发与重复请求

- 乐观锁冲突返回明确的并发错误，由调用方重新加载后决定；
- 相同 `requestNo` 且已完成直接返回原结果；
- 相同 `requestNo` 但请求内容不同必须拒绝；
- 重复外部事件按回执返回成功，不重复推进。

新增错误码在现有 `CORE.DOMAIN`、`CORE.APP`、`CORE.INFRA` 序列后追加，不复用已有编码。

---

## 19. 缓存、恢复与运维设计

### 19.1 缓存

只缓存：

- 已发布流程定义；
- 只读规则配置；
- 权限等 shared 组件自身数据。

不缓存：

- 当前流程状态；
- 当前步骤执行状态；
- 外部协作状态。

### 19.2 恢复任务

需要以下恢复能力：

- 认领 `PENDING_DISPATCH` 的外部协作；
- 回收长时间停留在 `DISPATCHING` 的协作；
- 处理 `RETRY_WAIT` 到期的步骤和协作；
- 对 `UNKNOWN` 协作执行外部查询和对账；
- 重算批次统计；
- shared-event 自身的事件恢复任务保持启用。

多实例恢复任务优先使用数据库行认领或唯一状态更新，避免再增加粗粒度全局锁。

### 19.3 监控指标

- 各状态流程实例数；
- 各状态步骤执行数；
- 外部协作等待时长；
- 重试次数和 UNKNOWN 数量；
- 重复回调次数；
- 乐观锁冲突次数；
- Outbox 待恢复数量；
- 文件/审批调用成功率和耗时。

---

## 20. 重构实施顺序

### 阶段 0：冻结设计与外部契约

- 评审本文；
- 确定流程定义存储与发布方式；
- 确认 file/approval 的 requestNo、幂等和查询契约；
- 建立现有 API、数据库和事件兼容清单。

### 阶段 1：建立领域模型与状态机

- 保留并重定位三个存量聚合；
- 新增 ProcessDefinition、StepExecution、ExternalInteraction；
- 引入 BusinessTypeCode、StepCode、DefinitionVersion；
- 建立合法状态迁移和不变量；
- 先完成领域单元测试，再修改持久化。

### 阶段 2：建立可靠性底座

- 统一 Repository → EventBus；
- 状态、步骤记录、外部协作和 Outbox 事务化；
- 接入 IdService、DistributedLock 和数据库乐观锁；
- 建立事件回执、重试和恢复机制；
- 移除不受控异步线程和重复监听器。

### 阶段 3：重构 file/approval 协作

- 收敛 Gateway 接口及外部 DTO；
- 建立 PENDING_DISPATCH → WAITING_CALLBACK → SUCCEEDED 链路；
- 补齐 requestNo、幂等和响应丢失对账；
- 接通表单上传、解析、流程实例创建和后续推进；
- 合并重复出站事件监听。

### 阶段 4：收敛可选能力与公共 API

- 修复或迁移材料能力；
- 建立批次事件统计和对账；
- ApplicationService 全面 CQE 化；
- Adapter 全面 Converter 化；
- 显式接入 web、cache、logging、permission 等 starter；
- 生产配置缺失时 fail-fast。

### 阶段 5：清理与生产验收

- 删除被新模型替代的字段、方法和默认空实现；
- 完成数据库迁移和兼容处理；
- 补齐 ArchUnit、并发、恢复和外部契约测试；
- 完成性能、超时和大文件场景验证；
- 更新模块文档和接入指南。

---

## 21. 测试策略与验收标准

### 21.1 领域测试

- 每个聚合的合法和非法状态迁移；
- 终态不可继续推进；
- 重试生成新 attempt；
- 表单和批次不变量；
- 流程定义发布校验。

### 21.2 应用与并发测试

- 相同 requestNo 重复推进只执行一次；
- 两个节点并发推进只产生一次有效跃迁；
- 乐观锁冲突不会覆盖新状态；
- 重复事件不会重复推进；
- 乱序事件不会错误推进。

### 21.3 外部协作测试

- 文件/审批明确失败进入可重试或失败状态；
- 请求超时进入 UNKNOWN；
- 响应丢失后可按 requestNo 对账；
- 服务重启后待派发任务可恢复；
- 审批通过、驳回、撤回分别进入正确状态；
- 文件部分成功不会误判为全部成功。

### 21.4 架构测试

- domain 不依赖 Spring、Web、JSON、数据库和 file/approval 类型；
- application 不依赖 file/approval DTO；
- ApplicationService 入参只有 CQE；
- Adapter 不直接构造领域对象；
- types 只包含 ID 类型；
- 跨服务只依赖 API 模块。

### 21.5 最终验收

1. 新业务只增加流程定义和业务 SPI，不修改 kernel。
2. 服务重启后流程可恢复。
3. 并发推进和重复回调不造成重复跃迁。
4. 文件/审批任务可追踪、可重试、可对账。
5. 流程定义升级不影响在途实例。
6. 状态、执行历史和事件可审计。
7. 通用技术能力全部通过 shared starter 提供。
8. 端到端测试不预置流程实例、不禁用自动推进机制。

---

## 22. 实施纪律

- 所有实现、重构和缺陷修复均在独立工作分支进行；本次工作分支为 `refactor/business-core-kernel-production`。
- 设计评审通过前不开始实现代码。
- 实施按阶段拆小步提交，先写失败测试，再修改实现。
- 不在流程重构中顺手改变无关模块行为。
- file/approval 契约变更需要独立评审和契约测试。
- 每一阶段完成后执行对应模块测试和架构测试，并更新本文状态。

---

## 23. 实施进度台账

> 记录实现批次与 §20 阶段的对应关系。实现批次按「先写失败测试 → 再实现 → 跑测试」小步推进。

### 23.1 阶段进度总览

| §20 阶段 | 状态 | 说明 |
|---|---|---|
| 阶段 0 冻结设计与外部契约 | 设计已确认，部分契约细节待冻结 | 本文定稿；`batchMode`、批次创建顺序与状态规则已补入 §6.3/§6.4/§8.5。流程定义存储与发布方式、file/approval 的 requestNo 幂等与查询契约在阶段 3 接入前独立评审（§11.4/§12.3） |
| 阶段 1 建立领域模型与状态机 | 进行中 | 批次/申请重定位、版本化流程定义模型与运行时接管、`definitionVersion` 持久化、`batchMode` 运行时校验、`BusinessApplication` 宏观状态机、`StepExecution` 与 `ExternalInteraction` 聚合领域模型及持久化/双库 DDL 均已完成。三类核心概念（ProcessDefinition/StepExecution/ExternalInteraction）领域模型、持久化与运行时接线齐备；`EventReceipt` 已落地。阶段 1 主线完成，剩余恢复/派发能力归入阶段 2/3 |
| 阶段 2 建立可靠性底座 | 进行中 | 领域事件发布所有权已统一到 Repository → `EventBus`（§13.2）；Outbox 序列化 P0 已修复（EventStore 自持注册 `JavaTimeModule` 的专用 mapper），落库真正生效；不受控异步线程已移除（受控 `applicationTaskExecutor` + 只读传递 app，§10.3）；发布失败事务回滚已落地（§9.1 业务状态与 Outbox 严格同事务）。`IdService` 号段生成已接入并补齐 `t_id_generator` DDL，`StepExecution` 运行时接线已完成。事件消费回执与入站幂等已落地（§7.4）。`ExternalInteraction` 运行时接线（阶段 A）已完成。外部协作重试恢复机制（四类动作 + `@Scheduled` 兜底 + AFTER_COMMIT 尽快认领，§19.2）已落地。`DistributedLock` 短临界区互斥已接入（§9.3 第一层，弱依赖可选注入）。§9.2 完整事务外派发与阶段 3 回调链路、其他业务服务本地事件发布失败隔离待后续批次 |
| 阶段 3 重构 file/approval 协作 | 未开始 | — |
| 阶段 4 收敛可选能力与公共 API | 未开始 | — |
| 阶段 5 清理与生产验收 | 未开始 | — |

### 23.2 实现批次

| 批次 | 提交 | 内容 | 验证 |
|---|---|---|---|
| 批次 1 | `79f74b0a` | 批次先行与申请归属闭环：重定位 `BusinessBatch`/`BusinessApplication`、新增 `ApplicationSource`、批次状态机与计数不变量、`markUpdated()` 乐观锁版本适配、表单裂变链路 | 35 个目标测试通过；全仓主代码编译通过；架构不变量测试通过 |
| 批次 2 | `69575979` | 版本化流程定义**纯领域模型**：`BusinessTypeCode`/`StepCode`/`DefinitionVersion` 强类型编码，`StartMode`/`InstanceGranularity`/`BatchMode`/`TaskType`/`ActionType` 枚举，`StepTransition`/`RetryPolicy`/`StepDefinition`/`ProcessDefinition` 不可变值对象及发布校验，`ProcessDefinitionGateway` SPI，`CORE.DOMAIN.0008-0013` 错误码 | 领域模块 93 个测试全部通过（新增 23 个）；全仓主代码编译通过；架构不变量 7 项通过；`git diff --check` 通过 |
| 批次 3 | `5095c9d3` | 版本化流程定义**运行时接管**：默认 `ProcessDefinitionGateway`（过渡期委托存量路由、按业务类型+版本缓存不可变定义）、`StepDefinitionResolver` 按实例固定版本解析、`StepRouteConfigAssembler` 无损还原执行配置；`FlowOrchestrationService`/`StepAutoAdvanceListener` 改按定义解析；`BusinessApplication` 增加 `definitionVersion` 与双库 DDL/DO/Converter；`batchMode` 运行时校验（`NONE` 可不关联批次）；`CORE.APP.0007-0008` 错误码 | 领域 97、应用 92、基础设施 40、架构不变量 7、annuity E2E 6 全部通过；全仓 `test-compile` 通过；`git diff --check` 通过 |
| 批次 4 | `71d5e1ca` | 条件表达式求值**显式失败**：追加 `CORE.INFRA.0006`；SpEL/Aviator/QLExpress 三个网关的求值异常由静默 `return false` 改为抛 `SystemException`（保留 cause 与表达式上下文），空表达式短路保持不变；`StepPipelineExecutor` 不改，`false` 仍为 SKIPPED、异常则中断事务；补 SpEL 网关测试与管道异常传播测试 | 领域 97、基础设施 49、架构不变量 7、annuity E2E 6 全部通过；全仓 `test-compile` 通过；`git diff --check` 通过 |
| 批次 5 | `791d7b33` | `BusinessApplication` **宏观状态机收敛**：`ApplicationStatus` 扩展到 §8.1 的 9 态并实现合法迁移表与终态约束（保留 `PROCESSING` 存量兼容并归一为 `RUNNING`）；`transit()` 收敛为 `completeCurrentStep()`（终态不可推进、同节点幂等、后继非空校验），补齐 `start`/`waitForUser`/`waitForExternal`/`fail`/`retry`/`cancel`/`complete`/`reject`/`withdraw`，所有更新统一调 `markUpdated` 并记录 `completeTime`；`FlowOrchestrationService` 适配 | 领域 117、应用 94、基础设施 49、架构不变量 7、annuity E2E 6 全部通过；全仓 `test-compile` 通过；`git diff --check` 通过 |
| 批次 6 | `7aa17a22` | **统一领域事件发布所有权**（§13.2）：`AbstractBusinessRepository` 改注入 `EventBus` 替代 `ApplicationEventPublisher`，`publishDomainEvents` 统一经 EventBus 发布（Outbox 落库 + 本地分发 + 远程投递）并清理队列；移除 3 个应用服务共 7 处手动 `publish`/`clear` 及不再需要的 `EventBus` 注入；适配 kernel 与 annuity 共 6 个 Repository 子类构造；补仓储事件发布测试 | 领域 117、应用 94、基础设施 51、架构不变量 7、annuity E2E 6 全部通过；全仓 `test-compile` 通过；`git diff --check` 通过 |
| 批次 7 | `08cb15ca` | **StepExecution 步骤执行聚合（纯领域模型）**：新增 `StepExecutionId`（BIGINT/SEGMENT，§16.3）、`StepExecutionState` 执行状态枚举（与现有 Handler 返回值 `StepExecutionStatus` 区分命名）、`StepExecution` 聚合根（`create`/`reconstitute`/`retryFrom` 与状态行为）、`StepExecutionRepository` 契约（`findByRequestNo`/`findByApplicationAndStep`/`findRunningByApplication`）；唯一性语义与领域测试。不接线运行时、不注册事件、不改 DDL | 领域 136、应用 94、基础设施 51、架构不变量 7、annuity E2E 6 全部通过；全仓 `test-compile` 通过；`git diff --check` 通过 |
| 批次 8 | `c88105d6` | **修复 Outbox 序列化 P0**（批次 7 发现）：`EventAutoConfiguration.eventStore()` 改为自持专用 `ObjectMapper`（显式注册 `JavaTimeModule` + `DddJacksonModule`），不再注入宿主 `@Primary` mapper；`jackson-datatype-jsr310` 依赖由 `test` 提升为 compile 以支撑生产期显式注册；补 `EventAutoConfigurationTest` 2 项（配置解耦契约 + H2 真实落库含 `LocalDateTime` 的领域事件） | 事件 starter 3、event-core 14、架构不变量 7、annuity E2E 6 全部通过；全仓 `test-compile` 通过；E2E 日志 `Failed to save event` 归零；`git diff --check` 通过 |
| 批次 9 | `38db9c3c` | **移除不受控异步线程**（§10.3，批次 8 发现的 NPE 实证）：`StepPipelineExecutor` 去掉 `@RequiredArgsConstructor` 改显式构造，注入 `@Qualifier("applicationTaskExecutor") Executor`；`dispatchAsyncAction` 以 `taskExecutor.execute` 替代 `CompletableFuture.runAsync`（ForkJoinPool.commonPool），并把 `app` 只读传入而非 `null`，修复 `AnnuityMaterialPreparedNotificationAction` 的 NPE；`StepPipelineExecutorTest` 补 3 项（app 非 null、异常隔离、执行线程为注入 Executor） | 应用 97、架构不变量 7、annuity E2E 6 全部通过；全仓 `test-compile` 通过；E2E 日志 `app is null` NPE 由 2 归零、`Failed to save event` 保持 0、异步动作完成日志 2 次；`git diff --check` 通过 |
| 批次 10 | `a3b09fd5` | **发布失败事务回滚**（§9.1 原子性）：移除 `AbstractBusinessRepository.publishDomainEvents` 的 try/catch，`eventBus.publish` 失败直接传播使应用服务 `@Transactional` 连同业务写入与 Outbox 一并回滚；失败时事件队列保留、首个事件失败即中断后续发布；仓储测试断言由「失败被隔离」改为「失败即抛、队列保留、快速失败」 | 基础设施 51、架构不变量 7、annuity E2E 6 全部通过；全仓 `test-compile` 通过；E2E 日志 `Failed to save event` 保持 0、无回归；`git diff --check` 通过 |
| 批次 11 | `d9d77ab4` | **StepExecution 持久化落地**（§16.3/§17）：新增 `StepExecutionDO`（BIGINT 主键、`KeyType.None`）、`StepExecutionMapper`、手写 `StepExecutionConverter`（`reconstitute` 往返）、`StepExecutionRepositoryImpl`（复用 `AbstractBusinessRepository`，提供 `findByRequestNo`/`findByApplicationAndStep`/`findRunningByApplication` 的 QueryWrapper 查询）；kernel PG/MySQL 与 annuity PG/MySQL/H2 共 5 份 DDL 增加 `t_step_execution` 与唯一索引 `(application_id,step_code,attempt)`、`request_no` 及 `(application_id,status)` 索引；不接线编排器 | 基础设施 59（新增 8）、架构不变量 7、annuity E2E 6 全部通过；全仓 `test-compile` 通过、H2 建表脚本经 E2E 实执行成功；`git diff --check` 通过 |
| 批次 12 | `c4dfde52` | **ExternalInteraction 外部协作聚合（纯领域模型）**（§7.3/§8.3/§16.3）：新增 `ExternalInteractionId`（BIGINT/SEGMENT）、`InteractionType`（FILE_PARSE/FILE_OUTBOUND/APPROVAL）、`ExternalInteractionStatus` 八态与合法迁移表（`UNKNOWN` 须先对账确认、不得直接重派）、`ExternalInteraction` 聚合根（`requestNo` 由 stepExecutionId 稳定派生且重试沿用、externalTaskId 关联回退、全字段 `reconstitute`）、`ExternalInteractionRepository` 契约；补 16 项领域测试。不接线运行时、不注册事件、不改 DDL | 领域 152（新增 16）、基础设施 59、架构不变量 7、annuity E2E 6 全部通过；全仓 `test-compile` 通过；`git diff --check` 通过 |
| 批次 13 | `477c8e12` | **ExternalInteraction 持久化落地**（§16.1/§17）：新增 `ExternalInteractionDO`/`Mapper`/手写 `Converter`（`reconstitute` 往返、两个 LONG ID、枚举名持久化）、`ExternalInteractionRepositoryImpl`（复用 `AbstractBusinessRepository`，4 个 QueryWrapper 查询）；kernel PG/MySQL 与 annuity PG/MySQL/H2 共 5 份 DDL 增加 `t_business_external_interaction` 与 `request_no` 唯一索引、`(interaction_type,external_task_id)` 联合唯一索引、`(status,next_retry_time)` 与 `application_id` 索引；不接线运行时 | 基础设施 68（新增 9）、架构不变量 7、annuity E2E 6 全部通过；全仓 `test-compile` 通过、H2 建表脚本经 E2E 实执行成功；`git diff --check` 通过 |
| 批次 14 | `feb509c7` | **补号段表并接线 StepExecution 运行时**（§9.1/§16.3）：5 份 DDL 补 `t_id_generator` 并把 `t_step_execution.request_no` 扩到 VARCHAR(128)；`FlowOrchestrationService.advanceStep` 注入 `StepExecutionRepository` + `IdService`，每次推进创建 `StepExecution`（PENDING→RUNNING→SUCCEEDED/WAITING_EXTERNAL/FAILED），与申请单状态在同一 `transactionTemplate` 事务保存；`requestNo` 由「申请单+步骤+attempt」稳定派生；修正 `StepExecutionId`/`ExternalInteractionId` 的 `@IdDefinition(format="%s")`；annuity 新增 `@Repository` 注册类并加 E2E 落库断言 | 应用 100（新增 3）、基础设施 68、架构不变量 7、annuity E2E 6 全部通过；E2E 断言 `t_step_execution` ≥4 行、`t_id_generator` ≥1 行（全链路真实落库）；`Failed to save event`/异步 NPE/`Value too long` 均归零；全仓 `test-compile` 通过；`git diff --check` 通过 |
| 批次 15 | `94732ca0` | **EventReceipt 事件消费回执与入站幂等**（§7.4/§9.2 阶段 C/§16.1）：domain 新增 `EventReceiptGateway` SPI；infrastructure 新增 `EventReceiptDO`/`Mapper`/`JdbcEventReceiptGateway`（先查后插 + `(event_id,consumer)` 唯一索引兜并发）；`FileParsedEventListener`/`ApprovalResultEventListener`（3 个 handle）/`OutboundFileGeneratedEventListener` 在共用入口接入重复事件短路；5 份 DDL 增加 `t_business_event_receipt` 与唯一索引；E2E 验证重复投递不重复推进、回执恰好 1 行 | 基础设施 72（新增 4）、应用 102（新增 2）、架构不变量 7、annuity E2E 6 全部通过；E2E 日志「重复文件解析事件已忽略」、`Failed to save event` 与异步 NPE 保持 0；全仓 `test-compile` 通过；`git diff --check` 通过 |
| 批次 16 | `0745a821` | **ExternalInteraction 运行时接线（阶段 A 轻量版）**（§9.2）：`FlowOrchestrationService.advanceStep` 在 `SUSPEND_ASYNC_WAIT` 时创建 `ExternalInteraction(PENDING_DISPATCH)`，与 StepExecution/申请单同事务保存；`InteractionType` 过渡期按 `mainProcessor` 名推断（APPROVAL/FILE_OUTBOUND/FILE_PARSE），`requestNo` 沿用 `SE-<stepExecutionId>`；annuity 新增 `@Repository` 注册类；补 3 项编排器测试并加 E2E 表就绪断言 | 应用 105（新增 3）、领域 152、基础设施 72、架构不变量 7、annuity E2E 6 全部通过；E2E 主流程不经过 SUSPEND 路径，记录创建由单元测试验证；`Failed to save event`/异步 NPE 保持 0；全仓 `test-compile` 通过；`git diff --check` 通过 |
| 批次 17 | `37413686` | **外部协作重试与恢复机制**（§19.2/§13.1）：新增 `ExternalInteractionRequestedEvent`（create 注册、Repository 经 EventBus 发布）与 AFTER_COMMIT 异步认领监听器；`ExternalInteractionRepository` 增加 `findByStatus`/`findStaleDispatching` 扫描契约与 QueryWrapper 实现（复用现有列、不新增索引）；新增 `ExternalInteractionRecoveryService` 四类动作（认领 PENDING_DISPATCH→DISPATCHING、回收滞留 DISPATCHING→UNKNOWN、处理 RETRY_WAIT 到期重派、UNKNOWN 按审批状态对账）与 `@Scheduled` 兜底 Job（`core.kernel.recovery.enabled` 可关闭）；单条失败隔离、绝不盲目重派。不改 annuity、不加 DDL 列、不改 4 个挂起 Handler 的外部调用时机 | 领域 152、应用 120（新增 15）、基础设施 74（新增 2）、架构不变量 7、annuity E2E 6 全部通过；全仓 `test-compile` 通过；E2E 日志 `Failed to save event`/异步 NPE/恢复任务异常均保持 0；`git diff --check` 通过 |
| 批次 18 | 本次（未提交） | **接入 DistributedLock 短临界区互斥**（§9.3 第一层）：`business-core-application` 加 `shared-lock` 纯接口依赖；`FlowOrchestrationService` 用 `ObjectProvider<DistributedLock>` 弱依赖可选注入（有 bean 加锁、无 bean 直通），`advanceStep` 拆为锁包装 + `doAdvanceStep`，REJECTED/WITHDRAWN 终止分支纳入锁，避免回调入口递归重复加锁死锁；锁键 `kernel:application:{id}`、等待 3s/租期 10s，获取失败异常传播。不改 annuity、不引入 Redis 强依赖 | 应用 124（新增 4）、领域 152、基础设施 74、架构不变量 7、annuity E2E 6 全部通过；全仓 `test-compile` 通过；E2E 无锁降级路径无 Redis/锁错误；`git diff --check` 通过 |

### 23.3 批次 2 范围边界

按评审确认的「纯领域模型」范围，本批次**不**包含：

- 不接管运行时路由（`FlowOrchestrationService` / `StepAutoAdvanceListener` 仍使用 `BusinessConfigGateway.getNextStep()`）；
- 不修改 `BusinessConfigGateway` 现有方法，不新增旁路配置；
- 不改持久化、不新增 DDL、不接线 `ProcessDefinitionGateway` 实现；
- 不触碰 file-service / approval-service / annuity-service。

批次 3 已完成上述第 1、2、4 项，并对第 3 项采用渐进策略：

1. 默认 `ProcessDefinitionGateway` 由 `CoreKernelAutoConfiguration` 以 `@ConditionalOnMissingBean` 注册，过渡期委托 `BusinessConfigGateway.getNextStep()` 适配出初始版本定义并按「业务类型 + 版本」缓存，不新增第二套配置；
2. `BusinessApplication` 持久化 `definitionVersion`，`StepDefinitionResolver` 始终按实例固定版本加载；存量无版本行回退到 `DefinitionVersion.initial()`；
3. **保留** `ApplicationFlowStep` 枚举作为存量持久化表示（`current_step` 列、领域事件签名、DO/Converter 均不改），`StepCode` 仅在定义层使用，经 `StepCode.of(step.name())` 映射；枚举退役留到阶段 5；
4. `batchMode=REQUIRED` 时创建工厂强制校验批次存在且可接收，`NONE` 时申请不关联批次；`ApplicationSource` 不再硬编码批次必填。

批次 3 额外同步：annuity 侧三份复制的建表脚本（H2/PG/MySQL）同步增加 `definition_version` 列，否则复用 kernel DO 时运行期 SQL 会失败。

### 23.4 后续批次待定决策

- **已定**：路由切换采用「默认 Gateway 过渡适配 + 保留枚举做持久化表示」的渐进策略；在途实例通过持久化 `definitionVersion` 固定版本，过渡期默认实现仅存在初始版本，其他版本返回空并交由对账处理。
- 独立版本化定义存储（替代过渡期委托适配器）与定义发布、缓存失效机制；
- `ApplicationFlowStep`（17 步枚举）的退役时机，以及 `current_step` 列与领域事件签名向开放 `StepCode` 的迁移；
- **已解决（批次 4）**：三个 `ConditionEvaluationGateway` 实现的求值异常统一抛 `SystemException(CORE.INFRA.0006)`，不再静默跳过；`StepPipelineExecutor` 保持不变，`false` 仍表示条件不满足（SKIPPED），异常则中断事务。后续仍需为流程定义发布增加表达式语法预校验；
- 默认 Gateway 硬编码的 `FORM_DRIVEN / PER_SUB_TASK / REQUIRED` 声明应由业务服务的自定义实现覆盖。
- **已解决（批次 5）**：`ApplicationStatus` 合法迁移表与终态约束落地；`transit()` 收敛为 `completeCurrentStep()`，`PROCESSING` 作为存量兼容状态归一为 `RUNNING`（阶段 5 清理时移除）。
- **已解决（批次 6）**：§2 点名的「Spring 本地事件发布与 EventBus 发布分叉」——原先 Repository 先本地分发并清空队列，应用服务随后的 `eventBus.publish()` 拿到空队列，Outbox 从未落库；现统一由 Repository 经 `EventBus` 发布。
- **已解决（批次 10）**：原 `publishDomainEvents` catch 单条失败仅记录日志、不回滚事务，与 §9.1「状态与 Outbox 同事务」的严格原子性有差距（批次 6 引入该隔离策略，§23.6 曾列为待复核项）；现移除 catch，发布失败即抛使应用事务整体回滚，失败时事件队列保留以便重试。
- **已解决（批次 8，P0，批次 7 发现）**：`AnnuityJacksonConfiguration` 的 `@Primary ObjectMapper` 由裸 `new ObjectMapper()` 构造，未注册 `JavaTimeModule`；`EventAutoConfiguration.eventStore()` 注入该容器 mapper 后，`JdbcEventStore.save()` 序列化含 `LocalDateTime occurredOn` 的领域事件抛 `Java 8 date/time type not supported`，`DefaultEventBus` 落库失败并 rethrow，最终被 `AbstractBusinessRepository.publishDomainEvents` 的 catch 静默吞掉 —— annuity 链路的 Outbox 实际仍不落库。批次 6 只修了发布所有权，未覆盖序列化能力；批次 8 改为 EventStore 自持专用 mapper（显式注册 `JavaTimeModule` + `DddJacksonModule`），不复用业务服务 `@Primary` mapper。`shared-event-core` 已有 compile scope 的 jsr310 传递依赖，starter 侧原 `test` scope 声明会覆盖它导致生产期不可用，已一并提升为 compile。
- **已解决（批次 9，批次 8 发现）**：`StepPipelineExecutor.dispatchAsyncAction()` 原在 `CompletableFuture.runAsync`（ForkJoinPool.commonPool）中硬编码 `action.execute(null, context, params)`，而 `AnnuityMaterialPreparedNotificationAction.execute()` 调 `app.operatorInfo()` —— E2E 每次稳定出现 2 处 `NullPointerException`（被 catch 吞掉、不影响断言）。属 §10.3 深层缺陷：SPI Javadoc 规定异步时 app 只读、实现却传 null、使用方又读 app，三方矛盾。批次 9 改用受控 `applicationTaskExecutor` 并只读传入 app，NPE 归零。更正批次 8 记录：`BusinessMetaContext` 并非 ThreadLocal，其 `extensionFacts` 为 `ConcurrentHashMap`（构造期归一化），本就并发安全。
- **新发现（批次 8）**：`JdbcEventStore.save()` 以 `event.eventId().toString()` 写 `event_id`，而 `EventId` 是 record（默认 `toString()` 为 `EventId[value=xxx]`），与通常期望的 ULID 字符串不一致；`EventDeliverer`/`claimPendingLogs` 按 `event_id` 关联时依赖同一写法，暂可工作但语义脆弱，后续统一为 `eventId().value()` 或显式序列化。
- **已解决（批次 14，批次 13 发现）**：`JdbcSegmentRepository` 依赖的 `t_id_generator` 表此前只在 `shared-id-core` 的 PG/MySQL 脚本中定义，annuity 三份复制脚本（含 E2E H2）均缺失，导致 `IdService.nextLongId` 在运行时接线后必然失败；批次 14 已补齐 5 份 DDL 并由 E2E 验证号段自动初始化可用。

### 23.5 批次 5 渐进边界

本批次刻意保留以下过渡形态，避免一次性改动扩散到尚未接线的外部协作链路：

- `reject()` / `withdraw()` 属于 §8.1 注明的「明确终止行为」，不走 `canTransitTo` 一般迁移表，仅校验非终态 + 幂等 + `markUpdated`。过渡期 `WAITING_EXTERNAL` 尚未由阶段 3 的外部协作接线建立，因此审批驳回仍从 `RUNNING` 触发；阶段 3 接入后应统一为「仅 `WAITING_EXTERNAL` 可进入驳回/撤回」并移除该例外。
- `completeCurrentStep()` 只校验「终态不可推进」与后继非空，不强制套用完整迁移表（同样因为等待态未接线）；幂等检查先于终态检查，以兼容终点步骤 `COMPLETED → COMPLETED` 自环。
- 状态更新统一取聚合内 `operatorInfo().operatorId()` 作为操作人，未在 API 层引入显式 operator 传递；外部事件回调按 §15.3 不伪造用户会话，后续接入会话时再改为显式传入。
- 不改 DDL/DO：`status` 仍以枚举名持久化；存量 `PROCESSING` 行读出后经 `normalized()` 归一，后续保存即写回 `RUNNING`。

### 23.6 批次 6 范围边界

- 仅统一 **kernel 三个业务聚合**（申请/批次/表单）的 Repository 发布所有权。`IntegrationEventSimulator` 仍用 Spring 本地事件模拟演示环境的集成事件投递，属演示基础设施，不在本批次范围。
- annuity 自有聚合 `AnnuityEmployeeBatchRepositoryImpl` 仍直接注入 `ApplicationEventPublisher` 发布自身领域事件，未纳入 `EventBus`/Outbox；业务服务自有聚合是否统一，留待阶段 2/4 与业务服务接入规范一并评审。
- 未新增或变更任何领域事件类型、`EventBus` 实现与 shared-event-starter 配置；`DefaultEventBus` 的落库 → 本地分发 → 远程投递顺序保持不变。
- 事务边界：`publishDomainEvents` 在 `save()` 内同步执行，与应用服务的 `@Transactional` 同事务；`eventStore.save` 失败被 catch 记录但不回滚（见 §23.4 待复核项）。

### 23.7 批次 7 范围边界

- 本批次仅新增纯领域模型切片（`StepExecutionId` / `StepExecutionState` / `StepExecution` / `StepExecutionRepository`），不接线运行时、不注册领域事件、不改 DDL/DO/Converter、不新增 Repository 实现；唯一性语义（`(applicationId, stepCode, attempt)` 与 `requestNo`）仅由聚合与 Repository 契约表达，物理唯一索引留待持久化批次。
- 执行状态枚举命名为 `StepExecutionState`，与既有 Handler 返回值枚举 `StepExecutionStatus`（SUCCESS/SUSPEND_ASYNC_WAIT/FAILED，10+ 处引用）刻意区分，避免语义冲突；后者退役待独立批次。
- annuity E2E 6 项虽 `BUILD SUCCESS`，但日志暴露 `DefaultEventBus: Failed to save event`（详见 §23.4 P0 条目）：Outbox 仍因序列化失败被上层 catch 吞掉。该缺陷属 annuity Jackson 配置 / shared-event-starter 注入策略，跨业务服务，不在本批次（kernel 领域模型）范围内修复，列为下一批次优先候选。
- `StepExecutionId` 采用 BIGINT + SEGMENT（§16.3），与存量三个 String 强类型 ID 不同；E2E 已验证 `IdService` 启动期对该 ID 定义的加载正常。

### 23.8 批次 8 范围边界

- 修复仅落在 `shared-event-starter`（`EventAutoConfiguration` + `pom.xml` + 测试），**不改 annuity 的 `AnnuityJacksonConfiguration`**：该 `@Primary` mapper 仍服务于 DO ↔ 领域对象的扩展字段多态转换，其缺少 `JavaTimeModule` 只影响被它序列化的对象；EventStore 现已与其解耦，从基础设施层一处修复覆盖所有业务服务。
- 采用显式 `registerModule(new JavaTimeModule())` 而非 `findAndRegisterModules()`：后者依赖运行期 ServiceLoader 扫描，模块缺失时会静默退化回到本缺陷；显式注册在编译期即被强制。与 `shared-logging-starter`/`file-infrastructure` 的既有写法一致。
- 专用 mapper 一并注册 `DddJacksonModule`，使领域事件中的强类型 `Identifier` 序列化为字符串而非嵌套对象（新增测试断言覆盖）。`dddJacksonModule()` 仍作为 `@Bean` 供 Spring Boot 自动注册到容器 mapper，两者互不冲突。
- 未改 `JdbcEventStore` / `DefaultEventBus` / `AbstractBusinessRepository`：`publishDomainEvents` 的单条失败隔离（§23.4 待复核项）与 §9.1 事务原子性差距仍在；本批次只保证「落库不再因序列化失败」。
- E2E 日志中 2 处异步扩展动作 `NullPointerException` 为既有缺陷（见 §23.4 新发现条目），本批次未处理；`Failed to save event` 已归零，作为 Outbox 落库恢复的验收信号。

### 23.9 批次 9 范围边界

- 保留 `isAsync` / `ASYNC_DISPATCHED` 语义：本批次只替换线程来源与 app 入参，不改 `StepExtensionConfig`、`PipelineExecutionResult`、`StepExtensionAction` SPI 及其 Javadoc（其「异步只读、不改状态」约束现由只读传递 app 真正满足）。配置中唯一 `isAsync=true` 的动作仍为 `annuityMaterialPreparedNotificationAction`（`step-routes.json`）。
- 采用 `@Qualifier("applicationTaskExecutor")`：与 `shared-logging-starter` 的 `LogCoreConfiguration` 惯例一致，复用 Spring Boot `TaskExecutionAutoConfiguration` 提供的受控线程池（各 starter 已标 `@EnableAsync`）；不自建线程池，避免与 `@Async` 监听器争用资源。因此去掉 Lombok `@RequiredArgsConstructor` 改显式构造，以保证 `@Qualifier` 落在构造器参数上生效。
- 异步动作仍与调用方事务解耦、异常仍被 catch 隔离（不中断主链路、不回滚主事务）；未引入 `TransactionSynchronization` / after-commit 语义，通知类动作在主事务提交前即派发的既有行为保持不变。若需严格「提交后才通知」，应作为独立批次评估。
- `app` 为只读传递：当前唯一异步动作仅读 `operatorInfo()`/`id()`；`BusinessMetaContext.extensionFacts` 为 `ConcurrentHashMap`，并发读写安全。后续若新增异步动作修改 app 状态，需在 SPI 层显式禁止并加检测。
- 单元测试注入 `Runnable::run` 同步执行器，使「执行线程 == 注入 Executor」可被断言，同时让异步分支零覆盖变为 3 项确定性测试；不依赖 `await`/超时等待。
- `event.eventId().toString()` 与 `publishDomainEvents` 失败隔离两项 §23.4 待办本批次未动。

### 23.10 批次 10 范围边界

- 仅改 **kernel 基类** `AbstractBusinessRepository.publishDomainEvents`。file-service（多个 Repository）、approval-service、annuity 自有 `AnnuityEmployeeBatchRepositoryImpl` 仍是各自 `ApplicationEventPublisher` + catch 隔离的本地事件实现，事件本就不进 Outbox、事务语义不同，未纳入本批次；需在阶段 2/4 与业务服务接入规范一并评审。
- `eventBus.publish` 抛出的 `RuntimeException` 直接传播（保留原始 cause 链），未包装为 `SystemException`：`DefaultEventBus` 在 `eventStore.save` 失败时已 `log.error` 并 rethrow，传播的是 shared 层已有异常而非本层自定义裸抛，可观测性由该日志与全局异常处理器保证。
- 事务回滚由应用服务的 `@Transactional` 保证：`save()` 内的 insert/update 与 `eventStore.save` 的 Outbox 写入同事务，异常传播即整体回滚。单元测试用 mock 验证「异常传播 + 队列保留 + 快速失败」契约，未加真实 DB 回滚集成测试；后续如需端到端验证，可在 E2E 注入失败 EventStore。
- `clearDomainEvents()` 位于循环之后，发布失败时不会执行，事件队列保留；重试 `save()` 会重新发布全部事件。因事务已整体回滚、Outbox 无残留，重试不会重复投递；但若上层捕获异常后在标记 rollback-only 的事务内重试，需另行评估。
- 未改 `DefaultEventBus` / `JdbcEventStore` / 其他服务 Repository；`event.eventId().toString()` 语义仍未统一（§23.4 待办）。

### 23.11 批次 11 范围边界

- 仅落地**持久化切片**，不接线编排器：`StepExecutionRepositoryImpl` 与其他 kernel 仓储一致不标注 `@Repository`，由具体业务服务继承并注册。当前无任何运行时代码创建/更新步骤执行记录，故 `t_step_execution` 表虽已建但暂无写入。
- 主键采用 BIGINT + `KeyType.None`（由 `IdService.nextLongId` 号段策略生成），与存量三个 String 强类型 ID 区分；`StepExecutionConverter` 负责 `Long ↔ StepExecutionId` 与枚举名持久化，`status` 列存 `StepExecutionState.name()`。
- 唯一性语义由**物理索引**保证：`uk_step_execution_app_step_attempt`、`uk_step_execution_request_no`；`findRunningByApplication` 通过 `(application_id, status=RUNNING)` 查询配合单一运行约束。查询统一使用 MyBatis-Flex `QueryWrapper` + 注解处理器生成的 `StepExecutionDOTableDef`，逻辑删除过滤由 `@Column(isLogicDelete=true)` 自动追加。
- Converter 沿用 kernel 既有模式：标注 `@Mapper(componentModel="spring")` 但方法手写（MapStruct 不支持 `factoryMethod=reconstitute`），测试直接实例化生成的 `StepExecutionConverterImpl`。
- 5 份 DDL 同步：kernel 的 PG/MySQL 与 annuity 的 PG/MySQL/H2（annuity 复用 kernel DO，三份为复制脚本）。H2 的 `deleted` 用 `INT DEFAULT 0`、PG 用 `BOOLEAN`、MySQL 用 `TINYINT(1)`，与既有脚本风格一致；E2E 已实执行 H2 建表。
- 测试用 mock mapper + `ArgumentCaptor<QueryWrapper>` 断言查询列名，未做真实 DB 的 insert/select 集成测试；Repository 的乐观锁与事件发布行为继承自已测的 `AbstractBusinessRepository`（StepExecution 不注册事件，发布环节为空操作）。
- `event.eventId().toString()` 统一、`ExternalInteraction` 聚合与运行时接线仍未处理。

### 23.12 批次 12 范围边界

- 纯领域模型切片，不接线运行时、不注册领域事件、不改 DDL/DO/Converter；`ExternalInteractionRepository` 仅定义契约（`findByRequestNo`/`findByExternalTaskId`/`findPendingRetry`/`findByApplication`），无实现，物理唯一索引留待持久化批次。
- 主键按 §16.3 用 BIGINT + `@IdDefinition(type = IdType.SEGMENT)`，与 `StepExecutionId` 一致；E2E 已验证新增 ID 类型在启动期正常加载（日志中 `IdDefinitionStartupValidator` 的 `validation.packages` 为空为既有配置警告，与本批次无关）。
- `requestNo` 由 `create` 内部按 `stepExecutionId` 派生为 `SE-<id>`，不提供外部传入入口：落实 §7.3「同一 requestNo 重试不得重新生成」，重试走同一聚合的 `FAILED → RETRY_WAIT → DISPATCHING` 状态迁移而非新建聚合。后续若需对接已有外部请求号，应引入 Parameter Object 显式传入并保持派生语义。
- `UNKNOWN` 只能经 `confirmSucceededAfterReconciliation` / `confirmFailedAfterReconciliation` 对账确认后进 `SUCCEEDED`/`FAILED`，迁移表显式禁止 `UNKNOWN → DISPATCHING/RETRY_WAIT`，避免超时场景重复创建外部任务。
- `matches()` 落实「优先按 requestNo、其次按 externalTaskId」关联规则，两者皆缺失时返回 false，防止盲目推进流程；该函数只做关联判定，不触发状态变更。
- 状态迁移方法统一「先 `transitTo` 校验并改状态、后写附带字段」，保证非法迁移抛 `DomainException` 时不留下部分字段变更。
- `reconstitute` 沿用批次 7 `StepExecution` 的多参数形态（未引入 Parameter Object），与既有领域模型保持一致；该形态参数数超出 04 规范 10.1 的建议上限，属既有先例的延续。
- 三类核心概念的领域模型现已齐备；`EventReceipt`（§7.4，基础设施模型）、`StepExecution`/`ExternalInteraction` 持久化与运行时接线、`event.eventId().toString()` 统一仍未处理。

### 23.13 批次 13 范围边界

- 仅落地持久化切片，不接线运行时：`ExternalInteractionRepositoryImpl` 与其他 kernel 仓储一致不标注 `@Repository`，由具体业务服务继承并注册；当前无运行时代码写入该表。
- 表名采用 spec §16.1 的 `t_business_external_interaction`，列 `next_retry_time` 遵循 §16.1 的 `(status, next_retry_time)` 索引与 06 规范时间字段命名；DO 字段 `nextRetryTime` 与领域字段 `nextRetryAt()` 由 Converter 映射。首次红灯即由 `next_retry_at` 与 `next_retry_time` 列名不一致暴露，已对齐。
- **新发现待办 1（表名偏差）**：批次 11 建的是 `t_step_execution`，而 spec §16.1 写 `t_business_step_execution`，命名不一致。本批次遵循 spec 使用带 `business_` 前缀的表名，两张新表风格差异留待专项统一（涉及 DO `@Table`、TableDef 与 5 份 DDL 的重命名，无生产数据可直接改）。
- **新发现待办 2（号段表缺失）**：`JdbcSegmentRepository` 依赖 `t_id_generator` 表，DDL 仅存在于 `shared-id-core` 的 PG/MySQL 脚本，**annuity 三份复制脚本（含 E2E 的 H2）均缺失**。任何真正调用 `IdService.nextLongId` 的运行时接线在 E2E 环境会失败；需在接线批次前补齐这 5 份 DDL 并验证号段生成。
- 唯一性语义由物理索引保证：`uk_external_interaction_request_no` 与 `uk_external_interaction_type_task`（`external_task_id` 为 NULL 时唯一索引允许多行，等价「非空时唯一」）；`findPendingRetry` 按 `status=RETRY_WAIT AND next_retry_time <= ?` 配合 `(status, next_retry_time)` 索引。
- 测试用 mock mapper + `ArgumentCaptor<QueryWrapper>` 断言查询列名，未做真实 DB insert/select 集成测试；乐观锁与事件发布行为继承自已测的 `AbstractBusinessRepository`（ExternalInteraction 不注册事件，发布环节为空操作）。
- `event.eventId().toString()` 统一、`EventReceipt`、两个聚合的运行时接线仍未处理。

### 23.14 批次 14 范围边界

- 本批次让 `t_step_execution` 从「已建但无写入」变为「随流程推进真实落库」：`FlowOrchestrationService.advanceStep` 每次调用创建一条 `StepExecution`，`attempt` 由 `findByApplicationAndStep(...).size() + 1` 得出，`requestNo` 由 `申请单:步骤:attempt` 稳定派生；Handler 结果经 `applyHandlerResult` 映射为 `SUCCEEDED` / `WAITING_EXTERNAL` / `FAILED`。
- 步骤执行记录与申请单状态、Outbox 同处 `transactionTemplate` 事务（§9.1）：`applicationRepository.save(app)` 与 `stepExecutionRepository.save(execution)` 在同一 lambda，事件发布失败即整体回滚。
- **本批次修复 2 个接线才暴露的真实缺陷**：
  1. `StepExecutionId` / `ExternalInteractionId` 的 `@IdDefinition` 沿用默认 `format="%p%d%s"`，`LongTypeHandler` 把业务前缀与日期拼进 Long 值，`Long.parseLong` 抛 `[SHARED.ID.0004] ID格式配置错误`；显式声明 `format="%s"`（纯序号）后 `isSimpleSequence=true`，号段值直接作为 Long 返回。此前领域测试用 `StepExecutionId.of(1L)` 手工构造，从未走 `IdService.nextLongId(Class,...)`，故未暴露。
  2. `t_step_execution.request_no VARCHAR(64)` 装不下 E2E 的 `APP-TEST-<uuid>:步骤:attempt`（69 字符）；5 份 DDL 扩到 `VARCHAR(128)`。`t_business_external_interaction.request_no` 保持 64（其 requestNo 由 `SE-<long>` 派生、长度可控），若未来接入外部 requestNo 需重新评估。
- **已解决（批次 13 待办 2）**：annuity 三份复制脚本（含 E2E H2）补齐 `t_id_generator`（H2 用 `TIMESTAMP`，去掉 `shared-id-core` PG 脚本的 `SET search_path` / `TIMESTAMPTZ`），`JdbcSegmentRepository` 自动初始化号段行；E2E 断言 `t_id_generator` ≥1 行。
- annuity 侧改动说明：本批次 annuity 的 3 份 DDL、`@Repository` 注册类与 E2E 断言均为**运行时接线与 E2E 验证所必需**（Spring 上下文需 `StepExecutionRepository` bean、H2 需两张表），不是为示例服务做兼容适配。
- 单元测试用 mock `IdService` / `StepExecutionRepository` 验证创建与状态映射；E2E 用 `JdbcClient` 查表验证真实落库（≥4 行）与号段初始化（≥1 行）。`requestNo` 目前由编排器内部派生，未按 §15.1 由推进 Command 传入；后续 CQE 化时需统一入口并保证同一请求重试不重新生成。
- 仍未处理：§9.2 阶段 B 的事务外派发 / AFTER_COMMIT、`ExternalInteraction` 运行时接线、`EventReceipt`、重试恢复任务、`DistributedLock`、表名偏差（待办 1）、`event.eventId().toString()` 统一。

### 23.15 批次 15 范围边界

- `EventReceipt` 按 §7.4 定位为**基础设施持久化模型**，不建领域聚合：domain 层只放 `EventReceiptGateway` SPI，DO/Mapper/实现均在 infrastructure，遵守 application 不依赖 infrastructure 实现的分层约束。
- 幂等判定基于 `(event_id, consumer)` 唯一索引：先 `selectOneByQuery` 短路已存在回执，再 insert；并发撞 `DuplicateKeyException` 也按重复短路。consumer 按监听器类名区分（FileParsed / ApprovalResult / OutboundFileGenerated），同一 eventId 可被不同 consumer 各消费一次。
- 短路放在各监听器的**共用处理入口**（`handleFileParsed` / `handleApproved|Rejected|Withdrawn` / `onOutboundFileGenerated`），使 Spring `@EventListener` 与生产 RocketMQ 直调都受保护；`onFileParsed` / `onApproved` 等入口仅做委托。
- 事件 DTO 均带 `eventId()`，E2E 用 UUID 构造；缺失 eventId/consumer 时**保守放行**（不阻断无法判重的事件），该边界需在生产接入时按契约收紧（§11.4/§12.3 要求事件具有稳定 eventId）。
- 主键按 §16.3 用 BIGINT，由 `IdService.nextLongId("EVENT_RECEIPT")` 生成（走 `nextLongId(String)` 重载，不经过 `LongTypeHandler` 的 format 分支，避开批次 14 修过的 ID 格式问题）。
- 5 份 DDL 同步 `t_business_event_receipt`（kernel PG/MySQL + annuity PG/MySQL/H2），E2E 已实执行 H2 建表；回执表只记录首次消费与结果摘要，未实现 §7.4 的失败错误摘要回填与 §19.2 的对账清理。
- 单元测试用 mock Mapper/IdService 验证 4 类分支；E2E 通过重复投递同一 event 断言流程状态不变且 `t_business_event_receipt` 恰好 1 行。未覆盖跨实例并发撞键的真实 DB 集成测试。
- 仍未处理：§9.2 阶段 B 的事务外派发 / AFTER_COMMIT、`ExternalInteraction` 运行时接线、重试恢复任务、`DistributedLock`、其他业务服务本地事件发布失败隔离、表名偏差（待办 1）、`event.eventId().toString()` 统一。

### 23.16 批次 16 范围边界

- **阶段 A 轻量版**：`advanceStep` 在 `SUSPEND_ASYNC_WAIT` 分支创建 `ExternalInteraction(PENDING_DISPATCH)`，与 `StepExecution` / 申请单状态同处 `transactionTemplate` 事务；4 个内置 Handler（`DefaultFormParsingHandler` / `OutboundGenerationHandler` / `ArchiveSubmissionHandler` / `ApprovalSubmissionHandler`）当前仍**直接调用外部网关**后返回挂起，本批次只补建协作记录作为恢复线索，**不改变外部调用时机**。§9.2 阶段 B 的 AFTER_COMMIT 派发、`DISPATCHING → WAITING_CALLBACK → SUCCEEDED` 状态推进与阶段 C 回调留后续。
- **InteractionType 过渡期推断**：`BusinessConfigProcessDefinitionAdapter` 目前只产出 `ActionType.BUSINESS`/`NONE`，拿不到 FILE_PARSE/FILE_OUTBOUND/APPROVAL；故按 `mainProcessor` bean 名小写包含的关键字推断（approval / outbound|archive / formparsing|form_parse）。无法识别的业务 Handler 挂起时不创建协作记录。独立版本化定义存储落地后应改由 `StepDefinition.actionType()` 显式声明，移除字符串匹配。
- `requestNo` 由 `ExternalInteraction.create` 按 `SE-<stepExecutionId>` 派生（批次 12 语义），与 `StepExecution.requestNo`（`申请单:步骤:attempt`）是两套独立幂等键；`stepExecutionId` 用 ID 引用关联，符合跨聚合不直接持有对象的约束。
- annuity 侧新增 `@Repository` 注册类（与批次 14 的 `StepExecutionRepositoryImpl` 同构），使 Spring 上下文提供 `ExternalInteractionRepository` bean；E2E `contextLoads` 增加 `t_business_external_interaction` 查询断言确认 DDL 与装配就绪。
- **验证边界**：E2E 的 step-routes.json 主流程 mainProcessor 均为业务 Handler（返回 SUCCESS），不经过 `SUSPEND_ASYNC_WAIT`，因此 `t_business_external_interaction` 在 E2E 中无写入；协作记录的创建、类型映射与同事务保存由 `FlowOrchestrationServiceTest` 的 3 项断言验证。真实落库需阶段 3 接入外部步骤后补 E2E 场景。
- 仍未处理：§9.2 阶段 B 事务外派发 / AFTER_COMMIT、阶段 C 回调与状态推进、重试恢复任务（§19.2）、`DistributedLock`、其他业务服务本地事件发布失败隔离、表名偏差（待办 1）、`event.eventId().toString()` 统一。

### 23.17 批次 17 范围边界

- 本批次按 §20 阶段划分聚焦**阶段 2 剩余的重试与恢复机制**。spec §9.2 阶段 B 明确「本地 `AFTER_COMMIT` 监听器**或**恢复任务认领待派发协作」为二选一；§19.2 的四条恢复能力（认领 `PENDING_DISPATCH`、回收长期滞留 `DISPATCHING`、处理 `RETRY_WAIT` 到期、对 `UNKNOWN` 执行外部查询与对账）本批次全部落地。AFTER_COMMIT 监听器只作「尽快认领」，`@Scheduled` Job 作兜底，二者都只推进协作状态、不重复触发外部调用。
- **完整事务外派发链路推迟到阶段 3**：4 个挂起 Handler 当前在事务内直接调 `triggerOutboundGeneration` / `startApproval` / `triggerAsyncParsing`；要改为事务外派发需重构这 4 个 Handler 并持久化派发参数，而 `FILE_*` gateway 返回 void、拿不到 `externalTaskId`，file/approval 也尚无 requestNo 幂等与按 requestNo 查询契约（§11.4/§12.3 明确留待阶段 3）。此外新增派发载荷列要同步 annuity 三份 DDL，与「冻结 annuity」冲突。故本批次不改 4 个 Handler 的外部调用时机、不加 DDL 列、不改 annuity 任何文件。
- `claimPendingDispatch` 只做 `PENDING_DISPATCH → DISPATCHING`（§9.2 阶段 B 第 2 步）；`DISPATCHING → WAITING_CALLBACK → SUCCEEDED` 与回调关联留阶段 3。`markWaitingCallback` 要求 `externalTaskId` 非空，当前契约无法满足，故未绕过该不变量。
- `reconcileUnknown` 当前仅对 `APPROVAL` 且已保存 `externalTaskId` 的协作调 `ApprovalIntegrationGateway.queryApprovalStatus` 对账（APPROVED→SUCCEEDED、REJECTED/WITHDRAWN→FAILED、PENDING/APPROVING 保留）；`FILE_*` 与缺少关联信息的协作保留 UNKNOWN 待阶段 3 补查询契约，绝不盲目重派（§8.3/§18.3）。
- 恢复动作每条独立保存，单条失败（含并发重复认领触发的非法状态迁移）catch 隔离；`repository.save` 为单条 SQL，未显式加 `@Transactional`，与批次 10 的「业务状态 + Outbox 同事务」路径不同——恢复链路的聚合由 `reconstitute` 重建、不注册事件，发布环节为空操作。
- `ExternalInteractionRecoveryJob` 由 `CoreKernelAutoConfiguration` 的 `@Bean` + `@ConditionalOnProperty` 注册（默认启用，可 `core.kernel.recovery.enabled=false` 关闭）；调度依赖宿主 `@EnableScheduling`（shared-event-starter 提供），kernel 不重复开启以免重复注册 `ScheduledAnnotationBeanPostProcessor`。E2E 中协作表为空、恢复任务查询 no-op，日志无异常。
- `ExternalInteractionRequestedEvent` 经 EventBus → `SpringEventDispatcher` → `@TransactionalEventListener(AFTER_COMMIT)` 链路（与 `StepEnteredEvent` 同构）。E2E 主流程不经过 SUSPEND 路径、不创建协作记录，故该事件的 Outbox 序列化/反序列化往返与 AFTER_COMMIT 真实触发**未做集成验证**；阶段 3 接入外部步骤后需补 E2E 场景。
- 仍未处理：`DistributedLock` 接入（§9.3）、§9.2 完整事务外派发与阶段 3 回调链路、其他业务服务本地事件发布失败隔离与纳入 Outbox、表名偏差（§23.13 待办 1）、`event.eventId().toString()` 统一、独立版本化定义存储（§14）。

### 23.18 批次 18 范围边界

- 本批次接入 §9.3 三层保护的**第一层**「`DistributedLock` 短临界区互斥」；后两层（数据库乐观锁、唯一约束）在批次 1/6/11/13/15 已具备，本批次未改动。
- **弱依赖可选注入的依据**：`shared-lock-starter` 是纯 Redis 实现且有启动期 PING fail-fast，而 annuity E2E 的 `application-test.yml` 明确禁用 Redis、且全仓无模块引入该 starter；按「冻结 annuity」不能改其依赖/配置。故 `business-core-application` 只依赖 `shared-lock` 纯接口（父 `pom.xml` 已有 dependencyManagement），用 `ObjectProvider<DistributedLock>` 可选注入：有 bean 则加锁、无 bean 则无锁直通，由乐观锁与唯一约束兜底。这与 `DistributedLock` 接口 Javadoc「弱依赖（单飞保护/防重）：装配条件不满足，相关组件自然不装配」及 spec §9.3 定位（锁只是「减少同实例并发」的第一层）一致；spec §14 的「生产缺 Redis fail-fast」由 starter 自身的 `LockConnectionChecker` 保证（宿主引入 starter 时），与「宿主未引入 starter」不矛盾。
- **加锁点与防重入**：`advanceStep` 拆为锁包装 + `doAdvanceStep`；`advanceByFileTaskId` / `advanceByOutboundFile` / `advanceByApprovalResult` 的 APPROVED 分支都经 `advanceStep` 加锁，故这些入口自身不再加锁；REJECTED / WITHDRAWN 两个终止分支单独用 `withApplicationLock` 包装。lock4j 的 `RedisTemplateLockExecutor` 为 SETNX 不可重入，此设计保证每条路径恰好一次加锁，避免自死锁。
- 锁键 `kernel:application:{applicationId}`，等待 3s、租期 10s（`DistributedLock` 无看门狗自动续期，spec §9.3 要求长任务显式拆分临界区）。获取失败由 shared-lock 抛 `SystemException(GET_LOCK_FAILED)` 直接传播、不吞掉，与批次 10 的发布失败传播风格一致。
- **已知差距**：`advanceByFileTaskId` / `advanceByApprovalResult` / `advanceByOutboundFile` 仍标注 `@Transactional`，锁在其内部的 `advanceStep` / 终止分支中释放，早于外层方法级事务提交，存在极小竞态窗口（乐观锁与唯一约束仍兜底，不会造成状态覆盖）。严格按 §9.1「提交事务后释放锁」需要 after-commit 释放句柄，留阶段 5 精修。
- `isSystemTask` 为只读查询，未加锁；文件/审批等待期间不持锁（等待由 `WAITING_EXTERNAL` 状态表达，非锁持有）。
- E2E 未引入锁 bean，走无锁直通路径，6 项通过且无 Redis/锁错误；「存在锁 bean 时在锁内执行、REJECTED 分支加锁、获取失败异常传播」由 `FlowOrchestrationServiceTest` 4 项单测覆盖，真实 Redis 下的并发互斥未做集成测试。
- 仍未处理：§9.2 完整事务外派发与阶段 3 回调链路、其他业务服务本地事件发布失败隔离与纳入 Outbox、表名偏差（§23.13 待办 1）、`event.eventId().toString()` 统一、独立版本化定义存储（§14）、阶段 3/4/5 主线。
