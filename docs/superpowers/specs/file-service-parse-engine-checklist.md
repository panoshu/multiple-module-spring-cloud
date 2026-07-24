# File Service Phase 1 Spec 自检清单

| 项 | 值 |
|----|----|
| 关联 spec | [file-service-parse-engine.md](./file-service-parse-engine.md) |
| 检查日期 | 2026-07-18 |
| 检查者 | Trae AI |

---

## 1. 占位符检查

- [x] 无 TODO / TBD / FIXME / XXX 标记
- [x] 无"待补充"、"待确定"等占位文本
- [x] 无 `???`、`<placeholder>` 等明显占位符
- [x] 所有代码示例方法体要么完整，要么用 `...` 表示省略且省略位置不涉及核心逻辑

## 2. 一致性检查

### 2.1 命名一致性
- [x] `FileTaskId`、`SubTaskId`、`TemplateConfigId`、`BizType`、`TemplateCode` 在全文统一
- [x] 聚合根命名统一：`ParseTask`、`SubTaskData`、`TemplateConfig`
- [x] 领域事件命名：`FileParsedEvent`（domain 层）
- [x] 集成事件命名：`FileParsedMessage`（api 层）
- [x] SPI 命名：`ExcelParser`、`ExpressionEvaluator`、`ConfigLoader`、`IntegrationEventConverter`
- [x] 包路径前缀统一为 `com.example.file`

### 2.2 依赖关系一致性
- [x] file-domain 依赖：shared-domain + file-types + lombok（无外部库）
- [x] Fesod 仅出现在 file-infrastructure
- [x] Aviator 仅出现在 file-infrastructure
- [x] API 接口在 file-api 定义，Controller 在 file-adapter 实现
- [x] 领域事件在 file-domain，集成事件在 file-api

### 2.3 流程一致性
- [x] 解析流程顺序：parse → derive → split → per-sub-task validate → persist → event（spec 4.2 与 6.1 一致）
- [x] 事件发布时机：事务提交后异步发送（spec 4.2 与 9.5 一致）
- [x] errorPolicy 行为：spec 4.6 与 6.9 一致

### 2.4 数据一致性
- [x] 数据库表数：4 张（file_parse_task、file_sub_task、file_sub_task_row、file_template_config）
- [x] 聚合根数：3 个（ParseTask、SubTaskData、TemplateConfig）
- [x] API 数：3 组（ParseApi、ParsedDataApi、ConfigApi）
- [x] 应用服务数：4 个（ParseExcel、QueryParsedData、QueryParseTask、ManageConfig）

## 3. 范围检查

### 3.1 Phase 1 范围内
- [x] 模块脚手架
- [x] 领域模型（3 聚合根）
- [x] 配置管理（DB + YAML 加载）
- [x] 解析引擎（Fesod + 区域状态机）
- [x] 校验/计算引擎（Aviator）
- [x] 拆分引擎
- [x] 数据持久化
- [x] 事件发布
- [x] 分页拉取 API
- [x] shared-event-starter 重构

### 3.2 Phase 1 范围外（应明确排除）
- [x] 目标 Excel 生成（Phase 2）
- [x] 配置管理 REST API 增删改查（Phase 2，Phase 1 只提供 YAML 导入 + 查询）
- [x] TTL 自动清理增强（Phase 2）
- [x] API 鉴权与限流（Phase 2）
- [x] 异步任务队列（YAGNI，未引入）

## 3.3 未定义行为检查
- [x] 拆分键为空时：明确三种策略（ERROR/IGNORE/DEFAULT）
- [x] 区域 trigger 未命中：明确跳过该区域
- [x] dataEnd 未配置：默认读到行流结束
- [x] 表格列为空：值为 null，校验阶段处理
- [x] 单元格类型不匹配：记录行级错误，按 errorPolicy 决定
- [x] TTL 过期：标记 EXPIRED，返回 410 Gone
- [x] 子任务 INVALID：返回 200 + 错误明细

## 4. 歧义检查

- [x] "派生"与"校验"的执行顺序明确：派生在拆分前，校验在拆分后
- [x] "领域事件"与"集成事件"职责明确：领域事件落库（本服务可见），集成事件发 MQ（跨服务）
- [x] "本地分发"与"远程分发"明确：本地发领域事件，远程发集成事件
- [x] "YAML 与 DB 关系"明确：YAML 是初始来源，DB 是权威来源
- [x] "同步执行"明确：HTTP 请求线程内完成，无异步任务队列
- [x] "事务边界"明确：T1 创建任务、T2 解析（无 DB 写）、T3-Tn 子任务独立事务、Tn+1 主任务最终状态

## 5. 可验证性检查

每个需求都有可验证的验收标准：
- [x] 功能验收：12 项可测试功能点
- [x] 架构验收：10 项架构约束
- [x] 质量验收：4 项质量指标

## 6. 风险与开放问题

- [x] 已识别风险 5 项，每项有缓解措施
- [x] 开放问题 4 项，每项有当前决策

## 7. 自检结论

✅ Spec 通过自检，可以提交用户审阅。

**注意点**：
1. `shared-event-starter` 重构是 Phase 1 的前置任务，会影响其他使用 EventBus 的服务，需在实现计划中优先处理
2. 向后兼容设计已明确：未实现 `IntegrationEventConverter` 的旧事件降级为直接发送领域事件
3. 测试库使用 H2 PostgreSQL 兼容模式（按用户偏好），部分索引语法差异需在测试 schema 中处理

