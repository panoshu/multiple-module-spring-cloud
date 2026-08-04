# multiple-module-spring-cloud 项目完整描述报告

> 分析时间：2026-07-19
> 仓库根：`d:\WorkSpace\Trae\multiple-module-spring-cloud`

---

## 一、项目概览

### 1.1 项目定位

`multiple-module-spring-cloud` 是一个基于 **DDD + 六边形架构** 的 Spring Cloud 微服务多模块工程，采用 JDK 25（启用
`--enable-preview`）+ Spring Boot 3.5.14 + Spring Cloud 2025.0.2 + Spring Cloud
Alibaba（Nacos）作为运行基座，目标是搭建可演进的企业级业务中台骨架，覆盖业务流程编排、文件解析、审批流、外部系统集成等典型场景。

### 1.2 顶层模块组成

根 `pom.xml` 声明 **6 个顶级模块**：

| 模块                   | 角色                     | 备注                                                      |
|------------------------|--------------------------|-----------------------------------------------------------|
| `demo-gateway`         | API 网关                 | Spring Cloud Gateway，含 ExcludeRouteFilter 自定义过滤器  |
| `demo-shared`          | 共享基础库               | 12 个子模块（5 基础抽象 + 7 自定义 Starter）              |
| `business-core-kernel` | **业务流程编排引擎内核** | 6 层完整 DDD，提供表单解析/流程编排/材料计算/明细摄入能力 |
| `integration-service`  | 外部系统集成             | 封装外部交易系统，含 5564 交易码查询                      |
| `approval-service`     | 审批服务                 | 7 层完整 DDD，含审批流和审批实例                          |
| `file-service`         | 文件服务                 | 7 层完整 DDD，含 Excel 解析/导出引擎                      |

> 注：规则文档 `01-架构与依赖规则.md` 提到的 `customer-service` 实际不存在；`business-core-api` 模块当前为空（仅有
> pom.xml，但其他 business-core-* 子模块代码齐全）。

### 1.3 关键版本

| 技术                         | 版本                     |
|------------------------------|--------------------------|
| JDK                          | 25（`--enable-preview`） |
| Spring Boot                  | 3.5.14                   |
| Spring Cloud                 | 2025.0.2                 |
| Spring Cloud Alibaba (Nacos) | 2025.0.0.0               |
| MyBatis-Flex                 | 1.11.5                   |
| Redisson                     | 4.3.1                    |
| RocketMQ Spring Boot Starter | 2.3.0                    |
| MapStruct                    | 1.6.3                    |
| Lombok                       | 1.18.46                  |
| Apache Fesod (Excel)         | 2.0.2-incubating         |
| Aviator (表达式引擎)         | 5.4.3                    |
| Fury (序列化)                | 0.10.3                   |
| Retrofit                     | 3.3.0                    |
| Logbook                      | 4.0.4                    |
| OpenHtmlToPdf                | 1.1.36                   |

---

## 二、整体架构

### 2.1 DDD + 六边形分层

每个业务服务严格遵循模块化分层，依赖方向单向收敛：

```
types → domain → api → application → adapter → infrastructure → starter
```

依赖规则（来自 `.trae/rules/01-架构与依赖规则.md`）：

- `types`：领域原语（仅 ID 类型），依赖 `shared-types`
- `domain`：实体、值对象、聚合根、领域服务、SPI 接口，依赖 `shared-domain + xxx-types`
- `api`：DTO、Command、Query、`@HttpExchange` 接口，依赖 `shared-api + xxx-types`
- `application`：应用服务、流程编排，依赖 `xxx-api + xxx-domain + shared-*starter`
- `adapter`：Controller、DTO 转换（MapStruct Converter），依赖 `xxx-api + xxx-application`
- `infrastructure`：Repository 实现、Mapper、DO、数据访问，依赖 `xxx-domain + shared-*starter`
- `starter`：启动类、`application.yml`、打包入口，依赖 `xxx-adapter + xxx-infrastructure`

### 2.2 核心架构原则

1. **依赖倒置**：domain 层不依赖 application/infrastructure，通过 SPI（Gateway 接口、Repository 接口）解耦
2. **领域层纯净**：domain 层禁止使用 Spring 注解、数据库注解、JSON 注解，唯一允许的外部库是 Lombok
3. **强类型 ID**：所有 ID 实现 `Identifier<?>` 接口，通过 `@IdDefinition` 注解声明生成策略（ULID/UUID_V7/SEGMENT）
4. **聚合根管理领域事件**：通过 `registerDomainEvent()` 注册，外部通过 `getDomainEvents()` 读取后发布
5. **跨聚合 ID 引用**：禁止直接对象引用，必须使用 ID 引用

### 2.3 Repository 接口契约

来自 `shared-domain/Repository.java` 的统一契约：

```java
public interface Repository<T extends AggregateRoot<ID>, ID extends Identifier<?>> {
    Optional<T> load(ID id);
    default T loadOrThrow(ID id) { ... }
    void save(T aggregateRoot);
    void delete(T aggregateRoot);
    void deleteById(ID id);
    List<T> loadAll();
    void streamByAppId(ID id, Consumer<AggregateRoot<ID>> processor);
}
```

所有业务服务的 RepositoryImpl 都必须实现这 7 个方法（`loadOrThrow` 是 default）。

---

## 三、demo-shared 共享模块体系

`demo-shared` 提供 **12 个子模块**，是整个项目的共享基础设施层。

### 3.1 基础抽象层（5 个）

| 模块               | 内容                                                                                                                        |
|--------------------|-----------------------------------------------------------------------------------------------------------------------------|
| `shared-types`     | 领域原语基类：`Identifier<T>`、`@IdDefinition`                                                                              |
| `shared-exception` | 异常体系：`BaseException` / `DomainException` / `BusinessException` / `SystemException` + `ErrorDefinition`                 |
| `shared-domain`    | DDD 基类：`Entity<ID>`、`AggregateRoot<ID>`、`ValueObject`、`Repository<T,ID>`、`DomainEvent`、`DefaultEventBus`、`@DomainService` |
| `shared-api`       | API 层公共 DTO：`PageData<T>`、`Result<T>` 等                                                                               |
| `shared-utils`     | 工具类集合                                                                                                                  |

### 3.2 自定义 Starter 层（7 个）

每个 Starter 通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 实现自动装配。

| Starter                  | 功能                                                                             |
|--------------------------|----------------------------------------------------------------------------------|
| `shared-id-starter`      | 强类型 ID 生成（ULID/UUID_V7/SEGMENT），含 `schema-mysql.sql` 和 `schema-pg.sql` |
| `shared-cache-starter`   | 多级缓存（Caffeine L1 + Redisson L2）+ 分布式锁防击穿                            |
| `shared-event-starter`   | 事件总线：领域事件 → 集成事件 DTO 转换 → MQ 发送                                 |
| `shared-web-starter`     | Web 通用配置（异常处理、序列化、拦截器）                                         |
| `shared-client-starter`  | HTTP 客户端（Retrofit + Logbook 日志）                                           |
| `shared-logging-starter` | 日志增强（含 schema-mysql.sql 和 schema-pg.sql）                                 |
| `shared-pdf-starter`     | PDF 生成（OpenHtmlToPdf + HTML 模板：bank-letter / month-report / red-header）   |
| `shared-file-starter`    | 文件存储抽象（依赖声明存在）                                                     |

> 已修复：`shared-logging-starter` 包名原为 `com.example.share.logging`（缺少 `d`），现已统一修正为
> `com.example.shared.logging`。

---

## 四、business-core-kernel 业务流程编排引擎内核

### 4.1 模块定位

`business-core-kernel` **不是空模块**，而是整个项目的 **业务流程编排引擎内核（kernel）**。它定义了一套 **通用的表单上传 →
异步解析 → 申请单裂变 → 步骤编排 → 材料计算 → 明细摄入** 的标准业务流水线，各业务线（年金、保理等）通过实现 SPI 接口扩展自己的明细逻辑，
**无需修改内核代码**。

模块自身遵循 6 层 DDD 结构（缺少 `starter`，因为作为内核被其他业务服务依赖而非独立部署）：

```
business-core-types / -domain / -api / -application
  / -adapter / -infrastructure
```

### 4.2 核心架构：三阶段编排模式

`business-core-kernel` 提供一个标准的"业务申请单生命周期"模板：

#### 阶段 1：表单上传与异步解析

由 [BusinessFormAppService.java](file:///d:/WorkSpace/Trae/multiple-module-spring-cloud/business-core-kernel/business-core-application/src/main/java/com/example/core/application/service/BusinessFormAppService.java)
编排：

1. **confirmUpload (formId, uploadedFile)** — BFF 调用，聚合根 `BusinessForm.markAsUploaded()` 产生 `FormUploadedEvent`
2. **triggerParsing (formId)** — 监听 `FormUploadedEvent`，从配置中心查 `FormParsingConfig`，通过
   `FileIntegrationGateway.triggerAsyncParsing` 触发底层文件服务异步解析，状态变更为 `PARSING`
3. **handleParsingResult (formId, parsedResults)** — 底层回调，状态变更为 `PARSED`，并 **裂变新生**多个
   `BusinessApplication` 申请单聚合根（一个表单可能拆分出多笔业务申请）

#### 阶段 2：申请单流程编排（核心引擎）

由 [FlowOrchestrationService.java](file:///d:/WorkSpace/Trae/multiple-module-spring-cloud/business-core-kernel/business-core-application/src/main/java/com/example/core/application/engine/service/FlowOrchestrationService.java)
的 `advanceStep(appId)` 方法驱动：

```
1. 加载聚合根 + 构建配置查询上下文（两阶段：基础上下文 → 执行提取器获取业务事实 → 完整上下文）
2. 查询下一步路由配置 StepRouteConfig（包含 preValidations / mainProcessor / detailProcessors / sideEffects / nextStep）
3. 执行前置管道（校验类扩展动作，失败即抛 SystemException 中断）
4. 执行核心处理器 StepActionHandler（返回 SUCCESS / SUSPEND_ASYNC_WAIT / FAILED）
5. 若成功：执行明细层处理 + 副作用管道，调用聚合根 transit(nextStep) 流转
6. 短事务落库 + 发布领域事件
```

#### 阶段 3：自动推进与挂起机制

- [ApplicationSpawnedListener](file:///d:/WorkSpace/Trae/multiple-module-spring-cloud/business-core-kernel/business-core-application/src/main/java/com/example/core/application/listener/ApplicationSpawnedListener.java)
  ：监听 `ApplicationSpawnedEvent`，在 `AFTER_COMMIT` 阶段异步将新申请单推入引擎，实现表单事务与申请单事务的完美隔离
- [StepAutoAdvanceListener](file:///d:/WorkSpace/Trae/multiple-module-spring-cloud/business-core-kernel/business-core-application/src/main/java/com/example/core/application/listener/StepAutoAdvanceListener.java)
  ：监听 `StepEnteredEvent`，若是 `SYSTEM_TASK` 自动推进，若是 `USER_TASK` 挂起等待 BFF 手动唤醒

### 4.3 SPI 扩展点（业务线扩展契约）

`business-core-kernel` 通过 3 个 SPI 接口实现"开箱即用 + 业务可扩展"：

#### 4.3.1 StepActionHandler — 步骤主处理器

```java
public interface StepActionHandler {
    String handlerName();  // 对应配置中心 JSON 的 mainProcessor
    StepExecutionStatus execute(BusinessApplication app, BusinessMetaContext context);
}
```

内核已提供 3 个开箱即用实现：

| Handler                          | handlerName                      | 职责                                                                   |
|----------------------------------|----------------------------------|------------------------------------------------------------------------|
| `DefaultFormParsingHandler`      | `defaultFormParsingHandler`      | 触发表单异步解析并返回 `SUSPEND_ASYNC_WAIT` 挂起引擎                   |
| `FormParsingHandler`             | `FormParsingHandler`             | 早期版本的表单解析处理器（已不推荐）                                   |
| `PlanMaterialPreparationHandler` | `planMaterialPreparationHandler` | 计算计划层（PLAN）材料清单，明细层材料由业务线在 postExtensions 中扩展 |

#### 4.3.2 StepExtensionAction — 步骤管道扩展动作

```java
public interface StepExtensionAction {
    String actionName();  // 对应配置中心 JSON 的 preExtensions/postExtensions 中的 beanName
    ExtensionExecutionResult execute(BusinessApplication app, BusinessMetaContext context, Map<String, Object> params);
}
```

支持 4 类扩展：VALIDATION（校验）、ENRICHMENT（数据丰富）、NOTIFICATION（通知）、AUDIT（审计）。

#### 4.3.3 BusinessFactExtractor — 业务事实提取器

```java
public interface BusinessFactExtractor {
    String extractorName();
    Map<String, Object> extractBusinessFacts(BusinessApplication businessApplication);
}
```

各业务线实现自己的提取器，将个性化数据塞入 `BusinessMetaContext.extensionFacts`，供 SpEL 表达式和规则引擎使用。

### 4.4 工业级基类：批量处理三剑客

`business-core-application/extension/` 提供 3 个工业级批量处理基类，是项目的工程化亮点：

#### 4.4.1 AbstractCursorPagingDetailAction — 游标分页（更新类首选）

[AbstractCursorPagingDetailAction.java](file:///d:/WorkSpace/Trae/multiple-module-spring-cloud/business-core-kernel/business-core-application/src/main/java/com/example/core/application/extension/AbstractCursorPagingDetailAction.java)

- **适用场景**：需要 UPDATE 明细数据的批处理（首选方案）
- **核心特性**：
  - 使用游标字段（如自增 id）排序，`id > lastId ORDER BY id ASC LIMIT batchSize`
  - 强烈建议明细表增加 `process_status` 字段，支持断点续传和服务重启重试
  - SpEL 条件过滤 + 单条异常隔离
  - 批量更新修改过的数据
- **子类钩子**：`loadDetailsBatch` / `extractFacts` / `doExecute` / `saveModifiedDetailsBatch` / `getDetailId`

#### 4.4.2 AbstractStreamingDetailAction — 流式查询（只读计算场景）

[AbstractStreamingDetailAction.java](file:///d:/WorkSpace/Trae/multiple-module-spring-cloud/business-core-kernel/business-core-application/src/main/java/com/example/core/application/extension/AbstractStreamingDetailAction.java)

- **适用场景**：只读、计算、内存聚合、少更新的场景
- **核心特性**：
  - 底层使用 ResultSet 流式读取，长期占用一个数据库连接
  - **强制开启 `REQUIRES_NEW` 新事务**进行批次写入，避免和流查询游标串用连接导致死锁
  - 通过 `Consumer<T>` 模式将数据源抽象解耦（不关心 MyBatis 还是 JPA）
- **警告**：若需要在流中批量 UPDATE，必须确保 `saveModifiedDetailsBatch` 内部开启全新事务

#### 4.4.3 AbstractJsonStreamIngestionAction — JSON 流式摄入（工业级大杀器）

[AbstractJsonStreamIngestionAction.java](file:///d:/WorkSpace/Trae/multiple-module-spring-cloud/business-core-kernel/business-core-application/src/main/java/com/example/core/application/extension/AbstractJsonStreamIngestionAction.java)

- **适用场景**：从文件服务下载大 JSON 文件并逐行摄入到数据库
- **核心特性**：
  - 使用 Jackson `JsonParser` 流式解析，防 OOM
  - **预热的 ObjectReader** 提升循环内解析性能
  - **单行数据异常隔离**：一行脏数据不能搞崩整个文件
  - **批次级异常隔离**：某批因 DB 死锁/唯一键冲突失败不影响其他批次
  - **降级补偿机制**：批次失败时自动降级为单条重试，每条单独开启新事务
  - **精准追踪**：通过 `extractTraceId` 回调输出脏数据的行号或唯一标识
  - **死信表预留**：注释中提供 `writeDeadLetterLog` 扩展点
- **子类钩子**：`mapToEntity` / `saveBatch` / `extractTraceId`

### 4.5 微编排核心：StepPipelineExecutor

[StepPipelineExecutor.java](file:///d:/WorkSpace/Trae/multiple-module-spring-cloud/business-core-kernel/business-core-application/src/main/java/com/example/core/application/pipeline/StepPipelineExecutor.java)
是扩展动作链的执行器：

- **排序执行**：按 `StepExtensionConfig.order` 排序后顺序执行
- **SpEL 条件过滤**：不满足条件的动作记录为 `SKIPPED`
- **异步派发**：`isAsync=true` 的动作通过 `CompletableFuture.runAsync` 异步执行
- **上下文突变**：扩展动作可通过 `ExtensionExecutionResult.mutations()` 向 `context.extensionFacts` 追加数据
- **致命/非致命区分**：`isCritical=true` 的动作失败会抛 `SystemException` 中断流程；非致命则忽略继续
- **飞行日志**：每个动作的执行结果都记录为 `ActionExecutionRecord`，包含 success/failure/skipped/async 四种语义

### 4.6 防腐层 Gateway 接口

`business-core-domain/gateway/` 定义 3 个防腐层接口，由 infrastructure 层实现：

| Gateway                      | 职责                                               | 默认实现                                             |
|------------------------------|----------------------------------------------------|------------------------------------------------------|
| `BusinessConfigGateway`      | 查询下一步路由、材料规则、提取器配置、表单解析配置 | 由各业务服务实现                                     |
| `ConditionEvaluationGateway` | SpEL 表达式求值                                    | `SpelConditionEvaluationGateway`（基于 Spring SpEL） |
| `FileIntegrationGateway`     | 触发异步解析、流式下载文件、申请上传 Token         | 由 file-service 集成实现                             |

### 4.7 领域事件体系

`business-core-domain/event/` 定义 5 个领域事件（均使用 `record` + `DomainEvent` 接口）：

| 事件                      | 触发时机                   |
|---------------------------|----------------------------|
| `FormUploadedEvent`       | 表单上传完成               |
| `ApplicationSpawnedEvent` | 表单解析完成裂变出新申请单 |
| `StepEnteredEvent`        | 申请单进入新步骤           |
| `PipelineExecutedEvent`   | 管道执行完成               |
| `BatchStatusChangedEvent` | 批次状态变更               |

所有事件均提供 `static of(...)` 方法，内部通过 `EventId.generate()` 和 `LocalDateTime.now()` 设置事件 ID 和时间。

### 4.8 Repository 契约

| Repository              | 聚合根                | 扩展方法                                                      |
|-------------------------|-----------------------|---------------------------------------------------------------|
| `ApplicationRepository` | `BusinessApplication` | 继承基础契约（无扩展）                                        |
| `FormRepository`        | `BusinessForm`        | `findByApplicationId(ApplicationId)`                          |
| `BatchRepository`       | `BusinessBatch`       | `findByFormId(FormId)` / `findByApplicationId(ApplicationId)` |

### 4.9 错误码体系

| 层          | 错误码枚举            | 示例                                                                            |
|-------------|-----------------------|---------------------------------------------------------------------------------|
| domain      | `CoreDomainErrorCode` | `INVALID_STATUS(200001)` / `INVALID_DATA(200002)` / `INVALID_OPERATION(200003)` |
| application | `CoreAppErrorCode`    | `STEP_HANDLER_FAILED(102001)` / `DATA_NOT_FOUND(101001)` / `INVALIDATE(103001)` |

### 4.10 Infrastructure 层支持

| 类                               | 职责                                                                                                   |
|----------------------------------|--------------------------------------------------------------------------------------------------------|
| `SpelConditionEvaluationGateway` | 基于 Spring SpEL 实现条件求值，注入 customerNo/productNo/businessType/facts 变量，异常容错视为不通过   |
| `DomainServiceConfiguration`     | 通过 `@ComponentScan` 扫描 `com.example.core` 和 `com.example.business` 包下标注 `@DomainService` 的类 |
| `LibJacksonModule`               | 注册 `BusinessExtension` 的 Jackson Mix-in                                                             |
| `BusinessExtensionMixIn`         | JSON 序列化配置                                                                                        |

### 4.11 business-core-kernel 使用指南

#### 业务线如何扩展明细层材料逻辑（来自 PlanMaterialPreparationHandler 注释）

1. **不要修改内核代码**
2. 在自己的业务模块中实现 `StepExtensionAction` 接口
3. 实现类中：
  - 注入业务特有的明细层 Repository（如 `EmployeeDetailRepository`）
  - 通过 `app.id()` 捞出明细数据
  - 注入内核的 `MaterialRuleEngine` 和 `ConditionEvaluationGateway`
  - 遍历明细，将明细转换为局部 Facts，调用引擎计算 `DETAIL` 级别材料
4. **配置闭环**：在配置中心 JSON 中，将实现的 Bean 名字配置到 `postExtensions` 中

#### 业务线如何使用表单解析能力（来自 DefaultFormParsingHandler 注释）

1. **无需编写任何代码**：在流程 JSON 配置中，将步骤的 `mainProcessor` 配置为 `"defaultFormParsingHandler"`
2. **配置解析规则**：在配置中心的 `FormParsingConfig` 中为业务类型配置 `parseTemplateId`
3. **明细落库**：在下一个步骤的 `detailProcessors` 中挂载 `AbstractJsonStreamIngestionAction` 子类，完成 JSON → 业务实体（如
   `EmployeeDetail`）的映射与落库

### 4.12 business-core-kernel 已知限制

- `business-core-api` 模块当前为空（仅有 pom.xml），未来可能存放跨服务共享的 DTO
- `business-core-adapter` 模块当前未填充代码（控制器由各业务服务的 adapter 层提供）
- `business-core-infrastructure` 仅提供 SpEL 求值和 Jackson 配置，未提供 `BusinessConfigGateway` 和
  `FileIntegrationGateway` 的默认实现（需各业务服务自行实现）
- 缺少 `starter` 层，作为内核被业务服务依赖而非独立部署

---

## 五、file-service 文件服务

### 5.1 模块定位

文件服务提供 Excel 解析、模板配置管理、数据校验、Excel 导出等能力。7 层模块齐全：

```
file-types / file-domain / file-api / file-application
  / file-adapter / file-infrastructure / file-starter
```

### 5.2 领域模型

**3 个聚合根**：

| 聚合根           | 职责                                 |
|------------------|--------------------------------------|
| `ParseTask`      | 解析任务（含子任务列表）             |
| `TemplateConfig` | 模板配置（含字段定义、触发器、策略） |
| `SubTaskData`    | 子任务数据（含行数据）               |

**11 个领域服务**（部分列举）：

- `ExcelParser` / `ExcelParserImpl`：基于 Fesod 的流式 Excel 解析
- `TableRegionParser`：表格区域解析（处理触发行）
- `ExcelExporter` / `FesodExcelExporter`：基于 Fesod 的 Excel 导出
- `ExportDecisionService`：导出决策（校验失败阻止导出）
- `AviatorExpressionEvaluator`：基于 Aviator 的表达式求值
- `YamlConfigLoader`：YAML → 领域对象映射
- `ParseFlowService`：解析流程编排

**5 个 Gateway SPI**：跨服务调用通过 Gateway 接口隔离，实现位于 infrastructure 层。

### 5.3 数据存储

- 数据库：PostgreSQL（JSONB 存储嵌套结构如 canonical data、configuration definitions、row data）
- 主表：`parse_task`、`template_config`
- 明细表：`file_sub_task_row`（独立表支持高效分页）
- 配置管理：数据库为权威源，YAML 为初始源

### 5.4 测试覆盖

`file-infrastructure/src/test` 下包含：

- `ParseFlowIntegrationTest`：解析流程集成测试（含 HEADER_SNIFF 指纹匹配回归）
- `ExportFlowIntegrationTest`：导出流程集成测试（happy path + 校验失败阻止导出）
- `FesodExcelExporterTest`：Fesod 占位符和 FillWrapper 烟雾测试
- `ExportDecisionServiceTest`：导出决策服务单元测试（4 个）
- 其他领域单元测试， **共计 52 个测试全部通过**

### 5.5 关键约束

- `ExcelParserImpl` 必须设置 `headRowNumber(0)` 防止表头数据丢失
- Excel 导出失败必须抛 `SystemException` + `FileErrorCodes.EXCEL_EXPORT_FAILED`
- 空列表导出必须验证占位行处理
- 测试文件不写入源码目录，使用 `@TempDir` 或 `target/test-classes`
- 测试输出统一到 `target/test-output/`
- Fesod 2.0.2 限制需在代码注释中文档化

---

## 六、approval-service 审批服务

### 6.1 模块结构

7 层完整 DDD，与 file-service 同构。位于 `approval-service/` 下。

### 6.2 领域模型

**2 个聚合根**：

| 聚合根             | 职责                                 |
|--------------------|--------------------------------------|
| `ApprovalFlow`     | 审批流定义（含节点列表，可发布版本） |
| `ApprovalInstance` | 审批实例（含节点执行记录、审批记录） |

**强类型 ID**（位于 `approval-types`）：

- `ApprovalFlowId` / `ApprovalInstanceId` / `NodeId` / `ExecutionId` / `RecordId` / `AccountManagerCode`

**3 个领域服务**：

- `ApprovalFlowService`：审批流管理（创建、发布版本、查询节点）
- `ApprovalInstanceService`：审批实例编排（启动、通过、驳回、转交、撤回）
- `ApprovalDecisionService`：审批决策

**8 个领域事件**：覆盖审批生命周期各节点（启动、通过、驳回、转交、撤回等）。

### 6.3 数据库

- 实际使用 **MySQL**（与规则声明的 PostgreSQL 不一致）
- 5 张表：`approval_flow`、`approval_node`、`approval_instance`、`approval_node_execution`、`approval_record`
- 同时提供 `schema-mysql.sql` 和 `schema-pg.sql` 两套 DDL

### 6.4 关键实现模式

#### RepositoryImpl 级联删除模式

以 [ApprovalInstanceRepositoryImpl.java](file:///d:/WorkSpace/Trae/multiple-module-spring-cloud/approval-service/approval-infrastructure/src/main/java/com/example/approval/infrastructure/repository/ApprovalInstanceRepositoryImpl.java#L171-L215)
为例，删除时按子表 → 主表顺序：

```java
// 1. 删除审批记录（通过 execution 关联）
recordMapper.deleteByQuery(... EXECUTION_ID in (select ID from execution where INSTANCE_ID = ?));
// 2. 删除节点执行记录
executionMapper.deleteByQuery(... INSTANCE_ID = ?);
// 3. 删除主表
instanceMapper.deleteById(instanceIdStr);
```

#### MapStruct 高级映射模式

由于领域对象使用 `record` + private 构造函数，MapStruct `@BeanMapping(factoryMethod = "reconstitute")` 不被支持，采用以下模式：

- `toDomain` 方向改为 `default` 方法，手动调用 `XxxDomain.reconstitute(...)`
- `toDO` 方向改为 `expression = "java(...)"`
- ID 类型 `Long → String` 转换使用 `String.valueOf(...)`

### 6.5 应用服务编排

每个用例遵循统一模式：

```
1. 加载审批实例/审批流
2. 调用聚合根行为方法（approve/reject/transfer/withdraw）
3. instanceRepository.save(instance)
4. instance.getDomainEvents().forEach(eventBus::publish)
5. instance.clearDomainEvents()
```

### 6.6 已知 TODO

- `RoleUserGatewayImpl.getUsersInSamePlan`：伪实现（返回空列表 + log.warn）
- `ApprovalInstanceRepositoryImpl.findByApproverId`：伪实现
- `ApprovalInstanceService.listMyPendingApprovals`：抛 `UnsupportedOperationException`
- `ApprovalInstanceService.getApprovalHistory`：返回空列表

---

## 七、integration-service 集成服务

### 7.1 模块结构

6 层结构（缺少 `types` 层，使用 `integration-service-*` 命名前缀）：

```
integration-service-api / -domain / -application
  / -adapter / -infrastructure / -starter
```

### 7.2 业务能力

封装外部交易系统调用，核心是 **5564 交易码查询**。

### 7.3 技术集成

- 通过 Retrofit 调用外部 HTTP 接口
- Logbook 记录请求/响应日志
- 通过 `@HttpExchange` 在 API 层定义接口，Adapter 层实现
- 配置位于 `integration-service-starter/src/main/resources/application.yml` 和 `application-local.yml`

### 7.4 已知问题

- `PortfolioBalanceConverter` 曾出现 `source = "aggregate"` MapStruct 错误，已修复
- `ApprovalFlowApi` / `ApprovalInstanceApi` 中 `PageInfo<T>` 误用问题（应使用 `PageData<T>`）

---

## 八、demo-gateway API 网关

### 8.1 模块结构

单模块结构，包含启动类 `GatewayApplication` 和自定义过滤器。

### 8.2 核心组件

- `ExcludeRouteFilter`：路由排除过滤器
- `ExcludeRouteMatcher`：路由匹配器
- `ExcludeRouteProperties`：配置属性绑定
- `GatewayFilterOrder`：过滤器顺序定义
- `FilterOrderChecker`：过滤器顺序检查器

### 8.3 配置

提供 4 套环境配置：

- `application.yml`（基础）
- `application-dev.yml`（开发）
- `application-local.yml`（本地）
- `application-prod.yml`（生产）
- `application-example.yml`（示例）
- `logback-spring.xml`（日志）

### 8.4 测试覆盖

包含 5 个测试类：`ExcludeRoutePropertiesBindingTest`、`ExcludeRoutePropertiesTest`、`ExcludeRouteFilterTest`、
`ExcludeRouteMatcherTest`、`GatewayIntegrationTest`。

---

## 九、配置体系分析

### 9.1 配置文件分布

全项目共 **14 个 application.yml** + 4 套环境配置：

| 服务                        | 配置文件                                 |
|-----------------------------|------------------------------------------|
| demo-gateway                | application.yml + dev/local/prod/example |
| integration-service-starter | application.yml + local                  |
| approval-starter            | application.yml + local                  |
| file-starter                | **缺失 application.yml**（已知问题）     |
| shared-event-starter        | application.yml（含 mysql.sql/pg.sql）   |
| shared-id-starter           | application.yml + test                   |
| shared-logging-starter      | application.yml                          |
| shared-pdf-starter          | application.yml                          |
| shared-web-starter          | application.yml                          |

### 9.2 启动类

4 个启动类（注：`business-core-kernel` 作为内核无启动类）：

| 启动类                   | 模块                        |
|--------------------------|-----------------------------|
| `GatewayApplication`     | demo-gateway                |
| `FileApplication`        | file-starter                |
| `ApprovalApplication`    | approval-starter            |
| `IntegrationApplication` | integration-service-starter |

### 9.3 数据库一致性

| 服务                   | 实际数据库                  | 规则声明                |
|------------------------|-----------------------------|-------------------------|
| file-service           | PostgreSQL（JSONB）         | PostgreSQL（首选）      |
| approval-service       | MySQL                       | MySQL（备选，已合法化） |
| shared-id-starter      | 提供 MySQL + PG 两套 schema | -                       |
| shared-logging-starter | 提供 MySQL + PG 两套 schema | -                       |
| shared-event-starter   | 提供 MySQL + PG 两套 schema | -                       |

> 已统一：`02-技术栈规范.md` 现声明 PostgreSQL 为首选、MySQL 为备选，与实际一致。

### 9.4 消息队列

- 规则文档 `02-技术栈规范.md` 声明使用 **RocketMQ Spring Boot Starter 2.3.0**（已与实际一致）
- 通过 `shared-event-starter` 统一封装

### 9.5 服务发现

使用 Spring Cloud Alibaba Nacos Discovery（2025.0.0.0）作为服务注册中心。

---

## 十、关键发现与已知问题

### 10.1 架构层面

| 编号 | 严重级别  | 问题                                        | 建议                                                |
|------|-----------|---------------------------------------------|-----------------------------------------------------|
| A1   | Important | `customer-service` 在规则中声明但实际不存在 | 移除规则引用或创建该模块                            |
| A2   | Minor     | `business-core-api` 为空模块                | 明确该模块定位，或填充共享 DTO，或下沉到 shared-api |
| A3   | Minor     | `business-core-adapter` 未填充代码          | 控制器由各业务服务的 adapter 层提供，可保持现状     |

### 10.2 规则与实现不一致

| 编号 | 严重级别  | 问题                                                             | 状态                                           |
|------|-----------|------------------------------------------------------------------|------------------------------------------------|
| B1   | Important | 规则声明 PostgreSQL，approval-service 实际用 MySQL               | ✅ 已修复：规则允许 MySQL/PG 双选              |
| B2   | Important | 规则声明 Spring Kafka，实际用 RocketMQ 2.3.0                     | ✅ 已修复：规则改为 RocketMQ                   |
| B3   | Minor     | `shared-logging-starter` 包名 `com.example.share.logging` 缺 `d` | ✅ 已修复：统一为 `com.example.shared.logging` |
| B4   | Minor     | file-starter 缺失 `application.yml`                              | ✅ 已修复：已创建配置文件                      |

### 10.3 代码 TODO 清单

| 模块                         | 文件                             | TODO 内容                                                                         |
|------------------------------|----------------------------------|-----------------------------------------------------------------------------------|
| approval-infrastructure      | `RoleUserGatewayImpl`            | `getUsersInSamePlan` 伪实现                                                       |
| approval-infrastructure      | `ApprovalInstanceRepositoryImpl` | `findByApproverId` 伪实现                                                         |
| approval-application         | `ApprovalInstanceService`        | `listMyPendingApprovals` 抛异常                                                   |
| approval-application         | `ApprovalInstanceService`        | `getApprovalHistory` 返回空列表                                                   |
| business-core-infrastructure | -                                | `BusinessConfigGateway` / `FileIntegrationGateway` 无默认实现，需业务服务自行实现 |

### 10.4 工程化亮点

- 严格的 DDD 七层模块划分，依赖方向清晰
- 强类型 ID 体系（`Identifier<T>` + `@IdDefinition`），避免裸 Long/String 滥用
- 完善的异常层次（`BaseException` → `DomainException` / `BusinessException` / `SystemException`）
- 7 个自定义 Spring Boot Starter 通过标准 `AutoConfiguration.imports` 装配
- 多级缓存（Caffeine + Redisson + 分布式锁）
- 双 payload 事件驱动（领域事件 + 集成事件 DTO）
- **business-core-kernel 提供工业级业务编排内核**：表单解析 → 申请单裂变 → 步骤编排 → 材料计算 → 明细摄入全链路模板
- **工业级批量处理三剑客**：游标分页 / 流式查询 / JSON 流式摄入，全部支持单行/批次级异常隔离与降级补偿
- **微编排引擎 StepPipelineExecutor**：SpEL 条件路由 + 同步/异步派发 + 致命/非致命区分 + 飞行日志
- 完善的测试覆盖（file-service 52 个测试全通过）
- TDD 开发流程有据可查（docs/superpowers/plans、docs/superpowers/specs）

---

## 十一、技术债务与后续建议

### 11.1 短期修复（优先级高）

1. ~~**修复 file-starter 缺失 application.yml**：补充数据源、Nacos、Redis 等基础配置~~ ✅ 已完成（2026-07-19）
2. ~~**统一数据库选型**：approval-service 迁移到 PostgreSQL 或在规则中明确允许 MySQL~~ ✅ 已完成（2026-07-19，方案：修改规则允许双数据库并存）
3. ~~**统一消息队列声明**：更新 `02-技术栈规范.md` 将 Spring Kafka 改为 RocketMQ~~ ✅ 已完成（2026-07-19）
4. ~~**修复 shared-logging-starter 包名**：`com.example.share.logging` → `com.example.shared.logging`~~ ✅
   已完成（2026-07-19，涉及 53 个 Java 文件 + imports 文件 + integration-service 配置 + 物理目录重命名）

### 11.2 中期补全（优先级中）

1. **实现 approval-service 待办列表查询**：设计专门的待审批视图表或缓存机制
2. **实现 RoleUserGatewayImpl.getUsersInSamePlan**：对接用户服务 API
3. **明确 business-core-api 定位**：填充共享业务 DTO 或下沉到 shared-api
4. **为 business-core-kernel 提供默认 Gateway 实现**：降低业务服务接入成本
5. **完善 PortfolioBalanceConverter 等历史问题排查**：避免 MapStruct 隐式映射风险

### 11.3 长期演进（优先级低）

1. **引入契约测试**：跨服务 API 调用通过 Pact 等工具验证兼容性
2. **补充集成测试**：approval-service 缺少端到端集成测试，business-core-kernel 缺少编排流程集成测试
3. **统一数据库 schema 管理**：Flyway 或 Liquibase 替代手工 schema-*.sql
4. **完善可观测性**：统一日志格式、链路追踪、指标采集
5. **business-core-kernel 文档化**：将 SPI 扩展指南抽取为独立文档，方便业务线接入

---

## 十二、总结

`multiple-module-spring-cloud` 是一个 **架构设计严谨、工程化程度高、DDD 落地彻底**的 Spring Cloud 微服务多模块项目。其核心价值体现在：

1. **架构一致性**：所有业务服务严格遵循 types → domain → api → application → adapter → infrastructure → starter
   七层划分，依赖方向单向收敛
2. **领域纯净性**：domain 层零框架依赖，仅使用 Lombok，通过 SPI 接口与基础设施解耦
3. **复用性强**：12 个 demo-shared 子模块覆盖 ID/缓存/事件/Web/客户端/日志/PDF/文件等横切关注点
4. **内核可扩展**：`business-core-kernel` 提供完整的业务流程编排内核，业务线通过实现 SPI 接口即可接入，无需修改内核代码
5. **工业级批处理**：3 个批量处理基类覆盖游标分页/流式查询/JSON 流式摄入全部场景，支持异常隔离和降级补偿
6. **可演进性**：强类型 ID、聚合根、领域事件等模式为后续业务扩展提供坚实基础

当前主要技术债务集中在 **规则文档与实现不一致**（数据库、消息队列）、 **部分 TODO 未实现**（approval-service 待办查询）、
**配置文件缺失**（file-starter）三个方面。建议按优先级逐步消化，保持架构一致性。

---

## 附录 A：项目目录结构（顶层）

```
multiple-module-spring-cloud/
├── .trae/                          # Trae IDE 配置 + 规则 + Skills
│   ├── rules/                      # 项目规则（架构/技术栈/命名/数据库等）
│   └── skills/                     # Superpowers 技能集
├── approval-service/               # 审批服务（7 层 DDD）
├── business-core-kernel/           # 业务流程编排引擎内核（6 层 DDD）
├── demo-gateway/                   # API 网关
├── demo-shared/                    # 共享基础库（12 个子模块）
├── docs/                           # 文档（plans/specs/模板配置/Fesod 说明）
├── file-service/                   # 文件服务（7 层 DDD）
├── integration-service/            # 集成服务（6 层）
├── form.yaml                       # 表单配置示例
├── import_declare_base.yaml        # 业务基线配置示例
├── source_cust_a_v1.yaml           # 客户 A 源模板配置示例
├── source_cust_b_v1.yaml           # 客户 B 源模板配置示例
└── pom.xml                         # 根 Maven POM
```

## 附录 B：business-core-kernel 目录结构

```
business-core-kernel/
├── business-core-types/            # 领域原语（ID 类型）
├── business-core-domain/           # 领域层
│   └── src/main/java/com/example/core/domain/
│       ├── annotation/             # @DomainService 注解
│       ├── aggregate/              # 聚合根（BusinessApplication / BusinessForm / BusinessBatch）
│       ├── event/                  # 5 个领域事件
│       ├── gateway/                # 3 个防腐层接口
│       ├── repository/             # 3 个 Repository 接口
│       ├── spi/                    # 3 个 SPI 扩展点
│       └── errorcode/              # CoreDomainErrorCode
├── business-core-api/              # API 层（当前为空，预留）
├── business-core-application/      # 应用层
│   └── src/main/java/com/example/core/application/
│       ├── extension/              # 工业级批量处理三剑客基类
│       ├── handler/                # 3 个开箱即用的 StepActionHandler
│       ├── listener/               # 2 个事务事件监听器
│       ├── pipeline/               # StepPipelineExecutor 微编排引擎
│       ├── service/                # 3 个应用服务
│       └── errorcode/              # CoreAppErrorCode
├── business-core-adapter/          # 适配层（当前为空，控制器由各业务服务提供）
└── business-core-infrastructure/   # 基础设施层
    └── src/main/java/com/example/core/infrastructure/
        ├── configuration/          # DomainServiceConfiguration + LibJacksonModule
        ├── evaluation/              # SpelConditionEvaluationGateway
        └── json/                    # BusinessExtensionMixIn
```
