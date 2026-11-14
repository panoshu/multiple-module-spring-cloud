-- =============================================================================
-- business-core-kernel - H2 测试 Schema（PostgreSQL 兼容模式）
-- =============================================================================
-- kernel 通用表的 H2 兼容定义，供依赖 kernel 的业务服务在 E2E 测试中组合加载：
--   schema-locations: classpath:schema-kernel-h2.sql,classpath:<业务服务自有>-h2.sql
-- 权威定义（生产）为同模块的 schema-pg.sql / schema-mysql.sql；本文件仅用于测试，
-- 相对 PG 定义做如下 H2 兼容调整：
--   1. JSONB → TEXT（H2 不支持原生 JSONB，按字符串存储）
--   2. 移除 COMMENT ON（H2 在 PostgreSQL 模式下意义不大）
--   3. deleted BOOLEAN → INT DEFAULT 0，时间戳保留 H2 默认值
-- 新增或修改 kernel 表时，请同步 schema-pg.sql / schema-mysql.sql / 本文件三处。
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
  is_proxy BOOLEAN DEFAULT FALSE,

  -- 统计字段
  expected_detail_count INT DEFAULT 0,

  -- 状态机字段
  status VARCHAR
(
  32
),
  current_step VARCHAR
(
  64
),

  apply_time TIMESTAMP,
  complete_time TIMESTAMP,

  -- 通用字段
  created_by VARCHAR
(
  64
) NOT NULL,
  updated_by VARCHAR
(
  64
),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted INT DEFAULT 0,
  version INT DEFAULT 0,
  PRIMARY KEY
(
  id
)
  );

CREATE INDEX IF NOT EXISTS idx_business_application_batch_id ON t_business_application(batch_id);
CREATE INDEX IF NOT EXISTS idx_business_application_form_id ON t_business_application(form_id);
CREATE INDEX IF NOT EXISTS idx_business_application_status ON t_business_application(status);
CREATE INDEX IF NOT EXISTS idx_business_application_current_step ON t_business_application(current_step);
CREATE INDEX IF NOT EXISTS idx_business_application_customer_no ON t_business_application(customer_no);
CREATE INDEX IF NOT EXISTS idx_business_application_create_time ON t_business_application(create_time);

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
  is_proxy BOOLEAN DEFAULT FALSE,

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
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted INT DEFAULT 0,
  version INT DEFAULT 0,
  PRIMARY KEY
(
  id
)
  );

CREATE INDEX IF NOT EXISTS idx_business_batch_status ON t_business_batch(status);
CREATE INDEX IF NOT EXISTS idx_business_batch_customer_no ON t_business_batch(customer_no);
CREATE INDEX IF NOT EXISTS idx_business_batch_business_type ON t_business_batch(business_type);
CREATE INDEX IF NOT EXISTS idx_business_batch_create_time ON t_business_batch(create_time);

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
  is_proxy BOOLEAN DEFAULT FALSE,

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
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted INT DEFAULT 0,
  version INT DEFAULT 0,
  PRIMARY KEY
(
  id
)
  );

CREATE INDEX IF NOT EXISTS idx_business_form_batch_id ON t_business_form(batch_id);
CREATE INDEX IF NOT EXISTS idx_business_form_status ON t_business_form(form_status);
CREATE INDEX IF NOT EXISTS idx_business_form_customer_no ON t_business_form(customer_no);
CREATE INDEX IF NOT EXISTS idx_business_form_create_time ON t_business_form(create_time);

-- ===== 步骤执行记录（spec §7.2 / §16.3） =====
CREATE TABLE IF NOT EXISTS t_business_step_execution
(
  id                  BIGINT       NOT NULL,
  application_id      VARCHAR(64)  NOT NULL,
  definition_version  VARCHAR(64)  NOT NULL,
  step_code           VARCHAR(64)  NOT NULL,
  attempt             INT          NOT NULL,
  request_no          VARCHAR(128)  NOT NULL,
  status              VARCHAR(32)  NOT NULL,
  started_at          TIMESTAMP,
  ended_at            TIMESTAMP,
  error_code          VARCHAR(64),
  error_summary       TEXT,
  checkpoint_ref      VARCHAR(255),
  created_by          VARCHAR(64)  NOT NULL,
  updated_by          VARCHAR(64),
  create_time         TIMESTAMP    NOT NULL,
  update_time         TIMESTAMP    NOT NULL,
  deleted             INT          NOT NULL DEFAULT 0,
  version             INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_business_step_execution_app_step_attempt ON t_business_step_execution(application_id, step_code, attempt);
CREATE UNIQUE INDEX IF NOT EXISTS uk_business_step_execution_request_no ON t_business_step_execution(request_no);
CREATE INDEX IF NOT EXISTS idx_business_step_execution_app_status ON t_business_step_execution(application_id, status);

-- ===== 外部协作记录（spec §7.3 / §16.1） =====
CREATE TABLE IF NOT EXISTS t_business_external_interaction
(
  id                   BIGINT       NOT NULL,
  application_id       VARCHAR(64)  NOT NULL,
  step_execution_id    BIGINT       NOT NULL,
  interaction_type     VARCHAR(32)  NOT NULL,
  request_no           VARCHAR(128)  NOT NULL,
  external_task_id     VARCHAR(64),
  status               VARCHAR(32)  NOT NULL,
  result_ref           VARCHAR(255),
  retry_count          INT          NOT NULL DEFAULT 0,
  next_retry_time      TIMESTAMP,
  last_error           TEXT,
  completed_at         TIMESTAMP,
  created_by           VARCHAR(64)  NOT NULL,
  updated_by           VARCHAR(64),
  create_time          TIMESTAMP    NOT NULL,
  update_time          TIMESTAMP    NOT NULL,
  deleted              INT          NOT NULL DEFAULT 0,
  version              INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_external_interaction_request_no ON t_business_external_interaction(request_no);
CREATE UNIQUE INDEX IF NOT EXISTS uk_external_interaction_type_task ON t_business_external_interaction(interaction_type, external_task_id);
CREATE INDEX IF NOT EXISTS idx_external_interaction_status_retry ON t_business_external_interaction(status, next_retry_time);
CREATE INDEX IF NOT EXISTS idx_external_interaction_application_id ON t_business_external_interaction(application_id);

-- ===== 号段 ID 生成表（shared-id-core / §16.3） =====
CREATE TABLE IF NOT EXISTS t_id_generator
(
  seq_key     VARCHAR(64) NOT NULL PRIMARY KEY,
  max_id      BIGINT      NOT NULL DEFAULT 0,
  step        INT         NOT NULL DEFAULT 100,
  create_time TIMESTAMP,
  update_time TIMESTAMP
);

-- ===== 事件消费回执（spec §7.4 / §16.1） =====
CREATE TABLE IF NOT EXISTS t_business_event_receipt
(
  id             BIGINT       NOT NULL,
  event_id       VARCHAR(128) NOT NULL,
  consumer       VARCHAR(64)  NOT NULL,
  status         VARCHAR(32)  NOT NULL,
  error_summary  TEXT,
  created_by     VARCHAR(64)  NOT NULL,
  updated_by     VARCHAR(64),
  create_time    TIMESTAMP    NOT NULL,
  update_time    TIMESTAMP    NOT NULL,
  deleted        INT          NOT NULL DEFAULT 0,
  version        INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_event_receipt_event_consumer ON t_business_event_receipt(event_id, consumer);
