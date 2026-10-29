# internet-gateway — Legacy Explain 文档

> 由 `/legacy-explain` 自动生成，最后更新：2026-09-08

---

## 1. 模块定位

`internet-gateway` 是互联网渠道的 API 网关，基于 Spring Cloud Gateway（WebFlux）构建，作为所有互联网客户端请求的唯一入口。职责：

1. **多渠道认证** — 基于 `gateway-shared` 的三通道 Sa-Token 认证体系，互联网网关仅启用 `INTERNET` 渠道
2. **会话上下文注入** — 认证通过后将 Token-Session 信息签名后透传至下游 BFF/业务服务
3. **路由转发** — 将 `/internet/**` 路径前缀的请求路由至 `internet-bff`，将 `/internet/auth/**` 路由至 `auth-service`
4. **报文加解密** — 可选的 SM4 字段级加解密（默认关闭）
5. **路由黑名单** — 可选的路径/方法级路由排除与状态码返回
6. **CORS 处理** — 允许互联网前端跨域访问

---

## 2. 架构概览

```
┌───────────────────────────────────────────────────────────┐
│                   Internet Client (Browser)                │
│        携带 satoken-internet Header 请求 /internet/**      │
└──────────────────────┬────────────────────────────────────┘
                       │
┌──────────────────────▼────────────────────────────────────┐
│                  internet-gateway (18081)                  │
│                                                           │
│  ┌─────────────────────────────────────────────────────┐  │
│  │              WebFilter Chain (按 Order 排序)          │  │
│  │                                                     │  │
│  │  -300  IP_BLOCK        (预留: IP 黑名单)             │  │
│  │  -250  EXCLUDE_ROUTE   (路由黑名单过滤)              │  │
│  │  -200  AUTH            (Sa-Token 认证)               │  │
│  │  -150  SESSION_CONTEXT (会话上下文签名注入)           │  │
│  │  -100  RATE_LIMIT      (预留: 限流)                  │  │
│  │   -50  TENANT_RESOLVE  (预留: 租户解析)              │  │
│  │   -10  CRYPTO          (SM4 报文加解密, 默认关闭)    │  │
│  │     0  REQUEST_LOG     (预留: 请求日志)              │  │
│  │   200  RESPONSE_REWRITE(预留: 响应重写)              │  │
│  └─────────────────────────────────────────────────────┘  │
│                                                           │
│  ┌─────────────────────────────────────────────────────┐  │
│  │            Spring Cloud Gateway Routes               │  │
│  │                                                     │  │
│  │  /internet/auth/**  →  auth-service  (StripPrefix=1) │  │
│  │  /internet/**       →  internet-bff  (StripPrefix=1) │  │
│  └─────────────────────────────────────────────────────┘  │
└──────────────────────┬────────────────────────────────────┘
                       │
          ┌────────────┴────────────┐
          ▼                         ▼
   ┌──────────────┐         ┌──────────────┐
   │  auth-service │         │ internet-bff │
   │  (认证服务)   │         │  (互联网BFF) │
   └──────────────┘         └──────────────┘
```

---

## 3. 依赖关系

### 3.1 Maven 依赖

| 依赖 | 用途 |
|------|------|
| `gateway-shared` | 核心网关能力：认证、会话注入、加解密、路由排除、过滤器排序 |
| `httpexchange-spring-boot-autoconfigure` | `@EnableExchangeClients` 扫描 auth-service API 接口代理 |
| `spring-cloud-starter-sofa-registry` | Nacos 服务发现 + 负载均衡路由 |

### 3.2 组件扫描

- `@SpringBootApplication(scanBasePackages = "com.example.gateway")` — 扫描 `gateway-shared` 下的所有组件
- `@EnableExchangeClients(basePackages = {"com.example.auth.api"})` — 注册 auth-service 的 `@HttpExchange` 接口代理 Bean

---

## 4. 核心组件详解（来自 gateway-shared）

### 4.1 GatewayFilterOrder — 过滤器执行顺序枚举

| 枚举值 | Order | 说明 |
|--------|-------|------|
| `IP_BLOCK` | -300 | IP 黑名单（预留） |
| `EXCLUDE_ROUTE` | -250 | 路由黑名单过滤 |
| `AUTH` | -200 | Sa-Token 认证 |
| `SESSION_CONTEXT_INJECT` | -150 | 会话上下文签名注入 |
| `RATE_LIMIT` | -100 | 限流（预留） |
| `TENANT_RESOLVE` | -50 | 租户解析（预留） |
| `CRYPTO` | -10 | SM4 报文加解密 |
| `REQUEST_LOG` | 0 | 请求日志（预留） |
| `RESPONSE_REWRITE` | 200 | 响应重写（预留） |

`FilterOrderChecker` 在应用启动时校验：
1. 所有 `com.example.*` 包下的 `GlobalFilter` 和 `WebFilter` 必须实现 `Ordered`
2. 所有 order 值必须在 `GatewayFilterOrder` 枚举中定义
3. 不允许两个过滤器使用同一个 order 值（冲突则启动失败）

### 4.2 SaTokenGatewayConfiguration — 认证过滤器

`SaReactorFilter` 在 order=-200 注册，核心认证逻辑：

```
请求进入
  │
  ├─ path 在 auth.gateway.public-paths 白名单中？
  │    └─ YES → skip（放行）
  │
  ├─ channelAwareSaRouter.matchChannel(path) 匹配到渠道？
  │    ├─ YES → getStpLogic(channel).checkLogin()
  │    │        （仅检查 INTERNET 渠道，因为 gateway.channels.enabled=[INTERNET]）
  │    └─ NO  → StpUtil.checkLogin()（默认 StpLogic，遍历所有已启用渠道的 tokenHeader）
  │
  └─ 认证失败 → handleError(e)
       ├─ NotLoginException      → HTTP 401 + {code:"COMMON.0002", message:"未登录或登录已过期"}
       ├─ NotPermissionException → HTTP 403 + {code:"COMMON.0003", message:"无权限访问"}
       ├─ NotRoleException       → HTTP 403 + {code:"COMMON.0003", message:"无权限访问"}
       └─ 其他异常               → HTTP 500 + {code:"COMMON.0050", message:"系统内部错误"}
```

**Sa-Token 1.45.0 兼容性处理**：`setStatus(int)` 在 Spring Framework 6.2+ 下不生效，直接通过 `SaResponse.getSource()` 获取 `ServerHttpResponse` 并调用 `setStatusCode(HttpStatusCode.valueOf(...))`。

### 4.3 ChannelAwareSaRouter — 多渠道 StpLogic 管理

构造时遍历 `GatewayChannelProperties.enabled`，为每个渠道创建独立的 `StpLogic`：

| 渠道 | loginType | tokenName | isReadHeader | isReadCookie |
|------|-----------|-----------|--------------|--------------|
| INTERNET | "internet" | "satoken-internet" | true | false |
| HQ | "hq" | "satoken-hq" | true | false |
| BRANCH | "branch" | "satoken-branch" | true | false |

`configureDefaultStpLogic()` 覆盖 `StpUtil.getTokenValueNotCut()`，使其遍历所有已启用渠道的 tokenHeader，支持非渠道前缀路径的多 Header 识别。

`matchChannel(path)` 两步校验：
1. `ChannelType.fromPath(path)` 非空（路径前缀匹配）
2. `enabledChannels.contains(channel)` （渠道已启用）

internet-gateway 配置 `gateway.channels.enabled: [INTERNET]`，因此只有 `/internet/**` 路径走渠道 StpLogic，其他路径走默认 StpLogic。

### 4.4 SessionContextInjector — 会话上下文签名注入

`GlobalFilter` 在 order=-150 执行，认证通过后：

1. **剥离敏感 Header**：移除 `satoken`、`satoken-internet`、`satoken-hq`、`satoken-branch`、`Authorization` 五个 Header，防止客户端伪造
2. **读取 Token-Session**：从当前渠道的 `StpLogic` 获取 `loginId` 和 `currentPlanId`
3. **构建会话上下文**：Base64 编码为 `X-Session-Context`
4. **HMAC-SHA256 签名**：
   - `X-Account-Id` = loginId（明文，供下游快速读取）
   - `X-Account-Sig` = HMAC-SHA256(loginId, signatureKey)
   - `X-Session-Sig` = HMAC-SHA256(sessionContext, signatureKey)
   - `X-Session-Expire` = 当前时间 + ttlSeconds（默认 300s）
5. **降级模式**：若 `signatureKey` 为空，仅写入 `X-Account-Id`（开发环境使用，生产环境必须配置签名密钥）

下游服务通过 `SessionContextResolver`（`core-kernel` 提供）验证签名并解析会话上下文。

### 4.5 GatewayStpInterfaceImpl — 网关层角色/权限

| 方法 | 返回值 | 说明 |
|------|--------|------|
| `getPermissionList()` | `[]` | 网关不做细粒度权限校验，下游服务通过 `@RequirePermission` 实时调用 auth-service |
| `getRoleList()` | 按 loginType 映射 | internet→["operator"], hq→["staff"], branch→["teller"] |

### 4.6 ExcludeRouteFilter — 路由黑名单过滤

`WebFilter` 在 order=-250 执行：

1. 使用 `ExcludeRouteMatcher.match(path, method)` 匹配规则
2. 匹配成功 → 返回规则指定的 HTTP 状态码（默认使用 `ExcludeRouteProperties.defaultStatus`）
3. 匹配失败 → 继续过滤器链

**配置绑定**（`gateway.exclude-routes`）：
```yaml
gateway:
  exclude-routes:
    default-status: 403
    rules:
      - path: /admin/**
        methods: [GET]
        status: 404
      - path: /private/**
        methods: []       # 空列表 = 匹配所有方法
        # status: null    # 使用 default-status
```

**规则校验**：
- `methods` 中的值必须是合法 HTTP 方法（大写化后校验）
- `status` 必须是合法 HTTP 状态码（Spring `HttpStatus.resolve()` 校验）
- `status=null` 允许（使用全局默认值）

### 4.7 CryptoFilter — SM4 字段级加解密

`WebFilter` 在 order=-10 执行（默认关闭，`gateway.crypto.enabled: false`）：

1. **前置检查**：功能是否启用、是否有字段配置、是否为排除路径、Content-Type 是否为 JSON
2. **请求解密**：`processor.process(requestBody, cryptoPolicy.decryptAction())` — 遍历 JSON，匹配字段名，调用 SM4 解密
3. **响应加密**：`processor.process(responseBody, cryptoPolicy.encryptAction())` — 遍历 JSON，匹配字段名，调用 SM4 加密

**配置绑定**（`gateway.crypto`）：
```yaml
gateway:
  crypto:
    enabled: true
    secret-key: "your-sm4-key"
    exclude-paths:
      - /public/**
    fields:
      idCard:
        aliases: [idNo, certNo]
      phone:
        aliases: [mobile, tel]
```

**CryptoPolicy 策略接口**：
- `encryptAction()` → 返回 `FieldAction`（后端明文 → 前端密文）
- `decryptAction()` → 返回 `FieldAction`（前端密文 → 后端明文）

**Sm4CryptoPolicy 实现**：
- 基于 `Encryptor` 接口，默认使用 `Sm4Encryptor`
- 容错策略：加解密失败返回 null（保留原值），空字符串不处理

**CryptoConfiguration 装配**：
- `gateway.crypto.enabled=true` → 装配 `Sm4Encryptor`（secretKey 必须配置）
- `gateway.crypto.enabled=false` → 装配 `NoopEncryptor`（透传，方法不会被实际调用）
- `JsonFieldProcessor` + `SimpleFieldMatcher` 始终装配（基于字段名+别名匹配）
- `CryptoPolicy` 始终为 `Sm4CryptoPolicy`（当 NoopEncryptor 时，加解密为透传操作）

---

## 5. 配置文件详解

### 5.1 application.yml

```yaml
server:
  port: 18081

spring:
  threads:
    virtual:
      enabled: true          # 启用虚拟线程

  data:
    redis:
      host: localhost
      port: 6379
      database: 0

  cloud:
    gateway:
      globalcors:            # CORS 配置
        cors-configurations:
          '[/**]':
            allowed-origins: ["http://localhost:3000", "http://localhost:5173"]
            allowed-methods: [GET, POST, PUT, DELETE, OPTIONS]
            allowed-headers: [Content-Type, Authorization, satoken, satoken-internet]
            allow-credentials: true

sa-token:                     # Sa-Token 基础配置
  token-name: satoken         # 默认 token header 名
  is-read-header: true
  is-read-cookie: false       # 前后端分离，禁用 cookie
  is-concurrent: true
  is-share: true
  token-style: uuid
  token-active-timeout: -1    # 永不过期（由业务控制）

gateway:
  crypto:
    enabled: false            # SM4 加解密默认关闭
  channels:
    enabled: [INTERNET]       # 仅启用互联网渠道

auth:
  gateway:
    public-paths:             # 认证白名单
      - /actuator/**
      - /favicon.ico
      - /internet/auth/login  # 互联网登录接口
```

### 5.2 application-local.yml

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
        namespace: dev
    gateway:
      routes:
        # 互联网 BFF 路由
        - id: internet-bff-route
          uri: lb://internet-bff
          predicates:
            - Path=/internet/**
          filters:
            - StripPrefix=1
            - AddRequestHeader=X-Gateway-Source, internet-gateway

        # 认证服务路由（优先匹配）
        - id: auth-service-internet-route
          uri: lb://auth-service
          predicates:
            - Path=/internet/auth/**
          filters:
            - StripPrefix=1

permission:
  session:
    signature-key: dev-session-signature-key-2026
    ttl-seconds: 300

logging:
  level:
    com.example: debug
```

### 5.3 application-test.yml

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
      - org.redisson.spring.starter.RedissonAutoConfiguration
      - com.alibaba.cloud.nacos.discovery.NacosDiscoveryAutoConfiguration
      - com.alibaba.cloud.nacos.NacosServiceAutoConfiguration
      - com.alibaba.cloud.nacos.registry.NacosServiceRegistryAutoConfiguration
      - com.alibaba.cloud.nacos.endpoint.NacosConfigEndpointAutoConfiguration
      - org.springframework.cloud.gateway.discovery.GatewayDiscoveryClientAutoConfiguration
      - org.springframework.cloud.client.discovery.composite.CompositeDiscoveryClientAutoConfiguration
      - org.springframework.cloud.client.discovery.simple.SimpleDiscoveryClientAutoConfiguration
  cloud:
    gateway:
      discovery:
        locator:
          enabled: false

gateway:
  channels:
    enabled: [INTERNET]

auth:
  gateway:
    public-paths:
      - /actuator/**
      - /favicon.ico
      - /internet/auth/login
```

---

## 6. 请求生命周期

### 6.1 互联网用户登录

```
Browser → POST /internet/auth/login
  → CORS 检查通过
  → ExcludeRouteFilter: 不命中黑名单 → pass
  → SaReactorFilter: /internet/auth/login 在白名单中 → skip
  → 路由匹配: auth-service-internet-route → StripPrefix=1
  → auth-service: POST /auth/login → 返回 satoken-internet token
```

### 6.2 认证后的业务请求

```
Browser → GET /internet/business/applications (Header: satoken-internet=xxx)
  → CORS 检查通过
  → ExcludeRouteFilter: 不命中 → pass
  → SaReactorFilter: matchChannel("/internet")=INTERNET → StpLogic(internet).checkLogin()
  → SessionContextInjector:
      1. 剥离 satoken-internet Header
      2. 读取 loginId + currentPlanId
      3. Base64 编码 → X-Session-Context
      4. HMAC-SHA256 签名 → X-Account-Sig, X-Session-Sig, X-Session-Expire
      5. 写入 X-Account-Id
  → 路由匹配: internet-bff-route → StripPrefix=1, AddRequestHeader(X-Gateway-Source, internet-gateway)
  → internet-bff: GET /business/applications
      → SessionContextResolver 验证签名 → 解析出 loginId, currentPlanId
      → 业务处理 → 返回响应
  → CryptoFilter: enabled=false → 不处理响应
  → 返回给 Browser
```

### 6.3 未认证请求

```
Browser → GET /internet/business/applications (无 satoken-internet Header)
  → SaReactorFilter: matchChannel("/internet")=INTERNET → StpLogic(internet).checkLogin()
  → 抛出 NotLoginException
  → handleError: HTTP 401 + {"code":"COMMON.0002","message":"未登录或登录已过期"}
```

---

## 7. 测试覆盖

### 7.1 internet-gateway 自身测试

| 测试类 | 覆盖内容 |
|--------|----------|
| `InternetGatewayApplicationTest` | 上下文启动验证（禁用 Redis/Nacos） |

### 7.2 gateway-shared 测试（internet-gateway 间接依赖）

| 测试类 | 覆盖内容 | 测试数 |
|--------|----------|--------|
| `ChannelTypeTest` | 枚举常量、`fromPath()` 路径匹配 | 15 |
| `GatewayStpInterfaceImplTest` | 角色映射、空权限列表 | 5 |
| `SaTokenGatewayConfigurationTest` | SaReactorFilter 创建、initDefaultStpLogic、handleError 异常映射 | 6 |
| `ChannelAwareSaRouterTest` | 构造函数初始化、getStpLogic、getChannelByLoginType、enabledChannels | 15 |
| `ChannelAwareSaRouterMatchChannelTest` | matchChannel 路径匹配 | 7 |
| `GatewayPropertiesTest` | 白名单 AntPathMatcher | 5 |
| `ExcludeRouteFilterTest` | 未命中放行、自定义状态码拦截、默认状态码拦截 | 3 |
| `ExcludeRouteMatcherTest` | 路径+方法匹配、方法为空匹配所有、顺序 | 5 |
| `ExcludeRoutePropertiesTest` | 配置绑定、默认构造、HTTP 方法/状态码校验 | 6 |
| `ExcludeRoutePropertiesBindingTest` | Spring Boot 配置绑定集成测试 | 1 |

总计约 68 个单元测试。

---

## 8. 与 intranet-gateway 的对比

| 维度 | internet-gateway | intranet-gateway |
|------|------------------|------------------|
| 端口 | 18081 | 18082 |
| 启用渠道 | `[INTERNET]` | `[HQ, BRANCH]` |
| 白名单路径 | `/internet/auth/login` | `/hq/auth/login`, `/branch/auth/login` |
| CORS | 启用（浏览器前端） | 启用（内部系统前端） |
| SM4 加解密 | 默认关闭 | 默认关闭 |
| 路由目标 | internet-bff, auth-service | intranet-bff, auth-service |
| 认证逻辑 | `/internet/**` → `StpLogic(internet).checkLogin()` | `/hq/**` → `StpLogic(hq)`, `/branch/**` → `StpLogic(branch)` |
| ExchangeClients | `com.example.auth.api` | `com.example.auth.api` |
| 核心差异 | 单渠道，简单路径匹配 | 双渠道，需要区分总部/分支机构认证 |

---

## 9. 待确认问题

1. **路由黑名单未配置**：internet-gateway 的 `application.yml` 和 `application-local.yml` 均未配置 `gateway.exclude-routes`，`ExcludeRouteFilter` 和 `ExcludeRouteMatcher` 虽然会被 Spring 实例化（因为 `@Component`），但 `ExcludeRouteProperties.rules` 默认为空列表，过滤器实际不拦截任何请求。这是否有意为之？是否应在生产环境配置黑名单？
2. **Sa-Token 默认 token-name 的作用**：`application.yml` 配置了 `sa-token.token-name: satoken`，但 internet-gateway 只启用了 `INTERNET` 渠道（tokenHeader=satoken-internet）。默认 token-name 在 `configureDefaultStpLogic()` 中被覆盖为遍历所有已启用渠道 tokenHeader 的逻辑。那 `sa-token.token-name: satoken` 的配置在互联网网关中是否还有实际意义？是否会造成混淆？
3. **CORS allowed-headers 是否应包含 satoken-hq 和 satoken-branch**：当前 CORS 配置 `allowed-headers` 仅包含 `satoken` 和 `satoken-internet`。如果未来某个前端同时访问互联网和内联网关，浏览器预检请求可能因缺少其他渠道 token header 而失败。是否需要统一添加所有渠道 token header？
4. **CryptoFilter 默认关闭下的 NoopEncryptor**：当 `gateway.crypto.enabled=false` 时，`CryptoConfiguration` 装配 `NoopEncryptor`，同时仍装配 `Sm4CryptoPolicy` 和 `JsonFieldProcessor`。`CryptoFilter` 虽然在 enabled=false 时直接跳过处理，但这些 Bean 仍会创建。是否有必要在 enabled=false 时完全跳过 Crypto 相关 Bean 的装配？
5. **SessionContextInjector 降级模式的安全风险**：当 `permission.session.signature-key` 为空时，`SessionContextInjector` 仅写入 `X-Account-Id` 而不签名。虽然文档标注为"开发环境使用"，但缺少启动时告警或强制校验。是否应在签名密钥为空且非 local profile 时拒绝启动或打印 WARN 日志？
6. **FilterOrderChecker 启动失败策略**：`FilterOrderChecker` 在 order 不合法或冲突时抛出 `IllegalStateException` 导致应用启动失败。这在开发阶段可能过于严格（新增过滤器忘记注册枚举值就直接启动失败）。是否考虑降级为 WARN 日志 + 开发模式开关？
7. **auth-service HTTP Exchange 代理的用途**：internet-gateway 配置了 `@EnableExchangeClients(basePackages = {"com.example.auth.api"})`，这会在网关中生成 auth-service API 接口的客户端代理 Bean。但当前路由规则中 `/internet/auth/**` 直接转发至 auth-service，网关本身并没有 Controller 调用这些代理。这些代理 Bean 是否有实际使用场景？还是仅为未来网关层直接调用 auth-service API 做预留？
8. **gateway.crypto.fields 配置的匹配精度**：`CryptoProperties.fields` 使用 `Map<String, FieldConfig>` 配置字段名和别名，`SimpleFieldMatcher` 仅按字段名匹配（不区分路径）。如果请求/响应 JSON 中多个层级存在相同字段名（如 `name`），所有层级都会被加解密，可能误伤非敏感字段。是否需要支持 JSONPath 级别的精确匹配？
9. **虚拟线程在 WebFlux 中的影响**：internet-gateway 启用了 `spring.threads.virtual.enabled=true`，但 Spring Cloud Gateway 基于 Reactor/WebFlux，本身不使用线程池处理请求。虚拟线程主要影响 `block()` 调用和 `@Async` 任务。在当前网关架构下，启用虚拟线程是否会产生实际效果？是否可能对 Reactor 线程模型产生干扰？
10. **生产环境签名密钥轮换**：`SessionContextInjector` 使用单一 `signatureKey` 对所有请求签名。下游服务 `SessionContextResolver` 使用相同密钥验证。如果密钥泄露需要轮换，需要同时更新网关和所有下游服务配置。是否有密钥轮换方案（如双密钥过渡期、密钥版本号）？

---

## 10. 关键文件索引

| 文件 | 说明 |
|------|------|
| `internet-gateway/pom.xml` | Maven 模块定义 |
| `internet-gateway/src/.../InternetGatewayApplication.java` | 启动类 + 组件扫描配置 |
| `internet-gateway/src/.../application.yml` | 主配置（端口/CORS/Sa-Token/渠道/白名单） |
| `internet-gateway/src/.../application-local.yml` | 本地开发配置（Nacos/路由/签名密钥） |
| `internet-gateway/src/.../application-test.yml` | 测试配置（禁用 Redis/Nacos） |
| `demo-shared/shared-lib/gateway-shared/src/.../security/SaTokenGatewayConfiguration.java` | 认证过滤器核心 |
| `demo-shared/shared-lib/gateway-shared/src/.../security/ChannelAwareSaRouter.java` | 多渠道 StpLogic 管理 |
| `demo-shared/shared-lib/gateway-shared/src/.../security/SessionContextInjector.java` | 会话上下文签名注入 |
| `demo-shared/shared-lib/gateway-shared/src/.../security/GatewayStpInterfaceImpl.java` | 网关角色映射 |
| `demo-shared/shared-lib/gateway-shared/src/.../security/ChannelType.java` | 渠道类型枚举 |
| `demo-shared/shared-lib/gateway-shared/src/.../security/GatewayProperties.java` | 认证白名单配置 |
| `demo-shared/shared-lib/gateway-shared/src/.../filter/ExcludeRouteFilter.java` | 路由黑名单过滤器 |
| `demo-shared/shared-lib/gateway-shared/src/.../filter/CryptoFilter.java` | SM4 加解密过滤器 |
| `demo-shared/shared-lib/gateway-shared/src/.../matcher/ExcludeRouteMatcher.java` | 路由黑名单匹配器 |
| `demo-shared/shared-lib/gateway-shared/src/.../crypto/CryptoPolicy.java` | 加解密策略接口 |
| `demo-shared/shared-lib/gateway-shared/src/.../crypto/Sm4CryptoPolicy.java` | SM4 策略实现 |
| `demo-shared/shared-lib/gateway-shared/src/.../config/CryptoConfiguration.java` | 加解密 Bean 装配 |
| `demo-shared/shared-lib/gateway-shared/src/.../config/CryptoProperties.java` | 加解密配置绑定 |
| `demo-shared/shared-lib/gateway-shared/src/.../config/ExcludeRouteProperties.java` | 路由黑名单配置绑定 |
| `demo-shared/shared-lib/gateway-shared/src/.../config/GatewayChannelProperties.java` | 渠道配置绑定 |
| `demo-shared/shared-lib/gateway-shared/src/.../config/GatewaySessionProperties.java` | 会话签名配置绑定 |
| `demo-shared/shared-lib/gateway-shared/src/.../order/GatewayFilterOrder.java` | 过滤器顺序枚举 |
| `demo-shared/shared-lib/gateway-shared/src/.../checker/FilterOrderChecker.java` | 过滤器顺序校验器 |

---

## 11. 扩展指南

### 新增互联网渠道专属过滤器

1. 在 `gateway-shared` 中新建 `WebFilter` 或 `GlobalFilter` 实现
2. 实现 `Ordered` 接口，使用 `GatewayFilterOrder` 枚举中已定义的 order 值
3. 如果需要新的 order 值，在 `GatewayFilterOrder` 枚举中添加（启动时 `FilterOrderChecker` 会自动校验）
4. 过滤器中可通过 `ChannelAwareSaRouter` 判断当前渠道，实现渠道差异化逻辑

### 新增路由规则

在 `application-local.yml`（或对应环境配置）的 `spring.cloud.gateway.routes` 下添加：

```yaml
- id: new-service-route
  uri: lb://new-service
  predicates:
    - Path=/internet/new-service/**
  filters:
    - StripPrefix=1
    - AddRequestHeader=X-Gateway-Source, internet-gateway
```

### 启用 SM4 加解密

1. 设置 `gateway.crypto.enabled: true`
2. 配置 `gateway.crypto.secret-key`（SM4 128-bit 密钥）
3. 配置 `gateway.crypto.fields` 指定需要加解密的字段名和别名
4. 可选配置 `gateway.crypto.exclude-paths` 排除特定路径
