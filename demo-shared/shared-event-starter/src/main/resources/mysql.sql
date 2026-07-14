-- 事件存储表
CREATE TABLE sys_event_store
(
  event_id    VARCHAR(64) PRIMARY KEY,
  event_type  VARCHAR(255) NOT NULL,
  occurred_on DATETIME(3)  NOT NULL,
  payload     LONGTEXT     NOT NULL, -- MySQL通常存Text，若版本支持也可设为JSON
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 事件分发日志表
CREATE TABLE sys_event_dispatch_log
(
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_id      VARCHAR(64)  NOT NULL,
  channel       VARCHAR(100) NOT NULL,
  status        VARCHAR(20)  NOT NULL,
  error_msg     TEXT,
  retry_count   INT      DEFAULT 0,
  next_retry_at DATETIME,
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_event_channel (event_id, channel),
  KEY           idx_status_retry(status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
