-- =====================================================
-- IAM 服务 H2 测试数据库脚本 (PostgreSQL 兼容模式)
-- 由 schema-pg.sql 转换而来:
--   - JSONB → CLOB
--   - 移除 CREATE INDEX 的 IF NOT EXISTS
--   - 移除部分索引 (WHERE deleted = FALSE ...)
--   - 保留 TIMESTAMP / BOOLEAN (H2 PostgreSQL 模式支持)
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
    channel_type    VARCHAR(16)  NOT NULL,
    login_name      VARCHAR(64)  NOT NULL,
    display_name    VARCHAR(128),
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    last_login_time TIMESTAMP,
    last_login_ip   VARCHAR(64),
    created_by      VARCHAR(64)  NOT NULL,
    create_time     TIMESTAMP    NOT NULL,
    updated_by      VARCHAR(64)  NOT NULL,
    update_time     TIMESTAMP    NOT NULL,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    version         INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_iam_user_channel_login
    ON t_iam_user (channel_type, login_name);

CREATE INDEX idx_iam_user_status
    ON t_iam_user (status);

CREATE INDEX idx_iam_user_channel
    ON t_iam_user (channel_type);

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
    user_id           BIGINT       NOT NULL,
    channel_type      VARCHAR(16)  NOT NULL,
    email             VARCHAR(128),
    phone             VARCHAR(32),
    organization      VARCHAR(255),
    position          VARCHAR(128),
    branch_id         VARCHAR(64),
    employee_no       VARCHAR(64),
    extra_attributes  CLOB,
    created_by        VARCHAR(64)  NOT NULL,
    create_time       TIMESTAMP    NOT NULL,
    updated_by        VARCHAR(64)  NOT NULL,
    update_time       TIMESTAMP    NOT NULL,
    deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    version           INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id)
);

CREATE INDEX idx_iam_user_profile_branch
    ON t_iam_user_profile (branch_id);

CREATE INDEX idx_iam_user_profile_employee
    ON t_iam_user_profile (employee_no);

COMMENT ON TABLE t_iam_user_profile IS '用户渠道档案表(与 t_iam_user 1:1)';
COMMENT ON COLUMN t_iam_user_profile.user_id IS '用户ID(共享主键,等于 t_iam_user.id)';
COMMENT ON COLUMN t_iam_user_profile.branch_id IS '网点编号(网点渠道必填)';
COMMENT ON COLUMN t_iam_user_profile.extra_attributes IS '扩展属性(JSON)';

-- ---------- 3. 凭据表 ----------
CREATE TABLE IF NOT EXISTS t_iam_credential (
    id              BIGINT       NOT NULL,
    owner_type      VARCHAR(32)  NOT NULL,
    owner_id        BIGINT       NOT NULL,
    credential_type VARCHAR(32)  NOT NULL,
    secret_hash     VARCHAR(255) NOT NULL,
    salt            VARCHAR(255),
    aux_data        CLOB,
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    expire_time     TIMESTAMP,
    created_by      VARCHAR(64)  NOT NULL,
    create_time     TIMESTAMP    NOT NULL,
    updated_by      VARCHAR(64)  NOT NULL,
    update_time     TIMESTAMP    NOT NULL,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    version         INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_iam_credential_owner_type_active
    ON t_iam_credential (owner_type, owner_id, credential_type);

CREATE INDEX idx_iam_credential_owner
    ON t_iam_credential (owner_id, owner_type);

COMMENT ON TABLE t_iam_credential IS '凭据表(密码/UKey/动态令牌)';
COMMENT ON COLUMN t_iam_credential.aux_data IS '辅助数据(JSON,如 UKey 公钥、动态令牌计数器)';
COMMENT ON COLUMN t_iam_credential.status IS '状态: ACTIVE-活动, EXPIRED-过期, REVOKED-撤销(终态)';

-- ---------- 4. 二次授权会话表(网点渠道专属) ----------
CREATE TABLE IF NOT EXISTS t_iam_secondary_auth_session (
    id                  BIGINT       NOT NULL,
    teller_id           BIGINT       NOT NULL,
    approver_id         BIGINT       NOT NULL,
    customer_no         VARCHAR(64)  NOT NULL,
    plan_id             VARCHAR(64)  NOT NULL,
    permission_snapshot CLOB,
    status              VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    initiated_at        TIMESTAMP    NOT NULL,
    authorized_at       TIMESTAMP,
    expire_at           TIMESTAMP,
    revoke_reason       VARCHAR(512),
    created_by          VARCHAR(64)  NOT NULL,
    create_time         TIMESTAMP    NOT NULL,
    updated_by          VARCHAR(64)  NOT NULL,
    update_time         TIMESTAMP    NOT NULL,
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    version             INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_iam_secondary_auth_teller_status
    ON t_iam_secondary_auth_session (teller_id, status);

CREATE INDEX idx_iam_secondary_auth_approver
    ON t_iam_secondary_auth_session (approver_id, status);

CREATE INDEX idx_iam_secondary_auth_expire
    ON t_iam_secondary_auth_session (expire_at);

COMMENT ON TABLE t_iam_secondary_auth_session IS '二次授权会话表(网点柜员借用经办人权限)';
COMMENT ON COLUMN t_iam_secondary_auth_session.permission_snapshot IS '权限快照(JSON,授权完成时冻结)';
COMMENT ON COLUMN t_iam_secondary_auth_session.status IS '状态: PENDING-待处理, AUTHORIZED-已授权, REJECTED-已拒绝, EXPIRED-已过期, REVOKED-已撤销, CLOSED-已关闭';

-- ---------- 5. 登录日志表 ----------
CREATE TABLE IF NOT EXISTS t_iam_login_log (
    id            BIGINT       NOT NULL,
    user_id       BIGINT,
    login_name    VARCHAR(64)  NOT NULL,
    channel_type  VARCHAR(16)  NOT NULL,
    success       BOOLEAN      NOT NULL,
    login_time    TIMESTAMP    NOT NULL,
    login_ip      VARCHAR(64),
    user_agent    VARCHAR(512),
    created_by    VARCHAR(64)  NOT NULL,
    create_time   TIMESTAMP    NOT NULL,
    updated_by    VARCHAR(64)  NOT NULL,
    update_time   TIMESTAMP    NOT NULL,
    deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    version       INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_iam_login_log_user_time
    ON t_iam_login_log (user_id, channel_type, login_time);

CREATE INDEX idx_iam_login_log_name_time
    ON t_iam_login_log (login_name, channel_type, login_time);

CREATE INDEX idx_iam_login_log_success_time
    ON t_iam_login_log (success, login_time);

COMMENT ON TABLE t_iam_login_log IS '登录日志表(审计每次登录尝试)';
COMMENT ON COLUMN t_iam_login_log.success IS '是否登录成功';
COMMENT ON COLUMN t_iam_login_log.user_id IS '用户ID(用户不存在时为 NULL)';

-- ---------- 6. 登录失败记录表(LoginLog 子表) ----------
CREATE TABLE IF NOT EXISTS t_iam_login_failure_record (
    id            BIGINT       NOT NULL,
    login_log_id  BIGINT       NOT NULL,
    reason        VARCHAR(64)  NOT NULL,
    detail        VARCHAR(512),
    failure_time  TIMESTAMP    NOT NULL,
    created_by    VARCHAR(64)  NOT NULL,
    create_time   TIMESTAMP    NOT NULL,
    updated_by    VARCHAR(64)  NOT NULL,
    update_time   TIMESTAMP    NOT NULL,
    deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    version       INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_iam_login_failure_log
    ON t_iam_login_failure_record (login_log_id);

CREATE INDEX idx_iam_login_failure_reason_time
    ON t_iam_login_failure_record (reason, failure_time);

COMMENT ON TABLE t_iam_login_failure_record IS '登录失败记录表(子表)';
COMMENT ON COLUMN t_iam_login_failure_record.login_log_id IS '关联登录日志ID';

-- ---------- 7. 权限规则表 ----------
CREATE TABLE IF NOT EXISTS t_iam_permission_rule (
    id                  BIGINT       NOT NULL,
    rule_code           VARCHAR(64)  NOT NULL,
    rule_name           VARCHAR(128) NOT NULL,
    subject_type        VARCHAR(32)  NOT NULL,
    subject_id          VARCHAR(64)  NOT NULL,
    business_code       VARCHAR(64)  NOT NULL,
    allowed_actions     CLOB         NOT NULL,
    inherit_to_children BOOLEAN      NOT NULL DEFAULT FALSE,
    override_mode       VARCHAR(16)  NOT NULL DEFAULT 'ADD',
    priority            INT,
    status              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    effective_at        TIMESTAMP    NOT NULL,
    expire_at           TIMESTAMP,
    created_by          VARCHAR(64)  NOT NULL,
    create_time         TIMESTAMP    NOT NULL,
    updated_by          VARCHAR(64)  NOT NULL,
    update_time         TIMESTAMP    NOT NULL,
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    version             INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_iam_permission_rule_code
    ON t_iam_permission_rule (rule_code);

CREATE INDEX idx_iam_perm_rule_subject
    ON t_iam_permission_rule (subject_type, subject_id, status);

CREATE INDEX idx_iam_perm_rule_subject_biz
    ON t_iam_permission_rule (subject_type, subject_id, business_code, status);

CREATE INDEX idx_iam_perm_rule_status_time
    ON t_iam_permission_rule (status, effective_at, expire_at);

COMMENT ON TABLE t_iam_permission_rule IS '权限规则表(授权域核心配置)';
COMMENT ON COLUMN t_iam_permission_rule.subject_type IS '主体维度: CUSTOMER/OPERATION_MODE/PRODUCT/PLAN/ACCOUNT_MANAGER';
COMMENT ON COLUMN t_iam_permission_rule.allowed_actions IS '授权动作集合(JSON,如 ["HANDLE","QUERY","AUDIT"])';
COMMENT ON COLUMN t_iam_permission_rule.inherit_to_children IS '是否继承给下属企业(仅 CUSTOMER 级有意义)';
COMMENT ON COLUMN t_iam_permission_rule.override_mode IS '覆盖模式: ADD-扩展, REMOVE-收紧';

-- ---------- 8. 计划代办关系表 ----------
CREATE TABLE IF NOT EXISTS t_iam_plan_delegation (
    id                 BIGINT       NOT NULL,
    delegation_code    VARCHAR(64)  NOT NULL,
    delegator_plan_no  VARCHAR(64)  NOT NULL,
    delegatee_plan_no  VARCHAR(64)  NOT NULL,
    delegation_type    VARCHAR(32)  NOT NULL,
    status             VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    effective_at       TIMESTAMP    NOT NULL,
    expire_at          TIMESTAMP,
    created_by         VARCHAR(64)  NOT NULL,
    create_time        TIMESTAMP    NOT NULL,
    updated_by         VARCHAR(64)  NOT NULL,
    update_time        TIMESTAMP    NOT NULL,
    deleted            BOOLEAN      NOT NULL DEFAULT FALSE,
    version            INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_iam_plan_delegation_code
    ON t_iam_plan_delegation (delegation_code);

CREATE INDEX idx_iam_plan_delegation_delegator
    ON t_iam_plan_delegation (delegator_plan_no, status);

CREATE INDEX idx_iam_plan_delegation_delegatee
    ON t_iam_plan_delegation (delegatee_plan_no, status);

CREATE INDEX idx_iam_plan_delegation_expire
    ON t_iam_plan_delegation (expire_at);

COMMENT ON TABLE t_iam_plan_delegation IS '计划代办关系表';
COMMENT ON COLUMN t_iam_plan_delegation.delegation_type IS '代办类型: ALL_OPERATORS-所有操作员, SPECIFIC_OPERATORS-指定操作员';

-- ---------- 9. 代办指定操作员表(PlanDelegation 子表) ----------
CREATE TABLE IF NOT EXISTS t_iam_plan_delegation_operator (
    id             BIGINT       NOT NULL,
    delegation_id  BIGINT       NOT NULL,
    operator_id    BIGINT       NOT NULL,
    created_by     VARCHAR(64)  NOT NULL,
    create_time    TIMESTAMP    NOT NULL,
    updated_by     VARCHAR(64)  NOT NULL,
    update_time    TIMESTAMP    NOT NULL,
    deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    version        INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_iam_plan_delegation_operator
    ON t_iam_plan_delegation_operator (delegation_id, operator_id);

CREATE INDEX idx_iam_plan_delegation_operator_delegation
    ON t_iam_plan_delegation_operator (delegation_id);

CREATE INDEX idx_iam_plan_delegation_operator_operator
    ON t_iam_plan_delegation_operator (operator_id);

COMMENT ON TABLE t_iam_plan_delegation_operator IS '代办指定操作员表(子表)';
COMMENT ON COLUMN t_iam_plan_delegation_operator.operator_id IS '操作员ID(FK to t_iam_user.id)';

-- ---------- 10. 代办授权权限表(PlanDelegation 子表) ----------
CREATE TABLE IF NOT EXISTS t_iam_plan_delegation_permission (
    id             BIGINT       NOT NULL,
    delegation_id  BIGINT       NOT NULL,
    business_code  VARCHAR(64)  NOT NULL,
    action         VARCHAR(16)  NOT NULL,
    created_by     VARCHAR(64)  NOT NULL,
    create_time    TIMESTAMP    NOT NULL,
    updated_by     VARCHAR(64)  NOT NULL,
    update_time    TIMESTAMP    NOT NULL,
    deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    version        INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_iam_plan_delegation_permission
    ON t_iam_plan_delegation_permission (delegation_id, business_code, action);

CREATE INDEX idx_iam_plan_delegation_permission_delegation
    ON t_iam_plan_delegation_permission (delegation_id);

COMMENT ON TABLE t_iam_plan_delegation_permission IS '代办授权权限表(子表)';

-- ---------- 11. 业务定义表 ----------
CREATE TABLE IF NOT EXISTS t_iam_business_definition (
    id                BIGINT       NOT NULL,
    business_code     VARCHAR(64)  NOT NULL,
    business_name     VARCHAR(128) NOT NULL,
    description       VARCHAR(512),
    supported_actions CLOB,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by        VARCHAR(64)  NOT NULL,
    create_time       TIMESTAMP    NOT NULL,
    updated_by        VARCHAR(64)  NOT NULL,
    update_time       TIMESTAMP    NOT NULL,
    deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    version           INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_iam_business_definition_code
    ON t_iam_business_definition (business_code);

CREATE INDEX idx_iam_business_definition_active
    ON t_iam_business_definition (active);

COMMENT ON TABLE t_iam_business_definition IS '业务定义表';
COMMENT ON COLUMN t_iam_business_definition.business_code IS '业务编码(全局唯一)';
COMMENT ON COLUMN t_iam_business_definition.supported_actions IS '支持动作集合(JSON,冗余字段)';

-- ---------- 12. 业务动作表(BusinessDefinition 子表) ----------
CREATE TABLE IF NOT EXISTS t_iam_business_action (
    id              BIGINT       NOT NULL,
    definition_id   BIGINT       NOT NULL,
    action          VARCHAR(16)  NOT NULL,
    description     VARCHAR(512),
    created_by      VARCHAR(64)  NOT NULL,
    create_time     TIMESTAMP    NOT NULL,
    updated_by      VARCHAR(64)  NOT NULL,
    update_time     TIMESTAMP    NOT NULL,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    version         INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_iam_business_action_def_action
    ON t_iam_business_action (definition_id, action);

CREATE INDEX idx_iam_business_action_definition
    ON t_iam_business_action (definition_id);

COMMENT ON TABLE t_iam_business_action IS '业务动作表(BusinessDefinition 子表)';

-- ---------- 13. 路由权限规则表 ----------
CREATE TABLE IF NOT EXISTS t_iam_route_rule (
    id             BIGINT       NOT NULL,
    route_pattern  VARCHAR(255) NOT NULL,
    check_type     VARCHAR(16)  NOT NULL,
    check_value    VARCHAR(255),
    description    VARCHAR(512),
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    priority       INT          NOT NULL DEFAULT 0,
    created_by     VARCHAR(64)  NOT NULL,
    create_time    TIMESTAMP    NOT NULL,
    updated_by     VARCHAR(64)  NOT NULL,
    update_time    TIMESTAMP    NOT NULL,
    deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    version        INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_iam_route_rule_pattern
    ON t_iam_route_rule (route_pattern);

CREATE INDEX idx_iam_route_rule_enabled_priority
    ON t_iam_route_rule (enabled, priority);

COMMENT ON TABLE t_iam_route_rule IS '路由权限规则表(网关动态鉴权配置)';
COMMENT ON COLUMN t_iam_route_rule.check_type IS '校验类型: LOGIN-登录校验, PERMISSION-权限校验, ROLE-角色校验, CHANNEL-渠道校验, SKIP-跳过校验';

-- =====================================================
-- 初始化数据(业务定义 + 网关路由权限规则)
-- =====================================================

-- 业务定义初始化(H2 不支持 ON CONFLICT,使用 MERGE 实现幂等)
MERGE INTO t_iam_business_definition (id, business_code, business_name, description, supported_actions, active, created_by, create_time, updated_by, update_time, deleted, version)
KEY(id)
VALUES
    (1, 'ANNUITY_ESTABLISH', '年金计划设立', '企业年金计划设立业务', '["HANDLE","QUERY","AUDIT"]', TRUE, 'SYSTEM', NOW(), 'SYSTEM', NOW(), FALSE, 0),
    (2, 'ANNUITY_CONTRIBUTION', '年金缴费', '企业年金缴费业务', '["HANDLE","QUERY"]', TRUE, 'SYSTEM', NOW(), 'SYSTEM', NOW(), FALSE, 0),
    (3, 'ANNUITY_PAYMENT', '年金支付', '企业年金支付业务', '["HANDLE","QUERY","AUDIT"]', TRUE, 'SYSTEM', NOW(), 'SYSTEM', NOW(), FALSE, 0);

-- 业务动作明细初始化
MERGE INTO t_iam_business_action (id, definition_id, action, description, created_by, create_time, updated_by, update_time, deleted, version)
KEY(id)
VALUES
    (101, 1, 'HANDLE', '办理年金计划设立', 'SYSTEM', NOW(), 'SYSTEM', NOW(), FALSE, 0),
    (102, 1, 'QUERY',  '查询年金计划设立', 'SYSTEM', NOW(), 'SYSTEM', NOW(), FALSE, 0),
    (103, 1, 'AUDIT',  '审计年金计划设立', 'SYSTEM', NOW(), 'SYSTEM', NOW(), FALSE, 0),
    (104, 2, 'HANDLE', '办理年金缴费', 'SYSTEM', NOW(), 'SYSTEM', NOW(), FALSE, 0),
    (105, 2, 'QUERY',  '查询年金缴费', 'SYSTEM', NOW(), 'SYSTEM', NOW(), FALSE, 0),
    (106, 3, 'HANDLE', '办理年金支付', 'SYSTEM', NOW(), 'SYSTEM', NOW(), FALSE, 0),
    (107, 3, 'QUERY',  '查询年金支付', 'SYSTEM', NOW(), 'SYSTEM', NOW(), FALSE, 0),
    (108, 3, 'AUDIT',  '审计年金支付', 'SYSTEM', NOW(), 'SYSTEM', NOW(), FALSE, 0);

-- 网关路由权限规则初始化
MERGE INTO t_iam_route_rule (id, route_pattern, check_type, check_value, description, enabled, priority, created_by, create_time, updated_by, update_time, deleted, version)
KEY(id)
VALUES
    (1, '/internet/**', 'LOGIN',    NULL,         '网上渠道登录校验', TRUE, 100, 'SYSTEM', NOW(), 'SYSTEM', NOW(), FALSE, 0),
    (2, '/hq/**',       'LOGIN',    NULL,         '总部渠道登录校验', TRUE, 100, 'SYSTEM', NOW(), 'SYSTEM', NOW(), FALSE, 0),
    (3, '/branch/**',   'LOGIN',    NULL,         '网点渠道登录校验', TRUE, 100, 'SYSTEM', NOW(), 'SYSTEM', NOW(), FALSE, 0),
    (4, '/internet/annuity/**', 'PERMISSION', 'ANNUITY_ESTABLISH.HANDLE', '网上渠道年金办理权限', TRUE, 90, 'SYSTEM', NOW(), 'SYSTEM', NOW(), FALSE, 0),
    (5, '/public/**',   'SKIP',     NULL,         '公共接口跳过校验', TRUE, 200, 'SYSTEM', NOW(), 'SYSTEM', NOW(), FALSE, 0);

-- =====================================================
-- 事件存储表 (shared-event-starter 的 JdbcEventStore 使用)
-- 纯技术日志表,使用原生 JDBC + NOW() (符合 06-数据库规范.md 第十节例外)
-- =====================================================

CREATE TABLE IF NOT EXISTS sys_event_store (
    event_id            VARCHAR(64)   NOT NULL,
    event_type          VARCHAR(255)  NOT NULL,
    integration_type    VARCHAR(64),
    occurred_on         TIMESTAMP     NOT NULL,
    domain_payload      CLOB          NOT NULL,
    integration_payload CLOB,
    created_at          TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id)
);

CREATE INDEX idx_sys_event_store_event_type     ON sys_event_store(event_type);
CREATE INDEX idx_sys_event_store_occurred_on    ON sys_event_store(occurred_on);
CREATE INDEX idx_sys_event_store_integration    ON sys_event_store(integration_type);

CREATE TABLE IF NOT EXISTS sys_event_dispatch_log (
    id              BIGINT       GENERATED BY DEFAULT AS IDENTITY,
    event_id        VARCHAR(64)  NOT NULL,
    channel         VARCHAR(100) NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    error_msg       CLOB,
    retry_count     INT          DEFAULT 0,
    next_retry_at   TIMESTAMP,
    created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_event_channel UNIQUE (event_id, channel)
);

CREATE INDEX idx_sys_event_dispatch_log_status       ON sys_event_dispatch_log(status);
CREATE INDEX idx_sys_event_dispatch_log_next_retry   ON sys_event_dispatch_log(next_retry_at);
CREATE INDEX idx_sys_event_dispatch_log_retry_count  ON sys_event_dispatch_log(retry_count);
