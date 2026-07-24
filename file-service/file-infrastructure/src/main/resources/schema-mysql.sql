-- 文件元数据表（MySQL 版本）
-- 与 schema-pg.sql 保持字段一致，差异：JSONB→JSON，部分索引改为普通索引，COMMENT 内联
CREATE TABLE IF NOT EXISTS `t_file_metadata` (
  `id` VARCHAR(64) NOT NULL COMMENT '文件ID（FileId）',
  `original_name` VARCHAR(512) DEFAULT NULL COMMENT '原始文件名（PENDING_UPLOAD 阶段允许 NULL）',
  `size` BIGINT DEFAULT NULL COMMENT '文件大小（字节，PENDING_UPLOAD 阶段允许 NULL）',
  `content_type` VARCHAR(128) DEFAULT NULL COMMENT 'MIME 类型',
  `md5` VARCHAR(64) DEFAULT NULL COMMENT '内容 MD5 指纹（历史字段，新数据使用 digest）',
  `digest` VARCHAR(128) DEFAULT NULL COMMENT '内容摘要（SM3）',
  `digest_algorithm` VARCHAR(20) DEFAULT 'SM3' COMMENT '摘要算法: SM3',

  `target_id` VARCHAR(64) NOT NULL COMMENT '存储目标 ID',
  `storage_type` VARCHAR(20) NOT NULL COMMENT '存储类型: LOCAL/OSS/NAS',
  `storage_key` VARCHAR(1024) DEFAULT NULL COMMENT '后端内部 key/path（PENDING_UPLOAD 阶段允许 NULL）',

  `usage` VARCHAR(20) NOT NULL COMMENT '文件用途: SOURCE/PARSED/EXPORT/ARCHIVE',
  `biz_type` VARCHAR(64) DEFAULT NULL COMMENT '业务类型',
  `source_app` VARCHAR(64) DEFAULT NULL COMMENT '来源系统标识',
  `business_batch_id` VARCHAR(64) DEFAULT NULL COMMENT '业务批次号',

  `access_scope` JSON DEFAULT NULL COMMENT '访问范围 JSON: {"customerNo":"C001","productNo":"P001"}',

  `status` VARCHAR(20) NOT NULL COMMENT '文件状态: PENDING_UPLOAD/UPLOADED/DELETED',
  `uploaded_by` VARCHAR(64) DEFAULT NULL COMMENT '上传人',
  `uploaded_at` TIMESTAMP NULL DEFAULT NULL COMMENT '上传时间',
  `expires_at` TIMESTAMP NULL DEFAULT NULL COMMENT '过期时间（NULL=永久）',

  `created_by` VARCHAR(64) NOT NULL COMMENT '创建人',
  `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BOOLEAN DEFAULT FALSE COMMENT '逻辑删除标志',
  `version` INT DEFAULT 0 COMMENT '乐观锁版本号',

  PRIMARY KEY (`id`),
  KEY `idx_file_metadata_batch_id` (`business_batch_id`),
  KEY `idx_file_metadata_usage_biz_type` (`usage`, `biz_type`),
  KEY `idx_file_metadata_status_expires` (`status`, `expires_at`),
  KEY `idx_file_metadata_target_id` (`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件元数据表';

-- 文件访问流水表（Token 申请 APPLY / 实际访问 ACCESS 双记录，用于审计）
CREATE TABLE IF NOT EXISTS `t_file_access_log` (
  `id` VARCHAR(64) NOT NULL COMMENT '流水ID（FileAccessLogId）',
  `file_id` VARCHAR(64) NOT NULL COMMENT '文件ID（FileId）',
  `action` VARCHAR(20) NOT NULL COMMENT '动作类型: APPLY=申请token, ACCESS=实际访问',
  `usage` VARCHAR(20) NOT NULL COMMENT '文件用途: SOURCE/PARSED/EXPORT/ARCHIVE',
  `customer_no` VARCHAR(64) NOT NULL COMMENT '客户号',
  `product_no` VARCHAR(64) NOT NULL COMMENT '产品号',
  `operator` VARCHAR(64) NOT NULL COMMENT '操作人（UserNo）',
  `source_app` VARCHAR(64) DEFAULT NULL COMMENT '来源系统标识',
  `source_ip` VARCHAR(64) DEFAULT NULL COMMENT '来源 IP（仅 ACCESS 记录）',
  `token_hash` VARCHAR(128) NOT NULL COMMENT 'token SHA-256 哈希，用于关联 APPLY 与 ACCESS 记录',
  `result` VARCHAR(20) NOT NULL COMMENT '访问结果: SUCCESS/FAIL/EXPIRED/REJECTED',
  `fail_reason` VARCHAR(512) DEFAULT NULL COMMENT '失败原因（result != SUCCESS 时填充）',
  `occur_at` TIMESTAMP NOT NULL COMMENT '发生时间',

  `created_by` VARCHAR(64) NOT NULL COMMENT '创建人',
  `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BOOLEAN DEFAULT FALSE COMMENT '逻辑删除标志',
  `version` INT DEFAULT 0 COMMENT '乐观锁版本号',

  PRIMARY KEY (`id`),
  KEY `idx_access_log_file_id` (`file_id`),
  KEY `idx_access_log_token_hash` (`token_hash`),
  KEY `idx_access_log_action_time` (`action`, `occur_at`),
  KEY `idx_access_log_customer_product` (`customer_no`, `product_no`, `occur_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件访问流水表（APPLY=申请token, ACCESS=实际访问）';

-- 文件解析任务表
CREATE TABLE IF NOT EXISTS `t_file_parse_task` (
  `id`                  VARCHAR(64)   NOT NULL                          COMMENT '任务ID（FileTaskId）',
  `biz_type`            VARCHAR(64)   NOT NULL                          COMMENT '业务类型',
  `template_code`       VARCHAR(64)   DEFAULT NULL                      COMMENT '模板编码',
  `source_file_name`    VARCHAR(512)  DEFAULT NULL                      COMMENT '源文件名',
  `source_file_id`      VARCHAR(64)   DEFAULT NULL                      COMMENT '源文件ID（FileId）',
  `status`              VARCHAR(32)   NOT NULL                          COMMENT '任务状态: PENDING/PROCESSING/SUCCESS/FAILED/CANCELLED',
  `error_policy`        VARCHAR(32)   DEFAULT NULL                      COMMENT '错误处理策略: SKIP/ABORT/QUARANTINE',
  `split_keys`          VARCHAR(255)  DEFAULT NULL                      COMMENT '拆分键列表（逗号分隔）',
  `total_rows`          INT           DEFAULT 0                         COMMENT '总行数',
  `sub_task_count`      INT           DEFAULT 0                         COMMENT '子任务数',
  `valid_count`         INT           DEFAULT 0                         COMMENT '有效行数',
  `invalid_count`       INT           DEFAULT 0                         COMMENT '无效行数',
  `sub_task_summaries`  JSON          DEFAULT NULL                      COMMENT '子任务摘要（JSON 数组）',
  `errors`              JSON          DEFAULT NULL                      COMMENT '错误列表（JSON 数组）',
  `started_at`          TIMESTAMP     NULL DEFAULT NULL                 COMMENT '开始时间',
  `finished_at`         TIMESTAMP     NULL DEFAULT NULL                 COMMENT '完成时间',

  `created_by`          VARCHAR(64)   NOT NULL                          COMMENT '创建人',
  `updated_by`          VARCHAR(64)   DEFAULT NULL                      COMMENT '更新人',
  `create_time`         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP         COMMENT '创建时间',
  `update_time`         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`             BOOLEAN       DEFAULT FALSE                     COMMENT '逻辑删除标志',
  `version`             INT           DEFAULT 0                         COMMENT '乐观锁版本号',

  PRIMARY KEY (`id`),
  KEY `idx_file_parse_task_biz_status`  (`biz_type`, `status`),
  KEY `idx_file_parse_task_source_file` (`source_file_id`),
  KEY `idx_file_parse_task_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件解析任务表';

-- 文件子任务数据表（支持按拆分键分页查询）
CREATE TABLE IF NOT EXISTS `t_file_sub_task_data` (
  `id`                  VARCHAR(64)   NOT NULL                          COMMENT '子任务ID（SubTaskId）',
  `file_task_id`        VARCHAR(64)   NOT NULL                          COMMENT '关联解析任务ID（FileTaskId）',
  `biz_type`            VARCHAR(64)   NOT NULL                          COMMENT '业务类型',
  `split_key_value`     VARCHAR(255)  DEFAULT NULL                      COMMENT '拆分键值',
  `context`             JSON          DEFAULT NULL                      COMMENT '解析上下文（JSON）',
  `properties`          JSON          DEFAULT NULL                      COMMENT '数据属性（JSON）',
  `tables`              JSON          DEFAULT NULL                      COMMENT '表格数据（JSON）',
  `row_count`           INT           DEFAULT 0                         COMMENT '行数',
  `status`              VARCHAR(32)   NOT NULL                          COMMENT '子任务状态: PENDING/PROCESSING/SUCCESS/FAILED',
  `validation_errors`   JSON          DEFAULT NULL                      COMMENT '校验错误（JSON 数组）',
  `expires_at`          TIMESTAMP     NULL DEFAULT NULL                 COMMENT '过期时间',
  `consumed_at`         TIMESTAMP     NULL DEFAULT NULL                 COMMENT '消费时间',

  `created_by`          VARCHAR(64)   NOT NULL                          COMMENT '创建人',
  `updated_by`          VARCHAR(64)   DEFAULT NULL                      COMMENT '更新人',
  `create_time`         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP         COMMENT '创建时间',
  `update_time`         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`             BOOLEAN       DEFAULT FALSE                     COMMENT '逻辑删除标志',
  `version`             INT           DEFAULT 0                         COMMENT '乐观锁版本号',

  PRIMARY KEY (`id`),
  KEY `idx_file_sub_task_data_task_id`     (`file_task_id`),
  KEY `idx_file_sub_task_data_split_key`   (`file_task_id`, `split_key_value`),
  KEY `idx_file_sub_task_data_status`      (`status`),
  KEY `idx_file_sub_task_data_expires_at`  (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件子任务数据表（按拆分键存储）';

-- 文件模板配置表
CREATE TABLE IF NOT EXISTS `t_file_template_config` (
  `id`                  VARCHAR(64)   NOT NULL                          COMMENT '模板配置ID（TemplateConfigId）',
  `biz_type`            VARCHAR(64)   NOT NULL                          COMMENT '业务类型',
  `template_version`    VARCHAR(64)   NOT NULL                          COMMENT '模板版本',
  `error_policy`        VARCHAR(32)   DEFAULT NULL                      COMMENT '错误处理策略: SKIP/ABORT/QUARANTINE',
  `canonical_model`     JSON          DEFAULT NULL                      COMMENT '规范化模型定义（JSON）',
  `validation_rules`    JSON          DEFAULT NULL                      COMMENT '校验规则（JSON）',
  `derivation_rules`    JSON          DEFAULT NULL                      COMMENT '派生规则（JSON）',
  `split_config`        JSON          DEFAULT NULL                      COMMENT '拆分配置（JSON）',
  `source_templates`    JSON          DEFAULT NULL                      COMMENT '源模板定义（JSON）',
  `target_template_ref` VARCHAR(64)   DEFAULT NULL                      COMMENT '目标模板引用',
  `target_mapping`      JSON          DEFAULT NULL                      COMMENT '目标映射（JSON）',
  `status`              VARCHAR(32)   NOT NULL                          COMMENT '状态: DRAFT/ACTIVE/DEPRECATED',
  `effective_from`      TIMESTAMP     NULL DEFAULT NULL                 COMMENT '生效开始时间',
  `effective_to`        TIMESTAMP     NULL DEFAULT NULL                 COMMENT '生效结束时间',

  `created_by`          VARCHAR(64)   NOT NULL                          COMMENT '创建人',
  `updated_by`          VARCHAR(64)   DEFAULT NULL                      COMMENT '更新人',
  `create_time`         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP         COMMENT '创建时间',
  `update_time`         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`             BOOLEAN       DEFAULT FALSE                     COMMENT '逻辑删除标志',
  `version`             INT           DEFAULT 0                         COMMENT '乐观锁版本号',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_template_config_biz_version` (`biz_type`, `template_version`),
  KEY `idx_file_template_config_biz_status`        (`biz_type`, `status`),
  KEY `idx_file_template_config_effective`         (`biz_type`, `effective_from`, `effective_to`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件模板配置表';
