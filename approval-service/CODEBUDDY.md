# approval-service 模块文档

## 一句话职责

审批流配置与实例执行引擎：管理多版本审批流模板的匹配规则与节点链，驱动审批实例的启动、签批（或签/会签）、转交、驳回和撤回，通过领域事件与 business-core-kernel 联动。

## 入口清单

| 入口 | 类型 | 位置 | 说明 |
|------|------|------|------|
| `ApprovalFlowApi` → `ApprovalFlowAdapter` | HTTP | `approval-api/.../ApprovalFlowApi.java:12` | 审批流 CRUD：创建/更新/废弃/查询/列表/匹配 |
| `ApprovalInstanceApi` → `ApprovalInstanceAdapter` | HTTP | `approval-api/.../ApprovalInstanceApi.java:11` | 审批实例操作：发起/通过/拒绝/转交/撤回/查询/待办/历史 |
| `ApprovalInstanceCreatedEventConverter` | 集成事件 | `approval-infrastructure/.../event/ApprovalInstanceCreatedEventConverter.java` | 领域事件→集成事件：实例创建 |
| `ApprovalInstanceApprovedEventConverter` | 集成事件 | `approval-infrastructure/.../event/ApprovalInstanceApprovedEventConverter.java` | 领域事件→集成事件：实例通过 |
| `ApprovalInstanceRejectedEventConverter` | 集成事件 | `approval-infrastructure/.../event/ApprovalInstanceRejectedEventConverter.java` | 领域事件→集成事件：实例拒绝 |
| `ApprovalInstanceWithdrawnEventConverter` | 集成事件 | `approval-infrastructure/.../event/ApprovalInstanceWithdrawnEventConverter.java` | 领域事件→集成事件：实例撤回 |

## 职责清单

1. **审批流模板管理**：创建/更新/废弃审批流，维护多版本匹配规则（产品编号、客户编号、客户经理、运营模式、业务类型、年金渠道）与有序审批节点链
2. **审批流匹配**：根据业务上下文在所有 ACTIVE 审批流中逐字段匹配，返回首个命中的审批流
3. **审批实例发起**：加载匹配的审批流 → 创建实例 → `instance.start()` → 保存 + 发布 `ApprovalInstanceCreated` 事件
4. **审批通过**：加载实例+审批流 → `instance.approve()` → 根据签批模式（OR_SIGN 直接通过 / AND_SIGN 需全员）→ 推进下一节点或标记 APPROVED → 保存 + 发布事件
5. **审批拒绝**：`instance.reject()` → 按 RejectTarget 处理（TERMINATE 终止 / INITIATOR 回发起人 / NODE 回指定节点）→ 保存 + 发布事件
6. **审批转交**：`instance.transfer()` → 记录转交审批记录，不改变节点状态，等待被转交人审批
7. **审批撤回**：`instance.withdraw()` → 仅发起人可操作 → 状态置 WITHDRAWN → 发布事件
8. **审批人解析**：根据节点类型（SPECIFIED_PLAN / SAME_PLAN / LEVEL_UP）和审批人类型（SPECIFIED_USER / SPECIFIED_ROLE），通过 Gateway 解析实际审批人列表
9. **签批模式判定**：OR_SIGN 一人通过即完成 / AND_SIGN 全员通过才完成（角色节点保守返回 false）
10. **领域事件发布**：Repository.save() 中统一发布聚合根注册的领域事件，转换为集成事件后投递 MQ

## 主流程

### 流程1：审批实例发起

```
ApprovalInstanceAdapter.startApproval
  └─ ApprovalInstanceService.startApproval
       ├─ ApprovalFlowService.matchApprovalFlow → ApprovalFlowMatcher.match
       │    └─ 遍历 ACTIVE 审批流，逐字段匹配 MatchRules
       ├─ ApprovalInstance.create(flowId, flowVersion, businessAppId, businessType, initiatorPlan, operator)
       │    └─ registerDomainEvent(ApprovalInstanceCreated)
       ├─ ApprovalInstanceRepository.save(instance)
       │    ├─ 插入/更新 t_approval_instance
       │    ├─ 保存 nodeExecutions + approvalRecords
       │    └─ publishDomainEvents → eventPublisher → MQ
       └─ return instanceId
```

### 流程2：审批通过

```
ApprovalInstanceAdapter.approve
  └─ ApprovalInstanceService.approve
       ├─ load instance + flow
       ├─ flow.getNode(currentNodeOrder) → ApprovalNode
       ├─ instance.approve(node, approverId, opinion, operator)
       │    ├─ validateCanApprove(approverId)
       │    ├─ ApprovalRecord.approve(recordId, approverId, opinion, operator)
       │    ├─ getOrCreateCurrentExecution(node, operator)
       │    │    └─ NodeExecution.create(executionId, nodeId, nodeOrder, operator)
       │    ├─ execution.addApprovalRecord(record, operator)
       │    ├─ if node.isOrSign() || allApproversApproved:
       │    │    ├─ execution.markApproved(operator)
       │    │    └─ moveToNextNode(node, operator)
       │    │         ├─ currentNodeOrder = currentNodeOrder.next()
       │    │         └─ if 超出最大节点: status = APPROVED
       │    └─ if status == APPROVED:
       │         └─ registerDomainEvent(ApprovalInstanceApproved)
       ├─ repository.save(instance) → publish events
       └─ return
```

### 流程3：审批拒绝

```
ApprovalInstanceAdapter.reject
  └─ ApprovalInstanceService.reject
       ├─ load instance + flow
       ├─ flow.getNode(currentNodeOrder) → ApprovalNode
       ├─ instance.reject(node, approverId, opinion, rejectTarget, operator)
       │    ├─ validateCanApprove(approverId)
       │    ├─ ApprovalRecord.reject(recordId, approverId, opinion, rejectTarget, operator)
       │    ├─ getOrCreateCurrentExecution → execution.addApprovalRecord
       │    ├─ execution.markRejected(operator)
       │    └─ handleRejection(rejectTarget, operator)
       │         ├─ TERMINATE → status = REJECTED
       │         ├─ INITIATOR → currentNodeOrder = first, status = PENDING
       │         └─ NODE → currentNodeOrder = target, status = APPROVING
       │    └─ if status == REJECTED:
       │         └─ registerDomainEvent(ApprovalInstanceRejected)
       ├─ repository.save(instance) → publish events
       └─ return
```

## 数据与依赖

### 数据库表（PostgreSQL）

| 表名 | 用途 | 关键索引 |
|------|------|----------|
| `t_approval_flow` | 审批流配置（含 JSON 匹配规则） | idx_business_type, idx_status, idx_create_time |
| `t_approval_node` | 审批节点配置（所属审批流、顺序、类型、审批人/角色、签批模式） | idx_flow_id, idx_node_order |
| `t_approval_instance` | 审批实例（关联审批流、业务申请、当前节点、状态） | idx_flow_id, idx_business_application_id, idx_business_no, idx_status |
| `t_approval_node_execution` | 节点执行记录（关联实例、节点、状态、时间） | idx_instance_id, idx_node_id, idx_status |
| `t_approval_record` | 审批操作记录（关联执行记录、审批人、动作、意见） | idx_execution_id, idx_approver_id, idx_action |

### 外部依赖

| 依赖 | 用途 | 接入方式 |
|------|------|----------|
| auth-service（未来） | 角色用户查询、计划层级查询 | PlanHierarchyGateway / RoleUserGateway（当前伪实现） |
| business-core-kernel | 审批结果事件驱动业务流推进 | ApprovalResultEventListener 监听集成事件 |

## 历史痕迹

| 痕迹 | 位置 | 说明 |
|------|------|------|
| ID 生成用 `System.currentTimeMillis()` | `ApprovalFlowService:66,93`, `ApprovalInstanceService:89,144,170` | ApprovalFlowId、ApprovalInstanceId、RecordId、ExecutionId 均用时间戳生成，并发场景有碰撞风险 |
| 两个应用服务方法未实现 | `ApprovalInstanceService.listMyPendingApprovals:246` → `UnsupportedOperationException`, `getApprovalHistory:279` → 空列表 | 待办审批列表和审批历史查询为 TODO |
| 审批流匹配全表扫描 | `ApprovalFlowService.matchApprovalFlow:194` → `findByStatus(ACTIVE)` | 先加载全部 ACTIVE 流再逐条匹配，无数据库层筛选 |
| `findActiveByMatchRules` 未精确实现 | `ApprovalFlowRepositoryImpl:224-241` | 注释 TODO，当前返回全部 ACTIVE 流 |
| `findByApproverId` 未实现 | `ApprovalInstanceRepositoryImpl:253-263` | warn 日志 + 空列表，待办审批查询无法工作 |
| `ApprovalNodeResolver` 多处 TODO | `ApprovalNodeResolver.java:62,71` | SAME_PLAN 和 LEVEL_UP 节点类型解析回退到 `node.approverIds()`，实际需要调用 Gateway |
| Gateway 全部伪实现 | `RoleUserGatewayImpl`, `PlanHierarchyGatewayImpl` | 所有方法返回空，标注 "待用户服务API提供后实现" |
| `NodeExecution.start()` 状态未变更 | `NodeExecution.java:128` | 注释说"保持PENDING，等待审批"，start() 调用无实际状态转换效果 |
| 权限全部 PLATFORM 级别 | `ApprovalFlowAdapter`, `ApprovalInstanceAdapter` 所有端点 | `@RequirePermission(business="APPROVAL_*", category=PermissionCategory.PLATFORM)`，无业务级权限区分 |
| 审批流节点保存先删后插 | `ApprovalFlowRepositoryImpl:249-268` | 每次保存都先 DELETE 全部旧节点再 INSERT，更新场景非增量 |
| 领域事件直接在 Repository 发布 | `ApprovalInstanceRepositoryImpl:333-353` | 绕过 ApplicationService 层的 `getDomainEvents().forEach(eventBus::publish)` 模式，直接用 `ApplicationEventPublisher` |
