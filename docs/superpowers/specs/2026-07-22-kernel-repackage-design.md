# business-core-kernel 重新分包设计

**日期**: 2026-07-22
**状态**: 设计确认,待编写实施计划
**分支**: 待创建

## 1. 背景与目标

### 1.1 现状

`business-core-kernel` 模块当前承载两类职责:
1. **业务领域模型**: `BusinessApplication` 聚合根、`BusinessForm`/`BusinessBatch` 聚合根、材料规则引擎、表单/审批/文件集成网关、业务应用服务等。
2. **流程编排引擎**: `StepPipelineExecutor` 管道执行器、`StepActionHandler`/`StepExtensionAction`/`BusinessFactExtractor` 三套 SPI、Registry 注册表、`StepRouteConfig`/`StepExtensionConfig` 配置值对象、条件求值网关、扩展动作抽象基类等。

两类代码混在同一包路径下(`com.example.core.domain.*`、`com.example.core.application.*`、`com.example.core.infrastructure.*`),缺乏清晰的边界划分,导致:
- 阅读时难以快速区分某类属于业务模型还是流程引擎
- 流程引擎的复用性被业务模型代码掩盖
- 后续扩展时容易破坏职责边界

### 1.2 目标

在**不拆分 Maven 模块**的前提下,通过 **Java 包级分包**将 `business-core-kernel` 的领域层、应用层、基础设施层内部按 `business`(业务领域模型)和 `engine`(流程编排引擎)两个子包重新组织,使两类职责的边界在包结构上清晰可见。

### 1.3 非目标

- 不拆分 Maven 模块(保持现有 7 个模块结构不变)
- 不修改 `StepPipelineExecutor` 的实现逻辑(经检验职责纯粹)
- 不重构 `BusinessApplication` 聚合根的充血模型(保留 `transit`/`recordPipelineExecution` 等流程行为在聚合根内)
- 不调整 `BusinessMetaContext` 的字段结构(保留作为业务维度与引擎事实字典的共享数据契约)

## 2. 设计决策

| 决策项 | 选择 | 理由 |
|--------|------|------|
| 分包粒度 | 包级分包(不拆分 Maven 模块) | 改动最小,保持现有依赖关系不变 |
| 子包命名 | `business` / `engine` | 语义清晰,`business` 表示业务领域模型,`engine` 表示流程编排引擎 |
| 混合职责处理 | 最小拆分 | 仅拆分明确混合的 `BusinessConfigGateway`,其余混合文件按主要职责归入对应包 |
| `BusinessApplication` 归属 | `business` | 用户确认: 该聚合根表示业务受理过程的全生命周期,业务数据由各业务服务自定义聚合根承载 |
| `BusinessMetaContext` 归属 | `engine` | 作为流程引擎与业务的数据契约,`extensionFacts` 字段由引擎的 `BusinessFactExtractor` 填充 |

## 3. 详细设计

### 3.1 domain 层分包

```
com.example.core.domain
├── business/                              # 业务领域模型
│   ├── aggregate/
│   │   ├── root/                          # BusinessApplication, BusinessForm, BusinessBatch
│   │   └── valueobject/                  # 业务值对象与枚举
│   │       ├── BusinessContext
│   │       ├── BusinessExtension
│   │       ├── BusinessFile
│   │       ├── OperatorInfo
│   │       ├── ParsedPlanResult
│   │       ├── MaterialItem
│   │       ├── MaterialConditionContext
│   │       ├── MaterialRuleConfig         # 材料规则配置(业务规则)
│   │       ├── FormParsingConfig          # 表单解析配置(业务规则)
│   │       ├── BusinessType
│   │       ├── BusinessLevel
│   │       ├── OperationModel
│   │       ├── AnnuityChannel
│   │       ├── AccountManager
│   │       ├── PlanBizApplicationRef
│   │       ├── BusinessFormRef
│   │       ├── ApplicationStatus
│   │       ├── BatchStatus
│   │       ├── FormStatus
│   │       └── RequirementType
│   ├── event/                             # 业务领域事件
│   │   ├── ApplicationSpawnedEvent
│   │   ├── FormUploadedEvent
│   │   └── BatchStatusChangedEvent
│   ├── gateway/                           # 业务集成网关
│   │   ├── ApprovalIntegrationGateway
│   │   ├── FileIntegrationGateway
│   │   └── BusinessRuleGateway            # 新增: 拆分自 BusinessConfigGateway 的业务规则查询
│   ├── repository/                        # 业务仓储
│   │   ├── ApplicationRepository
│   │   ├── FormRepository
│   │   └── BatchRepository
│   ├── service/                           # 业务领域服务
│   │   └── MaterialRuleEngine
│   └── errorcode/                         # 业务错误码(如有)
│
├── engine/                                # 流程编排引擎
│   ├── aggregate/
│   │   └── valueobject/                  # 引擎值对象与枚举
│   │       ├── StepRouteConfig            # 步骤路由配置
│   │       ├── StepExtensionConfig        # 扩展动作配置
│   │       ├── ExtractorConfig            # 事实提取器配置
│   │       ├── PipelineExecutionResult    # 管道执行结果
│   │       ├── ExtensionExecutionResult   # 扩展动作执行结果
│   │       ├── ValidationResult           # 校验结果
│   │       ├── StepExecutionStatus        # 步骤执行状态
│   │       ├── ValidationType             # 校验类型
│   │       ├── ApplicationFlowStep        # 流程节点定义
│   │       └── BusinessMetaContext        # 共享数据契约(业务维度 + extensionFacts)
│   ├── event/                             # 引擎领域事件
│   │   ├── StepEnteredEvent
│   │   └── PipelineExecutedEvent
│   ├── gateway/                           # 引擎网关
│   │   ├── ConditionEvaluationGateway     # 条件求值网关
│   │   └── RouteConfigGateway             # 新增: 拆分自 BusinessConfigGateway 的路由配置查询
│   ├── spi/                               # 引擎 SPI 接口
│   │   ├── StepActionHandler
│   │   ├── StepExtensionAction
│   │   └── BusinessFactExtractor
│   ├── service/                           # 引擎领域服务
│   │   └── registry/
│   │       ├── AbstractStrategyRegistry
│   │       ├── StepActionHandlerRegistry
│   │       ├── ExtensionActionRegistry
│   │       └── BusinessFactExtractorRegistry
│   ├── annotation/                        # 引擎注解
│   │   └── DomainService
│   └── errorcode/                         # 引擎错误码
│       └── CoreDomainErrorCode
```

**关键决策说明**:

1. **`BusinessApplication` 归入 `business.aggregate.root`**: 用户确认该聚合根表示业务受理过程的全生命周期,虽含 `transit`/`recordPipelineExecution` 等流程行为,但属于充血模型的合理混合,主体是业务领域模型。

2. **`BusinessMetaContext` 归入 `engine.aggregate.valueobject`**: 作为流程引擎与业务的数据契约,`extensionFacts` 字段由引擎的 `BusinessFactExtractor` 填充,供 SpEL 条件求值和扩展动作读取。放引擎侧更贴近其核心使用场景。

3. **`BusinessConfigGateway` 拆分为两个接口**:
   - `BusinessRuleGateway`(business 侧): 查询材料规则配置 `getMaterialRules`、表单解析配置 `getFormParsingConfig`/`getFormParseRule`、落库处理规则 `getIngestion`。
   - `RouteConfigGateway`(engine 侧): 查询下一步路由配置 `getNextStep`、事实提取器配置 `getExtractorConfig`。

4. **`ApplicationFlowStep` 归入 `engine`**: 作为流程节点定义,是引擎路由配置的核心标识。

5. **`ApplicationSpawnedEvent` 归入 `business.event`**: 业务领域事件(申请单孵化),由业务侧触发。

6. **`StepEnteredEvent`/`PipelineExecutedEvent` 归入 `engine.event`**: 引擎领域事件,由聚合根的 `transit`/`recordPipelineExecution` 触发。

### 3.2 application 层分包

```
com.example.core.application
├── business/                              # 业务应用服务
│   ├── service/                           # 业务应用服务
│   │   ├── BusinessFormAppService
│   │   └── MaterialAppService
│   ├── handler/                           # 业务步骤主处理器
│   │   ├── FormParsingHandler
│   │   ├── DefaultFormParsingHandler
│   │   ├── FileServiceParseHandler
│   │   ├── ApprovalSubmissionHandler
│   │   └── PlanMaterialPreparationHandler
│   └── listener/                          # 业务事件监听器
│       ├── FileParsedEventListener
│       └── ApprovalResultEventListener
│
├── engine/                                # 流程编排引擎应用层
│   ├── service/                           # 引擎应用服务
│   │   └── FlowOrchestrationService       # 重命名自 BusinessOrchestrationAppService
│   ├── pipeline/                          # 管道执行器
│   │   └── StepPipelineExecutor
│   ├── extension/                         # 扩展动作抽象基类
│   │   ├── AbstractJsonStreamIngestionAction
│   │   ├── AbstractStreamingDetailAction
│   │   └── AbstractCursorPagingDetailAction
│   ├── listener/                          # 引擎事件监听器
│   │   ├── StepAutoAdvanceListener
│   │   └── ApplicationSpawnedListener
│   └── errorcode/                         # 引擎应用层错误码
│       └── CoreAppErrorCode
```

**关键决策说明**:

1. **`BusinessOrchestrationAppService` 重命名为 `FlowOrchestrationService`**: 该类本质是流程编排引擎的入口编排器,而非业务应用服务。重命名后语义更清晰,归入 `engine.service`。

2. **`ApplicationSpawnedListener` 归入 `engine.listener`**: 监听 `ApplicationSpawnedEvent` 在事务提交后异步点火启动引擎,属于引擎自驱动机制。

3. **`FileParsedEventListener`/`ApprovalResultEventListener` 归入 `business.listener`**: 消费外部服务事件并推进业务流程,属于业务回调入口。

4. **`StepPipelineExecutor` 保持不变**: 经检验职责纯粹,仅移动包路径。

### 3.3 infrastructure 层分包

```
com.example.core.infrastructure
├── business/                              # 业务基础设施
│   ├── gateway/                           # 业务网关实现
│   │   ├── ApprovalServiceIntegrationGateway
│   │   ├── FileServiceIntegrationGateway
│   │   └── BusinessRuleGatewayImpl        # 新增: BusinessRuleGateway 实现
│   └── json/                              # 业务 JSON 配置
│       └── BusinessExtensionMixIn
│
├── engine/                                # 流程引擎基础设施
│   ├── evaluation/                        # 条件求值实现
│   │   └── SpelConditionEvaluationGateway
│   ├── gateway/                           # 引擎网关实现
│   │   └── RouteConfigGatewayImpl         # 新增: RouteConfigGateway 实现
│   ├── configuration/                     # 引擎配置
│   │   ├── DomainServiceConfiguration
│   │   └── LibJacksonModule
│   └── event/                             # 引擎事件模拟
│       └── IntegrationEventSimulator
```

**关键决策说明**:

1. **`BusinessConfigGateway` 的实现类拆分**: 原 `JsonBusinessConfigGateway`(或类似实现)需拆分为 `BusinessRuleGatewayImpl` 和 `RouteConfigGatewayImpl` 两个实现类,分别注入不同的配置源。

2. **`BusinessExtensionMixIn` 归入 `business.json`**: 业务扩展字段的多态序列化配置,属于业务领域模型的 JSON 配置。

3. **`LibJacksonModule` 归入 `engine.configuration`**: 注册 `BusinessExtension` Mix-in 的 Jackson 模块辅助类,属于引擎基础设施桥接。

### 3.4 BusinessConfigGateway 拆分设计

#### 原接口(混合职责)

```java
public interface BusinessConfigGateway {
  // 引擎路由配置查询
  StepRouteConfig getNextStep(BusinessMetaContext context, ApplicationFlowStep currentStep);
  ExtractorConfig getExtractorConfig(BusinessMetaContext context);

  // 业务规则配置查询
  List<MaterialRuleConfig> getMaterialRules(BusinessMetaContext context);
  Map<String, Object> getFormParseRule(BusinessMetaContext context);
  FormParsingConfig getFormParsingConfig(BusinessMetaContext context);
  String getIngestion(BusinessMetaContext context);
}
```

#### 拆分后

```java
// business 侧: 业务规则配置查询
public interface BusinessRuleGateway {
  List<MaterialRuleConfig> getMaterialRules(BusinessMetaContext context);
  Map<String, Object> getFormParseRule(BusinessMetaContext context);
  FormParsingConfig getFormParsingConfig(BusinessMetaContext context);
  String getIngestion(BusinessMetaContext context);
}

// engine 侧: 路由配置查询
public interface RouteConfigGateway {
  StepRouteConfig getNextStep(BusinessMetaContext context, ApplicationFlowStep currentStep);
  ExtractorConfig getExtractorConfig(BusinessMetaContext context);
}
```

### 3.5 FlowOrchestrationService 重命名说明

`BusinessOrchestrationAppService` 重命名为 `FlowOrchestrationService`,仅类名和包路径变化,内部逻辑不变。

**不修改的点**:
- `advanceStep` / `advanceByFileTaskId` / `advanceByApprovalResult` 方法签名保持不变
- `buildFullConfigContext` 两阶段上下文构建逻辑保持不变
- 依赖注入的 Bean 不变(仅 `BusinessConfigGateway` 替换为 `RouteConfigGateway`)

**`advanceByApprovalResult` 的硬编码常量**: 当前 `APPROVED/REJECTED/WITHDRAWN` 字符串常量保留,作为 follow-up 优化项(抽取为策略对象),不在本次重构范围内。

## 4. 影响范围

### 4.1 文件移动统计

| 层级 | 移动到 business | 移动到 engine | 新增 | 合计
|------|----------------|--------------|------|-----|
| domain | ~30 | ~20 | 1 (`BusinessRuleGateway`) | ~51 |
| application | ~8 | ~7 | 0 | ~15 |
| infrastructure | ~3 | ~5 | 2 (`BusinessRuleGatewayImpl`, `RouteConfigGatewayImpl`) | ~10 |
| **合计** | **~41** | **~32** | **3** | **~76** |

### 4.2 外部模块影响

| 模块 | 影响内容 |
|------|----------|
| `annuity-service` | 需同步更新所有 import kernel 类的包路径 |
| `file-service` | 无直接影响(不依赖 kernel) |
| `approval-service` | 无直接影响(不依赖 kernel) |

### 4.3 测试影响

- kernel 内部 7 个 test 文件需同步更新 import
- `annuity-service` 的测试文件需同步更新 import

## 5. 验证标准

1. **编译通过**: 全量 `mvn clean compile -DskipTests` BUILD SUCCESS
2. **测试通过**: kernel 33 测试 + annuity-service 47 测试全部通过
3. **包结构验证**: `business` 和 `engine` 两个子包在 domain/application/infrastructure 三层均存在
4. **无残留**: 原 `com.example.core.domain.aggregate`、`com.example.core.domain.event` 等旧包路径下无遗留文件(除 `package-info.java`)

## 6. 包间依赖关系说明

由于采用包级分包(不拆分 Maven 模块),`business` 和 `engine` 两个子包之间存在编译期依赖,这是设计允许的:

| 依赖方向 | 示例 | 说明 |
|----------|------|------|
| `business` → `engine` | `BusinessApplication` 依赖 `PipelineExecutionResult`、`StepEnteredEvent`、`PipelineExecutedEvent`、`BusinessMetaContext`、`ApplicationFlowStep` | 充血模型聚合根内聚流程行为,需引用引擎值对象和事件 |
| `engine` → `business` | `StepActionHandler`/`StepExtensionAction` SPI 依赖 `BusinessApplication` | 引擎 SPI 以业务聚合根为参数,实现编排 |
| `engine` → `engine` | `StepPipelineExecutor` 依赖 `ExtensionActionRegistry`、`ConditionEvaluationGateway` | 引擎内部依赖 |
| `business` → `business` | `BusinessFormAppService` 依赖 `FormRepository`、`FileIntegrationGateway` | 业务内部依赖 |

**关键约束**: 包级分包不引入强制隔离,仅通过包结构表达职责边界。若未来需要严格隔离(如引擎独立复用),可升级为模块级拆分。

## 7. Follow-up 项(不在本次范围)

- `FlowOrchestrationService.advanceByApprovalResult` 的 `APPROVED/REJECTED/WITHDRAWN` 字符串常量抽取为策略对象
- `BusinessApplication.recordPipelineExecution` 考虑抽取为独立的 `PipelineExecutionTracker` 领域服务
- `buildFullConfigContext` 两阶段上下文构建考虑抽取为 `ContextAssembler` 领域服务
