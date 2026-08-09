# BFF 层设计方案

## 一、背景与目标

### 1.1 当前架构

当前项目采用单体网关（demo-gateway）直连各业务微服务的架构：

- demo-gateway 按 Path 前缀路由到各业务服务（`/annuity/**` → annuity-service）
- business-core-kernel 作为公共业务能力库，被各业务服务以 Maven 依赖方式引入
- kernel 的 5 类公共 API（Batch/Form/Application/Material/Progress）通过 business-core-adapter 的 `@AutoConfiguration` +
  `@ComponentScan` 在各业务服务中自动暴露为 REST 端点
- 服务间通信用 `@HttpExchange`（httpexchange-spring-boot-autoconfigure），按包名映射目标 URL

### 1.2 目标

新增两个 BFF 模块和两个网关模块，按渠道分离：

| 模块             | 渠道      | 职责                              |
|------------------|-----------|-----------------------------------|
| internet-gateway | 互联网    | 认证、加解密、路由到 internet-bff |
| intranet-gateway | 网点/总部 | 认证、加解密、路由到 intranet-bff |
| internet-bff     | 互联网    | 请求路由、数据聚合、接口适配      |
| intranet-bff     | 网点/总部 | 请求路由、数据聚合、接口适配      |

请求链路： **前端 → 网关 → BFF → 业务服务**

### 1.3 核心设计约束

- BFF 对前端暴露收敛的 API 接口，通过请求体中的 `businessType` 等业务字段路由
- BFF 通过 Maven 依赖引入各业务服务的 API 模块，像标准服务间调用一样使用 `@HttpExchange`
- 新增业务类型（现有服务处理）：DB 加行，零代码变更
- 新增业务服务：加 Maven 依赖 + DB 加行 + 重启 BFF
- kernel 和现有业务服务不需要修改

## 二、BFF 层职责

### 2.1 BFF 应做的事

| 职责           | 说明                                                                                     |
|----------------|------------------------------------------------------------------------------------------|
| 渠道差异化适配 | 互联网和网点/总部渠道的前端交互模式、数据粒度、安全要求不同，BFF 为各自渠道裁剪/组装数据 |
| 请求路由与分发 | 根据请求中的 `businessType` 定位目标业务服务，将请求转发到正确的服务实例                 |
| 数据聚合与裁剪 | 前端一个页面可能需要调用多个后端服务，BFF 并发调用并聚合为一个响应                       |
| 协议转换       | 前端友好的数据格式 ↔ 后端服务 API 格式                                                   |
| 会话上下文处理 | 从网关透传的 `X-Session-Context` 中提取渠道信息、用户身份，按渠道做额外安全校验          |
| 审计记录       | 记录请求入口、路由决策、后端调用耗时、聚合结果，用于问题排查和行为追溯                   |

### 2.2 BFF 不应做的事

- 不承载业务规则（业务规则在 domain 层和 domain service）
- 不直接操作数据库（数据访问在 infrastructure 层）
- 不做通用的跨服务事务（事务边界在各业务服务的 application 层）
- 不重复实现 kernel 的 Controller（BFF 调用 kernel 暴露的 API，不重新实现）

### 2.3 BFF 代码的配置化边界

| 扩展场景                       | 需要改代码吗 | 说明                                    |
|--------------------------------|--------------|-----------------------------------------|
| 新增业务类型（现有服务处理）   | 不需要       | DB 路由表加一行，BFF 自动路由到对应服务 |
| 新增服务（处理已有业务类型集） | 加依赖+配置  | 加 Maven 依赖 + DB 加行 + 重启 BFF      |
| 新增 BFF 接口 / 新增聚合逻辑   | 必须写代码   | 这是 BFF 的核心价值，不是缺陷           |
| 后端 API 签名变更              | BFF 同步修改 | 强类型语言的编译期约束                  |

纯路由（businessType → serviceName）可配置化；接口编排（BFF 接口 → 后端 API 方法 + 聚合逻辑）必须写代码。这是强类型语言的自然约束，不是设计缺陷。

## 三、整体架构与部署拓扑

### 3.1 请求链路

```
互联网前端 ──→ internet-gateway ──→ internet-bff ──→ 业务服务(annuity/loan/...)
                                              ↕
网点/总部前端 ──→ intranet-gateway ──→ intranet-bff ──→ 业务服务(annuity/loan/...)
```

### 3.2 新增模块

| 模块               | 说明                                                                               |
|--------------------|------------------------------------------------------------------------------------|
| `gateway-shared`   | 公共网关组件（sa-token 集成、SM4 加解密、会话注入、过滤器基类）                    |
| `internet-gateway` | 互联网网关，处理 internet 渠道的认证/加解密/路由                                   |
| `intranet-gateway` | 内网网关，处理 branch/hq 渠道的认证/加解密/路由                                    |
| `bff-shared`       | 公共 BFF 组件（BusinessTypeRouter、KernelApiRegistry、KernelApiInvoker、公共 DTO） |
| `internet-bff`     | 互联网 BFF，依赖 bff-shared + 各业务服务 API 模块                                  |
| `intranet-bff`     | 内网 BFF，依赖 bff-shared + 各业务服务 API 模块                                    |

现有 `demo-gateway` 保留作为开发环境统一入口，或逐步废弃。

### 3.3 BFF 内部模块结构

BFF 不是领域服务，没有自己的领域模型，简化 DDD 分层：

```
bff-shared/                    # 公共模块（两个 BFF 共享）
├── BusinessTypeRouter         # businessType → serviceName 解析（DB + Caffeine 缓存）
├── KernelApiRegistryFactory   # 用 HttpServiceProxyFactory 动态创建 kernel API 代理
├── KernelApiRegistry          # serviceName → kernel API 代理注册表
├── BffAuditAspect             # 审计 AOP 切面
└── 公共 DTO 基类

internet-bff/                  # 互联网 BFF
├── bff-api/                   # BFF 对前端的 @HttpExchange 接口 + DTO
├── bff-application/           # 路由逻辑、聚合编排
├── bff-adapter/               # Controller 实现 BFF API
├── bff-infrastructure/        # DB 路由表访问、httpexchange 配置
└── bff-starter/               # 启动类

intranet-bff/                  # 内网 BFF（结构同 internet-bff）
├── bff-api/
├── bff-application/
├── bff-adapter/
├── bff-infrastructure/
└── bff-starter/
```

`bff-shared` 包含：

- `BusinessTypeRouter`：businessType → serviceName 解析（DB + Caffeine 缓存）
- `KernelApiRegistryFactory`：用 HttpServiceProxyFactory 为每个服务动态创建 kernel API 代理
- `KernelApiRegistry`：serviceName → kernel API 代理的注册表
- 公共 DTO：BFF 请求/响应基类
- 审计 AOP 切面

### 3.4 BFF 引入的依赖

每个 BFF 引入：

- `business-core-api` — kernel 公共 API 接口和 DTO（用于 KernelApiRegistry 类型安全调用）
- 各业务服务的 `xxx-api` 模块 — 服务专属 API（如 annuity-api、approval-api、file-api）
- `bff-shared` — 公共路由机制
- `shared-web-starter`、`shared-permission-starter` — Web 基础设施

## 四、路由机制详细设计

### 4.1 设计挑战

kernel 的 5 类公共 API 是同一个 `@HttpExchange` 接口，被多个业务服务以库方式暴露。httpexchange 库按包名映射 URL（一个包 →
一个 URL），无法对同一接口配置多个目标服务。

**解决方案**：不使用 httpexchange 库的包名映射，改用 Spring 6 原生的 `HttpServiceProxyFactory` 为每个服务动态创建 kernel
API 代理。路径从 `@HttpExchange` 注解自动读取，服务名从 DB 路由表动态解析。

### 4.2 DB 路由表

```sql
CREATE TABLE t_bff_route_config (
    id              BIGINT       PRIMARY KEY,
    business_type   VARCHAR(64)  NOT NULL,
    service_name    VARCHAR(128) NOT NULL,
    channel_scope   VARCHAR(32)  NOT NULL DEFAULT 'ALL',
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    description     VARCHAR(256),
    created_by      VARCHAR(64),
    create_time     TIMESTAMP,
    updated_by      VARCHAR(64),
    update_time     TIMESTAMP,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    version         INT          NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_bff_route_business_channel
    ON t_bff_route_config(business_type, channel_scope)
    WHERE deleted = FALSE;
```

`channel_scope` 允许同一业务类型在不同渠道路由到不同服务（默认 `ALL`）。

每个 BFF 维护自己的路由表数据库。两个 BFF 的路由表内容应保持一致（通过管理界面或脚本同步）。

示例数据：

| business_type   | service_name    | channel_scope |
|-----------------|-----------------|---------------|
| ACC_PLAN_CREATE | annuity-service | ALL           |
| ACC_PLAN_MODIFY | annuity-service | ALL           |
| ACC_PLAN_DELETE | annuity-service | ALL           |
| LOAN_APPLY      | loan-service    | ALL           |

### 4.3 BusinessTypeRouter

负责 businessType → serviceName 的解析，带 Caffeine 本地缓存：

```java
@Service
public class BusinessTypeRouter {
    private final BffRouteConfigRepository routeRepo;
    private final Cache<String, String> routeCache;
    private final Cache<String, Set<String>> serviceNamesCache;

    public String resolveServiceName(String businessType, ChannelType channel) {
        return routeCache.get(businessType, key ->
            routeRepo.findByBusinessType(key, channel)
                .orElseThrow(() -> new BusinessException("未找到业务类型路由: " + key))
        );
    }

    public Set<String> getAllServiceNames() { ... }

    public void refresh() { routeCache.invalidateAll(); }
}
```

缓存策略：TTL 5 分钟自动过期 + DB 变更时主动通知刷新（通过 Nacos 配置监听或事件总线）。

### 4.4 KernelApiRegistry

为每个服务动态创建 kernel API 代理的注册表：

```java
public class KernelApiRegistry {
    private final Map<String, BusinessBatchApi> batchApis;
    private final Map<String, BusinessFormApi> formApis;
    private final Map<String, BusinessApplicationApi> applicationApis;
    private final Map<String, MaterialAppApi> materialApis;
    private final Map<String, BusinessProgressApi> progressApis;

    public BusinessBatchApi getBatchApi(String serviceName) {
        return batchApis.get(serviceName);
    }
    // ... 其他 getter
}
```

**初始化工厂**：

```java
@Configuration
public class KernelApiRegistryFactory {

    @Bean
    public KernelApiRegistry kernelApiRegistry(
            @LoadBalanced RestClient.Builder lbBuilder,
            BusinessTypeRouter router) {

        Set<String> serviceNames = router.getAllServiceNames();
        Map<String, BusinessBatchApi> batchApis = new HashMap<>();
        Map<String, BusinessFormApi> formApis = new HashMap<>();
        // ... 其他 kernel API Map

        for (String serviceName : serviceNames) {
            RestClient client = lbBuilder.baseUrl("http://" + serviceName).build();
            HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(client)).build();

            batchApis.put(serviceName, factory.createClient(BusinessBatchApi.class));
            formApis.put(serviceName, factory.createClient(BusinessFormApi.class));
            // ... 其他 kernel API
        }

        return new KernelApiRegistry(batchApis, formApis, ...);
    }
}
```

**工作原理**：

- `factory.createClient(BusinessBatchApi.class)` 读取 `@HttpExchange("/core/batch")` + `@PostExchange("/create")`
  注解，自动构建请求 URL
- `@LoadBalanced RestClient.Builder` 使 `http://annuity-service` 通过 LoadBalancer 解析到实际实例
- 路径从注解自动读取，不硬编码
- 类型安全：直接调用 `BusinessBatchApi.create(CreateBatchCommand)`

### 4.5 服务专属 API 调用

对于服务专属 API（如 AnnuityApi、ApprovalInstanceApi），用标准 httpexchange 包名映射：

```yaml
httpexchange:
  clients:
    com.example.annuity.api:
      url: lb://annuity-service
    com.example.approval.api:
      url: lb://approval-service
    com.example.file.api:
      url: lb://file-service
    com.example.auth.api:
      url: lb://auth-service
```

每个服务的 API 包是唯一的，不存在映射冲突。

### 4.6 两类 API 的调用方式总结

| API 类型        | 调用方式                   | 路径来源           | 服务名来源        |
|-----------------|----------------------------|--------------------|-------------------|
| kernel 公共 API | KernelApiRegistry 动态代理 | @HttpExchange 注解 | DB 路由表         |
| 服务专属 API    | 标准 @HttpExchange 注入    | @HttpExchange 注解 | httpexchange 配置 |

## 五、BFF API 设计

### 5.1 设计原则

- BFF 暴露收敛的 API 接口，不逐个映射 kernel API
- 所有请求携带 `businessType` 作为路由字段
- BFF 的请求/响应 DTO 独立于 kernel DTO，可按渠道裁剪
- 简单路由：直接透传 kernel API 响应
- 聚合场景：BFF 定义聚合响应 DTO

### 5.2 businessType 传递策略

所有 BFF 请求都要求 `businessType` 字段，包括按 ID 查询的场景。理由：

- 用户在操作流程中始终处于某个业务类型上下文，前端天然持有 businessType
- BFF 用 businessType 做路由，后端服务不需要该字段时由 BFF 在转换时剥离
- 避免跨服务反查实体来确定业务类型的 chicken-egg 问题

对于跨业务类型的聚合查询（如"我的所有申请单"），BFF 扇出到多个服务聚合结果。

### 5.3 BFF API 接口示例

```java
@HttpExchange("/bff")
public interface BffBusinessApi {

    @PostExchange("/batch/create")
    ApiResult<BatchCreatedResponse> createBatch(@RequestBody @Valid BffCreateBatchRequest request);

    @PostExchange("/batch/detail")
    ApiResult<BatchDetailResponse> batchDetail(@RequestBody @Valid BffBatchDetailRequest request);

    @PostExchange("/form/upload-token")
    ApiResult<UploadTokenResponse> applyUploadToken(@RequestBody @Valid BffFormTokenRequest request);

    @PostExchange("/application/submit")
    ApiResult<SubmitResponse> submitApplication(@RequestBody @Valid BffSubmitRequest request);

    @PostExchange("/application/detail")
    ApiResult<ApplicationDetailResponse> applicationDetail(@RequestBody @Valid BffApplicationDetailRequest request);

    @PostExchange("/material/list")
    ApiResult<List<MaterialItemResponse>> listMaterials(@RequestBody @Valid BffListMaterialsRequest request);

    @PostExchange("/dashboard/batch-overview")
    ApiResult<BatchOverviewResponse> batchOverview(@RequestBody @Valid BffBatchOverviewRequest request);
}
```

### 5.4 BFF 请求 DTO 示例

```java
public record BffCreateBatchRequest(
    @NotBlank(message = "业务类型不能为空") String businessType,
    @NotBlank(message = "计划编号不能为空") String planNo,
    String operatorRemark
) {
    public CreateBatchCommand toCommand() {
        return new CreateBatchCommand(businessType, planNo, operatorRemark);
    }
}

public record BffBatchDetailRequest(
    @NotBlank String businessType,
    @NotBlank String batchId
) {
    public GetBatchDetailQuery toQuery() {
        return new GetBatchDetailQuery(BatchId.of(batchId));
    }
}
```

### 5.5 BFF Controller 实现

```java
@RestController
@RequiredArgsConstructor
public class BffBusinessController implements BffBusinessApi {
    private final BusinessTypeRouter router;
    private final KernelApiRegistry kernelApiRegistry;
    private final BffAggregationService aggregationService;

    @Override
    public ApiResult<BatchCreatedResponse> createBatch(BffCreateBatchRequest request) {
        String serviceName = router.resolveServiceName(request.businessType(), ChannelType.current());
        BusinessBatchApi batchApi = kernelApiRegistry.getBatchApi(serviceName);
        return batchApi.create(request.toCommand());
    }

    @Override
    public ApiResult<BatchOverviewResponse> batchOverview(BffBatchOverviewRequest request) {
        return aggregationService.getBatchOverview(request);
    }
}
```

### 5.6 聚合服务示例

```java
@Service
@RequiredArgsConstructor
public class BffAggregationService {
    private final BusinessTypeRouter router;
    private final KernelApiRegistry kernelApiRegistry;
    private final ApprovalInstanceApi approvalApi;

    public ApiResult<BatchOverviewResponse> getBatchOverview(BffBatchOverviewRequest request) {
        String serviceName = router.resolveServiceName(request.businessType(), ChannelType.current());

        CompletableFuture<ApiResult<BatchDetailResponse>> batchFuture = CompletableFuture.supplyAsync(() ->
            kernelApiRegistry.getBatchApi(serviceName).detail(request.toBatchQuery()));
        CompletableFuture<ApiResult<BatchProgressResponse>> progressFuture = CompletableFuture.supplyAsync(() ->
            kernelApiRegistry.getProgressApi(serviceName).batchProgress(request.toProgressQuery()));
        CompletableFuture<ApiResult<List<ApplicationSummaryResponse>>> appsFuture = CompletableFuture.supplyAsync(() ->
            kernelApiRegistry.getApplicationApi(serviceName).list(request.toListQuery()));

        CompletableFuture.allOf(batchFuture, progressFuture, appsFuture).join();

        return ApiResult.success(BffResponseAssembler.assemble(
            batchFuture.join().data(), progressFuture.join().data(), appsFuture.join().data()));
    }
}
```

### 5.7 互联网 BFF 与内网 BFF 的 API 差异

两个 BFF 的 API 接口独立定义：

- 互联网 BFF：暴露互联网渠道可操作的接口（受限业务类型、简化操作流程）
- 内网 BFF：暴露网点/总部渠道的完整接口（全部业务类型、审批操作、管理功能）

## 六、网关设计

### 6.1 模块结构

```
gateway-shared/                    # 公共网关组件
├── filter/
│   ├── CryptoFilter.java
│   └── ExcludeRouteFilter.java
├── security/
│   ├── ChannelAwareSaRouter.java
│   ├── SessionContextInjector.java
│   └── GatewayProperties.java
├── crypto/
├── config/
└── order/GatewayFilterOrder.java

internet-gateway/
├── InternetGatewayApplication.java
└── resources/application-local.yml

intranet-gateway/
├── IntranetGatewayApplication.java
└── resources/application-local.yml
```

### 6.2 路由配置

internet-gateway：

```yaml
spring.cloud.gateway.routes:
  - id: internet-bff-route
    uri: lb://internet-bff
    predicates: [Path=/internet/**]
    filters: [StripPrefix=1]
  - id: auth-service-route
    uri: lb://auth-service
    predicates: [Path=/internet/auth/**]
```

intranet-gateway：

```yaml
spring.cloud.gateway.routes:
  - id: intranet-bff-route
    uri: lb://intranet-bff
    predicates: [Path=/hq/**, /branch/**]
    filters: [StripPrefix=1]
  - id: auth-service-route
    uri: lb://auth-service
    predicates: [Path=/hq/auth/**, /branch/auth/**]
```

### 6.3 渠道认证差异

| 网关             | 渠道     | StpLogic          | 白名单路径                                         | 二次授权 |
|------------------|----------|-------------------|----------------------------------------------------|----------|
| internet-gateway | INTERNET | internet StpLogic | /internet/auth/login                               | 不需要   |
| intranet-gateway | BRANCH   | branch StpLogic   | /branch/auth/login, /branch/auth/secondary-auth/** | 需要     |
| intranet-gateway | HQ       | hq StpLogic       | /hq/auth/login                                     | 不需要   |

## 七、审计日志

### 7.1 审计记录内容

| 维度     | 字段                                         | 用途               |
|----------|----------------------------------------------|--------------------|
| 请求入口 | 时间戳、渠道、用户ID、请求路径、businessType | 请求溯源           |
| 路由决策 | 目标服务名、目标API路径、路由命中规则        | 路由排查           |
| 后端调用 | 每次服务调用的耗时、状态码、错误码           | 性能监控、问题定位 |
| 聚合结果 | 聚合了哪些服务、总耗时、最终响应状态         | 行为追溯           |

### 7.2 实现方式

通过 AOP 切面在 BFF Controller 层拦截，异步写入审计表（经 shared-event-starter 事件总线），不阻塞主流程。trace ID 贯穿前端 →
网关 → BFF → 业务服务全链路。

```java
@Aspect
@Component
public class BffAuditAspect {
    private final ApplicationEventPublisher eventPublisher;

    @Around("execution(* com.example.bff.adapter.controller..*.*(..))")
    public Object audit(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = pjp.proceed();
        long elapsed = System.currentTimeMillis() - start;

        eventPublisher.publishEvent(new BffAuditEvent(
            MDC.get("traceId"),
            SessionContext.current(),
            pjp.getSignature().toShortString(),
            extractBusinessType(pjp.getArgs()),
            result instanceof ApiResult<?> r ? r.code() : "UNKNOWN",
            elapsed
        ));
        return result;
    }
}
```

## 八、扩展机制

### 8.1 新增业务类型（现有服务处理）

1. 在 BFF 的 DB 路由表添加一行：`business_type = "LOAN_REPAY", service_name = "loan-service"`
2. BusinessTypeRouter 缓存自动刷新
3. 完成。BFF 代码零修改，不需要重启。

### 8.2 新增业务服务

1. 创建新服务模块，引入 kernel 模块，实现 BusinessTypeRegistrar
2. 在 BFF 的 pom.xml 添加新服务的 api Maven 依赖
3. 在 BFF 的 DB 路由表添加该服务处理的业务类型行
4. 在 BFF 的 application-local.yml 添加 httpexchange 客户端配置
5. 重启 BFF（KernelApiRegistryFactory 重建代理）

### 8.3 新增 BFF 接口/聚合逻辑

1. 在 bff-api 模块新增 BFF API 接口方法 + 请求/响应 DTO
2. 在 bff-adapter 模块实现 Controller 方法
3. 在 bff-application 模块编写路由/聚合逻辑
4. 完成。这是 BFF 的核心价值——定制化编排代码。

## 九、完整请求链路

```
前端请求 {businessType: "ACC_PLAN_CREATE", planNo: "...", ...}
  │
  ├─ X-Session-Context header (gateway 注入)
  ├─ X-Trace-Id header (gateway 生成)
  │
  → internet-gateway
    ├─ sa-token 认证 (INTERNET 渠道)
    ├─ SM4 解密请求体
    ├─ 注入 X-Session-Context (签名)
    └─ 路由到 lb://internet-bff
  │
  → internet-bff
    ├─ BFF Controller 接收请求
    ├─ BusinessTypeRouter: "ACC_PLAN_CREATE" → "annuity-service"
    ├─ KernelApiRegistry: getBatchApi("annuity-service") → BusinessBatchApi 代理
    ├─ 调用 batchApi.create(CreateBatchCommand)
    │   └─ 路径从 @HttpExchange 注解自动读取 → http://annuity-service/core/batch/create
    ├─ BFF 审计 AOP 记录 (异步)
    └─ 返回 ApiResult<BatchCreatedResponse>
  │
  → annuity-service
    ├─ SessionContextResolver 解析 X-Session-Context
    ├─ SupportedBusinessTypeValidator 校验 businessType
    ├─ BusinessAccessGuard 权限校验
    ├─ BusinessBatchController → BusinessBatchAppService → 领域逻辑
    └─ 返回 ApiResult<BatchCreatedResponse>
```

## 十、与现有架构的关系

### 10.1 kernel 不需要修改

kernel 的公共 API 定义（@HttpExchange 接口）和实现（Controller）保持不变。各业务服务继续通过 business-core-adapter 自动暴露
kernel API 端点。BFF 通过 HttpServiceProxyFactory 创建的代理读取相同的 @HttpExchange 注解，调用相同的端点路径。

### 10.2 现有服务不需要修改

各业务服务（annuity-service 等）继续以库方式引入 kernel。服务的 REST 端点路径不变。BFF 的引入不影响服务间的直接调用。

### 10.3 BusinessTypeRegistrar 的双重保障

- BFF 侧：DB 路由表确保请求路由到正确的服务
- 服务侧：SupportedBusinessTypeValidator 确保即使路由错误，服务也会拒绝不支持的 businessType
- 两层校验形成双重保障

### 10.4 demo-gateway 的处理

现有 demo-gateway 保留作为开发环境统一入口（同时处理 3 个渠道）。生产环境使用拆分后的 internet-gateway 和 intranet-gateway。
