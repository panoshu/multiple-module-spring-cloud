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
    pending_expires_at          TIMESTAMP    NOT NULL,
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

-- ========== Session 表说明 ==========
-- 注意：Session 聚合根不再持久化到数据库表，改由 SessionRepositoryImpl 基于 Redis 实现。
-- 详见 SessionRepositoryImpl 与 auth-infrastructure/pom.xml 中 sa-token-redis-template 依赖。
-- Redis Key 设计：
--   auth:session:id:{sessionId}                       —— 主键索引，存储 Session JSON
--   auth:session:account:{accountId}:channel:{channel} —— 主账号+渠道索引（仅活跃会话）
-- Session.id = Sa-Token tokenValue，二者合一。

-- ========== User 表 ==========
-- 用户聚合根 UserAggregate 持久化

CREATE TABLE IF NOT EXISTS t_auth_user (
    id                    VARCHAR(32)   NOT NULL,
    user_type             VARCHAR(32)   NOT NULL,
    identity_type         VARCHAR(32)   NOT NULL,
    identity_number       VARCHAR(64)   NOT NULL,
    mobile                VARCHAR(32),
    email                 VARCHAR(128),
    telephone_area_code   VARCHAR(16),
    telephone_number      VARCHAR(32),
    telephone_extension   VARCHAR(16),
    address_country       VARCHAR(64),
    address_province      VARCHAR(64),
    address_city          VARCHAR(64),
    address_district      VARCHAR(64),
    address_detail        VARCHAR(255),
    postal_code           VARCHAR(16),
    status                VARCHAR(16)   NOT NULL,
    created_by            VARCHAR(64)   NOT NULL,
    create_time           TIMESTAMP     NOT NULL,
    updated_by            VARCHAR(64),
    update_time           TIMESTAMP,
    deleted               BOOLEAN       NOT NULL DEFAULT FALSE,
    version               INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_t_auth_user PRIMARY KEY (id)
);

COMMENT ON TABLE t_auth_user IS '用户表';
COMMENT ON COLUMN t_auth_user.user_type IS '用户类型: AGENT/OPERATOR/TELLER';
COMMENT ON COLUMN t_auth_user.identity_type IS '证件类型: ID_CARD/PASSPORT 等';
COMMENT ON COLUMN t_auth_user.status IS '用户状态: ACTIVE/FROZEN/DISABLED';

CREATE INDEX idx_t_auth_user_mobile
    ON t_auth_user (mobile)
    WHERE deleted = FALSE AND mobile IS NOT NULL;

CREATE INDEX idx_t_auth_user_status
    ON t_auth_user (status)
    WHERE deleted = FALSE;

CREATE UNIQUE INDEX uk_t_auth_user_identity
    ON t_auth_user (identity_type, identity_number)
    WHERE deleted = FALSE;

-- ========== Credential 表 ==========
-- Credential 聚合根（sealed abstract，子类 PasswordCredential + UKeyCredential）
-- 采用单表继承策略，credential_type 区分子类

CREATE TABLE IF NOT EXISTS t_auth_credential (
    id                    VARCHAR(32)   NOT NULL,
    credential_type       VARCHAR(32)   NOT NULL,
    owner_type            VARCHAR(32)   NOT NULL,
    owner_id              VARCHAR(64)   NOT NULL,
    applicable_channels   JSONB         NOT NULL,
    validity_start        TIMESTAMP,
    validity_end          TIMESTAMP,
    status                VARCHAR(16)   NOT NULL,
    -- PasswordCredential 专属字段
    user_no               VARCHAR(32),
    password_hash         VARCHAR(255),
    -- UKeyCredential 专属字段
    key_serial            VARCHAR(128),
    -- 通用字段
    created_by            VARCHAR(64)   NOT NULL,
    create_time           TIMESTAMP     NOT NULL,
    updated_by            VARCHAR(64),
    update_time           TIMESTAMP,
    deleted               BOOLEAN       NOT NULL DEFAULT FALSE,
    version               INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_t_auth_credential PRIMARY KEY (id)
);

COMMENT ON TABLE t_auth_credential IS '凭证表（PasswordCredential + UKeyCredential 单表存储）';
COMMENT ON COLUMN t_auth_credential.credential_type IS '凭证类型: PASSWORD/U_KEY';
COMMENT ON COLUMN t_auth_credential.owner_type IS '持有者类型: UserCredentialOwner/CustomerCredentialOwner/PlanCredentialOwner';
COMMENT ON COLUMN t_auth_credential.applicable_channels IS '适用渠道集合 JSON 数组';

CREATE INDEX idx_t_auth_credential_owner
    ON t_auth_credential (owner_type, owner_id)
    WHERE deleted = FALSE;

CREATE INDEX idx_t_auth_credential_type
    ON t_auth_credential (credential_type, status)
    WHERE deleted = FALSE;

CREATE UNIQUE INDEX uk_t_auth_credential_ukey_serial
    ON t_auth_credential (key_serial)
    WHERE deleted = FALSE AND key_serial IS NOT NULL;

-- ========== RoleTemplate 表 ==========
-- 角色权限模板聚合根 RoleTemplate 持久化

CREATE TABLE IF NOT EXISTS t_auth_role_template (
    id                VARCHAR(32)   NOT NULL,
    role_code         VARCHAR(64)   NOT NULL,
    scope_dimension   VARCHAR(32)   NOT NULL,
    scope_value       VARCHAR(64),
    permissions       JSONB         NOT NULL,
    status            VARCHAR(16)   NOT NULL,
    created_by        VARCHAR(64)   NOT NULL,
    create_time       TIMESTAMP     NOT NULL,
    updated_by        VARCHAR(64),
    update_time       TIMESTAMP,
    deleted           BOOLEAN       NOT NULL DEFAULT FALSE,
    version           INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_t_auth_role_template PRIMARY KEY (id)
);

COMMENT ON TABLE t_auth_role_template IS '角色权限模板表';
COMMENT ON COLUMN t_auth_role_template.scope_dimension IS '范围维度: GLOBAL/CUSTOMER/PRODUCT/PLAN';
COMMENT ON COLUMN t_auth_role_template.scope_value IS '范围值，GLOBAL 时为 NULL';
COMMENT ON COLUMN t_auth_role_template.permissions IS '权限集合 JSON 数组';

CREATE INDEX idx_t_auth_role_template_code
    ON t_auth_role_template (role_code, status)
    WHERE deleted = FALSE;

CREATE INDEX idx_t_auth_role_template_scope
    ON t_auth_role_template (scope_dimension, scope_value)
    WHERE deleted = FALSE AND scope_value IS NOT NULL;

-- ========== RoleVisibilityScope 表 ==========
-- 角色可见性范围（值对象表，无聚合根 ID）
-- 按 (dimension, scope_value) 做 upsert

CREATE TABLE IF NOT EXISTS t_auth_role_visibility (
    id              BIGSERIAL,
    dimension       VARCHAR(32)   NOT NULL,
    scope_value     VARCHAR(64)   NOT NULL,
    mode            VARCHAR(16)   NOT NULL,
    created_by      VARCHAR(64)   NOT NULL,
    create_time     TIMESTAMP     NOT NULL,
    updated_by      VARCHAR(64),
    update_time     TIMESTAMP,
    deleted         BOOLEAN       NOT NULL DEFAULT FALSE,
    version         INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_t_auth_role_visibility PRIMARY KEY (id)
);

COMMENT ON TABLE t_auth_role_visibility IS '角色可见性范围表（值对象）';
COMMENT ON COLUMN t_auth_role_visibility.dimension IS '维度: PLAN/CUSTOMER';
COMMENT ON COLUMN t_auth_role_visibility.mode IS '可见性模式: SHOW_ALL/EXCLUSIVE_ONLY';

CREATE UNIQUE INDEX uk_t_auth_role_visibility_scope
    ON t_auth_role_visibility (dimension, scope_value)
    WHERE deleted = FALSE;

-- ========== Assignment 表 ==========
-- 身份分配聚合根 AgentIdentityAssignment 持久化

CREATE TABLE IF NOT EXISTS t_auth_assignment (
    id                VARCHAR(32)   NOT NULL,
    user_no           VARCHAR(32)   NOT NULL,
    role_code         VARCHAR(64)   NOT NULL,
    scope_dimension   VARCHAR(32)   NOT NULL,
    scope_value       VARCHAR(64)   NOT NULL,
    inheritable       BOOLEAN       NOT NULL DEFAULT FALSE,
    status            VARCHAR(16)   NOT NULL,
    created_by        VARCHAR(64)   NOT NULL,
    create_time       TIMESTAMP     NOT NULL,
    updated_by        VARCHAR(64),
    update_time       TIMESTAMP,
    deleted           BOOLEAN       NOT NULL DEFAULT FALSE,
    version           INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_t_auth_assignment PRIMARY KEY (id)
);

COMMENT ON TABLE t_auth_assignment IS '身份分配表';
COMMENT ON COLUMN t_auth_assignment.scope_dimension IS '范围维度: PLAN/CUSTOMER/PRODUCT/GLOBAL';
COMMENT ON COLUMN t_auth_assignment.inheritable IS '是否级联下级客户（仅 CUSTOMER 维度有效）';
COMMENT ON COLUMN t_auth_assignment.status IS '状态: ACTIVE/DEACTIVATED';

CREATE INDEX idx_t_auth_assignment_user
    ON t_auth_assignment (user_no, status)
    WHERE deleted = FALSE;

CREATE INDEX idx_t_auth_assignment_scope
    ON t_auth_assignment (scope_dimension, scope_value)
    WHERE deleted = FALSE;

CREATE INDEX idx_t_auth_assignment_role
    ON t_auth_assignment (role_code, status)
    WHERE deleted = FALSE;

-- ========== Grant 表 ==========
-- 授权策略主记录聚合根 Grant 持久化
-- subject/scopeRules/permissions 均以 JSON 存储
-- subject 是 sealed interface，通过 {"type":"...","data":{...}} 结构区分多态

CREATE TABLE IF NOT EXISTS t_auth_grant (
    id                VARCHAR(32)   NOT NULL,
    subject           JSONB         NOT NULL,
    scope_rules       JSONB         NOT NULL,
    permissions       JSONB         NOT NULL,
    grant_type        VARCHAR(32)   NOT NULL,
    origin            VARCHAR(32)   NOT NULL,
    effect            VARCHAR(16)   NOT NULL,
    source_plan_no    VARCHAR(32),
    target_plan_no    VARCHAR(32),
    status            VARCHAR(16)   NOT NULL,
    validity_start    TIMESTAMP,
    validity_end      TIMESTAMP,
    created_by        VARCHAR(64)   NOT NULL,
    create_time       TIMESTAMP     NOT NULL,
    updated_by        VARCHAR(64),
    update_time       TIMESTAMP,
    deleted           BOOLEAN       NOT NULL DEFAULT FALSE,
    version           INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_t_auth_grant PRIMARY KEY (id)
);

COMMENT ON TABLE t_auth_grant IS '授权策略主记录表';
COMMENT ON COLUMN t_auth_grant.subject IS '授权主体 JSON，含类型标识区分多态';
COMMENT ON COLUMN t_auth_grant.scope_rules IS '范围规则集合 JSON';
COMMENT ON COLUMN t_auth_grant.permissions IS '权限集合 JSON';
COMMENT ON COLUMN t_auth_grant.grant_type IS '类型: BASE/DELEGATE_WHOLESALE/DELEGATE_SELECTIVE';
COMMENT ON COLUMN t_auth_grant.origin IS '来源: HQ_CONFIG/PLAN_DELEGATE/CUSTOMER_TO_AGENT/ROLE_TEMPLATE';
COMMENT ON COLUMN t_auth_grant.effect IS '效果: ALLOW/DENY';
COMMENT ON COLUMN t_auth_grant.status IS '状态: DRAFT/PENDING_APPROVAL/EFFECTIVE/REJECTED/REVOKED';

CREATE INDEX idx_t_auth_grant_status
    ON t_auth_grant (status)
    WHERE deleted = FALSE;

CREATE INDEX idx_t_auth_grant_type
    ON t_auth_grant (grant_type, status)
    WHERE deleted = FALSE;

CREATE INDEX idx_t_auth_grant_origin
    ON t_auth_grant (origin, status)
    WHERE deleted = FALSE;

CREATE INDEX idx_t_auth_grant_validity
    ON t_auth_grant (validity_start, validity_end)
    WHERE deleted = FALSE;

-- 代办场景下的计划编号索引
CREATE INDEX idx_t_auth_grant_source_plan
    ON t_auth_grant (source_plan_no)
    WHERE deleted = FALSE AND source_plan_no IS NOT NULL;

CREATE INDEX idx_t_auth_grant_target_plan
    ON t_auth_grant (target_plan_no)
    WHERE deleted = FALSE AND target_plan_no IS NOT NULL;
