# File Service Phase 1 实现任务清单

| 项 | 值 |
|----|----|
| 关联 spec | [file-service-parse-engine.md](./file-service-parse-engine.md) |
| 创建日期 | 2026-07-18 |
| 任务总数 | 12 |

> 本清单用于后续 `writing-plans` 阶段生成详细实现计划。每个任务对应一个可独立验证的工作单元。

---

## T1: shared-event-starter 重构（前置任务）

**优先级**：P0（阻塞其他所有任务）
**预估文件数**：12
**依赖**：无

**范围**：
- 新增 `IntegrationEventConverter` SPI 接口（shared-domain）
- 改造 `EventBus`：注入转换器列表，publish 时转换
- 改造 `EventDispatcher` 接口：加 `integrationEvent` 参数
- 改造 `RocketMQEventDispatcher`/`RedisEventDispatcher`：发送集成事件
- 改造 `SpringEventDispatcher`：保持发送领域事件（本地）
- 改造 `EventDeliverer`/`EventRecoveryJob`：适配新签名
- 改造 `JdbcEventStore`：双 payload 落库，补偿任务用 `integration_payload`
- 改造 `EventAutoConfiguration`：注入转换器列表
- 更新 `pg.sql`/`mysql.sql`：加 `integration_type`、`integration_payload` 字段

**验收**：
- 现有使用 EventBus 的服务无需修改即可正常工作（向后兼容）
- 提供转换器后，MQ 消息体为集成事件 JSON
- 补偿任务能从 `integration_payload` 恢复发送

---

## T2: file-service 模块脚手架

**优先级**：P0
**预估文件数**：8（7 个 pom.xml + 1 个启动类）
**依赖**：T1

**范围**：
- 根 pom 加入 `<module>file-service</module>`
- file-service 父 pom 聚合 7 个子模块
- 7 个子模块 pom.xml（types/domain/api/application/adapter/infrastructure/starter）
- file-starter 启动类 `FileServiceApplication`
- file-starter `application.yml` 基础配置

**验收**：
- `mvn clean install -pl file-service` 构建成功
- 启动类能正常启动（连不上数据库无关，只要 Spring 上下文加载成功）

---

## T3: file-types 领域原语

**优先级**：P0
**预估文件数**：5
**依赖**：T2

**范围**：
- `FileTaskId`、`SubTaskId`、`TemplateConfigId`（ULID 标识，实现 `Identifier<String>`）
- `BizType`、`TemplateCode`（业务编码值对象）

**验收**：
- 所有类型实现 `Identifier<String>` 接口
- ULID 生成正常工作（依赖 shared-id-starter）
- 单元测试覆盖 equals/hashCode/toString

---

## T4: file-domain 领域模型

**优先级**：P0
**预估文件数**：约 45
**依赖**：T3

**范围**：
- 3 个聚合根：`ParseTask`、`SubTaskData`、`TemplateConfig`
- 1 个聚合内实体：`SourceTemplateDef`
- 值对象集合（SubTaskSummary、TaskError、RowError、BusinessContext、CanonicalData、FieldLocation、ValidationResult、FetchPagination、PageInfo、PagedRows）
- 配置定义值对象（CanonicalModelDef、PropertyFieldDef、TableDef、FieldDef、ValidationRule、DerivationRule、SplitConfig、RegionDef、RegionTrigger、KvStrategy、TableStrategy、DataEndRule、TargetMapping）
- 解析抽象（RawRow、RawRowStream、RegionParseResult 及其子类）
- 领域事件 `FileParsedEvent`
- 3 个 Repository 接口
- 3 个防腐层网关接口（ExcelParser、ExpressionEvaluator、ConfigLoader）
- 错误码 `FileErrorCodes`
- 8 个领域服务（RegionStateMachine、KeyValueRegionParser、TableRegionParser、CanonicalModelBuilder、DataDeriver、DataValidator、TaskSplitter、SourceTemplateIdentifier）

**验收**：
- file-domain 不依赖任何外部库（仅 lombok + shared-domain + file-types）
- 所有聚合根实现 `validateInvariants()`
- 领域服务单元测试覆盖率 ≥ 80%

---

## T5: file-api 接口定义与 DTO

**优先级**：P0
**预估文件数**：14
**依赖**：T3

**范围**：
- 3 个 API 接口：`ParseApi`、`ParsedDataApi`、`ConfigApi`（用 `@HttpExchange`）
- 11 个请求/响应 DTO（ParseRequest/Response、SubTaskResponse、TaskErrorResponse、ParseTaskSummaryResponse、PagedDataResponse、PageInfoResponse、RowErrorResponse、ImportConfigRequest/Response、TemplateConfigResponse）
- 集成事件 DTO `FileParsedMessage`

**验收**：
- 所有 API 接口用 `@HttpExchange`/`@GetExchange`/`@PostExchange` 标记
- 不使用 GET/POST 之外的请求类型
- DTO 字段与领域对象通过 Converter 转换（Converter 在 adapter 层）

---

## T6: file-application 应用服务

**优先级**：P0
**预估文件数**：11
**依赖**：T4、T5

**范围**：
- 4 个应用服务：`ParseExcelAppService`、`QueryParsedDataAppService`、`QueryParseTaskAppService`、`ManageConfigAppService`
- Command/Query/Result DTO（ParseExcelCommand、ImportYamlCommand、PagedDataQuery、TaskSummaryQuery、ParseExcelResult、PagedDataResult、TaskSummaryResult、ImportConfigResult）

**验收**：
- `ParseExcelAppService.parseAndStore` 完整实现解析全流程（含事务边界）
- 事件通过 `EventBus` 注册（事务提交后异步发送）
- 错误处理符合 spec 4.6 策略

---

## T7: file-adapter Controller 与 Converter

**优先级**：P0
**预估文件数**：6
**依赖**：T5、T6

**范围**：
- 3 个 Controller：`ParseController`、`ParsedDataController`、`ConfigController`（实现 API 接口，标注 `@RestController`）
- 3 个 MapStruct Converter：`ParseControllerConverter`、`ParsedDataControllerConverter`、`ConfigControllerConverter`

**验收**：
- Controller 实现 file-api 中定义的接口
- 所有 DTO ↔ Command/Result 转换通过 MapStruct
- Adapter 不直接操作 Entity/DO，不编写业务逻辑

---

## T8: file-infrastructure 持久化层

**优先级**：P0
**预估文件数**：约 15
**依赖**：T4

**范围**：
- 4 个 Mapper：`ParseTaskMapper`、`SubTaskDataMapper`、`SubTaskRowMapper`、`TemplateConfigMapper`
- 4 个 DO 实体：`ParseTaskDO`、`SubTaskDataDO`、`SubTaskRowDO`、`TemplateConfigDO`
- 3 个 Repository 实现：`ParseTaskRepositoryImpl`、`SubTaskDataRepositoryImpl`、`TemplateConfigRepositoryImpl`
- 3 个 DO Converter（MapStruct）：`ParseTaskDOConverter`、`SubTaskDataDOConverter`、`TemplateConfigDOConverter`
- 1 个 TypeHandler：`JsonTypeHandler`（处理 JSONB）
- 4 张表的 schema-pg.sql

**验收**：
- 4 张表能在 PostgreSQL 创建成功
- Repository 实现标准 load/save/delete/loadAll
- `SubTaskDataRepositoryImpl.findPagedRows` 支持分页查询
- `TemplateConfigRepositoryImpl` 支持 Caffeine + Redisson 双层缓存

---

## T9: file-infrastructure Fesod 解析器实现

**优先级**：P0
**预估文件数**：3
**依赖**：T4

**范围**：
- `FesodExcelReader`：用 Fesod 流式读取 Excel，逐行回调
- `ExcelParserImpl`：实现 `ExcelParser` SPI，用阻塞队列桥接推/拉模式
- `QueuedRawRowStream`：实现 `RawRowStream`，从阻塞队列消费

**验收**：
- 流式解析不一次性载入所有行（内存占用与文件大小无关）
- 虚拟线程并发执行 Fesod 推数据 + 状态机拉数据
- 端到端集成测试通过（用真实 Excel 文件）

---

## T10: file-infrastructure Aviator 表达式引擎实现

**优先级**：P0
**预估文件数**：1
**依赖**：T4

**范围**：
- `AviatorExpressionEvaluator`：实现 `ExpressionEvaluator` SPI
- 表达式编译缓存
- 安全配置（限制 Feature Set，禁用反射）

**验收**：
- Aviator 表达式正确求值
- 编译缓存生效（同一表达式只编译一次）
- 安全配置禁用了反射调用

---

## T11: file-infrastructure 配置加载与事件转换

**优先级**：P0
**预估文件数**：4
**依赖**：T4、T1

**范围**：
- `YamlConfigLoader`：实现 `ConfigLoader` SPI，用 SnakeYAML 解析 YAML
- `ClasspathConfigBootstrap`：启动时从 classpath 加载默认 YAML（仅 DB 无配置时）
- `FileParsedEventConverter`：实现 `IntegrationEventConverter<FileParsedEvent>`，转换为 `FileParsedMessage`（MapStruct）
- `LocalFileStorage`：本地文件存储 + 短期保留

**验收**：
- YAML 配置能正确解析为 `TemplateConfig` 领域对象
- 启动时 classpath 加载不影响 DB 中已存在的配置
- `FileParsedEventConverter` 正确实现领域事件到集成事件的转换

---

## T12: 测试与端到端集成

**优先级**：P0
**预估文件数**：约 15
**依赖**：T1-T11

**范围**：
- file-domain 单元测试：RegionStateMachineTest、KeyValueRegionParserTest、TableRegionParserTest、CanonicalModelBuilderTest、DataDeriverTest、DataValidatorTest、TaskSplitterTest、ParseTaskTest、SubTaskDataTest、TemplateConfigTest、FileParsedEventTest
- file-application 集成测试：ParseExcelAppServiceTest、QueryParsedDataAppServiceTest、ManageConfigAppServiceTest
- file-starter 端到端集成测试：FileServiceIntegrationTest（H2 PostgreSQL 兼容模式）
- 测试资源：H2 schema、测试用 Excel 文件、测试用 YAML 配置

**验收**：
- 单元测试覆盖率 ≥ 70%
- 端到端集成测试覆盖完整流程：上传 → 解析 → 拆分 → 校验 → 持久化 → 事件 → 拉取
- `mvn clean install` 构建成功，所有测试通过

---

## 任务依赖关系图

```
T1 (shared-event-starter 重构)
   │
   ▼
T2 (模块脚手架) ──────────────────────────────────┐
   │                                              │
   ▼                                              │
T3 (file-types) ─────┬───── T5 (file-api) ────────┤
                     │                            │
                     ▼                            ▼
              T4 (file-domain) ──┬─── T6 (application)
                                 │       │
                                 ├─── T7 (adapter) ◀── T6
                                 │
                                 ├─── T8 (持久化)
                                 ├─── T9 (Fesod)
                                 ├─── T10 (Aviator)
                                 └─── T11 (配置+事件转换) ◀── T1
                                          │
                                          ▼
                                    T12 (测试)
```

---

## 关键里程碑

| 里程碑 | 完成任务 | 验收点 |
|--------|---------|--------|
| M1: 基础设施就绪 | T1, T2 | 项目能构建，shared-event-starter 重构完成 |
| M2: 领域模型就绪 | T3, T4, T5 | 领域层完整，单元测试通过 |
| M3: 应用层就绪 | T6, T7 | 应用服务和 Controller 完成，DTO 转换正确 |
| M4: 基础设施就绪 | T8, T9, T10, T11 | 持久化、Fesod、Aviator、配置加载全部就绪 |
| M5: Phase 1 完成 | T12 | 端到端集成测试通过，可交付 |

