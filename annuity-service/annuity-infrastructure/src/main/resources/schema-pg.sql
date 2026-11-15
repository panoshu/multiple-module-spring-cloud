-- =============================================================================
-- annuity-service - PostgreSQL Schema（年金自有表）
-- =============================================================================
-- kernel 通用表（t_business_application / t_business_batch / t_business_form /
-- t_business_step_execution / t_business_external_interaction / t_id_generator /
-- t_business_event_receipt）由 business-core-infrastructure 的 schema-pg.sql
-- 唯一定义，本文件不再复制。部署顺序：先执行 kernel 的 schema-pg.sql，再执行本文件。
--
-- 本文件只建年金专属表：
--   t_annuity_employee_batch  ←  AnnuityEmployeeBatch
--   t_annuity_employee_detail ←  AnnuityEmployeeDetail
--
-- 设计要点：
--   1. 主键 id 由应用层通过 shared-id-starter 生成（ULID），禁用自增
--   2. 通用字段 created_by/create_time/updated_by/update_time/deleted/version 齐备
--   3. createTime/updateTime 由应用层管理，不使用 ORM 自动填充
-- =============================================================================

-- =============================================================================
-- 1. 年金员工明细批次表（年金专属）
-- =============================================================================
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
  deleted BOOLEAN DEFAULT FALSE,
  version INT DEFAULT 0,
  PRIMARY KEY
(
  id
)
  );
CREATE UNIQUE INDEX IF NOT EXISTS uk_annuity_emp_batch_application_id ON t_annuity_employee_batch(application_id);
CREATE INDEX IF NOT EXISTS idx_annuity_emp_batch_status ON t_annuity_employee_batch(batch_status);

COMMENT
ON TABLE  t_annuity_employee_batch IS '年金员工明细批次表';
COMMENT
ON COLUMN t_annuity_employee_batch.id                   IS '批次 ID（AnnuityEmployeeBatchId）';
COMMENT
ON COLUMN t_annuity_employee_batch.application_id       IS '关联申请单 ID（ApplicationId）';
COMMENT
ON COLUMN t_annuity_employee_batch.batch_status         IS '批次状态: PENDING/PROCESSING/COMPLETED/FAILED';
COMMENT
ON COLUMN t_annuity_employee_batch.total_employee_count IS '员工总数';
COMMENT
ON COLUMN t_annuity_employee_batch.processed_count      IS '已处理数';
COMMENT
ON COLUMN t_annuity_employee_batch.anomaly_count        IS '异常数';
COMMENT
ON COLUMN t_annuity_employee_batch.created_by           IS '创建人';
COMMENT
ON COLUMN t_annuity_employee_batch.updated_by           IS '更新人';
COMMENT
ON COLUMN t_annuity_employee_batch.create_time          IS '创建时间';
COMMENT
ON COLUMN t_annuity_employee_batch.update_time          IS '更新时间';
COMMENT
ON COLUMN t_annuity_employee_batch.deleted              IS '逻辑删除标志';
COMMENT
ON COLUMN t_annuity_employee_batch.version              IS '乐观锁版本号';

-- =============================================================================
-- 2. 年金员工明细表（年金专属）
-- =============================================================================
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
  materials JSONB,
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
  deleted BOOLEAN DEFAULT FALSE,
  version INT DEFAULT 0,
  PRIMARY KEY
(
  id
)
  );
CREATE INDEX IF NOT EXISTS idx_annuity_emp_detail_batch_id ON t_annuity_employee_detail(batch_id);
CREATE INDEX IF NOT EXISTS idx_annuity_emp_detail_status ON t_annuity_employee_detail(detail_status);
CREATE UNIQUE INDEX IF NOT EXISTS uk_annuity_emp_detail_idcard ON t_annuity_employee_detail(batch_id, id_card_no);

COMMENT
ON TABLE  t_annuity_employee_detail IS '年金员工明细表';
COMMENT
ON COLUMN t_annuity_employee_detail.id                   IS '明细 ID（AnnuityEmployeeDetailId）';
COMMENT
ON COLUMN t_annuity_employee_detail.batch_id             IS '关联批次 ID';
COMMENT
ON COLUMN t_annuity_employee_detail.employee_name        IS '员工姓名';
COMMENT
ON COLUMN t_annuity_employee_detail.id_card_no           IS '身份证号';
COMMENT
ON COLUMN t_annuity_employee_detail.age                  IS '年龄';
COMMENT
ON COLUMN t_annuity_employee_detail.monthly_salary       IS '月薪（分）';
COMMENT
ON COLUMN t_annuity_employee_detail.monthly_contribution IS '月缴存额（分）';
COMMENT
ON COLUMN t_annuity_employee_detail.detail_status        IS '明细状态: PENDING/VERIFIED/ANOMALY/MATERIAL_READY';
COMMENT
ON COLUMN t_annuity_employee_detail.anomaly_reason       IS '异常原因';
COMMENT
ON COLUMN t_annuity_employee_detail.materials            IS '材料清单（JSON 数组）';
COMMENT
ON COLUMN t_annuity_employee_detail.verified_at          IS '核查时间';
COMMENT
ON COLUMN t_annuity_employee_detail.material_prepared_at IS '材料准备时间';
COMMENT
ON COLUMN t_annuity_employee_detail.created_by           IS '创建人';
COMMENT
ON COLUMN t_annuity_employee_detail.updated_by           IS '更新人';
COMMENT
ON COLUMN t_annuity_employee_detail.create_time          IS '创建时间';
COMMENT
ON COLUMN t_annuity_employee_detail.update_time          IS '更新时间';
COMMENT
ON COLUMN t_annuity_employee_detail.deleted              IS '逻辑删除标志';
COMMENT
ON COLUMN t_annuity_employee_detail.version              IS '乐观锁版本号';
