# 审批流模块设计规格文档

> 文档版本：1.0  
> 创建日期：2026-07-13  
> 状态：待审核

---

## 一、需求概述

### 1.1 业务需求

实现一个工作流服务，支持审批流的配置和使用功能。

**配置维度**：支持基于产品、客户、账管人、运作模式、业务类型、年金渠道进行审批流配置。

**审批流结构**：单向线性流程，每个节点支持：
- 指定企业计划
- 同一计划下其他用户审批
- 按照计划层级关系逐级向上审批

**节点权限**：每个节点支持指定审批人、指定角色。

**审批模式**：支持"与签"（全部通过）和"或签"（任一通过）。

### 1.2 需求澄清结论

| 维度 | 需求结论 |
|------|---------|
| **触发场景** | 业务申请提交后触发，通过业务属性匹配审批流配置，匹配成功后业务状态置为"审批中"+同步启动审批流 |
| **企业计划层级** | 与组织机构对应，不固定层级；"同级互审"实际是"同一计划下其他用户审批" |
| **终止条件** | 在审批流配置中逐节点指定终止级别（仅 LEVEL_UP 类型节点有效） |
| **与签/或签** | 在审批流配置中逐节点指定 |
| **配置变更处理** | 新配置仅影响后续新申请，进行中的实例继续按原配置执行 |
| **异常流程** | 支持：驳回（可选择驳回目标）、转交、撤回；不支持超时 |
| **审计功能** | 完整支持：审批历史记录、操作日志、配置变更历史、效率统计 |

---

## 二、架构设计

### 2.1 设计方案

采用 **轻量级自研引擎 + DDD领域模型** 方案。

| 维度 | 说明 |
|------|------|
| **架构风格** | 完全符合项目DDD规范，领域层不依赖任何外部框架 |
| **核心设计** | ApprovalFlow（审批流定义）作为聚合根，ApprovalInstance（审批实例）作为独立聚合根 |
| **状态管理** | 通过领域事件驱动状态流转，审批节点状态由聚合根内部管理 |
| **扩展性** | 支持新增节点类型、审批模式，符合开闭原则 |

### 2.2 模块划分

```
approval-service/
├── approval-types/           # 领域原语层：审批相关ID类型、业务属性枚举
├── approval-domain/          # 领域层：聚合根、实体、值对象、领域服务、Repository接口、Gateway接口
├── approval-api/             # API定义层：Request、Response、DTO、Command、Query
├── approval-application/     # 应用层：应用服务、流程编排、事件处理
├── approval-adapter/         # 适配器层：Controller、DTO转换
├── approval-infrastructure/  # 基础设施层：Repository实现、Gateway实现、数据库访问
└── approval-starter/         # 启动模块：Spring Boot启动类
```

### 2.3 依赖关系

```
approval-types ──────> shared-types
approval-domain ─────> shared-domain + approval-types
approval-api ────────> shared-api + approval-types
approval-application ─> approval-api + approval-domain + shared-*starter
approval-adapter ─────> approval-api + approval-application
approval-infrastructure ─> approval-domain + shared-*starter
approval-starter ─────> approval-adapter + approval-infrastructure
```

---

## 三、领域模型设计

### 3.1 聚合根设计

| 聚合根 | 职责 | 说明 |
|--------|------|------|
| **ApprovalFlow** | 审批流定义（配置） | 定义审批流的节点序列、匹配规则、版本管理 |
| **ApprovalInstance** | 审批实例（运行时） | 审批流的一次执行实例，管理审批状态流转 |

### 3.2 ApprovalFlow 聚合根结构

```
ApprovalFlow (聚合根)
├── flowId: ApprovalFlowId          # 审批流ID
├── flowName: FlowName              # 审批流名称
├── matchRules: MatchRules          # 匹配规则（JSON）
├── nodes: List<ApprovalNode>       # 审批节点序列（实体）
├── version: FlowVersion            # 版本号（配置变更时递增）
├── status: FlowStatus              # 状态（ACTIVE/DEPRECATED）
└── auditInfo: AuditInfo            # 创建/修改信息

ApprovalNode (实体)
├── nodeId: NodeId                  # 节点ID
├── nodeType: NodeType              # 节点类型（SPECIFIED_PLAN/SAME_PLAN/LEVEL_UP）
├── specifiedPlanId: PlanNo?        # 指定企业计划ID（仅SPECIFIED_PLAN类型）
├── terminalLevel: TerminalLevel?   # 终止级别（仅LEVEL_UP类型）
├── approverType: ApproverType      # 审批人类型（SPECIFIED_USER/SPECIFIED_ROLE）
├── approverIds: List<UserNo>?      # 指定审批人ID列表
├── roleIds: List<RoleId>?          # 指定角色ID列表
├── signMode: SignMode              # 签批模式（AND_SIGN/OR_SIGN）
├── nodeOrder: NodeOrder            # 节点顺序
```

### 3.3 ApprovalInstance 聚合根结构

```
ApprovalInstance (聚合根)
├── instanceId: ApprovalInstanceId  # 审批实例ID
├── flowId: ApprovalFlowId          # 关联的审批流ID
├── flowVersion: FlowVersion        # 使用版本（实例创建时锁定）
├── businessApplicationId: ApplicationId  # 关联的业务申请ID
├── currentNodeOrder: NodeOrder      # 当前节点顺序
├── status: InstanceStatus          # 状态（PENDING/APPROVING/APPROVED/REJECTED/WITHDRAWN）
├── initiatorPlan: PlanNo           # 发起人所属企业计划
├── currentPlan: PlanNo?            # 当前审批计划（逐级向上时使用）
├── nodeExecutions: List<NodeExecution>  # 节点执行记录（实体）
└── auditInfo: AuditInfo

NodeExecution (实体)
├── executionId: ExecutionId        # 执行ID
├── nodeId: NodeId                  # 节点ID
├── nodeOrder: NodeOrder            # 节点顺序
├── status: ExecutionStatus         # 状态（PENDING/APPROVED/REJECTED/SKIPPED）
├── approvals: List<ApprovalRecord> # 审批记录（实体）
├── startedAt: LocalDateTime        # 开始时间
├── completedAt: LocalDateTime?     # 完成时间

ApprovalRecord (实体)
├── recordId: RecordId              # 记录ID
├── approverId: UserNo              # 审批人ID
├── action: ApprovalAction          # 审批动作（APPROVE/REJECT/TRANSFER）
├── opinion: ApprovalOpinion?       # 审批意见
├── rejectTarget: RejectTarget?     # 驳回目标（节点顺序/TERMINATE/INITIATOR）
├── transferTo: UserNo?             # 转交目标用户
├── operatedAt: LocalDateTime       # 操作时间
```

### 3.4 Gateway 防腐层接口

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
}

// domain/gateway/RoleUserGateway.java  
public interface RoleUserGateway {
    /**
     * 获取角色对应的用户列表
     * TODO: 待用户服务API提供后实现
     */
    List<UserNo> getUsersByRole(RoleId roleId);
    
    /**
     * 获取同一计划下的用户列表（排除当前申请人）
     * TODO: 待用户服务API提供后实现
     */
    List<UserNo> getUsersInSamePlan(PlanNo planId, UserNo excludeUser);
}
```

### 3.5 值对象设计

| 值对象 | 说明 |
|--------|------|
| **MatchRules** | 匹配规则：产品、客户、账管人、运作模式、业务类型、年金渠道的组合条件（JSON格式） |
| **NodeType** | 节点类型枚举：SPECIFIED_PLAN（指定企业计划）、SAME_PLAN（同计划互审）、LEVEL_UP（逐级向上） |
| **SignMode** | 签批模式枚举：AND_SIGN（与签）、OR_SIGN（或签） |
| **ApproverType** | 审批人类型枚举：SPECIFIED_USER、SPECIFIED_ROLE |
| **InstanceStatus** | 实例状态枚举：PENDING、APPROVING、APPROVED、REJECTED、WITHDRAWN |
| **ExecutionStatus** | 执行状态枚举：PENDING、APPROVED、REJECTED、SKIPPED |
| **ApprovalAction** | 审批动作枚举：APPROVE、REJECT、TRANSFER |
| **RejectTarget** | 驳回目标：TERMINATE（终止）、INITIATOR（退回发起人）、NODE_ORDER(N)（退回第N节点） |

---

## 四、审批流程状态流转

### 4.1 审批实例生命周期

```
[业务申请提交]
      │
      ▼
┌─────────┐    匹配成功    ┌───────────┐
│ PENDING │ ─────────────▶ │ APPROVING │
└─────────┘                └───────────┘
      │                          │
      │ 匹配失败                 │
      ▼                          ▼
 (业务直接处理)         ┌─────────────────────┐
                       │ 节点审批处理        │
                       │ - 通过 → 下一节点   │
                       │ - 驳回 → 见下方    │
                       │ - 转交 → 同节点     │
                       └─────────────────────┘
                                 │
                                 │ 所有节点通过
                                 ▼
                          ┌───────────┐
                          │ APPROVED │
                          └───────────┘
```

### 4.2 异常流程处理

#### 驳回处理

| 驳回目标 | 处理逻辑 |
|---------|---------|
| **TERMINATE** | 实例状态 → REJECTED，当前节点状态 → REJECTED，后续节点 → SKIPPED，发布 ApprovalInstanceRejectedEvent |
| **INITIATOR** | 实例状态 → PENDING，所有节点执行记录清空，等待发起人修改后重新提交 |
| **NODE_ORDER(N)** | 实例状态保持 APPROVING，当前节点状态 → REJECTED，currentNodeOrder 回退到 N，目标节点重新开始执行 |

#### 转交处理

- 实例状态不变（APPROVING）
- 创建 ApprovalRecord（action=TRANSFER）
- 将目标用户加入当前节点的待审批人列表
- 原审批人标记为"已转交"，不再有审批权限

#### 撤回处理

- 实例状态必须是 APPROVING
- 操作人必须是发起人
- 实例状态 → WITHDRAWN
- 发布 ApprovalInstanceWithdrawnEvent

### 4.3 领域事件

| 领域事件 | 触发时机 |
|---------|---------|
| **ApprovalInstanceCreatedEvent** | 审批实例创建成功 |
| **ApprovalInstanceApprovedEvent** | 审批通过完成 |
| **ApprovalInstanceRejectedEvent** | 审批驳回终止 |
| **ApprovalInstanceWithdrawnEvent** | 发起人撤回 |
| **ApprovalNodeCompletedEvent** | 节点审批完成 |
| **ApprovalRecordCreatedEvent** | 审批记录创建 |

---

## 五、数据模型设计

### 5.1 数据库表结构

#### approval_flow（审批流配置表）

```sql
CREATE TABLE approval_flow (
    id              BIGINT PRIMARY KEY,
    flow_name       VARCHAR(100) NOT NULL,
    match_rules     JSON NOT NULL,          -- 匹配规则（JSON格式）
    version         INT NOT NULL DEFAULT 1,
    status          VARCHAR(20) NOT NULL,   -- ACTIVE/DEPRECATED
    created_by      VARCHAR(50) NOT NULL,
    created_at      TIMESTAMP NOT NULL,
    updated_by      VARCHAR(50),
    updated_at      TIMESTAMP,
    UNIQUE KEY uk_flow_version (id, version)
);

-- JSON字段虚拟列索引
ALTER TABLE approval_flow 
ADD COLUMN product_no_virtual VARCHAR(50) 
AS (JSON_UNQUOTE(JSON_EXTRACT(match_rules, '$.productNo'))) VIRTUAL;

CREATE INDEX idx_flow_product ON approval_flow(product_no_virtual);
CREATE INDEX idx_flow_status ON approval_flow(status);
```

**match_rules JSON格式**：
```json
{
    "productNo": "P001",
    "customerNo": "C001",
    "accountManager": "AM001",
    "operationMode": "OM001",
    "businessType": "BT001",
    "annuityChannel": "AC001"
}
```

#### approval_node（审批节点配置表）

```sql
CREATE TABLE approval_node (
    id              BIGINT PRIMARY KEY,
    flow_id         BIGINT NOT NULL,
    flow_version    INT NOT NULL,
    node_order      INT NOT NULL,
    node_type       VARCHAR(20) NOT NULL,   -- SPECIFIED_PLAN/SAME_PLAN/LEVEL_UP
    specified_plan  VARCHAR(50),            -- 指定企业计划（仅SPECIFIED_PLAN类型）
    terminal_level  INT,                    -- 终止级别（仅LEVEL_UP类型）
    approver_type   VARCHAR(20) NOT NULL,   -- SPECIFIED_USER/SPECIFIED_ROLE
    approver_ids    VARCHAR(500),           -- 指定审批人ID列表（JSON数组）
    role_ids        VARCHAR(500),           -- 指定角色ID列表（JSON数组）
    sign_mode       VARCHAR(20) NOT NULL,   -- AND_SIGN/OR_SIGN
    created_at      TIMESTAMP NOT NULL,
    FOREIGN KEY (flow_id, flow_version) REFERENCES approval_flow(id, version),
    UNIQUE KEY uk_flow_node (flow_id, flow_version, node_order)
);
```

#### approval_instance（审批实例表）

```sql
CREATE TABLE approval_instance (
    id                  BIGINT PRIMARY KEY,
    flow_id             BIGINT NOT NULL,
    flow_version        INT NOT NULL,
    business_application_id BIGINT NOT NULL,
    current_node_order  INT NOT NULL,
    status              VARCHAR(20) NOT NULL,
    initiator_plan      VARCHAR(50) NOT NULL,
    current_plan        VARCHAR(50),
    created_by          VARCHAR(50) NOT NULL,
    created_at          TIMESTAMP NOT NULL,
    updated_by          VARCHAR(50),
    updated_at          TIMESTAMP,
    completed_at        TIMESTAMP,
    FOREIGN KEY (flow_id, flow_version) REFERENCES approval_flow(id, version)
);

CREATE INDEX idx_instance_business ON approval_instance(business_application_id);
CREATE INDEX idx_instance_status ON approval_instance(status);
```

#### approval_node_execution（节点执行记录表）

```sql
CREATE TABLE approval_node_execution (
    id              BIGINT PRIMARY KEY,
    instance_id     BIGINT NOT NULL,
    node_id         BIGINT NOT NULL,
    node_order      INT NOT NULL,
    status          VARCHAR(20) NOT NULL,
    started_at      TIMESTAMP NOT NULL,
    completed_at    TIMESTAMP,
    FOREIGN KEY (instance_id) REFERENCES approval_instance(id),
    FOREIGN KEY (node_id) REFERENCES approval_node(id),
    UNIQUE KEY uk_instance_node (instance_id, node_order)
);

CREATE INDEX idx_execution_instance ON approval_node_execution(instance_id);
```

#### approval_record（审批记录表）

```sql
CREATE TABLE approval_record (
    id              BIGINT PRIMARY KEY,
    execution_id    BIGINT NOT NULL,
    approver_id     VARCHAR(50) NOT NULL,
    action          VARCHAR(20) NOT NULL,
    opinion         VARCHAR(500),
    reject_target   VARCHAR(50),
    transfer_to     VARCHAR(50),
    operated_at     TIMESTAMP NOT NULL,
    FOREIGN KEY (execution_id) REFERENCES approval_node_execution(id)
);

CREATE INDEX idx_record_execution ON approval_record(execution_id);
CREATE INDEX idx_record_approver ON approval_record(approver_id);
```

#### approval_flow_change_log（配置变更历史表）

```sql
CREATE TABLE approval_flow_change_log (
    id              BIGINT PRIMARY KEY,
    flow_id         BIGINT NOT NULL,
    old_version     INT NOT NULL,
    new_version     INT NOT NULL,
    change_type     VARCHAR(50) NOT NULL,
    change_detail   VARCHAR(1000),
    changed_by      VARCHAR(50) NOT NULL,
    changed_at      TIMESTAMP NOT NULL,
    FOREIGN KEY (flow_id) REFERENCES approval_flow(id)
);

CREATE INDEX idx_change_log_flow ON approval_flow_change_log(flow_id);
```

#### approval_audit_log（审计日志表）

```sql
CREATE TABLE approval_audit_log (
    id              BIGINT PRIMARY KEY,
    instance_id     BIGINT NOT NULL,
    operation_type  VARCHAR(20) NOT NULL,
    operator_id     VARCHAR(50) NOT NULL,
    operator_name   VARCHAR(100),
    operation_detail VARCHAR(1000),
    operated_at     TIMESTAMP NOT NULL,
    ip_address      VARCHAR(50),
    FOREIGN KEY (instance_id) REFERENCES approval_instance(id)
);

CREATE INDEX idx_audit_instance ON approval_audit_log(instance_id);
CREATE INDEX idx_audit_operator ON approval_audit_log(operator_id);
CREATE INDEX idx_audit_time ON approval_audit_log(operated_at);
```

---

## 六、API 设计

### 6.1 API 接口定义

#### ApprovalFlowApi（审批流配置管理）

```java
@HttpExchange("/api/approval/flows")
public interface ApprovalFlowApi {

    @PostExchange("/create")
    ApiResult<ApprovalFlowIdResponse> create(@RequestBody @Valid CreateApprovalFlowRequest request);

    @PostExchange("/update")
    ApiResult<Void> update(@RequestBody @Valid UpdateApprovalFlowRequest request);

    @PostExchange("/deprecate")
    ApiResult<Void> deprecate(@RequestBody @Valid DeprecateApprovalFlowRequest request);

    @PostExchange("/get")
    ApiResult<ApprovalFlowDTO> get(@RequestBody @Valid GetApprovalFlowRequest request);

    @PostExchange("/list")
    ApiResult<PageInfo<ApprovalFlowDTO>> list(@RequestBody @Valid ListApprovalFlowsRequest request);

    @PostExchange("/versions")
    ApiResult<List<ApprovalFlowVersionDTO>> getVersions(@RequestBody @Valid GetApprovalFlowVersionRequest request);

    @PostExchange("/match")
    ApiResult<ApprovalFlowDTO> match(@RequestBody @Valid MatchApprovalFlowRequest request);
}
```

#### ApprovalInstanceApi（审批实例操作）

```java
@HttpExchange("/api/approval/instances")
public interface ApprovalInstanceApi {

    @PostExchange("/start")
    ApiResult<ApprovalInstanceIdResponse> start(@RequestBody @Valid StartApprovalRequest request);

    @PostExchange("/approve")
    ApiResult<Void> approve(@RequestBody @Valid ApproveRequest request);

    @PostExchange("/reject")
    ApiResult<Void> reject(@RequestBody @Valid RejectRequest request);

    @PostExchange("/transfer")
    ApiResult<Void> transfer(@RequestBody @Valid TransferRequest request);

    @PostExchange("/withdraw")
    ApiResult<Void> withdraw(@RequestBody @Valid WithdrawRequest request);

    @PostExchange("/get")
    ApiResult<ApprovalInstanceDTO> get(@RequestBody @Valid GetApprovalInstanceRequest request);

    @PostExchange("/my-pending")
    ApiResult<PageInfo<PendingApprovalDTO>> listMyPending(@RequestBody @Valid ListMyPendingApprovalsRequest request);

    @PostExchange("/history")
    ApiResult<List<ApprovalRecordDTO>> getHistory(@RequestBody @Valid GetApprovalHistoryRequest request);

    @PostExchange("/statistics")
    ApiResult<ApprovalStatisticsDTO> getStatistics(@RequestBody @Valid GetApprovalStatisticsRequest request);
}
```

### 6.2 Request/Response 定义

```java
// 启动审批
public record StartApprovalRequest(
    ApplicationId businessApplicationId,
    ProductNo productNo,
    CustomerNo customerNo,
    AccountManagerCode accountManager,
    OperationModeCode operationMode,
    BusinessTypeCode businessType,
    AnnuityChannelCode annuityChannel,
    UserNo initiatorId,
    PlanNo initiatorPlan
) {}

// 审批通过
public record ApproveRequest(
    ApprovalInstanceId instanceId,
    UserNo approverId,
    String opinion
) {}

// 审批驳回
public record RejectRequest(
    ApprovalInstanceId instanceId,
    UserNo approverId,
    String opinion,
    RejectTarget rejectTarget
) {}

// 审批转交
public record TransferRequest(
    ApprovalInstanceId instanceId,
    UserNo approverId,
    UserNo transferTo,
    String opinion
) {}

// 发起人撤回
public record WithdrawRequest(
    ApprovalInstanceId instanceId,
    UserNo initiatorId
) {}

// 匹配审批流
public record MatchApprovalFlowRequest(
    ProductNo productNo,
    CustomerNo customerNo,
    AccountManagerCode accountManager,
    OperationModeCode operationMode,
    BusinessTypeCode businessType,
    AnnuityChannelCode annuityChannel
) {}

// Response
public record ApprovalFlowIdResponse(ApprovalFlowId flowId) {}
public record ApprovalInstanceIdResponse(ApprovalInstanceId instanceId) {}
```

---

## 七、领域原语设计

### 7.1 approval-types 模块定义

```java
// 审批服务专用 ID 类型
public record ApprovalFlowId(Long value) implements Identifier<Long> {}
public record ApprovalInstanceId(Long value) implements Identifier<Long> {}
public record NodeId(Long value) implements Identifier<Long> {}
public record ExecutionId(Long value) implements Identifier<Long> {}

// 业务属性枚举（API层使用）
public enum OperationModeCode {
    MODE_A, MODE_B, MODE_C;
}

public enum BusinessTypeCode {
    TYPE_A, TYPE_B, TYPE_C;
}

public enum AnnuityChannelCode {
    CHANNEL_A, CHANNEL_B;
}

public record AccountManagerCode(String value) implements ValueObject {
    public AccountManagerCode {
        if (value == null || value.isBlank()) {
            throw new DomainException("AccountManagerCode cannot be empty");
        }
    }
}
```

### 7.2 分层策略

| 类别 | 示例 | 放置位置 | 说明 |
|------|------|---------|------|
| **公共 ID 类型** | ProductNo, CustomerNo, PlanNo, UserNo, ApplicationId | shared-types | 跨上下文的"身份证"，可共享 |
| **审批服务专用 ID** | ApprovalFlowId, ApprovalInstanceId | approval-types | 审批领域内部标识 |
| **业务属性类型** | AccountManagerCode, OperationModeCode | approval-types | 仅用于 API 方法签名 |
| **领域内部值对象** | MatchRules, RejectTarget | approval-domain | 封装领域规则 |

---

## 八、错误码设计

```java
public enum ApprovalDomainErrorCode implements ErrorDefinition {

    // 审批流配置相关 (AP001-AP009)
    APPROVAL_FLOW_NOT_FOUND("AP001", "[审批流不存在]{}"),
    APPROVAL_FLOW_DEPRECATED("AP002", "[审批流已废弃]{}"),
    APPROVAL_FLOW_VERSION_MISMATCH("AP003", "[审批流版本不匹配]{}"),
    APPROVAL_FLOW_NODE_INVALID("AP004", "[审批节点配置无效]{}"),
    APPROVAL_FLOW_MATCH_RULE_INVALID("AP005", "[匹配规则无效]{}"),

    // 审批实例相关 (AP010-AP019)
    APPROVAL_INSTANCE_NOT_FOUND("AP010", "[审批实例不存在]{}"),
    APPROVAL_INSTANCE_ALREADY_COMPLETED("AP011", "[审批实例已完成]{}"),
    APPROVAL_INSTANCE_ALREADY_WITHDRAWN("AP012", "[审批实例已撤回]{}"),
    APPROVAL_INSTANCE_NOT_APPROVING("AP013", "[审批实例不在审批中状态]{}"),
    APPROVAL_INSTANCE_ALREADY_PENDING("AP014", "[审批实例已在待审批状态]{}"),

    // 审批操作相关 (AP020-AP029)
    NOT_CURRENT_APPROVER("AP020", "[不是当前节点的审批人]{}"),
    APPROVER_ALREADY_APPROVED("AP021", "[审批人已审批]{}"),
    APPROVER_ALREADY_TRANSFERRED("AP022", "[审批人已转交]{}"),
    TRANSFER_TARGET_NOT_FOUND("AP023", "[转交目标用户不存在]{}"),
    WITHDRAW_NOT_BY_INITIATOR("AP024", "[撤回只能由发起人操作]{}"),
    INVALID_REJECT_TARGET("AP025", "[无效的驳回目标]{}"),
    APPROVAL_NODE_NOT_FOUND("AP026", "[审批节点不存在]{}"),

    // 匹配规则相关 (AP030-AP039)
    NO_MATCHING_APPROVAL_FLOW("AP030", "[未找到匹配的审批流]{}"),
    BUSINESS_APPLICATION_NOT_FOUND("AP031", "[业务申请不存在]{}"),
    ;

    final String code;
    final String message;

    ApprovalDomainErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return this.code;
    }

    @Override
    public String message() {
        return this.message;
    }
}
```

---

## 九、审计功能设计

### 9.1 审计日志类型

| 操作类型 | 说明 |
|---------|------|
| `INSTANCE_START` | 启动审批 |
| `INSTANCE_APPROVE` | 审批通过 |
| `INSTANCE_REJECT` | 审批驳回 |
| `INSTANCE_TRANSFER` | 审批转交 |
| `INSTANCE_WITHDRAW` | 发起人撤回 |
| `FLOW_CREATE` | 创建审批流配置 |
| `FLOW_UPDATE` | 更新审批流配置 |
| `FLOW_DEPRECATE` | 废弃审批流配置 |

### 9.2 配置变更历史

变更类型：
- `NODE_ADD`：新增节点
- `NODE_REMOVE`：删除节点
- `NODE_UPDATE`：修改节点配置
- `RULE_UPDATE`：修改匹配规则
- `STATUS_CHANGE`：状态变更

### 9.3 审批效率统计

```java
public record ApprovalStatisticsDTO(
    Integer totalInstances,
    Integer approvedCount,
    Integer rejectedCount,
    Integer withdrawnCount,
    Double approvalRate,
    Double avgApprovalDuration,
    Double avgNodeDuration,
    List<NodeStatisticsDTO> nodeStatistics
) {}
```

---

## 十、与业务核心模块的协作

### 10.1 事件驱动协作

```
┌─────────────────────────────────────────────────────────────────┐
│                  审批服务与业务核心模块协作                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  [业务申请提交]                                                  │
│       │                                                         │
│       ▼                                                         │
│  business-core-kernel                                           │
│       │                                                         │
│       │ 调用 ApprovalInstanceApi.start()                        │
│       ▼                                                         │
│  approval-service                                               │
│       │                                                         │
│       │ 发布 ApprovalInstanceCreatedEvent                       │
│       ▼                                                         │
│  business-core-kernel                                           │
│       │                                                         │
│       │ 更新业务申请状态 → 审批中                                │
│       ▼                                                         │
│  [审批完成]                                                      │
│       │                                                         │
│       │ 发布 ApprovalInstanceApprovedEvent                      │
│       ▼                                                         │
│  business-core-kernel                                           │
│       │                                                         │
│       │ 更新业务申请状态 → 通过                                  │
│       ▼                                                         │
│  [后续业务流程]                                                  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 10.2 依赖关系

- `approval-api` 可依赖 `business-core-api` 获取业务申请信息（如需要）
- 通过领域事件进行跨模块通信
- 审批服务不直接操作业务申请实体，仅通过事件通知状态变更

---

## 十一、待确认事项

| 序号 | 待确认事项 | 状态 |
|------|-----------|------|
| 1 | Gateway 接口实现：用户服务 API 待提供，先写伪代码 + TODO | 待实现 |
| 2 | 审批流配置的增删改查权限控制 | 待确认 |
| 3 | 审批效率统计的统计周期和维度 | 待确认 |

---

## 十二、附录

### A. 表关系图

```
approval_flow ──────▶ approval_node
       │
       └──────▶ approval_instance ──────▶ approval_node_execution
                                                    │
                                                    └──────▶ approval_record
```

### B. 参考文档

- [04-代码编写约束.md](file:///d:/WorkSpace/Trae/multiple-module-spring-cloud/.trae/rules/04-代码编写约束.md)
- [05-命名规范.md](file:///d:/WorkSpace/Trae/multiple-module-spring-cloud/.trae/rules/05-命名规范.md)
- [03-领域模型约束.md](file:///d:/WorkSpace/Trae/multiple-module-spring-cloud/.trae/rules/03-领域模型约束.md)