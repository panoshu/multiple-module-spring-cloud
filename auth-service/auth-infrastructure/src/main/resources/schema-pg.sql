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

-- ========== Session 表增量字段 ==========
-- Assumes t_auth_session base table exists; created by the Session infrastructure task

ALTER TABLE t_auth_session
ADD COLUMN IF NOT EXISTS secondary_auth_session_id VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_auth_session_secondary_auth
    ON t_auth_session (secondary_auth_session_id)
    WHERE deleted = FALSE AND secondary_auth_session_id IS NOT NULL;
