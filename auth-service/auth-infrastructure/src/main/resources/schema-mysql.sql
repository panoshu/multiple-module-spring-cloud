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

-- ========== Session 基表 ==========
-- t_auth_session 基表骨架，由 Session 聚合根字段推导；二次授权字段由下方 ALTER 增量添加

CREATE TABLE IF NOT EXISTS t_auth_session (
    id                          VARCHAR(32)  NOT NULL                  COMMENT '会话ID',
    primary_account_id          VARCHAR(32)  NOT NULL                  COMMENT '主账号ID',
    channel                     VARCHAR(32)  NOT NULL                  COMMENT '渠道',
    effective_identity_id       VARCHAR(32)                            COMMENT '有效身份-经办ID',
    effective_identity_acting   VARCHAR(32)                            COMMENT '有效身份-柜员ID',
    effective_via_secondary     TINYINT(1)   NOT NULL DEFAULT 0        COMMENT '是否经二次授权',
    selected_plan_id            VARCHAR(32)                            COMMENT '已选计划ID',
    expires_at                  DATETIME     NOT NULL                  COMMENT '会话过期时间',
    status                      VARCHAR(16)  NOT NULL                  COMMENT '会话状态',
    created_by                  VARCHAR(64)  NOT NULL,
    create_time                 DATETIME     NOT NULL,
    updated_by                  VARCHAR(64)  NOT NULL,
    update_time                 DATETIME     NOT NULL,
    deleted                     TINYINT(1)   NOT NULL DEFAULT 0,
    version                     INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_auth_session_primary_account (primary_account_id, deleted),
    KEY idx_auth_session_status (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道会话表';

-- ========== Session 表增量字段 ==========
-- 基表 t_auth_session 已由上方 CREATE TABLE 创建；此处仅追加二次授权相关字段

ALTER TABLE t_auth_session
ADD COLUMN secondary_auth_session_id VARCHAR(32) COMMENT '二次授权会话ID';

CREATE INDEX idx_auth_session_secondary_auth
    ON t_auth_session (secondary_auth_session_id, deleted);
