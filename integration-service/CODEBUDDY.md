# integration-service CODEBUDDY.md

> `/legacy-explain` 自动生成 — 2026-09-08

## 一、模块定位

integration-service 是外部交易系统集成服务，封装与核心交易系统的 HTTP 通信，对外提供统一的交易查询/提交接口。当前仅实现一个交易码 **5564（企业年金计划投资组合查询）**，但基础设施层设计了可扩展的通用交易框架，新增交易码只需添加 Req/Res DTO + Gateway 实现 + `@TradeCode` 注解。

## 二、架构概览

```
integration-service
├── integration-service-types       # 领域原语（当前为占位，仅 package-info）
├── integration-service-domain      # 领域层（2 Gateway SPI + 2 Domain Model）
├── integration-service-api         # API 协议层（1 @HttpExchange + 2 DTO）
├── integration-service-application # 应用层（1 Service Interface + 1 Impl）
├── integration-service-adapter     # 适配器层（1 Controller + 3 Converter）
├── integration-service-infrastructure # 基础设施层（通用交易框架 + 5564 特化实现）
└── integration-service-starter     # 启动入口
```

**注意**：本服务没有 `types` 层的实际内容（仅 package-info.java），ID 类型直接使用 shared-types 的 `String`。

## 三、领域层（domain）

### 3.1 Gateway SPI 接口

| 接口 | 方法 | 状态 |
|------|------|------|
| `TradeQueryGateway` | `queryPortfolioBalance(TradePortfolioQuery)` | 已实现 |
| `TradeGateway` | `queryPortfolioBalance(TradePortfolioQuery)` | **与 TradeQueryGateway 完全重复** |

**问题**：`TradeGateway` 和 `TradeQueryGateway` 方法签名完全一致，`TradeQueryGatewayImpl` 只实现后者，前者无实现类。推测 `TradeGateway` 是遗留或占位接口。

### 3.2 领域模型

#### TradePortfolioQuery（查询条件）

```java
record TradePortfolioQuery(
  String tellerNo, String tellerName, String channel,
  String enterpriseCustomerNo, String enterprisePlanNo, String annuityPlanNo,
  String accountNo,                    // 可选
  Pagination pagination                // 分页
)
```

- 工厂方法：`of(tellerNo, tellerName, channel, customerNo, planNo, annuityPlanNo, pageQuery)`
- Wither：`withAccount(String)`, `nextPage()`

#### TradePortfolioBalance（查询结果）

```java
record TradePortfolioBalance(
  PageInfo pageInfo,
  List<PortfolioItem> items
)
```

嵌套值对象：

| 值对象 | 字段 | 业务规则 |
|--------|------|----------|
| `PortfolioItem` | accountNo, accountName, portfolioNo, portfolioName, valuationDate, lockStatus, sharesInfo, netValueInfo, marketValueInfo | 顶层持仓明细 |
| `LockStatus` | subscriptionLocked, redemptionLocked | 封闭期状态 |
| `SharesInfo` | current, available, frozen | compact constructor 校验: current == available + frozen |
| `NetValueInfo` | unitValue, navDate | 净值 |
| `MarketValueInfo` | total, available, frozen | 市值 |

便捷方法：

- `totalMarketValue()` — 所有持仓市值求和
- `calculateProfitRate()` — 收益率 = (市值 - 份额 × 净值) / (份额 × 净值)
- `canSubscribe()` / `canRedeem()` — 判断是否有任一持仓可申购/赎回

## 四、API 层（api）

### 4.1 TradeQueryApi

```java
@HttpExchange("/api/v1/trade/portfolio")
public interface TradeQueryApi {
    @PostExchange("/balance")
    ApiResult<PortfolioBalanceDTO> queryPortfolioBalance(@Valid @RequestBody PortfolioQueryDTO query);
}
```

### 4.2 DTO 定义

| DTO | 用途 | 关键字段 |
|-----|------|----------|
| `PortfolioQueryDTO` | 请求 | enterpriseCustomerNo(@NotBlank), enterprisePlanNo(@NotBlank), annuityPlanNo(@NotBlank @Size(max=20)), accountNo(可选), pageQuery |
| `PortfolioBalanceDTO` | 响应 | items(PageData<PortfolioItemDTO>) |
| `PortfolioItemDTO` | 持仓明细 | accountNo, portfolioNo, canSubscribe, canRedeem, shares/sharesInfo, netValue, marketValue |
| `PageQuery` | 分页请求 | startPos(默认1), pageSize(默认20) — 来自 shared-api |
| `PageData<T>` | 分页响应 | items, totalCount, hasMore — 来自 shared-api |

## 五、应用层（application）

### TradeQueryAppService / TradeQueryAppServiceImpl

```java
@Service
@Transactional(readOnly = true)
public class TradeQueryAppServiceImpl implements TradeQueryAppService {
    private final TradeQueryGateway tradeQueryGateway;

    @Override
    public TradePortfolioBalance queryPortfolioBalance5564(TradePortfolioQuery query) {
        return tradeQueryGateway.queryPortfolioBalance(query);
    }
}
```

**特点**：极薄的应用层，直接委托 Gateway，方法名带交易码后缀 `5564` 以标识具体交易。`@Transactional(readOnly = true)` — 查询场景合理。

## 六、适配器层（adapter）

### 6.1 TradeQueryAdapter

```java
@RestController
@AllArgsConstructor
public class TradeQueryAdapter implements TradeQueryApi {
    @Override
    @RequirePermission(business = "TRADE_QUERY", action = "QUERY_BALANCE", category = PermissionCategory.PLATFORM)
    public ApiResult<PortfolioBalanceDTO> queryPortfolioBalance(@Valid @RequestBody PortfolioQueryDTO query) { ... }
}
```

权限：`@RequirePermission` 声明式权限校验（shared-permission-starter 提供）。

### 6.2 Converter 体系

| Converter | 方向 | 关键映射 |
|-----------|------|----------|
| `PortfolioConverter` | DTO → Domain | annuityProductNo → annuityPlanNo, pageRequest → pagination |
| `PortfolioBalanceConverter` | Domain → DTO | TradePortfolioBalance → PageData<PortfolioItemDTO> via @Named("domainToPageData") |
| `CommonConverter` | 通用 | PageQuery → Pagination |

## 七、基础设施层（infrastructure）— 通用交易框架

### 7.1 架构设计

```
Controller
    ↓ (AppService)
TradeQueryGatewayImpl ← extends BaseTradeGateway
    ↓ performTradeCall()
TradeQueryClient (Retrofit2)
    ↓ HTTP POST
外部交易系统
```

### 7.2 BaseTradeGateway（模板方法模式）

核心方法 `performTradeCall()`，7 步流水线：

```
1. 从请求类读取 @TradeCode 注解 → 获取交易码
2. 创建 TradeReqHead（含交易码 + 默认值）
3. 应用 headCustomizer（可选：注入柜员/渠道信息）
4. 组装 TradeRootRequest<Object> 信封
5. 调用 TradeQueryClient.execute() → TradeRootResponse<Res>
6. Jackson convertValue 反序列化到具体响应类
7. 应用 domainMapper 将响应转为领域模型
```

4 个重载（从全参数到最小参数）：

| 重载 | headCustomizer | responseClass | domainMapper |
|------|----------------|---------------|--------------|
| 完整 | UnaryOperator<TradeReqHead> | Class<Res> | Function<Res, Target> |
| 无 head | - | Class<Res> | Function<Res, Target> |
| 无 mapper | UnaryOperator | Class<Res> | - |
| 最简 | - | Class<Res> | - |

### 7.3 @TradeCode 注解

```java
@Target(TYPE)
public @interface TradeCode {
    String code();
    String description() default "";
}
```

标注在请求 DTO 类上，`BaseTradeGateway` 运行时通过反射读取。

### 7.4 TradeQueryClient（Retrofit2）

```java
@RetrofitClient(baseUrl = "${external.trade.url}")
public interface TradeQueryClient {
    @POST("/")
    CompletableFuture<TradeRootResponse<Object>> execute(@Body TradeRootRequest<Object> request);
}
```

统一 POST 到根路径 `/`，通过信封中的交易码路由。返回 `CompletableFuture` — 支持虚拟线程异步。

### 7.5 交易信封模型

#### 请求信封

```
TradeRootRequest<T>
  └─ AppRequest<T>          @JsonProperty("AppRequest")
       ├─ AppReqHead        交易码 + 请求序号
       └─ T                 业务请求体 (Query5564Req 等)
```

#### 响应信封

```
TradeRootResponse<T> implements ExternalResult<T>
  └─ AppResponse<T>        @JsonProperty("AppResponse")
       ├─ AppRspHead        交易码 + 响应序号
       └─ T                 业务响应体 (Query5564Res 等)
```

`TradeRootResponse<T>` 实现 `ExternalResult<T>`（shared-client 提供），统一错误处理：
- `isSuccess()` — 检查 `msgCode == "0000"`
- `getData()` — 返回 body
- `getErrorCode()` / `getErrorMsg()` — 从 `StatusInfo` 提取
- `dataOrThrow()` — 失败时抛 `SystemException(TradeErrorCode.TRADE_API_ERROR)`

#### TradeReqHead / TradeRspHead

| 字段 | @JsonProperty | 说明 |
|------|---------------|------|
| TradeTime | 交易时间 | |
| TradeDate | 交易日期 | |
| TradeCode | 交易码 | |
| Reserved | 保留字段 | |
| ReqSerialNo | 请求流水号 | 仅响应 |
| StatusInfo | 状态信息 | 仅响应 |

`TradeReqHead.createDefault(code)` 工厂方法；`withTeller(no, name)` / `withChannel(channel)` wither 方法。

#### TradeSource

```java
record TradeSource(
  NetworkName, OrganName, NetworkNo, ZoneNo, ZoneName,
  TradeChannel, TellerName, TellerNo, OrganNo
)
```

`createDefault()` + `withTeller(no, name)` / `withChannel(channel)`。

### 7.6 错误码

| 枚举值 | 错误码 | 消息 |
|--------|--------|------|
| TRADE_API_ERROR | SERVICE.INTEGRATION.0001 | 交易接口调用异常 |
| TRADE_BALANCE_NOT_ENOUGH | SERVICE.INTEGRATION.0002 | 账户余额不足 |
| TRADE_ACCOUNT_FROZEN | SERVICE.INTEGRATION.0003 | 账户已被冻结 |
| TRADE_CHANNEL_CLOSED | SERVICE.INTEGRATION.0004 | 交易渠道已关闭 |

**注意**：错误码 0002-0004 是预定义的，当前 5564 查询场景不会触发，为后续提交类交易预留。

## 八、基础设施层（infrastructure）— 5564 特化实现

### 8.1 Query5564Req

```java
@TradeCode(code = "5564")
record Query5564Req(
  @JsonProperty("QYKHH") String enterpriseCustomerNo,
  @JsonProperty("QYJHBH") String enterprisePlanNo,
  @JsonProperty("NJJHBH") String annuityPlanNo,
  @JsonProperty("ZHBH")   String accountNo,          // 可选
  @JsonProperty("BeginRecordPos") Integer beginRecordPos,
  @JsonProperty("RecordCount")   Integer recordCount
)
```

字段名保持外部系统的拼音缩写，通过 `@JsonProperty` 映射。

### 8.2 Query5564Res

```java
record Query5564Res(
  TotalCount, BeginRecordPos, RecordCount, HasMoreFlag,  // 分页
  QYKHH, QYJHBH,                                        // 条件回显
  Result → Item[] → PortfolioItem(18个字段)              // 持仓明细
)
```

便捷方法：`items()` → 空安全返回列表；`hasMore()` → `"1".equals(hasMoreFlag)`。

### 8.3 TradeQueryGatewayImpl

```java
@Component
public class TradeQueryGatewayImpl extends BaseTradeGateway implements TradeQueryGateway {

  @Override
  public TradePortfolioBalance queryPortfolioBalance(TradePortfolioQuery query) {
    Query5564Req reqBody = tradePortfolioInfraMapper.to5564Req(query);

    return performTradeCall(
      reqBody,
      head -> head.withTeller(query.tellerNo(), query.tellerName())
                  .withChannel(query.channel()),
      Query5564Res.class,
      tradePortfolioInfraMapper::toBalance
    );
  }
}
```

完整使用 BaseTradeGateway 的 4 参数重载：请求体 → head 定制 → 响应类 → 领域映射。

### 8.4 TradePortfolioInfraMapper

`@Mapper(componentModel = "spring", unmappedTargetPolicy = IGNORE)`

**请求转换**（Domain → Infra DTO）：
- MapStruct 自动映射：`enterpriseCustomerNo`, `enterprisePlanNo`, `annuityPlanNo`, `accountNo`
- 手动映射：`pagination.startPos → beginRecordPos`, `pagination.pageSize → recordCount`

**响应转换**（Infra DTO → Domain Model）：
- 主入口 `toBalance(Query5564Res)` → 构建 `TradePortfolioBalance`
- 分页信息：`buildPageInfo(res)` → `PageInfo(totalCount, startPos, returnedCount, hasMore)`
- 持仓列表：`buildPortfolioItems()` → 逐项转换 `toPortfolioItem()`
- 内聚值对象构建：`buildLockStatus()`, `buildSharesInfo()`, `buildNetValueInfo()`, `buildMarketValueInfo()`
- 空值安全：所有 `BigDecimal` 默认 `ZERO`，`Integer` 默认 `0`
- 特殊处理：`Query5564Res` 无 `navDate` 字段，复用 `valuationDate` 作为净值日期

**反向转换**（Domain → Infra DTO）：
- `toRes(TradePortfolioBalance)` → 用于缓存/同步场景
- `toInfraItem()` → 反向构建 `Query5564Res.PortfolioItem`

## 九、启动与配置

### 9.1 IntegrationApplication

```java
@EnableDiscoveryClient
@EnableExchangeClients(basePackages = "com.example.auth.api")
@SpringBootApplication
public class IntegrationApplication { ... }
```

`@EnableExchangeClients` 扫描 auth-api 包 — 用于 `@RequirePermission` 的实时权限校验 HTTP 调用。

### 9.2 关键配置

| 配置项 | 值 | 说明 |
|--------|-----|------|
| server.port | 18083 | 服务端口 |
| spring.application.name | cjannuity-channel-integration | Nacos 服务名 |
| retrofit.global-timeout | read=3s, write=3s, connect=1s | HTTP 超时 |
| shared.cache.mode | L1_ONLY | 仅本地缓存 |
| spring.threads.virtual.enabled | true | 虚拟线程 |
| spring.profiles.include | local,sofa | 默认包含本地+SOFA 配置 |
| httpexchange.clients.com.example.auth.api.url | lb://auth-service | 权限校验服务地址 |

### 9.3 数据源

使用 OceanBase 数据库（兼容 MySQL 协议），但当前 5564 交易为纯查询无本地表。

## 十、请求完整流转（5564 为例）

```
1. HTTP POST /api/v1/trade/portfolio/balance
2. TradeQueryAdapter.queryPortfolioBalance(PortfolioQueryDTO)
   → @RequirePermission 校验权限（调用 auth-service）
   → PortfolioConverter.toQuery(dto) → TradePortfolioQuery
3. TradeQueryAppServiceImpl.queryPortfolioBalance5564(query)
   → 委托 TradeQueryGateway
4. TradeQueryGatewayImpl.queryPortfolioBalance(query)
   → TradePortfolioInfraMapper.to5564Req(query) → Query5564Req
   → BaseTradeGateway.performTradeCall():
       ① 读取 @TradeCode("5564")
       ② TradeReqHead.createDefault("5564")
       ③ head.withTeller(tellerNo, tellerName).withChannel(channel)
       ④ 组装 TradeRootRequest<Query5564Req>
       ⑤ TradeQueryClient.execute(request) → TradeRootResponse<Object>
       ⑥ Jackson convertValue → TradeRootResponse<Query5564Res>
       ⑦ TradePortfolioInfraMapper.toBalance(res) → TradePortfolioBalance
5. PortfolioBalanceConverter.toResponse(balance) → PortfolioBalanceDTO
6. ApiResult<PortfolioBalanceDTO> → HTTP Response
```

## 十一、扩展新交易码的步骤

1. **infrastructure** — 新增 `@TradeCode(code="XXXX")` 标注的 `QueryXXXXReq` record
2. **infrastructure** — 新增 `QueryXXXXRes` record
3. **infrastructure** — 在 `TradePortfolioInfraMapper`（或新 Mapper）添加转换方法
4. **domain** — 新增或复用 Gateway 接口方法
5. **infrastructure** — 新增 Gateway 实现类，extends BaseTradeGateway，调用 `performTradeCall()`
6. **application** — 新增 AppService 方法
7. **api** — 新增 DTO
8. **adapter** — 新增/扩展 Controller 和 Converter

## 十二、测试覆盖

| 测试类 | 覆盖点 |
|--------|--------|
| `TradeRootResponseTest` | TradeRootResponse 空值安全（appResponse=null 时不 NPE） |
| `TradeErrorCodeTest` | 错误码契约（格式匹配、码段校验、消息无占位符、唯一性） |

## 十三、关键文件索引

| 层 | 文件 | 职责 |
|----|------|------|
| domain | `TradeQueryGateway.java` | 交易查询 SPI |
| domain | `TradeGateway.java` | 重复接口（⚠️ 待清理） |
| domain | `TradePortfolioBalance.java` | 持仓余额领域模型（含业务规则） |
| domain | `TradePortfolioQuery.java` | 查询条件领域模型 |
| api | `TradeQueryApi.java` | @HttpExchange 接口 |
| api | `PortfolioQueryDTO.java` | 请求 DTO |
| api | `PortfolioBalanceDTO.java` | 响应 DTO |
| application | `TradeQueryAppServiceImpl.java` | 应用服务（极薄委托） |
| adapter | `TradeQueryAdapter.java` | REST 控制器 |
| adapter | `PortfolioConverter.java` | DTO→Domain 转换 |
| adapter | `PortfolioBalanceConverter.java` | Domain→DTO 转换 |
| adapter | `CommonConverter.java` | 分页转换 |
| infra | `BaseTradeGateway.java` | 通用交易网关模板 |
| infra | `TradeQueryClient.java` | Retrofit2 HTTP 客户端 |
| infra | `@TradeCode` | 交易码声明注解 |
| infra | `TradeReqHead/RspHead/Source` | 信封模型 |
| infra | `TradeRootRequest/Response` | 请求/响应信封 |
| infra | `TradeQueryGatewayImpl.java` | 5564 网关实现 |
| infra | `Query5564Req/Res.java` | 5564 报文 DTO |
| infra | `TradePortfolioInfraMapper.java` | 基础设施层转换器 |
| infra | `TradeErrorCode.java` | 错误码枚举 |
| starter | `IntegrationApplication.java` | 启动类 |
