# 表单上传处理与出站流程分析

> 本文档分析当前表单上传处理的完整实现、新增/调整表单的操作步骤、解析结果存储与获取机制，以及出站表单生成与外部系统提交的设计方案。

---

## 一、当前表单上传处理流程

### 1.1 三阶段事件驱动编排

整个表单上传处理由 `business-core-kernel` 编排引擎驱动，分为 3 个阶段：

#### Phase 1：表单上传与异步解析

```
前端 POST /api/annuity/upload
  → AnnuityController.uploadForm()
    → 校验年金规则（初始缴费≥100元、外资准入）
    → BusinessBatchAppService.createBatch()           // 创建批次
    → BusinessForm.create()                            // 创建表单聚合根(状态=UPLOADED)，发布 FormUploadedEvent
    → formRepository.save(form) + 发布事件
```

**事件驱动后续：**

```
FormUploadedEvent → BusinessFormAppService.triggerParsing()
  → configGateway.getFormParsingConfig()               // 获取解析模板ID: 如 ANNUITY_DEFAULT_TEMPLATE
  → fileIntegrationGateway.triggerAsyncParsing()       // 调用 file-service 异步解析
  → form.markAsParsing() → save
```

#### Phase 2：解析回调与裂变

```
FileParsedEventDTO → FileParsedEventListener.onFileParsed()
  → FlowOrchestrationService.advanceByFileTaskId()
    → BusinessFormAppService.handleParsingResult()
      → form.markAsParsed() → save
      → 循环每个 ParsedPlanResult:
        → BusinessApplication.createFromForm()        // 每个计划裂变为一个申请单
          → 状态=PROCESSING, 当前步骤=FORM_DETAIL_INGESTION
          → 注册 ApplicationSpawnedEvent
        → applicationRepository.save() + 发布事件
```

#### Phase 3：自动推进与挂起

```
ApplicationSpawnedEvent → ApplicationSpawnedListener.onApplicationSpawned()
  → @Async @TransactionalEventListener(AFTER_COMMIT)
  → FlowOrchestrationService.advanceStep(appId)       // 点火启动

StepEnteredEvent → StepAutoAdvanceListener.onStepEntered()
  → SYSTEM_TASK → 引擎自动推进 advanceStep()
  → USER_TASK → 引擎挂起，等待 BFF 层手动唤醒
```

### 1.2 关键代码位置

| 组件 | 文件位置 |
|------|----------|
| 表单应用服务 | `business-core-application/.../step/service/BusinessFormAppService.java` |
| 表单聚合根 | `business-core-domain/.../aggregate/root/BusinessForm.java` |
| 申请单聚合根 | `business-core-domain/.../aggregate/root/BusinessApplication.java` |
| 文件集成网关(SPI) | `business-core-domain/.../gateway/FileIntegrationGateway.java` |
| 文件集成网关(实现) | `business-core-infrastructure/.../gateway/FileServiceIntegrationGateway.java` |
| 表单解析处理器 | `business-core-application/.../step/handler/DefaultFormParsingHandler.java` |
| JSON流式摄入基类 | `business-core-application/.../step/extension/AbstractJsonStreamIngestionAction.java` |
| 年金明细摄入实现 | `annuity-application/.../extension/AnnuityDetailIngestionAction.java` |
| 文件解析事件监听 | `business-core-application/.../listener/FileParsedEventListener.java` |
| 申请裂变监听 | `business-core-application/.../listener/ApplicationSpawnedListener.java` |
| 步骤自动推进监听 | `business-core-application/.../listener/StepAutoAdvanceListener.java` |
| 流程编排服务 | `business-core-application/.../service/FlowOrchestrationService.java` |

### 1.3 步骤推进管道

`FlowOrchestrationService.advanceStep()` 执行 6 阶段管线：

```
1. load aggregate
2. buildFullConfigContext (两阶段：基础维度 → 扩展事实)
3. query StepRouteConfig from BusinessConfigGateway
4. execute preValidations (StepPipelineExecutor)
5. execute mainProcessor (StepActionHandler)
6. execute detailProcessors
7. execute sideEffects (postExtensions)
8. transit to nextStep (or terminal)
9. transactionTemplate: save + publish domain events
```

### 1.4 BusinessForm 状态机

```
create() → UPLOADED → markAsUploaded() → UPLOADED
                     → markAsParsing() → PARSING
                     → markAsParsed()  → PARSED
                     → markAsDeleted() → DELETED (仅 UPLOADED/PARSED/WAITING_UPLOAD 可删除)
```

### 1.5 完整步骤流程（17步，5阶段）

| 阶段 | 步骤 |
|------|------|
| 发起 | DRAFT → FORM_DETAIL_INGESTION |
| 数据核查 | DATA_VERIFICATION → EMPLOYEE_DETAIL_VERIFICATION → ANOMALY_HANDLE → VERIFICATION_CONFIRM |
| 客户确认与材料 | CUSTOMER_CONFIRM → PLAN_MATERIAL_PREPARATION → MATERIAL_PACKAGE |
| 机构审核与提交 | TELLER_REVIEW → APPLICATION_REVIEW → APPROVAL_SUBMISSION → APPROVAL_RESULT |
| 受理与归档 | ACCEPTANCE → ARCHIVE → COMPLETED |

---

## 二、新增或调整表单操作指南

### 2.1 新增业务表单

新增一种业务表单（如"养老金"业务）需要以下步骤：

| 步骤 | 操作位置 | 具体内容 |
|------|----------|----------|
| 1 | 业务服务 api | 新增 `XxxFormApi extends BusinessFormApi`，标注 `@HttpExchange("/xxx/core/form")`，重新声明各方法并标注 `@PostExchange` |
| 2 | 业务服务 adapter | 新增 `XxxKernelFormController implements BusinessFormApi`，标注 `@RestController`，注入 `BusinessFormAppService` + `FormConverter` + `SessionContextResolver` |
| 3 | 业务服务 domain | 实现 `BusinessExtension`（多态值对象），添加 `@JsonTypeName("YOUR_TYPE")` |
| 4 | 业务服务 infrastructure | 实现 `BusinessConfigGateway`（配置步骤路由、材料规则、表单解析配置）、`FileIntegrationGateway`、`ApprovalIntegrationGateway` |
| 5 | 业务服务 infrastructure | 子类化 `BusinessApplicationRepositoryImpl`，标注 `@Repository`，覆写 `findByFileTaskId`/ `findByBatchId` |
| 6 | 业务服务 application | 按需实现 `StepActionHandler`、`StepExtensionAction`、`BusinessFactExtractor`，注册到对应 Registry |
| 7 | 业务服务 application | 实现 `AbstractJsonStreamIngestionAction<D, T>` 子类，定义 DTO→Entity 映射、批量保存、错误追踪 |
| 8 | 配置中心 | 配置 `FormParsingConfig`，指定 `parseTemplateId`（如 `ANNUITY_DEFAULT_TEMPLATE`）和 `splitRules` |
| 9 | 配置中心 | 配置 `step-routes.json`，定义步骤流转路径 |
| 10 | file-service | 配置对应的**源模板**（定义 Excel 区域识别规则、列映射），确保解析引擎能识别新表单样式 |
| 11 | Jackson 配置 | 在业务服务的 `ObjectMapper` 中注册 `BusinessExtension` 子类型 |
| 12 | application.yml | 配置 `core-kernel.controllers.enabled=true` |

### 2.2 调整已有表单

| 调整类型 | 操作位置 | 说明 |
|----------|----------|------|
| 修改解析规则 | file-service 源模板配置 | 调整区域定义、列映射规则 |
| 修改校验规则 | 业务服务 application | 调整 `StepExtensionAction` 实现或 `BusinessConfigGateway` 返回的规则 |
| 修改步骤流程 | 配置中心 | 调整 `step-routes.json` 配置 |
| 修改材料规则 | 配置中心 + 业务服务 | 调整 `BusinessConfigGateway` 返回的材料配置和计算逻辑 |
| 修改明细摄入 | 业务服务 application | 调整 `AbstractJsonStreamIngestionAction` 子类的映射逻辑 |

---

## 三、解析结果存储位置

表单解析结果存储在**两个层面**：

### 3.1 file-service 暂存区（原始解析结果）

file-service 将 Excel 解析为标准数据模型（Canonical Model），按业务键拆分为多个子任务（SubTask），每个子任务持久化到 PostgreSQL（JSONB 列），生成唯一 `subTaskId`。

核心数据模型：

```
ParseFileTask (聚合根)
├── fileTaskId: String              // 唯一标识
├── bizType: String                 // 如 FORM_DETAIL
├── status: String                  // SUCCESS / PARTIAL / FAILED
├── SubTaskData[]                   // 拆分后的子任务
│   ├── subTaskId: String           // 子任务唯一标识
│   ├── splitKey: String            // 业务键值（如计划编号）
│   ├── data: Map<String, Object>   // 标准化数据
│   │   ├── properties (KV 简单变量) // 如 customerNo, customerName
│   │   └── tables (List<Map> 列表)  // 如 employees: [{seq, name, idType, idNo}]
│   └── FileMetadata (PARSED 类型)  // 关联的文件元数据
└── failureReason: String           // 失败原因（如果有）
```

解析完成后发送 `FileParsedEventDTO`（凭证式事件，仅携带 `fileTaskId`，消息体轻量）。

### 3.2 业务服务持久化（明细落库）

业务服务收到解析完成事件后，引擎推进到 `FORM_DETAIL_INGESTION` 步骤，执行 `AbstractJsonStreamIngestionAction` 子类：

1. 通过 `FileIntegrationGateway.downloadStream(jsonFileId, context)` 两阶段令牌下载 JSON 流
2. Jackson `JsonParser` 流式逐行解析（防 OOM）
3. 将每行 JSON 映射为业务实体（如 `AnnuityEmployeeDetail`）
4. 批量保存到业务表（如 `t_annuity_employee_detail`）

### 3.3 申请单上的文件引用

`BusinessApplication` 聚合根的 `parsedJsonFileId` 字段记录了解析后 JSON 文件的引用凭证，持久化在 `t_business_application.parsed_json_file_id` 列。

---

## 四、业务服务获取表单内容的方式

### 4.1 方式一：流式摄入（已实现，推荐）

通过 `AbstractJsonStreamIngestionAction` 子类，在步骤管道的 `detailProcessors` 阶段自动执行。引擎在推进到 `FORM_DETAIL_INGESTION` 步骤时，自动调用注册的摄入 Action：

```java
// 年金实现示例：AnnuityDetailIngestionAction
// 引擎自动调用 execute():
//   1. app.parsedJsonFileId() 获取 JSON 文件凭证
//   2. fileGateway.downloadStream(jsonFileId, context) 流式下载
//   3. Jackson JsonParser 逐行解析
//   4. mapToEntity() 映射为业务实体
//   5. saveBatch() 批量落库（REQUIRES_NEW 短事务）
//   6. 批次失败时降级为单条重试
```

### 4.2 方式二：分页拉取 API（file-service 提供，设计中）

file-service 提供 `GET /parsed-data` 接口，业务服务可通过 `subTaskId` + 分页参数拉取标准数据模型的子集，支持断点续传。当前代码中暂未看到此 API 的具体调用。

---

## 五、出站表单工作流程设计

### 5.1 设计约束与核心原则

| 约束 | 说明 |
|------|------|
| 标准数据模型主存于 file-service | file-service 是"表单数据中枢"，拥有完整的标准化数据模型（Canonical Model） |
| 业务服务以读取、校验为主 | 业务服务通过流式/分页拉取获取数据，进行校验、审批等流程处理 |
| 业务服务少量写回场景 | 极少数场景下业务服务需要向标准数据模型补充数据（如审批结果、经办人签名等） |
| Excel 生成使用 Fesod 目标模板填充 | 利用 Fesod 的模板填充能力，保留外部系统的 Excel 样式 |

**核心设计原则：数据不出域、凭证驱动、file-service 统一写盘**

### 5.2 出站管线总体架构

```
┌──────────────────┐    凭证事件     ┌───────────────────┐   回调事件   ┌──────────────────┐
│   业务服务        │ ─────────────▶ │    file-service    │ ──────────▶ │ integration-svc  │
│ (kernel 编排)     │               │  (出站管线)         │             │ (交易提交)        │
│                  │ ◀───────────── │                    │ ◀────────── │                  │
└──────────────────┘  分页拉取/     └───────────────────┘  文件引用    └──────────────────┘
                      流式下载
```

### 5.3 出站管线四阶段设计

#### 阶段 1：业务服务触发出站

当业务流程推进到 `ARCHIVE` 步骤（归档/提交阶段），kernel 编排引擎执行出站触发：

```
FlowOrchestrationService.advanceStep(appId)
  → ARCHIVE 步骤的 mainProcessor = "archiveSubmissionHandler"
    → 调用 FileIntegrationGateway.triggerOutboundGeneration(...)
      参数：subTaskId, targetTemplateId, supplementalData(如有)
    → 返回异步凭证，引擎挂起 (SUSPEND_ASYNC_WAIT)
```

**关键设计 — 补充数据的传递：**

业务服务在校验/审批过程中产生的新数据（如审批结果、经办人签名、审批意见），通过 `supplementalData` 参数传递给 file-service，而非直接修改标准数据模型：

```java
// kernel 的 FileIntegrationGateway 新增方法
void triggerOutboundGeneration(
  FormId formId,                     // 表单ID
  String targetTemplateId,           // 目标模板ID（外部系统要求的格式）
  Map<String, Object> supplementalData  // 业务补充数据（少量）
);
```

`supplementalData` 的典型内容：

| 字段 | 来源 | 说明 |
|------|------|------|
| approvalResult | 审批步骤 | APPROVED / REJECTED |
| approverName | 审批步骤 | 审批人姓名 |
| approvalComment | 审批步骤 | 审批意见 |
| tellerNo | 经办步骤 | 经办人编号 |
| tellerName | 经办步骤 | 经办人姓名 |
| processingDate | 业务处理 | 业务办理日期 |

#### 阶段 2：file-service 合并数据并填充模板

file-service 收到出站请求后：

```
1. 根据 subTaskId 从暂存区加载标准数据模型 (CanonicalData)
2. 将 supplementalData 合并到 CanonicalData.properties 中
3. 根据 targetTemplateId 加载目标模板 (.xlsx)
4. 使用 FesodExcelExporter 执行模板填充
5. 将生成的文件存储到文件存储 (OSS/Minio)
6. 发布 OutboundFileGeneratedEvent（携带 fileId）
```

**Fesod 模板填充的关键流程：**

```java
// file-service 的出站用例
public class GenerateOutboundFileUseCase {

  void execute(String subTaskId, String targetTemplateId, Map<String, Object> supplementalData) {
    // 1. 加载标准数据
    CanonicalData data = parseFileTaskRepository.loadSubTaskData(subTaskId);

    // 2. 合并补充数据（业务服务的少量回写）
    Map<String, Object> mergedProperties = new LinkedHashMap<>(data.properties());
    mergedProperties.putAll(supplementalData);
    CanonicalData merged = CanonicalData.of(mergedProperties, data.tables());

    // 3. 转换为 SplitUnit 格式（Fesod 友好）
    SplitUnit unit = SplitUnit.of(subTaskId, merged);

    // 4. 加载目标模板流
    InputStream template = templateStorage.load(targetTemplateId);

    // 5. Fesod 模板填充
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    excelExporter.export(unit, template, out);

    // 6. 存储生成的文件
    FileId outboundFileId = fileStorageGateway.store(out.toByteArray(), targetTemplateId);

    // 7. 发布事件
    eventBus.publish(OutboundFileGeneratedEvent.of(subTaskId, outboundFileId));
  }
}
```

**Fesod 目标模板占位符规范：**

| 占位符类型 | 语法 | 数据来源 | 示例 |
|-----------|------|---------|------|
| 普通变量 | `{fieldName}` | CanonicalData.properties | `{customerNo}`, `{approvalResult}` |
| 列表变量 | `{.listName.field}` | CanonicalData.tables | `{.employees.name}` |
| 多列表前缀 | `{.prefix.field}` | FillWrapper 包装 | `{.employees.name}`, `{.accounts.accountNo}` |
| 补充变量 | `{fieldName}` | supplementalData 合并后 | `{approverName}`, `{tellerNo}` |

> 补充数据和标准属性在 Fesod 模板中**无语法差异**，合并后统一作为 properties 填充。

#### 阶段 3：业务服务收到出站文件事件

```
OutboundFileGeneratedEvent → OutboundFileEventListener.onOutboundFileGenerated()
  → FlowOrchestrationService.advanceByOutboundFileId(fileId)
    → 引擎推进 ARCHIVE → COMPLETED（或继续后续步骤）
```

#### 阶段 4：integration-service 提交到外部系统

出站文件生成后，有两种提交策略：

**策略 A：文件级提交（推荐，适用于外部系统接受文件上传）**

```
业务服务 → IntegrationGateway.submitOutboundFile(outboundFileId, tradeCode)
  → integration-service 加载文件
  → 通过 BaseTradeGateway.performTradeCall() 提交到外部系统
  → 返回提交结果事件
```

**策略 B：数据级提交（适用于外部系统接受结构化报文）**

```
业务服务 → IntegrationGateway.submitTradeData(subTaskId, tradeCode)
  → integration-service 从 file-service 拉取标准数据
  → 转换为外部系统报文格式 (QueryXXXXReq)
  → 通过 BaseTradeGateway.performTradeCall() 提交
  → 返回提交结果事件
```

### 5.4 出站管线完整时序

```
业务服务(kernal)           file-service              integration-service
     │                         │                          │
     │  triggerOutboundGen     │                          │
     │  (subTaskId, tplId,    │                          │
     │   supplementalData)    │                          │
     │ ─────────────────────▶ │                          │
     │                         │ 1. 加载 CanonicalData    │
     │                         │ 2. 合并 supplementalData │
     │                         │ 3. Fesod 模板填充        │
     │                         │ 4. 存储出站文件          │
     │                         │                          │
     │     SUSPEND_ASYNC_WAIT │                          │
     │                         │                          │
     │  OutboundFileGeneratedEvent                        │
     │ ◀───────────────────── │                          │
     │                         │                          │
     │  advanceStep (继续流程) │                          │
     │                         │                          │
     │  submitOutboundFile     │                          │
     │  (outboundFileId,       │                          │
     │   tradeCode)            │                          │
     │ ──────────────────────────────────────────────────▶│
     │                         │                          │ BaseTradeGateway
     │                         │                          │ .performTradeCall()
     │                         │                          │
     │     TradeResultEvent    │                          │
     │ ◀──────────────────────────────────────────────────│
     │                         │                          │
     │  处理提交结果           │                          │
     │  (推进/终止/重试)       │                          │
```

### 5.5 file-service 新增组件

| 组件 | 层 | 职责 |
|------|------|------|
| `GenerateOutboundFileUseCase` | application | 出站用例：合并数据 + 模板填充 + 存储 |
| `OutboundFileGeneratedEvent` | domain/event | 出站文件生成完成领域事件 |
| `TargetTemplateStorage` | domain/gateway | 目标模板加载 SPI |
| `LocalTargetTemplateStorage` | infrastructure | 从 classpath/DB 加载目标模板实现 |
| `ExcelExporter` | domain/gateway | 已存在，Fesod 模板填充 SPI |
| `FesodExcelExporter` | infrastructure | 已存在，Fesod 实现 |

### 5.6 kernel 新增组件

| 组件 | 层 | 职责 |
|------|------|------|
| `FileIntegrationGateway.triggerOutboundGeneration()` | domain/gateway | 新增方法：触发出站文件生成 |
| `ArchiveSubmissionHandler` | application | ARCHIVE 步骤主处理器 |
| `OutboundFileEventListener` | application/listener | 监听出站文件生成事件，推进引擎 |

### 5.7 integration-service 新增组件

| 组件 | 层 | 职责 |
|------|------|------|
| `FileSubmissionGateway` | domain/gateway | 文件提交 SPI |
| `FileSubmissionGatewayImpl` | infrastructure | 文件提交实现，从 file-service 下载文件后提交 |
| 新增 `@TradeCode` Req/Res | infrastructure | 对应外部系统的提交报文格式 |

### 5.8 补充数据写回的边界约定

`supplementalData` 仅限于以下场景，**禁止业务服务直接修改标准数据模型**：

| 场景 | 补充字段 | 写入时机 |
|------|----------|----------|
| 审批结果回填 | approvalResult, approverName, approvalComment | APPROVAL_RESULT 步骤 |
| 经办信息回填 | tellerNo, tellerName | TELLER_REVIEW 步骤 |
| 业务办理日期 | processingDate | ARCHIVE 步骤 |
| 机构信息回填 | branchNo, branchName | APPLICATION_REVIEW 步骤 |

**禁止事项：**

- ❌ 业务服务直接修改 CanonicalData 的 tables 数据
- ❌ 业务服务直接修改源模板解析出的 properties 数据
- ❌ 业务服务绕过 file-service 直接生成目标 Excel
- ❌ supplementalData 中的字段与标准属性重名（必须使用独立命名空间前缀，如 `biz_`）

---

## 六、TODO 清单

### 6.1 已完成项

- [x] 入站管线完整闭环（Excel 解析 → 校验 → 拆分 → 事件通知 → 分页拉取）
- [x] `ExcelExporter` SPI 定义（file-domain/gateway）
- [x] `FesodExcelExporter` 实现（file-infrastructure/gateway）
- [x] `AbstractJsonStreamIngestionAction` 基类（kernel）
- [x] `AnnuityDetailIngestionAction` 年金实现
- [x] 步骤管道编排引擎（FlowOrchestrationService）
- [x] 事件驱动自动推进（3 个 Listener）
- [x] integration-service 通用交易框架（BaseTradeGateway）

### 6.2 待实现项

#### 出站管线 — file-service 侧

- [ ] **TODO-01**: 定义 `OutboundFileGeneratedEvent` 领域事件
  - 位置: `file-domain/event/OutboundFileGeneratedEvent.java`
  - 内容: subTaskId, outboundFileId, targetTemplateId, generatedTime
  - 约束: 实现 DomainEvent 接口，提供 static of() 方法

- [ ] **TODO-02**: 定义 `TargetTemplateStorage` Gateway SPI
  - 位置: `file-domain/gateway/TargetTemplateStorage.java`
  - 方法: `InputStream load(String targetTemplateId)`
  - 约束: domain 层无外部依赖

- [ ] **TODO-03**: 实现 `LocalTargetTemplateStorage`
  - 位置: `file-infrastructure/gateway/LocalTargetTemplateStorage.java`
  - 逻辑: 先查 DB（t_target_template 表），未命中则从 classpath 加载
  - 约束: 支持 classpath: 前缀

- [ ] **TODO-04**: 实现 `GenerateOutboundFileUseCase`
  - 位置: `file-application/usecase/GenerateOutboundFileUseCase.java`
  - 逻辑: 加载 CanonicalData → 合并 supplementalData → Fesod 模板填充 → 存储 → 发布事件
  - 约束: 事务边界仅在事件发布处

- [ ] **TODO-05**: 新增 file-service REST API 触发出站
  - 位置: `file-adapter/controller/OutboundController.java`
  - 端点: `POST /api/v1/file/outbound/generate`
  - 请求体: `{ subTaskId, targetTemplateId, supplementalData }`

- [ ] **TODO-06**: 设计目标模板管理 DDL
  - 位置: `file-infrastructure/src/main/resources/schema-pg.sql`
  - 表: `t_target_template` (id, template_id, template_name, file_id, description, created_time)
  - 索引: `uk_target_template_template_id`

- [ ] **TODO-07**: 编写出站管线集成测试
  - 位置: `file-infrastructure/src/test/.../OutboundFlowIntegrationTest.java`
  - 场景: 加载标准数据 + 补充数据 → 填充模板 → 验证输出文件 → round-trip 解析验证

#### 出站管线 — kernel 侧

- [ ] **TODO-08**: `FileIntegrationGateway` 新增 `triggerOutboundGeneration` 方法
  - 位置: `business-core-domain/.../gateway/FileIntegrationGateway.java`
  - 签名: `void triggerOutboundGeneration(FormId formId, String targetTemplateId, Map<String, Object> supplementalData)`

- [ ] **TODO-09**: 实现 `FileServiceIntegrationGateway.triggerOutboundGeneration()`
  - 位置: `business-core-infrastructure/.../gateway/FileServiceIntegrationGateway.java`
  - 逻辑: 调用 file-service 的 `POST /api/v1/file/outbound/generate`

- [ ] **TODO-10**: 实现 `ArchiveSubmissionHandler`
  - 位置: `business-core-application/.../step/handler/ArchiveSubmissionHandler.java`
  - 逻辑: 收集 supplementalData → 调用 triggerOutboundGeneration → 返回 SUSPEND_ASYNC_WAIT
  - 注册: handlerName = "archiveSubmissionHandler"

- [ ] **TODO-11**: 实现 `OutboundFileEventListener`
  - 位置: `business-core-application/.../listener/OutboundFileEventListener.java`
  - 逻辑: 监听 OutboundFileGeneratedEventDTO → 查找申请单 → advanceStep

- [ ] **TODO-12**: 配置 ARCHIVE 步骤的 step-routes
  - 位置: 配置中心的 `step-routes.json`
  - 内容: ARCHIVE 步骤的 mainProcessor = "archiveSubmissionHandler"

- [ ] **TODO-13**: 定义 `OutboundFileGeneratedEventDTO`（集成事件 DTO）
  - 位置: `file-api/event/OutboundFileGeneratedEventDTO.java`

#### 出站管线 — integration-service 侧

- [ ] **TODO-14**: 定义 `FileSubmissionGateway` SPI
  - 位置: `integration-service-domain/gateway/FileSubmissionGateway.java`
  - 方法: `SubmissionResult submitFile(FileId outboundFileId, String tradeCode)`

- [ ] **TODO-15**: 实现 `FileSubmissionGatewayImpl`
  - 位置: `integration-service-infrastructure/gateway/FileSubmissionGatewayImpl.java`
  - 逻辑: 从 file-service 下载文件 → 构造提交报文 → BaseTradeGateway.performTradeCall()

- [ ] **TODO-16**: 新增提交类交易码的 Req/Res DTO
  - 位置: `integration-service-infrastructure/core/trade/dto/`
  - 约束: 使用 @TradeCode 注解，字段映射通过 @JsonProperty

#### 模板配置

- [ ] **TODO-17**: 设计源模板与目标模板的映射关系配置
  - 位置: 配置中心
  - 内容: 每个业务类型 → 源模板ID + 目标模板ID 的映射
  - 格式: YAML 或 DB 配置表

- [ ] **TODO-18**: 制作首批目标模板文件
  - 位置: `docs/excel/target-templates/`
  - 格式: .xlsx 文件，使用 Fesod 占位符语法（{fieldName} / {.listName.field}）

#### 架构约束与规范

- [ ] **TODO-19**: 制定 supplementalData 字段命名规范
  - 约束: 使用 `biz_` 前缀避免与标准属性冲突
  - 示例: biz_approvalResult, biz_tellerName, biz_processingDate

- [ ] **TODO-20**: 定义出站文件的生命周期管理策略
  - 内容: TTL 自动清理、归档策略、与业务数据的解绑时机

- [ ] **TODO-21**: 补充 file-service API 层的鉴权与限流
  - 当前状态: Phase 1 未实现，出站 API 必须补充

---

## 七、风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| Fesod FillWrapper API 兼容性 | 目标模板填充失败 | 实现前先写 smoke test 验证 API |
| supplementalData 与标准属性重名 | 数据覆盖导致不一致 | 强制 `biz_` 前缀，运行时校验 |
| 大数据量模板填充 OOM | 生成文件失败 | Fesod 自带流式写入，但需验证万行级场景 |
| 出站文件提交后外部系统格式变更 | 提交被拒 | 目标模板版本化管理，灰度切换 |
| file-service 暂存区数据过期 | 出站时数据已清理 | 出站前检查 TTL，过期则重新解析 |

---

## 八、扩展新目标模板的步骤

当外部系统新增或修改表单格式时：

| 步骤 | 操作 | 位置 |
|------|------|------|
| 1 | 制作新的目标模板 .xlsx 文件（使用 Fesod 占位符） | docs/excel/target-templates/ |
| 2 | 上传目标模板到 file-service（通过 API 或 DB） | file-service |
| 3 | 在配置中心添加 源模板 → 目标模板 的映射关系 | 配置中心 |
| 4 | 如需新的提交交易码，按 integration-service 扩展步骤新增 | integration-service |
| 5 | 端到端测试（出站生成 → 文件提交 → 外部系统验证） | 全链路 |
