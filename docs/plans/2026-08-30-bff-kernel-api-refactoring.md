# BFF 层与业务服务对 Core Kernel API 继承机制重构方案

> 日期：2026-08-30
> 状态：待实施

## 一、背景与问题

### 1.1 当前架构

BFF 层通过 `bff-shared` 共享库提供了一套运行时动态路由机制：

- `BusinessTypeRouter`：通过 DB 路由表 `t_bff_route_config` + Caffeine 缓存，将 `businessType` 映射为 `serviceName`
- `ServiceApiRegistry`：通用 `@HttpExchange` 代理工厂，使用 `@LoadBalanced RestClient.Builder`，按 `serviceName|apiInterface` 复合键缓存代理
- `KernelApiRegistry`：对 `ServiceApiRegistry` 的薄门面，提供 5 个 kernel API 的类型安全访问

调用链路：

```
请求(businessType)
  → BusinessTypeRouter.resolveServiceName(businessType)   // DB 查询 + 缓存
    → KernelApiRegistry.getBatchApi(serviceName)           // 动态创建/缓存代理
      → http://serviceName/core/batch/detail               // LoadBalancer 解析
```

### 1.2 问题

1. **过度设计**：当前只有 `annuity-service` 一个业务服务，运行时路由表 + 动态代理工厂是过度抽象
2. **核心矛盾**：kernel `@HttpExchange` 接口使用通用路径 `/core/batch`，所有实现该接口的服务端点路径完全一致，调用方无法通过路径区分目标服务
3. **重复基础设施**：`ServiceApiRegistry` 本质上是在运行时手动完成了 `httpexchange-spring-boot-autoconfigure` 在启动时自动完成的工作
4. **`ApiResult<Optional<T>>` 反模式**：`ApiResult` 已有成功/失败语义，嵌套 `Optional` 产生三态问题，且 `Optional` 不适合出现在 API 边界

## 二、重构目标

1. 各业务服务的 kernel API 端点路径带上服务级前缀，实现服务间可区分
2. 各 `xxx-api` 模块定义服务专有的 kernel API 接口，供 BFF 通过 httpexchange 标准机制调用
3. `business-core-api` 的接口改为纯 Java 契约接口，不带传输层注解
4. BFF 删除自定义的动态代理/路由基础设施，统一使用 httpexchange 自动配置
5. BFF 引入 Strategy 模式，使聚合逻辑按业务场景隔离，新增业务场景只需新增实现类
6. 消除 `ApiResult<Optional<T>>` 反模式

## 三、设计方案

### 3.1 business-core-api 接口降级为纯契约

**变更前：**
```java
@HttpExchange("/core/batch")
public interface BusinessBatchApi {
    @PostExchange("/active")
    ApiResult<Optional<BatchSummaryResponse>> findActive(@Valid @RequestBody FindActiveBatchQuery query);

    @PostExchange("/detail")
    ApiResult<BatchDetailResponse> detail(@Valid @RequestBody GetBatchDetailQuery query);
}
```

**变更后：**
```java
public interface BusinessBatchApi {
    ApiResult<BatchSummaryResponse> findActive(@Valid FindActiveBatchQuery query);
    ApiResult<BatchDetailResponse> detail(@Valid GetBatchDetailQuery query);
    ApiResult<BatchCreatedResponse> create(@Valid CreateBatchCommand command);
    ApiResult<Void> cancel(@Valid CancelBatchCommand command);
}
```

规则：
- 去掉 `@HttpExchange`、`@PostExchange`、`@GetExchange` — 路径是传输层关注点，不属于契约
- 去掉 `@RequestBody` — 序列化方式是传输层关注点
- 保留 `@Valid` — 参数校验是业务契约的一部分
- 去掉 `Optional` 包装 — 用 `data = null` 表示"成功但无数据"，用 `ApiResult.failure()` 表示业务错误

### 3.2 各 xxx-api 模块定义服务专有 kernel API 接口

每个业务服务的 `xxx-api` 模块中新增 `kernel` 子包，定义带服务前缀的 `@HttpExchange` 接口。

**以 annuity-api 为例：**

```
com.example.annuity.api
├── AnnuityApi.java                  (已有，业务独有 API)
└── kernel/                          (新增子包)
    ├── AnnuityBatchApi.java
    ├── AnnuityFormApi.java
    ├── AnnuityApplicationApi.java
    ├── AnnuityMaterialApi.java
    └── AnnuityProgressApi.java
```

```java
@HttpExchange("/annuity/core/batch")
public interface AnnuityBatchApi extends BusinessBatchApi {
    @PostExchange("/active")
    @Override
    ApiResult<BatchSummaryResponse> findActive(@Valid @RequestBody FindActiveBatchQuery query);

    @PostExchange("/detail")
    @Override
    ApiResult<BatchDetailResponse> detail(@Valid @RequestBody GetBatchDetailQuery query);

    @PostExchange("/create")
    @Override
    ApiResult<BatchCreatedResponse> create(@Valid @RequestBody CreateBatchCommand command);

    @PostExchange("/cancel")
    @Override
    ApiResult<Void> cancel(@Valid @RequestBody CancelBatchCommand command);
}
```

关键规则：
- 继承 `BusinessBatchApi`（纯契约），保证方法签名编译期一致
- 每个方法必须重新声明并标注 `@PostExchange` + `@RequestBody` — Spring httpexchange 代理从方法的 declaring class 读取注解，不追溯父接口
- 路径前缀 = `/{service-name}/core/{domain}`，如 `/annuity/core/batch`
- Command/Query/Response 类型直接复用 `business-core-api` 中的定义，不重复定义

### 3.3 各业务服务 adapter controller 添加服务级路径前缀

**变更前（kernel adapter controller）：**
```java
@RestController                        // 无 @RequestMapping
public class BusinessBatchController implements BusinessBatchApi {
    // 端点: POST /core/batch/detail
}
```

**变更后（annuity adapter controller）：**
```java
@RestController
@RequestMapping("/annuity/core/batch")   // 服务级前缀 + kernel 路径
@RequiredArgsConstructor
public class AnnuityBatchController implements BusinessBatchApi {
    // 实现纯契约接口，保证方法签名一致
    // 端点注册靠 @RequestMapping + @PostMapping

    @Override
    @PostMapping("/active")
    @RequirePermission(business = "BATCH", action = "VIEW")
    public ApiResult<BatchSummaryResponse> findActive(@Valid @RequestBody FindActiveBatchQuery query) { ... }

    @Override
    @PostMapping("/detail")
    @RequirePermission(business = "BATCH", action = "VIEW")
    public ApiResult<BatchDetailResponse> detail(@Valid @RequestBody GetBatchDetailQuery query) { ... }
}
```

规则：
- Controller 实现 `BusinessBatchApi`（纯契约），不实现 `AnnuityBatchApi`（客户端代理接口）— 服务端不需要 `@HttpExchange`，端点注册走 `@RequestMapping`/`@PostMapping`
- `@RequestMapping` 路径 = `/{service-name}/core/{domain}`，与对应 `AnnuityBatchApi` 的 `@HttpExchange` 路径一致
- 5 个 kernel controller 按同样模式改造

### 3.4 依赖关系

```
business-core-api (纯契约，无 @HttpExchange)
    ↑ implements                    ↑ extends
AnnuityBatchController           AnnuityBatchApi (@HttpExchange，供 BFF 客户端代理)
(服务端，@RequestMapping)         (客户端，@HttpExchange)
```

两者都关联到 `BusinessBatchApi`，但通过不同注解机制各自工作。

### 3.5 BFF 侧改造

#### 3.5.1 删除动态代理基础设施

删除以下组件：
- `ServiceApiRegistry`
- `KernelApiRegistry`
- `BusinessTypeRouter`
- `BffRouteConfig`（DO/Repository/相关查询）
- `t_bff_route_config` 数据库表
- `BffAutoConfiguration` 中对这些 Bean 的注册
- Caffeine 缓存配置（如果仅用于路由）

#### 3.5.2 配置 httpexchange 自动代理

```yaml
httpexchange:
  clients:
    com.example.annuity.api:
      url: lb://annuity-service
    com.example.approval.api:
      url: lb://approval-service
    com.example.auth.api:
      url: lb://auth-service
```

Spring 启动时自动为 `com.example.annuity.api` 包下所有 `@HttpExchange` 接口创建代理 Bean，base URL 统一指向 `annuity-service`。

#### 3.5.3 引入 Strategy 模式处理聚合

```java
/**
 * 业务场景处理器 — 每个 businessType 一个实现
 */
public interface BusinessScenarioHandler {

    /** 本处理器支持的业务类型 */
    BusinessType supportedType();

    /** 简单转发（无聚合） */
    ApiResult<BatchDetailResponse> batchDetail(GetBatchDetailQuery query);
    ApiResult<BatchCreatedResponse> createBatch(CreateBatchCommand command);
    ApiResult<UploadTokenResponse> applyUploadToken(ApplyUploadTokenCommand command);
    ApiResult<SubmitResponse> submitApplication(SubmitApplicationCommand command);
    ApiResult<ApplicationDetailResponse> applicationDetail(GetApplicationDetailQuery query);
    ApiResult<List<MaterialItemResponse>> listMaterials(ListMaterialsQuery query);

    /** 聚合场景（各业务可按需覆写） */
    ApiResult<BatchOverviewResponse> batchOverview(GetBatchDetailQuery query);
}
```

Registry 自动收集：

```java
@Service
public class ScenarioHandlerRegistry {

    private final Map<BusinessType, BusinessScenarioHandler> handlerMap;

    public ScenarioHandlerRegistry(List<BusinessScenarioHandler> handlers) {
        this.handlerMap = handlers.stream()
            .collect(Collectors.toMap(BusinessScenarioHandler::supportedType, Function.identity()));
    }

    public BusinessScenarioHandler handlerFor(BusinessType type) {
        BusinessScenarioHandler handler = handlerMap.get(type);
        if (handler == null) {
            throw new BusinessException(BffErrorCode.SCENARIO_NOT_SUPPORTED);
        }
        return handler;
    }
}
```

年金实现：

```java
@Service
public class AnnuityScenarioHandler implements BusinessScenarioHandler {

    private final AnnuityBatchApi batchApi;
    private final AnnuityFormApi formApi;
    private final AnnuityApplicationApi applicationApi;
    private final AnnuityMaterialApi materialApi;
    private final AnnuityProgressApi progressApi;
    private final AsyncTaskExecutor taskExecutor;

    @Override
    public BusinessType supportedType() { return BusinessType.ANNUITY; }

    @Override
    public ApiResult<BatchDetailResponse> batchDetail(GetBatchDetailQuery query) {
        return batchApi.detail(query);  // 简单转发
    }

    @Override
    public ApiResult<BatchOverviewResponse> batchOverview(GetBatchDetailQuery query) {
        // 年金特有的聚合 + 降级逻辑
        // 并发调用 batchApi + progressApi + applicationApi
    }

    // ... 其他简单转发方法
}
```

BFF 调度层简化为纯委托：

```java
@Service
public class BffBusinessService {

    private final ScenarioHandlerRegistry registry;

    public ApiResult<BatchDetailResponse> batchDetail(BffBatchDetailRequest request) {
        return registry.handlerFor(request.businessType())
            .batchDetail(new GetBatchDetailQuery(request.batchId()));
    }

    public ApiResult<BatchOverviewResponse> batchOverview(BffBatchOverviewRequest request) {
        return registry.handlerFor(request.businessType())
            .batchOverview(new GetBatchDetailQuery(request.batchId()));
    }
}
```

**新增业务场景只需新增 `XxxScenarioHandler` 实现类 + BFF 引入对应 `xxx-api` 依赖 + YAML 加一行配置。** 不改动任何已有代码。

### 3.6 消除 `ApiResult<Optional<T>>` 反模式

全部 `ApiResult<Optional<T>>` 改为 `ApiResult<T>`，用 `data = null` 表示"成功但无数据"。

仅影响 `BusinessBatchApi.findActive()` 一处。

## 四、变更清单

| 变更项 | 操作 | 涉及模块 |
|--------|------|----------|
| `BusinessBatchApi` 等 5 个 kernel API 接口 | 去掉 `@HttpExchange`/`@PostExchange`/`@RequestBody`，去掉 `Optional` | `business-core-api` |
| `AnnuityBatchApi` 等 5 个服务专有 kernel API 接口 | 新增，继承纯契约接口 | `annuity-api` |
| `AnnuityBatchController` 等 5 个 controller | 加 `@RequestMapping("/{service}/core/...")`，改用 `@PostMapping` | `annuity-adapter` |
| `ServiceApiRegistry` | 删除 | `bff-shared` |
| `KernelApiRegistry` | 删除 | `bff-shared` |
| `BusinessTypeRouter` + `BffRouteConfig`(DO/Repository) | 删除 | `bff-shared` |
| `t_bff_route_config` DDL | 删除 | `bff-shared` schema SQL |
| `BffAutoConfiguration` | 简化，去掉已删 Bean 的注册 | `bff-shared` |
| `BffBusinessService` | 重写为纯调度，委托 `ScenarioHandlerRegistry` | `bff-shared` |
| `BffAggregationService` | 删除，聚合逻辑移入 `AnnuityScenarioHandler` | `bff-shared` |
| `BusinessScenarioHandler` + `ScenarioHandlerRegistry` | 新增 | `bff-shared` |
| `AnnuityScenarioHandler` | 新增，从原 `BffAggregationService` 提取年金逻辑 | `internet-bff-application` 或 `intranet-bff-application` |
| `application.yml` (internet-bff, intranet-bff) | 添加 httpexchange clients 配置 | 两个 BFF starter |
| `ApiResult<Optional<...>>` | 改为 `ApiResult<...>`，用 null 表示无数据 | `business-core-api`, `business-core-adapter` |

## 五、风险与注意事项

1. **路径变更是破坏性变更**：kernel 端点从 `/core/batch/detail` 变为 `/annuity/core/batch/detail`。根据架构设计，kernel 服务不对外暴露，所有外部调用通过 BFF/Gateway，因此影响可控。但如果有内部服务间直接调用 kernel API 的场景，需要同步调整。

2. **接口方法必须重新声明**：子接口中每个方法必须重新声明并标注 `@PostExchange`/`@RequestBody`，因为 Spring httpexchange 从方法的 declaring class 读取注解。继承只保证签名编译期一致，不继承传输层注解。

3. **Controller 实现纯契约接口**：服务端 Controller 实现 `BusinessBatchApi`（纯契约），不实现 `AnnuityBatchApi`（客户端代理接口）。两者方法签名一致，但注解体系不同。

4. **BusinessScenarioHandler 的简单转发方法**：如果大量方法都是简单转发无聚合，可考虑提供 `AbstractBusinessScenarioHandler` 基类给出默认实现，子类只需覆写有聚合的方法。但这不是必须的，取决于实际复杂度。
