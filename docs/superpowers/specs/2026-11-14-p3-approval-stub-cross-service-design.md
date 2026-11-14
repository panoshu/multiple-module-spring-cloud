# P3-1 专项设计：approval-service stub 跨服务立项

> 生成日期：2026-11-14
> 依据：`2026-11-14-remaining-work-priority.md` P3-1、CODEBUDDY.md Known Gaps、当前代码逐项核实
> 性质：**立项方案，不是实施计划**。按「方案需与用户确认后才能进入编码阶段」，本文只产出核实结论、边界、分批设计与开放点，不写任何生产代码、不改现有契约
> 约束：延续「不要太大超级计划」——每个批次 3–8 文件、独立 TDD 与验收
> **推进状态（2026-11-14）**：批次 53/54/55 已全部完成，3 个 service stub 清零。能力 C 按 §4 建议的「最小可用版本」落地，§6 开放点逐项处置结论见设计文档 §23.62；**批次 56 已处置 ④**（approver 越权：会话账号覆盖 + fail-closed，BREAKING CHANGE 已同步 intranet-bff 的会话 header 透传，见 §23.63）；角色审批（需用户服务新契约）、审批操作类越权、`RoleUserGatewayImpl` stub 保留为遗留。

---

## 1. 结论先行

P3-1 不是「补 3 个 service 方法」，而是**一条跨服务管理端查询链路**，且牵出 2 个清单未记录的隐藏 stub 与 1 个安全设计缺陷。

| 维度 | 结论 |
|---|---|
| 范围 | approval-service 内部 + intranet-bff 消费契约；**kernel 零改动**（`ApprovalIntegrationGateway` 只有 `startApproval`/`queryApprovalStatus`） |
| 真实工作量 | 3 个 service stub + 2 个基础设施 stub（待审批人查询、角色用户网关）+ 分页/转换/测试 |
| 最大阻塞 | 「按审批人查待审批实例」的数据模型不支持 DB 直分页；`RoleUserGateway` 的用户服务提供方**仓库内不存在** |
| 建议 | 先做零外部依赖的 `getApprovalHistory`（批次 53），再做 `listApprovalFlows`（54）；`listMyPendingApprovals`（55）**须先确认 §6 开放点** |
| 安全 | `ListMyPendingApprovalsRequest.approver` 由请求体传入且 BFF 透传，**无「是否当前登录人」校验**，可查他人待审批列表——设计时必须处置 |

---

## 2. 现状核实（非照抄清单）

### 2.1 清单点名的 3 个 stub

| 方法 | 位置 | 现状 |
|---|---|---|
| `listApprovalFlows` | `ApprovalFlowService:154` | `throw new UnsupportedOperationException("待实现分页查询逻辑")` |
| `listMyPendingApprovals` | `ApprovalInstanceService:256` | `throw new UnsupportedOperationException("待实现待审批列表查询逻辑")` |
| `getApprovalHistory` | `ApprovalInstanceService:269` | `return List.of()`（注释 `TODO: 转换审批记录`） |

调用点只有 `ApprovalFlowAdapter.list` / `ApprovalInstanceAdapter.listMyPending` / `getHistory`，三者都是 `@HttpExchange` 对外契约，返回 `ApiResult<PageData<...>>` 或 `ApiResult<List<...>>`。

### 2.2 核实新发现的隐藏 stub（清单未记录）

| 位置 | 现状 | 影响 |
|---|---|---|
| `ApprovalInstanceRepositoryImpl.findByApproverId` | 返回 `Collections.emptyList()` + `log.warn`；注释写「需复杂的关联查询，可能要设计专门的待审批视图表或缓存机制」 | `listMyPendingApprovals` 的领域查询能力根本不存在 |
| `RoleUserGatewayImpl.getUsersByRole` / `getUsersInSamePlan` | 均 `return List.of()`（TODO「待用户服务 API 提供」） | 角色型审批人（`t_approval_node.role_ids`）无法展开 |

**提供方核实**：auth-service 只有 `RoleTemplateRepository`/`RoleTemplateResolver`（角色模板），**没有「角色 → 用户列表」API**；仓库内无其它实现。即角色审批人链路缺跨服务契约。

### 2.3 纠偏：并非 stub 的 `List.of()` / `emptyList()`

`ApprovalFlowConverter` 的 `jsonToUserNoList`/`jsonToStringList`、`ApprovalNodeResolver:35`、各 `RepositoryImpl` 的空集合返回，均为**正常的空值防御或配置解析**，不是待办。清单第 2 节「纠偏」口径同样适用，勿误排期。

### 2.4 跨服务边界（本立项的核心）

```
intranet-bff  ApprovalManagementService
   ├─ approvalFlowApi.list(...)      ──> approval-service /api/approval/flows
   ├─ approvalInstanceApi.listMyPending(...) ──> /api/approval/instances  ← 本立项
   └─ approvalInstanceApi.getHistory(...)     ──> /api/approval/instances

kernel  ApprovalIntegrationGateway.startApproval / queryApprovalStatus
   └─ 与上述 3 个管理端 API 无交集 → kernel 零改动

auth-service（角色→用户）── 当前无 API ──> RoleUserGateway（未接通）
```

---

## 3. 关键事实：契约、数据模型、分页

**契约**
- `Pagination(startPos, pageSize)`：offset 式，`pageSize` 5–100（构造校验）。
- `PageData(totalCount, currentStart, returnedCount, hasMore, items)`：`currentStart` 是记录偏移，非页码。
- `ApprovalFlowDTO` 含 `matchRules` + `List<ApprovalNodeDTO>`；`PendingApprovalDTO` 含 `flowName`/`currentNodeName`/`initiator`；`ApprovalRecordDTO` 含 `nodeName`/`actionType`/`operator`/`comment`/`operatedAt`。
- 现成模式：`FileTaskAdapter.listSubTasks`（内存分页 + `new PageData<>(total, start, size, hasMore, items)`）、`ParsedDataAdapter.fetchRows`（useCase 返回 PageInfo）。两者都可复用。

**数据模型**（决定难度）
- `t_approval_instance`：**无审批人字段**，只有 `flow_id` / `current_node_order` / `status` / `business_no` / `business_type` / `created_by` / `create_time`，已有 `idx_..._status`、`idx_..._create_time`。
- `t_approval_node`：`approver_type` / `approver_ids TEXT` / `role_ids TEXT` / `sign_mode`——**审批人配置在静态节点定义里**。
- `t_approval_node_execution`：无审批人字段；`t_approval_record.approver_id` 只记**已操作**的人。
- `ApprovalInstance` 聚合根内含 `List<NodeExecution>`，每个含 `List<ApprovalRecord>` → **`getApprovalHistory` 可零 DDL 实现**。
- `ApprovalFlowService` 已有私有 `convertToDTO(ApprovalFlow)`（含 nodes/matchRules）→ **`listApprovalFlows` 可复用**。
- `ApprovalFlowRepository` 已有 `findByStatus`（返回全量、无分页）；`ApprovalRecordMapper` 为空接口（继承 BaseMapper）。

**分页难度**：按审批人过滤需「实例 → 节点定义 → 展开 approverIds/roleIds → 判断当前用户」的应用层计算，DB 侧无法直接 WHERE；offset 分页在过滤后语义下 `totalCount` 需全量过滤后计数。这正是 `findByApproverId` stub 注释所指。

---

## 4. 三个能力的难点与建议处置

| 能力 | 难点 | 建议处置 |
|---|---|---|
| **A `getApprovalHistory`** | `ApprovalRecordDTO.nodeName` 需按 `flowId + nodeOrder` 查节点定义；节点可能已改版/删除 | 从聚合根取 records，按实例 `flowId()` 加载 `ApprovalFlow` 映射节点名；取不到时以 `nodeId`/`nodeOrder` 降级并在台账标注。**零外部依赖** |
| **B `listApprovalFlows`** | 条件过滤（businessType/status）+ 分页；`findByStatus` 只返回全量 | 新增 `ApprovalFlowRepository.findByCondition(businessType, status, pagination)` 返回 `(total, items)`，DB 侧 `count` + `limit/offset` 走索引（06 规范禁全表扫）；复用 `convertToDTO` |
| **C `listMyPendingApprovals`** | ① 审批人归属需应用层展开；② `role_ids` 依赖缺失的用户服务 API；③ `approver` 由请求体传入有越权风险；④ `flowName`/`currentNodeName`/`initiator` 需补充加载 | **须先确认 §6 开放点**。最小可用版本：只支持 `approver_ids` 指定用户、内存过滤后分页、approver 改由会话上下文取；`role_ids` 与越权校验按用户决策 |

---

## 5. 分批设计（编号接 52，开工时按实际顺延）

| 批次 | 内容 | 文件 | 验收 |
|---|---|---|---|
| **53（能力 A）** | 实现 `getApprovalHistory`：聚合根取 records → 补 nodeName → DTO 转换；补 `ApprovalInstanceService` / Converter / Controller 契约测试 | 3–4 | TDD 先红后绿；多节点多记录、空历史、节点缺失降级三类断言 |
| **54（能力 B）** | `ApprovalFlowRepository.findByCondition` + Impl（QueryWrapper 条件 + count/limit）+ `listApprovalFlows` 复用 `convertToDTO`；补仓储/应用/适配测试 | 4–6 | 条件过滤、分页边界（startPos 越界/边界 pageSize）、空结果 `PageData.empty()` |
| **55（能力 C）** | 依赖开放点确认后：`findByApproverId` 真实实现 + `listMyPendingApprovals` + approver 来源/越权处置 + DTO 补充字段；可能含双库 DDL（视图表或冗余列） | 5–8（+2 DDL） | 指定用户/角色（若支持）过滤、分页一致性、越权拒绝、flowName/nodeName 正确 |

**每批通用验收口径**（沿用批次 1–52）：TDD 先红后绿；approval 各模块测试 + intranet-bff 相关契约测试 + 全仓 `mvn test-compile` + `git diff --check`；台账补 `§23.x` 边界说明；收口批次回填提交号；按 Conventional Commits 提交、批次完成自动 commit + push。

**顺序**：53 → 54 可连续推进；55 必须先确认开放点。三批互不阻塞的部分（A/B）不依赖 C 的决策。

---

## 6. 开放点（需用户确认，确认前不开工）

1. **能力 C 的审批人范围**：① 只支持 `approver_ids` 指定用户（最小、无需外部契约）② 支持 `role_ids`（需先立项「用户服务角色→用户 API」跨服务新契约，超出本立项）③ 暂不做 C、只交付 A/B。
2. **分页实现**：DB 分页（新增 Repository 方法 + `count`，符合 06 规范）vs 内存分页（参考 file-service，审批流定义量小可接受）。待审批列表因过滤在应用层，倾向后者。
3. **是否新增 DDL**：待审批视图表 / `t_approval_instance.current_approvers` 冗余列 + 索引。涉及双库 `schema-pg.sql`/`schema-mysql.sql` 与存量数据回填，成本高；若不做，须接受「全量扫描活跃实例后内存过滤」的性能边界并如实标注。
4. **`approver` 参数来源与越权**：现状请求体传入、BFF 透传、无「是否当前登录人」校验。建议改为会话上下文（`SessionClaims`）取 approver 并移除/忽略请求体字段；是否一并补 `@RequirePermission` 的数据范围校验需定。此为**破坏性契约变更**（BFF 需同步）。
5. **`getApprovalHistory` 的 nodeName 降级**：节点定义查不到时返回 `nodeId`/`nodeOrder` 还是抛业务异常。
6. **`RoleUserGatewayImpl` stub 是否一并清理**：它属「用户服务 API 提供方」缺失，可能需单独立项；本立项建议只在台账记录，不改。

---

## 7. 不在本立项范围

- `business-core-kernel` 全部代码（核实零交集）。
- annuity-service 与其余业务服务。
- `RoleUserGateway` 的用户服务 API 提供方实现（需独立立项）。
- approval-service 其它已实现功能的重构；`ApprovalFlowConverter`/`ApprovalNodeResolver` 的正常空值防御。
- 多进程真 Redis 锁集成测试等既有遗留。

---

## 8. 维护约定

- 本文只做核实与分批决策；用户确认 §6 后按 §5 顺序逐批开工，每批独立验收、独立提交。
- 「暂不做/待立项」项不是遗忘：结论与触发条件留在本文与优先级清单 P3-1 行，避免后续会话重复评估。
- 跨服务契约变更（开放点 4）须在提交信息标 `BREAKING CHANGE:` 并同步 intranet-bff 消费端。
- 若核实发现某项已完成或描述过时，直接移入主清单第 2 节纠偏表。
