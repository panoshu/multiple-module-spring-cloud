SET search_path TO schema_demo;
show search_path;

CREATE TABLE IF NOT EXISTS t_loan_application
(
  id                VARCHAR(64)    NOT NULL,
  applicant_id_card VARCHAR(32)    NOT NULL,
  amount            DECIMAL(15, 2) NOT NULL,
  status            VARCHAR(20)    NOT NULL,
  reject_reason     VARCHAR(255),
  version           BIGINT,
  create_time       TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  update_time       TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

COMMENT ON TABLE t_loan_application IS '贷款申请单';
COMMENT ON COLUMN t_loan_application.id IS '申请单ID';
COMMENT ON COLUMN t_loan_application.applicant_id_card IS '申请人身份证';
COMMENT ON COLUMN t_loan_application.amount IS '申请金额';
COMMENT ON COLUMN t_loan_application.status IS '状态';
COMMENT ON COLUMN t_loan_application.reject_reason IS '拒绝原因';
COMMENT ON COLUMN t_loan_application.version IS '版本';
COMMENT ON COLUMN t_loan_application.create_time IS '创建时间';
COMMENT ON COLUMN t_loan_application.update_time IS '更新时间';
