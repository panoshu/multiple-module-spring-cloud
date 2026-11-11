-- =============================================================================
-- business-core-kernel - MySQL Schema
-- =============================================================================
-- 三张表对应 kernel 的三个聚合根：
--   t_business_application  ←  BusinessApplication
--   t_business_batch        ←  BusinessBatch
--   t_business_form         ←  BusinessForm
--
-- 设计要点：
--   1. BusinessContext / OperatorInfo 拍平为独立列，便于 SQL 查询和索引
--   2. businessExtension 使用 JSON 存储多态值对象
--   3. 主键 id 由应用层通过 shared-id-starter 生成（ULID），禁用自增
--   4. 通用字段 created_by/create_time/updated_by/update_time/deleted/version 齐备
--   5. createTime/updateTime 由应用层管理，不使用 ORM 自动填充
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 业务申请单表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_business_application
(
  id
  VARCHAR
(
  64
) NOT NULL,
  batch_id VARCHAR
(
  64
),
  form_id VARCHAR
(
  64
),
  definition_version VARCHAR
(
  64
),

  -- BusinessContext 拍平字段
  business_type VARCHAR
(
  64
),
  customer_no VARCHAR
(
  64
),
  customer_name VARCHAR
(
  255
),
  product_no VARCHAR
(
  64
),
  product_name VARCHAR
(
  255
),
  plan_no VARCHAR
(
  64
),
  plan_name VARCHAR
(
  255
),
  operation_model VARCHAR
(
  64
),
  account_manager VARCHAR
(
  64
),

  -- OperatorInfo 拍平字段
  channel VARCHAR
(
  64
),
  operator_id VARCHAR
(
  64
),
  operator_name VARCHAR
(
  255
),
  is_proxy TINYINT
(
  1
) DEFAULT 0,

  -- 文件与统计字段
  parsed_json_file_id VARCHAR
(
  64
),
  expected_detail_count INT DEFAULT 0,

  -- 业务扩展字段（多态 JSON）
  business_extension JSON,

  -- 状态机字段
  status VARCHAR
(
  32
),
  current_step VARCHAR
(
  64
),

  apply_time DATETIME,
  complete_time DATETIME,

  -- 通用字段
  created_by VARCHAR
(
  64
) NOT NULL,
  updated_by VARCHAR
(
  64
),
  create_time DATETIME,
  update_time DATETIME,
  deleted TINYINT
(
  1
) DEFAULT 0,
  version INT DEFAULT 0,
  PRIMARY KEY
(
  id
)
  );

CREATE INDEX idx_business_application_batch_id ON t_business_application (batch_id);
CREATE INDEX idx_business_application_form_id ON t_business_application (form_id);
CREATE INDEX idx_business_application_parsed_file_id ON t_business_application (parsed_json_file_id);
CREATE INDEX idx_business_application_status ON t_business_application (status);
CREATE INDEX idx_business_application_current_step ON t_business_application (current_step);
CREATE INDEX idx_business_application_customer_no ON t_business_application (customer_no);
CREATE INDEX idx_business_application_create_time ON t_business_application (create_time);

-- -----------------------------------------------------------------------------
-- 2. 业务批次表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_business_batch
(
  id
  VARCHAR
(
  64
) NOT NULL,

  -- BusinessContext 拍平字段
  business_type VARCHAR
(
  64
),
  customer_no VARCHAR
(
  64
),
  customer_name VARCHAR
(
  255
),
  product_no VARCHAR
(
  64
),
  product_name VARCHAR
(
  255
),
  plan_no VARCHAR
(
  64
),
  plan_name VARCHAR
(
  255
),
  operation_model VARCHAR
(
  64
),
  account_manager VARCHAR
(
  64
),

  -- OperatorInfo 拍平字段
  channel VARCHAR
(
  64
),
  operator_id VARCHAR
(
  64
),
  operator_name VARCHAR
(
  255
),
  is_proxy TINYINT
(
  1
) DEFAULT 0,

  -- 批次状态字段
  status VARCHAR
(
  32
),
  total_application_count INT DEFAULT 0,
  success_count INT DEFAULT 0,
  failed_count INT DEFAULT 0,

  -- 通用字段
  created_by VARCHAR
(
  64
) NOT NULL,
  updated_by VARCHAR
(
  64
),
  create_time DATETIME,
  update_time DATETIME,
  deleted TINYINT
(
  1
) DEFAULT 0,
  version INT DEFAULT 0,
  PRIMARY KEY
(
  id
)
  );

CREATE INDEX idx_business_batch_status ON t_business_batch (status);
CREATE INDEX idx_business_batch_customer_no ON t_business_batch (customer_no);
CREATE INDEX idx_business_batch_business_type ON t_business_batch (business_type);
CREATE INDEX idx_business_batch_create_time ON t_business_batch (create_time);

-- -----------------------------------------------------------------------------
-- 3. 业务表单表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_business_form
(
  id
  VARCHAR
(
  64
) NOT NULL,
  batch_id VARCHAR
(
  64
),

  -- BusinessContext 拍平字段
  business_type VARCHAR
(
  64
),
  customer_no VARCHAR
(
  64
),
  customer_name VARCHAR
(
  255
),
  product_no VARCHAR
(
  64
),
  product_name VARCHAR
(
  255
),
  plan_no VARCHAR
(
  64
),
  plan_name VARCHAR
(
  255
),
  operation_model VARCHAR
(
  64
),
  account_manager VARCHAR
(
  64
),

  -- OperatorInfo 拍平字段
  channel VARCHAR
(
  64
),
  operator_id VARCHAR
(
  64
),
  operator_name VARCHAR
(
  255
),
  is_proxy TINYINT
(
  1
) DEFAULT 0,

  -- 表单文件字段
  form_file_id VARCHAR
(
  64
),
  form_file_name VARCHAR
(
  512
),
  form_file_size BIGINT,

  -- 表单状态字段
  form_status VARCHAR
(
  32
),

  -- 通用字段
  created_by VARCHAR
(
  64
) NOT NULL,
  updated_by VARCHAR
(
  64
),
  create_time DATETIME,
  update_time DATETIME,
  deleted TINYINT
(
  1
) DEFAULT 0,
  version INT DEFAULT 0,
  PRIMARY KEY
(
  id
)
  );

CREATE INDEX idx_business_form_batch_id ON t_business_form (batch_id);
CREATE INDEX idx_business_form_status ON t_business_form (form_status);
CREATE INDEX idx_business_form_customer_no ON t_business_form (customer_no);
CREATE INDEX idx_business_form_create_time ON t_business_form (create_time);

-- ===== 步骤执行记录（spec §7.2 / §16.3） =====
CREATE TABLE IF NOT EXISTS t_step_execution
(
  id                  BIGINT       NOT NULL,
  application_id      VARCHAR(64)  NOT NULL,
  definition_version  VARCHAR(64)  NOT NULL,
  step_code           VARCHAR(64)  NOT NULL,
  attempt             INT          NOT NULL,
  request_no          VARCHAR(64)  NOT NULL,
  status              VARCHAR(32)  NOT NULL,
  started_at          DATETIME,
  ended_at            DATETIME,
  error_code          VARCHAR(64),
  error_summary       TEXT,
  checkpoint_ref      VARCHAR(255),
  created_by          VARCHAR(64)  NOT NULL,
  updated_by          VARCHAR(64),
  create_time         DATETIME     NOT NULL,
  update_time         DATETIME     NOT NULL,
  deleted             TINYINT(1)   NOT NULL DEFAULT 0,
  version             INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='步骤执行记录';

CREATE UNIQUE INDEX uk_step_execution_app_step_attempt
  ON t_step_execution (application_id, step_code, attempt);
CREATE UNIQUE INDEX uk_step_execution_request_no
  ON t_step_execution (request_no);
CREATE INDEX idx_step_execution_app_status
  ON t_step_execution (application_id, status);

-- ===== 外部协作记录（spec §7.3 / §16.1） =====
CREATE TABLE IF NOT EXISTS t_business_external_interaction
(
  id                   BIGINT       NOT NULL,
  application_id       VARCHAR(64)  NOT NULL,
  step_execution_id    BIGINT       NOT NULL,
  interaction_type     VARCHAR(32)  NOT NULL,
  request_no           VARCHAR(64)  NOT NULL,
  external_task_id     VARCHAR(64),
  status               VARCHAR(32)  NOT NULL,
  result_ref           VARCHAR(255),
  retry_count          INT          NOT NULL DEFAULT 0,
  next_retry_time      DATETIME,
  last_error           TEXT,
  completed_at         DATETIME,
  created_by           VARCHAR(64)  NOT NULL,
  updated_by           VARCHAR(64),
  create_time          DATETIME     NOT NULL,
  update_time          DATETIME     NOT NULL,
  deleted              TINYINT(1)   NOT NULL DEFAULT 0,
  version              INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部协作记录';

CREATE UNIQUE INDEX uk_external_interaction_request_no
  ON t_business_external_interaction (request_no);
CREATE UNIQUE INDEX uk_external_interaction_type_task
  ON t_business_external_interaction (interaction_type, external_task_id);
CREATE INDEX idx_external_interaction_status_retry
  ON t_business_external_interaction (status, next_retry_time);
CREATE INDEX idx_external_interaction_application_id
  ON t_business_external_interaction (application_id);
