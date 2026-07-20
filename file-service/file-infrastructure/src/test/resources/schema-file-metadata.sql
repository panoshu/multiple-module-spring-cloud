-- H2 兼容 DDL，供 FileMetadataTokenRepositoryTest 初始化内存数据库使用
-- 与 schema-pg.sql 的 t_file_metadata 表结构一致（含 Task 13 Token 扩展字段），但有 H2 适配：
--   1. 去除 PostgreSQL 专属语法（COMMENT/部分索引/JSONB → VARCHAR）
--   2. `deleted` 列从 BOOLEAN 改为 INT —— MyBatis-Flex 的 @Column(isLogicDelete = true)
--      默认用 0/1 进行逻辑删除比较，PostgreSQL 可 BOOLEAN=INT 隐式转换，
--      但 H2 即便在 MODE=PostgreSQL 下也会抛 "BOOLEAN and INTEGER are not comparable"。
--   3. access_scope 用 VARCHAR 存储 JSON 字符串（H2 不支持 JSONB）
--   4. original_name/size/storage_key 允许 NULL（Token 路径 PENDING_UPLOAD 时为空）

DROP TABLE IF EXISTS t_file_metadata;

CREATE TABLE t_file_metadata (
    id                  VARCHAR(64)   NOT NULL,
    original_name       VARCHAR(512),
    size                BIGINT,
    content_type        VARCHAR(128),
    md5                 VARCHAR(64),

    target_id           VARCHAR(64)   NOT NULL,
    storage_type        VARCHAR(20)   NOT NULL,
    storage_key         VARCHAR(1024),

    usage               VARCHAR(20)   NOT NULL,
    biz_type            VARCHAR(64),
    source_app          VARCHAR(64),
    business_batch_id   VARCHAR(64),

    status              VARCHAR(20)   NOT NULL,
    uploaded_by         VARCHAR(64),
    uploaded_at         TIMESTAMP,
    expires_at          TIMESTAMP,

    -- Token 访问机制扩展字段（Task 13）
    access_scope        VARCHAR(2048),
    digest              VARCHAR(128),
    digest_algorithm    VARCHAR(20)   DEFAULT 'SM3',

    created_by          VARCHAR(64)   NOT NULL,
    updated_by          VARCHAR(64),
    create_time         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    deleted             INT           DEFAULT 0,
    version             INT           DEFAULT 0,

    PRIMARY KEY (id)
);

CREATE INDEX idx_file_metadata_batch_id ON t_file_metadata(business_batch_id);
CREATE INDEX idx_file_metadata_usage_biz_type ON t_file_metadata(usage, biz_type);
CREATE INDEX idx_file_metadata_status_expires ON t_file_metadata(status, expires_at);
CREATE INDEX idx_file_metadata_target_id ON t_file_metadata(target_id);
