CREATE TABLE IF NOT EXISTS http_exchange_log (
  -- 基础信息
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  correlation_id UUID NOT NULL,
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
  complete BOOLEAN NOT NULL DEFAULT FALSE,

  -- 索引优化
  INDEX idx_correlation_id (correlation_id),
  INDEX idx_created_time (created_time),
  INDEX idx_request_time (request_time),
  INDEX idx_uri (uri(255)),  -- H2 支持前缀索引
  INDEX idx_status_code (status_code)
);
