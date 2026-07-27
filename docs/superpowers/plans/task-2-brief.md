# Task 2: 权限校验 SPI 与业务类型注册

**Files:**
- Create: `business-core-kernel/business-core-application/src/main/java/com/example/core/application/business/guard/BusinessAccessGuard.java`
- Create: `business-core-kernel/business-core-application/src/main/java/com/example/core/application/business/guard/DefaultBusinessAccessGuard.java`
- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/registrar/BusinessTypeRegistrar.java`
- Create: `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/validator/SupportedBusinessTypeValidator.java`
- Modify: `business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/business/errorcode/CoreDomainErrorCode.java`
- Test: `business-core-kernel/business-core-application/src/test/java/com/example/core/application/business/guard/DefaultBusinessAccessGuardTest.java`
- Test: `business-core-kernel/business-core-adapter/src/test/java/com/example/core/adapter/validator/SupportedBusinessTypeValidatorTest.java`

**Interfaces:**
- Consumes: `SessionContext`, `BusinessMetaContext` (api 层), `BusinessException`, `CommonError`
- Produces: `BusinessAccessGuard` SPI (application 层), `DefaultBusinessAccessGuard` bean, `BusinessTypeRegistrar` bean, `SupportedBusinessTypeValidator` bean

## Step 1: 扩展 CoreDomainErrorCode 新增 UNSUPPORTED_BUSINESS_TYPE

在 `CoreDomainErrorCode.java` 枚举中新增:

```java
UNSUPPORTED_BUSINESS_TYPE("CORE.DOMAIN.0004", "不支持的业务类型"),
PLAN_MISMATCH("CORE.DOMAIN.0005", "计划不一致"),
PROXY_FORBIDDEN("CORE.DOMAIN.0006", "无代办权限"),
SECONDARY_AUTH_REQUIRED("CORE.DOMAIN.0007", "需要二次授权"),
```

## Step 2: 编写 BusinessAccessGuard SPI 接口(application 层)

> **设计决策**:`BusinessAccessGuard` 放在 application 层(而非 domain 层 gateway),因为它依赖 api 层的 `SessionContext` / `BusinessMetaContext` DTO,符合依赖规则 `application → api + domain`。

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

## Step 3: 编写 DefaultBusinessAccessGuard 失败测试

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

## Step 5: 运行测试确认失败

Run: `mvn test -pl business-core-kernel/business-core-application -Dtest=DefaultBusinessAccessGuardTest`
Expected: FAIL (DefaultBusinessAccessGuard 不存在)

## Step 6: 编写 DefaultBusinessAccessGuard 实现

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

## Step 7: 运行测试确认通过

Run: `mvn test -pl business-core-kernel/business-core-application -Dtest=DefaultBusinessAccessGuardTest`
Expected: PASS (8 tests)

## Step 8: 编写 BusinessTypeRegistrar

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

## Step 9: 编写 SupportedBusinessTypeValidator 失败测试

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

## Step 10: 运行测试确认失败

Run: `mvn test -pl business-core-kernel/business-core-adapter -Dtest=SupportedBusinessTypeValidatorTest`
Expected: FAIL

## Step 11: 编写 SupportedBusinessTypeValidator 实现

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

## Step 12: 运行测试确认通过

Run: `mvn test -pl business-core-kernel/business-core-adapter -Dtest=SupportedBusinessTypeValidatorTest`
Expected: PASS (3 tests)

## Step 13: 提交

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
