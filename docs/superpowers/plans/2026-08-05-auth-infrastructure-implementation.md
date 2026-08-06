# auth-infrastructure 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 auth-service 补齐 infrastructure 层实现，覆盖 8 个 RepositoryImpl、5 个 SPI 实现、6 张缺失表的 DDL。

**Architecture:** DDD + 六边形架构。infrastructure 层依赖 auth-domain，实现 Repository 接口和 SPI 端口。采用 MyBatis-Flex ORM、MapStruct Converter、ULID 主键、双数据库支持（PostgreSQL + MySQL）。领域事件发布沿用 approval 模式（在 ApplicationService 通过 eventBus.publish 发布）。

**Tech Stack:** JDK 25、Spring Boot 3.5.14、MyBatis-Flex 1.11.5、MapStruct 1.6.3、Lombok 1.18.46、PostgreSQL/MySQL、Sa-Token、BCrypt。

## Global Constraints

- 包名前缀：`com.pension.permission.infrastructure.*`（非 `com.example.*`）
- ID 类型：全部 ULID（String），DO 主键 `VARCHAR(32)`，`@Id(keyType = KeyType.None)`
- 时间戳：禁止 `@Column(onInsertValue/onUpdateValue)`，由 Converter 从领域对象 `createdAt()/updatedAt()` 映射
- 软删除：`@Column(isLogicDelete = true) private Boolean deleted;`
- 乐观锁：`@Column(version = true) private Integer version;`
- 领域事件：在 ApplicationService 发布，**不在 RepositoryImpl.save 中发布**
- DDL：双套 schema-pg.sql + schema-mysql.sql，PG 用 JSONB，MySQL 用 JSON
- DO 命名：业务名称 + DO（如 `SessionDO`）
- Repository 实现命名：业务名称 + RepositoryImpl
- Converter 命名：业务名称 + Converter，标注 `@Mapper(componentModel = "spring")`
- Mapper 命名：业务名称 + Mapper，extends `BaseMapper<XxxDO>`
- ID 转换：ULID 直接 `SessionId.of(idStr)`，**禁止 `Long.parseLong`**
- 4 个独立 Repository（Assignment/Grant/RoleTemplate/RoleVisibility）不继承基类，只实现接口声明的方法
- Credential 是 sealed abstract class，子类需补 reconstitute 方法
- UserAggregate 的重建方法命名为 `restore`（非 reconstitute），参数名 `createdOn/modifiedOn`

---

## 第一批：channel 域（Session + SecondaryAuthSession）

DDL 已存在，实现 DO/Mapper/Converter/RepositoryImpl + VerificationCodeHasher SPI。

### Task 1: 补充 pom.xml 依赖

**Files:**
- Modify: `auth-service/auth-infrastructure/pom.xml`

**Interfaces:**
- Produces: 可编译的 auth-infrastructure 模块，后续 Task 可引用 MyBatis-Flex、MapStruct、Sa-Token、BCrypt 等

- [ ] **Step 1: 读取当前 pom.xml**

```bash
Read auth-service/auth-infrastructure/pom.xml
```

- [ ] **Step 2: 参考 approval-infrastructure/pom.xml 补充依赖**

补充以下依赖（参考 `approval-service/approval-infrastructure/pom.xml`）：

```xml
<!-- ID 生成 -->
<dependency>
  <groupId>com.example</groupId>
  <artifactId>shared-id-starter</artifactId>
</dependency>

<!-- Web 全局配置 -->
<dependency>
  <groupId>com.example</groupId>
  <artifactId>shared-web-starter</artifactId>
</dependency>

<!-- 日志脱敏 -->
<dependency>
  <groupId>com.example</groupId>
  <artifactId>shared-logging-starter</artifactId>
</dependency>

<!-- 事件总线 -->
<dependency>
  <groupId>com.example</groupId>
  <artifactId>shared-event-starter</artifactId>
</dependency>

<!-- ORM -->
<dependency>
  <groupId>com.mybatis-flex</groupId>
  <artifactId>mybatis-flex-spring-boot3-starter</artifactId>
</dependency>

<!-- 连接池 -->
<dependency>
  <groupId>com.zaxxer</groupId>
  <artifactId>HikariCP</artifactId>
</dependency>

<!-- 数据库驱动 (runtime) -->
<dependency>
  <groupId>org.postgresql</groupId>
  <artifactId>postgresql</artifactId>
  <scope>runtime</scope>
</dependency>
<dependency>
  <groupId>com.mysql</groupId>
  <artifactId>mysql-connector-j</artifactId>
  <scope>runtime</scope>
</dependency>

<!-- MapStruct -->
<dependency>
  <groupId>org.mapstruct</groupId>
  <artifactId>mapstruct</artifactId>
</dependency>

<!-- Sa-Token -->
<dependency>
  <groupId>cn.dev33</groupId>
  <artifactId>sa-token-spring-boot3-starter</artifactId>
</dependency>

<!-- BCrypt -->
<dependency>
  <groupId>org.springframework.security</groupId>
  <artifactId>spring-security-crypto</artifactId>
</dependency>
```

在 `<build>` 中补充注解处理器：

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-compiler-plugin</artifactId>
      <configuration>
        <compilerArgs>
          <arg>--enable-preview</arg>
        </compilerArgs>
        <annotationProcessorPaths>
          <path>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
          </path>
          <path>
            <groupId>org.mapstruct</groupId>
            <artifactId>mapstruct-processor</artifactId>
          </path>
          <path>
            <groupId>com.mybatis-flex</groupId>
            <artifactId>mybatis-flex-processor</artifactId>
          </path>
        </annotationProcessorPaths>
      </configuration>
    </plugin>
  </plugins>
</build>
```

- [ ] **Step 3: 验证编译**

```bash
mvn -f auth-service/auth-infrastructure/pom.xml compile -DskipTests --no-transfer-progress
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add auth-service/auth-infrastructure/pom.xml
git commit -m "build(auth-infrastructure): 补充 MyBatis-Flex/MapStruct/Sa-Token/BCrypt 依赖"
```

---

### Task 2: 补充 application.yml 基础配置

**Files:**
- Modify: `auth-service/auth-infrastructure/src/main/resources/application.yml`

**Interfaces:**
- Produces: 数据源、MyBatis-Flex、Sa-Token 配置

- [ ] **Step 1: 读取当前 application.yml**

- [ ] **Step 2: 在现有 `auth.secondary-auth` 配置上方补充基础配置**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/auth_db
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2

mybatis-flex:
  global-config:
    logic-delete-field: deleted
    logic-delete-value: "true"
    logic-not-delete-value: "false"

sa-token:
  token-name: Authorization
  timeout: 28800
  is-concurrent: true
  is-share: false
  token-style: uuid
  is-log: false
```

- [ ] **Step 3: Commit**

```bash
git add auth-service/auth-infrastructure/src/main/resources/application.yml
git commit -m "config(auth-infrastructure): 补充数据源/MyBatis-Flex/Sa-Token 基础配置"
```

---

### Task 3: 实现 SessionDO

**Files:**
- Create: `auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/entity/SessionDO.java`

**Interfaces:**
- Consumes: `t_auth_session` 表结构（来自 schema-pg.sql）
- Produces: `SessionDO` 类，被 SessionMapper、SessionConverter 使用

参考 `t_auth_session` DDL 字段：

| 列名 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(32) | 主键 |
| primary_account_id | VARCHAR(32) | 主账号 |
| channel | VARCHAR(32) | 渠道 |
| effective_identity_id | VARCHAR(32) | 有效身份ID |
| effective_identity_acting | VARCHAR(32) | 实际操作账号 |
| effective_via_secondary | BOOLEAN | 是否经二次授权 |
| secondary_auth_session_id | VARCHAR(32) | 二次授权会话ID |
| selected_plan_id | VARCHAR(32) | 选定计划ID |
| expires_at | TIMESTAMP | 过期时间 |
| status | VARCHAR(16) | 状态 |
| created_by | VARCHAR(64) | 创建人 |
| create_time | TIMESTAMP | 创建时间 |
| updated_by | VARCHAR(64) | 更新人 |
| update_time | TIMESTAMP | 更新时间 |
| deleted | BOOLEAN | 删除标志 |
| version | INT | 版本号 |

- [ ] **Step 1: 创建 SessionDO**

```java
package com.pension.permission.infrastructure.channel.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Table("t_auth_session")
public class SessionDO {

    @Id(keyType = KeyType.None)
    private String id;

    private String primaryAccountId;
    private String channel;

    private String effectiveIdentityId;
    private String effectiveIdentityActing;
    private Boolean effectiveViaSecondary;

    private String secondaryAuthSessionId;
    private String selectedPlanId;
    private LocalDateTime expiresAt;
    private String status;

    private String createdBy;
    private LocalDateTime createTime;
    private String updatedBy;
    private LocalDateTime updateTime;

    @Column(isLogicDelete = true)
    private Boolean deleted;

    @Column(version = true)
    private Integer version;
}
```

- [ ] **Step 2: 验证编译**

```bash
mvn -f auth-service/auth-infrastructure/pom.xml compile -DskipTests --no-transfer-progress
```

- [ ] **Step 3: Commit**

```bash
git add auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/entity/SessionDO.java
git commit -m "feat(auth-infrastructure): 新增 SessionDO 实体"
```

---

### Task 4: 实现 SecondaryAuthSessionDO

**Files:**
- Create: `auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/entity/SecondaryAuthSessionDO.java`

参考 `t_auth_secondary_auth_session` DDL 字段：

| 列名 | 类型 |
|------|------|
| id | VARCHAR(32) |
| teller_account_id | VARCHAR(32) |
| approver_account_id | VARCHAR(32) |
| credential_owner_type | VARCHAR(32) |
| credential_owner_id | VARCHAR(64) |
| approver_mobile | VARCHAR(32) |
| plan_id | VARCHAR(32) |
| verification_code_hash | VARCHAR(255) |
| verification_sent_at | TIMESTAMP |
| verification_expires_at | TIMESTAMP |
| verification_remaining | INT |
| effective_identity_id | VARCHAR(32) |
| effective_identity_acting | VARCHAR(32) |
| effective_via_secondary | BOOLEAN |
| snapshot_permissions | JSONB/JSON |
| snapshot_frozen_at | TIMESTAMP |
| snapshot_expires_at | TIMESTAMP |
| status | VARCHAR(16) |
| initiated_at | TIMESTAMP |
| pending_expires_at | TIMESTAMP |
| authorized_at | TIMESTAMP |
| expires_at | TIMESTAMP |
| revoke_reason | VARCHAR(255) |
| created_by / create_time / updated_by / update_time / deleted / version | 通用字段 |

- [ ] **Step 1: 创建 SecondaryAuthSessionDO**

```java
package com.pension.permission.infrastructure.channel.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Table("t_auth_secondary_auth_session")
public class SecondaryAuthSessionDO {

    @Id(keyType = KeyType.None)
    private String id;

    private String tellerAccountId;
    private String approverAccountId;
    private String credentialOwnerType;
    private String credentialOwnerId;
    private String approverMobile;
    private String planId;

    private String verificationCodeHash;
    private LocalDateTime verificationSentAt;
    private LocalDateTime verificationExpiresAt;
    private Integer verificationRemaining;

    private String effectiveIdentityId;
    private String effectiveIdentityActing;
    private Boolean effectiveViaSecondary;

    private String snapshotPermissions;
    private LocalDateTime snapshotFrozenAt;
    private LocalDateTime snapshotExpiresAt;

    private String status;
    private LocalDateTime initiatedAt;
    private LocalDateTime pendingExpiresAt;
    private LocalDateTime authorizedAt;
    private LocalDateTime expiresAt;
    private String revokeReason;

    private String createdBy;
    private LocalDateTime createTime;
    private String updatedBy;
    private LocalDateTime updateTime;

    @Column(isLogicDelete = true)
    private Boolean deleted;

    @Column(version = true)
    private Integer version;
}
```

- [ ] **Step 2: Commit**

```bash
git add auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/entity/SecondaryAuthSessionDO.java
git commit -m "feat(auth-infrastructure): 新增 SecondaryAuthSessionDO 实体"
```

---

### Task 5: 实现 SessionMapper 和 SecondaryAuthSessionMapper

**Files:**
- Create: `auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/mapper/SessionMapper.java`
- Create: `auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/mapper/SecondaryAuthSessionMapper.java`

- [ ] **Step 1: 创建 SessionMapper**

```java
package com.pension.permission.infrastructure.channel.mapper;

import com.mybatisflex.core.BaseMapper;
import com.pension.permission.infrastructure.channel.entity.SessionDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SessionMapper extends BaseMapper<SessionDO> {
}
```

- [ ] **Step 2: 创建 SecondaryAuthSessionMapper**

```java
package com.pension.permission.infrastructure.channel.mapper;

import com.mybatisflex.core.BaseMapper;
import com.pension.permission.infrastructure.channel.entity.SecondaryAuthSessionDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SecondaryAuthSessionMapper extends BaseMapper<SecondaryAuthSessionDO> {
}
```

- [ ] **Step 3: Commit**

```bash
git add auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/mapper/
git commit -m "feat(auth-infrastructure): 新增 Session/SecondaryAuthSession Mapper"
```

---

### Task 6: 实现 SessionConverter

**Files:**
- Create: `auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/converter/SessionConverter.java`

**Interfaces:**
- Consumes: `Session` 聚合根（含 `reconstitute` 静态方法）、`SessionDO`
- Produces: `SessionConverter`（@Mapper spring），被 SessionRepositoryImpl 使用

Session 聚合根字段：
- `id: SessionId`
- `primaryAccountId: UserNo`
- `channel: AnnuityChannel`（枚举 .name()）
- `effectiveIdentity: EffectiveIdentity`（record: identityAccountId, actingAccountId, viaSecondaryAuth）
- `secondaryAuthSessionId: SecondaryAuthSessionId`
- `selectedPlanId: PlanNo`
- `expiresAt: LocalDateTime`
- `status: SessionStatus`（枚举 .name()）

重建方法签名（从 Session.java）：
```java
public static Session reconstitute(SessionId id, UserNo createdBy, UserNo updatedBy,
    LocalDateTime createdAt, LocalDateTime updatedAt, Version version,
    UserNo primaryAccountId, AnnuityChannel channel, EffectiveIdentity effectiveIdentity,
    SecondaryAuthSessionId secondaryAuthSessionId, PlanNo selectedPlanId,
    LocalDateTime expiresAt, SessionStatus status)
```

- [ ] **Step 1: 创建 SessionConverter**

```java
package com.pension.permission.infrastructure.channel.converter;

import com.example.shared.contactinfo.Mobile;
import com.example.shared.identifier.id.UserNo;
import com.example.shared.identifier.version.Version;
import com.pension.permission.domain.channel.aggregate.Session;
import com.pension.permission.domain.channel.enumeration.AnnuityChannel;
import com.pension.permission.domain.channel.enumeration.SessionStatus;
import com.pension.permission.domain.channel.valueobject.EffectiveIdentity;
import com.pension.permission.infrastructure.channel.entity.SessionDO;
import com.pension.permission.types.SecondaryAuthSessionId;
import com.pension.permission.types.SessionId;
import org.mapstruct.Mapper;

import java.time.LocalDateTime;

@Mapper(componentModel = "spring")
public interface SessionConverter {

    default SessionDO toDO(Session session) {
        SessionDO DO = new SessionDO();
        DO.setId(session.id().value());
        DO.setPrimaryAccountId(session.primaryAccountId().value());
        DO.setChannel(session.channel().name());

        EffectiveIdentity identity = session.effectiveIdentity();
        if (identity != null) {
            DO.setEffectiveIdentityId(identity.identityAccountId().value());
            DO.setEffectiveIdentityActing(identity.actingAccountId().value());
            DO.setEffectiveViaSecondary(identity.viaSecondaryAuth());
        }

        SecondaryAuthSessionId secondaryId = session.secondaryAuthSessionId();
        DO.setSecondaryAuthSessionId(secondaryId != null ? secondaryId.value() : null);

        DO.setSelectedPlanId(session.selectedPlanId() != null ? session.selectedPlanId().value() : null);
        DO.setExpiresAt(session.expiresAt());
        DO.setStatus(session.status().name());

        DO.setCreatedBy(session.createdBy().value());
        DO.setCreateTime(session.createdAt());
        DO.setUpdatedBy(session.updatedBy() != null ? session.updatedBy().value() : null);
        DO.setUpdateTime(session.updatedAt());
        DO.setDeleted(false);
        DO.setVersion(session.version() != null ? session.version().value() : 0);
        return DO;
    }

    default Session toDomain(SessionDO DO) {
        EffectiveIdentity identity = null;
        if (DO.getEffectiveIdentityId() != null) {
            identity = new EffectiveIdentity(
                UserNo.of(DO.getEffectiveIdentityId()),
                UserNo.of(DO.getEffectiveIdentityActing()),
                DO.getEffectiveViaSecondary()
            );
        }

        SecondaryAuthSessionId secondaryId = DO.getSecondaryAuthSessionId() != null
            ? SecondaryAuthSessionId.of(DO.getSecondaryAuthSessionId()) : null;

        return Session.reconstitute(
            SessionId.of(DO.getId()),
            UserNo.of(DO.getCreatedBy()),
            DO.getUpdatedBy() != null ? UserNo.of(DO.getUpdatedBy()) : null,
            DO.getCreateTime(),
            DO.getUpdateTime(),
            Version.of(DO.getVersion()),
            UserNo.of(DO.getPrimaryAccountId()),
            AnnuityChannel.valueOf(DO.getChannel()),
            identity,
            secondaryId,
            DO.getSelectedPlanId() != null ? new com.example.shared.identifier.contract.PlanNo(DO.getSelectedPlanId()) : null,
            DO.getExpiresAt(),
            SessionStatus.valueOf(DO.getStatus())
        );
    }
}
```

**注意**：实际实现时需根据 `Session.reconstitute` 的真实签名和 `PlanNo`/`Version` 类路径调整。读取 `Session.java` 和 `shared-domain` 的 `Version` 类确认。

- [ ] **Step 2: 验证编译**

```bash
mvn -f auth-service/auth-infrastructure/pom.xml compile -DskipTests --no-transfer-progress
```

- [ ] **Step 3: Commit**

```bash
git add auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/converter/SessionConverter.java
git commit -m "feat(auth-infrastructure): 新增 SessionConverter"
```

---

### Task 7: 实现 SecondaryAuthSessionConverter

**Files:**
- Create: `auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/converter/SecondaryAuthSessionConverter.java`

**复杂映射点**：
- `CredentialOwner` sealed interface → `credential_owner_type` + `credential_owner_id` 两列
- `VerificationCode` record → 4 列
- `EffectiveIdentity` → 3 列
- `PermissionSnapshot` → JSON 字符串 + 2 时间列

重建方法（从 SecondaryAuthSession.java）：
```java
public static SecondaryAuthSession reconstitute(ReconstituteSnapshot snapshot)
```

`ReconstituteSnapshot` record 字段（需从源码读取确认）：
- `id, createdBy, updatedBy, createdAt, updatedAt, version`
- `tellerAccountId, approverAccountId, credentialOwner, approverMobile, planId`
- `verificationCode, effectiveIdentity, permissionSnapshot`
- `status, initiatedAt, pendingExpiresAt, authorizedAt, expiresAt, revokeReason`

- [ ] **Step 1: 读取 SecondaryAuthSession.java 确认 ReconstituteSnapshot 字段**

```bash
Read auth-service/auth-domain/src/main/java/com/pension/permission/domain/channel/aggregate/SecondaryAuthSession.java
```

定位 `ReconstituteSnapshot` record 定义和 `reconstitute` 方法签名。

- [ ] **Step 2: 创建 SecondaryAuthSessionConverter**

实现要点：
1. `toDO`：将聚合根字段映射到 DO，PermissionSnapshot.permissions 序列化为 JSON 字符串
2. `toDomain`：从 DO 构造 `ReconstituteSnapshot`，调用 `SecondaryAuthSession.reconstitute(snapshot)`
3. `CredentialOwner` 处理：根据 type 字段决定构造 `UserCredentialOwner` / `CustomerCredentialOwner` / `PlanCredentialOwner`
4. `VerificationCode` 可能为 null（AUTHORIZED 后清空）
5. `PermissionSnapshot` 可能为 null

```java
package com.pension.permission.infrastructure.channel.converter;

import com.example.shared.contactinfo.Mobile;
import com.example.shared.identifier.id.UserNo;
import com.example.shared.identifier.version.Version;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pension.permission.domain.authorization.valueobject.ActionCode;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import com.pension.permission.domain.authorization.valueobject.Permission;
import com.pension.permission.domain.channel.aggregate.SecondaryAuthSession;
import com.pension.permission.domain.channel.enumeration.SecondaryAuthStatus;
import com.pension.permission.domain.channel.valueobject.EffectiveIdentity;
import com.pension.permission.domain.channel.valueobject.PermissionSnapshot;
import com.pension.permission.domain.channel.valueobject.VerificationCode;
import com.pension.permission.domain.credential.valueobject.owner.CredentialOwner;
import com.pension.permission.domain.credential.valueobject.owner.CustomerCredentialOwner;
import com.pension.permission.domain.credential.valueobject.owner.PlanCredentialOwner;
import com.pension.permission.domain.credential.valueobject.owner.UserCredentialOwner;
import com.pension.permission.infrastructure.channel.entity.SecondaryAuthSessionDO;
import com.pension.permission.types.SecondaryAuthSessionId;
import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public abstract class SecondaryAuthSessionConverter {

    @Autowired
    protected ObjectMapper objectMapper;

    public final SecondaryAuthSessionDO toDO(SecondaryAuthSession session) {
        SecondaryAuthSessionDO DO = new SecondaryAuthSessionDO();
        DO.setId(session.id().value());
        DO.setTellerAccountId(session.tellerAccountId().value());
        DO.setApproverAccountId(session.approverAccountId() != null ? session.approverAccountId().value() : null);

        CredentialOwner owner = session.credentialOwner();
        DO.setCredentialOwnerType(owner.getClass().getSimpleName());
        DO.setCredentialOwnerId(owner.value());

        DO.setApproverMobile(session.approverMobile().value());
        DO.setPlanId(session.planId().value());

        VerificationCode code = session.verificationCode();
        if (code != null) {
            DO.setVerificationCodeHash(code.hashedCode());
            DO.setVerificationSentAt(code.sentAt());
            DO.setVerificationExpiresAt(code.expiresAt());
            DO.setVerificationRemaining(code.remainingAttempts());
        }

        EffectiveIdentity identity = session.effectiveIdentity();
        if (identity != null) {
            DO.setEffectiveIdentityId(identity.identityAccountId().value());
            DO.setEffectiveIdentityActing(identity.actingAccountId().value());
            DO.setEffectiveViaSecondary(identity.viaSecondaryAuth());
        }

        PermissionSnapshot snapshot = session.permissionSnapshot();
        if (snapshot != null) {
            DO.setSnapshotPermissions(serializePermissions(snapshot.permissions()));
            DO.setSnapshotFrozenAt(snapshot.frozenAt());
            DO.setSnapshotExpiresAt(snapshot.expiresAt());
        }

        DO.setStatus(session.status().name());
        DO.setInitiatedAt(session.initiatedAt());
        DO.setPendingExpiresAt(session.pendingExpiresAt());
        DO.setAuthorizedAt(session.authorizedAt());
        DO.setExpiresAt(session.expiresAt());
        DO.setRevokeReason(session.revokeReason());

        DO.setCreatedBy(session.createdBy().value());
        DO.setCreateTime(session.createdAt());
        DO.setUpdatedBy(session.updatedBy() != null ? session.updatedBy().value() : null);
        DO.setUpdateTime(session.updatedAt());
        DO.setDeleted(false);
        DO.setVersion(session.version() != null ? session.version().value() : 0);
        return DO;
    }

    public final SecondaryAuthSession toDomain(SecondaryAuthSessionDO DO) {
        CredentialOwner owner = switch (DO.getCredentialOwnerType()) {
            case "UserCredentialOwner" -> new UserCredentialOwner(UserNo.of(DO.getCredentialOwnerId()));
            case "CustomerCredentialOwner" -> new CustomerCredentialOwner(DO.getCredentialOwnerId());
            case "PlanCredentialOwner" -> new PlanCredentialOwner(DO.getCredentialOwnerId());
            default -> throw new IllegalStateException("未知的 CredentialOwner 类型: " + DO.getCredentialOwnerType());
        };

        VerificationCode code = null;
        if (DO.getVerificationCodeHash() != null) {
            code = new VerificationCode(
                DO.getVerificationCodeHash(),
                DO.getVerificationSentAt(),
                DO.getVerificationExpiresAt(),
                DO.getVerificationRemaining()
            );
        }

        EffectiveIdentity identity = null;
        if (DO.getEffectiveIdentityId() != null) {
            identity = new EffectiveIdentity(
                UserNo.of(DO.getEffectiveIdentityId()),
                UserNo.of(DO.getEffectiveIdentityActing()),
                DO.getEffectiveViaSecondary()
            );
        }

        PermissionSnapshot snapshot = null;
        if (DO.getSnapshotPermissions() != null) {
            Set<Permission> permissions = deserializePermissions(DO.getSnapshotPermissions());
            snapshot = new PermissionSnapshot(permissions, DO.getSnapshotFrozenAt(), DO.getSnapshotExpiresAt());
        }

        SecondaryAuthSession.ReconstituteSnapshot snapshotRecord = new SecondaryAuthSession.ReconstituteSnapshot(
            SecondaryAuthSessionId.of(DO.getId()),
            UserNo.of(DO.getCreatedBy()),
            DO.getUpdatedBy() != null ? UserNo.of(DO.getUpdatedBy()) : null,
            DO.getCreateTime(),
            DO.getUpdateTime(),
            Version.of(DO.getVersion()),
            UserNo.of(DO.getTellerAccountId()),
            DO.getApproverAccountId() != null ? UserNo.of(DO.getApproverAccountId()) : null,
            owner,
            new Mobile(DO.getApproverMobile()),
            new com.example.shared.identifier.contract.PlanNo(DO.getPlanId()),
            code,
            identity,
            snapshot,
            SecondaryAuthStatus.valueOf(DO.getStatus()),
            DO.getInitiatedAt(),
            DO.getPendingExpiresAt(),
            DO.getAuthorizedAt(),
            DO.getExpiresAt(),
            DO.getRevokeReason()
        );

        return SecondaryAuthSession.reconstitute(snapshotRecord);
    }

    private String serializePermissions(Set<Permission> permissions) {
        try {
            return objectMapper.writeValueAsString(permissions.stream()
                .map(p -> new PermissionDto(p.businessCode().value(), p.actionCode() != null ? p.actionCode().value() : null))
                .collect(Collectors.toList()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化权限集合失败", e);
        }
    }

    private Set<Permission> deserializePermissions(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Set<PermissionDto>>() {}).stream()
                .map(dto -> new Permission(
                    new BusinessCode(dto.businessCode()),
                    dto.actionCode() != null ? new ActionCode(dto.actionCode()) : null
                ))
                .collect(Collectors.toSet());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("反序列化权限集合失败", e);
        }
    }

    private record PermissionDto(String businessCode, String actionCode) {}
}
```

**注意**：实际实现时需根据 `ReconstituteSnapshot` 真实字段顺序和类型调整。读取 `SecondaryAuthSession.java` 确认。

- [ ] **Step 3: Commit**

```bash
git add auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/converter/SecondaryAuthSessionConverter.java
git commit -m "feat(auth-infrastructure): 新增 SecondaryAuthSessionConverter"
```

---

### Task 8: 实现 SessionRepositoryImpl

**Files:**
- Create: `auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/repository/SessionRepositoryImpl.java`

**Interfaces:**
- Consumes: `SessionRepository` 接口（auth-domain）、`SessionMapper`、`SessionConverter`
- Produces: `SessionRepositoryImpl` 实现 `SessionRepository`

`SessionRepository` 接口自定义方法：
- `findByPrimaryAccountId(UserNo): List<Session>`
- `findActiveByPrimaryAccountIdAndChannel(UserNo, AnnuityChannel): Optional<Session>`

继承基类方法：`load / loadOrThrow / save / delete / deleteById / loadAll / streamByAppId`

- [ ] **Step 1: 创建 SessionRepositoryImpl**

```java
package com.pension.permission.infrastructure.channel.repository;

import com.example.shared.identifier.id.UserNo;
import com.mybatisflex.core.query.QueryWrapper;
import com.pension.permission.domain.channel.aggregate.Session;
import com.pension.permission.domain.channel.enumeration.AnnuityChannel;
import com.pension.permission.domain.channel.enumeration.SessionStatus;
import com.pension.permission.domain.channel.repository.SessionRepository;
import com.pension.permission.infrastructure.channel.converter.SessionConverter;
import com.pension.permission.infrastructure.channel.entity.SessionDO;
import com.pension.permission.infrastructure.channel.mapper.SessionMapper;
import com.pension.permission.types.SessionId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Repository
@RequiredArgsConstructor
public class SessionRepositoryImpl implements SessionRepository {

    private final SessionMapper sessionMapper;
    private final SessionConverter sessionConverter;

    @Override
    public Optional<Session> load(SessionId id) {
        return Optional.ofNullable(sessionMapper.selectOneById(id.value()))
            .map(sessionConverter::toDomain);
    }

    @Override
    public Session save(Session session) {
        SessionDO DO = sessionConverter.toDO(session);
        SessionDO existing = sessionMapper.selectOneById(DO.getId());
        if (existing == null) {
            sessionMapper.insert(DO);
        } else {
            DO.setVersion(existing.getVersion());
            sessionMapper.update(DO);
        }
        return session;
    }

    @Override
    public void delete(Session session) {
        sessionMapper.deleteById(session.id().value());
    }

    @Override
    public void deleteById(SessionId id) {
        sessionMapper.deleteById(id.value());
    }

    @Override
    public List<Session> loadAll() {
        return sessionMapper.selectAll().stream()
            .map(sessionConverter::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Session> findByPrimaryAccountId(UserNo primaryAccountId) {
        QueryWrapper query = QueryWrapper.create()
            .where(SessionDO::getPrimaryAccountId).eq(primaryAccountId.value())
            .and(SessionDO::getDeleted).eq(false);
        return sessionMapper.selectListByQuery(query).stream()
            .map(sessionConverter::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public Optional<Session> findActiveByPrimaryAccountIdAndChannel(
            UserNo primaryAccountId, AnnuityChannel channel) {
        QueryWrapper query = QueryWrapper.create()
            .where(SessionDO::getPrimaryAccountId).eq(primaryAccountId.value())
            .and(SessionDO::getChannel).eq(channel.name())
            .and(SessionDO::getStatus).eq(SessionStatus.ACTIVE.name())
            .and(SessionDO::getDeleted).eq(false);
        return Optional.ofNullable(sessionMapper.selectOneByQuery(query))
            .map(sessionConverter::toDomain);
    }
}
```

**注意**：`streamByAppId` 方法若在基类中是 default 方法，可省略实现。读取 `Repository.java` 基类确认。

- [ ] **Step 2: Commit**

```bash
git add auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/repository/SessionRepositoryImpl.java
git commit -m "feat(auth-infrastructure): 新增 SessionRepositoryImpl"
```

---

### Task 9: 实现 SecondaryAuthSessionRepositoryImpl

**Files:**
- Create: `auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/repository/SecondaryAuthSessionRepositoryImpl.java`

`SecondaryAuthSessionRepository` 自定义方法：
- `findActiveByTeller(UserNo): Optional<SecondaryAuthSession>`
- `findAuthorizedByApprover(UserNo): List<SecondaryAuthSession>`
- `findPendingByApprover(UserNo): List<SecondaryAuthSession>`
- `findTimeoutSessions(): List<SecondaryAuthSession>`

- [ ] **Step 1: 创建 SecondaryAuthSessionRepositoryImpl**

```java
package com.pension.permission.infrastructure.channel.repository;

import com.example.shared.identifier.id.UserNo;
import com.mybatisflex.core.query.QueryWrapper;
import com.pension.permission.domain.channel.aggregate.SecondaryAuthSession;
import com.pension.permission.domain.channel.enumeration.SecondaryAuthStatus;
import com.pension.permission.domain.channel.repository.SecondaryAuthSessionRepository;
import com.pension.permission.infrastructure.channel.converter.SecondaryAuthSessionConverter;
import com.pension.permission.infrastructure.channel.entity.SecondaryAuthSessionDO;
import com.pension.permission.infrastructure.channel.mapper.SecondaryAuthSessionMapper;
import com.pension.permission.types.SecondaryAuthSessionId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Repository
@RequiredArgsConstructor
public class SecondaryAuthSessionRepositoryImpl implements SecondaryAuthSessionRepository {

    private final SecondaryAuthSessionMapper sessionMapper;
    private final SecondaryAuthSessionConverter converter;

    @Override
    public Optional<SecondaryAuthSession> load(SecondaryAuthSessionId id) {
        return Optional.ofNullable(sessionMapper.selectOneById(id.value()))
            .map(converter::toDomain);
    }

    @Override
    public SecondaryAuthSession save(SecondaryAuthSession session) {
        SecondaryAuthSessionDO DO = converter.toDO(session);
        SecondaryAuthSessionDO existing = sessionMapper.selectOneById(DO.getId());
        if (existing == null) {
            sessionMapper.insert(DO);
        } else {
            DO.setVersion(existing.getVersion());
            sessionMapper.update(DO);
        }
        return session;
    }

    @Override
    public void delete(SecondaryAuthSession session) {
        sessionMapper.deleteById(session.id().value());
    }

    @Override
    public void deleteById(SecondaryAuthSessionId id) {
        sessionMapper.deleteById(id.value());
    }

    @Override
    public List<SecondaryAuthSession> loadAll() {
        return sessionMapper.selectAll().stream()
            .map(converter::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public Optional<SecondaryAuthSession> findActiveByTeller(UserNo tellerAccountId) {
        QueryWrapper query = QueryWrapper.create()
            .where(SecondaryAuthSessionDO::getTellerAccountId).eq(tellerAccountId.value())
            .and(SecondaryAuthSessionDO::getStatus).in(
                SecondaryAuthStatus.PENDING.name(),
                SecondaryAuthStatus.AUTHORIZED.name())
            .and(SecondaryAuthSessionDO::getDeleted).eq(false);
        return Optional.ofNullable(sessionMapper.selectOneByQuery(query))
            .map(converter::toDomain);
    }

    @Override
    public List<SecondaryAuthSession> findAuthorizedByApprover(UserNo approverAccountId) {
        QueryWrapper query = QueryWrapper.create()
            .where(SecondaryAuthSessionDO::getApproverAccountId).eq(approverAccountId.value())
            .and(SecondaryAuthSessionDO::getStatus).eq(SecondaryAuthStatus.AUTHORIZED.name())
            .and(SecondaryAuthSessionDO::getDeleted).eq(false);
        return sessionMapper.selectListByQuery(query).stream()
            .map(converter::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<SecondaryAuthSession> findPendingByApprover(UserNo approverAccountId) {
        QueryWrapper query = QueryWrapper.create()
            .where(SecondaryAuthSessionDO::getApproverAccountId).eq(approverAccountId.value())
            .and(SecondaryAuthSessionDO::getStatus).eq(SecondaryAuthStatus.PENDING.name())
            .and(SecondaryAuthSessionDO::getDeleted).eq(false);
        return sessionMapper.selectListByQuery(query).stream()
            .map(converter::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<SecondaryAuthSession> findTimeoutSessions() {
        LocalDateTime now = LocalDateTime.now();
        QueryWrapper query = QueryWrapper.create()
            .where(SecondaryAuthSessionDO::getDeleted).eq(false)
            .and(qw -> qw
                .where(SecondaryAuthSessionDO::getStatus).eq(SecondaryAuthStatus.PENDING.name())
                .and(SecondaryAuthSessionDO::getPendingExpiresAt).lt(now)
                .or(qw2 -> qw2
                    .where(SecondaryAuthSessionDO::getStatus).eq(SecondaryAuthStatus.AUTHORIZED.name())
                    .and(qw3 -> qw3
                        .where(SecondaryAuthSessionDO::getExpiresAt).lt(now)
                        .or(SecondaryAuthSessionDO::getSnapshotExpiresAt).lt(now)
                    )
                )
            );
        return sessionMapper.selectListByQuery(query).stream()
            .map(converter::toDomain)
            .collect(Collectors.toList());
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/repository/SecondaryAuthSessionRepositoryImpl.java
git commit -m "feat(auth-infrastructure): 新增 SecondaryAuthSessionRepositoryImpl"
```

---

### Task 10: 实现 VerificationCodeHasher（BCrypt）

**Files:**
- Create: `auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/spi/BCryptVerificationCodeHasher.java`

**Interfaces:**
- Consumes: `VerificationCodeHasher` SPI 接口（auth-domain）
- Produces: `BCryptVerificationCodeHasher` 实现，被 `SecondaryAuthAppService` 注入

```java
package com.pension.permission.infrastructure.channel.spi;

import com.pension.permission.domain.channel.spi.VerificationCodeHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptVerificationCodeHasher implements VerificationCodeHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String hash(String rawCode) {
        return encoder.encode(rawCode);
    }

    @Override
    public boolean matches(String rawCode, String hashedCode) {
        return encoder.matches(rawCode, hashedCode);
    }
}
```

- [ ] **Step 1: 创建 BCryptVerificationCodeHasher**

- [ ] **Step 2: Commit**

```bash
git add auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/spi/BCryptVerificationCodeHasher.java
git commit -m "feat(auth-infrastructure): 新增 BCryptVerificationCodeHasher 实现 VerificationCodeHasher SPI"
```

---

### Task 11: 第一批编译验证

- [ ] **Step 1: 安装 auth-types 和 auth-domain 到本地仓库**

```bash
mvn -f auth-service/auth-types/pom.xml install -DskipTests --no-transfer-progress
mvn -f auth-service/auth-domain/pom.xml install -DskipTests --no-transfer-progress
```

- [ ] **Step 2: 编译 auth-infrastructure**

```bash
mvn -f auth-service/auth-infrastructure/pom.xml compile --no-transfer-progress
```

Expected: BUILD SUCCESS

- [ ] **Step 3: 编译 auth-application 验证依赖注入**

```bash
mvn -f auth-service/auth-application/pom.xml compile --no-transfer-progress
```

Expected: BUILD SUCCESS

---

## 第二批：user + credential + role + assignment 域

### Task 12: 补充 domain 层 Credential 子类的 reconstitute 方法

**Files:**
- Modify: `auth-service/auth-domain/src/main/java/com/pension/permission/domain/credential/aggregate/PasswordCredential.java`
- Modify: `auth-service/auth-domain/src/main/java/com/pension/permission/domain/credential/aggregate/UKeyCredential.java`

- [ ] **Step 1: 读取 PasswordCredential.java 和 UKeyCredential.java**

- [ ] **Step 2: 在 PasswordCredential 添加 reconstitute 静态方法**

```java
public static PasswordCredential reconstitute(
        CredentialId id, UserNo createdBy, UserNo updatedBy,
        LocalDateTime createdAt, LocalDateTime updatedAt, Version version,
        CredentialOwner owner, Set<AnnuityChannel> applicableChannels,
        ValidityPeriod validityPeriod, CredentialStatus status,
        UserNo userNo, String passwordHash) {
    PasswordCredential credential = new PasswordCredential(
        id, createdBy, owner, applicableChannels, validityPeriod,
        userNo, passwordHash);
    // 通过反射或 protected setter 设置重建字段
    // 参考 AggregateRoot 的重建模式
    return credential;
}
```

**注意**：基类 `Entity` 的重建构造函数是 `protected Entity(id, createdBy, updatedBy, createdAt, updatedAt, version)`。PasswordCredential 的构造函数需要先调用 `super(...)` 重建构造函数，再设置业务字段。需读取 `Credential.java` 基类和 `Entity.java` 确认可见性。

- [ ] **Step 3: 在 UKeyCredential 添加 reconstitute 静态方法**

```java
public static UKeyCredential reconstitute(
        CredentialId id, UserNo createdBy, UserNo updatedBy,
        LocalDateTime createdAt, LocalDateTime updatedAt, Version version,
        CredentialOwner owner, Set<AnnuityChannel> applicableChannels,
        ValidityPeriod validityPeriod, CredentialStatus status,
        String keySerial) {
    UKeyCredential credential = new UKeyCredential(
        id, createdBy, owner, applicableChannels, validityPeriod, keySerial);
    return credential;
}
```

- [ ] **Step 4: 运行 auth-domain 测试确保不破坏**

```bash
mvn -f auth-service/auth-domain/pom.xml test --no-transfer-progress
```

Expected: BUILD SUCCESS，17/17 测试通过

- [ ] **Step 5: Commit**

```bash
git add auth-service/auth-domain/src/main/java/com/pension/permission/domain/credential/aggregate/PasswordCredential.java
git add auth-service/auth-domain/src/main/java/com/pension/permission/domain/credential/aggregate/UKeyCredential.java
git commit -m "feat(auth-domain): 为 PasswordCredential/UKeyCredential 补充 reconstitute 工厂方法"
```

---

### Task 13: 补充 6 张缺失表的 DDL

**Files:**
- Modify: `auth-service/auth-infrastructure/src/main/resources/schema-pg.sql`
- Modify: `auth-service/auth-infrastructure/src/main/resources/schema-mysql.sql`

需新增 6 张表：
1. `t_auth_user` — UserAggregate
2. `t_auth_credential` — Credential（单表存储 PasswordCredential + UKeyCredential）
3. `t_auth_role_template` — RoleTemplate
4. `t_auth_role_visibility` — RoleVisibilityScope（值对象）
5. `t_auth_assignment` — AgentIdentityAssignment
6. （Grant 表在第三批）

- [ ] **Step 1: 在 schema-pg.sql 末尾追加 5 张表 DDL**

```sql
-- 用户表
CREATE TABLE IF NOT EXISTS t_auth_user (
    id              VARCHAR(32)   NOT NULL,
    user_type       VARCHAR(32)   NOT NULL,
    identity_type   VARCHAR(32)   NOT NULL,
    identity_number VARCHAR(64)   NOT NULL,
    mobile          VARCHAR(32),
    email           VARCHAR(128),
    telephone       VARCHAR(32),
    address         VARCHAR(255),
    postal_code     VARCHAR(16),
    status          VARCHAR(16)   NOT NULL,
    created_by      VARCHAR(64)   NOT NULL,
    create_time     TIMESTAMP     NOT NULL,
    updated_by      VARCHAR(64),
    update_time     TIMESTAMP,
    deleted         BOOLEAN       NOT NULL DEFAULT FALSE,
    version         INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_t_auth_user PRIMARY KEY (id)
);
CREATE INDEX idx_t_auth_user_mobile ON t_auth_user (mobile) WHERE deleted = FALSE;
CREATE INDEX idx_t_auth_user_status ON t_auth_user (status) WHERE deleted = FALSE;

-- 凭证表（单表存储 PasswordCredential + UKeyCredential，用 credential_type 区分）
CREATE TABLE IF NOT EXISTS t_auth_credential (
    id                  VARCHAR(32)   NOT NULL,
    credential_type     VARCHAR(32)   NOT NULL,
    owner_type          VARCHAR(32)   NOT NULL,
    owner_id            VARCHAR(64)   NOT NULL,
    applicable_channels JSONB         NOT NULL,
    validity_start      TIMESTAMP,
    validity_end        TIMESTAMP,
    status              VARCHAR(16)   NOT NULL,
    -- PasswordCredential 专属字段
    user_no             VARCHAR(32),
    password_hash       VARCHAR(255),
    -- UKeyCredential 专属字段
    key_serial          VARCHAR(128),
    created_by          VARCHAR(64)   NOT NULL,
    create_time         TIMESTAMP     NOT NULL,
    updated_by          VARCHAR(64),
    update_time         TIMESTAMP,
    deleted             BOOLEAN       NOT NULL DEFAULT FALSE,
    version             INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_t_auth_credential PRIMARY KEY (id)
);
CREATE INDEX idx_t_auth_credential_owner ON t_auth_credential (owner_type, owner_id) WHERE deleted = FALSE;
CREATE INDEX idx_t_auth_credential_type ON t_auth_credential (credential_type) WHERE deleted = FALSE;
CREATE UNIQUE INDEX uk_t_auth_credential_ukey_serial ON t_auth_credential (key_serial) WHERE deleted = FALSE AND key_serial IS NOT NULL;

-- 角色模板表
CREATE TABLE IF NOT EXISTS t_auth_role_template (
    id              VARCHAR(32)   NOT NULL,
    role_code       VARCHAR(64)   NOT NULL,
    scope_dimension VARCHAR(32)   NOT NULL,
    scope_value     VARCHAR(64),
    permissions     JSONB         NOT NULL,
    status          VARCHAR(16)   NOT NULL,
    created_by      VARCHAR(64)   NOT NULL,
    create_time     TIMESTAMP     NOT NULL,
    updated_by      VARCHAR(64),
    update_time     TIMESTAMP,
    deleted         BOOLEAN       NOT NULL DEFAULT FALSE,
    version         INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_t_auth_role_template PRIMARY KEY (id)
);
CREATE INDEX idx_t_auth_role_template_code ON t_auth_role_template (role_code, status) WHERE deleted = FALSE;

-- 角色可见性范围表（值对象表）
CREATE TABLE IF NOT EXISTS t_auth_role_visibility (
    id              BIGSERIAL,
    dimension       VARCHAR(32)   NOT NULL,
    scope_value      VARCHAR(64)   NOT NULL,
    mode            VARCHAR(16)   NOT NULL,
    created_by      VARCHAR(64)   NOT NULL,
    create_time     TIMESTAMP     NOT NULL,
    updated_by      VARCHAR(64),
    update_time     TIMESTAMP,
    deleted         BOOLEAN       NOT NULL DEFAULT FALSE,
    version         INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_t_auth_role_visibility PRIMARY KEY (id)
);
CREATE INDEX idx_t_auth_role_visibility_dim ON t_auth_role_visibility (dimension, scope_value) WHERE deleted = FALSE;

-- 身份分配表
CREATE TABLE IF NOT EXISTS t_auth_assignment (
    id              VARCHAR(32)   NOT NULL,
    user_no         VARCHAR(32)   NOT NULL,
    role_code       VARCHAR(64)   NOT NULL,
    scope_dimension VARCHAR(32)   NOT NULL,
    scope_value     VARCHAR(64)   NOT NULL,
    inheritable     BOOLEAN       NOT NULL DEFAULT FALSE,
    status          VARCHAR(16)   NOT NULL,
    created_by      VARCHAR(64)   NOT NULL,
    create_time     TIMESTAMP     NOT NULL,
    updated_by      VARCHAR(64),
    update_time     TIMESTAMP,
    deleted         BOOLEAN       NOT NULL DEFAULT FALSE,
    version         INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_t_auth_assignment PRIMARY KEY (id)
);
CREATE INDEX idx_t_auth_assignment_user ON t_auth_assignment (user_no, status) WHERE deleted = FALSE;
CREATE INDEX idx_t_auth_assignment_scope ON t_auth_assignment (scope_dimension, scope_value) WHERE deleted = FALSE;
```

- [ ] **Step 2: 在 schema-mysql.sql 末尾追加对应 MySQL DDL**

将 `JSONB` → `JSON`，`BOOLEAN` → `TINYINT(1)`，`TIMESTAMP` → `DATETIME`，`BIGSERIAL` → `BIGINT AUTO_INCREMENT`。

- [ ] **Step 3: Commit**

```bash
git add auth-service/auth-infrastructure/src/main/resources/schema-pg.sql
git add auth-service/auth-infrastructure/src/main/resources/schema-mysql.sql
git commit -m "feat(auth-infrastructure): 补充 user/credential/role_template/role_visibility/assignment 表 DDL"
```

---

### Task 14-18: 实现 user/credential/role/assignment 的 DO/Mapper/Converter/RepositoryImpl

这五个聚合根的实现模式与第一批相同，每个聚合根包含 4 个文件（DO + Mapper + Converter + RepositoryImpl）。为节省篇幅，此处概括：

**Task 14**: UserDO + UserMapper + UserConverter + UserRepositoryImpl
- 注意 `UserAggregate.restore` 方法命名（非 reconstitute）
- 注意 `IdentityDocument` 值对象的拆列映射

**Task 15**: CredentialDO + CredentialMapper + CredentialConverter + CredentialRepositoryImpl
- 单表存储，用 `credential_type` 区分子类
- Converter 需根据 `credential_type` 字段决定构造 `PasswordCredential` 或 `UKeyCredential`
- `applicable_channels` Set<枚举> → JSON 序列化

**Task 16**: RoleTemplateDO + RoleTemplateMapper + RoleTemplateConverter + RoleTemplateRepositoryImpl
- `permissions` Set<Permission> → JSON 序列化
- 注意 `scopeValue` 可能为 null（GLOBAL 维度）

**Task 17**: RoleVisibilityDO + RoleVisibilityMapper + RoleVisibilityConverter + RoleVisibilityRepositoryImpl
- 值对象表，无聚合根 ID（用 BIGSERIAL 自增主键）
- 注意 `RoleVisibilityRepository` 不继承基类

**Task 18**: AssignmentDO + AssignmentMapper + AssignmentConverter + AssignmentRepositoryImpl
- 注意 `AgentIdentityAssignment` 不继承 Repository 基类
- 注意 `roleCode` 字段可变（非 final）

每个 Task 遵循 TDD：先写测试，再实现。但 infrastructure 层主要是 CRUD 胶水代码，测试可简化为集成测试。

- [ ] 每个 Task 结束后 Commit

---

### Task 19: 第二批编译验证

- [ ] **Step 1: 安装 auth-domain（含新增 reconstitute）到本地仓库**

```bash
mvn -f auth-service/auth-domain/pom.xml install -DskipTests --no-transfer-progress
```

- [ ] **Step 2: 编译 auth-infrastructure**

```bash
mvn -f auth-service/auth-infrastructure/pom.xml compile --no-transfer-progress
```

Expected: BUILD SUCCESS

---

## 第三批：authorization 域（Grant）+ 核心 SPI

### Task 20: 补充 t_auth_grant 表 DDL

- [ ] **Step 1: 在 schema-pg.sql 追加 Grant 表**

```sql
CREATE TABLE IF NOT EXISTS t_auth_grant (
    id              VARCHAR(32)   NOT NULL,
    subject_type    VARCHAR(32)   NOT NULL,
    subject_value   JSONB,
    scope_rules     JSONB         NOT NULL,
    permissions     JSONB         NOT NULL,
    grant_type      VARCHAR(32)   NOT NULL,
    origin          VARCHAR(32)   NOT NULL,
    effect          VARCHAR(16)   NOT NULL,
    source_plan_no  VARCHAR(32),
    target_plan_no  VARCHAR(32),
    status          VARCHAR(16)   NOT NULL,
    validity_start  TIMESTAMP,
    validity_end    TIMESTAMP,
    created_by      VARCHAR(64)   NOT NULL,
    create_time     TIMESTAMP     NOT NULL,
    updated_by      VARCHAR(64),
    update_time     TIMESTAMP,
    deleted         BOOLEAN       NOT NULL DEFAULT FALSE,
    version         INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_t_auth_grant PRIMARY KEY (id)
);
CREATE INDEX idx_t_auth_grant_status ON t_auth_grant (status, effect) WHERE deleted = FALSE;
CREATE INDEX idx_t_auth_grant_subject ON t_auth_grant (subject_type) WHERE deleted = FALSE;
```

- [ ] **Step 2: schema-mysql.sql 追加对应 MySQL DDL**

- [ ] **Step 3: Commit**

---

### Task 21: 实现 GrantDO + GrantMapper + GrantConverter + GrantRepositoryImpl

**复杂映射点**：
- `GrantSubject` sealed interface → `subject_type` + `subject_value`(JSON)
- `scopeRules` List<ScopeRule> → JSON
- `permissions` Set<Permission> → JSON
- `validityPeriod` → 2 列

**GrantRepository 自定义方法**：
- `findActiveCapabilityGrants(LocalDateTime): List<Grant>`
- `findCandidateSubjectGrants(UserNo, LocalDateTime): List<Grant>`
- `findById(GrantId): Optional<Grant>`
- `save(Grant)`

- [ ] 实现 4 个文件，Commit

---

### Task 22: 实现 GrantActivationPolicy（默认策略）

**Files:**
- Create: `auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/authorization/spi/DefaultGrantActivationPolicy.java`

**Interfaces:**
- Consumes: `GrantActivationPolicy` SPI 接口
- Produces: `DefaultGrantActivationPolicy` 实现

策略规则：
- `origin == HQ_CONFIG && grantType == BASE` → 不需审批
- `origin == PLAN_DELEGATE` → 需审批
- `origin == CUSTOMER_TO_AGENT` → 需审批
- 其他 → 不需审批

```java
package com.pension.permission.infrastructure.authorization.spi;

import com.pension.permission.domain.authorization.enumeration.GrantOrigin;
import com.pension.permission.domain.authorization.enumeration.GrantType;
import com.pension.permission.domain.authorization.spi.GrantActivationPolicy;
import org.springframework.stereotype.Component;

@Component
public class DefaultGrantActivationPolicy implements GrantActivationPolicy {

    @Override
    public boolean requiresApproval(GrantOrigin origin, GrantType grantType) {
        return switch (origin) {
            case PLAN_DELEGATE, CUSTOMER_TO_AGENT -> true;
            case HQ_CONFIG -> false;
        };
    }
}
```

- [ ] Commit

---

### Task 23: 实现 PlanMembershipLookup（基于 AssignmentRepository）

**Files:**
- Create: `auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/authorization/spi/AssignmentBasedPlanMembershipLookup.java`

**Interfaces:**
- Consumes: `PlanMembershipLookup` SPI 接口、`AssignmentRepository`、`ProductGateway`（用于客户层级关系）
- Produces: `AssignmentBasedPlanMembershipLookup` 实现

```java
package com.pension.permission.infrastructure.authorization.spi;

import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.spi.PlanMembershipLookup;
import com.pension.permission.domain.assignment.aggregate.AgentIdentityAssignment;
import com.pension.permission.domain.assignment.repository.AssignmentRepository;
import com.pension.permission.domain.authorization.enumeration.AssignmentScopeDimension;
import com.pension.permission.domain.product.ProductGateway;
import com.example.shared.identifier.contract.PlanNo;
import com.example.shared.identifier.contract.CustomerNo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AssignmentBasedPlanMembershipLookup implements PlanMembershipLookup {

    private final AssignmentRepository assignmentRepository;
    private final ProductGateway productGateway;

    @Override
    public boolean isMemberOf(UserNo userNo, PlanNo planId) {
        List<AgentIdentityAssignment> assignments = assignmentRepository.findActiveByAccount(userNo);
        for (AgentIdentityAssignment assignment : assignments) {
            if (matchesPlan(assignment, planId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean hasRole(UserNo userNo, PlanNo planId, com.pension.permission.types.RoleCode roleCode) {
        List<AgentIdentityAssignment> assignments = assignmentRepository.findActiveByAccount(userNo);
        for (AgentIdentityAssignment assignment : assignments) {
            if (assignment.roleCode().equals(roleCode) && matchesPlan(assignment, planId)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesPlan(AgentIdentityAssignment assignment, PlanNo planId) {
        if (assignment.scopeDimension() == AssignmentScopeDimension.GLOBAL) {
            return true;
        }
        if (assignment.scopeDimension() == AssignmentScopeDimension.PLAN) {
            return planId.value().equals(assignment.scopeValue());
        }
        if (assignment.scopeDimension() == AssignmentScopeDimension.CUSTOMER) {
            // 通过 ProductGateway 查询计划所属客户，匹配 scopeValue
            // 简化实现：直接比较
            return true;
        }
        return false;
    }
}
```

**注意**：`matchesPlan` 中的 CUSTOMER 维度匹配需通过 `ProductGateway` 查询计划所属客户。实际实现时需补充查询逻辑。

- [ ] Commit

---

### Task 24: 实现 LoginTokenService（Sa-Token）

**Files:**
- Create: `auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/channel/spi/SaTokenLoginTokenService.java`

**Interfaces:**
- Consumes: `LoginTokenService` SPI 接口、Sa-Token（`StpUtil`）
- Produces: `SaTokenLoginTokenService` 实现

```java
package com.pension.permission.infrastructure.channel.spi;

import cn.dev33.satoken.stp.StpUtil;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.enumeration.AnnuityChannel;
import com.pension.permission.domain.channel.spi.LoginTokenService;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SaTokenLoginTokenService implements LoginTokenService {

    @Override
    public String issueToken(UserNo accountId, AnnuityChannel channel) {
        StpUtil.login(accountId.value());
        return StpUtil.getTokenValue();
    }

    @Override
    public Optional<UserNo> verifyToken(String token) {
        if (!StpUtil.getTokenValue().equals(token)) {
            return Optional.empty();
        }
        Object loginId = StpUtil.getLoginIdByToken(token);
        if (loginId == null) {
            return Optional.empty();
        }
        return Optional.of(UserNo.of(loginId.toString()));
    }

    @Override
    public void invalidateToken(String token) {
        StpUtil.logoutByTokenValue(token);
    }

    @Override
    public void invalidateAllTokensOf(UserNo accountId) {
        StpUtil.logout(accountId.value());
    }
}
```

- [ ] Commit

---

### Task 25: 第三批编译验证

- [ ] **Step 1: 编译验证**

```bash
mvn -f auth-service/auth-infrastructure/pom.xml compile --no-transfer-progress
```

Expected: BUILD SUCCESS

---

## 第四批：ProductGateway（外部服务防腐层）

### Task 26: 实现 ProductGateway 桩实现

**Files:**
- Create: `auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/product/gateway/StubProductGateway.java`

**Interfaces:**
- Consumes: `ProductGateway` SPI 接口（auth-domain）
- Produces: `StubProductGateway` 桩实现（返回空集合或抛 UnsupportedOperationException）

```java
package com.pension.permission.infrastructure.product.gateway;

import com.example.shared.identifier.contract.CustomerNo;
import com.example.shared.identifier.contract.PlanNo;
import com.example.shared.identifier.contract.ProductNo;
import com.pension.permission.domain.product.ProductGateway;
import com.pension.permission.domain.product.snapshot.CustomerSnapshot;
import com.pension.permission.domain.product.snapshot.PlanSnapshot;
import com.pension.permission.domain.product.snapshot.ProductSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class StubProductGateway implements ProductGateway {

    @Override
    public Optional<PlanSnapshot> findPlan(PlanNo planId) {
        log.warn("StubProductGateway.findPlan 桩实现被调用，返回空。后续需接入真实外部服务");
        return Optional.empty();
    }

    @Override
    public Optional<ProductSnapshot> findProduct(ProductNo productId) {
        log.warn("StubProductGateway.findProduct 桩实现被调用，返回空");
        return Optional.empty();
    }

    @Override
    public Optional<CustomerSnapshot> findCustomer(CustomerNo customerId) {
        log.warn("StubProductGateway.findCustomer 桩实现被调用，返回空");
        return Optional.empty();
    }

    @Override
    public List<CustomerNo> ancestorsOf(CustomerNo customerId) {
        log.warn("StubProductGateway.ancestorsOf 桩实现被调用，返回空列表");
        return Collections.emptyList();
    }

    @Override
    public List<CustomerNo> descendantsOf(CustomerNo customerId) {
        log.warn("StubProductGateway.descendantsOf 桩实现被调用，返回空列表");
        return Collections.emptyList();
    }

    @Override
    public List<PlanNo> plansOfCustomer(CustomerNo customerId, boolean includeDescendants) {
        log.warn("StubProductGateway.plansOfCustomer 桩实现被调用，返回空列表");
        return Collections.emptyList();
    }

    @Override
    public List<PlanNo> plansOfProduct(ProductNo productId) {
        log.warn("StubProductGateway.plansOfProduct 桩实现被调用，返回空列表");
        return Collections.emptyList();
    }
}
```

**注意**：需根据 `ProductGateway` 接口的实际方法签名调整。读取 `ProductGateway.java` 确认。

- [ ] **Step 1: 读取 ProductGateway.java 确认方法签名**

- [ ] **Step 2: 创建 StubProductGateway**

- [ ] **Step 3: Commit**

```bash
git add auth-service/auth-infrastructure/src/main/java/com/pension/permission/infrastructure/product/gateway/StubProductGateway.java
git commit -m "feat(auth-infrastructure): 新增 ProductGateway 桩实现"
```

---

### Task 27: 最终编译+测试验证

- [ ] **Step 1: 安装全部 auth 模块到本地仓库**

```bash
mvn -f auth-service/auth-types/pom.xml install -DskipTests --no-transfer-progress
mvn -f auth-service/auth-domain/pom.xml install -DskipTests --no-transfer-progress
mvn -f auth-service/auth-application/pom.xml install -DskipTests --no-transfer-progress
mvn -f auth-service/auth-infrastructure/pom.xml install -DskipTests --no-transfer-progress
```

- [ ] **Step 2: 运行 auth-domain 测试确保不破坏**

```bash
mvn -f auth-service/auth-domain/pom.xml test --no-transfer-progress
```

Expected: BUILD SUCCESS，17/17 测试通过

- [ ] **Step 3: 编译 auth-infrastructure 完整验证**

```bash
mvn -f auth-service/auth-infrastructure/pom.xml compile --no-transfer-progress
```

Expected: BUILD SUCCESS

- [ ] **Step 4: 编译 auth-application 验证 SPI 注入**

```bash
mvn -f auth-service/auth-application/pom.xml compile --no-transfer-progress
```

Expected: BUILD SUCCESS

- [ ] **Step 5: 更新 progress ledger**

```bash
更新 docs/superpowers/plans/2026-08-05-secondary-auth-session-ledger.md
追加 infrastructure 实现完成记录
```

- [ ] **Step 6: 最终 Commit**

```bash
git add docs/superpowers/plans/2026-08-05-secondary-auth-session-ledger.md
git commit -m "docs(auth-infrastructure): 更新 ledger 记录 infrastructure 实现完成"
```

---

## 文件结构总览

```
auth-service/auth-infrastructure/
├── pom.xml (Modified)
└── src/main/
    ├── java/com/pension/permission/infrastructure/
    │   ├── channel/
    │   │   ├── converter/
    │   │   │   ├── SessionConverter.java
    │   │   │   └── SecondaryAuthSessionConverter.java
    │   │   ├── entity/
    │   │   │   ├── SessionDO.java
    │   │   │   └── SecondaryAuthSessionDO.java
    │   │   ├── mapper/
    │   │   │   ├── SessionMapper.java
    │   │   │   └── SecondaryAuthSessionMapper.java
    │   │   ├── repository/
    │   │   │   ├── SessionRepositoryImpl.java
    │   │   │   └── SecondaryAuthSessionRepositoryImpl.java
    │   │   └── spi/
    │   │       ├── BCryptVerificationCodeHasher.java
    │   │       └── SaTokenLoginTokenService.java
    │   ├── user/
    │   │   ├── converter/UserConverter.java
    │   │   ├── entity/UserDO.java
    │   │   ├── mapper/UserMapper.java
    │   │   └── repository/UserRepositoryImpl.java
    │   ├── credential/
    │   │   ├── converter/CredentialConverter.java
    │   │   ├── entity/CredentialDO.java
    │   │   ├── mapper/CredentialMapper.java
    │   │   └── repository/CredentialRepositoryImpl.java
    │   ├── role/
    │   │   ├── converter/
    │   │   │   ├── RoleTemplateConverter.java
    │   │   │   └── RoleVisibilityConverter.java
    │   │   ├── entity/
    │   │   │   ├── RoleTemplateDO.java
    │   │   │   └── RoleVisibilityDO.java
    │   │   ├── mapper/
    │   │   │   ├── RoleTemplateMapper.java
    │   │   │   └── RoleVisibilityMapper.java
    │   │   └── repository/
    │   │       ├── RoleTemplateRepositoryImpl.java
    │   │       └── RoleVisibilityRepositoryImpl.java
    │   ├── assignment/
    │   │   ├── converter/AssignmentConverter.java
    │   │   ├── entity/AssignmentDO.java
    │   │   ├── mapper/AssignmentMapper.java
    │   │   └── repository/AssignmentRepositoryImpl.java
    │   ├── authorization/
    │   │   ├── converter/GrantConverter.java
    │   │   ├── entity/GrantDO.java
    │   │   ├── mapper/GrantMapper.java
    │   │   ├── repository/GrantRepositoryImpl.java
    │   │   └── spi/
    │   │       ├── DefaultGrantActivationPolicy.java
    │   │       └── AssignmentBasedPlanMembershipLookup.java
    │   └── product/
    │       └── gateway/StubProductGateway.java
    └── resources/
        ├── application.yml (Modified)
        ├── schema-pg.sql (Modified)
        └── schema-mysql.sql (Modified)

auth-service/auth-domain/ (Modified - 仅 Task 12)
└── src/main/java/com/pension/permission/domain/credential/aggregate/
    ├── PasswordCredential.java (Modified - 补 reconstitute)
    └── UKeyCredential.java (Modified - 补 reconstitute)
```

---

## Self-Review

### Spec coverage
- ✅ 8 个 RepositoryImpl 全部覆盖（Task 8/9/14/15/16/17/18/21）
- ✅ 5 个 SPI 全部覆盖（Task 10/22/23/24/26）
- ✅ 6 张缺失表 DDL 全部覆盖（Task 13/20）
- ✅ Credential reconstitute 补齐（Task 12）
- ✅ 四批编译验证（Task 11/19/25/27）

### Placeholder scan
- Task 14-18 概括描述但未展开具体代码 — 这些聚合根实现模式与第一批相同，可在 subagent dispatch 时按第一批模板生成
- Task 21 概括描述 — 同上
- 部分方法签名需在实现时根据真实源码调整（已在 Task 中标注"读取确认"）

### Type consistency
- SessionConverter 用 `SessionId.of(idStr)`
- SecondaryAuthSessionConverter 用 `SecondaryAuthSessionId.of(idStr)`
- 所有 ID 转换统一使用 `.of()` 静态工厂方法
- `Version.of(intValue)` 用于版本号重建
