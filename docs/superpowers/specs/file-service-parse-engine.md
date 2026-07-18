# File Service 表单解析与转换引擎设计文档（Phase 1）

## 文档信息

| 项 | 值 |
|----|----|
| 标题 | File Service 表单解析与转换引擎设计 |
| 阶段 | Phase 1（入站管线完整闭环） |
| 状态 | 待审阅 |
| 作者 | Trae AI + 用户协作设计 |
| 创建日期 | 2026-07-18 |
| 技术栈 | JDK 25 / Spring Boot 3.5.14 / MyBatis-Flex 1.11.5 / PostgreSQL / Apache Fesod 2.0.2 / Aviator 5.4.3 |

---

## 1. 背景与目标

### 1.1 业务背景

系统业务办理过程需要客户上传 Excel 表单，系统解析表单进行数据校验和计算处理，完成后将数据转换为外部系统的表单格式并提交给外部系统。

需求特点：
- 客户上传的表单样式多样（多种源模板对应同一业务类型）
- 外部系统的表单格式相对固定，偶尔会增加或修改字段
- 单文件可能达到万行级数据量
- 解析后需要按业务键拆分为多个子任务

### 1.2 设计目标

构建一个**灵活的数据解析和转换引擎**，通过配置驱动支持：
1. 多样化的源表单解析（区域状态机 + 配置化区域定义）
2. 标准化的数据校验与派生计算（Aviator 表达式引擎）
3. 按业务键拆分子任务（独立持久化 + 独立校验）
4. 凭证式事件通知 + 分页拉取（避免大消息体 + 流式处理）

### 1.3 Phase 1 范围

**包含**：
- 模块脚手架（7 层 DDD 结构）
- 配置管理（DB + YAML 加载）
- 解析引擎（Fesod + 区域状态机）
- 校验/计算引擎（Aviator）
- 拆分引擎
- 数据持久化（PostgreSQL JSONB + 分页查询）
- 领域事件发布（含 `shared-event-starter` 重构）
- 分页拉取 REST API

**不包含**（Phase 2）：
- 目标 Excel 生成（基于 Fesod 模板填充）
- 配置管理 REST API（增删改查）
- TTL 自动清理任务
- 鉴权与限流

### 1.4 核心交互流程

```
┌──────────────┐  上传Excel   ┌─────────────────────────┐  领域事件(MQ)   ┌──────────────┐
│  业务服务     │ ───────────▶ │      file-service        │ ──────────────▶ │  业务服务     │
│              │              │  (入站管线)               │                 │  收到凭证     │
│              │ ◀─────────── │                          │ ◀────────────── │              │
└──────────────┘  分页拉取    └─────────────────────────┘  GET /parsed-data └──────────────┘
                  (REST)
```

**关键设计**：引入"数据暂存区"，将解析任务从"数据推送"改为"凭证拉取"：
1. 引擎解析、校验、计算、拆分完成后，将拆分出的 N 个标准数据模型分别持久化
2. 引擎为每个拆分后的子任务生成唯一的 `subTaskId`
3. 发送领域事件（仅携带凭证，消息体轻量）
4. 业务服务收到事件后，根据 `subTaskId` 调用引擎 API 分页拉取

**架构价值**：
- **数据自治**：引擎是"临时工"，通过 TTL 机制自动清理过期数据
- **避免分布式事务陷阱**：业务方何时落库、落库多少、失败如何重试完全自控
- **流式处理可控性**：业务服务可保持极低内存占用，支持断点续传

---

## 2. 限界上下文与模块结构

### 2.1 限界上下文

**File Service（文件服务）** 作为一个独立的限界上下文，核心职责是"表单数据中枢"：

- **入站解析**：把客户上传的多样的 Excel 表单解析为标准数据模型（Canonical Model）
- **校验与计算**：基于 Aviator 表达式做跨字段校验和派生计算
- **拆分**：按业务键把一份解析结果切成多个子任务
- **暂存与分发**：把拆分后的子任务持久化，发领域事件通知业务服务，提供分页拉取 API

**边界外**（不在本上下文）：
- 业务流程编排（由 business-core-kernel / customer-service 负责）
- 与外部系统的实际提交动作（由 integration-service 负责）
- 目标 Excel 生成（Phase 2）

### 2.2 模块结构

遵循项目既有的 DDD 七层划分，新建 `file-service` 顶级模块：

```
file-service/
├── file-types              # 领域原语：FileTaskId, SubTaskId, BizType, TemplateCode 等
├── file-domain             # 聚合根、实体、值对象、领域服务、SPI、领域事件、错误码
├── file-api                # DTO、Command、Query、对外接口定义、集成事件
├── file-application        # 应用服务、流程编排
├── file-adapter            # Controller、DTO 转换
├── file-infrastructure     # Repository 实现、Fesod 解析器实现、Aviator 引擎实现、MyBatis-Flex
└── file-starter            # 启动类、application.yml、Excel 模板资源
```

### 2.3 跨服务依赖与通信

- **入站**：业务服务调用 file-service 的"上传解析"REST API
- **出站通知**：file-service 通过 `shared-event-starter`（RocketMQ/Redis dispatcher）发送领域事件
- **数据拉取**：业务服务通过 REST API 分页拉取 canonical data
- **共享内核**：依赖 `business-core-api`，领域事件类放在 `file-api` 模块供消费方引用

### 2.4 与现有基础设施的集成

| 能力 | 复用模块 | 用途 |
|------|---------|------|
| ID 生成 | `shared-id-starter` | 生成 `FileTaskId`、`SubTaskId`（ULID） |
| 事件发布 | `shared-event-starter`（重构后） | 事务后异步投递领域事件 |
| 缓存 | `shared-cache-starter` | 配置缓存（Caffeine + Redisson 双层） |
| 异常 | `shared-exception` | `BusinessException`/`DomainException`/`SystemException` |
| Web | `shared-web-starter` | 全局异常处理、链路追踪 |
| 日志 | `shared-logging-starter` | HTTP 请求日志 |

### 2.5 新增顶级依赖

- `org.apache.fesod:fesod-sheet:2.0.2-incubating` —— Excel 读写/填充（放在 `file-infrastructure`）
- `com.googlecode.aviator:aviator:5.4.3` —— 表达式引擎（放在 `file-infrastructure`，因 domain 层禁外部库）

---

## 3. 领域模型设计

### 3.1 聚合划分

3 个聚合根，各自是独立的一致性边界：

| 聚合根 | 职责 | 一致性边界 |
|--------|------|-----------|
| `ParseTask`（解析任务） | 一次 Excel 上传解析的全过程 | 主任务状态、拆分结果聚合点 |
| `SubTaskData`（子任务数据） | 拆分后的一份标准数据 + 校验结果 | 子任务级状态、行数据 |
| `TemplateConfig`（模板配置） | 业务基线 + 源模板配置 | 配置版本与生效 |

**为什么是 3 个而不是 1 个大聚合**：
- `SubTaskData` 数据量可能很大（万行级），若与 `ParseTask` 同聚合，加载主任务会拖死
- `TemplateConfig` 是配置态，与运行态分离
- 拆分后业务服务按 `subTaskId` 独立拉取，`SubTaskData` 必须可独立加载

### 3.2 领域原语（file-types）

```java
// 任务标识
@IdDefinition(type = IdType.ULID)
public record FileTaskId(String value) implements Identifier<String> {}

@IdDefinition(type = IdType.ULID)
public record SubTaskId(String value) implements Identifier<String> {}

@IdDefinition(type = IdType.ULID)
public record TemplateConfigId(String value) implements Identifier<String> {}

// 业务编码
public record BizType(String value) implements Identifier<String> {}
public record TemplateCode(String value) implements Identifier<String> {}
```

`FileTaskId`/`SubTaskId` 用 ULID（无需中心化号段，分布式友好）；`BizType`/`TemplateCode` 虽实现 `Identifier` 但语义是业务编码。

### 3.3 聚合根 1：ParseTask

```
ParseTask (AggregateRoot<FileTaskId>)
├── bizType: BizType
├── templateCode: TemplateCode                // 命中的源模板（AUTO 模式由识别器决定）
├── sourceFileName: String
├── sourceFileRef: String                     // 原始文件存储引用（短期保留）
├── status: TaskStatus                        // PENDING/PARSING/SPLITTING/VALIDATING/SUCCESS/PARTIAL_SUCCESS/FAILED
├── totalRows: int                            // 解析出的总行数（拆分前）
├── errorPolicy: ErrorPolicy                  // FAIL_FAST/COLLECT_ALL/SKIP_ERROR_ROWS
├── splitKeys: List<String>                   // 拆分键路径，如 ["detailList.deptCode"]
├── subTaskSummaries: List<SubTaskSummary>    // 拆分摘要（不含行数据，只含凭证）
├── errors: List<TaskError>                   // 任务级错误
├── startedAt / finishedAt: LocalDateTime
└── 方法：
    ├── markParsing() / markSplitting() / markValidating()
    ├── recordSubTask(SubTaskSummary)
    ├── markSuccess() / markPartialSuccess(int failedCount) / markFailed(TaskError)
    └── validateInvariants()
```

**SubTaskSummary 值对象**：
```java
public record SubTaskSummary(
    SubTaskId subTaskId,
    String splitKeyValue,
    int totalRows,
    int validRows,
    int invalidRows,
    SubTaskStatus status
) implements ValueObject {}
```

### 3.4 聚合根 2：SubTaskData

```
SubTaskData (AggregateRoot<SubTaskId>)
├── fileTaskId: FileTaskId                    // 反向引用主任务
├── bizType: BizType
├── splitKeyValue: String
├── context: BusinessContext                  // 上下文（含 promoteToContext 提升的拆分键）
├── properties: Map<String, Object>           // canonicalModel.properties 的数据
├── tables: Map<String, List<Map<String, Object>>>  // canonicalModel.tables 的数据
├── rowCount: int
├── status: SubTaskStatus                     // PENDING/VALID/INVALID/CONSUMED/EXPIRED
├── validationErrors: List<RowError>          // 行级校验错误
├── createdAt / expiresAt: LocalDateTime      // TTL 用
└── 方法：
    ├── applyValidationResult(ValidationResult)
    ├── markConsumed()
    ├── isExpired()
    └── validateInvariants()
```

**BusinessContext 值对象**：
```java
public record BusinessContext(Map<String, Object> variables) implements ValueObject {
    public static BusinessContext empty() { return new BusinessContext(Map.of()); }
    public BusinessContext with(String key, Object value) { ... }
}
```

**RowError 值对象**：
```java
public record RowError(
    int rowIndex,
    String tableCode,
    String expr,
    String message
) implements ValueObject {}
```

### 3.5 聚合根 3：TemplateConfig

```
TemplateConfig (AggregateRoot<TemplateConfigId>)
├── bizType: BizType
├── version: String
├── errorPolicy: ErrorPolicy
├── canonicalModel: CanonicalModelDef
├── validationRules: List<ValidationRule>
├── derivationRules: List<DerivationRule>
├── splitConfig: SplitConfig
├── sourceTemplates: List<SourceTemplateDef>  // 关联的源模板（1:N）
├── targetTemplateRef: String                 // Phase 2 用
├── targetMapping: TargetMapping              // Phase 2 用
├── status: ConfigStatus                      // DRAFT/ACTIVE/DEPRECATED
├── effectiveFrom / effectiveTo: LocalDateTime
└── 方法：
    ├── activate() / deprecate()
    ├── findSourceTemplate(TemplateCode): Optional<SourceTemplateDef>
    ├── autoIdentify(List<String> headers): Optional<SourceTemplateDef>
    └── validateInvariants()
```

`TemplateConfig` 把"业务基线 + 关联的所有源模板"聚合为一个整体。`SourceTemplateDef` 是聚合内的实体（不是独立聚合根），因为它脱离了 `TemplateConfig` 没有独立含义。

### 3.6 配置定义值对象集合

这些是 `TemplateConfig` 内部的值对象/实体，对应 YAML 配置结构：

```java
// 标准模型定义
public record CanonicalModelDef(
    List<PropertyFieldDef> properties,
    List<TableDef> tables
) implements ValueObject {}

public record PropertyFieldDef(String code, FieldType type, boolean required, String pattern) {}
public record TableDef(String code, List<FieldDef> fields) {}
public record FieldDef(String code, FieldType type, boolean required, Integer scale) {}

// 校验与派生
public record ValidationRule(ValidationScope scope, String expr, String message) {}
public record DerivationRule(String field, String expr) {}

// 拆分配置
public record SplitConfig(
    List<String> keys,
    SplitMissPolicy onMiss,            // ERROR/IGNORE/DEFAULT
    String defaultOnMissValue,
    String fileNamingTemplate,         // Phase 2 用
    boolean promoteToContext
) {}

// 源模板定义（聚合内实体）
public class SourceTemplateDef {
    private TemplateCode templateCode;
    private IdentifyRule identify;
    private List<RegionDef> regions;
}

public record IdentifyRule(IdentifyMode mode, List<String> fingerprint) {}
public record RegionDef(
    String name, RegionType type,       // KEY_VALUE/TABLE
    String bindTo,
    RegionTrigger trigger,              // 可选
    RegionStrategy strategy             // KvStrategy 或 TableStrategy
) {}

public sealed interface RegionStrategy permits KvStrategy, TableStrategy {}

// KEY_VALUE 区域策略
public record KvStrategy(
    KvValuePosition valuePosition,      // RIGHT/BELOW
    Map<String, List<String>> labelAliases,
    int maxBlankRows                    // 默认 3
) {}

// TABLE 区域策略
public record TableStrategy(
    int headerRows,                     // 默认 1
    TableMatchBy matchBy,               // HEADER_NAME/COLUMN_INDEX
    Map<String, List<String>> columnAliases,
    DataEndRule dataEnd
) {}

public record DataEndRule(
    List<String> markers,
    int blankRowCount
) {}

public record RegionTrigger(
    TriggerMatchType matchType,         // HEADER_SNIFF/REGEX
    int minMatchCount
) {}
```

这些配置定义类放在 `file-domain`。它们是不可变的（record），不依赖任何外部框架，符合领域层约束。SnakeYAML/Jackson 反序列化在 `file-infrastructure` 完成，反序列化后传入领域层。

### 3.7 领域事件（file-domain）

领域事件严格定义在 `file-domain` 层，实现 `DomainEvent` 接口：

```java
public record FileParsedEvent(
    EventId eventId,
    LocalDateTime occurredOn,
    FileTaskId fileTaskId,
    BizType bizType,
    TaskStatus status,
    int totalSubTasks,
    List<SubTaskSummary> subTasks,
    String failureReason
) implements DomainEvent {
    
    public static FileParsedEvent of(ParseTask task) {
        return new FileParsedEvent(
            EventId.generate(),
            LocalDateTime.now(),
            task.id(),
            task.bizType(),
            task.status(),
            task.subTaskSummaries().size(),
            task.subTaskSummaries(),
            task.errors().isEmpty() ? null : task.errors().toString()
        );
    }
}
```

**重要**：领域事件保留完整的领域语义，引用 `FileTaskId`、`BizType`、`SubTaskSummary` 等领域对象，**仅本服务可见**。跨服务通过集成事件 DTO 通信（见 §7.7）。

### 3.8 枚举

```java
public enum TaskStatus { PENDING, PARSING, SPLITTING, VALIDATING, SUCCESS, PARTIAL_SUCCESS, FAILED }
public enum SubTaskStatus { PENDING, VALID, INVALID, CONSUMED, EXPIRED }
public enum ErrorPolicy { FAIL_FAST, COLLECT_ALL, SKIP_ERROR_ROWS }
public enum FieldType { STRING, DECIMAL, INTEGER, DATE, BOOLEAN }
public enum RegionType { KEY_VALUE, TABLE }
public enum IdentifyMode { AUTO, MANUAL }
public enum SplitMissPolicy { ERROR, IGNORE, DEFAULT }
public enum ValidationScope { ROW, GLOBAL }
public enum ConfigStatus { DRAFT, ACTIVE, DEPRECATED }
public enum KvValuePosition { RIGHT, BELOW }
public enum TableMatchBy { HEADER_NAME, COLUMN_INDEX }
public enum TriggerMatchType { HEADER_SNIFF, REGEX }
```

### 3.9 领域服务

| 领域服务 | 职责 | 依赖 |
|---------|------|------|
| `ConfigRepository`(SPI) | 加载 `TemplateConfig` | 无（接口） |
| `SourceTemplateIdentifier` | AUTO 模式下根据表头指纹识别 | 纯领域逻辑 |
| `CanonicalModelBuilder` | 把区域解析结果组装为 canonical data | 纯领域逻辑 |
| `DataValidator` | 执行 `ValidationRule` | SPI: `ExpressionEvaluator` |
| `DataDeriver` | 执行 `DerivationRule` 派生字段 | SPI: `ExpressionEvaluator` |
| `TaskSplitter` | 按 `SplitConfig` 拆分 | 纯领域逻辑 |
| `RegionStateMachine` | 区域状态机驱动 | 纯领域逻辑 |
| `KeyValueRegionParser` | KEY_VALUE 区域解析 | 纯领域逻辑 |
| `TableRegionParser` | TABLE 区域解析 | 纯领域逻辑 |

`ExpressionEvaluator` 是 SPI 接口（在 domain 定义），Aviator 实现在 infrastructure。这样领域层不依赖 Aviator，符合"domain 层禁止依赖外部库"约束。

### 3.10 Repository 接口

```java
public interface ParseTaskRepository extends Repository<ParseTask, FileTaskId> {}

public interface SubTaskDataRepository extends Repository<SubTaskData, SubTaskId> {
    PagedRows findPagedRows(SubTaskId id, Pagination pagination);
    List<SubTaskSummary> findSummariesByTask(FileTaskId taskId);
}

public interface TemplateConfigRepository extends Repository<TemplateConfig, TemplateConfigId> {
    Optional<TemplateConfig> findActive(BizType bizType);
    Optional<TemplateConfig> findByBizTypeAndVersion(BizType bizType, String version);
}
```

---

## 4. 应用层与用例编排

### 4.1 应用服务总览

| 应用服务 | 用例 | 入口 |
|---------|------|------|
| `ParseExcelAppService` | 上传 Excel 并触发解析全流程 | REST POST `/api/v1/parse` |
| `QueryParsedDataAppService` | 分页拉取子任务数据 | REST GET `/api/v1/parsed-data/{subTaskId}` |
| `QueryParseTaskAppService` | 查询主任务状态/摘要 | REST GET `/api/v1/parse-tasks/{fileTaskId}` |
| `ManageConfigAppService` | 配置管理（YAML 导入 + DB 查询） | REST POST `/api/v1/configs/import`、GET `/api/v1/configs/{bizType}` |

### 4.2 核心用例：上传解析全流程

```
ParseExcelAppService.parseAndStore(cmd):
  输入: ParseExcelCommand { bizType, fileName, InputStream, operator, templateCode? }
  输出: ParseExcelResult { fileTaskId, status, totalRows, subTasks, errors }
  
  步骤:
  1. 创建 ParseTask（status=PENDING），保存到 Repository
  2. 加载 TemplateConfig（按 bizType 查 ACTIVE 配置，走缓存）
  3. AUTO 模式：读取 Excel 表头 → SourceTemplateIdentifier 识别 → 命中 SourceTemplateDef
     MANUAL 模式：使用指定的 SourceTemplateDef
  4. markParsing() → 调用 ExcelParser（Fesod 实现）按 regions 顺序解析
  5. CanonicalModelBuilder 组装 canonical data（properties + tables）
  6. markSplitting() → TaskSplitter 按 splitConfig 拆分为 N 份
  7. 对每份拆分结果（独立事务）：
     a. 创建 SubTaskData（status=PENDING），保存
     b. markValidating() → DataDeriver 执行派生 → DataValidator 执行校验
     c. 根据 errorPolicy 决定 sub-task 最终状态（VALID/INVALID）
     d. 更新 SubTaskData 保存
     e. 记录 SubTaskSummary 到 ParseTask
  8. 根据 N 个 sub-task 状态汇总主任务状态（SUCCESS/PARTIAL_SUCCESS/FAILED）
  9. markSuccess/markPartialSuccess/markFailed → 保存 ParseTask
  10. 注册 FileParsedEvent 到 ParseTask
  11. Repository.save(parseTask) 触发事件发布（事务后异步）
  12. 返回 ParseExcelResult
```

**事务边界**：整个用例**不**包在一个大事务里。拆分为多个小事务：
- T1: 创建 ParseTask（PENDING）
- T2: 解析 + 拆分（无 DB 写，纯内存操作）
- T3-Tn: 每个 SubTaskData 独立事务保存
- Tn+1: 更新 ParseTask 最终状态 + 注册事件（事务提交后发 MQ）

### 4.3 三引擎执行顺序（修正版）

```
canonical data
     │
     ▼
┌──────────────┐      ┌──────────────┐
│ DataDeriver  │ ───▶ │ TaskSplitter │  (派生在拆分前，因为拆分键可能依赖派生字段)
└──────────────┘      └──────┬───────┘
                             │
                             ▼
                      N 份 SubTaskData
                             │
                ┌────────────┼────────────┐
                ▼            ▼            ▼
          ┌──────────┐ ┌──────────┐ ┌──────────┐
          │Validator │ │Validator │ │Validator │  (每份独立校验)
          └────┬─────┘ └────┬─────┘ └────┬─────┘
               ▼            ▼            ▼
          VALID/INVALID  VALID/INVALID  VALID/INVALID
```

**顺序理由**：
- 派生先于拆分：因为拆分键可能依赖派生字段
- 校验在拆分后：因为"拆分后只有校验通过的才能生成外部表单"，校验状态需要随 sub-task 一起拆分

### 4.4 用例：分页拉取子任务数据

```
QueryParsedDataAppService.queryPaged(query):
  输入: PagedDataQuery { subTaskId, pagination }
  输出: PagedDataResult { subTaskId, splitKeyValue, status, pageInfo, rows, nextPageUrl }
  
  步骤:
  1. load SubTaskData（按 subTaskId）
  2. 校验状态：INVALID 返回错误信息 + 错误明细；EXPIRED 返回 410
  3. 校验 TTL：过期则标记 EXPIRED 并返回 410 Gone
  4. findPagedRows(subTaskId, pagination) 从 Repository 分页查询
  5. 返回 PagedDataResult（含 nextPageUrl）
```

**分页响应格式**：
```json
{
  "subTaskId": "sub_001",
  "splitKeyValue": "RD_DEPT",
  "status": "VALID",
  "context": { "deptCode": "RD_DEPT" },
  "properties": { "enterpriseName": "ABC公司", "declareDate": "2026-07-18" },
  "pageInfo": {
    "tableCode": "detailList",
    "totalCount": 1500,
    "startPos": 0,
    "returnedCount": 1000,
    "hasMore": true
  },
  "rows": [
    { "itemNo": "A001", "deptCode": "RD_DEPT", "amount": 100.00 }
  ],
  "nextPageUrl": "/api/v1/parsed-data/sub_001?tableCode=detailList&startPos=1000&pageSize=1000"
}
```

`FetchPagination` 是应用层独立定义的分页参数（不使用通用 `Pagination.MAX_PAGE_SIZE=100` 限制），上限 2000，默认 1000。

### 4.5 异步执行策略

整个解析流程是**同步执行**的（HTTP 请求线程内完成），原因：
- 解析是 CPU/IO 密集型，但单文件通常秒级完成
- 同步执行简化事务管理和错误处理
- 大文件场景由 Fesod 流式解析 + 分页拉取保障

两个异步点：
1. **事件发布**：`EventBus` 在事务提交后通过虚拟线程异步投递
2. **大文件超时保护**：HTTP 请求设置 5 分钟超时；若未来需要支持超长任务，再引入异步任务表

有意保持 YAGNI：不预先引入异步任务队列、不引入任务状态轮询 API。

### 4.6 错误处理策略

| 错误类型 | 处理方式 | HTTP 状态 |
|---------|---------|----------|
| 配置不存在 | `BusinessException` | 400 |
| Excel 格式错误 | `BusinessException`，任务状态 FAILED | 400 |
| 解析过程异常 | 任务状态 FAILED，事件 status=FAILED，返回 200 + 失败状态 | 200 |
| 校验失败（行级） | 不抛异常，记入 sub-task，状态 INVALID | 200 |
| 校验失败（任务级） | 视 errorPolicy 决定，可能 FAILED | 200 |
| 子任务过期 | `BusinessException` | 410 Gone |
| 系统异常 | `SystemException` | 500 |

**关键设计**：解析过程中的"业务错误"（如校验失败）不抛异常，而是记入任务状态。HTTP 200 表示"请求已被接受并处理"，具体成败看 `status` 字段。

### 4.7 errorPolicy 应用规则

| 策略 | 派生失败 | 必填/类型校验失败 | 表达式校验失败 |
|------|---------|-----------------|---------------|
| `FAIL_FAST` | 抛异常，任务 FAILED | 第一个错误即停止，sub-task INVALID | 第一个错误即停止，sub-task INVALID |
| `COLLECT_ALL` | 跳过该字段，记录错误 | 收集所有错误，sub-task INVALID | 收集所有错误，sub-task INVALID |
| `SKIP_ERROR_ROWS` | 跳过该行 | 跳过该行，不进 sub-task | 跳过该行，不进 sub-task |

`SKIP_ERROR_ROWS` 模式下，被跳过的行不会出现在任何 sub-task 中，但会在 ParseTask.errors 中记录总数。

---

## 5. 解析引擎设计——区域状态机

### 5.1 设计目标

源模板配置定义了"有序的区域集合"，引擎需要：
- 按行扫描 Excel，根据当前行内容判断"是否进入新区域"
- 在某个区域内，按该区域的 `strategy` 解析行数据
- 遇到退出标识时结束当前区域
- 全部行扫描完后，所有区域数据组装为 canonical data

**核心抽象**：把 Excel 看作一个**行流（Row Stream）**，每个 Region 是一个**状态**，扫描器是状态机。

### 5.2 核心抽象（file-domain）

```java
// 行数据（领域层抽象，不依赖 Fesod）
public record RawRow(
    int rowIndex,
    Map<Integer, String> cells,    // 列下标 → 单元格字符串值
    boolean isBlank
) {}

// 区域解析结果
public sealed interface RegionParseResult permits KvRegionResult, TableRegionResult, RegionSkip {}
public record KvRegionResult(String regionName, Map<String, Object> data) implements RegionParseResult {}
public record TableRegionResult(String regionName, List<Map<String, Object>> rows) implements RegionParseResult {}
public record RegionSkip() implements RegionParseResult {}

// 区域解析器 SPI
public interface RegionParser {
    RegionType supportedType();
    RegionParseResult parse(RawRowStream stream, RegionDef regionDef, ParseContext ctx);
}

// 行流游标
public interface RawRowStream {
    boolean hasNext();
    RawRow next();
    RawRow peek();
    int currentRowIndex();
}
```

**关键决策**：`RegionParser` 和 `RawRowStream` 是领域层定义的 SPI，`RawRow` 是与 Fesod 无关的纯领域抽象。Fesod 只在 infrastructure 层负责把 `Row` 转换为 `RawRow`，然后交给领域层的解析器。这样领域层完全不依赖 Fesod。

### 5.3 状态机引擎

```java
@DomainService
public class RegionStateMachine {
    
    private final Map<RegionType, RegionParser> parsers;
    
    public List<RegionParseResult> drive(RawRowStream stream, List<RegionDef> regions, ParseContext ctx) {
        List<RegionParseResult> results = new ArrayList<>();
        int regionIdx = 0;
        
        while (stream.hasNext() && regionIdx < regions.size()) {
            RawRow current = stream.peek();
            RegionDef target = regions.get(regionIdx);
            
            if (shouldEnterRegion(current, target, ctx)) {
                RegionParseResult result = parsers.get(target.type())
                    .parse(stream, target, ctx);
                results.add(result);
                regionIdx++;
            } else {
                stream.next();
            }
        }
        return results;
    }
}
```

**状态机流转图**：

```
┌─────────┐  无 trigger     ┌──────────────┐
│  START  │ ──────────────▶ │ Region[0]     │
└─────────┘                 │ (立即进入)    │
                            └──────┬───────┘
                                   │ RegionParser.parse 完成
                                   ▼
                            ┌──────────────┐
                            │ Region[1]     │
                            │ (trigger 判断)│
                            └──────┬───────┘
                                   │ ...
                                   ▼
                            ┌──────────────┐
                            │ Region[n]     │
                            └──────┬───────┘
                                   │ 行流耗尽
                                   ▼
                                  END
```

### 5.4 KEY_VALUE 区域解析器

支持两种布局：
- `RIGHT`：键在左、值在右（同一行）
- `BELOW`：键在上、值在下（相邻行）

```java
@DomainService
public class KeyValueRegionParser implements RegionParser {
    
    @Override
    public RegionParseResult parse(RawRowStream stream, RegionDef regionDef, ParseContext ctx) {
        KvStrategy strategy = (KvStrategy) regionDef.strategy();
        Map<String, String> labelAliases = strategy.labelAliases();
        Map<String, Object> data = new LinkedHashMap<>();
        int consecutiveBlank = 0;
        
        while (stream.hasNext()) {
            RawRow row = stream.peek();
            
            if (row.isBlank()) {
                if (++consecutiveBlank >= strategy.maxBlankRows()) break;
                stream.next();
                continue;
            }
            consecutiveBlank = 0;
            
            if (ctx.isNextRegionTrigger(row)) break;
            
            stream.next();
            Map<String, String> matched = matchLabels(row, labelAliases, strategy);
            data.putAll(matched);
        }
        
        return new KvRegionResult(regionDef.name(), data);
    }
}
```

### 5.5 TABLE 区域解析器

```java
@DomainService
public class TableRegionParser implements RegionParser {
    
    @Override
    public RegionParseResult parse(RawRowStream stream, RegionDef regionDef, ParseContext ctx) {
        TableStrategy strategy = (TableStrategy) regionDef.strategy();
        List<Map<String, Object>> rows = new ArrayList<>();
        
        Map<Integer, String> columnIndex = parseHeader(stream, strategy);
        
        while (stream.hasNext()) {
            RawRow row = stream.peek();
            if (isDataEnd(row, strategy.dataEnd())) break;
            stream.next();
            if (row.isBlank()) continue;
            Map<String, Object> rowData = mapRow(row, columnIndex, strategy);
            rows.add(rowData);
        }
        
        return new TableRegionResult(regionDef.name(), rows);
    }
}
```

### 5.6 Fesod 集成层（file-infrastructure）

只在 infrastructure 层出现 Fesod API。职责：把 Excel 文件读为 `RawRow` 流。

```java
@Component
public class FesodExcelReader {
    
    public void readStreaming(InputStream excelStream, RawRowConsumer consumer) {
        FesodSheet.read(excelStream, new ReadListener<Map<Integer, String>>() {
            @Override
            public void invoke(Map<Integer, String> data, AnalysisContext context) {
                int rowIndex = context.readRowHolder().getRowIndex();
                RawRow row = toRawRow(rowIndex, data);
                consumer.consume(row);
            }
            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                consumer.onComplete();
            }
        }).sheet().doRead();
    }
}
```

**桥接 Fesod 与领域状态机**：

```java
@Component
public class ExcelParserImpl implements ExcelParser {
    
    private final FesodExcelReader fesodReader;
    private final RegionStateMachine stateMachine;
    
    @Override
    public List<RegionParseResult> parse(InputStream excelStream, List<RegionDef> regions) {
        BlockingQueue<RawRow> queue = new LinkedBlockingQueue<>(1000);
        AtomicBoolean eof = new AtomicBoolean(false);
        
        Thread.startVirtualThread(() -> {
            fesodReader.readStreaming(excelStream, row -> queue.put(row));
            eof.set(true);
        });
        
        RawRowStream stream = new QueuedRawRowStream(queue, eof);
        ParseContext ctx = new ParseContext(regions);
        return stateMachine.drive(stream, regions, ctx);
    }
}
```

**设计要点**：Fesod 是推模式（监听器回调），状态机是拉模式（`peek`/`next`）。用阻塞队列桥接，生产者（Fesod）和消费者（状态机）并行，避免一次性把所有行载入内存。虚拟线程保证轻量级并发。

### 5.7 ParseContext

```java
public class ParseContext {
    private final List<RegionDef> regions;
    private int currentRegionIdx = 0;
    
    public boolean isNextRegionTrigger(RawRow row) {
        if (currentRegionIdx + 1 >= regions.size()) return false;
        RegionDef next = regions.get(currentRegionIdx + 1);
        if (next.trigger() == null) return false;
        return matchesTrigger(row, next.trigger());
    }
    
    public void enterRegion(int idx) { currentRegionIdx = idx; }
}
```

### 5.8 边界场景处理

| 场景 | 处理 |
|------|------|
| 区域无 trigger | 立即进入（适用于第一个区域或顺序确定的场景） |
| trigger 未命中 | 跳过该区域，进入下一个（容错） |
| 区域内遇到下一区域 trigger | 提前退出当前区域，当前行不消费 |
| 表头嗅探未达 minMatchCount | 跳过该行继续嗅探 |
| dataEnd 未配置 | 默认读到行流结束 |
| 表格列为空 | 该字段值为 null，校验阶段处理 required |
| 单元格类型不匹配 | 记录行级错误，按 errorPolicy 决定是否继续 |

---

## 6. 校验、计算、拆分引擎

### 6.1 表达式求值 SPI（file-domain）

Aviator 实现在 infrastructure，domain 只定义接口：

```java
public interface ExpressionEvaluator {
    /**
     * 在给定上下文中求值表达式
     * @param expr Aviator 表达式，如 "amount > 0" 或 "round(qty * unitPrice, 2)"
     * @param context 变量上下文（properties + 当前行 fields + BusinessContext）
     */
    Object evaluate(String expr, Map<String, Object> context);
}
```

### 6.2 Aviator 实现层（file-infrastructure）

```java
@Component
public class AviatorExpressionEvaluator implements ExpressionEvaluator {
    
    private final ConcurrentHashMap<String, Expression> compiled = new ConcurrentHashMap<>();
    
    @Override
    public Object evaluate(String expr, Map<String, Object> context) {
        Expression expression = compiled.computeIfAbsent(expr, AviatorEvaluator::compile);
        return expression.execute(context);
    }
}
```

**安全考虑**：Aviator 默认支持反射调用，生产环境应配置 `AviatorEvaluator.setOption(Options.FEATURE_SET, Feature.asSet(Feature.Assignment, Feature.Module))` 限制能力，避免恶意表达式。

### 6.3 派生引擎 DataDeriver

```java
@DomainService
public class DataDeriver {
    
    private final ExpressionEvaluator evaluator;
    
    public void derive(CanonicalData data, List<DerivationRule> rules, BusinessContext context) {
        List<DerivationRule> sorted = topologicalSort(rules);
        
        for (DerivationRule rule : sorted) {
            FieldLocation loc = FieldLocation.parse(rule.field());
            
            if (loc.isProperty()) {
                Map<String, Object> ctx = buildContext(data.properties(), context);
                Object value = evaluator.evaluate(rule.expr(), ctx);
                data.setProperty(loc.fieldName(), value);
            } else {
                List<Map<String, Object>> rows = data.tables().get(loc.tableCode());
                for (Map<String, Object> row : rows) {
                    Map<String, Object> ctx = buildRowContext(row, data.properties(), context);
                    Object value = evaluator.evaluate(rule.expr(), ctx);
                    row.put(loc.fieldName(), value);
                }
            }
        }
    }
}
```

**派生上下文构建**：
- properties 派生：`ctx = {所有 properties} + {context 变量}`
- table 行派生：`ctx = {当前行所有字段} + {所有 properties} + {context 变量}`

### 6.4 校验引擎 DataValidator

```java
@DomainService
public class DataValidator {
    
    private final ExpressionEvaluator evaluator;
    
    public ValidationResult validate(CanonicalData data, List<ValidationRule> rules, 
                                    BusinessContext context, ErrorPolicy errorPolicy) {
        List<RowError> errors = new ArrayList<>();
        
        errors.addAll(validateRequired(data));
        errors.addAll(validateTypes(data));
        
        for (ValidationRule rule : rules) {
            errors.addAll(validateRule(data, rule, context, errorPolicy));
            if (errorPolicy == ErrorPolicy.FAIL_FAST && !errors.isEmpty()) break;
        }
        
        return new ValidationResult(errors);
    }
}
```

### 6.5 拆分引擎 TaskSplitter

```java
@DomainService
public class TaskSplitter {
    
    public List<SubTaskData> split(CanonicalData data, SplitConfig config, 
                                   FileTaskId taskId, BizType bizType) {
        FieldLocation loc = FieldLocation.parse(config.keys().get(0));
        
        if (loc.isProperty()) {
            return splitByProperty(data, config, taskId, bizType);
        }
        return splitByTableField(data, loc, config, taskId, bizType);
    }
    
    private List<SubTaskData> splitByTableField(CanonicalData data, FieldLocation loc,
                                                SplitConfig config, FileTaskId taskId, BizType bizType) {
        List<Map<String, Object>> rows = data.tables().get(loc.tableCode());
        
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String keyValue = extractSplitKey(row, loc.fieldName(), config);
            grouped.computeIfAbsent(keyValue, k -> new ArrayList<>()).add(row);
        }
        
        List<SubTaskData> result = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
            String splitKeyValue = entry.getKey();
            
            CanonicalData subData = CanonicalData.of(
                data.properties(),
                Map.of(loc.tableCode(), entry.getValue())
            );
            
            BusinessContext ctx = config.promoteToContext()
                ? BusinessContext.empty().with(loc.fieldName(), splitKeyValue)
                : BusinessContext.empty();
            
            SubTaskData subTask = new SubTaskData(
                SubTaskId.generate(),
                taskId, bizType, splitKeyValue, ctx,
                subData.properties(), subData.tables(),
                entry.getValue().size(),
                SubTaskStatus.PENDING, List.of(),
                LocalDateTime.now(), calculateExpiresAt()
            );
            result.add(subTask);
        }
        return result;
    }
}
```

### 6.6 CanonicalData 领域对象

```java
public record CanonicalData(
    Map<String, Object> properties,
    Map<String, List<Map<String, Object>>> tables
) {
    public static CanonicalData empty() {
        return new CanonicalData(new LinkedHashMap<>(), new LinkedHashMap<>());
    }
    
    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }
    
    public static CanonicalData of(Map<String, Object> properties, 
                                   Map<String, List<Map<String, Object>>> tables) {
        return new CanonicalData(
            new LinkedHashMap<>(properties),
            tables.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, e -> new ArrayList<>(e.getValue())
            ))
        );
    }
}
```

### 6.7 FieldLocation 工具值对象

```java
public record FieldLocation(
    String tableCode,     // null 表示是 properties
    String fieldName
) {
    public static FieldLocation parse(String path) {
        int dot = path.indexOf('.');
        if (dot < 0) return new FieldLocation(null, path);
        return new FieldLocation(path.substring(0, dot), path.substring(dot + 1));
    }
    
    public boolean isProperty() { return tableCode == null; }
}
```

---

## 7. 数据持久化与配置管理

### 7.1 持久化技术栈

- **ORM**：MyBatis-Flex 1.11.5
- **数据库**：PostgreSQL（生产）+ H2（测试，PostgreSQL 兼容模式）
- **JSON 存储**：PostgreSQL JSONB
- **缓存**：Caffeine（本地）+ Redisson（分布式）

### 7.2 数据库表设计

#### 7.2.1 `file_parse_task`（解析主任务表）

```sql
CREATE TABLE file_parse_task (
    id              VARCHAR(64)   NOT NULL,
    biz_type        VARCHAR(64)   NOT NULL,
    template_code   VARCHAR(64),
    source_file_name VARCHAR(512) NOT NULL,
    source_file_ref VARCHAR(512),
    status          VARCHAR(32)   NOT NULL,
    error_policy    VARCHAR(32)   NOT NULL,
    split_keys      JSONB         NOT NULL,
    total_rows      INT           NOT NULL DEFAULT 0,
    sub_task_count  INT           NOT NULL DEFAULT 0,
    valid_count     INT           NOT NULL DEFAULT 0,
    invalid_count   INT           NOT NULL DEFAULT 0,
    errors          JSONB,
    started_at      TIMESTAMP     NOT NULL,
    finished_at     TIMESTAMP,
    created_by      VARCHAR(64)   NOT NULL,
    created_at      TIMESTAMP     NOT NULL,
    updated_by      VARCHAR(64)   NOT NULL,
    updated_at      TIMESTAMP     NOT NULL,
    version         INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_parse_task_biz_type ON file_parse_task(biz_type);
CREATE INDEX idx_parse_task_status ON file_parse_task(status);
CREATE INDEX idx_parse_task_created_at ON file_parse_task(created_at);
```

#### 7.2.2 `file_sub_task`（子任务表）

```sql
CREATE TABLE file_sub_task (
    id              VARCHAR(64)   NOT NULL,
    file_task_id    VARCHAR(64)   NOT NULL,
    biz_type        VARCHAR(64)   NOT NULL,
    split_key_value VARCHAR(128),
    context         JSONB         NOT NULL,
    properties      JSONB         NOT NULL,
    row_count       INT           NOT NULL DEFAULT 0,
    status          VARCHAR(32)   NOT NULL,
    validation_errors JSONB,
    expires_at      TIMESTAMP     NOT NULL,
    consumed_at     TIMESTAMP,
    created_by      VARCHAR(64)   NOT NULL,
    created_at      TIMESTAMP     NOT NULL,
    updated_by      VARCHAR(64)   NOT NULL,
    updated_at      TIMESTAMP     NOT NULL,
    version         INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_sub_task_file_task_id ON file_sub_task(file_task_id);
CREATE INDEX idx_sub_task_status ON file_sub_task(status);
CREATE INDEX idx_sub_task_expires_at ON file_sub_task(expires_at);
```

**关键设计**：子任务的 `properties` 用 JSONB 存储，但 `tables`（明细行数据）单独存到 `file_sub_task_row` 表。理由：明细可能万行级，JSONB 单行存储会导致单行过大，且无法高效分页查询。

#### 7.2.3 `file_sub_task_row`（子任务明细行表）

```sql
CREATE TABLE file_sub_task_row (
    id              BIGSERIAL     NOT NULL,
    sub_task_id     VARCHAR(64)   NOT NULL,
    table_code      VARCHAR(64)   NOT NULL,
    row_index       INT           NOT NULL,
    row_data        JSONB         NOT NULL,
    created_at      TIMESTAMP     NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_sub_task_row_query ON file_sub_task_row(sub_task_id, table_code, row_index);
```

这张表是分页拉取的核心。业务服务 `GET /parsed-data/{subTaskId}?startPos=0&pageSize=1000` 对应查询：
```sql
SELECT row_data FROM file_sub_task_row 
WHERE sub_task_id = ? AND table_code = ? AND row_index >= ? AND row_index < ?
ORDER BY row_index
```

#### 7.2.4 `file_template_config`（模板配置表）

```sql
CREATE TABLE file_template_config (
    id              VARCHAR(64)   NOT NULL,
    biz_type        VARCHAR(64)   NOT NULL,
    version         VARCHAR(32)   NOT NULL,
    error_policy    VARCHAR(32)   NOT NULL,
    config_body     JSONB         NOT NULL,
    target_template_ref VARCHAR(512),
    status          VARCHAR(32)   NOT NULL,
    effective_from  TIMESTAMP     NOT NULL,
    effective_to    TIMESTAMP,
    created_by      VARCHAR(64)   NOT NULL,
    created_at      TIMESTAMP     NOT NULL,
    updated_by      VARCHAR(64)   NOT NULL,
    updated_at      TIMESTAMP     NOT NULL,
    version         INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_template_config_active ON file_template_config(biz_type) 
    WHERE status = 'ACTIVE';
CREATE INDEX idx_template_config_biz_version ON file_template_config(biz_type, version);
```

**设计要点**：
- 配置整体存为一个 JSONB（`config_body`），避免拆成十几张子表带来的 JOIN 复杂度
- 用部分唯一索引保证同一 bizType 只有一个 ACTIVE（PostgreSQL 特性）
- H2 不支持部分唯一索引，测试时改用应用层校验

### 7.3 MyBatis-Flex 实体与领域对象映射

实体定义在 `file-infrastructure`，标注 `@Table`、`@Id`。JSONB 字段用 `@Column(typeHandler = JsonTypeHandler.class)` 处理。领域对象 ↔ DO 转换通过 MapStruct Converter。

### 7.4 分页查询实现

```java
@Repository
public class SubTaskDataRepositoryImpl implements SubTaskDataRepository {
    
    @Override
    public PagedRows findPagedRows(SubTaskId id, Pagination pagination) {
        SubTaskDO subTask = subTaskMapper.findOneById(id.value());
        String tableCode = extractFirstTableCode(subTask);
        
        QueryWrapper query = QueryWrapper.create()
            .where(SUB_TASK_ID.eq(id.value()))
            .and(TABLE_CODE.eq(tableCode))
            .and(ROW_INDEX.ge(pagination.startPos()))
            .and(ROW_INDEX.lt(pagination.startPos() + pagination.pageSize()))
            .orderBy(ROW_INDEX.asc());
        
        List<SubTaskRowDO> rowDOs = subTaskRowMapper.selectListByQuery(query);
        
        List<Map<String, Object>> rows = rowDOs.stream()
            .map(SubTaskRowDO::getRowData)
            .toList();
        
        PageInfo pageInfo = PageInfo.of(subTask.getRowCount(), pagination, rows.size());
        return new PagedRows(rows, pageInfo);
    }
}
```

### 7.5 配置加载与缓存

```java
@Component
public class TemplateConfigRepositoryImpl implements TemplateConfigRepository {
    
    private static final String CACHE_KEY_PREFIX = "file:config:";
    
    @Override
    public Optional<TemplateConfig> findActive(BizType bizType) {
        String key = CACHE_KEY_PREFIX + "active:" + bizType.value();
        return cache.get(key, TemplateConfig.class)
            .or(() -> {
                TemplateConfigDO doObj = mapper.selectOneByQuery(
                    QueryWrapper.create()
                        .where(BIZ_TYPE.eq(bizType.value()))
                        .and(STATUS.eq("ACTIVE"))
                );
                Optional<TemplateConfig> result = Optional.ofNullable(doObj)
                    .map(converter::toDomain);
                result.ifPresent(c -> cache.put(key, c, Duration.ofHours(1)));
                return result;
            });
    }
}
```

### 7.6 YAML 配置加载

支持两种 YAML 加载方式：

#### 7.6.1 启动时从 classpath 加载（Bootstrap）

```java
@Component
public class ClasspathConfigBootstrap {
    
    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        List<Resource> yamlFiles = scanClasspath(bootstrapDir);
        Map<String, List<Resource>> grouped = groupByBizType(yamlFiles);
        
        for (var entry : grouped.entrySet()) {
            BizType bizType = new BizType(entry.getKey());
            if (repo.findActive(bizType).isEmpty()) {
                TemplateConfig config = yamlLoader.loadFromYaml(...);
                config.activate();
                repo.save(config);
            }
        }
    }
}
```

#### 7.6.2 通过 API 导入 YAML

```java
@Component
public class YamlConfigLoader {
    
    private final Yaml yaml = new Yaml();
    
    public TemplateConfig loadFromYaml(BizType bizType, String baselineYaml, 
                                       List<String> sourceTemplateYamls) {
        Map<String, Object> baseline = yaml.load(baselineYaml);
        List<SourceTemplateDef> sources = sourceTemplateYamls.stream()
            .map(y -> yaml.load(y))
            .map(this::toSourceTemplateDef)
            .toList();
        return toTemplateConfig(baseline, sources, bizType);
    }
}
```

**YAML 与 DB 关系**：YAML 是配置的"初始来源"，DB 是"权威来源"。YAML 加载后立即写入 DB，之后所有读取都从 DB（带缓存）。修改 YAML 不会自动覆盖 DB，除非显式调用导入 API。

### 7.7 TTL 与自动清理

默认 TTL：30 天（可配置 `file-service.sub-task.ttl-days=30`）。过期后 `SubTaskStatus` 改为 `EXPIRED`，业务服务拉取时返回 410 Gone。

定时清理任务（Phase 1 简化版，只标记过期；物理删除 Phase 2 实现）：
```java
@Scheduled(cron = "0 0 3 * * *")
public void cleanupExpired() {
    subTaskRepo.markExpiredBefore(LocalDateTime.now());
}
```

### 7.8 文件存储设计

Phase 1 只实现本地存储 + 短期保留：

```java
@Component
public class LocalFileStorage {
    
    public String store(InputStream stream, String originalName) { ... }
    public InputStream load(String ref) { ... }
    
    @Scheduled(cron = "0 0 4 * * *")
    public void cleanupOldFiles() { ... }
}
```

Phase 2 视需要扩展对象存储。

### 7.9 测试数据库配置

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:file_service;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
```

H2 PostgreSQL 兼容模式。JSONB 用 TEXT 类型替代。部分唯一索引在测试 schema 中改用应用层校验。

---

## 8. REST API 设计与领域事件

### 8.1 分层职责

严格遵循项目规范，API 协议定义与实现分离：

```
file-api（协议层）                    file-adapter（实现层）
┌──────────────────────┐              ┌──────────────────────────┐
│ ParseApi (interface) │ ◀──implements─ │ ParseController          │
│ @HttpExchange        │              │ @RestController          │
│ + ParseRequest       │              │ + ParseControllerConverter│
│ + ParseResponse      │              │   (MapStruct)            │
└──────────────────────┘              └──────────────────────────┘
```

### 8.2 API 接口定义（file-api）

#### 8.2.1 ParseApi

```java
@HttpExchange("/api/v1/parse")
public interface ParseApi {
    
    @PostExchange(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResult<ParseResponse> parse(@Valid @RequestBody ParseRequest request);
    
    @GetExchange("/api/v1/parse-tasks/{fileTaskId}")
    ApiResult<ParseTaskSummaryResponse> getTaskSummary(
            @PathVariable("fileTaskId") String fileTaskId);
}
```

#### 8.2.2 ParsedDataApi

```java
@HttpExchange("/api/v1/parsed-data")
public interface ParsedDataApi {
    
    @GetExchange("/{subTaskId}")
    ApiResult<PagedDataResponse> queryPaged(
            @PathVariable("subTaskId") String subTaskId,
            @RequestParam(value = "tableCode", defaultValue = "detailList") String tableCode,
            @RequestParam(value = "startPos", defaultValue = "0") int startPos,
            @RequestParam(value = "pageSize", defaultValue = "1000") int pageSize);
}
```

#### 8.2.3 ConfigApi

```java
@HttpExchange("/api/v1/configs")
public interface ConfigApi {
    
    @PostExchange(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResult<ImportConfigResponse> importFromYaml(@Valid @RequestBody ImportConfigRequest request);
    
    @GetExchange("/{bizType}")
    ApiResult<TemplateConfigResponse> getActiveConfig(@PathVariable("bizType") String bizType);
}
```

### 8.3 请求/响应 DTO 定义（file-api）

```java
// 解析相关
public record ParseRequest(
    @NotBlank String bizType,
    @NotNull MultipartFile file,
    String templateCode,
    @NotBlank String operator
) {}

public record ParseResponse(
    String fileTaskId, String bizType, String status,
    int totalRows,
    List<SubTaskResponse> subTasks,
    List<TaskErrorResponse> errors
) {}

public record SubTaskResponse(
    String subTaskId, String splitKeyValue,
    int totalRows, int validRows, int invalidRows,
    String status
) {}

public record ParseTaskSummaryResponse(
    String fileTaskId, String bizType, String status, String templateCode,
    int totalRows, int subTaskCount, int validCount, int invalidCount,
    List<SubTaskResponse> subTasks, List<TaskErrorResponse> errors,
    LocalDateTime startedAt, LocalDateTime finishedAt, long durationMs
) {}

// 数据拉取
public record PagedDataResponse(
    String subTaskId, String fileTaskId, String bizType,
    String splitKeyValue, String status,
    Map<String, Object> context, Map<String, Object> properties,
    PageInfoResponse pageInfo,
    List<Map<String, Object>> rows,
    String nextPageUrl,
    List<RowErrorResponse> validationErrors
) {}

public record PageInfoResponse(
    String tableCode, int totalCount, int startPos, int returnedCount, boolean hasMore
) {}

// 配置管理
public record ImportConfigRequest(
    @NotBlank String bizType,
    @NotNull MultipartFile baselineFile,
    @NotNull List<MultipartFile> sourceTemplateFiles,
    @NotBlank String version,
    @NotBlank String operator
) {}

public record ImportConfigResponse(
    String configId, String bizType, String version, String status,
    int sourceTemplateCount, List<String> sourceTemplateCodes
) {}
```

### 8.4 Controller 实现（file-adapter）

```java
@RestController
@AllArgsConstructor
public class ParseController implements ParseApi {
    
    private final ParseExcelAppService parseService;
    private final QueryParseTaskAppService queryService;
    private final ParseControllerConverter converter;
    
    @Override
    public ApiResult<ParseResponse> parse(@Valid @RequestBody ParseRequest request) {
        ParseExcelCommand cmd = converter.toCommand(request);
        ParseExcelResult result = parseService.parseAndStore(cmd);
        return ApiResult.success(converter.toResponse(result));
    }
    
    @Override
    public ApiResult<ParseTaskSummaryResponse> getTaskSummary(String fileTaskId) {
        TaskSummaryResult result = queryService.querySummary(new TaskSummaryQuery(new FileTaskId(fileTaskId)));
        return ApiResult.success(converter.toSummaryResponse(result));
    }
}
```

Converter 用 MapStruct 完成 DTO ↔ Command/Result 转换。严格遵守：
- Adapter 层只做"DTO ↔ Command/Result 转换 + 调用应用服务"
- 所有转换通过 MapStruct Converter
- Adapter 不直接操作 Entity/DO
- Adapter 不编写业务逻辑

### 8.5 Command/Query/Result（file-application）

```java
public record ParseExcelCommand(
    BizType bizType, String fileName,
    InputStream excelStream, UserNo operator,
    TemplateCode templateCode
) {}

public record ParseExcelResult(
    FileTaskId fileTaskId, BizType bizType, TaskStatus status,
    int totalRows, List<SubTaskSummary> subTasks, List<TaskError> errors
) {}

public record PagedDataQuery(SubTaskId subTaskId, FetchPagination pagination) {}

public record PagedDataResult(
    SubTaskId subTaskId, FileTaskId fileTaskId, BizType bizType,
    String splitKeyValue, SubTaskStatus status,
    BusinessContext context, Map<String, Object> properties,
    PageInfo pageInfo, List<Map<String, Object>> rows,
    String nextPageUrl, List<RowError> validationErrors
) {}

public record FetchPagination(String tableCode, int startPos, int pageSize) {
    public static FetchPagination of(String tableCode, int startPos, int pageSize) {
        if (pageSize > 2000) pageSize = 2000;
        if (pageSize < 1) pageSize = 1000;
        return new FetchPagination(tableCode, Math.max(0, startPos), pageSize);
    }
}

public record ImportYamlCommand(
    BizType bizType, String baselineYaml,
    List<String> sourceTemplateYamls, String version, UserNo operator
) {}
```

### 8.6 领域事件设计（双轨制）

#### 8.6.1 问题诊断

现有 `shared-event-starter` 存在以下架构问题：

1. **领域事件直接发送到 MQ**（核心问题）
   - `DomainEvent` 实现类在 `xxx-domain` 模块，跨服务无法引用
   - 消费方反序列化时需要领域层依赖，严重违反 DDD

2. **`JdbcEventStore.findPendingLogs` 反序列化 Bug**
   - `Class.forName(eventType)` 用简单类名，会抛 `ClassNotFoundException`
   - 补偿任务恢复时必然失败

3. **领域事件直接序列化落库**
   - 落库的是领域事件 JSON，反序列化需要领域类可访问

#### 8.6.2 修正方案：双轨制

```
                    file-domain                           file-api
┌──────────────────────────────────┐      ┌──────────────────────────────────┐
│ FileParsedEvent (领域事件)        │      │ FileParsedMessage (集成事件 DTO)  │
│ - 实现 DomainEvent                │      │ - 纯 POJO/record                 │
│ - 携带领域语义                    │      │ - 跨服务共享契约                  │
│ - 仅本服务可见                    │      │ - 消费方依赖 file-api 即可        │
└──────────────────────────────────┘      └──────────────────────────────────┘
                  │                                       ▲
                  │ 转换（MapStruct）                       │
                  └───────────────────────────────────────┘
```

**核心原则**：
1. 领域事件留在 domain 层：`FileParsedEvent` 定义在 `file-domain`
2. 集成事件定义在 api 层：`FileParsedMessage` 定义在 `file-api`
3. 本服务落库领域事件：保留完整领域语义
4. 发往 MQ 的是集成事件：消费方只需依赖 `file-api`
5. 转换在 infrastructure 层完成：通过 MapStruct Converter

#### 8.6.3 领域事件（file-domain）

```java
public record FileParsedEvent(
    EventId eventId,
    LocalDateTime occurredOn,
    FileTaskId fileTaskId,
    BizType bizType,
    TaskStatus status,
    int totalSubTasks,
    List<SubTaskSummary> subTasks,
    String failureReason
) implements DomainEvent {
    public static FileParsedEvent of(ParseTask task) { ... }
}
```

#### 8.6.4 集成事件 DTO（file-api）

```java
public record FileParsedMessage(
    String eventId,
    LocalDateTime occurredOn,
    String eventType,                     // 固定为 "FileParsed"
    String fileTaskId,
    String bizType,
    String status,
    int totalSubTasks,
    List<SubTaskPayload> subTasks,
    String failureReason
) {
    public record SubTaskPayload(
        String subTaskId, String splitKeyValue,
        int totalRows, int validRows, int invalidRows,
        String status
    ) {}
    
    public static final String EVENT_TYPE = "FileParsed";
}
```

集成事件是纯 POJO，所有字段都是基础类型，不引用任何领域对象。

#### 8.6.5 转换器（file-infrastructure）

```java
@Mapper(componentModel = "spring")
public interface FileParsedEventConverter 
    extends IntegrationEventConverter<FileParsedEvent> {
    
    @Override
    default Class<FileParsedEvent> supportedEventType() {
        return FileParsedEvent.class;
    }
    
    @Override
    @Mapping(target = "eventId", source = "eventId.value")
    @Mapping(target = "eventType", constant = "FileParsed")
    @Mapping(target = "fileTaskId", source = "fileTaskId.value")
    @Mapping(target = "bizType", source = "bizType.value")
    @Mapping(target = "status", source = "status.name")
    FileParsedMessage toIntegrationEvent(FileParsedEvent event);
}
```

### 8.7 `shared-event-starter` 重构

#### 8.7.1 新增 SPI：`IntegrationEventConverter`

```java
// shared-domain/src/main/java/com/example/shared/domain/event/IntegrationEventConverter.java
public interface IntegrationEventConverter<D extends DomainEvent> {
    Class<D> supportedEventType();
    Object toIntegrationEvent(D domainEvent);
    
    default String integrationEventType() {
        return supportedEventType().getSimpleName();
    }
}
```

SPI 定义在 `shared-domain`（不依赖 Spring），各业务模块在 infrastructure 层实现。

#### 8.7.2 `EventStore` 存储结构改造

```sql
CREATE TABLE sys_event_store (
    event_id            VARCHAR(64)   NOT NULL,
    event_type          VARCHAR(128)  NOT NULL,     -- 领域事件全限定类名
    integration_type    VARCHAR(64),                 -- 集成事件类型标识（如 "FileParsed"）
    domain_payload      TEXT          NOT NULL,      -- 领域事件 JSON（本服务恢复用）
    integration_payload TEXT,                        -- 集成事件 JSON（发往 MQ 用）
    occurred_on         TIMESTAMP     NOT NULL,
    PRIMARY KEY (event_id)
);
```

`integration_payload` 是已经转换好的、可直接发送的 JSON。补偿任务恢复时无需再转换。

#### 8.7.3 `EventBus` 改造

```java
public class EventBus implements com.example.shared.domain.event.EventBus {
    
    private final List<EventDispatcher> dispatchers;
    private final EventStore eventStore;
    private final EventDeliverer eventDeliverer;
    private final List<IntegrationEventConverter<?>> converters;
    
    @Override
    public void publish(DomainEvent event) {
        // 1. 查找转换器
        IntegrationEventConverter converter = findConverter(event);
        Object integrationEvent = converter != null 
            ? converter.toIntegrationEvent(event) 
            : event;  // 无转换器则降级为直接发送领域事件（向后兼容）
        
        String integrationType = converter != null 
            ? converter.integrationEventType() 
            : event.eventType();
        
        // 2. 落库（领域事件 + 集成事件双份）
        eventStore.save(event, integrationEvent, integrationType);
        
        // 3. 遍历分发
        for (EventDispatcher dispatcher : dispatchers) {
            if (dispatcher.isRemote()) {
                long logId = eventStore.initDispatchLog(event.eventId().toString(), dispatcher.getChannelName());
                
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            VirtualThreadExecutor.executeAsync(() ->
                                eventDeliverer.deliver(dispatcher, event, integrationEvent, logId)
                            );
                        }
                    });
                } else {
                    VirtualThreadExecutor.executeAsync(() ->
                        eventDeliverer.deliver(dispatcher, event, integrationEvent, logId)
                    );
                }
            } else {
                safeLocalDispatch(dispatcher, event);
            }
        }
    }
}
```

#### 8.7.4 `EventDispatcher` 接口改造

```java
public interface EventDispatcher {
    void dispatch(DomainEvent domainEvent, Object integrationEvent);
    String getChannelName();
    default boolean isRemote() { return true; }
}
```

```java
// RocketMQEventDispatcher 修正版
public class RocketMQEventDispatcher implements EventDispatcher {
    
    @Override
    public void dispatch(DomainEvent domainEvent, Object integrationEvent) {
        String integrationType = integrationEvent.getClass().getSimpleName();
        String topic = "event_%s".formatted(integrationType);
        String destination = topic + ":" + integrationType;
        String key = domainEvent.eventId().toString();
        
        rocketMQTemplate.asyncSend(destination, integrationEvent, new SendCallback() { ... });
    }
}
```

#### 8.7.5 `JdbcEventStore.findPendingLogs` 修正

```java
@Override
public List<PendingEntry> findPendingLogs(int batchSize) {
    String sql = """
        SELECT l.id, l.channel, l.retry_count, s.integration_payload, s.integration_type
        FROM sys_event_dispatch_log l
        JOIN sys_event_store s ON l.event_id = s.event_id
        WHERE l.status IN ('PENDING', 'FAILED')
          AND (l.next_retry_at IS NULL OR l.next_retry_at <= NOW())
          AND l.retry_count < 10
        LIMIT ?
        """;
    
    return jdbcClient.sql(sql).param(batchSize)
        .query((rs, _) -> {
            long logId = rs.getLong("id");
            String channel = rs.getString("channel");
            String payload = rs.getString("integration_payload");
            String integrationType = rs.getString("integration_type");
            // 直接用 Map 反序列化，不依赖具体类
            Map<String, Object> integrationEvent = objectMapper.readValue(payload, Map.class);
            return new PendingEntry(logId, integrationEvent, channel, integrationType);
        })
        .list().stream().filter(Objects::nonNull).toList();
}
```

**关键改进**：补偿任务恢复时直接读 `integration_payload`（已经是可序列化的 JSON），用 `Map` 反序列化后直接发送，彻底避免 `Class.forName` 和领域类依赖问题。

#### 8.7.6 向后兼容

- 未实现 `IntegrationEventConverter` 的旧领域事件，降级为直接发送领域事件（保留现状）
- `SpringEventDispatcher`（本地分发）继续发送领域事件，保留领域语义

### 8.8 事件投递保证

- **至少一次（At-Least-Once）**：RocketMQ 事务消息 + 本地事件表兜底
- **消费者幂等**：业务服务根据 `fileTaskId` 做幂等判断
- **顺序性**：按 `fileTaskId` 做分区键

### 8.9 事件消费示例（业务服务侧）

业务服务通过 Retrofit 调用 file-api 接口拉取数据：

```java
// file-api 已经定义了 ParseApi、ParsedDataApi 接口
@Bean
public ParsedDataApi parsedDataApi(@Value("${file-service.base-url}") String baseUrl) {
    return new Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(JacksonConverterFactory.create())
        .build()
        .create(ParsedDataApi.class);
}

public void onFileParsed(FileParsedMessage event) {
    for (var sub : event.subTasks()) {
        if ("VALID".equals(sub.status())) {
            fetchAndProcess(sub.subTaskId());
        }
    }
}

private void fetchAndProcess(String subTaskId) throws IOException {
    int startPos = 0;
    while (true) {
        ApiResponse<PagedDataResponse> resp = parsedDataApi.queryPaged(
            subTaskId, "detailList", startPos, 1000
        ).execute().body();
        PagedDataResponse page = resp.getData();
        processBatch(page.getRows());
        if (!page.getPageInfo().isHasMore()) break;
        startPos = page.getPageInfo().getStartPos() + page.getPageInfo().getReturnedCount();
    }
}
```

### 8.10 完整事件流

```
file-service (生产方)                              business-service (消费方)
┌──────────────────────────────────────────┐      ┌──────────────────────────────────┐
│ ParseExcelAppService                      │      │ FileParsedMessageConsumer         │
│   └─ eventBus.publish(FileParsedEvent)    │      │   (依赖 file-api)                 │
│                                           │      │                                   │
│ EventBus                                  │      │   onMessage(FileParsedMessage) {  │
│   ├─ findConverter(FileParsedEvent)       │      │     fetchAndProcess(...)          │
│   ├─ converter.toMessage(domainEvent)     │      │   }                               │
│   │   → FileParsedMessage                 │      │                                   │
│   ├─ eventStore.save(domainEvent,         │      │   ┌─────────────────────────┐    │
│   │                  integrationEvent)    │      │   │ ParsedDataApi (Retrofit)│    │
│   └─ dispatcher.dispatch(domainEvent,     │      │   │   .queryPaged(subTaskId)│    │
│                            integrationEvt)│      │   └─────────────────────────┘    │
│                                           │      │                                   │
│ RocketMQEventDispatcher                   │      └──────────────────────────────────┘
│   └─ asyncSend("event_FileParsed",        │
│                  FileParsedMessage)       │
└───────────────┬───────────────────────────┘
                │
                ▼
        ┌───────────────┐
        │   RocketMQ    │
        └───────┬───────┘
                │ FileParsedMessage (JSON)
                ▼
        (消费方依赖 file-api 即可反序列化)
```

---

## 9. 目录结构与文件清单

### 9.1 `shared-event-starter` 重构文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `shared-domain/.../event/IntegrationEventConverter.java` | 新增 | SPI 接口 |
| `shared-event-starter/.../bus/EventBus.java` | 修改 | 注入转换器列表，发布时转换 |
| `shared-event-starter/.../deliverer/EventDeliverer.java` | 修改 | 传递集成事件 |
| `shared-event-starter/.../dispatcher/EventDispatcher.java` | 修改 | 接口签名加 `integrationEvent` 参数 |
| `shared-event-starter/.../dispatcher/RocketMQEventDispatcher.java` | 修改 | 发送集成事件 |
| `shared-event-starter/.../dispatcher/RedisEventDispatcher.java` | 修改 | 发送集成事件 |
| `shared-event-starter/.../dispatcher/SpringEventDispatcher.java` | 修改 | 保持发送领域事件（本地） |
| `shared-event-starter/.../store/JdbcEventStore.java` | 修改 | 双 payload 落库，补偿任务用 `integration_payload` |
| `shared-event-starter/.../job/EventRecoveryJob.java` | 修改 | 适配新签名 |
| `shared-event-starter/.../autoconfiguration/EventAutoConfiguration.java` | 修改 | 注入转换器列表 |
| `shared-event-starter/src/main/resources/pg.sql` | 修改 | 加 `integration_type`、`integration_payload` 字段 |
| `shared-event-starter/src/main/resources/mysql.sql` | 修改 | 同上 |

### 9.2 `file-service` 模块结构

```
file-service/
├── pom.xml
├── file-types/
│   └── src/main/java/com/example/file/types/
│       ├── FileTaskId.java
│       ├── SubTaskId.java
│       ├── TemplateConfigId.java
│       ├── BizType.java
│       └── TemplateCode.java
│
├── file-domain/
│   └── src/main/java/com/example/file/domain/
│       ├── model/
│       │   ├── aggregate/
│       │   │   ├── root/
│       │   │   │   ├── ParseTask.java
│       │   │   │   ├── SubTaskData.java
│       │   │   │   └── TemplateConfig.java
│       │   │   └── entity/
│       │   │       └── SourceTemplateDef.java
│       │   └── valueobject/
│       │       ├── SubTaskSummary.java
│       │       ├── TaskError.java
│       │       ├── RowError.java
│       │       ├── BusinessContext.java
│       │       ├── CanonicalData.java
│       │       ├── FieldLocation.java
│       │       ├── ValidationResult.java
│       │       ├── FetchPagination.java
│       │       ├── PageInfo.java
│       │       ├── PagedRows.java
│       │       ├── config/
│       │       │   ├── CanonicalModelDef.java
│       │       │   ├── PropertyFieldDef.java
│       │       │   ├── TableDef.java
│       │       │   ├── FieldDef.java
│       │       │   ├── ValidationRule.java
│       │       │   ├── DerivationRule.java
│       │       │   ├── SplitConfig.java
│       │       │   ├── RegionDef.java
│       │       │   ├── RegionTrigger.java
│       │       │   ├── KvStrategy.java
│       │       │   ├── TableStrategy.java
│       │       │   ├── DataEndRule.java
│       │       │   └── TargetMapping.java
│       │       └── parse/
│       │           ├── RawRow.java
│       │           ├── RawRowStream.java
│       │           ├── RegionParseResult.java
│       │           ├── KvRegionResult.java
│       │           ├── TableRegionResult.java
│       │           └── RegionSkip.java
│       ├── event/
│       │   └── FileParsedEvent.java
│       ├── repository/
│       │   ├── ParseTaskRepository.java
│       │   ├── SubTaskDataRepository.java
│       │   └── TemplateConfigRepository.java
│       ├── service/
│       │   ├── RegionStateMachine.java
│       │   ├── KeyValueRegionParser.java
│       │   ├── TableRegionParser.java
│       │   ├── CanonicalModelBuilder.java
│       │   ├── DataDeriver.java
│       │   ├── DataValidator.java
│       │   ├── TaskSplitter.java
│       │   └── SourceTemplateIdentifier.java
│       ├── gateway/
│       │   ├── ExcelParser.java
│       │   ├── ExpressionEvaluator.java
│       │   └── ConfigLoader.java
│       └── errorcode/
│           └── FileErrorCodes.java
│
├── file-api/
│   └── src/main/java/com/example/file/api/
│       ├── ParseApi.java
│       ├── ParsedDataApi.java
│       ├── ConfigApi.java
│       ├── dto/
│       │   ├── ParseRequest.java
│       │   ├── ParseResponse.java
│       │   ├── SubTaskResponse.java
│       │   ├── TaskErrorResponse.java
│       │   ├── ParseTaskSummaryResponse.java
│       │   ├── PagedDataResponse.java
│       │   ├── PageInfoResponse.java
│       │   ├── RowErrorResponse.java
│       │   ├── ImportConfigRequest.java
│       │   ├── ImportConfigResponse.java
│       │   └── TemplateConfigResponse.java
│       └── event/
│           └── FileParsedMessage.java
│
├── file-application/
│   └── src/main/java/com/example/file/application/
│       ├── service/
│       │   ├── ParseExcelAppService.java
│       │   ├── QueryParsedDataAppService.java
│       │   ├── QueryParseTaskAppService.java
│       │   └── ManageConfigAppService.java
│       └── dto/
│           ├── command/
│           │   ├── ParseExcelCommand.java
│           │   └── ImportYamlCommand.java
│           ├── query/
│           │   ├── PagedDataQuery.java
│           │   ├── TaskSummaryQuery.java
│           │   └── ActiveConfigQuery.java
│           └── result/
│               ├── ParseExcelResult.java
│               ├── PagedDataResult.java
│               ├── TaskSummaryResult.java
│               └── ImportConfigResult.java
│
├── file-adapter/
│   └── src/main/java/com/example/file/adapter/
│       ├── controller/
│       │   ├── ParseController.java
│       │   ├── ParsedDataController.java
│       │   └── ConfigController.java
│       └── converter/
│           ├── ParseControllerConverter.java
│           ├── ParsedDataControllerConverter.java
│           └── ConfigControllerConverter.java
│
├── file-infrastructure/
│   └── src/main/java/com/example/file/infra/
│       ├── excel/
│       │   ├── FesodExcelReader.java
│       │   ├── ExcelParserImpl.java
│       │   └── QueuedRawRowStream.java
│       ├── expression/
│       │   └── AviatorExpressionEvaluator.java
│       ├── config/
│       │   ├── YamlConfigLoader.java
│       │   └── ClasspathConfigBootstrap.java
│       ├── event/
│       │   └── FileParsedEventConverter.java
│       ├── persistence/
│       │   ├── mapper/
│       │   │   ├── ParseTaskMapper.java
│       │   │   ├── SubTaskDataMapper.java
│       │   │   ├── SubTaskRowMapper.java
│       │   │   └── TemplateConfigMapper.java
│       │   ├── entity/
│       │   │   ├── ParseTaskDO.java
│       │   │   ├── SubTaskDataDO.java
│       │   │   ├── SubTaskRowDO.java
│       │   │   └── TemplateConfigDO.java
│       │   ├── repository/
│       │   │   ├── ParseTaskRepositoryImpl.java
│       │   │   ├── SubTaskDataRepositoryImpl.java
│       │   │   └── TemplateConfigRepositoryImpl.java
│       │   ├── converter/
│       │   │   ├── ParseTaskDOConverter.java
│       │   │   ├── SubTaskDataDOConverter.java
│       │   │   └── TemplateConfigDOConverter.java
│       │   └── typehandler/
│       │       └── JsonTypeHandler.java
│       ├── storage/
│       │   └── LocalFileStorage.java
│       └── job/
│           └── SubTaskCleanupJob.java
│
└── file-starter/
    └── src/main/
        ├── java/com/example/file/starter/
        │   └── FileServiceApplication.java
        └── resources/
            ├── application.yml
            ├── application-dev.yml
            ├── application-test.yml
            ├── schema-pg.sql
            └── file-templates/
                ├── import_declare_base.yaml
                └── import_declare_CUST_A_V2.yaml
```

### 9.3 测试目录结构

```
file-service/
├── file-domain/src/test/java/com/example/file/domain/
│   ├── service/
│   │   ├── RegionStateMachineTest.java
│   │   ├── KeyValueRegionParserTest.java
│   │   ├── TableRegionParserTest.java
│   │   ├── CanonicalModelBuilderTest.java
│   │   ├── DataDeriverTest.java
│   │   ├── DataValidatorTest.java
│   │   └── TaskSplitterTest.java
│   ├── model/
│   │   ├── ParseTaskTest.java
│   │   ├── SubTaskDataTest.java
│   │   └── TemplateConfigTest.java
│   └── event/
│       └── FileParsedEventTest.java
│
├── file-application/src/test/java/com/example/file/application/
│   └── service/
│       ├── ParseExcelAppServiceTest.java
│       ├── QueryParsedDataAppServiceTest.java
│       └── ManageConfigAppServiceTest.java
│
└── file-starter/src/test/java/com/example/file/
    └── FileServiceIntegrationTest.java
```

### 9.4 依赖关系

```
file-types:
  └── shared-types

file-domain:
  ├── shared-domain
  ├── file-types
  └── (仅 lombok，无其他外部依赖)

file-api:
  ├── shared-api
  ├── file-types
  └── (spring-web for @HttpExchange)

file-application:
  ├── file-api
  ├── file-domain
  ├── shared-event-starter
  └── shared-logging-starter

file-adapter:
  ├── file-api
  ├── file-application
  ├── shared-web-starter
  └── (mapstruct)

file-infrastructure:
  ├── file-domain
  ├── file-application
  ├── shared-id-starter
  ├── shared-cache-starter
  ├── mybatis-flex-spring-boot3-starter
  ├── postgresql-driver
  ├── fesod-sheet
  ├── aviator
  └── snakeyaml

file-starter:
  ├── file-adapter
  ├── file-infrastructure
  └── spring-boot-starter
```

### 9.5 配置文件（application.yml）

```yaml
spring:
  application:
    name: file-service
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/file_service
    username: ${DB_USER:postgres}
    password: ${DB_PASS:postgres}
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB

mybatis-flex:
  global-config:
    logic-delete-column: deleted

file-service:
  config:
    bootstrap-dir: file-templates/
  sub-task:
    ttl-days: 30
  storage:
    local-dir: ${java.io.tmpdir}/file-service/files
    retain-hours: 24
  parse:
    max-rows: 100000
    timeout-seconds: 300

shared:
  event:
    rocketmq:
      enabled: true
      name-server: ${ROCKETMQ_NS:localhost:9876}
    redis:
      enabled: false

server:
  port: 8084
```

### 9.6 文件数量统计

| 模块 | 文件数 |
|------|-------|
| shared-event-starter（重构） | 12 |
| file-types | 5 |
| file-domain | 约 45 |
| file-api | 14 |
| file-application | 11 |
| file-adapter | 6 |
| file-infrastructure | 约 25 |
| file-starter | 5 |
| 测试文件 | 约 15 |
| **总计** | **约 138** |

---

## 10. 测试策略

### 10.1 测试金字塔

| 层级 | 测试类型 | 范围 | 数量 |
|------|---------|------|------|
| file-domain | 单元测试 | 领域服务、聚合根、值对象 | 约 12 |
| file-application | 集成测试 | 应用服务（mock Repository） | 约 3 |
| file-starter | 端到端测试 | 完整流程（H2） | 约 1 |
| shared-event-starter | 单元测试 | 重构后的 EventBus | 约 3 |

### 10.2 关键测试用例

**解析引擎**：
- `RegionStateMachineTest`：区域切换、trigger 命中、提前退出
- `KeyValueRegionParserTest`：RIGHT/BELOW 布局、连续空行退出
- `TableRegionParserTest`：表头解析、dataEnd 触发、列别名匹配

**校验引擎**：
- `DataValidatorTest`：必填、类型、表达式校验、errorPolicy 各分支
- `DataDeriverTest`：properties 派生、table 行派生、拓扑顺序

**拆分引擎**：
- `TaskSplitterTest`：按 table 字段拆分、onMiss 策略、promoteToContext

**端到端**：
- `FileServiceIntegrationTest`：上传 Excel → 解析 → 校验 → 拆分 → 持久化 → 事件发布 → 分页拉取

### 10.3 测试数据

测试 Excel 文件放在 `file-starter/src/test/resources/test-excel/`：
- `cust_a_v2_sample.xlsx` —— CUST_A_V2 格式样本
- `large_file_10000_rows.xlsx` —— 大文件性能测试
- `invalid_data.xlsx` —— 校验失败样本

---

## 11. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| `shared-event-starter` 重构影响其他服务 | 中 | 向后兼容设计：无转换器时降级为发送领域事件 |
| Fesod 推模式与状态机拉模式的桥接复杂度 | 中 | 阻塞队列 + 虚拟线程，单测覆盖 |
| Aviator 表达式安全风险 | 高 | 限制 Feature Set，禁止反射调用 |
| 大文件内存溢出 | 高 | Fesod 流式读取 + 分页持久化 + 阻塞队列背压 |
| PostgreSQL JSONB 查询性能 | 中 | 关键查询字段单独建索引，JSONB 仅用于存储 |
| 配置错误导致解析失败 | 中 | 配置加载时 validateInvariants，启动时校验 |

---

## 12. Phase 2 预告

Phase 1 完成后，Phase 2 将实现：
- 目标 Excel 生成 API（Fesod 模板填充 + target mapping 渲染）
- 配置管理 REST API（增删改查）
- TTL 物理清理任务
- 鉴权与限流
- 对象存储扩展

---

## 附录 A：领域事件双轨制决策记录

### 问题
原设计将 `FileParsedEvent` 放在 `file-api` 模块，违反 DDD 原则：领域事件应属于领域层。

### 决策
采用双轨制：
- 领域事件 `FileParsedEvent` 留在 `file-domain`，保留完整领域语义
- 集成事件 `FileParsedMessage` 定义在 `file-api`，纯 POJO，跨服务共享
- 通过 `IntegrationEventConverter` SPI 在 infrastructure 层转换

### 影响
- `shared-event-starter` 需要重构（12 个文件）
- 解决了原 `JdbcEventStore.findPendingLogs` 的 `Class.forName` 反序列化 Bug
- 业务服务消费方只需依赖 `file-api` 模块

---

## 附录 B：API 层接口定义模式决策记录

### 问题
原设计直接在 Adapter 层定义 Controller，违反项目规范。

### 决策
严格遵循 API 层约束：
- API 接口定义在 `file-api`，用 `@HttpExchange` 标记
- Controller 在 `file-adapter` 实现 API 接口，标注 `@RestController`
- Converter 用 MapStruct 完成 DTO ↔ Command/Result 转换

### 影响
- 业务服务可通过 Retrofit 代理 API 接口进行调用，无需手写 HTTP 客户端
- API 协议与实现解耦，便于多端复用

---

**文档结束**
