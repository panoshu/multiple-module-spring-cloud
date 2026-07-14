CREATE TABLE IF NOT EXISTS ` t_loan_application `
(
  `
  id
  `
  VARCHAR
(
  64
) NOT NULL COMMENT '申请单ID',
  ` applicant_id_card ` VARCHAR
(
  32
) NOT NULL COMMENT '申请人身份证',
  ` amount ` DECIMAL
(
  15,
  2
) NOT NULL COMMENT '申请金额',
  ` status ` VARCHAR
(
  20
) NOT NULL COMMENT '状态',
  ` reject_reason ` VARCHAR
(
  255
) DEFAULT NULL COMMENT '拒绝原因',
  ` create_time ` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  ` update_time ` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间', -- 已移除 ON UPDATE CURRENT_TIMESTAMP
  PRIMARY KEY
(
  `
  id
  `
)
  ) DEFAULT CHARSET=utf8mb4 COMMENT ='贷款申请单';
