-- 事件存储表：不可变，记录业务发生的原始事实
SET search_path TO schema_demo;
show search_path;

CREATE TABLE IF NOT EXISTS sys_event_store
(
  event_id    VARCHAR(64) PRIMARY KEY,
  event_type  VARCHAR(255) NOT NULL,
  occurred_on TIMESTAMP    NOT NULL,
  payload     JSONB        NOT NULL, -- PG使用JSONB获得更好的性能和查询能力
  created_at  TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 事件分发日志表：可变，记录每个通道的发送状态（Outbox模式的核心）
CREATE TABLE sys_event_dispatch_log
(
  id            BIGSERIAL PRIMARY KEY,
  event_id      VARCHAR(64)  NOT NULL,
  channel       VARCHAR(100) NOT NULL,
  status        VARCHAR(20)  NOT NULL, -- PENDING, SUCCESS, FAILED
  error_msg     TEXT,
  retry_count   INT       DEFAULT 0,
  next_retry_at TIMESTAMP,
  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_event_channel UNIQUE (event_id, channel)
);

CREATE INDEX idx_dispatch_status_retry ON sys_event_dispatch_log (status, next_retry_at);
