# business-core-kernel - AI Navigation Guide

> 本文件由 `/legacy-explain` 自动生成，帮助 AI 快速理解本模块的架构、核心流程与扩展点。

## 模块定位

`business-core-kernel` 是**通用业务申请编排引擎**（Kernel），提供标准化的"业务申请全生命周期"管线。各业务线（如 annuity-service）通过实现 SPI 接口注入业务特定逻辑，**不得修改 kernel 代码**。

## 架构总览

```
business-core-types      → 领域原语（ID 类型、BankAccount）
business-core-domain     → 聚合根、值对象、领域服务、SPI 接口、领域事件
business-core-api        → 对外契约（API 接口、Command/Query/Response、SessionContext）
business-core-application→ 应用服务、流程编排、事件监听、访问控制
business-core-adapter    → Controller、DTO 转换器、会话解析、自动配置
business-core-infrastructure → Repository 实现、DO/Mapper、集成网关、条件求值引擎
（无 starter — kernel 以 library 形式被业务服务消费）
```

## 核心聚合根

| 聚合根 | 表 | 职责 |
|--------|------|------|
| `BusinessApplication` | `t_business_application` | 业务申请单（per-plan 维度），持有状态机与流程步骤 |
| `BusinessForm` | `t_business_form` | 表单文件生命周期（上传→解析→确认），关联 Batch |
| `BusinessBatch` | `t_business_batch` | 批次跟踪与统计，自动计算终态 |

### BusinessApplication 关键行为

- `createFromForm()` — 工厂方法，注册 `ApplicationSpawnedEvent`
- `transit(step)` — 步骤跃迁，注册 `StepEnteredEvent`，终态标记 COMPLETED
- `reject()` / `withdraw()` — 终态转换
- `attachExtension(BusinessExtension)` — kernel 扩展点，业务线注入多态值对象
- `assignMaterials(List<MaterialItem>)` — 智能合并历史上传
- `isPlanMaterialSatisfied(MaterialConditionContext)` — 包件模式自动通过，逐件逐条校验
- `requireFileForParsing()` — 校验文件已挂载

### BusinessBatch 自动终态

- `incrementTotalApplicationCount()` / `incrementSuccessCount()` / `incrementFailedCount()` 每次变更后自动调 `recalculateStatus()`
- `BatchStatus.determine(failed, total)` → 全失败=FAILED, 部分=PARTIAL_FAILED, 无=COMPLETED

### BusinessForm 生命周期

- `create()` → WAITING_UPLOAD → `markAsUploaded()` → UPLOADED → `markAsParsing()` → PARSING → `markAsParsed()` → PARSED
- `markAsDeleted()` — 仅允许 UPLOADED/PARSED/WAITING_UPLOAD 状态

## SPI 扩展点

### 领域层 SPI（domain/gateway）

| 接口 | 职责 | 默认实现 |
|------|------|----------|
| `BusinessConfigGateway` | 查询步骤路由、材料规则、提取器配置、表单解析配置 | **无** — 业务服务必须实现 |
| `FileIntegrationGateway` | 触发异步解析、流式下载文件、申请上传 token | **无** — 业务服务必须实现 |
| `ConditionEvaluationGateway` | SpEL/Aviator/QLExpress 条件求值 | `SpelConditionEvaluationGateway`（matchIfMissing=true） |
| `ApprovalIntegrationGateway` | 启动审批、查询审批状态 | **无** — 业务服务必须实现 |

### 引擎层 SPI（domain/engine/service）

| 接口 | 职责 | 策略注册 |
|------|------|----------|
| `StepActionHandler` | 步骤主处理器（mainProcessor） | `StepActionHandlerRegistry` |
| `StepExtensionAction` | 管线扩展动作（preValidations/postExtensions） | `ExtensionActionRegistry` |
| `BusinessFactExtractor` | 提取业务特定事实到 `extensionFacts` | `BusinessFactExtractorRegistry` |

### 策略注册表模式

所有 SPI 通过 `AbstractStrategyRegistry<T>` 管理：Spring 容器启动时自动收集所有 Bean，按 `name()` 鉴别符存入 `ConcurrentHashMap`，运行时通过 `get(name)` 获取。

### Kernel 内置处理器

| Bean Name | 类 | 步骤 |
|-----------|------|------|
| `defaultFormParsingHandler` | `DefaultFormParsingHandler` | FORM_DETAIL_INGESTION |
| `planMaterialPreparationHandler` | `PlanMaterialPreparationHandler` | PLAN_MATERIAL_PREPARATION |
| `approvalSubmissionHandler` | `ApprovalSubmissionHandler` | APPROVAL_SUBMISSION |

## 三阶段编排

### Phase 1: 表单上传与异步解析

`BusinessFormAppService` 编排：`confirmUpload → triggerParsing → handleParsingResult`

1. 确认上传 → 表单状态 UPLOADED
2. 调用 `FileIntegrationGateway.triggerAsyncParsing()` → 表单状态 PARSING
3. 文件服务回调 → 流式消费 JSON → 创建多个 `BusinessApplication` 聚合根（每个 plan 一个）

### Phase 2: 步骤流程编排

`FlowOrchestrationService.advanceStep(appId)` 6 阶段管线：

```
1. load aggregate
2. buildFullConfigContext (含 extensionFacts)
3. query StepRouteConfig from BusinessConfigGateway
4. execute preValidations (StepPipelineExecutor)
5. execute mainProcessor
6. execute detailProcessors
7. execute postExtensions (sideEffects)
8. transit to nextStep (or terminal)
9. save + publish domain events
```

### Phase 3: 自动推进与挂起

- `ApplicationSpawnedListener` — 新申请自动推进第一步
- `StepAutoAdvanceListener` — SYSTEM_TASK 步骤自动推进；USER_TASK 步骤挂起等 BFF 层手动调用
- `FileParsedEventListener` — 解析完成后推进到 DATA_VERIFICATION
- `ApprovalResultEventListener` — 审批结果回调推进

## StepPipelineExecutor

扩展动作链执行器：

1. 按 `order` 排序
2. SpEL 条件过滤（`condition` 为空则默认通过）
3. 同步/异步分发（`isAsync=true` → `@Async`）
4. 上下文变更通过 `ExtensionExecutionResult.mutations()` 应用
5. critical 失败 → 抛异常回滚；non-critical 失败 → 记录并继续

## 条件求值引擎

三引擎可切换，通过 `ConditionEvaluationGateway` SPI：

| 实现 | 配置键 | 特点 |
|------|--------|------|
| `SpelConditionEvaluationGateway` | 默认（matchIfMissing=true） | 支持 `#customerNo`, `#productNo`, `#businessType`, `#facts` |
| `AviatorConditionEvaluationGateway` | `type=aviator` | 平铺 env map，`isCache=false` |
| `QLExpressConditionEvaluationGateway` | `type=qlexpress` | 使用 Express4Runner + QLOptions.cache(true) |

所有实现都有 `ConcurrentHashMap` 表达式缓存，求值异常统一返回 `false`。

## BusinessExtension 多态序列化

- 接口 `BusinessExtension extends ValueObject`，判别属性 `businessType()`
- Jackson Mix-in: `@JsonTypeInfo(use=NAME, include=EXISTING_PROPERTY, property="businessType")`
- `@JsonSubTypes({})` 空 — 业务模块通过 `@JsonSubTypes` 注册具体子类型
- `LibJacksonModule.registerMixIns(ObjectMapper)` 统一注册

## 访问控制

### SessionContext 安全模型

网关写入 sa-token 数据到 HTTP Header：
- `X-Session-Context`：Base64 编码的 JSON
- `X-Session-Sig`：HMAC 签名
- `X-Session-Expire`：过期时间戳

`SessionContextResolver` 验签后反序列化为 `SessionContext`。签名缺失或无效 → 返回空 SessionContext（fail-closed）。

### BusinessAccessGuard 渠道差异化

`DefaultBusinessAccessGuard`（`@ConditionalOnMissingBean` 可覆盖）：

| 渠道 | 额外校验 |
|------|----------|
| INTERNET | 代理委托检查（isProxy → onBehalfOf 必须在 delegatedPlanNos 中） |
| BRANCH | 二次认证检查（hasSecondaryAuth 必须为 true） |
| HQ | 无额外校验 |

公共校验：计划一致性 + 客户一致性 + `BUSINESS_{type}_HANDLE` 权限码。

### Controller 五步入口模式

所有 Controller 统一入口流程：

```
1. SupportedBusinessTypeValidator.validate(command, registrar)  // 类型校验
2. SessionContext session = sessionResolver.require()            // 会话解析
3. BusinessMetaContext metaCtx = metaAssembler.assemble(...)     // 元上下文组装
4. accessGuard.checkCanHandle(session, metaCtx)                 // 访问控制
5. appService.xxxCommand(...)                                    // 业务调用
```

所有 Controller 通过 `@ConditionalOnProperty("core-kernel.controllers.enabled")` 控制启用。

## 数据库设计

3 张表，均遵循项目规范（应用层管理时间戳、无 ORM 自动填充、逻辑删除、乐观锁）：

| 表 | 索引 |
|------|------|
| `t_business_application` | `idx_..._batch_id`, `idx_..._form_id`, `idx_..._parsed_file_id`, `idx_..._status`, `idx_..._current_step`, `idx_..._customer_no`, `idx_..._create_time` |
| `t_business_batch` | `idx_..._status`, `idx_..._customer_no`, `idx_..._business_type`, `idx_..._create_time` |
| `t_business_form` | `idx_..._batch_id`, `idx_..._status`, `idx_..._customer_no`, `idx_..._create_time` |

BusinessContext 和 OperatorInfo 均拍平为独立列便于 SQL 查询和索引。`business_extension` 字段使用 PostgreSQL JSONB 存储。

### DO ↔ 领域对象转换

三个 Converter 均为手写 MapStruct `@Mapper(componentModel="spring")`：
- `toDO`：遍历聚合根公开访问器，BusinessExtension 通过 `ObjectMapper` 序列化为 JSON 字符串
- `toDomain`：调用 `reconstitute()` 工厂方法重建聚合根，内存态字段（planMaterials/packageFile/applicationRefs/formRefs）置空

### Repository 基类

`AbstractBusinessRepository<T, ID, D>` 提供通用 CRUD + 领域事件发布。`BusinessApplicationRepositoryImpl` 和 `BusinessBatchRepositoryImpl`/`BusinessFormRepositoryImpl` **均不标注 `@Repository`**，由业务服务子类化后标注。

## 集成网关

| 网关 | 远程服务 | 关键特性 |
|------|----------|----------|
| `FileServiceIntegrationGateway` | `FileTaskApi`/`FileAccessApi` | 两阶段 token 下载，`StreamingResponseBody`→`PipedInputStream` 虚拟线程桥接，上传 token 含白名单+大小限制+TTL |
| `ApprovalServiceIntegrationGateway` | `ApprovalFlowApi`/`ApprovalInstanceApi` | 两步：匹配审批流(businessType+accountManager) → 启动审批实例 |

## IntegrationEventSimulator

Demo 环境替代 RocketMQ 消费者：将集成事件 DTO 通过 Spring `ApplicationEventPublisher` 发布到本地 `@EventListener`，实现进程内事件驱动。

## ApplicationFlowStep 完整流程

17 步，5 阶段：

| 阶段 | 步骤 |
|------|------|
| 发起 | DRAFT → FORM_DETAIL_INGESTION |
| 数据核查 | DATA_VERIFICATION → EMPLOYEE_DETAIL_VERIFICATION → ANOMALY_HANDLE → VERIFICATION_CONFIRM |
| 客户确认与材料 | CUSTOMER_CONFIRM → PLAN_MATERIAL_PREPARATION → MATERIAL_PACKAGE |
| 机构审核与提交 | TELLER_REVIEW → APPLICATION_REVIEW → APPROVAL_SUBMISSION → APPROVAL_RESULT |
| 受理与归档 | ACCEPTANCE → ARCHIVE → COMPLETED |

> TODO: 考虑合并 TELLER_REVIEW 与 APPLICATION_REVIEW

## 关键文件索引

| 类别 | 文件 |
|------|------|
| **聚合根** | `domain/business/aggregate/root/BusinessApplication.java` |
| | `domain/business/aggregate/root/BusinessForm.java` |
| | `domain/business/aggregate/root/BusinessBatch.java` |
| **流程引擎** | `application/engine/step/service/FlowOrchestrationService.java` |
| | `application/engine/step/service/StepPipelineExecutor.java` |
| **SPI 接口** | `domain/engine/gateway/BusinessConfigGateway.java` |
| | `domain/engine/gateway/FileIntegrationGateway.java` |
| | `domain/engine/gateway/ConditionEvaluationGateway.java` |
| | `domain/engine/gateway/ApprovalIntegrationGateway.java` |
| | `domain/engine/service/step/StepActionHandler.java` |
| | `domain/engine/service/step/StepExtensionAction.java` |
| | `domain/engine/service/step/BusinessFactExtractor.java` |
| **事件监听** | `application/business/listener/ApplicationSpawnedListener.java` |
| | `application/engine/listener/StepAutoAdvanceListener.java` |
| | `application/engine/listener/FileParsedEventListener.java` |
| | `application/engine/listener/ApprovalResultEventListener.java` |
| **应用服务** | `application/engine/step/service/BusinessFormAppService.java` |
| | `application/business/service/BusinessApplicationAppService.java` |
| | `application/business/service/BusinessBatchAppService.java` |
| | `application/business/service/MaterialAppService.java` |
| | `application/business/service/BusinessProgressAppService.java` |
| **访问控制** | `application/business/guard/BusinessAccessGuard.java` |
| | `application/business/guard/DefaultBusinessAccessGuard.java` |
| | `adapter/context/SessionContextResolver.java` |
| | `adapter/context/BusinessMetaContextAssembler.java` |
| **集成网关** | `infrastructure/engine/gateway/FileServiceIntegrationGateway.java` |
| | `infrastructure/engine/gateway/ApprovalServiceIntegrationGateway.java` |
| **求值引擎** | `infrastructure/engine/evaluation/SpelConditionEvaluationGateway.java` |
| | `infrastructure/engine/evaluation/AviatorConditionEvaluationGateway.java` |
| | `infrastructure/engine/evaluation/QLExpressConditionEvaluationGateway.java` |
| **序列化** | `infrastructure/engine/json/BusinessExtensionMixIn.java` |
| | `infrastructure/engine/configuration/LibJacksonModule.java` |
| **Repository** | `infrastructure/business/repository/AbstractBusinessRepository.java` |
| | `infrastructure/business/repository/BusinessApplicationRepositoryImpl.java` |
| **DDL** | `infrastructure/src/main/resources/schema-pg.sql` |
| | `infrastructure/src/main/resources/schema-mysql.sql` |

## 扩展新业务线指南

1. 在业务服务中实现 `BusinessExtension` 接口，添加 `@JsonTypeName("YOUR_TYPE")`
2. 实现 `BusinessConfigGateway`（必须），配置步骤路由、材料规则等
3. 实现 `FileIntegrationGateway` 和 `ApprovalIntegrationGateway`（必须）
4. 按需实现 `StepActionHandler`、`StepExtensionAction`、`BusinessFactExtractor`（可选）
5. 子类化 `BusinessApplicationRepositoryImpl`，标注 `@Repository`，覆写 `findByFileTaskId`/`findByBatchId`
6. 如需覆盖默认访问控制，实现 `BusinessAccessGuard` 并注册为 Bean
7. 在 Jackson `ObjectMapper` 中注册 BusinessExtension 子类型
8. 配置 `core-kernel.controllers.enabled=true` 启用 kernel 提供的 Controller
