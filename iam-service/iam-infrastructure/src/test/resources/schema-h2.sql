-- =====================================================
-- IAM 服务 H2 测试数据库脚本 (PostgreSQL 兼容模式)
-- 仅供 iam-infrastructure 集成测试使用:
--   - 仅包含 DDL,不含初始数据(测试自行准备数据)
--   - JSONB → CLOB
--   - 移除 CREATE INDEX 的 IF NOT EXISTS
--   - 保留 TIMESTAMP / BOOLEAN (H2 PostgreSQL 模式支持)
--   - deleted 列从 BOOLEAN 改为 INT —— MyBatis-Flex 的 @Column(isLogicDelete = true)
--     默认用 0/1 进行逻辑删除比较,PostgreSQL 可 BOOLEAN=INT 隐式转换,
--     但 H2 即便在 MODE=PostgreSQL 下也会抛 "BOOLEAN and INTEGER are not comparable"
-- 包含 13 张表 + 2 张事件存储表:
--   authentication 域: t_iam_user, t_iam_user_profile, t_iam_credential,
--                     t_iam_secondary_auth_session, t_iam_login_log,
--                     t_iam_login_failure_record
--   authorization 域 : t_iam_permission_rule, t_iam_plan_delegation,
--                     t_iam_plan_delegation_operator, t_iam_plan_delegation_permission,
--                     t_iam_business_definition, t_iam_business_action, t_iam_route_rule
--   事件存储         : sys_event_store, sys_event_dispatch_log
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
    deleted         INT          NOT NULL DEFAULT 0,
    version         INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_iam_user_channel_login
    ON t_iam_user (channel_type, login_name);

CREATE INDEX idx_iam_user_status
    ON t_iam_user (status);

CREATE INDEX idx_iam_user_channel
    ON t_iam_user (channel_type);

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
    deleted           INT          NOT NULL DEFAULT 0,
    version           INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id)
);

CREATE INDEX idx_iam_user_profile_branch
    ON t_iam_user_profile (branch_id);

CREATE INDEX idx_iam_user_profile_employee
    ON t_iam_user_profile (employee_no);

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
    deleted         INT          NOT NULL DEFAULT 0,
    version         INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_iam_credential_owner_type_active
    ON t_iam_credential (owner_type, owner_id, credential_type);

CREATE INDEX idx_iam_credential_owner
    ON t_iam_credential (owner_id, owner_type);

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
    deleted             INT          NOT NULL DEFAULT 0,
    version             INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_iam_secondary_auth_teller_status
    ON t_iam_secondary_auth_session (teller_id, status);

CREATE INDEX idx_iam_secondary_auth_approver
    ON t_iam_secondary_auth_session (approver_id, status);

CREATE INDEX idx_iam_secondary_auth_expire
    ON t_iam_secondary_auth_session (expire_at);

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
    deleted       INT          NOT NULL DEFAULT 0,
    version       INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_iam_login_log_user_time
    ON t_iam_login_log (user_id, channel_type, login_time);

CREATE INDEX idx_iam_login_log_name_time
    ON t_iam_login_log (login_name, channel_type, login_time);

CREATE INDEX idx_iam_login_log_success_time
    ON t_iam_login_log (success, login_time);

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
    deleted       INT          NOT NULL DEFAULT 0,
    version       INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX idx_iam_login_failure_log
    ON t_iam_login_failure_record (login_log_id);

CREATE INDEX idx_iam_login_failure_reason_time
    ON t_iam_login_failure_record (reason, failure_time);

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
    deleted             INT          NOT NULL DEFAULT 0,
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
    deleted            INT          NOT NULL DEFAULT 0,
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

-- ---------- 9. 代办指定操作员表(PlanDelegation 子表) ----------
CREATE TABLE IF NOT EXISTS t_iam_plan_delegation_operator (
    id             BIGINT       NOT NULL,
    delegation_id  BIGINT       NOT NULL,
    operator_id    BIGINT       NOT NULL,
    created_by     VARCHAR(64)  NOT NULL,
    create_time    TIMESTAMP    NOT NULL,
    updated_by     VARCHAR(64)  NOT NULL,
    update_time    TIMESTAMP    NOT NULL,
    deleted        INT          NOT NULL DEFAULT 0,
    version        INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_iam_plan_delegation_operator
    ON t_iam_plan_delegation_operator (delegation_id, operator_id, deleted);

CREATE INDEX idx_iam_plan_delegation_operator_delegation
    ON t_iam_plan_delegation_operator (delegation_id);

CREATE INDEX idx_iam_plan_delegation_operator_operator
    ON t_iam_plan_delegation_operator (operator_id);

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
    deleted        INT          NOT NULL DEFAULT 0,
    version        INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_iam_plan_delegation_permission
    ON t_iam_plan_delegation_permission (delegation_id, business_code, action, deleted);

CREATE INDEX idx_iam_plan_delegation_permission_delegation
    ON t_iam_plan_delegation_permission (delegation_id);

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
    deleted           INT          NOT NULL DEFAULT 0,
    version           INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_iam_business_definition_code
    ON t_iam_business_definition (business_code);

CREATE INDEX idx_iam_business_definition_active
    ON t_iam_business_definition (active);

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
    deleted         INT          NOT NULL DEFAULT 0,
    version         INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_iam_business_action_def_action
    ON t_iam_business_action (definition_id, action, deleted);

CREATE INDEX idx_iam_business_action_definition
    ON t_iam_business_action (definition_id);

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
    deleted        INT          NOT NULL DEFAULT 0,
    version        INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_iam_route_rule_pattern
    ON t_iam_route_rule (route_pattern);

CREATE INDEX idx_iam_route_rule_enabled_priority
    ON t_iam_route_rule (enabled, priority);

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
