# auth-domain 单元测试补充设计

> 日期：2026-08-06
> 范围：auth-service / auth-domain 模块（com.pension.permission.domain）
> 状态：设计待评审

## 一、背景与目标

### 1.1 问题背景

auth-service 经过近期迭代已落地 7 个子域、约 180 个类，但 [auth-domain 测试目录](file:///d:\WorkSpace\Trae\multiple-module-spring-cloud\auth-service\auth-domain\src\test\java) 仅有 4 个测试文件：

- [SecondaryAuthSessionTest](file:///d:\WorkSpace\Trae\multiple-module-spring-cloud\auth-service\auth-domain\src\test\java\com\pension\permission\domain\channel\aggregate\SecondaryAuthSessionTest.java)（channel 域聚合根）
- [VerificationCodeTest](file:///d:\WorkSpace\Trae\multiple-module-spring-cloud\auth-service\auth-domain\src\test\java\com\pension\permission\domain\channel\valueobject\VerificationCodeTest.java)
- [PermissionSnapshotTest](file:///d:\WorkSpace\Trae\multiple-module-spring-cloud\auth-service\auth-domain\src\test\java\com\pension\permission\domain\channel\valueobject\PermissionSnapshotTest.java)
- [SmokeTest](file:///d:\WorkSpace\Trae\multiple-module-spring-cloud\auth-service\auth-domain\src\test\java\com\pension\permission\domain\SmokeTest.java)（冒烟测试）

**核心缺陷**：
1. **权限判定无测试**：[AuthorizationEngine](file:///d:\WorkSpace\Trae\multiple-module-spring-cloud\auth-service\auth-domain\src\main\java\com\pension\permission\domain\authorization\service\AuthorizationEngine.java) 两层 AND 判定逻辑、[EffectResolver](file:///d:\WorkSpace\Trae\multiple-module-spring-cloud\auth-service\auth-domain\src\main\java\com\pension\permission\domain\authorization\service\EffectResolver.java) DENY 优先策略、[ScopeMatcher](file:///d:\WorkSpace\Trae\multiple-module-spring-cloud\auth-service\auth-domain\src\main\java\com\pension\permission\domain\authorization\service\ScopeMatcher.java) 范围匹配全部裸奔。
2. **聚合根状态机无测试**：Grant、AgentIdentityAssignment、Session、RoleTemplate、UserAggregate 等聚合根的业务规则和状态流转未被验证。
3. **凭证安全无测试**：PasswordCredential、UKeyCredential 的密码加密、轮换、撤销等安全相关逻辑无保护。
4. **Mockito 未引入**：[auth-domain/pom.xml](file:///d:\WorkSpace\Trae\multiple-module-spring-cloud\auth-service\auth-domain\pom.xml) 仅依赖 junit + assertj，复杂领域服务（如 AuthorizationEngine 依赖 3 个 SPI）难以纯手写桩实现测试。

### 1.2 设计目标

- 为 auth-domain 层核心类（约 35 个）补充单元测试，覆盖主要业务分支和状态流转
- 沿用项目现有测试风格：`@DisplayName`（中文）+ `@Nested` 分组 + AssertJ 断言 + given/when/then 结构
- 引入 Mockito 简化 SPI 依赖模拟
- 按子域组织 Fixture，提高测试数据复用性
- 分 4 个批次推进，每批可独立验证和提交，适合 subagent-driven 并行实施

### 1.3 非目标

- 不覆盖 application 层和 infrastructure 层（后续迭代）
- 不追求 100% 行覆盖率，聚焦核心业务逻辑分支
- 不重构被测代码（若发现设计问题记录为技术债，不在本批次处理）
- 不测试纯枚举、错误码常量、Repository 接口（无逻辑）、product 域快照类（纯数据持有）

## 二、测试范围

### 2.1 子域划分与覆盖矩阵

auth-domain 共 7 个子域，测试覆盖优先级如下：

| 子域 | 聚合根 | 领域服务 | 值对象 | 测试优先级 | 批次 |
|------|--------|----------|--------|-----------|------|
| authorization | Grant | AuthorizationEngine、EffectResolver、ScopeMatcher | subject 系列、Permission、ScopeRule、ActionCode、BusinessCode | P0 | 批次1 |
| assignment | AgentIdentityAssignment | EffectivePermissionService、GrantProvisioningService、PlanReachabilityService | RoleVisibilityScope | P0 | 批次2 |
| channel | Session（已有 SecondaryAuthSession） | DefaultSecondaryAuthService、IdentityResolutionService、3 个 PlanSelectionStrategy | EffectiveIdentity、SelectablePlanScope | P0 | 批次3 |
| credential | PasswordCredential、UKeyCredential | CredentialAuthenticator | owner 系列 | P1 | 批次4 |
| role | RoleTemplate | RoleTemplateResolver、RoleVisibilityResolver | - | P1 | 批次4 |
| user | UserAggregate | AuthenticationProvider | - | P1 | 批次4 |
| product | - | - | - | 不测 | - |

### 2.2 不测试的类

- **enumeration 包**：`Effect`、`GrantStatus`、`GrantType`、`AssignmentStatus`、`SessionStatus`、`SecondaryAuthStatus`、`CredentialStatus`、`CredentialType`、`UserStatus`、`UserType`、`RoleTemplateStatus` 等（纯枚举，无逻辑）
- **errorcode 包**：`SecondaryAuthErrorCode`、`CredentialError`、`UserError`、`RoleError` 等（错误码常量定义）
- **repository 包**：所有 Repository 接口（无实现逻辑）
- **event 包**：所有领域事件（record 类型，仅 `of()` 工厂方法，逻辑极简）
- **product 包**：`PlanSnapshot`、`CustomerSnapshot`、`ProductSnapshot`（纯数据持有）、`ProductGateway`（接口）、`ProductError`
- **spi 包**：`LoginTokenService`、`VerificationCodeHasher`、`GrantActivationPolicy`、`PlanMembershipLookup`（接口定义，实现由 infrastructure 提供）

### 2.3 测试类清单（按批次）

**批次1 - authorization 域（约 8 个测试类）**：
- `GrantTest`
- `AuthorizationEngineTest`
- `EffectResolverTest`
- `ScopeMatcherTest`
- `PermissionTest`
- `ScopeRuleTest`
- `subject/GrantSubjectTest`（覆盖 CapabilitySubject、PlanAllMembersSubject、PlanRoleSubject、UserListSubject 的 `covers` 逻辑）
- `ActionCodeTest` / `BusinessCodeTest`（若校验逻辑存在）

**批次2 - assignment 域（约 4 个测试类）**：
- `AgentIdentityAssignmentTest`
- `EffectivePermissionServiceTest`
- `GrantProvisioningServiceTest`
- `PlanReachabilityServiceTest`

**批次3 - channel 域补全（约 6 个测试类）**：
- `SessionTest`
- `DefaultSecondaryAuthServiceTest`
- `IdentityResolutionServiceTest`
- `BranchPlanSelectionStrategyTest`
- `HqPlanSelectionStrategyTest`
- `OnlinePlanSelectionStrategyTest`

**批次4 - credential + role + user 域（约 8 个测试类）**：
- `PasswordCredentialTest`
- `UKeyCredentialTest`
- `CredentialAuthenticatorTest`
- `RoleTemplateTest`
- `RoleTemplateResolverTest`
- `RoleVisibilityResolverTest`
- `UserAggregateTest` / `AuthenticationProviderTest`

## 三、依赖与基础设施

### 3.1 pom.xml 依赖补充

在 [auth-domain/pom.xml](file:///d:\WorkSpace\Trae\multiple-module-spring-cloud\auth-service\auth-domain\pom.xml) 的 `<dependencies>` 中添加：

```xml
<dependency>
  <groupId>org.mockito</groupId>
  <artifactId>mockito-core</artifactId>
  <scope>test</scope>
</dependency>
```

**理由**：
- `AuthorizationEngine` 依赖 `ProductGateway`、`GrantRepository`、`PlanMembershipLookup` 三个 SPI
- `EffectivePermissionService`、`GrantProvisioningService` 依赖 `AssignmentRepository`、`GrantRepository` 等
- 手写桩实现（参考现有 [SecondaryAuthSessionTest](file:///d:\WorkSpace\Trae\multiple-module-spring-cloud\auth-service\auth-domain\src\test\java\com\pension\permission\domain\channel\aggregate\SecondaryAuthSessionTest.java) 中的 `acceptHasher`/`rejectHasher`）在单依赖场景可行，但多依赖场景下 Mockito 更简洁、可读性更好

不引入 `mockito-junit-jupiter`，沿用 JUnit 5 的 `Mockito.mockStatic()` / `Mockito.mock()` 静态方法即可。

### 3.2 Fixture 组织

按子域创建 Fixture 类，集中管理测试数据和辅助构造方法，路径：`auth-service/auth-domain/src/test/java/com/pension/permission/domain/fixture/`

| Fixture 类 | 职责 |
|-----------|------|
| `AuthorizationFixtures` | Grant、Permission、ScopeRule、subject 系列、BusinessCode/ActionCode 构造 |
| `AssignmentFixtures` | AgentIdentityAssignment、RoleVisibilityScope 构造 |
| `ChannelFixtures` | Session、EffectiveIdentity、SelectablePlanScope 构造（复用现有 SecondaryAuthSessionTest 的 helper） |
| `CredentialFixtures` | PasswordCredential、UKeyCredential、owner 系列构造 |
| `RoleFixtures` | RoleTemplate 构造 |
| `UserFixtures` | UserAggregate 构造 |

**Fixture 设计原则**：
- 暴露 `public static` 工厂方法，方法名体现业务语义（如 `pendingGrant()`、`approvedGrant()`）
- 每个方法返回一个完整的可测试对象，调用方可链式修改特定字段
- 不持有可变状态（纯静态方法 + 不可变对象）

## 四、测试风格规范

### 4.1 类级结构

```java
@DisplayName("AuthorizationEngine 权限判定测试")
class AuthorizationEngineTest {

  private final GrantRepository grantRepository = mock(GrantRepository.class);
  private final ProductGateway productGateway = mock(ProductGateway.class);
  private final PlanMembershipLookup membershipLookup = mock(PlanMembershipLookup.class);
  private final AuthorizationEngine engine =
    new AuthorizationEngine(productGateway, grantRepository, membershipLookup);

  @Nested
  @DisplayName("能力层权限判定 checkPlanCapability")
  class CheckPlanCapabilityTest {
    @Test
    @DisplayName("命中 ALLOW 授权时应返回 true")
    void shouldReturnTrueWhenAllowGrantMatched() {
      // given
      var planId = PlanNo.of("PLAN-001");
      var business = BusinessCode.of("BIZ-001");
      var at = LocalDateTime.now();
      when(productGateway.requirePlan(planId)).thenReturn(AuthorizationFixtures.planSnapshot(planId));
      when(grantRepository.findActiveCapabilityGrants(at)).thenReturn(List.of(AuthorizationFixtures.allowGrant()));

      // when
      boolean result = engine.checkPlanCapability(planId, business, at);

      // then
      assertThat(result).isTrue();
    }
  }
}
```

### 4.2 断言规范

- **业务结果断言**：`assertThat(actual).isEqualTo(expected)` / `isTrue()` / `isFalse()`
- **异常断言**：`assertThatThrownBy(() -> ...).isInstanceOf(DomainException.class).extracting(e -> ((DomainException) e).errorDefinition().code()).isEqualTo("SERVICE.AUTH.xxxx")`
- **领域事件断言**：`assertThat(aggregate.domainEvents()).anyMatch(e -> e instanceof GrantApproved)`
- **状态断言**：`assertThat(grant.status()).isEqualTo(GrantStatus.APPROVED)`

### 4.3 测试方法命名

- 方法名：`shouldXxxWhenYyy`（英文，描述行为）
- `@DisplayName`：中文，描述业务场景
- `@Nested` 类名：英文，描述测试分组

## 五、实施批次

### 5.1 批次1 - authorization 域

**前置依赖**：完成 pom.xml 依赖补充 + 创建 `AuthorizationFixtures`

**实施顺序**（按风险从高到低）：
1. `EffectResolverTest` — DENY 优先、无授权时默认策略（逻辑最纯粹，无 SPI 依赖）
2. `ScopeMatcherTest` — 范围匹配规则（依赖 ProductGateway，可 mock）
3. `GrantTest` — 状态机流转、权限授予判定、撤销逻辑
4. `AuthorizationEngineTest` — 两层 AND 判定、能力层失败短路、主体层命中放行
5. `PermissionTest` / `ScopeRuleTest` / `subject/GrantSubjectTest` — 值对象校验逻辑

**验证**：`mvn -f auth-service/auth-domain/pom.xml test`

**提交**：`test(auth-domain): 补充 authorization 域单元测试`

### 5.2 批次2 - assignment 域

**前置依赖**：创建 `AssignmentFixtures`

**实施顺序**：
1. `AgentIdentityAssignmentTest` — 身份分配生命周期、角色变更、停用
2. `EffectivePermissionServiceTest` — 有效权限计算
3. `GrantProvisioningServiceTest` — 授权供应流程
4. `PlanReachabilityServiceTest` — 计划可达性判定

**验证**：同上

**提交**：`test(auth-domain): 补充 assignment 域单元测试`

### 5.3 批次3 - channel 域补全

**前置依赖**：创建 `ChannelFixtures`（整合现有 SecondaryAuthSessionTest 的 helper）

**实施顺序**：
1. `SessionTest` — 会话状态机、身份切换、计划选择、关闭/过期
2. `IdentityResolutionServiceTest` — 身份解析逻辑
3. 3 个 `PlanSelectionStrategy` 测试 — 网点/总部/互联网渠道的计划选择策略
4. `DefaultSecondaryAuthServiceTest` — 二次授权编排（与现有 SecondaryAuthSessionTest 互补，聚焦服务编排而非聚合根）

**验证**：同上

**提交**：`test(auth-domain): 补充 channel 域单元测试`

### 5.4 批次4 - credential + role + user 域

**前置依赖**：创建 `CredentialFixtures`、`RoleFixtures`、`UserFixtures`

**实施顺序**：
1. `PasswordCredentialTest` — 密码加密、验证、轮换、撤销
2. `UKeyCredentialTest` — UKey 签名、轮换、撤销
3. `CredentialAuthenticatorTest` — 凭证认证逻辑
4. `RoleTemplateTest` — 角色模板、可见性
5. `RoleTemplateResolverTest` / `RoleVisibilityResolverTest` — 角色解析
6. `UserAggregateTest` — 用户激活、冻结、停用
7. `AuthenticationProviderTest` — 认证提供者编排

**验证**：同上

**提交**：`test(auth-domain): 补充 credential/role/user 域单元测试`

## 六、验收标准

- [ ] `mvn -f auth-service/auth-domain/pom.xml test` 全绿
- [ ] 覆盖 7 个子域的核心类（约 25-30 个测试类）
- [ ] 聚合根状态机关键分支全覆盖（创建、激活、冻结、撤销、过期等）
- [ ] `AuthorizationEngine` 两层 AND 判定、DENY 优先短路、能力层失败等核心场景覆盖
- [ ] 错误码断言精确到 `SERVICE.AUTH.XXXX` 格式
- [ ] 每个批次独立提交，提交信息符合 Conventional Commits 规范
- [ ] Fixture 类无重复构造代码，测试类内 helper 最小化

## 七、风险与缓解

| 风险 | 缓解措施 |
|------|---------|
| 部分类可能因设计问题难以测试（如构造函数可见性、依赖注入不友好） | 记录为技术债，不在本批次重构；测试中使用反射或包可见性访问，必要时跳过并在 spec 中标注 |
| SPI 依赖较多，Mock 桩代码冗长 | 通过 Fixture 类封装 Mock 构造，避免每个测试类重复 setup |
| 状态机分支组合爆炸 | 按"正常路径 + 每个非法状态一次"原则覆盖，不追求全组合 |
| 现有 SecondaryAuthSessionTest 的 helper 与新 Fixture 重复 | 批次3 中将现有 helper 迁移到 `ChannelFixtures`，现有测试改为引用 Fixture |

## 八、后续规划

本批次完成后，后续迭代方向：
- application 层测试（ApplicationService 编排逻辑）
- infrastructure 层测试（RepositoryImpl、Converter、SPI 实现）
- 集成测试（结合 H2 内存数据库）
- 测试覆盖率报告接入（jacoco-maven-plugin）
