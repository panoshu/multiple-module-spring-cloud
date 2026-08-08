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
  id                    VARCHAR(64)   NOT NULL,
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
  is_proxy              TINYINT(1) DEFAULT 0,

  -- 文件与统计字段
  parsed_json_file_id   VARCHAR(64),
  expected_detail_count INT DEFAULT 0,

  -- 业务扩展字段（多态 JSON）
  business_extension    JSON,

  -- 状态机字段
  status                VARCHAR(32),
  current_step          VARCHAR(64),

  apply_time            DATETIME,
  complete_time         DATETIME,

  -- 通用字段
  created_by            VARCHAR(64)   NOT NULL,
  updated_by            VARCHAR(64),
  create_time           DATETIME,
  update_time           DATETIME,
  deleted               TINYINT(1) DEFAULT 0,
  version               INT DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE INDEX idx_business_application_batch_id       ON t_business_application(batch_id);
CREATE INDEX idx_business_application_form_id        ON t_business_application(form_id);
CREATE INDEX idx_business_application_parsed_file_id ON t_business_application(parsed_json_file_id);
CREATE INDEX idx_business_application_status         ON t_business_application(status);
CREATE INDEX idx_business_application_current_step   ON t_business_application(current_step);
CREATE INDEX idx_business_application_customer_no    ON t_business_application(customer_no);
CREATE INDEX idx_business_application_create_time    ON t_business_application(create_time);

-- -----------------------------------------------------------------------------
-- 2. 业务批次表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_business_batch
(
  id                       VARCHAR(64)   NOT NULL,

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
  is_proxy                 TINYINT(1) DEFAULT 0,

  -- 批次状态字段
  status                   VARCHAR(32),
  total_application_count  INT DEFAULT 0,
  success_count            INT DEFAULT 0,
  failed_count             INT DEFAULT 0,

  -- 通用字段
  created_by               VARCHAR(64)   NOT NULL,
  updated_by               VARCHAR(64),
  create_time              DATETIME,
  update_time              DATETIME,
  deleted                  TINYINT(1) DEFAULT 0,
  version                  INT DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE INDEX idx_business_batch_status         ON t_business_batch(status);
CREATE INDEX idx_business_batch_customer_no    ON t_business_batch(customer_no);
CREATE INDEX idx_business_batch_business_type  ON t_business_batch(business_type);
CREATE INDEX idx_business_batch_create_time    ON t_business_batch(create_time);

-- -----------------------------------------------------------------------------
-- 3. 业务表单表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_business_form
(
  id              VARCHAR(64)   NOT NULL,
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
  is_proxy        TINYINT(1) DEFAULT 0,

  -- 表单文件字段
  form_file_id    VARCHAR(64),
  form_file_name  VARCHAR(512),
  form_file_size  BIGINT,

  -- 表单状态字段
  form_status     VARCHAR(32),

  -- 通用字段
  created_by      VARCHAR(64)   NOT NULL,
  updated_by      VARCHAR(64),
  create_time     DATETIME,
  update_time     DATETIME,
  deleted         TINYINT(1) DEFAULT 0,
  version         INT DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE INDEX idx_business_form_batch_id     ON t_business_form(batch_id);
CREATE INDEX idx_business_form_status       ON t_business_form(form_status);
CREATE INDEX idx_business_form_customer_no  ON t_business_form(customer_no);
CREATE INDEX idx_business_form_create_time  ON t_business_form(create_time);
