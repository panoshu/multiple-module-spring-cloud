# 审批流模块 DDD 领域模型设计

> 文档版本：1.0  
> 创建日期：2026-07-13  
> 状态：待审核

---

## 一、战略设计

### 1.1 通用语言（Ubiquitous Language）

| 术语 | 业务含义 | 代码命名 |
|------|---------|---------|
| **审批流（ApprovalFlow）** | 预定义的审批流程配置，包含节点序列和匹配规则 | ApprovalFlow |
| **审批节点（ApprovalNode）** | 审批流中的一个审批环节，定义审批人和签批模式 | ApprovalNode |
| **审批实例（ApprovalInstance）** | 审批流的一次具体执行 | ApprovalInstance |
| **节点执行（NodeExecution）** | 审批实例中某个节点的执行状态 | NodeExecution |
| **审批记录（ApprovalRecord）** | 一次具体的审批操作记录 | ApprovalRecord |
| **匹配规则（MatchRules）** | 用于匹配业务申请与审批流的条件组合 | MatchRules |
| **与签（AND_SIGN）** | 所有审批人都通过才算通过 | SignMode.AND_SIGN |
| **或签（OR_SIGN）** | 任一审批人通过即算通过 | SignMode.OR_SIGN |
| **驳回（Reject）** | 审批人不同意，可选择终止或退回 | ApprovalAction.REJECT |
| **转交（Transfer）** | 审批人将审批权转交给其他人 | ApprovalAction.TRANSFER |
| **撤回（Withdraw）** | 发起人取消审批申请 | - |
| **逐级向上（LEVEL_UP）** | 按企业计划层级关系向上审批 | NodeType.LEVEL_UP |

### 1.2 领域事件（Domain Event）

| 领域事件 | 过去时态描述 | 触发命令 | 状态变更 |
|---------|-------------|---------|---------|
| **ApprovalFlowCreated** | 审批流已创建 | CreateApprovalFlow | null → ACTIVE |
| **ApprovalFlowUpdated** | 审批流已更新 | UpdateApprovalFlow | version递增 |
| **ApprovalFlowDeprecated** | 审批流已废弃 | DeprecateApprovalFlow | ACTIVE → DEPRECATED |
| **ApprovalInstanceCreated** | 审批实例已创建 | StartApproval | null → PENDING |
| **ApprovalInstanceStarted** | 审批实例已启动 | (匹配成功) | PENDING → APPROVING |
| **ApprovalNodeCompleted** | 审批节点已完成 | Approve | PENDING → APPROVED |
| **ApprovalNodeRejected** | 审批节点已驳回 | Reject | PENDING → REJECTED |
| **ApprovalInstanceApproved** | 审批实例已通过 | (所有节点通过) | APPROVING → APPROVED |
| **ApprovalInstanceRejected** | 审批实例已驳回终止 | Reject(TERMINATE) | APPROVING → REJECTED |
| **ApprovalInstanceReturned** | 审批实例已退回 | Reject(INITIATOR/NODE) | 回退状态 |
| **ApprovalTransferred** | 审批已转交 | Transfer | 添加转交记录 |
| **ApprovalInstanceWithdrawn** | 审批实例已撤回 | Withdraw | APPROVING → WITHDRAWN |

### 1.3 限界上下文（Bounded Context）

| 上下文 | 边界描述 | 核心聚合根 | 对外接口 |
|--------|---------|-----------|---------|
| **approval-service** | 审批流配置与执行 | ApprovalFlow, ApprovalInstance | approval-api |
| **business-core-kernel** | 业务申请核心 | BusinessApplication | business-core-api |
| **user-service** | 用户与组织管理 | User, Plan, Role | user-api（待提供） |

### 1.4 上下文映射（Context Map）

```
┌──────────────────────┐
│ business-core-kernel │ (Shared Kernel)
│  共享：ApplicationId │
│        ProductNo     │
│        CustomerNo    │
│        PlanNo        │
│        UserNo        │
└──────────┬───────────┘
           │ Customer/Supplier (事件驱动)
           ▼
┌──────────────────────┐
│  approval-service    │
│  发布事件：           │
│  ApprovalInstance*   │
└──────────┬───────────┘
           │ ACL (防腐层)
           ▼
┌──────────────────────┐
│    user-service      │ (Conformist)
│  提供：计划层级关系   │
│        角色用户映射   │
└──────────────────────┘
```

---

## 二、战术设计

### 2.1 聚合设计

#### 聚合划分原则

| 聚合 | 一致性边界 | 原因 |
|------|-----------|------|
| **ApprovalFlow** | 审批流配置 | 审批流节点之间需要保持配置一致性 |
| **ApprovalInstance** | 审批实例执行 | 审批实例的节点执行需要保持事务一致性 |

#### 聚合根 1：ApprovalFlow

```
ApprovalFlow (聚合根)
│
├── flowId: ApprovalFlowId          # 唯一标识
├── flowName: FlowName              # 流程名称
├── matchRules: MatchRules          # 匹配规则（值对象）
├── nodes: List<ApprovalNode>       # 审批节点（实体）
├── version: FlowVersion            # 版本号
├── status: FlowStatus              # 状态（ACTIVE/DEPRECATED）
└── auditInfo: AuditInfo            # 审计信息

不变条件：
- 节点序列不能为空，至少包含一个节点
- 节点顺序必须连续（1, 2, 3...）
- 版本号从1开始，每次更新递增
- 废弃状态不可逆

业务方法：
- create() - 创建审批流
- update() - 更新审批流（版本递增）
- deprecate() - 废弃审批流
- getNextNode(nodeOrder) - 获取下一节点
```

#### 聚合根 2：ApprovalInstance

```
ApprovalInstance (聚合根)
│
├── instanceId: ApprovalInstanceId  # 唯一标识
├── flowId: ApprovalFlowId          # 关联审批流ID
├── flowVersion: FlowVersion        # 锁定版本
├── businessApplicationId: ApplicationId  # 业务申请ID
├── currentNodeOrder: NodeOrder      # 当前节点顺序
├── status: InstanceStatus          # 状态
├── initiatorPlan: PlanNo           # 发起人计划
├── currentPlan: PlanNo?            # 当前审批计划
├── nodeExecutions: List<NodeExecution>  # 节点执行记录（实体）
└── auditInfo: AuditInfo

不变条件：
- 审批实例必须关联有效的审批流
- 节点执行按顺序进行，不可跳过
- 版本锁定，不受配置变更影响
- 只有发起人可以撤回
- 只有当前审批人可以审批

业务方法：
- start() - 启动审批
- approve() - 审批通过
- reject() - 审批驳回
- transfer() - 审批转交
- withdraw() - 发起人撤回
- moveToNextNode() - 推进到下一节点
```

### 2.2 实体设计

#### ApprovalNode（审批节点实体）

```
ApprovalNode (实体)
│
├── nodeId: NodeId                  # 唯一标识
├── nodeOrder: NodeOrder            # 节点顺序
├── nodeType: NodeType              # 节点类型
├── specifiedPlanId: PlanNo?        # 指定计划（SPECIFIED_PLAN）
├── terminalLevel: TerminalLevel?   # 终止级别（LEVEL_UP）
├── approverType: ApproverType      # 审批人类型
├── approverIds: List<UserNo>?      # 指定审批人
├── roleIds: List<RoleId>?          # 指定角色
└── signMode: SignMode              # 签批模式

不变条件：
- SPECIFIED_PLAN类型必须指定企业计划
- LEVEL_UP类型必须指定终止级别
- 审批人和角色不能同时为空
```

#### NodeExecution（节点执行实体）

```
NodeExecution (实体)
│
├── executionId: ExecutionId        # 唯一标识
├── nodeId: NodeId                  # 关联节点ID
├── nodeOrder: NodeOrder            # 节点顺序
├── status: ExecutionStatus         # 执行状态
├── approvals: List<ApprovalRecord> # 审批记录
├── startedAt: LocalDateTime        # 开始时间
└── completedAt: LocalDateTime?     # 完成时间

不变条件：
- 一个节点可以有多个审批记录（转交场景）
- 审批记录按时间顺序添加
```

#### ApprovalRecord（审批记录实体）

```
ApprovalRecord (实体)
│
├── recordId: RecordId              # 唯一标识
├── approverId: UserNo              # 审批人
├── action: ApprovalAction          # 审批动作
├── opinion: ApprovalOpinion?       # 审批意见
├── rejectTarget: RejectTarget?     # 驳回目标
├── transferTo: UserNo?             # 转交目标
└── operatedAt: LocalDateTime       # 操作时间

不变条件：
- APPROVE/REJECT动作必须由当前审批人执行
- TRANSFER动作必须指定转交目标
- REJECT动作必须指定驳回目标
```

### 2.3 值对象设计

| 值对象 | 类型 | 校验规则 | 说明 |
|--------|------|---------|------|
| **ApprovalFlowId** | record | Long > 0 | 审批流唯一标识 |
| **ApprovalInstanceId** | record | Long > 0 | 审批实例唯一标识 |
| **NodeId** | record | Long > 0 | 节点唯一标识 |
| **ExecutionId** | record | Long > 0 | 执行记录唯一标识 |
| **RecordId** | record | Long > 0 | 审批记录唯一标识 |
| **FlowName** | record | 1-100字符 | 审批流名称 |
| **FlowVersion** | record | >= 1 | 版本号 |
| **NodeOrder** | record | >= 1 | 节点顺序 |
| **MatchRules** | record | JSON格式 | 匹配规则 |
| **TerminalLevel** | record | >= 1 | 终止级别 |
| **ApprovalOpinion** | record | 0-500字符 | 审批意见 |
| **RejectTarget** | 枚举/record | TERMINATE/INITIATOR/NODE_ORDER(N) | 驳回目标 |

#### 枚举值对象

```java
// 节点类型
public enum NodeType {
    SPECIFIED_PLAN,   // 指定企业计划
    SAME_PLAN,        // 同计划互审
    LEVEL_UP          // 逐级向上
}

// 签批模式
public enum SignMode {
    AND_SIGN,  // 与签（全部通过）
    OR_SIGN    // 或签（任一通过）
}

// 审批人类型
public enum ApproverType {
    SPECIFIED_USER,  // 指定用户
    SPECIFIED_ROLE   // 指定角色
}

// 审批流状态
public enum FlowStatus {
    ACTIVE,      // 激活
    DEPRECATED   // 已废弃
}

// 审批实例状态
public enum InstanceStatus {
    PENDING,     // 待审批
    APPROVING,   // 审批中
    APPROVED,    // 已通过
    REJECTED,    // 已驳回
    WITHDRAWN    // 已撤回
}

// 节点执行状态
public enum ExecutionStatus {
    PENDING,     // 待执行
    APPROVED,    // 已通过
    REJECTED,    // 已驳回
    SKIPPED      // 已跳过
}

// 审批动作
public enum ApprovalAction {
    APPROVE,    // 通过
    REJECT,     // 驳回
    TRANSFER    // 转交
}
```

### 2.4 领域服务设计

| 领域服务 | 职责 | 方法 |
|---------|------|------|
| **ApprovalFlowMatcher** | 审批流匹配 | `match(MatchRules rules, List<ApprovalFlow> flows)` |
| **ApprovalNodeResolver** | 节点审批人解析 | `resolveApprovers(ApprovalNode node, ApprovalInstance instance)` |
| **ApprovalSignModeEvaluator** | 签批模式判定 | `evaluate(SignMode mode, List<ApprovalRecord> records)` |

```java
@DomainService
public class ApprovalFlowMatcher {
    
    /**
     * 根据匹配规则找到适用的审批流
     */
    public Optional<ApprovalFlow> match(MatchRules rules, List<ApprovalFlow> flows) {
        return flows.stream()
            .filter(flow -> flow.isActive())
            .filter(flow -> isMatch(rules, flow.getMatchRules()))
            .max(Comparator.comparing(ApprovalFlow::getVersion));
    }
    
    private boolean isMatch(MatchRules request, MatchRules config) {
        // 匹配逻辑：请求规则与配置规则匹配（空值表示匹配所有）
    }
}

@DomainService
public class ApprovalSignModeEvaluator {
    
    /**
     * 判定节点是否完成
     */
    public boolean isNodeCompleted(SignMode signMode, List<ApprovalRecord> records) {
        List<ApprovalRecord> validRecords = records.stream()
            .filter(r -> r.getAction() != ApprovalAction.TRANSFER)
            .toList();
        
        return switch (signMode) {
            case AND_SIGN -> validRecords.stream()
                .allMatch(r -> r.getAction() == ApprovalAction.APPROVE);
            case OR_SIGN -> validRecords.stream()
                .anyMatch(r -> r.getAction() == ApprovalAction.APPROVE);
        };
    }
}
```

### 2.5 仓储接口设计

| Repository | 聚合根 | 方法 |
|------------|--------|------|
| **ApprovalFlowRepository** | ApprovalFlow | load, save, findById, findByStatus, findActiveByMatchRules |
| **ApprovalInstanceRepository** | ApprovalInstance | load, save, findById, findByBusinessApplicationId, findByApproverId |

```java
// domain/repository/ApprovalFlowRepository.java
public interface ApprovalFlowRepository extends Repository<ApprovalFlow, ApprovalFlowId> {
    
    Optional<ApprovalFlow> load(ApprovalFlowId flowId);
    
    Optional<ApprovalFlow> load(ApprovalFlowId flowId, FlowVersion version);
    
    void save(ApprovalFlow flow);
    
    List<ApprovalFlow> findByStatus(FlowStatus status);
    
    List<ApprovalFlow> findActiveByMatchRules(MatchRules rules);
}

// domain/repository/ApprovalInstanceRepository.java
public interface ApprovalInstanceRepository extends Repository<ApprovalInstance, ApprovalInstanceId> {
    
    Optional<ApprovalInstance> load(ApprovalInstanceId instanceId);
    
    void save(ApprovalInstance instance);
    
    Optional<ApprovalInstance> findByBusinessApplicationId(ApplicationId applicationId);
    
    List<ApprovalInstance> findByApproverId(UserNo approverId, InstanceStatus status);
}
```

### 2.6 Gateway 防腐层接口设计

```java
// domain/gateway/PlanHierarchyGateway.java
public interface PlanHierarchyGateway {
    
    /**
     * 获取企业计划的父级计划ID
     * TODO: 待用户服务API提供后实现
     */
    Optional<PlanNo> getParentPlan(PlanNo planId);
    
    /**
     * 获取企业计划的层级深度
     * TODO: 待用户服务API提供后实现
     */
    int getPlanLevel(PlanNo planId);
    
    /**
     * 判断是否达到终止级别
     */
    default boolean isTerminalLevel(PlanNo planId, TerminalLevel terminalLevel) {
        return getPlanLevel(planId) >= terminalLevel.value();
    }
}

// domain/gateway/RoleUserGateway.java
public interface RoleUserGateway {
    
    /**
     * 获取角色对应的用户列表
     * TODO: 待用户服务API提供后实现
     */
    List<UserNo> getUsersByRole(RoleId roleId);
    
    /**
     * 获取同一计划下的用户列表（排除指定用户）
     * TODO: 待用户服务API提供后实现
     */
    List<UserNo> getUsersInSamePlan(PlanNo planId, UserNo excludeUser);
}
```

### 2.7 领域事件设计

```java
// domain/event/ApprovalFlowCreated.java
public record ApprovalFlowCreated(
    EventId eventId,
    LocalDateTime occurredAt,
    ApprovalFlowId flowId,
    FlowVersion version
) implements DomainEvent {
    
    public static ApprovalFlowCreated of(ApprovalFlowId flowId, FlowVersion version) {
        return new ApprovalFlowCreated(
            EventId.generate(),
            LocalDateTime.now(),
            flowId,
            version
        );
    }
}

// domain/event/ApprovalInstanceCreated.java
public record ApprovalInstanceCreated(
    EventId eventId,
    LocalDateTime occurredAt,
    ApprovalInstanceId instanceId,
    ApprovalFlowId flowId,
    ApplicationId businessApplicationId
) implements DomainEvent {
    
    public static ApprovalInstanceCreated of(
        ApprovalInstanceId instanceId,
        ApprovalFlowId flowId,
        ApplicationId businessApplicationId
    ) {
        return new ApprovalInstanceCreated(
            EventId.generate(),
            LocalDateTime.now(),
            instanceId,
            flowId,
            businessApplicationId
        );
    }
}

// domain/event/ApprovalInstanceApproved.java
public record ApprovalInstanceApproved(
    EventId eventId,
    LocalDateTime occurredAt,
    ApprovalInstanceId instanceId,
    ApplicationId businessApplicationId
) implements DomainEvent {
    
    public static ApprovalInstanceApproved of(
        ApprovalInstanceId instanceId,
        ApplicationId businessApplicationId
    ) {
        return new ApprovalInstanceApproved(
            EventId.generate(),
            LocalDateTime.now(),
            instanceId,
            businessApplicationId
        );
    }
}

// domain/event/ApprovalInstanceRejected.java
public record ApprovalInstanceRejected(
    EventId eventId,
    LocalDateTime occurredAt,
    ApprovalInstanceId instanceId,
    ApplicationId businessApplicationId,
    UserNo rejectedBy
) implements DomainEvent {
    
    public static ApprovalInstanceRejected of(
        ApprovalInstanceId instanceId,
        ApplicationId businessApplicationId,
        UserNo rejectedBy
    ) {
        return new ApprovalInstanceRejected(
            EventId.generate(),
            LocalDateTime.now(),
            instanceId,
            businessApplicationId,
            rejectedBy
        );
    }
}

// domain/event/ApprovalInstanceWithdrawn.java
public record ApprovalInstanceWithdrawn(
    EventId eventId,
    LocalDateTime occurredAt,
    ApprovalInstanceId instanceId,
    ApplicationId businessApplicationId
) implements DomainEvent {
    
    public static ApprovalInstanceWithdrawn of(
        ApprovalInstanceId instanceId,
        ApplicationId businessApplicationId
    ) {
        return new ApprovalInstanceWithdrawn(
            EventId.generate(),
            LocalDateTime.now(),
            instanceId,
            businessApplicationId
        );
    }
}
```

---

## 三、领域模型分包结构

```
approval-domain/
├── aggregate/
│   ├── root/
│   │   ├── ApprovalFlow.java           # 审批流聚合根
│   │   └── ApprovalInstance.java       # 审批实例聚合根
│   └── entity/
│       ├── ApprovalNode.java           # 审批节点实体
│       ├── NodeExecution.java          # 节点执行实体
│       └── ApprovalRecord.java         # 审批记录实体
├── valueobject/
│   ├── FlowName.java
│   ├── FlowVersion.java
│   ├── NodeOrder.java
│   ├── MatchRules.java
│   ├── TerminalLevel.java
│   ├── ApprovalOpinion.java
│   ├── RejectTarget.java
│   └── enums/
│       ├── NodeType.java
│       ├── SignMode.java
│       ├── ApproverType.java
│       ├── FlowStatus.java
│       ├── InstanceStatus.java
│       ├── ExecutionStatus.java
│       └── ApprovalAction.java
├── event/
│   ├── ApprovalFlowCreated.java
│   ├── ApprovalFlowUpdated.java
│   ├── ApprovalFlowDeprecated.java
│   ├── ApprovalInstanceCreated.java
│   ├── ApprovalInstanceApproved.java
│   ├── ApprovalInstanceRejected.java
│   ├── ApprovalInstanceWithdrawn.java
│   └── ApprovalNodeCompleted.java
├── repository/
│   ├── ApprovalFlowRepository.java
│   └── ApprovalInstanceRepository.java
├── gateway/
│   ├── PlanHierarchyGateway.java
│   └── RoleUserGateway.java
├── service/
│   ├── ApprovalFlowMatcher.java
│   ├── ApprovalNodeResolver.java
│   └── ApprovalSignModeEvaluator.java
└── errorcode/
    └── ApprovalDomainErrorCode.java
```

---

## 四、领域规则总结

### 4.1 业务不变量

| 不变量 | 描述 |
|--------|------|
| **节点顺序连续** | 审批节点必须按顺序执行（1, 2, 3...），不可跳过 |
| **版本锁定** | 审批实例创建时锁定审批流版本，不受后续配置变更影响 |
| **单一审批人** | 同一审批人在同一节点只能审批一次 |
| **发起人撤回** | 只有发起人可以撤回审批实例 |
| **当前审批人** | 只有当前节点的审批人可以执行审批操作 |
| **废弃不可逆** | 审批流废弃后不可恢复 |

### 4.2 状态转换规则

```
ApprovalFlow 状态转换：
  null → ACTIVE → DEPRECATED (终态)

ApprovalInstance 状态转换：
  null → PENDING → APPROVING → APPROVED (终态)
                               → REJECTED (终态)
                               → WITHDRAWN (终态)
                  ←───── (驳回退回发起人)

NodeExecution 状态转换：
  null → PENDING → APPROVED (终态)
                → REJECTED (终态)
                → SKIPPED (终态)
```

### 4.3 签批模式规则

| 签批模式 | 通过条件 | 驳回条件 |
|---------|---------|---------|
| **AND_SIGN（与签）** | 所有审批人 APPROVE | 任一审批人 REJECT |
| **OR_SIGN（或签）** | 任一审批人 APPROVE | 所有审批人 REJECT |

---

## 五、与其他上下文的协作

### 5.1 与 business-core-kernel 的协作

| 协作方式 | 说明 |
|---------|------|
| **共享内核** | 共享 ApplicationId, ProductNo, CustomerNo, PlanNo, UserNo 等领域原语 |
| **事件驱动** | 发布审批完成事件，业务申请模块订阅并更新状态 |

### 5.2 与 user-service 的协作

| 协作方式 | 说明 |
|---------|------|
| **防腐层** | 通过 Gateway 接口隔离用户服务的影响 |
| **依赖方向** | approval-service 依赖 user-service（通过 ACL） |

---

## 六、待确认事项

| 序号 | 待确认事项 | 状态 |
|------|-----------|------|
| 1 | Gateway 接口实现：用户服务 API 待提供 | 待实现 |
| 2 | 审批流配置的权限控制策略 | 待确认 |
| 3 | 审批效率统计的具体维度和计算公式 | 待确认 |