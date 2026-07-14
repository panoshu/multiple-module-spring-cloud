-- 审批流配置表
CREATE TABLE IF NOT EXISTS `t_approval_flow` (
  `id` VARCHAR(64) NOT NULL COMMENT '审批流ID',
  `flow_name` VARCHAR(255) NOT NULL COMMENT '审批流名称',
  `business_type` VARCHAR(64) NOT NULL COMMENT '业务类型',
  `match_rules` TEXT NOT NULL COMMENT '匹配规则（JSON）',
  `flow_version` INT NOT NULL COMMENT '版本号',
  `status` VARCHAR(20) NOT NULL COMMENT '状态：ACTIVE-有效，DEPRECATED-已废弃',
  `created_by` VARCHAR(64) NOT NULL COMMENT '创建人',
  `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BOOLEAN DEFAULT FALSE COMMENT '删除标记',
  `version` INT DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_business_type` (`business_type`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流配置表';

-- 审批节点配置表
CREATE TABLE IF NOT EXISTS `t_approval_node` (
  `id` VARCHAR(64) NOT NULL COMMENT '节点ID',
  `flow_id` VARCHAR(64) NOT NULL COMMENT '审批流ID',
  `node_order` INT NOT NULL COMMENT '节点顺序',
  `node_type` VARCHAR(20) NOT NULL COMMENT '节点类型：SPECIFIED_PLAN-指定方案，SAME_PLAN-同方案，LEVEL_UP-上一级',
  `specified_plan_id` VARCHAR(64) DEFAULT NULL COMMENT '指定方案ID',
  `terminal_level` INT DEFAULT NULL COMMENT '终止级别',
  `approver_type` VARCHAR(20) NOT NULL COMMENT '审批人类型：SPECIFIED_USER-指定用户，SPECIFIED_ROLE-指定角色',
  `approver_ids` TEXT DEFAULT NULL COMMENT '审批人ID列表（JSON）',
  `role_ids` TEXT DEFAULT NULL COMMENT '角色ID列表（JSON）',
  `sign_mode` VARCHAR(20) NOT NULL COMMENT '签批模式：OR_SIGN-或签，AND_SIGN-会签',
  `created_by` VARCHAR(64) NOT NULL COMMENT '创建人',
  `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BOOLEAN DEFAULT FALSE COMMENT '删除标记',
  `version` INT DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_flow_id` (`flow_id`),
  KEY `idx_node_order` (`node_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批节点配置表';

-- 审批实例表
CREATE TABLE IF NOT EXISTS `t_approval_instance` (
  `id` VARCHAR(64) NOT NULL COMMENT '审批实例ID',
  `flow_id` VARCHAR(64) NOT NULL COMMENT '审批流ID',
  `flow_version` INT NOT NULL COMMENT '审批流版本号',
  `business_application_id` VARCHAR(64) NOT NULL COMMENT '业务申请ID',
  `business_type` VARCHAR(64) NOT NULL COMMENT '业务类型',
  `business_no` VARCHAR(128) NOT NULL COMMENT '业务单号',
  `current_node_order` INT NOT NULL COMMENT '当前节点顺序',
  `status` VARCHAR(20) NOT NULL COMMENT '实例状态：PENDING-待审批，APPROVING-审批中，APPROVED-已通过，REJECTED-已拒绝，WITHDRAWN-已撤回',
  `initiator_plan` VARCHAR(64) DEFAULT NULL COMMENT '发起人方案',
  `current_plan` VARCHAR(64) DEFAULT NULL COMMENT '当前方案',
  `created_by` VARCHAR(64) NOT NULL COMMENT '创建人',
  `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BOOLEAN DEFAULT FALSE COMMENT '删除标记',
  `version` INT DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_flow_id` (`flow_id`),
  KEY `idx_business_application_id` (`business_application_id`),
  KEY `idx_business_no` (`business_no`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批实例表';

-- 节点执行记录表
CREATE TABLE IF NOT EXISTS `t_approval_node_execution` (
  `id` VARCHAR(64) NOT NULL COMMENT '执行ID',
  `instance_id` VARCHAR(64) NOT NULL COMMENT '审批实例ID',
  `node_id` VARCHAR(64) NOT NULL COMMENT '节点ID',
  `node_order` INT NOT NULL COMMENT '节点顺序',
  `status` VARCHAR(20) NOT NULL COMMENT '执行状态：PENDING-待执行，APPROVED-已通过，REJECTED-已拒绝，SKIPPED-已跳过',
  `started_at` TIMESTAMP DEFAULT NULL COMMENT '开始时间',
  `completed_at` TIMESTAMP DEFAULT NULL COMMENT '完成时间',
  `created_by` VARCHAR(64) NOT NULL COMMENT '创建人',
  `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BOOLEAN DEFAULT FALSE COMMENT '删除标记',
  `version` INT DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_instance_id` (`instance_id`),
  KEY `idx_node_id` (`node_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='节点执行记录表';

-- 审批记录表
CREATE TABLE IF NOT EXISTS `t_approval_record` (
  `id` VARCHAR(64) NOT NULL COMMENT '审批记录ID',
  `execution_id` VARCHAR(64) NOT NULL COMMENT '节点执行ID',
  `approver_id` VARCHAR(64) NOT NULL COMMENT '审批人ID',
  `action` VARCHAR(20) NOT NULL COMMENT '审批动作：APPROVE-通过，REJECT-拒绝，TRANSFER-转交',
  `opinion` VARCHAR(500) DEFAULT NULL COMMENT '审批意见',
  `reject_target` TEXT DEFAULT NULL COMMENT '驳回目标（JSON）',
  `transfer_to` VARCHAR(64) DEFAULT NULL COMMENT '转交目标用户',
  `operated_at` TIMESTAMP NOT NULL COMMENT '操作时间',
  `created_by` VARCHAR(64) NOT NULL COMMENT '创建人',
  `updated_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  `create_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` BOOLEAN DEFAULT FALSE COMMENT '删除标记',
  `version` INT DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_execution_id` (`execution_id`),
  KEY `idx_approver_id` (`approver_id`),
  KEY `idx_action` (`action`),
  KEY `idx_operated_at` (`operated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批记录表';