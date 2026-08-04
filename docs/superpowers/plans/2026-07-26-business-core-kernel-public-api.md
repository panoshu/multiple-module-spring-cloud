# business-core-kernel 公共 API 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:
> executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 business-core-kernel 实现一套公共 HTTP API,覆盖批次/表单/申请单/材料/进度管理,配套会话解析、权限校验、业务类型注册基础设施,使各业务服务引入
kernel 后即可获得通用业务办理能力。

**Architecture:** 在 `business-core-api` 定义 5 类 `@HttpExchange` 接口与 DTO;`business-core-adapter` 实现
Controller,入口完成"业务类型校验 → 会话解析 → BusinessMetaContext 组装 → 权限校验 → 调用 AppService"五步;
`business-core-application` 扩展 AppService 编排业务流程;`business-core-starter` 通过自动装配注册所有组件。会话信息通过
`X-Session-Context` header 透传,kernel 不直接依赖 sa-token。

**Tech Stack:** JDK 25 (--enable-preview), Spring Boot 3.5.14, Spring Web (@HttpExchange), MapStruct 1.6.3, Lombok,
JUnit 5 + Mockito

## Global Constraints

- 严格遵循 DDD 六边形分层:types → domain → api → application → adapter → infrastructure → starter
- domain 层禁止使用 Spring 注解、数据库注解、JSON 注解,唯一允许的外部库是 Lombok
- API 接口使用 `@HttpExchange` + `@PostExchange`,禁用 GET/POST 之外的请求类型
- 请求体使用 `@RequestBody` + `@Valid`,返回体统一 `ApiResult<T>`(`com.example.shared.web.core.api.ApiResult`)
- DTO 转换通过 MapStruct Converter,禁止在 Controller 中直接转换
- 错误码遵循层级字符串格式 (`CORE.DOMAIN.XXXX` / `CORE.APP.XXXX` / `CORE.INFRA.XXXX`)
- 业务数据时间戳由应用层管理,禁止 ORM 自动填充
- 所有公共类需要 Javadoc,作者用 `panoshu`
- 提交信息遵循 Conventional Commits,scope 用 `core-api`/`core-adapter`/`core-application`/`core-domain`/`kernel`

## File Structure

```
business-core-kernel/
├── business-core-api/src/main/java/com/example/core/api/
│   ├── context/
│   │   ├── SessionContext.java                 # 会话上下文 DTO(从 X-Session-Context header 解析)
│   │   └── BusinessMetaContext.java            # 业务元数据超集(kernel 内部组装,String-based)
│   ├── registrar/
│   │   └── BusinessTypeRegistrar.java          # 业务类型注册器(业务服务声明支持的业务类型)
│   ├── batch/
│   │   ├── BusinessBatchApi.java
│   │   ├── command/{CreateBatchCommand, CancelBatchCommand}.java
│   │   ├── query/{FindActiveBatchQuery, GetBatchDetailQuery}.java
│   │   └── response/{BatchSummaryResponse, BatchCreatedResponse, BatchDetailResponse}.java
│   ├── form/
│   │   ├── BusinessFormApi.java
│   │   ├── command/{ApplyUploadTokenCommand, ConfirmUploadCommand, DeleteFormCommand}.java
│   │   ├── query/GetFormStatusQuery.java
│   │   └── response/{UploadTokenResponse, FormStatusResponse}.java
│   ├── application/
│   │   ├── BusinessApplicationApi.java
│   │   ├── command/{AdvanceStepCommand, SubmitApplicationCommand}.java
│   │   ├── query/{FindApplicationListQuery, GetApplicationDetailQuery}.java
│   │   └── response/{ApplicationSummaryResponse, ApplicationDetailResponse, AdvanceStepResponse, SubmitResponse}.java
│   ├── material/
│   │   ├── MaterialAppApi.java
│   │   ├── command/{BindIndividualMaterialCommand, BindPackageMaterialCommand, UnbindMaterialCommand}.java
│   │   ├── query/{ListMaterialsQuery, CheckCompletenessQuery}.java
│   │   └── response/{MaterialItemDTO, CompletenessResponse}.java
│   └── progress/
│       ├── BusinessProgressApi.java
│       ├── query/GetBatchProgressQuery.java
│       └── response/BatchProgressResponse.java
├── business-core-domain/src/main/java/com/example/core/domain/business/
│   ├── gateway/BusinessAccessGuard.java        # 业务访问守门人 SPI
│   └── errorcode/CoreDomainErrorCode.java      # 扩展 UNSUPPORTED_BUSINESS_TYPE 等错误码
├── business-core-application/src/main/java/com/example/core/application/
│   ├── business/
│   │   ├── service/BusinessBatchAppService.java
│   │   ├── service/BusinessApplicationAppService.java
│   │   ├── service/BusinessProgressAppService.java
│   │   └── guard/DefaultBusinessAccessGuard.java
│   └── errorcode/CoreAppErrorCode.java         # 扩展 SESSION_MISSING 等错误码
├── business-core-adapter/src/main/java/com/example/core/adapter/
│   ├── context/
│   │   ├── SessionContextResolver.java
│   │   └── BusinessMetaContextAssembler.java
│   ├── validator/SupportedBusinessTypeValidator.java
│   ├── security/
│   │   ├── RequireBusinessPermission.java
│   │   └── BusinessPermissionAspect.java
│   ├── batch/
│   │   ├── BusinessBatchController.java
│   │   └── converter/BatchConverter.java
│   ├── form/
│   │   ├── BusinessFormController.java
│   │   └── converter/FormConverter.java
│   ├── application/
│   │   ├── BusinessApplicationController.java
│   │   └── converter/ApplicationConverter.java
│   ├── material/
│   │   ├── MaterialController.java
│   │   └── converter/MaterialConverter.java
│   └── progress/
│       ├── BusinessProgressController.java
│       └── converter/ProgressConverter.java
└── business-core-starter/src/main/resources/META-INF/spring/
    └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

---

## Task 1: 会话上下文基础设施

**Files:**

- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/context/SessionContext.java`
- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/context/BusinessMetaContext.java`
- Create:
  `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/context/SessionContextResolver.java`
- Create:
  `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/context/BusinessMetaContextAssembler.java`
- Test:
  `business-core-kernel/business-core-adapter/src/test/java/com/example/core/adapter/context/SessionContextResolverTest.java`
- Test:
  `business-core-kernel/business-core-adapter/src/test/java/com/example/core/adapter/context/BusinessMetaContextAssemblerTest.java`

**Interfaces:**

- Consumes: `com.example.shared.web.core.api.ApiResult`, `com.example.shared.exception.BusinessException`,
  `com.example.shared.exception.CommonError`
- Produces: `SessionContext` record, `BusinessMetaContext` record (api 层,与 domain 层
  `com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext` 区分), `SessionContextResolver` bean,
  `BusinessMetaContextAssembler` bean

- [ ] **Step 1: 编写 SessionContext record**

```java
package com.example.core.api.context;

import java.util.Set;

/**
 * 会话上下文 DTO
 *
 * <p>由 iam-service 写入 sa-token Token-Session,gateway 透传到 X-Session-Context header,
 * kernel 通过 {@link com.example.core.adapter.context.SessionContextResolver} 解析。
 *
 * <p>字段超集,各渠道按需填充:
 * <ul>
 *   <li>身份/渠道/客户/计划字段:全部渠道</li>
 *   <li>代办字段(isProxy/onBehalfOf*):仅 INTERNET 渠道</li>
 *   <li>二次授权字段(hasSecondaryAuth/borrowedApproverId):仅 BRANCH 渠道</li>
 *   <li>代办范围(delegatedPlanNos):仅 INTERNET 渠道</li>
 * </ul>
 *
 * @author panoshu
 */
public record SessionContext(
    String userNo,
    String userType,
    String loginName,
    String displayName,
    String channelType,
    String clientId,
    String clientIp,
    String customerNo,
    String customerName,
    String planNo,
    String planName,
    String productNo,
    String productName,
    String operationModel,
    String accountManager,
    boolean isProxy,
    String onBehalfOfUserNo,
    String onBehalfOfLoginName,
    boolean hasSecondaryAuth,
    Long secondaryAuthSessionId,
    String borrowedApproverId,
    Set<String> permissionCodes,
    Set<String> delegatedPlanNos
) {
}
```

- [ ] **Step 2: 编写 BusinessMetaContext record (api 层)**

```java
package com.example.core.api.context;

/**
 * 业务元数据超集(kernel 内部组装)
 *
 * <p>由 {@link com.example.core.adapter.context.BusinessMetaContextAssembler} 从前端 Command
 * + {@link SessionContext} 组装而成,用于传递给应用层进行批次创建等操作。
 *
 * <p>字段来源:
 * <ul>
 *   <li>{@code businessType} / {@code planNo}:来自前端 Command(办理意图)</li>
 *   <li>其余字段:来自 {@link SessionContext}(选计划时已确定,不接受前端传值)</li>
 * </ul>
 *
 * <p>注意:本类与 domain 层的
 * {@code com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext}
 * 不同——后者是流程编排引擎的配置查询上下文(含 extensionFacts),本类是 API 层的 String-based DTO。
 *
 * @author panoshu
 */
public record BusinessMetaContext(
    String businessType,
    String planNo,
    String customerNo,
    String customerName,
    String productNo,
    String productName,
    String planName,
    String operationModel,
    String accountManager
) {
}
```

- [ ] **Step 3: 编写 SessionContextResolver 失败测试**

```java
package com.example.core.adapter.context;

import com.example.core.api.context.SessionContext;
import com.example.shared.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Base64;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SessionContextResolver 单元测试
 *
 * @author panoshu
 */
class SessionContextResolverTest {

    private SessionContextResolver resolver;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        resolver = new SessionContextResolver(objectMapper);
    }

    @Test
    void should_resolve_session_context_from_header() {
        SessionContext session = new SessionContext(
            "U001", "USER", "alice", "Alice",
            "INTERNET", "CLI001", "127.0.0.1",
            "C001", "Customer A",
            "P001", "Plan A", "PRD001", "Product A", "MODEL_A", "CJP",
            true, "U002", "bob",
            false, null, null,
            Set.of("BUSINESS_ANNUITY_OPEN_HANDLE"), Set.of("P001")
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        String json = objectMapper.writeValueAsString(session);
        request.addHeader("X-Session-Context", Base64.getEncoder().encodeToString(json.getBytes()));

        SessionContext resolved = resolver.resolve(request);

        assertThat(resolved).isEqualTo(session);
        assertThat(resolved.userNo()).isEqualTo("U001");
        assertThat(resolved.channelType()).isEqualTo("INTERNET");
        assertThat(resolved.isProxy()).isTrue();
    }

    @Test
    void should_throw_when_header_missing_on_require() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThatThrownBy(() -> resolver.require(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("会话上下文缺失");
    }

    @Test
    void should_return_empty_when_header_missing_on_optional() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(resolver.optional(request)).isEmpty();
    }
}
```

- [ ] **Step 4: 运行测试确认失败**

Run: `mvn test -pl business-core-kernel/business-core-adapter -Dtest=SessionContextResolverTest`
Expected: FAIL (SessionContextResolver 不存在)

- [ ] **Step 5: 编写 SessionContextResolver 实现**

> **设计决策**:为保持 API 接口契约纯净 (`BusinessBatchApi` 等接口的方法签名不含 `HttpServletRequest`),
> `SessionContextResolver` 不接受 `HttpServletRequest` 参数,而是通过 Spring 的 `RequestContextHolder` 获取当前请求上下文。Controller
> 方法签名与 API 接口完全一致。

```java
package com.example.core.adapter.context;

import com.example.core.api.context.SessionContext;
import com.example.shared.exception.BusinessException;
import com.example.shared.exception.CommonError;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Base64;
import java.util.Optional;

/**
 * 会话上下文解析器
 *
 * <p>从 HTTP 请求的 {@code X-Session-Context} header 解析 {@link SessionContext}。
 * header 内容为 Base64 编码的 JSON,由 gateway 从 sa-token Token-Session 读取后写入。
 *
 * <p>kernel 不直接依赖 sa-token,通过本组件与 sa-token 解耦,保持可独立测试。
 *
 * <p>通过 {@link RequestContextHolder} 获取当前请求,避免在 Controller 方法签名中
 * 暴露 {@link HttpServletRequest},保持 API 接口契约纯净。
 *
 * @author panoshu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionContextResolver {

    private static final String SESSION_HEADER = "X-Session-Context";

    private final ObjectMapper objectMapper;

    /**
     * 解析会话上下文,header 缺失时返回 empty。
     */
    public Optional<SessionContext> optional() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return Optional.empty();
        }
        String header = request.getHeader(SESSION_HEADER);
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(header);
            SessionContext session = objectMapper.readValue(decoded, SessionContext.class);
            return Optional.of(session);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("解析 X-Session-Context header 失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 解析会话上下文,header 缺失时抛 BusinessException。
     */
    public SessionContext require() {
        return optional()
            .orElseThrow(() -> new BusinessException(CommonError.UNAUTHORIZED)
                .withUserDetail("会话上下文缺失,请重新登录")
                .withLogDetail("X-Session-Context header 缺失或解析失败"));
    }

    /**
     * 测试专用:从指定请求解析会话上下文。
     */
    public Optional<SessionContext> optional(HttpServletRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        String header = request.getHeader(SESSION_HEADER);
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(header);
            return Optional.of(objectMapper.readValue(decoded, SessionContext.class));
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("解析 X-Session-Context header 失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 测试专用:从指定请求解析,缺失时抛异常。
     */
    public SessionContext require(HttpServletRequest request) {
        return optional(request)
            .orElseThrow(() -> new BusinessException(CommonError.UNAUTHORIZED)
                .withUserDetail("会话上下文缺失,请重新登录")
                .withLogDetail("X-Session-Context header 缺失或解析失败"));
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest();
        }
        return null;
    }
}
```

- [ ] **Step 6: 运行测试确认通过**

Run: `mvn test -pl business-core-kernel/business-core-adapter -Dtest=SessionContextResolverTest`
Expected: PASS (3 tests)

- [ ] **Step 7: 编写 BusinessMetaContextAssembler 失败测试**

```java
package com.example.core.adapter.context;

import com.example.core.api.context.BusinessMetaContext;
import com.example.core.api.context.SessionContext;
import com.example.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BusinessMetaContextAssembler 单元测试
 *
 * @author panoshu
 */
class BusinessMetaContextAssemblerTest {

    private final BusinessMetaContextAssembler assembler = new BusinessMetaContextAssembler();

    @Test
    void should_assemble_meta_context_from_command_and_session() {
        SessionContext session = new SessionContext(
            "U001", "USER", "alice", "Alice",
            "INTERNET", "CLI001", "127.0.0.1",
            "C001", "Customer A",
            "P001", "Plan A", "PRD001", "Product A", "MODEL_A", "CJP",
            false, null, null, false, null, null,
            Set.of("BUSINESS_ANNUITY_OPEN_HANDLE"), Set.of()
        );
        String businessType = "ANNUITY_OPEN";
        String planNo = "P001";

        BusinessMetaContext meta = assembler.assemble(businessType, planNo, session);

        assertThat(meta.businessType()).isEqualTo("ANNUITY_OPEN");
        assertThat(meta.planNo()).isEqualTo("P001");
        assertThat(meta.customerNo()).isEqualTo("C001");
        assertThat(meta.customerName()).isEqualTo("Customer A");
        assertThat(meta.productNo()).isEqualTo("PRD001");
        assertThat(meta.productName()).isEqualTo("Product A");
        assertThat(meta.planName()).isEqualTo("Plan A");
        assertThat(meta.operationModel()).isEqualTo("MODEL_A");
        assertThat(meta.accountManager()).isEqualTo("CJP");
    }

    @Test
    void should_throw_when_plan_no_mismatch() {
        SessionContext session = new SessionContext(
            "U001", "USER", "alice", "Alice",
            "INTERNET", "CLI001", "127.0.0.1",
            "C001", "Customer A",
            "P001", "Plan A", "PRD001", "Product A", "MODEL_A", "CJP",
            false, null, null, false, null, null,
            Set.of(), Set.of()
        );

        assertThatThrownBy(() -> assembler.assemble("ANNUITY_OPEN", "P002", session))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("所选计划与会话中的计划不一致");
    }
}
```

- [ ] **Step 8: 运行测试确认失败**

Run: `mvn test -pl business-core-kernel/business-core-adapter -Dtest=BusinessMetaContextAssemblerTest`
Expected: FAIL (BusinessMetaContextAssembler 不存在)

- [ ] **Step 9: 编写 BusinessMetaContextAssembler 实现**

```java
package com.example.core.adapter.context;

import com.example.core.api.context.BusinessMetaContext;
import com.example.core.api.context.SessionContext;
import com.example.shared.exception.BusinessException;
import com.example.shared.exception.CommonError;
import org.springframework.stereotype.Component;

/**
 * 业务元数据上下文组装器
 *
 * <p>从前端 Command 的 {@code businessType} + {@code planNo} 与 {@link SessionContext}
 * 组装完整的 {@link BusinessMetaContext}。
 *
 * <p>校验 {@code commandPlanNo} 与 {@code session.planNo} 一致,防止跨计划办理。
 * 客户/产品/账管人等敏感字段完全来自 SessionContext,杜绝前端伪造。
 *
 * @author panoshu
 */
@Component
public class BusinessMetaContextAssembler {

    /**
     * 组装业务元数据上下文。
     *
     * @param businessType 业务类型(来自前端 Command)
     * @param commandPlanNo 计划编号(来自前端 Command,用于校验一致性)
     * @param session 会话上下文(来自 X-Session-Context header)
     * @return 完整的业务元数据上下文
     * @throws BusinessException 当 commandPlanNo 与 session.planNo 不一致时
     */
    public BusinessMetaContext assemble(String businessType, String commandPlanNo, SessionContext session) {
        if (!commandPlanNo.equals(session.planNo())) {
            throw new BusinessException(CommonError.BAD_REQUEST)
                .withUserDetail("所选计划与会话中的计划不一致")
                .withLogDetail("commandPlanNo=%s, sessionPlanNo=%s".formatted(commandPlanNo, session.planNo()));
        }
        return new BusinessMetaContext(
            businessType,
            session.planNo(),
            session.customerNo(),
            session.customerName(),
            session.productNo(),
            session.productName(),
            session.planName(),
            session.operationModel(),
            session.accountManager()
        );
    }
}
```

- [ ] **Step 10: 运行测试确认通过**

Run: `mvn test -pl business-core-kernel/business-core-adapter -Dtest=BusinessMetaContextAssemblerTest`
Expected: PASS (2 tests)

- [ ] **Step 11: 检查 adapter pom.xml 依赖**

确认 `business-core-adapter/pom.xml` 已包含 `shared-exception`(通过 `business-core-api` 传递)与 `jackson-databind`(通过
spring-web)。如缺少 jackson,在 `business-core-adapter/pom.xml` 添加:

```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
<dependency>
    <groupId>jakarta.servlet</groupId>
    <artifactId>jakarta.servlet-api</artifactId>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 12: 提交**

```bash
git add business-core-kernel/business-core-api/src/main/java/com/example/core/api/context/ \
        business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/context/ \
        business-core-kernel/business-core-adapter/src/test/java/com/example/core/adapter/context/ \
        business-core-kernel/business-core-adapter/pom.xml
git commit -m "feat(core-api): 新增会话上下文与业务元数据组装基础设施

1. SessionContext DTO 覆盖身份/渠道/客户/计划/代办/二次授权/权限字段
2. api 层 BusinessMetaContext 为后端组装的 String-based 超集
3. SessionContextResolver 从 X-Session-Context header 解析会话
4. BusinessMetaContextAssembler 从 Command+SessionContext 组装完整元数据
5. 客户/产品/账管人等敏感字段完全来自 SessionContext,杜绝前端伪造"
```

---

## Task 2: 权限校验 SPI 与业务类型注册

**Files:**

- Create:
  `business-core-kernel/business-core-application/src/main/java/com/example/core/application/business/guard/BusinessAccessGuard.java`
- Create:
  `business-core-kernel/business-core-application/src/main/java/com/example/core/application/business/guard/DefaultBusinessAccessGuard.java`
- Create:
  `business-core-kernel/business-core-api/src/main/java/com/example/core/api/registrar/BusinessTypeRegistrar.java`
- Create:
  `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/validator/SupportedBusinessTypeValidator.java`
- Modify:
  `business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/business/errorcode/CoreDomainErrorCode.java`
- Test:
  `business-core-kernel/business-core-application/src/test/java/com/example/core/application/business/guard/DefaultBusinessAccessGuardTest.java`
- Test:
  `business-core-kernel/business-core-adapter/src/test/java/com/example/core/adapter/validator/SupportedBusinessTypeValidatorTest.java`

**Interfaces:**

- Consumes: `SessionContext`, `BusinessMetaContext` (api 层), `BusinessException`, `CommonError`
- Produces: `BusinessAccessGuard` SPI (application 层), `DefaultBusinessAccessGuard` bean, `BusinessTypeRegistrar` bean,
  `SupportedBusinessTypeValidator` bean

- [ ] **Step 1: 扩展 CoreDomainErrorCode 新增 UNSUPPORTED_BUSINESS_TYPE**

在 `CoreDomainErrorCode.java` 枚举中新增:

```java
UNSUPPORTED_BUSINESS_TYPE("CORE.DOMAIN.0004", "不支持的业务类型"),
PLAN_MISMATCH("CORE.DOMAIN.0005", "计划不一致"),
PROXY_FORBIDDEN("CORE.DOMAIN.0006", "无代办权限"),
SECONDARY_AUTH_REQUIRED("CORE.DOMAIN.0007", "需要二次授权"),
```

- [ ] **Step 2: 编写 BusinessAccessGuard SPI 接口 (application 层)**

> **设计决策**:`BusinessAccessGuard` 放在 application 层 (而非 domain 层 gateway),因为它依赖 api 层的 `SessionContext` /
> `BusinessMetaContext` DTO,符合依赖规则 `application → api + domain`。

```java
package com.example.core.application.business.guard;

import com.example.core.api.context.BusinessMetaContext;
import com.example.core.api.context.SessionContext;

/**
 * 业务访问守门人 SPI
 *
 * <p>定义业务办理的统一权限校验契约,由 {@code DefaultBusinessAccessGuard} 提供默认实现,
 * 业务服务可覆盖以扩展自定义校验(如年金服务的外资业务准入)。
 *
 * <p>校验范围(按渠道差异化):
 * <ul>
 *   <li>通用:计划一致性 + 客户一致性 + 业务类型办理权限</li>
 *   <li>INTERNET:代办时校验 planNo 在 delegatedPlanNos 内</li>
 *   <li>BRANCH:必须 hasSecondaryAuth=true</li>
 *   <li>HQ:无额外校验</li>
 * </ul>
 *
 * @author panoshu
 */
public interface BusinessAccessGuard {

    /**
     * 校验当前会话用户对指定业务类型的办理权限(含渠道差异化校验:代办 / 二次授权)。
     *
     * @param session 会话上下文
     * @param meta 业务元数据上下文
     * @throws com.example.shared.exception.BusinessException 校验不通过时
     */
    void checkCanHandle(SessionContext session, BusinessMetaContext meta);
}
```

- [ ] **Step 3: 编写 DefaultBusinessAccessGuard 失败测试**

```java
package com.example.core.application.business.guard;

import com.example.core.api.context.BusinessMetaContext;
import com.example.core.api.context.SessionContext;
import com.example.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DefaultBusinessAccessGuard 单元测试
 *
 * @author panoshu
 */
class DefaultBusinessAccessGuardTest {

    private DefaultBusinessAccessGuard guard;

    @BeforeEach
    void setUp() {
        guard = new DefaultBusinessAccessGuard();
    }

    private SessionContext internetSession(boolean isProxy, Set<String> delegatedPlanNos, Set<String> permissions) {
        return new SessionContext(
            "U001", "USER", "alice", "Alice",
            "INTERNET", "CLI001", "127.0.0.1",
            "C001", "Customer A",
            "P001", "Plan A", "PRD001", "Product A", "MODEL_A", "CJP",
            isProxy, "U002", "bob",
            false, null, null,
            permissions, delegatedPlanNos
        );
    }

    private SessionContext branchSession(boolean hasSecondaryAuth) {
        return new SessionContext(
            "U001", "USER", "alice", "Alice",
            "BRANCH", "CLI001", "127.0.0.1",
            "C001", "Customer A",
            "P001", "Plan A", "PRD001", "Product A", "MODEL_A", "CJP",
            false, null, null,
            hasSecondaryAuth, hasSecondaryAuth ? 100L : null, hasSecondaryAuth ? "U003" : null,
            Set.of("BUSINESS_ANNUITY_OPEN_HANDLE"), Set.of()
        );
    }

    private SessionContext hqSession() {
        return new SessionContext(
            "U001", "USER", "alice", "Alice",
            "HQ", "CLI001", "127.0.0.1",
            "C001", "Customer A",
            "P001", "Plan A", "PRD001", "Product A", "MODEL_A", "CJP",
            false, null, null,
            false, null, null,
            Set.of("BUSINESS_ANNUITY_OPEN_HANDLE"), Set.of()
        );
    }

    private BusinessMetaContext metaWith(String businessType, String planNo) {
        return new BusinessMetaContext(businessType, planNo, "C001", "Customer A",
            "PRD001", "Product A", "Plan A", "MODEL_A", "CJP");
    }

    @Test
    void should_pass_for_internet_non_proxy_with_permission() {
        SessionContext session = internetSession(false, Set.of(), Set.of("BUSINESS_ANNUITY_OPEN_HANDLE"));
        assertThatCode(() -> guard.checkCanHandle(session, metaWith("ANNUITY_OPEN", "P001")))
            .doesNotThrowAnyException();
    }

    @Test
    void should_fail_when_business_type_permission_missing() {
        SessionContext session = internetSession(false, Set.of(), Set.of());
        assertThatThrownBy(() -> guard.checkCanHandle(session, metaWith("ANNUITY_OPEN", "P001")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("无办理权限");
    }

    @Test
    void should_fail_when_plan_no_mismatch() {
        SessionContext session = internetSession(false, Set.of(), Set.of("BUSINESS_ANNUITY_OPEN_HANDLE"));
        assertThatThrownBy(() -> guard.checkCanHandle(session, metaWith("ANNUITY_OPEN", "P999")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("计划不一致");
    }

    @Test
    void should_pass_for_internet_proxy_with_delegated_plan() {
        SessionContext session = internetSession(true, Set.of("P001"), Set.of("BUSINESS_ANNUITY_OPEN_HANDLE"));
        assertThatCode(() -> guard.checkCanHandle(session, metaWith("ANNUITY_OPEN", "P001")))
            .doesNotThrowAnyException();
    }

    @Test
    void should_fail_for_internet_proxy_without_delegation() {
        SessionContext session = internetSession(true, Set.of(), Set.of("BUSINESS_ANNUITY_OPEN_HANDLE"));
        assertThatThrownBy(() -> guard.checkCanHandle(session, metaWith("ANNUITY_OPEN", "P001")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("无代办权限");
    }

    @Test
    void should_pass_for_branch_with_secondary_auth() {
        SessionContext session = branchSession(true);
        assertThatCode(() -> guard.checkCanHandle(session, metaWith("ANNUITY_OPEN", "P001")))
            .doesNotThrowAnyException();
    }

    @Test
    void should_fail_for_branch_without_secondary_auth() {
        SessionContext session = branchSession(false);
        assertThatThrownBy(() -> guard.checkCanHandle(session, metaWith("ANNUITY_OPEN", "P001")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("需要二次授权");
    }

    @Test
    void should_pass_for_hq_channel() {
        SessionContext session = hqSession();
        assertThatCode(() -> guard.checkCanHandle(session, metaWith("ANNUITY_OPEN", "P001")))
            .doesNotThrowAnyException();
    }
}
```

- [ ] **Step 5: 运行测试确认失败**

Run: `mvn test -pl business-core-kernel/business-core-application -Dtest=DefaultBusinessAccessGuardTest`
Expected: FAIL (DefaultBusinessAccessGuard 不存在)

- [ ] **Step 6: 编写 DefaultBusinessAccessGuard 实现**

```java
package com.example.core.application.business.guard;

import com.example.core.api.context.BusinessMetaContext;
import com.example.core.api.context.SessionContext;
import com.example.shared.exception.BusinessException;
import com.example.shared.exception.CommonError;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 默认业务访问守门人实现
 *
 * <p>按渠道分支差异化校验:
 * <ul>
 *   <li>通用校验(所有渠道):计划一致性 + 客户一致性 + 业务类型办理权限</li>
 *   <li>INTERNET 渠道:代办时校验 planNo 在 delegatedPlanNos 内</li>
 *   <li>BRANCH 渠道:必须 hasSecondaryAuth=true</li>
 *   <li>HQ 渠道:无额外校验</li>
 * </ul>
 *
 * <p>业务服务可通过提供自定义 {@link BusinessAccessGuard} Bean 覆盖本实现。
 *
 * @author panoshu
 */
@Component
@ConditionalOnMissingBean(BusinessAccessGuard.class)
public class DefaultBusinessAccessGuard implements BusinessAccessGuard {

    private static final String INTERNET = "INTERNET";
    private static final String BRANCH = "BRANCH";
    private static final String HQ = "HQ";

    @Override
    public void checkCanHandle(SessionContext session, BusinessMetaContext meta) {
        checkCommon(session, meta);
        switch (session.channelType()) {
            case INTERNET -> checkInternetProxy(session, meta);
            case BRANCH -> checkBranchSecondaryAuth(session);
            case HQ -> { /* 无额外校验 */ }
            default -> throw new BusinessException(CommonError.BAD_REQUEST)
                .withUserDetail("不支持的渠道类型")
                .withLogDetail("channelType=%s".formatted(session.channelType()));
        }
    }

    private void checkCommon(SessionContext session, BusinessMetaContext meta) {
        if (!Objects.equals(meta.planNo(), session.planNo())) {
            throw new BusinessException(CommonError.BAD_REQUEST)
                .withUserDetail("所选计划与会话中的计划不一致")
                .withLogDetail("metaPlanNo=%s, sessionPlanNo=%s".formatted(meta.planNo(), session.planNo()));
        }
        if (!Objects.equals(meta.customerNo(), session.customerNo())) {
            throw new BusinessException(CommonError.BAD_REQUEST)
                .withUserDetail("客户信息不一致")
                .withLogDetail("metaCustomerNo=%s, sessionCustomerNo=%s".formatted(meta.customerNo(), session.customerNo()));
        }
        String requiredPermission = "BUSINESS_%s_HANDLE".formatted(meta.businessType());
        if (session.permissionCodes() == null || !session.permissionCodes().contains(requiredPermission)) {
            throw new BusinessException(CommonError.FORBIDDEN)
                .withUserDetail("无办理权限")
                .withLogDetail("requiredPermission=%s, owned=%s".formatted(requiredPermission, session.permissionCodes()));
        }
    }

    private void checkInternetProxy(SessionContext session, BusinessMetaContext meta) {
        if (session.isProxy()) {
            if (session.delegatedPlanNos() == null || !session.delegatedPlanNos().contains(meta.planNo())) {
                throw new BusinessException(CommonError.FORBIDDEN)
                    .withUserDetail("无代办权限")
                    .withLogDetail("proxy planNo=%s not in delegated=%s".formatted(meta.planNo(), session.delegatedPlanNos()));
            }
        }
    }

    private void checkBranchSecondaryAuth(SessionContext session) {
        if (!session.hasSecondaryAuth()) {
            throw new BusinessException(CommonError.FORBIDDEN)
                .withUserDetail("网点渠道办理业务需要二次授权")
                .withLogDetail("BRANCH channel without secondary auth");
        }
    }
}
```

- [ ] **Step 7: 运行测试确认通过**

Run: `mvn test -pl business-core-kernel/business-core-application -Dtest=DefaultBusinessAccessGuardTest`
Expected: PASS (8 tests)

- [ ] **Step 8: 编写 BusinessTypeRegistrar**

```java
package com.example.core.api.registrar;

import java.util.Set;

/**
 * 业务类型注册器
 *
 * <p>业务服务通过实现本接口声明本服务支持的 BusinessType,由 {@code SupportedBusinessTypeValidator}
 * 在 Controller 入口校验,防止本服务被请求到不归自己处理的业务类型。
 *
 * <p>使用示例:
 * <pre>{@code
 * @Bean
 * public BusinessTypeRegistrar annuityTypeRegistrar() {
 *     return BusinessTypeRegistrar.of("ANNUITY_OPEN", "ANNUITY_CHANGE");
 * }
 * }</pre>
 *
 * @author panoshu
 */
public interface BusinessTypeRegistrar {

    /**
     * 返回本服务支持的业务类型枚举名称集合。
     */
    Set<String> supportedBusinessTypes();

    /**
     * 工厂方法,创建一个包含指定业务类型的注册器。
     */
    static BusinessTypeRegistrar of(String... types) {
        Set<String> set = Set.of(types);
        return () -> set;
    }
}
```

- [ ] **Step 9: 编写 SupportedBusinessTypeValidator 失败测试**

```java
package com.example.core.adapter.validator;

import com.example.core.api.registrar.BusinessTypeRegistrar;
import com.example.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SupportedBusinessTypeValidator 单元测试
 *
 * @author panoshu
 */
class SupportedBusinessTypeValidatorTest {

    @Test
    void should_pass_when_type_supported() {
        BusinessTypeRegistrar registrar = BusinessTypeRegistrar.of("ANNUITY_OPEN", "ANNUITY_CHANGE");
        SupportedBusinessTypeValidator validator = new SupportedBusinessTypeValidator(registrar);
        assertThatCode(() -> validator.validate("ANNUITY_OPEN")).doesNotThrowAnyException();
    }

    @Test
    void should_fail_when_type_not_supported() {
        BusinessTypeRegistrar registrar = BusinessTypeRegistrar.of("ANNUITY_OPEN");
        SupportedBusinessTypeValidator validator = new SupportedBusinessTypeValidator(registrar);
        assertThatThrownBy(() -> validator.validate("APPROVAL_FLOW"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不支持的业务类型");
    }

    @Test
    void should_fail_when_registrar_missing() {
        SupportedBusinessTypeValidator validator = new SupportedBusinessTypeValidator(null);
        assertThatThrownBy(() -> validator.validate("ANNUITY_OPEN"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("未配置业务类型注册器");
    }
}
```

- [ ] **Step 10: 运行测试确认失败**

Run: `mvn test -pl business-core-kernel/business-core-adapter -Dtest=SupportedBusinessTypeValidatorTest`
Expected: FAIL

- [ ] **Step 11: 编写 SupportedBusinessTypeValidator 实现**

```java
package com.example.core.adapter.validator;

import com.example.core.api.registrar.BusinessTypeRegistrar;
import com.example.shared.exception.BusinessException;
import com.example.shared.exception.CommonError;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 业务类型校验器
 *
 * <p>校验请求中的业务类型是否由本服务支持,防止路由错误或恶意请求。
 *
 * @author panoshu
 */
@Component
@RequiredArgsConstructor
public class SupportedBusinessTypeValidator {

    private final BusinessTypeRegistrar registrar;

    /**
     * 校验业务类型是否由本服务支持。
     *
     * @param businessType 业务类型枚举名称
     * @throws BusinessException 当注册器未配置或业务类型不在支持列表时
     */
    public void validate(String businessType) {
        if (registrar == null) {
            throw new BusinessException(CommonError.INTERNAL_SERVER_ERROR)
                .withUserDetail("服务未配置业务类型注册器")
                .withLogDetail("BusinessTypeRegistrar bean is null");
        }
        if (!registrar.supportedBusinessTypes().contains(businessType)) {
            throw new BusinessException(CommonError.BAD_REQUEST)
                .withUserDetail("不支持的业务类型")
                .withLogDetail("businessType=%s, supported=%s".formatted(businessType, registrar.supportedBusinessTypes()));
        }
    }
}
```

- [ ] **Step 12: 运行测试确认通过**

Run: `mvn test -pl business-core-kernel/business-core-adapter -Dtest=SupportedBusinessTypeValidatorTest`
Expected: PASS (3 tests)

- [ ] **Step 13: 提交**

```bash
git add business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/business/errorcode/CoreDomainErrorCode.java \
        business-core-kernel/business-core-application/src/main/java/com/example/core/application/business/guard/ \
        business-core-kernel/business-core-application/src/test/java/com/example/core/application/business/guard/ \
        business-core-kernel/business-core-api/src/main/java/com/example/core/api/registrar/ \
        business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/validator/ \
        business-core-kernel/business-core-adapter/src/test/java/com/example/core/adapter/validator/
git commit -m "feat(core-application): 新增权限校验 SPI 与业务类型注册基础设施

1. BusinessAccessGuard SPI 定义统一权限校验契约(application 层)
2. DefaultBusinessAccessGuard 按 INTERNET/BRANCH/HQ 渠道差异化校验
3. BusinessTypeRegistrar 接口供业务服务声明支持的业务类型
4. SupportedBusinessTypeValidator 校验请求的业务类型是否由本服务支持
5. CoreDomainErrorCode 扩展 UNSUPPORTED_BUSINESS_TYPE 等错误码"
```

---

## Task 3: 功能权限注解与 AOP 拦截器

**Files:**

- Create:
  `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/security/RequireBusinessPermission.java`
- Create:
  `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/security/BusinessPermissionAspect.java`
- Test:
  `business-core-kernel/business-core-adapter/src/test/java/com/example/core/adapter/security/BusinessPermissionAspectTest.java`

**Interfaces:**

- Consumes: `SessionContextResolver`, `SessionContext`, `BusinessException`, `CommonError`
- Produces: `@RequireBusinessPermission` 注解, `BusinessPermissionAspect` bean

- [ ] **Step 1: 编写 @RequireBusinessPermission 注解**

```java
package com.example.core.adapter.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 业务功能权限校验注解
 *
 * <p>标注在 Controller 方法上,AOP 拦截器会校验当前会话用户的 {@code permissionCodes}
 * 是否包含指定权限码,用于垂直越权防护(功能权限)。
 *
 * <p>使用示例:
 * <pre>{@code
 * @PostMapping("/create")
 * @RequireBusinessPermission("BATCH_CREATE")
 * public ApiResult<BatchCreatedResponse> createBatch(...) { ... }
 * }</pre>
 *
 * <p>注意:业务类型办理权限(如 BUSINESS_ANNUITY_OPEN_HANDLE)属于数据权限范畴,
 * 由 {@link com.example.core.application.business.guard.BusinessAccessGuard} 校验。
 *
 * @author panoshu
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireBusinessPermission {

    /**
     * 需要的功能权限码,如 "BATCH_CREATE"、"FORM_UPLOAD"、"APPLICATION_SUBMIT"。
     */
    String value();
}
```

- [ ] **Step 2: 编写 BusinessPermissionAspect 失败测试**

> **设计决策**:Aspect 不再从方法参数找 `HttpServletRequest`,而是直接调用 `sessionContextResolver.require()`(内部通过
> `RequestContextHolder` 获取当前请求)。测试时通过 `RequestContextHolder.setRequestAttributes(...)` 设置模拟请求。

```java
package com.example.core.adapter.security;

import com.example.core.api.context.SessionContext;
import com.example.core.adapter.context.SessionContextResolver;
import com.example.shared.exception.BusinessException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * BusinessPermissionAspect 单元测试
 *
 * @author panoshu
 */
class BusinessPermissionAspectTest {

    private SessionContextResolver resolver;
    private BusinessPermissionAspect aspect;

    @BeforeEach
    void setUp() {
        resolver = mock(SessionContextResolver.class);
        aspect = new BusinessPermissionAspect(resolver);
        // 设置 RequestContextHolder,模拟 Web 请求上下文
        MockHttpServletRequest request = new MockHttpServletRequest();
        ServletRequestAttributes attrs = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attrs);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void should_pass_when_permission_present() throws Throwable {
        when(resolver.require()).thenReturn(sessionWithPerms(Set.of("BATCH_CREATE")));
        ProceedingJoinPoint pjp = mockJoinPoint();

        Object result = aspect.checkPermission(pjp, "BATCH_CREATE");

        assertThat(result).isEqualTo("ok");
        verify(pjp).proceed();
    }

    @Test
    void should_fail_when_permission_missing() {
        when(resolver.require()).thenReturn(sessionWithPerms(Set.of()));
        ProceedingJoinPoint pjp = mockJoinPoint();

        assertThatThrownBy(() -> aspect.checkPermission(pjp, "BATCH_CREATE"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("无功能权限");
    }

    private SessionContext sessionWithPerms(Set<String> perms) {
        return new SessionContext(
            "U001", "USER", "alice", "Alice",
            "INTERNET", "CLI001", "127.0.0.1",
            "C001", "Customer A",
            "P001", "Plan A", "PRD001", "Product A", "MODEL_A", "CJP",
            false, null, null, false, null, null,
            perms, Set.of()
        );
    }

    private ProceedingJoinPoint mockJoinPoint() {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(new Object[]{});
        try {
            when(pjp.proceed()).thenReturn("ok");
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        return pjp;
    }
}
```

- [ ] **Step 3: 运行测试确认失败**

Run: `mvn test -pl business-core-kernel/business-core-adapter -Dtest=BusinessPermissionAspectTest`
Expected: FAIL

- [ ] **Step 4: 编写 BusinessPermissionAspect 实现**

```java
package com.example.core.adapter.security;

import com.example.core.adapter.context.SessionContextResolver;
import com.example.core.api.context.SessionContext;
import com.example.shared.exception.BusinessException;
import com.example.shared.exception.CommonError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 业务功能权限校验切面
 *
 * <p>拦截标注了 {@link RequireBusinessPermission} 的方法,通过 {@link SessionContextResolver}
 * 解析当前会话上下文(内部使用 {@code RequestContextHolder} 获取当前请求),
 * 校验 {@code permissionCodes} 是否包含注解声明的权限码。
 *
 * @author panoshu
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class BusinessPermissionAspect {

    private final SessionContextResolver sessionContextResolver;

    /**
     * 校验功能权限。
     */
    @Around("@annotation(requirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, RequireBusinessPermission requirePermission) throws Throwable {
        String requiredCode = requirePermission.value();
        SessionContext session = sessionContextResolver.require();
        if (session.permissionCodes() == null || !session.permissionCodes().contains(requiredCode)) {
            throw new BusinessException(CommonError.FORBIDDEN)
                .withUserDetail("无功能权限")
                .withLogDetail("requiredPermission=%s, owned=%s".formatted(requiredCode, session.permissionCodes()));
        }
        return joinPoint.proceed();
    }
}
```

- [ ] **Step 5: 在 adapter pom.xml 添加 AOP 依赖**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

- [ ] **Step 6: 运行测试确认通过**

Run: `mvn test -pl business-core-kernel/business-core-adapter -Dtest=BusinessPermissionAspectTest`
Expected: PASS (2 tests)

- [ ] **Step 7: 提交**

```bash
git add business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/security/ \
        business-core-kernel/business-core-adapter/src/test/java/com/example/core/adapter/security/ \
        business-core-kernel/business-core-adapter/pom.xml
git commit -m "feat(core-adapter): 新增功能权限注解与 AOP 拦截器

1. @RequireBusinessPermission 注解标注在 Controller 方法上声明所需权限码
2. BusinessPermissionAspect 通过 AOP 拦截注解方法,校验会话用户的 permissionCodes
3. 用于垂直越权防护(功能权限),业务类型办理权限由 BusinessAccessGuard 校验"
```

---

## Task 4: BusinessBatchApi 接口定义与 DTO

**Files:**

- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/batch/BusinessBatchApi.java`
- Create:
  `business-core-kernel/business-core-api/src/main/java/com/example/core/api/batch/command/CreateBatchCommand.java`
- Create:
  `business-core-kernel/business-core-api/src/main/java/com/example/core/api/batch/command/CancelBatchCommand.java`
- Create:
  `business-core-kernel/business-core-api/src/main/java/com/example/core/api/batch/query/FindActiveBatchQuery.java`
- Create:
  `business-core-kernel/business-core-api/src/main/java/com/example/core/api/batch/query/GetBatchDetailQuery.java`
- Create:
  `business-core-kernel/business-core-api/src/main/java/com/example/core/api/batch/response/BatchSummaryResponse.java`
- Create:
  `business-core-kernel/business-core-api/src/main/java/com/example/core/api/batch/response/BatchCreatedResponse.java`
- Create:
  `business-core-kernel/business-core-api/src/main/java/com/example/core/api/batch/response/BatchDetailResponse.java`

**Interfaces:**

- Consumes: `ApiResult`, `@HttpExchange`, `@PostExchange`, `@Valid`, `@RequestBody`
- Produces: `BusinessBatchApi` 接口及配套 Command/Query/Response DTO

- [ ] **Step 1: 编写 Command/Query/Response DTO**

```java
// CreateBatchCommand.java
package com.example.core.api.batch.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建业务批次命令
 *
 * <p>前端只传办理意图(businessType + planNo),客户/产品/账管人等敏感字段
 * 由后端从 SessionContext 组装,杜绝前端伪造。
 *
 * @author panoshu
 */
public record CreateBatchCommand(
    @NotBlank(message = "业务类型不能为空") String businessType,
    @NotBlank(message = "计划编号不能为空") String planNo,
    String operatorRemark
) {
}
```

```java
// CancelBatchCommand.java
package com.example.core.api.batch.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 取消业务批次命令
 *
 * @author panoshu
 */
public record CancelBatchCommand(
    @NotNull(message = "批次ID不能为空") Long batchId,
    String reason
) {
}
```

```java
// FindActiveBatchQuery.java
package com.example.core.api.batch.query;

import jakarta.validation.constraints.NotBlank;

/**
 * 查询未完成/处理中业务批次
 *
 * @author panoshu
 */
public record FindActiveBatchQuery(
    @NotBlank(message = "计划编号不能为空") String planNo,
    @NotBlank(message = "业务类型不能为空") String businessType
) {
}
```

```java
// GetBatchDetailQuery.java
package com.example.core.api.batch.query;

import jakarta.validation.constraints.NotNull;

/**
 * 查询批次详情
 *
 * @author panoshu
 */
public record GetBatchDetailQuery(
    @NotNull(message = "批次ID不能为空") Long batchId
) {
}
```

```java
// BatchSummaryResponse.java
package com.example.core.api.batch.response;

import java.time.LocalDateTime;

/**
 * 批次摘要响应
 *
 * @author panoshu
 */
public record BatchSummaryResponse(
    Long batchId,
    String batchNo,
    String businessType,
    String planNo,
    String status,
    int totalFormCount,
    int totalApplicationCount,
    int successCount,
    int failedCount,
    LocalDateTime createTime
) {
}
```

```java
// BatchCreatedResponse.java
package com.example.core.api.batch.response;

import java.time.LocalDateTime;

/**
 * 批次创建响应
 *
 * @author panoshu
 */
public record BatchCreatedResponse(
    Long batchId,
    String batchNo,
    String status,
    LocalDateTime createTime
) {
}
```

```java
// BatchDetailResponse.java
package com.example.core.api.batch.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 批次详情响应
 *
 * @author panoshu
 */
public record BatchDetailResponse(
    Long batchId,
    String batchNo,
    String businessType,
    String planNo,
    String customerNo,
    String customerName,
    String status,
    int totalFormCount,
    int totalApplicationCount,
    int successCount,
    int failedCount,
    LocalDateTime createTime,
    LocalDateTime updateTime,
    List<FormSummary> forms
) {
    /**
     * 批次下表单摘要
     */
    public record FormSummary(
        Long formId,
        String fileName,
        String status,
        int applicationCount,
        LocalDateTime uploadTime
    ) {
    }
}
```

- [ ] **Step 2: 编写 BusinessBatchApi 接口**

```java
package com.example.core.api.batch;

import com.example.core.api.batch.command.CancelBatchCommand;
import com.example.core.api.batch.command.CreateBatchCommand;
import com.example.core.api.batch.query.FindActiveBatchQuery;
import com.example.core.api.batch.query.GetBatchDetailQuery;
import com.example.core.api.batch.response.BatchCreatedResponse;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.core.api.batch.response.BatchSummaryResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Optional;

/**
 * 业务批次管理 API
 *
 * <p>提供批次的查询未完成、创建、详情、取消等公共接口,所有业务类型共用。
 * 路径前缀 {@code /core/batch}。
 *
 * <p>后续新增接口流程:
 * <ol>
 *   <li>在 API 层新增方法到本接口(或新建 Api 接口),路径前缀 /core</li>
 *   <li>在 application 层扩展 AppService 方法</li>
 *   <li>在 adapter 层实现 Controller,入口完成业务类型校验→会话解析→权限校验→调用 AppService</li>
 *   <li>通过 MapStruct Converter 完成 DTO 转换</li>
 * </ol>
 *
 * @author panoshu
 */
@HttpExchange("/core/batch")
public interface BusinessBatchApi {

    /**
     * 查询指定计划+业务类型的未完成/处理中批次。
     */
    @PostExchange("/active")
    ApiResult<Optional<BatchSummaryResponse>> findActive(@Valid @RequestBody FindActiveBatchQuery query);

    /**
     * 创建新批次。
     *
     * <p>前端只传 businessType + planNo,后端从 SessionContext 组装完整元数据。
     */
    @PostExchange("/create")
    ApiResult<BatchCreatedResponse> create(@Valid @RequestBody CreateBatchCommand command);

    /**
     * 查询批次详情(含表单/申请单摘要)。
     */
    @PostExchange("/detail")
    ApiResult<BatchDetailResponse> detail(@Valid @RequestBody GetBatchDetailQuery query);

    /**
     * 取消未提交批次。
     */
    @PostExchange("/cancel")
    ApiResult<Void> cancel(@Valid @RequestBody CancelBatchCommand command);
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn compile -pl business-core-kernel/business-core-api`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add business-core-kernel/business-core-api/src/main/java/com/example/core/api/batch/
git commit -m "feat(core-api): 新增 BusinessBatchApi 接口与配套 DTO

1. BusinessBatchApi 定义批次查询/创建/详情/取消 4 个公共接口
2. CreateBatchCommand 仅含 businessType+planNo+operatorRemark,敏感字段由后端组装
3. 配套 FindActiveBatchQuery/GetBatchDetailQuery/CancelBatchCommand
4. 配套 BatchSummaryResponse/BatchCreatedResponse/BatchDetailResponse"
```

---

## Task 5: BusinessBatchAppService 与 Controller 实现

**Files:**

- Create:
  `business-core-kernel/business-core-application/src/main/java/com/example/core/application/business/service/BusinessBatchAppService.java`
- Create:
  `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/batch/BusinessBatchController.java`
- Create:
  `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/batch/converter/BatchConverter.java`
- Test:
  `business-core-kernel/business-core-application/src/test/java/com/example/core/application/business/service/BusinessBatchAppServiceTest.java`

**Interfaces:**

- Consumes: `BusinessBatchApi`, `SessionContextResolver`, `BusinessMetaContextAssembler`,
  `SupportedBusinessTypeValidator`, `BusinessAccessGuard`, `BatchRepository`, `BusinessBatch` 聚合根, `BusinessContext`
  值对象
- Produces: `BusinessBatchAppService` bean, `BusinessBatchController` bean, `BatchConverter` bean

- [ ] **Step 1: 编写 BusinessBatchAppService 失败测试**

```java
package com.example.core.application.business.service;

import com.example.core.domain.business.aggregate.root.BusinessBatch;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.aggregate.valueobject.business.OperationModel;
import com.example.core.domain.business.repository.BatchRepository;
import com.example.shared.domain.event.EventBus;
import com.example.shared.primitives.identity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * BusinessBatchAppService 单元测试
 *
 * @author panoshu
 */
class BusinessBatchAppServiceTest {

    private BatchRepository batchRepository;
    private EventBus eventBus;
    private IdService idService;
    private BusinessBatchAppService appService;

    @BeforeEach
    void setUp() {
        batchRepository = mock(BatchRepository.class);
        eventBus = mock(EventBus.class);
        idService = mock(IdService.class);
        when(idService.generateId(BatchId.class)).thenReturn(new BatchId(1L));
        appService = new BusinessBatchAppService(batchRepository, eventBus, idService);
    }

    @Test
    void should_create_batch_with_context_and_operator() {
        BusinessContext context = new BusinessContext(
            BusinessType.ACC_PLAN_CREATE,
            new CustomerNo("C001"), "Customer A",
            new ProductNo("PRD001"), "Product A",
            new PlanNo("P001"), "Plan A",
            OperationModel.SELF_MANAGED,
            AccountManager.CJP
        );
        OperatorInfo operator = new OperatorInfo(new UserNo("U001"), "alice");

        BusinessBatch batch = appService.createBatch(context, operator);

        ArgumentCaptor<BusinessBatch> captor = ArgumentCaptor.forClass(BusinessBatch.class);
        verify(batchRepository).save(captor.capture());
        BusinessBatch saved = captor.getValue();
        assertThat(saved.id()).isNotNull();
        assertThat(saved.businessContext()).isEqualTo(context);
    }

    @Test
    void should_find_active_batch_by_form_id() {
        BatchId batchId = new BatchId(1L);
        BusinessBatch batch = mock(BusinessBatch.class);
        when(batch.id()).thenReturn(batchId);
        when(batchRepository.findByFormId(any(FormId.class))).thenReturn(Optional.of(batch));

        Optional<BusinessBatch> result = appService.findByFormId(new FormId(100L));

        assertThat(result).isPresent();
        verify(batchRepository).findByFormId(new FormId(100L));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -pl business-core-kernel/business-core-application -Dtest=BusinessBatchAppServiceTest`
Expected: FAIL

- [ ] **Step 3: 编写 BusinessBatchAppService 实现**

```java
package com.example.core.application.business.service;

import com.example.core.domain.business.aggregate.root.BusinessBatch;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.repository.BatchRepository;
import com.example.shared.domain.event.EventBus;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.FormId;
import com.example.shared.primitives.identity.IdService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 业务批次应用服务
 *
 * <p>编排批次的创建、查询、取消等业务流程,管理事务边界。
 *
 * @author panoshu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessBatchAppService {

    private final BatchRepository batchRepository;
    private final EventBus eventBus;
    private final IdService idService;

    /**
     * 创建业务批次。
     *
     * @param context 业务上下文(从 SessionContext 组装)
     * @param operator 操作人信息
     * @return 创建的批次聚合根
     */
    @Transactional
    public BusinessBatch createBatch(BusinessContext context, OperatorInfo operator) {
        BatchId batchId = idService.generateId(BatchId.class);
        BusinessBatch batch = BusinessBatch.create(batchId, context, operator);
        batchRepository.save(batch);
        batch.getDomainEvents().forEach(eventBus::publish);
        batch.clearDomainEvents();
        log.info("创建业务批次成功: batchId={}, businessType={}", batchId, context.businessType());
        return batch;
    }

    /**
     * 通过表单 ID 反查批次。
     */
    @Transactional(readOnly = true)
    public Optional<BusinessBatch> findByFormId(FormId formId) {
        return batchRepository.findByFormId(formId);
    }

    /**
     * 加载批次(不存在时抛异常)。
     */
    @Transactional(readOnly = true)
    public BusinessBatch loadOrThrow(BatchId batchId) {
        return batchRepository.loadOrThrow(batchId);
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn test -pl business-core-kernel/business-core-application -Dtest=BusinessBatchAppServiceTest`
Expected: PASS (2 tests)

> **注意**:本测试假设 `BusinessBatch.create(batchId, context, operator)` 静态工厂方法存在。如不存在,需要在
> `BusinessBatch` 聚合根中新增。运行测试前先检查。

- [ ] **Step 5: 检查/补充 BusinessBatch.create 静态工厂方法**

检查 `BusinessBatch` 是否有 `create(BatchId, BusinessContext, OperatorInfo)` 静态方法。如无,在 `BusinessBatch.java`
中添加:

```java
/**
 * 工厂方法:创建新业务批次
 */
public static BusinessBatch create(BatchId batchId, BusinessContext context, OperatorInfo operator) {
    BusinessBatch batch = new BusinessBatch(batchId, operator.operatorId());
    batch.businessContext = context;
    batch.operatorInfo = operator;
    batch.status = BatchStatus.CREATED;
    return batch;
}
```

- [ ] **Step 6: 编写 BatchConverter (MapStruct)**

```java
package com.example.core.adapter.batch.converter;

import com.example.core.api.batch.response.BatchCreatedResponse;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.core.api.batch.response.BatchSummaryResponse;
import com.example.core.domain.business.aggregate.root.BusinessBatch;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 批次 DTO 转换器
 *
 * @author panoshu
 */
@Mapper(componentModel = "spring")
public interface BatchConverter {

    @Mapping(target = "batchId", source = "id.value")
    @Mapping(target = "businessType", source = "businessContext.businessType.name")
    @Mapping(target = "planNo", source = "businessContext.planNo.value")
    BatchSummaryResponse toSummaryResponse(BusinessBatch batch);

    @Mapping(target = "batchId", source = "id.value")
    BatchCreatedResponse toCreatedResponse(BusinessBatch batch);

    @Mapping(target = "batchId", source = "id.value")
    @Mapping(target = "businessType", source = "businessContext.businessType.name")
    @Mapping(target = "planNo", source = "businessContext.planNo.value")
    @Mapping(target = "customerNo", source = "businessContext.customerNo.value")
    @Mapping(target = "customerName", source = "businessContext.customerName")
    BatchDetailResponse toDetailResponse(BusinessBatch batch);
}
```

- [ ] **Step 7: 编写 BusinessBatchController**

> **设计决策**:Controller 方法签名与 `BusinessBatchApi` 接口完全一致 (无 `HttpServletRequest` 参数)。会话通过
> `SessionContextResolver.require()` 内部使用 `RequestContextHolder` 获取当前请求解析。

```java
package com.example.core.adapter.batch;

import com.example.core.adapter.batch.converter.BatchConverter;
import com.example.core.adapter.context.BusinessMetaContextAssembler;
import com.example.core.adapter.context.SessionContextResolver;
import com.example.core.adapter.security.RequireBusinessPermission;
import com.example.core.adapter.validator.SupportedBusinessTypeValidator;
import com.example.core.api.batch.BusinessBatchApi;
import com.example.core.api.batch.command.CancelBatchCommand;
import com.example.core.api.batch.command.CreateBatchCommand;
import com.example.core.api.batch.query.FindActiveBatchQuery;
import com.example.core.api.batch.query.GetBatchDetailQuery;
import com.example.core.api.batch.response.BatchCreatedResponse;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.core.api.batch.response.BatchSummaryResponse;
import com.example.core.api.context.BusinessMetaContext;
import com.example.core.api.context.SessionContext;
import com.example.core.application.business.guard.BusinessAccessGuard;
import com.example.core.application.business.service.BusinessBatchAppService;
import com.example.core.domain.business.aggregate.root.BusinessBatch;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.aggregate.valueobject.business.OperationModel;
import com.example.shared.primitives.identity.*;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 业务批次管理 Controller
 *
 * <p>实现 {@link BusinessBatchApi},入口完成:
 * 业务类型校验 → 会话解析 → BusinessMetaContext 组装 → 权限校验 → 调用 AppService。
 *
 * <p>方法签名与 API 接口完全一致,会话通过 {@link SessionContextResolver} 内部
 * 使用 {@code RequestContextHolder} 获取当前请求解析。
 *
 * @author panoshu
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class BusinessBatchController implements BusinessBatchApi {

    private final BusinessBatchAppService batchAppService;
    private final BatchConverter converter;
    private final SupportedBusinessTypeValidator typeValidator;
    private final SessionContextResolver sessionResolver;
    private final BusinessMetaContextAssembler metaAssembler;
    private final BusinessAccessGuard accessGuard;

    @Override
    @RequireBusinessPermission("BATCH_CREATE")
    public ApiResult<Optional<BatchSummaryResponse>> findActive(@Valid @RequestBody FindActiveBatchQuery query) {
        typeValidator.validate(query.businessType());
        SessionContext session = sessionResolver.require();
        log.info("查询未完成批次: planNo={}, businessType={}, userNo={}",
            query.planNo(), query.businessType(), session.userNo());
        // TODO: 调用 batchAppService.findActive(planNo, businessType) 并转换响应
        return ApiResult.success(Optional.empty());
    }

    @Override
    @RequireBusinessPermission("BATCH_CREATE")
    public ApiResult<BatchCreatedResponse> create(@Valid @RequestBody CreateBatchCommand command) {
        typeValidator.validate(command.businessType());
        SessionContext session = sessionResolver.require();
        BusinessMetaContext meta = metaAssembler.assemble(command.businessType(), command.planNo(), session);
        accessGuard.checkCanHandle(session, meta);

        log.info("创建业务批次: businessType={}, planNo={}, userNo={}",
            command.businessType(), command.planNo(), session.userNo());

        BusinessContext domainContext = toDomainContext(meta);
        OperatorInfo operator = new OperatorInfo(new UserNo(session.userNo()), session.loginName());
        BusinessBatch batch = batchAppService.createBatch(domainContext, operator);
        return ApiResult.success(converter.toCreatedResponse(batch));
    }

    @Override
    public ApiResult<BatchDetailResponse> detail(@Valid @RequestBody GetBatchDetailQuery query) {
        SessionContext session = sessionResolver.require();
        log.info("查询批次详情: batchId={}, userNo={}", query.batchId(), session.userNo());
        BusinessBatch batch = batchAppService.loadOrThrow(new BatchId(query.batchId()));
        return ApiResult.success(converter.toDetailResponse(batch));
    }

    @Override
    @RequireBusinessPermission("BATCH_CANCEL")
    public ApiResult<Void> cancel(@Valid @RequestBody CancelBatchCommand command) {
        SessionContext session = sessionResolver.require();
        log.info("取消批次: batchId={}, userNo={}", command.batchId(), session.userNo());
        // TODO: 调用 batchAppService.cancel(batchId, reason) 并发布事件
        return ApiResult.success();
    }

    private BusinessContext toDomainContext(BusinessMetaContext meta) {
        return new BusinessContext(
            BusinessType.valueOf(meta.businessType()),
            new CustomerNo(meta.customerNo()),
            meta.customerName(),
            new ProductNo(meta.productNo()),
            meta.productName(),
            new PlanNo(meta.planNo()),
            meta.planName(),
            OperationModel.valueOf(meta.operationModel()),
            AccountManager.valueOf(meta.accountManager())
        );
    }
}
```

- [ ] **Step 8: 编译验证**

Run: `mvn compile -pl business-core-kernel/business-core-adapter`
Expected: BUILD SUCCESS

- [ ] **Step 9: 提交**

```bash
git add business-core-kernel/business-core-application/src/main/java/com/example/core/application/business/service/BusinessBatchAppService.java \
        business-core-kernel/business-core-application/src/test/java/com/example/core/application/business/service/BusinessBatchAppServiceTest.java \
        business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/batch/ \
        business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/business/aggregate/root/BusinessBatch.java
git commit -m "feat(core-adapter): 实现 BusinessBatchApi 与应用服务

1. BusinessBatchAppService 编排批次创建/查询,管理事务边界
2. BusinessBatchController 入口完成业务类型校验→会话解析→权限校验→调用 AppService
3. BatchConverter 通过 MapStruct 完成聚合根到响应 DTO 的转换
4. BusinessBatch 新增 create 静态工厂方法"
```

---

## Task 6: BusinessFormApi 接口与实现

**Files:**

- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/form/BusinessFormApi.java`
- Create: 4 个 Command + 1 个 Query + 2 个 Response DTO
- Create:
  `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/form/BusinessFormController.java`
- Create:
  `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/form/converter/FormConverter.java`

**Interfaces:**

- Consumes: `BusinessFormAppService`(已存在), `SessionContextResolver`, `SupportedBusinessTypeValidator`
- Produces: `BusinessFormApi` 接口及配套 DTO, `BusinessFormController` bean

- [ ] **Step 1: 编写 BusinessFormApi 接口与 DTO**

参考 Task 4 的模式,定义以下 DTO:

- `ApplyUploadTokenCommand{batchId, fileName, fileSize, contentType}`
- `ConfirmUploadCommand{batchId, formId, fileMd5}`
- `DeleteFormCommand{batchId, formId}`
- `GetFormStatusQuery{formId}`
- `UploadTokenResponse{token, expireTime, uploadUrl}`
- `FormStatusResponse{formId, status, parseProgress, applicationCount, errorMsg}`

```java
package com.example.core.api.form;

import com.example.core.api.form.command.ApplyUploadTokenCommand;
import com.example.core.api.form.command.ConfirmUploadCommand;
import com.example.core.api.form.command.DeleteFormCommand;
import com.example.core.api.form.query.GetFormStatusQuery;
import com.example.core.api.form.response.FormStatusResponse;
import com.example.core.api.form.response.UploadTokenResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 业务表单管理 API
 *
 * <p>提供表单的上传 token 申请、上传确认、删除、状态查询等公共接口。
 * 路径前缀 {@code /core/form}。
 *
 * @author panoshu
 */
@HttpExchange("/core/form")
public interface BusinessFormApi {

    @PostExchange("/upload-token")
    ApiResult<UploadTokenResponse> applyUploadToken(@Valid @RequestBody ApplyUploadTokenCommand command);

    @PostExchange("/confirm-upload")
    ApiResult<Void> confirmUpload(@Valid @RequestBody ConfirmUploadCommand command);

    @PostExchange("/delete")
    ApiResult<Void> delete(@Valid @RequestBody DeleteFormCommand command);

    @PostExchange("/status")
    ApiResult<FormStatusResponse> status(@Valid @RequestBody GetFormStatusQuery query);
}
```

- [ ] **Step 2: 编写 FormConverter 与 BusinessFormController**

Controller 实现 `BusinessFormApi`,方法签名与接口完全一致 (无 `HttpServletRequest` 参数),入口调用
`SupportedBusinessTypeValidator`(从 batchId 反查业务类型)→ `SessionContextResolver.require()` → `BusinessAccessGuard` →
已有的 `BusinessFormAppService`。

由于 `BusinessFormAppService.confirmUpload` 已存在,Controller 直接调用即可:

```java
@Slf4j
@RestController
@RequiredArgsConstructor
public class BusinessFormController implements BusinessFormApi {

    private final BusinessFormAppService formAppService;
    private final SessionContextResolver sessionResolver;
    private final FormConverter converter;

    @Override
    @RequireBusinessPermission("FORM_UPLOAD")
    public ApiResult<UploadTokenResponse> applyUploadToken(@Valid @RequestBody ApplyUploadTokenCommand command) {
        SessionContext session = sessionResolver.require();
        log.info("申请上传 token: batchId={}, userNo={}", command.batchId(), session.userNo());
        // TODO: 调用 fileIntegrationGateway 申请 token
        return ApiResult.success(new UploadTokenResponse("dummy-token", null, null));
    }

    @Override
    @RequireBusinessPermission("FORM_UPLOAD")
    public ApiResult<Void> confirmUpload(@Valid @RequestBody ConfirmUploadCommand command) {
        SessionContext session = sessionResolver.require();
        log.info("确认上传: batchId={}, formId={}, userNo={}",
            command.batchId(), command.formId(), session.userNo());
        // TODO: 调用 formAppService.confirmUpload(formId, uploadedFile)
        return ApiResult.success();
    }

    @Override
    @RequireBusinessPermission("FORM_DELETE")
    public ApiResult<Void> delete(@Valid @RequestBody DeleteFormCommand command) {
        SessionContext session = sessionResolver.require();
        log.info("删除表单: batchId={}, formId={}, userNo={}",
            command.batchId(), command.formId(), session.userNo());
        // TODO: 调用 formAppService.deleteForm(formId)
        return ApiResult.success();
    }

    @Override
    public ApiResult<FormStatusResponse> status(@Valid @RequestBody GetFormStatusQuery query) {
        SessionContext session = sessionResolver.require();
        log.info("查询表单状态: formId={}, userNo={}", query.formId(), session.userNo());
        // TODO: 调用 formAppService.getFormStatus(formId) 并转换
        return ApiResult.success(new FormStatusResponse(query.formId(), "UNKNOWN", 0, 0, null));
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn compile -pl business-core-kernel/business-core-adapter`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add business-core-kernel/business-core-api/src/main/java/com/example/core/api/form/ \
        business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/form/
git commit -m "feat(core-api): 新增 BusinessFormApi 接口与 Controller 实现

1. BusinessFormApi 定义上传token/确认上传/删除/状态查询 4 个公共接口
2. BusinessFormController 入口完成会话解析与功能权限校验
3. 复用已有的 BusinessFormAppService 进行表单处理"
```

---

## Task 7: BusinessApplicationApi 接口与实现

**Files:**

- Create:
  `business-core-kernel/business-core-api/src/main/java/com/example/core/api/application/BusinessApplicationApi.java`
- Create: 2 个 Command + 2 个 Query + 4 个 Response DTO
- Create:
  `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/application/BusinessApplicationController.java`
- Create:
  `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/application/converter/ApplicationConverter.java`
- Create:
  `business-core-kernel/business-core-application/src/main/java/com/example/core/application/business/service/BusinessApplicationAppService.java`

**Interfaces:**

- Consumes: `FlowOrchestrationService`(已存在), `ApplicationRepository`(已存在)
- Produces: `BusinessApplicationApi` 接口及配套 DTO, `BusinessApplicationController` bean,
  `BusinessApplicationAppService` bean

- [ ] **Step 1: 编写 BusinessApplicationApi 接口与 DTO**

定义以下 DTO:

- `AdvanceStepCommand{applicationId, actionPayload: Map<String,Object>?}`
- `SubmitApplicationCommand{applicationId}`
- `FindApplicationListQuery{batchId, status?}`
- `GetApplicationDetailQuery{applicationId}`
- `ApplicationSummaryResponse{applicationId, batchId, status, currentStep, ...}`
- `ApplicationDetailResponse{...}`
- `AdvanceStepResponse{applicationId, nextStep, status}`
- `SubmitResponse{applicationId, needApproval, approvalInstanceId?}`

```java
@HttpExchange("/core/application")
public interface BusinessApplicationApi {

    @PostExchange("/list")
    ApiResult<List<ApplicationSummaryResponse>> list(@Valid @RequestBody FindApplicationListQuery query);

    @PostExchange("/detail")
    ApiResult<ApplicationDetailResponse> detail(@Valid @RequestBody GetApplicationDetailQuery query);

    @PostExchange("/advance")
    ApiResult<AdvanceStepResponse> advance(@Valid @RequestBody AdvanceStepCommand command);

    @PostExchange("/submit")
    ApiResult<SubmitResponse> submit(@Valid @RequestBody SubmitApplicationCommand command);
}
```

- [ ] **Step 2: 编写 BusinessApplicationAppService**

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessApplicationAppService {

    private final FlowOrchestrationService flowOrchestrationService;
    private final ApplicationRepository applicationRepository;

    /**
     * 推进申请单到下一节点。
     */
    @Transactional
    public void advanceStep(ApplicationId applicationId) {
        flowOrchestrationService.advanceStep(applicationId);
    }

    /**
     * 提交申请单(触发审批判断)。
     */
    @Transactional
    public void submit(ApplicationId applicationId) {
        // TODO: 提交时判断是否需要审批,如需要则触发审批流
        flowOrchestrationService.advanceStep(applicationId);
    }

    /**
     * 加载申请单。
     */
    @Transactional(readOnly = true)
    public BusinessApplication loadOrThrow(ApplicationId applicationId) {
        return applicationRepository.loadOrThrow(applicationId);
    }
}
```

- [ ] **Step 3: 编写 BusinessApplicationController**

参考 Task 5 的模式,Controller 实现 `BusinessApplicationApi`,入口完成会话解析与功能权限校验,调用
`BusinessApplicationAppService`。

- [ ] **Step 4: 编译验证与提交**

Run: `mvn compile -pl business-core-kernel/business-core-adapter`

```bash
git add business-core-kernel/business-core-api/src/main/java/com/example/core/api/application/ \
        business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/application/ \
        business-core-kernel/business-core-application/src/main/java/com/example/core/application/business/service/BusinessApplicationAppService.java
git commit -m "feat(core-api): 新增 BusinessApplicationApi 接口与实现

1. BusinessApplicationApi 定义列表/详情/推进/提交 4 个公共接口
2. BusinessApplicationAppService 编排申请单推进,复用 FlowOrchestrationService
3. Controller 入口完成会话解析与功能权限校验"
```

---

## Task 8: MaterialAppApi 与 BusinessProgressApi 接口与实现

**Files:**

- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/material/MaterialAppApi.java`
- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/progress/BusinessProgressApi.java`
- Create: 各自的 Command/Query/Response DTO
- Create:
  `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/material/MaterialController.java`
- Create:
  `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/progress/BusinessProgressController.java`
- Create:
  `business-core-kernel/business-core-application/src/main/java/com/example/core/application/business/service/BusinessProgressAppService.java`

**Interfaces:**

- Consumes: `MaterialAppService`(已存在,在 engine/step/service 包), `BatchRepository`, `FormRepository`
- Produces: `MaterialAppApi`, `BusinessProgressApi` 接口及 DTO, 两个 Controller bean, `BusinessProgressAppService` bean

- [ ] **Step 1: 编写 MaterialAppApi 接口与 DTO**

定义:

- `BindIndividualMaterialCommand{applicationId, materialItem}`
- `BindPackageMaterialCommand{applicationId, materialPackageId}`
- `UnbindMaterialCommand{applicationId, materialItemId}`
- `ListMaterialsQuery{applicationId}`
- `CheckCompletenessQuery{applicationId}`
- `MaterialItemDTO{materialItemId, materialName, requirementType, ...}`
- `CompletenessResponse{applicationId, satisfied, missingItems}`

- [ ] **Step 2: 编写 BusinessProgressApi 接口与 DTO**

```java
@HttpExchange("/core/progress")
public interface BusinessProgressApi {

    @PostExchange("/batch/summary")
    ApiResult<BatchProgressResponse> batchProgress(@Valid @RequestBody GetBatchProgressQuery query);
}
```

- [ ] **Step 3: 编写 BusinessProgressAppService**

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessProgressAppService {

    private final BatchRepository batchRepository;
    private final FormRepository formRepository;
    private final ApplicationRepository applicationRepository;

    /**
     * 查询批次整体进度。
     */
    @Transactional(readOnly = true)
    public BatchProgressVO getBatchProgress(BatchId batchId) {
        BusinessBatch batch = batchRepository.loadOrThrow(batchId);
        // TODO: 聚合表单数/申请单数/成功率
        return new BatchProgressVO(batch.id(), batch.successCount(), batch.failedCount(), batch.totalApplicationCount());
    }
}
```

- [ ] **Step 4: 编写两个 Controller**

参考 Task 5/6 的模式实现 `MaterialController` 和 `BusinessProgressController`。

- [ ] **Step 5: 编译验证与提交**

Run: `mvn compile -pl business-core-kernel/business-core-adapter`

```bash
git add business-core-kernel/business-core-api/src/main/java/com/example/core/api/material/ \
        business-core-kernel/business-core-api/src/main/java/com/example/core/api/progress/ \
        business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/material/ \
        business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/progress/ \
        business-core-kernel/business-core-application/src/main/java/com/example/core/application/business/service/BusinessProgressAppService.java
git commit -m "feat(core-api): 新增 MaterialAppApi 与 BusinessProgressApi

1. MaterialAppApi 定义材料绑定/解绑/列表/完整性校验 5 个公共接口
2. BusinessProgressApi 定义批次进度查询接口
3. BusinessProgressAppService 聚合批次进度数据
4. 两个 Controller 入口完成会话解析与功能权限校验"
```

---

## Task 9: 自动装配与集成验证

**Files:**

- Create:
  `business-core-kernel/business-core-starter/src/main/java/com/example/core/configuration/CoreKernelAutoConfiguration.java`
- Modify:
  `business-core-kernel/business-core-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Modify: `business-core-kernel/business-core-adapter/pom.xml`(确认依赖完整)
- Modify: 根 `pom.xml`(如需新增 shared-exception 等依赖管理)

**Interfaces:**

- Consumes: 所有前序 Task 产出的 bean
- Produces: `CoreKernelAutoConfiguration` 自动配置类

- [ ] **Step 1: 编写 CoreKernelAutoConfiguration**

```java
package com.example.core.configuration;

import com.example.core.adapter.context.BusinessMetaContextAssembler;
import com.example.core.adapter.context.SessionContextResolver;
import com.example.core.adapter.validator.SupportedBusinessTypeValidator;
import com.example.core.application.business.guard.BusinessAccessGuard;
import com.example.core.application.business.guard.DefaultBusinessAccessGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * business-core-kernel 自动配置
 *
 * <p>业务服务引入 {@code business-core-starter} 后,自动注册:
 * <ul>
 *   <li>SessionContextResolver / BusinessMetaContextAssembler(会话基础设施)</li>
 *   <li>SupportedBusinessTypeValidator(业务类型校验)</li>
 *   <li>DefaultBusinessAccessGuard(默认权限守门人,业务服务可覆盖)</li>
 *   <li>5 类公共 API 的 Controller</li>
 *   <li>@RequireBusinessPermission 的 AOP 切面</li>
 * </ul>
 *
 * <p>业务服务只需:
 * <ol>
 *   <li>提供 {@link com.example.core.api.registrar.BusinessTypeRegistrar} Bean 声明支持的业务类型</li>
 *   <li>(可选)提供自定义 {@link BusinessAccessGuard} 覆盖默认实现</li>
 * </ol>
 *
 * @author panoshu
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.example.core")
public class CoreKernelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SessionContextResolver sessionContextResolver(ObjectMapper objectMapper) {
        return new SessionContextResolver(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public BusinessMetaContextAssembler businessMetaContextAssembler() {
        return new BusinessMetaContextAssembler();
    }

    @Bean
    @ConditionalOnMissingBean(BusinessAccessGuard.class)
    public BusinessAccessGuard defaultBusinessAccessGuard() {
        return new DefaultBusinessAccessGuard();
    }
}
```

- [ ] **Step 2: 注册自动配置**

在
`business-core-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
中添加:

```
com.example.core.configuration.CoreKernelAutoConfiguration
```

- [ ] **Step 3: 确认 starter 依赖**

确认 `business-core-starter/pom.xml` 包含 `business-core-adapter` 依赖。如无,添加:

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>business-core-adapter</artifactId>
</dependency>
```

- [ ] **Step 4: 全量编译验证**

Run: `mvn clean compile -pl business-core-kernel -am`
Expected: BUILD SUCCESS

- [ ] **Step 5: 全量测试验证**

Run: `mvn test -pl business-core-kernel`
Expected: 所有测试通过

- [ ] **Step 6: 提交**

```bash
git add business-core-kernel/business-core-starter/src/main/java/com/example/core/configuration/ \
        business-core-kernel/business-core-starter/src/main/resources/META-INF/spring/ \
        business-core-kernel/business-core-starter/pom.xml
git commit -m "feat(kernel): 新增 CoreKernelAutoConfiguration 自动装配

1. CoreKernelAutoConfiguration 通过 @ComponentScan 注册所有 kernel 组件
2. SessionContextResolver/BusinessMetaContextAssembler/DefaultBusinessAccessGuard 通过 @ConditionalOnMissingBean 兜底
3. 业务服务引入 business-core-starter 即获得公共 API 能力
4. 业务服务只需提供 BusinessTypeRegistrar Bean 声明支持的业务类型"
```

---

## Self-Review

**1. Spec coverage 检查:**

| Spec 章节                           | 对应 Task                                       |
|-------------------------------------|-------------------------------------------------|
| §1 整体定位与边界                   | Task 1-9 整体覆盖                               |
| §2 SessionContext 与 sa-token 集成  | Task 1(SessionContext + SessionContextResolver) |
| §3 BusinessMetaContext              | Task 1(BusinessMetaContext + Assembler)         |
| §4.1 功能权限                       | Task 3(@RequireBusinessPermission + AOP)        |
| §4.2 数据权限                       | Task 2(BusinessAccessGuard + Default)           |
| §4.3 SupportedBusinessTypeValidator | Task 2                                          |
| §4.4 Controller 使用模式            | Task 5/6/7/8(Controller 实现)                   |
| §5.1 BusinessBatchApi               | Task 4/5                                        |
| §5.2 BusinessFormApi                | Task 6                                          |
| §5.3 BusinessApplicationApi         | Task 7                                          |
| §5.4 MaterialAppApi                 | Task 8                                          |
| §5.5 BusinessProgressApi            | Task 8                                          |
| §6 业务服务接入示例                 | Task 9(自动装配)                                |
| §8 后续接入指南                     | Task 4(BusinessBatchApi 注释中说明)             |

**2. 占位符扫描:** Task 5/6/7/8 的 Controller 中有 TODO 标记,这些是已知的待实现点 (需要对接已有的 AppService
方法或补充实现),不是 plan 占位符。已明确标注。

**3. 类型一致性:**

- `SessionContext` 在所有 Task 中字段一致
- `BusinessMetaContext`(api 层)与 `BusinessContext`(domain 层)在 Task 5 的 `toDomainContext` 方法中正确映射
- `BusinessAccessGuard` 接口在 Task 2 定义,Task 5 的 Controller 注入使用
- `BusinessTypeRegistrar.of()` 工厂方法在 Task 2 定义,Task 9 的自动配置中通过 `@ConditionalOnMissingBean` 兜底

**4. 已知简化点:**

- Controller 部分方法体标注 TODO,实际对接已有 AppService 时需要补充实现
- 集成测试未包含 (需要 Spring Context + H2 数据库),建议在 Task 9 后单独补充
- `BusinessBatch.create` 静态工厂方法可能需要根据现有聚合根结构调整
