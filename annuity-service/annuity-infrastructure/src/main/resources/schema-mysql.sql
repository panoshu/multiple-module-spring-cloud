-- =============================================================================
-- 年金业务演示服务 - MySQL Schema
-- =============================================================================
-- 与 schema-pg.sql 保持结构一致，差异点：
--   1. business_extension 使用 JSON 而非 JSONB（MySQL 5.7+ 支持）
--   2. TIMESTAMP DEFAULT CURRENT_TIMESTAMP 受 MySQL 限制，update_time 需显式 ON UPDATE
--   3. 索引创建使用 KEY 而非 CREATE INDEX IF NOT EXISTS（MySQL 不支持 IF NOT EXISTS 索引语法）
--   4. 表/字段注释使用 COMMENT 内联语法
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 年金业务申请单表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_annuity_application`
(
  `id`
  VARCHAR
(
  64
) NOT NULL COMMENT '申请单 ID（ApplicationId，ULID）',
  `batch_id` VARCHAR
(
  64
) DEFAULT NULL COMMENT '关联批次 ID',
  `form_id` VARCHAR
(
  64
) DEFAULT NULL COMMENT '关联表单 ID',

  `business_type` VARCHAR
(
  64
) DEFAULT NULL COMMENT '业务类型: ACC_PLAN_CREATE/MODIFY/DELETE',
  `customer_no` VARCHAR
(
  64
) DEFAULT NULL COMMENT '客户编号',
  `customer_name` VARCHAR
(
  255
) DEFAULT NULL COMMENT '客户名称',
  `product_no` VARCHAR
(
  64
) DEFAULT NULL COMMENT '产品编号',
  `product_name` VARCHAR
(
  255
) DEFAULT NULL COMMENT '产品名称',
  `plan_no` VARCHAR
(
  64
) DEFAULT NULL COMMENT '方案编号',
  `plan_name` VARCHAR
(
  255
) DEFAULT NULL COMMENT '方案名称',
  `operation_model` VARCHAR
(
  64
) DEFAULT NULL COMMENT '运作模式',
  `account_manager` VARCHAR
(
  64
) DEFAULT NULL COMMENT '账户管理人代码',

  `channel` VARCHAR
(
  64
) DEFAULT NULL COMMENT '年金渠道',
  `operator_id` VARCHAR
(
  64
) DEFAULT NULL COMMENT '操作人编号',
  `operator_name` VARCHAR
(
  255
) DEFAULT NULL COMMENT '操作人姓名',
  `is_proxy` BOOLEAN DEFAULT FALSE COMMENT '是否代办',

  `parsed_json_file_id` VARCHAR
(
  64
) DEFAULT NULL COMMENT '解析后 JSON 文件 ID',
  `expected_detail_count` INT DEFAULT 0 COMMENT '预期明细数量',

  `business_extension` JSON DEFAULT NULL COMMENT '业务扩展字段（JSON 多态: AnnuityApplicationExtension）',

  `status` VARCHAR
(
  32
) DEFAULT NULL COMMENT '申请单状态',
  `current_step` VARCHAR
(
  64
) DEFAULT NULL COMMENT '当前流程步骤',
  `apply_time` TIMESTAMP NULL DEFAULT NULL COMMENT '申请时间',
  `complete_time` TIMESTAMP NULL DEFAULT NULL COMMENT '完成时间',

  `created_by` VARCHAR
(
  64
) NOT NULL COMMENT '创建人',
  `updated_by` VARCHAR
(
  64
) DEFAULT NULL COMMENT '更新人',
  `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BOOLEAN DEFAULT FALSE COMMENT '逻辑删除标志',
  `version` INT DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY
(
  `id`
),
  KEY `idx_annuity_application_batch_id`
(
  `batch_id`
),
  KEY `idx_annuity_application_form_id`
(
  `form_id`
),
  KEY `idx_annuity_application_parsed_file_id`
(
  `parsed_json_file_id`
),
  KEY `idx_annuity_application_status`
(
  `status`
),
  KEY `idx_annuity_application_current_step`
(
  `current_step`
),
  KEY `idx_annuity_application_customer_no`
(
  `customer_no`
),
  KEY `idx_annuity_application_create_time`
(
  `create_time`
)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='年金业务申请单表';

-- -----------------------------------------------------------------------------
-- 2. 年金业务批次表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_annuity_batch`
(
  `id`
  VARCHAR
(
  64
) NOT NULL COMMENT '批次 ID（BatchId）',

  `business_type` VARCHAR
(
  64
) DEFAULT NULL COMMENT '业务类型',
  `customer_no` VARCHAR
(
  64
) DEFAULT NULL COMMENT '客户编号',
  `customer_name` VARCHAR
(
  255
) DEFAULT NULL COMMENT '客户名称',
  `product_no` VARCHAR
(
  64
) DEFAULT NULL COMMENT '产品编号',
  `product_name` VARCHAR
(
  255
) DEFAULT NULL COMMENT '产品名称',
  `plan_no` VARCHAR
(
  64
) DEFAULT NULL COMMENT '方案编号',
  `plan_name` VARCHAR
(
  255
) DEFAULT NULL COMMENT '方案名称',
  `operation_model` VARCHAR
(
  64
) DEFAULT NULL COMMENT '运作模式',
  `account_manager` VARCHAR
(
  64
) DEFAULT NULL COMMENT '账户管理人代码',

  `channel` VARCHAR
(
  64
) DEFAULT NULL COMMENT '年金渠道',
  `operator_id` VARCHAR
(
  64
) DEFAULT NULL COMMENT '操作人编号',
  `operator_name` VARCHAR
(
  255
) DEFAULT NULL COMMENT '操作人姓名',
  `is_proxy` BOOLEAN DEFAULT FALSE COMMENT '是否代办',

  `status` VARCHAR
(
  32
) DEFAULT NULL COMMENT '批次状态: CREATED/PROCESSING/PARTIAL_FAILED/FAILED/COMPLETED',
  `total_application_count` INT DEFAULT 0 COMMENT '总申请单数量',
  `success_count` INT DEFAULT 0 COMMENT '成功申请单数量',
  `failed_count` INT DEFAULT 0 COMMENT '失败申请单数量',

  `created_by` VARCHAR
(
  64
) NOT NULL COMMENT '创建人',
  `updated_by` VARCHAR
(
  64
) DEFAULT NULL COMMENT '更新人',
  `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BOOLEAN DEFAULT FALSE COMMENT '逻辑删除标志',
  `version` INT DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY
(
  `id`
),
  KEY `idx_annuity_batch_status`
(
  `status`
),
  KEY `idx_annuity_batch_customer_no`
(
  `customer_no`
),
  KEY `idx_annuity_batch_business_type`
(
  `business_type`
),
  KEY `idx_annuity_batch_create_time`
(
  `create_time`
)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='年金业务批次表';

-- -----------------------------------------------------------------------------
-- 3. 年金业务表单表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_annuity_form`
(
  `id`
  VARCHAR
(
  64
) NOT NULL COMMENT '表单 ID（FormId，ULID）',
  `batch_id` VARCHAR
(
  64
) DEFAULT NULL COMMENT '关联批次 ID',

  `business_type` VARCHAR
(
  64
) DEFAULT NULL COMMENT '业务类型',
  `customer_no` VARCHAR
(
  64
) DEFAULT NULL COMMENT '客户编号',
  `customer_name` VARCHAR
(
  255
) DEFAULT NULL COMMENT '客户名称',
  `product_no` VARCHAR
(
  64
) DEFAULT NULL COMMENT '产品编号',
  `product_name` VARCHAR
(
  255
) DEFAULT NULL COMMENT '产品名称',
  `plan_no` VARCHAR
(
  64
) DEFAULT NULL COMMENT '方案编号',
  `plan_name` VARCHAR
(
  255
) DEFAULT NULL COMMENT '方案名称',
  `operation_model` VARCHAR
(
  64
) DEFAULT NULL COMMENT '运作模式',
  `account_manager` VARCHAR
(
  64
) DEFAULT NULL COMMENT '账户管理人代码',

  `channel` VARCHAR
(
  64
) DEFAULT NULL COMMENT '年金渠道',
  `operator_id` VARCHAR
(
  64
) DEFAULT NULL COMMENT '操作人编号',
  `operator_name` VARCHAR
(
  255
) DEFAULT NULL COMMENT '操作人姓名',
  `is_proxy` BOOLEAN DEFAULT FALSE COMMENT '是否代办',

  `form_file_id` VARCHAR
(
  64
) DEFAULT NULL COMMENT '表单文件 ID（FileId）',
  `form_file_name` VARCHAR
(
  512
) DEFAULT NULL COMMENT '表单文件名',
  `form_file_size` BIGINT DEFAULT NULL COMMENT '表单文件大小（字节）',

  `form_status` VARCHAR
(
  32
) DEFAULT NULL COMMENT '表单状态: WAITING_UPLOAD/UPLOADING/UPLOADED/PARSING/VALIDATING/PARSED/DELETED',

  `created_by` VARCHAR
(
  64
) NOT NULL COMMENT '创建人',
  `updated_by` VARCHAR
(
  64
) DEFAULT NULL COMMENT '更新人',
  `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BOOLEAN DEFAULT FALSE COMMENT '逻辑删除标志',
  `version` INT DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY
(
  `id`
),
  KEY `idx_annuity_form_batch_id`
(
  `batch_id`
),
  KEY `idx_annuity_form_status`
(
  `form_status`
),
  KEY `idx_annuity_form_customer_no`
(
  `customer_no`
),
  KEY `idx_annuity_form_create_time`
(
  `create_time`
)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='年金业务表单表';

-- -----------------------------------------------------------------------------
-- 4. 年金员工明细批次表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_annuity_employee_batch`
(
  `id`
  VARCHAR
(
  64
) NOT NULL COMMENT '批次 ID（AnnuityEmployeeBatchId）',
  `application_id` VARCHAR
(
  64
) NOT NULL COMMENT '关联申请单 ID（ApplicationId）',
  `batch_status` VARCHAR
(
  32
) NOT NULL DEFAULT 'PENDING' COMMENT '批次状态: PENDING/PROCESSING/COMPLETED/FAILED',
  `total_employee_count` INT NOT NULL DEFAULT 0 COMMENT '员工总数',
  `processed_count` INT NOT NULL DEFAULT 0 COMMENT '已处理数',
  `anomaly_count` INT NOT NULL DEFAULT 0 COMMENT '异常数',
  `created_by` VARCHAR
(
  64
) NOT NULL COMMENT '创建人',
  `updated_by` VARCHAR
(
  64
) DEFAULT NULL COMMENT '更新人',
  `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BOOLEAN DEFAULT FALSE COMMENT '逻辑删除标志',
  `version` INT DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY
(
  `id`
),
  UNIQUE KEY `uk_annuity_emp_batch_application_id`
(
  `application_id`
),
  KEY `idx_annuity_emp_batch_status`
(
  `batch_status`
)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='年金员工明细批次表';

-- -----------------------------------------------------------------------------
-- 5. 年金员工明细表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_annuity_employee_detail`
(
  `id`
  VARCHAR
(
  64
) NOT NULL COMMENT '明细 ID（AnnuityEmployeeDetailId）',
  `batch_id` VARCHAR
(
  64
) NOT NULL COMMENT '关联批次 ID',
  `employee_name` VARCHAR
(
  255
) NOT NULL COMMENT '员工姓名',
  `id_card_no` VARCHAR
(
  32
) NOT NULL COMMENT '身份证号',
  `age` INT DEFAULT NULL COMMENT '年龄',
  `monthly_salary` BIGINT DEFAULT NULL COMMENT '月薪（分）',
  `monthly_contribution` BIGINT DEFAULT NULL COMMENT '月缴存额（分）',
  `detail_status` VARCHAR
(
  32
) NOT NULL DEFAULT 'PENDING' COMMENT '明细状态: PENDING/VERIFIED/ANOMALY/MATERIAL_READY',
  `anomaly_reason` VARCHAR
(
  512
) DEFAULT NULL COMMENT '异常原因',
  `materials` JSON DEFAULT NULL COMMENT '材料清单（JSON 数组）',
  `verified_at` DATETIME DEFAULT NULL COMMENT '核查时间',
  `material_prepared_at` DATETIME DEFAULT NULL COMMENT '材料准备时间',
  `created_by` VARCHAR
(
  64
) NOT NULL COMMENT '创建人',
  `updated_by` VARCHAR
(
  64
) DEFAULT NULL COMMENT '更新人',
  `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BOOLEAN DEFAULT FALSE COMMENT '逻辑删除标志',
  `version` INT DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY
(
  `id`
),
  UNIQUE KEY `uk_annuity_emp_detail_idcard`
(
  `batch_id`,
  `id_card_no`
),
  KEY `idx_annuity_emp_detail_batch_id`
(
  `batch_id`
),
  KEY `idx_annuity_emp_detail_status`
(
  `detail_status`
)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='年金员工明细表';
