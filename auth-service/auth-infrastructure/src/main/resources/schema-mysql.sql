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
    PRIMARY KEY (id),
    KEY idx_teller_account (teller_account_id, status, deleted),
    KEY idx_approver_pending (approver_account_id, status, deleted),
    KEY idx_approver_authorized (approver_account_id, status, deleted),
    KEY idx_expires (expires_at, status, deleted),
    KEY idx_plan (plan_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='二次授权会话表';

-- ========== Session 表增量字段 ==========
-- Assumes t_auth_session base table exists; created by the Session infrastructure task

ALTER TABLE t_auth_session
ADD COLUMN secondary_auth_session_id VARCHAR(32) COMMENT '二次授权会话ID';

CREATE INDEX idx_auth_session_secondary_auth
    ON t_auth_session (secondary_auth_session_id, deleted);
