-- ============================================================================
-- 本地全链路验证种子数据（2026-11-16）
-- 适用：PostgreSQL schema_demo；服务以 local profile + permission.enforcement=false 启动
-- 幂等：可重复执行（固定主键 + ON CONFLICT）
-- 登录密码统一为 Test123456（BCrypt 哈希见下）
-- ============================================================================
BEGIN;

-- ----------------------------------------------------------------------------
-- 1. 登录账号：经办人 U1001、审批人 U-APPROVER
-- ----------------------------------------------------------------------------
INSERT INTO t_auth_user
  (id, user_type, identity_type, identity_number, mobile, status,
   created_by, create_time, deleted, version)
VALUES
  ('U1001', 'AGENT', 'ID_CARD', '110101199001011234', '+8613800000001', 'ACTIVE',
   'seed', now(), false, 0),
  ('U-APPROVER', 'AGENT', 'ID_CARD', '110101199001019999', '+8613800000002', 'ACTIVE',
   'seed', now(), false, 0)
ON CONFLICT (id) DO UPDATE
  SET status = 'ACTIVE', deleted = false, update_time = now();

-- ----------------------------------------------------------------------------
-- 2. 密码凭证（Test123456）
-- ----------------------------------------------------------------------------
INSERT INTO t_auth_credential
  (id, credential_type, owner_type, owner_id, applicable_channels,
   validity_start, validity_end, status, user_no, password_hash,
   created_by, create_time, deleted, version)
VALUES
  ('cred-demo-0001', 'PASSWORD', 'UserCredentialOwner', 'U1001', '["NETAPP"]'::jsonb,
   now(), now() + interval '180 days', 'ACTIVE', 'U1001',
   '$2a$10$s4uAMR8fsGb61g2J.hX3zOcPIPsqo8OAIzGBivm682eawpeYGd226',
   'seed', now(), false, 0),
  ('cred-demo-approver', 'PASSWORD', 'UserCredentialOwner', 'U-APPROVER', '["NETAPP"]'::jsonb,
   now(), now() + interval '180 days', 'ACTIVE', 'U-APPROVER',
   '$2a$10$s4uAMR8fsGb61g2J.hX3zOcPIPsqo8OAIzGBivm682eawpeYGd226',
   'seed', now(), false, 0)
ON CONFLICT (id) DO UPDATE
  SET password_hash = EXCLUDED.password_hash,
      applicable_channels = EXCLUDED.applicable_channels,
      status = 'ACTIVE', deleted = false, update_time = now();

-- ----------------------------------------------------------------------------
-- 3. 计划权限：两个账号均可触达 P-1（PlanSnapshot 由 auth local 配置提供）
-- ----------------------------------------------------------------------------
INSERT INTO t_auth_assignment
  (id, user_no, role_code, scope_dimension, scope_value, inheritable,
   status, created_by, create_time, deleted, version)
VALUES
  ('asg-e2e-0001', 'U1001', 'AGENT_REVIEW', 'PLAN', 'P-1', false,
   'ACTIVE', 'seed', now(), false, 0),
  ('asg-e2e-approver', 'U-APPROVER', 'AGENT_REVIEW', 'PLAN', 'P-1', false,
   'ACTIVE', 'seed', now(), false, 0)
ON CONFLICT (id) DO UPDATE
  SET status = 'ACTIVE', deleted = false, update_time = now();

-- ----------------------------------------------------------------------------
-- 4. 客户渠道开通：P-1 所属客户 C-1 开通网上渠道
--    —— selectable-plans 会按此过滤；缺失时返回空列表
-- ----------------------------------------------------------------------------
INSERT INTO t_auth_customer_channel_entitlement
  (id, customer_no, enabled_channels, created_by, create_time, deleted, version)
VALUES
  ('ent-demo-c1', 'C-1', '["NETAPP"]'::jsonb, 'seed', now(), false, 0)
ON CONFLICT (id) DO UPDATE
  SET enabled_channels = EXCLUDED.enabled_channels,
      deleted = false, update_time = now();

-- ----------------------------------------------------------------------------
-- 5. 审批流：ACC_PLAN_CREATE，指定审批人 U-APPROVER
--    match_rules.accountManager 必须与创建批次时的 accountManager 一致（CJP）
-- ----------------------------------------------------------------------------
INSERT INTO t_approval_flow
  (id, flow_name, business_type, match_rules, flow_version, status,
   created_by, updated_by, create_time, update_time, deleted, version)
VALUES
  ('99000000000000001', '本地验证-年金新建计划审批流', 'ACC_PLAN_CREATE',
   '{"productNo":null,"customerNo":null,"accountManager":"CJP","operationMode":null,"businessType":"ACC_PLAN_CREATE","annuityChannel":null}',
   1, 'ACTIVE', 'SYSTEM', 'SYSTEM', now(), now(), false, 0)
ON CONFLICT (id) DO UPDATE
  SET flow_name = EXCLUDED.flow_name,
      business_type = EXCLUDED.business_type,
      match_rules = EXCLUDED.match_rules,
      status = 'ACTIVE', deleted = false, update_time = now();

INSERT INTO t_approval_node
  (id, flow_id, node_order, node_type, approver_type, approver_ids,
   sign_mode, created_by, updated_by, create_time, update_time, deleted, version)
VALUES
  ('99000000000000002', '99000000000000001', 1, 'SAME_PLAN', 'SPECIFIED_USER',
   '["U-APPROVER"]', 'OR_SIGN', 'SYSTEM', 'SYSTEM', now(), now(), false, 0)
ON CONFLICT (id) DO UPDATE
  SET flow_id = EXCLUDED.flow_id,
      approver_ids = EXCLUDED.approver_ids,
      deleted = false, update_time = now();

COMMIT;

-- ============================================================================
-- 文件模板无需手工插入：
--   file-service local profile 的 file.demo.template-seed.enabled=true 会幂等播种
--   FORM_DETAIL 模板（t_file_template_config，biz_type=FORM_DETAIL）。
-- 验证：
--   SELECT id, biz_type, template_version, status
--   FROM t_file_template_config WHERE biz_type = 'FORM_DETAIL' AND status = 'ACTIVE';
-- ============================================================================
