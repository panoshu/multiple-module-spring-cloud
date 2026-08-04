# iam-service 用户与权限服务实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:
> executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按设计文档 `docs/superpowers/specs/2026-07-26-iam-service-design.md` 从零实现 iam-service 用户与权限服务,包含
6 聚合根、13 张表、sa-token 多 StpLogic 集成、防腐层 Gateway (Mock 实现)与 demo-gateway 网关集成。

**Architecture:** DDD + 六边形架构 7 层模块 (types → domain → api → application → adapter → infrastructure → starter)
。两个限界上下文:authentication (认证)与 authorization (授权)。sa-token 通过三套 StpLogic 实现多渠道 Token 隔离;权限计算结果缓存在
sa-token Token-Session 中。防腐层 Gateway 接口定义在领域层,Mock 实现在基础设施层,等外部接口就绪后替换。

**Tech Stack:** JDK 25 (--enable-preview)、Spring Boot 3.5.14、Spring Cloud 2025.0.2、MyBatis-Flex
1.11.5、PostgreSQL/MySQL、Redisson 4.3.1、MapStruct 1.6.3、Lombok 1.18.46、sa-token 1.45.0、RocketMQ 2.3.6。

## Global Constraints

- **Java 版本**: JDK 25,编译参数必须包含 `--enable-preview`
- **Spring Boot**: 3.5.14 / **Spring Cloud**: 2025.0.2 / **Spring Cloud Alibaba**: 2025.0.0.0
- **ORM**: 仅使用 `mybatis-flex-spring-boot3-starter` 1.11.5,禁止引入 spring-jdbc 或 spring-boot-starter-jdbc
- **数据库**: PostgreSQL 首选 (runtime 依赖 `org.postgresql:postgresql`),MySQL 备选;测试使用 H2
- **sa-token**: 1.45.0,使用 `sa-token-spring-boot3-starter`(iam-service)与 `sa-token-reactor-spring-boot3-starter`
  (demo-gateway)+ `sa-token-redis-jackson`
- **错误码格式**: `SERVICE.IAM.XXXX`(层级字符串),消息使用纯文本,禁止 `{}` 占位符与方括号前缀
- **时间戳管理**: 业务数据时间由应用层管理 (`Entity` 基类构造函数 + `markUpdated()`),DO 禁止使用
  `@Column(onInsertValue/onUpdateValue)`
- **API 接口**: 必须使用 `@HttpExchange` + `@GetExchange`/`@PostExchange`,返回 `ApiResult<T>`,定义在 iam-api 模块
- **DTO 转换**: 必须通过 MapStruct Converter/Mapper,禁止在 Controller 中直接转换
- **领域层约束**: 禁止 Spring 注解、数据库框架注解、JSON 序列化框架;仅允许依赖 lombok
- **提交规范**: Conventional Commits,格式 `<type>(<scope>): <subject>`,scope 使用 `iam-types`/`iam-domain`/`iam-api`/
  `iam-application`/`iam-adapter`/`iam-infrastructure`/`iam-starter`/`iam-service`
- **sa-token 工具类位置**: 必须放在 `iam-adapter/security/` 包,禁止放在 iam-api (api 层不能依赖 sa-token)
- **测试数据库**: 测试使用 H2 内存数据库,不使用 MySQL

---

## File Structure

### 模块布局

```
iam-service/                                     # 父模块(pom packaging)
├── pom.xml                                       # 父 POM,声明 7 个子模块 + dependencyManagement
├── iam-types/                                    # ID 类型定义
│   ├── pom.xml
│   └── src/main/java/com/example/iam/types/
│       ├── UserId.java                           # 统一用户 ID(三渠道共用)
│       ├── CredentialId.java
│       ├── SecondaryAuthSessionId.java
│       ├── LoginLogId.java
│       ├── PermissionRuleId.java
│       ├── PlanDelegationId.java
│       ├── BusinessDefinitionId.java
│       ├── RouteRuleId.java
│       └── package-info.java
├── iam-domain/                                   # 领域层
│   ├── pom.xml
│   └── src/main/java/com/example/iam/domain/
│       ├── authentication/                       # 认证限界上下文
│       │   ├── aggregate/
│       │   │   ├── root/
│       │   │   │   ├── User.java                 # 统一 User 聚合根(三渠道)
│       │   │   │   ├── Credential.java
│       │   │   │   ├── SecondaryAuthSession.java
│       │   │   │   └── LoginLog.java
│       │   │   ├── entity/
│       │   │   │   ├── UserProfile.java         # 渠道专属档案(实体)
│       │   │   │   └── LoginFailureRecord.java
│       │   │   └── valueobject/
│       │   │       ├── ChannelType.java          # INTERNET/HQ/BRANCH
│       │   │       ├── UserStatus.java           # ACTIVE/DISABLED/LOCKED
│       │   │       ├── CredentialType.java       # PASSWORD/UKEY/DYNAMIC_TOKEN
│       │   │       ├── CredentialStatus.java     # ACTIVE/EXPIRED/REVOKED
│       │   │       └── SecondaryAuthStatus.java # PENDING/AUTHORIZED/EXPIRED/REVOKED/CLOSED/REJECTED
│       │   ├── event/
│       │   │   ├── UserCreatedEvent.java
│       │   │   ├── UserDisabledEvent.java
│       │   │   ├── UserEnabledEvent.java
│       │   │   ├── UserLoginSucceededEvent.java
│       │   │   ├── UserLoginFailedEvent.java
│       │   │   ├── CredentialCreatedEvent.java
│       │   │   ├── CredentialChangedEvent.java
│       │   │   ├── CredentialExpiredEvent.java
│       │   │   ├── SecondaryAuthInitiatedEvent.java
│       │   │   ├── SecondaryAuthCompletedEvent.java
│       │   │   ├── SecondaryAuthRevokedEvent.java
│       │   │   └── SecondaryAuthExpiredEvent.java
│       │   ├── repository/
│       │   │   ├── UserRepository.java
│       │   │   ├── CredentialRepository.java
│       │   │   ├── SecondaryAuthSessionRepository.java
│       │   │   └── LoginLogRepository.java
│       │   ├── service/
│       │   │   └── (认证域无核心领域服务,SPI 在 strategy 包)
│       │   ├── strategy/                         # SPI 扩展点
│       │   │   ├── CredentialValidator.java     # 凭据验证策略接口
│       │   │   ├── PasswordCredentialValidator.java
│       │   │   ├── SecondaryAuthStrategy.java    # 二次授权策略接口
│       │   │   └── DefaultSecondaryAuthStrategy.java
│       │   └── errorcode/
│       │       └── IamAuthErrorCode.java         # 35 个错误码
│       ├── authorization/                        # 授权限界上下文
│       │   ├── aggregate/
│       │   │   ├── root/
│       │   │   │   ├── PermissionRule.java
│       │   │   │   └── PlanDelegation.java
│       │   │   ├── entity/
│       │   │   │   ├── BusinessDefinition.java
│       │   │   │   ├── BusinessAction.java
│       │   │   │   ├── RouteRule.java
│       │   │   │   ├── DelegationOperator.java
│       │   │   │   └── DelegationPermission.java
│       │   │   └── valueobject/
│       │   │       ├── SubjectType.java         # CUSTOMER/OPERATION_MODE/PRODUCT/PLAN/ACCOUNT_MANAGER
│       │   │       ├── OverrideMode.java        # ADD/REMOVE
│       │   │       ├── Action.java              # HANDLE/QUERY/AUDIT
│       │   │       ├── OperationMode.java       # SINGLE_TRUSTEE/...
│       │   │       ├── DelegationType.java      # ALL_OPERATORS/SPECIFIC_OPERATORS
│       │   │       ├── DelegationStatus.java    # ACTIVE/REVOKED/EXPIRED
│       │   │       ├── RuleStatus.java          # ACTIVE/DISABLED
│       │   │       ├── PermissionCode.java      # 权限码值对象
│       │   │       ├── PermissionSnapshot.java  # 权限快照(含 userId/planId/permissions/calculatedAt)
│       │   │       ├── PlanMetadata.java        # 计划元数据(防腐层值对象)
│       │   │       ├── CustomerInfo.java
│       │   │       ├── ProductInfo.java
│       │   │       └── OrganizationInfo.java
│       │   ├── event/
│       │   │   ├── PermissionRuleCreatedEvent.java
│       │   │   ├── PermissionRuleDisabledEvent.java
│       │   │   ├── PermissionRuleEnabledEvent.java
│       │   │   ├── PlanDelegationCreatedEvent.java
│       │   │   ├── PlanDelegationRevokedEvent.java
│       │   │   └── PlanDelegationActivatedEvent.java
│       │   ├── repository/
│       │   │   ├── PermissionRuleRepository.java
│       │   │   ├── PlanDelegationRepository.java
│       │   │   ├── BusinessDefinitionRepository.java
│       │   │   └── RouteRuleRepository.java
│       │   ├── service/
│       │   │   ├── PermissionResolver.java          # 权限计算接口
│       │   │   ├── DefaultPermissionResolver.java   # 默认实现
│       │   │   ├── BusinessRegistryService.java     # 业务注册表领域服务
│       │   │   └── PermissionCombinationStrategy.java # 组合策略 SPI
│       │   ├── strategy/
│       │   │   └── PriorityOverrideStrategy.java   # 默认组合策略实现
│       │   ├── gateway/                              # 防腐层接口(领域层)
│       │   │   ├── PlanMetadataGateway.java
│       │   │   ├── CustomerGateway.java
│       │   │   ├── ProductGateway.java
│       │   │   └── OrganizationGateway.java
│       │   └── errorcode/
│       │       └── IamAuthzErrorCode.java           # 38 个错误码
│       └── system/
│           └── errorcode/
│               └── IamSystemErrorCode.java           # 28 个错误码
├── iam-api/                                     # API 接口层
│   ├── pom.xml
│   └── src/main/java/com/example/iam/api/
│       ├── auth/                                # 认证相关 API
│       │   ├── InternetAuthApi.java
│       │   ├── HqAuthApi.java
│       │   └── BranchAuthApi.java
│       ├── user/                                # 用户管理 API
│       │   ├── UserManagementApi.java
│       │   └── CredentialManagementApi.java
│       ├── plan/                                # 计划选择 API
│       │   └── PlanSelectionApi.java
│       ├── permission/                          # 权限规则 API
│       │   ├── PermissionRuleApi.java
│       │   └── PlanDelegationApi.java
│       ├── secondary/                           # 二次授权 API
│       │   └── SecondaryAuthApi.java
│       ├── dto/                                 # DTO 定义
│       │   ├── auth/
│       │   ├── user/
│       │   ├── plan/
│       │   ├── permission/
│       │   └── secondary/
│       ├── command/                             # Command 对象
│       ├── query/                               # Query 对象
│       └── integration_event/                   # 集成事件 DTO
│           ├── UserDisabledIntegrationEvent.java
│           ├── SecondaryAuthCompletedIntegrationEvent.java
│           ├── PermissionRuleChangedIntegrationEvent.java
│           └── PlanDelegationChangedIntegrationEvent.java
├── iam-application/                             # 应用服务层
│   ├── pom.xml
│   └── src/main/java/com/example/iam/application/
│       ├── service/
│       │   ├── AuthApplicationService.java          # 三渠道登录编排
│       │   ├── UserManagementApplicationService.java
│       │   ├── CredentialApplicationService.java
│       │   ├── PlanSelectionApplicationService.java
│       │   ├── PermissionRuleApplicationService.java
│       │   ├── PlanDelegationApplicationService.java
│       │   └── SecondaryAuthApplicationService.java
│       └── listener/                            # 领域事件订阅者
│           └── IamDomainEventListener.java
├── iam-adapter/                                 # 适配器层
│   ├── pom.xml
│   └── src/main/java/com/example/iam/adapter/
│       ├── controller/
│       │   ├── InternetAuthController.java
│       │   ├── HqAuthController.java
│       │   ├── BranchAuthController.java
│       │   ├── UserManagementController.java
│       │   ├── CredentialManagementController.java
│       │   ├── PlanSelectionController.java
│       │   ├── PermissionRuleController.java
│       │   ├── PlanDelegationController.java
│       │   └── SecondaryAuthController.java
│       ├── converter/                           # MapStruct DTO Converter
│       │   ├── AuthConverter.java
│       │   ├── UserConverter.java
│       │   ├── PlanConverter.java
│       │   ├── PermissionConverter.java
│       │   └── SecondaryAuthConverter.java
│       └── security/                            # sa-token 集成(关键!)
│           ├── StpInternetUtil.java
│           ├── StpHqUtil.java
│           ├── StpBranchUtil.java
│           ├── IamStpInterfaceImpl.java         # 实现 StpInterface
│           ├── ChannelContext.java              # 渠道上下文(含 checkPermission/checkRole)
│           ├── ChannelContextProvider.java
│           └── SaTokenConfiguration.java        # 多 StpLogic 注册
├── iam-infrastructure/                          # 基础设施层
│   ├── pom.xml
│   ├── src/main/java/com/example/iam/infrastructure/
│   │   ├── entity/                              # DO 实体(13 张表)
│   │   │   ├── UserDO.java
│   │   │   ├── UserProfileDO.java
│   │   │   ├── CredentialDO.java
│   │   │   ├── SecondaryAuthSessionDO.java
│   │   │   ├── LoginLogDO.java
│   │   │   ├── LoginFailureRecordDO.java
│   │   │   ├── PermissionRuleDO.java
│   │   │   ├── PlanDelegationDO.java
│   │   │   ├── DelegationOperatorDO.java
│   │   │   ├── DelegationPermissionDO.java
│   │   │   ├── BusinessDefinitionDO.java
│   │   │   ├── BusinessActionDO.java
│   │   │   └── RouteRuleDO.java
│   │   ├── mapper/                              # MyBatis-Flex Mapper(13 个)
│   │   ├── converter/                           # Entity Converter(DO ↔ 领域对象)
│   │   │   ├── UserConverter.java
│   │   │   ├── CredentialConverter.java
│   │   │   ├── SecondaryAuthSessionConverter.java
│   │   │   ├── LoginLogConverter.java
│   │   │   ├── PermissionRuleConverter.java
│   │   │   ├── PlanDelegationConverter.java
│   │   │   ├── BusinessDefinitionConverter.java
│   │   │   └── RouteRuleConverter.java
│   │   ├── repository/                          # Repository 实现(8 个)
│   │   │   ├── UserRepositoryImpl.java
│   │   │   ├── CredentialRepositoryImpl.java
│   │   │   ├── SecondaryAuthSessionRepositoryImpl.java
│   │   │   ├── LoginLogRepositoryImpl.java
│   │   │   ├── PermissionRuleRepositoryImpl.java
│   │   │   ├── PlanDelegationRepositoryImpl.java
│   │   │   ├── BusinessDefinitionRepositoryImpl.java
│   │   │   └── RouteRuleRepositoryImpl.java
│   │   ├── gateway/                             # 防腐层 Mock 实现
│   │   │   ├── PlanMetadataGatewayImpl.java
│   │   │   ├── CustomerGatewayImpl.java
│   │   │   ├── ProductGatewayImpl.java
│   │   │   ├── OrganizationGatewayImpl.java
│   │   │   ├── external/                        # 外部 API 接口(@HttpExchange)
│   │   │   │   ├── ExternalPlanApi.java
│   │   │   │   ├── ExternalCustomerApi.java
│   │   │   │   └── ExternalProductApi.java
│   │   │   ├── converter/                       # 外部 DTO 转领域值对象
│   │   │   │   ├── PlanMetadataConverter.java
│   │   │   │   ├── CustomerInfoConverter.java
│   │   │   │   └── ProductInfoConverter.java
│   │   │   └── dto/                             # 外部响应 DTO
│   │   │       ├── ExternalPlanResponse.java
│   │   │       ├── ExternalCustomerResponse.java
│   │   │       └── ExternalProductResponse.java
│   │   └── configuration/
│   │       ├── IamMyBatisFlexConfiguration.java
│   │       └── IamDomainServiceConfiguration.java
│   └── src/main/resources/
│       ├── schema-pg.sql                        # PostgreSQL DDL(13 表)
│       └── schema-mysql.sql                     # MySQL DDL(13 表)
└── iam-starter/                                 # 启动模块
    ├── pom.xml
    └── src/main/
        ├── java/com/example/iam/IamApplication.java
        └── resources/
            ├── application.yml
            └── application-local.yml
```

### demo-gateway 修改

```
demo-gateway/src/main/java/com/example/gateway/
├── security/                                    # 新增:sa-token 网关集成
│   ├── ChannelAwareSaRouter.java               # 渠道识别 + 登录校验
│   ├── RouteRuleLoader.java                     # 路由规则加载器
│   ├── RouteRule.java                           # 路由规则值对象
│   └── SaTokenGatewayConfiguration.java        # SaReactorFilter 配置
└── (现有文件保持不变)
```

### 父 pom.xml 修改

- `<modules>` 中新增 `iam-service`
- `<properties>` 中新增 `<sa-token.version>1.45.0</sa-token.version>`
- `<dependencyManagement>` 中新增 iam-* 7 个模块 + sa-token 3 个依赖

---

## Task 编排总览

| Phase | Task    | 范围                                                                   | 依赖           |
|-------|---------|------------------------------------------------------------------------|----------------|
| 1     | Task 1  | 项目骨架 + 父 POM 集成                                                 | 无             |
| 1     | Task 2  | iam-types 层 ID 类型                                                   | Task 1         |
| 2     | Task 3  | 错误码三件套(IamAuth/Authz/System)                                     | Task 1         |
| 2     | Task 4  | authentication 域值对象                                                | Task 2, Task 3 |
| 2     | Task 5  | User 聚合根 + UserProfile 实体                                         | Task 4         |
| 2     | Task 6  | Credential 聚合根 + CredentialValidator SPI                            | Task 4         |
| 2     | Task 7  | SecondaryAuthSession 聚合根 + SPI                                      | Task 4         |
| 2     | Task 8  | LoginLog 聚合根 + LoginFailureRecord                                   | Task 4         |
| 2     | Task 9  | authentication 域事件 + Repository 接口                                | Task 5-8       |
| 3     | Task 10 | authorization 域值对象 + 防腐层 Gateway 接口                           | Task 2, Task 3 |
| 3     | Task 11 | PermissionRule 聚合根 + Repository 接口                                | Task 10        |
| 3     | Task 12 | PlanDelegation 聚合根 + 子实体 + Repository 接口                       | Task 10        |
| 3     | Task 13 | BusinessDefinition + RouteRule 实体 + Repository 接口                  | Task 10        |
| 3     | Task 14 | PermissionResolver + PermissionCombinationStrategy SPI                 | Task 10-13     |
| 3     | Task 15 | authorization 域事件                                                   | Task 11-13     |
| 4     | Task 16 | iam-api 层 DTO/Command/Query + API 接口                                | Task 9, 15     |
| 4     | Task 17 | iam-application 应用服务 + 事件订阅者                                  | Task 16        |
| 5     | Task 18 | iam-infrastructure DO + Mapper(13 表)                                  | Task 17        |
| 5     | Task 19 | iam-infrastructure Entity Converter + Repository 实现                  | Task 18        |
| 5     | Task 20 | iam-infrastructure 防腐层 Gateway Mock 实现                            | Task 19        |
| 5     | Task 21 | schema-pg.sql + schema-mysql.sql 双 DDL                                | Task 18        |
| 6     | Task 22 | iam-adapter sa-token 集成(StpUtil × 3 + ChannelContext + StpInterface) | Task 17        |
| 6     | Task 23 | iam-adapter Controller + Converter                                     | Task 22        |
| 7     | Task 24 | iam-starter 启动类 + 配置文件                                          | Task 23        |
| 7     | Task 25 | demo-gateway sa-token 集成                                             | Task 24        |
| 7     | Task 26 | 错误码规范文档更新 + 全量构建验证                                      | Task 25        |

---

## Phase 1: 项目骨架与父 POM 集成

### Task 1: 项目骨架与父 POM 集成

**Files:**

- Create: `iam-service/pom.xml`
- Create: `iam-service/iam-types/pom.xml`
- Create: `iam-service/iam-domain/pom.xml`
- Create: `iam-service/iam-api/pom.xml`
- Create: `iam-service/iam-application/pom.xml`
- Create: `iam-service/iam-adapter/pom.xml`
- Create: `iam-service/iam-infrastructure/pom.xml`
- Create: `iam-service/iam-starter/pom.xml`
- Modify: `pom.xml`(根 POM)
- Modify: `.trae/rules/08-错误码规范.md`

**Interfaces:**

- Produces: 7 个 iam-* Maven 模块可被引用;根 POM `<modules>` 包含 `iam-service`

- [ ] **Step 1: 创建 iam-service 父 POM**

`iam-service/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.example</groupId>
    <artifactId>multiple-module-spring-cloud</artifactId>
    <version>1.0-SNAPSHOT</version>
  </parent>

  <artifactId>iam-service</artifactId>
  <packaging>pom</packaging>

  <modules>
    <module>iam-types</module>
    <module>iam-domain</module>
    <module>iam-api</module>
    <module>iam-application</module>
    <module>iam-adapter</module>
    <module>iam-infrastructure</module>
    <module>iam-starter</module>
  </modules>

  <dependencyManagement>
    <dependencies>
      <dependency><groupId>com.example</groupId><artifactId>iam-types</artifactId><version>${project.version}</version></dependency>
      <dependency><groupId>com.example</groupId><artifactId>iam-domain</artifactId><version>${project.version}</version></dependency>
      <dependency><groupId>com.example</groupId><artifactId>iam-api</artifactId><version>${project.version}</version></dependency>
      <dependency><groupId>com.example</groupId><artifactId>iam-application</artifactId><version>${project.version}</version></dependency>
      <dependency><groupId>com.example</groupId><artifactId>iam-adapter</artifactId><version>${project.version}</version></dependency>
      <dependency><groupId>com.example</groupId><artifactId>iam-infrastructure</artifactId><version>${project.version}</version></dependency>
      <dependency><groupId>com.example</groupId><artifactId>iam-starter</artifactId><version>${project.version}</version></dependency>
    </dependencies>
  </dependencyManagement>
</project>
```

- [ ] **Step 2: 创建 iam-types/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.example</groupId>
    <artifactId>iam-service</artifactId>
    <version>1.0-SNAPSHOT</version>
  </parent>
  <artifactId>iam-types</artifactId>
  <dependencies>
    <dependency><groupId>com.example</groupId><artifactId>shared-types</artifactId></dependency>
  </dependencies>
</project>
```

- [ ] **Step 3: 创建 iam-domain/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.example</groupId>
    <artifactId>iam-service</artifactId>
    <version>1.0-SNAPSHOT</version>
  </parent>
  <artifactId>iam-domain</artifactId>
  <dependencies>
    <dependency><groupId>com.example</groupId><artifactId>shared-domain</artifactId></dependency>
    <dependency><groupId>com.example</groupId><artifactId>iam-types</artifactId></dependency>
    <dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><scope>provided</scope></dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 4: 创建 iam-api/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.example</groupId>
    <artifactId>iam-service</artifactId>
    <version>1.0-SNAPSHOT</version>
  </parent>
  <artifactId>iam-api</artifactId>
  <dependencies>
    <dependency><groupId>com.example</groupId><artifactId>shared-api</artifactId></dependency>
    <dependency><groupId>com.example</groupId><artifactId>iam-types</artifactId></dependency>
    <dependency><groupId>jakarta.validation</groupId><artifactId>jakarta.validation-api</artifactId></dependency>
    <dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><scope>provided</scope></dependency>
  </dependencies>
</project>
```

- [ ] **Step 5: 创建 iam-application/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.example</groupId>
    <artifactId>iam-service</artifactId>
    <version>1.0-SNAPSHOT</version>
  </parent>
  <artifactId>iam-application</artifactId>
  <dependencies>
    <dependency><groupId>com.example</groupId><artifactId>iam-api</artifactId></dependency>
    <dependency><groupId>com.example</groupId><artifactId>iam-domain</artifactId></dependency>
    <dependency><groupId>com.example</groupId><artifactId>shared-id-starter</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter</artifactId></dependency>
    <dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><scope>provided</scope></dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 6: 创建 iam-adapter/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.example</groupId>
    <artifactId>iam-service</artifactId>
    <version>1.0-SNAPSHOT</version>
  </parent>
  <artifactId>iam-adapter</artifactId>
  <dependencies>
    <dependency><groupId>com.example</groupId><artifactId>iam-api</artifactId></dependency>
    <dependency><groupId>com.example</groupId><artifactId>iam-application</artifactId></dependency>
    <!-- sa-token:仅在 adapter 层引入 -->
    <dependency><groupId>cn.dev33</groupId><artifactId>sa-token-spring-boot3-starter</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.mapstruct</groupId><artifactId>mapstruct</artifactId></dependency>
    <dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><scope>provided</scope></dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 7: 创建 iam-infrastructure/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.example</groupId>
    <artifactId>iam-service</artifactId>
    <version>1.0-SNAPSHOT</version>
  </parent>
  <artifactId>iam-infrastructure</artifactId>
  <dependencies>
    <dependency><groupId>com.example</groupId><artifactId>iam-domain</artifactId></dependency>
    <dependency><groupId>com.example</groupId><artifactId>shared-id-starter</artifactId></dependency>
    <dependency><groupId>com.example</groupId><artifactId>shared-cache-starter</artifactId></dependency>
    <dependency><groupId>com.mybatis-flex</groupId><artifactId>mybatis-flex-spring-boot3-starter</artifactId></dependency>
    <dependency><groupId>com.zaxxer</groupId><artifactId>HikariCP</artifactId></dependency>
    <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency>
    <dependency><groupId>org.mapstruct</groupId><artifactId>mapstruct</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
    <dependency>
      <groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId>
    </dependency>
    <dependency>
      <groupId>com.fasterxml.jackson.datatype</groupId>
      <artifactId>jackson-datatype-jsr310</artifactId>
    </dependency>
    <dependency>
      <groupId>io.github.danielliu1123</groupId>
      <artifactId>httpexchange-spring-boot-autoconfigure</artifactId>
    </dependency>
    <dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><scope>provided</scope></dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>com.h2database</groupId>
      <artifactId>h2</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 8: 创建 iam-starter/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.example</groupId>
    <artifactId>iam-service</artifactId>
    <version>1.0-SNAPSHOT</version>
  </parent>
  <artifactId>iam-starter</artifactId>
  <dependencies>
    <dependency><groupId>com.example</groupId><artifactId>iam-adapter</artifactId></dependency>
    <dependency><groupId>com.example</groupId><artifactId>iam-infrastructure</artifactId></dependency>
    <dependency><groupId>cn.dev33</groupId><artifactId>sa-token-redis-jackson</artifactId></dependency>
    <dependency>
      <groupId>org.redisson</groupId>
      <artifactId>redisson-spring-boot-starter</artifactId>
    </dependency>
    <dependency>
      <groupId>com.alibaba.cloud</groupId>
      <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>
  <build>
    <finalName>iam-service</finalName>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 9: 在根 pom.xml 的 `<modules>` 中添加 iam-service**

修改 `pom.xml` 第 14-22 行的 `<modules>` 部分,在 `<module>annuity-service</module>` 之后追加:

```xml
<module>iam-service</module>
```

- [ ] **Step 10: 在根 pom.xml 的 `<properties>` 中添加 sa-token 版本**

修改 `pom.xml` 的 `<properties>` 部分,在 `<kona-crypto.version>1.0.15</kona-crypto.version>` 之后追加:

```xml
<sa-token.version>1.45.0</sa-token.version>
```

- [ ] **Step 11: 在根 pom.xml 的 `<dependencyManagement>` 中添加 iam-* 模块和 sa-token 依赖**

在 `<!-- 2nd Dependencies-->` 部分末尾 (`annuity-starter` 之后)追加:

```xml
<!-- iam-service 模块 -->
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

在 `<!-- Other 3rd Dependencies -->` 部分末尾 (kona-crypto 之后)追加:

```xml
<!-- sa-token -->
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
```

- [ ] **Step 12: 在 08-错误码规范.md 中添加 IAM 模块缩写**

修改 `.trae/rules/08-错误码规范.md` 的 SERVICE 域模块缩写分配表,在 `| ANNUITY | annuity-service |` 之后追加:

```markdown
| IAM | iam-service |
```

- [ ] **Step 13: 清理 iam-service 下的旧文件**

执行命令清理掉之前残留的旧实现 (避免与新设计冲突):

```bash
git rm -rf iam-service/iam-application/src iam-service/iam-infrastructure/src iam-service/iam-domain/src
```

(此操作不影响已 commit 的历史代码,可通过 `git show HEAD:path` 查阅)

- [ ] **Step 14: 验证 Maven 构建**

Run: `mvn -pl iam-service -am validate -q`
Expected: BUILD SUCCESS,无错误输出

- [ ] **Step 15: 提交**

```bash
git add iam-service/pom.xml iam-service/iam-*/pom.xml pom.xml .trae/rules/08-错误码规范.md
git commit -m "build(iam-service): 初始化 iam-service 7 模块骨架与父 POM 集成

1. 创建 iam-service 父 POM 与 7 个子模块(types/domain/api/application/adapter/infrastructure/starter)
2. 根 POM 新增 iam-service 模块注册、sa-token 1.45.0 版本属性、iam-* 与 sa-token 依赖管理
3. 08-错误码规范.md 新增 IAM 模块缩写
4. 清理 iam-service 旧实现代码,后续按设计文档统一 User 模型重建"
```

---

### Task 2: iam-types 层 ID 类型

**Files:**

- Create: `iam-service/iam-types/src/main/java/com/example/iam/types/UserId.java`
- Create: `iam-service/iam-types/src/main/java/com/example/iam/types/CredentialId.java`
- Create: `iam-service/iam-types/src/main/java/com/example/iam/types/SecondaryAuthSessionId.java`
- Create: `iam-service/iam-types/src/main/java/com/example/iam/types/LoginLogId.java`
- Create: `iam-service/iam-types/src/main/java/com/example/iam/types/PermissionRuleId.java`
- Create: `iam-service/iam-types/src/main/java/com/example/iam/types/PlanDelegationId.java`
- Create: `iam-service/iam-types/src/main/java/com/example/iam/types/BusinessDefinitionId.java`
- Create: `iam-service/iam-types/src/main/java/com/example/iam/types/RouteRuleId.java`
- Create: `iam-service/iam-types/src/main/java/com/example/iam/types/package-info.java`
- Test: `iam-service/iam-types/src/test/java/com/example/iam/types/IdTypesTest.java`

**Interfaces:**

- Consumes: `com.example.shared.primitives.identity.Identifier`(来自 shared-types)
- Produces: 8 个 ID 类型,均 `extends Identifier<Long>`,提供 `of(Long)` 与 `value()` 方法

- [ ] **Step 1: 写失败测试**

`iam-service/iam-types/src/test/java/com/example/iam/types/IdTypesTest.java`:

```java
package com.example.iam.types;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdTypesTest {

    @Test
    void userIdOfShouldReturnValue() {
        UserId id = UserId.of(123L);
        assertEquals(123L, id.value());
        assertEquals(123L, id.longValue());
    }

    @Test
    void allIdTypesShouldBeEqualWhenSameValue() {
        assertEquals(UserId.of(1L), UserId.of(1L));
        assertEquals(CredentialId.of(1L), CredentialId.of(1L));
        assertEquals(SecondaryAuthSessionId.of(1L), SecondaryAuthSessionId.of(1L));
        assertEquals(LoginLogId.of(1L), LoginLogId.of(1L));
        assertEquals(PermissionRuleId.of(1L), PermissionRuleId.of(1L));
        assertEquals(PlanDelegationId.of(1L), PlanDelegationId.of(1L));
        assertEquals(BusinessDefinitionId.of(1L), BusinessDefinitionId.of(1L));
        assertEquals(RouteRuleId.of(1L), RouteRuleId.of(1L));
    }

    @Test
    void ofStringShouldParseLong() {
        assertEquals(UserId.of(42L), UserId.of("42"));
    }

    @Test
    void ofNullShouldThrow() {
        assertThrows(NullPointerException.class, () -> UserId.of((Long) null));
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `mvn -pl iam-service/iam-types -am test -Dtest=IdTypesTest`
Expected: 编译失败,`UserId` 等类未找到

- [ ] **Step 3: 实现 UserId**

`iam-service/iam-types/src/main/java/com/example/iam/types/UserId.java`:

```java
package com.example.iam.types;

import com.example.shared.primitives.identity.Identifier;

/**
 * 统一用户 ID(三渠道共用:网上/总部/网点)。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record UserId(Long value) implements Identifier<Long> {

    public static UserId of(Long value) {
        return new UserId(value);
    }

    public static UserId of(String value) {
        return new UserId(Long.parseLong(value));
    }

    public long longValue() {
        return value;
    }
}
```

- [ ] **Step 4: 实现 CredentialId**

```java
package com.example.iam.types;

import com.example.shared.primitives.identity.Identifier;

public record CredentialId(Long value) implements Identifier<Long> {
    public static CredentialId of(Long value) { return new CredentialId(value); }
    public static CredentialId of(String value) { return new CredentialId(Long.parseLong(value)); }
    public long longValue() { return value; }
}
```

- [ ] **Step 5: 实现 SecondaryAuthSessionId**

```java
package com.example.iam.types;

import com.example.shared.primitives.identity.Identifier;

public record SecondaryAuthSessionId(Long value) implements Identifier<Long> {
    public static SecondaryAuthSessionId of(Long value) { return new SecondaryAuthSessionId(value); }
    public static SecondaryAuthSessionId of(String value) { return new SecondaryAuthSessionId(Long.parseLong(value)); }
    public long longValue() { return value; }
}
```

- [ ] **Step 6: 实现 LoginLogId**

```java
package com.example.iam.types;

import com.example.shared.primitives.identity.Identifier;

public record LoginLogId(Long value) implements Identifier<Long> {
    public static LoginLogId of(Long value) { return new LoginLogId(value); }
    public static LoginLogId of(String value) { return new LoginLogId(Long.parseLong(value)); }
    public long longValue() { return value; }
}
```

- [ ] **Step 7: 实现 PermissionRuleId**

```java
package com.example.iam.types;

import com.example.shared.primitives.identity.Identifier;

public record PermissionRuleId(Long value) implements Identifier<Long> {
    public static PermissionRuleId of(Long value) { return new PermissionRuleId(value); }
    public static PermissionRuleId of(String value) { return new PermissionRuleId(Long.parseLong(value)); }
    public long longValue() { return value; }
}
```

- [ ] **Step 8: 实现 PlanDelegationId**

```java
package com.example.iam.types;

import com.example.shared.primitives.identity.Identifier;

public record PlanDelegationId(Long value) implements Identifier<Long> {
    public static PlanDelegationId of(Long value) { return new PlanDelegationId(value); }
    public static PlanDelegationId of(String value) { return new PlanDelegationId(Long.parseLong(value)); }
    public long longValue() { return value; }
}
```

- [ ] **Step 9: 实现 BusinessDefinitionId**

```java
package com.example.iam.types;

import com.example.shared.primitives.identity.Identifier;

public record BusinessDefinitionId(Long value) implements Identifier<Long> {
    public static BusinessDefinitionId of(Long value) { return new BusinessDefinitionId(value); }
    public static BusinessDefinitionId of(String value) { return new BusinessDefinitionId(Long.parseLong(value)); }
    public long longValue() { return value; }
}
```

- [ ] **Step 10: 实现 RouteRuleId**

```java
package com.example.iam.types;

import com.example.shared.primitives.identity.Identifier;

public record RouteRuleId(Long value) implements Identifier<Long> {
    public static RouteRuleId of(Long value) { return new RouteRuleId(value); }
    public static RouteRuleId of(String value) { return new RouteRuleId(Long.parseLong(value)); }
    public long longValue() { return value; }
}
```

- [ ] **Step 11: 实现 package-info**

`iam-service/iam-types/src/main/java/com/example/iam/types/package-info.java`:

```java
/**
 * iam-service 领域原语 ID 类型定义。
 * <p>
 * 所有 ID 类型均为 {@code record},实现 {@link com.example.shared.primitives.identity.Identifier} 接口。
 * 三渠道(网上/总部/网点)用户统一使用 {@link UserId},不再按渠道区分。
 *
 * @author iam-service
 * @since 2026/7/26
 */
package com.example.iam.types;
```

- [ ] **Step 12: 运行测试,确认通过**

Run: `mvn -pl iam-service/iam-types -am test -Dtest=IdTypesTest`
Expected: Tests run: 4, Failures: 0, Errors: 0

- [ ] **Step 13: 提交**

```bash
git add iam-service/iam-types/src
git commit -m "feat(iam-types): 新增 8 个领域原语 ID 类型

1. 实现 UserId/CredentialId/SecondaryAuthSessionId/LoginLogId
2. 实现 PermissionRuleId/PlanDelegationId/BusinessDefinitionId/RouteRuleId
3. 三渠道用户统一使用 UserId(不再分 InternetUserId/HqUserId/BranchUserId)
4. 所有 ID 类型为 record,实现 Identifier<Long>,提供 of(Long)/of(String) 工厂方法"
```

---
