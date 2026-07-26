# business-core-kernel 公共 API 设计

> 状态: 设计已确认,待实现规划
> 创建日期: 2026-07-26
> 作者: brainstorming session
> 关联文档: `2026-07-26-iam-service-design.md`

---

## 1. 整体定位与边界

### 1.1 kernel 提供什么

- 业务办理的**通用流程框架**:批次 / 表单 / 申请单 / 材料 / 流程编排 / 事件 / SPI
- 一套**公共 HTTP API**(定义在 `business-core-api`,实现在 `business-core-adapter`),覆盖所有业务类型共有的操作
- 一个**业务元数据超集** `BusinessMetaContext`(强类型 record),作为公共接口的统一入参
- 一套**会话与权限基础设施**:`SessionContextResolver`、`BusinessAccessGuard`、`SupportedBusinessTypeValidator`

### 1.2 业务服务做什么

- 引入 `business-core-kernel`(`-api`/`-adapter`/`-application` 等依赖)
- 通过 `BusinessTypeRegistrar` 声明本服务支持的业务类型(枚举或配置)
- 实现 kernel 的 SPI 扩展点(`StepActionHandler` / `BusinessFactExtractor` / `StepExtensionAction`)与 `ApplicationRepository.findByFileTaskId` 等覆写
- **个性化接口**(参数结构特殊、kernel 超集无法承载的)自行在 `xxx-api` 定义、`xxx-adapter` 实现,但仍可复用 kernel 的 `SessionContextResolver` 与 `BusinessAccessGuard` 做鉴权

### 1.3 关键边界

- kernel **不依赖**任何业务服务的 API 模块;业务服务依赖 kernel
- 后续 BFF(互联网 / 内网)只调 kernel 与业务服务的对外 API,不感知 kernel 内部聚合
- 公共接口入参为强类型超集 `BusinessMetaContext`,业务差异通过 `BusinessExtension` 承载,不污染超集

---

## 2. SessionContext 与 sa-token 集成

### 2.1 存储位置

SessionContext 作为 sa-token `Token-Session` 的一部分,与 token 同生命周期,统一存于 Redis,由 iam-service 写入。kernel 不直接读 sa-token(避免强绑定),业务服务无状态,水平扩展时不需要本地 session 同步。

### 2.2 字段(超集,各渠道按需填充)

| 分组 | 字段 | 来源 | 适用渠道 | 说明 |
|---|---|---|---|---|
| 身份 | `userNo` / `userType` / `loginName` / `displayName` | 登录 | 全部 | USER / PARTNER / SYSTEM |
| 渠道 | `channelType` / `clientId` / `clientIp` | 登录 | 全部 | INTERNET / HQ / BRANCH |
| 客户 | `customerNo` / `customerName` | 选计划时确定 | 全部 | |
| 计划 | `planNo` / `planName` / `productNo` / `productName` / `operationModel` | 选计划时写入 | 全部 | |
| 代办 | `isProxy` / `onBehalfOfUserNo` / `onBehalfOfLoginName` | 发起代办时 | **仅 INTERNET** | 网上渠道代办办理,代办人代实际用户办理业务 |
| 二次授权 | `hasSecondaryAuth` / `secondaryAuthSessionId` / `borrowedApproverId` | 二次授权完成时 | **仅 BRANCH** | 网点渠道借用企业授权人身份办理 |
| 权限 | `permissionCodes: Set<String>` | 选计划 / 权限变更时 | 全部 | 功能权限码 + 业务类型办理权限码 |
| 代办范围 | `delegatedPlanNos: Set<String>` | 选计划时写入 | **仅 INTERNET** | 当前用户可代办的计划列表 |

> **渠道办理语义**:
> - **INTERNET(网上渠道)**:可代办办理,`isProxy=true` 时表示代办,需校验 `planNo` 在 `delegatedPlanNos` 内
> - **HQ(总部渠道)**:选计划直接办理,无代办无二次授权
> - **BRANCH(网点渠道)**:二次授权模式,通过 `borrowedApproverId` 借用企业授权人身份办理,不属于代办

### 2.3 写入时机(iam-service 负责)

1. 登录成功 → 写入身份 + 渠道字段
2. 选择计划 → 写入客户 + 计划 + 权限快照(INTERNET 渠道同时写入 `delegatedPlanNos`)
3. 清除计划 → 清空客户 / 计划 / 权限字段
4. 二次授权完成(仅 BRANCH)→ 写入 `hasSecondaryAuth` / `secondaryAuthSessionId` / `borrowedApproverId`
5. 二次授权撤销 / 权限规则变更 / 代办委托变更 → 同步刷新

### 2.4 透传方式

gateway 在请求转发前,从 sa-token `Token-Session` 读取 SessionContext,序列化为 JSON 放入 `X-Session-Context` header(签名或加密由 gateway 统一处理);下游服务通过 `SessionContextResolver` 解析。

### 2.5 kernel 侧组件

- `SessionContextResolver`:从 header 解析并缓存到请求上下文,提供 `require()` / `optional()` 访问器
- 不在 kernel 内部直接读 sa-token,保持 kernel 可独立测试

---

## 3. BusinessMetaContext(业务元数据超集)

### 3.1 定位

前端发起业务办理意图时携带的"业务基础信息超集",用于 `createBatch` 等需要强类型元数据的公共接口。

### 3.2 字段

```java
public record BusinessMetaContext(
    String businessType,        // 业务类型枚举名(必填)
    String customerNo,          // 客户编号(必填)
    String customerName,
    String productNo,           // 产品编号(必填)
    String productName,
    String planNo,              // 计划编号(必填)
    String planName,
    String operationModel,      // 运作模式(必填)
    String accountManagerNo     // 客户经理(可空)
) {}
```

### 3.3 与 SessionContext 的关系

- `BusinessMetaContext` = 前端声明"我要办什么业务"(意图)
- `SessionContext` = gateway 透传"当前用户是谁"(身份)
- kernel 在 `createBatch` 时**校验两者一致**:
  - `meta.planNo` 必须等于 `session.planNo`(防止跨计划办理)
  - `meta.customerNo` 必须等于 `session.customerNo`(防止跨客户办理)
  - `meta.businessType` 是否在用户办理权限范围内由 `BusinessAccessGuard.checkCanHandle` 统一校验(见 §4.2),不通过功能权限码 `permissionCodes` 表达

### 3.4 为什么用强类型超集而不是 Map

- 编译期校验、IDE 提示、`@Valid` 注解可用
- 各业务类型的差异主要在"明细数据"(走 FormUpload),不在"基础元数据"
- 业务服务如果有特殊字段,用 `BusinessExtension`(已有)承载,不污染超集

---

## 4. 权限校验机制

### 4.1 功能权限(垂直越权防护)

- 基于 `session.permissionCodes` 校验接口对应的权限码(接口级访问权限,如 `BATCH_CREATE`、`FORM_UPLOAD`、`APPLICATION_SUBMIT`)
- kernel 提供 `@RequireBusinessPermission("BATCH_CREATE")` 注解 + AOP 拦截器
- 业务服务自定义接口可同样使用此注解
- 注:业务类型办理权限(如 `BUSINESS_ANNUITY_OPEN_HANDLE`)属于数据权限范畴,见 §4.2

### 4.2 数据权限(水平越权防护)

- 校验"当前用户能否办理 `session.planNo` + `meta.businessType`"(业务类型办理权限)
- **代办校验(仅 INTERNET 渠道)**:若 `session.channelType=INTERNET` 且 `session.isProxy=true`,校验 `session.planNo` 在 `session.delegatedPlanNos` 委托范围内
- **二次授权校验(仅 BRANCH 渠道)**:若 `session.channelType=BRANCH`,校验 `session.hasSecondaryAuth=true`(已完成二次授权);HQ 渠道无需此校验
- kernel 提供 SPI 接口:

```java
public interface BusinessAccessGuard {
    /**
     * 校验当前会话用户对指定业务类型的办理权限(含渠道差异化校验:代办 / 二次授权)
     */
    void checkCanHandle(SessionContext session, BusinessMetaContext meta);
}
```

- kernel 提供默认实现 `DefaultBusinessAccessGuard`,按渠道分支校验:
  - **通用校验**(所有渠道):
    - `meta.planNo` 等于 `session.planNo`(防止跨计划办理)
    - `meta.customerNo` 等于 `session.customerNo`(防止跨客户办理)
    - `meta.businessType` 在 `session.permissionCodes` 中(权限码采用 `BUSINESS_{TYPE}_HANDLE` 命名约定,如 `BUSINESS_ANNUITY_OPEN_HANDLE`)
  - **INTERNET 渠道额外校验**:
    - 若 `isProxy=true`,校验 `session.planNo` 在 `session.delegatedPlanNos` 内
  - **BRANCH 渠道额外校验**:
    - `session.hasSecondaryAuth=true`(必须完成二次授权才能办理)
  - **HQ 渠道**:无额外校验
- 业务服务可提供自定义实现覆盖(如年金服务需要额外校验外资业务准入),通过 `@ConditionalOnMissingBean` 兜底

### 4.3 SupportedBusinessTypeValidator

- 业务服务通过 `BusinessTypeRegistrar` 注册本服务支持的 BusinessType
- Controller 入口调用 `validator.validate(meta.businessType)`,防止本服务被请求到不归自己处理的业务类型

### 4.4 在 Controller 中的使用模式

```java
@PostMapping("/create")
public ApiResult<BatchCreatedResponse> createBatch(@Valid @RequestBody CreateBatchCommand command) {
    SupportedBusinessTypeValidator.validate(command.businessType());    // 本服务支持?
    SessionContext session = sessionContextResolver.require();          // 取会话
    accessGuard.checkCanHandle(session, command.metaContext());         // 功能+数据权限
    // ... 调用 appService
}
```

---

## 5. 公共 API 清单

所有接口定义在 `business-core-api`,路径前缀 `/core`,统一 `@HttpExchange` + `@PostExchange`,返回 `ApiResult<T>`。

### 5.1 BusinessBatchApi(`/core/batch`)

| 方法 | 路径 | 入参 | 返回 | 说明 |
|---|---|---|---|---|
| findActive | `/active` | `FindActiveBatchQuery{planNo, businessType}` | `BatchSummaryResponse` | 查询未完成 / 处理中批次 |
| create | `/create` | `CreateBatchCommand{metaContext, operatorRemark}` | `BatchCreatedResponse` | 创建新批次 |
| detail | `/detail` | `GetBatchDetailQuery{batchId}` | `BatchDetailResponse` | 批次详情(含表单 / 申请单摘要) |
| cancel | `/cancel` | `CancelBatchCommand{batchId, reason}` | `Void` | 取消未提交批次 |

### 5.2 BusinessFormApi(`/core/form`)

| 方法 | 路径 | 入参 | 返回 | 说明 |
|---|---|---|---|---|
| applyUploadToken | `/upload-token` | `ApplyUploadTokenCommand{batchId, fileName, fileSize, contentType}` | `UploadTokenResponse` | 向文件服务申请上传 token |
| confirmUpload | `/confirm-upload` | `ConfirmUploadCommand{batchId, formId, fileMd5}` | `Void` | 确认上传完成,触发表单解析 |
| delete | `/delete` | `DeleteFormCommand{batchId, formId}` | `Void` | 删除已上传表单 |
| status | `/status` | `GetFormStatusQuery{formId}` | `FormStatusResponse` | 查询表单解析进度 / 结果 |

### 5.3 BusinessApplicationApi(`/core/application`)

| 方法 | 路径 | 入参 | 返回 | 说明 |
|---|---|---|---|---|
| list | `/list` | `FindApplicationListQuery{batchId, status?}` | `List<ApplicationSummaryResponse>` | 批次下申请单列表 |
| detail | `/detail` | `GetApplicationDetailQuery{applicationId}` | `ApplicationDetailResponse` | 申请单详情 |
| advance | `/advance` | `AdvanceStepCommand{applicationId, actionPayload: Map<String,Object>?}` | `AdvanceStepResponse` | 推进到下一节点;`actionPayload` 为可选的业务参数,由各业务服务的 `StepActionHandler` 解析 |
| submit | `/submit` | `SubmitApplicationCommand{applicationId}` | `SubmitResponse` | 最终提交(触发审批判断) |

### 5.4 MaterialAppApi(`/core/material`)

| 方法 | 路径 | 入参 | 返回 | 说明 |
|---|---|---|---|---|
| bindIndividual | `/individual/bind` | `BindIndividualMaterialCommand{applicationId, materialItem}` | `Void` | 单个材料绑定 |
| bindPackage | `/package/bind` | `BindPackageMaterialCommand{applicationId, materialPackageId}` | `Void` | 材料包绑定 |
| unbind | `/unbind` | `UnbindMaterialCommand{applicationId, materialItemId}` | `Void` | 解绑材料 |
| list | `/list` | `ListMaterialsQuery{applicationId}` | `List<MaterialItemDTO>` | 申请单材料清单 |
| checkCompleteness | `/completeness` | `CheckCompletenessQuery{applicationId}` | `CompletenessResponse` | 校验材料完整性 |

### 5.5 BusinessProgressApi(`/core/progress`)

可选,基于事件订阅的进度推送查询兜底。

| 方法 | 路径 | 入参 | 返回 | 说明 |
|---|---|---|---|---|
| batchProgress | `/batch/summary` | `GetBatchProgressQuery{batchId}` | `BatchProgressResponse` | 批次整体进度(表单数 / 申请单数 / 成功率) |

> 实时推送走 SSE / WebSocket 由 BFF 层处理,kernel 只提供查询兜底。

### 5.6 个性化接口归属

- 业务服务特殊参数(如年金的"缴费金额、计划类型、外资标志")在 `xxx-api` 自定义接口,入参用业务服务自己的 Command
- 业务服务自定义接口仍可调用 kernel 的 `SessionContextResolver` / `BusinessAccessGuard` / `FlowOrchestrationService`

---

## 6. 业务服务接入示例

以 `annuity-service` 为例:

```java
// 1. 声明支持的业务类型
@Configuration
public class AnnuityBusinessTypeConfig {
    @Bean
    public BusinessTypeRegistrar annuityTypeRegistrar() {
        return BusinessTypeRegistrar.of(
            BusinessType.ANNUITY_OPEN,
            BusinessType.ANNUITY_CHANGE
        );
    }
}

// 2. 引入 kernel 依赖后,公共 API 自动可用(通过 @ComponentScan 或自动配置)
//    annuity-service 无需重新实现 /core/batch/create

// 3. 个性化接口(如有)
@HttpExchange("/annuity/special")
public interface AnnuitySpecialApi {
    @PostExchange("/calc")
    ApiResult<CalcResponse> calc(@Valid @RequestBody AnnuityCalcCommand cmd);
}

@RestController
public class AnnuitySpecialController implements AnnuitySpecialApi {
    private final SessionContextResolver sessionResolver;
    private final BusinessAccessGuard accessGuard;
    // 复用 kernel 鉴权能力
}
```

### 6.1 自动装配

- `business-core-starter` 通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册 Controller 与基础 Bean
- 业务服务引入 `business-core-starter` 即获得公共 API;若要禁用某些接口,通过 `core.kernel.enabled-apis` 配置排除

---

## 7. 实现范围与不在本 spec 范围

### 7.1 本 spec 覆盖

- `business-core-api` 的 5 类公共 API 接口定义与请求 / 响应 DTO
- `business-core-adapter` 的 Controller 实现骨架(含鉴权调用)
- `SessionContext` / `SessionContextResolver` / `BusinessAccessGuard` / `SupportedBusinessTypeValidator` / `BusinessTypeRegistrar` 基础设施
- `BusinessMetaContext` 强类型超集定义
- `business-core-starter` 自动装配

### 7.2 不在本 spec 覆盖

- BFF 层(互联网 / 内网)的设计与实现
- iam-service 内部对 SessionContext 的写入逻辑(由 `2026-07-26-iam-service-design.md` 负责)
- gateway 对 `X-Session-Context` header 的签名 / 加密细节
- 各业务服务的个性化接口实现
- 实时进度推送的 SSE / WebSocket 协议

---

## 8. 后续接入指南(新增接口怎么操作)

当后续需要新增一个公共接口时,按以下步骤操作:

1. **判断归属**:该接口是否所有业务类型都需要?如果是,放 kernel;否则放业务服务自定义接口
2. **API 层定义**:在 `business-core-api` 新增方法到对应 Api 接口(或新建 Api 接口),路径前缀 `/core`,使用 `@HttpExchange` / `@PostExchange`
3. **DTO 设计**:入参用 Command / Query(record + `@Valid`),返回用 Response DTO;若入参需要业务元数据,复用 `BusinessMetaContext`
4. **应用层服务**:在 `business-core-application` 扩展 AppService 方法,通过 `FlowOrchestrationService` 或 Repository 编排
5. **Adapter 实现**:在 `business-core-adapter` 实现 Controller,入口依次调用 `SupportedBusinessTypeValidator.validate` → `sessionContextResolver.require` → `accessGuard.checkCanHandle` → appService
6. **DTO 转换**:通过 MapStruct Converter 完成 Command → 领域对象、领域对象 → Response DTO 的转换,禁止在 Controller 中直接转换
7. **单元测试**:覆盖 Controller 鉴权路径、AppService 编排路径、Converter 转换契约
8. **自动装配**:若新建了 Api 接口对应的 Controller,在 `business-core-starter` 自动配置中注册

业务服务个性化接口遵循同样的鉴权流程(步骤 5),只是接口定义与 DTO 在业务服务自己的 `xxx-api` / `xxx-adapter` 模块。

---

## 9. 风险与待办

| 风险 / 待办 | 处理方式 |
|---|---|
| `X-Session-Context` header 被伪造 | 由 gateway 统一签名 / 加密,kernel 侧 `SessionContextResolver` 校验签名 |
| 业务服务需要覆盖默认 `BusinessAccessGuard` | 通过 `@ConditionalOnMissingBean` 兜底,业务服务自定义实现优先 |
| 公共 API 与业务服务个性化 API 路径冲突 | 公共 API 统一 `/core` 前缀,业务服务用 `/annuity` 等业务前缀,从命名空间上隔离 |
| 实时进度推送协议未定 | 本 spec 仅提供查询兜底,推送协议由 BFF spec 负责 |
| iam-service 写入 SessionContext 的具体实现 | 由 `2026-07-26-iam-service-design.md` 负责 |
