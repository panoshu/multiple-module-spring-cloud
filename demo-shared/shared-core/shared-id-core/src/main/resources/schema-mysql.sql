CREATE TABLE IF NOT EXISTS t_id_generator
(
  seq_key
  VARCHAR
(
  64
) NOT NULL PRIMARY KEY COMMENT '物理序列键',
  max_id BIGINT NOT NULL DEFAULT 0 COMMENT '当前最大ID',
  step INT NOT NULL DEFAULT 100 COMMENT '步长',
  create_time TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP COMMENT '更新时间'
  );
