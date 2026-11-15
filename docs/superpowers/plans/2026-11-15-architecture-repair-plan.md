# 全项目架构问题修复计划（执行看板）

> 更新时间：2026-11-15  
> 状态图例：✅ 已完成；🟡 进行中/部分完成；⬜ 未开始；⛔ 阻塞  
> 本文件为活文档：每完成一项任务，更新看板对应行并在「最近更新」追加一行；文末保留原始计划全文作为任务定义依据。

## 最近更新

- 2026-11-16：新增全链路验证指南 `docs/superpowers/specs/2026-11-16-e2e-login-to-approval-verification.md` 与幂等种子脚本 `scripts/local/seed-e2e-data.sql`；按指南实测 P-1 全链路（批次 `20261116000002`、申请单 COMPLETED、审批 `61902894068137984` APPROVED、批次 COMPLETED/1/1/0）。
- 2026-11-16：联调中发现并修复两处登录/选计划缺陷：`CustomerChannelEntitlementPoConverter` 同时兼容 DB 的 `Channel.name()` 与历史 CodeEnum 编码；`SessionClaimsWriter` 在选计划后补齐 customerNo/productNo/planName，避免文件 token 的 `accessScope` 为空。auth-adapter、auth-infrastructure 定向测试通过。
- 2026-11-16：V-05/V-06 收口时全仓回归通过——306 reports / 1596 tests / 0 failures / 0 errors / 2 skipped；随后新增的 auth 选计划修复已通过 auth-adapter / auth-infrastructure 定向测试，全仓回归留待下次统一执行。
- 2026-11-15：V-06 完成——事件通道回滚演练：annuity 以 `--shared.event.rocketmq.enabled=false --shared.event.redis.enabled=true` 重启，经 Redis 发布 `EventEnvelope`，`RedisEventSubscriber` 与 `FileParsedEventListener` 正常接收；随后切回 RocketMQ，探针事件 `SEND_OK` 且监听器恢复。
- 2026-11-15：V-05 完成——统一 8 个服务 logback MDC pattern；网关生成/透传 `X-Trace-Id`，servlet 服务 `BizContextPropagationFilter` 生成/复用并写入 MDC；实测网关→auth/BFF→annuity traceId 全链一致，annuity 真实流程日志含 applicationId/stepExecutionId/requestNo/businessType；新增 10 项单测（trace 7 + channel 3）；顺带修复登录非法 channel 的 NPE（改为 COMMON.0001）。
- 2026-11-15：全仓回归通过——303 reports / 1586 tests / 0 failures / 0 errors / 2 skipped（P-02/I-02 收口后）。
- 2026-11-15：P-02 完成——approval-domain 移除 `business-core-types`，领域/应用/基础设施/适配层统一改用 String ID；approval 全模块构建与测试通过；配置检查脚本新增 API/领域层禁止回引 `business-core-types` 规则。
- 2026-11-15：服务层真实全链路联调成功（file→kernel→approval→kernel），详见 `docs/superpowers/specs/2026-11-15-e2e-integration-readiness.md` §6；Phase 1 P0 业务链关闭。
- 2026-11-15：结构整改后真实链路回归通过（批次 `20261115000092`）：上传→解析→裂变→APPROVAL→真实审批→COMPLETED，审批实例 `61844242720358400`，批次经 60s 对账任务收敛为 `COMPLETED/1/0`。
- 2026-11-15：K-05 完成——新增 `business-core-starter`，annuity 启动类移除 `com.example.core` 扫描与 kernel mapper 扫描；新增 `KernelHostScanContractTest`，annuity E2E 全绿。
- 2026-11-15：K-06 完成——approval 网关改为 `core.integration.approval.enabled` 条件装配；`ApprovalDispatchHandler` 改为可选网关并 fail-fast；`IntegrationEventSimulator` 改为 `core.kernel.event-simulator.enabled` 显式开启（test profile 开启）。
- 2026-11-15：K-09 完成——API 层 `BusinessMetaContext` 重命名为 `BusinessMetaCommandContext`，消除与 domain 同名歧义。
- 2026-11-15：K-10 / S-02 完成——删除 `Repository.streamByAppId` 及全仓 18 个实现，清理无用 `Consumer/AggregateRoot` import。
- 2026-11-15：K-11 完成——kernel 生产就绪校验新增 `PermissionCheckPort` / `RequirePermissionAspect` fail-fast，补齐对应单测。
- 2026-11-15：S-06 / S-07 完成——移除 kernel 未使用的 `shared-cache-starter` / `shared-client-starter` 传递依赖；annuity E2E 保持全绿。
- 2026-11-15：S-03 完成——新增 `shared-discovery-starter`，Nacos discovery/commons-io/JAXB 从 `shared-web-starter` 拆出，7 个服务启动模块显式引入 discovery starter；annuity 启动与 E2E 验证通过。
- 2026-11-15：K-07 完成——application 层改用 kernel 自有入站消息，外部 file/approval 事件 DTO 的映射下沉到 `business-core-adapter` 的 `ExternalEventInboundAdapter`；application 的 `file-api`/`approval-api` 依赖已移除，相关监听器/验收测试改按 kernel 消息断言，新增适配器映射测试。
- 2026-11-15：K-08 完成——新增 `FileRefId`（business-core-types）；kernel domain/application/adapter 不再依赖 `file-types.FileId`，文件服务 `FileId` 仅在 `FileServiceIntegrationGateway`/`BusinessFormConverter` 边界转换；annuity 适配层/仓储同步切换；annuity E2E（6+2+1 项）通过。
- 2026-11-15：A-04 完成——kernel 五个通用仓储改由 `business-core-starter` 统一注册；annuity 删除 5 个 `extends XxxRepositoryImpl` 空壳子类，`findByFileTaskId` 由 kernel 仓储委托宿主 `ParsedFileReferenceGateway`；annuity E2E 通过。
- 2026-11-15：X-06 完成——新增 ArchUnit `KernelStarterBoundaryTest`（7 项），并把 application/domain 的外部 API/types 依赖、`FileId` 边界、仓储继承、starter Bean 装配固化为构建期规则；规则运行通过。
- 2026-11-15：S-04 完成——sa-token 异常映射从 `shared-web-core` 迁至 `auth-adapter.SaTokenExceptionHandler`，shared-web-core 不再传递 sa-token-core；新增 2 项映射单测。
- 2026-11-15：S-05 完成——权限实现拆到 `shared-permission-core`，starter 仅保留自动装配；新增 `PermissionCachePort`，缓存失效监听器与具体 HttpExchange 执行器解耦；权限 core/starter 测试通过。
- 2026-11-15：S-01 完成——`EventId` 迁入 shared-domain.event、分页值对象迁入 shared-domain.page；删除无消费者的 SerialNo/TaskId；approval-api 的 `Pagination` 统一为 `PageQuery`（P-03 同步完成）；相关测试通过。
- 2026-11-15：S-06 完成——删除 `SharedCacheProperties` 中无消费者的 serializerType/topic/allowedPackages/cacheNullValues/useKeyPrefix，收敛为 ttl/mode/L1 容量四项实际生效配置。
- 2026-11-15：S-08/S-09 完成——crypto/pdf starter 标记实验性；PDF 字体示例改 classpath 资源。
- 2026-11-15：S-10/11/12/13 完成——五个 starter 的 `application.yml` 删除，示例归档到 `docs/config-examples/`；trace mapping 与脱敏默认值移入属性类型，避免 starter 隐式覆盖宿主配置。
- 2026-11-15：S-14/S-15/S-17 完成——会话签名工具迁入 security-contract、TriFunction 内联；shared-api 包名与 BizTag 文档化；新增 starter 依赖矩阵。
- 2026-11-15：Phase 4 部分完成——删除无调用的 PlanHierarchyGateway/TradeGateway 与空 integration-service-types；internet-bff 端口改 18092；B-03 业务类型清单改由 ScenarioHandlerRegistry 汇总；B-04 新增 bff-annuity-support 去重；X-05 渠道映射集中到 Channel.fromChannelType。
- 2026-11-15：AU-01/X-07 完成——新增 IAM 边界文档；同步 repair plan、e2e readiness、CODEBUDDY 与 starter 依赖矩阵。
- 2026-11-15：AU-03/AU-04 完成——auth 应用层改用统一缓存端口；权限上报端口下沉 domain，adapter 不再依赖 infrastructure；相关测试通过。
- 2026-11-15：全仓 `mvn test` 通过——292 个报告 / 1525 tests / 0 failures / 0 errors（2 skipped）；网关上下文测试补齐 Redis 端口 mock，ArchUnit 监听器规则按 K-07 更新。
- 2026-11-15：V-01/V-02/V-04 完成——7 服务启动；网关登录→BFF→annuity 聚合链路打通；修复网关缺失 `X-Session-Context`、Reactive 重复转发、BFF 会话头透传与异步上下文丢失；伪造会话头被剥离验证通过。
- 2026-11-15：F-03/AU-02 完成——file-api 与 auth-api 改用 String 传输字段，适配层边界包装强类型，两模块均移除 business-core-types 依赖。
- 2026-11-15：全仓 `mvn test` 再次通过——292 reports / 1525 tests / 0 failures（2 skipped）。
- 2026-11-15：最终 jar 复验——7 服务全部启动；网关登录、internet-bff 批次详情、三路聚合、无 token 拒绝均通过。
- 2026-11-15：X-04 完成——RocketMQ 5.3.2 Proxy（gRPC 8081）原生通道接线；新增 RocketMQEventDispatcher/Subscriber（显式事件类型信封、consumer group 隔离），本地 file/approval/annuity 切换 RocketMQ 并完成全链路。
- 2026-11-15：本地服务 JVM 上限调整：`-Xms128m -Xmx256m`。
- 2026-11-15：全仓 `mvn test` 通过（293 reports / 1527 tests / 0 failures）；PDF 测试显式 headless，规避容器缺少 libXi 的环境依赖。
- 2026-11-15：P0-00-3 完成——新增架构配置检查脚本并通过。
- 2026-11-15：P0-00-4 完成——全仓依赖分析执行并归档基线；误报与后续可收敛项已标注。
- 2026-11-15：F-01/F-02/P-04/I-03 完成——file adapter 去 infrastructure、file infrastructure 去 file-api；approval 事件转换器迁入 adapter 且 approval-api 降 test scope；integration 提交端点补权限校验。
- 2026-11-15：F-03 完成——file-api 请求 DTO 改用 String 传输字段，adapter 在边界包装 CustomerNo/ProductNo/UserNo；file-api 不再依赖 business-core-types。
- 2026-11-15：AU-02 完成——auth-api 渠道命令/查询改用 String 传输，应用服务边界包装强类型；auth-api 不再依赖 business-core-types。

## 状态摘要（2026-11-15 收口）

- 进度：✅ 65 项 / 🟡 1 项 / ⬜ 0 项（按看板统计）。
- 已闭环：Phase 2 kernel 边界（K-05~K-11、A-04、X-06）；Phase 3 shared-starter 治理（S-01~S-17）；
  Phase 4 的 P-01/P-03/P-02、I-01/I-02/I-04、B-02/B-03/B-04、AU-01/AU-03/AU-04、X-05、X-07；
  Phase 5 的 V-01~V-06（含可观测性与回滚演练）。
- 验证：全仓 `mvn test` 通过；服务层真实链批次 `20261115000111`/`20261115000121` 完成
  （APPROVAL→COMPLETED，审批 APPROVED，批次 COMPLETED/1/0）；
  8 服务入口（7 业务服务 + integration）：网关登录→internet-bff 批次详情/三路聚合通过；
  RocketMQ 5.3.2 Proxy(gRPC 8081) 通道批次 `20261115000131` 完成，topic `event` 位点增长。
- 全仓测试（V-05/V-06 收口后）：306 reports / 1596 tests / 0 failures / 0 errors / 2 skipped。
- 本地服务统一 `-Xms128m -Xmx256m`（scripts/local/start-all-services.sh）。
- 待后续：prod 环境矩阵实测（P0-00-5，需发布环境）。

## 执行看板

### Phase 0 基线与防回归

| ID | 任务 | 状态 | 证据 / 备注 |
|---|---|---|---|
| P0-00-1 | 建立修复台账 | ✅ | 本文件 |
| P0-00-2 | 记录基线 | ✅ | `mvn -DskipTests install` 通过；kernel 500、file 334、approval、annuity E2E 全绿 |
| P0-00-3 | 配置检查脚本 | ✅ | 新增 `scripts/check-architecture-config.sh` + `check_architecture_config.py`：starter 无 yml、宿主不扫 kernel、shared-web 无 sa-token、非 local 不关权限强制；实测通过 |
| P0-00-4 | 依赖分析 | ✅ | 已执行全仓 `mvn dependency:analyze`；报告 `docs/analysis/dependency-analyze-baseline.md`。大量告警为 JUnit aggregator/Spring Boot starter 的字节码误报，真实冗余已由 S-06/S-07/AU-03/AU-04 处理 |
| P0-00-5 | 环境矩阵 | 🟡 | 新增 `docs/architecture/environment-matrix.md`（local/test/prod + 发布检查清单）；prod 待发布环境实测 |

### Phase 1 P0 运行时闭环

| ID | 任务 | 状态 | 证据 / 备注 |
|---|---|---|---|
| X-01 | httpexchange 配置迁移与客户端装配测试 | ✅ | 各服务已迁移 `http-exchange`；`HttpExchangeConfigBindingTest`；annuity file-api 改由 kernel 静态客户端装配 |
| X-02 | profile 隔离与 dev 配置清理 | ✅ | `include: local` → `default: local`；gateway 渠道分组 |
| X-03 | 权限装配 fail-fast 与会话验签兜底 | ✅ | `PermissionAutoConfiguration` 显式 Bean + `PermissionReadinessValidator`；enforcement 开关 |
| K-01/02/03 | 表单上传→解析→裂变链路 | ✅ | `FormUploadedEventListener`、`BusinessForm` 事件、subTaskId 裂变；`AnnuityFormUploadFlowTest` |
| A-01 | APPROVAL 接入 `approvalSubmissionHandler` | ✅ | step-routes 已配置；真实审批实例创建成功 |
| X-04 | 跨服务事件通道 + integration 幂等 | ✅ | 新增 RocketMQ 5.x 原生 gRPC 通道（Producer + SimpleConsumer，Proxy 8081）；本地 file/approval/annuity/integration 切换 RocketMQ，真实链路批次 `20261115000131` 完成；integration 事件订阅与出站幂等已由 I-02 收口 |
| A-02/03/08 | Mock 隔离与真实 file gateway | ✅ | file-mock 关闭、真实 base-url 生效；客户画像仍 Mock（外部主数据未接入，已隔离） |
| K-11 | kernel 生产就绪校验扩展 | ✅ | `KernelProductionReadinessValidator` 已校验签名密钥 + verifier + `PermissionCheckPort` + `RequirePermissionAspect`；单测 7 项通过 |

### Phase 2 kernel 边界收口

| ID | 任务 | 状态 | 证据 / 备注 |
|---|---|---|---|
| K-05 | 新增 `business-core-starter`，移除宿主 kernel 扫描 | ✅ | starter 模块 + `BusinessCoreStarterAutoConfiguration`（mapper scan / @EnableAsync）；annuity 启动类只剩 annuity 扫描；`KernelHostScanContractTest` |
| K-06 | 集成网关/模拟器条件装配 | ✅ | approval 网关 `core.integration.approval.enabled`；dispatch handler 可选依赖 + fail-fast；simulator `core.kernel.event-simulator.enabled`（test 开启） |
| K-07 | 入站事件 DTO 适配器隔离 | ✅ | 新增 kernel 自有 `FileParsedMessage`/`ApprovalResultMessage`/`OutboundFileGeneratedMessage`；`ExternalEventInboundAdapter` 在 adapter 层完成外部 DTO→kernel 消息映射；application 已移除 `file-api`/`approval-api` 依赖 |
| K-08 | `FileId` 领域隔离 | ✅ | 新增 `business-core-types.FileRefId`；domain/application 改用 kernel 自有类型；仅 infrastructure 文件网关在 file-service API 边界转换为 `FileId`；annuity 同步切换，E2E 全绿 |
| K-09 | `BusinessMetaContext` 重命名/收敛 | ✅ | API 层改为 `BusinessMetaCommandContext`，domain 保留 `BusinessMetaContext` |
| K-10 | 删除 `Repository.streamByAppId` | ✅ | 接口 + 18 个实现已删除，全仓 test-compile 通过 |
| A-04 | annuity 改为依赖 kernel starter | ✅ | starter 直接装配 5 个 kernel 仓储 Bean（`@ConditionalOnMissingBean` 可覆盖）；删除 annuity 五个空壳仓储子类；`findByFileTaskId` 改由 kernel 仓储委托 `ParsedFileReferenceGateway`；annuity E2E 全绿 |
| X-06 | ArchUnit 增加 starter/边界规则 | ✅ | 新增 `KernelStarterBoundaryTest`（7 项）：application/domain 禁依赖 file/approval API/types、`FileId` 仅限 infrastructure 边界、业务服务不得继承 kernel 仓储、starter 五仓储 Bean 装配契约、application 事件监听禁外部 DTO |

### Phase 3 shared-starter 治理

| ID | 任务 | 状态 | 证据 / 备注 |
|---|---|---|---|
| S-03 | web starter 拆出 Nacos/JAXB/commons-io | ✅ | 新增 `shared-discovery-starter`；shared-web 只保留 Web/AOP；7 个服务显式引入 discovery |
| S-04 | sa-token 异常映射迁出 shared-web-core | ✅ | 新增 `auth-adapter.SaTokenExceptionHandler`；`shared-web-core` 移除 sa-token-core 依赖与异常映射；auth-adapter 2 项映射测试通过 |
| S-05 | permission 拆 core/starter + fail-fast + 缓存端口 | ✅ | 新增 `shared-permission-core`（24 个实现类）与瘦身 starter（仅自动装配）；新增 `PermissionCachePort`，失效监听器不再依赖具体 HTTP 执行器；core/starter 测试通过 |
| S-01 | shared-types 清理 | ✅ | `EventId` 迁入 shared-domain（随 `DomainEvent` 同包，避免与 shared-event-core 循环依赖）；无消费者的 `SerialNo`/`TaskId` 删除；`Pagination`/`PageInfo` 移至 shared-domain.page；approval API 统一 `PageQuery`；shared-types 仅保留标识契约与枚举工具 |
| S-02 | 删除 `Repository.streamByAppId` | ✅ | 与 K-10 合并完成 |
| S-06 | cache 残留属性、kernel 冗余依赖 | ✅ | kernel 已移除 `shared-cache-starter`；`SharedCacheProperties` 删除无消费者的 serializerType/topic/allowedPackages/cacheNullValues/useKeyPrefix；cache 模块编译通过 |
| S-07 | client starter kernel 冗余依赖移除 | ✅ | kernel `shared-client-starter` 已移除；annuity E2E 通过 |
| S-08 | crypto starter 消费者收口 | ✅ | 标记为实验性并说明 gateway/file 当前直接使用 `shared-crypto` 自建配置，生产接入前统一 |
| S-09 | pdf starter 实验标记/字体修复 | ✅ | 标记实验性；删除 starter `application.yml`；字体示例改为 classpath 资源，禁止 Windows 绝对路径 |
| S-10/11/12/13 | 移除 starter 内 `application.yml` | ✅ | event/id/logging/pdf/web 五个 starter 的 `application.yml` 已删除，示例移入 `docs/config-examples/`；trace mapping、脱敏规则默认值内聚到属性类型 |
| S-14 | shared-utils 会话/并发工具归位 | ✅ | `SessionSignatureUtils` 迁入 `shared-security-contract`；`TriFunction` 内联到 `VirtualThreadExecutor`；permission/gateway 移除 shared-utils 依赖 |
| S-15 | shared-api 命名与 BizTag 收敛 | ✅ | 文档化保留 `com.example.shared.web` 包名（避免破坏性迁移）；BizTag 标注窄用途并新增 package-info |
| S-16 | ArchUnit anchor 补全 + 新规则 | ✅ | 与 X-06 合并：`KernelStarterBoundaryTest` 7 项规则已落地 |
| S-17 | starter 依赖矩阵 | ✅ | 新增 `docs/architecture/shared-starter-dependency-matrix.md`，明确 11 个 starter 的必需/可选依赖与装配规则 |

### Phase 4 服务与 BFF 清理

| ID | 任务 | 状态 | 证据 / 备注 |
|---|---|---|---|
| AU-01 | auth IAM 边界文档 | ✅ | 新增 `docs/architecture/auth-iam-boundary.md`：职责边界、契约分层、调用链、禁止事项 |
| AU-02 | auth-api 去 `business-core-types` | ✅ | 渠道命令/查询改用 String 传输；adapter 直接映射，应用服务在边界包装 CustomerNo/Channel/UserNo；auth-api 移除 business-core-types 依赖 |
| AU-03 | auth-application 去 shared-event-core/Caffeine 直依赖 | ✅ | `MenuNodeCache` 改用 `ICacheTemplate`（shared-cache-core）；移除无 import 的 shared-event-core 与 Caffeine 依赖；新增 auth-menu-node TTL 规则 |
| AU-04 | auth-adapter 去 auth-infrastructure | ✅ | 权限上报端口下沉 auth-domain（`PermissionRegistrationUseCase`/Result）；adapter 只依赖端口；移除 auth-adapter→auth-infrastructure 依赖并显式补 Jackson |
| F-01 | file-adapter 去 file-infrastructure | ✅ | 新增 `FileTokenPolicy` 应用端口，infrastructure 绑定 `FileTokenProperties` 实现；adapter 只依赖端口，移除 file-infrastructure 依赖 |
| F-02 | file-infrastructure 去 file-api | ✅ | 领域事件转换器迁入 file-adapter.event；`HttpOutboundEnricherGateway` 迁入 file-adapter.outbound；infrastructure 主代码不再 import file-api |
| F-03 | file-api 去 `business-core-types` | ✅ | file-api 的 customerNo/productNo/uploader/downloader 改为 String 传输字段；adapter 转换时包装强类型；file-api 移除 business-core-types 依赖 |
| P-01 | 删除 `PlanHierarchyGateway` | ✅ | 接口与实现均无调用者，已删除 |
| P-02 | approval-domain 去 `business-core-types` | ✅ | approval-domain 仅保留 approval-types/shared-domain；领域/应用/基础设施/适配层统一 String ID；approval 全模块测试通过；`check_architecture_config.py` 新增回引拦截 |
| P-03 | 统一分页模型 | ✅ | approval-api 改用 `PageQuery`；共享分页值对象迁至 shared-domain.page |
| P-04 | approval-infrastructure 去 approval-api/auth-api 越层依赖 | ✅ | 4 个事件转换器迁入 approval-adapter.event；approval-api 降为 test scope；auth-api 保留为 infrastructure 的外部出站客户端契约（非自身 API 越层） |
| I-01 | 删除 `TradeGateway` | ✅ | 接口无实现/调用者，已删除 |
| I-02 | integration 出站事件幂等 | ✅ | 新增 `OutboundSubmissionReceiptGateway` + `JdbcOutboundSubmissionReceiptGateway`（唯一键/`ON CONFLICT`/PROCESSING→SUCCESS/release）；监听器走 `submitOutboundFileOnce`，重复事件返回 `DUPLICATE`；3 项单测通过；integration 18083 启动后回执表已创建 |
| I-03 | integration 内部端点保护 | ✅ | `TradeQueryAdapter` 已有 `@RequirePermission`；`TradeSubmissionAdapter.submitFile` 补充 `TRADE_SUBMISSION/SUBMIT` 权限校验 |
| I-04 | `integration-service-types` 清理 | ✅ | 模块仅含 package-info，已整体删除并从 reactor/依赖管理移除 |
| G-01 | gateway profile 修复 | ✅ | gateway 配置已按渠道分组/default |
| G-02 | gateway-framework 装配方式统一 | ✅ | 决策：gateway-framework 作为显式框架依赖（非 starter），由 channel-gateway 单宿主装配；不引入自动配置扫描，避免网关侧隐式 Bean 覆盖 |
| G-03 | SessionContextInjector Redis 耦合决策/隔离 | ✅ | 决策：网关直读 Reactive Redis（不依赖 sa-token reactor 上下文）；`ChannelGatewayApplicationTest` 用 `@MockitoBean` 提供测试端口，上下文测试通过 |
| B-01 | intranet-bff httpexchange 修复 | ✅ | 前序批次已迁移 |
| B-02 | 18090 端口冲突修复 | ✅ | internet-bff 主配置改为 18092；启动脚本去掉临时覆盖 |
| B-03 | 实现 `listBusinessTypes()` | ✅ | 新增 `ScenarioHandlerRegistry.supportedTypes()` 与 `BusinessScenarioHandler.serviceName()`；SystemManagementService 汇总返回，不再返回空列表；补单测 |
| B-04 | 两个 BFF 的 AnnuityScenarioHandler 去重/ADR | ✅ | 新增 `bff-annuity-support` 共享模块，两个 BFF 显式依赖并扫描 `com.example.bff.annuity`；删除两份复制实现与重复测试 |
| B-05 | BFF 权限边界确认 | ✅ | 权限管理仅 intranet-bff 暴露且仅转发 auth-api；网关做渠道/会话校验，业务服务做最终权限判定；已写入 IAM 边界文档 |
| X-05 | Channel/ChannelType 映射统一 | ✅ | 新增 `Channel.fromChannelType` 集中映射（INTERNET→NETAPP、HQ→TELLER、BRANCH→BANK_BRANCH），未知渠道 fail-fast；kernel/annuity 控制器复用 |
| X-07 | 文档与 CODEBUDDY 更新 | ✅ | 更新 repair plan、e2e readiness §6.7、CODEBUDDY、starter 依赖矩阵、IAM 边界文档 |

### Phase 5 全链路验证与发布

| ID | 任务 | 状态 | 证据 / 备注 |
|---|---|---|---|
| V-01 | 单服务启动矩阵 | ✅ | 7 业务服务 + integration 全部启动（18080/18081/18082/18083/18085/18090/18091/18092），启动脚本已固化本地权限开关 |
| V-02 | 多服务 E2E | ✅ | 网关登录→internet-bff 批次详情/三路聚合全部成功；服务层链路批次 `20261115000111` 已闭环 |
| V-03 | 事件通道验证 | ✅ | file→kernel、approval→kernel Redis 跨 JVM 实测通过 |
| V-04 | 安全验证 | ✅ | 网关+服务层均验证：无 token 返回 `COMMON.0002`；伪造会话头被网关剥离后仍以真实身份成功；服务层伪造/缺失会话均拒绝 |
| V-05 | 可观测性验证 | ✅ | 8 服务统一 logback MDC pattern；网关/服务端生成并透传 `X-Trace-Id`；实测 gateway→auth、gateway→BFF→annuity 同一 traceId，annuity 流程日志含 applicationId/stepExecutionId/requestNo/businessType；10 项单测（trace 7 + channel 3） |
| V-06 | 回滚演练 | ✅ | annuity 配置回切 Redis（RocketMQ 关闭）后发布 `EventEnvelope`，`RedisEventSubscriber`/kernel 监听器正常消费；再切回 RocketMQ 并确认订阅器启动；详见 readiness §13 |

---

## 原始计划全文

# 全项目架构问题修复计划

> 依据：上一轮架构分析结论 + 当前分支代码/POM/配置静态核对  
> 目标：先把运行链路和安全基线修通，再收口 kernel 与 shared 边界，最后做服务级清理与全链路验证  
> 原则：**P0 未清零不进入生产联调；kernel 领域模型不改写；不引入 BPMN/新流程微服务；所有改动可独立提交、可回滚**

---

## 一、修复目标与完成定义

### 1.1 总体目标

1. **运行闭环**：annuity 的表单上传→解析→裂变→步骤推进→审批挂起/回调→完成链路在仓库内真实接通。
2. **多服务闭环**：file/approval/kernel/integration 之间的集成事件在本地与生产各有明确、可验证的传输通道。
3. **安全基线**：权限切面、会话验签、profile 隔离、内部端点保护均 fail-fast，不存在“配置错误即静默放行”。
4. **边界收口**：kernel 提供 starter，业务服务不再扫描 kernel 包/继承 kernel 基础设施实现；shared starter 符合“薄自动装配”定义。
5. **可验证**：每个 P0/P1 问题都有对应自动化测试或启动契约测试；最终具备 Nacos+Redis+PG 多服务 E2E 脚本。

### 1.2 完成定义（DoD）

- `mvn clean install` 全绿，`arch-tests` 全绿。
- 新增测试覆盖：表单主流程 E2E、审批挂起/回调 E2E、权限装配 fail-fast、profile 隔离、httpexchange 客户端装配、跨服务事件投递。
- 全仓不存在已失效的 `httpexchange.clients.<package>.url` 写法。
- 生产 profile 不加载 `dev`/`local` 中的路由、密钥、端口。
- kernel 宿主不再出现 `scanBasePackages = "com.example.core"` 和 kernel mapper 扫描。
- shared starter 不再自带 `application.yml`；无消费者 starter 有明确状态标记。
- 文档与代码一致：更新 `CODEBUDDY.md`、`docs/service-call-architecture.md`、`docs/business-processing-flow.md`，并新增关键 ADR。

---

## 二、宏观修复路线图

| 阶段 | 目标 | 主要内容 | 退出标准 | 预估 |
|---|---|---|---|---|
| **Phase 0 基线与防回归** | 防止修复过程中引入新退化 | 建分支、记录测试基线、补配置 lint、建立问题台账 | `mvn clean install` 基线可复现；问题清单落盘 `.scratch/` | 2–3 天 |
| **Phase 1 P0 运行时闭环** | 让演示链路与安全基线真正成立 | 表单/审批链路、事件通道、profile、httpexchange、权限 fail-fast | P0 全部关闭；新增 E2E 全绿 | 10–15 天 |
| **Phase 2 kernel 边界收口** | kernel 从“白盒库”变成“可复用 starter” | `business-core-starter`、条件装配、入站 DTO 隔离、FileId 隔离 | annuity 不再扫描 kernel 包；kernel starter 装配测试全绿 | 8–10 天 |
| **Phase 3 shared-starter 治理** | starter 回归薄壳、能力使用闭环 | web/permission 拆分、Nacos/ sa-token 迁移、application.yml 清理、shared-types 清理 | starter 依赖矩阵与职责矩阵一致 | 8–12 天 |
| **Phase 4 服务与 BFF 清理** | 消除服务级死代码、越层依赖、配置缺陷 | auth IAM 边界、adapter→infra、BFF 配置/端口/TODO、渠道词表 | 服务级问题清单清零或标记决策 | 5–8 天 |
| **Phase 5 全链路验证与发布** | 证明多服务真实协作 | Nacos+Redis+PG 启动矩阵、API link curls、日志/MDC、回滚演练 | 端到端 HTTP 全链路通过；发布清单完成 | 5–8 天 |

### 2.1 依赖关系

```text
Phase 0
  └─ Phase 1
       ├─ X-01 httpexchange 配置迁移
       ├─ X-02 profile 隔离
       ├─ X-03 权限 fail-fast
       ├─ K-01/02/03 表单链路
       ├─ A-01 审批链路
       └─ X-04 跨服务事件通道
            └─ Phase 2（kernel starter 化）
                 ├─ Phase 3（shared 治理）
                 │    └─ Phase 4（服务/BFF 清理）
                 └─ Phase 5（全链路验证）
```

**关键顺序约束**：

1. 先修配置与安全基线（X-01/02/03），否则后续 E2E 的失败无法区分是业务代码还是客户端/密钥问题。
2. 表单与审批链路（K/A）先于 kernel starter 化，避免移动代码后再改行为。
3. kernel starter 化先于 shared 治理，因为 kernel 是 shared starter 的最大消费者。
4. 服务级清理最后做，避免与 P0/P1 修复产生大面积冲突。

---

## 三、阶段任务分解

### Phase 0：基线与防回归

| ID | 任务 | 动作 | 产出 |
|---|---|---|---|
| P0-00-1 | 建立修复台账 | 在 `.scratch/` 下按服务建 issue 文件，使用本文问题 ID | 可跟踪清单 |
| P0-00-2 | 记录基线 | 执行 `mvn clean install -DskipTests`、`mvn test`、`arch-tests`，记录失败项 | 基线报告 |
| P0-00-3 | 配置检查脚本 | 新增 `scripts/check-architecture-config.sh`：检查 `httpexchange.clients`、`include: local`、`dev-` 密钥、starter 内 `application.yml` | CI 可执行检查 |
| P0-00-4 | 依赖分析 | 执行 `mvn dependency:analyze`，输出未使用/声明缺失依赖清单 | 依赖基线 |
| P0-00-5 | 环境矩阵 | 固化 local/dev/internet/intranet/prod 的配置来源与密钥注入方式 | 环境矩阵文档 |

### Phase 1：P0 运行时闭环

| ID | 任务 | 依赖 | 负责模块 |
|---|---|---|---|
| X-01 | httpexchange 配置迁移与客户端装配测试 | P0 | 全服务 |
| X-02 | profile 隔离与 dev 配置清理 | P0 | gateway + 全服务 |
| X-03 | 权限装配 fail-fast 与会话验签兜底 | P0 | shared-permission |
| K-01/02/03 | 表单上传→解析→裂变链路接通 | X-01 | kernel + annuity |
| A-01 | APPROVAL 步骤接入 approvalSubmissionHandler | X-01 | annuity + kernel |
| X-04 | 跨服务事件通道统一 + integration 幂等 | X-01/02 | shared-event + 各服务 |
| A-02/03/08 | Mock 隔离与真实 file gateway profile | X-01 | annuity |
| K-11 | kernel 生产就绪校验扩展 | X-03 | kernel adapter |

### Phase 2：kernel 边界收口

| ID | 任务 | 依赖 | 负责模块 |
|---|---|---|---|
| K-05 | 新增 `business-core-starter`，移除 `@ComponentScan` | X-01/02 | kernel |
| K-06 | 集成网关/模拟器条件装配 | K-05 | kernel |
| K-07 | 入站事件 DTO 适配器隔离 | K-05 | kernel |
| K-08 | `FileId` 领域隔离 | K-05 | kernel + file |
| K-09 | `BusinessMetaContext` 重命名/收敛 | K-05 | kernel |
| K-10 | 删除 `Repository.streamByAppId` | K-05 | shared-domain + 各服务 |
| A-04 | annuity 改为依赖 kernel starter，减少白盒依赖 | K-05 | annuity |
| X-06 | ArchUnit 增加 starter/边界规则 | K-05 | arch-tests |

### Phase 3：shared-starter 治理

| ID | 任务 | 依赖 | 负责模块 |
|---|---|---|---|
| S-03 | 拆出 Nacos/JAXB/commons-io，web starter 纯化 | Phase 2 | shared-web |
| S-04 | sa-token 异常映射迁出 shared-web-core | S-03 | shared-web |
| S-05 | permission 拆 core/starter + fail-fast + 缓存端口 | X-03 | shared-permission |
| S-01 | shared-types 清理（EventId/SerialNo/TaskId/分页） | Phase 2 | shared-types |
| S-02 | 删除 `Repository.streamByAppId` | K-10 | shared-domain |
| S-06 | cache 残留属性清理、kernel 冗余依赖移除 | Phase 2 | shared-cache/kernel |
| S-07 | client starter kernel 冗余依赖移除 | Phase 2 | shared-client/kernel |
| S-08 | crypto starter 消费者收口或标记实验 | Phase 2 | shared-crypto/gateway/file |
| S-09 | pdf starter 实验标记/默认字体修复 | Phase 2 | shared-pdf |
| S-10/11/12/13 | 移除 starter 内 `application.yml`，默认值改配置属性 | Phase 2 | 各 starter |
| S-14 | shared-utils 会话/并发工具归位 | Phase 3 | shared-utils |
| S-15 | shared-api 命名与 BizTag 收敛 | Phase 3 | shared-api |
| S-16 | ArchUnit anchor 补全 + 新规则 | Phase 3 | arch-tests |

### Phase 4：服务与 BFF 清理

| ID | 任务 | 依赖 | 负责模块 |
|---|---|---|---|
| AU-01 | auth IAM 边界文档与模块说明 | Phase 3 | auth |
| AU-02 | auth-api 去 `business-core-types`（迁 `auth-types` 或 shared） | Phase 3 | auth |
| AU-03 | auth-application 去 `shared-event-core`/Caffeine 直依赖 | S-05 | auth |
| AU-04 | auth-adapter 去 auth-infrastructure 依赖 | Phase 3 | auth |
| F-01 | file-adapter 去 file-infrastructure 依赖 | Phase 3 | file |
| F-02 | file-infrastructure 去 file-api 依赖（事件转换下沉/上移） | Phase 3 | file |
| F-03 | file-api 去 `business-core-types` | AU-02 同步 | file |
| P-01 | 删除 `PlanHierarchyGateway` 及实现 | Phase 3 | approval |
| P-02 | approval-domain 去 `business-core-types` | AU-02 同步 | approval |
| P-03 | 统一分页模型 | S-01 | approval |
| P-04 | approval-infrastructure 去 approval-api/auth-api 越层依赖 | Phase 3 | approval |
| I-01 | 删除 `TradeGateway` | Phase 3 | integration |
| I-02 | integration 出站事件幂等 | X-04 | integration |
| I-03 | 内部端点保护策略 | X-03 | integration |
| I-04 | 删除或填充 `integration-service-types` | Phase 3 | integration |
| G-01 | gateway profile 修复 | X-02 | gateway |
| G-02 | gateway-framework 装配方式统一（starter 或明确框架） | Phase 4 | gateway |
| G-03 | SessionContextInjector Redis 耦合决策/隔离 | Phase 4 | gateway |
| B-01 | intranet-bff httpexchange 修复 | X-01 | BFF |
| B-02 | 18090 端口冲突修复 | Phase 4 | BFF |
| B-03 | 实现 `listBusinessTypes()` | Phase 4 | intranet-bff |
| B-04 | 两个 BFF 的 AnnuityScenarioHandler 去重/ADR | Phase 4 | BFF |
| B-05 | BFF 权限边界确认与内部端点保护 | X-03 | BFF |
| X-05 | Channel/ChannelType 映射统一 | Phase 4 | auth/gateway/annuity |
| X-07 | 文档与 CODEBUDDY 更新 | Phase 5 | docs |

### Phase 5：全链路验证与发布

| ID | 任务 | 内容 |
|---|---|---|
| V-01 | 单服务启动矩阵 | 依次启动 auth/file/approval/integration/annuity/BFF/gateway，验证端口、Nacos 注册、Redis/PG 连接 |
| V-02 | 多服务 E2E | 执行 docs/api-link-test-curls.md 脚本，覆盖 form、approval、file、integration、auth 链路 |
| V-03 | 事件通道验证 | 验证 file→kernel、approval→kernel、file→integration 的跨 JVM 事件 |
| V-04 | 安全验证 | 无 token/无权限/伪造 header/缺密钥启动失败/内部端点不可达 |
| V-05 | 可观测性验证 | MDC `traceId/applicationId/stepExecutionId/requestNo/businessType` 输出 |
| V-06 | 回滚演练 | 按 Phase 任务回滚，确认配置开关可恢复旧路径 |

---

## 四、微观问题清单与逐项修复任务

### 4.1 business-core-kernel

| ID | 问题 | 修复动作 | 验收 | 优先级 |
|---|---|---|---|---|
| K-01 | `FormUploadedEvent` 无监听器，`triggerParsing` 无调用者 | 新增 `FormUploadedEventListener`（AFTER_COMMIT）→ `BusinessFormAppService.triggerParsing` | 确认上传后自动触发解析（Mock/真实均可） | P0 |
| K-02 | `BusinessForm.markAsUploaded()` 未注册事件 | 状态从 `WAITING_UPLOAD/UPLOADED` 迁移时注册 `FormUploadedEvent`；避免重复注册 | 单测覆盖状态迁移与事件数量 | P0 |
| K-03 | `handleParsingResult` 无生产调用者，缺少“解析结果→裂变”入口 | 新增 `ParsedResultQueryGateway` 端口；`FileParsedEventListener` 拉取结果后调用 `handleParsingResult`；Mock/真实实现各一 | E2E 从 confirmUpload 到多个 BusinessApplication 生成 | P0 |
| K-04 | 解析事件缺少 formId/结果映射 | 扩展 `FileParsedEventDTO` 或在 kernel 侧通过 `ParsedFileReferenceGateway`/`FileTaskApi` 解析；定义 DTO→`ParsedPlanResult` 映射 | 事件可定位 form 并生成申请单 | P0 |
| K-05 | 无 starter，`@ComponentScan("com.example.core")`，宿主扫描 kernel 包/mapper | 新建 `business-core-starter`；改为显式 `@Bean` + `@ConditionalOnMissingBean`；kernel mapper 由 starter 扫描；host 只依赖 starter | annuity 启动类不再出现 `com.example.core` 扫描 | P1 |
| K-06 | 默认集成网关无条件注册、模拟器在生产 infra | `ApprovalServiceIntegrationGateway` 加 `@ConditionalOnBean(ApprovalFlowApi/ApprovalInstanceApi)`；`IntegrationEventSimulator` 加 demo profile/属性；file gateway 条件保持 | 无 approval 客户端时不启动失败；生产上下文不注册模拟器 | P1 |
| K-07 | application 直接 import file/approval 集成事件 DTO | 入站监听器迁到 adapter/独立 inbound 模块；转换为 kernel 自有事件后再进 application | application 不再 import `*.api.event.*` | P1 |
| K-08 | domain 直接依赖 `file-types.FileId` | 在 kernel domain 定义 `FileReference`/`FileRefId`；infrastructure 映射 `FileId`；或把 `FileId` 提升到 shared-types | domain 不依赖 file-types | P1 |
| K-09 | API/domain 两个 `BusinessMetaContext` 同名 | 重命名 API 层为 `BusinessMetaCommandContext` 或 domain 层为 `FlowConfigContext` | 无同名冲突，文档更新 | P2 |
| K-10 | `Repository.streamByAppId` 无调用者且语义不通用 | 从 `Repository` 删除；需要流式处理的仓储单独定义方法 | 全仓编译通过，无默认实现 | P2 |
| K-11 | 生产就绪校验只查签名密钥，不查权限切面/客户端 | 扩展 `KernelProductionReadinessValidator`：非开发 profile 必须有 `PermissionCheckPort`、`RequirePermissionAspect`、签名密钥 | 缺任一配置启动失败 | P0 |
| K-12 | `FileServiceClientConfiguration` 静态 base-url、与 httpexchange 双轨、无 `@ConditionalOnMissingBean` | 统一到 httpexchange 或明确保留静态客户端；加 `@ConditionalOnMissingBean`；支持 load-balanced base-url | 不出现重复 Bean；客户端可配置 | P2 |

**K-01/02/03 建议实施顺序**：

1. 先写失败测试：`confirmUpload` 后期待 form 状态 PARSING、file gateway 被调用、application 被创建。
2. 加事件监听器，让 `FormUploadedEvent` 驱动 `triggerParsing`。
3. 在 `FileParsedEventListener` 中增加结果拉取与 `handleParsingResult`，保留 `completeWaitingStep` 分支。
4. 用 `EventReceiptGateway` 保证同一 fileTaskId 只裂变一次；`handleParsingResult` 内部用 form 状态做幂等。
5. E2E 同时覆盖 SUCCESS、PARTIAL、FAILED 三种状态。

---

### 4.2 annuity-service

| ID | 问题 | 修复动作 | 验收 | 优先级 |
|---|---|---|---|---|
| A-01 | APPROVAL 步骤 `mainProcessor: null`，审批未接线 | 配置 `mainProcessor: "approvalSubmissionHandler"`；确认 APPROVAL 保持 USER_TASK 由 BFF 推进 | 推进到 APPROVAL 时创建 ExternalInteraction 并调用 approval API | P0 |
| A-02 | MockFileIntegrationGateway `@Primary` 永久覆盖真实实现 | 将 Mock 改为 `@Profile("test")` 或 `@ConditionalOnProperty("annuity.demo.file-mock.enabled")`；本地/生产用真实 gateway | 生产 profile 不装配 Mock | P0 |
| A-03 | 扩展存储/解析引用/客户/通知均为内存或 Mock | 为每个 Mock 加 profile/属性隔离；在 README 标明“仅演示”；真实业务接入时替换 | 生产上下文无内存态/Mock Bean | P1 |
| A-04 | 每层依赖 kernel 对应实现模块并继承 Repository | Phase 2 改为依赖 `business-core-starter`；Repository 改为组合 kernel 通用仓储或提供 SPI 默认实现 | annuity 不再继承 `BusinessApplicationRepositoryImpl` | P1 |
| A-05 | `AnnuityDetailIngestionAction.saveBatch` 用 `replace("B-","")` 反推 ApplicationId | 在 `AnnuityEmployeeDetail` 存储真实 `ApplicationId`，或通过映射表关联；禁止字符串反推 | 代码无 `replace("B-")`，单测覆盖 | P2 |
| A-06 | E2E 直接构造 BusinessApplication，绕过主流程 | 新增 `AnnuityFormFlowEndToEndTest`：confirmUpload→解析→裂变→自动推进；保留旧测试作为步骤级测试 | 新 E2E 不直接调用 `createFromForm` | P0 |
| A-07 | `uploadForm` 自定义入口不触发解析 | 明确该入口的语义：要么调用 `confirmUpload`/`triggerParsing`，要么改为仅创建草稿；文档更新 | 上传后状态与事件链一致 | P0 |
| A-08 | 无 `core.integration.file.base-url`，真实 file gateway 从未装配 | 在 local/dev/prod profile 配置真实 file-service 地址；test profile 用 Mock | 真实 profile 装配 `FileServiceIntegrationGateway` | P0 |
| A-09 | 5 个 kernel Controller 复制粘贴 | 暂时保留（文档已有模式）；Phase 4 评估提取 `AbstractKernelController` 或代码生成 | 不重复业务逻辑，仅转发 | P2 |

---

### 4.3 file-service

| ID | 问题 | 修复动作 | 验收 | 优先级 |
|---|---|---|---|---|
| F-01 | `file-adapter` 依赖 `file-infrastructure`（FileTokenProperties） | 将 `FileTokenProperties` 迁到 `file-application`/`file-api` 的配置包，或通过 `@ConfigurationProperties` 在 starter 中暴露；adapter 只依赖 application | `file-adapter` POM 不再依赖 infrastructure | P1 |
| F-02 | `file-infrastructure` 依赖 `file-api`（事件 DTO/enrichment） | 把事件转换器/`HttpOutboundEnricherGateway` 移到独立 `file-adapter`/`file-inbound` 模块；infrastructure 只依赖 domain | infrastructure 不依赖 api | P1 |
| F-03 | `file-api` 依赖 `business-core-types`（CustomerNo/ProductNo/UserNo） | 与 auth/approval 同步，把跨上下文 ID 迁到 `shared-types` 或 `shared-business-vocabulary`；或 file-api 使用 String 契约 | file-api 不再依赖 kernel types | P1 |
| F-04 | `application.yml` 无条件 `include: local`，dev 密钥可能进生产 | 改 profile group/default；密钥走环境变量；见 X-02 | prod 无 dev 密钥 | P0 |
| F-05 | 跨服务事件未启用 | 见 X-04 | file 事件可跨 JVM 到达 kernel/integration | P0 |
| F-06 | 文件类型模块部分值对象无 `@IdDefinition` | 确认 `BizType/FileCategory/TemplateCode` 是否需要 ID 策略；不需要则保持 record，文档说明 | 类型语义清晰 | P2 |
| F-07 | `file-service` 测试覆盖分散 | 增加 form parse→canonical→fetch rows 的集成测试；与 kernel E2E 对接 | 新增测试全绿 | P2 |

---

### 4.4 approval-service

| ID | 问题 | 修复动作 | 验收 | 优先级 |
|---|---|---|---|---|
| P-01 | `PlanHierarchyGateway`/`PlanHierarchyGatewayImpl` 死代码 | 删除接口、实现及相关 TODO；保留 `RoleUserGateway` 的 parent plan 能力 | 全仓无引用 | P2 |
| P-02 | approval-domain 依赖 `business-core-types`（ApplicationId/PlanNo/UserNo） | 与 auth/file 同步迁移共享 ID；domain 依赖 shared-types 或 approval-types | 不再依赖 business-core-types | P1 |
| P-03 | `Pagination` 与 `PageData/PageQuery` 两套分页 | 统一为 `PageData/PageQuery`；API 请求改用 `PageQuery` | 全服务分页模型一致 | P2 |
| P-04 | approval-infrastructure 依赖 approval-api/auth-api | 把 Converter/事件 DTO 映射移到 adapter；auth 调用通过 domain gateway 端口，infra 实现端口 | 依赖方向符合规则或记录 ADR 例外 | P1 |
| P-05 | 跨服务事件未启用 | 见 X-04 | approval 事件可到 kernel | P0 |
| P-06 | 审批查询/历史服务在测试中存在但主类未显式拆分 | 评估将 `ApprovalInstanceService` 拆为命令/查询服务，降低类体积 | 单类 ≤500 行、职责清晰 | P2 |

---

### 4.5 auth-service

| ID | 问题 | 修复动作 | 验收 | 优先级 |
|---|---|---|---|---|
| AU-01 | 实际是 IAM 平台，命名/边界未体现 | 文档明确 IAM 边界；模块 README 列出 user/credential/channel/authorization/assignment/role/permission/product 子域；后续再评估拆服务 | 团队认知一致，接口不混用 | P1 |
| AU-02 | auth-api 依赖 `business-core-types` | 把 `Channel/CustomerNo/UserNo/PlanNo` 迁到 `shared-types` 或 `shared-business-vocabulary`；auth-api 不再依赖 kernel | auth-api 只依赖 shared-api/shared-types/auth-types | P1 |
| AU-03 | auth-application 依赖 `shared-event-core` 与 Caffeine | application 只依赖 `EventBus`/缓存端口；Caffeine 改为 `ICacheTemplate` 或本地缓存端口；starter 负责装配 | application POM 去掉 shared-event-core/caffeine | P1 |
| AU-04 | auth-adapter 依赖 auth-infrastructure（PermissionScannerService） | 把权限扫描服务上移到 application；adapter 只依赖 application/api | adapter POM 去 infrastructure | P1 |
| AU-05 | 权限/验签 fail-open | 见 X-03；auth 自身 LocalPermissionExecutor 不受影响 | 非开发环境缺配置启动失败 | P0 |
| AU-06 | `PermissionAutoConfiguration` Javadoc 过时 | 更新为 shared-security-contract | 文档与代码一致 | P2 |
| AU-07 | 无 profile 隔离/生产就绪校验 | 同 X-02/X-03；auth 也应有密钥/客户端校验（若使用 HTTP 执行器） | 启动契约测试 | P0 |

---

### 4.6 integration-service

| ID | 问题 | 修复动作 | 验收 | 优先级 |
|---|---|---|---|---|
| I-01 | `TradeGateway` 与 `TradeQueryGateway` 完全重复且无使用 | 删除 `TradeGateway`，只保留 `TradeQueryGateway` | 无引用，编译通过 | P2 |
| I-02 | `OutboundFileGeneratedEventListener` 无幂等/回执 | 引入 eventId 回执表（或复用 shared-event 的 receipt 抽象）；提交前 `tryConsume` | 重复事件不重复提交外部系统 | P0 |
| I-03 | `TradeSubmissionAdapter.submitFile` 无 `@RequirePermission` | 标记为内部端点；增加网络隔离/内部 token 方案；或补 `@RequirePermission` 并配置服务间调用凭据 | 内部端点不可被未授权外部直接访问 | P1 |
| I-04 | `integration-service-types` 仅有 package-info | 删除该模块（含根 POM 声明），或补充实际 ID 类型 | 构建无空模块 | P2 |
| I-05 | 测试仅 2 个 | 为 `BaseTradeGateway`、`FileSubmissionGatewayImpl`、事件监听器补单测/契约测试 | 关键路径有测试 | P2 |
| I-06 | 出站默认 tradeCode=5564 硬编码属性 | 把 tradeCode 映射外置到配置/模板/事件字段 | 无业务硬编码 | P2 |

---

### 4.7 channel-gateway + gateway-framework

| ID | 问题 | 修复动作 | 验收 | 优先级 |
|---|---|---|---|---|
| G-01 | `include: local,dev` 无条件激活 dev | 改为 `spring.profiles.default: dev` + `spring.profiles.group.internet: local`、`group.intranet: local`、`group.dev: local` | `--spring.profiles.active=internet` 不加载 dev 路由/端口/白名单 | P0 |
| G-02 | gateway-framework 不是 starter，同包扫描装配 | 增加 `META-INF/spring/...AutoConfiguration.imports`，或明确它是“框架库”并在文档中约定 | 可复用且装配可预期 | P1 |
| G-03 | SessionContextInjector 直读 sa-token Redis key | 增加隔离层 `SaTokenSessionReader`；升级 sa-token 时只改一处；补集成测试 | 不直接散落 key 拼接 | P1 |
| G-04 | profile 端口 18081/18082 与 approval/file 冲突（本机） | 统一端口规划表；本地启动脚本检查端口占用 | 本机可同时启动 | P2 |
| G-05 | 文档仍提 internet-gateway/intranet-gateway/demo-gateway | 更新为 channel-gateway 三 profile | 文档与代码一致 | P2 |
| G-06 | 缺少 profile 隔离测试 | 增加 GatewayProfileIsolationTest，断言端口/路由/渠道/白名单 | 测试全绿 | P0 |
| G-07 | dev 直连业务服务路由可能误入生产 | 仅放在 `application-dev.yml`，并加配置检查 | prod 无 `/annuity/**`、`/file/**` 直连路由 | P0 |

---

### 4.8 internet-bff / intranet-bff / bff-framework

| ID | 问题 | 修复动作 | 验收 | 优先级 |
|---|---|---|---|---|
| B-01 | intranet-bff 使用已失效的 `httpexchange.clients.<package>.url` | 迁移到 `http-exchange.channels`（approval/auth/annuity 三个 channel）；补客户端装配测试 | 三个客户端 Bean 均存在且 base URL 正确 | P0 |
| B-02 | internet-bff 与 annuity 都是 18090 | 重新规划 BFF 端口（如 internet-bff 18092、intranet-bff 18091）；更新网关路由/文档 | 本机同时启动不冲突 | P1 |
| B-03 | `SystemManagementService.listBusinessTypes()` 返回空 | 从 `ScenarioHandlerRegistry` 汇总 `supportedTypes()`，组装 `BffBusinessTypeResponse` | 接口返回 ANNUITY_OPEN/ANNUITY_CHANGE | P1 |
| B-04 | 两个 BFF 的 AnnuityScenarioHandler/BffBusinessController 重复 | 方案 A：抽到 `bff-annuity` 共享模块；方案 B：保留重复并写 ADR；推荐先写 ADR，Phase 4 再抽 | 代码重复可解释、可维护 | P2 |
| B-05 | BFF 权限边界不清，`BffPermissionController` 转发无注解的内部权限 API | 明确 BFF 只做登录态校验 + 下游权限；为管理类 BFF 增加 `@RequirePermission` 或网关路由级权限；补测试 | 未授权用户不能调用权限查询/审批操作 | P1 |
| B-06 | `include: local` 无条件 | 同 X-02 | prod 不加载本地配置 | P0 |
| B-07 | `BffAggregationService` 与设计文档“删除”不一致 | 确认保留（当前在用）；更新 docs/plans 或补 ADR | 文档与代码一致 | P2 |

---

### 4.9 demo-shared

| ID | 问题 | 修复动作 | 验收 | 优先级 |
|---|---|---|---|---|
| S-01 | shared-types 残留 `EventId/TaskId/SerialNo/PageInfo/Pagination` | `EventId` → `shared-event-core`；`TaskId/SerialNo` 若无消费者删除；分页统一到 `PageData/PageQuery`（与 P-03 同步） | shared-types 只保留 ID 契约与必要原语 | P1 |
| S-02 | `Repository.streamByAppId` 不通用且无调用者 | 删除；各仓储按需定义流式方法 | 编译通过 | P2 |
| S-03 | shared-web-starter 夹带 Nacos/JAXB/commons-io | 拆出 `shared-discovery-starter` 或把 Nacos 放服务 starter；删除未使用 JAXB/commons-io | web starter 只含 web 能力 | P1 |
| S-04 | shared-web-core 依赖 sa-token-core 并处理 sa-token 异常 | 把 sa-token 异常映射迁到 gateway/auth 专用 advice；或 sa-token-core 改 optional + `@ConditionalOnClass` | shared-web 不依赖 sa-token | P1 |
| S-05 | permission starter 实为 SDK、fail-open、直接 Caffeine | 拆 `shared-permission-core` + starter；加启动校验；本地缓存改用 `ICacheTemplate` 或明确豁免；修 Javadoc | starter 只做装配；缺配置 fail-fast | P0/P1 |
| S-06 | cache 残留属性、kernel 引入未用、`ICacheTemplate` 条件过严 | 删除 `serializerType/allowedPackages/topic/cacheNullValues/useKeyPrefix` 或标注 deprecated；移除 kernel adapter 的 cache starter；明确 L1_ONLY 需要 jetcache local 配置 | 配置无歧义；依赖无冗余 | P1 |
| S-07 | kernel 引入 shared-client-starter 未用 | 从 kernel infra POM 移除；integration 保留 | 依赖分析无未用 | P2 |
| S-08 | shared-crypto-starter 零消费者，gateway/file 绕过 | 让 gateway/file 改用 starter，或把 starter 标记 experimental 并在文档说明 | 能力入口唯一 | P1 |
| S-09 | shared-pdf-starter 零消费者、Windows 字体、application.yml | 标记 experimental；字体路径改为配置/资源；移除 application.yml | 不再被误认为生产可用 | P2 |
| S-10 | shared-event-starter 自带 application.yml（RocketMQ localhost 默认） | 删除 application.yml；默认关闭 RocketMQ；示例放文档；跨服务通道见 X-04 | jar 内无 application.yml | P1 |
| S-11 | shared-id-starter 自带示例规则 application.yml | 删除；示例放文档；`IdProperties` 默认值保持 | 同上 | P2 |
| S-12 | shared-logging-starter 自带默认脱敏/导出配置 | 删除 application.yml；默认关闭 DB 导出/脱敏，由宿主显式开启 | 同上 | P1 |
| S-13 | shared-web-starter 自带 application.yml | 删除；trace mapping 由宿主配置 | 同上 | P2 |
| S-14 | shared-utils 混入 SessionSignatureUtils/TriFunction | 会话签名工具迁到 `shared-security-contract` 或 `shared-security-utils`；TriFunction 内联到 event-core | utils 只保留通用并发/函数工具 | P2 |
| S-15 | shared-api 包名偏 web、BizTag 使用面窄 | 评估重命名包（破坏性大，可只改文档）；BizTag 保留但文档说明 | 语义清晰 | P2 |
| S-16 | ArchUnit anchor 缺失、shared-web-core 违规未发现 | 在 arch-tests POM 增加所有 shared jar；增加“starter 无业务 API、lib 无 Spring 注解、starter 无 application.yml”规则 | 新规则能捕获当前已知违规（修复后应通过） | P1 |
| S-17 | starter 依赖过重（如 web 带 Nacos、lock 带 redis） | 明确每个 starter 的“必需依赖/optional 依赖”矩阵；重依赖改为 optional + 条件装配 | 依赖矩阵文档 + pom 注释 | P1 |

---

## 五、跨服务专项修复方案

### 5.1 X-01 httpexchange 配置迁移

**现状**：库 `httpexchange-spring-boot-autoconfigure:3.5.5` 的配置前缀是 `http-exchange`，`clients` 是 `Set<Class<?>>`，没有 `httpexchange.clients.<package>.url`。internet-bff 已改用新写法；intranet-bff、annuity、file、approval、integration 仍是旧写法。

**修复步骤**：

1. **Spike（0.5 天）**
   - 在一个测试模块中用 `ApplicationContextRunner` 配置 `http-exchange.base-url` 和 `http-exchange.channels`，断言 `@HttpExchange` 客户端 Bean 创建成功、base URL 正确。
   - 确认 `channels[].clients` 的精确写法（类名/接口名列表）以及 `classes` 字段的绑定方式。
2. **定义服务级配置矩阵**

| 服务 | 下游 | 方案 |
|---|---|---|
| internet-bff | annuity | 全局 `http-exchange.base-url: lb://annuity-service`（已正确） |
| intranet-bff | approval/auth/annuity | `http-exchange.channels` 三个 channel，分别绑定 package/class |
| annuity | approval/file/integration/auth | 四个 channel，或按包拆 `@EnableExchangeClients` + 手动 `RestClient` 配置 |
| file | auth | 单 channel/全局 base-url |
| approval | auth | 单 channel/全局 base-url |
| integration | auth | 单 channel/全局 base-url |
| channel-gateway | auth | 已有路由；auth-api 客户端单 channel |

3. 迁移配置，删除旧 `httpexchange.clients` 块。
4. 增加每个服务的 `ExchangeClientWiringTest`：注入客户端 Bean，断言非空；通过自定义 `HttpClientCustomizer` 捕获 base URL。
5. 把检查加入 `scripts/check-architecture-config.sh`：禁止 `httpexchange.clients` 出现。

**回退**：保留旧配置块注释一个版本，确认新配置全绿后删除。

### 5.2 X-02 profile 与密钥隔离

**修复步骤**：

1. `channel-gateway`：
   - 删除 `include: local,dev`。
   - 增加 `spring.profiles.default: dev`。
   - 增加 `spring.profiles.group.internet: local`、`group.intranet: local`、`group.dev: local`。
2. 各服务：
   - 把 `include: local` 改为 profile group（`group.<profile>: local`）或要求显式激活 local。
   - `permission.session.signature-key` 只从环境变量读取，local 文件保留 `dev-` 占位但仅 local profile 生效。
3. 增加 `ProfileIsolationTest`：
   - gateway：`internet` profile 下端口 18081、channels=[INTERNET]、无 `/annuity/**` 等 dev 直连路由。
   - service：`prod` profile 下不包含 `dev-` 密钥。
4. 增加 `scripts/check-architecture-config.sh` 检查 `include: local`/`include: local,dev`。

### 5.3 X-03 权限与验签 fail-fast

**修复步骤**：

1. 新增 `PermissionReadinessValidator`（`shared-permission-starter`）：
   - 读取 `Environment.getActiveProfiles()`，非 `test/local/dev` 时执行检查。
   - 必须有 `PermissionCheckPort` Bean、`RequirePermissionAspect` Bean、非空且非 `dev-` 的 `permission.session.signature-key`。
   - 缺失时抛 `IllegalStateException`，阻止启动。
2. `DefaultAccountIdResolver`：
   - 在非开发 profile 且密钥为空时返回 `null`（fail-closed），不再信任明文 header。
3. 提供显式关闭开关：`permission.enforcement.enabled=false`，仅用于确实不需要 HTTP 权限的服务（需文档说明）。
4. 增加 `PermissionReadinessValidatorTest`：覆盖有/无客户端、有/无密钥、开发/生产 profile。
5. kernel 的 `KernelProductionReadinessValidator` 复用同一套检查，避免重复逻辑。

### 5.4 X-04 跨服务事件通道

**决策建议**：

| 环境 | 通道 | 理由 |
|---|---|---|
| local/test | Redis pub/sub | 已有实现、轻量、便于 E2E |
| dev | Redis pub/sub | 与 local 一致 |
| prod | RocketMQ（推荐）或 Redis（需接受 at-most-once） | Outbox + 恢复任务更适合 MQ；需补消费者 |

**修复步骤**：

1. 删除 `shared-event-starter/src/main/resources/application.yml`，避免默认 `rocketmq.enabled=true`。
2. 定义每个服务的 transport 矩阵（是否发布、是否订阅、topic/tag）。
3. local/test：所有需要跨服务事件的服务开启 `shared.event.redis.enabled=true`；确保 Redis 连接可用。
4. prod：
   - 若选 RocketMQ：为 file/approval/integration/kernel 增加 `rocketmq-spring-boot-starter` 和消费者类，消费者调用现有 `handle*` 方法并经过 `EventReceiptGateway`。
   - 若选 Redis：明确 at-most-once + Outbox 补偿的语义，并在文档中记录。
5. integration-service：
   - 增加事件回执表/复用 receipt；`OutboundFileGeneratedEventListener` 先 `tryConsume(eventId)` 再提交。
6. 增加跨 JVM 测试：Testcontainers Redis（或本地 Redis）启动 file→kernel 或 approval→kernel 事件投递；至少在 CI 可选。
7. 增加 `EventTransportReadinessTest`：生产 profile 下若服务定义 `IntegrationEventConverter` 但没有远程 dispatcher，启动失败或 WARN（按决策）。

### 5.5 X-05 渠道词表统一

1. 明确 `Channel`（业务渠道：NETAPP/TELLER/WECHAT/REGIONAL_CENTER/BANK_BRANCH）与 `ChannelType`（会话渠道：INTERNET/HQ/BRANCH）的职责。
2. 新建 `ChannelMapper`（放在 `shared-security-contract` 或 `business-core-types`），提供唯一的双向映射。
3. 替换 `SessionClaimsWriter`、`AnnuityKernelBatchController.mapChannel`、auth 相关映射中的内联 switch。
4. 增加映射单测，覆盖所有枚举值。

### 5.6 X-06 ArchUnit 与配置防回归

1. `arch-tests/pom.xml` 增加所有 shared jar（至少 `shared-web-core`、`shared-types`、`shared-domain`、`shared-event-core`、`shared-cache-core`、`shared-logging-core`）。
2. 新增规则：
   - `demo-shared` 不依赖业务 API（现有规则扩展 anchor）。
   - `shared-lib` 不包含 `@Configuration/@Component/@Service`。
   - starter jar 不得包含 `application.yml`（可用测试读取 classpath 资源）。
   - kernel application 不依赖 `*.api.event.*` 与 `file-types`（Phase 2 后）。
   - 集成事件监听器必须持有回执网关（扩展到 integration-service）。
3. 新增 `scripts/check-architecture-config.sh`，在 CI/本地构建前执行。

---

## 六、验证矩阵

| 验证项 | 方式 | 通过标准 |
|---|---|---|
| 编译/单测 | `mvn clean install` | 全绿 |
| kernel 领域/应用 | kernel 既有 77 个测试 + 新增 | 全绿 |
| ArchUnit | `arch-tests` | 全绿，新增规则有效 |
| 表单主流程 | 新增 `AnnuityFormFlowEndToEndTest` | confirmUpload→parse→spawn→auto-advance→COMPLETED |
| 审批链路 | 新增 `AnnuityApprovalEndToEndTest` | APPROVAL 挂起→ExternalInteraction→approval API→回调→COMPLETED |
| 文件真实链路 | local profile + 真实 file-service 或 WireMock | token 申请、解析触发、结果拉取、出站生成 |
| 权限 fail-fast | `ApplicationContextRunner` | 缺客户端/密钥/切面时启动失败 |
| profile 隔离 | gateway/service 启动测试 | internet/intranet/prod 不加载 dev/local 配置 |
| httpexchange | 每服务 `ExchangeClientWiringTest` | 客户端 Bean 非空、base URL 正确 |
| 跨服务事件 | Redis/MQ 集成测试或本地多 JVM | file/approval→kernel、file→integration 均收到 |
| 多服务 E2E | Nacos+Redis+PG + `docs/api-link-test-curls.md` | 全链路 curl 通过 |
| 日志/MDC | 启动服务后调用接口 | 5 个 MDC 键出现在日志 |
| 安全 | 无 token/无权限/伪造 header/内部端点直连 | 全部拒绝或不可达 |

---

## 七、提交与发布策略

1. **分支**：每个 Phase 一个 feature 分支，基于 `refactor/business-core-kernel-production`；不直接改 main。
2. **提交粒度**：一个 issue 一个 commit，commit message 使用 `fix(模块): ...` / `refactor(模块): ...`。
3. **开关**：
   - 事件通道：`shared.event.redis.enabled` / `shared.event.rocketmq.enabled`。
   - Mock：`annuity.demo.file-mock.enabled` / `annuity.demo.customer-mock.enabled`。
   - 权限：`permission.enforcement.enabled`。
   - 真实文件网关：`core.integration.file.base-url`。
4. **回滚**：
   - 配置类改动优先于代码类改动，可单独 revert。
   - kernel starter 化保留旧装配路径一个版本（`core.kernel.legacy-scan.enabled`），确认全绿后删除。
   - 事件通道保留双通道开关，出问题可切回本地 Spring 事件（仅演示环境）。
5. **发布顺序**：
   1. auth-service（权限/密钥）
   2. file-service（文件 API/事件）
   3. approval-service（审批事件）
   4. integration-service（事件消费）
   5. annuity-service（kernel starter + 主流程）
   6. BFF / gateway（配置/路由）
6. **文档**：每个 Phase 结束更新 `CODEBUDDY.md`、`docs/service-call-architecture.md`、`docs/business-processing-flow.md`，新增 ADR：
   - `docs/adr/ADR-001-kernel-consumption-model.md`
   - `docs/adr/ADR-002-event-transport.md`
   - `docs/adr/ADR-003-iam-boundary.md`
   - `docs/adr/ADR-004-bff-duplication.md`

---

## 八、风险与应对

| 风险 | 影响 | 应对 |
|---|---|---|
| 表单链路改造触发状态机回归 | 高 | 先写 E2E，保留旧 `advanceStep` 路径；按 form 状态做幂等 |
| 跨服务事件从本地 Spring 切到 Redis/MQ 后消息丢失 | 高 | 保留 Outbox + 恢复任务；先在 local 验证 Redis，再切 prod MQ |
| kernel starter 化导致宿主 Bean 缺失 | 高 | `KernelStarterAssemblyTest` + 启动对比测试（Bean 清单快照） |
| permission fail-fast 导致现有服务启动失败 | 中 | 先在 test/local 关闭校验；生产配置密钥后再开启；提供 `permission.enforcement.enabled=false` 逃生开关 |
| httpexchange 库版本不支持预期 channel 写法 | 中 | Phase 1 先做 spike；不支持则回退到项目自有 `RestClient` 配置，但需统一封装 |
| auth/file API 去 `business-core-types` 影响面大 | 中 | 先建立 `shared-business-vocabulary` 模块，保留旧类型一个版本并标记 deprecated |
| BFF 端口/路由调整影响前端 | 中 | 只改本地端口；网关路由保持路径不变；发布前同步前端 |
| 多服务 E2E 环境不稳定 | 中 | 提供 docker-compose/脚本；CI 可选；关键链路用 WireMock 单服务验证 |

---

## 九、建议执行顺序（看板视图）

```text
NOW（第一周）
  ├─ P0-00 基线/检查脚本
  ├─ X-01 httpexchange spike + 配置迁移
  ├─ X-02 profile 隔离
  ├─ X-03 权限 fail-fast
  └─ K-01/K-02/K-03 表单链路（与 X-01 并行）

NEXT（第二周）
  ├─ A-01 审批接线
  ├─ A-02/03/08 Mock 隔离 + 真实 file profile
  ├─ X-04 跨服务事件通道
  └─ K-11 kernel 就绪校验扩展

THEN（第三周）
  ├─ K-05 business-core-starter
  ├─ K-06/K-07/K-08 边界隔离
  ├─ A-04 annuity 依赖收口
  └─ X-06 ArchUnit 新规则

LATER（第四周及以后）
  ├─ S-03/S-04 web 拆分
  ├─ S-05 permission 拆分
  ├─ S-01/S-02 shared-types/domain 清理
  ├─ F/P/AU/I 服务级越层与死代码
  ├─ BFF 配置/端口/TODO
  └─ Phase 5 多服务 E2E + 文档
```

---

## 十、明确不在本次范围 / 建议延后

1. **拆分 auth-service 为多个微服务**：当前 IAM 子域共享事务与权限判定，拆服务成本高；本次只做边界文档与模块内收口。
2. **kernel 独立部署为流程微服务**：设计明确不引入 BPMN/独立流程服务，保持库形态。
3. **annuity 自有存储持久化**：annuity 是冻结示例，wontfix；真实业务接入时实现持久化。
4. **多进程真 Redis + 多 JVM 并发集成测试**：需要环境，作为 Phase 5 可选/条件执行。
5. **强制 shared-pdf/crypto 找到消费者**：若当前无业务需求，标记 experimental，不为了“用起来”而造需求。
6. **大规模重构 auth 领域模型/approval 业务规则**：超出架构修复范围，另立专项。

---

## 十一、最终交付物清单

- [ ] `.scratch/architecture-fix/<service>.md`：每个服务的问题清单与勾选状态。
- [ ] `scripts/check-architecture-config.sh`：配置/架构静态检查。
- [ ] 新增测试：`AnnuityFormFlowEndToEndTest`、`AnnuityApprovalEndToEndTest`、`PermissionReadinessValidatorTest`、`GatewayProfileIsolationTest`、`ExchangeClientWiringTest`、`EventTransportReadinessTest`。
- [ ] `business-core-starter` 模块与 `KernelStarterAssemblyTest`。
- [ ] 更新 `arch-tests`（anchor + 新规则）。
- [ ] 更新 `docs/` 与 `docs/adr/`。
- [ ] 多服务 E2E 启动脚本与验证报告。

**建议第一步**：先执行 Phase 0 + `X-01`、`X-02`、`X-03` 三个任务。这三项不改变领域模型，但能消除“配置错误导致后续 E2E 全部失真”的最大干扰；完成后再进入 kernel 表单/审批链路改造。
