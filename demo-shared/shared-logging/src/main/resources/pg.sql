-- PostgreSQL DDL
SELECT current_database();
SET search_path TO engine;
-- 启用 UUID 扩展（可选，如果 correlation_id 是 UUID 类型）
-- CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS http_exchange_log (
  -- 核心关联ID，必须唯一以支持 ON CONFLICT
  correlation_id VARCHAR(64) NOT NULL PRIMARY KEY,
  -- 时间字段，Postgres 推荐使用 TIMESTAMPTZ
  created_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  request_time TIMESTAMPTZ,
  response_time TIMESTAMPTZ,
  duration_millis BIGINT,
  -- 请求信息
  method VARCHAR(10),
  uri TEXT,           -- Postgres TEXT 性能优异，无长度限制烦恼
  remote VARCHAR(45),
  -- 【关键】使用 JSONB 存储结构化数据
  request_headers JSONB,
  request_content JSONB,
  content_type VARCHAR(100),
  -- 响应信息
  status_code INTEGER,
  response_headers JSONB,
  response_content JSONB,
  -- 客户端信息
  client_info TEXT,
  ip VARCHAR(45),
  user_agent TEXT,
  -- 状态
  truncated BOOLEAN DEFAULT FALSE,
  complete BOOLEAN DEFAULT FALSE,
  -- 约束
  CONSTRAINT uq_correlation_id UNIQUE (correlation_id)
  );

-- 索引优化
-- 1. 基础查询索引
CREATE INDEX IF NOT EXISTS idx_created_time ON http_exchange_log(created_time);
CREATE INDEX IF NOT EXISTS idx_request_time ON http_exchange_log(request_time);

-- 2. JSONB GIN 索引 (Postgres 的杀手锏)
-- 允许你高效查询：WHERE request_headers @> '{"Content-Type": "application/json"}'
CREATE INDEX IF NOT EXISTS idx_request_headers_gin ON http_exchange_log USING GIN (request_headers);
CREATE INDEX IF NOT EXISTS idx_response_headers_gin ON http_exchange_log USING GIN (response_headers);

-- 如果你需要经常查询请求体里的内容，也可以给 content 加索引
-- CREATE INDEX IF NOT EXISTS idx_request_content_gin ON http_exchange_log USING GIN (request_content);
