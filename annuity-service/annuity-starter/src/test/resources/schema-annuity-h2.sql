-- =============================================================================
-- annuity-service - H2 测试 Schema 自有表（PostgreSQL 兼容模式）
-- =============================================================================
-- kernel 通用表（t_business_* / t_id_generator / t_business_event_receipt）
-- 已迁到 business-core-infrastructure 的 schema-kernel-h2.sql，由
-- application-test.yml 的 schema-locations 组合加载，本文件不再复制定义。
--
-- 本文件只包含：
--   1. shared-event-starter 的事件存储表（sys_event_store / sys_event_dispatch_log）
--   2. 年金专属表：
--      t_annuity_employee_batch  ←  AnnuityEmployeeBatch
--      t_annuity_employee_detail ←  AnnuityEmployeeDetail
--
-- 生产部署脚本（annuity-infrastructure 的 schema-pg.sql / schema-mysql.sql）
-- 同样只保留年金自有表；kernel 表请先执行 business-core-infrastructure 的
-- schema-pg.sql / schema-mysql.sql。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 事件存储表（shared-event-starter 的 JdbcEventStore 使用）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_event_store
(
  event_id
  VARCHAR
(
  64
) PRIMARY KEY,
  event_type VARCHAR
(
  255
) NOT NULL,
  integration_type VARCHAR
(
  64
),
  occurred_on TIMESTAMP NOT NULL,
  domain_payload TEXT NOT NULL,
  integration_payload TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
  );

CREATE TABLE IF NOT EXISTS sys_event_dispatch_log
(
  id
  BIGINT
  GENERATED
  BY
  DEFAULT AS
  IDENTITY
  PRIMARY
  KEY,
  event_id
  VARCHAR
(
  64
) NOT NULL,
  channel VARCHAR
(
  100
) NOT NULL,
  status VARCHAR
(
  20
) NOT NULL,
  error_msg TEXT,
  retry_count INT DEFAULT 0,
  next_retry_at TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_event_channel UNIQUE
(
  event_id,
  channel
)
  );

CREATE INDEX IF NOT EXISTS idx_dispatch_status_retry ON sys_event_dispatch_log (status, next_retry_at);

-- -----------------------------------------------------------------------------
-- 2. 年金员工明细批次表（年金专属，H2 兼容版）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_annuity_employee_batch
(
  id
  VARCHAR
(
  64
) NOT NULL,
  application_id VARCHAR
(
  64
) NOT NULL,
  batch_status VARCHAR
(
  32
) NOT NULL DEFAULT 'PENDING',
  total_employee_count INT NOT NULL DEFAULT 0,
  processed_count INT NOT NULL DEFAULT 0,
  anomaly_count INT NOT NULL DEFAULT 0,
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
CREATE UNIQUE INDEX IF NOT EXISTS uk_annuity_emp_batch_application_id ON t_annuity_employee_batch(application_id);
CREATE INDEX IF NOT EXISTS idx_annuity_emp_batch_status ON t_annuity_employee_batch(batch_status);

-- -----------------------------------------------------------------------------
-- 3. 年金员工明细表（年金专属，H2 兼容版）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_annuity_employee_detail
(
  id
  VARCHAR
(
  64
) NOT NULL,
  batch_id VARCHAR
(
  64
) NOT NULL,
  employee_name VARCHAR
(
  255
) NOT NULL,
  id_card_no VARCHAR
(
  32
) NOT NULL,
  age INT,
  monthly_salary BIGINT,
  monthly_contribution BIGINT,
  detail_status VARCHAR
(
  32
) NOT NULL DEFAULT 'PENDING',
  anomaly_reason VARCHAR
(
  512
),
  materials TEXT,
  verified_at TIMESTAMP,
  material_prepared_at TIMESTAMP,
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
CREATE INDEX IF NOT EXISTS idx_annuity_emp_detail_batch_id ON t_annuity_employee_detail(batch_id);
CREATE INDEX IF NOT EXISTS idx_annuity_emp_detail_status ON t_annuity_employee_detail(detail_status);
CREATE UNIQUE INDEX IF NOT EXISTS uk_annuity_emp_detail_idcard ON t_annuity_employee_detail(batch_id, id_card_no);
