# 生产级业务流程内核详细设计

> 状态：待评审（仅设计，不包含实现授权）
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
