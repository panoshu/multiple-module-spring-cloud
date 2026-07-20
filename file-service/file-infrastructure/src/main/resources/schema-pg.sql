-- 文件元数据表
CREATE TABLE IF NOT EXISTS t_file_metadata (
    id                  VARCHAR(64)   NOT NULL,
    original_name       VARCHAR(512)  NOT NULL,
    size                BIGINT        NOT NULL,
    content_type        VARCHAR(128),
    md5                 VARCHAR(64),

    target_id           VARCHAR(64)   NOT NULL,
    storage_type        VARCHAR(20)   NOT NULL,
    storage_key         VARCHAR(1024) NOT NULL,

    usage               VARCHAR(20)   NOT NULL,
    biz_type            VARCHAR(64),
    source_app          VARCHAR(64),
    business_batch_id   VARCHAR(64),

    status              VARCHAR(20)   NOT NULL,
    uploaded_by         VARCHAR(64),
    uploaded_at         TIMESTAMP,
    expires_at          TIMESTAMP,

    created_by          VARCHAR(64)   NOT NULL,
    updated_by          VARCHAR(64),
    create_time         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    deleted             BOOLEAN       DEFAULT FALSE,
    version             INT           DEFAULT 0,

    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_file_metadata_batch_id ON t_file_metadata(business_batch_id);
CREATE INDEX IF NOT EXISTS idx_file_metadata_usage_biz_type ON t_file_metadata(usage, biz_type);
CREATE INDEX IF NOT EXISTS idx_file_metadata_status_expires ON t_file_metadata(status, expires_at) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_file_metadata_target_id ON t_file_metadata(target_id);

COMMENT ON TABLE t_file_metadata IS '文件元数据表';
COMMENT ON COLUMN t_file_metadata.id IS '文件ID（FileId）';
COMMENT ON COLUMN t_file_metadata.original_name IS '原始文件名';
COMMENT ON COLUMN t_file_metadata.size IS '文件大小（字节）';
COMMENT ON COLUMN t_file_metadata.content_type IS 'MIME 类型';
COMMENT ON COLUMN t_file_metadata.md5 IS '内容 MD5 指纹';
COMMENT ON COLUMN t_file_metadata.target_id IS '存储目标 ID';
COMMENT ON COLUMN t_file_metadata.storage_type IS '存储类型: LOCAL/OSS/NAS';
COMMENT ON COLUMN t_file_metadata.storage_key IS '后端内部 key/path';
COMMENT ON COLUMN t_file_metadata.usage IS '文件用途: SOURCE/PARSED/EXPORT/ARCHIVE';
COMMENT ON COLUMN t_file_metadata.biz_type IS '业务类型';
COMMENT ON COLUMN t_file_metadata.source_app IS '来源系统标识';
COMMENT ON COLUMN t_file_metadata.business_batch_id IS '业务批次号';
COMMENT ON COLUMN t_file_metadata.status IS '文件状态: PENDING_UPLOAD/UPLOADED/DELETED';
COMMENT ON COLUMN t_file_metadata.uploaded_by IS '上传人';
COMMENT ON COLUMN t_file_metadata.uploaded_at IS '上传时间';
COMMENT ON COLUMN t_file_metadata.expires_at IS '过期时间（NULL=永久）';
COMMENT ON COLUMN t_file_metadata.deleted IS '逻辑删除标志';
COMMENT ON COLUMN t_file_metadata.version IS '乐观锁版本号';

-- Token 访问机制扩展（Task 13）
ALTER TABLE t_file_metadata ADD COLUMN IF NOT EXISTS access_scope JSONB;
ALTER TABLE t_file_metadata ADD COLUMN IF NOT EXISTS digest VARCHAR(128);
ALTER TABLE t_file_metadata ADD COLUMN IF NOT EXISTS digest_algorithm VARCHAR(20) DEFAULT 'SM3';
ALTER TABLE t_file_metadata ALTER COLUMN original_name DROP NOT NULL;
ALTER TABLE t_file_metadata ALTER COLUMN size DROP NOT NULL;
ALTER TABLE t_file_metadata ALTER COLUMN storage_key DROP NOT NULL;

COMMENT ON COLUMN t_file_metadata.access_scope IS '访问范围 JSON: {"customerNo":"C001","productNo":"P001"}';
COMMENT ON COLUMN t_file_metadata.digest IS '内容摘要（SM3）';
COMMENT ON COLUMN t_file_metadata.digest_algorithm IS '摘要算法: SM3';

-- ParseTask 表字段迁移（source_file_ref → source_file_id）
-- 注意：此 DDL 仅用于新建库，旧库迁移需单独执行
-- ALTER TABLE t_file_parse_task RENAME COLUMN source_file_ref TO source_file_id;
-- ALTER TABLE t_file_parse_task ALTER COLUMN source_file_id TYPE VARCHAR(64);
-- CREATE INDEX IF NOT EXISTS idx_parse_task_source_file_id ON t_file_parse_task(source_file_id);

-- 文件访问流水表（Token 申请 APPLY / 实际访问 ACCESS 双记录，用于审计）
CREATE TABLE IF NOT EXISTS t_file_access_log (
    id              VARCHAR(64)   NOT NULL,
    file_id         VARCHAR(64)   NOT NULL,
    action          VARCHAR(20)   NOT NULL,
    usage           VARCHAR(20)   NOT NULL,
    customer_no     VARCHAR(64)   NOT NULL,
    product_no      VARCHAR(64)   NOT NULL,
    operator        VARCHAR(64)   NOT NULL,
    source_app      VARCHAR(64),
    source_ip       VARCHAR(64),
    token_hash      VARCHAR(128)  NOT NULL,
    result          VARCHAR(20)   NOT NULL,
    fail_reason     VARCHAR(512),
    occur_at        TIMESTAMP     NOT NULL,
    created_by      VARCHAR(64)   NOT NULL,
    updated_by      VARCHAR(64),
    create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN       DEFAULT FALSE,
    version         INT           DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_access_log_file_id ON t_file_access_log(file_id);
CREATE INDEX IF NOT EXISTS idx_access_log_token_hash ON t_file_access_log(token_hash);
CREATE INDEX IF NOT EXISTS idx_access_log_action_time ON t_file_access_log(action, occur_at);
CREATE INDEX IF NOT EXISTS idx_access_log_customer_product ON t_file_access_log(customer_no, product_no, occur_at);

COMMENT ON TABLE t_file_access_log IS '文件访问流水表（APPLY=申请token, ACCESS=实际访问）';
COMMENT ON COLUMN t_file_access_log.id IS '流水ID（FileAccessLogId）';
COMMENT ON COLUMN t_file_access_log.file_id IS '文件ID（FileId）';
COMMENT ON COLUMN t_file_access_log.action IS '动作类型: APPLY=申请token, ACCESS=实际访问';
COMMENT ON COLUMN t_file_access_log.usage IS '文件用途: SOURCE/PARSED/EXPORT/ARCHIVE';
COMMENT ON COLUMN t_file_access_log.customer_no IS '客户号';
COMMENT ON COLUMN t_file_access_log.product_no IS '产品号';
COMMENT ON COLUMN t_file_access_log.operator IS '操作人（UserNo）';
COMMENT ON COLUMN t_file_access_log.source_app IS '来源系统标识';
COMMENT ON COLUMN t_file_access_log.source_ip IS '来源 IP（仅 ACCESS 记录）';
COMMENT ON COLUMN t_file_access_log.token_hash IS 'token SHA-256 哈希，用于关联 APPLY 与 ACCESS 记录';
COMMENT ON COLUMN t_file_access_log.result IS '访问结果: SUCCESS/FAIL/EXPIRED/REJECTED';
COMMENT ON COLUMN t_file_access_log.fail_reason IS '失败原因（result != SUCCESS 时填充）';
COMMENT ON COLUMN t_file_access_log.occur_at IS '发生时间';
COMMENT ON COLUMN t_file_access_log.deleted IS '逻辑删除标志';
COMMENT ON COLUMN t_file_access_log.version IS '乐观锁版本号';
