-- =============================================================================
-- annuity-service - MySQL Schema（年金自有表）
-- =============================================================================
-- kernel 通用表（t_business_application / t_business_batch / t_business_form /
-- t_business_step_execution / t_business_external_interaction / t_id_generator /
-- t_business_event_receipt）由 business-core-infrastructure 的 schema-mysql.sql
-- 唯一定义，本文件不再复制。部署顺序：先执行 kernel 的 schema-mysql.sql，再执行本文件。
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

-- -----------------------------------------------------------------------------
-- 1. 年金员工明细批次表（年金专属）
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
-- 2. 年金员工明细表（年金专属）
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
