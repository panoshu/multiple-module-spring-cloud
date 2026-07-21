-- =============================================================================
-- 年金业务演示服务 - PostgreSQL Schema
-- =============================================================================
-- 三张表对应 kernel 的三个聚合根：
--   t_annuity_application  ←  BusinessApplication
--   t_annuity_batch        ←  BusinessBatch
--   t_annuity_form         ←  BusinessForm
--
-- 设计要点：
--   1. BusinessContext / OperatorInfo 拍平为独立列，便于 SQL 查询和索引
--   2. businessExtension 使用 JSONB 存储多态值对象（AnnuityApplicationExtension）
--   3. 主键 id 由应用层通过 shared-id-starter 生成（ULID），禁用自增
--   4. 通用字段 created_by/created_time/updated_by/updated_time/deleted/version 齐备
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 年金业务申请单表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_annuity_application (
    id                      VARCHAR(64)   NOT NULL,
    batch_id                VARCHAR(64),
    form_id                 VARCHAR(64),

    -- BusinessContext 拍平字段
    business_type           VARCHAR(64),
    customer_no             VARCHAR(64),
    customer_name           VARCHAR(255),
    product_no              VARCHAR(64),
    product_name            VARCHAR(255),
    plan_no                 VARCHAR(64),
    plan_name               VARCHAR(255),
    operation_model         VARCHAR(64),
    account_manager         VARCHAR(64),

    -- OperatorInfo 拍平字段
    channel                 VARCHAR(64),
    operator_id             VARCHAR(64),
    operator_name           VARCHAR(255),
    is_proxy                BOOLEAN       DEFAULT FALSE,

    -- 文件与统计字段
    parsed_json_file_id     VARCHAR(64),
    expected_detail_count   INT           DEFAULT 0,

    -- 业务扩展字段（多态 JSON）
    business_extension      JSONB,

    -- 状态机字段
    status                  VARCHAR(32),
    current_step            VARCHAR(64),

    apply_time              TIMESTAMP,
    complete_time           TIMESTAMP,

    -- 通用字段
    created_by              VARCHAR(64)   NOT NULL,
    updated_by              VARCHAR(64),
    create_time             TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time             TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    deleted                 BOOLEAN       DEFAULT FALSE,
    version                 INT           DEFAULT 0,

    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_annuity_application_batch_id        ON t_annuity_application(batch_id);
CREATE INDEX IF NOT EXISTS idx_annuity_application_form_id         ON t_annuity_application(form_id);
CREATE INDEX IF NOT EXISTS idx_annuity_application_parsed_file_id  ON t_annuity_application(parsed_json_file_id);
CREATE INDEX IF NOT EXISTS idx_annuity_application_status          ON t_annuity_application(status);
CREATE INDEX IF NOT EXISTS idx_annuity_application_current_step    ON t_annuity_application(current_step);
CREATE INDEX IF NOT EXISTS idx_annuity_application_customer_no     ON t_annuity_application(customer_no);
CREATE INDEX IF NOT EXISTS idx_annuity_application_create_time     ON t_annuity_application(create_time);

COMMENT ON TABLE  t_annuity_application IS '年金业务申请单表';
COMMENT ON COLUMN t_annuity_application.id                    IS '申请单 ID（ApplicationId，ULID）';
COMMENT ON COLUMN t_annuity_application.batch_id              IS '关联批次 ID';
COMMENT ON COLUMN t_annuity_application.form_id               IS '关联表单 ID';
COMMENT ON COLUMN t_annuity_application.business_type         IS '业务类型: ACC_PLAN_CREATE/MODIFY/DELETE';
COMMENT ON COLUMN t_annuity_application.customer_no           IS '客户编号';
COMMENT ON COLUMN t_annuity_application.customer_name         IS '客户名称';
COMMENT ON COLUMN t_annuity_application.product_no            IS '产品编号';
COMMENT ON COLUMN t_annuity_application.product_name          IS '产品名称';
COMMENT ON COLUMN t_annuity_application.plan_no               IS '方案编号';
COMMENT ON COLUMN t_annuity_application.plan_name             IS '方案名称';
COMMENT ON COLUMN t_annuity_application.operation_model       IS '运作模式: Single_Trustee/Single_Account_Manager/Trustee_Account_Combined';
COMMENT ON COLUMN t_annuity_application.account_manager       IS '账户管理人代码（AccountManager.value）';
COMMENT ON COLUMN t_annuity_application.channel               IS '年金渠道: NETAPP/CJ_TELLER/WECHAT/REGIONAL_CENTER/BANK_BRANCH';
COMMENT ON COLUMN t_annuity_application.operator_id           IS '操作人编号（UserNo）';
COMMENT ON COLUMN t_annuity_application.operator_name         IS '操作人姓名';
COMMENT ON COLUMN t_annuity_application.is_proxy              IS '是否代办';
COMMENT ON COLUMN t_annuity_application.parsed_json_file_id   IS '解析后 JSON 文件 ID（FileId）';
COMMENT ON COLUMN t_annuity_application.expected_detail_count IS '预期明细数量';
COMMENT ON COLUMN t_annuity_application.business_extension    IS '业务扩展字段（JSONB 多态: AnnuityApplicationExtension）';
COMMENT ON COLUMN t_annuity_application.status                IS '申请单状态: DRAFT/PROCESSING/REJECTED/COMPLETED/CANCELLED/WITHDRAWN';
COMMENT ON COLUMN t_annuity_application.current_step          IS '当前流程步骤（ApplicationFlowStep 枚举名）';
COMMENT ON COLUMN t_annuity_application.apply_time            IS '申请时间';
COMMENT ON COLUMN t_annuity_application.complete_time         IS '完成时间';
COMMENT ON COLUMN t_annuity_application.created_by            IS '创建人';
COMMENT ON COLUMN t_annuity_application.updated_by            IS '更新人';
COMMENT ON COLUMN t_annuity_application.create_time           IS '创建时间';
COMMENT ON COLUMN t_annuity_application.update_time           IS '更新时间';
COMMENT ON COLUMN t_annuity_application.deleted               IS '逻辑删除标志';
COMMENT ON COLUMN t_annuity_application.version               IS '乐观锁版本号';

-- -----------------------------------------------------------------------------
-- 2. 年金业务批次表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_annuity_batch (
    id                      VARCHAR(64)   NOT NULL,

    -- BusinessContext 拍平字段
    business_type           VARCHAR(64),
    customer_no             VARCHAR(64),
    customer_name           VARCHAR(255),
    product_no              VARCHAR(64),
    product_name            VARCHAR(255),
    plan_no                 VARCHAR(64),
    plan_name               VARCHAR(255),
    operation_model         VARCHAR(64),
    account_manager         VARCHAR(64),

    -- OperatorInfo 拍平字段
    channel                 VARCHAR(64),
    operator_id             VARCHAR(64),
    operator_name           VARCHAR(255),
    is_proxy                BOOLEAN       DEFAULT FALSE,

    -- 批次状态字段
    status                  VARCHAR(32),
    total_application_count INT           DEFAULT 0,
    success_count           INT           DEFAULT 0,
    failed_count            INT           DEFAULT 0,

    -- 通用字段
    created_by              VARCHAR(64)   NOT NULL,
    updated_by              VARCHAR(64),
    create_time             TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time             TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    deleted                 BOOLEAN       DEFAULT FALSE,
    version                 INT           DEFAULT 0,

    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_annuity_batch_status         ON t_annuity_batch(status);
CREATE INDEX IF NOT EXISTS idx_annuity_batch_customer_no    ON t_annuity_batch(customer_no);
CREATE INDEX IF NOT EXISTS idx_annuity_batch_business_type  ON t_annuity_batch(business_type);
CREATE INDEX IF NOT EXISTS idx_annuity_batch_create_time    ON t_annuity_batch(create_time);

COMMENT ON TABLE  t_annuity_batch IS '年金业务批次表';
COMMENT ON COLUMN t_annuity_batch.id                       IS '批次 ID（BatchId）';
COMMENT ON COLUMN t_annuity_batch.business_type            IS '业务类型';
COMMENT ON COLUMN t_annuity_batch.customer_no              IS '客户编号';
COMMENT ON COLUMN t_annuity_batch.customer_name            IS '客户名称';
COMMENT ON COLUMN t_annuity_batch.product_no               IS '产品编号';
COMMENT ON COLUMN t_annuity_batch.product_name             IS '产品名称';
COMMENT ON COLUMN t_annuity_batch.plan_no                  IS '方案编号';
COMMENT ON COLUMN t_annuity_batch.plan_name                IS '方案名称';
COMMENT ON COLUMN t_annuity_batch.operation_model          IS '运作模式';
COMMENT ON COLUMN t_annuity_batch.account_manager          IS '账户管理人代码';
COMMENT ON COLUMN t_annuity_batch.channel                  IS '年金渠道';
COMMENT ON COLUMN t_annuity_batch.operator_id              IS '操作人编号';
COMMENT ON COLUMN t_annuity_batch.operator_name            IS '操作人姓名';
COMMENT ON COLUMN t_annuity_batch.is_proxy                 IS '是否代办';
COMMENT ON COLUMN t_annuity_batch.status                   IS '批次状态: CREATED/PROCESSING/PARTIAL_FAILED/FAILED/COMPLETED';
COMMENT ON COLUMN t_annuity_batch.total_application_count  IS '总申请单数量';
COMMENT ON COLUMN t_annuity_batch.success_count            IS '成功申请单数量';
COMMENT ON COLUMN t_annuity_batch.failed_count             IS '失败申请单数量';
COMMENT ON COLUMN t_annuity_batch.created_by               IS '创建人';
COMMENT ON COLUMN t_annuity_batch.updated_by               IS '更新人';
COMMENT ON COLUMN t_annuity_batch.create_time              IS '创建时间';
COMMENT ON COLUMN t_annuity_batch.update_time              IS '更新时间';
COMMENT ON COLUMN t_annuity_batch.deleted                  IS '逻辑删除标志';
COMMENT ON COLUMN t_annuity_batch.version                  IS '乐观锁版本号';

-- -----------------------------------------------------------------------------
-- 3. 年金业务表单表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_annuity_form (
    id                      VARCHAR(64)   NOT NULL,
    batch_id                VARCHAR(64),

    -- BusinessContext 拍平字段
    business_type           VARCHAR(64),
    customer_no             VARCHAR(64),
    customer_name           VARCHAR(255),
    product_no              VARCHAR(64),
    product_name            VARCHAR(255),
    plan_no                 VARCHAR(64),
    plan_name               VARCHAR(255),
    operation_model         VARCHAR(64),
    account_manager         VARCHAR(64),

    -- OperatorInfo 拍平字段
    channel                 VARCHAR(64),
    operator_id             VARCHAR(64),
    operator_name           VARCHAR(255),
    is_proxy                BOOLEAN       DEFAULT FALSE,

    -- 表单文件字段
    form_file_id            VARCHAR(64),
    form_file_name          VARCHAR(512),
    form_file_size          BIGINT,

    -- 表单状态字段
    form_status             VARCHAR(32),

    -- 通用字段
    created_by              VARCHAR(64)   NOT NULL,
    updated_by              VARCHAR(64),
    create_time             TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    update_time             TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    deleted                 BOOLEAN       DEFAULT FALSE,
    version                 INT           DEFAULT 0,

    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_annuity_form_batch_id      ON t_annuity_form(batch_id);
CREATE INDEX IF NOT EXISTS idx_annuity_form_status        ON t_annuity_form(form_status);
CREATE INDEX IF NOT EXISTS idx_annuity_form_customer_no   ON t_annuity_form(customer_no);
CREATE INDEX IF NOT EXISTS idx_annuity_form_create_time   ON t_annuity_form(create_time);

COMMENT ON TABLE  t_annuity_form IS '年金业务表单表';
COMMENT ON COLUMN t_annuity_form.id              IS '表单 ID（FormId，ULID）';
COMMENT ON COLUMN t_annuity_form.batch_id        IS '关联批次 ID';
COMMENT ON COLUMN t_annuity_form.business_type   IS '业务类型';
COMMENT ON COLUMN t_annuity_form.customer_no     IS '客户编号';
COMMENT ON COLUMN t_annuity_form.customer_name   IS '客户名称';
COMMENT ON COLUMN t_annuity_form.product_no      IS '产品编号';
COMMENT ON COLUMN t_annuity_form.product_name    IS '产品名称';
COMMENT ON COLUMN t_annuity_form.plan_no         IS '方案编号';
COMMENT ON COLUMN t_annuity_form.plan_name       IS '方案名称';
COMMENT ON COLUMN t_annuity_form.operation_model IS '运作模式';
COMMENT ON COLUMN t_annuity_form.account_manager IS '账户管理人代码';
COMMENT ON COLUMN t_annuity_form.channel         IS '年金渠道';
COMMENT ON COLUMN t_annuity_form.operator_id     IS '操作人编号';
COMMENT ON COLUMN t_annuity_form.operator_name   IS '操作人姓名';
COMMENT ON COLUMN t_annuity_form.is_proxy        IS '是否代办';
COMMENT ON COLUMN t_annuity_form.form_file_id    IS '表单文件 ID（FileId）';
COMMENT ON COLUMN t_annuity_form.form_file_name  IS '表单文件名';
COMMENT ON COLUMN t_annuity_form.form_file_size  IS '表单文件大小（字节）';
COMMENT ON COLUMN t_annuity_form.form_status     IS '表单状态: WAITING_UPLOAD/UPLOADING/UPLOADED/PARSING/VALIDATING/PARSED/DELETED';
COMMENT ON COLUMN t_annuity_form.created_by      IS '创建人';
COMMENT ON COLUMN t_annuity_form.updated_by      IS '更新人';
COMMENT ON COLUMN t_annuity_form.create_time     IS '创建时间';
COMMENT ON COLUMN t_annuity_form.update_time     IS '更新时间';
COMMENT ON COLUMN t_annuity_form.deleted         IS '逻辑删除标志';
COMMENT ON COLUMN t_annuity_form.version         IS '乐观锁版本号';
