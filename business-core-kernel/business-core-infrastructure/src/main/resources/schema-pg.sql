-- =============================================================================
-- business-core-kernel - PostgreSQL Schema
-- =============================================================================
-- 三张表对应 kernel 的三个聚合根：
--   t_business_application  ←  BusinessApplication
--   t_business_batch        ←  BusinessBatch
--   t_business_form         ←  BusinessForm
--
-- 设计要点：
--   1. BusinessContext / OperatorInfo 拍平为独立列，便于 SQL 查询和索引
--   2. businessExtension 使用 JSONB 存储多态值对象
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
  is_proxy BOOLEAN DEFAULT FALSE,

  -- 文件与统计字段
  parsed_json_file_id VARCHAR
(
  64
),
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
  create_time TIMESTAMP,
  update_time TIMESTAMP,
  deleted BOOLEAN DEFAULT FALSE,
  version INT DEFAULT 0,
  PRIMARY KEY
(
  id
)
  );

CREATE INDEX IF NOT EXISTS idx_business_application_batch_id ON t_business_application(batch_id);
CREATE INDEX IF NOT EXISTS idx_business_application_form_id ON t_business_application(form_id);
CREATE INDEX IF NOT EXISTS idx_business_application_parsed_file_id ON t_business_application(parsed_json_file_id);
CREATE INDEX IF NOT EXISTS idx_business_application_status ON t_business_application(status);
CREATE INDEX IF NOT EXISTS idx_business_application_current_step ON t_business_application(current_step);
CREATE INDEX IF NOT EXISTS idx_business_application_customer_no ON t_business_application(customer_no);
CREATE INDEX IF NOT EXISTS idx_business_application_create_time ON t_business_application(create_time);

COMMENT
ON TABLE  t_business_application IS '业务申请单表（kernel 通用）';
COMMENT
ON COLUMN t_business_application.id                    IS '申请单 ID（ApplicationId，ULID）';
COMMENT
ON COLUMN t_business_application.batch_id              IS '关联批次 ID';
COMMENT
ON COLUMN t_business_application.form_id               IS '关联表单 ID';
COMMENT
ON COLUMN t_business_application.business_type         IS '业务类型';
COMMENT
ON COLUMN t_business_application.customer_no           IS '客户编号';
COMMENT
ON COLUMN t_business_application.customer_name         IS '客户名称';
COMMENT
ON COLUMN t_business_application.product_no            IS '产品编号';
COMMENT
ON COLUMN t_business_application.product_name          IS '产品名称';
COMMENT
ON COLUMN t_business_application.plan_no               IS '方案编号';
COMMENT
ON COLUMN t_business_application.plan_name             IS '方案名称';
COMMENT
ON COLUMN t_business_application.operation_model       IS '运作模式';
COMMENT
ON COLUMN t_business_application.account_manager       IS '账户管理人代码';
COMMENT
ON COLUMN t_business_application.channel               IS '渠道';
COMMENT
ON COLUMN t_business_application.operator_id           IS '操作人编号（UserNo）';
COMMENT
ON COLUMN t_business_application.operator_name         IS '操作人姓名';
COMMENT
ON COLUMN t_business_application.is_proxy              IS '是否代办';
COMMENT
ON COLUMN t_business_application.parsed_json_file_id   IS '解析后 JSON 文件 ID（FileId）';
COMMENT
ON COLUMN t_business_application.expected_detail_count IS '预期明细数量';
COMMENT
ON COLUMN t_business_application.status                IS '申请单状态';
COMMENT
ON COLUMN t_business_application.current_step          IS '当前流程步骤（ApplicationFlowStep 枚举名）';
COMMENT
ON COLUMN t_business_application.apply_time            IS '申请时间';
COMMENT
ON COLUMN t_business_application.complete_time         IS '完成时间';
COMMENT
ON COLUMN t_business_application.created_by            IS '创建人';
COMMENT
ON COLUMN t_business_application.updated_by            IS '更新人';
COMMENT
ON COLUMN t_business_application.create_time           IS '创建时间';
COMMENT
ON COLUMN t_business_application.update_time           IS '更新时间';
COMMENT
ON COLUMN t_business_application.deleted               IS '逻辑删除标志';
COMMENT
ON COLUMN t_business_application.version               IS '乐观锁版本号';

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
  create_time TIMESTAMP,
  update_time TIMESTAMP,
  deleted BOOLEAN DEFAULT FALSE,
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

COMMENT
ON TABLE  t_business_batch IS '业务批次表（kernel 通用）';
COMMENT
ON COLUMN t_business_batch.id                      IS '批次 ID（BatchId）';
COMMENT
ON COLUMN t_business_batch.business_type           IS '业务类型';
COMMENT
ON COLUMN t_business_batch.customer_no             IS '客户编号';
COMMENT
ON COLUMN t_business_batch.customer_name           IS '客户名称';
COMMENT
ON COLUMN t_business_batch.product_no              IS '产品编号';
COMMENT
ON COLUMN t_business_batch.product_name            IS '产品名称';
COMMENT
ON COLUMN t_business_batch.plan_no                 IS '方案编号';
COMMENT
ON COLUMN t_business_batch.plan_name               IS '方案名称';
COMMENT
ON COLUMN t_business_batch.operation_model         IS '运作模式';
COMMENT
ON COLUMN t_business_batch.account_manager         IS '账户管理人代码';
COMMENT
ON COLUMN t_business_batch.channel                 IS '渠道';
COMMENT
ON COLUMN t_business_batch.operator_id             IS '操作人编号';
COMMENT
ON COLUMN t_business_batch.operator_name           IS '操作人姓名';
COMMENT
ON COLUMN t_business_batch.is_proxy                IS '是否代办';
COMMENT
ON COLUMN t_business_batch.status                  IS '批次状态: CREATED/PROCESSING/PARTIAL_FAILED/FAILED/COMPLETED/CANCELLED';
COMMENT
ON COLUMN t_business_batch.total_application_count IS '总申请单数量';
COMMENT
ON COLUMN t_business_batch.success_count           IS '成功申请单数量';
COMMENT
ON COLUMN t_business_batch.failed_count            IS '失败申请单数量';
COMMENT
ON COLUMN t_business_batch.created_by              IS '创建人';
COMMENT
ON COLUMN t_business_batch.updated_by              IS '更新人';
COMMENT
ON COLUMN t_business_batch.create_time             IS '创建时间';
COMMENT
ON COLUMN t_business_batch.update_time             IS '更新时间';
COMMENT
ON COLUMN t_business_batch.deleted                 IS '逻辑删除标志';
COMMENT
ON COLUMN t_business_batch.version                 IS '乐观锁版本号';

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
  create_time TIMESTAMP,
  update_time TIMESTAMP,
  deleted BOOLEAN DEFAULT FALSE,
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

COMMENT
ON TABLE  t_business_form IS '业务表单表（kernel 通用）';
COMMENT
ON COLUMN t_business_form.id              IS '表单 ID（FormId，ULID）';
COMMENT
ON COLUMN t_business_form.batch_id        IS '关联批次 ID';
COMMENT
ON COLUMN t_business_form.business_type   IS '业务类型';
COMMENT
ON COLUMN t_business_form.customer_no     IS '客户编号';
COMMENT
ON COLUMN t_business_form.customer_name   IS '客户名称';
COMMENT
ON COLUMN t_business_form.product_no      IS '产品编号';
COMMENT
ON COLUMN t_business_form.product_name    IS '产品名称';
COMMENT
ON COLUMN t_business_form.plan_no         IS '方案编号';
COMMENT
ON COLUMN t_business_form.plan_name       IS '方案名称';
COMMENT
ON COLUMN t_business_form.operation_model IS '运作模式';
COMMENT
ON COLUMN t_business_form.account_manager IS '账户管理人代码';
COMMENT
ON COLUMN t_business_form.channel         IS '渠道';
COMMENT
ON COLUMN t_business_form.operator_id     IS '操作人编号';
COMMENT
ON COLUMN t_business_form.operator_name   IS '操作人姓名';
COMMENT
ON COLUMN t_business_form.is_proxy        IS '是否代办';
COMMENT
ON COLUMN t_business_form.form_file_id    IS '表单文件 ID（FileId）';
COMMENT
ON COLUMN t_business_form.form_file_name  IS '表单文件名';
COMMENT
ON COLUMN t_business_form.form_file_size  IS '表单文件大小（字节）';
COMMENT
ON COLUMN t_business_form.form_status     IS '表单状态: WAITING_UPLOAD/UPLOADING/UPLOADED/PARSING/VALIDATING/PARSED/DELETED';
COMMENT
ON COLUMN t_business_form.created_by      IS '创建人';
COMMENT
ON COLUMN t_business_form.updated_by      IS '更新人';
COMMENT
ON COLUMN t_business_form.create_time     IS '创建时间';
COMMENT
ON COLUMN t_business_form.update_time     IS '更新时间';
COMMENT
ON COLUMN t_business_form.deleted         IS '逻辑删除标志';
COMMENT
ON COLUMN t_business_form.version         IS '乐观锁版本号';

-- ===== 步骤执行记录（spec §7.2 / §16.3） =====
CREATE TABLE IF NOT EXISTS t_business_step_execution
(
  id                  BIGINT       NOT NULL,
  application_id     VARCHAR(64) NOT NULL,
  definition_version  VARCHAR(64) NOT NULL,
  step_code           VARCHAR(64) NOT NULL,
  attempt             INT         NOT NULL,
  request_no          VARCHAR(128) NOT NULL,
  status              VARCHAR(32) NOT NULL,
  started_at          TIMESTAMP,
  ended_at            TIMESTAMP,
  error_code          VARCHAR(64),
  error_summary       TEXT,
  checkpoint_ref      VARCHAR(255),
  created_by          VARCHAR(64) NOT NULL,
  updated_by          VARCHAR(64),
  create_time         TIMESTAMP   NOT NULL,
  update_time         TIMESTAMP   NOT NULL,
  deleted             BOOLEAN     NOT NULL DEFAULT FALSE,
  version             INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_business_step_execution_app_step_attempt
  ON t_business_step_execution (application_id, step_code, attempt);
CREATE UNIQUE INDEX IF NOT EXISTS uk_business_step_execution_request_no
  ON t_business_step_execution (request_no);
CREATE INDEX IF NOT EXISTS idx_business_step_execution_app_status
  ON t_business_step_execution (application_id, status);

-- ===== 外部协作记录（spec §7.3 / §16.1） =====
CREATE TABLE IF NOT EXISTS t_business_external_interaction
(
  id                   BIGINT       NOT NULL,
  application_id       VARCHAR(64)  NOT NULL,
  step_execution_id    BIGINT       NOT NULL,
  interaction_type     VARCHAR(32)  NOT NULL,
  request_no           VARCHAR(128) NOT NULL,
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
  deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
  version              INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_external_interaction_request_no
  ON t_business_external_interaction (request_no);
CREATE UNIQUE INDEX IF NOT EXISTS uk_external_interaction_type_task
  ON t_business_external_interaction (interaction_type, external_task_id);
CREATE INDEX IF NOT EXISTS idx_external_interaction_status_retry
  ON t_business_external_interaction (status, next_retry_time);
CREATE INDEX IF NOT EXISTS idx_external_interaction_application_id
  ON t_business_external_interaction (application_id);

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
  deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
  version        INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_event_receipt_event_consumer
  ON t_business_event_receipt (event_id, consumer);
