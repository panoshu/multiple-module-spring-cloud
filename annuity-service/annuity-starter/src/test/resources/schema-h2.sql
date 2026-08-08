-- =============================================================================
-- annuity-service - H2 测试 Schema (PostgreSQL 兼容模式)
-- =============================================================================
-- 与 schema-pg.sql 字段一致，仅做以下 H2 兼容性调整：
--   1. JSONB → TEXT（H2 不支持原生 JSONB，按字符串存储）
--   2. 移除 COMMENT ON（H2 在 PostgreSQL 模式下虽支持但意义不大）
--   3. 移除带 WHERE 子句的部分索引（H2 不支持 Partial Index）
--
-- 本文件包含两类表：
--   1. kernel 通用表（与 business-core-kernel/schema-pg.sql 保持一致）：
--      t_business_application  ←  BusinessApplication
--      t_business_batch        ←  BusinessBatch
--      t_business_form         ←  BusinessForm
--      annuity-service 已重构为复用 kernel 的 DO/Mapper/Converter，故三张聚合根
--      表沿用 kernel 表名，不再使用 t_annuity_* 前缀。
--
--   2. 年金专属表（annuity-service 自有聚合根）：
--      t_annuity_employee_batch  ←  AnnuityEmployeeBatch
--      t_annuity_employee_detail ←  AnnuityEmployeeDetail
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 业务申请单表（kernel 通用）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_business_application
(
  id                    VARCHAR(64)  NOT NULL,
  batch_id              VARCHAR(64),
  form_id               VARCHAR(64),

  -- BusinessContext 拍平字段
  business_type         VARCHAR(64),
  customer_no           VARCHAR(64),
  customer_name         VARCHAR(255),
  product_no            VARCHAR(64),
  product_name          VARCHAR(255),
  plan_no               VARCHAR(64),
  plan_name             VARCHAR(255),
  operation_model       VARCHAR(64),
  account_manager       VARCHAR(64),

  -- OperatorInfo 拍平字段
  channel               VARCHAR(64),
  operator_id           VARCHAR(64),
  operator_name         VARCHAR(255),
  is_proxy              BOOLEAN DEFAULT FALSE,

  -- 文件与统计字段
  parsed_json_file_id   VARCHAR(64),
  expected_detail_count INT DEFAULT 0,

  -- 业务扩展字段（H2: TEXT 替代 PostgreSQL JSONB）
  business_extension    TEXT,

  -- 状态机字段
  status                VARCHAR(32),
  current_step          VARCHAR(64),

  apply_time            TIMESTAMP,
  complete_time         TIMESTAMP,

  -- 通用字段
  created_by            VARCHAR(64)  NOT NULL,
  updated_by            VARCHAR(64),
  create_time           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted               INT DEFAULT 0,
  version               INT DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_business_application_batch_id       ON t_business_application(batch_id);
CREATE INDEX IF NOT EXISTS idx_business_application_form_id        ON t_business_application(form_id);
CREATE INDEX IF NOT EXISTS idx_business_application_parsed_file_id ON t_business_application(parsed_json_file_id);
CREATE INDEX IF NOT EXISTS idx_business_application_status         ON t_business_application(status);
CREATE INDEX IF NOT EXISTS idx_business_application_current_step   ON t_business_application(current_step);
CREATE INDEX IF NOT EXISTS idx_business_application_customer_no    ON t_business_application(customer_no);
CREATE INDEX IF NOT EXISTS idx_business_application_create_time    ON t_business_application(create_time);

-- -----------------------------------------------------------------------------
-- 2. 业务批次表（kernel 通用）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_business_batch
(
  id                       VARCHAR(64)  NOT NULL,

  -- BusinessContext 拍平字段
  business_type            VARCHAR(64),
  customer_no              VARCHAR(64),
  customer_name            VARCHAR(255),
  product_no               VARCHAR(64),
  product_name             VARCHAR(255),
  plan_no                  VARCHAR(64),
  plan_name                VARCHAR(255),
  operation_model          VARCHAR(64),
  account_manager          VARCHAR(64),

  -- OperatorInfo 拍平字段
  channel                  VARCHAR(64),
  operator_id              VARCHAR(64),
  operator_name            VARCHAR(255),
  is_proxy                 BOOLEAN DEFAULT FALSE,

  -- 批次状态字段
  status                   VARCHAR(32),
  total_application_count  INT DEFAULT 0,
  success_count            INT DEFAULT 0,
  failed_count             INT DEFAULT 0,

  -- 通用字段
  created_by               VARCHAR(64)  NOT NULL,
  updated_by               VARCHAR(64),
  create_time              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted                  INT DEFAULT 0,
  version                  INT DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_business_batch_status         ON t_business_batch(status);
CREATE INDEX IF NOT EXISTS idx_business_batch_customer_no    ON t_business_batch(customer_no);
CREATE INDEX IF NOT EXISTS idx_business_batch_business_type  ON t_business_batch(business_type);
CREATE INDEX IF NOT EXISTS idx_business_batch_create_time    ON t_business_batch(create_time);

-- -----------------------------------------------------------------------------
-- 3. 业务表单表（kernel 通用）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_business_form
(
  id              VARCHAR(64)  NOT NULL,
  batch_id        VARCHAR(64),

  -- BusinessContext 拍平字段
  business_type   VARCHAR(64),
  customer_no     VARCHAR(64),
  customer_name   VARCHAR(255),
  product_no      VARCHAR(64),
  product_name    VARCHAR(255),
  plan_no         VARCHAR(64),
  plan_name       VARCHAR(255),
  operation_model VARCHAR(64),
  account_manager VARCHAR(64),

  -- OperatorInfo 拍平字段
  channel         VARCHAR(64),
  operator_id     VARCHAR(64),
  operator_name   VARCHAR(255),
  is_proxy        BOOLEAN DEFAULT FALSE,

  -- 表单文件字段
  form_file_id    VARCHAR(64),
  form_file_name  VARCHAR(512),
  form_file_size  BIGINT,

  -- 表单状态字段
  form_status     VARCHAR(32),

  -- 通用字段
  created_by      VARCHAR(64)  NOT NULL,
  updated_by      VARCHAR(64),
  create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted         INT DEFAULT 0,
  version         INT DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_business_form_batch_id     ON t_business_form(batch_id);
CREATE INDEX IF NOT EXISTS idx_business_form_status       ON t_business_form(form_status);
CREATE INDEX IF NOT EXISTS idx_business_form_customer_no  ON t_business_form(customer_no);
CREATE INDEX IF NOT EXISTS idx_business_form_create_time  ON t_business_form(create_time);

-- -----------------------------------------------------------------------------
-- 4. 事件存储表（shared-event-starter 的 JdbcEventStore 使用）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_event_store
(
  event_id            VARCHAR(64) PRIMARY KEY,
  event_type          VARCHAR(255) NOT NULL,
  integration_type    VARCHAR(64),
  occurred_on         TIMESTAMP    NOT NULL,
  domain_payload      TEXT         NOT NULL,
  integration_payload TEXT,
  created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_event_dispatch_log
(
  id           BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  event_id     VARCHAR(64)  NOT NULL,
  channel      VARCHAR(100) NOT NULL,
  status       VARCHAR(20)  NOT NULL,
  error_msg    TEXT,
  retry_count  INT DEFAULT 0,
  next_retry_at TIMESTAMP,
  created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_event_channel UNIQUE (event_id, channel)
);

CREATE INDEX IF NOT EXISTS idx_dispatch_status_retry ON sys_event_dispatch_log (status, next_retry_at);

-- -----------------------------------------------------------------------------
-- 5. 年金员工明细批次表（年金专属，H2 兼容版）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_annuity_employee_batch
(
  id                   VARCHAR(64) NOT NULL,
  application_id       VARCHAR(64) NOT NULL,
  batch_status         VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  total_employee_count INT         NOT NULL DEFAULT 0,
  processed_count      INT         NOT NULL DEFAULT 0,
  anomaly_count        INT         NOT NULL DEFAULT 0,
  created_by           VARCHAR(64) NOT NULL,
  updated_by           VARCHAR(64),
  create_time          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted              INT DEFAULT 0,
  version              INT DEFAULT 0,
  PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_annuity_emp_batch_application_id ON t_annuity_employee_batch(application_id);
CREATE INDEX IF NOT EXISTS idx_annuity_emp_batch_status ON t_annuity_employee_batch(batch_status);

-- -----------------------------------------------------------------------------
-- 6. 年金员工明细表（年金专属，H2 兼容版）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_annuity_employee_detail
(
  id                   VARCHAR(64)  NOT NULL,
  batch_id             VARCHAR(64)  NOT NULL,
  employee_name        VARCHAR(255) NOT NULL,
  id_card_no           VARCHAR(32)  NOT NULL,
  age                  INT,
  monthly_salary       BIGINT,
  monthly_contribution BIGINT,
  detail_status        VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
  anomaly_reason       VARCHAR(512),
  materials            TEXT,
  verified_at          TIMESTAMP,
  material_prepared_at TIMESTAMP,
  created_by           VARCHAR(64) NOT NULL,
  updated_by           VARCHAR(64),
  create_time          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted              INT DEFAULT 0,
  version              INT DEFAULT 0,
  PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_annuity_emp_detail_batch_id ON t_annuity_employee_detail(batch_id);
CREATE INDEX IF NOT EXISTS idx_annuity_emp_detail_status ON t_annuity_employee_detail(detail_status);
CREATE UNIQUE INDEX IF NOT EXISTS uk_annuity_emp_detail_idcard ON t_annuity_employee_detail(batch_id, id_card_no);
