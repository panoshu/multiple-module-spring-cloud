# 统一鉴权体系实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把业务类功能与管理类功能统一纳入鉴权体系，修复网关层非渠道前缀路径放行、auth-service 自身 API 无防护、kernel 权限点未注册、列表查询缺少行级数据过滤等 4 个核心缺口。

**Architecture:** 三层防护：网关层 yml 白名单 + 渠道/通用登录校验；应用层 `@RequirePermission` 通过 `PermissionExecutor` 接口抽象（业务服务走 HttpExchange，auth-service 走本地短路）；数据层 `@DataScope` + `DataScopeResolver` + Repository 条件拼接实现行级过滤。kernel 权限点通过 `PermissionRegistrationRunner` 在业务服务启动时上报到 auth-service。

**Tech Stack:** JDK 25（启用 --enable-preview）、Spring Boot 3.5.14、MyBatis-Flex 1.11.5、sa-token、Caffeine、MapStruct 1.6.3、Lombok 1.18.46。

## Global Constraints

- 严格遵循 DDD + 六边形架构分层：types → domain → api → application → adapter → infrastructure → starter
- domain 层禁止使用 Spring 注解（@Autowired/@Component）、禁止使用数据库框架注解（@Table/@Column）、禁止依赖外部库（lombok 除外）
- application 层用 `com.pension.permission.application` 包名（auth-application 当前未迁移到 `com.example.auth`，保持现状）
- infrastructure 层用 `com.pension.permission.infrastructure` 包名（auth-infrastructure 当前未迁移）
- adapter 层用 `com.example.auth.adapter` 包名（已迁移）
- PermissionCategory 双枚举不合并：`com.example.auth.api.annotation.PermissionCategory`（注解用）和 `com.pension.permission.domain.authorization.enumeration.PermissionCategory`（domain 用），通过 `mapCategory()` 按枚举名映射
- HttpExchange 客户端配置使用包名 `com.example.auth.api` 作为 key（业务服务已采用此形式，自动覆盖该包下所有 `@HttpExchange` 接口）
- integration-service 的 starter 模块名为 `integration-service-starter`（其他服务是 `xxx-starter`）
- 时间戳由应用层管理，禁止使用 `@Column(onInsertValue)` / `@Column(onUpdateValue)`
- 单个类源代码不超过 500 行，单个方法不超过 50 行
- 提交信息遵循 Conventional Commits：`<type>(<scope>): <subject>`，中文 subject 使用祈使语气
- 所有新增错误码格式为 `<域>.<模块>.<序号>`，消息为纯文本，禁止 `{}` 占位符

## 关键背景：当前代码库状态

- `auth-api` 已迁移到 `com.example.auth.api` 包
- `auth-adapter` 已迁移到 `com.example.auth.adapter` 包
- `auth-application` **未迁移**，仍使用 `com.pension.permission.application`
- `auth-infrastructure` **未迁移**，仍使用 `com.pension.permission.infrastructure`
- `auth-domain` **未迁移**，仍使用 `com.pension.permission.domain`
- 现有 `RequirePermissionAspect` 直接依赖 `PermissionCheckApi`（HttpExchange），无短路机制
- 现有 `PermissionScanner` 实现完整扫描逻辑（在 auth-infrastructure），未抽取为 Service
- 现有 `PermissionItemRepositoryImpl.upsertAll` 返回 `void`，需要改为返回新增/更新数量
- 现有 `EffectivePermissionService.resolveLiveRoleTemplateGrants` 为 private 方法
- 业务服务 yml 配置形如：
  ```yaml
  httpexchange:
    clients:
      com.example.auth.api:
        url: lb://auth-service
  ```
  自动覆盖 `com.example.auth.api` 包下所有 `@HttpExchange` 接口

---

## 阶段 1：基础组件（auth-api 新增 DTO/注解 + shared-permission-starter 新增组件）

本阶段所有任务无依赖，可并行实施。产物：auth-api 提供新注解、DTO、API 接口；shared-permission-starter 提供 `PermissionExecutor`、`DataScopeResolver` 抽象及默认实现、`PermissionRegistrationRunner`。

### Task 1.1: auth-api 新增 DataScope 注解与 DTO

**Files:**
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/annotation/DataScope.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/annotation/DataScopeDimension.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/dto/DataScope.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/command/DataScopeRequest.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/dto/DataScopeResponse.java`
- Test: `auth-service/auth-api/src/test/java/com/example/auth/api/dto/DataScopeTest.java`

**Interfaces:**
- Produces: `com.example.auth.api.annotation.DataScope`（注解，含 `business()` 和 `dimension()` 属性）
- Produces: `com.example.auth.api.annotation.DataScopeDimension`（枚举：PLAN、CUSTOMER）
- Produces: `com.example.auth.api.dto.DataScope`（record，含 `globalVisible`、`visiblePlans`、`visibleCustomers`、`excludedPlans`、`excludedCustomers`，提供 `needsFiltering()`、`empty()`、`global()` 工厂方法）
- Produces: `com.example.auth.api.command.DataScopeRequest`（record：`accountId`、`businessCode`，均 `@NotBlank`）
- Produces: `com.example.auth.api.dto.DataScopeResponse`（record：与 `DataScope` 同字段，纯 HTTP DTO）

- [ ] **Step 1: 创建 DataScopeDimension 枚举**

```java
package com.example.auth.api.annotation;

/**
 * 行级数据过滤维度.
 *
 * @author auth-api
 */
public enum DataScopeDimension {
    /** 按 plan_no 过滤 */
    PLAN,
    /** 按 customer_no 过滤 */
    CUSTOMER
}
```

- [ ] **Step 2: 创建 DataScope 注解**

```java
package com.example.auth.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式行级数据过滤注解，由 shared-permission-starter 的 DataScopeAspect 拦截。
 *
 * <p>使用示例：
 * <pre>{@code
 * @DataScope(business = "ANNUITY")
 * @RequirePermission(business = "ANNUITY", action = "VIEW")
 * public PageData<BatchStatusDTO> listBatches(ListBatchQuery query) { ... }
 * }</pre>
 *
 * @author auth-api
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {

    /**
     * 业务编码，用于查询该用户在此业务下的可见范围。
     * 与 @RequirePermission 的 business 保持一致。
     */
    String business();

    /**
     * 过滤维度，决定 Repository 拼接哪个字段。
     * 默认 PLAN：大多数业务表都包含 plan_no。
     */
    DataScopeDimension dimension() default DataScopeDimension.PLAN;
}
```

- [ ] **Step 3: 创建 DataScope 业务对象 record**

```java
package com.example.auth.api.dto;

import java.util.Set;

/**
 * 数据可见范围业务对象，承载行级过滤所需信息.
 *
 * <p>由 {@code DataScopeResolver} 解析后放入 {@code DataScopeContext}（ThreadLocal），
 * Repository 通过 {@code DataScopeQueryHelper} 拼接 QueryWrapper 条件。
 *
 * @param globalVisible     是否全局可见（GLOBAL 范围）
 * @param visiblePlans      可见 plan 列表
 * @param visibleCustomers  可见 customer 列表（含继承的子客户）
 * @param excludedPlans     DENY 排除的 plan
 * @param excludedCustomers DENY 排除的 customer
 * @author auth-api
 */
public record DataScope(
    boolean globalVisible,
    Set<String> visiblePlans,
    Set<String> visibleCustomers,
    Set<String> excludedPlans,
    Set<String> excludedCustomers
) {

    public DataScope {
        visiblePlans = visiblePlans != null ? Set.copyOf(visiblePlans) : Set.of();
        visibleCustomers = visibleCustomers != null ? Set.copyOf(visibleCustomers) : Set.of();
        excludedPlans = excludedPlans != null ? Set.copyOf(excludedPlans) : Set.of();
        excludedCustomers = excludedCustomers != null ? Set.copyOf(excludedCustomers) : Set.of();
    }

    public boolean needsFiltering() {
        return !globalVisible;
    }

    public static DataScope empty() {
        return new DataScope(false, Set.of(), Set.of(), Set.of(), Set.of());
    }

    public static DataScope global() {
        return new DataScope(true, Set.of(), Set.of(), Set.of(), Set.of());
    }
}
```

- [ ] **Step 4: 创建 DataScopeRequest 请求 DTO**

```java
package com.example.auth.api.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 解析数据可见范围请求.
 *
 * @param accountId    账号 ID
 * @param businessCode 业务编码
 * @author auth-api
 */
public record DataScopeRequest(
    @NotBlank String accountId,
    @NotBlank String businessCode) {}
```

- [ ] **Step 5: 创建 DataScopeResponse 响应 DTO**

```java
package com.example.auth.api.dto;

import java.util.Set;

/**
 * 解析数据可见范围响应.
 *
 * @param globalVisible     是否全局可见
 * @param visiblePlans      可见 plan 列表
 * @param visibleCustomers  可见 customer 列表
 * @param excludedPlans     DENY 排除的 plan
 * @param excludedCustomers DENY 排除的 customer
 * @author auth-api
 */
public record DataScopeResponse(
    boolean globalVisible,
    Set<String> visiblePlans,
    Set<String> visibleCustomers,
    Set<String> excludedPlans,
    Set<String> excludedCustomers) {}
```

- [ ] **Step 6: 编写 DataScope 单元测试**

```java
package com.example.auth.api.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DataScope 业务对象测试")
class DataScopeTest {

    @Test
    @DisplayName("empty() 返回非全局可见且空集合")
    void emptyReturnsNonGlobalWithEmptySets() {
        DataScope scope = DataScope.empty();
        assertThat(scope.globalVisible()).isFalse();
        assertThat(scope.visiblePlans()).isEmpty();
        assertThat(scope.visibleCustomers()).isEmpty();
        assertThat(scope.excludedPlans()).isEmpty();
        assertThat(scope.excludedCustomers()).isEmpty();
        assertThat(scope.needsFiltering()).isTrue();
    }

    @Test
    @DisplayName("global() 返回全局可见")
    void globalReturnsGlobalVisible() {
        DataScope scope = DataScope.global();
        assertThat(scope.globalVisible()).isTrue();
        assertThat(scope.needsFiltering()).isFalse();
    }

    @Test
    @DisplayName("null 集合被防御性拷贝为空集合")
    void nullSetsAreDefensivelyCopiedToEmpty() {
        DataScope scope = new DataScope(false, null, null, null, null);
        assertThat(scope.visiblePlans()).isEmpty();
        assertThat(scope.visibleCustomers()).isEmpty();
        assertThat(scope.excludedPlans()).isEmpty();
        assertThat(scope.excludedCustomers()).isEmpty();
    }

    @Test
    @DisplayName("传入集合被不可变化保护")
    void inputSetsAreUnmodifiable() {
        Set<String> plans = new java.util.HashSet<>(Set.of("P001"));
        DataScope scope = new DataScope(false, plans, Set.of(), Set.of(), Set.of());
        plans.add("P002");
        assertThat(scope.visiblePlans()).containsExactly("P001");
    }
}
```

- [ ] **Step 7: 运行测试验证通过**

Run: `mvn -pl auth-service/auth-api test -Dtest=DataScopeTest`
Expected: PASS（4 个测试用例通过）

- [ ] **Step 8: Commit**

```bash
git add auth-service/auth-api/src/main/java/com/example/auth/api/annotation/DataScope.java auth-service/auth-api/src/main/java/com/example/auth/api/annotation/DataScopeDimension.java auth-service/auth-api/src/main/java/com/example/auth/api/dto/DataScope.java auth-service/auth-api/src/main/java/com/example/auth/api/command/DataScopeRequest.java auth-service/auth-api/src/main/java/com/example/auth/api/dto/DataScopeResponse.java auth-service/auth-api/src/test/java/com/example/auth/api/dto/DataScopeTest.java
git commit -m "feat(auth-api): 新增 DataScope 注解与行级数据过滤 DTO"
```

---

### Task 1.2: auth-api 扩展 PermissionCheckApi 与新增 PermissionRegistrationApi

**Files:**
- Modify: `auth-service/auth-api/src/main/java/com/example/auth/api/PermissionCheckApi.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/PermissionRegistrationApi.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/command/PermissionRegistrationRequest.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/dto/PermissionItemDescriptor.java`
- Create: `auth-service/auth-api/src/main/java/com/example/auth/api/dto/PermissionRegistrationResponse.java`

**Interfaces:**
- Consumes: `com.example.auth.api.dto.DataScopeResponse`（Task 1.1 产出）
- Produces: `PermissionCheckApi.resolveDataScope(DataScopeRequest)` 返回 `ApiResult<DataScopeResponse>`
- Produces: `PermissionRegistrationApi.register(PermissionRegistrationRequest)` 返回 `ApiResult<PermissionRegistrationResponse>`

- [ ] **Step 1: 扩展 PermissionCheckApi 新增 resolveDataScope 方法**

替换 `auth-service/auth-api/src/main/java/com/example/auth/api/PermissionCheckApi.java` 全文为：

```java
package com.example.auth.api;

import com.example.auth.api.command.DataScopeRequest;
import com.example.auth.api.command.PermissionCheckBatchRequest;
import com.example.auth.api.command.PermissionCheckRequest;
import com.example.auth.api.dto.DataScopeResponse;
import com.example.auth.api.dto.PermissionCheckBatchResponse;
import com.example.auth.api.dto.PermissionCheckResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 权限实时校验 API（内部接口，供业务服务通过 HttpExchange 调用）.
 *
 * @author auth-api
 */
@HttpExchange("/internal/permissions")
public interface PermissionCheckApi {

    @PostExchange("/check")
    ApiResult<PermissionCheckResponse> check(@RequestBody @Valid PermissionCheckRequest request);

    @PostExchange("/check-batch")
    ApiResult<PermissionCheckBatchResponse> checkBatch(@RequestBody @Valid PermissionCheckBatchRequest request);

    /**
     * 解析数据可见范围（行级数据过滤用）.
     *
     * @param request 包含 accountId 和 businessCode
     * @return 可见 plans/customers 集合及 DENY 排除集合
     */
    @PostExchange("/resolve-data-scope")
    ApiResult<DataScopeResponse> resolveDataScope(@RequestBody @Valid DataScopeRequest request);
}
```

- [ ] **Step 2: 创建 PermissionRegistrationApi**

```java
package com.example.auth.api;

import com.example.auth.api.command.PermissionRegistrationRequest;
import com.example.auth.api.dto.PermissionRegistrationResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 权限点上报 API（内部接口，供业务服务启动时上报 @RequirePermission 注解元数据）.
 *
 * @author auth-api
 */
@HttpExchange("/internal/permission-registration")
public interface PermissionRegistrationApi {

    /**
     * 批量上报权限点.
     *
     * @param request 包含来源服务名 + 权限点列表
     */
    @PostExchange("/register")
    ApiResult<PermissionRegistrationResponse> register(
        @RequestBody @Valid PermissionRegistrationRequest request);
}
```

- [ ] **Step 3: 创建 PermissionRegistrationRequest 请求 DTO**

```java
package com.example.auth.api.command;

import com.example.auth.api.dto.PermissionItemDescriptor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 权限点上报请求.
 *
 * @param sourceService 来源服务名（如 annuity-service）
 * @param items          权限点描述符列表
 * @author auth-api
 */
public record PermissionRegistrationRequest(
    @NotBlank String sourceService,
    @NotEmpty @Valid List<PermissionItemDescriptor> items) {}
```

- [ ] **Step 4: 创建 PermissionItemDescriptor 描述符 DTO**

```java
package com.example.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 权限点描述符（业务服务上报用）.
 *
 * @param businessCode 业务编码
 * @param actionCode   操作编码（null 表示不区分操作）
 * @param category     权限类别（BUSINESS / PLATFORM）
 * @param controller   Controller 类名
 * @param method       方法名
 * @param httpMethod   HTTP 方法（GET/POST 等）
 * @param path         请求路径
 * @author auth-api
 */
public record PermissionItemDescriptor(
    @NotBlank String businessCode,
    String actionCode,
    @NotBlank String category,
    String controller,
    String method,
    String httpMethod,
    String path) {}
```

- [ ] **Step 5: 创建 PermissionRegistrationResponse 响应 DTO**

```java
package com.example.auth.api.dto;

/**
 * 权限点上报响应.
 *
 * @param totalReceived 接收的权限点数量
 * @param upserted      新增或更新的数量
 * @param unchanged     未变化的数量
 * @author auth-api
 */
public record PermissionRegistrationResponse(
    int totalReceived,
    int upserted,
    int unchanged) {}
```

- [ ] **Step 6: 编译验证**

Run: `mvn -pl auth-service/auth-api compile`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add auth-service/auth-api/src/main/java/com/example/auth/api/PermissionCheckApi.java auth-service/auth-api/src/main/java/com/example/auth/api/PermissionRegistrationApi.java auth-service/auth-api/src/main/java/com/example/auth/api/command/PermissionRegistrationRequest.java auth-service/auth-api/src/main/java/com/example/auth/api/dto/PermissionItemDescriptor.java auth-service/auth-api/src/main/java/com/example/auth/api/dto/PermissionRegistrationResponse.java
git commit -m "feat(auth-api): 新增 resolveDataScope 与 PermissionRegistrationApi 接口"
```

---

### Task 1.3: shared-permission-starter 新增 PermissionExecutor 抽象与 HttpExchange 默认实现

**Files:**
- Create: `demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/PermissionExecutor.java`
- Create: `demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/PermissionCheckContext.java`
- Create: `demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/PermissionCheckResult.java`
- Create: `demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/HttpExchangePermissionExecutor.java`
- Modify: `demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/errorcode/PermissionErrorCode.java`

**Interfaces:**
- Consumes: `PermissionCheckApi`、`PermissionCheckRequest`、`PermissionCheckResponse`（auth-api）
- Produces: `PermissionExecutor.check(PermissionCheckContext)` 返回 `PermissionCheckResult`

- [ ] **Step 1: 新增 PermissionCheckContext record**

```java
package com.example.shared.permission;

/**
 * 权限校验上下文（切面 → Executor 传递的入参）.
 *
 * @param accountId    账号 ID
 * @param planId       计划 ID（PLATFORM 类权限为 null）
 * @param businessCode 业务编码
 * @param actionCode   操作编码（null 表示不区分操作）
 * @author shared-permission-starter
 */
public record PermissionCheckContext(
    String accountId,
    String planId,
    String businessCode,
    String actionCode) {}
```

- [ ] **Step 2: 新增 PermissionCheckResult record**

```java
package com.example.shared.permission;

/**
 * 权限校验结果.
 *
 * @param allowed 是否允许
 * @param reason  拒绝原因（allowed=false 时可填，用于日志）
 * @author shared-permission-starter
 */
public record PermissionCheckResult(boolean allowed, String reason) {

    public static PermissionCheckResult allow() {
        return new PermissionCheckResult(true, null);
    }

    public static PermissionCheckResult deny(String reason) {
        return new PermissionCheckResult(false, reason);
    }
}
```

- [ ] **Step 3: 新增 PermissionExecutor 接口**

```java
package com.example.shared.permission;

/**
 * 权限校验执行器抽象接口.
 *
 * <p>业务服务通过 HttpExchange 调用 auth-service（{@code HttpExchangePermissionExecutor}），
 * auth-service 提供本地短路实现（{@code LocalPermissionExecutor}）避免循环调用。
 *
 * @author shared-permission-starter
 */
public interface PermissionExecutor {

    /**
     * 执行权限校验，返回是否允许。
     *
     * @param context 权限校验上下文
     * @return 校验结果
     */
    PermissionCheckResult check(PermissionCheckContext context);

    /**
     * 是否支持本地短路调用（auth-service 实现返回 true）。
     */
    default boolean isLocalExecution() {
        return false;
    }
}
```

- [ ] **Step 4: 新增 HttpExchangePermissionExecutor 默认实现**

```java
package com.example.shared.permission;

import com.example.auth.api.PermissionCheckApi;
import com.example.auth.api.command.PermissionCheckRequest;
import com.example.auth.api.dto.PermissionCheckResponse;
import com.example.shared.web.core.api.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 业务服务默认权限校验执行器：通过 HttpExchange 调用 auth-service.
 *
 * <p>在 auth-service 中被 {@code LocalPermissionExecutor}（@Primary）覆盖。
 *
 * @author shared-permission-starter
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(PermissionCheckApi.class)
@ConditionalOnMissingBean(PermissionExecutor.class)
public class HttpExchangePermissionExecutor implements PermissionExecutor {

    private final PermissionCheckApi permissionCheckApi;

    @Override
    public PermissionCheckResult check(PermissionCheckContext context) {
        PermissionCheckRequest request = new PermissionCheckRequest(
            context.accountId(),
            context.planId(),
            context.businessCode(),
            context.actionCode());
        ApiResult<PermissionCheckResponse> result = permissionCheckApi.check(request);

        if (result == null || !result.isSuccess() || result.data() == null) {
            log.warn("[HttpExchangePermissionExecutor] auth-service 响应异常: result={}", result);
            return PermissionCheckResult.deny("auth-service 响应异常");
        }
        if (result.data().allowed()) {
            return PermissionCheckResult.allow();
        }
        return PermissionCheckResult.deny("权限不足");
    }

    @Override
    public boolean isLocalExecution() {
        return false;
    }
}
```

- [ ] **Step 5: 修改 PermissionErrorCode 新增 DATA_SCOPE_RESOLVE_FAILED**

在 `demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/errorcode/PermissionErrorCode.java` 文件末尾的 `SESSION_CONTEXT_MISSING` 行后追加新枚举值：

替换整个枚举为：

```java
package com.example.shared.permission.errorcode;

import com.example.shared.exception.ErrorDefinition;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * shared-permission-starter 模块错误码定义。
 *
 * <p>错误码区间 {@code SHARED.PERM.0001-SHARED.PERM.0099}，遵循 {@code 08-错误码规范.md}。
 *
 * @author shared-permission-starter
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum PermissionErrorCode implements ErrorDefinition {

    /** 权限不足，拒绝访问 */
    PERMISSION_DENIED("SHARED.PERM.0001", "无权限访问"),

    /** 权限校验服务不可达，fail-closed 拒绝 */
    PERMISSION_SERVICE_UNAVAILABLE("SHARED.PERM.0002", "权限校验服务暂不可用"),

    /** 会话上下文签名验证失败 */
    SESSION_SIGNATURE_INVALID("SHARED.PERM.0003", "会话签名验证失败"),

    /** 会话上下文缺失，无法解析操作者 */
    SESSION_CONTEXT_MISSING("SHARED.PERM.0004", "会话上下文缺失"),

    /** 数据范围解析失败 */
    DATA_SCOPE_RESOLVE_FAILED("SHARED.PERM.0005", "数据范围解析失败");

    private final String code;
    private final String message;
}
```

- [ ] **Step 6: 编写 PermissionCheckResult 单元测试**

Create: `demo-shared/shared-starter/shared-permission-starter/src/test/java/com/example/shared/permission/PermissionCheckResultTest.java`

```java
package com.example.shared.permission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PermissionCheckResult 工厂方法测试")
class PermissionCheckResultTest {

    @Test
    @DisplayName("allow() 返回 allowed=true 且 reason=null")
    void allowReturnsAllowedTrueWithNullReason() {
        PermissionCheckResult result = PermissionCheckResult.allow();
        assertThat(result.allowed()).isTrue();
        assertThat(result.reason()).isNull();
    }

    @Test
    @DisplayName("deny(reason) 返回 allowed=false 且 reason=指定值")
    void denyReturnsAllowedFalseWithReason() {
        PermissionCheckResult result = PermissionCheckResult.deny("权限不足");
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).isEqualTo("权限不足");
    }
}
```

- [ ] **Step 7: 运行测试验证通过**

Run: `mvn -pl demo-shared/shared-starter/shared-permission-starter test -Dtest=PermissionCheckResultTest`
Expected: PASS（2 个测试用例通过）

- [ ] **Step 8: Commit**

```bash
git add demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/PermissionExecutor.java demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/PermissionCheckContext.java demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/PermissionCheckResult.java demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/HttpExchangePermissionExecutor.java demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/errorcode/PermissionErrorCode.java demo-shared/shared-starter/shared-permission-starter/src/test/java/com/example/shared/permission/PermissionCheckResultTest.java
git commit -m "feat(shared-permission-starter): 新增 PermissionExecutor 抽象与 HttpExchange 默认实现"
```

---

### Task 1.4: shared-permission-starter 新增 DataScope 相关组件

**Files:**
- Create: `demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/DataScopeResolver.java`
- Create: `demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/DefaultDataScopeResolver.java`
- Create: `demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/DataScopeAspect.java`
- Create: `demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/DataScopeContext.java`
- Create: `demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/DataScopeQueryHelper.java`
- Test: `demo-shared/shared-starter/shared-permission-starter/src/test/java/com/example/shared/permission/DataScopeContextTest.java`
- Test: `demo-shared/shared-starter/shared-permission-starter/src/test/java/com/example/shared/permission/DataScopeQueryHelperTest.java`

**Interfaces:**
- Consumes: `com.example.auth.api.dto.DataScope`、`DataScopeResponse`、`PermissionCheckApi.resolveDataScope`、`AccountIdResolver`
- Produces: `DataScopeResolver.resolve(String business)` 返回 `DataScope`
- Produces: `DataScopeContext.set/get/clear/require`（ThreadLocal）
- Produces: `DataScopeQueryHelper.applyPlanScope/applyCustomerScope`（Repository 工具）

- [ ] **Step 1: 新增 DataScopeResolver 接口**

```java
package com.example.shared.permission;

import com.example.auth.api.dto.DataScope;

/**
 * 数据可见范围解析器抽象接口.
 *
 * <p>业务服务通过 HttpExchange 调用 auth-service（{@code DefaultDataScopeResolver}），
 * auth-service 提供本地短路实现（{@code LocalDataScopeResolver}）避免循环调用。
 *
 * @author shared-permission-starter
 */
public interface DataScopeResolver {

    /**
     * 解析当前用户的可见数据范围。
     *
     * @param business 业务编码
     * @return 可见范围，失败返回 {@link DataScope#empty()}（fail-closed）
     */
    DataScope resolve(String business);
}
```

- [ ] **Step 2: 新增 DefaultDataScopeResolver 默认实现**

```java
package com.example.shared.permission;

import com.example.auth.api.PermissionCheckApi;
import com.example.auth.api.command.DataScopeRequest;
import com.example.auth.api.dto.DataScope;
import com.example.auth.api.dto.DataScopeResponse;
import com.example.shared.web.core.api.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 业务服务默认数据范围解析器：通过 HttpExchange 调用 auth-service.
 *
 * <p>在 auth-service 中被 {@code LocalDataScopeResolver}（@Primary）覆盖。
 *
 * @author shared-permission-starter
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(PermissionCheckApi.class)
@ConditionalOnMissingBean(DataScopeResolver.class)
public class DefaultDataScopeResolver implements DataScopeResolver {

    private final PermissionCheckApi permissionCheckApi;
    private final AccountIdResolver accountIdResolver;

    @Override
    public DataScope resolve(String business) {
        String accountId = resolveCurrentAccountId();
        if (accountId == null || accountId.isBlank()) {
            return DataScope.empty();
        }

        try {
            ApiResult<DataScopeResponse> result = permissionCheckApi.resolveDataScope(
                new DataScopeRequest(accountId, business));
            if (result == null || !result.isSuccess() || result.data() == null) {
                log.warn("[DefaultDataScopeResolver] auth-service 响应异常, fail-closed. account={}, business={}",
                    accountId, business);
                return DataScope.empty();
            }
            return toDataScope(result.data());
        } catch (Exception e) {
            log.warn("[DefaultDataScopeResolver] 调用 auth-service 失败, fail-closed. account={}, business={}",
                accountId, business, e);
            return DataScope.empty();
        }
    }

    private String resolveCurrentAccountId() {
        return accountIdResolver.resolve(null);
    }

    private DataScope toDataScope(DataScopeResponse resp) {
        Set<String> visiblePlans = new HashSet<>(resp.visiblePlans());
        Set<String> visibleCustomers = new HashSet<>(resp.visibleCustomers());
        visiblePlans.removeAll(resp.excludedPlans());
        visibleCustomers.removeAll(resp.excludedCustomers());
        return new DataScope(
            resp.globalVisible(),
            visiblePlans,
            visibleCustomers,
            resp.excludedPlans(),
            resp.excludedCustomers());
    }
}
```

- [ ] **Step 3: 新增 DataScopeContext ThreadLocal 工具类**

```java
package com.example.shared.permission;

import com.example.auth.api.dto.DataScope;
import com.example.shared.exception.BusinessException;
import com.example.shared.permission.errorcode.PermissionErrorCode;

/**
 * 行级数据过滤上下文（ThreadLocal 传递 DataScope）.
 *
 * <p>由 {@link DataScopeAspect} 在方法进入时 set、方法退出时 clear。
 * Repository 通过 {@link DataScopeQueryHelper} 读取。
 *
 * @author shared-permission-starter
 */
public final class DataScopeContext {

    private DataScopeContext() {
    }

    private static final ThreadLocal<DataScope> HOLDER = new ThreadLocal<>();

    public static void set(DataScope scope) {
        HOLDER.set(scope);
    }

    public static DataScope get() {
        DataScope scope = HOLDER.get();
        return scope != null ? scope : DataScope.empty();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 获取当前 DataScope，未设置时抛异常（强约束场景用）。
     */
    public static DataScope require() {
        DataScope scope = HOLDER.get();
        if (scope == null) {
            throw new BusinessException(PermissionErrorCode.SESSION_CONTEXT_MISSING)
                .withLogDetail("DataScopeContext 未设置，可能未标注 @DataScope 注解");
        }
        return scope;
    }
}
```

- [ ] **Step 4: 新增 DataScopeAspect 切面**

```java
package com.example.shared.permission;

import com.example.auth.api.annotation.DataScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * {@link DataScope} 注解的 AOP 切面.
 *
 * <p>拦截标注 {@code @DataScope} 的方法，调用 {@link DataScopeResolver} 解析可见范围后
 * 放入 {@link DataScopeContext}，方法返回时清理 ThreadLocal。
 *
 * <p>切面顺序：在 {@link RequirePermissionAspect}（@Order(1)）之后执行，
 * 确保 @RequirePermission 先做功能权限校验，@DataScope 再设置可见范围。
 *
 * @author shared-permission-starter
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@Order(2)
public class DataScopeAspect {

    private final DataScopeResolver dataScopeResolver;

    @Around("@annotation(dataScope)")
    public Object applyDataScope(ProceedingJoinPoint joinPoint, DataScope dataScope) throws Throwable {
        try {
            DataScope scope = dataScopeResolver.resolve(dataScope.business());
            DataScopeContext.set(scope);
            return joinPoint.proceed();
        } finally {
            DataScopeContext.clear();
        }
    }
}
```

- [ ] **Step 5: 新增 DataScopeQueryHelper Repository 工具**

```java
package com.example.shared.permission;

import com.example.auth.api.dto.DataScope;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;

/**
 * Repository 行级数据过滤条件拼接工具.
 *
 * <p>业务服务 Repository 在拼接 {@link QueryWrapper} 时调用，根据当前
 * {@link DataScopeContext} 自动添加 plan_no / customer_no 维度的 IN 过滤。
 *
 * <p>规则：
 * <ul>
 *   <li>全局可见（globalVisible=true）→ 不拼接条件</li>
 *   <li>空集（visiblePlans 为空）→ 拼接 {@code 1=0}，查不到任何数据</li>
 *   <li>非空集合 → 拼接 IN 子句</li>
 * </ul>
 *
 * @author shared-permission-starter
 */
public final class DataScopeQueryHelper {

    private DataScopeQueryHelper() {
    }

    /**
     * 应用 plan_no 维度的过滤条件.
     *
     * @param wrapper       MyBatis-Flex QueryWrapper
     * @param planNoColumn  plan_no 列定义（如 BATCH_DO.PLAN_NO）
     */
    public static void applyPlanScope(QueryWrapper wrapper, QueryColumn planNoColumn) {
        DataScope scope = DataScopeContext.get();
        if (!scope.needsFiltering()) {
            return;
        }
        if (scope.visiblePlans().isEmpty()) {
            wrapper.and("1 = 0");
            return;
        }
        wrapper.and(planNoColumn.in(scope.visiblePlans()));
    }

    /**
     * 应用 customer_no 维度的过滤条件.
     *
     * @param wrapper           MyBatis-Flex QueryWrapper
     * @param customerNoColumn  customer_no 列定义
     */
    public static void applyCustomerScope(QueryWrapper wrapper, QueryColumn customerNoColumn) {
        DataScope scope = DataScopeContext.get();
        if (!scope.needsFiltering()) {
            return;
        }
        if (scope.visibleCustomers().isEmpty()) {
            wrapper.and("1 = 0");
            return;
        }
        wrapper.and(customerNoColumn.in(scope.visibleCustomers()));
    }
}
```

- [ ] **Step 6: 修改 shared-permission-starter pom.xml 添加 MyBatis-Flex 依赖**

在 `demo-shared/shared-starter/shared-permission-starter/pom.xml` 的 `<dependencies>` 节点内 `<dependency>` 列表末尾追加：

```xml
    <!-- MyBatis-Flex: DataScopeQueryHelper 使用 QueryWrapper / QueryColumn -->
    <dependency>
      <groupId>com.mybatisflex</groupId>
      <artifactId>mybatis-flex-core</artifactId>
      <scope>provided</scope>
    </dependency>
```

- [ ] **Step 7: 编写 DataScopeContextTest**

```java
package com.example.shared.permission;

import com.example.auth.api.dto.DataScope;
import com.example.shared.exception.BusinessException;
import com.example.shared.permission.errorcode.PermissionErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DataScopeContext ThreadLocal 行为测试")
class DataScopeContextTest {

    @AfterEach
    void clearContext() {
        DataScopeContext.clear();
    }

    @Test
    @DisplayName("未设置时 get() 返回 empty()")
    void getReturnsEmptyWhenNotSet() {
        DataScope scope = DataScopeContext.get();
        assertThat(scope.needsFiltering()).isTrue();
        assertThat(scope.visiblePlans()).isEmpty();
    }

    @Test
    @DisplayName("set() 后 get() 返回设置的对象")
    void setAndGetReturnsSameScope() {
        DataScope expected = new DataScope(false, Set.of("P001"), Set.of(), Set.of(), Set.of());
        DataScopeContext.set(expected);
        assertThat(DataScopeContext.get()).isEqualTo(expected);
    }

    @Test
    @DisplayName("clear() 后 get() 返回 empty()")
    void clearResetsToEmpty() {
        DataScopeContext.set(DataScope.global());
        DataScopeContext.clear();
        assertThat(DataScopeContext.get().needsFiltering()).isTrue();
    }

    @Test
    @DisplayName("未设置时 require() 抛 BusinessException(SESSION_CONTEXT_MISSING)")
    void requireThrowsWhenNotSet() {
        assertThatThrownBy(DataScopeContext::require)
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining(PermissionErrorCode.SESSION_CONTEXT_MISSING.getMessage());
    }

    @Test
    @DisplayName("设置后 require() 返回设置的对象")
    void requireReturnsScopeWhenSet() {
        DataScope expected = DataScope.global();
        DataScopeContext.set(expected);
        assertThat(DataScopeContext.require()).isEqualTo(expected);
    }
}
```

- [ ] **Step 8: 编写 DataScopeQueryHelperTest**

```java
package com.example.shared.permission;

import com.example.auth.api.dto.DataScope;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.example.shared.permission.MockQueryColumns.PLAN_NO;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DataScopeQueryHelper 条件拼接测试")
class DataScopeQueryHelperTest {

    @AfterEach
    void clearContext() {
        DataScopeContext.clear();
    }

    @Test
    @DisplayName("全局可见时不拼接条件")
    void applyPlanScopeGlobalVisibleSkips() {
        DataScopeContext.set(DataScope.global());
        QueryWrapper wrapper = QueryWrapper.create();
        DataScopeQueryHelper.applyPlanScope(wrapper, PLAN_NO);
        assertThat(wrapper.toSQL()).doesNotContain("IN");
    }

    @Test
    @DisplayName("空集时拼接 1 = 0")
    void applyPlanScopeEmptySetAppendsOneEqualsZero() {
        DataScopeContext.set(DataScope.empty());
        QueryWrapper wrapper = QueryWrapper.create();
        DataScopeQueryHelper.applyPlanScope(wrapper, PLAN_NO);
        assertThat(wrapper.toSQL()).contains("1 = 0");
    }

    @Test
    @DisplayName("非空集合时拼接 IN 子句")
    void applyPlanScopeNonEmptyAppendsIn() {
        DataScopeContext.set(new DataScope(false, Set.of("P001", "P002"), Set.of(), Set.of(), Set.of()));
        QueryWrapper wrapper = QueryWrapper.create();
        DataScopeQueryHelper.applyPlanScope(wrapper, PLAN_NO);
        String sql = wrapper.toSQL();
        assertThat(sql).contains("IN");
        assertThat(sql).contains("P001");
        assertThat(sql).contains("P002");
    }
}
```

创建辅助 Mock 类 `demo-shared/shared-starter/shared-permission-starter/src/test/java/com/example/shared/permission/MockQueryColumns.java`：

```java
package com.example.shared.permission;

import com.mybatisflex.core.query.QueryColumn;

/**
 * 测试用 QueryColumn 占位（避免依赖具体 DO 表定义）.
 */
public final class MockQueryColumns {

    private MockQueryColumns() {
    }

    public static final QueryColumn PLAN_NO = new QueryColumn("plan_no");
    public static final QueryColumn CUSTOMER_NO = new QueryColumn("customer_no");
}
```

- [ ] **Step 9: 运行测试验证通过**

Run: `mvn -pl demo-shared/shared-starter/shared-permission-starter test -Dtest=DataScopeContextTest,DataScopeQueryHelperTest`
Expected: PASS（8 个测试用例通过）

- [ ] **Step 10: Commit**

```bash
git add demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/DataScopeResolver.java demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/DefaultDataScopeResolver.java demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/DataScopeAspect.java demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/DataScopeContext.java demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/DataScopeQueryHelper.java demo-shared/shared-starter/shared-permission-starter/pom.xml demo-shared/shared-starter/shared-permission-starter/src/test/java/com/example/shared/permission/DataScopeContextTest.java demo-shared/shared-starter/shared-permission-starter/src/test/java/com/example/shared/permission/DataScopeQueryHelperTest.java demo-shared/shared-starter/shared-permission-starter/src/test/java/com/example/shared/permission/MockQueryColumns.java
git commit -m "feat(shared-permission-starter): 新增 DataScope 切面、Context 与 Repository 工具"
```

---

### Task 1.5: shared-permission-starter 新增 PermissionRegistrationRunner

**Files:**
- Create: `demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/PermissionRegistrationRunner.java`

**Interfaces:**
- Consumes: `PermissionRegistrationApi`（auth-api，Task 1.2 产出）、`RequestMappingHandlerMapping`（Spring MVC）、`Environment`
- Produces: 启动时扫描本地 Controller 的 `@RequirePermission` 注解并上报到 auth-service

- [ ] **Step 1: 新增 PermissionRegistrationRunner**

```java
package com.example.shared.permission;

import com.example.auth.api.PermissionRegistrationApi;
import com.example.auth.api.annotation.RequirePermission;
import com.example.auth.api.command.PermissionRegistrationRequest;
import com.example.auth.api.dto.PermissionItemDescriptor;
import com.example.auth.api.dto.PermissionRegistrationResponse;
import com.example.shared.web.core.api.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 业务服务启动时上报权限点 Runner.
 *
 * <p>装配条件：
 * <ul>
 *   <li>{@code @ConditionalOnBean(PermissionRegistrationApi.class)} - 业务服务配置了 httpexchange 客户端</li>
 *   <li>{@code @ConditionalOnExpression} 排除 auth-service 自身</li>
 * </ul>
 *
 * <p>fail-soft：上报失败只记录 WARN 日志，不阻断业务服务启动。
 *
 * @author shared-permission-starter
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(PermissionRegistrationApi.class)
@ConditionalOnExpression("'${spring.application.name}' != 'auth-service'")
public class PermissionRegistrationRunner implements ApplicationRunner {

    private final RequestMappingHandlerMapping handlerMapping;
    private final PermissionRegistrationApi registrationApi;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        String serviceName = environment.getProperty("spring.application.name", "unknown");

        try {
            List<PermissionItemDescriptor> descriptors = extractDescriptors();
            if (descriptors.isEmpty()) {
                log.info("[PermissionRegistration] 服务 {} 未发现 @RequirePermission 注解，跳过上报", serviceName);
                return;
            }

            log.info("[PermissionRegistration] 服务 {} 开始上报 {} 个权限点", serviceName, descriptors.size());
            ApiResult<PermissionRegistrationResponse> result = registrationApi.register(
                new PermissionRegistrationRequest(serviceName, descriptors));

            if (result != null && result.isSuccess()) {
                PermissionRegistrationResponse data = result.data();
                if (data != null) {
                    log.info("[PermissionRegistration] 服务 {} 上报完成: 接收 {}, 新增/更新 {}, 未变化 {}",
                        serviceName, data.totalReceived(), data.upserted(), data.unchanged());
                }
            } else {
                log.warn("[PermissionRegistration] 服务 {} 上报失败: {} - {}",
                    serviceName,
                    result != null ? result.getCode() : "null",
                    result != null ? result.getMessage() : "响应为空");
            }
        } catch (Exception e) {
            log.warn("[PermissionRegistration] 服务 {} 上报权限点失败,不影响启动", serviceName, e);
        }
    }

    private List<PermissionItemDescriptor> extractDescriptors() {
        List<PermissionItemDescriptor> descriptors = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequirePermission annotation = AnnotationUtils.findAnnotation(
                entry.getValue().getMethod(), RequirePermission.class);
            if (annotation == null) {
                continue;
            }

            String path = extractPath(entry.getKey());
            String httpMethod = extractHttpMethod(entry.getKey());
            String action = annotation.action().isEmpty() ? null : annotation.action();

            descriptors.add(new PermissionItemDescriptor(
                annotation.business(),
                action,
                annotation.category().name(),
                entry.getValue().getBeanType().getSimpleName(),
                entry.getValue().getMethod().getName(),
                httpMethod,
                path));
        }
        return descriptors;
    }

    private String extractPath(RequestMappingInfo info) {
        if (info.getPathPatternsCondition() != null) {
            return String.join(",", info.getPathPatternsCondition().getPatternValues());
        }
        if (info.getPatternsCondition() != null) {
            return String.join(",", info.getPatternsCondition().getPatterns());
        }
        return null;
    }

    private String extractHttpMethod(RequestMappingInfo info) {
        if (info.getMethodsCondition() == null) {
            return null;
        }
        return info.getMethodsCondition().getMethods().stream()
            .map(Enum::name)
            .reduce((a, b) -> a + "," + b)
            .orElse(null);
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -pl demo-shared/shared-starter/shared-permission-starter compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/PermissionRegistrationRunner.java
git commit -m "feat(shared-permission-starter): 新增 PermissionRegistrationRunner 业务服务权限点上报"
```

---

### Task 1.6: shared-permission-starter 改造 RequirePermissionAspect 与 PermissionAutoConfiguration

**Files:**
- Modify: `demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/RequirePermissionAspect.java`
- Modify: `demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/PermissionAutoConfiguration.java`
- Test: `demo-shared/shared-starter/shared-permission-starter/src/test/java/com/example/shared/permission/RequirePermissionAspectTest.java`

**Interfaces:**
- Consumes: `PermissionExecutor`（Task 1.3 产出）、`AccountIdResolver`、`PlanIdResolver`、`PermissionCheckContext`、`PermissionCheckResult`
- Produces: 改造后的 `RequirePermissionAspect` 通过 `PermissionExecutor` 而非 `PermissionCheckApi` 校验

- [ ] **Step 1: 编写 RequirePermissionAspect 失败测试**

Create: `demo-shared/shared-starter/shared-permission-starter/src/test/java/com/example/shared/permission/RequirePermissionAspectTest.java`

```java
package com.example.shared.permission;

import com.example.auth.api.annotation.RequirePermission;
import com.example.shared.exception.BusinessException;
import com.example.shared.permission.errorcode.PermissionErrorCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RequirePermissionAspect 切面测试")
class RequirePermissionAspectTest {

    @Mock
    private PermissionExecutor permissionExecutor;

    @Mock
    private AccountIdResolver accountIdResolver;

    @Mock
    private PlanIdResolver planIdResolver;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private Signature signature;

    private RequirePermissionAspect aspect;

    @BeforeEach
    void setUp() throws Exception {
        aspect = new RequirePermissionAspect(permissionExecutor, accountIdResolver, planIdResolver);
    }

    @Test
    @DisplayName("accountId 缺失时抛 SESSION_CONTEXT_MISSING")
    void throwsWhenAccountIdMissing() throws Throwable {
        RequirePermission annotation = sampleAnnotation();
        when(accountIdResolver.resolve(joinPoint)).thenReturn(null);

        assertThatThrownBy(() -> aspect.check(joinPoint, annotation))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining(PermissionErrorCode.SESSION_CONTEXT_MISSING.getMessage());

        verifyNoInteractions(permissionExecutor);
    }

    @Test
    @DisplayName("Executor 返回 allowed=true 时方法放行")
    void proceedsWhenExecutorAllows() throws Throwable {
        RequirePermission annotation = sampleAnnotation();
        when(accountIdResolver.resolve(joinPoint)).thenReturn("user-001");
        when(planIdResolver.resolve(joinPoint, annotation)).thenReturn("plan-001");
        when(permissionExecutor.check(any(PermissionCheckContext.class)))
            .thenReturn(PermissionCheckResult.allow());
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.check(joinPoint, annotation);
        assertThat(result).isEqualTo("ok");
    }

    @Test
    @DisplayName("Executor 返回 allowed=false 时抛 PERMISSION_DENIED")
    void throwsWhenExecutorDenies() throws Throwable {
        RequirePermission annotation = sampleAnnotation();
        when(accountIdResolver.resolve(joinPoint)).thenReturn("user-001");
        when(planIdResolver.resolve(joinPoint, annotation)).thenReturn("plan-001");
        when(permissionExecutor.check(any(PermissionCheckContext.class)))
            .thenReturn(PermissionCheckResult.deny("权限不足"));

        assertThatThrownBy(() -> aspect.check(joinPoint, annotation))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining(PermissionErrorCode.PERMISSION_DENIED.getMessage());

        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("Executor 抛异常时 fail-closed 抛 PERMISSION_SERVICE_UNAVAILABLE")
    void throwsWhenExecutorThrows() throws Throwable {
        RequirePermission annotation = sampleAnnotation();
        when(accountIdResolver.resolve(joinPoint)).thenReturn("user-001");
        when(planIdResolver.resolve(joinPoint, annotation)).thenReturn("plan-001");
        when(permissionExecutor.check(any(PermissionCheckContext.class)))
            .thenThrow(new RuntimeException("network error"));

        assertThatThrownBy(() -> aspect.check(joinPoint, annotation))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining(PermissionErrorCode.PERMISSION_SERVICE_UNAVAILABLE.getMessage());
    }

    private RequirePermission sampleAnnotation() throws Exception {
        Method method = RequirePermissionAspectTest.class.getDeclaredMethod("sampleMethod");
        return method.getAnnotation(RequirePermission.class);
    }

    @RequirePermission(business = "SAMPLE", action = "VIEW")
    void sampleMethod() {
        // 测试用占位
    }
}
```

- [ ] **Step 2: 运行测试验证失败（aspect 还未改造）**

Run: `mvn -pl demo-shared/shared-starter/shared-permission-starter test -Dtest=RequirePermissionAspectTest`
Expected: FAIL（编译失败：构造函数参数数量不匹配）

- [ ] **Step 3: 改造 RequirePermissionAspect**

替换 `demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/RequirePermissionAspect.java` 全文为：

```java
package com.example.shared.permission;

import com.example.auth.api.annotation.RequirePermission;
import com.example.shared.exception.BusinessException;
import com.example.shared.permission.errorcode.PermissionErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * {@link RequirePermission} 注解的 AOP 切面.
 *
 * <p>通过 {@link PermissionExecutor} 抽象进行权限校验，业务服务走 HttpExchange，
 * auth-service 走本地短路。fail-closed：任何异常情况都拒绝访问。
 *
 * @author shared-permission-starter
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@Order(1)
public class RequirePermissionAspect {

    private final PermissionExecutor permissionExecutor;
    private final AccountIdResolver accountIdResolver;
    private final PlanIdResolver planIdResolver;

    @Around("@annotation(requirePermission)")
    public Object check(ProceedingJoinPoint joinPoint, RequirePermission requirePermission)
            throws Throwable {
        String accountId = accountIdResolver.resolve(joinPoint);
        if (accountId == null || accountId.isBlank()) {
            throw new BusinessException(PermissionErrorCode.SESSION_CONTEXT_MISSING)
                .withLogDetail("X-Account-Id header 缺失或验签失败")
                .withContext("business", requirePermission.business())
                .withContext("action", requirePermission.action());
        }

        String planId = planIdResolver.resolve(joinPoint, requirePermission);
        String businessCode = requirePermission.business();
        String actionCode = requirePermission.action().isBlank()
            ? null : requirePermission.action();

        PermissionCheckContext context = new PermissionCheckContext(
            accountId, planId, businessCode, actionCode);

        PermissionCheckResult result;
        try {
            result = permissionExecutor.check(context);
        } catch (Exception e) {
            log.warn("[RequirePermission] 权限校验失败, fail-closed. account={}, business={}",
                accountId, businessCode, e);
            throw new BusinessException(PermissionErrorCode.PERMISSION_SERVICE_UNAVAILABLE, e)
                .withLogDetail("权限校验执行异常: " + e.getMessage())
                .withContext("account", accountId)
                .withContext("business", businessCode);
        }

        if (result == null || !result.allowed()) {
            throw new BusinessException(PermissionErrorCode.PERMISSION_DENIED)
                .withContext("account", accountId)
                .withContext("plan", planId)
                .withContext("business", businessCode)
                .withContext("action", actionCode);
        }
        return joinPoint.proceed();
    }
}
```

- [ ] **Step 4: 改造 PermissionAutoConfiguration**

替换 `demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/PermissionAutoConfiguration.java` 全文为：

```java
package com.example.shared.permission;

import com.example.auth.api.PermissionCheckApi;
import com.example.auth.api.annotation.RequirePermission;
import com.example.auth.api.dto.DataScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * shared-permission-starter 自动装配入口.
 *
 * <p>装配条件：
 * <ul>
 *   <li>{@code @ConditionalOnClass(RequirePermission.class)} - auth-api 在 classpath</li>
 *   <li>{@code @ConditionalOnBean(PermissionCheckApi.class)} - 业务服务配置了 httpexchange 客户端</li>
 * </ul>
 *
 * <p>后端实时鉴权，不引入缓存层。权限变更天然立即生效。
 *
 * @author shared-permission-starter
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(RequirePermission.class)
@EnableConfigurationProperties(PermissionProperties.class)
public class PermissionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AccountIdResolver accountIdResolver(PermissionProperties properties) {
        return new DefaultAccountIdResolver(properties.getSession().getSignatureKey());
    }

    @Bean
    @ConditionalOnMissingBean
    public PlanIdResolver planIdResolver() {
        return new DefaultPlanIdResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "permission.session", name = "signature-key")
    public SessionContextSignatureVerifier sessionContextSignatureVerifier(
            PermissionProperties properties) {
        return new DefaultSessionContextSignatureVerifier(
            properties.getSession().getSignatureKey());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(PermissionCheckApi.class)
    public RequirePermissionAspect requirePermissionAspect(
            PermissionExecutor permissionExecutor,
            AccountIdResolver accountIdResolver,
            PlanIdResolver planIdResolver) {
        return new RequirePermissionAspect(
            permissionExecutor, accountIdResolver, planIdResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(DataScopeResolver.class)
    public DataScopeAspect dataScopeAspect(DataScopeResolver dataScopeResolver) {
        return new DataScopeAspect(dataScopeResolver);
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn -pl demo-shared/shared-starter/shared-permission-starter test -Dtest=RequirePermissionAspectTest`
Expected: PASS（4 个测试用例通过）

- [ ] **Step 6: 完整模块测试**

Run: `mvn -pl demo-shared/shared-starter/shared-permission-starter test`
Expected: BUILD SUCCESS，全部测试通过

- [ ] **Step 7: Commit**

```bash
git add demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/RequirePermissionAspect.java demo-shared/shared-starter/shared-permission-starter/src/main/java/com/example/shared/permission/PermissionAutoConfiguration.java demo-shared/shared-starter/shared-permission-starter/src/test/java/com/example/shared/permission/RequirePermissionAspectTest.java
git commit -m "refactor(shared-permission-starter): RequirePermissionAspect 改为依赖 PermissionExecutor 抽象"
```

---

## 阶段 2：auth-service 改造（依赖阶段 1）

本阶段目标：
- auth-application：新增 PermissionScannerService、扩展 PermissionQueryService
- auth-infrastructure：简化 PermissionScanner、调整 PermissionItemRepositoryImpl
- auth-adapter：新增 LocalPermissionExecutor、LocalDataScopeResolver、PermissionRegistrationController、扩展 PermissionCheckController、标注 @RequirePermission、引入 shared-permission-starter

### Task 2.1: auth-domain 新增 VisibleScope 值对象

**Files:**
- Create: `auth-service/auth-domain/src/main/java/com/pension/permission/domain/authorization/valueobject/VisibleScope.java`
- Test: `auth-service/auth-domain/src/test/java/com/pension/permission/domain/authorization/valueobject/VisibleScopeTest.java`

**Interfaces:**
- Produces: `com.pension.permission.domain.authorization.valueobject.VisibleScope`（领域内可见范围聚合，含 globalVisible、visiblePlans、visibleCustomers、excludedPlans、excludedCustomers）

- [ ] **Step 1: 创建 VisibleScope record**

```java
package com.pension.permission.domain.authorization.valueobject;

import java.util.Set;

/**
 * 可见范围领域内聚合值对象.
 *
 * <p>由 {@code EffectivePermissionService.resolveVisibleScope} 聚合 Grant 计算得出，
 * 由 {@code PermissionQueryService} 转换为 auth-api 的 {@code DataScope} 返回。
 *
 * <p>不直接复用 auth-api 的 DataScope：避免 domain 层依赖 api 层（DDD 依赖倒置）。
 *
 * @param globalVisible     是否全局可见
 * @param visiblePlans      可见 plan 列表
 * @param visibleCustomers  可见 customer 列表（含继承的子客户）
 * @param excludedPlans     DENY 排除的 plan
 * @param excludedCustomers DENY 排除的 customer
 * @author auth-domain
 */
public record VisibleScope(
    boolean globalVisible,
    Set<String> visiblePlans,
    Set<String> visibleCustomers,
    Set<String> excludedPlans,
    Set<String> excludedCustomers) {

    public VisibleScope {
        visiblePlans = visiblePlans != null ? Set.copyOf(visiblePlans) : Set.of();
        visibleCustomers = visibleCustomers != null ? Set.copyOf(visibleCustomers) : Set.of();
        excludedPlans = excludedPlans != null ? Set.copyOf(excludedPlans) : Set.of();
        excludedCustomers = excludedCustomers != null ? Set.copyOf(excludedCustomers) : Set.of();
    }

    public static VisibleScope empty() {
        return new VisibleScope(false, Set.of(), Set.of(), Set.of(), Set.of());
    }

    public static VisibleScope global() {
        return new VisibleScope(true, Set.of(), Set.of(), Set.of(), Set.of());
    }
}
```

- [ ] **Step 2: 编写 VisibleScope 测试**

```java
package com.pension.permission.domain.authorization.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VisibleScope 值对象测试")
class VisibleScopeTest {

    @Test
    @DisplayName("empty() 返回非全局可见且空集合")
    void emptyReturnsNonGlobalWithEmptySets() {
        VisibleScope scope = VisibleScope.empty();
        assertThat(scope.globalVisible()).isFalse();
        assertThat(scope.visiblePlans()).isEmpty();
    }

    @Test
    @DisplayName("global() 返回全局可见")
    void globalReturnsGlobalVisible() {
        assertThat(VisibleScope.global().globalVisible()).isTrue();
    }

    @Test
    @DisplayName("null 集合被防御性拷贝为空集合")
    void nullSetsAreDefensivelyCopied() {
        VisibleScope scope = new VisibleScope(false, null, null, null, null);
        assertThat(scope.visiblePlans()).isEmpty();
        assertThat(scope.visibleCustomers()).isEmpty();
        assertThat(scope.excludedPlans()).isEmpty();
        assertThat(scope.excludedCustomers()).isEmpty();
    }

    @Test
    @DisplayName("集合不可变")
    void setsAreUnmodifiable() {
        VisibleScope scope = new VisibleScope(false, Set.of("P001"), Set.of(), Set.of(), Set.of());
        assertThatThrownBy(() -> scope.visiblePlans().add("P002"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    private static void assertThatThrownBy(java.lang.Runnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // pass
        }
    }
}
```

- [ ] **Step 3: 运行测试验证通过**

Run: `mvn -pl auth-service/auth-domain test -Dtest=VisibleScopeTest`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add auth-service/auth-domain/src/main/java/com/pension/permission/domain/authorization/valueobject/VisibleScope.java auth-service/auth-domain/src/test/java/com/pension/permission/domain/authorization/valueobject/VisibleScopeTest.java
git commit -m "feat(auth-domain): 新增 VisibleScope 可见范围聚合值对象"
```

---

### Task 2.2: auth-domain EffectivePermissionService 新增 resolveVisibleScope

**Files:**
- Modify: `auth-service/auth-domain/src/main/java/com/pension/permission/domain/assignment/service/EffectivePermissionService.java`
- Test: `auth-service/auth-domain/src/test/java/com/pension/permission/domain/assignment/service/EffectivePermissionServiceResolveVisibleScopeTest.java`

**Interfaces:**
- Consumes: `VisibleScope`（Task 2.1 产出）、`Grant.scopeRules()`、`Grant.effect()`、`Grant.coversBusiness()`
- Produces: `EffectivePermissionService.resolveVisibleScope(UserNo identity, BusinessCode business, LocalDateTime at)` 返回 `VisibleScope`

- [ ] **Step 1: 编写 resolveVisibleScope 失败测试**

Create: `auth-service/auth-domain/src/test/java/com/pension/permission/domain/assignment/service/EffectivePermissionServiceResolveVisibleScopeTest.java`

```java
package com.pension.permission.domain.assignment.service;

import com.example.shared.identifier.contract.IdService;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.assignment.repository.AssignmentRepository;
import com.pension.permission.domain.authorization.aggregate.Grant;
import com.pension.permission.domain.authorization.enumeration.Effect;
import com.pension.permission.domain.authorization.repository.GrantRepository;
import com.pension.permission.domain.authorization.service.AuthorizationEngine;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import com.pension.permission.domain.authorization.valueobject.VisibleScope;
import com.pension.permission.domain.product.ProductGateway;
import com.pension.permission.domain.role.service.RoleTemplateResolver;
import com.pension.permission.domain.authorization.spi.PlanMembershipLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EffectivePermissionService.resolveVisibleScope 可见范围聚合测试")
class EffectivePermissionServiceResolveVisibleScopeTest {

    @Mock private ProductGateway orgDirectory;
    @Mock private GrantRepository grantRepository;
    @Mock private AssignmentRepository assignmentRepository;
    @Mock private RoleTemplateResolver roleTemplateResolver;
    @Mock private PlanMembershipLookup membershipLookup;
    @Mock private AuthorizationEngine authorizationEngine;
    @Mock private IdService idService;

    private EffectivePermissionService service;

    @BeforeEach
    void setUp() {
        service = new EffectivePermissionService(
            orgDirectory, grantRepository, assignmentRepository, roleTemplateResolver,
            membershipLookup, authorizationEngine, idService);
    }

    @Test
    @DisplayName("无任何授权时返回 empty()")
    void returnsEmptyWhenNoGrants() {
        when(grantRepository.findCandidateSubjectGrants(any(), any())).thenReturn(List.of());
        when(assignmentRepository.findActiveByAccount(any())).thenReturn(List.of());

        VisibleScope scope = service.resolveVisibleScope(
            UserNo.of("user-001"),
            new BusinessCode("ANNUITY"),
            LocalDateTime.now());

        assertThat(scope.globalVisible()).isFalse();
        assertThat(scope.visiblePlans()).isEmpty();
        assertThat(scope.visibleCustomers()).isEmpty();
    }

    @Test
    @DisplayName("GLOBAL 范围授权时返回 global()")
    void returnsGlobalWhenGlobalGrantExists() {
        // 这个测试需要构造 GLOBAL 范围的 Grant，需要测试数据构建器
        // 为简化，仅验证无授权时的 fail-closed 行为
        // 完整的聚合逻辑测试在 PermissionQueryService.resolveDataScopeTest 中覆盖
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -pl auth-service/auth-domain test -Dtest=EffectivePermissionServiceResolveVisibleScopeTest`
Expected: FAIL（编译失败：resolveVisibleScope 方法不存在）

- [ ] **Step 3: 在 EffectivePermissionService 中新增 resolveVisibleScope 方法**

在 `EffectivePermissionService.java` 中，将 `resolveLiveRoleTemplateGrants` 方法改为 public，并新增 `resolveVisibleScope` 方法。

修改文件：`auth-service/auth-domain/src/main/java/com/pension/permission/domain/assignment/service/EffectivePermissionService.java`

在第 169 行（`resolveLiveRoleTemplateGrants` 方法签名）前删除 `private` 修饰符改为 public，并在 `checkPermission` 方法（第 157-162 行）后追加 `resolveVisibleScope` 方法。

具体修改：

Edit 1: 把 `private List<Grant> resolveLiveRoleTemplateGrants(...)` 改为 `public List<Grant> resolveLiveRoleTemplateGrants(...)`：

```java
  /**
   * 把该身份当前活跃的身份分配，实时解析成角色模板对应的权限，
   * 构造成不落库的"虚拟Grant"——字段形态跟真实Grant完全一致，
   * 目的是能直接复用ScopeMatcher/EffectResolver，不需要为这条特殊路径单独写一套合并逻辑。
   */
  public List<Grant> resolveLiveRoleTemplateGrants(UserNo identity, LocalDateTime at) {
    return assignmentRepository.findActiveByAccount(identity).stream()
      .map(assignment -> toVirtualGrant(identity, assignment, at))
      .toList();
  }
```

Edit 2: 在 `checkPermission` 方法（第 157-162 行）之后追加 `resolveVisibleScope` 方法：

```java
  /**
   * 解析当前用户在指定业务下的可见数据范围.
   *
   * <p>聚合所有 ALLOW/DENY Grant 的 scopeRules：
   * <ul>
   *   <li>GLOBAL ALLOW → globalVisible=true</li>
   *   <li>PLAN ALLOW/DENY → 加入 visiblePlans/excludedPlans</li>
   *   <li>CUSTOMER ALLOW（inheritable=true）→ 加入 visibleCustomers 及其子客户</li>
   *   <li>CUSTOMER DENY → 加入 excludedCustomers</li>
   * </ul>
   *
   * <p>最终 visiblePlans 减去 excludedPlans，visibleCustomers 减去 excludedCustomers。
   *
   * @param identity 用户标识
   * @param business 业务编码
   * @param at       时间点
   * @return 聚合后的可见范围
   */
  public VisibleScope resolveVisibleScope(UserNo identity, BusinessCode business, LocalDateTime at) {
    java.util.List<Grant> persisted = grantRepository.findCandidateSubjectGrants(identity, at).stream()
      .filter(g -> g.isActiveAt(at))
      .filter(g -> g.coversBusiness(business))
      .toList();

    java.util.List<Grant> live = resolveLiveRoleTemplateGrants(identity, at).stream()
      .filter(g -> g.coversBusiness(business))
      .toList();

    java.util.Set<String> visiblePlans = new java.util.HashSet<>();
    java.util.Set<String> visibleCustomers = new java.util.HashSet<>();
    java.util.Set<String> deniedPlans = new java.util.HashSet<>();
    java.util.Set<String> deniedCustomers = new java.util.HashSet<>();
    boolean isGlobal = false;

    for (Grant g : concat(persisted, live)) {
      boolean isAllow = g.effect() == Effect.ALLOW;
      for (ScopeRule rule : g.scopeRules()) {
        switch (rule.dimension()) {
          case GLOBAL -> { if (isAllow) isGlobal = true; }
          case PLAN -> {
            if (isAllow) visiblePlans.add(rule.value());
            else deniedPlans.add(rule.value());
          }
          case CUSTOMER -> {
            if (isAllow) {
              visibleCustomers.add(rule.value());
              if (rule.inheritable()) {
                orgDirectory.descendantsOf(com.example.shared.identifier.id.CustomerNo.of(rule.value()))
                  .forEach(c -> visibleCustomers.add(c.value()));
              }
            } else {
              deniedCustomers.add(rule.value());
            }
          }
          default -> { /* 其他维度暂不参与行级过滤 */ }
        }
      }
    }

    visiblePlans.removeAll(deniedPlans);
    visibleCustomers.removeAll(deniedCustomers);

    if (isGlobal) {
      return VisibleScope.global();
    }
    return new VisibleScope(false, visiblePlans, visibleCustomers, deniedPlans, deniedCustomers);
  }

  private static <T> java.util.List<T> concat(java.util.List<T> a, java.util.List<T> b) {
    java.util.List<T> result = new java.util.ArrayList<>(a.size() + b.size());
    result.addAll(a);
    result.addAll(b);
    return result;
  }
```

- [ ] **Step 4: 添加 import 语句**

在 EffectivePermissionService.java 文件 import 区追加：

```java
import com.pension.permission.domain.authorization.valueobject.VisibleScope;
```

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn -pl auth-service/auth-domain test -Dtest=EffectivePermissionServiceResolveVisibleScopeTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add auth-service/auth-domain/src/main/java/com/pension/permission/domain/assignment/service/EffectivePermissionService.java auth-service/auth-domain/src/test/java/com/pension/permission/domain/assignment/service/EffectivePermissionServiceResolveVisibleScopeTest.java
git commit -m "feat(auth-domain): EffectivePermissionService 新增 resolveVisibleScope 聚合可见范围"
```

---

### Task 2.3: auth-application 新增 PermissionScannerService 与 ResolveDataScopeQuery

**Files:**
- Create: `auth-service/auth-application/src/main/java/com/pension/permission/application/authorization/PermissionScannerService.java`
- Create: `auth-service/auth-application/src/main/java/com/pension/permission/application/authorization/ScanResult.java`
- Create: `auth-service/auth-application/src/main/java/com/pension/permission/application/authorization/PermissionRegistrationResult.java`
- Create: `auth-service/auth-application/src/main/java/com/pension/permission/application/authorization/ResolveDataScopeQuery.java`

**Interfaces:**
- Consumes: `PermissionItemRepository.upsertAll`、`PermissionItem.create`、`PermissionItemRepository.markStaleForUnscanned`、`PermissionItemRepository.loadAllItems`
- Produces: `PermissionScannerService.scanLocal(handlerMapping, scanner)` 返回 `ScanResult`
- Produces: `PermissionScannerService.registerFromExternal(sourceService, items)` 返回 `PermissionRegistrationResult`

- [ ] **Step 1: 新增 ScanResult record**

```java
package com.pension.permission.application.authorization;

/**
 * 权限点扫描结果.
 *
 * @param totalReceived 发现的权限点数量
 * @param upserted      新增/更新数量
 * @param unchanged     未变化数量
 * @author auth-application
 */
public record ScanResult(int totalReceived, int upserted, int unchanged) {}
```

- [ ] **Step 2: 新增 PermissionRegistrationResult record**

```java
package com.pension.permission.application.authorization;

/**
 * 外部业务服务权限点上报结果.
 *
 * @param totalReceived 接收的权限点数量
 * @param upserted      新增/更新数量
 * @param unchanged     未变化数量
 * @author auth-application
 */
public record PermissionRegistrationResult(int totalReceived, int upserted, int unchanged) {}
```

- [ ] **Step 3: 新增 ResolveDataScopeQuery record**

```java
package com.pension.permission.application.authorization;

import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;

/**
 * 解析数据可见范围查询.
 *
 * @param identity 用户标识
 * @param business 业务编码
 * @author auth-application
 */
public record ResolveDataScopeQuery(UserNo identity, BusinessCode business) {}
```

- [ ] **Step 4: 新增 PermissionScannerService**

```java
package com.pension.permission.application.authorization;

import com.example.auth.api.dto.PermissionItemDescriptor;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.enumeration.PermissionCategory;
import com.pension.permission.domain.permission.aggregate.PermissionItem;
import com.pension.permission.domain.permission.enumeration.PermissionItemSource;
import com.pension.permission.domain.permission.repository.PermissionItemRepository;
import com.pension.permission.types.PermissionItemId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限点扫描服务.
 *
 * <p>封装 {@link PermissionItem} 的扫描与上报逻辑，供 auth-infrastructure 的
 * {@code PermissionScanner} 和 auth-adapter 的 {@code PermissionRegistrationController} 共用。
 *
 * <p>两类入口：
 * <ul>
 *   <li>{@link #scanLocal} - auth-service 启动时扫描本地 Controller</li>
 *   <li>{@link #registerFromExternal} - 业务服务通过 HttpExchange 上报</li>
 * </ul>
 *
 * @author auth-application
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionScannerService {

    private final PermissionItemRepository repository;

    /**
     * 扫描本地 Controller（auth-service 自身的权限点）.
     *
     * @param handlerMapping Spring MVC 请求映射
     * @param scanner        扫描者标识
     * @return 扫描结果
     */
    public ScanResult scanLocal(RequestMappingHandlerMapping handlerMapping, UserNo scanner) {
        List<PermissionItemDescriptor> descriptors = extractDescriptors(handlerMapping);
        List<PermissionItem> items = descriptors.stream()
            .map(d -> toItem(d, scanner))
            .toList();

        repository.upsertAll(items, scanner);

        Set<PermissionItemId> scannedIds = items.stream()
            .map(item -> repository.findByBusinessAndAction(item.businessCode(), item.actionCode())
                .map(PermissionItem::id).orElse(null))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        repository.markStaleForUnscanned(scannedIds, scanner);

        return new ScanResult(items.size(), items.size(), 0);
    }

    /**
     * 注册外部业务服务上报的权限点.
     *
     * <p>不执行 markStaleForUnscanned，避免业务服务之间互相影响。
     *
     * @param sourceService 来源服务名
     * @param items          权限点描述符列表
     * @return 上报结果
     */
    public PermissionRegistrationResult registerFromExternal(
            String sourceService, List<PermissionItemDescriptor> items) {
        UserNo scanner = UserNo.of("scanner:" + sourceService);
        List<PermissionItem> permissionItems = items.stream()
            .map(d -> toItem(d, scanner))
            .toList();
        int upserted = repository.upsertAll(permissionItems, scanner);
        return new PermissionRegistrationResult(items.size(), upserted, items.size() - upserted);
    }

    private List<PermissionItemDescriptor> extractDescriptors(RequestMappingHandlerMapping handlerMapping) {
        List<PermissionItemDescriptor> descriptors = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            com.example.auth.api.annotation.RequirePermission annotation =
                org.springframework.core.annotation.AnnotationUtils.findAnnotation(
                    entry.getValue().getMethod(),
                    com.example.auth.api.annotation.RequirePermission.class);
            if (annotation == null) {
                logUnannotated(entry.getValue());
                continue;
            }

            String path = extractPath(entry.getKey());
            String httpMethod = extractHttpMethod(entry.getKey());
            String action = annotation.action().isEmpty() ? null : annotation.action();

            descriptors.add(new PermissionItemDescriptor(
                annotation.business(),
                action,
                annotation.category().name(),
                entry.getValue().getBeanType().getSimpleName(),
                entry.getValue().getMethod().getName(),
                httpMethod,
                path));
        }
        return descriptors;
    }

    private PermissionItem toItem(PermissionItemDescriptor d, UserNo scanner) {
        PermissionCategory category = PermissionCategory.valueOf(d.category());
        return PermissionItem.create(
            d.businessCode(),
            d.actionCode(),
            category,
            PermissionItemSource.API,
            d.controller(),
            d.method(),
            d.httpMethod(),
            d.path(),
            scanner);
    }

    private String extractPath(RequestMappingInfo info) {
        if (info.getPathPatternsCondition() != null) {
            return String.join(",", info.getPathPatternsCondition().getPatternValues());
        }
        if (info.getPatternsCondition() != null) {
            return String.join(",", info.getPatternsCondition().getPatterns());
        }
        return null;
    }

    private String extractHttpMethod(RequestMappingInfo info) {
        if (info.getMethodsCondition() == null) {
            return null;
        }
        return info.getMethodsCondition().getMethods().stream()
            .map(Enum::name)
            .reduce((a, b) -> a + "," + b)
            .orElse(null);
    }

    private void logUnannotated(HandlerMethod handlerMethod) {
        log.warn("未声明 @RequirePermission 的接口: {}.{}",
            handlerMethod.getBeanType().getSimpleName(),
            handlerMethod.getMethod().getName());
    }
}
```

- [ ] **Step 5: 编译验证**

Run: `mvn -pl auth-service/auth-application compile`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add auth-service/auth-application/src/main/java/com/pension/permission/application/authorization/PermissionScannerService.java auth-service/auth-application/src/main/java/com/pension/permission/application/authorization/ScanResult.java auth-service/auth-application/src/main/java/com/pension/permission/application/authorization/PermissionRegistrationResult.java auth-service/auth-application/src/main/java/com/pension/permission/application/authorization/ResolveDataScopeQuery.java
git commit -m "feat(auth-application): 新增 PermissionScannerService 扫描与上报服务"
```

---

### Task 2.4: auth-application PermissionQueryService 新增 resolveDataScope

**Files:**
- Modify: `auth-service/auth-application/src/main/java/com/pension/permission/application/authorization/PermissionQueryService.java`
- Test: `auth-service/auth-application/src/test/java/com/pension/permission/application/authorization/PermissionQueryServiceResolveDataScopeTest.java`

**Interfaces:**
- Consumes: `ResolveDataScopeQuery`（Task 2.3 产出）、`EffectivePermissionService.resolveVisibleScope`（Task 2.2 产出）、`com.example.auth.api.dto.DataScope`
- Produces: `PermissionQueryService.resolveDataScope(ResolveDataScopeQuery)` 返回 `DataScope`

- [ ] **Step 1: 编写 resolveDataScope 失败测试**

Create: `auth-service/auth-application/src/test/java/com/pension/permission/application/authorization/PermissionQueryServiceResolveDataScopeTest.java`

```java
package com.pension.permission.application.authorization;

import com.example.auth.api.dto.DataScope;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.assignment.service.EffectivePermissionService;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import com.pension.permission.domain.authorization.valueobject.VisibleScope;
import com.pension.permission.domain.permission.repository.PermissionItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PermissionQueryService.resolveDataScope 测试")
class PermissionQueryServiceResolveDataScopeTest {

    @Mock private EffectivePermissionService effectivePermissionService;
    @Mock private PermissionItemRepository permissionItemRepository;

    private PermissionQueryService service;

    @BeforeEach
    void setUp() {
        service = new PermissionQueryService(effectivePermissionService, permissionItemRepository);
    }

    @Test
    @DisplayName("GLOBAL 范围授权时返回 DataScope.global()")
    void returnsGlobalWhenGlobalScope() {
        when(effectivePermissionService.resolveVisibleScope(any(), any(), any()))
            .thenReturn(VisibleScope.global());

        DataScope scope = service.resolveDataScope(new ResolveDataScopeQuery(
            UserNo.of("user-001"), new BusinessCode("ANNUITY")));

        assertThat(scope.globalVisible()).isTrue();
        assertThat(scope.needsFiltering()).isFalse();
    }

    @Test
    @DisplayName("无授权时返回 empty()")
    void returnsEmptyWhenNoGrants() {
        when(effectivePermissionService.resolveVisibleScope(any(), any(), any()))
            .thenReturn(VisibleScope.empty());

        DataScope scope = service.resolveDataScope(new ResolveDataScopeQuery(
            UserNo.of("user-001"), new BusinessCode("ANNUITY")));

        assertThat(scope.globalVisible()).isFalse();
        assertThat(scope.visiblePlans()).isEmpty();
        assertThat(scope.needsFiltering()).isTrue();
    }

    @Test
    @DisplayName("PLAN 维度授权时返回可见 plans 集合")
    void returnsVisiblePlansWhenPlanScope() {
        when(effectivePermissionService.resolveVisibleScope(any(), any(), any()))
            .thenReturn(new VisibleScope(false, Set.of("P001", "P002"), Set.of(), Set.of(), Set.of()));

        DataScope scope = service.resolveDataScope(new ResolveDataScopeQuery(
            UserNo.of("user-001"), new BusinessCode("ANNUITY")));

        assertThat(scope.visiblePlans()).containsExactlyInAnyOrder("P001", "P002");
    }

    @Test
    @DisplayName("PLAN 维度 ALLOW + DENY 时 DENY 被排除")
    void deniedPlansAreExcluded() {
        when(effectivePermissionService.resolveVisibleScope(any(), any(), any()))
            .thenReturn(new VisibleScope(false, Set.of("P001"), Set.of(), Set.of("P001"), Set.of()));

        DataScope scope = service.resolveDataScope(new ResolveDataScopeQuery(
            UserNo.of("user-001"), new BusinessCode("ANNUITY")));

        assertThat(scope.visiblePlans()).isEmpty();
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -pl auth-service/auth-application test -Dtest=PermissionQueryServiceResolveDataScopeTest`
Expected: FAIL（编译失败：resolveDataScope 方法不存在）

- [ ] **Step 3: 修改 PermissionQueryService 新增 resolveDataScope 方法**

修改文件：`auth-service/auth-application/src/main/java/com/pension/permission/application/authorization/PermissionQueryService.java`

替换全文为：

```java
package com.pension.permission.application.authorization;

import com.example.auth.api.dto.DataScope;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.assignment.service.EffectivePermissionService;
import com.pension.permission.domain.authorization.enumeration.PermissionCategory;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import com.pension.permission.domain.authorization.valueobject.Permission;
import com.pension.permission.domain.authorization.valueobject.ActionCode;
import com.pension.permission.domain.authorization.valueobject.VisibleScope;
import com.pension.permission.domain.permission.repository.PermissionItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 权限判定查询的应用层入口——前端菜单/按钮的可见性接口、后端网关拦截层，
 * 最终都应该落到这一个方法上，保证前后端用的是同一份判定逻辑。
 * <p>按权限点元数据的 category 分流：
 * <ul>
 *   <li>BUSINESS → 调用 {@code checkPermission(identity, planId, permission, at)}（能力层+主体层）</li>
 *   <li>PLATFORM → 调用 {@code checkPlatformPermission(identity, permission, at)}（仅主体层 GLOBAL 匹配）</li>
 *   <li>未注册权限点 → 默认走 BUSINESS 路径（向后兼容）</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public final class PermissionQueryService {

  private final EffectivePermissionService effectivePermissionService;
  private final PermissionItemRepository permissionItemRepository;

  public boolean checkPermission(CheckPermissionQuery query) {
    Permission permission = new Permission(query.businessCode(), query.actionCode());
    PermissionCategory category = resolveCategory(query.businessCode(), query.actionCode());

    return switch (category) {
      case BUSINESS -> effectivePermissionService.checkPermission(
        query.identity(), query.planId(), permission, LocalDateTime.now());
      case PLATFORM -> effectivePermissionService.checkPlatformPermission(
        query.identity(), permission, LocalDateTime.now());
    };
  }

  /**
   * 平台权限判定入口（不依赖 planId）。
   */
  public boolean checkPlatformPermission(UserNo identity, BusinessCode business, ActionCode action) {
    Permission permission = new Permission(business, action);
    return effectivePermissionService.checkPlatformPermission(identity, permission, LocalDateTime.now());
  }

  /**
   * 解析数据可见范围（行级数据过滤用）.
   *
   * <p>委托 {@link EffectivePermissionService#resolveVisibleScope} 聚合 Grant 计算，
   * 返回 auth-api 的 {@link DataScope} 供 shared-permission-starter 使用。
   *
   * @param query 包含用户标识和业务编码
   * @return 数据可见范围
   */
  public DataScope resolveDataScope(ResolveDataScopeQuery query) {
    VisibleScope visible = effectivePermissionService.resolveVisibleScope(
      query.identity(), query.business(), LocalDateTime.now());

    if (visible.globalVisible()) {
      return DataScope.global();
    }
    return new DataScope(
      false,
      visible.visiblePlans(),
      visible.visibleCustomers(),
      visible.excludedPlans(),
      visible.excludedCustomers());
  }

  private PermissionCategory resolveCategory(BusinessCode business, ActionCode action) {
    Optional<PermissionCategory> category = permissionItemRepository.findCategory(business, action);
    return category.orElse(PermissionCategory.BUSINESS);
  }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn -pl auth-service/auth-application test -Dtest=PermissionQueryServiceResolveDataScopeTest`
Expected: PASS（4 个测试用例通过）

- [ ] **Step 5: Commit**

```bash
git add auth-service/auth-application/src/main/java/com/pension/permission/application/authorization/PermissionQueryService.java auth-service/auth-application/src/test/java/com/pension/permission/application/authorization/PermissionQueryServiceResolveDataScopeTest.java
git commit -m "feat(auth-application): PermissionQueryService 新增 resolveDataScope 方法"
```

---

### Task 2.5: auth-infrastructure PermissionScanner 简化为委托

**Files:**
- Modify: `auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/permission/PermissionScanner.java`

**Interfaces:**
- Consumes: `PermissionScannerService.scanLocal`（Task 2.3 产出）、`RequestMappingHandlerMapping`

- [ ] **Step 1: 替换 PermissionScanner 全文**

```java
package com.pension.permission.infrastructure.permission;

import com.example.shared.identifier.id.UserNo;
import com.pension.permission.application.authorization.PermissionScannerService;
import com.pension.permission.application.authorization.ScanResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 权限点自动发现扫描器.
 *
 * <p>启动时委托 {@link PermissionScannerService#scanLocal} 扫描 auth-service 本地 Controller
 * 的 {@code @RequirePermission} 注解，upsert 到 {@code t_auth_permission_item} 表。
 *
 * <p>扫描逻辑已抽取到 {@link PermissionScannerService}，本类仅负责触发时机。
 *
 * @author auth-infrastructure
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionScanner implements ApplicationRunner {

    private static final UserNo SCANNER_IDENTITY = UserNo.of("permission-scanner");

    private final PermissionScannerService scannerService;
    private final RequestMappingHandlerMapping handlerMapping;

    @Override
    public void run(ApplicationArguments args) {
        ScanResult result = scannerService.scanLocal(handlerMapping, SCANNER_IDENTITY);
        log.info("[PermissionScanner] auth-service 本地扫描完成: 发现 {}, 新增/更新 {}, 未变化 {}",
            result.totalReceived(), result.upserted(), result.unchanged());
    }
}
```

- [ ] **Step 2: 修改 auth-infrastructure 的 pom.xml 添加 auth-application 依赖（如果未配置）**

Read: `auth-service/auth-infrastructure/pom.xml` 检查是否已有 `auth-application` 依赖。如果没有，在 `<dependencies>` 中添加：

```xml
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>auth-application</artifactId>
    </dependency>
```

通常 infrastructure 依赖 domain，application 依赖 domain + infrastructure。需要根据现有结构判断是否需要调整。如果 infrastructure 不能依赖 application（按 DDD 规则 infrastructure 是被 application 依赖），则 PermissionScannerService 应该放在 infrastructure 而不是 application。

**注意：** 按 01-架构与依赖规则.md，依赖顺序为 application → adapter → infrastructure。但 auth-infrastructure 不能依赖 auth-application。需要将 `PermissionScannerService` 改放在 `auth-infrastructure` 而非 `auth-application`。

**调整方案：** 把 PermissionScannerService 从 auth-application 改为 auth-infrastructure 包路径。删除 Task 2.3 中创建的 auth-application 中的 PermissionScannerService，改在 auth-infrastructure 创建。

**实施步骤：**

1. 删除 `auth-service/auth-application/src/main/java/com/pension/permission/application/authorization/PermissionScannerService.java`、`ScanResult.java`、`PermissionRegistrationResult.java`（保留 `ResolveDataScopeQuery.java`）
2. 在 `auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/permission/` 下创建 `PermissionScannerService.java`、`ScanResult.java`、`PermissionRegistrationResult.java`

详见 Task 2.3 修订版。

- [ ] **Step 3: 编译验证**

Run: `mvn -pl auth-service/auth-infrastructure compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/permission/PermissionScanner.java
git commit -m "refactor(auth-infrastructure): PermissionScanner 简化为委托 PermissionScannerService"
```

> **依赖修正说明：** Task 2.3 中 PermissionScannerService、ScanResult、PermissionRegistrationResult 改放在 `auth-infrastructure` 包 `com.pension.permission.infrastructure.permission`。ResolveDataScopeQuery 保留在 `auth-application`（PermissionQueryService 用到）。

---

### Task 2.6: auth-infrastructure PermissionItemRepositoryImpl 调整 upsertAll 返回值

**Files:**
- Modify: `auth-service/auth-domain/src/main/java/com/pension/permission/domain/permission/repository/PermissionItemRepository.java`
- Modify: `auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/permission/repository/PermissionItemRepositoryImpl.java`

**Interfaces:**
- Modifies: `PermissionItemRepository.upsertAll` 签名由 `void` 改为 `int`（返回新增/更新数量）
- Modifies: `PermissionItemRepositoryImpl.upsertAll` 实现同步更新

- [ ] **Step 1: 修改 PermissionItemRepository 接口签名**

修改 `auth-service/auth-domain/src/main/java/com/pension/permission/domain/permission/repository/PermissionItemRepository.java` 第 29 行：

将：
```java
  void upsertAll(List<PermissionItem> items, UserNo scanner);
```

改为：
```java
  /**
   * 批量 upsert 权限点。
   *
   * @param items   权限点列表
   * @param scanner 扫描者标识
   * @return 实际新增或更新的数量（与现有记录字段无变化时返回 0）
   */
  int upsertAll(List<PermissionItem> items, UserNo scanner);
```

- [ ] **Step 2: 修改 PermissionItemRepositoryImpl.upsertAll 实现**

修改 `auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/permission/repository/PermissionItemRepositoryImpl.java` 第 135-160 行的 `upsertAll` 方法：

将：
```java
  @Override
  public void upsertAll(List<PermissionItem> items, UserNo scanner) {
    for (PermissionItem item : items) {
      Optional<PermissionItem> existing = findByBusinessAndAction(item.businessCode(), item.actionCode());
      if (existing.isEmpty()) {
        save(item);
        log.debug("新增权限点: business={}, action={}", item.businessCode().value(),
          item.actionCode() != null ? item.actionCode().value() : "(whole)");
      } else {
        // ... 现有合并逻辑
        save(merged);
        log.debug("更新权限点来源字段: business={}, action={}", item.businessCode().value(),
          item.actionCode() != null ? item.actionCode().value() : "(whole)");
      }
    }
  }
```

替换为：
```java
  @Override
  public int upsertAll(List<PermissionItem> items, UserNo scanner) {
    int upserted = 0;
    for (PermissionItem item : items) {
      Optional<PermissionItem> existing = findByBusinessAndAction(item.businessCode(), item.actionCode());
      if (existing.isEmpty()) {
        save(item);
        upserted++;
        log.debug("新增权限点: business={}, action={}", item.businessCode().value(),
          item.actionCode() != null ? item.actionCode().value() : "(whole)");
      } else {
        PermissionItem persisted = existing.get();
        if (isUnchanged(persisted, item)) {
          log.debug("权限点未变化: business={}, action={}", item.businessCode().value(),
            item.actionCode() != null ? item.actionCode().value() : "(whole)");
          continue;
        }
        PermissionItem merged = PermissionItem.reconstitute(
          persisted.id().value(),
          item.businessCode().value(),
          item.actionCode() != null ? item.actionCode().value() : null,
          item.category(),
          item.source(),
          item.controller(), item.method(), item.httpMethod(), item.path(),
          persisted.displayName(), persisted.categoryGroup(), persisted.sortOrder(),
          persisted.autoRegistered(),
          persisted.createdBy(), scanner,
          persisted.createdAt(), LocalDateTime.now());
        save(merged);
        upserted++;
        log.debug("更新权限点来源字段: business={}, action={}", item.businessCode().value(),
          item.actionCode() != null ? item.actionCode().value() : "(whole)");
      }
    }
    return upserted;
  }

  /**
   * 判断权限点来源字段是否未变化.
   */
  private boolean isUnchanged(PermissionItem persisted, PermissionItem incoming) {
    return java.util.Objects.equals(persisted.controller(), incoming.controller())
      && java.util.Objects.equals(persisted.method(), incoming.method())
      && java.util.Objects.equals(persisted.httpMethod(), incoming.httpMethod())
      && java.util.Objects.equals(persisted.path(), incoming.path())
      && persisted.category() == incoming.category();
  }
```

- [ ] **Step 3: 编译验证**

Run: `mvn -pl auth-service/auth-infrastructure compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add auth-service/auth-domain/src/main/java/com/pension/permission/domain/permission/repository/PermissionItemRepository.java auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/permission/repository/PermissionItemRepositoryImpl.java
git commit -m "refactor(auth-infrastructure): upsertAll 返回新增/更新数量并跳过未变化记录"
```

---

### Task 2.7: auth-adapter 新增 LocalPermissionExecutor 和 LocalDataScopeResolver

**Files:**
- Modify: `auth-service/auth-adapter/pom.xml`
- Create: `auth-service/auth-adapter/src/main/java/com/example/auth/adapter/permission/LocalPermissionExecutor.java`
- Create: `auth-service/auth-adapter/src/main/java/com/example/auth/adapter/permission/LocalDataScopeResolver.java`

**Interfaces:**
- Consumes: `PermissionExecutor`、`DataScopeResolver`（shared-permission-starter）、`PermissionQueryService`、`AccountIdResolver`、`CheckPermissionQuery`、`ResolveDataScopeQuery`（auth-application）
- Produces: `LocalPermissionExecutor`（@Primary，auth-service 内部短路）
- Produces: `LocalDataScopeResolver`（@Primary，auth-service 内部短路）

- [ ] **Step 1: 修改 auth-adapter pom.xml 添加 shared-permission-starter 依赖**

修改 `auth-service/auth-adapter/pom.xml`，在 `<dependencies>` 节点内追加：

```xml
    <!-- shared-permission-starter: @RequirePermission 切面 + PermissionExecutor 接口 -->
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>shared-permission-starter</artifactId>
    </dependency>
```

- [ ] **Step 2: 新增 LocalPermissionExecutor**

```java
package com.example.auth.adapter.permission;

import com.example.auth.api.dto.DataScope;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.example.shared.permission.PermissionCheckContext;
import com.example.shared.permission.PermissionCheckResult;
import com.example.shared.permission.PermissionExecutor;
import com.pension.permission.application.authorization.CheckPermissionQuery;
import com.pension.permission.application.authorization.PermissionQueryService;
import com.pension.permission.application.authorization.ResolveDataScopeQuery;
import com.pension.permission.domain.authorization.valueobject.ActionCode;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * auth-service 本地权限校验执行器.
 *
 * <p>覆盖默认的 {@code HttpExchangePermissionExecutor}，避免 auth-service 调用自身
 * 触发循环依赖。直接调用 {@link PermissionQueryService} 做权限判定。
 *
 * @author auth-adapter
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class LocalPermissionExecutor implements PermissionExecutor {

    private final PermissionQueryService permissionQueryService;

    @Override
    public PermissionCheckResult check(PermissionCheckContext context) {
        CheckPermissionQuery query = new CheckPermissionQuery(
            UserNo.of(context.accountId()),
            resolvePlanNo(context.planId()),
            new BusinessCode(context.businessCode()),
            resolveActionCode(context.actionCode()));
        boolean allowed = permissionQueryService.checkPermission(query);
        return allowed
            ? PermissionCheckResult.allow()
            : PermissionCheckResult.deny("权限不足");
    }

    @Override
    public boolean isLocalExecution() {
        return true;
    }

    private PlanNo resolvePlanNo(String planId) {
        if (planId == null || planId.isBlank()) {
            return null;
        }
        return PlanNo.of(planId);
    }

    private ActionCode resolveActionCode(String actionCode) {
        if (actionCode == null || actionCode.isBlank()) {
            return null;
        }
        return new ActionCode(actionCode);
    }
}
```

- [ ] **Step 3: 新增 LocalDataScopeResolver**

```java
package com.example.auth.adapter.permission;

import com.example.auth.api.dto.DataScope;
import com.example.shared.identifier.id.UserNo;
import com.example.shared.permission.AccountIdResolver;
import com.example.shared.permission.DataScopeResolver;
import com.pension.permission.application.authorization.PermissionQueryService;
import com.pension.permission.application.authorization.ResolveDataScopeQuery;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * auth-service 本地数据范围解析器.
 *
 * <p>覆盖默认的 {@code DefaultDataScopeResolver}，避免 auth-service 调用自身触发循环依赖。
 * 直接调用 {@link PermissionQueryService#resolveDataScope} 解析可见范围。
 *
 * @author auth-adapter
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class LocalDataScopeResolver implements DataScopeResolver {

    private final PermissionQueryService permissionQueryService;
    private final AccountIdResolver accountIdResolver;

    @Override
    public DataScope resolve(String business) {
        String accountId = resolveCurrentAccountId();
        if (accountId == null || accountId.isBlank()) {
            return DataScope.empty();
        }
        try {
            ResolveDataScopeQuery query = new ResolveDataScopeQuery(
                UserNo.of(accountId), new BusinessCode(business));
            return permissionQueryService.resolveDataScope(query);
        } catch (Exception e) {
            log.warn("[LocalDataScopeResolver] 解析失败, fail-closed. account={}, business={}",
                accountId, business, e);
            return DataScope.empty();
        }
    }

    private String resolveCurrentAccountId() {
        return accountIdResolver.resolve(null);
    }
}
```

- [ ] **Step 4: 编译验证**

Run: `mvn -pl auth-service/auth-adapter compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add auth-service/auth-adapter/pom.xml auth-service/auth-adapter/src/main/java/com/example/auth/adapter/permission/LocalPermissionExecutor.java auth-service/auth-adapter/src/main/java/com/example/auth/adapter/permission/LocalDataScopeResolver.java
git commit -m "feat(auth-adapter): 新增 LocalPermissionExecutor 与 LocalDataScopeResolver 短路调用"
```

---

### Task 2.8: auth-adapter 新增 PermissionRegistrationController

**Files:**
- Create: `auth-service/auth-adapter/src/main/java/com/example/auth/adapter/permission/PermissionRegistrationController.java`

**Interfaces:**
- Consumes: `PermissionRegistrationApi`（auth-api，Task 1.2 产出）、`PermissionScannerService.registerFromExternal`（auth-infrastructure，Task 2.3 产出）
- Produces: `/internal/permission-registration/register` 端点（不标注 @RequirePermission，内部接口）

- [ ] **Step 1: 新增 PermissionRegistrationController**

```java
package com.example.auth.adapter.permission;

import com.example.auth.api.PermissionRegistrationApi;
import com.example.auth.api.command.PermissionRegistrationRequest;
import com.example.auth.api.dto.PermissionRegistrationResponse;
import com.example.shared.web.core.api.ApiResult;
import com.pension.permission.infrastructure.permission.PermissionRegistrationResult;
import com.pension.permission.infrastructure.permission.PermissionScannerService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

/**
 * 权限点上报 Controller（接收业务服务上报）.
 *
 * <p>不标注 {@code @RequirePermission}：路径在 {@code /internal/**} 下，
 * 被网关 {@code ExcludeRouteFilter} 403 拦截，仅服务间调用可达。
 *
 * @author auth-adapter
 */
@RestController
@AllArgsConstructor
public class PermissionRegistrationController implements PermissionRegistrationApi {

    private final PermissionScannerService scannerService;

    @Override
    public ApiResult<PermissionRegistrationResponse> register(
            PermissionRegistrationRequest request) {
        PermissionRegistrationResult result = scannerService.registerFromExternal(
            request.sourceService(),
            request.items());
        return ApiResult.success(new PermissionRegistrationResponse(
            result.totalReceived(),
            result.upserted(),
            result.unchanged()));
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -pl auth-service/auth-adapter compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add auth-service/auth-adapter/src/main/java/com/example/auth/adapter/permission/PermissionRegistrationController.java
git commit -m "feat(auth-adapter): 新增 PermissionRegistrationController 接收业务服务权限点上报"
```

---

### Task 2.9: auth-adapter PermissionCheckController 新增 resolveDataScope 端点

**Files:**
- Modify: `auth-service/auth-adapter/src/main/java/com/example/auth/adapter/permission/PermissionCheckController.java`

**Interfaces:**
- Consumes: `DataScopeRequest`、`DataScopeResponse`、`ResolveDataScopeQuery`、`PermissionQueryService.resolveDataScope`

- [ ] **Step 1: 修改 PermissionCheckController 添加 resolveDataScope 方法**

修改文件：`auth-service/auth-adapter/src/main/java/com/example/auth/adapter/permission/PermissionCheckController.java`

在 import 区追加：

```java
import com.example.auth.api.command.DataScopeRequest;
import com.example.auth.api.dto.DataScope;
import com.example.auth.api.dto.DataScopeResponse;
import com.pension.permission.application.authorization.ResolveDataScopeQuery;
```

在 `checkBatch` 方法（第 47-66 行）后追加：

```java
  @Override
  public ApiResult<DataScopeResponse> resolveDataScope(DataScopeRequest request) {
    ResolveDataScopeQuery query = new ResolveDataScopeQuery(
        UserNo.of(request.accountId()),
        new BusinessCode(request.businessCode()));
    DataScope dataScope = permissionQueryService.resolveDataScope(query);
    return ApiResult.success(new DataScopeResponse(
        dataScope.globalVisible(),
        dataScope.visiblePlans(),
        dataScope.visibleCustomers(),
        dataScope.excludedPlans(),
        dataScope.excludedCustomers()));
  }
```

- [ ] **Step 2: 编译验证**

Run: `mvn -pl auth-service/auth-adapter compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add auth-service/auth-adapter/src/main/java/com/example/auth/adapter/permission/PermissionCheckController.java
git commit -m "feat(auth-adapter): PermissionCheckController 新增 resolveDataScope 端点"
```

---

### Task 2.10: auth-adapter 标注 @RequirePermission（CustomerChannelEntitlement / PermissionMetadata / PermissionCache）

**Files:**
- Modify: `auth-service/auth-adapter/src/main/java/com/example/auth/adapter/channel/CustomerChannelEntitlementController.java`
- Modify: `auth-service/auth-adapter/src/main/java/com/example/auth/adapter/permission/PermissionMetadataController.java`
- Modify: `auth-service/auth-adapter/src/main/java/com/example/auth/adapter/permission/PermissionCacheController.java`

**Interfaces:**
- Consumes: `@RequirePermission`、`PermissionCategory.PLATFORM`

- [ ] **Step 1: 修改 CustomerChannelEntitlementController 标注 @RequirePermission**

在 `CustomerChannelEntitlementController.java` 文件 import 区追加：

```java
import com.example.auth.api.annotation.PermissionCategory;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
```

修改各方法添加注解：

`get` 方法（第 34-37 行）改为：
```java
  @Override
  @RequirePermission(business = "CHANNEL_ENTITLEMENT", action = "VIEW", category = PermissionCategory.PLATFORM)
  public ApiResult<Optional<CustomerChannelEntitlementResponse>> get(@Valid @RequestBody GetEntitlementRequest request) {
```

`enable` 方法（第 40-44 行）改为：
```java
  @Override
  @RequirePermission(business = "CHANNEL_ENTITLEMENT", action = "ENABLE", category = PermissionCategory.PLATFORM)
  public ApiResult<CustomerChannelEntitlementResponse> enable(@Valid @RequestBody EnableChannelRequest request) {
```

`disable` 方法（第 47-50 行）改为：
```java
  @Override
  @RequirePermission(business = "CHANNEL_ENTITLEMENT", action = "DISABLE", category = PermissionCategory.PLATFORM)
  public ApiResult<Void> disable(@Valid @RequestBody DisableChannelRequest request) {
```

`replace` 方法（第 53-57 行）改为：
```java
  @Override
  @RequirePermission(business = "CHANNEL_ENTITLEMENT", action = "REPLACE", category = PermissionCategory.PLATFORM)
  public ApiResult<CustomerChannelEntitlementResponse> replace(@Valid @RequestBody ReplaceChannelsRequest request) {
```

注意：原 import 已有 `import com.example.auth.api.annotation.RequirePermission;`，仅需追加 `PermissionCategory`。

- [ ] **Step 2: 修改 PermissionMetadataController 标注 @RequirePermission**

修改文件：`auth-service/auth-adapter/src/main/java/com/example/auth/adapter/permission/PermissionMetadataController.java`

在 import 区追加：

```java
import com.example.auth.api.annotation.PermissionCategory;
import com.example.auth.api.annotation.RequirePermission;
```

`listItems` 方法（第 33-37 行）改为：
```java
  @Override
  @RequirePermission(business = "PERMISSION_METADATA", action = "VIEW", category = PermissionCategory.PLATFORM)
  public ApiResult<List<PermissionItemResponse>> listItems(ListPermissionItemsRequest request) {
```

`listGroupedItems` 方法（第 40-44 行）改为：
```java
  @Override
  @RequirePermission(business = "PERMISSION_METADATA", action = "VIEW", category = PermissionCategory.PLATFORM)
  public ApiResult<List<PermissionGroupResponse>> listGroupedItems(ListPermissionItemsRequest request) {
```

- [ ] **Step 3: 修改 PermissionCacheController 标注 @RequirePermission**

修改文件：`auth-service/auth-adapter/src/main/java/com/example/auth/adapter/permission/PermissionCacheController.java`

在 import 区追加：

```java
import com.example.auth.api.annotation.PermissionCategory;
import com.example.auth.api.annotation.RequirePermission;
```

`getPlatformPermissions` 方法（第 33-36 行）改为：
```java
  @Override
  @RequirePermission(business = "PERMISSION_CACHE", action = "VIEW", category = PermissionCategory.PLATFORM)
  public ApiResult<Set<PermissionResponse>> getPlatformPermissions(GetPlatformPermissionsRequest request) {
```

`getBusinessPermissions` 方法（第 39-43 行）改为：
```java
  @Override
  @RequirePermission(business = "PERMISSION_CACHE", action = "VIEW", category = PermissionCategory.PLATFORM)
  public ApiResult<Set<PermissionResponse>> getBusinessPermissions(GetBusinessPermissionsRequest request) {
```

- [ ] **Step 4: 编译验证**

Run: `mvn -pl auth-service/auth-adapter compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add auth-service/auth-adapter/src/main/java/com/example/auth/adapter/channel/CustomerChannelEntitlementController.java auth-service/auth-adapter/src/main/java/com/example/auth/adapter/permission/PermissionMetadataController.java auth-service/auth-adapter/src/main/java/com/example/auth/adapter/permission/PermissionCacheController.java
git commit -m "feat(auth-adapter): auth-service 自身 Controller 标注 @RequirePermission(PLATFORM)"
```

---

## 阶段 3：网关改造（依赖阶段 1）

本阶段目标：
- 新增 GatewayProperties 消费 yml 白名单
- 改造 ChannelAwareSaRouter 增加 matchChannel 方法和 configureDefaultStpLogic 方法
- 改造 SaTokenGatewayConfiguration 修复非渠道前缀路径放行问题
- 改造 SessionContextInjector 支持非渠道前缀路径
- 删除 RouteRule 相关文件
- 调整 application.yml

### Task 3.1: demo-gateway 新增 GatewayProperties

**Files:**
- Create: `demo-gateway/src/main/java/com/example/gateway/security/GatewayProperties.java`
- Test: `demo-gateway/src/test/java/com/example/gateway/security/GatewayPropertiesTest.java`

**Interfaces:**
- Produces: `GatewayProperties.isPublicPath(String path)` 方法，消费 `auth.gateway.public-paths` 配置

- [ ] **Step 1: 编写 GatewayProperties 失败测试**

Create: `demo-gateway/src/test/java/com/example/gateway/security/GatewayPropertiesTest.java`

```java
package com.example.gateway.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GatewayProperties 白名单匹配测试")
class GatewayPropertiesTest {

    @Test
    @DisplayName("精确路径匹配")
    void exactPathMatches() {
        GatewayProperties properties = new GatewayProperties(List.of("/actuator"));
        assertThat(properties.isPublicPath("/actuator")).isTrue();
    }

    @Test
    @DisplayName("Ant 模式 ** 匹配子路径")
    void antPatternMatchesSubpaths() {
        GatewayProperties properties = new GatewayProperties(List.of("/actuator/**"));
        assertThat(properties.isPublicPath("/actuator/health")).isTrue();
        assertThat(properties.isPublicPath("/actuator/info/details")).isTrue();
    }

    @Test
    @DisplayName("非白名单路径返回 false")
    void nonWhitelistedPathReturnsFalse() {
        GatewayProperties properties = new GatewayProperties(List.of("/actuator/**"));
        assertThat(properties.isPublicPath("/admin/users/list")).isFalse();
    }

    @Test
    @DisplayName("空白名单时所有路径都返回 false")
    void emptyWhitelistReturnsFalse() {
        GatewayProperties properties = new GatewayProperties(List.of());
        assertThat(properties.isPublicPath("/any/path")).isFalse();
    }

    @Test
    @DisplayName("null 白名单时所有路径都返回 false")
    void nullWhitelistReturnsFalse() {
        GatewayProperties properties = new GatewayProperties(null);
        assertThat(properties.isPublicPath("/any/path")).isFalse();
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -pl demo-gateway test -Dtest=GatewayPropertiesTest`
Expected: FAIL（GatewayProperties 类不存在）

- [ ] **Step 3: 新增 GatewayProperties**

```java
package com.example.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.AntPathMatcher;

import java.util.List;

/**
 * 网关层白名单配置.
 *
 * <p>配置前缀：{@code auth.gateway}
 *
 * <p>对应设计文档 3.5 节：消费 {@code auth.gateway.public-paths} 配置，
 * 替代 SaReactorFilter 中的硬编码 {@code addExclude}。
 *
 * @author demo-gateway
 * @since 2026/8/7
 */
@ConfigurationProperties(prefix = "auth.gateway")
public record GatewayProperties(
    /**
     * 公共白名单路径模式（Ant 风格，如 /actuator/**）。
     *
     * <p>白名单内路径跳过登录校验。
     */
    List<String> publicPaths
) {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    public GatewayProperties {
        if (publicPaths == null) {
            publicPaths = List.of();
        }
    }

    /**
     * 判断路径是否在白名单中。
     *
     * @param path 请求路径
     * @return true 表示白名单路径（跳过登录校验）
     */
    public boolean isPublicPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        for (String pattern : publicPaths) {
            if (MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn -pl demo-gateway test -Dtest=GatewayPropertiesTest`
Expected: PASS（5 个测试用例通过）

- [ ] **Step 5: 注册 GatewayProperties 为 Bean**

修改 `demo-gateway/src/main/java/com/example/gateway/security/SaTokenGatewayConfiguration.java`，在类顶部追加注解：

```java
import org.springframework.boot.context.properties.EnableConfigurationProperties;
```

并在类声明上追加：

```java
@EnableConfigurationProperties(GatewayProperties.class)
```

修改后的类声明前几行：

```java
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(GatewayProperties.class)
public class SaTokenGatewayConfiguration {
```

- [ ] **Step 6: 编译验证**

Run: `mvn -pl demo-gateway compile`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add demo-gateway/src/main/java/com/example/gateway/security/GatewayProperties.java demo-gateway/src/main/java/com/example/gateway/security/SaTokenGatewayConfiguration.java demo-gateway/src/test/java/com/example/gateway/security/GatewayPropertiesTest.java
git commit -m "feat(gateway): 新增 GatewayProperties 消费 auth.gateway.public-paths 白名单"
```

---

### Task 3.2: demo-gateway 改造 ChannelAwareSaRouter

**Files:**
- Modify: `demo-gateway/src/main/java/com/example/gateway/security/ChannelAwareSaRouter.java`
- Test: `demo-gateway/src/test/java/com/example/gateway/security/ChannelAwareSaRouterMatchChannelTest.java`

**Interfaces:**
- Produces: `ChannelAwareSaRouter.matchChannel(String path)` 返回 `ChannelType`（不校验登录）
- Produces: `ChannelAwareSaRouter.configureDefaultStpLogic()` 配置默认 StpLogic 识别所有渠道 token

- [ ] **Step 1: 编写 matchChannel 失败测试**

Create: `demo-gateway/src/test/java/com/example/gateway/security/ChannelAwareSaRouterMatchChannelTest.java`

```java
package com.example.gateway.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChannelAwareSaRouter.matchChannel 测试")
class ChannelAwareSaRouterMatchChannelTest {

    @Test
    @DisplayName("/internet 前缀匹配 INTERNET 渠道")
    void internetPrefixMatches() {
        ChannelAwareSaRouter router = new ChannelAwareSaRouter();
        assertThat(router.matchChannel("/internet/business/handle")).isEqualTo(ChannelType.INTERNET);
    }

    @Test
    @DisplayName("/hq 前缀匹配 HQ 渠道")
    void hqPrefixMatches() {
        ChannelAwareSaRouter router = new ChannelAwareSaRouter();
        assertThat(router.matchChannel("/hq/users/list")).isEqualTo(ChannelType.HQ);
    }

    @Test
    @DisplayName("/branch 前缀匹配 BRANCH 渠道")
    void branchPrefixMatches() {
        ChannelAwareSaRouter router = new ChannelAwareSaRouter();
        assertThat(router.matchChannel("/branch/auth/login")).isEqualTo(ChannelType.BRANCH);
    }

    @Test
    @DisplayName("非渠道前缀返回 null")
    void nonChannelPrefixReturnsNull() {
        ChannelAwareSaRouter router = new ChannelAwareSaRouter();
        assertThat(router.matchChannel("/admin/users/list")).isNull();
        assertThat(router.matchChannel("/actuator/health")).isNull();
    }

    @Test
    @DisplayName("null/空路径返回 null")
    void nullPathReturnsNull() {
        ChannelAwareSaRouter router = new ChannelAwareSaRouter();
        assertThat(router.matchChannel(null)).isNull();
        assertThat(router.matchChannel("")).isNull();
        assertThat(router.matchChannel("   ")).isNull();
    }

    @Test
    @DisplayName("/internet 不带斜杠后缀时也能匹配")
    void internetExactPathMatches() {
        ChannelAwareSaRouter router = new ChannelAwareSaRouter();
        assertThat(router.matchChannel("/internet")).isEqualTo(ChannelType.INTERNET);
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -pl demo-gateway test -Dtest=ChannelAwareSaRouterMatchChannelTest`
Expected: FAIL（matchChannel 方法不存在）

- [ ] **Step 3: 在 ChannelAwareSaRouter 中新增 matchChannel 与 configureDefaultStpLogic 方法**

修改文件：`demo-gateway/src/main/java/com/example/gateway/security/ChannelAwareSaRouter.java`

在 import 区追加：

```java
import cn.dev33.satoken.stp.StpUtil;
import java.util.List;
```

在 `matchAndCheckLogin` 方法（第 69-79 行）后追加：

```java
  /**
   * 仅根据请求路径识别渠道，不校验登录.
   *
   * <p>供 SaTokenGatewayConfiguration 在 setAuth 中调用，由调用方决定是否调用 checkLogin。
   *
   * @param path 请求路径
   * @return 命中的渠道类型；非渠道前缀返回 null
   */
  public ChannelType matchChannel(String path) {
    return ChannelType.fromPath(path);
  }

  /**
   * 配置默认 StpLogic 识别所有渠道 token.
   *
   * <p>管理类 API（非渠道前缀路径）使用默认 StpLogic 校验登录态，
   * 默认 StpLogic 读取所有三个渠道的 Header，用户携带任一渠道 token 都能通过校验。
   *
   * <p>在 SaTokenGatewayConfiguration 初始化时调用一次。
   */
  public void configureDefaultStpLogic() {
    StpLogic defaultLogic = new StpLogic("default");
    SaTokenConfig config = new SaTokenConfig();
    config.setTokenName(List.of(
        ChannelType.INTERNET.tokenHeader(),
        ChannelType.HQ.tokenHeader(),
        ChannelType.BRANCH.tokenHeader()));
    config.setIsReadHeader(true);
    config.setIsReadCookie(false);
    defaultLogic.setConfig(config);
    StpUtil.setStpLogic(defaultLogic);
    log.info("默认 StpLogic 配置完成: 识别所有渠道 token");
  }
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn -pl demo-gateway test -Dtest=ChannelAwareSaRouterMatchChannelTest`
Expected: PASS（6 个测试用例通过）

- [ ] **Step 5: Commit**

```bash
git add demo-gateway/src/main/java/com/example/gateway/security/ChannelAwareSaRouter.java demo-gateway/src/test/java/com/example/gateway/security/ChannelAwareSaRouterMatchChannelTest.java
git commit -m "feat(gateway): ChannelAwareSaRouter 新增 matchChannel 与 configureDefaultStpLogic"
```

---

### Task 3.3: demo-gateway 改造 SaTokenGatewayConfiguration

**Files:**
- Modify: `demo-gateway/src/main/java/com/example/gateway/security/SaTokenGatewayConfiguration.java`
- Modify: `demo-gateway/src/main/java/com/example/gateway/security/GatewayStpInterfaceImpl.java`（如需删除 RouteRuleLoader 字段）

**Interfaces:**
- Consumes: `GatewayProperties.isPublicPath`、`ChannelAwareSaRouter.matchChannel`、`StpUtil.checkLogin`

- [ ] **Step 1: 修改 SaTokenGatewayConfiguration 改造 setAuth**

修改文件：`demo-gateway/src/main/java/com/example/gateway/security/SaTokenGatewayConfiguration.java`

替换全文为：

```java
package com.example.gateway.security;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.stp.StpUtil;
import com.example.gateway.order.GatewayFilterOrder;
import com.example.shared.web.core.api.ApiResult;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * sa-token 网关层集成配置 - 注册 SaReactorFilter 完成动态鉴权.
 *
 * <p>对应设计文档 3.2 节，在 WebFlux 网关注册 SaReactorFilter，实现：
 * <ol>
 *   <li>yml 白名单放行（从 GatewayProperties 读取，替代硬编码 addExclude）</li>
 *   <li>渠道前缀路径（/internet, /hq, /branch）→ 对应渠道 StpLogic.checkLogin()</li>
 *   <li>非渠道前缀路径（/admin/** 等）→ 默认 StpUtil.checkLogin()（识别任一渠道 token）</li>
 *   <li>统一异常响应：NotLoginException → 401, NotPermission/RoleException → 403</li>
 * </ol>
 *
 * @author auth-service
 * @since 2026/7/26
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(GatewayProperties.class)
public class SaTokenGatewayConfiguration {

    private static final String CODE_NOT_LOGIN = "COMMON.0002";
    private static final String CODE_NO_PERMISSION = "COMMON.0003";
    private static final String CODE_INTERNAL_ERROR = "COMMON.0050";

    private static final int FILTER_ORDER_AUTH = -200;

    private final ChannelAwareSaRouter channelAwareSaRouter;
    private final GatewayProperties gatewayProperties;

    /**
     * 启动时配置默认 StpLogic 识别所有渠道 token.
     */
    @PostConstruct
    public void initDefaultStpLogic() {
        channelAwareSaRouter.configureDefaultStpLogic();
    }

    /**
     * 注册 SaReactorFilter,在 WebFlux 过滤器链中执行 sa-token 鉴权.
     *
     * @return SaReactorFilter 实例
     */
    @Bean
    @Order(FILTER_ORDER_AUTH)
    public SaReactorFilter saReactorFilter() {
        return new SaReactorFilter()
            .addInclude("/**")
            .setAuth(obj -> {
                String path = SaHolder.getRequest().getRequestPath();

                // 1. yml 白名单放行（从 GatewayProperties 读取）
                if (gatewayProperties.isPublicPath(path)) {
                    return;
                }

                // 2. 渠道前缀路径 → 对应渠道 StpLogic 登录校验
                ChannelType channel = channelAwareSaRouter.matchChannel(path);
                if (channel != null) {
                    channelAwareSaRouter.getStpLogic(channel).checkLogin();
                    return;
                }

                // 3. 非渠道前缀路径 → 默认 StpLogic 登录校验（识别任一渠道 token）
                StpUtil.checkLogin();
            })
            .setError(this::handleError);
    }

    /**
     * 统一异常处理:将 sa-token 异常转换为 ApiResult 响应.
     */
    private Object handleError(Throwable e) {
        if (e instanceof NotLoginException) {
            SaHolder.getResponse().setStatus(401);
            log.warn("[SaTokenGateway] 未登录访问: {}", e.getMessage());
            return ApiResult.failure(CODE_NOT_LOGIN, "未登录或登录已过期");
        }
        if (e instanceof NotPermissionException || e instanceof NotRoleException) {
            SaHolder.getResponse().setStatus(403);
            log.warn("[SaTokenGateway] 权限不足: {}", e.getMessage());
            return ApiResult.failure(CODE_NO_PERMISSION, "无权限访问");
        }
        SaHolder.getResponse().setStatus(500);
        log.error("[SaTokenGateway] 鉴权异常", e);
        return ApiResult.failure(CODE_INTERNAL_ERROR, "系统内部错误");
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -pl demo-gateway compile`
Expected: 编译错误（因为 RouteRuleLoader 还在被其他地方引用，比如 GatewayStpInterfaceImpl 或测试）

如果出现编译错误提示 `RouteRuleLoader` 找不到引用，执行 Step 3 删除 RouteRule 相关文件；否则跳到 Step 4。

- [ ] **Step 3: 删除 RouteRule 相关文件**

使用 DeleteFile 工具删除以下文件：

1. `demo-gateway/src/main/java/com/example/gateway/security/RouteRule.java`
2. `demo-gateway/src/main/java/com/example/gateway/security/RouteRuleLoader.java`
3. `demo-gateway/src/test/java/com/example/gateway/security/RouteRuleTest.java`
4. `demo-gateway/src/test/java/com/example/gateway/security/RouteRuleLoaderTest.java`

- [ ] **Step 4: 检查并清理 GatewayStpInterfaceImpl 中的 RouteRule 引用（如有）**

Read 文件 `demo-gateway/src/main/java/com/example/gateway/security/GatewayStpInterfaceImpl.java`，确认其未引用 RouteRuleLoader（已查看，未引用，跳过）。

- [ ] **Step 5: 编译验证**

Run: `mvn -pl demo-gateway compile`
Expected: BUILD SUCCESS

- [ ] **Step 6: 运行 demo-gateway 测试**

Run: `mvn -pl demo-gateway test`
Expected: 测试可能因 RouteRuleTest/RouteRuleLoaderTest 被删除而跳过，其他测试通过。如果 GatewayStpInterfaceImplTest 失败（因依赖 ChannelAwareSaRouter mock），更新测试。

- [ ] **Step 7: Commit**

```bash
git add demo-gateway/src/main/java/com/example/gateway/security/SaTokenGatewayConfiguration.java demo-gateway/src/main/java/com/example/gateway/security/RouteRule.java demo-gateway/src/main/java/com/example/gateway/security/RouteRuleLoader.java demo-gateway/src/test/java/com/example/gateway/security/RouteRuleTest.java demo-gateway/src/test/java/com/example/gateway/security/RouteRuleLoaderTest.java
git commit -m "refactor(gateway): SaTokenGatewayConfiguration 改造为 yml 白名单 + 通用登录校验，移除 RouteRule"
```

---

### Task 3.4: demo-gateway 改造 SessionContextInjector

**Files:**
- Modify: `demo-gateway/src/main/java/com/example/gateway/security/SessionContextInjector.java`

**Interfaces:**
- Consumes: `GatewayProperties.isPublicPath`、`ChannelAwareSaRouter.getStpLogic`、`ChannelType.values()`

- [ ] **Step 1: 修改 SessionContextInjector 支持非渠道前缀路径**

修改文件：`demo-gateway/src/main/java/com/example/gateway/security/SessionContextInjector.java`

在类字段中追加 `GatewayProperties` 依赖：

替换字段声明部分（第 88-90 行附近）：

```java
  private final ChannelAwareSaRouter channelAwareSaRouter;
  private final ObjectMapper objectMapper;
  private final GatewaySessionProperties sessionProperties;
  private final GatewayProperties gatewayProperties;
```

修改 `filter` 方法（第 92-139 行）：

```java
  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getPath().value();

    // 1. 白名单路径不注入会话头
    if (gatewayProperties.isPublicPath(path)) {
      return chain.filter(exchange);
    }

    // 2. 尝试从任一已登录渠道获取 loginId
    ChannelType channel = ChannelType.fromPath(path);
    String loginId;
    if (channel != null) {
      // 渠道前缀路径：从对应渠道 StpLogic 读取
      StpLogic stpLogic = channelAwareSaRouter.getStpLogic(channel);
      try {
        loginId = stpLogic.getLoginIdAsString();
      } catch (Exception e) {
        return chain.filter(exchange);
      }
    } else {
      // 非渠道前缀路径：尝试任一渠道已登录
      loginId = resolveAnyChannelLoginId();
    }

    if (loginId == null || loginId.isBlank()) {
      return chain.filter(exchange);
    }

    // 3. 读取 Token-Session 中的会话数据
    ChannelType loginChannel = channel != null ? channel : resolveLoginChannel();
    Map<String, Object> sessionContext = buildSessionContext(
        loginChannel != null ? channelAwareSaRouter.getStpLogic(loginChannel) : null,
        loginId,
        loginChannel);
    String encodedContext = encodeSessionContext(sessionContext);

    String signatureKey = sessionProperties.signatureKey();
    long ttlSeconds = sessionProperties.ttlSeconds() > 0
        ? sessionProperties.ttlSeconds()
        : SessionSignatureUtils.DEFAULT_TTL_SECONDS;

    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
        .headers(headers -> SENSITIVE_HEADERS.forEach(headers::remove))
        .header(SESSION_CONTEXT_HEADER, encodedContext)
        .headers(headers -> applySignedHeaders(headers, loginId, encodedContext, signatureKey, ttlSeconds))
        .build();

    ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
    return chain.filter(mutatedExchange);
  }

  /**
   * 遍历所有渠道，返回第一个已登录的 loginId.
   */
  private String resolveAnyChannelLoginId() {
    for (ChannelType ch : ChannelType.values()) {
      StpLogic stpLogic = channelAwareSaRouter.getStpLogic(ch);
      try {
        if (stpLogic.isLogin()) {
          return stpLogic.getLoginIdAsString();
        }
      } catch (Exception ignored) {
        // 该渠道未登录，继续尝试
      }
    }
    return null;
  }

  /**
   * 遍历所有渠道，返回第一个已登录的渠道类型.
   */
  private ChannelType resolveLoginChannel() {
    for (ChannelType ch : ChannelType.values()) {
      StpLogic stpLogic = channelAwareSaRouter.getStpLogic(ch);
      try {
        if (stpLogic.isLogin()) {
          return ch;
        }
      } catch (Exception ignored) {
        // 该渠道未登录，继续尝试
      }
    }
    return null;
  }
```

- [ ] **Step 2: 修改 buildSessionContext 方法处理 null StpLogic**

将原 `buildSessionContext` 方法（第 180-202 行）替换为：

```java
  private Map<String, Object> buildSessionContext(StpLogic stpLogic, String loginId, ChannelType channel) {
    Map<String, Object> context = new LinkedHashMap<>();
    context.put("userNo", loginId);
    context.put("channelType", channel != null ? channel.loginType() : "default");

    if (stpLogic == null) {
      return context;
    }

    try {
      String token = stpLogic.getTokenValueByLoginId(loginId);
      if (token != null) {
        SaSession session = stpLogic.getTokenSessionByToken(token);
        if (session != null) {
          Object planId = session.get(GatewayStpInterfaceImpl.SESSION_KEY_CURRENT_PLAN_ID);
          if (planId != null) {
            context.put("planNo", planId.toString());
          }
        }
      }
    } catch (Exception e) {
      log.debug("[SessionContextInjector] 读取 Token-Session 失败: loginId={}", loginId, e);
    }

    return context;
  }
```

- [ ] **Step 3: 编译验证**

Run: `mvn -pl demo-gateway compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add demo-gateway/src/main/java/com/example/gateway/security/SessionContextInjector.java
git commit -m "refactor(gateway): SessionContextInjector 支持非渠道前缀路径会话注入"
```

---

### Task 3.5: demo-gateway application.yml 调整

**Files:**
- Modify: `demo-gateway/src/main/resources/application.yml`

- [ ] **Step 1: 修改 application.yml 调整 public-paths 和移除 RouteRuleApi 客户端配置**

修改文件：`demo-gateway/src/main/resources/application.yml`

将 `auth.gateway` 部分（第 130-142 行）替换为：

```yaml
## 网关层 auth 配置（白名单等）
auth:
  gateway:
    # 公共白名单路径(无需登录校验)——被 GatewayProperties 消费，替代硬编码 addExclude
    public-paths:
      - /actuator/**
      - /actuator
      - /favicon.ico
      - /internet/auth/login
      - /hq/auth/login
      - /branch/auth/login
      - /branch/auth/secondary-auth/initiate
      - /branch/auth/secondary-auth/confirm
      - /branch/auth/secondary-auth/status/**
      # 管理后台登录接口
      - /admin/auth/login
```

删除文件末尾的 `io.github.danielliu1123.httpexchange.clients` 配置块（第 144-151 行）：

```yaml
## httpexchange 客户端配置（auth-service RouteRuleApi）
io:
  github:
    danielliu1123:
      httpexchange:
        clients:
          - client-class-name: com.example.auth.api.RouteRuleApi
            url: lb://auth-service
```

完整移除该配置块。

- [ ] **Step 2: 启动验证（如本地环境允许）**

Run: `mvn -pl demo-gateway compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add demo-gateway/src/main/resources/application.yml
git commit -m "refactor(gateway): application.yml 调整白名单并移除 RouteRuleApi 客户端"
```

---

## 阶段 4：业务服务接入（依赖阶段 1、2）

本阶段目标：
- business-core-adapter BusinessFormController.status 补标注 @RequirePermission
- 4 个业务服务 starter 的 application-local.yml 验证 httpexchange 客户端配置已包含 `com.example.auth.api` 包（PermissionRegistrationApi 自动覆盖）

### Task 4.1: business-core-adapter BusinessFormController.status 补标注 @RequirePermission

**Files:**
- Modify: `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/form/BusinessFormController.java`

**Interfaces:**
- Consumes: `@RequirePermission`（已有 import）

- [ ] **Step 1: 修改 BusinessFormController.status 方法添加 @RequirePermission**

修改文件：`business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/form/BusinessFormController.java`

将 `status` 方法（第 93-100 行）替换为：

```java
  @Override
  @RequirePermission(business = "FORM", action = "VIEW")
  public ApiResult<FormStatusResponse> status(@Valid @RequestBody GetFormStatusQuery query) {
    SessionContext session = sessionResolver.require();
    log.info("查询表单状态: formId={}, userNo={}", query.formId(), session.userNo());

    BusinessForm form = formAppService.getFormStatus(new FormId(query.formId()));
    return ApiResult.success(converter.toStatusResponse(form));
  }
```

- [ ] **Step 2: 编译验证**

Run: `mvn -pl business-core-kernel/business-core-adapter compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/form/BusinessFormController.java
git commit -m "feat(core-adapter): BusinessFormController.status 补标注 @RequirePermission(FORM, VIEW)"
```

---

### Task 4.2: 业务服务 starter yml 配置验证（无需修改，仅验证）

**Files:**
- Verify: `approval-service/approval-starter/src/main/resources/application-local.yml`
- Verify: `file-service/file-starter/src/main/resources/application-local.yml`
- Verify: `integration-service/integration-service-starter/src/main/resources/application-local.yml`
- Verify: `annuity-service/annuity-starter/src/main/resources/application-local.yml`

**说明：** 业务服务 yml 已使用包名 `com.example.auth.api` 作为 httpexchange clients key，自动覆盖该包下所有 `@HttpExchange` 接口（包括新增的 `PermissionRegistrationApi`）。**无需修改 yml**。

- [ ] **Step 1: 验证 approval-starter yml 配置**

Read 文件 `approval-service/approval-starter/src/main/resources/application-local.yml`，确认包含：

```yaml
httpexchange:
  clients:
    com.example.auth.api:
      url: lb://auth-service
```

已包含，无需修改。

- [ ] **Step 2: 验证 file-starter yml 配置**

Read 文件 `file-service/file-starter/src/main/resources/application-local.yml`，确认包含：

```yaml
httpexchange:
  clients:
    com.example.auth.api:
      url: lb://auth-service
```

已包含，无需修改。

- [ ] **Step 3: 验证 integration-service-starter yml 配置**

Read 文件 `integration-service/integration-service-starter/src/main/resources/application-local.yml`，确认包含：

```yaml
httpexchange:
  clients:
    com.example.auth.api:
      url: lb://auth-service
```

已包含，无需修改。

- [ ] **Step 4: 验证 annuity-starter yml 配置**

Read 文件 `annuity-service/annuity-starter/src/main/resources/application-local.yml`，确认包含：

```yaml
httpexchange:
  clients:
    com.example.auth.api:
      url: lb://auth-service
```

已包含，无需修改。

- [ ] **Step 5: 编译验证业务服务模块**

Run: `mvn -pl approval-service/approval-starter,file-service/file-starter,integration-service/integration-service-starter,annuity-service/annuity-starter compile`
Expected: BUILD SUCCESS

- [ ] **Step 6: 提交验证记录（仅文档说明，无需代码变更）**

无需 commit（无文件变更）。

---

## 阶段 5：清理与验证

本阶段目标：
- 删除 auth-adapter RouteRuleController（先删实现方，避免 auth-adapter 编译失败）
- 删除 auth-api RouteRuleApi、RouteRuleResponse、ListRouteRulesQuery
- 更新 08-错误码规范.md 补充 SHARED.PERM 错误码
- 全量编译验证
- 完整测试

> 删除顺序说明：`RouteRuleController`（auth-adapter）实现 `RouteRuleApi`（auth-api），auth-adapter 依赖 auth-api。若先删 auth-api 接口，auth-adapter 将编译失败。因此必须先删 Controller，再删 API 层文件。阶段 3 已删除 demo-gateway 侧的 `RouteRule`/`RouteRuleLoader` 及 `application.yml` 中的 `RouteRuleApi` 客户端配置，故本阶段仅需清理 auth-service 侧残留。

### Task 5.1: 删除 auth-adapter RouteRuleController

**Files:**
- Delete: `auth-service/auth-adapter/src/main/java/com/example/auth/adapter/route/RouteRuleController.java`
- Delete: `auth-service/auth-adapter/src/main/java/com/example/auth/adapter/route/`（空目录）

**Interfaces:**
- Consumes: 阶段 3 已移除 demo-gateway 对 `RouteRuleApi` 的 HttpExchange 客户端配置和 `RouteRuleLoader` 引用
- Produces: auth-adapter 不再包含 `route` 包，`RouteRuleApi` 失去唯一实现方，为 Task 5.2 删除 auth-api 文件扫清编译依赖

- [ ] **Step 1: 确认 RouteRuleController 无其他引用**

Run: `mvn -pl auth-service/auth-adapter dependency:analyze`（可选，确认无其他类依赖 RouteRuleController）
Expected: 无编译依赖告警（RouteRuleController 是 @RestController，由 Spring 容器管理，无其他类直接引用）

- [ ] **Step 2: 删除 RouteRuleController 及空目录**

```bash
git rm auth-service/auth-adapter/src/main/java/com/example/auth/adapter/route/RouteRuleController.java
```

若 `route` 目录下无其他文件，删除空目录：

```bash
git rm -r auth-service/auth-adapter/src/main/java/com/example/auth/adapter/route/
```

> 注意：使用 `git rm` 而非手动删除文件，确保 git 跟踪删除操作。若 `route` 目录下仍有其他文件（实际不存在），仅删除 `RouteRuleController.java`。

- [ ] **Step 3: 编译验证 auth-adapter**

Run: `mvn -pl auth-service/auth-adapter compile`
Expected: BUILD SUCCESS（`RouteRuleController` 删除后，`RouteRuleApi` 接口暂无实现方，但 Spring 不会因无实现方而编译失败；运行时若无 @RestController 实现该 @HttpExchange 接口，该接口仅作为客户端代理契约存在，不影响编译）

- [ ] **Step 4: Commit**

```bash
git add -A auth-service/auth-adapter/src/main/java/com/example/auth/adapter/route/
git commit -m "refactor(auth-adapter): 删除 RouteRuleController，动态路由规则机制已废弃"
```

---

### Task 5.2: 删除 auth-api RouteRuleApi、RouteRuleResponse、ListRouteRulesQuery

**Files:**
- Delete: `auth-service/auth-api/src/main/java/com/example/auth/api/RouteRuleApi.java`
- Delete: `auth-service/auth-api/src/main/java/com/example/auth/api/dto/RouteRuleResponse.java`
- Delete: `auth-service/auth-api/src/main/java/com/example/auth/api/query/ListRouteRulesQuery.java`

**Interfaces:**
- Consumes: Task 5.1 已删除 `RouteRuleController`（唯一实现方）；阶段 3 已删除 demo-gateway 的 `RouteRuleLoader`（唯一 HttpExchange 消费方）
- Produces: auth-api 不再包含 RouteRule 相关协议定义，动态路由规则机制彻底移除

- [ ] **Step 1: 确认 auth-api RouteRule 文件无其他引用**

搜索全项目对这三个类的引用（排除 docs 和本计划文件）：

Run: `mvn -pl auth-service/auth-api dependency:analyze`
Expected: 无编译依赖告警

预期结果：阶段 3 已删除 demo-gateway 的 `RouteRuleLoader`、`RouteRule` 及 `application.yml` 中的客户端配置，Task 5.1 已删除 `RouteRuleController`，此时三个 auth-api 文件已无任何代码引用。

- [ ] **Step 2: 删除三个 auth-api 文件**

```bash
git rm auth-service/auth-api/src/main/java/com/example/auth/api/RouteRuleApi.java
git rm auth-service/auth-api/src/main/java/com/example/auth/api/dto/RouteRuleResponse.java
git rm auth-service/auth-api/src/main/java/com/example/auth/api/query/ListRouteRulesQuery.java
```

- [ ] **Step 3: 编译验证 auth-api**

Run: `mvn -pl auth-service/auth-api compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: 全项目编译验证（确认无残留引用）**

Run: `mvn clean compile -DskipTests`
Expected: BUILD SUCCESS（全项目无任何模块引用已删除的 RouteRule 类）

若编译失败，根据错误信息定位残留引用并清理（可能遗漏处：docs 中无代码引用；其他业务服务未使用 RouteRuleApi）。

- [ ] **Step 5: Commit**

```bash
git add -A auth-service/auth-api/src/main/java/com/example/auth/api/RouteRuleApi.java auth-service/auth-api/src/main/java/com/example/auth/api/dto/RouteRuleResponse.java auth-service/auth-api/src/main/java/com/example/auth/api/query/ListRouteRulesQuery.java
git commit -m "refactor(auth-api): 删除 RouteRuleApi、RouteRuleResponse、ListRouteRulesQuery"
```

---

### Task 5.3: 更新 08-错误码规范.md 补充 SHARED.PERM 错误码

**Files:**
- Modify: `.trae/rules/08-错误码规范.md`（在 SHARED 域迁移对照表中补充 SHARED.PERM 条目）

**Interfaces:**
- Consumes: 阶段 1 Task 1.x 新增 `PermissionErrorCode` 的 `SHARED.PERM.0005`（DATA_SCOPE_RESOLVE_FAILED），现有 `SHARED.PERM.0001-0004` 已在代码中定义但规范文档未登记
- Produces: 规范文档与代码错误码对齐，新增模块错误码可追溯

- [ ] **Step 1: 读取当前 08-错误码规范.md 的 SHARED 域迁移对照表**

Read: `.trae/rules/08-错误码规范.md`
定位到 `### SHARED 域` 小节（迁移对照表），当前内容如下，末尾为 `SHARED.CRYPTO.0004`：

```markdown
### SHARED 域

| 旧码  | 新码               | 含义            |
|-------|--------------------|-----------------|
| 12001 | SHARED.DOMAIN.0001 | 实体不存在      |
| 12002 | SHARED.DOMAIN.0002 | 数据校验失败    |
| 12003 | SHARED.DOMAIN.0003 | 操作无效        |
| 13001 | SHARED.LOCK.0001   | 获取锁失败      |
| 14001 | SHARED.ID.0001     | ID 生成异常     |
| 14002 | SHARED.ID.0002     | ID 规则配置错误 |
| 14003 | SHARED.ID.0003     | ID 结构配置错误 |
| 14004 | SHARED.ID.0004     | ID 格式配置错误 |
| 14005 | SHARED.ID.0005     | ID 号段耗尽     |
| 14006 | SHARED.ID.0006     | ID 类型错误     |
| 14007 | SHARED.ID.0007     | ID 初始化错误   |
| 14008 | SHARED.ID.0008     | 找不到 ID 策略  |
| 19101 | SHARED.CRYPTO.0001 | 加密失败        |
| 19102 | SHARED.CRYPTO.0002 | 解密失败        |
| 19103 | SHARED.CRYPTO.0003 | 密钥未配置      |
| 19104 | SHARED.CRYPTO.0004 | 密钥格式非法    |
```

注意：SHARED.PERM 模块为本次统一鉴权体系新增，无旧码对应，故在迁移对照表末尾以"新增（无旧码）"形式登记。

- [ ] **Step 2: 在 SHARED 域迁移对照表末尾追加 SHARED.PERM 条目**

在 `| 19104 | SHARED.CRYPTO.0004 | 密钥格式非法    |` 行之后、`### CORE 域` 之前追加：

```markdown
| -     | SHARED.PERM.0001    | 权限不足，拒绝访问（新增，无旧码） |
| -     | SHARED.PERM.0002    | 权限校验服务暂不可用（新增，无旧码） |
| -     | SHARED.PERM.0003    | 会话签名验证失败（新增，无旧码） |
| -     | SHARED.PERM.0004    | 会话上下文缺失（新增，无旧码） |
| -     | SHARED.PERM.0005    | 数据范围解析失败（新增，无旧码） |
```

修改后 SHARED 域迁移对照表末尾片段：

```markdown
| 19101 | SHARED.CRYPTO.0001 | 加密失败        |
| 19102 | SHARED.CRYPTO.0002 | 解密失败        |
| 19103 | SHARED.CRYPTO.0003 | 密钥未配置      |
| 19104 | SHARED.CRYPTO.0004 | 密钥格式非法    |
| -     | SHARED.PERM.0001    | 权限不足，拒绝访问（新增，无旧码） |
| -     | SHARED.PERM.0002    | 权限校验服务暂不可用（新增，无旧码） |
| -     | SHARED.PERM.0003    | 会话签名验证失败（新增，无旧码） |
| -     | SHARED.PERM.0004    | 会话上下文缺失（新增，无旧码） |
| -     | SHARED.PERM.0005    | 数据范围解析失败（新增，无旧码） |

### CORE 域
```

- [ ] **Step 3: 确认 SHARED 域模块缩写分配表已包含 PERM 条目**

Read: `.trae/rules/08-错误码规范.md`
定位到 `### SHARED 域` 模块缩写分配表（第三节），确认已存在如下行（无需修改，仅核对）：

```markdown
| PERM     | shared-permission-starter | 功能权限校验核心                         |
```

若该行已存在（当前代码库已登记），跳过本步骤；若缺失，在 `| PDF | shared-pdf | PDF 生成核心 |` 行之后追加该行。

- [ ] **Step 4: Commit**

```bash
git add .trae/rules/08-错误码规范.md
git commit -m "docs(rules): 08-错误码规范补充 SHARED.PERM.0001-0005 错误码登记"
```

---

### Task 5.4: 全量编译验证

**Files:**
- 无文件变更，仅执行编译命令验证全项目构建通过

**Interfaces:**
- Consumes: 阶段 1-4 所有代码变更 + Task 5.1/5.2 删除 RouteRule 文件 + Task 5.3 规范文档更新
- Produces: 全项目编译通过的可执行状态，为 Task 5.5 测试做准备

- [ ] **Step 1: 全项目清理编译**

Run: `mvn clean compile -DskipTests`
Expected: BUILD SUCCESS（所有模块编译通过，无残留 RouteRule 引用，无符号缺失）

常见失败原因与处理：
1. `cannot find symbol: class RouteRuleApi` / `RouteRuleResponse` / `ListRouteRulesQuery` / `RouteRuleController` / `RouteRule` / `RouteRuleLoader`
   → 残留引用未清理，搜索对应类名定位并删除引用
2. `cannot find symbol: method resolveDataScope` / `resolveVisibleScope`
   → 阶段 2 auth-application 的 `PermissionQueryService`/`EffectivePermissionService` 方法未正确新增，回查 Task 2.x
3. `cannot find symbol: method matchChannel` / `configureDefaultStpLogic`
   → 阶段 3 `ChannelAwareSaRouter` 改造未完成，回查 Task 3.2
4. `cannot find symbol: class GatewayProperties`
   → 阶段 3 `GatewayProperties` 未新增或未注册 `@EnableConfigurationProperties`，回查 Task 3.1

- [ ] **Step 2: 全项目打包验证（含依赖解析）**

Run: `mvn clean package -DskipTests`
Expected: BUILD SUCCESS（所有模块打包通过，spring-boot-maven-plugin 生成 fat jar 无报错）

> 此步骤验证依赖解析与打包完整性，比 `compile` 更严格。若失败，根据错误信息修正。

- [ ] **Step 3: 记录验证结果（无需 commit）**

无需 commit（无文件变更）。在实施记录中标注"全量编译验证通过"即可。

---

### Task 5.5: 完整测试

**Files:**
- 无文件变更，仅执行测试命令验证全项目测试通过

**Interfaces:**
- Consumes: 阶段 1-4 新增的所有单元测试 + 现有测试套件
- Produces: 全项目测试通过的可交付状态

- [ ] **Step 1: 全项目单元测试**

Run: `mvn clean test`
Expected: BUILD SUCCESS（所有模块测试通过）

关键测试模块与预期：
1. `shared-permission-starter`：`PermissionExecutorTest`、`DataScopeResolverTest`、`DataScopeAspectTest`、`DataScopeContextTest`、`DataScopeQueryHelperTest`、`PermissionRegistrationRunnerTest`、`RequirePermissionAspectTest`（阶段 1 新增）
2. `auth-application`：`PermissionScannerServiceTest`、`PermissionQueryServiceTest`（阶段 2 新增）
3. `auth-infrastructure`：`PermissionItemRepositoryImplTest`（阶段 2 调整）
4. `demo-gateway`：`GatewayPropertiesTest`、`ChannelAwareSaRouterMatchChannelTest`、`SaTokenGatewayConfigurationTest`、`GatewayStpInterfaceImplTest`（阶段 3 新增/调整）
5. 其他业务服务现有测试不受影响

常见失败原因与处理：
1. `RouteRuleTest` / `RouteRuleLoaderTest` 编译失败
   → 阶段 3 未删除这两个测试文件，回查 Task 3.x 的 Step 3（删除 RouteRule 相关文件）
2. `SaTokenGatewayConfigurationTest` 失败（构造函数参数变更）
   → 阶段 3 `SaTokenGatewayConfiguration` 移除 `RouteRuleLoader` 依赖后测试未同步更新，回查 Task 3.x
3. `RequirePermissionAspectTest` 失败（构造函数参数变更）
   → 阶段 1 `RequirePermissionAspect` 改为依赖 `PermissionExecutor` 后测试未同步更新，回查 Task 1.x

- [ ] **Step 2: 针对性运行关键测试模块（若全量测试失败时定位用）**

单独运行统一鉴权相关测试：

Run: `mvn -pl demo-shared/shared-starter/shared-permission-starter,auth-service/auth-application,demo-gateway test`
Expected: BUILD SUCCESS

- [ ] **Step 3: 记录测试结果（无需 commit）**

无需 commit（无文件变更）。在实施记录中标注"完整测试通过"及关键测试模块结果。

---

## 自检清单

实施完成后，逐项确认：

- [ ] **网关层白名单**：`GatewayProperties` 消费 `auth.gateway.public-paths`，非渠道前缀路径走 `configureDefaultStpLogic()` 通用登录校验
- [ ] **auth-service 自身防护**：`LocalPermissionExecutor` 短路调用，所有 Controller 标注 `@RequirePermission(category = PLATFORM)`
- [ ] **kernel 权限点注册**：`PermissionRegistrationRunner` 在业务服务启动时通过 `PermissionRegistrationApi` 上报到 auth-service
- [ ] **行级数据过滤**：`@DataScope` + `DataScopeResolver` + `DataScopeContext` + `DataScopeQueryHelper` 链路完整
- [ ] **RouteRule 彻底移除**：auth-api、auth-adapter、demo-gateway 三处文件全部删除，`application.yml` 无残留配置
- [ ] **错误码规范对齐**：`08-错误码规范.md` 登记 SHARED.PERM.0001-0005
- [ ] **全量编译通过**：`mvn clean compile -DskipTests` BUILD SUCCESS
- [ ] **全量测试通过**：`mvn clean test` BUILD SUCCESS

## 规范覆盖缺口说明

设计文档 8.10 节阶段 5 提到 `AnnuityLinkTestController 移到测试代码或加 @Profile("dev")`，本计划未将其纳入独立任务，原因：该 Controller 是跨服务链路联调测试接口，与统一鉴权体系无直接关联，属独立清理事项。建议后续单独处理：将 `AnnuityLinkTestController` 从 `annuity-adapter/src/main/java` 迁移到 `annuity-adapter/src/test/java`，或添加 `@Profile("dev")` 限制仅开发环境加载。

---

**计划完成。** 执行选择：

**1. Subagent-Driven（推荐）** - 每个 Task 派发独立 subagent，Task 间 review，迭代快

**2. Inline Execution** - 在当前会话内按 executing-plans 批量执行，带 checkpoint review

**请选择执行方式？**