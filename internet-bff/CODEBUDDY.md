# internet-bff 模块文档

> 由 `/legacy-explain` 自动生成，供 AI 研发助手快速理解模块全貌。

## 一、模块定位

`internet-bff` 是 **互联网渠道的 BFF（Backend For Frontend）层**，负责面向互联网端用户的前端请求适配、聚合和路由。它本身几乎不含业务逻辑——所有核心逻辑委托给 `bff-shared` 共享库和下游业务微服务。

**与 intranet-bff 的关系**：两个 BFF 共享 `bff-shared` 库（位于 `demo-shared/shared-lib/bff-shared/`），各自仅在 adapter 层提供渠道特有的 Controller，并通过 `bff.channel-scope` 配置区分渠道身份。

## 二、架构总览

```
internet-bff
├── internet-bff-api            ← 空（互联网渠道特有 DTO 占位）
├── internet-bff-application    ← 空（互联网渠道特有编排占位）
├── internet-bff-adapter        ← BffBusinessController（唯一 Java 文件）
├── internet-bff-infrastructure ← 空（DDL 存在，代码待填充）
└── internet-bff-starter        ← InternetBffApplication + application.yml
```

**依赖链**：`internet-bff-adapter → bff-shared → (core-api, annuity-api)`

`bff-shared` 承载了全部业务逻辑框架：

```
bff-shared (com.example.bff.shared)
├── business/
│   ├── BffBusinessApi.java          ← @HttpExchange 接口定义（7 端点）
│   ├── BffBusinessService.java      ← 简单转发服务
│   ├── BffAggregationService.java   ← 聚合查询服务
│   ├── BusinessScenarioHandler.java ← 策略接口
│   ├── ScenarioHandlerRegistry.java ← 策略注册表
│   ├── AnnuityScenarioHandler.java  ← 年金场景实现（含并发聚合）
│   ├── BffResponseAssembler.java    ← 聚合响应组装器
│   ├── BffAggregationException.java ← 主实体失败异常
│   └── dto/                         ← 7 个 BFF 请求 DTO + BatchOverviewResponse
└── errorcode/
    └── BffErrorCode.java            ← SCENARIO_NOT_SUPPORTED
```

## 三、策略模式框架

### 3.1 BusinessScenarioHandler 接口

| 方法 | 语义 | 路由目标 |
|------|------|----------|
| `supportedTypes()` | 声明支持的 businessType 集合 | 注册表构建 |
| `createBatch(request)` | 创建批次 | BatchApi |
| `batchDetail(request)` | 查询批次详情 | BatchApi |
| `applyUploadToken(request)` | 申请上传令牌 | FormApi |
| `submitApplication(request)` | 提交申请 | ApplicationApi |
| `applicationDetail(request)` | 查询申请详情 | ApplicationApi |
| `listMaterials(request)` | 查询材料列表 | MaterialApi |
| `batchOverview(request)` | 聚合总览（含默认实现） | 多 API 并发 |

默认 `batchOverview` 回退到仅 `batchDetail`，不含进度和申请列表。

### 3.2 ScenarioHandlerRegistry

- 启动时收集所有 `BusinessScenarioHandler` Bean
- 按 `supportedTypes()` 展开，构建 `Map<String, BusinessScenarioHandler>`
- `handlerFor(businessType)` 未命中时抛 `BusinessException(SCENARIO_NOT_SUPPORTED)`

### 3.3 BffBusinessService vs BffAggregationService

| 服务 | 职责 | 路由方式 |
|------|------|----------|
| `BffBusinessService` | 6 个简单 CRUD 转发 | `registry.handlerFor(businessType).xxxMethod(request)` |
| `BffAggregationService` | 聚合查询（batchOverview） | 同上，但仅 1 个方法 |

## 四、AnnuityScenarioHandler 并发聚合

这是 BFF 层最复杂的逻辑，仅 `batchOverview` 方法有实际编排：

```
batchOverview(request)
  ├── CF1: batchApi.detail()          ← 主实体，失败抛 BffAggregationException（fail-fast）
  ├── CF2: progressApi.batchProgress() ← 辅助，失败降级为 null
  └── CF3: applicationApi.list()       ← 辅助，失败降级为 null
  └── CF.allOf().join() → BffResponseAssembler.assemble(detail, progress, apps)
```

**降级语义**：
- 主实体失败 → 整个请求失败，通过 `BffAggregationException` 传递原始错误码和消息
- 辅助 API 失败 → 降级（progress=null, applications=空列表），主数据仍正常返回

**线程模型**：使用注入的 `Executor asyncExecutor`，在 `CompletableFuture.supplyAsync()` 中执行。

## 五、API 契约

`BffBusinessApi` 定义 7 个 `@PostExchange` 端点：

| 端点 | 路径 | 请求体 | 返回体 |
|------|------|--------|--------|
| createBatch | `/bff/create-batch` | BffCreateBatchRequest | BatchCreatedResponse |
| batchDetail | `/bff/batch-detail` | BffBatchDetailRequest | BatchDetailResponse |
| applyUploadToken | `/bff/apply-upload-token` | BffFormTokenRequest | UploadTokenResponse |
| submitApplication | `/bff/submit-application` | BffSubmitRequest | SubmitResponse |
| applicationDetail | `/bff/application-detail` | BffApplicationDetailRequest | ApplicationDetailResponse |
| listMaterials | `/bff/list-materials` | BffListMaterialsRequest | List\<MaterialItemResponse\> |
| batchOverview | `/bff/batch-overview` | BffBatchOverviewRequest | BatchOverviewResponse |

**设计特点**：
- 所有请求均携带 `businessType` 字段，用于策略路由
- 请求到达 handler 后，`businessType` 被剥离，不再传给下游 API
- 统一返回 `ApiResult<T>`

## 六、internet-bff 渠道特有配置

### 6.1 启动类

```java
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = {"com.example.bff.internet", "com.example.bff.shared"})
```

扫描自身包 + bff-shared 包，使共享组件生效。

### 6.2 application.yml 关键配置

```yaml
server:
  port: 18090
  servlet:
    context-path: /internet-bff

bff:
  channel-scope: INTERNET
```

### 6.3 application-local.yml

- Nacos 服务发现
- `@EnableExchangeClients` 扫描 `com.example.annuity.api.kernel` → 指向 `lb://annuity-service`
- 支持并发调用 5 个下游 annuity-service API

## 七、数据库设计

`t_bff_route_config`（仅 DDL，尚无 Java 代码）：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| business_type | VARCHAR(64) | 业务类型 |
| service_name | VARCHAR(128) | 目标服务名 |
| channel_scope | VARCHAR(32) | 渠道范围（默认 ALL） |
| enabled | BOOLEAN | 启用标志 |
| description | VARCHAR(256) | 描述 |
| created_by / create_time | — | 审计字段 |
| updated_by / update_time | — | 审计字段 |
| deleted | BOOLEAN | 逻辑删除 |
| version | INT | 乐观锁 |

唯一索引：`uk_bff_route_config_bt_cs (business_type, channel_scope) WHERE deleted = FALSE`

> 该表目前无对应的 DO/Mapper/Repository 实现。infrastructure 模块 pom 描述为"动态路由已移除，待 Strategy 模式重构后按需填充"。

## 八、请求流转

```
前端 → internet-gateway → BffBusinessController
         ↓
    BffBusinessService / BffAggregationService
         ↓
    ScenarioHandlerRegistry.handlerFor(businessType)
         ↓
    AnnuityScenarioHandler（或其他实现）
         ↓
    @HttpExchange 代理 → annuity-service / file-service / approval-service
```

## 九、测试覆盖

| 测试类 | 覆盖内容 |
|--------|----------|
| `BffBusinessControllerTest` | Controller 委托给 Service 的 4 个方法 |
| `InternetBffApplicationTest` | Spring 上下文加载 + Controller Bean 注册 |
| `ScenarioHandlerRegistryTest` | 注册/查找/未命中异常 + 默认 batchOverview |
| `AnnuityScenarioHandlerTest` | 全部 7 个方法的映射 + 3 个聚合降级场景 |
| `BffAggregationServiceTest` | 委托给 handler + 未注册类型异常 |
| `BffRequestDtoTest` | 7 个 DTO record 访问器 |
| `BffErrorCodeTest` | 错误码格式 + 唯一性 |

## 十、扩展指南（新增业务线）

1. **在 bff-shared 中新增 Handler**：实现 `BusinessScenarioHandler`，声明 `supportedTypes()`，注入对应业务的 `@HttpExchange` API 代理。
2. **如需自定义聚合**：覆盖 `batchOverview()` 方法，实现并发编排逻辑。
3. **无需修改 internet-bff 代码**：由于 `scanBasePackages` 包含 `com.example.bff.shared`，新 Handler 自动注册。
4. **如有互联网渠道特有 DTO**：放入 `internet-bff-api`；如需渠道特有编排，放入 `internet-bff-application`。

## 十一、关键文件索引

| 文件 | 路径 |
|------|------|
| BFF API 接口 | `demo-shared/shared-lib/bff-shared/src/main/java/.../BffBusinessApi.java` |
| 简单转发服务 | `demo-shared/shared-lib/bff-shared/src/main/java/.../BffBusinessService.java` |
| 聚合查询服务 | `demo-shared/shared-lib/bff-shared/src/main/java/.../BffAggregationService.java` |
| 策略接口 | `demo-shared/shared-lib/bff-shared/src/main/java/.../BusinessScenarioHandler.java` |
| 策略注册表 | `demo-shared/shared-lib/bff-shared/src/main/java/.../ScenarioHandlerRegistry.java` |
| 年金场景实现 | `demo-shared/shared-lib/bff-shared/src/main/java/.../AnnuityScenarioHandler.java` |
| 聚合响应组装 | `demo-shared/shared-lib/bff-shared/src/main/java/.../BffResponseAssembler.java` |
| 互联网 Controller | `internet-bff/internet-bff-adapter/src/main/java/.../BffBusinessController.java` |
| 启动类 | `internet-bff/internet-bff-starter/src/main/java/.../InternetBffApplication.java` |
| 主配置 | `internet-bff/internet-bff-starter/src/main/resources/application.yml` |
| 本地配置 | `internet-bff/internet-bff-starter/src/main/resources/application-local.yml` |
| PostgreSQL DDL | `internet-bff/internet-bff-infrastructure/src/main/resources/schema-pg.sql` |
| MySQL DDL | `internet-bff/internet-bff-infrastructure/src/main/resources/schema-mysql.sql` |
| H2 DDL | `internet-bff/internet-bff-infrastructure/src/main/resources/schema-h2.sql` |
