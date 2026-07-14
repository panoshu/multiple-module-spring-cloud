CREATE TABLE IF NOT EXISTS `file_record`
(
  `id`            VARCHAR(32)  NOT NULL COMMENT '主键ID (ULID)',
  `original_name` VARCHAR(255) DEFAULT NULL COMMENT '原始文件名',
  `extension`     VARCHAR(20)  DEFAULT NULL COMMENT '文件后缀 (如 pdf)',
  `size`          BIGINT       DEFAULT 0 COMMENT '文件大小 (字节)',
  `mime_type`     VARCHAR(128) DEFAULT NULL COMMENT 'MIME类型',
  `hash`          VARCHAR(64)  DEFAULT NULL COMMENT '文件哈希 (SHA-256)',

  `storage_type`  VARCHAR(20)  NOT NULL COMMENT '存储引擎 (LOCAL, S3, ALIYUN_OSS)',
  `bucket`        VARCHAR(64)  DEFAULT NULL COMMENT '存储桶/命名空间',
  `storage_key`   VARCHAR(512) NOT NULL COMMENT '存储物理路径/对象Key',

  `status`        VARCHAR(20)  NOT NULL COMMENT '状态 (TEMP, PERSISTENT, DELETED)',
  `biz_type`      VARCHAR(64)  NOT NULL COMMENT '业务类型',
  `owner_id`      VARCHAR(64)  DEFAULT NULL COMMENT '上传者/归属人ID',
  `acl`           VARCHAR(20)  DEFAULT 'PRIVATE' COMMENT '访问控制 (PRIVATE, PUBLIC_READ)',

  `metadata`      JSON         DEFAULT NULL COMMENT '扩展元数据 (JSON)',

  `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted`    TINYINT(1)   DEFAULT 0 COMMENT '逻辑删除 (0:正常, 1:删除)',
  `revision`      INT          DEFAULT 0 COMMENT '乐观锁版本号',

  PRIMARY KEY (`id`),
  KEY `idx_hash` (`hash`),
  KEY `idx_biz_status` (`biz_type`, `status`),
  KEY `idx_owner` (`owner_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='统一文件记录表';
