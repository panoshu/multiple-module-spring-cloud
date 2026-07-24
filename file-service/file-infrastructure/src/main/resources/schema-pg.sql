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

-- =============================================================================
-- 文件解析任务表
-- =============================================================================
CREATE TABLE IF NOT EXISTS t_file_parse_task (
    id                  VARCHAR(64)   NOT NULL,
    biz_type            VARCHAR(64)   NOT NULL,
    template_code       VARCHAR(64),
    source_file_name    VARCHAR(512),
    source_file_id      VARCHAR(64),
    status              VARCHAR(32)   NOT NULL,
    error_policy        VARCHAR(32),
    split_keys          VARCHAR(255),
    total_rows          INT           DEFAULT 0,
    sub_task_count      INT           DEFAULT 0,
    valid_count         INT           DEFAULT 0,
    invalid_count       INT           DEFAULT 0,
    sub_task_summaries  JSONB,
    errors              JSONB,
    started_at          TIMESTAMP,
    finished_at         TIMESTAMP,

    created_by          VARCHAR(64)   NOT NULL,
    updated_by          VARCHAR(64),
    create_time         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    deleted             BOOLEAN       DEFAULT FALSE,
    version             INT           DEFAULT 0,

    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_file_parse_task_biz_status    ON t_file_parse_task(biz_type, status);
CREATE INDEX IF NOT EXISTS idx_file_parse_task_source_file   ON t_file_parse_task(source_file_id);
CREATE INDEX IF NOT EXISTS idx_file_parse_task_create_time   ON t_file_parse_task(create_time);

COMMENT ON TABLE  t_file_parse_task IS '文件解析任务表';
COMMENT ON COLUMN t_file_parse_task.id IS '任务ID（FileTaskId）';
COMMENT ON COLUMN t_file_parse_task.biz_type IS '业务类型';
COMMENT ON COLUMN t_file_parse_task.template_code IS '模板编码';
COMMENT ON COLUMN t_file_parse_task.source_file_name IS '源文件名';
COMMENT ON COLUMN t_file_parse_task.source_file_id IS '源文件ID（FileId）';
COMMENT ON COLUMN t_file_parse_task.status IS '任务状态: PENDING/PROCESSING/SUCCESS/FAILED/CANCELLED';
COMMENT ON COLUMN t_file_parse_task.error_policy IS '错误处理策略: SKIP/ABORT/QUARANTINE';
COMMENT ON COLUMN t_file_parse_task.split_keys IS '拆分键列表（逗号分隔）';
COMMENT ON COLUMN t_file_parse_task.total_rows IS '总行数';
COMMENT ON COLUMN t_file_parse_task.sub_task_count IS '子任务数';
COMMENT ON COLUMN t_file_parse_task.valid_count IS '有效行数';
COMMENT ON COLUMN t_file_parse_task.invalid_count IS '无效行数';
COMMENT ON COLUMN t_file_parse_task.sub_task_summaries IS '子任务摘要（JSONB 数组）';
COMMENT ON COLUMN t_file_parse_task.errors IS '错误列表（JSONB 数组）';
COMMENT ON COLUMN t_file_parse_task.started_at IS '开始时间';
COMMENT ON COLUMN t_file_parse_task.finished_at IS '完成时间';

-- =============================================================================
-- 文件子任务数据表（支持按拆分键分页查询）
-- =============================================================================
CREATE TABLE IF NOT EXISTS t_file_sub_task_data (
    id                  VARCHAR(64)   NOT NULL,
    file_task_id        VARCHAR(64)   NOT NULL,
    biz_type            VARCHAR(64)   NOT NULL,
    split_key_value     VARCHAR(255),
    context             JSONB,
    properties          JSONB,
    tables              JSONB,
    row_count           INT           DEFAULT 0,
    status              VARCHAR(32)   NOT NULL,
    validation_errors   JSONB,
    expires_at          TIMESTAMP,
    consumed_at         TIMESTAMP,

    created_by          VARCHAR(64)   NOT NULL,
    updated_by          VARCHAR(64),
    create_time         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    deleted             BOOLEAN       DEFAULT FALSE,
    version             INT           DEFAULT 0,

    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_file_sub_task_data_task_id       ON t_file_sub_task_data(file_task_id);
CREATE INDEX IF NOT EXISTS idx_file_sub_task_data_split_key     ON t_file_sub_task_data(file_task_id, split_key_value);
CREATE INDEX IF NOT EXISTS idx_file_sub_task_data_status        ON t_file_sub_task_data(status);
CREATE INDEX IF NOT EXISTS idx_file_sub_task_data_expires_at    ON t_file_sub_task_data(expires_at);

COMMENT ON TABLE  t_file_sub_task_data IS '文件子任务数据表（按拆分键存储）';
COMMENT ON COLUMN t_file_sub_task_data.id IS '子任务ID（SubTaskId）';
COMMENT ON COLUMN t_file_sub_task_data.file_task_id IS '关联解析任务ID（FileTaskId）';
COMMENT ON COLUMN t_file_sub_task_data.biz_type IS '业务类型';
COMMENT ON COLUMN t_file_sub_task_data.split_key_value IS '拆分键值';
COMMENT ON COLUMN t_file_sub_task_data.context IS '解析上下文（JSONB）';
COMMENT ON COLUMN t_file_sub_task_data.properties IS '数据属性（JSONB）';
COMMENT ON COLUMN t_file_sub_task_data.tables IS '表格数据（JSONB）';
COMMENT ON COLUMN t_file_sub_task_data.row_count IS '行数';
COMMENT ON COLUMN t_file_sub_task_data.status IS '子任务状态: PENDING/PROCESSING/SUCCESS/FAILED';
COMMENT ON COLUMN t_file_sub_task_data.validation_errors IS '校验错误（JSONB 数组）';
COMMENT ON COLUMN t_file_sub_task_data.expires_at IS '过期时间';
COMMENT ON COLUMN t_file_sub_task_data.consumed_at IS '消费时间';

-- =============================================================================
-- 文件模板配置表
-- =============================================================================
CREATE TABLE IF NOT EXISTS t_file_template_config (
    id                  VARCHAR(64)   NOT NULL,
    biz_type            VARCHAR(64)   NOT NULL,
    template_version    VARCHAR(64)   NOT NULL,
    error_policy        VARCHAR(32),
    canonical_model     JSONB,
    validation_rules    JSONB,
    derivation_rules    JSONB,
    split_config        JSONB,
    source_templates    JSONB,
    target_template_ref VARCHAR(64),
    target_mapping      JSONB,
    status              VARCHAR(32)   NOT NULL,
    effective_from      TIMESTAMP,
    effective_to        TIMESTAMP,

    created_by          VARCHAR(64)   NOT NULL,
    updated_by          VARCHAR(64),
    create_time         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    deleted             BOOLEAN       DEFAULT FALSE,
    version             INT           DEFAULT 0,

    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_file_template_config_biz_version ON t_file_template_config(biz_type, template_version);
CREATE INDEX IF NOT EXISTS idx_file_template_config_biz_status        ON t_file_template_config(biz_type, status);
CREATE INDEX IF NOT EXISTS idx_file_template_config_effective         ON t_file_template_config(biz_type, effective_from, effective_to);

COMMENT ON TABLE  t_file_template_config IS '文件模板配置表';
COMMENT ON COLUMN t_file_template_config.id IS '模板配置ID（TemplateConfigId）';
COMMENT ON COLUMN t_file_template_config.biz_type IS '业务类型';
COMMENT ON COLUMN t_file_template_config.template_version IS '模板版本';
COMMENT ON COLUMN t_file_template_config.error_policy IS '错误处理策略: SKIP/ABORT/QUARANTINE';
COMMENT ON COLUMN t_file_template_config.canonical_model IS '规范化模型定义（JSONB）';
COMMENT ON COLUMN t_file_template_config.validation_rules IS '校验规则（JSONB）';
COMMENT ON COLUMN t_file_template_config.derivation_rules IS '派生规则（JSONB）';
COMMENT ON COLUMN t_file_template_config.split_config IS '拆分配置（JSONB）';
COMMENT ON COLUMN t_file_template_config.source_templates IS '源模板定义（JSONB）';
COMMENT ON COLUMN t_file_template_config.target_template_ref IS '目标模板引用';
COMMENT ON COLUMN t_file_template_config.target_mapping IS '目标映射（JSONB）';
COMMENT ON COLUMN t_file_template_config.status IS '状态: DRAFT/ACTIVE/DEPRECATED';
COMMENT ON COLUMN t_file_template_config.effective_from IS '生效开始时间';
COMMENT ON COLUMN t_file_template_config.effective_to IS '生效结束时间';
