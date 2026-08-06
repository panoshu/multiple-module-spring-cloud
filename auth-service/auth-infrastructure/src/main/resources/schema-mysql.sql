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
    pending_expires_at          DATETIME     NOT NULL                  COMMENT '待授权超时时间(5分钟窗口)',
    authorized_at               DATETIME                               COMMENT '授权时间',
    expires_at                  DATETIME     NOT NULL                  COMMENT '会话过期时间',
    revoke_reason               VARCHAR(255)                           COMMENT '撤销原因',
    created_by                  VARCHAR(64)  NOT NULL,
    create_time                 DATETIME     NOT NULL,
    updated_by                  VARCHAR(64)  NOT NULL,
    update_time                 DATETIME     NOT NULL,
    deleted                     TINYINT(1)   NOT NULL DEFAULT 0,
    version                     INT          NOT NULL DEFAULT 0,
    active_teller_key           VARCHAR(32)  GENERATED ALWAYS AS (CASE WHEN deleted = 0 AND status IN ('PENDING','AUTHORIZED') THEN teller_account_id ELSE NULL END) STORED COMMENT '活跃柜员唯一键(生成列)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_secondary_auth_teller_active (active_teller_key),
    KEY idx_teller_account (teller_account_id, status, deleted),
    KEY idx_approver_pending (approver_account_id, status, deleted),
    KEY idx_approver_authorized (approver_account_id, status, deleted),
    KEY idx_expires (expires_at, status, deleted),
    KEY idx_plan (plan_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='二次授权会话表';

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
    id                    VARCHAR(32)   NOT NULL                  COMMENT '用户ID(ULID)',
    user_type             VARCHAR(32)   NOT NULL                  COMMENT '用户类型: AGENT/OPERATOR/TELLER',
    identity_type         VARCHAR(32)   NOT NULL                  COMMENT '证件类型: ID_CARD/PASSPORT 等',
    identity_number       VARCHAR(64)   NOT NULL                  COMMENT '证件号码',
    mobile                VARCHAR(32)                             COMMENT '手机号',
    email                 VARCHAR(128)                            COMMENT '邮箱',
    telephone_area_code   VARCHAR(16)                             COMMENT '电话区号',
    telephone_number      VARCHAR(32)                             COMMENT '电话号码',
    telephone_extension   VARCHAR(16)                             COMMENT '分机号',
    address_country       VARCHAR(64)                             COMMENT '国家',
    address_province      VARCHAR(64)                             COMMENT '省份',
    address_city          VARCHAR(64)                             COMMENT '城市',
    address_district      VARCHAR(64)                             COMMENT '区县',
    address_detail        VARCHAR(255)                            COMMENT '详细地址',
    postal_code           VARCHAR(16)                             COMMENT '邮政编码',
    status                VARCHAR(16)   NOT NULL                  COMMENT '用户状态: ACTIVE/FROZEN/DISABLED',
    created_by            VARCHAR(64)   NOT NULL,
    create_time           DATETIME     NOT NULL,
    updated_by            VARCHAR(64),
    update_time           DATETIME,
    deleted               TINYINT(1)   NOT NULL DEFAULT 0,
    version               INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_t_auth_user_identity (identity_type, identity_number, deleted),
    KEY idx_t_auth_user_mobile (mobile, deleted),
    KEY idx_t_auth_user_status (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ========== Credential 表 ==========
-- Credential 聚合根（sealed abstract，子类 PasswordCredential + UKeyCredential）
-- 采用单表继承策略，credential_type 区分子类

CREATE TABLE IF NOT EXISTS t_auth_credential (
    id                    VARCHAR(32)   NOT NULL                  COMMENT '凭证ID(ULID)',
    credential_type       VARCHAR(32)   NOT NULL                  COMMENT '凭证类型: PASSWORD/U_KEY',
    owner_type            VARCHAR(32)   NOT NULL                  COMMENT '持有者类型: UserCredentialOwner/CustomerCredentialOwner/PlanCredentialOwner',
    owner_id              VARCHAR(64)   NOT NULL                  COMMENT '持有者ID',
    applicable_channels   JSON         NOT NULL                  COMMENT '适用渠道集合 JSON 数组',
    validity_start        DATETIME                               COMMENT '有效期开始',
    validity_end          DATETIME                               COMMENT '有效期结束',
    status                VARCHAR(16)   NOT NULL                  COMMENT '状态: ACTIVE/REVOKED/DISABLED',
    -- PasswordCredential 专属字段
    user_no               VARCHAR(32)                             COMMENT '密码所属账号(Password 专属)',
    password_hash         VARCHAR(255)                            COMMENT '密码哈希(Password 专属)',
    -- UKeyCredential 专属字段
    key_serial            VARCHAR(128)                            COMMENT 'UKey 序列号(UKey 专属)',
    -- 通用字段
    created_by            VARCHAR(64)   NOT NULL,
    create_time           DATETIME     NOT NULL,
    updated_by            VARCHAR(64),
    update_time           DATETIME,
    deleted               TINYINT(1)   NOT NULL DEFAULT 0,
    version               INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_t_auth_credential_ukey_serial (key_serial, deleted),
    KEY idx_t_auth_credential_owner (owner_type, owner_id, deleted),
    KEY idx_t_auth_credential_type (credential_type, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='凭证表';

-- ========== RoleTemplate 表 ==========
-- 角色权限模板聚合根 RoleTemplate 持久化

CREATE TABLE IF NOT EXISTS t_auth_role_template (
    id                VARCHAR(32)   NOT NULL                  COMMENT '模板ID(ULID)',
    role_code         VARCHAR(64)   NOT NULL                  COMMENT '角色编码',
    scope_dimension   VARCHAR(32)   NOT NULL                  COMMENT '范围维度: GLOBAL/CUSTOMER/PRODUCT/PLAN',
    scope_value       VARCHAR(64)                             COMMENT '范围值，GLOBAL 时为 NULL',
    permissions       JSON         NOT NULL                  COMMENT '权限集合 JSON 数组',
    status            VARCHAR(16)   NOT NULL                  COMMENT '状态: DRAFT/EFFECTIVE/INACTIVE',
    created_by        VARCHAR(64)   NOT NULL,
    create_time       DATETIME     NOT NULL,
    updated_by        VARCHAR(64),
    update_time       DATETIME,
    deleted           TINYINT(1)   NOT NULL DEFAULT 0,
    version           INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_t_auth_role_template_code (role_code, status, deleted),
    KEY idx_t_auth_role_template_scope (scope_dimension, scope_value, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限模板表';

-- ========== RoleVisibilityScope 表 ==========
-- 角色可见性范围（值对象表，无聚合根 ID）
-- 按 (dimension, scope_value) 做 upsert

CREATE TABLE IF NOT EXISTS t_auth_role_visibility (
    id              BIGINT       NOT NULL AUTO_INCREMENT          COMMENT '自增主键',
    dimension       VARCHAR(32)  NOT NULL                          COMMENT '维度: PLAN/CUSTOMER',
    scope_value     VARCHAR(64)  NOT NULL                          COMMENT '范围值',
    mode            VARCHAR(16)  NOT NULL                          COMMENT '可见性模式: SHOW_ALL/EXCLUSIVE_ONLY',
    created_by      VARCHAR(64)  NOT NULL,
    create_time     DATETIME     NOT NULL,
    updated_by      VARCHAR(64),
    update_time     DATETIME,
    deleted         TINYINT(1)   NOT NULL DEFAULT 0,
    version         INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_t_auth_role_visibility_scope (dimension, scope_value, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色可见性范围表';

-- ========== Assignment 表 ==========
-- 身份分配聚合根 AgentIdentityAssignment 持久化

CREATE TABLE IF NOT EXISTS t_auth_assignment (
    id                VARCHAR(32)   NOT NULL                  COMMENT '分配ID(ULID)',
    user_no           VARCHAR(32)   NOT NULL                  COMMENT '用户账号',
    role_code         VARCHAR(64)   NOT NULL                  COMMENT '角色编码',
    scope_dimension   VARCHAR(32)   NOT NULL                  COMMENT '范围维度: PLAN/CUSTOMER/PRODUCT/GLOBAL',
    scope_value       VARCHAR(64)   NOT NULL                  COMMENT '范围值',
    inheritable       TINYINT(1)   NOT NULL DEFAULT 0         COMMENT '是否级联下级客户（仅 CUSTOMER 维度有效）',
    status            VARCHAR(16)   NOT NULL                  COMMENT '状态: ACTIVE/DEACTIVATED',
    created_by        VARCHAR(64)   NOT NULL,
    create_time       DATETIME     NOT NULL,
    updated_by        VARCHAR(64),
    update_time       DATETIME,
    deleted           TINYINT(1)   NOT NULL DEFAULT 0,
    version           INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_t_auth_assignment_user (user_no, status, deleted),
    KEY idx_t_auth_assignment_scope (scope_dimension, scope_value, deleted),
    KEY idx_t_auth_assignment_role (role_code, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='身份分配表';

-- ========== Grant 表 ==========
-- 授权策略主记录聚合根 Grant 持久化
-- subject/scopeRules/permissions 均以 JSON 存储
-- subject 是 sealed interface，通过 {"type":"...","data":{...}} 结构区分多态

CREATE TABLE IF NOT EXISTS t_auth_grant (
    id                VARCHAR(32)   NOT NULL                  COMMENT 'Grant ID(ULID)',
    subject           JSON         NOT NULL                  COMMENT '授权主体 JSON，含类型标识区分多态',
    scope_rules       JSON         NOT NULL                  COMMENT '范围规则集合 JSON',
    permissions       JSON         NOT NULL                  COMMENT '权限集合 JSON',
    grant_type        VARCHAR(32)   NOT NULL                  COMMENT '类型: BASE/DELEGATE_WHOLESALE/DELEGATE_SELECTIVE',
    origin            VARCHAR(32)   NOT NULL                  COMMENT '来源: HQ_CONFIG/PLAN_DELEGATE/CUSTOMER_TO_AGENT/ROLE_TEMPLATE',
    effect            VARCHAR(16)   NOT NULL                  COMMENT '效果: ALLOW/DENY',
    source_plan_no    VARCHAR(32)                             COMMENT '代办-授权方计划编号',
    target_plan_no    VARCHAR(32)                             COMMENT '代办-接受方计划编号',
    status            VARCHAR(16)   NOT NULL                  COMMENT '状态: DRAFT/PENDING_APPROVAL/EFFECTIVE/REJECTED/REVOKED',
    validity_start    DATETIME                                COMMENT '有效期开始时间',
    validity_end      DATETIME                                COMMENT '有效期结束时间(null=长期有效)',
    created_by        VARCHAR(64)   NOT NULL,
    create_time       DATETIME     NOT NULL,
    updated_by        VARCHAR(64),
    update_time       DATETIME,
    deleted           TINYINT(1)   NOT NULL DEFAULT 0,
    version           INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_t_auth_grant_status (status, deleted),
    KEY idx_t_auth_grant_type (grant_type, status, deleted),
    KEY idx_t_auth_grant_origin (origin, status, deleted),
    KEY idx_t_auth_grant_validity (validity_start, validity_end, deleted),
    KEY idx_t_auth_grant_source_plan (source_plan_no, deleted),
    KEY idx_t_auth_grant_target_plan (target_plan_no, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='授权策略主记录表';

-- ========== PermissionItem 表 ==========
-- 权限点元数据聚合根 PermissionItem 持久化
-- 由 PermissionScanner 自动扫描 @RequirePermission 注解 upsert 写入

CREATE TABLE IF NOT EXISTS t_auth_permission_item (
    id              VARCHAR(32)   NOT NULL                  COMMENT '权限点ID(ULID)',
    business_code   VARCHAR(64)   NOT NULL                  COMMENT '业务编码',
    action_code     VARCHAR(64)                             COMMENT '操作编码（NULL=整个业务）',
    category        VARCHAR(16)   NOT NULL                  COMMENT '权限类别: BUSINESS/PLATFORM',
    source          VARCHAR(16)   NOT NULL                  COMMENT '来源: API/MANUAL',
    controller      VARCHAR(255)                            COMMENT '控制器类名',
    method          VARCHAR(255)                            COMMENT '方法名',
    http_method     VARCHAR(16)                             COMMENT 'HTTP方法',
    path            VARCHAR(512)                            COMMENT '请求路径',
    display_name    VARCHAR(128)                            COMMENT '展示名称',
    description     VARCHAR(512)                            COMMENT '描述',
    category_group  VARCHAR(64)                             COMMENT '分类分组',
    sort_order      INT           NOT NULL DEFAULT 0        COMMENT '排序序号',
    auto_registered TINYINT(1)    NOT NULL DEFAULT 1        COMMENT '是否自动注册（被扫描器标记为 stale 时置 0）',
    created_by      VARCHAR(64)   NOT NULL,
    create_time     DATETIME      NOT NULL,
    updated_by      VARCHAR(64),
    update_time     DATETIME,
    deleted         TINYINT(1)    NOT NULL DEFAULT 0,
    version         INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_permission_item_biz_action (business_code, action_code),
    KEY idx_permission_item_category (category),
    KEY idx_permission_item_group (category_group)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限点元数据表';

-- ========== CustomerChannelEntitlement 表 ==========
-- 客户渠道开通记录聚合根 CustomerChannelEntitlement 持久化
-- 记录客户开通了哪些登录渠道（网上/网点等），用于登录/二次授权准入校验

CREATE TABLE IF NOT EXISTS t_auth_customer_channel_entitlement (
    id                VARCHAR(32)   NOT NULL                  COMMENT '开通记录ID(ULID)',
    customer_no       VARCHAR(32)   NOT NULL                  COMMENT '客户编号',
    enabled_channels  JSON          NOT NULL                  COMMENT '已开通渠道集合 JSON 数组（AnnuityChannel.name）',
    created_by        VARCHAR(64)   NOT NULL,
    create_time       DATETIME      NOT NULL,
    updated_by        VARCHAR(64),
    update_time       DATETIME,
    deleted           TINYINT(1)    NOT NULL DEFAULT 0,
    version           INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_t_auth_customer_channel_entitlement_customer (customer_no, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户渠道开通记录表';

