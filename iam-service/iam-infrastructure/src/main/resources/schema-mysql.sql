-- =====================================================
-- IAM 服务 MySQL 数据库脚本
-- 包含 13 张表:
--   authentication 域: t_iam_user, t_iam_user_profile, t_iam_credential,
--                     t_iam_secondary_auth_session, t_iam_login_log,
--                     t_iam_login_failure_record
--   authorization 域 : t_iam_permission_rule, t_iam_plan_delegation,
--                     t_iam_plan_delegation_operator, t_iam_plan_delegation_permission,
--                     t_iam_business_definition, t_iam_business_action, t_iam_route_rule
--
-- 注意: MySQL 不支持部分索引(partial index),软删除过滤逻辑由应用层 QueryWrapper 保证
--       唯一性约束通过复合索引(包含 deleted 列)间接实现软删除后重建同名记录
-- =====================================================

-- ---------- 1. 用户主表 ----------
CREATE TABLE IF NOT EXISTS `t_iam_user` (
    `id`              BIGINT       NOT NULL                  COMMENT '用户ID(由 shared-id-starter 生成)',
    `channel_type`    VARCHAR(16)  NOT NULL                  COMMENT '渠道类型 INTERNET/HQ/BRANCH',
    `login_name`      VARCHAR(64)  NOT NULL                  COMMENT '登录名',
    `display_name`     VARCHAR(128) DEFAULT NULL             COMMENT '显示名',
    `status`          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '用户状态 ACTIVE/DISABLED/LOCKED',
    `last_login_time` TIMESTAMP    NULL DEFAULT NULL         COMMENT '最后登录时间(由应用层管理)',
    `last_login_ip`   VARCHAR(64)  DEFAULT NULL             COMMENT '最后登录IP',
    `created_by`      VARCHAR(64)  NOT NULL                  COMMENT '创建人',
    `create_time`     TIMESTAMP    NOT NULL                  COMMENT '创建时间',
    `updated_by`      VARCHAR(64)  NOT NULL                  COMMENT '更新人',
    `update_time`     TIMESTAMP    NOT NULL                  COMMENT '更新时间',
    `deleted`         TINYINT(1)   NOT NULL DEFAULT 0        COMMENT '删除标记: 0-未删除, 1-已删除',
    `version`         INT          NOT NULL DEFAULT 0        COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_iam_user_channel_login` (`channel_type`, `login_name`, `deleted`),
    KEY `idx_iam_user_status` (`status`, `deleted`),
    KEY `idx_iam_user_channel` (`channel_type`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IAM 用户主表';

-- ---------- 2. 用户渠道档案表(与 t_iam_user 1:1 共享主键) ----------
CREATE TABLE IF NOT EXISTS `t_iam_user_profile` (
    `user_id`           BIGINT       NOT NULL                  COMMENT '用户ID(共享主键,等于 t_iam_user.id)',
    `channel_type`      VARCHAR(16)  NOT NULL                  COMMENT '渠道类型',
    `email`             VARCHAR(128) DEFAULT NULL              COMMENT '邮箱',
    `phone`             VARCHAR(32)  DEFAULT NULL              COMMENT '电话',
    `organization`      VARCHAR(255) DEFAULT NULL             COMMENT '所属组织',
    `position`          VARCHAR(128) DEFAULT NULL              COMMENT '职位',
    `branch_id`         VARCHAR(64)  DEFAULT NULL              COMMENT '网点编号(网点渠道必填)',
    `employee_no`       VARCHAR(64)  DEFAULT NULL              COMMENT '员工工号',
    `extra_attributes`  JSON         DEFAULT NULL              COMMENT '扩展属性(JSON)',
    `created_by`        VARCHAR(64)  NOT NULL                  COMMENT '创建人',
    `create_time`       TIMESTAMP    NOT NULL                  COMMENT '创建时间',
    `updated_by`        VARCHAR(64)  NOT NULL                  COMMENT '更新人',
    `update_time`       TIMESTAMP    NOT NULL                  COMMENT '更新时间',
    `deleted`           TINYINT(1)   NOT NULL DEFAULT 0        COMMENT '删除标记',
    `version`           INT          NOT NULL DEFAULT 0         COMMENT '乐观锁版本号',
    PRIMARY KEY (`user_id`),
    KEY `idx_iam_user_profile_branch` (`branch_id`, `deleted`),
    KEY `idx_iam_user_profile_employee` (`employee_no`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户渠道档案表(与 t_iam_user 1:1)';

-- ---------- 3. 凭据表 ----------
CREATE TABLE IF NOT EXISTS `t_iam_credential` (
    `id`               BIGINT       NOT NULL                   COMMENT '凭据ID',
    `owner_type`       VARCHAR(32)  NOT NULL                   COMMENT '归属类型 INTERNET_USER/HQ_USER/BRANCH_USER',
    `owner_id`         BIGINT       NOT NULL                   COMMENT '归属实体ID(User.id)',
    `credential_type`  VARCHAR(32)  NOT NULL                   COMMENT '凭据类型 PASSWORD/UKEY/DYNAMIC_TOKEN',
    `secret_hash`      VARCHAR(255) NOT NULL                   COMMENT '密文(BCrypt哈希/RSA公钥指纹/TOTP seed)',
    `salt`             VARCHAR(255) DEFAULT NULL               COMMENT '盐值(BCrypt 内嵌盐时为 NULL)',
    `aux_data`         JSON         DEFAULT NULL               COMMENT '辅助数据(JSON)',
    `status`           VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE'  COMMENT '状态 ACTIVE/EXPIRED/REVOKED',
    `expire_time`      TIMESTAMP    NULL DEFAULT NULL          COMMENT '过期时间(NULL表示永久)',
    `created_by`       VARCHAR(64)  NOT NULL                  COMMENT '创建人',
    `create_time`      TIMESTAMP    NOT NULL                  COMMENT '创建时间',
    `updated_by`       VARCHAR(64)  NOT NULL                  COMMENT '更新人',
    `update_time`      TIMESTAMP    NOT NULL                  COMMENT '更新时间',
    `deleted`          TINYINT(1)   NOT NULL DEFAULT 0        COMMENT '删除标记',
    `version`          INT          NOT NULL DEFAULT 0        COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iam_credential_owner_type_active` (`owner_type`, `owner_id`, `credential_type`, `deleted`, `status`),
    KEY `idx_iam_credential_owner` (`owner_id`, `owner_type`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='凭据表(密码/UKey/动态令牌)';

-- ---------- 4. 二次授权会话表(网点渠道专属) ----------
CREATE TABLE IF NOT EXISTS `t_iam_secondary_auth_session` (
    `id`                  BIGINT       NOT NULL                COMMENT '会话ID',
    `teller_id`           BIGINT       NOT NULL                 COMMENT '柜员用户ID',
    `approver_id`         BIGINT       NOT NULL                 COMMENT '经办人用户ID',
    `customer_no`         VARCHAR(64)  NOT NULL                 COMMENT '客户编号',
    `plan_id`             VARCHAR(64)  NOT NULL                 COMMENT '计划编号',
    `permission_snapshot` JSON         DEFAULT NULL             COMMENT '权限快照(JSON 数组)',
    `status`              VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态 PENDING/AUTHORIZED/REJECTED/EXPIRED/REVOKED/CLOSED',
    `initiated_at`        TIMESTAMP    NOT NULL                 COMMENT '发起时间',
    `authorized_at`       TIMESTAMP    NULL DEFAULT NULL        COMMENT '授权时间',
    `expire_at`           TIMESTAMP    NULL DEFAULT NULL        COMMENT '过期时间',
    `revoke_reason`       VARCHAR(512) DEFAULT NULL             COMMENT '撤销原因',
    `created_by`          VARCHAR(64)  NOT NULL                COMMENT '创建人',
    `create_time`         TIMESTAMP    NOT NULL                COMMENT '创建时间',
    `updated_by`          VARCHAR(64)  NOT NULL                COMMENT '更新人',
    `update_time`         TIMESTAMP    NOT NULL                COMMENT '更新时间',
    `deleted`             TINYINT(1)   NOT NULL DEFAULT 0       COMMENT '删除标记',
    `version`             INT          NOT NULL DEFAULT 0       COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_iam_secondary_auth_teller_status` (`teller_id`, `status`, `deleted`),
    KEY `idx_iam_secondary_auth_approver` (`approver_id`, `status`, `deleted`),
    KEY `idx_iam_secondary_auth_expire` (`expire_at`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='二次授权会话表(网点柜员借用经办人权限)';

-- ---------- 5. 登录日志表 ----------
CREATE TABLE IF NOT EXISTS `t_iam_login_log` (
    `id`            BIGINT       NOT NULL                    COMMENT '日志ID',
    `user_id`       BIGINT       DEFAULT NULL                COMMENT '用户ID(用户不存在时为 NULL)',
    `login_name`    VARCHAR(64)  NOT NULL                    COMMENT '登录名',
    `channel_type`  VARCHAR(16)  NOT NULL                    COMMENT '渠道类型',
    `success`       TINYINT(1)   NOT NULL                    COMMENT '是否登录成功: 0-失败, 1-成功',
    `login_time`    TIMESTAMP    NOT NULL                    COMMENT '登录时间',
    `login_ip`      VARCHAR(64)  DEFAULT NULL                COMMENT '登录IP',
    `user_agent`    VARCHAR(512) DEFAULT NULL                COMMENT 'User-Agent',
    `created_by`    VARCHAR(64)  NOT NULL                    COMMENT '创建人',
    `create_time`   TIMESTAMP    NOT NULL                    COMMENT '创建时间',
    `updated_by`    VARCHAR(64)  NOT NULL                    COMMENT '更新人',
    `update_time`   TIMESTAMP    NOT NULL                    COMMENT '更新时间',
    `deleted`       TINYINT(1)   NOT NULL DEFAULT 0          COMMENT '删除标记',
    `version`       INT          NOT NULL DEFAULT 0          COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_iam_login_log_user_time` (`user_id`, `channel_type`, `login_time`, `deleted`),
    KEY `idx_iam_login_log_name_time` (`login_name`, `channel_type`, `login_time`, `deleted`),
    KEY `idx_iam_login_log_success_time` (`success`, `login_time`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志表(审计每次登录尝试)';

-- ---------- 6. 登录失败记录表(LoginLog 子表) ----------
CREATE TABLE IF NOT EXISTS `t_iam_login_failure_record` (
    `id`            BIGINT       NOT NULL                    COMMENT '记录ID',
    `login_log_id`  BIGINT       NOT NULL                    COMMENT '关联登录日志ID',
    `reason`        VARCHAR(64)  NOT NULL                    COMMENT '失败原因代码(如 WRONG_PASSWORD/USER_NOT_FOUND)',
    `detail`        VARCHAR(512) DEFAULT NULL                COMMENT '人类可读详情',
    `failure_time`  TIMESTAMP    NOT NULL                    COMMENT '失败时间',
    `created_by`    VARCHAR(64)  NOT NULL                    COMMENT '创建人',
    `create_time`   TIMESTAMP    NOT NULL                    COMMENT '创建时间',
    `updated_by`    VARCHAR(64)  NOT NULL                    COMMENT '更新人',
    `update_time`   TIMESTAMP    NOT NULL                    COMMENT '更新时间',
    `deleted`       TINYINT(1)   NOT NULL DEFAULT 0          COMMENT '删除标记',
    `version`       INT          NOT NULL DEFAULT 0          COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_iam_login_failure_log` (`login_log_id`, `deleted`),
    KEY `idx_iam_login_failure_reason_time` (`reason`, `failure_time`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录失败记录表(子表)';

-- ---------- 7. 权限规则表 ----------
CREATE TABLE IF NOT EXISTS `t_iam_permission_rule` (
    `id`                  BIGINT       NOT NULL                COMMENT '规则ID',
    `rule_code`           VARCHAR(64)  NOT NULL                COMMENT '规则编码(全局唯一)',
    `rule_name`           VARCHAR(128) NOT NULL                COMMENT '规则名称',
    `subject_type`        VARCHAR(32)  NOT NULL                COMMENT '主体维度 CUSTOMER/OPERATION_MODE/PRODUCT/PLAN/ACCOUNT_MANAGER',
    `subject_id`          VARCHAR(64)  NOT NULL                COMMENT '主体标识',
    `business_code`       VARCHAR(64)  NOT NULL                COMMENT '业务编码(关联 BusinessDefinition)',
    `allowed_actions`    JSON         NOT NULL                COMMENT '授权动作集合(JSON 数组,如 ["HANDLE","QUERY"])',
    `inherit_to_children` TINYINT(1)  NOT NULL DEFAULT 0      COMMENT '是否继承给下属企业: 0-否, 1-是(仅 CUSTOMER 级有意义)',
    `override_mode`       VARCHAR(16)  NOT NULL DEFAULT 'ADD'  COMMENT '覆盖模式 ADD 扩展 / REMOVE 收紧',
    `priority`            INT          DEFAULT NULL            COMMENT '优先级(NULL则使用 SubjectType.priority)',
    `status`              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态 ACTIVE/DISABLED',
    `effective_at`        TIMESTAMP    NOT NULL                COMMENT '生效时间',
    `expire_at`           TIMESTAMP    NULL DEFAULT NULL        COMMENT '失效时间(NULL表示永久)',
    `created_by`          VARCHAR(64)  NOT NULL                COMMENT '创建人',
    `create_time`         TIMESTAMP    NOT NULL                COMMENT '创建时间',
    `updated_by`          VARCHAR(64)  NOT NULL                COMMENT '更新人',
    `update_time`         TIMESTAMP    NOT NULL                COMMENT '更新时间',
    `deleted`             TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '删除标记',
    `version`             INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iam_permission_rule_code` (`rule_code`, `deleted`),
    KEY `idx_iam_perm_rule_subject` (`subject_type`, `subject_id`, `status`, `deleted`),
    KEY `idx_iam_perm_rule_subject_biz` (`subject_type`, `subject_id`, `business_code`, `status`, `deleted`),
    KEY `idx_iam_perm_rule_status_time` (`status`, `effective_at`, `expire_at`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限规则表(授权域核心配置)';

-- ---------- 8. 计划代办关系表 ----------
CREATE TABLE IF NOT EXISTS `t_iam_plan_delegation` (
    `id`                BIGINT       NOT NULL                 COMMENT '代办关系ID',
    `delegation_code`   VARCHAR(64)  NOT NULL                 COMMENT '代办编码(全局唯一)',
    `delegator_plan_no` VARCHAR(64)  NOT NULL                 COMMENT '授权方计划编号',
    `delegatee_plan_no` VARCHAR(64)  NOT NULL                 COMMENT '被授权方计划编号',
    `delegation_type`   VARCHAR(32)  NOT NULL                 COMMENT '代办类型 ALL_OPERATORS/SPECIFIC_OPERATORS',
    `status`            VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态 ACTIVE/REVOKED/EXPIRED',
    `effective_at`      TIMESTAMP    NOT NULL                 COMMENT '生效时间',
    `expire_at`         TIMESTAMP    NULL DEFAULT NULL         COMMENT '失效时间(NULL表示永久)',
    `created_by`        VARCHAR(64)  NOT NULL                COMMENT '创建人',
    `create_time`       TIMESTAMP    NOT NULL                COMMENT '创建时间',
    `updated_by`        VARCHAR(64)  NOT NULL                COMMENT '更新人',
    `update_time`       TIMESTAMP    NOT NULL                COMMENT '更新时间',
    `deleted`           TINYINT(1)   NOT NULL DEFAULT 0       COMMENT '删除标记',
    `version`           INT          NOT NULL DEFAULT 0        COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iam_plan_delegation_code` (`delegation_code`, `deleted`),
    KEY `idx_iam_plan_delegation_delegator` (`delegator_plan_no`, `status`, `deleted`),
    KEY `idx_iam_plan_delegation_delegatee` (`delegatee_plan_no`, `status`, `deleted`),
    KEY `idx_iam_plan_delegation_expire` (`expire_at`, `status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计划代办关系表';

-- ---------- 9. 代办指定操作员表(PlanDelegation 子表) ----------
CREATE TABLE IF NOT EXISTS `t_iam_plan_delegation_operator` (
    `id`            BIGINT       NOT NULL                    COMMENT '记录ID',
    `delegation_id` BIGINT       NOT NULL                    COMMENT '代办关系ID(FK to t_iam_plan_delegation.id)',
    `operator_id`   BIGINT       NOT NULL                    COMMENT '操作员ID(FK to t_iam_user.id)',
    `created_by`    VARCHAR(64)  NOT NULL                    COMMENT '创建人',
    `create_time`   TIMESTAMP    NOT NULL                    COMMENT '创建时间',
    `updated_by`    VARCHAR(64)  NOT NULL                    COMMENT '更新人',
    `update_time`   TIMESTAMP    NOT NULL                    COMMENT '更新时间',
    `deleted`       TINYINT(1)   NOT NULL DEFAULT 0          COMMENT '删除标记',
    `version`       INT          NOT NULL DEFAULT 0          COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iam_plan_delegation_operator` (`delegation_id`, `operator_id`, `deleted`),
    KEY `idx_iam_plan_delegation_operator_delegation` (`delegation_id`, `deleted`),
    KEY `idx_iam_plan_delegation_operator_operator` (`operator_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代办指定操作员表(子表)';

-- ---------- 10. 代办授权权限表(PlanDelegation 子表) ----------
CREATE TABLE IF NOT EXISTS `t_iam_plan_delegation_permission` (
    `id`            BIGINT       NOT NULL                    COMMENT '记录ID',
    `delegation_id` BIGINT       NOT NULL                    COMMENT '代办关系ID(FK to t_iam_plan_delegation.id)',
    `business_code` VARCHAR(64)  NOT NULL                    COMMENT '业务编码',
    `action`        VARCHAR(16)  NOT NULL                    COMMENT '业务动作 HANDLE/QUERY/AUDIT',
    `created_by`    VARCHAR(64)  NOT NULL                    COMMENT '创建人',
    `create_time`   TIMESTAMP    NOT NULL                    COMMENT '创建时间',
    `updated_by`    VARCHAR(64)  NOT NULL                    COMMENT '更新人',
    `update_time`   TIMESTAMP    NOT NULL                    COMMENT '更新时间',
    `deleted`       TINYINT(1)   NOT NULL DEFAULT 0          COMMENT '删除标记',
    `version`       INT          NOT NULL DEFAULT 0          COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iam_plan_delegation_permission` (`delegation_id`, `business_code`, `action`, `deleted`),
    KEY `idx_iam_plan_delegation_permission_delegation` (`delegation_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代办授权权限表(子表)';

-- ---------- 11. 业务定义表 ----------
CREATE TABLE IF NOT EXISTS `t_iam_business_definition` (
    `id`                BIGINT       NOT NULL                COMMENT '业务定义ID',
    `business_code`     VARCHAR(64)  NOT NULL                COMMENT '业务编码(全局唯一,如 ANNUITY_ESTABLISH)',
    `business_name`     VARCHAR(128) NOT NULL                COMMENT '业务名称',
    `description`       VARCHAR(512) DEFAULT NULL             COMMENT '业务描述',
    `supported_actions` JSON         DEFAULT NULL             COMMENT '支持动作集合(JSON,冗余字段)',
    `active`            TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '是否启用: 0-禁用, 1-启用',
    `created_by`        VARCHAR(64)  NOT NULL                COMMENT '创建人',
    `create_time`       TIMESTAMP    NOT NULL                COMMENT '创建时间',
    `updated_by`        VARCHAR(64)  NOT NULL                COMMENT '更新人',
    `update_time`       TIMESTAMP    NOT NULL                COMMENT '更新时间',
    `deleted`           TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '删除标记',
    `version`           INT          NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iam_business_definition_code` (`business_code`, `deleted`),
    KEY `idx_iam_business_definition_active` (`active`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务定义表';

-- ---------- 12. 业务动作表(BusinessDefinition 子表) ----------
CREATE TABLE IF NOT EXISTS `t_iam_business_action` (
    `id`            BIGINT       NOT NULL                    COMMENT '记录ID',
    `definition_id` BIGINT       NOT NULL                    COMMENT '业务定义ID(FK to t_iam_business_definition.id)',
    `action`        VARCHAR(16)  NOT NULL                    COMMENT '业务动作 HANDLE/QUERY/AUDIT',
    `description`   VARCHAR(512) DEFAULT NULL                 COMMENT '动作描述',
    `created_by`    VARCHAR(64)  NOT NULL                    COMMENT '创建人',
    `create_time`   TIMESTAMP    NOT NULL                    COMMENT '创建时间',
    `updated_by`    VARCHAR(64)  NOT NULL                    COMMENT '更新人',
    `update_time`   TIMESTAMP    NOT NULL                    COMMENT '更新时间',
    `deleted`       TINYINT(1)   NOT NULL DEFAULT 0          COMMENT '删除标记',
    `version`       INT          NOT NULL DEFAULT 0          COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iam_business_action_def_action` (`definition_id`, `action`, `deleted`),
    KEY `idx_iam_business_action_definition` (`definition_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务动作表(BusinessDefinition 子表)';

-- ---------- 13. 路由权限规则表 ----------
CREATE TABLE IF NOT EXISTS `t_iam_route_rule` (
    `id`            BIGINT       NOT NULL                    COMMENT '规则ID',
    `route_pattern` VARCHAR(255) NOT NULL                    COMMENT '路由匹配模式(Ant 风格,如 /internet/**)',
    `check_type`    VARCHAR(16)  NOT NULL                    COMMENT '校验类型 LOGIN/PERMISSION/ROLE/CHANNEL/SKIP',
    `check_value`    VARCHAR(255) DEFAULT NULL                COMMENT '校验值(权限码/角色名/渠道名,SKIP 时为 NULL)',
    `description`   VARCHAR(512) DEFAULT NULL                 COMMENT '规则描述',
    `enabled`       TINYINT(1)   NOT NULL DEFAULT 1          COMMENT '是否启用: 0-禁用, 1-启用',
    `priority`      INT          NOT NULL DEFAULT 0          COMMENT '优先级(数值越大优先级越高)',
    `created_by`    VARCHAR(64)  NOT NULL                    COMMENT '创建人',
    `create_time`   TIMESTAMP    NOT NULL                    COMMENT '创建时间',
    `updated_by`    VARCHAR(64)  NOT NULL                    COMMENT '更新人',
    `update_time`   TIMESTAMP    NOT NULL                    COMMENT '更新时间',
    `deleted`       TINYINT(1)   NOT NULL DEFAULT 0          COMMENT '删除标记',
    `version`       INT          NOT NULL DEFAULT 0          COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_iam_route_rule_pattern` (`route_pattern`, `deleted`),
    KEY `idx_iam_route_rule_enabled_priority` (`enabled`, `priority`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='路由权限规则表(网关动态鉴权配置)';

-- =====================================================
-- 初始化数据(业务定义 + 网关路由权限规则)
-- =====================================================

-- 业务定义初始化(支持的动作通过子表 t_iam_business_action 维护,此处 supported_actions 为冗余字段)
INSERT INTO `t_iam_business_definition` (`id`, `business_code`, `business_name`, `description`, `supported_actions`, `active`, `created_by`, `create_time`, `updated_by`, `update_time`, `deleted`, `version`)
VALUES
    (1, 'ANNUITY_ESTABLISH',  '年金计划设立', '企业年金计划设立业务', '["HANDLE","QUERY","AUDIT"]', 1, 'SYSTEM', NOW(), 'SYSTEM', NOW(), 0, 0),
    (2, 'ANNUITY_CONTRIBUTION','年金缴费',    '企业年金缴费业务',     '["HANDLE","QUERY"]',         1, 'SYSTEM', NOW(), 'SYSTEM', NOW(), 0, 0),
    (3, 'ANNUITY_PAYMENT',    '年金支付',     '企业年金支付业务',     '["HANDLE","QUERY","AUDIT"]', 1, 'SYSTEM', NOW(), 'SYSTEM', NOW(), 0, 0)
ON DUPLICATE KEY UPDATE `id` = `id`;

-- 业务动作明细初始化
INSERT INTO `t_iam_business_action` (`id`, `definition_id`, `action`, `description`, `created_by`, `create_time`, `updated_by`, `update_time`, `deleted`, `version`)
VALUES
    -- 年金计划设立(HANDLE/QUERY/AUDIT)
    (101, 1, 'HANDLE', '办理年金计划设立', 'SYSTEM', NOW(), 'SYSTEM', NOW(), 0, 0),
    (102, 1, 'QUERY',  '查询年金计划设立', 'SYSTEM', NOW(), 'SYSTEM', NOW(), 0, 0),
    (103, 1, 'AUDIT',  '审计年金计划设立', 'SYSTEM', NOW(), 'SYSTEM', NOW(), 0, 0),
    -- 年金缴费(HANDLE/QUERY)
    (104, 2, 'HANDLE', '办理年金缴费',     'SYSTEM', NOW(), 'SYSTEM', NOW(), 0, 0),
    (105, 2, 'QUERY',  '查询年金缴费',     'SYSTEM', NOW(), 'SYSTEM', NOW(), 0, 0),
    -- 年金支付(HANDLE/QUERY/AUDIT)
    (106, 3, 'HANDLE', '办理年金支付',     'SYSTEM', NOW(), 'SYSTEM', NOW(), 0, 0),
    (107, 3, 'QUERY',  '查询年金支付',     'SYSTEM', NOW(), 'SYSTEM', NOW(), 0, 0),
    (108, 3, 'AUDIT',  '审计年金支付',     'SYSTEM', NOW(), 'SYSTEM', NOW(), 0, 0)
ON DUPLICATE KEY UPDATE `id` = `id`;

-- 网关路由权限规则初始化
INSERT INTO `t_iam_route_rule` (`id`, `route_pattern`, `check_type`, `check_value`, `description`, `enabled`, `priority`, `created_by`, `create_time`, `updated_by`, `update_time`, `deleted`, `version`)
VALUES
    (1, '/internet/**',          'LOGIN',      NULL,                       '网上渠道登录校验',         1, 100, 'SYSTEM', NOW(), 'SYSTEM', NOW(), 0, 0),
    (2, '/hq/**',                'LOGIN',      NULL,                       '总部渠道登录校验',         1, 100, 'SYSTEM', NOW(), 'SYSTEM', NOW(), 0, 0),
    (3, '/branch/**',            'LOGIN',      NULL,                       '网点渠道登录校验',         1, 100, 'SYSTEM', NOW(), 'SYSTEM', NOW(), 0, 0),
    (4, '/internet/annuity/**',  'PERMISSION', 'ANNUITY_ESTABLISH.HANDLE', '网上渠道年金办理权限',     1,  90, 'SYSTEM', NOW(), 'SYSTEM', NOW(), 0, 0),
    (5, '/public/**',            'SKIP',       NULL,                       '公共接口跳过校验',         1, 200, 'SYSTEM', NOW(), 'SYSTEM', NOW(), 0, 0)
ON DUPLICATE KEY UPDATE `id` = `id`;

-- =====================================================
-- 事件存储表 (shared-event-starter 的 JdbcEventStore 使用)
-- 纯技术日志表,使用原生 JDBC + NOW() (符合 06-数据库规范.md 第十节例外)
-- =====================================================

CREATE TABLE IF NOT EXISTS `sys_event_store` (
  `event_id`            VARCHAR(64)   NOT NULL                          COMMENT '事件ID (EventId,主键)',
  `event_type`          VARCHAR(255)  NOT NULL                          COMMENT '领域事件类型名',
  `integration_type`    VARCHAR(64)   DEFAULT NULL                     COMMENT '集成事件类型名 (NULL 表示无集成事件)',
  `occurred_on`         TIMESTAMP     NOT NULL                          COMMENT '事件发生时间',
  `domain_payload`      TEXT          NOT NULL                          COMMENT '领域事件 JSON 序列化内容',
  `integration_payload` TEXT          DEFAULT NULL                     COMMENT '集成事件 JSON 序列化内容 (NULL 表示无集成事件)',
  `created_at`          TIMESTAMP     DEFAULT CURRENT_TIMESTAMP         COMMENT '记录创建时间',
  PRIMARY KEY (`event_id`),
  KEY `idx_sys_event_store_event_type`     (`event_type`),
  KEY `idx_sys_event_store_occurred_on`    (`occurred_on`),
  KEY `idx_sys_event_store_integration`    (`integration_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='领域事件存储表';

CREATE TABLE IF NOT EXISTS `sys_event_dispatch_log` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT                COMMENT '自增主键 (技术日志表,非业务表)',
  `event_id`        VARCHAR(64)  NOT NULL                               COMMENT '关联事件ID',
  `channel`         VARCHAR(100) NOT NULL                               COMMENT '分发通道: SPRING/REDIS/ROCKETMQ',
  `status`          VARCHAR(20)  NOT NULL                               COMMENT '分发状态: PENDING/SUCCESS/FAILED',
  `error_msg`       TEXT         DEFAULT NULL                           COMMENT '失败错误信息',
  `retry_count`     INT          DEFAULT 0                              COMMENT '已重试次数 (上限 10)',
  `next_retry_at`   TIMESTAMP    NULL DEFAULT NULL                      COMMENT '下次重试时间',
  `created_at`      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP              COMMENT '记录创建时间',
  `updated_at`      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_event_channel` (`event_id`, `channel`),
  KEY `idx_sys_event_dispatch_log_status`       (`status`),
  KEY `idx_sys_event_dispatch_log_next_retry`   (`next_retry_at`),
  KEY `idx_sys_event_dispatch_log_retry_count`  (`retry_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事件分发日志表';
