-- 核心序列表：基于“业务+日期+客户”三个维度
CREATE TABLE t_cust_seq_conf (
  -- 主键设计：BIZ:20231122:CUST888
  -- 利用唯一主键约束，天然解决并发下的初始化问题
  seq_key VARCHAR(64) NOT NULL COMMENT '联合主键',
  current_max_id BIGINT NOT NULL DEFAULT 0 COMMENT '当前已分配的最大序列值',
  step INT NOT NULL DEFAULT 20 COMMENT '步长：客户维度建议设小，如20',
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (seq_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户维度流水号配置表';
