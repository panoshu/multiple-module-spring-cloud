# SecondaryAuthSession 独立聚合根设计

> 日期：2026-08-05
> 范围：auth-service（com.pension.permission）
> 状态：设计待评审

## 一、背景与目标

### 1.1 问题背景

auth-service 当前的网点二次授权实现存在重大安全缺陷：

1.
**单步冒充风险**：[DefaultSecondaryAuthService.elevate ()](file:///d:\WorkSpace\Trae\multiple-module-spring-cloud\auth-service\auth-domain\src\main\java\com\pension\permission\domain\channel\service\DefaultSecondaryAuthService.java)
中，柜员只要持有客户 UKey + 经办手机号， **立即**获得经办人身份， **经办人本人不需要确认**。UKey 被盗 + 手机号泄露即可完全冒充经办人。

2.
**缺少状态机**：[Session 聚合根](file:///d:\WorkSpace\Trae\multiple-module-spring-cloud\auth-service\auth-domain\src\main\java\com\pension\permission\domain\channel\aggregate\Session.java)
的二次授权状态隐藏在 `EffectiveIdentity` 值对象中，无法表达"待授权/已拒绝/已撤销"等语义。

3. **审计能力弱**：只有 `SessionIdentityElevated` 一个事件，缺失发起、拒绝、撤销、超时等完整审计链。

4. **不变量未保护**：无法表达"一柜员同时只能有一个活跃二次授权会话"这一关键业务约束。

### 1.2 设计目标

- 引入 `SecondaryAuthSession` 独立聚合根，支持 **短信验证码两段式授权**
- 设计完整状态机（6 状态），覆盖二次授权全生命周期
- 冻结权限快照，支持快照 + TTL + 事件驱动的权限判定策略
- 提供 `SecondaryAuthStrategy` SPI 扩展点，支持未来多种授权方式
- 保持 auth-service 现有 `Grant` 模型与 `DENY 优先` 安全原则不被破坏

### 1.3 非目标

- 不引入 `PermissionCombinationStrategy` SPI（另行评估）
- 不重构 `Grant` 聚合根
- 不修改 `AuthorizationEngine` 的两层 AND 判定逻辑
- 不修改 Session 聚合根为多渠道继承结构（保持单一聚合根）

## 二、多渠道 Session 拆分评估

### 2.1 拆分是否增强安全性

**结论：不增强**。渠道隔离的真正防线在三层：

1. **Token 层**：sa-token 三套 `StpLogic`（`satoken-internet` / `satoken-hq` / `satoken-branch`）各自独立，互联网 token
   无法读取网点会话
2. **网关层**：demo-gateway 路径前缀 `/internet/**` / `/hq/**` / `/branch/**` 严格隔离
3. **Repository 查询层**：`SessionRepository.findByTokenAndChannel(token, channel)` 强制带 channel 条件

聚合根层面的继承拆分不会增加任何安全性——攻击者若绕过 token 和网关，无论 Session 是基类还是子类都能查到任何记录。

### 2.2 拆分的代价

| 代价              | 说明                                                                                                                                  |
|-------------------|---------------------------------------------------------------------------------------------------------------------------------------|
| 违反 DDD 原则     | 聚合根不应使用继承多态，[03-领域模型约束](file:///d:\WorkSpace\Trae\multiple-module-spring-cloud\.trae\rules\03-领域模型约束.md) 隐含 |
| 共享字段重复      | primaryAccountId / channel / effectiveIdentity / expiresAt / status 在三个子类重复                                                    |
| 多态分派反模式    | 应用层 `if (session instanceof BranchSession)` 违反开闭原则                                                                           |
| Repository 复杂化 | MyBatis-Flex 对聚合根继承支持差                                                                                                       |

### 2.3 最终决策

**保持单一 Session 聚合根**，渠道隔离通过：

- Token 严格隔离（已有设计）
- 网关路径前缀严格隔离（已有设计）
- Repository 查询强制带 channel 条件（实现层约束）
- Session 内部通过 channel + 状态校验防止误操作（如 `applySecondaryAuth` 仅允许 BRANCH 渠道）

## 三、短信验证码授权流程

### 3.1 交互流程

```
柜员（网点终端）              系统                       经办人（手机）

1. 发起授权 ──────────────►
   (柜员凭证 +
    经办账号ID +
    目标计划)

                            2. 校验柜员身份
                            3. 根据经办账号ID读取其当前手机号
                            4. 校验经办人在该计划
                               有生效的身份分配
                            5. 生成 6 位验证码
                            6. 哈希存储验证码
                            7. 创建 Session(PENDING)
                            8. 发送短信 ──────────────────────►
                                              ◄──────────────  收到验证码

9. 输入验证码 ─────────────►
   (sessionId +
    验证码)

                            10. 校验验证码哈希
                            11. 校验未过期/未耗尽
                            12. 冻结权限快照
                            13. 设置 EffectiveIdentity
                            14. 状态 PENDING → AUTHORIZED
                            15. 联动更新柜员 Session

◄──────────────────── 授权完成，可办理业务
```

### 3.2 安全机制

| 机制             | 说明                                           |
|------------------|------------------------------------------------|
| 验证码哈希存储   | 数据库不存明文，使用 BCrypt 哈希               |
| 验证码时效       | 默认 5 分钟，超时自动失效                      |
| 重试次数限制     | 默认 3 次，耗尽后会话状态变为 EXPIRED          |
| 一次性使用       | 验证成功后立即失效                             |
| 柜员活跃会话唯一 | 同一柜员同时只能有一个 PENDING/AUTHORIZED 会话 |
| 完整审计         | 发起、发送、确认、拒绝、撤销、超时全程事件记录 |

### 3.3 VerificationCode 值对象

```java
public record VerificationCode(
    String hashedCode,                  // BCrypt 哈希后的验证码
    LocalDateTime sentAt,               // 发送时间
    LocalDateTime expiresAt,            // 过期时间
    int remainingAttempts              // 剩余重试次数
) implements ValueObject {

    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isExhausted() {
        return remainingAttempts <= 0;
    }

    public boolean matches(String rawCode) {
        return BCrypt.verifypw(rawCode, hashedCode);
    }

    public VerificationCode onAttemptFailed() {
        return new VerificationCode(
            hashedCode, sentAt, expiresAt, remainingAttempts - 1);
    }
}
```

## 四、SecondaryAuthSession 聚合根设计

### 4.1 字段设计

```
SecondaryAuthSession (聚合根)
├── id: SecondaryAuthSessionId
├── tellerAccountId: UserNo               // 柜员（发起方）
├── approverAccountId: UserNo             // 经办人（被授权方，PENDING 时为 null）
├── credentialOwner: CredentialOwner      // 发起时使用的凭证持有者
├── approverMobile: Mobile                // 经办人手机号（发送验证码用）
├── planId: PlanNo                        // 目标计划
├── verificationCode: VerificationCode    // 验证码值对象（PENDING 时持有）
├── effectiveIdentity: EffectiveIdentity  // 授权完成后的身份分叉（AUTHORIZED 时持有）
├── permissionSnapshot: PermissionSnapshot // 权限快照（AUTHORIZED 时冻结）
├── status: SecondaryAuthStatus           // 状态机
├── initiatedAt: LocalDateTime            // 发起时间
├── authorizedAt: LocalDateTime           // 授权完成时间
├── expiresAt: LocalDateTime              // 会话过期时间（业务层，如 2 小时）
├── revokeReason: String                  // 撤销原因（REVOKED 时填充）
├── created_by / created_at / updated_by / updated_at / deleted / version  // 通用字段
```

### 4.2 PermissionSnapshot 值对象

```java
public record PermissionSnapshot(
    Set<Permission> permissions,
    LocalDateTime frozenAt,
    LocalDateTime expiresAt              // TTL 过期时间，默认 frozenAt + 30s
) implements ValueObject {

    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    public boolean contains(Permission permission) {
        return permissions.contains(permission);
    }
}
```

### 4.3 状态机

```
                                  ┌──────────────┐
                                  │   PENDING    │ ◄── 柜员发起（生成验证码、发短信）
                                  │  (待授权)     │
                                  └──────┬───────┘
                                         │
              ┌──────────────────────────┼──────────────────────────┐
              │                          │                          │
              ▼                          ▼                          ▼
      ┌──────────────┐          ┌──────────────┐          ┌──────────────┐
      │  AUTHORIZED  │          │   REJECTED   │          │   EXPIRED    │
      │  (已授权)     │          │  (已拒绝)     │          │  (已超时)     │
      └──────┬───────┘          └──────────────┘          └──────────────┘
             │                       (终态)                    (终态)
             │
      ┌──────┴──────────────────┐
      │                         │
      ▼                         ▼
┌──────────────┐         ┌──────────────┐
│   REVOKED    │         │   CLOSED     │
│  (已撤销)     │         │  (已关闭)     │
│  (经办撤销)   │         │  (柜员登出)   │
└──────────────┘         └──────────────┘
   (终态)                   (终态)
```

### 4.4 状态流转规则

| 当前状态   | 目标状态   | 触发条件                           |
|------------|------------|------------------------------------|
| （初始）   | PENDING    | 柜员发起，生成验证码，发短信       |
| PENDING    | AUTHORIZED | 验证码校验通过，冻结快照           |
| PENDING    | REJECTED   | 验证码重试次数耗尽（自动）         |
| PENDING    | EXPIRED    | 待授权超时（默认 5 分钟）          |
| AUTHORIZED | REVOKED    | 经办人主动撤销 / 紧急收权          |
| AUTHORIZED | CLOSED     | 柜员登出 / 会话过期（默认 2 小时） |
| AUTHORIZED | EXPIRED    | 快照 TTL 过期（默认 30 秒）        |

非法状态转移抛 `DomainException`。

### 4.5 关键不变量

1. **柜员活跃会话唯一性**：同一柜员同时只能有一个 `PENDING` 或 `AUTHORIZED` 状态的会话（Repository 层部分唯一索引保证）
2. **状态机严格流转**：非法状态转移抛 `DomainException`
3. **权限快照不可变性**：`AUTHORIZED` 后 `permissionSnapshot` 不可修改
4. **EffectiveIdentity 时序**：仅 `AUTHORIZED` 状态可获取
5. **验证码一次性**：验证成功后立即清空 `verificationCode` 字段
6. **渠道限制**：发起柜员必须是 `BANK_BRANCH` 渠道

### 4.6 行为方法

```java
public class SecondaryAuthSession extends AggregateRoot<SecondaryAuthSessionId> {

    // 1. 柜员发起（PENDING）
    public static SecondaryAuthSession initiate(
        SecondaryAuthSessionId id,
        UserNo tellerAccountId,
        CredentialOwner credentialOwner,
        UserNo approverAccountId,       // 应用层根据经办人账号查询其当前手机号
        Mobile approverMobile,          // 从经办人账号读取，非柜员输入
        PlanNo planId,
        VerificationCode verificationCode,
        Duration pendingTimeout,        // 默认 5 分钟，可配置
        Duration sessionTimeout,       // 默认 2 小时，可配置
        UserNo operator);

    // 2. 柜员输入验证码确认（PENDING → AUTHORIZED）
    //    委托 SecondaryAuthStrategy.authorize() 调用
    public void authorize(
        String rawCode,
        PermissionSnapshot snapshot,
        EffectiveIdentity identity,
        UserNo operator);

    // 3. 重发验证码（PENDING，重置 verificationCode）
    public void resendVerificationCode(
        VerificationCode newCode,
        UserNo operator);

    // 4. 验证码校验失败（PENDING，剩余次数减 1，耗尽则自动 REJECTED）
    public void recordFailedAttempt(UserNo operator);

    // 5. 撤销（AUTHORIZED → REVOKED）
    public void revoke(UserNo revoker, String reason);

    // 6. 柜员登出（AUTHORIZED → CLOSED）
    public void close(UserNo operator);

    // 7. 超时过期（PENDING → EXPIRED / AUTHORIZED → EXPIRED）
    public void expireIfTimeout(LocalDateTime now);

    // 8. 查询方法
    public boolean isEffectiveAt(LocalDateTime now);
    public boolean authorizes(UserNo operatorId);
    public PermissionSnapshot snapshotOrThrow();
    public EffectiveIdentity effectiveIdentityOrThrow();
    public VerificationCode verificationCodeOrThrow();
}
```

### 4.7 领域事件

| 事件                     | 触发时机              | 异步订阅者动作                                                      |
|--------------------------|-----------------------|---------------------------------------------------------------------|
| `SecondaryAuthInitiated` | 柜员发起              | 发送短信验证码到经办人手机                                          |
| `SecondaryAuthCompleted` | 验证码确认通过        | 更新柜员 Session（写入 secondaryAuthSessionId + effectiveIdentity） |
| `SecondaryAuthRejected`  | 验证码重试耗尽        | 通知柜员拒绝结果                                                    |
| `SecondaryAuthRevoked`   | 经办人撤销 / 紧急收权 | 踢柜员下线 + 清缓存 + 审计                                          |
| `SecondaryAuthExpired`   | 超时过期              | 通知柜员 + 清缓存                                                   |
| `SecondaryAuthClosed`    | 柜员登出              | 审计记录                                                            |

事件必须实现 `DomainEvent` 接口，使用 record 类型，提供 static `of()`
方法（遵循 [03-领域模型约束](file:///d:\WorkSpace\Trae\multiple-module-spring-cloud\.trae\rules\03-领域模型约束.md) 第七节）。

## 五、Session 聚合根改造

### 5.1 字段变更

```java
public class Session extends AggregateRoot<SessionId> {
    private final UserNo primaryAccountId;
    private final AnnuityChannel channel;

    // 【保留】effectiveIdentity，作为 SecondaryAuthSession 授权后的输出写入
    private EffectiveIdentity effectiveIdentity;

    // 【新增】引用二次授权会话
    private SecondaryAuthSessionId secondaryAuthSessionId;

    private PlanNo selectedPlanId;
    private final LocalDateTime expiresAt;
    private SessionStatus status;

    // 【移除】elevateIdentity 方法（原一步提升逻辑）
    // 【新增】applySecondaryAuth：应用二次授权结果
    // 【新增】clearSecondaryAuth：撤销时清除引用
}
```

### 5.2 行为变更

```java
// 应用二次授权结果（监听 SecondaryAuthCompleted 事件后调用）
public void applySecondaryAuth(
    SecondaryAuthSessionId sessionId,
    EffectiveIdentity identity,
    UserNo operator) {
    if (this.channel != AnnuityChannel.BANK_BRANCH) {
        throw new DomainException("仅网点渠道支持二次授权");
    }
    if (this.secondaryAuthSessionId != null) {
        throw new DomainException("当前会话已绑定二次授权，请先撤销");
    }
    this.secondaryAuthSessionId = sessionId;
    this.effectiveIdentity = identity;
    registerDomainEvent(SessionIdentityElevated.of(...));
}

// 撤销二次授权（监听 SecondaryAuthRevoked 事件后调用）
public void clearSecondaryAuth(UserNo operator) {
    if (this.channel != AnnuityChannel.BANK_BRANCH) return;
    this.secondaryAuthSessionId = null;
    this.effectiveIdentity = EffectiveIdentity.direct(this.primaryAccountId);
    // 不发事件，撤销事件由 SecondaryAuthSession 发起
}
```

## 六、权限判定策略（快照 + TTL + 事件）

### 6.1 判定流程

```
柜员办理业务请求
  │
  ▼
网关校验 satoken-branch token
  │
  ▼
业务服务调用 PermissionQueryService.checkPermission
  │
  ▼
检查 Session 是否绑定 secondaryAuthSessionId
  │
  ├─ 否 → 拒绝（网点渠道未完成二次授权）
  │
  ▼ 是
加载 SecondaryAuthSession
  │
  ├─ 状态非 AUTHORIZED → 拒绝
  ├─ 快照已过期（snapshotExpiresAt < now）→ 拒绝，触发 EXPIRED 流转
  │
  ▼ 快照有效
检查本地 TTL 缓存（key = (tellerId, planId, sessionId)）
  │
  ├─ 命中 → 直接返回缓存结果
  │
  ▼ 未命中
从 snapshot 判定权限（O(1) 查找，不走完整 AuthorizationEngine）
  │
  ▼
写入本地 TTL 缓存（默认 30 秒）
  │
  ▼
返回判定结果
```

### 6.2 事件驱动失效

```
SecondaryAuthRevoked 事件
  → 清除柜员 Session 的 secondaryAuthSessionId
  → 清除本地 TTL 缓存
  → 踢柜员下线

AccountFrozen 事件
  → 撤销该经办人所有 AUTHORIZED 的 SecondaryAuthSession
  → 触发 SecondaryAuthRevoked 联动

PermissionRuleChanged 集成事件
  → 清除本地 TTL 缓存
  → 【关键】不立即重算快照——快照已冻结，仅清缓存，下次办理时从快照重新读
```

## 七、SPI 扩展点

### 7.1 SecondaryAuthStrategy 接口

```java
public interface SecondaryAuthStrategy {

    /** 支持的授权类型标识 */
    String supports();

    /** 发起授权 */
    SecondaryAuthSession initiate(SecondaryAuthContext context);

    /** 完成授权（校验验证码、冻结快照） */
    SecondaryAuthSession authorize(
    SecondaryAuthSession session,
        AuthorizeCommand command);
}
```

### 7.2 默认实现

`SmsCodeSecondaryAuthStrategy`：短信验证码授权（当前设计）

### 7.3 未来扩展

- `FaceRecognitionSecondaryAuthStrategy`：人脸识别授权
- `UKeySignatureSecondaryAuthStrategy`：UKey 签名授权

通过 `@ConditionalOnProperty(name = "auth.secondary-auth.strategy", havingValue = "sms-code")` 切换实现。

## 八、数据库表结构

### 8.1 PostgreSQL Schema

```sql
-- 二次授权会话主表
CREATE TABLE t_auth_secondary_auth_session (
    id                          VARCHAR(32)  NOT NULL,
    teller_account_id           VARCHAR(32)  NOT NULL,
    approver_account_id         VARCHAR(32),
    credential_owner_type       VARCHAR(32)  NOT NULL,
    credential_owner_id         VARCHAR(64)  NOT NULL,
    approver_mobile             VARCHAR(20)  NOT NULL,
    plan_id                     VARCHAR(32)  NOT NULL,
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

-- 部分索引：柜员活跃会话唯一性约束
CREATE UNIQUE INDEX uk_auth_secondary_auth_teller_active
    ON t_auth_secondary_auth_session (teller_account_id)
    WHERE deleted = FALSE AND status IN ('PENDING', 'AUTHORIZED');

-- 索引：查询经办人待确认会话
CREATE INDEX idx_auth_secondary_auth_approver_pending
    ON t_auth_secondary_auth_session (approver_account_id, status)
    WHERE deleted = FALSE AND status = 'PENDING';

-- 索引：查询经办人已授权会话（撤销时用）
CREATE INDEX idx_auth_secondary_auth_approver_authorized
    ON t_auth_secondary_auth_session (approver_account_id, status)
    WHERE deleted = FALSE AND status = 'AUTHORIZED';

-- 索引：超时清理任务
CREATE INDEX idx_auth_secondary_auth_expires
    ON t_auth_secondary_auth_session (expires_at, status)
    WHERE deleted = FALSE AND status IN ('PENDING', 'AUTHORIZED');

-- 索引：按计划查询（审计用）
CREATE INDEX idx_auth_secondary_auth_plan
    ON t_auth_secondary_auth_session (plan_id, status)
    WHERE deleted = FALSE;
```

### 8.2 MySQL Schema

```sql
CREATE TABLE t_auth_secondary_auth_session (
    id                          VARCHAR(32)  NOT NULL                  COMMENT '二次授权会话ID',
    teller_account_id           VARCHAR(32)  NOT NULL                  COMMENT '柜员账号ID',
    approver_account_id         VARCHAR(32)                            COMMENT '经办人账号ID',
    credential_owner_type       VARCHAR(32)  NOT NULL                  COMMENT '凭证持有者类型',
    credential_owner_id         VARCHAR(64)  NOT NULL                  COMMENT '凭证持有者ID',
    approver_mobile             VARCHAR(20)  NOT NULL                  COMMENT '经办人手机号',
    plan_id                     VARCHAR(32)  NOT NULL                  COMMENT '目标计划ID',
    verification_code_hash      VARCHAR(255)                           COMMENT 'BCrypt哈希验证码',
    verification_sent_at        DATETIME                               COMMENT '验证码发送时间',
    verification_expires_at    DATETIME                               COMMENT '验证码过期时间',
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
```

### 8.3 Session 表增量改造

```sql
-- PostgreSQL
ALTER TABLE t_auth_session
ADD COLUMN secondary_auth_session_id VARCHAR(32);

CREATE INDEX idx_auth_session_secondary_auth
    ON t_auth_session (secondary_auth_session_id)
    WHERE deleted = FALSE AND secondary_auth_session_id IS NOT NULL;

-- MySQL
ALTER TABLE t_auth_session
ADD COLUMN secondary_auth_session_id VARCHAR(32) COMMENT '二次授权会话ID';

CREATE INDEX idx_auth_session_secondary_auth
    ON t_auth_session (secondary_auth_session_id, deleted);
```

### 8.4 权限快照 JSON 格式

```json
{
  "frozenAt": "2026-08-05T10:30:00",
  "expiresAt": "2026-08-05T10:30:30",
  "permissions": [
    {"businessCode": "ANNUITY_CONTRIBUTION", "actionCode": "HANDLE"},
    {"businessCode": "ANNUITY_CONTRIBUTION", "actionCode": "QUERY"},
    {"businessCode": "ANNUITY_PAYMENT", "actionCode": "QUERY"}
  ]
}
```

## 九、应用层编排

### 9.1 SecondaryAuthAppService

```java
@Service
@AllArgsConstructor
public class SecondaryAuthAppService {
    private final SecondaryAuthSessionRepository sessionRepository;
    private final SecondaryAuthStrategy strategy;
    private final PermissionResolver permissionResolver;
    private final IdentityResolutionService identityResolutionService;
    private final SmsGateway smsGateway;
    private final SecondaryAuthConfig config;

    // 1. 柜员发起二次授权
    @Transactional
    public InitiateResult initiate(InitiateSecondaryAuthCommand cmd) {
        // 校验柜员活跃会话唯一性
        sessionRepository.findActiveByTeller(cmd.tellerId())
            .ifPresent(s -> {
                throw new BusinessException(SecondaryAuthErrorCode.ACTIVE_SESSION_EXISTS);
            });

        // 校验经办人在该计划有生效的身份分配，并读取其当前手机号
        ApproverInfo approverInfo = identityResolutionService
            .resolveApproverByAccount(cmd.approverAccountId(), cmd.planId());
        Mobile approverMobile = approverInfo.mobile();

        // 生成验证码（明文，仅在此方法作用域内）
        String rawCode = VerificationCodeGenerator.generate6Digit();
        VerificationCode code = VerificationCode.of(
            rawCode,
            LocalDateTime.now(),
            config.getPendingTimeout());

        // 委托策略发起
        SecondaryAuthSession session = strategy.initiate(buildContext(
            cmd, approverInfo, code));

        sessionRepository.save(session);

        // 发送短信（事务提交后发送，失败不回滚业务）
        // 通过事件驱动，SmsGateway 订阅 SecondaryAuthInitiated

        return new InitiateResult(session.id(), rawCode);
    }

    // 2. 柜员输入验证码确认
    @Transactional
    public void confirm(ConfirmSecondaryAuthCommand cmd) {
        SecondaryAuthSession session = sessionRepository
            .loadOrThrow(cmd.sessionId());

        if (session.isPendingExpired(LocalDateTime.now())) {
            session.expireIfTimeout(LocalDateTime.now());
            sessionRepository.save(session);
            throw new BusinessException(SecondaryAuthErrorCode.SESSION_EXPIRED);
        }

        // 校验验证码
        if (!session.verificationCode().matches(cmd.rawCode())) {
            session.recordFailedAttempt(cmd.operator());
            if (session.verificationCode().isExhausted()) {
                session.reject(cmd.operator());
            }
            sessionRepository.save(session);
            throw new BusinessException(SecondaryAuthErrorCode.INVALID_VERIFICATION_CODE);
        }

        // 冻结权限快照
        PermissionSnapshot snapshot = permissionResolver.resolve(
            session.approverAccountId(),
            session.planId(),
            config.getSnapshotTtl());

        EffectiveIdentity identity = new EffectiveIdentity(
            session.approverAccountId(),
            session.tellerAccountId(),
            true);

        // 委托策略完成授权
        strategy.authorize(session, new AuthorizeCommand(
            cmd.rawCode(), snapshot, identity, cmd.operator()));

        sessionRepository.save(session);
    }

    // 3. 重发验证码（PENDING 状态下，柜员可请求重发）
    @Transactional
    public void resendCode(ResendCodeCommand cmd) {
        SecondaryAuthSession session = sessionRepository.loadOrThrow(cmd.sessionId());
        String rawCode = VerificationCodeGenerator.generate6Digit();
        VerificationCode newCode = VerificationCode.of(
            rawCode,
            LocalDateTime.now(),
            config.getPendingTimeout());
        session.resendVerificationCode(newCode, cmd.operator());
        sessionRepository.save(session);
        // 通过事件驱动发送短信
    }

    // 4. 经办人撤销
    @Transactional
    public void revoke(RevokeSecondaryAuthCommand cmd) {
        SecondaryAuthSession session = sessionRepository.loadOrThrow(cmd.sessionId());
        session.revoke(cmd.operator(), cmd.reason());
        sessionRepository.save(session);
    }

    // 5. 柜员登出
    @Transactional
    public void close(CloseSecondaryAuthCommand cmd) {
        SecondaryAuthSession session = sessionRepository.loadOrThrow(cmd.sessionId());
        session.close(cmd.operator());
        sessionRepository.save(session);
    }
}
```

### 9.2 SessionAppService 事件监听

```java
@TransactionalEventListener(phase = AFTER_COMMIT)
public void onSecondaryAuthCompleted(SecondaryAuthCompleted event) {
    Session session = sessionRepository
        .findByPrimaryAccountId(event.tellerId())
        .orElseThrow();
    session.applySecondaryAuth(
        event.sessionId(),
        event.identity(),
        event.tellerId());
    sessionRepository.save(session);
}

@TransactionalEventListener(phase = AFTER_COMMIT)
public void onSecondaryAuthRevoked(SecondaryAuthRevoked event) {
    sessionRepository.findByPrimaryAccountId(event.tellerId())
        .ifPresent(session -> {
            session.clearSecondaryAuth(event.revoker());
            sessionRepository.save(session);
        });
    loginTokenService.invalidateAllTokensOf(event.tellerId());
}
```

## 十、配置项

### 10.1 application.yml

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

### 10.2 配置类

```java
@Configuration
@ConfigurationProperties(prefix = "auth.secondary-auth")
public class SecondaryAuthConfig {
    private String strategy = "sms-code";
    private Duration pendingTimeout = Duration.ofMinutes(5);
    private Duration sessionTimeout = Duration.ofHours(2);
    private Duration snapshotTtl = Duration.ofSeconds(30);
    private int verificationCodeLength = 6;
    private int verificationMaxAttempts = 3;
    private boolean smsEnabled = true;
    // getters/setters
}
```

## 十一、错误码

遵循 [08-错误码规范](file:///d:\WorkSpace\Trae\multiple-module-spring-cloud\.trae\rules\08-错误码规范.md)，在 SERVICE
域下新增 AUTH 模块缩写（与 iam-service 的 IAM 缩写区分，auth-service 使用 AUTH），错误码范围为 `SERVICE.AUTH.0001-0999`：

```java
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
    // constructor, getters
}
```

## 十二、实施影响清单

### 12.1 新增文件

| 类型               | 路径                                                                         |
|--------------------|------------------------------------------------------------------------------|
| 聚合根             | `auth-domain/.../channel/aggregate/SecondaryAuthSession.java`                |
| 值对象             | `auth-domain/.../channel/valueobject/VerificationCode.java`                  |
| 值对象             | `auth-domain/.../channel/valueobject/PermissionSnapshot.java`                |
| 枚举               | `auth-domain/.../channel/.../SecondaryAuthStatus.java`                       |
| Repository 接口    | `auth-domain/.../channel/repository/SecondaryAuthSessionRepository.java`     |
| SPI 接口           | `auth-domain/.../channel/service/SecondaryAuthStrategy.java`                 |
| 默认策略实现       | `auth-domain/.../channel/service/SmsCodeSecondaryAuthStrategy.java`          |
| 领域事件           | `auth-domain/.../channel/event/SecondaryAuthInitiated.java` 等 6 个          |
| 错误码             | `auth-domain/.../channel/errorcode/SecondaryAuthErrorCode.java`              |
| ApplicationService | `auth-application/.../channel/SecondaryAuthAppService.java`                  |
| Command            | `auth-application/.../channel/command/InitiateSecondaryAuthCommand.java` 等  |
| 配置类             | `auth-infrastructure/.../config/SecondaryAuthConfig.java`                    |
| Repository 实现    | `auth-infrastructure/.../repository/SecondaryAuthSessionRepositoryImpl.java` |
| DO                 | `auth-infrastructure/.../entity/SecondaryAuthSessionDO.java`                 |
| Mapper             | `auth-infrastructure/.../mapper/SecondaryAuthSessionMapper.java`             |
| Converter          | `auth-infrastructure/.../converter/SecondaryAuthSessionConverter.java`       |
| DDL                | `auth-infrastructure/src/main/resources/schema-pg.sql`（增量）               |
| DDL                | `auth-infrastructure/src/main/resources/schema-mysql.sql`（增量）            |

### 12.2 修改文件

| 文件                                                                                                                                                                                                             | 变更                                                                                                          |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| [Session.java](file:///d:\WorkSpace\Trae\multiple-module-spring-cloud\auth-service\auth-domain\src\main\java\com\pension\permission\domain\channel\aggregate\Session.java)                                       | 移除 `elevateIdentity`，新增 `secondaryAuthSessionId` 字段 + `applySecondaryAuth` / `clearSecondaryAuth` 方法 |
| [DefaultSecondaryAuthService.java](file:///d:\WorkSpace\Trae\multiple-module-spring-cloud\auth-service\auth-domain\src\main\java\com\pension\permission\domain\channel\service\DefaultSecondaryAuthService.java) | 重构为发起流程的辅助，核心逻辑移至 `SecondaryAuthStrategy`                                                    |
| Session 表 DDL                                                                                                                                                                                                   | 新增 `secondary_auth_session_id` 字段 + 索引                                                                  |
| 父 pom.xml                                                                                                                                                                                                       | 添加 `BCrypt` 依赖（如尚未引入）                                                                              |

### 12.3 删除文件

无删除，保持向后兼容（原 `elevateIdentity` 方法移除但 `EffectiveIdentity` 值对象保留）。

## 十三、与现有安全设计的协同

### 13.1 与 Grant 模型的关系

- **不修改 Grant 聚合根**：SecondaryAuthSession 是独立的会话聚合，不涉及授权记录
- **不修改 AuthorizationEngine**：权限判定仍走两层 AND + DENY 优先
- **快照来源**：`PermissionResolver` 内部调用 `EffectivePermissionService` 生成快照

### 13.2 与 outbox 紧急收权的协同

```
AccountFrozen 事件
  → outbox 写入（同一事务）
  → MQ 广播 iam.permission.rule.changed
  → 业务服务清缓存

同时：
  → SecondaryAuthAppService 监听 AccountFrozen
  → 撤销该经办人所有 AUTHORIZED 的 SecondaryAuthSession
  → 触发 SecondaryAuthRevoked
  → 踢柜员下线
```

### 13.3 与 LoginTokenService 的协同

- `SecondaryAuthCompleted` → 不踢人（柜员已登录，只是获得二次授权）
- `SecondaryAuthRevoked` → 踢柜员下线（`LoginTokenService.invalidateAllTokensOf`）
- `SecondaryAuthExpired` → 踢柜员下线

## 十四、测试策略

### 14.1 领域层单元测试

| 测试场景     | 覆盖点                                  |
|--------------|-----------------------------------------|
| 发起授权     | initiate 正常创建 PENDING 会话          |
| 验证码确认   | authorizeWithCode 正确流转到 AUTHORIZED |
| 验证码错误   | recordFailedAttempt 次数递减            |
| 验证码耗尽   | 自动流转到 REJECTED                     |
| 验证码过期   | expireIfTimeout 流转到 EXPIRED          |
| 非法状态转移 | 抛 DomainException                      |
| 撤销         | AUTHORIZED → REVOKED                    |
| 登出         | AUTHORIZED → CLOSED                     |
| 快照过期     | AUTHORIZED → EXPIRED                    |
| 不变量校验   | 柜员活跃会话唯一性（Repository 层）     |

### 14.2 应用层集成测试

| 测试场景     | 覆盖点                                  |
|--------------|-----------------------------------------|
| 完整授权流程 | 发起 → 收验证码 → 确认 → 办理业务       |
| 紧急收权     | 账号冻结 → 撤销会话 → 踢人下线          |
| 事件联动     | SecondaryAuthCompleted 更新柜员 Session |
| 权限快照 TTL | 30 秒后快照过期                         |

## 十五、未决事项

1. **验证码生成算法**：当前设计为 6 位数字，是否需要支持更复杂的验证码（如字母数字混合）？建议保持 6 位数字，简单且符合用户习惯。

2. **多设备登录场景**：同一柜员是否允许在多个网点终端同时登录？当前设计通过柜员活跃会话唯一性约束禁止。如需支持，需要调整不变量。

3. **验证码发送频率限制**：重发验证码是否需要限制频率（如 60 秒内只能重发一次）？建议在应用层增加频率限制，防止恶意触发短信发送。
