# Task 2 报告：权限校验 SPI 与业务类型注册

**状态:** DONE_WITH_CONCERNS

**日期:** 2026-07-26

## 一、概要

实现 Task 2 中定义的权限校验 SPI、默认实现、业务类型注册器和业务类型校验器。所有 11 个测试通过 (8 + 3)，遵循 TDD 流程编写并完成提交。

## 二、文件清单

### 创建文件

| 文件                                    | 路径                                                                                                                                                                                          |
|-----------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| BusinessAccessGuard SPI                 | `d:\WorkSpace\Trae\multiple-module-spring-cloud\business-core-kernel\business-core-application\src\main\java\com\example\core\application\business\guard\BusinessAccessGuard.java`            |
| DefaultBusinessAccessGuard 实现         | `d:\WorkSpace\Trae\multiple-module-spring-cloud\business-core-kernel\business-core-application\src\main\java\com\example\core\application\business\guard\DefaultBusinessAccessGuard.java`     |
| DefaultBusinessAccessGuard 单元测试     | `d:\WorkSpace\Trae\multiple-module-spring-cloud\business-core-kernel\business-core-application\src\test\java\com\example\core\application\business\guard\DefaultBusinessAccessGuardTest.java` |
| BusinessTypeRegistrar 接口              | `d:\WorkSpace\Trae\multiple-module-spring-cloud\business-core-kernel\business-core-api\src\main\java\com\example\core\api\registrar\BusinessTypeRegistrar.java`                               |
| SupportedBusinessTypeValidator 实现     | `d:\WorkSpace\Trae\multiple-module-spring-cloud\business-core-kernel\business-core-adapter\src\main\java\com\example\core\adapter\validator\SupportedBusinessTypeValidator.java`              |
| SupportedBusinessTypeValidator 单元测试 | `d:\WorkSpace\Trae\multiple-module-spring-cloud\business-core-kernel\business-core-adapter\src\test\java\com\example\core\adapter\validator\SupportedBusinessTypeValidatorTest.java`          |

### 修改文件

| 文件                              | 路径                                                                                                                                                                         | 变更说明                                         |
|-----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------|
| CoreDomainErrorCode               | `d:\WorkSpace\Trae\multiple-module-spring-cloud\business-core-kernel\business-core-domain\src\main\java\com\example\core\domain\business\errorcode\CoreDomainErrorCode.java` | 新增 4 个错误码(0004-0007)                       |
| business-core-application pom.xml | `d:\WorkSpace\Trae\multiple-module-spring-cloud\business-core-kernel\business-core-application\pom.xml`                                                                      | 新增 `spring-boot-autoconfigure` (provided) 依赖 |

## 三、提交记录

| Commit Hash | Commit Message                                                    |
|-------------|-------------------------------------------------------------------|
| `ed8ead8`   | `feat(core-application): 新增权限校验 SPI 与业务类型注册基础设施` |

提交内容包含 8 个文件变更，382 行新增。

## 四、测试结果

### 测试套件 1: DefaultBusinessAccessGuardTest

**命令:**
`mvn test -pl business-core-kernel/business-core-application -Dtest=DefaultBusinessAccessGuardTest -Dsurefire.failIfNoSpecifiedTests=false`

**结果:** BUILD SUCCESS

```
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.099 s -- in com.example.core.application.business.guard.DefaultBusinessAccessGuardTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### 测试套件 2: SupportedBusinessTypeValidatorTest

**命令:**
`mvn test -pl business-core-kernel/business-core-adapter -am -Dtest=SupportedBusinessTypeValidatorTest -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false`

> 注：使用 `-am` 是因为 `business-core-api` 模块需要先构建以使新增的 `BusinessTypeRegistrar` 类对 `business-core-adapter`
> 可见。

**结果:** BUILD SUCCESS

```
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.117 s -- in com.example.core.adapter.validator.SupportedBusinessTypeValidatorTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### TDD 流程

- **Step 3 → Step 5:** 先编写 `DefaultBusinessAccessGuardTest` (8 测试), 运行确认 FAIL (compilation error: 找不到
  `DefaultBusinessAccessGuard` 类) → 实现 → 运行确认 PASS
- **Step 9 → Step 10:** 先编写 `SupportedBusinessTypeValidatorTest` (3 测试), 运行确认 FAIL (compilation error: 找不到
  `SupportedBusinessTypeValidator` 类) → 实现 → 运行确认 PASS

## 五、自检清单

| # | 检查项                                                                                                   | 结果                       |
|---|----------------------------------------------------------------------------------------------------------|----------------------------|
| 1 | 所有 11 个测试通过(8 + 3)                                                                                | ✅                         |
| 2 | `mvn test -pl business-core-kernel/business-core-application -Dtest=DefaultBusinessAccessGuardTest` 通过 | ✅ 8 tests pass            |
| 3 | `mvn test -pl business-core-kernel/business-core-adapter -Dtest=SupportedBusinessTypeValidatorTest` 通过 | ✅ 3 tests pass (需 `-am`) |
| 4 | `CoreDomainErrorCode` 共 7 个条目 (3 个已有 + 4 个新增)，码段 0001-0007                                  | ✅                         |
| 5 | 所有新增 public 类/方法均带 `@author panoshu` Javadoc                                                    | ✅                         |
| 6 | `displayMessage()` 断言修复应用于全部 6 个受影响测试方法                                                 | ✅ (4 + 2)                 |
| 7 | 业务逻辑未泄漏到错误层级 (SPI 在 application，Registrar 在 api，Validator 在 adapter)                    | ✅                         |
| 8 | Commit 信息符合 Conventional Commits 规范，scope 为 `core-application`                                   | ✅                         |

### 错误码验证

`CoreDomainErrorCode` 当前条目：

1. `INVALID_STATUS("CORE.DOMAIN.0001", "状态有误")` (已有)
2. `INVALID_DATA("CORE.DOMAIN.0002", "数据有误")` (已有)
3. `INVALID_OPERATION("CORE.DOMAIN.0003", "操作有误")` (已有)
4. `UNSUPPORTED_BUSINESS_TYPE("CORE.DOMAIN.0004", "不支持的业务类型")` (新增)
5. `PLAN_MISMATCH("CORE.DOMAIN.0005", "计划不一致")` (新增)
6. `PROXY_FORBIDDEN("CORE.DOMAIN.0006", "无代办权限")` (新增)
7. `SECONDARY_AUTH_REQUIRED("CORE.DOMAIN.0007", "需要二次授权")` (新增)

## 六、Concern 2: `@ConditionalOnMissingBean` 在 `@Component` 上无效

### 问题描述

Brief 在 `DefaultBusinessAccessGuard` 上同时使用 `@Component` 和 `@ConditionalOnMissingBean(BusinessAccessGuard.class)`：

```java
@Component
@ConditionalOnMissingBean(BusinessAccessGuard.class)
public class DefaultBusinessAccessGuard implements BusinessAccessGuard {
```

**问题：** `@ConditionalOnMissingBean` 仅在 `@Configuration` 类的 `@Bean` 方法上生效，对组件扫描的 `@Component` 类无效。这意味着：

1. 当业务服务 (如 annuity-service)提供自己的 `BusinessAccessGuard` Bean 时，会出现两个 Bean (默认实现 + 业务实现) → 导致
   `NoUniqueBeanDefinitionException`。
2. 默认实现不会被自动跳过。

### 处理决策

**按 Brief 原文保留**，理由：

1. 单元测试使用 `new DefaultBusinessAccessGuard()` 直接实例化，不依赖 Spring DI，所以测试通过不受影响。
2. 真正的 Bean 覆盖机制需要在 Task 9 (auto-configuration) 的 `@Configuration` 类中实现，通过 `@ConditionalOnMissingBean`
   在 `@Bean` 方法上正确生效。
3. 这属于跨任务的设计协调问题，不应在 Task 2 中单独处理。

### 影响

- **当前状态 (Task 2 完成后)：** 单元测试通过，组件可被组件扫描发现，但 `@ConditionalOnMissingBean` 是装饰性的，无实际效果。
- **未来影响 (Task 9 启动时)：** 必须将 `DefaultBusinessAccessGuard` 的注册从 `@Component` 改为 `@Configuration` 类中的
  `@Bean` 方法 (标注 `@ConditionalOnMissingBean`)，并去掉类上的 `@Component` 注解；或者业务服务自行通过 `@Primary` 等方式解决
  Bean 冲突。

### 建议 (供 Task 9 参考)

```java
// Task 9 的 KernelAutoConfiguration 中:
@Configuration
public class KernelAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(BusinessAccessGuard.class)
    public BusinessAccessGuard defaultBusinessAccessGuard() {
        return new DefaultBusinessAccessGuard();
    }
}

// 并修改 DefaultBusinessAccessGuard，去掉 @Component 和 @ConditionalOnMissingBean
```

## 七、pom.xml 依赖验证

### 验证步骤

检查 `business-core-application/pom.xml` 是否有 `spring-boot-autoconfigure` 依赖（提供 `@ConditionalOnMissingBean`）：

1. 阅读现有 pom.xml：未包含 `spring-boot-autoconfigure`。
2. 编写 `DefaultBusinessAccessGuard` 并运行测试，得到编译错误：
   ```
   程序包org.springframework.boot.autoconfigure.condition不存在
   ```
3. 确认需要添加依赖。

### 添加的依赖

```xml
<!-- Spring Boot AutoConfigure (提供 @ConditionalOnMissingBean 等条件注解) -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-autoconfigure</artifactId>
  <scope>provided</scope>
</dependency>
```

- **scope 选择 `provided`：** 与 `spring-context` 一致，符合该模块作为应用层不打包 Spring Boot 运行时的约定。运行时由
  `starter` 模块传递。
- **版本管理：** 通过父 `pom.xml` 的 `spring-boot-dependencies` BOM 自动管理，无需指定版本。

### 添加后验证

重新运行测试，编译成功，所有 8 个测试通过。

## 八、Concern 1 处理：`displayMessage()` 断言修复

### 应用位置 (共 6 处)

**`DefaultBusinessAccessGuardTest.java` (4 处)：**

1. `should_fail_when_business_type_permission_missing` → "无办理权限"
2. `should_fail_when_plan_no_mismatch` → "计划不一致"
3. `should_fail_for_internet_proxy_without_delegation` → "无代办权限"
4. `should_fail_for_branch_without_secondary_auth` → "需要二次授权"

**`SupportedBusinessTypeValidatorTest.java` (2 处)：**

5. `should_fail_when_type_not_supported` → "不支持的业务类型"
6. `should_fail_when_registrar_missing` → "未配置业务类型注册器"

### 修改模式

将 Brief 中的：

```java
.hasMessageContaining("xxx")
```

替换为：

```java
.extracting(ex -> ((BusinessException) ex).displayMessage())
.asString()
.contains("xxx");
```

### 原因回顾

`BusinessException.getMessage()` 返回的是 `errorInfo()`（如 `[COMMON.0003] 无权限访问`），不包含 `userDetail` 文本。而
`displayMessage()` 返回 `message + "，" + userDetail`，包含目标断言文本。这是 Task 1 已验证的成熟方案。

### 匹配验证

| 测试方法                      | 目标文本             | 实现 userDetail                | 是否包含    |
|-------------------------------|----------------------|--------------------------------|-------------|
| permission_missing            | 无办理权限           | "无办理权限"                   | ✅ 完全匹配 |
| plan_no_mismatch              | 计划不一致           | "所选计划与会话中的计划不一致" | ✅ contains |
| proxy_without_delegation      | 无代办权限           | "无代办权限"                   | ✅ 完全匹配 |
| branch_without_secondary_auth | 需要二次授权         | "网点渠道办理业务需要二次授权" | ✅ contains |
| type_not_supported            | 不支持的业务类型     | "不支持的业务类型"             | ✅ 完全匹配 |
| registrar_missing             | 未配置业务类型注册器 | "服务未配置业务类型注册器"     | ✅ contains |

## 九、关键代码摘要

### BusinessAccessGuard SPI (application 层)

```java
public interface BusinessAccessGuard {
    void checkCanHandle(SessionContext session, BusinessMetaContext meta);
}
```

### DefaultBusinessAccessGuard 实现要点

- 通用校验 (所有渠道)：计划一致性 + 客户一致性 + 业务类型办理权限
- INTERNET 渠道：代办时校验 planNo 在 delegatedPlanNos 内
- BRANCH 渠道：必须 hasSecondaryAuth=true
- HQ 渠道：无额外校验
- 默认渠道：抛 `不支持的渠道类型`

### BusinessTypeRegistrar (api 层)

```java
public interface BusinessTypeRegistrar {
    Set<String> supportedBusinessTypes();
    static BusinessTypeRegistrar of(String... types) { ... }
}
```

### SupportedBusinessTypeValidator (adapter 层)

- 注入 `BusinessTypeRegistrar` bean
- `validate(String businessType)`：
  - registrar 为 null → 抛 INTERNAL_SERVER_ERROR + "服务未配置业务类型注册器"
  - 类型不在支持列表 → 抛 BAD_REQUEST + "不支持的业务类型"

## 十、其他备注

### Brief Step 4 缺失

Brief 从 Step 3 跳到 Step 5，编号笔误，无内容缺失。Step 3 写测试，Step 5 运行测试，正常流程。

### 业务权限码命名约定

`DefaultBusinessAccessGuard.checkCommon` 中根据 `meta.businessType()` 拼接出所需权限码：

```java
String requiredPermission = "BUSINESS_%s_HANDLE".formatted(meta.businessType());
```

例如 `businessType="ANNUITY_OPEN"` → 所需权限为 `BUSINESS_ANNUITY_OPEN_HANDLE`。这与测试用例中的权限码命名一致。

### 渠道类型字符串约定

实现使用字符串字面量 `INTERNET`/`BRANCH`/`HQ` 匹配 `session.channelType()`。这些字符串与 `SessionContext` 中的
`channelType` 字段约定一致 (参考 Task 1 文档)。

## 十一、总结

Task 2 已完成，所有验收标准达成。Concern 2 (`@ConditionalOnMissingBean` 在 `@Component` 上无效)已标记为
DONE_WITH_CONCERNS，建议在 Task 9 (auto-configuration) 中通过 `@Configuration` + `@Bean` + `@ConditionalOnMissingBean`
模式解决。
