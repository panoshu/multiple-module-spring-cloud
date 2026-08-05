# SecondaryAuthSession 独立聚合根实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 auth-service 中引入 SecondaryAuthSession 独立聚合根，支持短信验证码两段式授权，冻结权限快照，保持 Grant 模型与 DENY 优先原则不被破坏。

**Architecture:** 在 auth-domain 的 channel 子域下新增 SecondaryAuthSession 聚合根、VerificationCode/PermissionSnapshot 值对象、6 状态状态机、6 个领域事件、Repository 接口、SecondaryAuthStrategy SPI；改造 Session 聚合根引用 SecondaryAuthSession；在 auth-application 新增 SecondaryAuthAppService 编排用例。BCrypt 哈希通过端口接口 VerificationCodeHasher 抽象，避免 domain 层直接依赖加密库。

**Tech Stack:** JDK 25（--enable-preview，因使用 sealed interface/record 模式）、Spring Boot 3.5.14、JUnit 5 + AssertJ（测试）、Lombok、shared-domain 基类（AggregateRoot/Entity/Repository/DomainEvent/ValueObject）

## Global Constraints

- 根包名：`com.pension.permission`（非 `com.example.xxx`）
- 聚合根模式：两个 private 构造函数 + 两个 static 工厂方法（`create` 业务创建、`reconstitute` 重建）；业务创建末尾调用 `validateInvariants()` + `registerDomainEvent()`；重建只调用 `validateInvariants()`
- `AggregateRoot<ID>` 基类位于 `com.example.shared.domain.aggregate.root`，继承 `Entity<ID>`，业务创建构造函数签名 `(ID id, UserNo userNo)`，重建构造函数签名 `(ID id, UserNo createdBy, UserNo updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, Version version)`
- `Entity<ID>.markUpdated(UserNo)` 会自动更新 updatedAt/version 并调用 `validateInvariants()`
- 值对象必须 `implements ValueObject`（来自 `com.example.shared.domain.aggregate.valueobject`）
- ID 类型定义在 `auth-types/.../types/`，使用 `@IdDefinition(type = IdType.ULID)`，record 实现 `Identifier<String>`
- 领域事件使用 record + `implements DomainEvent` + `static of(...)` 方法（内部 `EventId.generate()` + `LocalDateTime.now()`）
- 错误码格式 `SERVICE.AUTH.XXXX`（遵循设计文档与规则 08）
- 测试：JUnit 5 + AssertJ，纯单元测试，命名 `should_xxx_when_yyy`，使用 `@DisplayName` 中文描述
- 错误码枚举模式：`@Getter @RequiredArgsConstructor` + `implements ErrorDefinition`
- 领域服务：`@DomainService @RequiredArgsConstructor` + `final class`
- 时间戳由应用层管理，不依赖 ORM 自动填充

## File Structure

### 新建文件（auth-types）
- `auth-types/src/main/java/com/pension/permission/types/SecondaryAuthSessionId.java` — 二次授权会话 ID

### 新建文件（auth-domain）
- `auth-domain/src/main/java/com/pension/permission/domain/channel/aggregate/SecondaryAuthSession.java` — 聚合根
- `auth-domain/src/main/java/com/pension/permission/domain/channel/enumeration/SecondaryAuthStatus.java` — 状态机枚举
- `auth-domain/src/main/java/com/pension/permission/domain/channel/errorcode/SecondaryAuthErrorCode.java` — 错误码
- `auth-domain/src/main/java/com/pension/permission/domain/channel/event/SecondaryAuthInitiated.java` — 领域事件
- `auth-domain/src/main/java/com/pension/permission/domain/channel/event/SecondaryAuthCompleted.java` — 领域事件
- `auth-domain/src/main/java/com/pension/permission/domain/channel/event/SecondaryAuthRejected.java` — 领域事件
- `auth-domain/src/main/java/com/pension/permission/domain/channel/event/SecondaryAuthRevoked.java` — 领域事件
- `auth-domain/src/main/java/com/pension/permission/domain/channel/event/SecondaryAuthExpired.java` — 领域事件
- `auth-domain/src/main/java/com/pension/permission/domain/channel/event/SecondaryAuthClosed.java` — 领域事件
- `auth-domain/src/main/java/com/pension/permission/domain/channel/repository/SecondaryAuthSessionRepository.java` — Repository 接口
- `auth-domain/src/main/java/com/pension/permission/domain/channel/service/SecondaryAuthStrategy.java` — SPI 接口
- `auth-domain/src/main/java/com/pension/permission/domain/channel/spi/VerificationCodeHasher.java` — 哈希端口
- `auth-domain/src/main/java/com/pension/permission/domain/channel/valueobject/VerificationCode.java` — 验证码值对象
- `auth-domain/src/main/java/com/pension/permission/domain/channel/valueobject/PermissionSnapshot.java` — 权限快照值对象

### 新建测试文件（auth-domain）
- `auth-domain/src/test/java/com/pension/permission/domain/channel/valueobject/VerificationCodeTest.java`
- `auth-domain/src/test/java/com/pension/permission/domain/channel/valueobject/PermissionSnapshotTest.java`
- `auth-domain/src/test/java/com/pension/permission/domain/channel/aggregate/SecondaryAuthSessionTest.java`

### 修改文件（auth-domain）
- `auth-domain/pom.xml` — 添加 junit-jupiter、assertj-core 测试依赖
- `auth-domain/src/main/java/com/pension/permission/domain/channel/aggregate/Session.java` — 新增字段和方法
- `auth-domain/src/main/java/com/pension/permission/domain/channel/service/DefaultSecondaryAuthService.java` — 清理 unused import
- `auth-domain/src/main/java/com/pension/permission/domain/channel/service/SecondaryAuthService.java` — 标记 @Deprecated

### 新建文件（auth-application）
- `auth-application/src/main/java/com/pension/permission/application/channel/SecondaryAuthAppService.java` — 应用服务
- `auth-application/src/main/java/com/pension/permission/application/channel/command/InitiateSecondaryAuthCommand.java`
- `auth-application/src/main/java/com/pension/permission/application/channel/command/ConfirmSecondaryAuthCommand.java`
- `auth-application/src/main/java/com/pension/permission/application/channel/command/ResendCodeCommand.java`
- `auth-application/src/main/java/com/pension/permission/application/channel/command/RevokeSecondaryAuthCommand.java`
- `auth-application/src/main/java/com/pension/permission/application/channel/command/CloseSecondaryAuthCommand.java`
- `auth-application/src/main/java/com/pension/permission/application/channel/config/SecondaryAuthConfig.java`

### 修改文件（auth-application）
- `auth-application/src/main/java/com/pension/permission/application/channel/SessionApplicationService.java` — 新增事件监听方法

---

## Task 1: 搭建 auth-domain 测试基础设施

**Files:**
- Modify: `auth-service/auth-domain/pom.xml`
- Test: `auth-service/auth-domain/src/test/java/com/pension/permission/domain/SmokeTest.java`

**Interfaces:**
- Consumes: 父 POM 管理的 junit-jupiter、assertj-core 版本
- Produces: 可运行的测试环境（`mvn test` 可执行）

- [ ] **Step 1: 查看父 POM 确认测试依赖版本管理**

Run: `cd d:\WorkSpace\Trae\multiple-module-spring-cloud && mvn help:effective-pom -pl auth-service/auth-domain -DskipTests -q | findstr /C:"junit-jupiter" /C:"assertj-core"`

Expected: 输出包含 `junit-jupiter` 和 `assertj-core` 的版本声明（由父 POM dependencyManagement 管理）

- [ ] **Step 2: 修改 auth-domain/pom.xml 添加测试依赖**

在 `</dependencies>` 前添加：

```xml
<dependency>
  <groupId>org.junit.jupiter</groupId>
  <artifactId>junit-jupiter</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.assertj</groupId>
  <artifactId>assertj-core</artifactId>
  <scope>test</scope>
</dependency>
```

- [ ] **Step 3: 编写冒烟测试验证测试环境可用**

Create `auth-service/auth-domain/src/test/java/com/pension/permission/domain/SmokeTest.java`:

```java
package com.pension.permission.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("测试环境冒烟测试")
class SmokeTest {

  @Test
  @DisplayName("JUnit5 和 AssertJ 应当可用")
  void should_work_when_junit5_and_assertj_available() {
    assertThat(1 + 1).isEqualTo(2);
  }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `cd d:\WorkSpace\Trae\multiple-module-spring-cloud && mvn test -pl auth-service/auth-domain -Dtest=SmokeTest -q`

Expected: BUILD SUCCESS，测试通过

- [ ] **Step 5: 提交**

```bash
cd d:\WorkSpace\Trae\multiple-module-spring-cloud
git add auth-service/auth-domain/pom.xml auth-service/auth-domain/src/test/java/com/pension/permission/domain/SmokeTest.java
git commit -m "build(auth-domain): 添加 JUnit5 与 AssertJ 测试依赖"
```

---

## Task 2: 新建 SecondaryAuthSessionId

**Files:**
- Create: `auth-service/auth-types/src/main/java/com/pension/permission/types/SecondaryAuthSessionId.java`

**Interfaces:**
- Consumes: `com.example.shared.identifier.contract.Identifier`、`com.example.shared.identifier.contract.IdDefinition`、`com.example.shared.identifier.contract.IdType`（来自 shared-types）
- Produces: `SecondaryAuthSessionId`（record，`implements Identifier<String>`，`@IdDefinition(type = IdType.ULID)`）

- [ ] **Step 1: 参考现有 SessionId 实现模式**

Read `auth-service/auth-types/src/main/java/com/pension/permission/types/SessionId.java` 确认 import 路径和注解模式。

- [ ] **Step 2: 创建 SecondaryAuthSessionId**

Create `auth-service/auth-types/src/main/java/com/pension/permission/types/SecondaryAuthSessionId.java`:

```java
package com.pension.permission.types;

import com.example.shared.identifier.contract.IdDefinition;
import com.example.shared.identifier.contract.IdType;
import com.example.shared.identifier.contract.Identifier;

import java.util.Objects;

/**
 * 二次授权会话 ID.
 */
@IdDefinition(type = IdType.ULID)
public record SecondaryAuthSessionId(String value) implements Identifier<String> {
  public SecondaryAuthSessionId {
    Objects.requireNonNull(value, "value");
    if (value.isBlank()) {
      throw new IllegalArgumentException("SecondaryAuthSessionId cannot be blank.");
    }
  }
}
```

- [ ] **Step 3: 编译验证**

Run: `cd d:\WorkSpace\Trae\multiple-module-spring-cloud && mvn compile -pl auth-service/auth-types -q`

Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
cd d:\WorkSpace\Trae\multiple-module-spring-cloud
git add auth-service/auth-types/src/main/java/com/pension/permission/types/SecondaryAuthSessionId.java
git commit -m "feat(auth-types): 新增 SecondaryAuthSessionId 标识符"
```

---

## Task 3: 新建 SecondaryAuthStatus 状态机枚举

**Files:**
- Create: `auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/enumeration/SecondaryAuthStatus.java`

**Interfaces:**
- Produces: `SecondaryAuthStatus`（enum，6 个状态）

- [ ] **Step 1: 创建枚举**

```java
package com.pension.permission.domain.channel.enumeration;

/**
 * 二次授权会话状态机.
 *
 * <pre>
 *                                  ┌──────────────┐
 *                                  │   PENDING    │ ◄── 柜员发起
 *                                  └──────┬───────┘
 *              ┌──────────────────────────┼──────────────────────────┐
 *              ▼                          ▼                          ▼
 *      ┌──────────────┐          ┌──────────────┐          ┌──────────────┐
 *      │  AUTHORIZED  │          │   REJECTED   │          │   EXPIRED    │
 *      └──────┬───────┘          └──────────────┘          └──────────────┘
 *      ┌──────┴──────────────────┐
 *      ▼                         ▼
 * ┌──────────────┐         ┌──────────────┐
 * │   REVOKED    │         │   CLOSED     │
 * └──────────────┘         └──────────────┘
 * </pre>
 */
public enum SecondaryAuthStatus {
  PENDING,
  AUTHORIZED,
  REJECTED,
  EXPIRED,
  REVOKED,
  CLOSED;

  /**
   * 判断是否为终态.
   */
  public boolean isTerminal() {
    return this == REJECTED || this == EXPIRED || this == REVOKED || this == CLOSED;
  }

  /**
   * 判断是否为活跃态（可继续流转）.
   */
  public boolean isActive() {
    return this == PENDING || this == AUTHORIZED;
  }
}
```

- [ ] **Step 2: 编译验证**

Run: `cd d:\WorkSpace\Trae\multiple-module-spring-cloud && mvn compile -pl auth-service/auth-domain -q`

Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
cd d:\WorkSpace\Trae\multiple-module-spring-cloud
git add auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/enumeration/SecondaryAuthStatus.java
git commit -m "feat(auth-domain): 新增 SecondaryAuthStatus 状态机枚举"
```

---

## Task 4: 新建 SecondaryAuthErrorCode 错误码

**Files:**
- Create: `auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/errorcode/SecondaryAuthErrorCode.java`

**Interfaces:**
- Consumes: `com.example.shared.exception.ErrorDefinition`（来自 shared-exception，通过 shared-domain 传递依赖）
- Produces: `SecondaryAuthErrorCode`（enum，`implements ErrorDefinition`，14 个错误码）

- [ ] **Step 1: 确认 ErrorDefinition 接口可访问**

Read `demo-shared/shared-lib/shared-exception/src/main/java/com/example/shared/exception/ErrorDefinition.java` 确认 `getCode()` 和 `getMessage()` 方法签名。

- [ ] **Step 2: 创建错误码枚举**

```java
package com.pension.permission.domain.channel.errorcode;

import com.example.shared.exception.ErrorDefinition;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 二次授权错误码.
 *
 * <p>遵循规则 08：SERVICE 域下 AUTH 模块缩写，范围 0101-0199.</p>
 */
@Getter
@AllArgsConstructor
public enum SecondaryAuthErrorCode implements ErrorDefinition {
  ACTIVE_SESSION_EXISTS("SERVICE.AUTH.0101", "柜员已有活跃的二次授权会话"),
  SESSION_NOT_FOUND("SERVICE.AUTH.0102", "二次授权会话不存在"),
  SESSION_EXPIRED("SERVICE.AUTH.0103", "二次授权会话已过期"),
  SESSION_NOT_PENDING("SERVICE.AUTH.0104", "二次授权会话不在待授权状态"),
  SESSION_NOT_AUTHORIZED("SERVICE.AUTH.0105", "二次授权会话不在已授权状态"),
  INVALID_VERIFICATION_CODE("SERVICE.AUTH.0106", "验证码错误"),
  VERIFICATION_CODE_EXPIRED("SERVICE.AUTH.0107", "验证码已过期"),
  VERIFICATION_CODE_EXHAUSTED("SERVICE.AUTH.0108", "验证码重试次数已耗尽"),
  SNAPSHOT_EXPIRED("SERVICE.AUTH.0109", "权限快照已过期"),
  SNAPSHOT_NOT_FOUND("SERVICE.AUTH.0110", "权限快照不存在"),
  APPROVER_NOT_FOUND("SERVICE.AUTH.0111", "经办人不存在"),
  APPROVER_NOT_ASSIGNED("SERVICE.AUTH.0112", "经办人在该计划上无生效的身份分配"),
  SMS_SEND_FAILED("SERVICE.AUTH.0113", "短信发送失败"),
  CHANNEL_NOT_SUPPORTED("SERVICE.AUTH.0114", "当前渠道不支持二次授权");

  private final String code;
  private final String message;
}
```

- [ ] **Step 3: 编译验证**

Run: `cd d:\WorkSpace\Trae\multiple-module-spring-cloud && mvn compile -pl auth-service/auth-domain -q`

Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
cd d:\WorkSpace\Trae\multiple-module-spring-cloud
git add auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/errorcode/SecondaryAuthErrorCode.java
git commit -m "feat(auth-domain): 新增 SecondaryAuthErrorCode 错误码枚举"
```

---

## Task 5: 新建 VerificationCode 值对象

**Files:**
- Create: `auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/valueobject/VerificationCode.java`
- Test: `auth-service/auth-domain/src/test/java/com/pension/permission/domain/channel/valueobject/VerificationCodeTest.java`

**Interfaces:**
- Consumes: `com.example.shared.domain.aggregate.valueobject.ValueObject`
- Produces: `VerificationCode`（record，`implements ValueObject`）

- [ ] **Step 1: 先写失败的测试**

Create `auth-service/auth-domain/src/test/java/com/pension/permission/domain/channel/valueobject/VerificationCodeTest.java`:

```java
package com.pension.permission.domain.channel.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("VerificationCode 值对象测试")
class VerificationCodeTest {

  @Nested
  @DisplayName("创建验证码")
  class CreateTest {

    @Test
    @DisplayName("应当用明文和超时时间创建验证码")
    void should_create_when_valid_input() {
      LocalDateTime now = LocalDateTime.now();
      VerificationCode code = VerificationCode.of("123456", now, Duration.ofMinutes(5));
      assertThat(code.hashedCode()).isNotBlank();
      assertThat(code.sentAt()).isEqualTo(now);
      assertThat(code.expiresAt()).isEqualTo(now.plusMinutes(5));
      assertThat(code.remainingAttempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("明文验证码为空时应当抛异常")
    void should_throw_when_raw_code_null() {
      assertThatThrownBy(() -> VerificationCode.of(null, LocalDateTime.now(), Duration.ofMinutes(5)))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("rawCode");
    }

    @Test
    @DisplayName("超时时间为 null 时应当抛异常")
    void should_throw_when_timeout_null() {
      assertThatThrownBy(() -> VerificationCode.of("123456", LocalDateTime.now(), null))
        .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("过期判断")
  class IsExpiredTest {

    @Test
    @DisplayName("当前时间在过期时间之前应当未过期")
    void should_not_expired_when_before_expires_at() {
      LocalDateTime now = LocalDateTime.now();
      VerificationCode code = VerificationCode.of("123456", now, Duration.ofMinutes(5));
      assertThat(code.isExpired(now.plusMinutes(4))).isFalse();
    }

    @Test
    @DisplayName("当前时间等于过期时间应当已过期")
    void should_expired_when_equals_expires_at() {
      LocalDateTime now = LocalDateTime.now();
      VerificationCode code = VerificationCode.of("123456", now, Duration.ofMinutes(5));
      assertThat(code.isExpired(now.plusMinutes(5))).isTrue();
    }
  }

  @Nested
  @DisplayName("重试次数")
  class AttemptsTest {

    @Test
    @DisplayName("初始剩余次数应当为 3")
    void should_have_3_remaining_initially() {
      VerificationCode code = VerificationCode.of("123456", LocalDateTime.now(), Duration.ofMinutes(5));
      assertThat(code.remainingAttempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("失败一次后剩余次数应当减 1")
    void should_decrement_when_attempt_failed() {
      VerificationCode code = VerificationCode.of("123456", LocalDateTime.now(), Duration.ofMinutes(5));
      VerificationCode afterFail = code.onAttemptFailed();
      assertThat(afterFail.remainingAttempts()).isEqualTo(2);
    }

    @Test
    @DisplayName("剩余次数为 0 时应当已耗尽")
    void should_exhausted_when_zero_remaining() {
      VerificationCode code = VerificationCode.of("123456", LocalDateTime.now(), Duration.ofMinutes(5));
      VerificationCode exhausted = code.onAttemptFailed().onAttemptFailed().onAttemptFailed();
      assertThat(exhausted.isExhausted()).isTrue();
    }
  }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `cd d:\WorkSpace\Trae\multiple-module-spring-cloud && mvn test -pl auth-service/auth-domain -Dtest=VerificationCodeTest -q`

Expected: FAIL with "VerificationCode not found"

- [ ] **Step 3: 实现 VerificationCode**

> **注意**：domain 层不直接依赖 BCrypt。`hashedCode` 字段存储的是应用层哈希后的字符串，`matches` 方法通过 `VerificationCodeHasher` 端口接口完成校验（在后续 Task 中定义）。本值对象只负责存储哈希值和重试次数。

Create `auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/valueobject/VerificationCode.java`:

```java
package com.pension.permission.domain.channel.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 验证码值对象.
 *
 * <p>存储哈希后的验证码，不持有明文。明文仅在创建瞬间存在于应用层方法栈中。
 * 哈希校验通过 {@link com.pension.permission.domain.channel.spi.VerificationCodeHasher} 端口完成。</p>
 */
public record VerificationCode(
  String hashedCode,
  LocalDateTime sentAt,
  LocalDateTime expiresAt,
  int remainingAttempts
) implements ValueObject {

  public VerificationCode {
    Objects.requireNonNull(hashedCode, "hashedCode");
    Objects.requireNonNull(sentAt, "sentAt");
    Objects.requireNonNull(expiresAt, "expiresAt");
    if (expiresAt.isBefore(sentAt)) {
      throw new IllegalArgumentException("expiresAt must not be before sentAt");
    }
    if (remainingAttempts < 0) {
      throw new IllegalArgumentException("remainingAttempts must not be negative");
    }
  }

  /**
   * 创建验证码（应用层先哈希明文，再传入此方法）.
   *
   * @param hashedCode 已哈希的验证码字符串
   * @param sentAt 发送时间
   * @param timeout 超时时间
   * @return VerificationCode 实例
   */
  public static VerificationCode of(String hashedCode, LocalDateTime sentAt, Duration timeout) {
    Objects.requireNonNull(hashedCode, "hashedCode");
    Objects.requireNonNull(sentAt, "sentAt");
    Objects.requireNonNull(timeout, "timeout");
    return new VerificationCode(hashedCode, sentAt, sentAt.plus(timeout), 3);
  }

  /**
   * 校验是否已过期.
   */
  public boolean isExpired(LocalDateTime now) {
    return !now.isBefore(expiresAt);
  }

  /**
   * 校验重试次数是否已耗尽.
   */
  public boolean isExhausted() {
    return remainingAttempts <= 0;
  }

  /**
   * 记录一次校验失败，返回剩余次数减 1 的新实例（不可变）.
   */
  public VerificationCode onAttemptFailed() {
    return new VerificationCode(hashedCode, sentAt, expiresAt, Math.max(0, remainingAttempts - 1));
  }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `cd d:\WorkSpace\Trae\multiple-module-spring-cloud && mvn test -pl auth-service/auth-domain -Dtest=VerificationCodeTest -q`

Expected: PASS

- [ ] **Step 5: 提交**

```bash
cd d:\WorkSpace\Trae\multiple-module-spring-cloud
git add auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/valueobject/VerificationCode.java auth-service/auth-domain/src/test/java/com/pension/permission/domain/channel/valueobject/VerificationCodeTest.java
git commit -m "feat(auth-domain): 新增 VerificationCode 值对象"
```

---

## Task 6: 新建 PermissionSnapshot 值对象

**Files:**
- Create: `auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/valueobject/PermissionSnapshot.java`
- Test: `auth-service/auth-domain/src/test/java/com/pension/permission/domain/channel/valueobject/PermissionSnapshotTest.java`

**Interfaces:**
- Consumes: `com.pension.permission.domain.authorization.valueobject.Permission`（已有）
- Produces: `PermissionSnapshot`（record，`implements ValueObject`）

- [ ] **Step 1: 确认 Permission 值对象结构**

Read `auth-service/auth-domain/src/main/java/com/pension/permission/domain/authorization/valueobject/Permission.java` 确认 `businessCode()`、`actionCode()` 方法。

- [ ] **Step 2: 先写失败的测试**

Create `auth-service/auth-domain/src/test/java/com/pension/permission/domain/channel/valueobject/PermissionSnapshotTest.java`:

```java
package com.pension.permission.domain.channel.valueobject;

import com.pension.permission.domain.authorization.valueobject.ActionCode;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import com.pension.permission.domain.authorization.valueobject.Permission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PermissionSnapshot 值对象测试")
class PermissionSnapshotTest {

  private Permission permission(String business, String action) {
    return new Permission(new BusinessCode(business), new ActionCode(action));
  }

  @Nested
  @DisplayName("创建快照")
  class CreateTest {

    @Test
    @DisplayName("应当用权限集合和 TTL 创建快照")
    void should_create_when_valid_input() {
      LocalDateTime now = LocalDateTime.now();
      Set<Permission> permissions = Set.of(
        permission("ANNUITY_CONTRIBUTION", "HANDLE"),
        permission("ANNUITY_PAYMENT", "QUERY"));
      PermissionSnapshot snapshot = PermissionSnapshot.of(permissions, now, Duration.ofSeconds(30));
      assertThat(snapshot.permissions()).hasSize(2);
      assertThat(snapshot.frozenAt()).isEqualTo(now);
      assertThat(snapshot.expiresAt()).isEqualTo(now.plusSeconds(30));
    }

    @Test
    @DisplayName("权限集合为空时应当抛异常")
    void should_throw_when_permissions_empty() {
      assertThatThrownBy(() ->
        PermissionSnapshot.of(Set.of(), LocalDateTime.now(), Duration.ofSeconds(30)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("permissions");
    }

    @Test
    @DisplayName("frozenAt 为 null 时应当抛异常")
    void should_throw_when_frozen_at_null() {
      assertThatThrownBy(() ->
        PermissionSnapshot.of(Set.of(permission("B", "A")), null, Duration.ofSeconds(30)))
        .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("过期判断")
  class IsExpiredTest {

    @Test
    @DisplayName("当前时间在过期前应当未过期")
    void should_not_expired_before_expires_at() {
      LocalDateTime now = LocalDateTime.now();
      PermissionSnapshot snapshot = PermissionSnapshot.of(
        Set.of(permission("B", "A")), now, Duration.ofSeconds(30));
      assertThat(snapshot.isExpired(now.plusSeconds(29))).isFalse();
    }

    @Test
    @DisplayName("当前时间到达过期时间应当已过期")
    void should_expired_when_reaches_expires_at() {
      LocalDateTime now = LocalDateTime.now();
      PermissionSnapshot snapshot = PermissionSnapshot.of(
        Set.of(permission("B", "A")), now, Duration.ofSeconds(30));
      assertThat(snapshot.isExpired(now.plusSeconds(30))).isTrue();
    }
  }

  @Nested
  @DisplayName("权限包含判断")
  class ContainsTest {

    @Test
    @DisplayName("快照中包含的权限应当返回 true")
    void should_return_true_when_permission_in_snapshot() {
      LocalDateTime now = LocalDateTime.now();
      Permission p = permission("ANNUITY_CONTRIBUTION", "HANDLE");
      PermissionSnapshot snapshot = PermissionSnapshot.of(Set.of(p), now, Duration.ofSeconds(30));
      assertThat(snapshot.contains(p)).isTrue();
    }

    @Test
    @DisplayName("快照中不包含的权限应当返回 false")
    void should_return_false_when_permission_not_in_snapshot() {
      LocalDateTime now = LocalDateTime.now();
      Permission p1 = permission("ANNUITY_CONTRIBUTION", "HANDLE");
      Permission p2 = permission("ANNUITY_PAYMENT", "QUERY");
      PermissionSnapshot snapshot = PermissionSnapshot.of(Set.of(p1), now, Duration.ofSeconds(30));
      assertThat(snapshot.contains(p2)).isFalse();
    }
  }
}
```

- [ ] **Step 3: 运行测试验证失败**

Run: `cd d:\WorkSpace\Trae\multiple-module-spring-cloud && mvn test -pl auth-service/auth-domain -Dtest=PermissionSnapshotTest -q`

Expected: FAIL with "PermissionSnapshot not found"

- [ ] **Step 4: 实现 PermissionSnapshot**

Create `auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/valueobject/PermissionSnapshot.java`:

```java
package com.pension.permission.domain.channel.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;
import com.pension.permission.domain.authorization.valueobject.Permission;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 权限快照值对象.
 *
 * <p>二次授权确认瞬间冻结的经办人权限集合，用于授权后业务办理时的快速判定。
 * 快照不可变，TTL 过期后需要重新发起授权。</p>
 */
public record PermissionSnapshot(
  Set<Permission> permissions,
  LocalDateTime frozenAt,
  LocalDateTime expiresAt
) implements ValueObject {

  public PermissionSnapshot {
    Objects.requireNonNull(permissions, "permissions");
    Objects.requireNonNull(frozenAt, "frozenAt");
    Objects.requireNonNull(expiresAt, "expiresAt");
    if (permissions.isEmpty()) {
      throw new IllegalArgumentException("permissions must not be empty");
    }
    if (expiresAt.isBefore(frozenAt)) {
      throw new IllegalArgumentException("expiresAt must not be before frozenAt");
    }
    permissions = Collections.unmodifiableSet(new HashSet<>(permissions));
  }

  /**
   * 创建权限快照.
   *
   * @param permissions 冻结的权限集合
   * @param frozenAt 冻结时间
   * @param ttl 存活时间（从 frozenAt 开始计算）
   * @return PermissionSnapshot 实例
   */
  public static PermissionSnapshot of(Set<Permission> permissions, LocalDateTime frozenAt, Duration ttl) {
    Objects.requireNonNull(ttl, "ttl");
    return new PermissionSnapshot(permissions, frozenAt, frozenAt.plus(ttl));
  }

  /**
   * 校验快照是否已过期.
   */
  public boolean isExpired(LocalDateTime now) {
    return !now.isBefore(expiresAt);
  }

  /**
   * 校验快照是否包含指定权限.
   */
  public boolean contains(Permission permission) {
    return permissions.contains(permission);
  }
}
```

- [ ] **Step 5: 运行测试验证通过**

Run: `cd d:\WorkSpace\Trae\multiple-module-spring-cloud && mvn test -pl auth-service/auth-domain -Dtest=PermissionSnapshotTest -q`

Expected: PASS

- [ ] **Step 6: 提交**

```bash
cd d:\WorkSpace\Trae\multiple-module-spring-cloud
git add auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/valueobject/PermissionSnapshot.java auth-service/auth-domain/src/test/java/com/pension/permission/domain/channel/valueobject/PermissionSnapshotTest.java
git commit -m "feat(auth-domain): 新增 PermissionSnapshot 值对象"
```

---

## Task 7: 新建 VerificationCodeHasher 端口接口

**Files:**
- Create: `auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/spi/VerificationCodeHasher.java`

**Interfaces:**
- Produces: `VerificationCodeHasher`（SPI 接口，`hash` 和 `matches` 方法）

- [ ] **Step 1: 创建端口接口**

```java
package com.pension.permission.domain.channel.spi;

/**
 * 验证码哈希端口.
 *
 * <p>domain 层不直接依赖 BCrypt 等加密库，通过此端口接口隔离。
 * 实现由 infrastructure 层提供（如 BCryptVerificationCodeHasher）。</p>
 */
public interface VerificationCodeHasher {

  /**
   * 对明文验证码进行哈希.
   *
   * @param rawCode 明文验证码
   * @return 哈希后的字符串
   */
  String hash(String rawCode);

  /**
   * 校验明文验证码是否匹配哈希值.
   *
   * @param rawCode 明文验证码
   * @param hashedCode 哈希后的字符串
   * @return 是否匹配
   */
  boolean matches(String rawCode, String hashedCode);
}
```

- [ ] **Step 2: 编译验证**

Run: `cd d:\WorkSpace\Trae\multiple-module-spring-cloud && mvn compile -pl auth-service/auth-domain -q`

Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
cd d:\WorkSpace\Trae\multiple-module-spring-cloud
git add auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/spi/VerificationCodeHasher.java
git commit -m "feat(auth-domain): 新增 VerificationCodeHasher 端口接口"
```

---

## Task 8: 新建 6 个领域事件

**Files:**
- Create: `auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/event/SecondaryAuthInitiated.java`
- Create: `auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/event/SecondaryAuthCompleted.java`
- Create: `auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/event/SecondaryAuthRejected.java`
- Create: `auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/event/SecondaryAuthRevoked.java`
- Create: `auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/event/SecondaryAuthExpired.java`
- Create: `auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/event/SecondaryAuthClosed.java`

**Interfaces:**
- Consumes: `com.example.shared.domain.event.DomainEvent`、`com.example.shared.identifier.id.EventId`、`com.pension.permission.types.SecondaryAuthSessionId`
- Produces: 6 个领域事件 record

- [ ] **Step 1: 参考 SessionIdentityElevated 事件实现模式**

Read `auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/event/SessionIdentityElevated.java` 确认 record 字段顺序和 `of()` 方法模式。

- [ ] **Step 2: 创建 SecondaryAuthInitiated 事件**

```java
package com.pension.permission.domain.channel.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.shared.identifier.user.UserNo;
import com.pension.permission.types.SecondaryAuthSessionId;

import java.time.LocalDateTime;

/**
 * 二次授权发起事件.
 */
public record SecondaryAuthInitiated(
  SecondaryAuthSessionId sessionId,
  UserNo tellerAccountId,
  UserNo approverAccountId,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo createdBy
) implements DomainEvent {

  public static SecondaryAuthInitiated of(
    SecondaryAuthSessionId sessionId,
    UserNo tellerAccountId,
    UserNo approverAccountId,
    UserNo createdBy
  ) {
    return new SecondaryAuthInitiated(
      sessionId, tellerAccountId, approverAccountId,
      EventId.generate(), LocalDateTime.now(), createdBy);
  }
}
```

- [ ] **Step 3: 创建 SecondaryAuthCompleted 事件**

```java
package com.pension.permission.domain.channel.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.shared.identifier.user.UserNo;
import com.pension.permission.domain.channel.valueobject.EffectiveIdentity;
import com.pension.permission.domain.channel.valueobject.PermissionSnapshot;
import com.pension.permission.types.SecondaryAuthSessionId;

import java.time.LocalDateTime;

/**
 * 二次授权完成事件.
 */
public record SecondaryAuthCompleted(
  SecondaryAuthSessionId sessionId,
  UserNo tellerAccountId,
  UserNo approverAccountId,
  EffectiveIdentity effectiveIdentity,
  PermissionSnapshot permissionSnapshot,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo createdBy
) implements DomainEvent {

  public static SecondaryAuthCompleted of(
    SecondaryAuthSessionId sessionId,
    UserNo tellerAccountId,
    UserNo approverAccountId,
    EffectiveIdentity effectiveIdentity,
    PermissionSnapshot permissionSnapshot,
    UserNo createdBy
  ) {
    return new SecondaryAuthCompleted(
      sessionId, tellerAccountId, approverAccountId,
      effectiveIdentity, permissionSnapshot,
      EventId.generate(), LocalDateTime.now(), createdBy);
  }
}
```

- [ ] **Step 4: 创建 SecondaryAuthRejected 事件**

```java
package com.pension.permission.domain.channel.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.shared.identifier.user.UserNo;
import com.pension.permission.types.SecondaryAuthSessionId;

import java.time.LocalDateTime;

/**
 * 二次授权拒绝事件.
 */
public record SecondaryAuthRejected(
  SecondaryAuthSessionId sessionId,
  UserNo tellerAccountId,
  UserNo approverAccountId,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo createdBy
) implements DomainEvent {

  public static SecondaryAuthRejected of(
    SecondaryAuthSessionId sessionId,
    UserNo tellerAccountId,
    UserNo approverAccountId,
    UserNo createdBy
  ) {
    return new SecondaryAuthRejected(
      sessionId, tellerAccountId, approverAccountId,
      EventId.generate(), LocalDateTime.now(), createdBy);
  }
}
```

- [ ] **Step 5: 创建 SecondaryAuthRevoked 事件**

```java
package com.pension.permission.domain.channel.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.shared.identifier.user.UserNo;
import com.pension.permission.types.SecondaryAuthSessionId;

import java.time.LocalDateTime;

/**
 * 二次授权撤销事件.
 */
public record SecondaryAuthRevoked(
  SecondaryAuthSessionId sessionId,
  UserNo tellerAccountId,
  UserNo approverAccountId,
  String reason,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo createdBy
) implements DomainEvent {

  public static SecondaryAuthRevoked of(
    SecondaryAuthSessionId sessionId,
    UserNo tellerAccountId,
    UserNo approverAccountId,
    String reason,
    UserNo createdBy
  ) {
    return new SecondaryAuthRevoked(
      sessionId, tellerAccountId, approverAccountId, reason,
      EventId.generate(), LocalDateTime.now(), createdBy);
  }
}
```

- [ ] **Step 6: 创建 SecondaryAuthExpired 事件**

```java
package com.pension.permission.domain.channel.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.shared.identifier.user.UserNo;
import com.pension.permission.types.SecondaryAuthSessionId;

import java.time.LocalDateTime;

/**
 * 二次授权过期事件.
 */
public record SecondaryAuthExpired(
  SecondaryAuthSessionId sessionId,
  UserNo tellerAccountId,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo createdBy
) implements DomainEvent {

  public static SecondaryAuthExpired of(
    SecondaryAuthSessionId sessionId,
    UserNo tellerAccountId,
    UserNo createdBy
  ) {
    return new SecondaryAuthExpired(
      sessionId, tellerAccountId,
      EventId.generate(), LocalDateTime.now(), createdBy);
  }
}
```

- [ ] **Step 7: 创建 SecondaryAuthClosed 事件**

```java
package com.pension.permission.domain.channel.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.shared.identifier.user.UserNo;
import com.pension.permission.types.SecondaryAuthSessionId;

import java.time.LocalDateTime;

/**
 * 二次授权关闭事件.
 */
public record SecondaryAuthClosed(
  SecondaryAuthSessionId sessionId,
  UserNo tellerAccountId,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo createdBy
) implements DomainEvent {

  public static SecondaryAuthClosed of(
    SecondaryAuthSessionId sessionId,
    UserNo tellerAccountId,
    UserNo createdBy
  ) {
    return new SecondaryAuthClosed(
      sessionId, tellerAccountId,
      EventId.generate(), LocalDateTime.now(), createdBy);
  }
}
```

- [ ] **Step 8: 编译验证**

Run: `cd d:\WorkSpace\Trae\multiple-module-spring-cloud && mvn compile -pl auth-service/auth-domain -q`

Expected: BUILD SUCCESS

- [ ] **Step 9: 提交**

```bash
cd d:\WorkSpace\Trae\multiple-module-spring-cloud
git add auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/event/
git commit -m "feat(auth-domain): 新增 6 个二次授权领域事件"
```

---

## Task 9: 新建 SecondaryAuthSession 聚合根（initiate 方法）

**Files:**
- Create: `auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/aggregate/SecondaryAuthSession.java`
- Test: `auth-service/auth-domain/src/test/java/com/pension/permission/domain/channel/aggregate/SecondaryAuthSessionTest.java`

**Interfaces:**
- Consumes:
  - `com.example.shared.domain.aggregate.root.AggregateRoot`
  - `com.example.shared.domain.aggregate.version.Version`
  - `com.example.shared.exception.DomainException`
  - `com.example.shared.identifier.user.UserNo`
  - `com.example.shared.annuity.AnnuityChannel`
  - `com.example.shared.contactinfo.Mobile`
  - `com.pension.permission.types.SecondaryAuthSessionId`
  - `com.pension.permission.domain.authorization.valueobject.Permission`（通过 PermissionSnapshot 间接消费）
  - `com.pension.permission.domain.credential.valueobject.owner.CredentialOwner`
- Produces: `SecondaryAuthSession`（聚合根，包含 `initiate`/`authorize`/`recordFailedAttempt`/`resendVerificationCode`/`revoke`/`close`/`expireIfTimeout` 方法）

- [ ] **Step 1: 先写 initiate 方法的测试**

Create `auth-service/auth-domain/src/test/java/com/pension/permission/domain/channel/aggregate/SecondaryAuthSessionTest.java`:

```java
package com.pension.permission.domain.channel.aggregate;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.contactinfo.Mobile;
import com.example.shared.identifier.user.UserNo;
import com.pension.permission.domain.channel.enumeration.SecondaryAuthStatus;
import com.pension.permission.domain.channel.valueobject.VerificationCode;
import com.pension.permission.domain.credential.valueobject.owner.CustomerCredentialOwner;
import com.pension.permission.domain.credential.valueobject.owner.CredentialOwner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SecondaryAuthSession 聚合根测试")
class SecondaryAuthSessionTest {

  private VerificationCode code(LocalDateTime now) {
    return VerificationCode.of("hashed-123456", now, Duration.ofMinutes(5));
  }

  private CredentialOwner owner() {
    return new CustomerCredentialOwner(null);
  }

  private Mobile mobile() {
    return Mobile.of("+8613800138000");
  }

  @Nested
  @DisplayName("initiate 发起授权")
  class InitiateTest {

    @Test
    @DisplayName("发起后状态应当为 PENDING")
    void should_be_pending_when_initiated() {
      LocalDateTime now = LocalDateTime.now();
      SecondaryAuthSession session = SecondaryAuthSession.initiate(
        new SecondaryAuthSessionIdForTest("s-1"),
        UserNo.of("teller-1"),
        owner(),
        UserNo.of("approver-1"),
        mobile(),
        null,
        code(now),
        Duration.ofMinutes(5),
        Duration.ofHours(2),
        UserNo.of("teller-1"));
      assertThat(session.status()).isEqualTo(SecondaryAuthStatus.PENDING);
    }

    @Test
    @DisplayName("发起后应当注册 SecondaryAuthInitiated 事件")
    void should_register_initiated_event() {
      LocalDateTime now = LocalDateTime.now();
      SecondaryAuthSession session = SecondaryAuthSession.initiate(
        new SecondaryAuthSessionIdForTest("s-1"),
        UserNo.of("teller-1"),
        owner(),
        UserNo.of("approver-1"),
        mobile(),
        null,
        code(now),
        Duration.ofMinutes(5),
        Duration.ofHours(2),
        UserNo.of("teller-1"));
      assertThat(session.domainEvents())
        .anyMatch(e -> "SecondaryAuthInitiated".equals(e.eventType()));
    }

    @Test
    @DisplayName("柜员账号为 null 时应当抛异常")
    void should_throw_when_teller_null() {
      assertThatThrownBy(() -> SecondaryAuthSession.initiate(
        new SecondaryAuthSessionIdForTest("s-1"),
        null,
        owner(),
        UserNo.of("approver-1"),
        mobile(),
        null,
        code(LocalDateTime.now()),
        Duration.ofMinutes(5),
        Duration.ofHours(2),
        UserNo.of("teller-1")))
        .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("验证码为 null 时应当抛异常")
    void should_throw_when_code_null() {
      assertThatThrownBy(() -> SecondaryAuthSession.initiate(
        new SecondaryAuthSessionIdForTest("s-1"),
        UserNo.of("teller-1"),
        owner(),
        UserNo.of("approver-1"),
        mobile(),
        null,
        null,
        Duration.ofMinutes(5),
        Duration.ofHours(2),
        UserNo.of("teller-1")))
        .isInstanceOf(NullPointerException.class);
    }
  }

  /** 用于测试的 SecondaryAuthSessionId 简单实现. */
  private record SecondaryAuthSessionIdForTest(String value)
    implements com.example.shared.identifier.contract.Identifier<String> {
    @Override public String value() { return value; }
  }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `cd d:\WorkSpace\Trae\multiple-module-spring-cloud && mvn test -pl auth-service/auth-domain -Dtest=SecondaryAuthSessionTest -q`

Expected: FAIL with "SecondaryAuthSession not found"

- [ ] **Step 3: 实现 SecondaryAuthSession 聚合根（initiate 方法 + 基本骨架）**

Create `auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/aggregate/SecondaryAuthSession.java`:

```java
package com.pension.permission.domain.channel.aggregate;

import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.version.Version;
import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.contactinfo.Mobile;
import com.example.shared.exception.DomainException;
import com.example.shared.identifier.user.UserNo;
import com.pension.permission.domain.channel.enumeration.SecondaryAuthStatus;
import com.pension.permission.domain.channel.errorcode.SecondaryAuthErrorCode;
import com.pension.permission.domain.channel.event.SecondaryAuthInitiated;
import com.pension.permission.domain.channel.valueobject.EffectiveIdentity;
import com.pension.permission.domain.channel.valueobject.PermissionSnapshot;
import com.pension.permission.domain.channel.valueobject.VerificationCode;
import com.pension.permission.domain.credential.valueobject.owner.CredentialOwner;
import com.pension.permission.types.SecondaryAuthSessionId;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 二次授权会话聚合根.
 *
 * <p>支持短信验证码两段式授权：
 * <ol>
 *   <li>柜员发起 → PENDING，生成验证码，发短信</li>
 *   <li>柜员输入验证码 → AUTHORIZED，冻结权限快照</li>
 *   <li>经办人撤销 / 紧急收权 → REVOKED</li>
 *   <li>柜员登出 / 会话过期 → CLOSED</li>
 *   <li>待授权超时 / 快照 TTL 过期 → EXPIRED</li>
 *   <li>验证码重试耗尽 → REJECTED</li>
 * </ol>
 * </p>
 */
public class SecondaryAuthSession extends AggregateRoot<SecondaryAuthSessionId> {

  private final UserNo tellerAccountId;
  private UserNo approverAccountId;
  private final CredentialOwner credentialOwner;
  private final Mobile approverMobile;
  private final com.example.shared.annuity.PlanNo planId;
  private VerificationCode verificationCode;
  private EffectiveIdentity effectiveIdentity;
  private PermissionSnapshot permissionSnapshot;
  private SecondaryAuthStatus status;
  private final LocalDateTime initiatedAt;
  private LocalDateTime authorizedAt;
  private final LocalDateTime expiresAt;
  private String revokeReason;

  private SecondaryAuthSession(
    SecondaryAuthSessionId id, UserNo creator,
    UserNo tellerAccountId, UserNo approverAccountId,
    CredentialOwner credentialOwner, Mobile approverMobile,
    com.example.shared.annuity.PlanNo planId,
    VerificationCode verificationCode,
    LocalDateTime initiatedAt, LocalDateTime expiresAt,
    SecondaryAuthStatus status
  ) {
    super(id, creator);
    this.tellerAccountId = tellerAccountId;
    this.approverAccountId = approverAccountId;
    this.credentialOwner = credentialOwner;
    this.approverMobile = approverMobile;
    this.planId = planId;
    this.verificationCode = verificationCode;
    this.initiatedAt = initiatedAt;
    this.expiresAt = expiresAt;
    this.status = status;
    validateInvariants();
    registerDomainEvent(SecondaryAuthInitiated.of(
      id, tellerAccountId, approverAccountId, creator));
  }

  private SecondaryAuthSession(
    SecondaryAuthSessionId id, UserNo createdBy, UserNo updatedBy,
    LocalDateTime createdAt, LocalDateTime updatedAt, Version version,
    UserNo tellerAccountId, UserNo approverAccountId,
    CredentialOwner credentialOwner, Mobile approverMobile,
    com.example.shared.annuity.PlanNo planId,
    VerificationCode verificationCode,
    EffectiveIdentity effectiveIdentity,
    PermissionSnapshot permissionSnapshot,
    SecondaryAuthStatus status,
    LocalDateTime initiatedAt, LocalDateTime authorizedAt,
    LocalDateTime expiresAt, String revokeReason
  ) {
    super(id, createdBy, updatedBy, createdAt, updatedAt, version);
    this.tellerAccountId = tellerAccountId;
    this.approverAccountId = approverAccountId;
    this.credentialOwner = credentialOwner;
    this.approverMobile = approverMobile;
    this.planId = planId;
    this.verificationCode = verificationCode;
    this.effectiveIdentity = effectiveIdentity;
    this.permissionSnapshot = permissionSnapshot;
    this.status = status;
    this.initiatedAt = initiatedAt;
    this.authorizedAt = authorizedAt;
    this.expiresAt = expiresAt;
    this.revokeReason = revokeReason;
    validateInvariants();
  }

  /**
   * 柜员发起二次授权（PENDING）.
   *
   * @param id 会话 ID
   * @param tellerAccountId 柜员账号
   * @param credentialOwner 发起时使用的凭证持有者
   * @param approverAccountId 经办人账号
   * @param approverMobile 经办人手机号
   * @param planId 目标计划（可为 null，用于非计划场景）
   * @param verificationCode 验证码值对象
   * @param pendingTimeout 待授权超时时间（默认 5 分钟）
   * @param sessionTimeout 会话过期时间（默认 2 小时）
   * @param operator 操作人（柜员）
   * @return SecondaryAuthSession 实例
   */
  public static SecondaryAuthSession initiate(
    SecondaryAuthSessionId id,
    UserNo tellerAccountId,
    CredentialOwner credentialOwner,
    UserNo approverAccountId,
    Mobile approverMobile,
    com.example.shared.annuity.PlanNo planId,
    VerificationCode verificationCode,
    Duration pendingTimeout,
    Duration sessionTimeout,
    UserNo operator
  ) {
    Objects.requireNonNull(tellerAccountId, "tellerAccountId");
    Objects.requireNonNull(credentialOwner, "credentialOwner");
    Objects.requireNonNull(approverAccountId, "approverAccountId");
    Objects.requireNonNull(approverMobile, "approverMobile");
    Objects.requireNonNull(verificationCode, "verificationCode");
    Objects.requireNonNull(pendingTimeout, "pendingTimeout");
    Objects.requireNonNull(sessionTimeout, "sessionTimeout");
    Objects.requireNonNull(operator, "operator");
    LocalDateTime now = LocalDateTime.now();
    return new SecondaryAuthSession(
      id, operator,
      tellerAccountId, approverAccountId,
      credentialOwner, approverMobile,
      planId, verificationCode,
      now, now.plus(sessionTimeout),
      SecondaryAuthStatus.PENDING);
  }

  @Override
  protected void validateInvariants() {
    if (tellerAccountId == null) {
      throw new IllegalStateException("tellerAccountId cannot be null");
    }
    if (credentialOwner == null) {
      throw new IllegalStateException("credentialOwner cannot be null");
    }
    if (approverMobile == null) {
      throw new IllegalStateException("approverMobile cannot be null");
    }
    if (status == null) {
      throw new IllegalStateException("status cannot be null");
    }
    if (initiatedAt == null) {
      throw new IllegalStateException("initiatedAt cannot be null");
    }
    if (expiresAt == null) {
      throw new IllegalStateException("expiresAt cannot be null");
    }
    if (status == SecondaryAuthStatus.PENDING && verificationCode == null) {
      throw new IllegalStateException("verificationCode cannot be null when PENDING");
    }
    if (status == SecondaryAuthStatus.AUTHORIZED) {
      if (effectiveIdentity == null) {
        throw new IllegalStateException("effectiveIdentity cannot be null when AUTHORIZED");
      }
      if (permissionSnapshot == null) {
        throw new IllegalStateException("permissionSnapshot cannot be null when AUTHORIZED");
      }
      if (approverAccountId == null) {
        throw new IllegalStateException("approverAccountId cannot be null when AUTHORIZED");
      }
    }
  }

  // 查询方法

  public SecondaryAuthStatus status() { return status; }
  public UserNo tellerAccountId() { return tellerAccountId; }
  public UserNo approverAccountId() { return approverAccountId; }
  public CredentialOwner credentialOwner() { return credentialOwner; }
  public Mobile approverMobile() { return approverMobile; }
  public com.example.shared.annuity.PlanNo planId() { return planId; }
  public VerificationCode verificationCode() { return verificationCode; }
  public EffectiveIdentity effectiveIdentity() { return effectiveIdentity; }
  public PermissionSnapshot permissionSnapshot() { return permissionSnapshot; }
  public LocalDateTime initiatedAt() { return initiatedAt; }
  public LocalDateTime authorizedAt() { return authorizedAt; }
  public LocalDateTime expiresAt() { return expiresAt; }
  public String revokeReason() { return revokeReason; }

  public boolean isEffectiveAt(LocalDateTime now) {
    return status == SecondaryAuthStatus.AUTHORIZED
      && !expiresAt.isBefore(now)
      && (permissionSnapshot == null || !permissionSnapshot.isExpired(now));
  }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `cd d:\WorkSpace\Trae\multiple-module-spring-cloud && mvn test -pl auth-service/auth-domain -Dtest=SecondaryAuthSessionTest -q`

Expected: PASS

- [ ] **Step 5: 提交**

```bash
cd d:\WorkSpace\Trae\multiple-module-spring-cloud
git add auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/aggregate/SecondaryAuthSession.java auth-service/auth-domain/src/test/java/com/pension/permission/domain/channel/aggregate/SecondaryAuthSessionTest.java
git commit -m "feat(auth-domain): 新增 SecondaryAuthSession 聚合根 initiate 方法"
```

---

## Task 10: 扩展 SecondaryAuthSession（authorize / recordFailedAttempt / resendVerificationCode 方法）

**Files:**
- Modify: `auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/aggregate/SecondaryAuthSession.java`
- Test: `auth-service/auth-domain/src/test/java/com/pension/permission/domain/channel/aggregate/SecondaryAuthSessionTest.java`

**Interfaces:**
- Consumes: `com.pension.permission.domain.channel.spi.VerificationCodeHasher`（Task 7 产出）
- Produces: `authorize` / `recordFailedAttempt` / `resendVerificationCode` 方法

- [ ] **Step 1: 先写 authorize 方法的测试**

在 `SecondaryAuthSessionTest.java` 中追加测试类（在 `InitiateTest` 后）：

```java
@Nested
@DisplayName("authorize 确认授权")
class AuthorizeTest {

  @Test
  @DisplayName("验证码匹配时应当流转到 AUTHORIZED 并冻结快照")
  void should_be_authorized_when_code_matches(com.pension.permission.domain.channel.spi.VerificationCodeHasher hasher) {
    // 此测试通过参数注入 hasher（JUnit 不支持，改为手动构造）
  }

  @Test
  @DisplayName("验证码不匹配时应当抛 INVALID_VERIFICATION_CODE")
  void should_throw_when_code_not_match() {
    LocalDateTime now = LocalDateTime.now();
    SecondaryAuthSession session = SecondaryAuthSession.initiate(
      new SecondaryAuthSessionIdForTest("s-1"),
      UserNo.of("teller-1"), owner(),
      UserNo.of("approver-1"), mobile(), null,
      code(now), Duration.ofMinutes(5), Duration.ofHours(2),
      UserNo.of("teller-1"));

    com.pension.permission.domain.channel.spi.VerificationCodeHasher rejectHasher =
      (raw, hashed) -> false;

    com.pension.permission.domain.channel.valueobject.PermissionSnapshot snapshot =
      com.pension.permission.domain.channel.valueobject.PermissionSnapshot.of(
        java.util.Set.of(), now, Duration.ofSeconds(30));
    // 注意：PermissionSnapshot.of 不允许空集合，此处用单元素集合
    com.pension.permission.domain.authorization.valueobject.Permission p =
      new com.pension.permission.domain.authorization.valueobject.Permission(
        new com.pension.permission.domain.authorization.valueobject.BusinessCode("B"),
        new com.pension.permission.domain.authorization.valueobject.ActionCode("A"));
    snapshot = com.pension.permission.domain.channel.valueobject.PermissionSnapshot.of(
      java.util.Set.of(p), now, Duration.ofSeconds(30));

    com.pension.permission.domain.channel.valueobject.EffectiveIdentity identity =
      com.pension.permission.domain.channel.valueobject.EffectiveIdentity.direct(UserNo.of("approver-1"));

    assertThatThrownBy(() -> session.authorize(
      "wrong-code", snapshot, identity, rejectHasher, UserNo.of("teller-1")))
      .isInstanceOf(com.example.shared.exception.DomainException.class);
    assertThat(session.status()).isEqualTo(SecondaryAuthStatus.PENDING);
  }

  @Test
  @DisplayName("验证码匹配时应当流转到 AUTHORIZED 并清空 verificationCode")
  void should_clear_code_when_authorized() {
    LocalDateTime now = LocalDateTime.now();
    SecondaryAuthSession session = SecondaryAuthSession.initiate(
      new SecondaryAuthSessionIdForTest("s-1"),
      UserNo.of("teller-1"), owner(),
      UserNo.of("approver-1"), mobile(), null,
      code(now), Duration.ofMinutes(5), Duration.ofHours(2),
      UserNo.of("teller-1"));

    com.pension.permission.domain.channel.spi.VerificationCodeHasher acceptHasher =
      (raw, hashed) -> true;

    com.pension.permission.domain.authorization.valueobject.Permission p =
      new com.pension.permission.domain.authorization.valueobject.Permission(
        new com.pension.permission.domain.authorization.valueobject.BusinessCode("B"),
        new com.pension.permission.domain.authorization.valueobject.ActionCode("A"));
    com.pension.permission.domain.channel.valueobject.PermissionSnapshot snapshot =
      com.pension.permission.domain.channel.valueobject.PermissionSnapshot.of(
        java.util.Set.of(p), now, Duration.ofSeconds(30));
    com.pension.permission.domain.channel.valueobject.EffectiveIdentity identity =
      com.pension.permission.domain.channel.valueobject.EffectiveIdentity.direct(UserNo.of("approver-1"));

    session.authorize("123456", snapshot, identity, acceptHasher, UserNo.of("teller-1"));

    assertThat(session.status()).isEqualTo(SecondaryAuthStatus.AUTHORIZED);
    assertThat(session.verificationCode()).isNull();
    assertThat(session.permissionSnapshot()).isEqualTo(snapshot);
    assertThat(session.effectiveIdentity()).isEqualTo(identity);
    assertThat(session.domainEvents())
      .anyMatch(e -> "SecondaryAuthCompleted".equals(e.eventType()));
  }

  @Test
  @DisplayName("非 PENDING 状态调用 authorize 应当抛异常")
  void should_throw_when_not_pending() {
    LocalDateTime now = LocalDateTime.now();
    SecondaryAuthSession session = SecondaryAuthSession.initiate(
      new SecondaryAuthSessionIdForTest("s-1"),
      UserNo.of("teller-1"), owner(),
      UserNo.of("approver-1"), mobile(), null,
      code(now), Duration.ofMinutes(5), Duration.ofHours(2),
      UserNo.of("teller-1"));

    com.pension.permission.domain.channel.spi.VerificationCodeHasher acceptHasher =
      (raw, hashed) -> true;
    com.pension.permission.domain.authorization.valueobject.Permission p =
      new com.pension.permission.domain.authorization.valueobject.Permission(
        new com.pension.permission.domain.authorization.valueobject.BusinessCode("B"),
        new com.pension.permission.domain.authorization.valueobject.ActionCode("A"));
    com.pension.permission.domain.channel.valueobject.PermissionSnapshot snapshot =
      com.pension.permission.domain.channel.valueobject.PermissionSnapshot.of(
        java.util.Set.of(p), now, Duration.ofSeconds(30));
    com.pension.permission.domain.channel.valueobject.EffectiveIdentity identity =
      com.pension.permission.domain.channel.valueobject.EffectiveIdentity.direct(UserNo.of("approver-1"));

    session.authorize("123456", snapshot, identity, acceptHasher, UserNo.of("teller-1"));

    assertThatThrownBy(() -> session.authorize(
      "123456", snapshot, identity, acceptHasher, UserNo.of("teller-1")))
      .isInstanceOf(com.example.shared.exception.DomainException.class);
  }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `cd d:\WorkSpace\Trae\multiple-module-spring-cloud && mvn test -pl auth-service/auth-domain -Dtest=SecondaryAuthSessionTest -q`

Expected: FAIL（authorize 方法未实现）

- [ ] **Step 3: 实现 authorize / recordFailedAttempt / resendVerificationCode 方法**

在 `SecondaryAuthSession.java` 的查询方法前追加：

```java
/**
 * 柜员输入验证码确认（PENDING → AUTHORIZED）.
 *
 * <p>验证码校验通过后：
 * <ul>
 *   <li>清空 verificationCode 字段（一次性使用）</li>
 *   <li>冻结 permissionSnapshot</li>
 *   <li>设置 effectiveIdentity</li>
 *   <li>状态流转到 AUTHORIZED</li>
 * </ul>
 * </p>
 *
 * @param rawCode 明文验证码
 * @param snapshot 权限快照（应用层预先解析）
 * @param identity 有效身份（应用层预先构造）
 * @param hasher 验证码哈希器
 * @param operator 操作人
 */
public void authorize(
  String rawCode,
  PermissionSnapshot snapshot,
  EffectiveIdentity identity,
  com.pension.permission.domain.channel.spi.VerificationCodeHasher hasher,
  UserNo operator
) {
  Objects.requireNonNull(rawCode, "rawCode");
  Objects.requireNonNull(snapshot, "snapshot");
  Objects.requireNonNull(identity, "identity");
  Objects.requireNonNull(hasher, "hasher");
  Objects.requireNonNull(operator, "operator");
  if (status != SecondaryAuthStatus.PENDING) {
    throw new DomainException(SecondaryAuthErrorCode.SESSION_NOT_PENDING);
  }
  if (verificationCode == null) {
    throw new DomainException(SecondaryAuthErrorCode.VERIFICATION_CODE_EXPIRED);
  }
  if (verificationCode.isExhausted()) {
    this.status = SecondaryAuthStatus.REJECTED;
    registerDomainEvent(SecondaryAuthRejected.of(
      id(), tellerAccountId, approverAccountId, operator));
    markUpdated(operator);
    throw new DomainException(SecondaryAuthErrorCode.VERIFICATION_CODE_EXHAUSTED);
  }
  if (!hasher.matches(rawCode, verificationCode.hashedCode())) {
    this.verificationCode = verificationCode.onAttemptFailed();
    markUpdated(operator);
    throw new DomainException(SecondaryAuthErrorCode.INVALID_VERIFICATION_CODE);
  }
  this.verificationCode = null;
  this.permissionSnapshot = snapshot;
  this.effectiveIdentity = identity;
  this.authorizedAt = LocalDateTime.now();
  this.status = SecondaryAuthStatus.AUTHORIZED;
  registerDomainEvent(SecondaryAuthCompleted.of(
    id(), tellerAccountId, approverAccountId, identity, snapshot, operator));
  markUpdated(operator);
}

/**
 * 记录一次校验失败（PENDING，剩余次数减 1，耗尽则自动 REJECTED）.
 *
 * <p>此方法用于应用层在 authorize 抛异常后显式记录失败（如需独立追踪）。
 * authorize 方法内部已自动调用 onAttemptFailed，因此通常无需手动调用此方法。</p>
 *
 * @param operator 操作人
 */
public void recordFailedAttempt(UserNo operator) {
  Objects.requireNonNull(operator, "operator");
  if (status != SecondaryAuthStatus.PENDING) {
    throw new DomainException(SecondaryAuthErrorCode.SESSION_NOT_PENDING);
  }
  if (verificationCode == null) {
    throw new DomainException(SecondaryAuthErrorCode.VERIFICATION_CODE_EXPIRED);
  }
  this.verificationCode = verificationCode.onAttemptFailed();
  if (this.verificationCode.isExhausted()) {
    this.status = SecondaryAuthStatus.REJECTED;
    registerDomainEvent(SecondaryAuthRejected.of(
      id(), tellerAccountId, approverAccountId, operator));
  }
  markUpdated(operator);
}

/**
 * 重发验证码（PENDING，重置 verificationCode）.
 *
 * @param newCode 新的验证码值对象（应用层已哈希）
 * @param operator 操作人
 */
public void resendVerificationCode(VerificationCode newCode, UserNo operator) {
  Objects.requireNonNull(newCode, "newCode");
  Objects.requireNonNull(operator, "operator");
  if (status != SecondaryAuthStatus.PENDING) {
    throw new DomainException(SecondaryAuthErrorCode.SESSION_NOT_PENDING);
  }
  this.verificationCode = newCode;
  // 重发视为新的发起事件
  registerDomainEvent(SecondaryAuthInitiated.of(
    id(), tellerAccountId, approverAccountId, operator));
  markUpdated(operator);
}
```

需要在文件顶部补充 import：

```java
import com.pension.permission.domain.channel.event.SecondaryAuthCompleted;
import com.pension.permission.domain.channel.event.SecondaryAuthRejected;
```

- [ ] **Step 4: 运行测试验证通过**

Run: `cd d:\WorkSpace\Trae\multiple-module-spring-cloud && mvn test -pl auth-service/auth-domain -Dtest=SecondaryAuthSessionTest -q`

Expected: PASS

- [ ] **Step 5: 提交**

```bash
cd d:\WorkSpace\Trae\multiple-module-spring-cloud
git add auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/aggregate/SecondaryAuthSession.java auth-service/auth-domain/src/test/java/com/pension/permission/domain/channel/aggregate/SecondaryAuthSessionTest.java
git commit -m "feat(auth-domain): 扩展 SecondaryAuthSession authorize/recordFailedAttempt/resendVerificationCode 方法"
```

---

## Task 11: 扩展 SecondaryAuthSession（revoke / close / expireIfTimeout 方法）

**Files:**
- Modify: `auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/aggregate/SecondaryAuthSession.java`
- Test: `auth-service/auth-domain/src/test/java/com/pension/permission/domain/channel/aggregate/SecondaryAuthSessionTest.java`

**Interfaces:**
- Produces: `revoke` / `close` / `expireIfTimeout` 方法

- [ ] **Step 1: 先写 revoke/close/expireIfTimeout 的测试**

在 `SecondaryAuthSessionTest.java` 中追加：

```java
@Nested
@DisplayName("revoke 撤销授权")
class RevokeTest {

  @Test
  @DisplayName("AUTHORIZED 状态撤销后应当流转到 REVOKED")
  void should_be_revoked_when_authorized() {
    LocalDateTime now = LocalDateTime.now();
    SecondaryAuthSession session = SecondaryAuthSession.initiate(
      new SecondaryAuthSessionIdForTest("s-1"),
      UserNo.of("teller-1"), owner(),
      UserNo.of("approver-1"), mobile(), null,
      code(now), Duration.ofMinutes(5), Duration.ofHours(2),
      UserNo.of("teller-1"));

    com.pension.permission.domain.channel.spi.VerificationCodeHasher acceptHasher =
      (raw, hashed) -> true;
    com.pension.permission.domain.authorization.valueobject.Permission p =
      new com.pension.permission.domain.authorization.valueobject.Permission(
        new com.pension.permission.domain.authorization.valueobject.BusinessCode("B"),
        new com.pension.permission.domain.authorization.valueobject.ActionCode("A"));
    com.pension.permission.domain.channel.valueobject.PermissionSnapshot snapshot =
      com.pension.permission.domain.channel.valueobject.PermissionSnapshot.of(
        java.util.Set.of(p), now, Duration.ofSeconds(30));
    com.pension.permission.domain.channel.valueobject.EffectiveIdentity identity =
      com.pension.permission.domain.channel.valueobject.EffectiveIdentity.direct(UserNo.of("approver-1"));
    session.authorize("123456", snapshot, identity, acceptHasher, UserNo.of("teller-1"));

    session.revoke(UserNo.of("approver-1"), "经办人主动撤销");

    assertThat(session.status()).isEqualTo(SecondaryAuthStatus.REVOKED);
    assertThat(session.revokeReason()).isEqualTo("经办人主动撤销");
    assertThat(session.domainEvents())
      .anyMatch(e -> "SecondaryAuthRevoked".equals(e.eventType()));
  }

  @Test
  @DisplayName("非 AUTHORIZED 状态调用 revoke 应当抛异常")
  void should_throw_when_revoke_not_authorized() {
    LocalDateTime now = LocalDateTime.now();
    SecondaryAuthSession session = SecondaryAuthSession.initiate(
      new SecondaryAuthSessionIdForTest("s-1"),
      UserNo.of("teller-1"), owner(),
      UserNo.of("approver-1"), mobile(), null,
      code(now), Duration.ofMinutes(5), Duration.ofHours(2),
      UserNo.of("teller-1"));

    assertThatThrownBy(() -> session.revoke(UserNo.of("approver-1"), "测试"))
      .isInstanceOf(com.example.shared.exception.DomainException.class);
  }
}

@Nested
@DisplayName("close 关闭会话")
class CloseTest {

  @Test
  @DisplayName("AUTHORIZED 状态关闭后应当流转到 CLOSED")
  void should_be_closed_when_authorized() {
    LocalDateTime now = LocalDateTime.now();
    SecondaryAuthSession session = SecondaryAuthSession.initiate(
      new SecondaryAuthSessionIdForTest("s-1"),
      UserNo.of("teller-1"), owner(),
      UserNo.of("approver-1"), mobile(), null,
      code(now), Duration.ofMinutes(5), Duration.ofHours(2),
      UserNo.of("teller-1"));
    com.pension.permission.domain.channel.spi.VerificationCodeHasher acceptHasher =
      (raw, hashed) -> true;
    com.pension.permission.domain.authorization.valueobject.Permission p =
      new com.pension.permission.domain.authorization.valueobject.Permission(
        new com.pension.permission.domain.authorization.valueobject.BusinessCode("B"),
        new com.pension.permission.domain.authorization.valueobject.ActionCode("A"));
    com.pension.permission.domain.channel.valueobject.PermissionSnapshot snapshot =
      com.pension.permission.domain.channel.valueobject.PermissionSnapshot.of(
        java.util.Set.of(p), now, Duration.ofSeconds(30));
    com.pension.permission.domain.channel.valueobject.EffectiveIdentity identity =
      com.pension.permission.domain.channel.valueobject.EffectiveIdentity.direct(UserNo.of("approver-1"));
    session.authorize("123456", snapshot, identity, acceptHasher, UserNo.of("teller-1"));

    session.close(UserNo.of("teller-1"));

    assertThat(session.status()).isEqualTo(SecondaryAuthStatus.CLOSED);
    assertThat(session.domainEvents())
      .anyMatch(e -> "SecondaryAuthClosed".equals(e.eventType()));
  }
}

@Nested
@DisplayName("expireIfTimeout 超时过期")
class ExpireTest {

  @Test
  @DisplayName("PENDING 超时后应当流转到 EXPIRED")
  void should_expire_when_pending_timeout() {
    LocalDateTime now = LocalDateTime.now();
    SecondaryAuthSession session = SecondaryAuthSession.initiate(
      new SecondaryAuthSessionIdForTest("s-1"),
      UserNo.of("teller-1"), owner(),
      UserNo.of("approver-1"), mobile(), null,
      code(now), Duration.ofMinutes(5), Duration.ofHours(2),
      UserNo.of("teller-1"));

    // 模拟 6 分钟后（超过 5 分钟的 pendingTimeout）
    session.expireIfTimeout(now.plusMinutes(6));

    assertThat(session.status()).isEqualTo(SecondaryAuthStatus.EXPIRED);
    assertThat(session.domainEvents())
      .anyMatch(e -> "SecondaryAuthExpired".equals(e.eventType()));
  }

  @Test
  @DisplayName("未超时时不应当流转状态")
  void should_not_expire_when_not_timeout() {
    LocalDateTime now = LocalDateTime.now();
    SecondaryAuthSession session = SecondaryAuthSession.initiate(
      new SecondaryAuthSessionIdForTest("s-1"),
      UserNo.of("teller-1"), owner(),
      UserNo.of("approver-1"), mobile(), null,
      code(now), Duration.ofMinutes(5), Duration.ofHours(2),
      UserNo.of("teller-1"));

    session.expireIfTimeout(now.plusMinutes(4));

    assertThat(session.status()).isEqualTo(SecondaryAuthStatus.PENDING);
  }

  @Test
  @DisplayName("终态状态调用 expireIfTimeout 应当不做任何事")
  void should_no_op_when_terminal() {
    LocalDateTime now = LocalDateTime.now();
    SecondaryAuthSession session = SecondaryAuthSession.initiate(
      new SecondaryAuthSessionIdForTest("s-1"),
      UserNo.of("teller-1"), owner(),
      UserNo.of("approver-1"), mobile(), null,
      code(now), Duration.ofMinutes(5), Duration.ofHours(2),
      UserNo.of("teller-1"));
    com.pension.permission.domain.channel.spi.VerificationCodeHasher acceptHasher =
      (raw, hashed) -> true;
    com.pension.permission.domain.authorization.valueobject.Permission p =
      new com.pension.permission.domain.authorization.valueobject.Permission(
        new com.pension.permission.domain.authorization.valueobject.BusinessCode("B"),
        new com.pension.permission.domain.authorization.valueobject.ActionCode("A"));
    com.pension.permission.domain.channel.valueobject.PermissionSnapshot snapshot =
      com.pension.permission.domain.channel.valueobject.PermissionSnapshot.of(
        java.util.Set.of(p), now, Duration.ofSeconds(30));
    com.pension.permission.domain.channel.valueobject.EffectiveIdentity identity =
      com.pension.permission.domain.channel.valueobject.EffectiveIdentity.direct(UserNo.of("approver-1"));
    session.authorize("123456", snapshot, identity, acceptHasher, UserNo.of("teller-1"));
    session.close(UserNo.of("teller-1"));

    session.expireIfTimeout(now.plusHours(3));

    // 仍然 CLOSED
    assertThat(session.status()).isEqualTo(SecondaryAuthStatus.CLOSED);
  }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `cd d:\WorkSpace\Trae\multiple-module-spring-cloud && mvn test -pl auth-service/auth-domain -Dtest=SecondaryAuthSessionTest -q`

Expected: FAIL（revoke/close/expireIfTimeout 未实现）

- [ ] **Step 3: 实现 revoke/close/expireIfTimeout 方法**

在 `SecondaryAuthSession.java` 的 `resendVerificationCode` 方法后追加：

```java
/**
 * 撤销授权（AUTHORIZED → REVOKED）.
 *
 * <p>经办人主动撤销或紧急收权时调用。</p>
 *
 * @param revoker 撤销人
 * @param reason 撤销原因
 */
public void revoke(UserNo revoker, String reason) {
  Objects.requireNonNull(revoker, "revoker");
  Objects.requireNonNull(reason, "reason");
  if (status != SecondaryAuthStatus.AUTHORIZED) {
    throw new DomainException(SecondaryAuthErrorCode.SESSION_NOT_AUTHORIZED);
  }
  this.status = SecondaryAuthStatus.REVOKED;
  this.revokeReason = reason;
  registerDomainEvent(SecondaryAuthRevoked.of(
    id(), tellerAccountId, approverAccountId, reason, revoker));
  markUpdated(revoker);
}

/**
 * 柜员登出（AUTHORIZED → CLOSED）.
 *
 * @param operator 操作人
 */
public void close(UserNo operator) {
  Objects.requireNonNull(operator, "operator");
  if (status != SecondaryAuthStatus.AUTHORIZED) {
    throw new DomainException(SecondaryAuthErrorCode.SESSION_NOT_AUTHORIZED);
  }
  this.status = SecondaryAuthStatus.CLOSED;
  registerDomainEvent(SecondaryAuthClosed.of(id(), tellerAccountId, operator));
  markUpdated(operator);
}

/**
 * 超时过期（PENDING → EXPIRED / AUTHORIZED → EXPIRED）.
 *
 * <p>仅活跃态（PENDING/AUTHORIZED）会检查超时，终态不做任何事。
 * PENDING 检查 verificationCode.expiresAt，AUTHORIZED 检查 expiresAt 和 snapshot.expiresAt。</p>
 *
 * @param now 当前时间
 */
public void expireIfTimeout(LocalDateTime now) {
  Objects.requireNonNull(now, "now");
  if (status.isTerminal()) {
    return;
  }
  boolean shouldExpire = false;
  if (status == SecondaryAuthStatus.PENDING) {
    if (verificationCode != null && verificationCode.isExpired(now)) {
      shouldExpire = true;
    }
  } else if (status == SecondaryAuthStatus.AUTHORIZED) {
    if (expiresAt.isBefore(now) || (permissionSnapshot != null && permissionSnapshot.isExpired(now))) {
      shouldExpire = true;
    }
  }
  if (!shouldExpire) {
    return;
  }
  this.status = SecondaryAuthStatus.EXPIRED;
  registerDomainEvent(SecondaryAuthExpired.of(id(), tellerAccountId, tellerAccountId));
  markUpdated(tellerAccountId);
}
```

需要在文件顶部补充 import：

```java
import com.pension.permission.domain.channel.event.SecondaryAuthRevoked;
import com.pension.permission.domain.channel.event.SecondaryAuthExpired;
import com.pension.permission.domain.channel.event.SecondaryAuthClosed;
```

- [ ] **Step 4: 运行测试验证通过**

Run: `cd d:\WorkSpace\Trae\multiple-module-spring-cloud && mvn test -pl auth-service/auth-domain -Dtest=SecondaryAuthSessionTest -q`

Expected: PASS

- [ ] **Step 5: 提交**

```bash
cd d:\WorkSpace\Trae\multiple-module-spring-cloud
git add auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/aggregate/SecondaryAuthSession.java auth-service/auth-domain/src/test/java/com/pension/permission/domain/channel/aggregate/SecondaryAuthSessionTest.java
git commit -m "feat(auth-domain): 扩展 SecondaryAuthSession revoke/close/expireIfTimeout 方法"
```

---

## Task 12: 新增 reconstitute 工厂方法

**Files:**
- Modify: `auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/aggregate/SecondaryAuthSession.java`

**Interfaces:**
- Produces: `reconstitute` static 方法（用于 Repository 从数据库重建聚合根）

- [ ] **Step 1: 在 SecondaryAuthSession.java 末尾追加 reconstitute 方法**

在 `initiate` 方法后追加：

```java
/**
 * 从持久化数据重建聚合根.
 *
 * <p>不产生领域事件，仅恢复状态。</p>
 */
public static SecondaryAuthSession reconstitute(
  SecondaryAuthSessionId id,
  UserNo createdBy, UserNo updatedBy,
  LocalDateTime createdAt, LocalDateTime updatedAt, Version version,
  UserNo tellerAccountId, UserNo approverAccountId,
  CredentialOwner credentialOwner, Mobile approverMobile,
  com.example.shared.annuity.PlanNo planId,
  VerificationCode verificationCode,
  EffectiveIdentity effectiveIdentity,
  PermissionSnapshot permissionSnapshot,
  SecondaryAuthStatus status,
  LocalDateTime initiatedAt, LocalDateTime authorizedAt,
  LocalDateTime expiresAt, String revokeReason
) {
  return new SecondaryAuthSession(
    id, createdBy, updatedBy, createdAt, updatedAt, version,
    tellerAccountId, approverAccountId,
    credentialOwner, approverMobile, planId,
    verificationCode, effectiveIdentity, permissionSnapshot,
    status, initiatedAt, authorizedAt, expiresAt, revokeReason);
}
```

- [ ] **Step 2: 编译验证**

Run: `cd d:\WorkSpace\Trae\multiple-module-spring-cloud && mvn compile -pl auth-service/auth-domain -q`

Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
cd d:\WorkSpace\Trae\multiple-module-spring-cloud
git add auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/aggregate/SecondaryAuthSession.java
git commit -m "feat(auth-domain): 新增 SecondaryAuthSession.reconstitute 工厂方法"
```

---

## Task 13: 新建 SecondaryAuthSessionRepository 接口

**Files:**
- Create: `auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/repository/SecondaryAuthSessionRepository.java`

**Interfaces:**
- Consumes: `com.example.shared.domain.repository.Repository`、`SecondaryAuthSession`（Task 9-12 产出）
- Produces: `SecondaryAuthSessionRepository`（继承 `Repository<SecondaryAuthSession, SecondaryAuthSessionId>`，扩展查询方法）

- [ ] **Step 1: 创建 Repository 接口**

```java
package com.pension.permission.domain.channel.repository;

import com.example.shared.domain.repository.Repository;
import com.example.shared.identifier.user.UserNo;
import com.pension.permission.domain.channel.aggregate.SecondaryAuthSession;
import com.pension.permission.types.SecondaryAuthSessionId;

import java.util.List;
import java.util.Optional;

/**
 * 二次授权会话 Repository 接口.
 */
public interface SecondaryAuthSessionRepository
  extends Repository<SecondaryAuthSession, SecondaryAuthSessionId> {

  /**
   * 查询柜员当前活跃的二次授权会话（PENDING 或 AUTHORIZED）.
   *
   * <p>用于校验柜员活跃会话唯一性不变量。</p>
   */
  Optional<SecondaryAuthSession> findActiveByTeller(UserNo tellerAccountId);

  /**
   * 查询经办人所有 AUTHORIZED 状态的会话.
   *
   * <p>用于紧急收权时撤销经办人所有授权。</p>
   */
  List<SecondaryAuthSession> findAuthorizedByApprover(UserNo approverAccountId);

  /**
   * 查询经办人所有 PENDING 状态的会话.
   *
   * <p>用于经办人查询待确认列表（未来扩展）。</p>
   */
  List<SecondaryAuthSession> findPendingByApprover(UserNo approverAccountId);

  /**
   * 查询所有超时的活跃会话.
   *
   * <p>用于定时清理任务。</p>
   */
  List<SecondaryAuthSession> findTimeoutSessions();
}
```

- [ ] **Step 2: 编译验证**

Run: `cd d:\WorkSpace\Trae\multiple-module-spring-cloud && mvn compile -pl auth-service/auth-domain -q`

Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
cd d:\WorkSpace\Trae\multiple-module-spring-cloud
git add auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/repository/SecondaryAuthSessionRepository.java
git commit -m "feat(auth-domain): 新增 SecondaryAuthSessionRepository 接口"
```

---

## Task 14: 新建 SecondaryAuthStrategy SPI 接口

**Files:**
- Create: `auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/service/SecondaryAuthStrategy.java`

**Interfaces:**
- Produces: `SecondaryAuthStrategy`（SPI 接口，`supports`/`initiate`/`authorize` 方法）

- [ ] **Step 1: 创建 SPI 接口**

```java
package com.pension.permission.domain.channel.service;

import com.pension.permission.domain.channel.aggregate.SecondaryAuthSession;
import com.pension.permission.domain.channel.valueobject.EffectiveIdentity;
import com.pension.permission.domain.channel.valueobject.PermissionSnapshot;
import com.pension.permission.domain.channel.valueobject.VerificationCode;

/**
 * 二次授权策略 SPI.
 *
 * <p>不同策略对应不同的授权方式（短信验证码、人脸识别、UKey 签名等）。
 * 默认实现为 SmsCodeSecondaryAuthStrategy（短信验证码）。</p>
 */
public interface SecondaryAuthStrategy {

  /**
   * 策略标识.
   */
  String supports();

  /**
   * 发起授权.
   *
   * <p>本接口方法保留用于未来策略扩展。当前实现统一使用 SecondaryAuthSession.initiate() 静态工厂方法。
   * 策略实现可以在此方法中封装特定于策略的发起逻辑（如生成不同长度的验证码）。</p>
   */
  SecondaryAuthSession initiate(SecondaryAuthContext context);

  /**
   * 完成授权.
   *
   * <p>校验验证码、冻结快照、设置 EffectiveIdentity。
   * 策略实现可以在此方法中封装特定于策略的校验逻辑。</p>
   */
  SecondaryAuthSession authorize(
    SecondaryAuthSession session,
    AuthorizeCommand command);

  /**
   * 发起上下文.
   */
  record SecondaryAuthContext(
    com.pension.permission.types.SecondaryAuthSessionId id,
    com.example.shared.identifier.user.UserNo tellerAccountId,
    com.pension.permission.domain.credential.valueobject.owner.CredentialOwner credentialOwner,
    com.example.shared.identifier.user.UserNo approverAccountId,
    com.example.shared.contactinfo.Mobile approverMobile,
    com.example.shared.annuity.PlanNo planId,
    VerificationCode verificationCode,
    java.time.Duration pendingTimeout,
    java.time.Duration sessionTimeout,
    com.example.shared.identifier.user.UserNo operator
  ) {}

  /**
   * 授权命令.
   */
  record AuthorizeCommand(
    String rawCode,
    PermissionSnapshot snapshot,
    EffectiveIdentity identity,
    com.example.shared.identifier.user.UserNo operator
  ) {}
}
```

- [ ] **Step 2: 编译验证**

Run: `cd d:\WorkSpace\Trae\multiple-module-spring-cloud && mvn compile -pl auth-service/auth-domain -q`

Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
cd d:\WorkSpace\Trae\multiple-module-spring-cloud
git add auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/service/SecondaryAuthStrategy.java
git commit -m "feat(auth-domain): 新增 SecondaryAuthStrategy SPI 接口"
```

---

## Task 15: 改造 Session 聚合根

**Files:**
- Modify: `auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/aggregate/Session.java`

**Interfaces:**
- Consumes: `SecondaryAuthSessionId`（Task 2 产出）
- Produces: `applySecondaryAuth` / `clearSecondaryAuth` 方法 + `secondaryAuthSessionId` 字段

- [ ] **Step 1: 读取现有 Session.java**

Read `auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/aggregate/Session.java` 完整内容。

- [ ] **Step 2: 添加 import 和字段**

在 Session.java 中添加 import：

```java
import com.pension.permission.types.SecondaryAuthSessionId;
```

在 Session 类的字段区域添加（在 `effectiveIdentity` 字段附近）：

```java
private SecondaryAuthSessionId secondaryAuthSessionId;
```

- [ ] **Step 3: 新增 applySecondaryAuth 方法**

在 Session 类的方法区域追加：

```java
/**
 * 应用二次授权结果.
 *
 * <p>监听 SecondaryAuthCompleted 事件后调用，将柜员会话与二次授权会话绑定。
 * 仅网点渠道允许调用此方法。</p>
 *
 * @param sessionId 二次授权会话 ID
 * @param identity 有效身份
 * @param operator 操作人
 */
public void applySecondaryAuth(
  SecondaryAuthSessionId sessionId,
  EffectiveIdentity identity,
  UserNo operator) {
  if (this.channel != AnnuityChannel.BANK_BRANCH) {
    throw new DomainException(com.pension.permission.domain.channel.errorcode.SecondaryAuthErrorCode.CHANNEL_NOT_SUPPORTED);
  }
  if (this.secondaryAuthSessionId != null) {
    throw new DomainException(com.pension.permission.domain.channel.errorcode.SecondaryAuthErrorCode.ACTIVE_SESSION_EXISTS);
  }
  this.secondaryAuthSessionId = sessionId;
  this.effectiveIdentity = identity;
  markUpdated(operator);
  registerDomainEvent(SessionIdentityElevated.of(
    this.id, this.primaryAccountId, null, identity, operator));
}

/**
 * 清除二次授权引用.
 *
 * <p>监听 SecondaryAuthRevoked 事件后调用。不产生独立事件，撤销事件由 SecondaryAuthSession 发起。</p>
 *
 * @param operator 操作人
 */
public void clearSecondaryAuth(UserNo operator) {
  if (this.channel != AnnuityChannel.BANK_BRANCH) {
    return;
  }
  this.secondaryAuthSessionId = null;
  this.effectiveIdentity = EffectiveIdentity.direct(this.primaryAccountId);
  markUpdated(operator);
}

/**
 * 获取当前绑定的二次授权会话 ID.
   */
public SecondaryAuthSessionId secondaryAuthSessionId() {
  return secondaryAuthSessionId;
}
```

- [ ] **Step 4: 编译验证**

Run: `cd d:\WorkSpace\Trae\multiple-module-spring-cloud && mvn compile -pl auth-service/auth-domain -q`

Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
cd d:\WorkSpace\Trae\multiple-module-spring-cloud
git add auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/aggregate/Session.java
git commit -m "refactor(auth-domain): Session 聚合根新增 applySecondaryAuth/clearSecondaryAuth 方法"
```

---

## Task 16: 清理 DefaultSecondaryAuthService 的 unused import

**Files:**
- Modify: `auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/service/DefaultSecondaryAuthService.java`

- [ ] **Step 1: 读取 DefaultSecondaryAuthService.java**

Read 文件内容，确认 `import com.pension.permission.domain.shared.Channel;` 是 unused import 且引用的类不存在。

- [ ] **Step 2: 删除 unused import**

删除该 import 行。

- [ ] **Step 3: 编译验证**

Run: `cd d:\WorkSpace\Trae\multiple-module-spring-cloud && mvn compile -pl auth-service/auth-domain -q`

Expected: BUILD SUCCESS

- [ ] **Step 4: 标记 SecondaryAuthService 接口为 @Deprecated**

在 `SecondaryAuthService.java` 接口上添加 `@Deprecated` 注解和 Javadoc：

```java
/**
 * @deprecated 已被 {@link SecondaryAuthStrategy} 替代，将在后续版本移除。
 *     新代码请使用 SecondaryAuthStrategy SPI。
 */
@Deprecated
public interface SecondaryAuthService {
  EffectiveIdentity elevate(UserNo tellerAccountId, CredentialOwner credentialOwner,
                            String proof, Mobile phoneNumber);
}
```

- [ ] **Step 5: 编译验证**

Run: `cd d:\WorkSpace\Trae\multiple-module-spring-cloud && mvn compile -pl auth-service/auth-domain -q`

Expected: BUILD SUCCESS

- [ ] **Step 6: 提交**

```bash
cd d:\WorkSpace\Trae\multiple-module-spring-cloud
git add auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/service/DefaultSecondaryAuthService.java auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/service/SecondaryAuthService.java
git commit -m "refactor(auth-domain): 清理 DefaultSecondaryAuthService unused import 并标记 SecondaryAuthService 为 Deprecated"
```

---

## Task 17: 新建 Command 对象

**Files:**
- Create: `auth-application/src/main/java/com/pension/permission/application/channel/command/InitiateSecondaryAuthCommand.java`
- Create: `auth-application/src/main/java/com/pension/permission/application/channel/command/ConfirmSecondaryAuthCommand.java`
- Create: `auth-application/src/main/java/com/pension/permission/application/channel/command/ResendCodeCommand.java`
- Create: `auth-application/src/main/java/com/pension/permission/application/channel/command/RevokeSecondaryAuthCommand.java`
- Create: `auth-application/src/main/java/com/pension/permission/application/channel/command/CloseSecondaryAuthCommand.java`

**Interfaces:**
- Consumes: `UserNo`、`PlanNo`、`SecondaryAuthSessionId`、`CredentialOwner`
- Produces: 5 个 Command record

- [ ] **Step 1: 创建 InitiateSecondaryAuthCommand**

```java
package com.pension.permission.application.channel.command;

import com.example.shared.identifier.user.UserNo;
import com.pension.permission.domain.credential.valueobject.owner.CredentialOwner;

/**
 * 发起二次授权命令.
 */
public record InitiateSecondaryAuthCommand(
  UserNo tellerAccountId,
  CredentialOwner credentialOwner,
  UserNo approverAccountId,
  com.example.shared.annuity.PlanNo planId
) {}
```

- [ ] **Step 2: 创建 ConfirmSecondaryAuthCommand**

```java
package com.pension.permission.application.channel.command;

import com.example.shared.identifier.user.UserNo;
import com.pension.permission.types.SecondaryAuthSessionId;

/**
 * 确认二次授权命令.
 */
public record ConfirmSecondaryAuthCommand(
  SecondaryAuthSessionId sessionId,
  String rawCode,
  UserNo operator
) {}
```

- [ ] **Step 3: 创建 ResendCodeCommand**

```java
package com.pension.permission.application.channel.command;

import com.example.shared.identifier.user.UserNo;
import com.pension.permission.types.SecondaryAuthSessionId;

/**
 * 重发验证码命令.
 */
public record ResendCodeCommand(
  SecondaryAuthSessionId sessionId,
  UserNo operator
) {}
```

- [ ] **Step 4: 创建 RevokeSecondaryAuthCommand**

```java
package com.pension.permission.application.channel.command;

import com.example.shared.identifier.user.UserNo;
import com.pension.permission.types.SecondaryAuthSessionId;

/**
 * 撤销二次授权命令.
 */
public record RevokeSecondaryAuthCommand(
  SecondaryAuthSessionId sessionId,
  UserNo operator,
  String reason
) {}
```

- [ ] **Step 5: 创建 CloseSecondaryAuthCommand**

```java
package com.pension.permission.application.channel.command;

import com.example.shared.identifier.user.UserNo;
import com.pension.permission.types.SecondaryAuthSessionId;

/**
 * 关闭二次授权会话命令.
 */
public record CloseSecondaryAuthCommand(
  SecondaryAuthSessionId sessionId,
  UserNo operator
) {}
```

- [ ] **Step 6: 编译验证**

Run: `cd d:\WorkSpace\Trae\multiple-module-spring-cloud && mvn compile -pl auth-service/auth-application -q`

Expected: BUILD SUCCESS

- [ ] **Step 7: 提交**

```bash
cd d:\WorkSpace\Trae\multiple-module-spring-cloud
git add auth-service/auth-application/src/main/java/com/pension/permission/application/channel/command/
git commit -m "feat(auth-application): 新增二次授权 Command 对象"
```

---

## Task 18: 新建 SecondaryAuthConfig 配置类

**Files:**
- Create: `auth-application/src/main/java/com/pension/permission/application/channel/config/SecondaryAuthConfig.java`

**Interfaces:**
- Produces: `SecondaryAuthConfig`（Spring `@ConfigurationProperties`）

- [ ] **Step 1: 创建配置类**

```java
package com.pension.permission.application.channel.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 二次授权配置.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "auth.secondary-auth")
public class SecondaryAuthConfig {

  /**
   * 授权策略标识（sms-code / face-recognition / ukey-signature）.
   */
  private String strategy = "sms-code";

  /**
   * 待授权超时时间（默认 5 分钟）.
   */
  private Duration pendingTimeout = Duration.ofMinutes(5);

  /**
   * 授权后会话过期时间（默认 2 小时）.
   */
  private Duration sessionTimeout = Duration.ofHours(2);

  /**
   * 权限快照 TTL（默认 30 秒）.
   */
  private Duration snapshotTtl = Duration.ofSeconds(30);

  /**
   * 验证码长度（默认 6 位）.
   */
  private int verificationCodeLength = 6;

  /**
   * 验证码最大重试次数（默认 3 次）.
   */
  private int verificationMaxAttempts = 3;

  /**
   * 短信发送开关（测试环境可关闭）.
   */
  private boolean smsEnabled = true;
}
```

- [ ] **Step 2: 编译验证**

Run: `cd d:\WorkSpace\Trae\multiple-module-spring-cloud && mvn compile -pl auth-service/auth-application -q`

Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
cd d:\WorkSpace\Trae\multiple-module-spring-cloud
git add auth-service/auth-application/src/main/java/com/pension/permission/application/channel/config/SecondaryAuthConfig.java
git commit -m "feat(auth-application): 新增 SecondaryAuthConfig 配置类"
```

---

## Task 19: 新建 SecondaryAuthAppService

**Files:**
- Create: `auth-application/src/main/java/com/pension/permission/application/channel/SecondaryAuthAppService.java`

**Interfaces:**
- Consumes: `SecondaryAuthSessionRepository`（Task 13）、`VerificationCodeHasher`（Task 7）、`SecondaryAuthConfig`（Task 18）、`Command`（Task 17）
- Produces: `SecondaryAuthAppService`（应用服务，5 个用例方法）

- [ ] **Step 1: 创建应用服务**

```java
package com.pension.permission.application.channel;

import com.example.shared.contactinfo.Mobile;
import com.example.shared.exception.BusinessException;
import com.example.shared.identifier.user.UserNo;
import com.pension.permission.application.channel.command.CloseSecondaryAuthCommand;
import com.pension.permission.application.channel.command.ConfirmSecondaryAuthCommand;
import com.pension.permission.application.channel.command.InitiateSecondaryAuthCommand;
import com.pension.permission.application.channel.command.ResendCodeCommand;
import com.pension.permission.application.channel.command.RevokeSecondaryAuthCommand;
import com.pension.permission.application.channel.config.SecondaryAuthConfig;
import com.pension.permission.domain.channel.aggregate.SecondaryAuthSession;
import com.pension.permission.domain.channel.errorcode.SecondaryAuthErrorCode;
import com.pension.permission.domain.channel.repository.SecondaryAuthSessionRepository;
import com.pension.permission.domain.channel.spi.VerificationCodeHasher;
import com.pension.permission.domain.channel.valueobject.EffectiveIdentity;
import com.pension.permission.domain.channel.valueobject.PermissionSnapshot;
import com.pension.permission.domain.channel.valueobject.VerificationCode;
import com.pension.permission.types.SecondaryAuthSessionId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 二次授权应用服务.
 *
 * <p>编排二次授权用例：
 * <ol>
 *   <li>柜员发起 → 生成验证码 → 发短信</li>
 *   <li>柜员输入验证码 → 校验 → 冻结快照 → 授权完成</li>
 *   <li>重发验证码</li>
 *   <li>经办人撤销</li>
 *   <li>柜员登出</li>
 * </ol>
 * </p>
 *
 * <p>注意：本类不直接生成权限快照，快照由 PermissionResolver 端口提供（未来 Task）。
 * 当前实现中 confirm 方法的快照参数由调用方传入，应用服务仅负责编排。</p>
 */
@Service
@RequiredArgsConstructor
public class SecondaryAuthAppService {

  private final SecondaryAuthSessionRepository sessionRepository;
  private final VerificationCodeHasher codeHasher;
  private final SecondaryAuthConfig config;

  /**
   * 柜员发起二次授权.
   *
   * @param cmd 发起命令
   * @param approverMobile 经办人手机号（应用层从经办人账号查询）
   * @return 会话 ID
   */
  @Transactional
  public SecondaryAuthSessionId initiate(InitiateSecondaryAuthCommand cmd, Mobile approverMobile) {
    // 校验柜员活跃会话唯一性
    sessionRepository.findActiveByTeller(cmd.tellerId())
      .ifPresent(s -> {
        throw new BusinessException(SecondaryAuthErrorCode.ACTIVE_SESSION_EXISTS);
      });

    // 生成验证码（明文，仅在此方法作用域内）
    String rawCode = generateCode();
    String hashedCode = codeHasher.hash(rawCode);
    VerificationCode code = VerificationCode.of(
      hashedCode, LocalDateTime.now(), config.getPendingTimeout());

    // 创建会话
    SecondaryAuthSessionId id = SecondaryAuthSessionId.generate();
    SecondaryAuthSession session = SecondaryAuthSession.initiate(
      id, cmd.tellerId(), cmd.credentialOwner(),
      cmd.approverAccountId(), approverMobile, cmd.planId(),
      code, config.getPendingTimeout(), config.getSessionTimeout(),
      cmd.tellerId());

    sessionRepository.save(session);
    return id;
  }

  /**
   * 柜员输入验证码确认.
   *
   * @param cmd 确认命令
   * @param snapshot 权限快照（由 PermissionResolver 预先解析）
   */
  @Transactional
  public void confirm(ConfirmSecondaryAuthCommand cmd, PermissionSnapshot snapshot) {
    SecondaryAuthSession session = sessionRepository.loadOrThrow(cmd.sessionId());

    if (session.status().isTerminal()) {
      throw new BusinessException(SecondaryAuthErrorCode.SESSION_EXPIRED);
    }

    EffectiveIdentity identity = new EffectiveIdentity(
      session.approverAccountId(),
      cmd.operator(),
      true);

    session.authorize(cmd.rawCode(), snapshot, identity, codeHasher, cmd.operator());
    sessionRepository.save(session);
  }

  /**
   * 重发验证码.
   */
  @Transactional
  public void resendCode(ResendCodeCommand cmd) {
    SecondaryAuthSession session = sessionRepository.loadOrThrow(cmd.sessionId());
    String rawCode = generateCode();
    String hashedCode = codeHasher.hash(rawCode);
    VerificationCode newCode = VerificationCode.of(
      hashedCode, LocalDateTime.now(), config.getPendingTimeout());
    session.resendVerificationCode(newCode, cmd.operator());
    sessionRepository.save(session);
  }

  /**
   * 撤销二次授权.
   */
  @Transactional
  public void revoke(RevokeSecondaryAuthCommand cmd) {
    SecondaryAuthSession session = sessionRepository.loadOrThrow(cmd.sessionId());
    session.revoke(cmd.operator(), cmd.reason());
    sessionRepository.save(session);
  }

  /**
   * 关闭二次授权会话（柜员登出）.
   */
  @Transactional
  public void close(CloseSecondaryAuthCommand cmd) {
    SecondaryAuthSession session = sessionRepository.loadOrThrow(cmd.sessionId());
    session.close(cmd.operator());
    sessionRepository.save(session);
  }

  /**
   * 紧急收权：撤销经办人所有 AUTHORIZED 会话.
   *
   * @param approverAccountId 经办人账号
   */
  @Transactional
  public void revokeAllByApprover(UserNo approverAccountId) {
    sessionRepository.findAuthorizedByApprover(approverAccountId)
      .forEach(session -> {
        session.revoke(approverAccountId, "账号冻结紧急收权");
        sessionRepository.save(session);
      });
  }

  private String generateCode() {
    int length = config.getVerificationCodeLength();
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append((int) (Math.random() * 10));
    }
    return sb.toString();
  }
}
```

- [ ] **Step 2: 编译验证**

Run: `cd d:\WorkSpace\Trae\multiple-module-spring-cloud && mvn compile -pl auth-service/auth-application -q`

Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
cd d:\WorkSpace\Trae\multiple-module-spring-cloud
git add auth-service/auth-application/src/main/java/com/pension/permission/application/channel/SecondaryAuthAppService.java
git commit -m "feat(auth-application): 新增 SecondaryAuthAppService 应用服务"
```

---

## Task 20: SessionApplicationService 新增事件监听

**Files:**
- Modify: `auth-application/src/main/java/com/pension/permission/application/channel/SessionApplicationService.java`

**Interfaces:**
- Consumes: `SecondaryAuthCompleted`、`SecondaryAuthRevoked` 事件
- Produces: 两个 `@TransactionalEventListener` 方法

- [ ] **Step 1: 读取现有 SessionApplicationService.java**

Read 文件内容，确认现有结构、字段和依赖注入方式。

- [ ] **Step 2: 在 SessionApplicationService 中添加事件监听方法**

在类中追加（注意 import）：

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onSecondaryAuthCompleted(com.pension.permission.domain.channel.event.SecondaryAuthCompleted event) {
  Session session = sessionRepository.findByPrimaryAccountId(event.tellerAccountId())
    .orElseThrow(() -> new BusinessException(com.pension.permission.domain.channel.errorcode.SecondaryAuthErrorCode.SESSION_NOT_FOUND));
  session.applySecondaryAuth(event.sessionId(), event.effectiveIdentity(), event.tellerAccountId());
  sessionRepository.save(session);
}

@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onSecondaryAuthRevoked(com.pension.permission.domain.channel.event.SecondaryAuthRevoked event) {
  sessionRepository.findByPrimaryAccountId(event.tellerAccountId())
    .ifPresent(session -> {
      session.clearSecondaryAuth(event.createdBy());
      sessionRepository.save(session);
    });
}
```

如果 `SessionRepository` 没有 `findByPrimaryAccountId` 方法，需要先在 `SessionRepository` 接口中添加：

```java
Optional<Session> findByPrimaryAccountId(UserNo primaryAccountId);
```

- [ ] **Step 3: 编译验证**

Run: `cd d:\WorkSpace\Trae\multiple-module-spring-cloud && mvn compile -pl auth-service/auth-application -q`

Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
cd d:\WorkSpace\Trae\multiple-module-spring-cloud
git add auth-service/auth-application/src/main/java/com/pension/permission/application/channel/SessionApplicationService.java auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/repository/SessionRepository.java
git commit -m "feat(auth-application): SessionApplicationService 新增二次授权事件监听"
```

---

## Task 21: 数据库 DDL（PostgreSQL）

**Files:**
- Create: `auth-service/auth-infrastructure/src/main/resources/schema-pg.sql`（增量追加，若文件已存在则追加内容）

**Interfaces:**
- Produces: `t_auth_secondary_auth_session` 表 + `t_auth_session` 字段增量

- [ ] **Step 1: 检查 schema-pg.sql 是否存在**

Run: `dir auth-service\auth-infrastructure\src\main\resources\schema-pg.sql`

如果文件不存在，创建新文件；如果存在，在文件末尾追加。

- [ ] **Step 2: 追加 PostgreSQL DDL**

```sql
-- ========== SecondaryAuthSession 表 ==========

CREATE TABLE t_auth_secondary_auth_session (
    id                          VARCHAR(32)  NOT NULL,
    teller_account_id           VARCHAR(32)  NOT NULL,
    approver_account_id         VARCHAR(32),
    credential_owner_type       VARCHAR(32)  NOT NULL,
    credential_owner_id         VARCHAR(64)  NOT NULL,
    approver_mobile             VARCHAR(20)  NOT NULL,
    plan_id                     VARCHAR(32),
    verification_code_hash      VARCHAR(255),
    verification_sent_at        TIMESTAMP,
    verification_expires_at     TIMESTAMP,
    verification_remaining      INT,
    effective_identity_id       VARCHAR(32),
    effective_identity_acting   VARCHAR(32),
    effective_via_secondary     BOOLEAN      NOT NULL DEFAULT FALSE,
    snapshot_permissions        JSONB,
    snapshot_frozen_at          TIMESTAMP,
    snapshot_expires_at         TIMESTAMP,
    status                      VARCHAR(16)  NOT NULL,
    initiated_at                TIMESTAMP    NOT NULL,
    authorized_at               TIMESTAMP,
    expires_at                  TIMESTAMP    NOT NULL,
    revoke_reason               VARCHAR(255),
    created_by                  VARCHAR(64)  NOT NULL,
    create_time                 TIMESTAMP    NOT NULL,
    updated_by                  VARCHAR(64)  NOT NULL,
    update_time                 TIMESTAMP    NOT NULL,
    deleted                     BOOLEAN      NOT NULL DEFAULT FALSE,
    version                     INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

COMMENT ON TABLE t_auth_secondary_auth_session IS '二次授权会话表';
COMMENT ON COLUMN t_auth_secondary_auth_session.verification_code_hash IS 'BCrypt 哈希后的验证码，不存明文';
COMMENT ON COLUMN t_auth_secondary_auth_session.snapshot_permissions IS '权限快照 JSON';

CREATE UNIQUE INDEX uk_auth_secondary_auth_teller_active
    ON t_auth_secondary_auth_session (teller_account_id)
    WHERE deleted = FALSE AND status IN ('PENDING', 'AUTHORIZED');

CREATE INDEX idx_auth_secondary_auth_approver_pending
    ON t_auth_secondary_auth_session (approver_account_id, status)
    WHERE deleted = FALSE AND status = 'PENDING';

CREATE INDEX idx_auth_secondary_auth_approver_authorized
    ON t_auth_secondary_auth_session (approver_account_id, status)
    WHERE deleted = FALSE AND status = 'AUTHORIZED';

CREATE INDEX idx_auth_secondary_auth_expires
    ON t_auth_secondary_auth_session (expires_at, status)
    WHERE deleted = FALSE AND status IN ('PENDING', 'AUTHORIZED');

CREATE INDEX idx_auth_secondary_auth_plan
    ON t_auth_secondary_auth_session (plan_id, status)
    WHERE deleted = FALSE;

-- ========== Session 表增量字段 ==========

ALTER TABLE t_auth_session
ADD COLUMN IF NOT EXISTS secondary_auth_session_id VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_auth_session_secondary_auth
    ON t_auth_session (secondary_auth_session_id)
    WHERE deleted = FALSE AND secondary_auth_session_id IS NOT NULL;
```

- [ ] **Step 3: 提交**

```bash
cd d:\WorkSpace\Trae\multiple-module-spring-cloud
git add auth-service/auth-infrastructure/src/main/resources/schema-pg.sql
git commit -m "build(auth-infrastructure): 新增 t_auth_secondary_auth_session 表 PostgreSQL DDL"
```

---

## Task 22: 数据库 DDL（MySQL）

**Files:**
- Create: `auth-service/auth-infrastructure/src/main/resources/schema-mysql.sql`（增量追加，若文件已存在则追加内容）

- [ ] **Step 1: 检查 schema-mysql.sql 是否存在**

Run: `dir auth-service\auth-infrastructure\src\main\resources\schema-mysql.sql`

- [ ] **Step 2: 追加 MySQL DDL**

```sql
-- ========== SecondaryAuthSession 表 ==========

CREATE TABLE t_auth_secondary_auth_session (
    id                          VARCHAR(32)  NOT NULL                  COMMENT '二次授权会话ID',
    teller_account_id           VARCHAR(32)  NOT NULL                  COMMENT '柜员账号ID',
    approver_account_id         VARCHAR(32)                            COMMENT '经办人账号ID',
    credential_owner_type       VARCHAR(32)  NOT NULL                  COMMENT '凭证持有者类型',
    credential_owner_id         VARCHAR(64)  NOT NULL                  COMMENT '凭证持有者ID',
    approver_mobile             VARCHAR(20)  NOT NULL                  COMMENT '经办人手机号',
    plan_id                     VARCHAR(32)                            COMMENT '目标计划ID',
    verification_code_hash      VARCHAR(255)                           COMMENT 'BCrypt哈希验证码',
    verification_sent_at        DATETIME                               COMMENT '验证码发送时间',
    verification_expires_at     DATETIME                               COMMENT '验证码过期时间',
    verification_remaining      INT                                    COMMENT '验证码剩余次数',
    effective_identity_id       VARCHAR(32)                            COMMENT '有效身份-经办ID',
    effective_identity_acting   VARCHAR(32)                            COMMENT '有效身份-柜员ID',
    effective_via_secondary     TINYINT(1)   NOT NULL DEFAULT 0        COMMENT '是否经二次授权',
    snapshot_permissions        JSON                                    COMMENT '权限快照JSON',
    snapshot_frozen_at          DATETIME                               COMMENT '快照冻结时间',
    snapshot_expires_at         DATETIME                               COMMENT '快照TTL过期时间',
    status                      VARCHAR(16)  NOT NULL                  COMMENT '状态',
    initiated_at                DATETIME     NOT NULL                  COMMENT '发起时间',
    authorized_at               DATETIME                               COMMENT '授权时间',
    expires_at                  DATETIME     NOT NULL                  COMMENT '会话过期时间',
    revoke_reason               VARCHAR(255)                           COMMENT '撤销原因',
    created_by                  VARCHAR(64)  NOT NULL,
    create_time                 DATETIME     NOT NULL,
    updated_by                  VARCHAR(64)  NOT NULL,
    update_time                 DATETIME     NOT NULL,
    deleted                     TINYINT(1)   NOT NULL DEFAULT 0,
    version                     INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_teller_account (teller_account_id, status, deleted),
    KEY idx_approver_pending (approver_account_id, status, deleted),
    KEY idx_approver_authorized (approver_account_id, status, deleted),
    KEY idx_expires (expires_at, status, deleted),
    KEY idx_plan (plan_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='二次授权会话表';

-- ========== Session 表增量字段 ==========

ALTER TABLE t_auth_session
ADD COLUMN secondary_auth_session_id VARCHAR(32) COMMENT '二次授权会话ID';

CREATE INDEX idx_auth_session_secondary_auth
    ON t_auth_session (secondary_auth_session_id, deleted);
```

- [ ] **Step 3: 提交**

```bash
cd d:\WorkSpace\Trae\multiple-module-spring-cloud
git add auth-service/auth-infrastructure/src/main/resources/schema-mysql.sql
git commit -m "build(auth-infrastructure): 新增 t_auth_secondary_auth_session 表 MySQL DDL"
```

---

## Task 23: 添加 application.yml 配置项

**Files:**
- Create or Modify: `auth-service/auth-infrastructure/src/main/resources/application.yml`（或 `application-local.yml`）

- [ ] **Step 1: 检查配置文件是否存在**

Run: `dir auth-service\auth-infrastructure\src\main\resources\application*.yml`

- [ ] **Step 2: 添加配置项**

在 application.yml 中追加（如果文件不存在则创建）：

```yaml
auth:
  secondary-auth:
    # 授权策略（sms-code / face-recognition / ukey-signature）
    strategy: sms-code

    # 待授权超时时间（默认 5 分钟）
    pending-timeout: 5m

    # 授权后会话过期时间（默认 2 小时）
    session-timeout: 2h

    # 权限快照 TTL（默认 30 秒）
    snapshot-ttl: 30s

    # 验证码长度（默认 6 位）
    verification-code-length: 6

    # 验证码最大重试次数（默认 3 次）
    verification-max-attempts: 3

    # 短信发送开关（测试环境可关闭）
    sms-enabled: true
```

- [ ] **Step 3: 提交**

```bash
cd d:\WorkSpace\Trae\multiple-module-spring-cloud
git add auth-service/auth-infrastructure/src/main/resources/application.yml
git commit -m "config(auth-infrastructure): 添加 auth.secondary-auth 配置项"
```

---

## Task 24: 最终编译与全量测试验证

**Files:**
- None（验证任务）

- [ ] **Step 1: 全量编译**

Run: `cd d:\WorkSpace\Trae\multiple-module-spring-cloud && mvn clean compile -pl auth-service/auth-types,auth-service/auth-domain,auth-service/auth-application -q`

Expected: BUILD SUCCESS

- [ ] **Step 2: 运行所有测试**

Run: `cd d:\WorkSpace\Trae\multiple-module-spring-cloud && mvn test -pl auth-service/auth-domain -q`

Expected: BUILD SUCCESS，所有测试通过

- [ ] **Step 3: 修复任何编译错误或测试失败**

如果有错误，逐个修复。

- [ ] **Step 4: 提交（如果有修复）**

```bash
cd d:\WorkSpace\Trae\multiple-module-spring-cloud
git add -A
git commit -m "fix(auth-service): 修复编译错误和测试失败"
```

---

## 自审清单

### Spec 覆盖检查

| 设计文档章节 | 覆盖 Task | 状态 |
|------------|----------|------|
| 第二节 多渠道 Session 拆分评估 | 不涉及代码变更 | ✅ |
| 第三节 短信验证码授权流程 | Task 9-11（聚合根）、Task 19（应用服务） | ✅ |
| 第四节 SecondaryAuthSession 聚合根设计 | Task 9-12 | ✅ |
| 4.1 字段设计 | Task 9 | ✅ |
| 4.2 PermissionSnapshot 值对象 | Task 6 | ✅ |
| 4.3 状态机 | Task 3 + Task 9-11 | ✅ |
| 4.4 状态流转规则 | Task 9-11 | ✅ |
| 4.5 关键不变量 | Task 9 validateInvariants | ✅ |
| 4.6 行为方法 | Task 9-11 | ✅ |
| 4.7 领域事件 | Task 8 | ✅ |
| 第五节 Session 聚合根改造 | Task 15 | ✅ |
| 第六节 权限判定策略 | 应用层不实现（留待 infrastructure 层） | ⚠️ 推迟 |
| 第七节 SPI 扩展点 | Task 14 | ✅ |
| 第八节 数据库表结构 | Task 21-22 | ✅ |
| 第九节 应用层编排 | Task 19-20 | ✅ |
| 第十节 配置项 | Task 18, 23 | ✅ |
| 第十一节 错误码 | Task 4 | ✅ |
| 第十二节 实施影响清单 | Task 1-23 全覆盖 | ✅ |
| 第十三节 与现有安全设计的协同 | Task 19（revokeAllByApprover） | ✅ |
| 第十四节 测试策略 | Task 5, 6, 9-11 | ✅ |

### 推迟事项

1. **第六节权限判定策略**：需要在业务服务侧实现 `CachingPermissionClient`，涉及 permission-sdk 模块改造，超出本次实施范围，留待后续。
2. **infrastructure 层实现**：`SecondaryAuthSessionRepositoryImpl`、`SecondaryAuthSessionDO`、`SecondaryAuthSessionMapper`、`SecondaryAuthSessionConverter`、`BCryptVerificationCodeHasher` 需要补充 auth-infrastructure 的依赖（MyBatis-Flex、Spring Security BCrypt），超出本次实施范围，留待后续。
3. **PermissionResolver 端口**：设计文档 9.1 节提到 `PermissionResolver` 用于解析权限快照，但当前 auth-application 没有此接口。Task 19 的 `confirm` 方法接受外部传入的 `PermissionSnapshot`，留待后续在应用层补充 `PermissionResolver` 端口和实现。

### 类型一致性检查

- `SecondaryAuthSessionId`：Task 2 定义为 record，Task 9-12 使用一致 ✅
- `VerificationCode`：Task 5 定义字段 `hashedCode`/`sentAt`/`expiresAt`/`remainingAttempts`，Task 9 使用一致 ✅
- `PermissionSnapshot`：Task 6 定义字段 `permissions`/`frozenAt`/`expiresAt`，Task 9-11 使用一致 ✅
- `SecondaryAuthStatus`：Task 3 定义 6 个枚举值，Task 9-11 使用一致 ✅
- `SecondaryAuthErrorCode`：Task 4 定义 14 个错误码，Task 9-11 使用一致 ✅
- `authorize` 方法签名：Task 10 定义为 `(String rawCode, PermissionSnapshot snapshot, EffectiveIdentity identity, VerificationCodeHasher hasher, UserNo operator)`，Task 19 调用一致 ✅
- `initiate` 方法签名：Task 9 定义为 10 个参数，Task 19 调用一致 ✅

---

## 执行选择

Plan complete and saved to `docs/superpowers/plans/2026-08-05-secondary-auth-session-implementation.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
