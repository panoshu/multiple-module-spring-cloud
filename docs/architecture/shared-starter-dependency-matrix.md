# shared-starter 依赖矩阵（S-17）

> 目标：明确每个 starter 的「必需依赖」与「可选依赖」，避免“引入即被迫携带 Nacos/Redis/sa-token”的隐式耦合。
> 可选依赖统一使用 Maven `optional=true` + `@ConditionalOnClass` / `@ConditionalOnProperty` 装配。

| Starter | 核心能力 | 必需依赖 | 可选依赖（条件装配） | 说明 |
|---|---|---|---|---|
| `shared-web-starter` | Web/AOP 底座、统一异常、链路过滤 | `shared-web-core`, `spring-boot-starter-web`, `spring-boot-starter-aop` | 无 | S-03：Nacos/commons-io/JAXB 已拆出 |
| `shared-discovery-starter` | Nacos 服务注册/发现 | `spring-cloud-starter-alibaba-nacos-discovery` | 无 | 需要注册中心的服务显式引入 |
| `shared-id-starter` | ULID/Segment ID 生成 | `shared-id-core`, MyBatis-Flex（Segment 模式） | 无 | 不携带 `application.yml`；规则由宿主配置 |
| `shared-event-starter` | 领域事件 Outbox/Redis/RocketMQ | `shared-event-core`, `shared-domain` | Redis：`spring-data-redis`；RocketMQ：`rocketmq-spring-boot-starter` | 跨服务通道默认关闭，宿主显式 `shared.event.*.enabled=true` |
| `shared-lock-starter` | 分布式锁（Lock4j） | `shared-lock`, Lock4j | Redis：`spring-boot-starter-data-redis` | 无 Redis 宿主使用本地锁实现 |
| `shared-logging-starter` | 访问日志、脱敏、耗时 | `shared-logging-core`, `spring-boot-starter-aop` | DB 导出：`spring-jdbc`；Logbook：`logbook-*` | 默认脱敏规则内聚在 `SanitizationProperties`；DB 导出默认关闭 |
| `shared-permission-starter` | `@RequirePermission` 自动装配 | `shared-permission-core`, `spring-boot-autoconfigure` | Redis 失效订阅：`spring-data-redis` | S-05：实现已下沉 core；缺配置时 `PermissionReadinessValidator` fail-fast |
| `shared-cache-starter` | JetCache 统一缓存 | `shared-cache-core`, JetCache | Redis：`spring-data-redis`（L2/MULTI） | `L1_ONLY` 只需 JetCache local 配置 |
| `shared-client-starter` | HTTP 客户端封装 | `shared-client-core`, `spring-web` | 无 | integration-service 使用 |
| `shared-crypto-starter` | SM4 自动装配 | `shared-crypto` | 无 | **实验性**：gateway/file 当前直接使用 `shared-crypto` 自建配置 |
| `shared-pdf-starter` | PDF 模板渲染 | `shared-pdf-core`, 模板引擎 | 字体：classpath 资源 | **实验性**：禁止 Windows 绝对路径；不携带 `application.yml` |

## 规则

1. starter 只做装配，不承载业务 API；实现代码放对应 `shared-*-core`。
2. starter 不得携带 `application.yml`；默认值通过 `@ConfigurationProperties` 的 `@DefaultValue`/构造器内聚，示例放 `docs/config-examples/`。
3. 重依赖（Redis/RocketMQ/Nacos/sa-token）必须 optional 或独立 starter。
4. 新增 starter 时必须同步更新本矩阵与 `arch-tests` 的边界规则。

## 非 starter 框架

`gateway-framework` 是 channel-gateway 的显式框架依赖，不是 auto-configuration starter：
它提供过滤器顺序、渠道 sa-token 校验、会话注入、加解密等网关专属能力，由 channel-gateway 直接装配。
这样避免把 WebFlux/网关依赖传递给普通业务服务，也避免 starter 的条件装配在多 profile 网关中隐式覆盖。
