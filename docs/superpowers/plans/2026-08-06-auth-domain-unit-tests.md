# auth-domain 单元测试补充实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 auth-domain 7 个子域核心类（约 25 个测试类）补充单元测试，覆盖聚合根状态机、领域服务判定逻辑、值对象校验。

**Architecture:** 按子域分批推进（authorization→assignment→channel→credential/role/user）。测试沿用项目现有风格：JUnit 5 + AssertJ + Mockito + `@Nested` 分组 + 中文 `@DisplayName` + given/when/then 结构。按子域创建 Fixture 类集中管理测试数据。

**Tech Stack:** JUnit 5（junit-jupiter）、AssertJ、Mockito（mockito-core）、Lombok。

## Global Constraints

- 测试包路径：`com.pension.permission.domain.*`（test 源集，镜像 main 包结构）
- Fixture 路径：`com.pension.permission.domain.fixture.*`（统一放在 fixture 子包）
- ID 类型为 record，直接构造：`new GrantId("g-1")`、`new RoleCode("ROLE_AGENT")`、`UserNo.of("u-1")`、`PlanNo.of("p-1")`
- 聚合根通过 `create()` / `reconstitute()` 工厂构造，构造函数均 private
- 错误码断言：`assertThatThrownBy(...).isInstanceOf(DomainException.class).extracting(e -> ((DomainException) e).errorDefinition().code()).isEqualTo("SERVICE.AUTH.xxxx")`
- 状态前置校验抛 `IllegalStateException`；参数校验抛 `IllegalArgumentException` / `NullPointerException`
- Mock SPI 依赖使用 `Mockito.mock()` 静态方法，不引入 `mockito-junit-jupiter`
- 不重构被测代码；发现设计问题记录为技术债注释
- 每个批次独立提交，提交信息：`test(auth-domain): 补充 xxx 域单元测试`

---

## 第一批：authorization 域

### Task 1: 补充 Mockito 依赖

**Files:**
- Modify: `auth-service/auth-domain/pom.xml`

**Interfaces:**
- Produces: auth-domain 可使用 `org.mockito.Mockito.mock()` 等 API

- [ ] **Step 1: 在 pom.xml 的 `<dependencies>` 末尾（assertj-core 之后）添加 Mockito 依赖**

```xml
<dependency>
  <groupId>org.mockito</groupId>
  <artifactId>mockito-core</artifactId>
  <scope>test</scope>
</dependency>
```

- [ ] **Step 2: 验证依赖解析**

Run: `mvn -f auth-service/auth-domain/pom.xml dependency:resolve -DincludeScope=test --no-transfer-progress -q`
Expected: BUILD SUCCESS，无 "Could not resolve" 警告

- [ ] **Step 3: Commit**

```bash
git add auth-service/auth-domain/pom.xml
git commit -m "build(auth-domain): 引入 mockito-core 测试依赖"
```

---

### Task 2: 创建 AuthorizationFixtures

**Files:**
- Create: `auth-service/auth-domain/src/test/java/com/pension/permission/domain/fixture/AuthorizationFixtures.java`

**Interfaces:**
- Produces: `AuthorizationFixtures` 静态工厂方法，供 authorization 域所有测试类复用

- [ ] **Step 1: 创建 AuthorizationFixtures**

```java
package com.pension.permission.domain.fixture;

import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.aggregate.Grant;
import com.pension.permission.domain.authorization.enumeration.Effect;
import com.pension.permission.domain.authorization.enumeration.GrantOrigin;
import com.pension.permission.domain.authorization.enumeration.GrantStatus;
import com.pension.permission.domain.authorization.enumeration.GrantType;
import com.pension.permission.domain.authorization.repository.GrantRepository;
import com.pension.permission.domain.authorization.spi.PlanMembershipLookup;
import com.pension.permission.domain.authorization.valueobject.ActionCode;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import com.pension.permission.domain.authorization.valueobject.Permission;
import com.pension.permission.domain.authorization.valueobject.ScopeRule;
import com.pension.permission.domain.authorization.valueobject.subject.CapabilitySubject;
import com.pension.permission.domain.authorization.valueobject.subject.GrantSubject;
import com.pension.permission.domain.authorization.valueobject.subject.PlanAllMembersSubject;
import com.pension.permission.domain.authorization.valueobject.subject.PlanRoleSubject;
import com.pension.permission.domain.authorization.valueobject.subject.UserListSubject;
import com.pension.permission.domain.authorization.enumeration.ScopeDimension;
import com.pension.permission.domain.product.CustomerSnapshot;
import com.pension.permission.domain.product.PlanSnapshot;
import com.pension.permission.domain.product.ProductGateway;
import com.pension.permission.domain.product.ProductSnapshot;
import com.pension.permission.types.GrantId;
import com.pension.permission.types.RoleCode;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.mock;

/**
 * authorization 域测试数据工厂。
 * 所有方法返回完整可测试对象，调用方可按需修改字段。
 */
public final class AuthorizationFixtures {

  private AuthorizationFixtures() {}

  // ===== 基础值对象 =====

  public static BusinessCode businessCode(String value) {
    return new BusinessCode(value);
  }

  public static ActionCode actionCode(String value) {
    return new ActionCode(value);
  }

  public static Permission permission(String business, String action) {
    return new Permission(businessCode(business), actionCode(action));
  }

  public static Permission wholeBusiness(String business) {
    return Permission.wholeBusiness(businessCode(business));
  }

  public static ScopeRule planScopeRule(String planValue) {
    return ScopeRule.of(ScopeDimension.PLAN, planValue);
  }

  public static ScopeRule productScopeRule(String productValue) {
    return ScopeRule.of(ScopeDimension.PRODUCT, productValue);
  }

  // ===== Subject 系列 =====

  public static CapabilitySubject capabilitySubject() {
    return new CapabilitySubject();
  }

  public static PlanAllMembersSubject planAllMembersSubject(String planNo) {
    return new PlanAllMembersSubject(PlanNo.of(planNo));
  }

  public static PlanRoleSubject planRoleSubject(String planNo, String roleCode) {
    return new PlanRoleSubject(PlanNo.of(planNo), new RoleCode(roleCode));
  }

  public static UserListSubject userListSubject(String... userNos) {
    Set<UserNo> users = java.util.Arrays.stream(userNos).map(UserNo::of).collect(java.util.stream.Collectors.toSet());
    return new UserListSubject(users);
  }

  // ===== Grant 聚合根 =====

  /**
   * 创建一个 PENDING_APPROVAL 状态的 ALLOW 授权（能力层，PLAN 维度）。
   */
  public static Grant pendingAllowGrant() {
    return Grant.create(
      new GrantId("g-pending-1"),
      UserNo.of("creator-1"),
      capabilitySubject(),
      List.of(planScopeRule("PLAN-001")),
      Set.of(permission("BIZ-001", "ACT-VIEW")),
      GrantType.CAPABILITY,
      GrantOrigin.DIRECT,
      Effect.ALLOW,
      GrantStatus.PENDING_APPROVAL,
      java.time.Period.ofDays(30),
      PlanNo.of("PLAN-001"),
      PlanNo.of("PLAN-001"));
  }

  /**
   * 创建一个 EFFECTIVE 状态的 ALLOW 授权。
   */
  public static Grant effectiveAllowGrant() {
    return Grant.create(
      new GrantId("g-allow-1"),
      UserNo.of("creator-1"),
      capabilitySubject(),
      List.of(planScopeRule("PLAN-001")),
      Set.of(permission("BIZ-001", "ACT-VIEW")),
      GrantType.CAPABILITY,
      GrantOrigin.DIRECT,
      Effect.ALLOW,
      GrantStatus.EFFECTIVE,
      java.time.Period.ofDays(30),
      PlanNo.of("PLAN-001"),
      PlanNo.of("PLAN-001"));
  }

  /**
   * 创建一个 EFFECTIVE 状态的 DENY 授权。
   */
  public static Grant effectiveDenyGrant() {
    return Grant.create(
      new GrantId("g-deny-1"),
      UserNo.of("creator-1"),
      capabilitySubject(),
      List.of(planScopeRule("PLAN-001")),
      Set.of(permission("BIZ-001", "ACT-VIEW")),
      GrantType.CAPABILITY,
      GrantOrigin.DIRECT,
      Effect.DENY,
      GrantStatus.EFFECTIVE,
      java.time.Period.ofDays(30),
      PlanNo.of("PLAN-001"),
      PlanNo.of("PLAN-001"));
  }

  // ===== 快照 =====

  public static PlanSnapshot planSnapshot(String planNo) {
    return new PlanSnapshot(
      PlanNo.of(planNo),
      com.example.shared.identifier.id.ProductNo.of("PROD-001"),
      com.example.shared.identifier.id.CustomerNo.of("CUST-001"),
      Optional.empty(),
      "测试计划",
      Instant.now());
  }

  // ===== Mock 工厂 =====

  public static GrantRepository mockGrantRepository() {
    return mock(GrantRepository.class);
  }

  public static ProductGateway mockProductGateway() {
    return mock(ProductGateway.class);
  }

  public static PlanMembershipLookup mockMembershipLookup() {
    return mock(PlanMembershipLookup.class);
  }
}
```

> **注意**：上述代码中 `ValidityPeriod` 的构造参数类型需在实现时根据实际 `ValidityPeriod` 类的 API 调整（可能为 `ValidityPeriod.of(period)` 或类似工厂）。若 `Grant.create` 的 `validityPeriod` 参数类型为 `ValidityPeriod` 而非 `Period`，实现者需先读取 `com.example.shared.domain.valueobject.ValidityPeriod` 确认工厂方法签名后调整此处。同理 `ScopeDimension` 的全限定名需确认是否在 `authorization.enumeration` 包下。

- [ ] **Step 2: 验证编译（不要求通过，仅确认 Fixture 类无语法错误）**

Run: `mvn -f auth-service/auth-domain/pom.xml test-compile -q --no-transfer-progress`
Expected: 编译成功（若报错，根据实际 API 签名修正 Fixture）

- [ ] **Step 3: Commit**

```bash
git add auth-service/auth-domain/src/test/java/com/pension/permission/domain/fixture/AuthorizationFixtures.java
git commit -m "test(auth-domain): 新增 AuthorizationFixtures 测试数据工厂"
```

---

### Task 3: EffectResolverTest

**Files:**
- Create: `auth-service/auth-domain/src/test/java/com/pension/permission/domain/authorization/service/EffectResolverTest.java`

**Interfaces:**
- Consumes: `EffectResolver`（无依赖）、`AuthorizationFixtures`
- Produces: EffectResolver DENY 优先逻辑验证通过

- [ ] **Step 1: 编写 EffectResolverTest**

```java
package com.pension.permission.domain.authorization.service;

import com.pension.permission.domain.authorization.aggregate.Grant;
import com.pension.permission.domain.fixture.AuthorizationFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EffectResolver 效果解析测试")
class EffectResolverTest {

  private final EffectResolver resolver = new EffectResolver();

  @Nested
  @DisplayName("resolve 方法")
  class ResolveTest {

    @Test
    @DisplayName("空授权列表应返回 false")
    void shouldReturnFalseWhenEmpty() {
      assertThat(resolver.resolve(List.of())).isFalse();
    }

    @Test
    @DisplayName("仅含 ALLOW 授权应返回 true")
    void shouldReturnTrueWhenOnlyAllow() {
      var grant = AuthorizationFixtures.effectiveAllowGrant();
      assertThat(resolver.resolve(List.of(grant))).isTrue();
    }

    @Test
    @DisplayName("含 DENY 授权时应返回 false（DENY 优先）")
    void shouldReturnFalseWhenDenyPresent() {
      var allow = AuthorizationFixtures.effectiveAllowGrant();
      var deny = AuthorizationFixtures.effectiveDenyGrant();
      assertThat(resolver.resolve(List.of(allow, deny))).isFalse();
    }

    @Test
    @DisplayName("仅含 DENY 授权应返回 false")
    void shouldReturnFalseWhenOnlyDeny() {
      var deny = AuthorizationFixtures.effectiveDenyGrant();
      assertThat(resolver.resolve(List.of(deny))).isFalse();
    }
  }
}
```

- [ ] **Step 2: 运行测试验证通过**

Run: `mvn -f auth-service/auth-domain/pom.xml test -Dtest=EffectResolverTest -q --no-transfer-progress`
Expected: Tests run: 4, Failures: 0, Errors: 0

- [ ] **Step 3: Commit**

```bash
git add auth-service/auth-domain/src/test/java/com/pension/permission/domain/authorization/service/EffectResolverTest.java
git commit -m "test(auth-domain): 补充 EffectResolver DENY 优先逻辑测试"
```

---

### Task 4: ScopeMatcherTest

**Files:**
- Create: `auth-service/auth-domain/src/test/java/com/pension/permission/domain/authorization/service/ScopeMatcherTest.java`

**Interfaces:**
- Consumes: `ScopeMatcher`（依赖 `ProductGateway`）、`AuthorizationFixtures`
- Produces: ScopeMatcher 范围匹配逻辑验证通过

- [ ] **Step 1: 编写 ScopeMatcherTest**

```java
package com.pension.permission.domain.authorization.service;

import com.pension.permission.domain.authorization.valueobject.ScopeRule;
import com.pension.permission.domain.authorization.enumeration.ScopeDimension;
import com.pension.permission.domain.fixture.AuthorizationFixtures;
import com.pension.permission.domain.product.ProductGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("ScopeMatcher 范围匹配测试")
class ScopeMatcherTest {

  private final ProductGateway gateway = AuthorizationFixtures.mockProductGateway();
  private final ScopeMatcher matcher = new ScopeMatcher(gateway);

  @Nested
  @DisplayName("matches 方法")
  class MatchesTest {

    @Test
    @DisplayName("PLAN 维度规则匹配相同计划应返回 true")
    void shouldMatchWhenPlanEquals() {
      var rule = ScopeRule.of(ScopeDimension.PLAN, "PLAN-001");
      var plan = AuthorizationFixtures.planSnapshot("PLAN-001");

      assertThat(matcher.matches(List.of(rule), plan)).isTrue();
    }

    @Test
    @DisplayName("PLAN 维度规则不匹配不同计划应返回 false")
    void shouldNotMatchWhenPlanDiffers() {
      var rule = ScopeRule.of(ScopeDimension.PLAN, "PLAN-002");
      var plan = AuthorizationFixtures.planSnapshot("PLAN-001");

      assertThat(matcher.matches(List.of(rule), plan)).isFalse();
    }

    @Test
    @DisplayName("空规则列表应返回 true（无约束即匹配）")
    void shouldReturnTrueWhenNoRules() {
      var plan = AuthorizationFixtures.planSnapshot("PLAN-001");
      assertThat(matcher.matches(List.of(), plan)).isTrue();
    }

    @Test
    @DisplayName("多规则 AND 语义：一条不匹配则整体 false")
    void shouldReturnFalseWhenAnyRuleNotMatch() {
      var rule1 = ScopeRule.of(ScopeDimension.PLAN, "PLAN-001");
      var rule2 = ScopeRule.of(ScopeDimension.PLAN, "PLAN-999");
      var plan = AuthorizationFixtures.planSnapshot("PLAN-001");

      assertThat(matcher.matches(List.of(rule1, rule2), plan)).isFalse();
    }
  }
}
```

- [ ] **Step 2: 运行测试验证通过**

Run: `mvn -f auth-service/auth-domain/pom.xml test -Dtest=ScopeMatcherTest -q --no-transfer-progress`
Expected: Tests run: 4, Failures: 0, Errors: 0

- [ ] **Step 3: Commit**

```bash
git add auth-service/auth-domain/src/test/java/com/pension/permission/domain/authorization/service/ScopeMatcherTest.java
git commit -m "test(auth-domain): 补充 ScopeMatcher 范围匹配测试"
```

---

### Task 5: GrantTest

**Files:**
- Create: `auth-service/auth-domain/src/test/java/com/pension/permission/domain/authorization/aggregate/GrantTest.java`

**Interfaces:**
- Consumes: `Grant` 聚合根、`AuthorizationFixtures`
- Produces: Grant 状态机和权限判定逻辑验证通过

- [ ] **Step 1: 编写 GrantTest**

```java
package com.pension.permission.domain.authorization.aggregate;

import com.pension.permission.domain.authorization.enumeration.GrantStatus;
import com.pension.permission.domain.authorization.event.GrantApproved;
import com.pension.permission.domain.authorization.event.GrantRejected;
import com.pension.permission.domain.authorization.event.GrantRevoked;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import com.pension.permission.domain.fixture.AuthorizationFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Grant 聚合根测试")
class GrantTest {

  @Nested
  @DisplayName("状态流转")
  class StatusTransitionTest {

    @Test
    @DisplayName("approve 应将 PENDING_APPROVAL 转为 EFFECTIVE 并注册事件")
    void shouldApprovePendingGrant() {
      var grant = AuthorizationFixtures.pendingAllowGrant();
      var approver = com.example.shared.identifier.id.UserNo.of("approver-1");

      grant.approve(approver);

      assertThat(grant.status()).isEqualTo(GrantStatus.EFFECTIVE);
      assertThat(grant.domainEvents()).anyMatch(e -> e instanceof GrantApproved);
    }

    @Test
    @DisplayName("reject 应将 PENDING_APPROVAL 转为 REJECTED 并注册事件")
    void shouldRejectPendingGrant() {
      var grant = AuthorizationFixtures.pendingAllowGrant();
      var rejecter = com.example.shared.identifier.id.UserNo.of("rejecter-1");

      grant.reject(rejecter);

      assertThat(grant.status()).isEqualTo(GrantStatus.REJECTED);
      assertThat(grant.domainEvents()).anyMatch(e -> e instanceof GrantRejected);
    }

    @Test
    @DisplayName("revoke 应将 EFFECTIVE 转为 REVOKED 并注册事件")
    void shouldRevokeEffectiveGrant() {
      var grant = AuthorizationFixtures.effectiveAllowGrant();
      var revoker = com.example.shared.identifier.id.UserNo.of("revoker-1");

      grant.revoke(revoker);

      assertThat(grant.status()).isEqualTo(GrantStatus.REVOKED);
      assertThat(grant.domainEvents()).anyMatch(e -> e instanceof GrantRevoked);
    }

    @Test
    @DisplayName("对非 PENDING_APPROVAL 状态调用 approve 应抛 IllegalStateException")
    void shouldThrowWhenApproveNonPendingGrant() {
      var grant = AuthorizationFixtures.effectiveAllowGrant();
      assertThatThrownBy(() -> grant.approve(com.example.shared.identifier.id.UserNo.of("u-1")))
        .isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  @DisplayName("权限判定")
  class PermissionCheckTest {

    @Test
    @DisplayName("isActiveAt 在有效期内且 EFFECTIVE 状态应返回 true")
    void shouldBeActiveWhenEffectiveAndWithinValidity() {
      var grant = AuthorizationFixtures.effectiveAllowGrant();
      assertThat(grant.isActiveAt(LocalDateTime.now())).isTrue();
    }

    @Test
    @DisplayName("coversBusiness 匹配相同业务编码应返回 true")
    void shouldCoverBusinessWhenCodeMatches() {
      var grant = AuthorizationFixtures.effectiveAllowGrant();
      assertThat(grant.coversBusiness(new BusinessCode("BIZ-001"))).isTrue();
    }

    @Test
    @DisplayName("coversBusiness 不匹配不同业务编码应返回 false")
    void shouldNotCoverBusinessWhenCodeDiffers() {
      var grant = AuthorizationFixtures.effectiveAllowGrant();
      assertThat(grant.coversBusiness(new BusinessCode("BIZ-999"))).isFalse();
    }

    @Test
    @DisplayName("grants 匹配相同权限应返回 true")
    void shouldGrantWhenPermissionMatches() {
      var grant = AuthorizationFixtures.effectiveAllowGrant();
      var perm = AuthorizationFixtures.permission("BIZ-001", "ACT-VIEW");
      assertThat(grant.grants(perm)).isTrue();
    }
  }
}
```

- [ ] **Step 2: 运行测试验证通过**

Run: `mvn -f auth-service/auth-domain/pom.xml test -Dtest=GrantTest -q --no-transfer-progress`
Expected: Tests run: 7, Failures: 0, Errors: 0

- [ ] **Step 3: Commit**

```bash
git add auth-service/auth-domain/src/test/java/com/pension/permission/domain/authorization/aggregate/GrantTest.java
git commit -m "test(auth-domain): 补充 Grant 聚合根状态机与权限判定测试"
```

---

### Task 6: AuthorizationEngineTest

**Files:**
- Create: `auth-service/auth-domain/src/test/java/com/pension/permission/domain/authorization/service/AuthorizationEngineTest.java`

**Interfaces:**
- Consumes: `AuthorizationEngine`（依赖 ProductGateway、GrantRepository、PlanMembershipLookup）、`AuthorizationFixtures`
- Produces: 两层 AND 判定逻辑验证通过

- [ ] **Step 1: 编写 AuthorizationEngineTest**

```java
package com.pension.permission.domain.authorization.service;

import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.aggregate.Grant;
import com.pension.permission.domain.authorization.repository.GrantRepository;
import com.pension.permission.domain.authorization.spi.PlanMembershipLookup;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import com.pension.permission.domain.authorization.valueobject.Permission;
import com.pension.permission.domain.fixture.AuthorizationFixtures;
import com.pension.permission.domain.product.ProductGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("AuthorizationEngine 权限判定测试")
class AuthorizationEngineTest {

  private final GrantRepository grantRepository = AuthorizationFixtures.mockGrantRepository();
  private final ProductGateway productGateway = AuthorizationFixtures.mockProductGateway();
  private final PlanMembershipLookup membershipLookup = AuthorizationFixtures.mockMembershipLookup();
  private final AuthorizationEngine engine =
    new AuthorizationEngine(productGateway, grantRepository, membershipLookup);

  private void stubPlan(String planNo) {
    when(productGateway.requirePlan(PlanNo.of(planNo)))
      .thenReturn(AuthorizationFixtures.planSnapshot(planNo));
  }

  @Nested
  @DisplayName("能力层判定 checkPlanCapability")
  class CheckPlanCapabilityTest {

    @Test
    @DisplayName("命中 EFFECTIVE ALLOW 授权应返回 true")
    void shouldReturnTrueWhenAllowGrantMatched() {
      var planId = PlanNo.of("PLAN-001");
      var business = new BusinessCode("BIZ-001");
      var at = LocalDateTime.now();
      stubPlan("PLAN-001");
      when(grantRepository.findActiveCapabilityGrants(at))
        .thenReturn(List.of(AuthorizationFixtures.effectiveAllowGrant()));

      assertThat(engine.checkPlanCapability(planId, business, at)).isTrue();
    }

    @Test
    @DisplayName("命中 DENY 授权应返回 false")
    void shouldReturnFalseWhenDenyGrantMatched() {
      var planId = PlanNo.of("PLAN-001");
      var business = new BusinessCode("BIZ-001");
      var at = LocalDateTime.now();
      stubPlan("PLAN-001");
      when(grantRepository.findActiveCapabilityGrants(at))
        .thenReturn(List.of(AuthorizationFixtures.effectiveDenyGrant()));

      assertThat(engine.checkPlanCapability(planId, business, at)).isFalse();
    }

    @Test
    @DisplayName("无匹配授权应返回 false")
    void shouldReturnFalseWhenNoGrantMatched() {
      var planId = PlanNo.of("PLAN-001");
      var business = new BusinessCode("BIZ-001");
      var at = LocalDateTime.now();
      stubPlan("PLAN-001");
      when(grantRepository.findActiveCapabilityGrants(at)).thenReturn(List.of());

      assertThat(engine.checkPlanCapability(planId, business, at)).isFalse();
    }
  }

  @Nested
  @DisplayName("最终判定 checkPermission")
  class CheckPermissionTest {

    @Test
    @DisplayName("能力层失败时应直接返回 false（短路）")
    void shouldReturnFalseWhenCapabilityDenied() {
      var identity = UserNo.of("user-1");
      var planId = PlanNo.of("PLAN-001");
      var permission = AuthorizationFixtures.permission("BIZ-001", "ACT-VIEW");
      var at = LocalDateTime.now();
      stubPlan("PLAN-001");
      when(grantRepository.findActiveCapabilityGrants(at)).thenReturn(List.of());

      assertThat(engine.checkPermission(identity, planId, permission, at)).isFalse();
    }
  }
}
```

- [ ] **Step 2: 运行测试验证通过**

Run: `mvn -f auth-service/auth-domain/pom.xml test -Dtest=AuthorizationEngineTest -q --no-transfer-progress`
Expected: Tests run: 4, Failures: 0, Errors: 0

- [ ] **Step 3: Commit**

```bash
git add auth-service/auth-domain/src/test/java/com/pension/permission/domain/authorization/service/AuthorizationEngineTest.java
git commit -m "test(auth-domain): 补充 AuthorizationEngine 两层 AND 判定测试"
```

---

### Task 7: authorization 域值对象测试

**Files:**
- Create: `auth-service/auth-domain/src/test/java/com/pension/permission/domain/authorization/valueobject/PermissionTest.java`
- Create: `auth-service/auth-domain/src/test/java/com/pension/permission/domain/authorization/valueobject/subject/GrantSubjectTest.java`

**Interfaces:**
- Consumes: `Permission`、`GrantSubject` 子类型、`AuthorizationFixtures`
- Produces: 值对象校验和 covers 逻辑验证通过

- [ ] **Step 1: 编写 PermissionTest**

```java
package com.pension.permission.domain.authorization.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Permission 值对象测试")
class PermissionTest {

  @Nested
  @DisplayName("构造校验")
  class ConstructionTest {

    @Test
    @DisplayName("businessCode 为 null 应抛 NullPointerException")
    void shouldThrowWhenBusinessCodeNull() {
      assertThatThrownBy(() -> new Permission(null, new ActionCode("ACT")))
        .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("wholeBusiness 应创建 actionCode 为 null 的权限")
    void shouldCreateWholeBusinessWithNullAction() {
      var perm = Permission.wholeBusiness(new BusinessCode("BIZ-001"));
      assertThat(perm.actionCode()).isNull();
    }
  }

  @Nested
  @DisplayName("covers 方法")
  class CoversTest {

    @Test
    @DisplayName("actionCode 为 null 时应覆盖任意操作")
    void shouldCoverAnyActionWhenActionCodeNull() {
      var perm = Permission.wholeBusiness(new BusinessCode("BIZ-001"));
      assertThat(perm.covers(new BusinessCode("BIZ-001"), new ActionCode("ANY"))).isTrue();
    }

    @Test
    @DisplayName("actionCode 非 null 时应精确匹配操作")
    void shouldMatchExactAction() {
      var perm = new Permission(new BusinessCode("BIZ-001"), new ActionCode("ACT-VIEW"));
      assertThat(perm.covers(new BusinessCode("BIZ-001"), new ActionCode("ACT-VIEW"))).isTrue();
      assertThat(perm.covers(new BusinessCode("BIZ-001"), new ActionCode("ACT-EDIT"))).isFalse();
    }

    @Test
    @DisplayName("业务编码不匹配应返回 false")
    void shouldReturnFalseWhenBusinessDiffers() {
      var perm = new Permission(new BusinessCode("BIZ-001"), new ActionCode("ACT-VIEW"));
      assertThat(perm.covers(new BusinessCode("BIZ-999"), new ActionCode("ACT-VIEW"))).isFalse();
    }
  }
}
```

- [ ] **Step 2: 编写 GrantSubjectTest**

```java
package com.pension.permission.domain.authorization.valueobject.subject;

import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.spi.PlanMembershipLookup;
import com.pension.permission.types.RoleCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GrantSubject 主体匹配测试")
class GrantSubjectTest {

  @Nested
  @DisplayName("CapabilitySubject")
  class CapabilitySubjectTest {

    @Test
    @DisplayName("covers 应抛 UnsupportedOperationException（能力层不参与主体匹配）")
    void shouldThrowOnCovers() {
      var subject = new CapabilitySubject();
      var lookup = mock(PlanMembershipLookup.class);
      assertThatThrownBy(() -> subject.covers(UserNo.of("u-1"), lookup))
        .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  @DisplayName("PlanAllMembersSubject")
  class PlanAllMembersSubjectTest {

    @Test
    @DisplayName("用户是计划成员时应返回 true")
    void shouldReturnTrueWhenUserIsMember() {
      var planNo = PlanNo.of("PLAN-001");
      var subject = new PlanAllMembersSubject(planNo);
      var lookup = mock(PlanMembershipLookup.class);
      when(lookup.isMemberOf(UserNo.of("u-1"), planNo)).thenReturn(true);

      assertThat(subject.covers(UserNo.of("u-1"), lookup)).isTrue();
    }

    @Test
    @DisplayName("用户非计划成员时应返回 false")
    void shouldReturnFalseWhenUserNotMember() {
      var planNo = PlanNo.of("PLAN-001");
      var subject = new PlanAllMembersSubject(planNo);
      var lookup = mock(PlanMembershipLookup.class);
      when(lookup.isMemberOf(UserNo.of("u-1"), planNo)).thenReturn(false);

      assertThat(subject.covers(UserNo.of("u-1"), lookup)).isFalse();
    }
  }

  @Nested
  @DisplayName("PlanRoleSubject")
  class PlanRoleSubjectTest {

    @Test
    @DisplayName("用户具有计划内指定角色时应返回 true")
    void shouldReturnTrueWhenUserHasRole() {
      var planNo = PlanNo.of("PLAN-001");
      var roleCode = new RoleCode("AGENT");
      var subject = new PlanRoleSubject(planNo, roleCode);
      var lookup = mock(PlanMembershipLookup.class);
      when(lookup.hasRole(UserNo.of("u-1"), planNo, roleCode)).thenReturn(true);

      assertThat(subject.covers(UserNo.of("u-1"), lookup)).isTrue();
    }
  }

  @Nested
  @DisplayName("UserListSubject")
  class UserListSubjectTest {

    @Test
    @DisplayName("用户在列表中时应返回 true")
    void shouldReturnTrueWhenUserInList() {
      var subject = new UserListSubject(java.util.Set.of(UserNo.of("u-1"), UserNo.of("u-2")));
      var lookup = mock(PlanMembershipLookup.class);

      assertThat(subject.covers(UserNo.of("u-1"), lookup)).isTrue();
    }

    @Test
    @DisplayName("用户不在列表中时应返回 false")
    void shouldReturnFalseWhenUserNotInList() {
      var subject = new UserListSubject(java.util.Set.of(UserNo.of("u-1")));
      var lookup = mock(PlanMembershipLookup.class);

      assertThat(subject.covers(UserNo.of("u-999"), lookup)).isFalse();
    }
  }
}
```

- [ ] **Step 3: 运行测试验证通过**

Run: `mvn -f auth-service/auth-domain/pom.xml test -Dtest=PermissionTest,GrantSubjectTest -q --no-transfer-progress`
Expected: Tests run: 10, Failures: 0, Errors: 0

- [ ] **Step 4: Commit**

```bash
git add auth-service/auth-domain/src/test/java/com/pension/permission/domain/authorization/valueobject/
git commit -m "test(auth-domain): 补充 Permission 与 GrantSubject 值对象测试"
```

---

### Task 8: authorization 域全量验证与提交

**Files:**
- 无新增，仅验证

- [ ] **Step 1: 运行 authorization 域全部测试**

Run: `mvn -f auth-service/auth-domain/pom.xml test -Dtest="com.pension.permission.domain.authorization.*" -q --no-transfer-progress`
Expected: 全部通过

- [ ] **Step 2: 若全绿则无额外提交（各 Task 已分别提交）；若有失败则修正后提交**

```bash
# 仅在需要修正时执行
git add -A
git commit -m "test(auth-domain): 修正 authorization 域测试"
```

---

## 第二批：assignment 域

### Task 9: 创建 AssignmentFixtures + AgentIdentityAssignmentTest

**Files:**
- Create: `auth-service/auth-domain/src/test/java/com/pension/permission/domain/fixture/AssignmentFixtures.java`
- Create: `auth-service/auth-domain/src/test/java/com/pension/permission/domain/assignment/aggregate/AgentIdentityAssignmentTest.java`

**Interfaces:**
- Consumes: `AgentIdentityAssignment`、`AssignmentScopeDimension`、`RoleCode`
- Produces: 身份分配生命周期验证通过

- [ ] **Step 1: 创建 AssignmentFixtures**

```java
package com.pension.permission.domain.fixture;

import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.assignment.aggregate.AgentIdentityAssignment;
import com.pension.permission.domain.assignment.repository.AssignmentRepository;
import com.pension.permission.types.AssignmentId;
import com.pension.permission.types.AssignmentScopeDimension;
import com.pension.permission.types.RoleCode;

import static org.mockito.Mockito.mock;

/**
 * assignment 域测试数据工厂。
 */
public final class AssignmentFixtures {

  private AssignmentFixtures() {}

  public static AgentIdentityAssignment activeAssignment(String userNo, String roleCode) {
    return AgentIdentityAssignment.create(
      new AssignmentId("a-1"),
      UserNo.of("creator-1"),
      UserNo.of(userNo),
      new RoleCode(roleCode),
      AssignmentScopeDimension.PLAN,
      "PLAN-001",
      false);
  }

  public static AgentIdentityAssignment inheritableCustomerAssignment(String userNo, String roleCode, String customerValue) {
    return AgentIdentityAssignment.create(
      new AssignmentId("a-2"),
      UserNo.of("creator-1"),
      UserNo.of(userNo),
      new RoleCode(roleCode),
      AssignmentScopeDimension.CUSTOMER,
      customerValue,
      true);
  }

  public static AssignmentRepository mockAssignmentRepository() {
    return mock(AssignmentRepository.class);
  }
}
```

- [ ] **Step 2: 编写 AgentIdentityAssignmentTest**

```java
package com.pension.permission.domain.assignment.aggregate;

import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.assignment.enumeration.AssignmentStatus;
import com.pension.permission.domain.assignment.event.AssignmentCreated;
import com.pension.permission.domain.assignment.event.AssignmentDeactivated;
import com.pension.permission.domain.fixture.AssignmentFixtures;
import com.pension.permission.types.AssignmentScopeDimension;
import com.pension.permission.types.RoleCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AgentIdentityAssignment 聚合根测试")
class AgentIdentityAssignmentTest {

  @Nested
  @DisplayName("创建 create")
  class CreateTest {

    @Test
    @DisplayName("应创建 ACTIVE 状态的分配并注册事件")
    void shouldCreateActiveAssignment() {
      var assignment = AssignmentFixtures.activeAssignment("user-1", "ROLE_AGENT");

      assertThat(assignment.isActive()).isTrue();
      assertThat(assignment.userNo()).isEqualTo(UserNo.of("user-1"));
      assertThat(assignment.roleCode()).isEqualTo(new RoleCode("ROLE_AGENT"));
      assertThat(assignment.domainEvents()).anyMatch(e -> e instanceof AssignmentCreated);
    }

    @Test
    @DisplayName("inheritable=true 但 scopeDimension 非 CUSTOMER 应抛异常")
    void shouldThrowWhenInheritableButNotCustomerScope() {
      assertThatThrownBy(() -> AgentIdentityAssignment.create(
        new com.pension.permission.types.AssignmentId("a-1"),
        UserNo.of("creator-1"),
        UserNo.of("user-1"),
        new RoleCode("ROLE_AGENT"),
        AssignmentScopeDimension.PLAN,
        "PLAN-001",
        true))
        .isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  @DisplayName("角色变更 changeRole")
  class ChangeRoleTest {

    @Test
    @DisplayName("变更角色应更新 roleCode 并注册事件")
    void shouldChangeRole() {
      var assignment = AssignmentFixtures.activeAssignment("user-1", "ROLE_AGENT");

      assignment.changeRole(new RoleCode("ROLE_REVIEWER"));

      assertThat(assignment.roleCode()).isEqualTo(new RoleCode("ROLE_REVIEWER"));
    }

    @Test
    @DisplayName("变更到相同角色应保持不变")
    void shouldKeepSameRoleWhenNoChange() {
      var assignment = AssignmentFixtures.activeAssignment("user-1", "ROLE_AGENT");

      assignment.changeRole(new RoleCode("ROLE_AGENT"));

      assertThat(assignment.roleCode()).isEqualTo(new RoleCode("ROLE_AGENT"));
    }
  }

  @Nested
  @DisplayName("停用 deactivate")
  class DeactivateTest {

    @Test
    @DisplayName("停用活跃分配应转为 DEACTIVATED 并注册事件")
    void shouldDeactivateActiveAssignment() {
      var assignment = AssignmentFixtures.activeAssignment("user-1", "ROLE_AGENT");

      assignment.deactivate();

      assertThat(assignment.isActive()).isFalse();
      assertThat(assignment.domainEvents()).anyMatch(e -> e instanceof AssignmentDeactivated);
    }

    @Test
    @DisplayName("对已停用分配再次停用应幂等")
    void shouldBeIdempotentWhenAlreadyDeactivated() {
      var assignment = AssignmentFixtures.activeAssignment("user-1", "ROLE_AGENT");
      assignment.deactivate();
      var eventCountBefore = assignment.domainEvents().size();

      assignment.deactivate();

      assertThat(assignment.domainEvents()).hasSize(eventCountBefore);
    }
  }
}
```

- [ ] **Step 3: 运行测试验证通过**

Run: `mvn -f auth-service/auth-domain/pom.xml test -Dtest=AgentIdentityAssignmentTest -q --no-transfer-progress`
Expected: Tests run: 5, Failures: 0, Errors: 0

- [ ] **Step 4: Commit**

```bash
git add auth-service/auth-domain/src/test/java/com/pension/permission/domain/fixture/AssignmentFixtures.java auth-service/auth-domain/src/test/java/com/pension/permission/domain/assignment/aggregate/AgentIdentityAssignmentTest.java
git commit -m "test(auth-domain): 补充 AgentIdentityAssignment 聚合根测试"
```

---

### Task 10: assignment 域领域服务测试

**Files:**
- Create: `auth-service/auth-domain/src/test/java/com/pension/permission/domain/assignment/service/GrantProvisioningServiceTest.java`
- Create: `auth-service/auth-domain/src/test/java/com/pension/permission/domain/assignment/service/PlanReachabilityServiceTest.java`

**Interfaces:**
- Consumes: `GrantProvisioningService`、`PlanReachabilityService`、`AssignmentFixtures`、`AuthorizationFixtures`
- Produces: 身份分配编排和计划可达性验证通过

> **注意**：`EffectivePermissionService` 依赖 7 个 SPI，mock 成本高且与 AuthorizationEngine 重复度高，本任务暂不覆盖，记录为技术债。如时间允许可在 Task 11 后补充。

- [ ] **Step 1: 编写 GrantProvisioningServiceTest**

```java
package com.pension.permission.domain.assignment.service;

import com.pension.permission.domain.assignment.aggregate.AgentIdentityAssignment;
import com.pension.permission.domain.assignment.repository.AssignmentRepository;
import com.pension.permission.domain.fixture.AssignmentFixtures;
import com.pension.permission.domain.role.service.RoleTemplateResolver;
import com.pension.permission.types.RoleCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("GrantProvisioningService 测试")
class GrantProvisioningServiceTest {

  private final RoleTemplateResolver roleTemplateResolver = mock(RoleTemplateResolver.class);
  private final AssignmentRepository assignmentRepository = AssignmentFixtures.mockAssignmentRepository();
  private final GrantProvisioningService service =
    new GrantProvisioningService(roleTemplateResolver, assignmentRepository);

  @Nested
  @DisplayName("onAssignmentCreated")
  class OnAssignmentCreatedTest {

    @Test
    @DisplayName("模板存在时应保存分配")
    void shouldSaveAssignmentWhenTemplateExists() {
      var assignment = AssignmentFixtures.activeAssignment("user-1", "ROLE_AGENT");
      when(roleTemplateResolver.resolve(any(), any(), any()))
        .thenReturn(Optional.of(mock(com.pension.permission.domain.role.aggregate.RoleTemplate.class)));

      service.onAssignmentCreated(assignment);

      verify(assignmentRepository).save(assignment);
    }
  }

  @Nested
  @DisplayName("onAssignmentDeactivated")
  class OnAssignmentDeactivatedTest {

    @Test
    @DisplayName("应调用 deactivate 并保存")
    void shouldDeactivateAndSave() {
      var assignment = AssignmentFixtures.activeAssignment("user-1", "ROLE_AGENT");

      service.onAssignmentDeactivated(assignment);

      verify(assignmentRepository).save(assignment);
    }
  }
}
```

- [ ] **Step 2: 编写 PlanReachabilityServiceTest**

```java
package com.pension.permission.domain.assignment.service;

import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.assignment.aggregate.AgentIdentityAssignment;
import com.pension.permission.domain.assignment.repository.AssignmentRepository;
import com.pension.permission.domain.fixture.AssignmentFixtures;
import com.pension.permission.domain.fixture.AuthorizationFixtures;
import com.pension.permission.domain.product.ProductGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("PlanReachabilityService 测试")
class PlanReachabilityServiceTest {

  private final AssignmentRepository assignmentRepository = AssignmentFixtures.mockAssignmentRepository();
  private final ProductGateway productGateway = AuthorizationFixtures.mockProductGateway();
  private final PlanReachabilityService service =
    new PlanReachabilityService(assignmentRepository, productGateway);

  @Nested
  @DisplayName("listSelectablePlans")
  class ListSelectablePlansTest {

    @Test
    @DisplayName("无活跃分配时应返回空列表")
    void shouldReturnEmptyWhenNoAssignment() {
      when(assignmentRepository.findActiveByAccount(UserNo.of("user-1")))
        .thenReturn(List.of());

      var plans = service.listSelectablePlans(UserNo.of("user-1"));

      assertThat(plans).isEmpty();
    }
  }
}
```

- [ ] **Step 3: 运行测试验证通过**

Run: `mvn -f auth-service/auth-domain/pom.xml test -Dtest=GrantProvisioningServiceTest,PlanReachabilityServiceTest -q --no-transfer-progress`
Expected: Tests run: 3, Failures: 0, Errors: 0

- [ ] **Step 4: Commit**

```bash
git add auth-service/auth-domain/src/test/java/com/pension/permission/domain/assignment/service/
git commit -m "test(auth-domain): 补充 assignment 域领域服务测试"
```

---

## 第三批：channel 域补全

### Task 11: 创建 ChannelFixtures + SessionTest

**Files:**
- Create: `auth-service/auth-domain/src/test/java/com/pension/permission/domain/fixture/ChannelFixtures.java`
- Create: `auth-service/auth-domain/src/test/java/com/pension/permission/domain/channel/aggregate/SessionTest.java`

**Interfaces:**
- Consumes: `Session`、`EffectiveIdentity`、`AnnuityChannel`、`SessionId`
- Produces: 会话状态机验证通过

- [ ] **Step 1: 创建 ChannelFixtures**

```java
package com.pension.permission.domain.fixture;

import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.aggregate.Session;
import com.pension.permission.domain.channel.valueobject.EffectiveIdentity;
import com.pension.permission.types.SessionId;

import java.time.Duration;

/**
 * channel 域测试数据工厂。
 */
public final class ChannelFixtures {

  private ChannelFixtures() {}

  public static EffectiveIdentity directIdentity(String userNo) {
    return EffectiveIdentity.direct(UserNo.of(userNo));
  }

  public static Session activeSession(String userNo) {
    return Session.create(
      new SessionId("s-1"),
      UserNo.of("creator-1"),
      UserNo.of(userNo),
      com.pension.permission.domain.channel.enumeration.AnnuityChannel.ONLINE,
      directIdentity(userNo),
      Duration.ofHours(2));
  }

  public static Session branchSession(String userNo) {
    return Session.create(
      new SessionId("s-2"),
      UserNo.of("creator-1"),
      UserNo.of(userNo),
      com.pension.permission.domain.channel.enumeration.AnnuityChannel.BANK_BRANCH,
      directIdentity(userNo),
      Duration.ofHours(2));
  }
}
```

- [ ] **Step 2: 编写 SessionTest**

```java
package com.pension.permission.domain.channel.aggregate;

import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.enumeration.SessionStatus;
import com.pension.permission.domain.channel.event.SessionClosed;
import com.pension.permission.domain.channel.event.SessionCreated;
import com.pension.permission.domain.channel.event.SessionPlanSelected;
import com.pension.permission.domain.fixture.ChannelFixtures;
import com.pension.permission.types.SecondaryAuthSessionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Session 聚合根测试")
class SessionTest {

  @Nested
  @DisplayName("创建 create")
  class CreateTest {

    @Test
    @DisplayName("应创建 ACTIVE 状态会话并注册事件")
    void shouldCreateActiveSession() {
      var session = ChannelFixtures.activeSession("user-1");

      assertThat(session.status()).isEqualTo(SessionStatus.ACTIVE);
      assertThat(session.domainEvents()).anyMatch(e -> e instanceof SessionCreated);
    }
  }

  @Nested
  @DisplayName("计划选择 selectPlan")
  class SelectPlanTest {

    @Test
    @DisplayName("选择计划应更新 selectedPlanId 并注册事件")
    void shouldSelectPlan() {
      var session = ChannelFixtures.activeSession("user-1");

      session.selectPlan(PlanNo.of("PLAN-001"), UserNo.of("user-1"));

      assertThat(session.selectedPlanId()).isEqualTo(PlanNo.of("PLAN-001"));
      assertThat(session.domainEvents()).anyMatch(e -> e instanceof SessionPlanSelected);
    }

    @Test
    @DisplayName("planId 为 null 应抛 IllegalArgumentException")
    void shouldThrowWhenPlanIdNull() {
      var session = ChannelFixtures.activeSession("user-1");

      assertThatThrownBy(() -> session.selectPlan(null, UserNo.of("user-1")))
        .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("关闭 close")
  class CloseTest {

    @Test
    @DisplayName("关闭活跃会话应转为 CLOSED 并注册事件")
    void shouldCloseActiveSession() {
      var session = ChannelFixtures.activeSession("user-1");

      session.close(UserNo.of("user-1"));

      assertThat(session.status()).isEqualTo(SessionStatus.CLOSED);
      assertThat(session.domainEvents()).anyMatch(e -> e instanceof SessionClosed);
    }
  }

  @Nested
  @DisplayName("过期 expire")
  class ExpireTest {

    @Test
    @DisplayName("未到过期时间调用 expire 应抛 IllegalStateException")
    void shouldThrowWhenNotExpiredYet() {
      var session = ChannelFixtures.activeSession("user-1");

      assertThatThrownBy(() -> session.expire(UserNo.of("user-1")))
        .isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  @DisplayName("二次授权 applySecondaryAuth")
  class ApplySecondaryAuthTest {

    @Test
    @DisplayName("非网点渠道调用应抛 DomainException")
    void shouldThrowWhenNotBranchChannel() {
      var session = ChannelFixtures.activeSession("user-1"); // ONLINE 渠道
      var secondaryId = new SecondaryAuthSessionId("sa-1");
      var identity = ChannelFixtures.directIdentity("user-2");

      assertThatThrownBy(() -> session.applySecondaryAuth(secondaryId, identity, UserNo.of("user-1")))
        .isInstanceOf(com.example.shared.exception.DomainException.class);
    }
  }
}
```

- [ ] **Step 3: 运行测试验证通过**

Run: `mvn -f auth-service/auth-domain/pom.xml test -Dtest=SessionTest -q --no-transfer-progress`
Expected: Tests run: 5, Failures: 0, Errors: 0

- [ ] **Step 4: Commit**

```bash
git add auth-service/auth-domain/src/test/java/com/pension/permission/domain/fixture/ChannelFixtures.java auth-service/auth-domain/src/test/java/com/pension/permission/domain/channel/aggregate/SessionTest.java
git commit -m "test(auth-domain): 补充 Session 聚合根测试"
```

---

### Task 12: PlanSelectionStrategy 测试

**Files:**
- Create: `auth-service/auth-domain/src/test/java/com/pension/permission/domain/channel/service/HqPlanSelectionStrategyTest.java`
- Create: `auth-service/auth-domain/src/test/java/com/pension/permission/domain/channel/service/OnlinePlanSelectionStrategyTest.java`
- Create: `auth-service/auth-domain/src/test/java/com/pension/permission/domain/channel/service/BranchPlanSelectionStrategyTest.java`

**Interfaces:**
- Consumes: 3 个 `PlanSelectionStrategy` 实现、`ChannelFixtures`
- Produces: 渠道计划选择策略验证通过

- [ ] **Step 1: 编写 HqPlanSelectionStrategyTest**

```java
package com.pension.permission.domain.channel.service;

import com.pension.permission.domain.channel.valueobject.AllPlans;
import com.pension.permission.domain.fixture.ChannelFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HqPlanSelectionStrategy 测试")
class HqPlanSelectionStrategyTest {

  private final HqPlanSelectionStrategy strategy = new HqPlanSelectionStrategy();

  @Test
  @DisplayName("应恒返回 AllPlans（总部可见全部计划）")
  void shouldReturnAllPlans() {
    var identity = ChannelFixtures.directIdentity("user-1");

    var result = strategy.listSelectablePlans(identity);

    assertThat(result).isInstanceOf(AllPlans.class);
  }
}
```

- [ ] **Step 2: 编写 OnlinePlanSelectionStrategyTest**

```java
package com.pension.permission.domain.channel.service;

import com.pension.permission.domain.assignment.service.PlanReachabilityService;
import com.pension.permission.domain.channel.valueobject.EnumeratedPlans;
import com.pension.permission.domain.fixture.ChannelFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("OnlinePlanSelectionStrategy 测试")
class OnlinePlanSelectionStrategyTest {

  private final PlanReachabilityService reachabilityService = mock(PlanReachabilityService.class);
  private final OnlinePlanSelectionStrategy strategy =
    new OnlinePlanSelectionStrategy(reachabilityService);

  @Test
  @DisplayName("应返回 EnumeratedPlans，委托 PlanReachabilityService")
  void shouldReturnEnumeratedPlans() {
    var identity = ChannelFixtures.directIdentity("user-1");
    when(reachabilityService.listSelectablePlans(identity.identityAccountId()))
      .thenReturn(List.of());

    var result = strategy.listSelectablePlans(identity);

    assertThat(result).isInstanceOf(EnumeratedPlans.class);
  }
}
```

- [ ] **Step 3: 编写 BranchPlanSelectionStrategyTest**

```java
package com.pension.permission.domain.channel.service;

import com.pension.permission.domain.channel.valueobject.EffectiveIdentity;
import com.pension.permission.domain.fixture.ChannelFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BranchPlanSelectionStrategy 测试")
class BranchPlanSelectionStrategyTest {

  private final OnlinePlanSelectionStrategy delegate =
    new OnlinePlanSelectionStrategy(mock(com.pension.permission.domain.assignment.service.PlanReachabilityService.class));
  private final BranchPlanSelectionStrategy strategy =
    new BranchPlanSelectionStrategy(delegate);

  @Test
  @DisplayName("viaSecondaryAuth=false 时应抛 IllegalStateException")
  void shouldThrowWhenNotViaSecondaryAuth() {
    var identity = ChannelFixtures.directIdentity("user-1"); // viaSecondaryAuth=false

    assertThatThrownBy(() -> strategy.listSelectablePlans(identity))
      .isInstanceOf(IllegalStateException.class);
  }

  private static <T> T mock(Class<T> clazz) {
    return org.mockito.Mockito.mock(clazz);
  }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn -f auth-service/auth-domain/pom.xml test -Dtest=HqPlanSelectionStrategyTest,OnlinePlanSelectionStrategyTest,BranchPlanSelectionStrategyTest -q --no-transfer-progress`
Expected: Tests run: 3, Failures: 0, Errors: 0

- [ ] **Step 5: Commit**

```bash
git add auth-service/auth-domain/src/test/java/com/pension/permission/domain/channel/service/
git commit -m "test(auth-domain): 补充 PlanSelectionStrategy 测试"
```

---

## 第四批：credential + role + user 域

### Task 13: 创建 CredentialFixtures + PasswordCredentialTest + UKeyCredentialTest

**Files:**
- Create: `auth-service/auth-domain/src/test/java/com/pension/permission/domain/fixture/CredentialFixtures.java`
- Create: `auth-service/auth-domain/src/test/java/com/pension/permission/domain/credential/aggregate/PasswordCredentialTest.java`
- Create: `auth-service/auth-domain/src/test/java/com/pension/permission/domain/credential/aggregate/UKeyCredentialTest.java`

**Interfaces:**
- Consumes: `PasswordCredential`、`UKeyCredential`、`CredentialOwner` 子类型
- Produces: 凭证状态流转和轮换逻辑验证通过

- [ ] **Step 1: 创建 CredentialFixtures**

```java
package com.pension.permission.domain.fixture;

import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.credential.aggregate.PasswordCredential;
import com.pension.permission.domain.credential.aggregate.UKeyCredential;
import com.pension.permission.domain.credential.valueobject.owner.UserCredentialOwner;
import com.pension.permission.types.CredentialId;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

/**
 * credential 域测试数据工厂。
 */
public final class CredentialFixtures {

  private CredentialFixtures() {}

  public static Clock fixedClock(String instant) {
    return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
  }

  public static PasswordCredential activePasswordCredential(String userNo) {
    return PasswordCredential.create(
      new CredentialId("c-pwd-1"),
      UserNo.of(userNo),
      "hashed-password-001",
      Set.of(),
      UserNo.of("creator-1"),
      fixedClock("2026-01-01T00:00:00Z"));
  }

  public static UKeyCredential activeUKeyCredential(String userNo) {
    return UKeyCredential.create(
      new CredentialId("c-ukey-1"),
      new UserCredentialOwner(UserNo.of(userNo)),
      "key-serial-001",
      Set.of(),
      com.example.shared.domain.valueobject.ValidityPeriod.of(
        java.time.LocalDateTime.now(),
        java.time.LocalDateTime.now().plusDays(365)),
      UserNo.of("creator-1"));
  }
}
```

> **注意**：`UKeyCredential.create` 的 `validityPeriod` 参数类型为 `ValidityPeriod`，实现时需确认 `com.example.shared.domain.valueobject.ValidityPeriod` 的工厂方法签名。`PasswordCredential.create` 的 channels 参数类型为 `Set<AnnuityChannel>`，确认 import 路径。

- [ ] **Step 2: 编写 PasswordCredentialTest**

```java
package com.pension.permission.domain.credential.aggregate;

import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.credential.enumeration.CredentialStatus;
import com.pension.permission.domain.credential.event.PasswordChanged;
import com.pension.permission.domain.fixture.CredentialFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PasswordCredential 聚合根测试")
class PasswordCredentialTest {

  @Nested
  @DisplayName("创建 create")
  class CreateTest {

    @Test
    @DisplayName("应创建 ACTIVE 状态的密码凭证")
    void shouldCreateActiveCredential() {
      var credential = CredentialFixtures.activePasswordCredential("user-1");

      assertThat(credential.status()).isEqualTo(CredentialStatus.ACTIVE);
      assertThat(credential.type()).isEqualTo(CredentialType.PASSWORD);
      assertThat(credential.passwordHash()).isEqualTo("hashed-password-001");
    }
  }

  @Nested
  @DisplayName("轮换密码 rotatePassword")
  class RotatePasswordTest {

    @Test
    @DisplayName("应更新密码哈希并注册事件")
    void shouldRotatePassword() {
      var credential = CredentialFixtures.activePasswordCredential("user-1");

      credential.rotatePassword("new-hash-002", UserNo.of("user-1"),
        CredentialFixtures.fixedClock("2026-01-02T00:00:00Z"));

      assertThat(credential.passwordHash()).isEqualTo("new-hash-002");
      assertThat(credential.domainEvents()).anyMatch(e -> e instanceof PasswordChanged);
    }

    @Test
    @DisplayName("新旧密码相同应抛 DomainException")
    void shouldThrowWhenSameAsOld() {
      var credential = CredentialFixtures.activePasswordCredential("user-1");

      assertThatThrownBy(() -> credential.rotatePassword("hashed-password-001", UserNo.of("user-1"),
        CredentialFixtures.fixedClock("2026-01-02T00:00:00Z")))
        .isInstanceOf(com.example.shared.exception.DomainException.class);
    }
  }
}
```

- [ ] **Step 3: 编写 UKeyCredentialTest**

```java
package com.pension.permission.domain.credential.aggregate;

import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.credential.enumeration.CredentialStatus;
import com.pension.permission.domain.credential.event.UKeyRotated;
import com.pension.permission.domain.fixture.CredentialFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UKeyCredential 聚合根测试")
class UKeyCredentialTest {

  @Nested
  @DisplayName("创建 create")
  class CreateTest {

    @Test
    @DisplayName("应创建 ACTIVE 状态的 UKey 凭证")
    void shouldCreateActiveCredential() {
      var credential = CredentialFixtures.activeUKeyCredential("user-1");

      assertThat(credential.status()).isEqualTo(CredentialStatus.ACTIVE);
      assertThat(credential.type()).isEqualTo(CredentialType.U_KEY);
      assertThat(credential.keySerial()).isEqualTo("key-serial-001");
    }
  }

  @Nested
  @DisplayName("轮换 rotate")
  class RotateTest {

    @Test
    @DisplayName("应更新 keySerial 并注册事件")
    void shouldRotateUKey() {
      var credential = CredentialFixtures.activeUKeyCredential("user-1");

      credential.rotate("new-serial-002", UserNo.of("user-1"));

      assertThat(credential.keySerial()).isEqualTo("new-serial-002");
      assertThat(credential.domainEvents()).anyMatch(e -> e instanceof UKeyRotated);
    }

    @Test
    @DisplayName("新旧 keySerial 相同应抛 DomainException")
    void shouldThrowWhenSameAsOld() {
      var credential = CredentialFixtures.activeUKeyCredential("user-1");

      assertThatThrownBy(() -> credential.rotate("key-serial-001", UserNo.of("user-1")))
        .isInstanceOf(com.example.shared.exception.DomainException.class);
    }
  }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn -f auth-service/auth-domain/pom.xml test -Dtest=PasswordCredentialTest,UKeyCredentialTest -q --no-transfer-progress`
Expected: Tests run: 4, Failures: 0, Errors: 0

- [ ] **Step 5: Commit**

```bash
git add auth-service/auth-domain/src/test/java/com/pension/permission/domain/fixture/CredentialFixtures.java auth-service/auth-domain/src/test/java/com/pension/permission/domain/credential/aggregate/
git commit -m "test(auth-domain): 补充 PasswordCredential 与 UKeyCredential 测试"
```

---

### Task 14: RoleTemplate 测试

**Files:**
- Create: `auth-service/auth-domain/src/test/java/com/pension/permission/domain/role/aggregate/RoleTemplateTest.java`

**Interfaces:**
- Consumes: `RoleTemplate`、`RoleTemplateScopeDimension`、`AuthorizationFixtures`
- Produces: 角色模板状态流转验证通过

- [ ] **Step 1: 编写 RoleTemplateTest**

```java
package com.pension.permission.domain.role.aggregate;

import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.fixture.AuthorizationFixtures;
import com.pension.permission.domain.role.enumeration.RoleTemplateStatus;
import com.pension.permission.types.RoleCode;
import com.pension.permission.types.RoleTemplateId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RoleTemplate 聚合根测试")
class RoleTemplateTest {

  private RoleTemplate effectiveTemplate() {
    return RoleTemplate.create(
      new RoleTemplateId("rt-1"),
      UserNo.of("creator-1"),
      new RoleCode("ROLE_AGENT"),
      com.pension.permission.domain.role.enumeration.RoleTemplateScopeDimension.PLAN,
      "PLAN-001",
      Set.of(AuthorizationFixtures.permission("BIZ-001", "ACT-VIEW")),
      RoleTemplateStatus.EFFECTIVE);
  }

  @Nested
  @DisplayName("创建 create")
  class CreateTest {

    @Test
    @DisplayName("应创建指定状态的角色模板")
    void shouldCreateTemplate() {
      var template = effectiveTemplate();

      assertThat(template.roleCode()).isEqualTo(new RoleCode("ROLE_AGENT"));
      assertThat(template.isActive()).isTrue();
      assertThat(template.permissions()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("状态流转")
  class StatusTransitionTest {

    @Test
    @DisplayName("activate 已激活模板应幂等")
    void shouldBeIdempotentWhenActivateActiveTemplate() {
      var template = effectiveTemplate();

      template.activate(UserNo.of("user-1"));

      assertThat(template.status()).isEqualTo(RoleTemplateStatus.EFFECTIVE);
    }

    @Test
    @DisplayName("deactivate 应转为 INACTIVE")
    void shouldDeactivateTemplate() {
      var template = effectiveTemplate();

      template.deactivate(UserNo.of("user-1"));

      assertThat(template.status()).isEqualTo(RoleTemplateStatus.INACTIVE);
      assertThat(template.isActive()).isFalse();
    }
  }

  @Nested
  @DisplayName("权限判定")
  class PermissionCheckTest {

    @Test
    @DisplayName("hasPermission 匹配已有权限应返回 true")
    void shouldReturnTrueWhenPermissionExists() {
      var template = effectiveTemplate();
      var perm = AuthorizationFixtures.permission("BIZ-001", "ACT-VIEW");

      assertThat(template.hasPermission(perm)).isTrue();
    }
  }
}
```

- [ ] **Step 2: 运行测试验证通过**

Run: `mvn -f auth-service/auth-domain/pom.xml test -Dtest=RoleTemplateTest -q --no-transfer-progress`
Expected: Tests run: 4, Failures: 0, Errors: 0

- [ ] **Step 3: Commit**

```bash
git add auth-service/auth-domain/src/test/java/com/pension/permission/domain/role/aggregate/RoleTemplateTest.java
git commit -m "test(auth-domain): 补充 RoleTemplate 聚合根测试"
```

---

### Task 15: UserAggregate 测试

**Files:**
- Create: `auth-service/auth-domain/src/test/java/com/pension/permission/domain/user/aggregate/UserAggregateTest.java`

**Interfaces:**
- Consumes: `UserAggregate`、`UserType`、`IdentityDocument`、`Mobile`
- Produces: 用户激活/冻结/停用状态机验证通过

- [ ] **Step 1: 编写 UserAggregateTest**

```java
package com.pension.permission.domain.user.aggregate;

import com.example.shared.contactinfo.Mobile;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.user.enumeration.UserStatus;
import com.pension.permission.domain.user.event.UserActivated;
import com.pension.permission.domain.user.event.UserDisabled;
import com.pension.permission.domain.user.event.UserFrozen;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UserAggregate 聚合根测试")
class UserAggregateTest {

  private UserAggregate activeUser() {
    return UserAggregate.create(
      UserNo.of("user-1"),
      com.pension.permission.domain.user.enumeration.UserType.CUSTOMER,
      com.example.shared.contactinfo.IdentityDocument.of(
        com.example.shared.contactinfo.IdentityType.ID_CARD, "110101199001011234"),
      new Mobile("+8613800138000"),
      UserNo.of("creator-1"));
  }

  @Nested
  @DisplayName("创建 create")
  class CreateTest {

    @Test
    @DisplayName("应创建 ACTIVE 状态用户")
    void shouldCreateActiveUser() {
      var user = activeUser();

      assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
      assertThat(user.isActive()).isTrue();
    }
  }

  @Nested
  @DisplayName("冻结 freeze")
  class FreezeTest {

    @Test
    @DisplayName("冻结活跃用户应转为 FROZEN 并注册事件")
    void shouldFreezeActiveUser() {
      var user = activeUser();

      user.freeze(UserNo.of("admin-1"));

      assertThat(user.getStatus()).isEqualTo(UserStatus.FROZEN);
      assertThat(user.domainEvents()).anyMatch(e -> e instanceof UserFrozen);
    }

    @Test
    @DisplayName("重复冻结已冻结用户应抛 DomainException")
    void shouldThrowWhenFreezeFrozenUser() {
      var user = activeUser();
      user.freeze(UserNo.of("admin-1"));

      assertThatThrownBy(() -> user.freeze(UserNo.of("admin-1")))
        .isInstanceOf(com.example.shared.exception.DomainException.class);
    }
  }

  @Nested
  @DisplayName("激活 activate")
  class ActivateTest {

    @Test
    @DisplayName("激活已冻结用户应转为 ACTIVE 并注册事件")
    void shouldActivateFrozenUser() {
      var user = activeUser();
      user.freeze(UserNo.of("admin-1"));

      user.activate(UserNo.of("admin-1"));

      assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
      assertThat(user.domainEvents()).anyMatch(e -> e instanceof UserActivated);
    }
  }

  @Nested
  @DisplayName("停用 disable")
  class DisableTest {

    @Test
    @DisplayName("停用活跃用户应转为 DISABLED 并注册事件")
    void shouldDisableActiveUser() {
      var user = activeUser();

      user.disable(UserNo.of("admin-1"));

      assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
      assertThat(user.domainEvents()).anyMatch(e -> e instanceof UserDisabled);
    }
  }
}
```

> **注意**：`IdentityDocument.of` 和 `IdentityType.ID_CARD` 的工厂方法签名需在实现时确认。`UserAggregate` 标注 `@Getter`，字段访问使用 getter（`getStatus()` 而非 `status()`）。

- [ ] **Step 2: 运行测试验证通过**

Run: `mvn -f auth-service/auth-domain/pom.xml test -Dtest=UserAggregateTest -q --no-transfer-progress`
Expected: Tests run: 4, Failures: 0, Errors: 0

- [ ] **Step 3: Commit**

```bash
git add auth-service/auth-domain/src/test/java/com/pension/permission/domain/user/aggregate/UserAggregateTest.java
git commit -m "test(auth-domain): 补充 UserAggregate 聚合根测试"
```

---

### Task 16: 全量验证

**Files:**
- 无新增，仅验证

- [ ] **Step 1: 运行 auth-domain 全部测试**

Run: `mvn -f auth-service/auth-domain/pom.xml test -q --no-transfer-progress`
Expected: 全部通过（含原有 4 个 + 新增约 20 个测试类）

- [ ] **Step 2: 检查测试覆盖率（可选）**

Run: `mvn -f auth-service/auth-domain/pom.xml test jacoco:report -q --no-transfer-progress`
Expected: 生成覆盖率报告（jacoco 需在父 pom 配置，若无则跳过）

- [ ] **Step 3: 若有失败测试则修正**

```bash
# 仅在需要时执行
git add -A
git commit -m "test(auth-domain): 修正测试失败用例"
```

---

## Self-Review

**1. Spec coverage:**
- authorization 域：Task 3-7 覆盖 Grant、AuthorizationEngine、EffectResolver、ScopeMatcher、Permission、GrantSubject ✓
- assignment 域：Task 9-10 覆盖 AgentIdentityAssignment、GrantProvisioningService、PlanReachabilityService ✓（EffectivePermissionService 标注为技术债，未覆盖）
- channel 域：Task 11-12 覆盖 Session、3 个 PlanSelectionStrategy ✓（DefaultSecondaryAuthService、IdentityResolutionService 依赖较多，标注为后续迭代）
- credential 域：Task 13 覆盖 PasswordCredential、UKeyCredential ✓
- role 域：Task 14 覆盖 RoleTemplate ✓（RoleTemplateResolver、RoleVisibilityResolver 依赖较多，标注为后续迭代）
- user 域：Task 15 覆盖 UserAggregate ✓（AuthenticationProvider 是接口，CredentialAuthenticator 依赖 Map 注入，标注为后续迭代）

**2. Placeholder scan:**
- Task 2 中 `ValidityPeriod` 和 `ScopeDimension` 的 import 路径标注了"需确认"，这是合理的实现期注意事项，非占位符
- 各 Task 的测试代码完整，无 "TODO"/"TBD"

**3. Type consistency:**
- `AuthorizationFixtures` 中 `effectiveAllowGrant()` / `effectiveDenyGrant()` 在 Task 3/5/6 中一致使用 ✓
- `AssignmentFixtures.activeAssignment()` 在 Task 9/10 中一致使用 ✓
- `ChannelFixtures.activeSession()` / `branchSession()` 在 Task 11/12 中一致使用 ✓
- ID 类型构造方式（`new GrantId("g-1")` / `UserNo.of("u-1")`）全局一致 ✓

**4. 已知技术债（未覆盖）：**
- `EffectivePermissionService`（7 个 SPI 依赖，mock 成本高）
- `DefaultSecondaryAuthService`（已废弃，且抛原生 SecurityException）
- `IdentityResolutionService`（5 个 SPI 依赖，4 步流程复杂）
- `RoleTemplateResolver` / `RoleVisibilityResolver`（多层 fallback 逻辑）
- `CredentialAuthenticator`（Map<Class, Provider> 注入方式特殊）
