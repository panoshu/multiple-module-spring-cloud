# demo-shared 模块架构分析报告

## 一、demo-shared 子模块分类

### A. DDD + 六边形架构必须模块

这些模块为 DDD + 六边形架构提供核心抽象契约，缺少它们则架构范式无法成立：

| 模块 | 层级 | 核心职责 | 必要性说明 |
|---|---|---|---|
| **shared-types** | shared-lib | 领域原语基础：`Identifier<T>`、`IdDefinition`、`IdType`、`IdService` | 强类型 ID 是聚合根身份的基石，缺少则所有聚合根和 Repository 契约无法定义 |
| **shared-domain** | shared-lib | DDD 核心抽象：`Entity<ID>`、`AggregateRoot<ID>`、`ValueObject`、`Repository<T,ID>`、`DomainEvent`、`EventBus`、`DomainService` | 六边形架构的"端口"定义全在此处，是最核心的模块 |
| **shared-exception** | shared-lib | 异常体系：`ErrorDefinition`、`BaseException`、`DomainException`、`BusinessException`、`SystemException` | 领域层异常与基础设施层异常的隔离是依赖倒置的前提 |
| **shared-api** | shared-lib | API 层契约：`ApiResult<T>`、`PageData<T>`、`PageQuery`、`@HttpExchange` 基础 | 跨服务调用必须依赖 API 接口而非实现，是六边形"适配器"的协议基础 |

### B. 工具/基础设施模块

这些模块提供具体的技术能力，是架构的"适配器实现"，理论上可以被替换而不影响架构范式本身：

| 模块 | 层级 | 核心职责 | 可替换性 |
|---|---|---|---|
| **shared-crypto** | shared-lib | SM4 加解密 | 可替换为其他加密实现 |
| **shared-json** | shared-lib | 流式 JSON 字段处理器 | 仅被 gateway-shared 和 logging-core 使用 |
| **shared-lock** | shared-lib | 分布式锁抽象 + 本地/Redis 实现 | 可替换为其他锁方案 |
| **shared-utils** | shared-lib | `TriFunction`、`VirtualThreadExecutor` | 纯工具，与架构无关 |
| **gateway-shared** | shared-lib | 网关认证/加解密/会话注入组件 | 仅被网关应用使用 |
| **bff-shared** | shared-lib | BFF 聚合编排/ScenarioHandler 基础设施 | 仅被 BFF 应用使用 |
| **shared-cache-core** | shared-core | L1/L2 分层缓存引擎 | 可替换为其他缓存方案 |
| **shared-client-core** | shared-core | HTTP 客户端 trace 拦截器 | 可替换为其他客户端方案 |
| **shared-event-core** | shared-core | 领域事件→集成事件→RocketMQ | 可替换为其他消息方案 |
| **shared-id-core** | shared-core | TinyId 号段式 ID 生成 | 可替换为 Snowflake 等方案 |
| **shared-logging-core** | shared-core | 日志脱敏引擎 + 持久化管道 | 可替换为其他日志方案 |
| **shared-pdf-core** | shared-core | HTML→PDF 渲染 | 可替换为其他 PDF 方案 |
| **shared-web-core** | shared-core | 全局异常处理、trace 传播 | 可替换为其他 Web 框架方案 |
| **shared-crypto-starter** | shared-starter | crypto 自动装配 | 薄壳，纯配置 |
| **shared-cache-starter** | shared-starter | cache 自动装配 | 薄壳，纯配置 |
| **shared-id-starter** | shared-starter | ID 生成自动装配 | 薄壳，纯配置 |
| **shared-event-starter** | shared-starter | 事件总线自动装配 | 薄壳，纯配置 |
| **shared-logging-starter** | shared-starter | 日志自动装配 | 薄壳，纯配置 |
| **shared-web-starter** | shared-starter | Web 全局处理自动装配 | 薄壳，纯配置 |
| **shared-client-starter** | shared-starter | HTTP 客户端自动装配 | 薄壳，纯配置 |
| **shared-pdf-starter** | shared-starter | PDF 自动装配 | 薄壳，纯配置 |
| **shared-permission-starter** | shared-starter | 权限校验 AOP + 数据范围过滤 | 可替换为其他权限方案 |

---

## 二、第一性原理分析

从第一性原理出发，我们对每个模块追问三个问题：

1. **它的本质功能是什么？**（剥离实现细节后，它解决的根本问题）
2. **当前实现是否忠实于这个本质？**（有没有偏离、膨胀、越界）
3. **它应该放在哪里？**（从依赖方向和职责边界判断）

---

### 2.1 shared-types —— 领域原语层

**本质功能**：为所有服务提供**通用的领域原语协议**——主要是强类型 ID 的抽象契约（`Identifier<T>` + `IdDefinition`），使得跨服务引用 ID 类型时不产生循环依赖。

**当前问题**：

| 问题 | 分析 |
|---|---|
| **身份膨胀**：包含 13 个具体 ID 类型 | `AcceptanceNo`、`ApplicationId`、`BatchId` 等是特定业务概念，不是"通用原语"。它们放在 shared-types 会导致所有依赖 shared-types 的模块间接依赖这些业务概念 |
| **值对象泄漏**：`Address`、`Email`、`Mobile`、`IdentityDocument` 等 | 这些是特定限界上下文的值对象，仅 auth-service 使用。放在 shared-types 违反了限界上下文的边界 |
| **枚举混入**：`CodeEnum<C>`、`AnnuityChannel`、`OperatingMode` | `CodeEnum` 是框架反射工具，`AnnuityChannel` 等是业务枚举。都不是"领域原语" |
| **分页重叠**：`PageInfo`/`Pagination` 与 shared-api 的 `PageData`/`PageQuery` 功能重复 | 两套分页模型共存，使用者困惑 |

**结论**：shared-types 的**核心契约**（`Identifier`、`IdDefinition`、`IdType`、`IdService`）是架构必须的，但当前实现严重膨胀，约 60% 的内容不属于此模块。

---

### 2.2 shared-domain —— 领域核心抽象层

**本质功能**：定义 DDD 的**元模型**——实体、聚合根、值对象、仓储、领域事件、领域服务的抽象接口。这是六边形架构的"内核"，所有业务服务的 domain 层都依赖于此。

**当前问题**：

| 问题 | 分析 |
|---|---|
| **EventDispatcher / EventStore / IntegrationEventConverter 不属于核心抽象** | `EventDispatcher` 是应用层的编排逻辑，`EventStore` 是基础设施层的持久化契约，`IntegrationEventConverter` 是跨上下文的适配器。三者都是具体策略，不是元模型 |
| **AggregateRoot 依赖 UserNo** | `markUpdated(UserNo)` 硬编码了一个具体业务 ID 类型。从第一性原理看，"谁修改了聚合"只需要一个 `String` 或通用标识符，不需要引入 `UserNo` 类型耦合 |
| **SharedDomainErrorCode** | 错误码是具体策略实现，不属于元模型定义 |

**结论**：shared-domain 应该只保留**不可替换的元模型抽象**：`Entity`、`AggregateRoot`、`ValueObject`、`Repository`、`DomainEvent`、`EventBus`、`DomainService`。其余应下放到 shared-event-core 或 shared-exception。

---

### 2.3 shared-exception —— 异常体系层

**本质功能**：定义**统一的错误表达协议**——`ErrorDefinition` 接口和 `BaseException` 抽象类，使得领域层、应用层、基础设施层可以用同一套语义表达错误，同时保持依赖方向正确。

**当前问题**：本模块实现较为干净，`CommonError` 的存在是合理的（HTTP 通用错误属于公共协议）。**无明显偏离**。

**结论**：架构必要，实现忠实。唯一可优化的是 `CommonError` 是否应该放在 shared-api 中（因为它是 API 层的 HTTP 映射协议），但当前放此处也无大碍。

---

### 2.4 shared-api —— API 协议层

**本质功能**：定义**跨服务调用的公共协议**——统一响应格式 `ApiResult<T>`、分页协议 `PageData/PageQuery`，使得服务间通过 API 接口而非实现类耦合。

**当前问题**：

| 问题 | 分析 |
|---|---|
| **与 shared-types 的分页重叠** | `PageInfo`/`Pagination`（shared-types）与 `PageData`/`PageQuery`（shared-api）功能重复，应统一为一套 |
| **`@BizTag` 注解** | 仅在 2 处使用，不属于"公共 API 协议"，更像是特定切面的标记 |

**结论**：架构必要。需清理分页重复和 `@BizTag` 的归属。

---

### 2.5 shared-crypto —— 加解密层

**本质功能**：提供**数据加密的抽象和默认实现**，使得网关/服务可以对敏感字段透明加解密，而调用方不需要知道具体算法。

**当前实现评价**：

- **层次正确**：`Encryptor` 接口 + `Sm4Encryptor` 实现在 shared-lib，自动装配在 shared-crypto-starter
- **接口简洁**：`encrypt(String)` / `decrypt(String)`，恰好够用
- **无 Spring 依赖**：纯 Java + KonaCrypto

**结论**：工具模块，实现优秀，是三层分层的**标杆范例**。其他模块应学习此模式。

---

### 2.6 shared-json —— JSON 处理层

**本质功能**：提供**流式 JSON 字段处理能力**，使得网关加解密过滤器和日志脱敏器可以在不反序列化整棵 JSON 树的情况下处理特定字段。

**当前问题**：

| 问题 | 分析 |
|---|---|
| **消费面极窄** | 仅被 `gateway-shared`（CryptoFilter）和 `shared-logging-core`（脱敏）使用 |
| **是否应该是独立模块？** | 从第一性原理看，"流式 JSON 字段处理"是一个通用技术能力，但其需求场景确实很少 |

**结论**：工具模块。当前作为独立模块存在合理（保持了 shared-lib 的纯 Java 约束），但如果未来没有更多消费者，可以考虑内联到 gateway-shared 和 logging-core 中。

---

### 2.7 shared-lock —— 分布式锁层

**本质功能**：提供**分布式锁的抽象和多种实现**（本地锁、Redis 锁），使得业务服务可以用统一接口获取锁，无需关心底层实现。

**当前问题**：

| 问题 | 分析 |
|---|---|
| **严重层次违规**：包含 `AutoConfiguration.imports` | 在 shared-lib 中注册了 `DistributedLockAutoConfiguration`，违反了"shared-lib 无 Spring 依赖、无自动装配"的约定。自动装配应放在 shared-starter 层，但本模块**没有对应的 starter** |
| **`DistributedLockFactory` 使用硬编码 bean 名称** | `"localDistributedLock"` / `"redissonDistributedLock"` 是字符串魔法值，违反规则 10.4 |
| **`lockAndRun` 抛出裸 `RuntimeException`** | 应使用 `SystemException` + `LockErrorDefinition` |

**结论**：工具模块，功能定位正确，但实现有三个违规点。需要：(1) 将自动装配拆出 `shared-lock-starter`；(2) 修复硬编码和异常处理。

---

### 2.8 shared-utils —— 工具层

**本质功能**：存放**无法归入其他模块的通用工具代码**。

**当前问题**：

| 内容 | 分析 |
|---|---|
| `TriFunction<T,U,V,R>` | JDK 没有三参数函数式接口，但这个接口仅被 shared-event-core 使用。从第一性原理看，它是一个局部需求，不应放在全局 shared 层 |
| `VirtualThreadExecutor` | ~150 行的结构化并发工具，使用了 JDK 25 的 `StructuredTaskScope`。这是一个有价值的基础设施组件，但放在 shared-utils 中显得定位模糊 |

**结论**：工具模块。`TriFunction` 应该放到实际使用它的模块中。`VirtualThreadExecutor` 值得保留但应重命名为独立模块（如 `shared-concurrency`）以明确语义。

---

### 2.9 gateway-shared —— 网关公共组件

**本质功能**：为多个网关应用（internet-gateway、intranet-gateway、demo-gateway）提供**公共的网关层能力**——认证、加解密、会话注入、路由排除。

**当前问题**：

| 问题 | 分析 |
|---|---|
| **严重层次违规**：位于 shared-lib 但使用 `@Configuration`、`@Bean`、`@Component` | shared-lib 层的约定是"纯 Java，无 Spring"。gateway-shared 完全违反此约定 |
| **反向依赖**：依赖 `auth-api` | shared 层 → 业务 API 层，这是依赖方向的反转。公共基础层不应知道任何具体业务服务的存在 |
| **应该在哪里？** | 从第一性原理看，网关公共组件本质上是**网关应用的 starter**，应该放在 shared-starter 层，或者作为顶级模块独立存在 |

**结论**：工具模块，但**当前位置错误**。应迁移到 shared-starter 层（如 `gateway-starter`）或独立为顶级模块。

---

### 2.10 bff-shared —— BFF 公共组件

**本质功能**：为多个 BFF 应用提供**公共的聚合编排能力**——ScenarioHandler 注册表、业务场景处理器、DTO 转换。

**当前问题**：

| 问题 | 分析 |
|---|---|
| **严重层次违规**：位于 shared-lib 但使用 `@Service` | 同 gateway-shared |
| **反向依赖**：依赖 `business-core-api` + `annuity-api` | shared 层同时依赖两个业务服务的 API，是严重的耦合。这实际上让 shared 层变成了"业务粘合层" |
| **业务逻辑泄漏**：`AnnuityScenarioHandler` 是具体的业务场景处理器 | 放在 shared 层意味着修改年金业务逻辑需要改 shared 模块，违反了限界上下文独立性 |

**结论**：工具模块，但**当前位置严重错误**。BFF 的聚合编排逻辑本质上是 BFF 的应用层，应直接放在各 BFF 的 application 模块中，而不是提取到 shared 层。

---

### 2.11 shared-core 系列（7个模块）

**本质功能**：提供**基础设施能力的核心实现**（不含自动装配），是 shared-lib（纯抽象）和 shared-starter（纯装配）之间的桥梁。

**通用问题**：

| 模块 | 核心问题 |
|---|---|
| **shared-cache-core** | 实现**干净**：L1/L2 缓存 + 防击穿锁，符合 core 层定义 |
| **shared-client-core** | 实现**干净**：仅 4 文件，trace 拦截器 |
| **shared-event-core** | 实现**基本干净**，但 `IntegrationEventConverter` 应在此处而非 shared-domain |
| **shared-id-core** | 实现**干净**：TinyId 号段式 ID 生成 |
| **shared-logging-core** | **过度膨胀**：48 文件，包含导出管道、持久化逻辑，部分内容更像应用层 |
| **shared-pdf-core** | 实现**干净**：5 文件 |
| **shared-web-core** | 实现**基本干净**，但 `GlobalExceptionHandler` 是否应该由 starter 层提供值得讨论 |

**结论**：core 层整体定位正确，是三层分层的中间层。主要问题是 shared-logging-core 过度膨胀和 shared-event-core 与 shared-domain 的职责边界不清。

---

### 2.12 shared-starter 系列（9个模块）

**本质功能**：提供**Spring Boot 自动装配**，通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 将 core 层的组件注册到 Spring 容器。

**当前问题**：

| 问题 | 分析 |
|---|---|
| **缺失 shared-lock-starter** | shared-lock 的自动装配错误地放在了 shared-lib 中，缺少对应的 starter |
| **缺失 gateway-starter** | gateway-shared 的自动装配也错误地放在了 shared-lib 中 |
| **shared-permission-starter 依赖 auth-api** | 与 gateway-shared 同样的反向依赖问题 |

**结论**：starter 层定位正确。需要补齐缺失的 starter，并将错误放在 lib/core 中的自动装配迁移至此。

---

## 三、总结：核心发现

从第一性原理出发，demo-shared 模块体系存在**三个根本性矛盾**：

### 矛盾 1：shared-lib 的"纯 Java"约定被严重打破

按约定，shared-lib 应该是"无 Spring 依赖、无自动装配"的纯 Java 库。但实际：

- `shared-lock` 包含 `AutoConfiguration.imports`
- `gateway-shared` 大量使用 `@Configuration`/`@Bean`/`@Component`
- `bff-shared` 使用 `@Service`

**根因**：缺少对应的 starter 模块时，开发者将自动装配逻辑"就近"放入了 lib 层。

### 矛盾 2：shared 层的"公共性"被打破

shared 层应该是**所有服务平等依赖的公共基础**，不应偏向任何特定业务。但实际：

- `gateway-shared` 依赖 `auth-api`（反向依赖）
- `bff-shared` 依赖 `business-core-api` + `annuity-api`（反向依赖）
- `shared-permission-starter` 依赖 `auth-api`（反向依赖）
- `shared-types` 包含仅 1-2 个服务使用的业务值对象和枚举

**根因**：混淆了"跨服务复用"和"真正的公共抽象"。被多个服务使用 ≠ 应该放在 shared 层。判断标准应该是**依赖方向**：shared 层不能向上依赖业务层。

### 矛盾 3：职责膨胀

多个模块超出其本质功能范围：

- `shared-types`：从"ID 原语协议"膨胀为"业务 ID + 值对象 + 枚举 + 分页 + 工具"的大杂烩
- `shared-domain`：从"DDD 元模型"膨胀为包含事件分发、持久化、集成转换的实现
- `shared-logging-core`：从"日志脱敏"膨胀为包含导出管道、持久化的 48 文件模块

**根因**：没有建立清晰的"模块职责边界"评审标准，新增代码时倾向于"就近放入"而非"判断归属"。

---

## 四、优化建议

| 优先级 | 动作 | 收益 |
|---|---|---|
| **P0** | 将 `gateway-shared` 迁出 shared-lib → 成为独立模块或 `gateway-starter` | 消除层次违规 + 反向依赖 |
| **P0** | 将 `bff-shared` 的内容下沉到各 BFF 的 application 层 | 消除 shared → 业务层的反向依赖 |
| **P0** | 将 `shared-lock` 的自动装配拆出 `shared-lock-starter` | 修复层次违规 |
| **P1** | 将 `shared-types` 中的业务 ID 类型归还各服务 types 层 | 消除共享耦合，恢复限界上下文独立性 |
| **P1** | 将 `shared-types` 中的值对象/枚举归还各自业务域 | 同上 |
| **P1** | 统一分页模型：删除 `PageInfo`/`Pagination`，统一使用 `PageData`/`PageQuery` | 消除二义性 |
| **P1** | 将 `EventDispatcher`/`EventStore`/`IntegrationEventConverter` 从 shared-domain 下放到 shared-event-core | 保持元模型的纯粹性 |
| **P2** | `AggregateRoot.markUpdated()` 参数从 `UserNo` 改为 `String` | 消除元模型对业务类型的耦合 |
| **P2** | 修复 `DistributedLockFactory` 的硬编码 bean 名称 | 消除魔法值 |
| **P2** | 修复 `lockAndRun` 的裸 `RuntimeException` | 对齐异常体系 |
