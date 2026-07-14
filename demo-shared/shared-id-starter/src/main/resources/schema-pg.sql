SET search_path TO schema_demo;
show search_path;

-- 创建表
CREATE TABLE IF NOT EXISTS t_id_generator
(
  seq_key     VARCHAR(64) NOT NULL PRIMARY KEY,
  max_id      BIGINT      NOT NULL DEFAULT 0,
  step        INT         NOT NULL DEFAULT 100,
  create_time TIMESTAMPTZ,
  update_time TIMESTAMPTZ
);

-- 添加表注释（可选，原表无整体表注释，此处略）

-- 添加列注释
COMMENT ON COLUMN t_id_generator.seq_key IS '物理序列键';
COMMENT ON COLUMN t_id_generator.max_id IS '当前最大ID';
COMMENT ON COLUMN t_id_generator.step IS '步长';
COMMENT ON COLUMN t_id_generator.create_time IS '创建时间';
COMMENT ON COLUMN t_id_generator.update_time IS '更新时间';
