CREATE TABLE IF NOT EXISTS file_record
(
  id            VARCHAR(32)  NOT NULL,
  original_name VARCHAR(255),
  extension     VARCHAR(20),
  size          BIGINT,
  mime_type     VARCHAR(128),
  hash          VARCHAR(64),

  storage_type  VARCHAR(20)  NOT NULL,
  bucket        VARCHAR(64),
  storage_key   VARCHAR(512) NOT NULL,

  status        VARCHAR(20)  NOT NULL,
  biz_type      VARCHAR(64)  NOT NULL,
  owner_id      VARCHAR(64),
  acl           VARCHAR(20) DEFAULT 'PRIVATE',

  metadata      JSONB,

  create_time   TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
  update_time   TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
  is_deleted    SMALLINT    DEFAULT 0,
  revision      INTEGER     DEFAULT 0,

  PRIMARY KEY (id)
);

-- 添加注释
COMMENT ON TABLE file_record IS '统一文件记录表';
COMMENT ON COLUMN file_record.id IS '主键ID (ULID)';
COMMENT ON COLUMN file_record.hash IS '文件哈希 (SHA-256)';
COMMENT ON COLUMN file_record.metadata IS '扩展元数据 (JSONB)';
COMMENT ON COLUMN file_record.is_deleted IS '逻辑删除';

-- 创建索引
CREATE INDEX idx_file_hash ON file_record (hash);
CREATE INDEX idx_file_biz_status ON file_record (biz_type, status);
CREATE INDEX idx_file_owner ON file_record (owner_id);
-- 如果需要定期清理临时文件，加上时间索引很有用
CREATE INDEX idx_file_create_time ON file_record (create_time);
