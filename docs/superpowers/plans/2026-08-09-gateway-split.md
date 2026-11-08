# 网关拆分编码方案：internet-gateway / intranet-gateway

## 一、背景与目标

现有 `demo-gateway` 是单体 WebFlux 网关，同时处理 INTERNET/HQ/BRANCH 三渠道的全部能力（sa-token 认证、SM4
加解密、会话签名注入、路由、白名单）。

生产环境需拆分为两个独立入口（对应 BFF 设计文档第 6 章）：

- `internet-gateway`：互联网入口，仅 INTERNET 渠道，路由到 `internet-bff`
- `intranet-gateway`：内网入口，HQ + BRANCH 渠道，路由到 `intranet-bff`

`demo-gateway` 保留作为开发环境统一入口（三渠道齐备）。

## 二、已确认决策

1. 共享代码来源： **从现有 demo-gateway 抽取**到新模块 `gateway-shared`，demo-gateway 改为依赖它，避免复制粘贴。
2. 实施范围：本次先输出 **详细编码方案**，用户确认后再编码。
3. 渠道注册方式： **配置驱动**——通过 properties 配置各网关启用哪些渠道，`gateway-shared` 保持通用。

## 三、模块结构

```
gateway-shared/              # 新公共模块（从 demo-gateway 抽取）
├── pom.xml
├── checker/FilterOrderChecker.java      (抽取)
├── config/
│   ├── CryptoConfiguration.java         (抽取)
│   ├── CryptoProperties.java            (抽取)
│   ├── ExcludeRouteProperties.java      (抽取)
│   ├── GatewaySessionProperties.java    (抽取)
│   └── GatewayChannelProperties.java    (新增，配置驱动渠道注册)
├── crypto/
│   ├── CryptoPolicy.java                (抽取)
│   └── Sm4CryptoPolicy.java             (抽取)
├── filter/
│   ├── CryptoFilter.java                (抽取)
│   └── ExcludeRouteFilter.java          (抽取)
├── matcher/ExcludeRouteMatcher.java     (抽取)
├── order/GatewayFilterOrder.java        (抽取)
└── security/
    ├── ChannelType.java                 (抽取)
    ├── ChannelAwareSaRouter.java        (重构：配置驱动)
    ├── GatewayProperties.java           (抽取)
    ├── GatewayStpInterfaceImpl.java     (抽取)
    ├── SaTokenGatewayConfiguration.java (重构：渠道差异参数化)
    └── SessionContextInjector.java      (抽取)

internet-gateway/            # 新应用模块（互联网入口）
├── src/main/java/com/example/gateway/internet/InternetGatewayApplication.java
└── src/main/resources/
    ├── application.yml
    └── application-local.yml

intranet-gateway/            # 新应用模块（内网入口）
├── src/main/java/com/example/gateway/intranet/IntranetGatewayApplication.java
└── src/main/resources/
    ├── application.yml
    └── application-local.yml
```

## 四、gateway-shared 依赖调整

参考 `bff-shared` 的瘦依赖风格。`gateway-shared` 需要保持能独立编译、被三个网关复用的依赖集合。核心依赖（从 demo-gateway pom
迁移）：

- `spring-cloud-starter-gateway-server-webflux`（filter 基类）
- `spring-cloud-starter-loadbalancer`
- `sa-token-reactor-spring-boot3-starter`、`sa-token-redis-template`、`commons-pool2`
- `shared-crypto`、`shared-json`
- `shared-api`（ApiResult）
- `auth-api` + `httpexchange-spring-boot-autoconfigure`（SessionSignatureUtils 依赖）
- `caffeine`、`redisson-spring-boot-starter`
- `spring-boot-configuration-processor`、`lombok`（provided）
- `spring-boot-starter-test`（test）

> 注意：`auth-api` 是网关调用 auth-service 的动态路由规则所必需，`SessionContextInjector` 也依赖 `SessionSignatureUtils`（在
> auth-api 的 util 包），故保留。

## 五、配置驱动渠道注册（核心重构）

### 5.1 新增配置类 `GatewayChannelProperties`

```java
@ConfigurationProperties(prefix = "gateway.channels")
public record GatewayChannelProperties(
    @DefaultValue List<ChannelType> enabled
) {
    public GatewayChannelProperties {
        enabled = enabled != null ? List.copyOf(enabled) : List.of();
    }
}
```

### 5.2 `ChannelType` 支持按名称反查

在 `ChannelType` 增加 `fromName(String)` 方法（现有 `fromPath` 保留），供配置绑定与路由识别使用。

### 5.3 重构 `ChannelAwareSaRouter`

由"枚举全量注册"改为"按配置注册"：

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelAwareSaRouter {
    private final Map<ChannelType, StpLogic> stpLogicMap;
    private final List<ChannelType> enabledChannels;   // 来自配置

    public ChannelAwareSaRouter(GatewayChannelProperties props) {
        this.enabledChannels = props.enabled();
        this.stpLogicMap = new EnumMap<>(ChannelType.class);
        for (ChannelType channel : enabledChannels) {
            StpLogic logic = new StpLogic(channel.loginType());
            SaTokenConfig config = new SaTokenConfig();
            config.setTokenName(channel.tokenHeader());
            config.setIsReadHeader(true);
            config.setIsReadCookie(false);
            logic.setConfig(config);
            stpLogicMap.put(channel, logic);
        }
    }

    // 遍历 allEnabledChannels() 而非枚举全量
    public void configureDefaultStpLogic() {
        List<String> channelHeaders = enabledChannels.stream()
            .map(ChannelType::tokenHeader).toList();
        // ... 同现有逻辑，仅遍历 enabledChannels
    }

    public StpLogic getStpLogic(ChannelType channel) { return stpLogicMap.get(channel); }
    public ChannelType matchChannel(String path) { return ChannelType.fromPath(path); }
    public List<ChannelType> enabledChannels() { return enabledChannels; }
}
```

### 5.4 重构 `SaTokenGatewayConfiguration`

- 渠道前缀登录校验逻辑不变（`matchChannel` → `getStpLogic(channel).checkLogin()`）
- `configureDefaultStpLogic` 只识别 **本网关启用的**渠道 Header（由 `enabledChannels()` 提供）
- 白名单路径由各网关 `application.yml` 的 `auth.gateway.public-paths` 配置

### 5.5 `SessionContextInjector`

`resolveLoginChannel()` 当前遍历 `ChannelType.values()`，改为遍历 `channelAwareSaRouter.enabledChannels()`，避免在
internet 网关误识别 hq/branch 渠道。

## 六、各网关差异配置

### 6.1 internet-gateway（仅 INTERNET）

```yaml
server.port: 18081
spring.application.name: internet-gateway
gateway:
  channels:
    enabled: [ INTERNET ]
  routes:
    - id: internet-bff-route
      uri: lb://internet-bff
      predicates: [ Path=/internet/** ]
      filters: [ StripPrefix=1 ]
    - id: auth-service-route
      uri: lb://auth-service
      predicates: [ Path=/internet/auth/** ]
auth.gateway.public-paths:
  - /actuator/**
  - /internet/auth/login
```

### 6.2 intranet-gateway（HQ + BRANCH）

```yaml
server.port: 18082
spring.application.name: intranet-gateway
gateway:
  channels:
    enabled: [ HQ, BRANCH ]
  routes:
    - id: intranet-bff-route
      uri: lb://intranet-bff
      predicates: [ Path=/hq/**, /branch/** ]
      filters: [ StripPrefix=1 ]
    - id: auth-service-route
      uri: lb://auth-service
      predicates: [ Path=/hq/auth/**, /branch/auth/** ]
auth.gateway.public-paths:
  - /actuator/**
  - /hq/auth/login
  - /branch/auth/login
  - /branch/auth/secondary-auth/initiate
  - /branch/auth/secondary-auth/confirm
  - /branch/auth/secondary-auth/status/**
```

### 6.3 demo-gateway（保留开发入口）

- 改为依赖 `gateway-shared`，删除被抽取的本地类
- `gateway.channels.enabled: [ INTERNET, HQ, BRANCH ]`（三渠道齐备）
- 其余配置保持不变

## 七、启动类

两个新网关启动类结构一致，仅 `@EnableExchangeClients` 扫描 auth-api：

```java
@EnableDiscoveryClient
@EnableExchangeClients(basePackages = {"com.example.auth.api"})
@SpringBootApplication
@ConfigurationPropertiesScan
public class InternetGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(InternetGatewayApplication.class, args);
    }
}
```

## 八、建议任务拆分（实施阶段）

1. **Task 1**：新建 `gateway-shared` 模块，迁移 checker/config/crypto/filter/matcher/order 包（纯搬运，不改逻辑）
2. **Task 2**：新增 `GatewayChannelProperties` + 新增 `ChannelType.fromName`，重构 `ChannelAwareSaRouter`/
   `SaTokenGatewayConfiguration`/`SessionContextInjector` 为配置驱动，迁移 security 包
3. **Task 3**：创建 `internet-gateway` 应用模块（启动类 + 配置文件）
4. **Task 4**：创建 `intranet-gateway` 应用模块（启动类 + 配置文件）
5. **Task 5**：改造 `demo-gateway` 依赖 gateway-shared，删除重复类，配置三渠道
6. **Task 6**：迁移测试（gateway-shared 单测）、补充两新网关启动测试、全量构建验证

## 九、验证方式

- `mvn clean package -DskipTests` 全量构建通过
- 各网关 `spring-boot:run` 启动成功，`FilterOrderChecker` 打印过滤器顺序无冲突
- 对 `demo-gateway` 保留的测试（`GatewayIntegrationTest` 等）迁移到 gateway-shared 后仍通过
- 新网关启动测试验证 `@EnableExchangeClients`、`@ConfigurationPropertiesScan` 装配正常
