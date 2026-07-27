# Task 1: 会话上下文基础设施

**Files:**
- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/context/SessionContext.java`
- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/context/BusinessMetaContext.java`
- Create: `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/context/SessionContextResolver.java`
- Create: `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/context/BusinessMetaContextAssembler.java`
- Test: `business-core-kernel/business-core-adapter/src/test/java/com/example/core/adapter/context/SessionContextResolverTest.java`
- Test: `business-core-kernel/business-core-adapter/src/test/java/com/example/core/adapter/context/BusinessMetaContextAssemblerTest.java`

**Interfaces:**
- Consumes: `com.example.shared.web.core.api.ApiResult`, `com.example.shared.exception.BusinessException`, `com.example.shared.exception.CommonError`
- Produces: `SessionContext` record, `BusinessMetaContext` record (api 层,与 domain 层 `com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext` 区分), `SessionContextResolver` bean, `BusinessMetaContextAssembler` bean

## Step 1: 编写 SessionContext record

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

## Step 2: 编写 BusinessMetaContext record(api 层)

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

## Step 3: 编写 SessionContextResolver 失败测试

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

## Step 4: 运行测试确认失败

Run: `mvn test -pl business-core-kernel/business-core-adapter -Dtest=SessionContextResolverTest`
Expected: FAIL (SessionContextResolver 不存在)

## Step 5: 编写 SessionContextResolver 实现

> **设计决策**:为保持 API 接口契约纯净(`BusinessBatchApi` 等接口的方法签名不含 `HttpServletRequest`),`SessionContextResolver` 不接受 `HttpServletRequest` 参数,而是通过 Spring 的 `RequestContextHolder` 获取当前请求上下文。Controller 方法签名与 API 接口完全一致。

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

## Step 6: 运行测试确认通过

Run: `mvn test -pl business-core-kernel/business-core-adapter -Dtest=SessionContextResolverTest`
Expected: PASS (3 tests)

## Step 7: 编写 BusinessMetaContextAssembler 失败测试

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

## Step 8: 运行测试确认失败

Run: `mvn test -pl business-core-kernel/business-core-adapter -Dtest=BusinessMetaContextAssemblerTest`
Expected: FAIL (BusinessMetaContextAssembler 不存在)

## Step 9: 编写 BusinessMetaContextAssembler 实现

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

## Step 10: 运行测试确认通过

Run: `mvn test -pl business-core-kernel/business-core-adapter -Dtest=BusinessMetaContextAssemblerTest`
Expected: PASS (2 tests)

## Step 11: 检查 adapter pom.xml 依赖

确认 `business-core-adapter/pom.xml` 已包含 `shared-exception`(通过 `business-core-api` 传递)与 `jackson-databind`(通过 spring-web)。如缺少 jackson,在 `business-core-adapter/pom.xml` 添加:

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

## Step 12: 提交

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
