# business-core-kernel 重新分包设计

**日期**: 2026-07-22
**状态**: 设计修订 v2(基于用户对 kernel 扩展性设计的澄清)
**分支**: 待创建

## 1. 背景与目标

### 1.1 现状

`business-core-kernel` 模块当前承载两类职责:

1. **业务领域模型**: `BusinessApplication`、`BusinessForm`、`BusinessBatch` 三个聚合根及其直接关联的值对象、事件、Repository 接口。
2. **流程编排引擎**: 包含三层能力——
   - **引擎核心**: `StepPipelineExecutor` 管道执行器、`StepActionHandler`/`StepExtensionAction`/`BusinessFactExtractor` 三套 SPI、Registry 注册表、条件求值网关、配置值对象、`BusinessMetaContext` 共享数据契约。
   - **公共步骤实现**: kernel 开箱提供的 5 个 `StepActionHandler`(表单解析×3、审批提交、材料准备)、3 个 `StepExtensionAction` 抽象基类(游标分页、JSON 流式摄入、流式查询)、`MaterialRuleEngine` 支撑领域服务、`BusinessFormAppService`/`MaterialAppService` 公共步骤应用服务、外部回调监听器。
   - **集成网关**: `ApprovalIntegrationGateway`、`FileIntegrationGateway` 等防腐层接口,供公共步骤调用外部服务(approval-service / file-service)。

两类代码混在同一包路径下,缺乏清晰的边界划分。

### 1.2 kernel 的扩展性设计

kernel 模块的核心价值在于"三层可插拔"架构:

```
┌─────────────────────────────────────────────────────────────┐
│  各业务服务(如 annuity-service)                              │
│  ├── 自定义业务聚合根(继承 BusinessApplication 扩展业务字段)   │
│  ├── 实现 BusinessFactExtractor(提取业务事实注入 extensionFacts)│
│  └── 实现明细层 StepExtensionAction(业务特定的明细处理)        │
├─────────────────────────────────────────────────────────────┤
│  kernel 公共步骤实现(common-step)                             │
│  ├── 计划层材料准备 Handler(复用,无需各业务重写)               │
│  ├── 表单解析 Handler ×3(复用)                                │
│  ├── 审批提交 Handler(复用)                                   │
│  ├── 明细摄入/分页/流式查询扩展基类(业务服务继承后实现明细逻辑) │
│  └── 集成网关:屏蔽 approval-service / file-service            │
├─────────────────────────────────────────────────────────────┤
│  kernel 引擎核心(engine-core)                                │
│  ├── StepPipelineExecutor:四插槽管道(preValidations/         │
│  │   mainProcessor/detailProcessors/sideEffects)              │
│  ├── 三套 SPI + Registry 自动收集策略                          │
│  ├── ConditionEvaluationGateway:SpEL 条件求值                 │
│  └── BusinessConfigGateway:路由与步骤配置查询                  │
└─────────────────────────────────────────────────────────────┘
```

**关键认知**: 材料处理、审批、表单处理不是"业务领域模型",而是 kernel 提供的**公共步骤实现**。它们放在 kernel 中是为了让各业务服务直接复用,避免重复实现。集成网关是公共步骤调用外部服务的防腐层接口,同样属于引擎的一部分。

### 1.3 目标

在**不拆分 Maven 模块**的前提下,通过 **Java 包级分包**将 kernel 的领域层、应用层、基础设施层内部按 `business`(业务领域模型)和 `engine`(流程编排引擎)两个子包重新组织。

### 1.4 非目标

- 不拆分 Maven 模块(保持现有 7 个模块结构不变)
- 不修改 `StepPipelineExecutor` 的实现逻辑(经检验职责纯粹)
- 不重构 `BusinessApplication` 聚合根的充血模型(保留 `transit`/`recordPipelineExecution` 等流程行为)
- 不拆分 `BusinessConfigGateway`(其所有方法都服务于引擎路由或公共步骤配置,统一归入 engine)
- 不调整 `BusinessMetaContext` 的字段结构

## 2. 设计决策

| 决策项 | 选择 | 理由 |
|--------|------|------|
| 分包粒度 | 包级分包(不拆分 Maven 模块) | 改动最小,保持现有依赖关系不变 |
| 子包命名 | `business` / `engine` | `business` 表示业务领域模型(3 聚合根),`engine` 表示流程编排引擎(含核心+公共步骤+集成网关) |
| `BusinessApplication` 归属 | `business` | 业务受理过程全生命周期聚合根,业务数据由各业务服务自定义聚合根承载 |
| `MaterialRuleEngine` 归属 | `engine` | 公共步骤(材料准备)的支撑领域服务,非业务模型 |
| 集成网关归属 | `engine` | `ApprovalIntegrationGateway`/`FileIntegrationGateway` 是公共步骤调用外部服务的防腐层,属于引擎 |
| `BusinessConfigGateway` 是否拆分 | 不拆分 | 所有方法都服务于引擎(路由配置 + 公共步骤配置),统一归入 `engine.gateway` |
| `BusinessOrchestrationAppService` 重命名 | `FlowOrchestrationService` | 该类是引擎入口编排器,非业务应用服务;重命名后语义更清晰 |
| `StepPipelineExecutor` 是否修改 | 不修改 | 职责纯粹,仅移动包路径 |

## 3. 详细设计

### 3.1 domain 层分包

```
com.example.core.domain
├── business/                              # 业务领域模型(32 类)
│   ├── aggregate/
│   │   ├── root/                          # 聚合根
│   │   │   ├── BusinessApplication        # 业务申请聚合根
│   │   │   ├── BusinessForm               # 业务表单聚合根
│   │   │   └── BusinessBatch              # 业务批次聚合根
│   │   └── valueobject/                  # 业务值对象与枚举
│   │       ├── BusinessContext            # 业务上下文(客户/产品/计划/业务类型)
│   │       ├── OperatorInfo              # 操作人信息
│   │       ├── BusinessFile              # 业务文件附件
│   │       ├── MaterialItem              # 业务材料(含 isSatisfied 校验)
│   │       ├── MaterialConditionContext   # 材料条件评估上下文(函数式接口)
│   │       ├── BusinessExtension         # 业务扩展信息接口(多态值对象)
│   │       ├── ParsedPlanResult          # 表单解析拆分结果
│   │       ├── ValidationResult          # 校验结果值对象接口
│   │       ├── reference/
│   │       │   ├── BusinessFormRef       # 业务表单引用
│   │       │   └── PlanBizApplicationRef # 计划申请引用
│   │       ├── business/                 # 业务枚举
│   │       │   ├── AccountManager
│   │       │   ├── AnnuityChannel
│   │       │   ├── BusinessLevel
│   │       │   ├── BusinessType
│   │       │   └── OperationModel
│   │       └── enums/                    # 状态/材料/校验枚举
│   │           ├── status/               # ApplicationStatus, BatchStatus, FormStatus
│   │           ├── material/             # RequirementType
│   │           └── validate/             # ValidationType
│   ├── event/                             # 业务领域事件
│   │   ├── ApplicationSpawnedEvent       # 申请单孵化(触发引擎点火)
│   │   ├── FormUploadedEvent             # 表单上传
│   │   └── BatchStatusChangedEvent       # 批次状态变更
│   ├── repository/                        # 业务仓储接口
│   │   ├── ApplicationRepository
│   │   ├── FormRepository
│   │   └── BatchRepository
│   └── errorcode/                         # 业务错误码
│       └── CoreDomainErrorCode           # INVALID_STATUS/INVALID_DATA/INVALID_OPERATION
│
├── engine/                                # 流程编排引擎(26 类)
│   ├── spi/                               # 引擎 SPI 接口
│   │   ├── StepActionHandler             # 步骤主处理器 SPI
│   │   ├── StepExtensionAction           # 步骤扩展动作 SPI
│   │   └── BusinessFactExtractor         # 业务事实提取器 SPI(各业务服务实现)
│   ├── service/                           # 引擎领域服务
│   │   ├── registry/                     # 策略注册器
│   │   │   ├── AbstractStrategyRegistry  # 泛型基类
│   │   │   ├── StepActionHandlerRegistry
│   │   │   ├── ExtensionActionRegistry
│   │   │   └── BusinessFactExtractorRegistry
│   │   └── step/                         # 公共步骤支撑服务
│   │       └── MaterialRuleEngine        # 材料规则引擎(供 PlanMaterialPreparationHandler 使用)
│   ├── gateway/                           # 引擎网关接口
│   │   ├── ConditionEvaluationGateway    # 条件求值网关(引擎核心)
│   │   ├── BusinessConfigGateway         # 配置查询网关(路由+步骤配置)
│   │   ├── ApprovalIntegrationGateway    # 审批集成网关(公共步骤用)
│   │   └── FileIntegrationGateway        # 文件集成网关(公共步骤用)
│   ├── aggregate/
│   │   └── valueobject/                  # 引擎值对象与枚举
│   │       ├── BusinessMetaContext       # 共享数据契约(业务维度 + extensionFacts)
│   │       ├── config/                   # 引擎配置值对象
│   │       │   ├── StepRouteConfig       # 步骤路由配置
│   │       │   ├── StepExtensionConfig   # 扩展动作配置
│   │       │   ├── ExtractorConfig       # 事实提取器配置
│   │       │   ├── FormParsingConfig     # 表单解析配置
│   │       │   └── MaterialRuleConfig    # 材料规则配置
│   │       ├── PipelineExecutionResult   # 管道执行结果
│   │       ├── ExtensionExecutionResult  # 扩展动作执行结果
│   │       ├── StepExecutionStatus       # 步骤执行状态枚举
│   │       └── enums/
│   │           └── workflow/
│   │               └── ApplicationFlowStep # 流程节点定义
│   ├── event/                             # 引擎领域事件
│   │   ├── StepEnteredEvent              # 步骤进入事件
│   │   └── PipelineExecutedEvent         # 管道执行事件
│   └── annotation/                        # 引擎注解
│       └── DomainService                 # 领域服务标记注解
```

**关键决策说明**:

1. **`business` 包只含 3 个聚合根及其直接关联**: `BusinessApplication`/`BusinessForm`/`BusinessBatch` 是纯业务领域模型。`MaterialItem` 虽含 `isSatisfied` 校验逻辑,但它是业务材料的值对象,归入 business。

2. **`MaterialRuleEngine` 归入 `engine.service.step`**: 它是公共步骤 `PlanMaterialPreparationHandler` 的支撑领域服务,解析 `MaterialRuleConfig` 生成材料蓝图。不是业务模型。

3. **集成网关归入 `engine.gateway`**: `ApprovalIntegrationGateway`/`FileIntegrationGateway` 是公共步骤调用外部服务的防腐层接口,属于引擎的一部分。

4. **`BusinessConfigGateway` 不拆分**: 其所有方法(`getNextStep`/`getExtractorConfig`/`getMaterialRules`/`getFormParseRule`/`getFormParsingConfig`/`getIngestion`)都服务于引擎路由或公共步骤配置,统一归入 `engine.gateway`。

5. **`StepEnteredEvent`/`PipelineExecutedEvent` 归入 `engine.event`**: 虽由 `BusinessApplication` 的 `transit`/`recordPipelineExecution` 注册(充血模型混合),但语义上是引擎事件(步骤流转、管道执行)。

6. **`ApplicationSpawnedEvent` 归入 `business.event`**: 业务领域事件(申请单孵化),由 `BusinessApplication.createFromForm` 注册,触发引擎点火。

7. **`CoreDomainErrorCode` 归入 `business.errorcode`**: 主要被业务聚合根使用(`BusinessApplication` 抛 `DomainException` 时引用)。

### 3.2 application 层分包

application 层全部属于 `engine`(无 business 内容,因为 `BusinessFormAppService`/`MaterialAppService` 是公共步骤应用服务)。

```
com.example.core.application
└── engine/                                # 流程编排引擎应用层(17 类)
    ├── service/                           # 引擎入口编排服务
    │   └── FlowOrchestrationService       # 重命名自 BusinessOrchestrationAppService
    ├── pipeline/                          # 管道执行器
    │   └── StepPipelineExecutor           # 四插槽管道执行核心
    ├── step/                              # 公共步骤实现
    │   ├── handler/                       # 步骤主处理器(5 个)
    │   │   ├── FormParsingHandler         # 表单解析(旧版)
    │   │   ├── DefaultFormParsingHandler  # 默认表单异步解析
    │   │   ├── FileServiceParseHandler    # 文件服务解析
    │   │   ├── ApprovalSubmissionHandler  # 审批提交
    │   │   └── PlanMaterialPreparationHandler # 计划层材料准备
    │   ├── extension/                     # 扩展动作基类(3 个)
    │   │   ├── AbstractJsonStreamIngestionAction # JSON 流式摄入明细落库
    │   │   ├── AbstractStreamingDetailAction     # 流式查询明细动作
    │   │   └── AbstractCursorPagingDetailAction  # 游标分页明细动作
    │   └── service/                       # 公共步骤应用服务
    │       ├── BusinessFormAppService     # 表单应用服务(上传确认→解析→裂变)
    │       └── MaterialAppService         # 材料应用服务(绑定单个/打包材料)
    ├── listener/                          # 监听器(4 个)
    │   ├── ApplicationSpawnedListener     # 引擎点火:监听 ApplicationSpawnedEvent
    │   ├── StepAutoAdvanceListener        # 引擎自动流转:监听 StepEnteredEvent
    │   ├── ApprovalResultEventListener    # 审批结果回调:监听审批集成事件
    │   └── FileParsedEventListener        # 文件解析回调:监听解析完成事件
    └── errorcode/                         # 引擎应用层错误码
        └── CoreAppErrorCode               # STEP_HANDLER_FAILED 等
```

**关键决策说明**:

1. **`BusinessOrchestrationAppService` 重命名为 `FlowOrchestrationService`**: 该类本质是流程编排引擎的入口编排器,非业务应用服务。重命名后语义更清晰。仅类名和包路径变化,内部逻辑不变。

2. **公共步骤统一归入 `engine.step`**: `handler`/`extension`/`service` 三个子包分别承载步骤主处理器、扩展动作基类、公共步骤应用服务。这些是 kernel 提供给各业务服务复用的开箱实现。

3. **监听器统一归入 `engine.listener`**: `ApplicationSpawnedListener`/`StepAutoAdvanceListener` 是引擎自驱动机制;`ApprovalResultEventListener`/`FileParsedEventListener` 是外部集成回调入口。均属于引擎应用层。

4. **`StepPipelineExecutor` 保持不变**: 经检验职责纯粹(按序执行扩展动作链 + 条件过滤 + 同步/异步派发 + 上下文突变 + 异常隔离),仅移动包路径。

### 3.3 infrastructure 层分包

```
com.example.core.infrastructure
└── engine/                                # 流程编排引擎基础设施(7 类)
    ├── evaluation/                        # 条件求值实现
    │   └── SpelConditionEvaluationGateway # Spring SpEL 实现
    ├── configuration/                     # 引擎配置
    │   ├── DomainServiceConfiguration    # @DomainService 组件扫描
    │   └── LibJacksonModule             # Jackson 模块(注册 BusinessExtensionMixIn)
    ├── gateway/                           # 集成网关实现
    │   ├── ApprovalServiceIntegrationGateway # approval-service 集成
    │   └── FileServiceIntegrationGateway     # file-service 集成
    ├── json/                              # JSON 配置
    │   └── BusinessExtensionMixIn        # BusinessExtension 多态序列化 MixIn
    └── event/                             # 事件模拟
        └── IntegrationEventSimulator     # 演示环境集成事件模拟器
```

**关键决策说明**:

1. **infrastructure 层全部属于 `engine`**: 所有基础设施实现都为引擎服务(条件求值、配置、集成网关、事件模拟)。`BusinessExtensionMixIn` 虽为业务扩展值对象做序列化,但它是引擎 Jackson 配置的一部分,归入 `engine.json`。

2. **`LibJacksonModule` 与 `BusinessExtensionMixIn` 分置**: `LibJacksonModule` 是 Jackson 模块配置(归入 `engine.configuration`),`BusinessExtensionMixIn` 是多态序列化 MixIn(归入 `engine.json`)。两者通过引用关联。

### 3.4 FlowOrchestrationService 重命名说明

`BusinessOrchestrationAppService` 重命名为 `FlowOrchestrationService`,仅类名和包路径变化,内部逻辑不变。

**不修改的点**:
- `advanceStep` / `advanceByFileTaskId` / `advanceByApprovalResult` 方法签名保持不变
- `buildFullConfigContext` 两阶段上下文构建逻辑保持不变
- 依赖注入的 Bean 不变

**关于 `advanceByApprovalResult` 的硬编码常量**: 当前 `APPROVED`/`REJECTED`/`WITHDRAWN` 字符串常量保留,作为 follow-up 优化项(抽取为策略对象),不在本次重构范围内。

### 3.5 BusinessOrchestrationAppService 与 StepPipelineExecutor 设计检查结论

**`StepPipelineExecutor`**: 职责纯粹,无需修改。

该类是微编排核心,按序执行扩展动作链:
1. 从 `ExtensionActionRegistry` 获取该步骤配置的所有扩展动作
2. 按 `order` 排序,通过 `ConditionEvaluationGateway` 评估 SpEL `condition` 过滤
3. 同步动作直接执行,异步动作通过 `TaskExecutor` 派发
4. 处理 `mutations`(上下文突变)合并到 `BusinessMetaContext`
5. 致命失败(`isCritical=true` 且异常)中断管道

通过 SPI 与业务解耦,不包含任何业务逻辑。

**`BusinessOrchestrationAppService`(→ `FlowOrchestrationService`)**: 作为引擎入口收口合理,仅需重命名。

该类本质是流程编排引擎入口,同时承担外部回调入口(`advanceByFileTaskId`/`advanceByApprovalResult`):
- `advanceStep`: 引擎主流程(两阶段构建上下文→前置校验→主处理器→明细处理→副作用→流转→持久化发布)
- `advanceByFileTaskId`: 文件解析完成后的回调入口
- `advanceByApprovalResult`: 审批结果回调入口

外部回调入口是引擎的必要组成部分(外部服务通过回调驱动引擎推进),不是"业务逻辑"。重命名为 `FlowOrchestrationService` 即可明确其引擎入口定位。

## 4. 影响范围

### 4.1 文件移动统计

| 层级 | 移动到 business | 移动到 engine | 新增 | 合计 |
|------|----------------|--------------|------|------|
| domain | ~32 | ~26 | 0 | ~58 |
| application | 0 | ~17 | 0 | ~17 |
| infrastructure | 0 | ~7 | 0 | ~7 |
| **合计** | **~32** | **~50** | **0** | **~82** |

**注**: 无新增文件(不拆分 `BusinessConfigGateway`),仅 1 个文件重命名(`BusinessOrchestrationAppService` → `FlowOrchestrationService`)。

### 4.2 外部模块影响

| 模块 | 影响内容 |
|------|----------|
| `annuity-service` | 需同步更新所有 import kernel 类的包路径 |
| `file-service` | 无直接影响(不依赖 kernel) |
| `approval-service` | 无直接影响(不依赖 kernel) |

### 4.3 测试影响

- kernel 内部测试文件需同步更新 import
- `annuity-service` 的测试文件需同步更新 import

## 5. 验证标准

1. **编译通过**: 全量 `mvn clean compile -DskipTests` BUILD SUCCESS
2. **测试通过**: kernel 测试 + annuity-service 47 测试全部通过
3. **包结构验证**: `business` 和 `engine` 两个子包在 domain 层均存在;`engine` 子包在 application/infrastructure 层均存在
4. **无残留**: 原 `com.example.core.domain.aggregate`、`com.example.core.domain.event` 等旧包路径下无遗留文件(除 `package-info.java`)

## 6. 包间依赖关系说明

由于采用包级分包(不拆分 Maven 模块),`business` 和 `engine` 两个子包之间存在编译期依赖,这是设计允许的:

| 依赖方向 | 示例 | 说明 |
|----------|------|------|
| `business` → `engine` | `BusinessApplication` 依赖 `PipelineExecutionResult`、`StepEnteredEvent`、`PipelineExecutedEvent`、`BusinessMetaContext`、`ApplicationFlowStep` | 充血模型聚合根内聚流程行为,需引用引擎值对象和事件 |
| `engine` → `business` | `StepActionHandler`/`StepExtensionAction` SPI 依赖 `BusinessApplication` | 引擎 SPI 以业务聚合根为参数,实现编排 |
| `engine` → `engine` | `StepPipelineExecutor` 依赖 `ExtensionActionRegistry`、`ConditionEvaluationGateway`;`PlanMaterialPreparationHandler` 依赖 `MaterialRuleEngine`、`BusinessConfigGateway` | 引擎内部依赖 |
| `business` → `business` | `BusinessApplication` 依赖 `MaterialItem`、`BusinessContext` | 业务内部依赖 |

**关键约束**: 包级分包不引入强制隔离,仅通过包结构表达职责边界。若未来需要严格隔离(如引擎独立复用),可升级为模块级拆分。

## 7. Follow-up 项(不在本次范围)

- `FlowOrchestrationService.advanceByApprovalResult` 的 `APPROVED`/`REJECTED`/`WITHDRAWN` 字符串常量抽取为策略对象
- `BusinessApplication.recordPipelineExecution` 考虑抽取为独立的 `PipelineExecutionTracker` 领域服务
- `buildFullConfigContext` 两阶段上下文构建考虑抽取为 `ContextAssembler` 领域服务
- `BusinessFormAppService`/`MaterialAppService` 可考虑重命名以明确其"公共步骤应用服务"定位
