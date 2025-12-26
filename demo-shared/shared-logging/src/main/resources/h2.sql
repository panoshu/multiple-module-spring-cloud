CREATE TABLE IF NOT EXISTS http_exchange_log (
  -- 基础信息
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  correlation_id VARCHAR(64) NOT NULL,
  created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  -- 时间轴
  request_time TIMESTAMP,
  response_time TIMESTAMP,
  duration_millis BIGINT,

  -- 请求信息
  method VARCHAR(10) NOT NULL,
  uri VARCHAR(2048) NOT NULL,
  remote VARCHAR(45),
  request_headers CLOB,
  request_content CLOB,
  content_type VARCHAR(100),

  -- 响应信息
  status_code INTEGER,
  response_headers CLOB,
  response_content CLOB,

  -- 客户端信息
  client_info VARCHAR(500),
  ip VARCHAR(45),
  user_agent VARCHAR(500),

  -- 状态标记
  truncated BOOLEAN NOT NULL DEFAULT FALSE,
  complete BOOLEAN NOT NULL DEFAULT FALSE
);

-- 单独创建索引（H2兼容版本）
CREATE INDEX IF NOT EXISTS idx_correlation_id ON http_exchange_log(correlation_id);
CREATE INDEX IF NOT EXISTS idx_created_time ON http_exchange_log(created_time);
CREATE INDEX IF NOT EXISTS idx_request_time ON http_exchange_log(request_time);
-- H2不支持前缀索引语法 uri(255)，改为完整索引
CREATE INDEX IF NOT EXISTS idx_uri ON http_exchange_log(uri);
CREATE INDEX IF NOT EXISTS idx_status_code ON http_exchange_log(status_code);
