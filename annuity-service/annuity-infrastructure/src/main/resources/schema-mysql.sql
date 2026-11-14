-- =============================================================================
-- annuity-service - MySQL Schema
-- =============================================================================
-- 本文件包含两类表：
--   1. kernel 通用表（与 business-core-kernel/schema-mysql.sql 保持一致）：
--      t_business_application  ←  BusinessApplication
--      t_business_batch        ←  BusinessBatch
--      t_business_form         ←  BusinessForm
--      annuity-service 已重构为复用 kernel 的 DO/Mapper/Converter，故三张聚合根
--      表沿用 kernel 表名，不再使用 t_annuity_* 前缀。
--
--   2. 年金专属表（annuity-service 自有聚合根）：
--      t_annuity_employee_batch  ←  AnnuityEmployeeBatch
--      t_annuity_employee_detail ←  AnnuityEmployeeDetail
--
-- 设计要点：
--   1. BusinessContext / OperatorInfo 拍平为独立列，便于 SQL 查询和索引
--   2. business_extension 使用 JSON（MySQL 5.7+ 支持）
--   3. 主键 id 由应用层通过 shared-id-starter 生成（ULID），禁用自增
--   4. 通用字段 created_by/create_time/updated_by/update_time/deleted/version 齐备
--   5. createTime/updateTime 由应用层管理，不使用 ORM 自动填充
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 业务申请单表（kernel 通用）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_business_application
(
  id
  VARCHAR
(
  64
) NOT NULL COMMENT '申请单 ID（ApplicationId，ULID）',
  batch_id VARCHAR
(
  64
) DEFAULT NULL COMMENT '关联批次 ID',
  form_id VARCHAR
(
  64
) DEFAULT NULL COMMENT '关联表单 ID',
  definition_version VARCHAR
(
  64
) DEFAULT NULL COMMENT '流程定义版本',

  -- BusinessContext 拍平字段
  business_type VARCHAR
(
  64
) DEFAULT NULL COMMENT '业务类型',
  customer_no VARCHAR
(
  64
) DEFAULT NULL COMMENT '客户编号',
  customer_name VARCHAR
(
  255
) DEFAULT NULL COMMENT '客户名称',
  product_no VARCHAR
(
  64
) DEFAULT NULL COMMENT '产品编号',
  product_name VARCHAR
(
  255
) DEFAULT NULL COMMENT '产品名称',
  plan_no VARCHAR
(
  64
) DEFAULT NULL COMMENT '方案编号',
  plan_name VARCHAR
(
  255
) DEFAULT NULL COMMENT '方案名称',
  operation_model VARCHAR
(
  64
) DEFAULT NULL COMMENT '运作模式',
  account_manager VARCHAR
(
  64
) DEFAULT NULL COMMENT '账户管理人代码',

  -- OperatorInfo 拍平字段
  channel VARCHAR
(
  64
) DEFAULT NULL COMMENT '渠道',
  operator_id VARCHAR
(
  64
) DEFAULT NULL COMMENT '操作人编号（UserNo）',
  operator_name VARCHAR
(
  255
) DEFAULT NULL COMMENT '操作人姓名',
  is_proxy TINYINT
(
  1
) DEFAULT 0 COMMENT '是否代办',

  -- 文件与统计字段
  parsed_json_file_id VARCHAR
(
  64
) DEFAULT NULL COMMENT '解析后 JSON 文件 ID（FileId）',
  expected_detail_count INT DEFAULT 0 COMMENT '预期明细数量',

  -- 业务扩展字段（多态 JSON）
  business_extension JSON DEFAULT NULL COMMENT '业务扩展字段（JSON 多态）',

  -- 状态机字段
  status VARCHAR
(
  32
) DEFAULT NULL COMMENT '申请单状态',
  current_step VARCHAR
(
  64
) DEFAULT NULL COMMENT '当前流程步骤（ApplicationFlowStep 枚举名）',

  apply_time DATETIME DEFAULT NULL COMMENT '申请时间',
  complete_time DATETIME DEFAULT NULL COMMENT '完成时间',

  -- 通用字段
  created_by VARCHAR
(
  64
) NOT NULL COMMENT '创建人',
  updated_by VARCHAR
(
  64
) DEFAULT NULL COMMENT '更新人',
  create_time DATETIME DEFAULT NULL COMMENT '创建时间',
  update_time DATETIME DEFAULT NULL COMMENT '更新时间',
  deleted TINYINT
(
  1
) DEFAULT 0 COMMENT '逻辑删除标志',
  version INT DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY
(
  id
),
  KEY idx_business_application_batch_id
(
  batch_id
),
  KEY idx_business_application_form_id
(
  form_id
),
  KEY idx_business_application_parsed_file_id
(
  parsed_json_file_id
),
  KEY idx_business_application_status
(
  status
),
  KEY idx_business_application_current_step
(
  current_step
),
  KEY idx_business_application_customer_no
(
  customer_no
),
  KEY idx_business_application_create_time
(
  create_time
)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务申请单表（kernel 通用）';

-- -----------------------------------------------------------------------------
-- 2. 业务批次表（kernel 通用）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_business_batch
(
  id
  VARCHAR
(
  64
) NOT NULL COMMENT '批次 ID（BatchId）',

  -- BusinessContext 拍平字段
  business_type VARCHAR
(
  64
) DEFAULT NULL COMMENT '业务类型',
  customer_no VARCHAR
(
  64
) DEFAULT NULL COMMENT '客户编号',
  customer_name VARCHAR
(
  255
) DEFAULT NULL COMMENT '客户名称',
  product_no VARCHAR
(
  64
) DEFAULT NULL COMMENT '产品编号',
  product_name VARCHAR
(
  255
) DEFAULT NULL COMMENT '产品名称',
  plan_no VARCHAR
(
  64
) DEFAULT NULL COMMENT '方案编号',
  plan_name VARCHAR
(
  255
) DEFAULT NULL COMMENT '方案名称',
  operation_model VARCHAR
(
  64
) DEFAULT NULL COMMENT '运作模式',
  account_manager VARCHAR
(
  64
) DEFAULT NULL COMMENT '账户管理人代码',

  -- OperatorInfo 拍平字段
  channel VARCHAR
(
  64
) DEFAULT NULL COMMENT '渠道',
  operator_id VARCHAR
(
  64
) DEFAULT NULL COMMENT '操作人编号',
  operator_name VARCHAR
(
  255
) DEFAULT NULL COMMENT '操作人姓名',
  is_proxy TINYINT
(
  1
) DEFAULT 0 COMMENT '是否代办',

  -- 批次状态字段
  status VARCHAR
(
  32
) DEFAULT NULL COMMENT '批次状态: CREATED/PROCESSING/PARTIAL_FAILED/FAILED/COMPLETED/CANCELLED',
  total_application_count INT DEFAULT 0 COMMENT '总申请单数量',
  success_count INT DEFAULT 0 COMMENT '成功申请单数量',
  failed_count INT DEFAULT 0 COMMENT '失败申请单数量',

  -- 通用字段
  created_by VARCHAR
(
  64
) NOT NULL COMMENT '创建人',
  updated_by VARCHAR
(
  64
) DEFAULT NULL COMMENT '更新人',
  create_time DATETIME DEFAULT NULL COMMENT '创建时间',
  update_time DATETIME DEFAULT NULL COMMENT '更新时间',
  deleted TINYINT
(
  1
) DEFAULT 0 COMMENT '逻辑删除标志',
  version INT DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY
(
  id
),
  KEY idx_business_batch_status
(
  status
),
  KEY idx_business_batch_customer_no
(
  customer_no
),
  KEY idx_business_batch_business_type
(
  business_type
),
  KEY idx_business_batch_create_time
(
  create_time
)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务批次表（kernel 通用）';

-- -----------------------------------------------------------------------------
-- 3. 业务表单表（kernel 通用）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_business_form
(
  id
  VARCHAR
(
  64
) NOT NULL COMMENT '表单 ID（FormId，ULID）',
  batch_id VARCHAR
(
  64
) DEFAULT NULL COMMENT '关联批次 ID',

  -- BusinessContext 拍平字段
  business_type VARCHAR
(
  64
) DEFAULT NULL COMMENT '业务类型',
  customer_no VARCHAR
(
  64
) DEFAULT NULL COMMENT '客户编号',
  customer_name VARCHAR
(
  255
) DEFAULT NULL COMMENT '客户名称',
  product_no VARCHAR
(
  64
) DEFAULT NULL COMMENT '产品编号',
  product_name VARCHAR
(
  255
) DEFAULT NULL COMMENT '产品名称',
  plan_no VARCHAR
(
  64
) DEFAULT NULL COMMENT '方案编号',
  plan_name VARCHAR
(
  255
) DEFAULT NULL COMMENT '方案名称',
  operation_model VARCHAR
(
  64
) DEFAULT NULL COMMENT '运作模式',
  account_manager VARCHAR
(
  64
) DEFAULT NULL COMMENT '账户管理人代码',

  -- OperatorInfo 拍平字段
  channel VARCHAR
(
  64
) DEFAULT NULL COMMENT '渠道',
  operator_id VARCHAR
(
  64
) DEFAULT NULL COMMENT '操作人编号',
  operator_name VARCHAR
(
  255
) DEFAULT NULL COMMENT '操作人姓名',
  is_proxy TINYINT
(
  1
) DEFAULT 0 COMMENT '是否代办',

  -- 表单文件字段
  form_file_id VARCHAR
(
  64
) DEFAULT NULL COMMENT '表单文件 ID（FileId）',
  form_file_name VARCHAR
(
  512
) DEFAULT NULL COMMENT '表单文件名',
  form_file_size BIGINT DEFAULT NULL COMMENT '表单文件大小（字节）',

  -- 表单状态字段
  form_status VARCHAR
(
  32
) DEFAULT NULL COMMENT '表单状态: WAITING_UPLOAD/UPLOADING/UPLOADED/PARSING/VALIDATING/PARSED/DELETED',

  -- 通用字段
  created_by VARCHAR
(
  64
) NOT NULL COMMENT '创建人',
  updated_by VARCHAR
(
  64
) DEFAULT NULL COMMENT '更新人',
  create_time DATETIME DEFAULT NULL COMMENT '创建时间',
  update_time DATETIME DEFAULT NULL COMMENT '更新时间',
  deleted TINYINT
(
  1
) DEFAULT 0 COMMENT '逻辑删除标志',
  version INT DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY
(
  id
),
  KEY idx_business_form_batch_id
(
  batch_id
),
  KEY idx_business_form_status
(
  form_status
),
  KEY idx_business_form_customer_no
(
  customer_no
),
  KEY idx_business_form_create_time
(
  create_time
)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务表单表（kernel 通用）';

-- -----------------------------------------------------------------------------
-- 4. 年金员工明细批次表（年金专属）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_annuity_employee_batch
(
  id
  VARCHAR
(
  64
) NOT NULL COMMENT '批次 ID（AnnuityEmployeeBatchId）',
  application_id VARCHAR
(
  64
) NOT NULL COMMENT '关联申请单 ID（ApplicationId）',
  batch_status VARCHAR
(
  32
) NOT NULL DEFAULT 'PENDING' COMMENT '批次状态: PENDING/PROCESSING/COMPLETED/FAILED',
  total_employee_count INT NOT NULL DEFAULT 0 COMMENT '员工总数',
  processed_count INT NOT NULL DEFAULT 0 COMMENT '已处理数',
  anomaly_count INT NOT NULL DEFAULT 0 COMMENT '异常数',
  created_by VARCHAR
(
  64
) NOT NULL COMMENT '创建人',
  updated_by VARCHAR
(
  64
) DEFAULT NULL COMMENT '更新人',
  create_time DATETIME DEFAULT NULL COMMENT '创建时间',
  update_time DATETIME DEFAULT NULL COMMENT '更新时间',
  deleted TINYINT
(
  1
) DEFAULT 0 COMMENT '逻辑删除标志',
  version INT DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY
(
  id
),
  UNIQUE KEY uk_annuity_emp_batch_application_id
(
  application_id
),
  KEY idx_annuity_emp_batch_status
(
  batch_status
)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='年金员工明细批次表';

-- -----------------------------------------------------------------------------
-- 5. 年金员工明细表（年金专属）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_annuity_employee_detail
(
  id
  VARCHAR
(
  64
) NOT NULL COMMENT '明细 ID（AnnuityEmployeeDetailId）',
  batch_id VARCHAR
(
  64
) NOT NULL COMMENT '关联批次 ID',
  employee_name VARCHAR
(
  255
) NOT NULL COMMENT '员工姓名',
  id_card_no VARCHAR
(
  32
) NOT NULL COMMENT '身份证号',
  age INT DEFAULT NULL COMMENT '年龄',
  monthly_salary BIGINT DEFAULT NULL COMMENT '月薪（分）',
  monthly_contribution BIGINT DEFAULT NULL COMMENT '月缴存额（分）',
  detail_status VARCHAR
(
  32
) NOT NULL DEFAULT 'PENDING' COMMENT '明细状态: PENDING/VERIFIED/ANOMALY/MATERIAL_READY',
  anomaly_reason VARCHAR
(
  512
) DEFAULT NULL COMMENT '异常原因',
  materials JSON DEFAULT NULL COMMENT '材料清单（JSON 数组）',
  verified_at DATETIME DEFAULT NULL COMMENT '核查时间',
  material_prepared_at DATETIME DEFAULT NULL COMMENT '材料准备时间',
  created_by VARCHAR
(
  64
) NOT NULL COMMENT '创建人',
  updated_by VARCHAR
(
  64
) DEFAULT NULL COMMENT '更新人',
  create_time DATETIME DEFAULT NULL COMMENT '创建时间',
  update_time DATETIME DEFAULT NULL COMMENT '更新时间',
  deleted TINYINT
(
  1
) DEFAULT 0 COMMENT '逻辑删除标志',
  version INT DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY
(
  id
),
  UNIQUE KEY uk_annuity_emp_detail_idcard
(
  batch_id,
  id_card_no
),
  KEY idx_annuity_emp_detail_batch_id
(
  batch_id
),
  KEY idx_annuity_emp_detail_status
(
  detail_status
)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='年金员工明细表';

-- ===== 步骤执行记录（spec §7.2 / §16.3，复用 kernel DO） =====
CREATE TABLE IF NOT EXISTS t_business_step_execution
(
  id                  BIGINT       NOT NULL,
  application_id      VARCHAR(64)  NOT NULL,
  definition_version  VARCHAR(64)  NOT NULL,
  step_code           VARCHAR(64)  NOT NULL,
  attempt             INT          NOT NULL,
  request_no          VARCHAR(128)  NOT NULL,
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

CREATE UNIQUE INDEX uk_business_step_execution_app_step_attempt
  ON t_business_step_execution (application_id, step_code, attempt);
CREATE UNIQUE INDEX uk_business_step_execution_request_no
  ON t_business_step_execution (request_no);
CREATE INDEX idx_business_step_execution_app_status
  ON t_business_step_execution (application_id, status);

-- ===== 外部协作记录（spec §7.3 / §16.1，复用 kernel DO） =====
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

-- ===== 号段 ID 生成表（shared-id-core / §16.3） =====
CREATE TABLE IF NOT EXISTS t_id_generator
(
  seq_key     VARCHAR(64) NOT NULL PRIMARY KEY COMMENT '物理序列键',
  max_id      BIGINT      NOT NULL DEFAULT 0 COMMENT '当前最大ID',
  step        INT         NOT NULL DEFAULT 100 COMMENT '步长',
  create_time TIMESTAMP,
  update_time TIMESTAMP
);

-- ===== 事件消费回执（spec §7.4 / §16.1，复用 kernel DO） =====
CREATE TABLE IF NOT EXISTS t_business_event_receipt
(
  id             BIGINT       NOT NULL,
  event_id       VARCHAR(128) NOT NULL,
  consumer       VARCHAR(64)  NOT NULL,
  status         VARCHAR(32)  NOT NULL,
  error_summary  TEXT,
  created_by     VARCHAR(64)  NOT NULL,
  updated_by     VARCHAR(64),
  create_time    DATETIME     NOT NULL,
  update_time    DATETIME     NOT NULL,
  deleted        TINYINT(1)   NOT NULL DEFAULT 0,
  version        INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事件消费回执';

CREATE UNIQUE INDEX uk_event_receipt_event_consumer
  ON t_business_event_receipt (event_id, consumer);
