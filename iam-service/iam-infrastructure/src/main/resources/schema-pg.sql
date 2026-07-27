-- =====================================================
-- IAM 服务 PostgreSQL 数据库脚本
-- 包含 13 张表:
--   authentication 域: t_iam_user, t_iam_user_profile, t_iam_credential,
--                     t_iam_secondary_auth_session, t_iam_login_log,
--                     t_iam_login_failure_record
--   authorization 域 : t_iam_permission_rule, t_iam_plan_delegation,
--                     t_iam_plan_delegation_operator, t_iam_plan_delegation_permission,
--                     t_iam_business_definition, t_iam_business_action, t_iam_route_rule
-- =====================================================

-- ---------- 1. 用户主表 ----------
CREATE TABLE IF NOT EXISTS t_iam_user (
    id              BIGINT       NOT NULL,
    channel_type    VARCHAR(16)  NOT NULL,                    -- 渠道类型 INTERNET/HQ/BRANCH
    login_name      VARCHAR(64)  NOT NULL,                    -- 登录名
    display_name    VARCHAR(128),                              -- 显示名
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',    -- 用户状态 ACTIVE/DISABLED/LOCKED
    last_login_time TIMESTAMP,                                 -- 最后登录时间
    last_login_ip   VARCHAR(64),                                -- 最后登录IP
    created_by      VARCHAR(64)  NOT NULL,
    create_time     TIMESTAMP    NOT NULL,
    updated_by      VARCHAR(64)  NOT NULL,
    update_time     TIMESTAMP    NOT NULL,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    version         INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

-- 部分索引:按渠道+登录名唯一(软删除后允许重建同名)
CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_user_channel_login
    ON t_iam_user (channel_type, login_name)
    WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_iam_user_status
    ON t_iam_user (status) WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_iam_user_channel
    ON t_iam_user (channel_type) WHERE deleted = FALSE;

COMMENT ON TABLE t_iam_user IS 'IAM 用户主表';
COMMENT ON COLUMN t_iam_user.id IS '用户ID(由 shared-id-starter 生成)';
COMMENT ON COLUMN t_iam_user.channel_type IS '渠道类型: INTERNET-网上渠道, HQ-总部渠道, BRANCH-网点渠道';
COMMENT ON COLUMN t_iam_user.login_name IS '登录名(渠道内唯一)';
COMMENT ON COLUMN t_iam_user.display_name IS '显示名';
COMMENT ON COLUMN t_iam_user.status IS '用户状态: ACTIVE-启用, DISABLED-禁用, LOCKED-锁定';
COMMENT ON COLUMN t_iam_user.last_login_time IS '最后登录时间(由应用层管理)';
COMMENT ON COLUMN t_iam_user.last_login_ip IS '最后登录IP';
COMMENT ON COLUMN t_iam_user.deleted IS '软删除标记';
COMMENT ON COLUMN t_iam_user.version IS '乐观锁版本号';

-- ---------- 2. 用户渠道档案表(与 t_iam_user 1:1 共享主键) ----------
CREATE TABLE IF NOT EXISTS t_iam_user_profile (
    user_id           BIGINT       NOT NULL,                  -- 用户ID(同时为外键到 t_iam_user.id)
    channel_type      VARCHAR(16)  NOT NULL,
    email             VARCHAR(128),
    phone             VARCHAR(32),
    organization      VARCHAR(255),
    position          VARCHAR(128),
    branch_id         VARCHAR(64),                            -- 网点渠道必填
    employee_no       VARCHAR(64),
    extra_attributes  JSONB,                                  -- 扩展属性(JSON)
    created_by        VARCHAR(64)  NOT NULL,
    create_time       TIMESTAMP    NOT NULL,
    updated_by        VARCHAR(64)  NOT NULL,
    update_time       TIMESTAMP    NOT NULL,
    deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    version           INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id)
);

CREATE INDEX IF NOT EXISTS idx_iam_user_profile_branch
    ON t_iam_user_profile (branch_id) WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_iam_user_profile_employee
    ON t_iam_user_profile (employee_no) WHERE deleted = FALSE;

COMMENT ON TABLE t_iam_user_profile IS '用户渠道档案表(与 t_iam_user 1:1)';
COMMENT ON COLUMN t_iam_user_profile.user_id IS '用户ID(共享主键,等于 t_iam_user.id)';
COMMENT ON COLUMN t_iam_user_profile.branch_id IS '网点编号(网点渠道必填)';
COMMENT ON COLUMN t_iam_user_profile.extra_attributes IS '扩展属性(JSON)';

-- ---------- 3. 凭据表 ----------
CREATE TABLE IF NOT EXISTS t_iam_credential (
    id              BIGINT       NOT NULL,
    owner_type      VARCHAR(32)  NOT NULL,                    -- 归属类型 INTERNET_USER/HQ_USER/BRANCH_USER
    owner_id        BIGINT       NOT NULL,                    -- 归属实体ID(User.id)
    credential_type VARCHAR(32)  NOT NULL,                    -- 凭据类型 PASSWORD/UKEY/DYNAMIC_TOKEN
    secret_hash     VARCHAR(255) NOT NULL,                    -- 密文(BCrypt哈希/RSA公钥指纹/TOTP seed)
    salt            VARCHAR(255),                              -- 盐值(BCrypt 内嵌盐时为 NULL)
    aux_data        JSONB,                                     -- 辅助数据(JSON)
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',    -- 状态 ACTIVE/EXPIRED/REVOKED
    expire_time     TIMESTAMP,                                 -- 过期时间(NULL表示永久)
    created_by      VARCHAR(64)  NOT NULL,
    create_time     TIMESTAMP    NOT NULL,
    updated_by      VARCHAR(64)  NOT NULL,
    update_time     TIMESTAMP    NOT NULL,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    version         INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

-- 同一归属同类型只能有一条活动凭据
CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_credential_owner_type_active
    ON t_iam_credential (owner_type, owner_id, credential_type)
    WHERE deleted = FALSE AND status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_iam_credential_owner
    ON t_iam_credential (owner_id, owner_type) WHERE deleted = FALSE;

COMMENT ON TABLE t_iam_credential IS '凭据表(密码/UKey/动态令牌)';
COMMENT ON COLUMN t_iam_credential.aux_data IS '辅助数据(JSON,如 UKey 公钥、动态令牌计数器)';
COMMENT ON COLUMN t_iam_credential.status IS '状态: ACTIVE-活动, EXPIRED-过期, REVOKED-撤销(终态)';

-- ---------- 4. 二次授权会话表(网点渠道专属) ----------
CREATE TABLE IF NOT EXISTS t_iam_secondary_auth_session (
    id                  BIGINT       NOT NULL,
    teller_id           BIGINT       NOT NULL,                -- 柜员用户ID
    approver_id         BIGINT       NOT NULL,                -- 经办人用户ID
    customer_no         VARCHAR(64)  NOT NULL,                -- 客户编号
    plan_id             VARCHAR(64)  NOT NULL,                -- 计划编号
    permission_snapshot JSONB,                                -- 权限快照(JSON 数组字符串)
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING',-- 状态 PENDING/AUTHORIZED/REJECTED/EXPIRED/REVOKED/CLOSED
    initiated_at        TIMESTAMP    NOT NULL,                -- 发起时间
    authorized_at       TIMESTAMP,                             -- 授权时间
    expire_at           TIMESTAMP,                             -- 过期时间
    revoke_reason       VARCHAR(512),                          -- 撤销原因
    created_by          VARCHAR(64)  NOT NULL,
    create_time         TIMESTAMP    NOT NULL,
    updated_by          VARCHAR(64)  NOT NULL,
    update_time         TIMESTAMP    NOT NULL,
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    version             INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_iam_secondary_auth_teller_status
    ON t_iam_secondary_auth_session (teller_id, status) WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_iam_secondary_auth_approver
    ON t_iam_secondary_auth_session (approver_id, status) WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_iam_secondary_auth_expire
    ON t_iam_secondary_auth_session (expire_at) WHERE deleted = FALSE AND status = 'AUTHORIZED';

COMMENT ON TABLE t_iam_secondary_auth_session IS '二次授权会话表(网点柜员借用经办人权限)';
COMMENT ON COLUMN t_iam_secondary_auth_session.permission_snapshot IS '权限快照(JSON,授权完成时冻结)';
COMMENT ON COLUMN t_iam_secondary_auth_session.status IS '状态: PENDING-待处理, AUTHORIZED-已授权, REJECTED-已拒绝, EXPIRED-已过期, REVOKED-已撤销, CLOSED-已关闭';

-- ---------- 5. 登录日志表 ----------
CREATE TABLE IF NOT EXISTS t_iam_login_log (
    id            BIGINT       NOT NULL,
    user_id       BIGINT,                                  -- 用户ID(可空,非用户登录场景)
    login_name    VARCHAR(64)  NOT NULL,                    -- 登录名
    channel_type  VARCHAR(16)  NOT NULL,                    -- 渠道类型
    success       BOOLEAN      NOT NULL,                    -- 是否登录成功
    login_time    TIMESTAMP    NOT NULL,                    -- 登录时间
    login_ip      VARCHAR(64),                              -- 登录IP
    user_agent    VARCHAR(512),                              -- User-Agent
    created_by    VARCHAR(64)  NOT NULL,
    create_time   TIMESTAMP    NOT NULL,
    updated_by    VARCHAR(64)  NOT NULL,
    update_time   TIMESTAMP    NOT NULL,
    deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    version       INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_iam_login_log_user_time
    ON t_iam_login_log (user_id, channel_type, login_time DESC) WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_iam_login_log_name_time
    ON t_iam_login_log (login_name, channel_type, login_time DESC) WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_iam_login_log_success_time
    ON t_iam_login_log (success, login_time) WHERE deleted = FALSE AND success = FALSE;

COMMENT ON TABLE t_iam_login_log IS '登录日志表(审计每次登录尝试)';
COMMENT ON COLUMN t_iam_login_log.success IS '是否登录成功';
COMMENT ON COLUMN t_iam_login_log.user_id IS '用户ID(用户不存在时为 NULL)';

-- ---------- 6. 登录失败记录表(LoginLog 子表) ----------
CREATE TABLE IF NOT EXISTS t_iam_login_failure_record (
    id            BIGINT       NOT NULL,
    login_log_id  BIGINT       NOT NULL,                    -- 关联登录日志ID(FK to t_iam_login_log.id)
    reason        VARCHAR(64)  NOT NULL,                    -- 失败原因代码(如 WRONG_PASSWORD/USER_NOT_FOUND)
    detail        VARCHAR(512),                              -- 人类可读详情
    failure_time  TIMESTAMP    NOT NULL,                    -- 失败时间
    created_by    VARCHAR(64)  NOT NULL,
    create_time   TIMESTAMP    NOT NULL,
    updated_by    VARCHAR(64)  NOT NULL,
    update_time   TIMESTAMP    NOT NULL,
    deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    version       INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_iam_login_failure_log
    ON t_iam_login_failure_record (login_log_id) WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_iam_login_failure_reason_time
    ON t_iam_login_failure_record (reason, failure_time) WHERE deleted = FALSE;

COMMENT ON TABLE t_iam_login_failure_record IS '登录失败记录表(子表)';
COMMENT ON COLUMN t_iam_login_failure_record.login_log_id IS '关联登录日志ID';

-- ---------- 7. 权限规则表 ----------
CREATE TABLE IF NOT EXISTS t_iam_permission_rule (
    id                  BIGINT       NOT NULL,
    rule_code           VARCHAR(64)  NOT NULL,                -- 规则编码(全局唯一)
    rule_name           VARCHAR(128) NOT NULL,                -- 规则名称
    subject_type        VARCHAR(32)  NOT NULL,                -- 主体维度 CUSTOMER/OPERATION_MODE/PRODUCT/PLAN/ACCOUNT_MANAGER
    subject_id          VARCHAR(64)  NOT NULL,                -- 主体标识
    business_code       VARCHAR(64)  NOT NULL,                -- 业务编码(关联 BusinessDefinition)
    allowed_actions     JSONB        NOT NULL,                -- 授权动作集合(JSON 数组,如 ["HANDLE","QUERY"])
    inherit_to_children BOOLEAN      NOT NULL DEFAULT FALSE,  -- 是否继承给下属企业(仅 CUSTOMER 级有意义)
    override_mode       VARCHAR(16)  NOT NULL DEFAULT 'ADD',  -- 覆盖模式 ADD 扩展 / REMOVE 收紧
    priority            INT,                                   -- 优先级(NULL则使用 SubjectType.priority)
    status              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',-- 状态 ACTIVE/DISABLED
    effective_at        TIMESTAMP    NOT NULL,                -- 生效时间
    expire_at           TIMESTAMP,                             -- 失效时间(NULL表示永久)
    created_by          VARCHAR(64)  NOT NULL,
    create_time         TIMESTAMP    NOT NULL,
    updated_by          VARCHAR(64)  NOT NULL,
    update_time         TIMESTAMP    NOT NULL,
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    version             INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_permission_rule_code
    ON t_iam_permission_rule (rule_code) WHERE deleted = FALSE;

-- 权限计算核心查询索引:主体维度+主体标识+状态+时间
CREATE INDEX IF NOT EXISTS idx_iam_perm_rule_subject
    ON t_iam_permission_rule (subject_type, subject_id, status) WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_iam_perm_rule_subject_biz
    ON t_iam_permission_rule (subject_type, subject_id, business_code, status) WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_iam_perm_rule_status_time
    ON t_iam_permission_rule (status, effective_at, expire_at) WHERE deleted = FALSE;

COMMENT ON TABLE t_iam_permission_rule IS '权限规则表(授权域核心配置)';
COMMENT ON COLUMN t_iam_permission_rule.subject_type IS '主体维度: CUSTOMER/OPERATION_MODE/PRODUCT/PLAN/ACCOUNT_MANAGER';
COMMENT ON COLUMN t_iam_permission_rule.allowed_actions IS '授权动作集合(JSON,如 ["HANDLE","QUERY","AUDIT"])';
COMMENT ON COLUMN t_iam_permission_rule.inherit_to_children IS '是否继承给下属企业(仅 CUSTOMER 级有意义)';
COMMENT ON COLUMN t_iam_permission_rule.override_mode IS '覆盖模式: ADD-扩展, REMOVE-收紧';

-- ---------- 8. 计划代办关系表 ----------
CREATE TABLE IF NOT EXISTS t_iam_plan_delegation (
    id                 BIGINT       NOT NULL,
    delegation_code    VARCHAR(64)  NOT NULL,                 -- 代办编码(全局唯一)
    delegator_plan_no  VARCHAR(64)  NOT NULL,                 -- 授权方计划编号
    delegatee_plan_no  VARCHAR(64)  NOT NULL,                 -- 被授权方计划编号
    delegation_type    VARCHAR(32)  NOT NULL,                 -- 代办类型 ALL_OPERATORS/SPECIFIC_OPERATORS
    status             VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE', -- 状态 ACTIVE/REVOKED/EXPIRED
    effective_at       TIMESTAMP    NOT NULL,                 -- 生效时间
    expire_at          TIMESTAMP,                              -- 失效时间(NULL表示永久)
    created_by         VARCHAR(64)  NOT NULL,
    create_time        TIMESTAMP    NOT NULL,
    updated_by         VARCHAR(64)  NOT NULL,
    update_time        TIMESTAMP    NOT NULL,
    deleted            BOOLEAN      NOT NULL DEFAULT FALSE,
    version            INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_plan_delegation_code
    ON t_iam_plan_delegation (delegation_code) WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_iam_plan_delegation_delegator
    ON t_iam_plan_delegation (delegator_plan_no, status) WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_iam_plan_delegation_delegatee
    ON t_iam_plan_delegation (delegatee_plan_no, status) WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_iam_plan_delegation_expire
    ON t_iam_plan_delegation (expire_at) WHERE deleted = FALSE AND status = 'ACTIVE';

COMMENT ON TABLE t_iam_plan_delegation IS '计划代办关系表';
COMMENT ON COLUMN t_iam_plan_delegation.delegation_type IS '代办类型: ALL_OPERATORS-所有操作员, SPECIFIC_OPERATORS-指定操作员';

-- ---------- 9. 代办指定操作员表(PlanDelegation 子表) ----------
CREATE TABLE IF NOT EXISTS t_iam_plan_delegation_operator (
    id             BIGINT       NOT NULL,
    delegation_id  BIGINT       NOT NULL,                    -- 代办关系ID(FK to t_iam_plan_delegation.id)
    operator_id    BIGINT       NOT NULL,                    -- 操作员ID(FK to t_iam_user.id)
    created_by     VARCHAR(64)  NOT NULL,
    create_time    TIMESTAMP    NOT NULL,
    updated_by     VARCHAR(64)  NOT NULL,
    update_time    TIMESTAMP    NOT NULL,
    deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    version        INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_plan_delegation_operator
    ON t_iam_plan_delegation_operator (delegation_id, operator_id) WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_iam_plan_delegation_operator_delegation
    ON t_iam_plan_delegation_operator (delegation_id) WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_iam_plan_delegation_operator_operator
    ON t_iam_plan_delegation_operator (operator_id) WHERE deleted = FALSE;

COMMENT ON TABLE t_iam_plan_delegation_operator IS '代办指定操作员表(子表)';
COMMENT ON COLUMN t_iam_plan_delegation_operator.operator_id IS '操作员ID(FK to t_iam_user.id)';

-- ---------- 10. 代办授权权限表(PlanDelegation 子表) ----------
CREATE TABLE IF NOT EXISTS t_iam_plan_delegation_permission (
    id             BIGINT       NOT NULL,
    delegation_id  BIGINT       NOT NULL,                    -- 代办关系ID(FK to t_iam_plan_delegation.id)
    business_code  VARCHAR(64)  NOT NULL,                    -- 业务编码
    action         VARCHAR(16)  NOT NULL,                    -- 业务动作 HANDLE/QUERY/AUDIT
    created_by     VARCHAR(64)  NOT NULL,
    create_time    TIMESTAMP    NOT NULL,
    updated_by     VARCHAR(64)  NOT NULL,
    update_time    TIMESTAMP    NOT NULL,
    deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    version        INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_plan_delegation_permission
    ON t_iam_plan_delegation_permission (delegation_id, business_code, action) WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_iam_plan_delegation_permission_delegation
    ON t_iam_plan_delegation_permission (delegation_id) WHERE deleted = FALSE;

COMMENT ON TABLE t_iam_plan_delegation_permission IS '代办授权权限表(子表)';

-- ---------- 11. 业务定义表 ----------
CREATE TABLE IF NOT EXISTS t_iam_business_definition (
    id                BIGINT       NOT NULL,
    business_code     VARCHAR(64)  NOT NULL,                  -- 业务编码(全局唯一,如 ANNUITY_ESTABLISH)
    business_name     VARCHAR(128) NOT NULL,                  -- 业务名称
    description       VARCHAR(512),                            -- 业务描述
    supported_actions JSONB,                                    -- 支持动作集合(JSON,冗余字段,实际明细存子表)
    active            BOOLEAN      NOT NULL DEFAULT TRUE,     -- 是否启用
    created_by        VARCHAR(64)  NOT NULL,
    create_time       TIMESTAMP    NOT NULL,
    updated_by        VARCHAR(64)  NOT NULL,
    update_time       TIMESTAMP    NOT NULL,
    deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    version           INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_business_definition_code
    ON t_iam_business_definition (business_code) WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_iam_business_definition_active
    ON t_iam_business_definition (active) WHERE deleted = FALSE;

COMMENT ON TABLE t_iam_business_definition IS '业务定义表';
COMMENT ON COLUMN t_iam_business_definition.business_code IS '业务编码(全局唯一)';
COMMENT ON COLUMN t_iam_business_definition.supported_actions IS '支持动作集合(JSON,冗余字段)';

-- ---------- 12. 业务动作表(BusinessDefinition 子表) ----------
CREATE TABLE IF NOT EXISTS t_iam_business_action (
    id              BIGINT       NOT NULL,
    definition_id   BIGINT       NOT NULL,                    -- 业务定义ID(FK to t_iam_business_definition.id)
    action          VARCHAR(16)  NOT NULL,                    -- 业务动作 HANDLE/QUERY/AUDIT
    description     VARCHAR(512),                              -- 动作描述
    created_by      VARCHAR(64)  NOT NULL,
    create_time     TIMESTAMP    NOT NULL,
    updated_by      VARCHAR(64)  NOT NULL,
    update_time     TIMESTAMP    NOT NULL,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    version         INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_business_action_def_action
    ON t_iam_business_action (definition_id, action) WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_iam_business_action_definition
    ON t_iam_business_action (definition_id) WHERE deleted = FALSE;

COMMENT ON TABLE t_iam_business_action IS '业务动作表(BusinessDefinition 子表)';

-- ---------- 13. 路由权限规则表 ----------
CREATE TABLE IF NOT EXISTS t_iam_route_rule (
    id             BIGINT       NOT NULL,
    route_pattern  VARCHAR(255) NOT NULL,                     -- 路由匹配模式(Ant 风格,如 /internet/**)
    check_type     VARCHAR(16)  NOT NULL,                     -- 校验类型 LOGIN/PERMISSION/ROLE/CHANNEL/SKIP
    check_value    VARCHAR(255),                               -- 校验值(权限码/角色名/渠道名,SKIP 时为 NULL)
    description    VARCHAR(512),                               -- 规则描述
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,        -- 是否启用
    priority       INT          NOT NULL DEFAULT 0,           -- 优先级(数值越大优先级越高)
    created_by     VARCHAR(64)  NOT NULL,
    create_time    TIMESTAMP    NOT NULL,
    updated_by     VARCHAR(64)  NOT NULL,
    update_time    TIMESTAMP    NOT NULL,
    deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    version        INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_route_rule_pattern
    ON t_iam_route_rule (route_pattern) WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_iam_route_rule_enabled_priority
    ON t_iam_route_rule (enabled, priority DESC) WHERE deleted = FALSE;

COMMENT ON TABLE t_iam_route_rule IS '路由权限规则表(网关动态鉴权配置)';
COMMENT ON COLUMN t_iam_route_rule.check_type IS '校验类型: LOGIN-登录校验, PERMISSION-权限校验, ROLE-角色校验, CHANNEL-渠道校验, SKIP-跳过校验';

-- =====================================================
-- 初始化数据(业务定义 + 网关路由权限规则)
-- =====================================================

-- 业务定义初始化(支持的动作通过子表 t_iam_business_action 维护,此处 supported_actions 为冗余字段)
INSERT INTO t_iam_business_definition (id, business_code, business_name, description, supported_actions, active, created_by, create_time, updated_by, update_time, deleted, version)
VALUES
    (1, 'ANNUITY_ESTABLISH', '年金计划设立', '企业年金计划设立业务', '["HANDLE","QUERY","AUDIT"]', TRUE, 'SYSTEM', NOW(), 'SYSTEM', NOW(), FALSE, 0),
    (2, 'ANNUITY_CONTRIBUTION', '年金缴费', '企业年金缴费业务', '["HANDLE","QUERY"]', TRUE, 'SYSTEM', NOW(), 'SYSTEM', NOW(), FALSE, 0),
    (3, 'ANNUITY_PAYMENT', '年金支付', '企业年金支付业务', '["HANDLE","QUERY","AUDIT"]', TRUE, 'SYSTEM', NOW(), 'SYSTEM', NOW(), FALSE, 0)
ON CONFLICT (id) DO NOTHING;

-- 业务动作明细初始化
INSERT INTO t_iam_business_action (id, definition_id, action, description, created_by, create_time, updated_by, update_time, deleted, version)
VALUES
    -- 年金计划设立(HANDLE/QUERY/AUDIT)
    (101, 1, 'HANDLE', '办理年金计划设立', 'SYSTEM', NOW(), 'SYSTEM', NOW(), FALSE, 0),
    (102, 1, 'QUERY',  '查询年金计划设立', 'SYSTEM', NOW(), 'SYSTEM', NOW(), FALSE, 0),
    (103, 1, 'AUDIT',  '审计年金计划设立', 'SYSTEM', NOW(), 'SYSTEM', NOW(), FALSE, 0),
    -- 年金缴费(HANDLE/QUERY)
    (104, 2, 'HANDLE', '办理年金缴费', 'SYSTEM', NOW(), 'SYSTEM', NOW(), FALSE, 0),
    (105, 2, 'QUERY',  '查询年金缴费', 'SYSTEM', NOW(), 'SYSTEM', NOW(), FALSE, 0),
    -- 年金支付(HANDLE/QUERY/AUDIT)
    (106, 3, 'HANDLE', '办理年金支付', 'SYSTEM', NOW(), 'SYSTEM', NOW(), FALSE, 0),
    (107, 3, 'QUERY',  '查询年金支付', 'SYSTEM', NOW(), 'SYSTEM', NOW(), FALSE, 0),
    (108, 3, 'AUDIT',  '审计年金支付', 'SYSTEM', NOW(), 'SYSTEM', NOW(), FALSE, 0)
ON CONFLICT (id) DO NOTHING;

-- 网关路由权限规则初始化
INSERT INTO t_iam_route_rule (id, route_pattern, check_type, check_value, description, enabled, priority, created_by, create_time, updated_by, update_time, deleted, version)
VALUES
    (1, '/internet/**', 'LOGIN',    NULL,         '网上渠道登录校验', TRUE, 100, 'SYSTEM', NOW(), 'SYSTEM', NOW(), FALSE, 0),
    (2, '/hq/**',       'LOGIN',    NULL,         '总部渠道登录校验', TRUE, 100, 'SYSTEM', NOW(), 'SYSTEM', NOW(), FALSE, 0),
    (3, '/branch/**',   'LOGIN',    NULL,         '网点渠道登录校验', TRUE, 100, 'SYSTEM', NOW(), 'SYSTEM', NOW(), FALSE, 0),
    (4, '/internet/annuity/**', 'PERMISSION', 'ANNUITY_ESTABLISH.HANDLE', '网上渠道年金办理权限', TRUE, 90, 'SYSTEM', NOW(), 'SYSTEM', NOW(), FALSE, 0),
    (5, '/public/**',   'SKIP',     NULL,         '公共接口跳过校验', TRUE, 200, 'SYSTEM', NOW(), 'SYSTEM', NOW(), FALSE, 0)
ON CONFLICT (id) DO NOTHING;

-- =====================================================
-- 事件存储表 (shared-event-starter 的 JdbcEventStore 使用)
-- 纯技术日志表,使用原生 JDBC + NOW() (符合 06-数据库规范.md 第十节例外)
-- =====================================================

CREATE TABLE IF NOT EXISTS sys_event_store (
    event_id            VARCHAR(64)   NOT NULL,
    event_type          VARCHAR(255)  NOT NULL,
    integration_type    VARCHAR(64),
    occurred_on         TIMESTAMP     NOT NULL,
    domain_payload      TEXT          NOT NULL,
    integration_payload TEXT,
    created_at          TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id)
);

CREATE INDEX IF NOT EXISTS idx_sys_event_store_event_type     ON sys_event_store(event_type);
CREATE INDEX IF NOT EXISTS idx_sys_event_store_occurred_on    ON sys_event_store(occurred_on);
CREATE INDEX IF NOT EXISTS idx_sys_event_store_integration    ON sys_event_store(integration_type);

COMMENT ON TABLE  sys_event_store IS '领域事件存储表';
COMMENT ON COLUMN sys_event_store.event_id IS '事件ID (EventId,主键)';
COMMENT ON COLUMN sys_event_store.event_type IS '领域事件类型名';
COMMENT ON COLUMN sys_event_store.integration_type IS '集成事件类型名 (NULL 表示无集成事件)';
COMMENT ON COLUMN sys_event_store.occurred_on IS '事件发生时间';
COMMENT ON COLUMN sys_event_store.domain_payload IS '领域事件 JSON 序列化内容';
COMMENT ON COLUMN sys_event_store.integration_payload IS '集成事件 JSON 序列化内容 (NULL 表示无集成事件)';
COMMENT ON COLUMN sys_event_store.created_at IS '记录创建时间';

CREATE TABLE IF NOT EXISTS sys_event_dispatch_log (
    id              BIGINT       GENERATED BY DEFAULT AS IDENTITY,
    event_id        VARCHAR(64)  NOT NULL,
    channel         VARCHAR(100) NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    error_msg       TEXT,
    retry_count     INT          DEFAULT 0,
    next_retry_at   TIMESTAMP,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_event_channel UNIQUE (event_id, channel)
);

CREATE INDEX IF NOT EXISTS idx_sys_event_dispatch_log_status       ON sys_event_dispatch_log(status);
CREATE INDEX IF NOT EXISTS idx_sys_event_dispatch_log_next_retry   ON sys_event_dispatch_log(next_retry_at);
CREATE INDEX IF NOT EXISTS idx_sys_event_dispatch_log_retry_count  ON sys_event_dispatch_log(retry_count);

COMMENT ON TABLE  sys_event_dispatch_log IS '事件分发日志表';
COMMENT ON COLUMN sys_event_dispatch_log.id IS '自增主键 (技术日志表,非业务表)';
COMMENT ON COLUMN sys_event_dispatch_log.event_id IS '关联事件ID';
COMMENT ON COLUMN sys_event_dispatch_log.channel IS '分发通道: SPRING/REDIS/ROCKETMQ';
COMMENT ON COLUMN sys_event_dispatch_log.status IS '分发状态: PENDING/SUCCESS/FAILED';
COMMENT ON COLUMN sys_event_dispatch_log.error_msg IS '失败错误信息';
COMMENT ON COLUMN sys_event_dispatch_log.retry_count IS '已重试次数 (上限 10)';
COMMENT ON COLUMN sys_event_dispatch_log.next_retry_at IS '下次重试时间';
COMMENT ON COLUMN sys_event_dispatch_log.created_at IS '记录创建时间';
COMMENT ON COLUMN sys_event_dispatch_log.updated_at IS '记录更新时间';
