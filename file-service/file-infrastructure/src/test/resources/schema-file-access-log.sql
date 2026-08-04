-- H2 兼容 DDL，供 FileAccessLogRepositoryImplTest 初始化内存数据库使用
-- 与 schema-pg.sql 的 t_file_access_log 表结构基本一致，但有两点 H2 适配：
--   1. 去除 PostgreSQL 专属语法（COMMENT/部分索引）
--   2. `deleted` 列从 BOOLEAN 改为 INT —— MyBatis-Flex 的 @Column(isLogicDelete = true)
--      默认用 0/1 进行逻辑删除比较，PostgreSQL 可 BOOLEAN=INT 隐式转换，
--      但 H2 即便在 MODE=PostgreSQL 下也会抛 "BOOLEAN and INTEGER are not comparable"。

DROP TABLE IF EXISTS t_file_access_log;

CREATE TABLE t_file_access_log
(
  id          VARCHAR(64)  NOT NULL,
  file_id     VARCHAR(64)  NOT NULL,
  action      VARCHAR(20)  NOT NULL,
  usage       VARCHAR(20)  NOT NULL,
  customer_no VARCHAR(64)  NOT NULL,
  product_no  VARCHAR(64)  NOT NULL,
  operator    VARCHAR(64)  NOT NULL,
  source_app  VARCHAR(64),
  source_ip   VARCHAR(64),
  token_hash  VARCHAR(128) NOT NULL,
  result      VARCHAR(20)  NOT NULL,
  fail_reason VARCHAR(512),
  occur_at    TIMESTAMP    NOT NULL,
  created_by  VARCHAR(64)  NOT NULL,
  updated_by  VARCHAR(64),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted     INT       DEFAULT 0,
  version     INT       DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE INDEX idx_access_log_file_id ON t_file_access_log (file_id);
CREATE INDEX idx_access_log_token_hash ON t_file_access_log (token_hash);
CREATE INDEX idx_access_log_action_time ON t_file_access_log (action, occur_at);
CREATE INDEX idx_access_log_customer_product ON t_file_access_log (customer_no, product_no, occur_at);
