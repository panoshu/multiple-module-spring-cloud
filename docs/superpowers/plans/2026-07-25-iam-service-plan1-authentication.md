# iam-service Plan 1: 基础设施 + 认证上下文 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭建 iam-service Maven 模块结构，实现认证上下文（三套账号、凭据、二次授权、sa-token 集成）

**Architecture:** DDD + 六边形架构。三套独立 StpLogic（internet/hq/branch），凭据体系通过 `CredentialValidator` 策略接口实现开闭原则，二次授权通过 `SecondaryAuthStrategy` 策略接口扩展。domain 层不依赖任何框架，通过 Repository 接口和领域事件与基础设施层解耦。

**Tech Stack:** Sa-Token 1.45.0 + Spring Boot 3.5.14 + MyBatis-Flex 1.11.5 + PostgreSQL（生产）/H2（测试）+ Redis + MapStruct 1.6.3 + BCrypt（密码加密）

## Global Constraints

- JDK 25 启用 `--enable-preview`
- 包根路径：`com.example.iam`
- domain 层禁止使用 Spring/MyBatis 注解，领域服务标注 `@DomainService`
- 时间戳由应用层管理（不使用 ORM 自动填充）
- API 必须使用 `@HttpExchange` + 仅 GET/POST + `ApiResult<T>`
- DTO 转换通过 MapStruct Converter，禁止在 Adapter 中直接转换
- 错误码格式 `SERVICE.IAM.XXXX`
- 提交信息遵循 Conventional Commits（type 用英文，subject 用中文祈使语气）
- 单类不超过 500 行，单方法不超过 50 行
- 测试数据库使用 H2（参考 user_profile 偏好）

## 关联文档

- 设计规范：[2026-07-25-iam-service-design.md](../specs/2026-07-25-iam-service-design.md)
- 总览计划：[2026-07-25-iam-service-overview.md](./2026-07-25-iam-service-overview.md)
- sa-token 文档：[sa-token-使用说明.md](../../../sa-token-使用说明.md)
- 项目规则：[.trae/rules/](../../../.trae/rules/)

## File Structure

### 项目结构总览

```
iam-service/
├── pom.xml                                       # 父模块
├── iam-types/                                    # 强类型 ID
├── iam-domain/                                   # 认证上下文聚合根、领域服务、Repository 接口、策略、领域事件、错误码
├── iam-api/                                      # sa-token 工具类、@HttpExchange API 接口、DTO
├── iam-application/                              # 应用服务编排、Command、事件监听器、Hook 默认实现
├── iam-adapter/                                  # Controller 实现、Adapter Converter
├── iam-infrastructure/                           # DO、Mapper、Converter、Repository 实现、StpInterface 实现、SQL
└── iam-starter/                                  # 启动类、application.yml、端到端集成测试
```

> 各模块详细文件路径见各任务说明。

### 关键基类依赖

| 基类 | 包路径 | 用途 |
|------|--------|------|
| `AggregateRoot<ID>` | `com.example.shared.domain.aggregate.root` | 聚合根基类，含领域事件注册能力 |
| `Entity<ID>` | `com.example.shared.domain.aggregate.entity` | 实体基类 |
| `Version` | `com.example.shared.domain.aggregate.valueobject` | 乐观锁版本号 |
| `DomainEvent` | `com.example.shared.domain.event` | 领域事件接口 |
| `Repository<T, ID>` | `com.example.shared.domain.repository` | Repository 通用接口 |
| `@DomainService` | `com.example.shared.domain.annotation` | 领域服务标记注解 |
| `Identifier<T>` | `com.example.shared.primitives.identity` | 强类型 ID 接口 |
| `UserNo` / `CustomerNo` / `EventId` | `com.example.shared.primitives.identity` | 已有领域原语（可复用） |

---

## Task 1: 创建 iam-service Maven 父模块

**Files:**
- Create: `iam-service/pom.xml`

**Interfaces:**
- Produces: iam-service 父模块（packaging=pom），声明 7 个子模块

- [ ] **Step 1: 创建 iam-service/pom.xml**

参考 `annuity-service/pom.xml` 结构。父模块 `artifactId=iam-service`，`packaging=pom`，在 `<modules>` 中声明 7 个子模块（iam-types/iam-domain/iam-api/iam-application/iam-adapter/iam-infrastructure/iam-starter），在 `<dependencyManagement>` 中声明 7 个子模块的坐标。

完整内容参考 `annuity-service/pom.xml`，将 artifactId 改为 `iam-service`，将各 module 改为 iam-* 前缀。

- [ ] **Step 2: Commit**

```bash
git add iam-service/pom.xml
git commit -m "feat(iam-service): 创建 iam-service 父模块 Maven 结构"
```

---

## Task 2: 创建 7 个子模块 pom.xml

**Files:**
- Create: `iam-service/iam-types/pom.xml`
- Create: `iam-service/iam-domain/pom.xml`
- Create: `iam-service/iam-api/pom.xml`
- Create: `iam-service/iam-application/pom.xml`
- Create: `iam-service/iam-adapter/pom.xml`
- Create: `iam-service/iam-infrastructure/pom.xml`
- Create: `iam-service/iam-starter/pom.xml`

**Interfaces:**
- Produces: 7 个子模块 pom.xml，依赖关系符合 01-架构与依赖规则.md

### 各子模块 pom.xml 关键内容

- **iam-types/pom.xml**: parent=iam-service，依赖 `shared-types` + `lombok(provided)`
- **iam-domain/pom.xml**: parent=iam-service，依赖 `shared-domain` + `shared-exception` + `shared-types` + `iam-types` + `lombok` + `spring-boot-starter-test(test)` + `jbcrypt`（PasswordCredentialValidator 需要）+ `org.mockito.mockito-junit-jupiter(test)`
- **iam-api/pom.xml**: parent=iam-service，依赖 `shared-api` + `shared-types` + `iam-types` + `sa-token-spring-boot3-starter` + `spring-boot-starter-validation` + `lombok`
- **iam-application/pom.xml**: parent=iam-service，依赖 `iam-api` + `iam-domain` + `shared-domain` + `shared-event-starter` + `spring-boot-starter` + `spring-tx` + `mapstruct` + `lombok` + `spring-boot-starter-test(test)`
- **iam-adapter/pom.xml**: parent=iam-service，依赖 `iam-api` + `iam-application` + `spring-boot-starter-web` + `mapstruct` + `lombok`
- **iam-infrastructure/pom.xml**: parent=iam-service，依赖 `iam-domain` + `iam-api` + `shared-domain` + `shared-id-starter` + `shared-cache-starter` + `shared-crypto-starter` + `mybatis-flex-spring-boot3-starter` + `postgresql(runtime)` + `sa-token-spring-boot3-starter` + `sa-token-redis-jackson` + `mapstruct` + `lombok` + `spring-boot-starter-test(test)` + `h2(test)`
- **iam-starter/pom.xml**: parent=iam-service，依赖 `iam-adapter` + `iam-infrastructure` + `shared-web-starter` + `spring-cloud-starter-alibaba-nacos-discovery` + `spring-boot-starter-test(test)`，配置 `spring-boot-maven-plugin` + `finalName=iam-service`

- [ ] **Step 1: 按 04-代码编写约束.md 第 9 节在父 pom 的 dependencyManagement 中声明各 iam-* 模块**（详见 Task 3）

- [ ] **Step 2: 依次创建 7 个 pom.xml**

实施者请参考 `annuity-service/annuity-*/pom.xml` 的格式创建各 iam 子模块 pom.xml。

- [ ] **Step 3: Commit**

```bash
git add iam-service/iam-*/pom.xml
git commit -m "build(iam-service): 创建 7 个子模块 pom.xml"
```

---

## Task 3: 注册 iam-service 到父 pom.xml 并添加 sa-token 依赖

**Files:**
- Modify: `pom.xml`（根 pom.xml）

**Interfaces:**
- Produces: iam-service 在父 pom 中注册；sa-token/bcrypt 版本号在 properties 中声明；iam-* 与 sa-token-* 在 dependencyManagement 中声明

- [ ] **Step 1: 在根 pom.xml 的 `<modules>` 中追加 `<module>iam-service</module>`**

- [ ] **Step 2: 在根 pom.xml 的 `<properties>` 末尾追加**

```xml
    <sa-token.version>1.45.0</sa-token.version>
    <bcrypt.version>0.4</bcrypt.version>
```

- [ ] **Step 3: 在根 pom.xml 的 `<!-- 2nd Dependencies-->` 部分追加 7 个 iam-* 模块声明**

```xml
      <!-- iam-service -->
      <dependency>
        <groupId>com.example</groupId>
        <artifactId>iam-types</artifactId>
        <version>${project.version}</version>
      </dependency>
      <dependency>
        <groupId>com.example</groupId>
        <artifactId>iam-domain</artifactId>
        <version>${project.version}</version>
      </dependency>
      <dependency>
        <groupId>com.example</groupId>
        <artifactId>iam-api</artifactId>
        <version>${project.version}</version>
      </dependency>
      <dependency>
        <groupId>com.example</groupId>
        <artifactId>iam-application</artifactId>
        <version>${project.version}</version>
      </dependency>
      <dependency>
        <groupId>com.example</groupId>
        <artifactId>iam-adapter</artifactId>
        <version>${project.version}</version>
      </dependency>
      <dependency>
        <groupId>com.example</groupId>
        <artifactId>iam-infrastructure</artifactId>
        <version>${project.version}</version>
      </dependency>
      <dependency>
        <groupId>com.example</groupId>
        <artifactId>iam-starter</artifactId>
        <version>${project.version}</version>
      </dependency>
```

- [ ] **Step 4: 在根 pom.xml 的 `<!-- Other 3rd Dependencies -->` 部分追加 sa-token 与 BCrypt**

```xml
      <!-- Sa-Token -->
      <dependency>
        <groupId>cn.dev33</groupId>
        <artifactId>sa-token-spring-boot3-starter</artifactId>
        <version>${sa-token.version}</version>
      </dependency>
      <dependency>
        <groupId>cn.dev33</groupId>
        <artifactId>sa-token-reactor-spring-boot3-starter</artifactId>
        <version>${sa-token.version}</version>
      </dependency>
      <dependency>
        <groupId>cn.dev33</groupId>
        <artifactId>sa-token-redis-jackson</artifactId>
        <version>${sa-token.version}</version>
      </dependency>

      <!-- BCrypt（密码哈希） -->
      <dependency>
        <groupId>org.mindrot</groupId>
        <artifactId>jbcrypt</artifactId>
        <version>${bcrypt.version}</version>
      </dependency>
```

- [ ] **Step 5: 验证 Maven 结构**

Run: `mvn validate -pl iam-service -am`
Expected: BUILD SUCCESS

- [ ] **Step 6: 在 08-错误码规范.md SERVICE 域追加 IAM 模块缩写**

修改 `.trae/rules/08-错误码规范.md` 第 96 行附近的 SERVICE 域表格，在 annuity 行之后追加：

```markdown
| IAM | iam-service |
```

- [ ] **Step 7: Commit**

```bash
git add pom.xml .trae/rules/08-错误码规范.md
git commit -m "build(iam-service): 注册 iam-service 到父 pom 并添加 sa-token 依赖"
```

---

## Task 4: 创建 iam-types 强类型 ID

**Files:**
- Create: `iam-service/iam-types/src/main/java/com/example/iam/types/InternetUserId.java`
- Create: `iam-service/iam-types/src/main/java/com/example/iam/types/HqUserId.java`
- Create: `iam-service/iam-types/src/main/java/com/example/iam/types/BranchUserId.java`
- Create: `iam-service/iam-types/src/main/java/com/example/iam/types/CredentialId.java`
- Create: `iam-service/iam-types/src/main/java/com/example/iam/types/SecondaryAuthSessionId.java`
- Create: `iam-service/iam-types/src/main/java/com/example/iam/types/LoginLogId.java`
- Create: `iam-service/iam-types/src/main/java/com/example/iam/types/AccountManagerCode.java`

**Interfaces:**
- Consumes: `com.example.shared.primitives.identity.Identifier`
- Produces: 7 个 record 类型 ID

- [ ] **Step 1: 创建 InternetUserId**

```java
package com.example.iam.types;

import com.example.shared.primitives.identity.Identifier;

public record InternetUserId(Long value) implements Identifier<Long> {
  public InternetUserId {
    if (value == null || value <= 0) {
      throw new IllegalArgumentException("InternetUserId must be positive");
    }
  }
  public static InternetUserId of(Long value) {
    return new InternetUserId(value);
  }
}
```

- [ ] **Step 2: 同样模式创建 HqUserId、BranchUserId、CredentialId、SecondaryAuthSessionId、LoginLogId**

均实现 `Identifier<Long>`，校验 `value > 0`，提供 `of(Long)` 工厂方法。

- [ ] **Step 3: 创建 AccountManagerCode**

```java
package com.example.iam.types;

import com.example.shared.primitives.identity.Identifier;

public record AccountManagerCode(String value) implements Identifier<String> {
  public AccountManagerCode {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("AccountManagerCode empty");
    }
  }
  public static AccountManagerCode of(String value) {
    return new AccountManagerCode(value);
  }
}
```

- [ ] **Step 4: 编译验证 + Commit**

```bash
mvn compile -pl iam-service/iam-types -am
git add iam-service/iam-types/src/
git commit -m "feat(iam-types): 新增强类型 ID（InternetUserId/HqUserId/BranchUserId 等）"
```

---

## Task 5: 创建认证上下文枚举类与错误码

**Files:**
- Create: `iam-service/iam-domain/src/main/java/com/example/iam/domain/shared/errorcode/IamCommonErrorCode.java`
- Create: `iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/aggregate/valueobject/ChannelType.java`
- Create: `iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/aggregate/valueobject/CredentialType.java`
- Create: `iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/aggregate/valueobject/UserStatus.java`
- Create: `iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/aggregate/valueobject/SecondaryAuthStatus.java`
- Create: `iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/aggregate/valueobject/SecondaryAuthStrategyType.java`
- Create: `iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/errorcode/IamAuthErrorCode.java`

**Interfaces:**
- Consumes: `com.example.shared.exception.ErrorDefinition`
- Produces: 5 个枚举 + 2 个错误码枚举

- [ ] **Step 1: 先查 shared-exception 中 ErrorDefinition 接口签名**

实施者请先查看 `demo-shared/shared-lib/shared-exception/src/main/java/com/example/shared/exception/ErrorDefinition.java` 和 `demo-shared/shared-lib/shared-domain/src/main/java/com/example/shared/domain/errorcode/SharedDomainErrorCode.java`，确认 ErrorDefinition 是接口还是 abstract class/record，以及方法名是 `code()`/`message()` 还是 `getCode()`/`getMessage()`。

- [ ] **Step 2: 创建枚举类**

```java
// ChannelType
public enum ChannelType { INTERNET, HQ, BRANCH }

// CredentialType（开闭原则扩展点）
public enum CredentialType { PASSWORD, UKEY, OTP, CERTIFICATE }

// UserStatus
public enum UserStatus { ACTIVE, DISABLED, LOCKED }

// SecondaryAuthStatus
public enum SecondaryAuthStatus { PENDING, COMPLETED, REVOKED, EXPIRED }

// SecondaryAuthStrategyType（开闭原则扩展点）
public enum SecondaryAuthStrategyType { CREDENTIAL, AUTHORIZATION_CODE, SCAN }
```

- [ ] **Step 3: 创建 IamCommonErrorCode 与 IamAuthErrorCode**

参考 `SharedDomainErrorCode` 模式。两个枚举的码值：

| 错误码枚举 | 常量 | 码 | 消息 |
|------------|------|-----|------|
| IamCommonErrorCode | EXTERNAL_SYSTEM_FAILURE | SERVICE.IAM.0071 | 外部系统调用失败 |
| IamCommonErrorCode | EXTERNAL_DATA_INVALID | SERVICE.IAM.0072 | 外部系统返回数据无效 |
| IamCommonErrorCode | UNKNOWN_ERROR | SERVICE.IAM.0099 | 未知错误 |
| IamAuthErrorCode | USER_NOT_FOUND | SERVICE.IAM.0001 | 用户不存在 |
| IamAuthErrorCode | CREDENTIAL_INVALID | SERVICE.IAM.0002 | 凭据无效 |
| IamAuthErrorCode | CREDENTIAL_EXPIRED | SERVICE.IAM.0003 | 凭据已过期 |
| IamAuthErrorCode | ACCOUNT_DISABLED | SERVICE.IAM.0004 | 账号已禁用 |
| IamAuthErrorCode | ACCOUNT_LOCKED | SERVICE.IAM.0005 | 账号已锁定 |
| IamAuthErrorCode | LOGIN_FAIL_LIMIT_EXCEEDED | SERVICE.IAM.0006 | 登录失败次数超限 |
| IamAuthErrorCode | SECONDARY_AUTH_SESSION_NOT_FOUND | SERVICE.IAM.0011 | 二次授权会话不存在 |
| IamAuthErrorCode | SECONDARY_AUTH_SESSION_EXPIRED | SERVICE.IAM.0012 | 二次授权会话已过期 |
| IamAuthErrorCode | SECONDARY_AUTH_SESSION_COMPLETED | SERVICE.IAM.0013 | 二次授权会话已完成 |
| IamAuthErrorCode | SECONDARY_AUTH_STRATEGY_NOT_SUPPORTED | SERVICE.IAM.0014 | 不支持的二次授权策略 |
| IamAuthErrorCode | NOT_BRANCH_USER_CANNOT_SWITCH_BACK | SERVICE.IAM.0015 | 当前身份非柜员，无法切换回柜员 |

> 实施者根据 Step 1 实际查到的 ErrorDefinition 签名调整 `code()` / `message()` 方法名。

- [ ] **Step 4: 编译验证 + Commit**

```bash
mvn compile -pl iam-service/iam-domain -am
git add iam-service/iam-domain/src/main/java/com/example/iam/domain/shared/ \
        iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/aggregate/valueobject/ \
        iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/errorcode/
git commit -m "feat(iam-domain): 新增认证上下文枚举与错误码定义"
```

---

## Task 6: 创建 InternetUser 聚合根（TDD）

**Files:**
- Create: `iam-service/iam-domain/src/test/java/com/example/iam/domain/authentication/aggregate/root/InternetUserTest.java`
- Create: `iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/aggregate/root/InternetUser.java`

**Interfaces:**
- Consumes: `AggregateRoot<InternetUserId>`、`UserNo`、`CustomerNo`、`UserStatus`、`Version`
- Produces: `InternetUser` 聚合根，含 `create()` / `disable()` / `enable()` / `lock()` / `recordLogin()` / `reconstitute()`

- [ ] **Step 1: 写失败测试 InternetUserTest**

测试用例：
- `create_should_return_active_user_with_correct_fields`：验证 create 工厂方法返回 ACTIVE 状态用户，字段正确，version=initial
- `disable_should_mark_user_disabled_and_increment_version`：验证 disable 修改 status 并递增 version
- `enable_should_mark_user_active`：验证 enable 后 status=ACTIVE
- `lock_should_mark_user_locked`：验证 lock 后 status=LOCKED
- `reconstitute_should_rebuild_user_from_persistence`：验证 reconstitute 重建所有字段
- `create_should_throw_when_login_name_blank`：验证 loginName 为空抛 IllegalArgumentException

完整测试代码：

```java
package com.example.iam.domain.authentication.aggregate.root;

import com.example.iam.domain.authentication.aggregate.valueobject.UserStatus;
import com.example.iam.types.InternetUserId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class InternetUserTest {

    @Test
    void create_should_return_active_user_with_correct_fields() {
        UserNo operator = UserNo.of("U001");
        InternetUser user = InternetUser.create(
            InternetUserId.of(1L), CustomerNo.of("C001"),
            "hr001", "张三", operator
        );

        assertEquals(InternetUserId.of(1L), user.id());
        assertEquals(CustomerNo.of("C001"), user.customerNo());
        assertEquals("hr001", user.loginName());
        assertEquals("张三", user.displayName());
        assertEquals(UserStatus.ACTIVE, user.status());
        assertEquals(operator, user.createdBy());
        assertEquals(Version.initial(), user.version());
    }

    @Test
    void disable_should_mark_user_disabled_and_increment_version() {
        InternetUser user = createActiveUser();
        Version oldVersion = user.version();

        user.disable(UserNo.of("U002"));

        assertEquals(UserStatus.DISABLED, user.status());
        assertTrue(user.version().value() > oldVersion.value());
    }

    @Test
    void enable_should_mark_user_active() {
        InternetUser user = createActiveUser();
        user.disable(UserNo.of("U002"));
        user.enable(UserNo.of("U003"));
        assertEquals(UserStatus.ACTIVE, user.status());
    }

    @Test
    void lock_should_mark_user_locked() {
        InternetUser user = createActiveUser();
        user.lock(UserNo.of("U002"));
        assertEquals(UserStatus.LOCKED, user.status());
    }

    @Test
    void reconstitute_should_rebuild_user_from_persistence() {
        LocalDateTime created = LocalDateTime.of(2026, 7, 25, 10, 0);
        LocalDateTime updated = LocalDateTime.of(2026, 7, 25, 11, 0);

        InternetUser user = InternetUser.reconstitute(
            InternetUserId.of(1L), CustomerNo.of("C001"), "hr001", "张三",
            UserStatus.ACTIVE, null, null,
            UserNo.of("U001"), UserNo.of("U002"),
            created, updated, Version.of(3L)
        );

        assertEquals(Version.of(3L), user.version());
        assertEquals(created, user.createdAt());
    }

    @Test
    void create_should_throw_when_login_name_blank() {
        assertThrows(IllegalArgumentException.class, () ->
            InternetUser.create(InternetUserId.of(1L), CustomerNo.of("C001"), "", "张三", UserNo.of("U001"))
        );
    }

    private InternetUser createActiveUser() {
        return InternetUser.create(InternetUserId.of(1L), CustomerNo.of("C001"), "hr001", "张三", UserNo.of("U001"));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl iam-service/iam-domain -am -Dtest=InternetUserTest`
Expected: FAIL（InternetUser 类不存在）

- [ ] **Step 3: 实现 InternetUser 聚合根**

```java
package com.example.iam.domain.authentication.aggregate.root;

import com.example.iam.domain.authentication.aggregate.valueobject.UserStatus;
import com.example.iam.types.InternetUserId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

/**
 * 网上渠道经办人账号聚合根
 *
 * <p>客户企业的 HR，通过互联网访问系统办理业务</p>
 */
public class InternetUser extends AggregateRoot<InternetUserId> {

    private final CustomerNo customerNo;
    private final String loginName;
    private final String displayName;
    private UserStatus status;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;

    private InternetUser(InternetUserId id, CustomerNo customerNo, String loginName,
                         String displayName, UserStatus status,
                         LocalDateTime lastLoginTime, String lastLoginIp,
                         UserNo createdBy, UserNo updatedBy,
                         LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        super(id, createdBy, updatedBy, createdAt, updatedAt, version);
        this.customerNo = customerNo;
        this.loginName = loginName;
        this.displayName = displayName;
        this.status = status;
        this.lastLoginTime = lastLoginTime;
        this.lastLoginIp = lastLoginIp;
        validateInvariants();
    }

    public static InternetUser create(InternetUserId id, CustomerNo customerNo,
                                      String loginName, String displayName, UserNo creator) {
        return new InternetUser(id, customerNo, loginName, displayName, UserStatus.ACTIVE,
            null, null, creator, creator, LocalDateTime.now(), LocalDateTime.now(), Version.initial());
    }

    public static InternetUser reconstitute(InternetUserId id, CustomerNo customerNo,
                                            String loginName, String displayName, UserStatus status,
                                            LocalDateTime lastLoginTime, String lastLoginIp,
                                            UserNo createdBy, UserNo updatedBy,
                                            LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        return new InternetUser(id, customerNo, loginName, displayName, status,
            lastLoginTime, lastLoginIp, createdBy, updatedBy, createdAt, updatedAt, version);
    }

    public void disable(UserNo operator) {
        this.status = UserStatus.DISABLED;
        markUpdated(operator);
    }

    public void enable(UserNo operator) {
        this.status = UserStatus.ACTIVE;
        markUpdated(operator);
    }

    public void lock(UserNo operator) {
        this.status = UserStatus.LOCKED;
        markUpdated(operator);
    }

    public void recordLogin(LocalDateTime loginTime, String ip) {
        this.lastLoginTime = loginTime;
        this.lastLoginIp = ip;
        markUpdated(this.updatedBy() != null ? this.updatedBy() : this.createdBy());
    }

    @Override
    protected void validateInvariants() {
        if (customerNo == null) throw new IllegalArgumentException("customerNo cannot be null");
        if (loginName == null || loginName.isBlank()) throw new IllegalArgumentException("loginName cannot be blank");
        if (status == null) throw new IllegalArgumentException("status cannot be null");
    }

    public CustomerNo customerNo() { return customerNo; }
    public String loginName() { return loginName; }
    public String displayName() { return displayName; }
    public UserStatus status() { return status; }
    public LocalDateTime lastLoginTime() { return lastLoginTime; }
    public String lastLoginIp() { return lastLoginIp; }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn test -pl iam-service/iam-domain -am -Dtest=InternetUserTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/aggregate/root/InternetUser.java \
        iam-service/iam-domain/src/test/java/com/example/iam/domain/authentication/aggregate/root/InternetUserTest.java
git commit -m "feat(iam-domain): 新增 InternetUser 聚合根"
```

---

## Task 7: 创建 HqUser 聚合根（TDD）

**Files:**
- Create: `iam-service/iam-domain/src/test/java/com/example/iam/domain/authentication/aggregate/root/HqUserTest.java`
- Create: `iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/aggregate/root/HqUser.java`

**Interfaces:**
- Consumes: `AggregateRoot<HqUserId>`、`UserNo`、`UserStatus`
- Produces: `HqUser` 聚合根，字段：staffNo、loginName、displayName、department、status、lastLoginTime、lastLoginIp

- [ ] **Step 1: 写失败测试 HqUserTest**

```java
package com.example.iam.domain.authentication.aggregate.root;

import com.example.iam.domain.authentication.aggregate.valueobject.UserStatus;
import com.example.iam.types.HqUserId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class HqUserTest {

    @Test
    void create_should_return_active_user_with_correct_fields() {
        UserNo operator = UserNo.of("U001");
        HqUser user = HqUser.create(
            HqUserId.of(1L), "S001", "admin001", "管理员", "IT", operator
        );

        assertEquals(HqUserId.of(1L), user.id());
        assertEquals("S001", user.staffNo());
        assertEquals("admin001", user.loginName());
        assertEquals("管理员", user.displayName());
        assertEquals("IT", user.department());
        assertEquals(UserStatus.ACTIVE, user.status());
        assertEquals(operator, user.createdBy());
        assertEquals(Version.initial(), user.version());
    }

    @Test
    void disable_should_mark_user_disabled_and_increment_version() {
        HqUser user = createActiveUser();
        Version oldVersion = user.version();

        user.disable(UserNo.of("U002"));

        assertEquals(UserStatus.DISABLED, user.status());
        assertTrue(user.version().value() > oldVersion.value());
    }

    @Test
    void enable_should_mark_user_active() {
        HqUser user = createActiveUser();
        user.disable(UserNo.of("U002"));
        user.enable(UserNo.of("U003"));
        assertEquals(UserStatus.ACTIVE, user.status());
    }

    @Test
    void reconstitute_should_rebuild_user_from_persistence() {
        LocalDateTime created = LocalDateTime.of(2026, 7, 25, 10, 0);
        LocalDateTime updated = LocalDateTime.of(2026, 7, 25, 11, 0);

        HqUser user = HqUser.reconstitute(
            HqUserId.of(1L), "S001", "admin001", "管理员", "IT",
            UserStatus.ACTIVE, null, null,
            UserNo.of("U001"), UserNo.of("U002"),
            created, updated, Version.of(3L)
        );

        assertEquals(Version.of(3L), user.version());
        assertEquals(created, user.createdAt());
        assertEquals("IT", user.department());
    }

    @Test
    void create_should_throw_when_login_name_blank() {
        assertThrows(IllegalArgumentException.class, () ->
            HqUser.create(HqUserId.of(1L), "S001", "", "管理员", "IT", UserNo.of("U001"))
        );
    }

    @Test
    void create_should_throw_when_staff_no_blank() {
        assertThrows(IllegalArgumentException.class, () ->
            HqUser.create(HqUserId.of(1L), "", "admin001", "管理员", "IT", UserNo.of("U001"))
        );
    }

    private HqUser createActiveUser() {
        return HqUser.create(HqUserId.of(1L), "S001", "admin001", "管理员", "IT", UserNo.of("U001"));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl iam-service/iam-domain -am -Dtest=HqUserTest`
Expected: FAIL（HqUser 类不存在）

- [ ] **Step 3: 实现 HqUser 聚合根**

```java
package com.example.iam.domain.authentication.aggregate.root;

import com.example.iam.domain.authentication.aggregate.valueobject.UserStatus;
import com.example.iam.types.HqUserId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

/**
 * 总部渠道运营人员账号聚合根
 *
 * <p>本公司运营人员，通过内网访问系统办理业务</p>
 */
public class HqUser extends AggregateRoot<HqUserId> {

    private final String staffNo;
    private final String loginName;
    private final String displayName;
    private final String department;
    private UserStatus status;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;

    private HqUser(HqUserId id, String staffNo, String loginName, String displayName, String department,
                   UserStatus status, LocalDateTime lastLoginTime, String lastLoginIp,
                   UserNo createdBy, UserNo updatedBy,
                   LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        super(id, createdBy, updatedBy, createdAt, updatedAt, version);
        this.staffNo = staffNo;
        this.loginName = loginName;
        this.displayName = displayName;
        this.department = department;
        this.status = status;
        this.lastLoginTime = lastLoginTime;
        this.lastLoginIp = lastLoginIp;
        validateInvariants();
    }

    public static HqUser create(HqUserId id, String staffNo, String loginName,
                                String displayName, String department, UserNo creator) {
        return new HqUser(id, staffNo, loginName, displayName, department, UserStatus.ACTIVE,
            null, null, creator, creator, LocalDateTime.now(), LocalDateTime.now(), Version.initial());
    }

    public static HqUser reconstitute(HqUserId id, String staffNo, String loginName, String displayName,
                                       String department, UserStatus status,
                                       LocalDateTime lastLoginTime, String lastLoginIp,
                                       UserNo createdBy, UserNo updatedBy,
                                       LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        return new HqUser(id, staffNo, loginName, displayName, department, status,
            lastLoginTime, lastLoginIp, createdBy, updatedBy, createdAt, updatedAt, version);
    }

    public void disable(UserNo operator) {
        this.status = UserStatus.DISABLED;
        markUpdated(operator);
    }

    public void enable(UserNo operator) {
        this.status = UserStatus.ACTIVE;
        markUpdated(operator);
    }

    public void recordLogin(LocalDateTime loginTime, String ip) {
        this.lastLoginTime = loginTime;
        this.lastLoginIp = ip;
        markUpdated(this.updatedBy() != null ? this.updatedBy() : this.createdBy());
    }

    @Override
    protected void validateInvariants() {
        if (staffNo == null || staffNo.isBlank()) throw new IllegalArgumentException("staffNo cannot be blank");
        if (loginName == null || loginName.isBlank()) throw new IllegalArgumentException("loginName cannot be blank");
        if (status == null) throw new IllegalArgumentException("status cannot be null");
    }

    public String staffNo() { return staffNo; }
    public String loginName() { return loginName; }
    public String displayName() { return displayName; }
    public String department() { return department; }
    public UserStatus status() { return status; }
    public LocalDateTime lastLoginTime() { return lastLoginTime; }
    public String lastLoginIp() { return lastLoginIp; }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn test -pl iam-service/iam-domain -am -Dtest=HqUserTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/aggregate/root/HqUser.java \
        iam-service/iam-domain/src/test/java/com/example/iam/domain/authentication/aggregate/root/HqUserTest.java
git commit -m "feat(iam-domain): 新增 HqUser 聚合根"
```

---

## Task 8: 创建 BranchUser 聚合根（TDD）

**Files:**
- Create: `iam-service/iam-domain/src/test/java/com/example/iam/domain/authentication/aggregate/root/BranchUserTest.java`
- Create: `iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/aggregate/root/BranchUser.java`

**Interfaces:**
- Consumes: `AggregateRoot<BranchUserId>`、`UserNo`、`UserStatus`
- Produces: `BranchUser` 聚合根，字段：bankCode、branchCode、tellerNo、loginName、displayName、status、lastLoginTime、lastLoginIp

- [ ] **Step 1: 写失败测试 BranchUserTest**

```java
package com.example.iam.domain.authentication.aggregate.root;

import com.example.iam.domain.authentication.aggregate.valueobject.UserStatus;
import com.example.iam.types.BranchUserId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class BranchUserTest {

    @Test
    void create_should_return_active_user_with_correct_fields() {
        UserNo operator = UserNo.of("U001");
        BranchUser user = BranchUser.create(
            BranchUserId.of(1L), "B001", "BR001", "T001", "teller001", "柜员", operator
        );

        assertEquals(BranchUserId.of(1L), user.id());
        assertEquals("B001", user.bankCode());
        assertEquals("BR001", user.branchCode());
        assertEquals("T001", user.tellerNo());
        assertEquals("teller001", user.loginName());
        assertEquals("柜员", user.displayName());
        assertEquals(UserStatus.ACTIVE, user.status());
        assertEquals(Version.initial(), user.version());
    }

    @Test
    void disable_should_mark_user_disabled_and_increment_version() {
        BranchUser user = createActiveUser();
        Version oldVersion = user.version();

        user.disable(UserNo.of("U002"));

        assertEquals(UserStatus.DISABLED, user.status());
        assertTrue(user.version().value() > oldVersion.value());
    }

    @Test
    void enable_should_mark_user_active() {
        BranchUser user = createActiveUser();
        user.disable(UserNo.of("U002"));
        user.enable(UserNo.of("U003"));
        assertEquals(UserStatus.ACTIVE, user.status());
    }

    @Test
    void reconstitute_should_rebuild_user_from_persistence() {
        LocalDateTime created = LocalDateTime.of(2026, 7, 25, 10, 0);
        LocalDateTime updated = LocalDateTime.of(2026, 7, 25, 11, 0);

        BranchUser user = BranchUser.reconstitute(
            BranchUserId.of(1L), "B001", "BR001", "T001", "teller001", "柜员",
            UserStatus.ACTIVE, null, null,
            UserNo.of("U001"), UserNo.of("U002"),
            created, updated, Version.of(3L)
        );

        assertEquals(Version.of(3L), user.version());
        assertEquals("B001", user.bankCode());
    }

    @Test
    void create_should_throw_when_login_name_blank() {
        assertThrows(IllegalArgumentException.class, () ->
            BranchUser.create(BranchUserId.of(1L), "B001", "BR001", "T001", "", "柜员", UserNo.of("U001"))
        );
    }

    @Test
    void create_should_throw_when_teller_no_blank() {
        assertThrows(IllegalArgumentException.class, () ->
            BranchUser.create(BranchUserId.of(1L), "B001", "BR001", "", "teller001", "柜员", UserNo.of("U001"))
        );
    }

    private BranchUser createActiveUser() {
        return BranchUser.create(BranchUserId.of(1L), "B001", "BR001", "T001", "teller001", "柜员", UserNo.of("U001"));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl iam-service/iam-domain -am -Dtest=BranchUserTest`
Expected: FAIL（BranchUser 类不存在）

- [ ] **Step 3: 实现 BranchUser 聚合根**

```java
package com.example.iam.domain.authentication.aggregate.root;

import com.example.iam.domain.authentication.aggregate.valueobject.UserStatus;
import com.example.iam.types.BranchUserId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

/**
 * 网点渠道柜员账号聚合根
 *
 * <p>合作银行的柜员，通过专线网络访问系统办理业务</p>
 */
public class BranchUser extends AggregateRoot<BranchUserId> {

    private final String bankCode;
    private final String branchCode;
    private final String tellerNo;
    private final String loginName;
    private final String displayName;
    private UserStatus status;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;

    private BranchUser(BranchUserId id, String bankCode, String branchCode, String tellerNo,
                       String loginName, String displayName,
                       UserStatus status, LocalDateTime lastLoginTime, String lastLoginIp,
                       UserNo createdBy, UserNo updatedBy,
                       LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        super(id, createdBy, updatedBy, createdAt, updatedAt, version);
        this.bankCode = bankCode;
        this.branchCode = branchCode;
        this.tellerNo = tellerNo;
        this.loginName = loginName;
        this.displayName = displayName;
        this.status = status;
        this.lastLoginTime = lastLoginTime;
        this.lastLoginIp = lastLoginIp;
        validateInvariants();
    }

    public static BranchUser create(BranchUserId id, String bankCode, String branchCode, String tellerNo,
                                    String loginName, String displayName, UserNo creator) {
        return new BranchUser(id, bankCode, branchCode, tellerNo, loginName, displayName,
            UserStatus.ACTIVE, null, null, creator, creator,
            LocalDateTime.now(), LocalDateTime.now(), Version.initial());
    }

    public static BranchUser reconstitute(BranchUserId id, String bankCode, String branchCode, String tellerNo,
                                          String loginName, String displayName,
                                          UserStatus status, LocalDateTime lastLoginTime, String lastLoginIp,
                                          UserNo createdBy, UserNo updatedBy,
                                          LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        return new BranchUser(id, bankCode, branchCode, tellerNo, loginName, displayName,
            status, lastLoginTime, lastLoginIp, createdBy, updatedBy, createdAt, updatedAt, version);
    }

    public void disable(UserNo operator) {
        this.status = UserStatus.DISABLED;
        markUpdated(operator);
    }

    public void enable(UserNo operator) {
        this.status = UserStatus.ACTIVE;
        markUpdated(operator);
    }

    public void recordLogin(LocalDateTime loginTime, String ip) {
        this.lastLoginTime = loginTime;
        this.lastLoginIp = ip;
        markUpdated(this.updatedBy() != null ? this.updatedBy() : this.createdBy());
    }

    @Override
    protected void validateInvariants() {
        if (bankCode == null || bankCode.isBlank()) throw new IllegalArgumentException("bankCode cannot be blank");
        if (tellerNo == null || tellerNo.isBlank()) throw new IllegalArgumentException("tellerNo cannot be blank");
        if (loginName == null || loginName.isBlank()) throw new IllegalArgumentException("loginName cannot be blank");
        if (status == null) throw new IllegalArgumentException("status cannot be null");
    }

    public String bankCode() { return bankCode; }
    public String branchCode() { return branchCode; }
    public String tellerNo() { return tellerNo; }
    public String loginName() { return loginName; }
    public String displayName() { return displayName; }
    public UserStatus status() { return status; }
    public LocalDateTime lastLoginTime() { return lastLoginTime; }
    public String lastLoginIp() { return lastLoginIp; }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn test -pl iam-service/iam-domain -am -Dtest=BranchUserTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/aggregate/root/BranchUser.java \
        iam-service/iam-domain/src/test/java/com/example/iam/domain/authentication/aggregate/root/BranchUserTest.java
git commit -m "feat(iam-domain): 新增 BranchUser 聚合根"
```

---

## Task 9: 创建 Credential 聚合根（TDD）

**Files:**
- Create: `iam-service/iam-domain/src/test/java/com/example/iam/domain/authentication/aggregate/root/CredentialTest.java`
- Create: `iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/aggregate/root/Credential.java`

**Interfaces:**
- Consumes: `AggregateRoot<CredentialId>`、`UserNo`、`CredentialType`、`UserStatus`、`CredentialValidator`（Task 10 创建）
- Produces: `Credential` 聚合根，含 `create()` / `createWithOwner()` / `verify(input, validator)` / `changeSecret()` / `disable()` / `reconstitute()`

- [ ] **Step 1: 写失败测试 CredentialTest**

测试用例：
- `create_should_return_active_credential`：验证 create 返回 ACTIVE 状态凭据
- `changeSecret_should_update_secret_and_increment_version`：验证 changeSecret 修改 secret 并递增 version
- `disable_should_mark_credential_disabled`：验证 disable 后 status=DISABLED

完整测试代码：

```java
package com.example.iam.domain.authentication.aggregate.root;

import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import com.example.iam.domain.authentication.aggregate.valueobject.UserStatus;
import com.example.iam.types.CredentialId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CredentialTest {

    @Test
    void create_should_return_active_credential() {
        UserNo owner = UserNo.of("U001");
        Credential credential = Credential.create(
            CredentialId.of(1L), CredentialType.PASSWORD,
            "hashed-secret", "salt-value", owner
        );

        assertEquals(CredentialId.of(1L), credential.id());
        assertEquals(CredentialType.PASSWORD, credential.credentialType());
        assertEquals("hashed-secret", credential.secret());
        assertEquals(UserStatus.ACTIVE, credential.status());
    }

    @Test
    void changeSecret_should_update_secret_and_increment_version() {
        Credential credential = createActiveCredential();
        long oldVersion = credential.version().value();

        credential.changeSecret("new-secret", "new-salt", UserNo.of("U002"));

        assertEquals("new-secret", credential.secret());
        assertEquals("new-salt", credential.salt());
        assertTrue(credential.version().value() > oldVersion);
    }

    @Test
    void disable_should_mark_credential_disabled() {
        Credential credential = createActiveCredential();
        credential.disable(UserNo.of("U002"));
        assertEquals(UserStatus.DISABLED, credential.status());
    }

    private Credential createActiveCredential() {
        return Credential.create(CredentialId.of(1L), CredentialType.PASSWORD,
            "hashed-secret", "salt-value", UserNo.of("U001"));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -pl iam-service/iam-domain -am -Dtest=CredentialTest`
Expected: FAIL（Credential 与 CredentialValidator 都不存在）

- [ ] **Step 3: 暂不实现，先去 Task 10 创建 CredentialValidator 接口**

---

## Task 10: 创建 CredentialValidator 策略 + PasswordCredentialValidator 实现（TDD）

**Files:**
- Create: `iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/strategy/CredentialValidator.java`
- Create: `iam-service/iam-domain/src/test/java/com/example/iam/domain/authentication/strategy/PasswordCredentialValidatorTest.java`
- Create: `iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/strategy/PasswordCredentialValidator.java`
- Modify: `iam-service/iam-domain/pom.xml`（添加 jbcrypt 依赖）

**Interfaces:**
- Produces: `CredentialValidator` 策略接口 + `PasswordCredentialValidator` 默认实现

- [ ] **Step 1: 在 iam-domain/pom.xml 添加 jbcrypt 依赖**

```xml
    <dependency>
      <groupId>org.mindrot</groupId>
      <artifactId>jbcrypt</artifactId>
    </dependency>
```

- [ ] **Step 2: 创建 CredentialValidator 策略接口**

```java
package com.example.iam.domain.authentication.strategy;

import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;

/**
 * 凭据验证策略接口（开闭原则扩展点）
 *
 * <p>每种凭据类型对应一个实现：
 * <ul>
 *   <li>PASSWORD → PasswordCredentialValidator（BCrypt）</li>
 *   <li>UKEY → UKeyCredentialValidator（未来）</li>
 *   <li>OTP → OTPCredentialValidator（未来）</li>
 * </ul>
 */
public interface CredentialValidator {

    /**
     * 验证用户输入的凭据是否匹配存储的凭据
     *
     * @param input          用户输入的凭据（如明文密码）
     * @param storedSecret   存储的凭据密文（如 BCrypt hash）
     * @param salt           盐值（部分算法可能不用）
     * @param credentialType 凭据类型
     * @return true 如果验证通过
     */
    boolean verify(String input, String storedSecret, String salt, CredentialType credentialType);

    /**
     * 该策略支持的凭据类型
     */
    CredentialType supportedType();
}
```

- [ ] **Step 3: 写失败测试 PasswordCredentialValidatorTest**

```java
package com.example.iam.domain.authentication.strategy;

import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

import static org.junit.jupiter.api.Assertions.*;

class PasswordCredentialValidatorTest {

    @Test
    void verify_should_return_true_when_password_matches() {
        String plain = "myPassword123";
        String hash = BCrypt.hashpw(plain, BCrypt.gensalt());
        PasswordCredentialValidator validator = new PasswordCredentialValidator();

        boolean result = validator.verify(plain, hash, null, CredentialType.PASSWORD);

        assertTrue(result);
    }

    @Test
    void verify_should_return_false_when_password_not_matches() {
        String hash = BCrypt.hashpw("correctPassword", BCrypt.gensalt());
        PasswordCredentialValidator validator = new PasswordCredentialValidator();

        boolean result = validator.verify("wrongPassword", hash, null, CredentialType.PASSWORD);

        assertFalse(result);
    }

    @Test
    void supportedType_should_return_password() {
        assertEquals(CredentialType.PASSWORD, new PasswordCredentialValidator().supportedType());
    }

    @Test
    void hashPassword_should_return_bcrypt_hash() {
        String hash = PasswordCredentialValidator.hashPassword("myPassword");

        assertNotNull(hash);
        assertTrue(hash.startsWith("$2a$"));
        assertTrue(BCrypt.checkpw("myPassword", hash));
    }
}
```

- [ ] **Step 4: 运行测试验证失败**

Run: `mvn test -pl iam-service/iam-domain -am -Dtest=PasswordCredentialValidatorTest`
Expected: FAIL

- [ ] **Step 5: 实现 PasswordCredentialValidator**

```java
package com.example.iam.domain.authentication.strategy;

import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import org.mindrot.jbcrypt.BCrypt;

/**
 * 密码凭据验证器（默认实现，使用 BCrypt）
 *
 * <p>BCrypt 自带盐，存储的 secret 即 BCrypt hash，salt 字段未使用</p>
 */
public class PasswordCredentialValidator implements CredentialValidator {

    @Override
    public boolean verify(String input, String storedSecret, String salt, CredentialType credentialType) {
        if (input == null || input.isBlank() || storedSecret == null || storedSecret.isBlank()) {
            return false;
        }
        if (credentialType != CredentialType.PASSWORD) {
            return false;
        }
        try {
            return BCrypt.checkpw(input, storedSecret);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public CredentialType supportedType() {
        return CredentialType.PASSWORD;
    }

    /**
     * 对明文密码进行 BCrypt 哈希
     */
    public static String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isBlank()) {
            throw new IllegalArgumentException("plainPassword cannot be blank");
        }
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt());
    }
}
```

- [ ] **Step 6: 运行测试验证通过**

Run: `mvn test -pl iam-service/iam-domain -am -Dtest=PasswordCredentialValidatorTest`
Expected: PASS

- [ ] **Step 7: 实现 Credential 聚合根（Task 9 Step 3）**

```java
package com.example.iam.domain.authentication.aggregate.root;

import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import com.example.iam.domain.authentication.aggregate.valueobject.UserStatus;
import com.example.iam.domain.authentication.strategy.CredentialValidator;
import com.example.iam.types.CredentialId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

/**
 * 凭据聚合根
 *
 * <p>一个账号可有多条凭据（不同类型），类型通过 CredentialType 枚举扩展，
 * 验证逻辑通过 CredentialValidator 策略接口扩展（开闭原则）</p>
 */
public class Credential extends AggregateRoot<CredentialId> {

    private final String ownerType;     // INTERNET_USER / HQ_USER / BRANCH_USER
    private final Long ownerId;
    private final CredentialType credentialType;
    private String secret;
    private String salt;
    private UserStatus status;
    private LocalDateTime lastChangedAt;

    private Credential(CredentialId id, String ownerType, Long ownerId, CredentialType credentialType,
                      String secret, String salt, UserStatus status, LocalDateTime lastChangedAt,
                      UserNo createdBy, UserNo updatedBy,
                      LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        super(id, createdBy, updatedBy, createdAt, updatedAt, version);
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.credentialType = credentialType;
        this.secret = secret;
        this.salt = salt;
        this.status = status;
        this.lastChangedAt = lastChangedAt;
        validateInvariants();
    }

    public static Credential create(CredentialId id, CredentialType credentialType,
                                    String secret, String salt, UserNo creator) {
        return new Credential(id, null, null, credentialType, secret, salt,
            UserStatus.ACTIVE, LocalDateTime.now(),
            creator, creator, LocalDateTime.now(), LocalDateTime.now(), Version.initial());
    }

    public static Credential createWithOwner(CredentialId id, String ownerType, Long ownerId,
                                             CredentialType credentialType,
                                             String secret, String salt, UserNo creator) {
        return new Credential(id, ownerType, ownerId, credentialType, secret, salt,
            UserStatus.ACTIVE, LocalDateTime.now(),
            creator, creator, LocalDateTime.now(), LocalDateTime.now(), Version.initial());
    }

    public static Credential reconstitute(CredentialId id, String ownerType, Long ownerId,
                                          CredentialType credentialType,
                                          String secret, String salt, UserStatus status,
                                          LocalDateTime lastChangedAt,
                                          UserNo createdBy, UserNo updatedBy,
                                          LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        return new Credential(id, ownerType, ownerId, credentialType, secret, salt, status, lastChangedAt,
            createdBy, updatedBy, createdAt, updatedAt, version);
    }

    /**
     * 验证输入的凭据是否匹配
     */
    public boolean verify(String input, CredentialValidator validator) {
        if (status != UserStatus.ACTIVE) {
            return false;
        }
        return validator.verify(input, this.secret, this.salt, this.credentialType);
    }

    public void changeSecret(String newSecret, String newSalt, UserNo operator) {
        if (newSecret == null || newSecret.isBlank()) {
            throw new IllegalArgumentException("newSecret cannot be blank");
        }
        this.secret = newSecret;
        this.salt = newSalt;
        this.lastChangedAt = LocalDateTime.now();
        markUpdated(operator);
    }

    public void disable(UserNo operator) {
        this.status = UserStatus.DISABLED;
        markUpdated(operator);
    }

    @Override
    protected void validateInvariants() {
        if (credentialType == null) throw new IllegalArgumentException("credentialType cannot be null");
        if (secret == null || secret.isBlank()) throw new IllegalArgumentException("secret cannot be blank");
        if (status == null) throw new IllegalArgumentException("status cannot be null");
    }

    public String ownerType() { return ownerType; }
    public Long ownerId() { return ownerId; }
    public CredentialType credentialType() { return credentialType; }
    public String secret() { return secret; }
    public String salt() { return salt; }
    public UserStatus status() { return status; }
    public LocalDateTime lastChangedAt() { return lastChangedAt; }
}
```

- [ ] **Step 8: 回到 Task 9 运行 CredentialTest 验证通过**

Run: `mvn test -pl iam-service/iam-domain -am -Dtest=CredentialTest`
Expected: PASS

- [ ] **Step 9: Commit（Task 9 + Task 10）**

```bash
git add iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/strategy/ \
        iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/aggregate/root/Credential.java \
        iam-service/iam-domain/src/test/java/com/example/iam/domain/authentication/strategy/PasswordCredentialValidatorTest.java \
        iam-service/iam-domain/src/test/java/com/example/iam/domain/authentication/aggregate/root/CredentialTest.java \
        iam-service/iam-domain/pom.xml
git commit -m "feat(iam-domain): 新增 Credential 聚合根与 PasswordCredentialValidator 策略"
```

---

## Task 11: 创建 SecondaryAuthSession 聚合根（TDD）

**Files:**
- Create: `iam-service/iam-domain/src/test/java/com/example/iam/domain/authentication/aggregate/root/SecondaryAuthSessionTest.java`
- Create: `iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/aggregate/root/SecondaryAuthSession.java`

**Interfaces:**
- Consumes: `AggregateRoot<SecondaryAuthSessionId>`、`BranchUserId`、`InternetUserId`、`SecondaryAuthStatus`、`SecondaryAuthStrategyType`
- Produces: `SecondaryAuthSession` 聚合根，方法：`initiate()` / `complete()` / `revoke()` / `isExpired()` / `reconstitute()`

- [ ] **Step 1: 写失败测试 SecondaryAuthSessionTest**

测试用例：
- `initiate_should_return_pending_session`：验证 initiate 返回 PENDING 状态，isExpired(now)=false，isExpired(now+31min)=true
- `complete_should_mark_session_completed`：验证 complete 后 status=COMPLETED，completedAt 非空
- `complete_should_throw_when_session_expired`：验证过期会话 complete 抛 IllegalStateException
- `revoke_should_mark_session_revoked`
- `complete_should_throw_when_session_already_completed`

- [ ] **Step 2: 运行测试验证失败**

- [ ] **Step 3: 实现 SecondaryAuthSession 聚合根**

字段：branchUserId、internetUserId、strategyType、expiresAt、status、completedAt、actingThroughRef。
方法：`initiate()` / `complete()` / `revoke()` / `isExpired(now)` / `reconstitute()`。

业务规则：
- complete 时校验 status != COMPLETED && status != REVOKED && !isExpired(now)，否则抛 IllegalStateException
- revoke 时如果已 COMPLETED 抛异常，已 REVOKED 直接返回

- [ ] **Step 4: 运行测试验证通过 + Commit**

```bash
git commit -m "feat(iam-domain): 新增 SecondaryAuthSession 聚合根"
```

---

## Task 12: 创建 SecondaryAuthStrategy 策略 + CredentialSecondaryAuthStrategy 实现

**Files:**
- Create: `iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/strategy/SecondaryAuthStrategy.java`
- Create: `iam-service/iam-domain/src/test/java/com/example/iam/domain/authentication/strategy/CredentialSecondaryAuthStrategyTest.java`
- Create: `iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/strategy/CredentialSecondaryAuthStrategy.java`

**Interfaces:**
- Consumes: `SecondaryAuthSession`、`Credential`、`CredentialValidator`、`CredentialType`、`UserStatus`
- Produces: `SecondaryAuthStrategy` 策略接口 + `CredentialSecondaryAuthStrategy` 默认实现

- [ ] **Step 1: 创建 SecondaryAuthStrategy 接口**

```java
package com.example.iam.domain.authentication.strategy;

import com.example.iam.domain.authentication.aggregate.root.Credential;
import com.example.iam.domain.authentication.aggregate.root.SecondaryAuthSession;
import com.example.iam.domain.authentication.aggregate.valueobject.SecondaryAuthStrategyType;

import java.util.List;

/**
 * 二次授权策略接口（开闭原则扩展点）
 *
 * <p>每种二次授权方式对应一个实现：
 * <ul>
 *   <li>CREDENTIAL → CredentialSecondaryAuthStrategy（凭据验证，默认实现）</li>
 *   <li>AUTHORIZATION_CODE → AuthorizationCodeSecondaryAuthStrategy（未来，授权码）</li>
 *   <li>SCAN → ScanSecondaryAuthStrategy（未来，扫码）</li>
 * </ul>
 */
public interface SecondaryAuthStrategy {

    /**
     * 执行二次授权验证
     *
     * @param session       二次授权会话
     * @param input         用户输入（如密码、授权码）
     * @param credentials   目标用户的所有凭据
     * @param validator     凭据验证器
     * @return true 如果验证通过
     */
    boolean authenticate(SecondaryAuthSession session, String input,
                         List<Credential> credentials, CredentialValidator validator);

    /**
     * 该策略支持的二次授权类型
     */
    SecondaryAuthStrategyType supportedType();
}
```

- [ ] **Step 2: 写失败测试 CredentialSecondaryAuthStrategyTest**

```java
package com.example.iam.domain.authentication.strategy;

import com.example.iam.domain.authentication.aggregate.root.Credential;
import com.example.iam.domain.authentication.aggregate.root.SecondaryAuthSession;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import com.example.iam.domain.authentication.aggregate.valueobject.SecondaryAuthStrategyType;
import com.example.iam.domain.authentication.aggregate.valueobject.SecondaryAuthStatus;
import com.example.iam.domain.authentication.aggregate.valueobject.UserStatus;
import com.example.iam.types.BranchUserId;
import com.example.iam.types.CredentialId;
import com.example.iam.types.InternetUserId;
import com.example.iam.types.SecondaryAuthSessionId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CredentialSecondaryAuthStrategyTest {

    @Test
    void authenticate_should_return_true_when_password_matches() {
        String plain = "HrPwd123";
        String hash = BCrypt.hashpw(plain, BCrypt.gensalt());
        Credential credential = Credential.reconstitute(
            CredentialId.of(1L), "INTERNET_USER", 100L, CredentialType.PASSWORD,
            hash, null, UserStatus.ACTIVE, LocalDateTime.now(),
            UserNo.of("U001"), UserNo.of("U001"),
            LocalDateTime.now(), LocalDateTime.now(),
            com.example.shared.domain.aggregate.valueobject.Version.initial()
        );
        SecondaryAuthSession session = createPendingSession();
        CredentialSecondaryAuthStrategy strategy = new CredentialSecondaryAuthStrategy();
        PasswordCredentialValidator validator = new PasswordCredentialValidator();

        boolean result = strategy.authenticate(session, plain, List.of(credential), validator);

        assertTrue(result);
    }

    @Test
    void authenticate_should_return_false_when_password_not_matches() {
        String hash = BCrypt.hashpw("CorrectPwd", BCrypt.gensalt());
        Credential credential = Credential.reconstitute(
            CredentialId.of(1L), "INTERNET_USER", 100L, CredentialType.PASSWORD,
            hash, null, UserStatus.ACTIVE, LocalDateTime.now(),
            UserNo.of("U001"), UserNo.of("U001"),
            LocalDateTime.now(), LocalDateTime.now(),
            com.example.shared.domain.aggregate.valueobject.Version.initial()
        );
        SecondaryAuthSession session = createPendingSession();
        CredentialSecondaryAuthStrategy strategy = new CredentialSecondaryAuthStrategy();
        PasswordCredentialValidator validator = new PasswordCredentialValidator();

        boolean result = strategy.authenticate(session, "WrongPwd", List.of(credential), validator);

        assertFalse(result);
    }

    @Test
    void authenticate_should_return_false_when_no_password_credential() {
        // 仅有 UKEY 凭据，无 PASSWORD 凭据
        Credential ukeyCredential = Credential.reconstitute(
            CredentialId.of(1L), "INTERNET_USER", 100L, CredentialType.UKEY,
            "ukey-data", "salt", UserStatus.ACTIVE, LocalDateTime.now(),
            UserNo.of("U001"), UserNo.of("U001"),
            LocalDateTime.now(), LocalDateTime.now(),
            com.example.shared.domain.aggregate.valueobject.Version.initial()
        );
        SecondaryAuthSession session = createPendingSession();
        CredentialSecondaryAuthStrategy strategy = new CredentialSecondaryAuthStrategy();
        PasswordCredentialValidator validator = new PasswordCredentialValidator();

        boolean result = strategy.authenticate(session, "anyInput", List.of(ukeyCredential), validator);

        assertFalse(result);
    }

    @Test
    void supportedType_should_return_credential() {
        assertEquals(SecondaryAuthStrategyType.CREDENTIAL,
            new CredentialSecondaryAuthStrategy().supportedType());
    }

    private SecondaryAuthSession createPendingSession() {
        return SecondaryAuthSession.reconstitute(
            SecondaryAuthSessionId.of(1L),
            BranchUserId.of(2L), InternetUserId.of(100L),
            SecondaryAuthStrategyType.CREDENTIAL,
            LocalDateTime.now().plusMinutes(30),
            SecondaryAuthStatus.PENDING, null, null,
            UserNo.of("U002"), UserNo.of("U002"),
            LocalDateTime.now(), LocalDateTime.now(),
            com.example.shared.domain.aggregate.valueobject.Version.initial()
        );
    }
}
```

- [ ] **Step 3: 运行测试验证失败**

Run: `mvn test -pl iam-service/iam-domain -am -Dtest=CredentialSecondaryAuthStrategyTest`
Expected: FAIL（CredentialSecondaryAuthStrategy 类不存在）

- [ ] **Step 4: 实现 CredentialSecondaryAuthStrategy**

```java
package com.example.iam.domain.authentication.strategy;

import com.example.iam.domain.authentication.aggregate.root.Credential;
import com.example.iam.domain.authentication.aggregate.root.SecondaryAuthSession;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import com.example.iam.domain.authentication.aggregate.valueobject.SecondaryAuthStrategyType;
import com.example.iam.domain.authentication.aggregate.valueobject.UserStatus;

import java.util.List;

/**
 * 凭据二次授权策略（默认实现）
 *
 * <p>从目标用户（internetUserId 对应的账号）的凭据列表中筛选 PASSWORD 类型且 ACTIVE 状态的凭据，
 * 调用 CredentialValidator 验证。任一凭据验证通过即返回 true。</p>
 */
public class CredentialSecondaryAuthStrategy implements SecondaryAuthStrategy {

    @Override
    public boolean authenticate(SecondaryAuthSession session, String input,
                                List<Credential> credentials, CredentialValidator validator) {
        if (input == null || input.isBlank() || credentials == null || credentials.isEmpty()) {
            return false;
        }
        return credentials.stream()
            .filter(c -> c.credentialType() == CredentialType.PASSWORD)
            .filter(c -> c.status() == UserStatus.ACTIVE)
            .anyMatch(c -> c.verify(input, validator));
    }

    @Override
    public SecondaryAuthStrategyType supportedType() {
        return SecondaryAuthStrategyType.CREDENTIAL;
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn test -pl iam-service/iam-domain -am -Dtest=CredentialSecondaryAuthStrategyTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/strategy/SecondaryAuthStrategy.java \
        iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/strategy/CredentialSecondaryAuthStrategy.java \
        iam-service/iam-domain/src/test/java/com/example/iam/domain/authentication/strategy/CredentialSecondaryAuthStrategyTest.java
git commit -m "feat(iam-domain): 新增 SecondaryAuthStrategy 策略与凭据二次授权实现"
```

---

## Task 13: 创建认证领域事件

**Files:**
- Create: `iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/event/UserLoggedInEvent.java`
- Create: `iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/event/UserLoginFailedEvent.java`
- Create: `iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/event/UserLoggedOutEvent.java`
- Create: `iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/event/CredentialChangedEvent.java`
- Create: `iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/event/SecondaryAuthCompletedEvent.java`
- Create: `iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/event/SecondaryAuthRevokedEvent.java`

**Interfaces:**
- Consumes: `DomainEvent`、`EventId`（已有 `EventId.generate()` 静态方法）
- Produces: 6 个 record 类型领域事件，均实现 `DomainEvent`，提供 `of()` 工厂方法

- [ ] **Step 1: 创建 6 个领域事件**

每个事件 record 包含：`EventId eventId`、`LocalDateTime occurredOn`、业务字段。提供 `of()` 静态方法，内部通过 `EventId.generate()` 和 `LocalDateTime.now()` 设置事件ID和时间。

事件字段：
- `UserLoggedInEvent`: userId, channel, ipAddress, userAgent
- `UserLoginFailedEvent`: loginName, channel, failReason, ipAddress
- `UserLoggedOutEvent`: userId, channel
- `CredentialChangedEvent`: userId, credentialType, changedBy
- `SecondaryAuthCompletedEvent`: sessionId, branchUserId, internetUserId, strategyType
- `SecondaryAuthRevokedEvent`: sessionId, revokedBy

- [ ] **Step 2: 编译验证 + Commit**

```bash
git commit -m "feat(iam-domain): 新增认证上下文 6 个领域事件"
```

---

## Task 14: 创建 Repository 接口与 LoginHook 接口

**Files:**
- Create: `iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/repository/InternetUserRepository.java`
- Create: `iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/repository/HqUserRepository.java`
- Create: `iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/repository/BranchUserRepository.java`
- Create: `iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/repository/CredentialRepository.java`
- Create: `iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/repository/SecondaryAuthSessionRepository.java`
- Create: `iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/repository/LoginLogRepository.java`
- Create: `iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/hook/LoginHook.java`

**Interfaces:**
- Consumes: `Repository<T, ID>`、各聚合根、ID 类型
- Produces: 6 个 Repository 接口 + 1 个 Hook 接口

- [ ] **Step 1: 创建 6 个 Repository 接口**

```java
// InternetUserRepository
public interface InternetUserRepository extends Repository<InternetUser, InternetUserId> {
    Optional<InternetUser> findByLoginName(String loginName);
    List<InternetUser> findByCustomerNo(CustomerNo customerNo);
}

// HqUserRepository
public interface HqUserRepository extends Repository<HqUser, HqUserId> {
    Optional<HqUser> findByLoginName(String loginName);
}

// BranchUserRepository
public interface BranchUserRepository extends Repository<BranchUser, BranchUserId> {
    Optional<BranchUser> findByLoginName(String loginName);
}

// CredentialRepository
public interface CredentialRepository extends Repository<Credential, CredentialId> {
    List<Credential> findByOwner(String ownerType, Long ownerId);
    List<Credential> findByOwnerAndType(String ownerType, Long ownerId, CredentialType credentialType);
}

// SecondaryAuthSessionRepository
public interface SecondaryAuthSessionRepository extends Repository<SecondaryAuthSession, SecondaryAuthSessionId> {
    List<SecondaryAuthSession> findActiveByBranchUser(BranchUserId branchUserId);
}
```

LoginLogRepository 是流水类记录（参考 user_profile 偏好），不继承 Repository，仅支持追加：

```java
public interface LoginLogRepository {
    void append(String userType, Long userId, String loginName, ChannelType channel,
                String loginResult, String failReason,
                LocalDateTime loginTime, String ipAddress, String userAgent,
                String createdBy);
    long countFailuresSince(String loginName, ChannelType channel, LocalDateTime since);
}
```

- [ ] **Step 2: 创建 LoginHook 接口**

```java
public interface LoginHook {
    default void preLogin(LoginContext ctx) {}
    default void postLoginSuccess(LoginSuccessContext ctx) {}
    default void postLoginFailure(LoginFailureContext ctx) {}

    LoginHook NO_OP = new LoginHook() {};

    record LoginContext(String loginName, ChannelType channel, String ipAddress, String userAgent,
                        Map<String, Object> attributes) {}
    record LoginSuccessContext(Long userId, ChannelType channel, String tokenValue,
                              String ipAddress, String userAgent) {}
    record LoginFailureContext(String loginName, ChannelType channel, String failReason,
                              String ipAddress, String userAgent) {}
}
```

- [ ] **Step 3: 编译验证 + Commit**

```bash
git commit -m "feat(iam-domain): 新增认证上下文 Repository 接口与 LoginHook 接口"
```

---

## Task 15: 创建 LoginService 领域服务（TDD）

**Files:**
- Create: `iam-service/iam-domain/src/test/java/com/example/iam/domain/authentication/service/LoginServiceTest.java`
- Create: `iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/service/LoginService.java`

**Interfaces:**
- Consumes: 各 UserRepository、CredentialRepository、LoginLogRepository、PasswordCredentialValidator、LoginHook
- Produces: `LoginService`，方法 `login(channel, loginName, credentialInput, ctx): LoginResult`

- [ ] **Step 1: 写失败测试 LoginServiceTest**

测试用例：
- `login_should_return_success_when_internet_user_credential_matches`：Mock userRepository.findByLoginName + credentialRepository.findByOwnerAndType 返回匹配凭据，验证 LoginResult.success=true 且 userId 正确
- `login_should_return_failure_when_user_not_found`
- `login_should_return_failure_when_credential_mismatch`
- `login_should_return_failure_when_user_disabled`
- `login_should_call_postLoginFailure_hook_when_failed`：验证 hook.postLoginFailure 被调用

- [ ] **Step 2: 运行测试验证失败**

- [ ] **Step 3: 实现 LoginService**

```java
@DomainService
public class LoginService {
    // 构造函数注入：internetUserRepository, hqUserRepository, branchUserRepository,
    //               credentialRepository, loginLogRepository, passwordValidator, loginHook

    public LoginResult login(ChannelType channel, String loginName, String credentialInput,
                            LoginHook.LoginContext ctx) {
        loginHook.preLogin(ctx);

        LoginResult result = switch (channel) {
            case INTERNET -> loginInternet(loginName, credentialInput, ctx);
            case HQ -> loginHq(loginName, credentialInput, ctx);
            case BRANCH -> loginBranch(loginName, credentialInput, ctx);
        };

        if (result.success()) {
            loginHook.postLoginSuccess(new LoginHook.LoginSuccessContext(
                result.userId(), channel, null, ctx.ipAddress(), ctx.userAgent()
            ));
        } else {
            loginHook.postLoginFailure(new LoginHook.LoginFailureContext(
                loginName, channel, result.failReason(), ctx.ipAddress(), ctx.userAgent()
            ));
        }
        return result;
    }

    // loginInternet/loginHq/loginBranch 私有方法：
    // 1. findByLoginName，未找到返回 failure("USER_NOT_FOUND")
    // 2. 校验 status，DISABLED 返回 "ACCOUNT_DISABLED"，LOCKED 返回 "ACCOUNT_LOCKED"
    // 3. credentialRepository.findByOwnerAndType 查找 PASSWORD 凭据
    // 4. 遍历凭据调用 verify(input, passwordValidator)，任一成功即通过
    // 5. 验证失败返回 "CREDENTIAL_INVALID"
    // 6. 成功时 user.recordLogin(now, ip) + userRepository.save(user)
    // 7. 返回 LoginResult.success(userId, channel)

    public record LoginResult(boolean success, Long userId, ChannelType channel, String failReason) {
        public static LoginResult success(Long userId, ChannelType channel) { ... }
        public static LoginResult failure(String failReason) { ... }
    }
}
```

- [ ] **Step 4: 运行测试验证通过 + Commit**

```bash
git commit -m "feat(iam-domain): 新增 LoginService 领域服务"
```

---

## Task 16: 创建 SecondaryAuthService 领域服务（TDD）

**Files:**
- Create: `iam-service/iam-domain/src/test/java/com/example/iam/domain/authentication/service/SecondaryAuthServiceTest.java`
- Create: `iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/service/SecondaryAuthService.java`

**Interfaces:**
- Consumes: `SecondaryAuthSessionRepository`、`SecondaryAuthStrategy`、`CredentialRepository`、`CredentialValidator`
- Produces: `SecondaryAuthService`，方法：`initiate()` / `complete()` / `revoke()`

- [ ] **Step 1: 写失败测试 SecondaryAuthServiceTest**

测试用例：
- `initiate_should_save_pending_session`
- `complete_should_return_true_when_credential_matches`
- `complete_should_return_false_when_session_not_found`

- [ ] **Step 2: 运行测试验证失败**

- [ ] **Step 3: 实现 SecondaryAuthService**

```java
@DomainService
public class SecondaryAuthService {
    // 构造注入：sessionRepository, strategy, credentialValidator, credentialRepository

    public SecondaryAuthSession initiate(BranchUserId branchUserId, InternetUserId internetUserId,
                                        SecondaryAuthStrategyType strategyType,
                                        LocalDateTime expiresAt, UserNo initiator) {
        // 生成 SecondaryAuthSessionId，调用 SecondaryAuthSession.initiate()，save 并返回
    }

    public boolean complete(SecondaryAuthSessionId sessionId, String input, UserNo operator) {
        // 1. sessionRepository.load(sessionId)，不存在返回 false
        // 2. session.isExpired(now) 返回 false
        // 3. credentialRepository.findByOwner("INTERNET_USER", internetUserId.value())
        // 4. strategy.authenticate(session, input, credentials, credentialValidator)
        // 5. 验证失败返回 false，成功时 session.complete(operator) + save，返回 true
    }

    public void revoke(SecondaryAuthSessionId sessionId, UserNo operator) {
        // load + revoke + save
    }
}
```

- [ ] **Step 4: 运行测试验证通过 + Commit**

```bash
git commit -m "feat(iam-domain): 新增 SecondaryAuthService 领域服务"
```

---

## Task 17: 创建 IdentitySwitchService 领域服务

**Files:**
- Create: `iam-service/iam-domain/src/main/java/com/example/iam/domain/authentication/service/IdentitySwitchService.java`

**Interfaces:**
- Consumes: `SecondaryAuthSessionRepository`
- Produces: `IdentitySwitchService`，方法：`getCurrentActingAs(branchUserId)` / `canSwitchBack(branchUserId)`

> 注：实际的身份切换操作（sa-token switchTo）由 application 层调用 StpBranchUtil 完成。domain 层只负责校验二次授权会话是否有效。

- [ ] **Step 1: 实现 IdentitySwitchService**

```java
@DomainService
public class IdentitySwitchService {
    private final SecondaryAuthSessionRepository sessionRepository;

    public Optional<InternetUserId> getCurrentActingAs(BranchUserId branchUserId) {
        // 查询 findActiveByBranchUser，过滤 status=COMPLETED 且 !isExpired(now)，返回 internetUserId
    }

    public boolean canSwitchBack(BranchUserId branchUserId) {
        return getCurrentActingAs(branchUserId).isPresent();
    }
}
```

- [ ] **Step 2: 编译验证 + Commit**

```bash
git commit -m "feat(iam-domain): 新增 IdentitySwitchService 领域服务"
```

---

## Task 18: 创建 StpInternetUtil / StpHqUtil / StpBranchUtil 工具类

**Files:**
- Create: `iam-service/iam-api/src/main/java/com/example/iam/api/satoken/StpInternetUtil.java`
- Create: `iam-service/iam-api/src/main/java/com/example/iam/api/satoken/StpHqUtil.java`
- Create: `iam-service/iam-api/src/main/java/com/example/iam/api/satoken/StpBranchUtil.java`

**Interfaces:**
- Consumes: `cn.dev33.satoken.stp.StpLogic`
- Produces: 三套独立 StpLogic 工具类

- [ ] **Step 1: 创建 StpInternetUtil**

```java
package com.example.iam.api.satoken;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.SaTokenModel;
import cn.dev33.satoken.stp.StpLogic;

/**
 * 网上渠道 StpLogic 工具类
 *
 * <p>对应 INTERNET 渠道，独立 token-name=iam-internet-token，独立 Redis 命名空间</p>
 */
public class StpInternetUtil {

    public static final String TYPE = "internet";
    public static StpLogic stpLogic = new StpLogic(TYPE);

    public static void login(Object id) { stpLogic.login(id); }
    public static void checkLogin() { stpLogic.checkLogin(); }
    public static Object getLoginId() { return stpLogic.getLoginId(); }
    public static Long getLoginIdAsLong() { return stpLogic.getLoginIdAsLong(); }
    public static boolean isLogin() { return stpLogic.isLogin(); }
    public static void logout() { stpLogic.logout(); }
    public static SaSession getSession() { return stpLogic.getSession(); }
    public static SaSession getTokenSession() { return stpLogic.getTokenSession(); }
    public static SaTokenModel getTokenInfo() { return stpLogic.getTokenInfo(); }
    public static String getTokenValue() { return stpLogic.getTokenValue(); }
    public static void checkPermission(String permission) { stpLogic.checkPermission(permission); }
    public static boolean hasPermission(String permission) { return stpLogic.hasPermission(permission); }
}
```

- [ ] **Step 2: 创建 StpHqUtil（同上，TYPE = "hq"）**

- [ ] **Step 3: 创建 StpBranchUtil（同上，TYPE = "branch"，额外含 switchTo/endSwitch/isSwitch/getSwitchLoginId 方法）**

```java
public static void switchTo(Object internetUserId) { stpLogic.switchTo(internetUserId); }
public static void endSwitch() { stpLogic.endSwitch(); }
public static boolean isSwitch() { return stpLogic.isSwitch(); }
public static Object getSwitchLoginId() { return stpLogic.getSwitchLoginId(); }
```

- [ ] **Step 4: 编译验证 + Commit**

```bash
git commit -m "feat(iam-api): 新增三套 StpLogic 工具类"
```

---

## Task 19: 创建认证相关 DTO 与 API 接口

**Files:**
- Create: 9 个 DTO record（`iam-api/.../dto/auth/`）
- Create: 3 个 Auth API 接口（`iam-api/.../auth/`）
- Create: 3 个 User 管理 API 接口（`iam-api/.../user/`）

**Interfaces:**
- Consumes: jakarta.validation 注解、`@HttpExchange`、`@GetExchange`、`@PostExchange`、`ApiResult<T>`、`PageData<T>`
- Produces: 9 个 DTO + 6 个 API 接口

- [ ] **Step 1: 创建认证相关 DTO**

```java
// InternetLoginRequest
public record InternetLoginRequest(
    @NotBlank String loginName,
    @NotBlank String password,
    String ipAddress,
    String userAgent
) {}

// HqLoginRequest（同上）
// BranchLoginRequest（同上）

// LoginResponse
public record LoginResponse(String tokenName, String tokenValue, Long userId, String channel) {}

// ChangeCredentialRequest
public record ChangeCredentialRequest(
    @NotBlank String oldCredential,
    @NotBlank String newCredential,
    @NotBlank String credentialType
) {}

// InitiateSecondaryAuthRequest
public record InitiateSecondaryAuthRequest(
    @NotNull Long branchUserId,
    @NotNull Long internetUserId,
    @NotBlank String strategyType
) {}

// CompleteSecondaryAuthRequest
public record CompleteSecondaryAuthRequest(
    @NotNull Long sessionId,
    @NotBlank String input
) {}

// RevokeSecondaryAuthRequest
public record RevokeSecondaryAuthRequest(@NotNull Long sessionId) {}

// SecondaryAuthResponse
public record SecondaryAuthResponse(Long sessionId, String status, LocalDateTime expiresAt) {}
```

- [ ] **Step 2: 创建 InternetAuthApi / HqAuthApi / BranchAuthApi**

按 04-代码编写约束.md 第 2 节规范，使用 `@HttpExchange` + `@GetExchange`/`@PostExchange` + `@Valid @RequestBody` + `ApiResult<T>`：

```java
@HttpExchange(url = "/internet/auth")
public interface InternetAuthApi {
    @PostExchange(url = "/login")
    ApiResult<LoginResponse> login(@Valid @RequestBody InternetLoginRequest request);

    @PostExchange(url = "/logout")
    ApiResult<Void> logout();

    @GetExchange(url = "/current")
    ApiResult<InternetUserResponse> currentUser();

    @PostExchange(url = "/change-credential")
    ApiResult<Void> changeCredential(@Valid @RequestBody ChangeCredentialRequest request);
}

@HttpExchange(url = "/hq/auth")
public interface HqAuthApi {
    @PostExchange(url = "/login")
    ApiResult<LoginResponse> login(@Valid @RequestBody HqLoginRequest request);

    @PostExchange(url = "/logout")
    ApiResult<Void> logout();

    @GetExchange(url = "/current")
    ApiResult<HqUserResponse> currentUser();
}

@HttpExchange(url = "/branch/auth")
public interface BranchAuthApi {
    @PostExchange(url = "/login")
    ApiResult<LoginResponse> login(@Valid @RequestBody BranchLoginRequest request);

    @PostExchange(url = "/secondary-auth/initiate")
    ApiResult<SecondaryAuthResponse> initiateSecondaryAuth(@Valid @RequestBody InitiateSecondaryAuthRequest request);

    @PostExchange(url = "/secondary-auth/complete")
    ApiResult<Void> completeSecondaryAuth(@Valid @RequestBody CompleteSecondaryAuthRequest request);

    @PostExchange(url = "/secondary-auth/revoke")
    ApiResult<Void> revokeSecondaryAuth(@RequestBody RevokeSecondaryAuthRequest request);

    @PostExchange(url = "/switch-back")
    ApiResult<Void> switchBackToTeller();
}
```

- [ ] **Step 3: 创建用户管理 API（InternetUserApi / HqUserApi / BranchUserApi）**

每个 API 含：create / update / disable / reset-credential / list 方法。返回 `ApiResult<XxxUserResponse>` 或 `ApiResult<PageData<XxxUserResponse>>`。

- [ ] **Step 4: 编译验证 + Commit**

```bash
git commit -m "feat(iam-api): 新增认证相关 DTO 与 API 接口"
```

---

## Task 20: 创建 DO 实体类与 Mapper

**Files:**
- Create: 6 个 DO 类（`iam-infrastructure/.../entity/`）
- Create: 6 个 Mapper 接口（`iam-infrastructure/.../mapper/`）

**Interfaces:**
- Consumes: `@Table`、`@Id`、`@Column`、`KeyType`、MyBatis-Flex BaseMapper
- Produces: 6 个 DO + 6 个 Mapper

- [ ] **Step 1: 创建 6 个 DO 类**

参考 `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/entity/FileMetadataDO.java` 模式：

| DO 类 | 表名 | 关键字段 |
|-------|------|----------|
| InternetUserDO | t_iam_internet_user | id, userNo, customerNo, loginName, displayName, status, lastLoginTime, lastLoginIp + 通用字段 |
| HqUserDO | t_iam_hq_user | id, staffNo, loginName, displayName, department, status, lastLoginTime, lastLoginIp + 通用字段 |
| BranchUserDO | t_iam_branch_user | id, tellerNo, bankCode, branchCode, loginName, displayName, status, lastLoginTime, lastLoginIp + 通用字段 |
| CredentialDO | t_iam_credential | id, ownerType, ownerId, credentialType, secret, salt, status, lastChangedAt + 通用字段 |
| SecondaryAuthSessionDO | t_iam_secondary_auth_session | id, sessionNo, branchUserId, internetUserId, strategyType, status, expiresAt, completedAt, actingThroughRef + 通用字段 |
| LoginLogDO | t_iam_login_log | id, userType, userId, loginName, channel, loginResult, failReason, loginTime, ipAddress, userAgent + 通用字段 |

**通用字段**（参考 06-数据库规范.md 第六节）：id(BIGINT, @Id keyType=KeyType.None)、createdBy、createTime、updatedBy、updateTime、deleted(@Column isLogicDelete)、version(@Column version=true)

> 注意：参考 06-数据库规范.md 第十节，createTime/updateTime 作为普通字段，不使用 onInsertValue/onUpdateValue。

- [ ] **Step 2: 创建 6 个 Mapper 接口**

```java
public interface InternetUserMapper extends BaseMapper<InternetUserDO> {}
// 其他 5 个 Mapper 同上
```

- [ ] **Step 3: 编译验证 + Commit**

```bash
git commit -m "feat(iam-infrastructure): 新增 6 个 DO 实体类与 Mapper"
```

---

## Task 21: 创建 Entity ↔ DO Converter

**Files:**
- Create: 6 个 Converter 接口（`iam-infrastructure/.../converter/`）

**Interfaces:**
- Consumes: `@Mapper(componentModel = "spring")`、MapStruct
- Produces: 6 个 Converter，方法 `toDO(aggregate)` / `toDomain(do)`

- [ ] **Step 1: 创建 6 个 Converter**

参考 `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/converter/FileMetadataConverter.java` 模式：

```java
@Mapper(componentModel = "spring")
public interface InternetUserConverter {

    default InternetUserDO toDO(InternetUser user) {
        InternetUserDO aDo = new InternetUserDO();
        aDo.setId(user.id() != null ? user.id().value() : null);
        aDo.setUserNo(...);
        aDo.setCustomerNo(user.customerNo() != null ? user.customerNo().value() : null);
        aDo.setLoginName(user.loginName());
        aDo.setDisplayName(user.displayName());
        aDo.setStatus(user.status() != null ? user.status().name() : null);
        aDo.setLastLoginTime(user.lastLoginTime());
        aDo.setLastLoginIp(user.lastLoginIp());
        aDo.setCreatedBy(user.createdBy() != null ? user.createdBy().value() : null);
        aDo.setUpdatedBy(user.updatedBy() != null ? user.updatedBy().value() : null);
        aDo.setCreateTime(user.createdAt());
        aDo.setUpdateTime(user.updatedAt());
        aDo.setDeleted(false);
        aDo.setVersion(user.version() != null ? (int) user.version().value() : 0);
        return aDo;
    }

    default InternetUser toDomain(InternetUserDO aDo) {
        if (aDo == null) return null;
        return InternetUser.reconstitute(
            InternetUserId.of(aDo.getId()),
            CustomerNo.of(aDo.getCustomerNo()),
            aDo.getLoginName(),
            aDo.getDisplayName(),
            aDo.getStatus() != null ? UserStatus.valueOf(aDo.getStatus()) : null,
            aDo.getLastLoginTime(),
            aDo.getLastLoginIp(),
            UserNo.of(aDo.getCreatedBy()),
            UserNo.of(aDo.getUpdatedBy()),
            aDo.getCreateTime(),
            aDo.getUpdateTime(),
            Version.of((long) aDo.getVersion())
        );
    }
}
```

> **重要**：参考 06-数据库规范.md 第十节，`toDO` 必须从领域对象的 `createdAt()`/`updatedAt()` 映射到 DO 的 `createTime`/`updateTime`，禁止使用 `@Mapping(ignore = true)`。

- [ ] **Step 2: 编译验证 + Commit**

```bash
git commit -m "feat(iam-infrastructure): 新增 6 个 Entity 与 DO Converter"
```

---

## Task 22: 创建 Repository 实现与 schema SQL

**Files:**
- Create: 6 个 RepositoryImpl（`iam-infrastructure/.../repository/`）
- Create: `iam-infrastructure/src/main/resources/schema-pg.sql`
- Create: `iam-infrastructure/src/main/resources/schema-mysql.sql`

**Interfaces:**
- Consumes: `@Repository`、`ApplicationEventPublisher`、MyBatis-Flex `QueryWrapper`、各 Mapper、各 Converter
- Produces: 6 个 Repository 实现 + 2 个 SQL 文件

- [ ] **Step 1: 创建 6 个 RepositoryImpl**

参考 `file-service/file-infrastructure/src/main/java/com/example/file/infrastructure/repository/FileMetadataRepositoryImpl.java` 模式：

```java
@Repository
@RequiredArgsConstructor
public class InternetUserRepositoryImpl implements InternetUserRepository {
    private final InternetUserMapper mapper;
    private final InternetUserConverter converter;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Optional<InternetUser> load(InternetUserId id) {
        InternetUserDO aDo = mapper.selectOneById(id.value());
        return Optional.ofNullable(aDo).map(converter::toDomain);
    }

    @Override
    public void save(InternetUser user) {
        InternetUserDO aDo = converter.toDO(user);
        if (mapper.selectOneById(aDo.getId()) == null) {
            mapper.insert(aDo);
        } else {
            mapper.update(aDo);
        }
        publishDomainEvents(user);
    }

    @Override
    public Optional<InternetUser> findByLoginName(String loginName) {
        // 用 QueryWrapper 查询 login_name = ? and deleted = 0
    }

    @Override
    public List<InternetUser> findByCustomerNo(CustomerNo customerNo) {
        // 用 QueryWrapper 查询 customer_no = ? and deleted = 0
    }

    // 其他方法：delete / deleteById / loadAll / streamByAppId
    // publishDomainEvents 私有方法：发布聚合根的领域事件 + clearDomainEvents
}
```

> 注意：参考 06-数据库规范.md 第八节，查询必须包含 `deleted = 0` 过滤。MyBatis-Flex 的 `@Column(isLogicDelete = true)` 会自动过滤，但显式构造 QueryWrapper 时需手动追加。

- [ ] **Step 2: 创建 schema-pg.sql**

参考 spec 第七章 7.1 节，创建 7 张 PostgreSQL 表（t_iam_internet_user / t_iam_hq_user / t_iam_branch_user / t_iam_credential / t_iam_secondary_auth_session / t_iam_login_log）。每张表含通用字段（id, created_by, create_time, updated_by, update_time, deleted, version）和必要索引。

- [ ] **Step 3: 创建 schema-mysql.sql**

将 schema-pg.sql 的 PostgreSQL 类型转换为 MySQL 类型（BOOLEAN→TINYINT(1)，TIMESTAMP→DATETIME）。索引语法保持兼容。

- [ ] **Step 4: 编译验证 + Commit**

```bash
git commit -m "feat(iam-infrastructure): 新增 6 个 Repository 实现与 SQL schema"
```

---

## Task 23: 创建应用服务（AuthApplicationService + UserManagementApplicationService）

**Files:**
- Create: 11 个 Command record（`iam-application/.../auth/command/`）
- Create: `iam-application/.../auth/service/AuthApplicationService.java`
- Create: `iam-application/.../auth/service/UserManagementApplicationService.java`
- Create: `iam-application/.../auth/hook/NoOpLoginHook.java`
- Create: `iam-application/.../converter/AuthCommandConverter.java`

**Interfaces:**
- Consumes: `LoginService`、`SecondaryAuthService`、`IdentitySwitchService`、各 UserRepository、CredentialRepository、StpXxxUtil、`@Service`、`@Transactional`
- Produces: AuthApplicationService（编排登录流程，调用 sa-token）+ UserManagementApplicationService

- [ ] **Step 1: 创建 11 个 Command record**

```java
// InternetLoginCommand
public record InternetLoginCommand(String loginName, String password, String ipAddress, String userAgent) {}

// HqLoginCommand
public record HqLoginCommand(String loginName, String password, String ipAddress, String userAgent) {}

// BranchLoginCommand
public record BranchLoginCommand(String loginName, String password, String ipAddress, String userAgent) {}

// ChangeCredentialCommand
public record ChangeCredentialCommand(String oldCredential, String newCredential, String credentialType) {}

// InitiateSecondaryAuthCommand
public record InitiateSecondaryAuthCommand(Long branchUserId, Long internetUserId, String strategyType) {}

// CompleteSecondaryAuthCommand
public record CompleteSecondaryAuthCommand(Long sessionId, String input) {}

// RevokeSecondaryAuthCommand
public record RevokeSecondaryAuthCommand(Long sessionId) {}

// SwitchBackCommand（空 record，仅作为 CQE 标识）
public record SwitchBackCommand() {}

// CreateInternetUserCommand
public record CreateInternetUserCommand(String customerNo, String loginName, String displayName, String password) {}

// UpdateInternetUserCommand
public record UpdateInternetUserCommand(Long userId, String displayName) {}

// DisableUserCommand
public record DisableUserCommand(Long userId) {}

// ResetCredentialCommand
public record ResetCredentialCommand(Long userId, String newCredential) {}
```

- [ ] **Step 2: 实现 AuthApplicationService**

```java
@Service
@AllArgsConstructor
public class AuthApplicationService {
    private final LoginService loginService;
    private final SecondaryAuthService secondaryAuthService;
    private final IdentitySwitchService identitySwitchService;
    private final InternetUserRepository internetUserRepository;
    private final HqUserRepository hqUserRepository;
    private final BranchUserRepository branchUserRepository;
    private final CredentialRepository credentialRepository;

    @Transactional
    public LoginResponse internetLogin(InternetLoginCommand cmd) {
        LoginService.LoginResult result = loginService.login(
            ChannelType.INTERNET, cmd.loginName(), cmd.password(),
            new LoginHook.LoginContext(cmd.loginName(), ChannelType.INTERNET, cmd.ipAddress(), cmd.userAgent(), Map.of())
        );
        if (!result.success()) {
            throw new BusinessException(IamAuthErrorCode.valueOf(result.failReason()));
        }
        StpInternetUtil.login(result.userId());
        SaTokenModel tokenInfo = StpInternetUtil.getTokenInfo();
        return new LoginResponse(tokenInfo.tokenName, tokenInfo.tokenValue, result.userId(), ChannelType.INTERNET.name());
    }

    // hqLogin / branchLogin 类似，使用 StpHqUtil / StpBranchUtil

    public void logout(ChannelType channel) {
        switch (channel) {
            case INTERNET -> StpInternetUtil.logout();
            case HQ -> StpHqUtil.logout();
            case BRANCH -> StpBranchUtil.logout();
        }
    }

    @Transactional
    public SecondaryAuthResponse initiateSecondaryAuth(InitiateSecondaryAuthCommand cmd) {
        SecondaryAuthSession session = secondaryAuthService.initiate(
            BranchUserId.of(cmd.branchUserId()),
            InternetUserId.of(cmd.internetUserId()),
            SecondaryAuthStrategyType.valueOf(cmd.strategyType()),
            LocalDateTime.now().plusMinutes(30),
            // operator 从 sa-token 当前登录柜员获取
            UserNo.of(StpBranchUtil.getLoginIdAsLong().toString())
        );
        return new SecondaryAuthResponse(session.id().value(), session.status().name(), session.expiresAt());
    }

    @Transactional
    public void completeSecondaryAuth(CompleteSecondaryAuthCommand cmd) {
        boolean success = secondaryAuthService.complete(
            SecondaryAuthSessionId.of(cmd.sessionId()),
            cmd.input(),
            UserNo.of(StpBranchUtil.getLoginIdAsLong().toString())
        );
        if (!success) {
            throw new BusinessException(IamAuthErrorCode.CREDENTIAL_INVALID);
        }
        // 二次授权成功后切换为经办人身份
        // 从 session 读取 internetUserId
        // StpBranchUtil.switchTo(internetUserId)
    }

    @Transactional
    public void revokeSecondaryAuth(RevokeSecondaryAuthCommand cmd) {
        secondaryAuthService.revoke(
            SecondaryAuthSessionId.of(cmd.sessionId()),
            UserNo.of(StpBranchUtil.getLoginIdAsLong().toString())
        );
    }

    @Transactional
    public void switchBackToTeller() {
        if (!StpBranchUtil.isSwitch()) {
            throw new BusinessException(IamAuthErrorCode.NOT_BRANCH_USER_CANNOT_SWITCH_BACK);
        }
        StpBranchUtil.endSwitch();
    }
}
```

- [ ] **Step 3: 实现 UserManagementApplicationService**

包含 createInternetUser / updateInternetUser / disableUser / resetCredential 等方法，编排聚合根操作 + Credential 创建（调用 PasswordCredentialValidator.hashPassword）+ Repository.save。

- [ ] **Step 4: 创建 NoOpLoginHook**

```java
@Component
public class NoOpLoginHook implements LoginHook {
    // 使用接口默认实现，类体为空
}
```

- [ ] **Step 5: 创建 AuthCommandConverter**

MapStruct Mapper，将 Request DTO 转 Command：

```java
@Mapper(componentModel = "spring")
public interface AuthCommandConverter {
    InternetLoginCommand toCommand(InternetLoginRequest request, String ipAddress, String userAgent);
    HqLoginCommand toCommand(HqLoginRequest request, String ipAddress, String userAgent);
    BranchLoginCommand toCommand(BranchLoginRequest request, String ipAddress, String userAgent);
    // ...
}
```

- [ ] **Step 6: 编译验证 + Commit**

```bash
git commit -m "feat(iam-application): 新增 AuthApplicationService 与 UserManagementApplicationService"
```

---

## Task 24: 创建领域事件监听器（写登录日志）

**Files:**
- Create: `iam-application/.../auth/event/LoginLogEventListener.java`

**Interfaces:**
- Consumes: `UserLoggedInEvent`、`UserLoginFailedEvent`、`LoginLogRepository`
- Produces: `LoginLogEventListener`，监听登录事件并写入登录日志

- [ ] **Step 1: 实现 LoginLogEventListener**

```java
@Component
@AllArgsConstructor
public class LoginLogEventListener {

    private final LoginLogRepository loginLogRepository;

    @EventListener
    @Async
    public void onUserLoggedIn(UserLoggedInEvent event) {
        loginLogRepository.append(
            "INTERNET_USER", // 根据 channel 区分
            Long.parseLong(event.userId().value()),
            event.userId().value(),
            event.channel(),
            "SUCCESS",
            null,
            event.occurredOn(),
            event.ipAddress(),
            event.userAgent(),
            event.userId().value()
        );
    }

    @EventListener
    @Async
    public void onUserLoginFailed(UserLoginFailedEvent event) {
        loginLogRepository.append(
            "UNKNOWN",
            null,
            event.loginName(),
            event.channel(),
            "FAILURE",
            event.failReason(),
            event.occurredOn(),
            event.ipAddress(),
            event.userAgent(),
            "SYSTEM"
        );
    }
}
```

> 注：需要在 LoginService 中触发事件。修改 LoginService 在登录成功/失败时通过 Repository.save 时一并发布事件（Repository 实现负责 publishEvent）。

- [ ] **Step 2: 编译验证 + Commit**

```bash
git commit -m "feat(iam-application): 新增 LoginLogEventListener 监听登录事件"
```

---

## Task 25: 创建 Adapter Converter 与 Controller

**Files:**
- Create: `iam-adapter/.../converter/AuthResponseConverter.java`
- Create: `iam-adapter/.../converter/UserResponseConverter.java`
- Create: `iam-adapter/.../auth/InternetAuthController.java`
- Create: `iam-adapter/.../auth/HqAuthController.java`
- Create: `iam-adapter/.../auth/BranchAuthController.java`
- Create: `iam-adapter/.../user/InternetUserController.java`
- Create: `iam-adapter/.../user/HqUserController.java`
- Create: `iam-adapter/.../user/BranchUserController.java`

**Interfaces:**
- Consumes: API 接口、ApplicationService、AuthCommandConverter、`@RestController`、`ApiResult`
- Produces: 6 个 Controller 实现 API 接口

- [ ] **Step 1: 创建 AuthResponseConverter**

```java
@Mapper(componentModel = "spring")
public interface AuthResponseConverter {
    LoginResponse toResponse(LoginResult result, String tokenName, String tokenValue);
    SecondaryAuthResponse toResponse(SecondaryAuthSession session);
    InternetUserResponse toResponse(InternetUser user);
    HqUserResponse toResponse(HqUser user);
    BranchUserResponse toResponse(BranchUser user);
}
```

- [ ] **Step 2: 创建 InternetAuthController**

```java
@RestController
@AllArgsConstructor
public class InternetAuthController implements InternetAuthApi {
    private final AuthApplicationService authApplicationService;
    private final AuthCommandConverter commandConverter;
    private final AuthResponseConverter responseConverter;

    @Override
    public ApiResult<LoginResponse> login(@Valid @RequestBody InternetLoginRequest request) {
        // 从 HttpServletRequest 提取 ipAddress 和 userAgent
        InternetLoginCommand cmd = commandConverter.toCommand(request, ipAddress, userAgent);
        LoginResponse response = authApplicationService.internetLogin(cmd);
        return ApiResult.success(response);
    }

    @Override
    public ApiResult<Void> logout() {
        authApplicationService.logout(ChannelType.INTERNET);
        return ApiResult.success(null);
    }

    @Override
    public ApiResult<InternetUserResponse> currentUser() {
        Long userId = StpInternetUtil.getLoginIdAsLong();
        InternetUser user = internetUserRepository.load(InternetUserId.of(userId))
            .orElseThrow(() -> new BusinessException(IamAuthErrorCode.USER_NOT_FOUND));
        return ApiResult.success(responseConverter.toResponse(user));
    }

    @Override
    public ApiResult<Void> changeCredential(@Valid @RequestBody ChangeCredentialRequest request) {
        ChangeCredentialCommand cmd = commandConverter.toCommand(request);
        authApplicationService.changeCredential(cmd);
        return ApiResult.success(null);
    }
}
```

- [ ] **Step 3: 创建 HqAuthController / BranchAuthController（同上模式）**

- [ ] **Step 4: 创建 InternetUserController / HqUserController / BranchUserController**

每个 Controller 实现 create / update / disable / reset-credential / list 方法，调用 UserManagementApplicationService。

- [ ] **Step 5: 编译验证 + Commit**

```bash
git commit -m "feat(iam-adapter): 新增 6 个 Controller 与 Converter"
```

---

## Task 26: 创建启动类、配置文件与 StpInterface 占位实现

**Files:**
- Create: `iam-starter/src/main/java/com/example/iam/IamApplication.java`
- Create: `iam-starter/src/main/resources/application.yml`
- Create: `iam-starter/src/main/resources/application-local.yml`
- Create: `iam-infrastructure/src/main/java/com/example/iam/infrastructure/satoken/StpInterfaceImpl.java`

**Interfaces:**
- Consumes: `@SpringBootApplication`、sa-token 配置、`StpInterface`
- Produces: 启动类 + 配置文件 + StpInterface 占位实现（返回空权限列表，Plan 2 实现）

- [ ] **Step 1: 创建启动类**

```java
@SpringBootApplication(scanBasePackages = "com.example.iam")
public class IamApplication {
    public static void main(String[] args) {
        SpringApplication.run(IamApplication.class, args);
    }
}
```

- [ ] **Step 2: 创建 application.yml**

参考 `annuity-service/annuity-starter/src/main/resources/application.yml` 风格：

```yaml
server:
  tomcat:
    threads:
      max: 200
      min-spare: 20
  port: 8085

spring:
  application:
    name: iam-service
  threads:
    virtual:
      enabled: true
  http:
    client:
      factory: http-components
      pool:
        max-total: 100
        max-per-route: 20
        validate-after-inactivity: 5s
      settings:
        connect-timeout: 3s
        read-timeout: 30s
        connection-time-to-live: 30s
  profiles:
    include: local

mybatis-flex:
  mapper-locations: classpath*:/mapper/**/*.xml

trace:
  context:
    mapping:
      UserId: X-User-Id
      Channel: X-Channel

# sa-token 配置（三套账号体系独立 token-name，参考 sa-token-使用说明.md 第二十一节）
sa-token:
  token-name: iam-token
  timeout: 2592000
  active-timeout: -1
  is-concurrent: true
  is-share: false
  token-style: uuid
  is-log: false
  # 多账号体系独立配置：每个 StpLogic 对应独立 token-name 与 Redis 命名空间
  uniques:
    internet:
      token-name: iam-internet-token
    hq:
      token-name: iam-hq-token
    branch:
      token-name: iam-branch-token

logging:
  file:
    path: /applog/${spring.application.name}
    name: ${spring.application.name}.log
  logback:
    rollingpolicy:
      total-size-cap: 1GB
      max-history: 30
```

- [ ] **Step 3: 创建 application-local.yml**

参考 `annuity-service/annuity-starter/src/main/resources/application-local.yml` 风格。注意：本服务暂不调用其他业务服务 API，**不配置 httpexchange.clients**；Plan 5 集成时再追加。

```yaml
spring:
  cloud:
    nacos.discovery.server-addr: 127.0.0.1:8848
    loadbalancer:
      enabled: true
  datasource:
    url: jdbc:postgresql://127.0.0.1:55432/pgdb?currentSchema=schema_iam&timezone=Asia/Shanghai
    username: user_demo
    password: passwd_demo
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 1
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      password:

# shared-logging-starter 脱敏配置（最小化满足 SanitizationProperties @NotNull/@NotEmpty 校验）
shared:
  logging:
    obfuscate:
      global:
        enable: false
        replacement: "***"
        enable-wildcard-paths: false
      fields:
        password:
          aliases:
            - $.password
            - $.newCredential
            - $.oldCredential
          strategy: FULL

logging:
  level:
    root: INFO
    com.example: INFO
    com.example.iam: DEBUG
    cn.dev33.satoken: INFO
    org.zalando.logbook: TRACE
    com.alibaba.nacos: ERROR
    com.alibaba.cloud.nacos: ERROR
```

- [ ] **Step 4: 创建 application-test.yml（测试环境）**

参考 `annuity-service/annuity-starter/src/test/resources/application-test.yml`，使用 H2 内存数据库（PostgreSQL 兼容模式），禁用 Nacos / Redis：

```yaml
spring:
  autoconfigure:
    exclude:
      - com.alibaba.cloud.nacos.discovery.NacosDiscoveryAutoConfiguration
      - com.alibaba.cloud.nacos.discovery.NacosServiceRegistryAutoConfiguration
      - com.alibaba.cloud.nacos.NacosAutoConfiguration
      - com.alibaba.cloud.nacos.discovery.NacosDiscoveryClientConfiguration
      - com.example.shared.client.autoconfigure.ClientAutoConfiguration
      # 测试环境禁用 sa-token Redis 持久化，使用内存
      - cn.dev33.satoken.dao.SaTokenDaoByRedis
  cloud:
    nacos:
      discovery:
        enabled: false
      config:
        enabled: false
    discovery:
      enabled: false
  datasource:
    url: jdbc:h2:mem:iam_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE
    driver-class-name: org.h2.Driver
    username: sa
    password:
  sql:
    init:
      mode: always
      schema-locations: classpath:schema-h2.sql

mybatis-flex:
  mapper-locations: classpath*:/mapper/**/*.xml

# sa-token 测试配置（使用内存会话）
sa-token:
  token-name: iam-token
  timeout: 2592000
  active-timeout: -1
  is-concurrent: true
  is-share: false
  token-style: uuid
  is-log: false

shared:
  logging:
    obfuscate:
      global:
        enable: false
        replacement: "***"
        enable-wildcard-paths: false
      fields:
        password:
          aliases:
            - $.password
          strategy: FULL
      strategies:
        FULL: { }

logging:
  level:
    root: WARN
    com.example: INFO
    com.example.iam: DEBUG
    org.springframework: WARN
    com.alibaba.nacos: OFF
    com.alibaba.cloud.nacos: OFF
```

- [ ] **Step 5: 创建 schema-h2.sql（测试用 H2 schema）**

将 schema-pg.sql 中的 PostgreSQL 类型转换为 H2 兼容类型（BOOLEAN→BOOLEAN H2 支持、TIMESTAMP→TIMESTAMP、SERIAL→BIGINT），保存在 `iam-starter/src/test/resources/schema-h2.sql`。

实施者请直接复制 `iam-infrastructure/src/main/resources/schema-pg.sql` 内容并按如下规则替换：
- 移除 `IF NOT EXISTS` 中的 PostgreSQL 特有语法（如 `CREATE TABLE IF NOT EXISTS` H2 也支持，可保留）
- 将 `BOOLEAN NOT NULL DEFAULT FALSE` 保留（H2 在 PostgreSQL 模式下支持）
- 将 `TIMESTAMP` 保留（H2 在 PostgreSQL 模式下支持）
- 移除 PostgreSQL 特有的 `JSONB` 类型，改用 `CLOB`（H2 兼容）
- 移除索引中的 `IF NOT EXISTS`（H2 不支持该语法）

- [ ] **Step 6: 创建 StpInterface 占位实现**

```java
package com.example.iam.infrastructure.satoken;

import cn.dev33.satoken.stp.StpInterface;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * sa-token 权限/角色查询占位实现
 *
 * <p>Plan 1 阶段返回空列表，确保登录流程可走通。
 * Plan 2/3 接入 RBAC + PBAC 后替换为真实权限计算逻辑。</p>
 *
 * @see com.example.iam.api.satoken.StpInternetUtil
 * @see com.example.iam.api.satoken.StpHqUtil
 * @see com.example.iam.api.satoken.StpBranchUtil
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // Plan 2/3 实现真实权限计算
        return List.of();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        // Plan 2 实现真实角色查询
        return List.of();
    }
}
```

> **注意**：`loginType` 参数对应 `StpLogic.getType()`，即 `"internet"` / `"hq"` / `"branch"`。Plan 2/3 实现时需根据 loginType 路由到不同的权限查询逻辑。

- [ ] **Step 7: 创建 SecondaryAuthCleanupTask（可选，清理过期二次授权会话）**

```java
package com.example.iam.infrastructure.task;

import com.example.iam.domain.authentication.repository.SecondaryAuthSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 二次授权会话清理任务
 *
 * <p>每 10 分钟扫描过期的 PENDING 状态会话并标记为 EXPIRED</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecondaryAuthCleanupTask {

    private final SecondaryAuthSessionRepository sessionRepository;

    @Scheduled(fixedDelay = 600_000)
    public void cleanupExpiredSessions() {
        // Plan 1 阶段仅占位，Plan 3 实现完整清理逻辑
        log.debug("二次授权会话清理任务执行（Plan 1 占位）");
    }
}
```

> 在 `IamApplication` 添加 `@EnableScheduling` 注解。

- [ ] **Step 8: 编译验证**

Run: `mvn clean compile -pl iam-service -am`
Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add iam-service/iam-starter/src/ \
        iam-service/iam-infrastructure/src/main/java/com/example/iam/infrastructure/satoken/ \
        iam-service/iam-infrastructure/src/main/java/com/example/iam/infrastructure/task/
git commit -m "feat(iam-starter): 新增启动类、配置文件与 StpInterface 占位实现"
```

---

## Task 27: 端到端集成测试

**Files:**
- Create: `iam-service/iam-starter/src/test/java/com/example/iam/IamEndToEndTest.java`

**Interfaces:**
- Consumes: `AuthApplicationService`、`UserManagementApplicationService`、`StpInternetUtil`、`StpHqUtil`、`StpBranchUtil`、`InternetUserRepository`、`CredentialRepository`
- Produces: 端到端集成测试，覆盖三套账号登录、凭据修改、二次授权、身份切换场景

> **测试目标**：在 H2 内存数据库 + sa-token 内存会话环境下，验证 Plan 1 全部交付物能正确协同工作。参考 `annuity-service/annuity-starter/src/test/java/com/example/annuity/AnnuityEndToEndTest.java` 模式。

- [ ] **Step 1: 写 IamEndToEndTest 测试类**

测试用例：
1. `contextLoads`：Spring 上下文加载成功，核心 Bean 注入
2. `internetUserLogin_should_return_token_and_record_login_log`：创建 InternetUser + PASSWORD 凭据 → 调用 internetLogin → 验证返回 token + StpInternetUtil.isLogin()=true + LoginLog 表有 SUCCESS 记录
3. `internetUserLogin_withWrongPassword_should_throw_and_record_failure_log`：错误密码登录抛 BusinessException + LoginLog 表有 FAILURE 记录
4. `changeCredential_should_update_password_and_allow_login_with_new_password`：修改密码 → 旧密码登录失败 → 新密码登录成功
5. `hqUserLogin_should_return_hq_token`：总部用户登录使用 StpHqUtil
6. `branchUserSecondaryAuth_should_complete_and_switch_identity`：网点柜员登录 → 发起二次授权 → 完成二次授权 → 验证 StpBranchUtil.isSwitch()=true → 切换回柜员身份
7. `secondaryAuth_withWrongPassword_should_not_complete`：错误密码完成二次授权抛 BusinessException + 会话保持 PENDING
8. `expiredSecondaryAuth_should_throw_on_complete`：构造过期会话（通过反射修改 expiresAt）→ complete 抛 BusinessException

测试代码骨架：

```java
package com.example.iam;

import com.example.iam.application.auth.command.*;
import com.example.iam.application.auth.service.AuthApplicationService;
import com.example.iam.application.auth.service.UserManagementApplicationService;
import com.example.iam.api.dto.auth.LoginResponse;
import com.example.iam.api.dto.auth.SecondaryAuthResponse;
import com.example.iam.api.satoken.StpBranchUtil;
import com.example.iam.api.satoken.StpHqUtil;
import com.example.iam.api.satoken.StpInternetUtil;
import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.domain.authentication.repository.LoginLogRepository;
import com.example.iam.types.BranchUserId;
import com.example.iam.types.InternetUserId;
import com.example.shared.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes = IamApplication.class)
@ActiveProfiles("test")
@DisplayName("iam-service Plan 1 端到端集成测试")
class IamEndToEndTest {

    @Autowired private AuthApplicationService authApplicationService;
    @Autowired private UserManagementApplicationService userManagementService;
    @Autowired private LoginLogRepository loginLogRepository;

    @AfterEach
    void cleanupSession() {
        StpInternetUtil.logout();
        StpHqUtil.logout();
        StpBranchUtil.logout();
    }

    @Test
    @DisplayName("Spring 上下文加载成功：核心 Bean 全部就绪")
    void contextLoads() {
        assertThat(authApplicationService).isNotNull();
        assertThat(userManagementService).isNotNull();
        assertThat(loginLogRepository).isNotNull();
    }

    @Test
    @DisplayName("网上渠道用户登录：返回 token 且记录登录日志")
    void internetUserLogin_should_return_token_and_record_login_log() {
        // 1. 准备：创建用户 + 密码凭据
        Long userId = userManagementService.createInternetUser(
            new CreateInternetUserCommand("C-TEST-001", "hr001", "张三", "Passw0rd!"));

        // 2. 登录
        LoginResponse resp = authApplicationService.internetLogin(
            new InternetLoginCommand("hr001", "Passw0rd!", "127.0.0.1", "JUnit"));

        // 3. 断言
        assertThat(resp.userId()).isEqualTo(userId);
        assertThat(resp.channel()).isEqualTo(ChannelType.INTERNET.name());
        assertThat(resp.tokenValue()).isNotBlank();
        assertThat(StpInternetUtil.isLogin()).isTrue();
        assertThat(StpInternetUtil.getLoginIdAsLong()).isEqualTo(userId);

        // 4. 验证登录日志（异步，等待 1 秒）
        sleep(1000);
        long successCount = loginLogRepository.countFailuresSince("hr001", ChannelType.INTERNET,
            java.time.LocalDateTime.now().minusMinutes(1));
        // countFailuresSince 返回失败次数，应为 0
        assertThat(successCount).isZero();
    }

    @Test
    @DisplayName("错误密码登录：抛异常且记录失败日志")
    void internetUserLogin_withWrongPassword_should_throw_and_record_failure_log() {
        userManagementService.createInternetUser(
            new CreateInternetUserCommand("C-TEST-002", "hr002", "李四", "CorrectPwd!"));

        assertThatThrownBy(() -> authApplicationService.internetLogin(
            new InternetLoginCommand("hr002", "WrongPwd", "127.0.0.1", "JUnit")))
            .isInstanceOf(BusinessException.class);

        sleep(1000);
        long failCount = loginLogRepository.countFailuresSince("hr002", ChannelType.INTERNET,
            java.time.LocalDateTime.now().minusMinutes(1));
        assertThat(failCount).isEqualTo(1);
    }

    @Test
    @DisplayName("修改密码：旧密码失效，新密码可用")
    void changeCredential_should_update_password_and_allow_login_with_new_password() {
        Long userId = userManagementService.createInternetUser(
            new CreateInternetUserCommand("C-TEST-003", "hr003", "王五", "OldPwd!"));

        // StpInternetUtil 模拟登录态（changeCredential 需要登录上下文）
        StpInternetUtil.login(userId);
        authApplicationService.changeCredential(
            new ChangeCredentialCommand("OldPwd!", "NewPwd!", "PASSWORD"));
        StpInternetUtil.logout();

        // 旧密码登录失败
        assertThatThrownBy(() -> authApplicationService.internetLogin(
            new InternetLoginCommand("hr003", "OldPwd!", "127.0.0.1", "JUnit")))
            .isInstanceOf(BusinessException.class);

        // 新密码登录成功
        LoginResponse resp = authApplicationService.internetLogin(
            new InternetLoginCommand("hr003", "NewPwd!", "127.0.0.1", "JUnit"));
        assertThat(resp.userId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("总部用户登录：使用 StpHqUtil")
    void hqUserLogin_should_return_hq_token() {
        Long hqUserId = userManagementService.createHqUser(
            new CreateHqUserCommand("S001", "admin001", "管理员", "IT", "AdminPwd!"));

        LoginResponse resp = authApplicationService.hqLogin(
            new HqLoginCommand("admin001", "AdminPwd!", "127.0.0.1", "JUnit"));

        assertThat(resp.userId()).isEqualTo(hqUserId);
        assertThat(resp.channel()).isEqualTo(ChannelType.HQ.name());
        assertThat(StpHqUtil.isLogin()).isTrue();
    }

    @Test
    @DisplayName("网点二次授权流程：完成授权并切换为经办人身份")
    void branchUserSecondaryAuth_should_complete_and_switch_identity() {
        // 1. 准备：网点柜员 + 经办人
        Long branchUserId = userManagementService.createBranchUser(
            new CreateBranchUserCommand("B001", "T001", "teller001", "柜员", "TellerPwd!"));
        Long internetUserId = userManagementService.createInternetUser(
            new CreateInternetUserCommand("C-TEST-004", "hr004", "赵六", "HrPwd!"));

        // 2. 网点柜员登录
        authApplicationService.branchLogin(
            new BranchLoginCommand("teller001", "TellerPwd!", "10.0.0.1", "JUnit"));
        StpBranchUtil.login(branchUserId);

        // 3. 发起二次授权
        SecondaryAuthResponse resp = authApplicationService.initiateSecondaryAuth(
            new InitiateSecondaryAuthCommand(branchUserId, internetUserId, "CREDENTIAL"));
        assertThat(resp.status()).isEqualTo("PENDING");

        // 4. 完成二次授权
        authApplicationService.completeSecondaryAuth(
            new CompleteSecondaryAuthCommand(resp.sessionId(), "HrPwd!"));

        // 5. 切换为经办人身份（应用层在 completeSecondaryAuth 内已调用 switchTo）
        assertThat(StpBranchUtil.isSwitch()).isTrue();

        // 6. 切换回柜员身份
        authApplicationService.switchBackToTeller();
        assertThat(StpBranchUtil.isSwitch()).isFalse();
    }

    @Test
    @DisplayName("错误密码完成二次授权：抛异常且会话保持 PENDING")
    void secondaryAuth_withWrongPassword_should_not_complete() {
        Long branchUserId = userManagementService.createBranchUser(
            new CreateBranchUserCommand("B002", "T002", "teller002", "柜员2", "TellerPwd!"));
        Long internetUserId = userManagementService.createInternetUser(
            new CreateInternetUserCommand("C-TEST-005", "hr005", "钱七", "HrPwd!"));

        StpBranchUtil.login(branchUserId);
        SecondaryAuthResponse resp = authApplicationService.initiateSecondaryAuth(
            new InitiateSecondaryAuthCommand(branchUserId, internetUserId, "CREDENTIAL"));

        assertThatThrownBy(() -> authApplicationService.completeSecondaryAuth(
            new CompleteSecondaryAuthCommand(resp.sessionId(), "WrongPwd")))
            .isInstanceOf(BusinessException.class);

        // 会话应仍为 PENDING（未被 complete）
        // 验证：再次用正确密码完成应能成功
        authApplicationService.completeSecondaryAuth(
            new CompleteSecondaryAuthCommand(resp.sessionId(), "HrPwd!"));
    }

    private void sleep(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
```

> **实施者注意**：
> 1. `CreateHqUserCommand` / `CreateBranchUserCommand` 在 Task 23 Step 1 列出的 Command 之外，请补齐。
> 2. 测试用例 6（过期会话）可通过反射修改 `SecondaryAuthSession.expiresAt` 字段，参考 `AnnuityEndToEndTest.setUpdatedBy` 模式。
> 3. 若异步事件监听器导致测试不稳定，可在测试类添加 `@MockBean private LoginLogEventListener listener` 禁用异步日志写入，改为直接查询 `LoginLogRepository` 验证。

- [ ] **Step 2: 运行端到端测试**

Run: `mvn test -pl iam-service/iam-starter -am -Dtest=IamEndToEndTest`
Expected: PASS（7 个测试用例全部通过）

- [ ] **Step 3: 修复测试中发现的问题**

若测试失败，按以下顺序排查：
1. Spring 上下文加载失败 → 检查 Bean 注入、配置文件、schema-h2.sql 是否正确
2. sa-token 多账号体系冲突 → 检查 `StpLogic` 类型常量是否唯一
3. 异步事件未触发 → 检查 `LoginLogEventListener` 的 `@Async` 配置，必要时改为同步
4. MyBatis-Flex 映射失败 → 检查 DO 的 `@Table` / `@Column` 注解与 H2 schema 字段名是否一致

- [ ] **Step 4: Commit**

```bash
git add iam-service/iam-starter/src/test/
git commit -m "test(iam-starter): 新增 Plan 1 端到端集成测试"
```

---

## Task 28: Plan 1 收尾

**Files:**
- Modify: `docs/superpowers/plans/2026-07-25-iam-service-overview.md`（标记 Plan 1 完成状态）
- Run: 全量测试与构建验证

**Interfaces:**
- Produces: Plan 1 全部交付物就绪，可进入 Plan 2

- [ ] **Step 1: 全量编译与测试**

Run: `mvn clean test -pl iam-service -am`
Expected: BUILD SUCCESS，所有单元测试 + 端到端测试通过

- [ ] **Step 2: 验证错误码登记**

检查 `.trae/rules/08-错误码规范.md` SERVICE 域已包含 IAM 缩写，且 `IamCommonErrorCode` / `IamAuthErrorCode` 中的码值未与既有错误码冲突。

- [ ] **Step 3: 验证 SQL 文件齐全**

确认 `iam-infrastructure/src/main/resources/` 下有：
- `schema-pg.sql`（生产 PostgreSQL）
- `schema-mysql.sql`（生产 MySQL）

确认 `iam-starter/src/test/resources/` 下有：
- `schema-h2.sql`（测试 H2）

- [ ] **Step 4: 更新总览文档**

修改 `docs/superpowers/plans/2026-07-25-iam-service-overview.md`，在「计划文档索引」部分追加：

```markdown
- [Plan 2: RBAC 上下文](./2026-07-25-iam-service-plan2-rbac.md) — 待制定
- [Plan 3: PBAC + 防腐层](./2026-07-25-iam-service-plan3-pbac.md) — 待制定
- [Plan 4: 代办 + 审计](./2026-07-25-iam-service-plan4-agency-audit.md) — 待制定
- [Plan 5: 路由鉴权 + 集成](./2026-07-25-iam-service-plan5-routing.md) — 待制定
```

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/plans/2026-07-25-iam-service-overview.md
git commit -m "docs(iam-service): Plan 1 完成，更新总览文档"
```

- [ ] **Step 6: Plan 1 完成检查清单**

提交 Plan 1 完成确认前，逐项核对：

- [ ] 三套账号聚合根（InternetUser / HqUser / BranchUser）已实现 + 单元测试通过
- [ ] Credential 聚合根 + CredentialValidator 策略 + PasswordCredentialValidator 实现已完成
- [ ] SecondaryAuthSession 聚合根 + SecondaryAuthStrategy 策略 + CredentialSecondaryAuthStrategy 实现已完成
- [ ] 6 个领域事件已定义
- [ ] 6 个 Repository 接口 + 实现 + DO + Mapper + Converter 已完成
- [ ] LoginService / SecondaryAuthService / IdentitySwitchService 领域服务已实现
- [ ] AuthApplicationService / UserManagementApplicationService 应用服务已实现
- [ ] 三套 StpLogic 工具类（StpInternetUtil / StpHqUtil / StpBranchUtil）已创建
- [ ] StpInterface 占位实现已就绪（返回空列表）
- [ ] 6 个 Controller + Adapter Converter 已实现
- [ ] schema-pg.sql / schema-mysql.sql / schema-h2.sql 三个 SQL 文件齐全
- [ ] application.yml / application-local.yml / application-test.yml 配置文件齐全
- [ ] IamEndToEndTest 7 个测试用例全部通过
- [ ] 08-错误码规范.md 已登记 IAM 模块缩写
- [ ] 所有 commit 信息遵循 09-提交信息规范.md

---

## Plan 1 完成后的状态

Plan 1 完成后，iam-service 具备以下能力：

1. **三套账号体系**：网上渠道 / 总部渠道 / 网点渠道各自独立的账号、登录、会话管理
2. **凭据体系**：支持密码凭据（BCrypt），通过 `CredentialValidator` 策略接口可扩展 UKEY / OTP 等类型
3. **二次授权**：网点柜员可发起针对经办人的二次授权，验证通过后切换为经办人身份办理业务
4. **身份切换**：网点柜员可在柜员身份与经办人身份之间切换
5. **登录日志**：自动记录登录成功/失败日志
6. **sa-token 集成**：三套独立 StpLogic，独立 token-name，独立 Redis 命名空间

**待 Plan 2-5 补充**：
- 角色 + RBAC 权限（Plan 2）
- 业务权限计算 + 防腐层 Gateway（Plan 3）
- 代办关系 + 审计日志（Plan 4）
- 路由鉴权 + 网关集成 + @SaCheckBiz AOP（Plan 5）
