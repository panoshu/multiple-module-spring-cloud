-- 审批流配置表
CREATE TABLE IF NOT EXISTS t_approval_flow
(
  id
  VARCHAR
(
  64
) NOT NULL,
  flow_name VARCHAR
(
  255
) NOT NULL,
  business_type VARCHAR
(
  64
) NOT NULL,
  match_rules TEXT NOT NULL,
  flow_version INT NOT NULL,
  status VARCHAR
(
  20
) NOT NULL,
  created_by VARCHAR
(
  64
) NOT NULL,
  updated_by VARCHAR
(
  64
),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted BOOLEAN DEFAULT FALSE,
  version INT DEFAULT 0,
  PRIMARY KEY
(
  id
)
  );

CREATE INDEX IF NOT EXISTS idx_approval_flow_business_type ON t_approval_flow(business_type);
CREATE INDEX IF NOT EXISTS idx_approval_flow_status ON t_approval_flow(status);
CREATE INDEX IF NOT EXISTS idx_approval_flow_create_time ON t_approval_flow(create_time);

COMMENT
ON TABLE t_approval_flow IS '审批流配置表';
COMMENT
ON COLUMN t_approval_flow.id IS '审批流ID';
COMMENT
ON COLUMN t_approval_flow.flow_name IS '审批流名称';
COMMENT
ON COLUMN t_approval_flow.business_type IS '业务类型';
COMMENT
ON COLUMN t_approval_flow.match_rules IS '匹配规则（JSON）';
COMMENT
ON COLUMN t_approval_flow.flow_version IS '版本号';
COMMENT
ON COLUMN t_approval_flow.status IS '状态：ACTIVE-有效，DEPRECATED-已废弃';
COMMENT
ON COLUMN t_approval_flow.created_by IS '创建人';
COMMENT
ON COLUMN t_approval_flow.updated_by IS '更新人';
COMMENT
ON COLUMN t_approval_flow.create_time IS '创建时间';
COMMENT
ON COLUMN t_approval_flow.update_time IS '更新时间';
COMMENT
ON COLUMN t_approval_flow.deleted IS '删除标记';
COMMENT
ON COLUMN t_approval_flow.version IS '乐观锁版本号';

-- 审批节点配置表
CREATE TABLE IF NOT EXISTS t_approval_node
(
  id
  VARCHAR
(
  64
) NOT NULL,
  flow_id VARCHAR
(
  64
) NOT NULL,
  node_order INT NOT NULL,
  node_type VARCHAR
(
  20
) NOT NULL,
  specified_plan_id VARCHAR
(
  64
),
  terminal_level INT,
  approver_type VARCHAR
(
  20
) NOT NULL,
  approver_ids TEXT,
  role_ids TEXT,
  sign_mode VARCHAR
(
  20
) NOT NULL,
  created_by VARCHAR
(
  64
) NOT NULL,
  updated_by VARCHAR
(
  64
),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted BOOLEAN DEFAULT FALSE,
  version INT DEFAULT 0,
  PRIMARY KEY
(
  id
)
  );

CREATE INDEX IF NOT EXISTS idx_approval_node_flow_id ON t_approval_node(flow_id);
CREATE INDEX IF NOT EXISTS idx_approval_node_order ON t_approval_node(node_order);

COMMENT
ON TABLE t_approval_node IS '审批节点配置表';

-- 审批实例表
CREATE TABLE IF NOT EXISTS t_approval_instance
(
  id
  VARCHAR
(
  64
) NOT NULL,
  flow_id VARCHAR
(
  64
) NOT NULL,
  flow_version INT NOT NULL,
  business_application_id VARCHAR
(
  64
) NOT NULL,
  business_type VARCHAR
(
  64
) NOT NULL,
  business_no VARCHAR
(
  128
) NOT NULL,
  current_node_order INT NOT NULL,
  status VARCHAR
(
  20
) NOT NULL,
  initiator_plan VARCHAR
(
  64
),
  current_plan VARCHAR
(
  64
),
  created_by VARCHAR
(
  64
) NOT NULL,
  updated_by VARCHAR
(
  64
),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted BOOLEAN DEFAULT FALSE,
  version INT DEFAULT 0,
  PRIMARY KEY
(
  id
)
  );

CREATE INDEX IF NOT EXISTS idx_approval_instance_flow_id ON t_approval_instance(flow_id);
CREATE INDEX IF NOT EXISTS idx_approval_instance_business_application_id ON t_approval_instance(business_application_id);
CREATE INDEX IF NOT EXISTS idx_approval_instance_business_no ON t_approval_instance(business_no);
CREATE INDEX IF NOT EXISTS idx_approval_instance_status ON t_approval_instance(status);
CREATE INDEX IF NOT EXISTS idx_approval_instance_create_time ON t_approval_instance(create_time);

COMMENT
ON TABLE t_approval_instance IS '审批实例表';

-- 节点执行记录表
CREATE TABLE IF NOT EXISTS t_approval_node_execution
(
  id
  VARCHAR
(
  64
) NOT NULL,
  instance_id VARCHAR
(
  64
) NOT NULL,
  node_id VARCHAR
(
  64
) NOT NULL,
  node_order INT NOT NULL,
  status VARCHAR
(
  20
) NOT NULL,
  started_at TIMESTAMP,
  completed_at TIMESTAMP,
  created_by VARCHAR
(
  64
) NOT NULL,
  updated_by VARCHAR
(
  64
),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted BOOLEAN DEFAULT FALSE,
  version INT DEFAULT 0,
  PRIMARY KEY
(
  id
)
  );

CREATE INDEX IF NOT EXISTS idx_approval_node_execution_instance_id ON t_approval_node_execution(instance_id);
CREATE INDEX IF NOT EXISTS idx_approval_node_execution_node_id ON t_approval_node_execution(node_id);
CREATE INDEX IF NOT EXISTS idx_approval_node_execution_status ON t_approval_node_execution(status);

COMMENT
ON TABLE t_approval_node_execution IS '节点执行记录表';

-- 审批记录表
CREATE TABLE IF NOT EXISTS t_approval_record
(
  id
  VARCHAR
(
  64
) NOT NULL,
  execution_id VARCHAR
(
  64
) NOT NULL,
  approver_id VARCHAR
(
  64
) NOT NULL,
  action VARCHAR
(
  20
) NOT NULL,
  opinion VARCHAR
(
  500
),
  reject_target TEXT,
  transfer_to VARCHAR
(
  64
),
  operated_at TIMESTAMP NOT NULL,
  created_by VARCHAR
(
  64
) NOT NULL,
  updated_by VARCHAR
(
  64
),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted BOOLEAN DEFAULT FALSE,
  version INT DEFAULT 0,
  PRIMARY KEY
(
  id
)
  );

CREATE INDEX IF NOT EXISTS idx_approval_record_execution_id ON t_approval_record(execution_id);
CREATE INDEX IF NOT EXISTS idx_approval_record_approver_id ON t_approval_record(approver_id);
CREATE INDEX IF NOT EXISTS idx_approval_record_action ON t_approval_record(action);
CREATE INDEX IF NOT EXISTS idx_approval_record_operated_at ON t_approval_record(operated_at);

COMMENT
ON TABLE t_approval_record IS '审批记录表';
