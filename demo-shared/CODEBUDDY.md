# demo-shared 模块说明

## 一、模块定位

demo-shared 是整个微服务项目的共享基础设施层，采用 **三层架构** 组织 26 个子模块，为所有业务服务（file-service、approval-service、annuity-service、auth-service 等）和网关/BFF 提供公共能力：

```
shared-lib      → 纯库层，无 Spring 依赖，可被任何 Java 项目引用
shared-core     → 核心逻辑层，选择性依赖 Spring，提供框架级核心实现
shared-starter  → Spring Boot 自动配置层，开箱即用的 starter 封装
```

**核心设计原则**：
- **渐进式依赖**：从 lib → core → starter，依赖逐步增强，业务可按需引入
- **零框架渗透**：shared-lib 中的 domain/types/exception/json 不依赖任何 Spring 注解
- **条件装配**：shared-starter 大量使用 `@ConditionalOnClass`/`@ConditionalOnBean`/`@ConditionalOnProperty`，按classpath和配置动态装配

## 二、三层架构总览

### 2.1 shared-lib（10 个子模块）— 纯库层

| 子模块 | 外部依赖 | 职责 |
|--------|----------|------|
| `shared-types` | 仅 Lombok | 领域原语：`Identifier<T>` 接口、`@IdDefinition` 注解、`IdService` SPI、`CodeEnum<C>`、分页记录、强类型 ID（ApplicationId 等）、联系信息值对象、身份证件 |
| `shared-exception` | 无 | 异常体系：`ErrorDefinition` → `BaseException`(abstract) → `DomainException`/`BusinessException`/`SystemException`；链式上下文 `withUserDetail`/`withLogDetail`/`withContext`；`CommonError` 枚举 |
| `shared-domain` | shared-types + shared-exception | DDD 建材：`Entity<ID>`、`AggregateRoot<ID>`（含领域事件）、`ValueObject`、`Version`（乐观锁）、`@DomainService`、`Repository<T,ID>`、`DomainEvent`、`EventBus`/`IntegrationEventConverter` SPI |
| `shared-api` | spring-web + jakarta.validation | API 基础：`ApiResult<T>` record（code/message/data/timestamp）、`PageData<T>` record、`PageQuery` record |
| `shared-utils` | shared-types + shared-exception + SLF4J(provided) + Lombok | 工具：`TriFunction<T,U,V,R>` 函数接口、`VirtualThreadExecutor`（JDK 24/25 StructuredTaskScope 并发执行器，MDC 传播，信号量限流） |
| `shared-crypto` | shared-exception + KonaCrypto | SM4 加解密：`Encryptor` 接口 → `Sm4Encryptor`（CBC/PKCS7Padding，随机 IV 前置，URL-safe Base64），错误码 SHARED.CRYPTO.0001-0004 |
| `shared-json` | jackson-core(provided) | JSON 流式处理：`JsonFieldProcessor`（token 级流式，路径栈，零中间树，1M 字符体限制），`FieldMatcher`（Simple/JsonPath），`FieldAction` 函数式替换 |
| `shared-lock` | shared-exception + Redisson(optional) + Spring Boot autoconfigure | 分布式锁：`DistributedLock` 接口（tryLock/unlock/lockAndRun 模板），`LocalDistributedLock`（ConcurrentHashMap+ReentrantLock），`RedissonDistributedLock`（RLock），工厂降级：请求 Redis 锁但无 Redisson → 退化为本地锁 |
| `gateway-shared` | Spring Cloud Gateway WebFlux + shared-api + shared-crypto + shared-json + auth-api + Sa-Token Reactive + Redisson | 网关公共：`ChannelType` 枚举、`ChannelAwareSaRouter`（路径前缀→StpLogic 路由）、`SaTokenGatewayConfiguration`、`GatewayStpInterfaceImpl`、`SessionContextInjector` GlobalFilter、`CryptoFilter` WebFilter、`ExcludeRouteFilter`、`FilterOrderChecker` |
| `bff-shared` | business-core-api + annuity-api + shared-api + shared-exception + Spring Web + Spring Boot | BFF 公共：`BffBusinessApi`/`BffBusinessService`/`BffAggregationService`/`BffResponseAssembler` 聚合编排、`BusinessScenarioHandler` 策略接口 + `ScenarioHandlerRegistry`、`AnnuityScenarioHandler`、`BffErrorCode` |

### 2.2 shared-core（7 个子模块）— 核心逻辑层

| 子模块 | 职责 |
|--------|------|
| `shared-cache-core` | 两级缓存框架：`ICacheTemplate` 接口、`LayeredCache`（L1→L2 查找+回填、分布式锁防击穿+双重检查、sync policy 同步失效）、`DynamicCacheManager`（按 CacheRule 动态创建 L1_ONLY/L2_ONLY/MULTI 缓存、Redis 缺失时 MULTI→L1_ONLY 降级）、Jackson/Fury 双序列化策略 |
| `shared-id-core` | ID 生成框架：`IdGenerationStrategy` SPI → `GlobalIdGenerator` 门面；`AnnotationIdMetadataResolver`（解析 `@IdDefinition`，提取泛型类型，校验 Long→仅 SEGMENT/SNOWFLAKE，MethodHandle 构造器）；SEGMENT 策略（双缓冲 `SegmentBufferAllocator`、Caffeine 缓存、分布式锁双重检查、`JdbcSegmentRepository` REQUIRES_NEW 事务乐观更新）；UUID_V7（48bit 毫秒时间戳+随机数）；ULID（Crockford Base32，48bit 时间戳+80bit 随机数）；启动校验器 `IdDefinitionStartupValidator` |
| `shared-logging-core` | 日志管道：`HttpExchangeLog` 纯 POJO（`@JsonRawValue` headers/content）、`LogProcessor` → `SanitizationEngine`（遍历 `LogSanitizer`，单条失败不影响整体）→ `AsyncLogPipeline`（MDC+RequestAttribute 备份上下文，异步脱敏+导出）；`LogExporter` → `DatabaseLogExporter`（PG/MySQL/OceanBase 三库适配）、`FileLogExporter`（SLF4J JSON）；`MethodTimingAspect`（`@WithDurationLogging` AOP，阈值过滤，µs/ms 显示）；5 种脱敏策略（FULL_MASK/PARTIAL_MASK/REPLACE/REMOVE/KEEP）+ 5 种校验器 |
| `shared-client-core` | 外部服务网关基类：`ExternalResult<T>` 接口、`BaseGateway` 抽象类（3 种调用模式）；`SimpleErrorDecoder`（404→NOT_FOUND，其他→REMOTE_SERVICE_ERROR，IO→NETWORK_ERROR）；`EnhancedErrorInterceptor`（OkHttp GlobalInterceptor 丰富 IOException 信息） |
| `shared-web-core` | Web 层公共：`GlobalExceptionHandler`（5 类异常映射）；`RetrofitIOExceptionHandler`（RetrofitIOException→NETWORK_ERROR）；`BizContextAccessor` SPI（`withContext(Map, ThrowableSupplier)` 跨线程上下文传播）；`TraceContextProperties`（双向 alias↔headerKey 映射，O(1) 查找）；`BizContextPropagationFilter`（最高优先级 Servlet Filter，提取 Header→MDC→RequestAttribute 备份→响应写 trace-id）；`ClientIpUtils`（7 级代理 IP 解析链）；追踪拦截器（Feign/OkHttp/Retrofit/Spring Web） |
| `shared-pdf-core` | PDF 生成：`PdfService`（Thymeleaf 渲染→OpenHTMLtoPDF 转换，A4 布局，缓存字体，base URI 解析相对资源）；`PdfFontCache`（ConcurrentHashMap 启动时预加载字体字节到内存）；`BasePdfContext`（`@SuperBuilder` 抽象类，`@JsonIgnore` 元数据）；`PdfMetadata`（丰富领域模型，`merge(override)` 级联默认值）；`PdfProperties` 配置 |
| `shared-permission-starter` | 声明式权限框架：`RequirePermissionAspect`（@Order(1)，fail-closed，解析 accountId/planId）；`HttpExchangePermissionExecutor`（调用 auth-service PermissionCheckApi）；`DataScopeAspect`（@Order(2)，设置 DataScopeContext ThreadLocal）；`PermissionRegistrationRunner`（ApplicationRunner 扫描 @RequirePermission 注解并上报）；`PermissionErrorCode`（SHARED.PERM.0001-0005） |

### 2.3 shared-starter（9 个子模块）— 自动配置层

| Starter | 自动装配核心 Bean |
|---------|-------------------|
| `shared-cache-starter` | `SharedCacheAutoConfiguration`：L1 Caffeine 工厂 + L2 Redis 工厂（内部 `RedisSupportConfiguration`）+ `DynamicCacheManager` + `SharedCacheTemplate`；Redis 同步使用 UUID instanceId + RedisMessageListenerContainer 广播失效；`CacheSerializationConfiguration`：JACKSON（默认，GenericJackson2JsonRedisSerializer + allowedPackages 多态校验）或 FURY（ThreadSafeFury） |
| `shared-client-starter` | `ClientAutoConfiguration`：`@Import({ClientInterceptorConfig, RetrofitLoggingConfig})`；前者注册 `EnhancedErrorInterceptor` @GlobalInterceptor @Order(MIN_VALUE)；后者桥接 Logbook 进 Retrofit |
| `shared-logging-starter` | `SharedLoggingAutoConfiguration`：`@Import({LogCoreConfiguration, LogSanitizationConfiguration, LogExportConfiguration, LogbookIntegrationConfiguration})`；4 个内部配置分别装配异步管道、脱敏引擎（5 策略+5 校验器）、导出器（DB+File）、Logbook 桥接 |
| `shared-event-starter` | `EventAutoConfiguration`：`JdbcEventStore` + `SpringEventDispatcher` + `EventDeliverer` + `DefaultEventBus` + `EventRecoveryJob`（@EnableScheduling）；可选 Redis 分发器（`shared.event.redis.enabled=true`）和 RocketMQ 分发器（`shared.event.rocketmq.enabled=true`）；`DddJacksonModule` 序列化支持 |
| `shared-web-starter` | `WebAutoConfiguration`：`@Import({BizTraceAutoConfiguration, GlobalExceptionHandler})`；前者装配 `MdcBizContextAccessor` + `BizContextPropagationFilter` + `BizContextAspect` + `ExceptionTraceAspect`（SERVLET）+ Feign/Retrofit/Spring Web 追踪拦截器 |
| `shared-id-starter` | `IdAutoConfiguration`：`@Import({DistributedLockConfiguration, StatelessIdConfiguration, SegmentIdConfiguration, IdServiceConfiguration})`；依次装配本地锁+工厂、UUID_V7/ULID 策略、SEGMENT 策略（JdbcSegmentRepository+SegmentBufferAllocator+DefaultSegmentIdService）、AnnotationIdMetadataResolver+IdDefinitionStartupValidator+TypeHandler+GlobalIdGenerator |
| `shared-pdf-starter` | `SharedPdfAutoConfiguration`：`PdfFontCache` + `PdfService`；`@Import(TemplateEngineConfiguration)` 装配 ITemplateResolver（file:/ 或 classpath: 策略）+ 专用 TemplateEngine |
| `shared-crypto-starter` | `CryptoAutoConfiguration`：注册 `KonaCryptoProvider` + `Sm4Encryptor`；条件：`shared.crypto.secret-key` 属性存在 + KonaCrypto classpath 存在 |
| `shared-permission-starter` | `PermissionAutoConfiguration`：AccountIdResolver + PlanIdResolver + SessionContextSignatureVerifier + RequirePermissionAspect（条件 PermissionCheckApi）+ DataScopeAspect（条件 DataScopeResolver） |

**所有 Starter 统一使用 Spring Boot 3.x 自动配置注册方式**：
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

## 三、核心设计模式

### 3.1 强类型 ID 体系（shared-types + shared-id-core + shared-id-starter）

**定义链**：
```
@IdDefinition(strategy = SEGMENT)         // types 层注解声明策略
public record ApplicationId(Long value)   // types 层 record 实现 Identifier<Long>
    implements Identifier<Long> {}

AnnotationIdMetadataResolver              // id-core 解析 @IdDefinition + 泛型
GlobalIdGenerator                         // id-core 门面，调度策略+类型处理器
SegmentBufferAllocator                    // id-core 双缓冲分配器
JdbcSegmentRepository                     // id-core JDBC 乐观更新
IdDefinitionStartupValidator              // id-starter 启动校验（fail-fast）
```

**四种策略**：

| 策略 | 算法 | 适用 ID 类型 | 说明 |
|------|------|-------------|------|
| SEGMENT | 号段双缓冲 | Long | Caffeine 缓存号段，分布式锁+双重检查防击穿，REQUIRES_NEW 事务乐观更新 |
| ULID | Crockford Base32 | String | 48bit 时间戳(10字符) + 80bit 随机数(16字符)，单调递增友好 |
| UUID_V7 | RFC 9562 | String | 48bit 毫秒时间戳 + version + 随机数 |
| SNOWFLAKE | 雪花算法 | Long | 已定义但未实现（启动校验会报错） |

**类型约束**：Long 类型 ID 仅支持 SEGMENT/SNOWFLAKE 策略，String 类型支持 ULID/UUID_V7。

### 3.2 两级缓存框架（shared-cache-core + shared-cache-starter）

**查找流程**：
```
L1(Caffeine) hit → return
L1 miss → L2(Redis) hit → 回填 L1 → return
L2 miss → 分布式锁 → 双重检查 L2 → 仍 miss → loader 加载 → 写 L2 → 写 L1 → 释放锁
```

**动态缓存管理**：
- `DynamicCacheManager` 按 CacheRule 为每个 cacheName 动态创建缓存实例
- 三种模式：`L1_ONLY`（Caffeine）、`L2_ONLY`（Redis）、`MULTI`（两级）
- 降级：请求 MULTI 模式但 Redis 不可用时，自动降级为 `L1_ONLY`

**缓存同步**：
- Redis pub/sub 广播 `CacheEvictMessage`（含 UUID instanceId 防止回声）
- 收到消息后 L1 本地失效

**序列化**：
- JACKSON（默认）：GenericJackson2JsonRedisSerializer + `allowedPackages` 白名单校验多态类型
- FURY（可选）：ThreadSafeFury 高性能序列化

### 3.3 DDD 领域事件管道（shared-domain + shared-event-starter）

**发布-存储-分发三层**：
```
聚合根.registerDomainEvent(event)       // domain 层注册
ApplicationService:
  repository.save(aggregate)             // application 层持久化
  aggregate.getDomainEvents()            // 取出事件
  eventBus.publish(events)               // 发布到 EventBus
  aggregate.clearDomainEvents()          // 清理

DefaultEventBus:
  eventStore.store(events)               // 1. 持久化到 sys_event_store
  dispatcher.dispatch(events)            // 2. Spring ApplicationEventPublisher 同步分发
  deliverer.deliver(events)              // 3. 异步投递到 MQ

EventDeliverer:
  converter.toIntegrationEvent(event)    // 领域事件 → 集成事件（SPI 转换）
  dispatcher.dispatch(integrationEvent)  // 投递到 Redis/RocketMQ

EventRecoveryJob:
  @Scheduled(fixedDelay)                 // 定时扫描 PENDING 状态事件重投
```

### 3.4 日志脱敏管道（shared-logging-core + shared-logging-starter）

**请求/响应处理流**：
```
HTTP 请求/响应 → Logbook Sink → LogbookSinkAdapter → LogProcessor
  → AsyncLogPipeline（异步）
    → SanitizationEngine
      → HeaderSanitizer（@Order(2)）
      → QueryParamSanitizer（@Order(3)）
      → JsonBodySanitizer（@Order(1)）
          → SanitizationContext（规则索引）
            → ValueSanitizer
              → SanitizationStrategyFactory
                → FULL_MASK / PARTIAL_MASK / REPLACE / REMOVE / KEEP
    → LogExporterDispatcher
      → DatabaseLogExporter（PG/MySQL/OceanBase）
      → FileLogExporter（SLF4J JSON）
```

**脱敏策略**：

| 策略 | 效果 | 配置 |
|------|------|------|
| FULL_MASK | 全部替换为 `***` | `type: FULL_MASK` |
| PARTIAL_MASK | 保留首尾字符，中间替换 | `type: PARTIAL_MASK, retainPrefix: 3, retainSuffix: 4, maskChar: *` |
| REPLACE | 替换为固定值 | `type: REPLACE, replacement: [REDACTED]` |
| REMOVE | 移除整个字段 | `type: REMOVE` |
| KEEP | 原样保留（用于白名单） | `type: KEEP` |

### 3.5 分布式锁降级模式（shared-lock）

```
DistributedLockFactory.create(name, LockType)
  → 请求 Redis 锁
    → Redisson 在 classpath？
      → 是：返回 RedissonDistributedLock
      → 否：降级返回 LocalDistributedLock + WARN 日志
```

所有业务代码只依赖 `DistributedLock` 接口，不感知底层实现。

### 3.6 声明式权限框架（shared-permission-starter）

**运行时流程**：
```
@RequirePermission("annuity:apply:submit")
public ApiResult<...> submit(...) { ... }

→ RequirePermissionAspect (@Order(1))
  → 解析 accountId (AccountIdResolver) + planId (PlanIdResolver)
  → PermissionExecutor.check(accountId, planId, permission)
    → HttpExchangePermissionExecutor
      → PermissionCheckApi (调用 auth-service)
        → 正常：放行
        → 权限不足：抛 PERMISSION_DENIED
        → 服务不可用：抛 PERMISSION_SERVICE_UNAVAILABLE

→ DataScopeAspect (@Order(2)) [可选]
  → DataScopeResolver.resolve(accountId, planId, permission)
  → DataScopeContext.set(dataScope)
  → 业务代码通过 DataScopeContext 获取数据范围
  → finally: DataScopeContext.clear()
```

**权限自注册**：
```
PermissionRegistrationRunner (ApplicationRunner)
  → 扫描 RequestMappingHandlerMapping 中所有 @RequirePermission 注解
  → 提取 permission + method + path
  → 调用 auth-service 上报权限描述符
  → fail-soft：上报失败仅 WARN，不阻塞启动
```

## 四、跨层依赖关系

```
shared-types ◄──── shared-domain, shared-api, shared-utils, shared-lock
shared-exception ◄── shared-domain, shared-utils, shared-crypto, shared-lock
shared-domain ◄──── shared-lock, shared-id-core, shared-event-starter
shared-api ◄─────── gateway-shared, bff-shared
shared-json ◄────── gateway-shared
shared-crypto ◄──── gateway-shared, shared-crypto-starter
shared-lock ◄────── shared-id-starter, shared-cache-starter, shared-event-starter
shared-cache-core ◄─ shared-cache-starter
shared-id-core ◄─── shared-id-starter
shared-logging-core ◄─ shared-logging-starter
shared-client-core ◄─ shared-client-starter
shared-web-core ◄─── shared-web-starter
shared-pdf-core ◄─── shared-pdf-starter
```

## 五、异常体系

```
ErrorDefinition (interface)
  └── BaseException (abstract)
        ├── DomainException  — fillInStackTrace() 被覆写为 return this（无栈填充，高性能）
        ├── BusinessException — WARN 级别日志
        └── SystemException   — ERROR 级别日志

CommonError 枚举：
  COMMON.0000  操作成功
  COMMON.0001  请求参数错误
  COMMON.0002  未登录或登录已过期
  COMMON.0003  无权限访问
  COMMON.0004  请求资源不存在
  COMMON.0005  请求方法不允许
  COMMON.0006  请求过于频繁
  COMMON.0050  系统内部错误
  COMMON.0051  服务降级
  COMMON.0052  第三方服务调用失败
  COMMON.0053  网络异常
  COMMON.0054  并发错误
  COMMON.0055  请求超时
  COMMON.0099  未知错误
```

**链式上下文模式**：
```java
throw new BusinessException(CommonError.BAD_REQUEST)
    .withUserDetail("工号格式不正确")      // 展示给用户
    .withLogDetail("userNo=Z123, 长度=4")  // 仅写日志
    .withContext("field", "userNo");       // 结构化上下文

exception.displayMessage()  → "工号格式不正确"
exception.logMessage()      → "请求参数错误" + logContext
```

## 六、虚拟线程执行器（shared-utils）

`VirtualThreadExecutor` 基于 JDK 24/25 的 `StructuredTaskScope`，提供三种并发模式：

| 模式 | 方法 | 说明 |
|------|------|------|
| Fire-and-forget | `runAsync(Runnable)` | 不等待结果，MDC 传播 |
| 异构组合 | `combine(Task<T1>, Task<T2>)` / `combine3(...)` | 2 或 3 个不同类型任务并行，全部成功时返回 Tuple |
| 同构批量 | `invokeAll(List<Task<T>>, maxConcurrency)` | 信号量限制并发度，全部成功时返回 List<T> |

所有模式均自动传播 MDC 上下文到子线程。

## 七、JSON 流式处理框架（shared-json）

`JsonFieldProcessor` 提供零中间树的 JSON 字段流式处理：

```java
new JsonFieldProcessor()
    .withBodySizeLimit(1_000_000)   // 最大 1M 字符
    .addMatcher(new SimpleFieldMatcher(Set.of("password", "token")))
    .addAction((path, value) -> "***")  // 返回非 null 替换，null 保留原值
    .process(jsonString, writer);
```

**两种匹配器**：
- `SimpleFieldMatcher`：字段名集合匹配，O(1) 查找
- `JsonPathFieldMatcher`：JSON Path 表达式匹配，支持 `[*]` 数组展平

**路径匹配算法**（`PathMatcher`）：反向回溯，从叶子节点向根匹配，支持通配符。

## 八、SM4 加密（shared-crypto）

```
加密流程:  生成随机 16 字节 IV → SM4/CBC/PKCS7Padding 加密 → IV + 密文 → URL-safe Base64
解密流程:  URL-safe Base64 解码 → 前 16 字节 IV → 剩余密文 → SM4/CBC/PKCS7Padding 解密
```

使用腾讯 KonaCrypto 作为 JCE Provider，密钥通过 `shared.crypto.secret-key` 配置。

## 九、网关公共组件（gateway-shared）

**多渠道认证路由**：
```
ChannelType(internet/intranet/merchant)
  → ChannelAwareSaRouter: 路径前缀 /api/internet/** → internetStpLogic
  → SaTokenGatewayConfiguration: 注册三个 StpLogic + ChannelAwareSaRouter
  → GatewayStpInterfaceImpl: 权限列表获取
  → SessionContextInjector(GlobalFilter): 注入会话上下文到请求头
```

**加密过滤器**：
```
CryptoFilter(WebFilter): 请求体 SM4 解密 → 响应体 SM4 加密
```

**过滤器顺序检查**：
```
FilterOrderChecker: 启动时校验过滤器顺序一致性
```

## 十、BFF 公共组件（bff-shared）

**聚合编排模式**：
```
BffBusinessService: 通用业务查询（单个业务服务调用）
BffAggregationService: 聚合查询（多个业务服务并行调用 + BffResponseAssembler 组装）

ScenarioHandlerRegistry + BusinessScenarioHandler:
  按 scenario 路由到具体处理器（如 AnnuityScenarioHandler）
```

## 十一、配置属性一览

| Starter | 配置前缀 | 关键属性 |
|---------|----------|----------|
| shared-cache-starter | `shared.cache` | mode(L1_ONLY/L2_ONLY/MULTI), serializerType(JACKSON/FURY), rules[name→CacheRule], allowedPackages |
| shared-id-starter | `shared.id.segment` | initial, step, bizType→seqKey 映射 |
| shared-logging-starter | `shared.logging.timing.enable`, `shared.logging.database.enable`, `shared.logging.file.enable`, `logbook.obfuscate.global.enable` | 计时开关、DB导出开关、文件导出开关、脱敏开关 |
| shared-event-starter | `shared.event.redis.enabled`, `shared.event.rocketmq.enabled`, `shared.event.rocketmq.topic` | Redis/RocketMQ 分发器开关 |
| shared-pdf-starter | `shared.pdf` | templatePrefix, templateSuffix, baseUri, defaultMetadata, fonts |
| shared-crypto-starter | `shared.crypto.secret-key` | SM4 密钥（必填） |
| shared-web-starter | `trace.context` | headers→alias 双向映射 |
| shared-permission-starter | — | 通过 `@ConditionalOnBean(PermissionCheckApi.class)` 激活 |

## 十二、已知设计决策与待确认问题

1. **SNOWFLAKE 策略未实现**：`IdGenerationStrategy` 中定义了 SNOWFLAKE 枚举值，但无对应实现，`IdDefinitionStartupValidator` 会对其报错。是否计划实现？
2. **VirtualThreadExecutor 依赖 JDK 24+**：使用 `StructuredTaskScope`，需要在 JDK 24/25 上运行，且需 `--enable-preview`。是否所有部署环境都满足？
3. **shared-lock Redis 缺失时静默降级**：请求 Redis 锁但 classpath 无 Redisson 时，降级为本地锁仅打印 WARN 日志。生产环境是否需要更严格的告警？
4. **shared-event-starter EventRecoveryJob 调度频率硬编码**：`@Scheduled(fixedDelay=...)` 的值是否需要通过配置暴露？
5. **shared-logging-starter 脱敏规则配置复杂度**：5 种策略 × 5 种校验器，配置项较多。是否需要提供预设模板？
6. **shared-cache-starter MULTI 降级无明确指标**：Redis 不可用时 MULTI→L1_ONLY 降级，是否有健康检查指标暴露？
7. **shared-permission-starter fail-soft 注册**：权限上报失败仅 WARN，是否可能在 auth-service 重启后补注册？
8. **shared-json PathMatcher 反向回溯**：对超深嵌套 JSON 是否有性能风险？
9. **shared-id-starter SEGMENT 号段耗尽**：`SHARED.ID.0005` 号段耗尽异常，是否有监控和预扩容机制？
10. **bff-shared ScenarioHandlerRegistry 无动态注册**：场景处理器在启动时固定，是否需要运行时动态扩展？
