-- 事件存储表（MySQL 版本，用 TEXT/JSON 替代 JSONB）
CREATE TABLE IF NOT EXISTS sys_event_store
(
  event_id
  VARCHAR
(
  64
) PRIMARY KEY,
  event_type VARCHAR
(
  255
) NOT NULL,
  integration_type VARCHAR
(
  64
),
  occurred_on DATETIME NOT NULL,
  domain_payload JSON NOT NULL,
  integration_payload JSON,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
  );

CREATE TABLE IF NOT EXISTS sys_event_dispatch_log
(
  id
  BIGINT
  AUTO_INCREMENT
  PRIMARY
  KEY,
  event_id
  VARCHAR
(
  64
) NOT NULL,
  channel VARCHAR
(
  100
) NOT NULL,
  status VARCHAR
(
  20
) NOT NULL,
  error_msg TEXT,
  retry_count INT DEFAULT 0,
  next_retry_at DATETIME,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_event_channel UNIQUE
(
  event_id,
  channel
)
  );

CREATE INDEX idx_dispatch_status_retry ON sys_event_dispatch_log (status, next_retry_at);
