# 用户服务「角色→用户」API 立项设计

> 生成日期：2026-11-14
> 依据：`2026-11-14-remaining-work-priority.md` P3-1 遗留、`2026-11-14-p3-approval-stub-cross-service-design.md` §6 开放点 ①、设计文档 §23.62/§23.64、当前代码逐项核实
> 性质：**立项方案，不是实施计划**。按「方案需与用户确认后才能进入编码阶段」，本文只产出核实结论、契约草案、分批设计与开放点，不写任何生产代码、不改现有契约
> 约束：延续「不要太大超级计划」——每个批次 3–8 文件、独立 TDD 与验收

---

## 1. 结论先行

| 维度 | 结论 |
|---|---|
| 立项目标 | 解锁 approval-service 的角色审批（`SPECIFIED_ROLE` 节点），替掉 `RoleUserGatewayImpl` 的 stub |
| 供给侧基础 | auth-service **已具备数据**：`t_auth_assignment`（user_no / role_code / scope_dimension / scope_value / status），但**缺「按角色查用户」的仓储方法与对外 API** |
| 最大风险 | 现 SPI `getUsersByRole(String roleId)` **缺 scope**。auth 的分配是 PLAN/CUSTOMER/PRODUCT/GLOBAL 维度，直接按角色查会返回全平台同角色用户 → **数据范围错误**，契约必须先补 scope |
| 隐藏债务 | `ApprovalNodeResolver` **无调用方**（未接线）、`getUsersInSamePlan` **完全无消费方**；`ApprovalInstance.allApproversApproved` 对角色节点仅做 `roleIds().isEmpty()` 防御，角色节点会签判断不完整 |
| 建议 | 分批 58–61：先落 auth 侧契约与查询能力，再改 approval 网关，最后解锁能力 C 并修正领域流转；**先只做 `SPECIFIED_ROLE`**，SAME_PLAN/LEVEL_UP 与组织目录接入留后续 |

---

## 2. 现状核实

### 2.1 消费侧（approval-service）

| 事实 | 核实结果 |
|---|---|
| SPI 契约 | `approval-domain/gateway/RoleUserGateway`：`List<UserNo> getUsersByRole(String roleId)`、`List<UserNo> getUsersInSamePlan(PlanNo, UserNo)` |
| 实现 | `approval-infrastructure/gateway/RoleUserGatewayImpl`：两个方法均 `return List.of()` + `log.warn`，TODO 标注「待用户服务 API 提供」 |
| `getUsersByRole` 调用点 | 仅 `ApprovalNodeResolver.resolveApprovers`（domain 领域服务，遍历 `node.roleIds()` 展开）；**该 Resolver 全仓无调用方**（预留 SPI 未接线） |
| `getUsersInSamePlan` 调用点 | **无**（仅接口与 Impl 声明） |
| `roleIds` 类型 | `ApprovalNode.roleIds()` 是 `List<String>` 自由字符串（来自流程定义配置 JSON），非强类型 |
| 能力 C 现状 | `listMyPendingApprovals` 只认 `isSpecifiedUserApproval()`，`SPECIFIED_ROLE` 节点跳过（§23.62） |
| 领域流转 | `ApprovalInstance.allApproversApproved`：`approverIds` 为空时返回 `node.roleIds().isEmpty()`，即角色节点只要有 roleIds 就**永不判定完成**（防御性，避免会签节点首次审批即误判完成），未展开角色用户数 |

**结论**：`RoleUserGateway` 目前是**未接线的预留 SPI**。立项不只是「补一个 HTTP 客户端」，还牵出「角色节点如何正确流转」的领域问题。

### 2.2 供给侧（auth-service）

| 事实 | 核实结果 |
|---|---|
| 数据表 | `t_auth_assignment`：`user_no` / `role_code` / `scope_dimension` / `scope_value` / `inheritable` / `status` / `deleted` / `version` 齐备（PG + MySQL 双库） |
| scope 语义 | `AssignmentScopeDimension` = `PLAN` / `CUSTOMER` / `PRODUCT` / `GLOBAL`；`AgentIdentityAssignment` 有 `scopeDimension()`/`scopeValue()`/`roleCode()`/`userNo()`/`isActive()`/`isInheritable()` |
| `RoleCode` | `auth-types` 的 `record RoleCode(String value)`，注释「数据驱动，不写死枚举」 |
| 仓储 | `AssignmentRepository` 仅 `findActiveByAccount(UserNo)`、`findAllActive()`；`AssignmentRepositoryImpl` 用 MyBatis-Flex `QueryWrapper`，**无按角色/按 scope 的查询方法**（新增容易） |
| 领域服务 | `PlanReachabilityService.listAssignmentsForPlan(PlanNo)` 已实现 PLAN/CUSTOMER/PRODUCT 继承匹配，但依赖 `ProductGateway.requirePlan/ancestorsOf`；该网关当前**只有 `StubProductGateway`**（生产不可用） |
| 权限域端口 | `PlanMembershipLookup`（`isMemberOf` / `hasRole`）是**用户→角色**方向，与本立项**角色→用户**相反，不能直接复用 |
| 对外 API | auth-api 现有 8 个 `@HttpExchange` 接口（权限/菜单/会话/渠道/二次授权），**无 Assignment / 角色用户查询接口**；风格为 `@PostExchange` + `@RequestBody @Valid` CQE + `ApiResult<T>` |

### 2.3 跨服务依赖与装配

- 01 架构规则：**跨服务只依赖 API 模块** → approval-infrastructure 可依赖 `auth-api`，不可依赖 auth-domain/application/adapter
- 父 `pom.xml` 的 `dependencyManagement` **已管理** `auth-api` 版本 ✅
- approval 各模块 pom **未显式依赖** auth-api；但 `approval-starter/application-local.yml` **已配置** `httpexchange.clients.com.example.auth.api = lb://auth-service`（权限校验用）
- `shared-permission-starter` 注释明确「A2 起不再依赖 auth-api」，靠 `shared-security-contract` 端口解耦 → 本立项需在 `approval-infrastructure/pom.xml` **显式新增 auth-api 依赖**
- 跨服务依赖先例：`intranet-bff-web`、`channel-gateway`、`arch-tests` 均已依赖 auth-api ✅

---

## 3. 关键设计难点（6 个，均需决策）

1. **scope 缺失（最高优先）**：`getUsersByRole(String roleId)` 无 scope。auth 分配锚定在计划/客户/产品/全局维度，不带 scope 的查询会把全平台同角色用户返回给审批流，导致「A 计划的审批任务出现在 B 计划用户待办里」。新 API 契约必须带 `scopeDimension + scopeValue`（或至少 `planNo`），并明确 GLOBAL 角色的处理。
2. **roleId 语义对齐**：approval 的 `roleIds: List<String>` 是配置自由字符串，auth 用 `RoleCode(String)`。需约定：① 直接按字符串值匹配 `role_code`；② auth 侧校验角色是否存在并拒绝未知角色；③ 在审批流定义发布期校验（可复用批次 34 的 `ProcessDefinitionPublishService` 条件预校验思路）。
3. **N+1 与性能**：能力 C 目前对**每个活跃实例**加载 flow 定义并判断当前节点审批人；角色展开若每实例一次跨服务 HTTP，会 N+1。需**批量接口**（一次传多个 roleId + 统一 scope）或进程内短 TTL 缓存，并在 auth 侧支持 `role_code IN (...)` 查询。
4. **未接线 Resolver 与领域流转**：`ApprovalNodeResolver.resolveApprovers` 无调用方；`allApproversApproved` 对角色节点只做防御。若不接线，角色节点即使能查到用户也无法正确判断会签完成。这是比「待办列表」更深的领域正确性改造。
5. **`getUsersInSamePlan` / SAME_PLAN / LEVEL_UP 节点**：`getUsersInSamePlan` 无任何消费方；`ApprovalNodeResolver` 里 SAME_PLAN / LEVEL_UP 分支仍是 TODO（回退 `node.approverIds()`）。是否纳入本立项需定；推荐**先只做 `SPECIFIED_ROLE`**，其余保留 TODO 并如实标注。
6. **组织目录依赖**：`PlanReachabilityService.listAssignmentsForPlan` 依赖 `ProductGateway`（仅 Stub）。若 API 要支持「按计划范围展开角色」，需绕开组织目录（直接按 `scope_dimension=PLAN AND scope_value=planNo` 精确查）或先接入真实组织目录；后者超出本立项。

---

## 4. 建议的 API 契约（草案，待确认）

新增 `auth-api/RoleUserQueryApi`（`@HttpExchange("/role-users")`，仅 GET/POST 交换）：

| 端点 | 请求 CQE | 响应 | 说明 |
|---|---|---|---|
| `/by-roles` | `ListRoleUsersRequest`（`List<String> roleCodes`、`AssignmentScopeDimension scopeDimension`、`String scopeValue`、可选分页） | `ApiResult<RoleUserBatchResponse>`（`Map<String, List<String>>` roleCode→userNos） | 批量，规避 N+1 |
| `/plan-members` | `ListPlanMembersRequest`（`PlanNo planNo`、`UserNo excludeUserNo`） | `ApiResult<List<String>>` | 可选，对应 `getUsersInSamePlan`；推荐**暂不实现** |

约束：
- 响应只返回 `UserNo` 字符串列表，**不返回手机号/姓名等敏感字段**
- 服务端按 `@RequirePermission(business = "AUTH_ASSIGNMENT", action = "VIEW", category = PLATFORM)` 保护；跨服务调用需带会话 header（沿用批次 56 的 `AccountId/AccountSig` 透传）
- 只查 `status=ACTIVE` 且 `deleted=0` 的分配（逻辑删除由 MyBatis-Flex 自动过滤）
- `scopeValue` 与 `roleCode` 均参数化查询（QueryWrapper），禁字符串拼接（06 规范）

**approval 侧**：`RoleUserGatewayImpl` 改为注入 `RoleUserQueryApi`（`@HttpExchange` 客户端），把 `getUsersByRole(roleId)` 的单角色调用适配为批量请求的单元素入参；同时需给 SPI 补 scope（领域层签名变更，见开放点 1）。

---

## 5. 分批设计（编号接 57，开工时按实际顺延）

| 批次 | 内容 | 文件 | 验收 |
|---|---|---|---|
| **58（auth-api 契约）** | 新增 `RoleUserQueryApi` + `ListRoleUsersRequest` / `RoleUserBatchResponse`；父 pom 无需改（已管理 auth-api） | 3–4 | `@HttpExchange` 风格与现有 8 接口一致；CQE 校验注解齐备 |
| **59（auth 供给侧）** | `AssignmentRepository.findActiveByRoleCodesAndScope(...)` + Impl（`role_code IN` + scope 条件 + 索引）；auth-application 查询服务；auth-adapter Controller（含权限） | 5–7 | 仓储按角色/scope 过滤、ACTIVE、逻辑删除；应用服务去重；Controller 权限校验；单元 + 契约测试 |
| **60（approval 网关）** | `approval-infrastructure/pom.xml` 加 auth-api；`RoleUserGatewayImpl` 改为 `@HttpExchange` 客户端（含超时/降级）；SPI 补 scope 参数 | 4–6 | 契约测试：批量展开、空结果、auth 不可用降级；零 annuity 改动 |
| **61（能力 C 解锁 + 领域接线）** | `listMyPendingApprovals` 支持 `SPECIFIED_ROLE`（批量展开 + 缓存）；`ApprovalNodeResolver` 接线；`allApproversApproved` 修正角色节点会签判断 | 6–8 | 角色节点待办命中、会签完成判断、非角色节点零回归；若超 8 文件再拆 61/62 |

**顺序**：58 → 59 → 60 串行（契约→供给→消费）；61 依赖 60。每批独立 TDD、全仓验证、台账、自动提交推送。

---

## 6. 开放点（需用户确认，确认前不开工）

1. **scope 契约**：`getUsersByRole` 是否改为带 `scopeDimension + scopeValue` 的领域签名？（推荐：是。不带 scope 会造成跨计划数据泄漏，是本立项最关键的决策。可能需同步改 `ApprovalNodeResolver` 与流程定义模型）
2. **纳入范围**：是否只做 `SPECIFIED_ROLE`（推荐），还是连 `getUsersInSamePlan` / SAME_PLAN / LEVEL_UP 节点一并实现？后者需组织目录真实接入，规模显著扩大。
3. **批量 vs 单角色 + 缓存**：推荐批量接口（`/by-roles`）。若采用单角色接口，需明确缓存策略与 TTL。
4. **`ApprovalNodeResolver` 接线与 `allApproversApproved` 修正是否纳入**：推荐纳入（否则角色审批流转仍不正确），但会触及领域层，建议独立批次 61。
5. **auth 不可用时的降级**：fail-closed（拒绝角色节点审批 / 待办列表不显示角色节点）还是 fail-open（回退到指定用户）？推荐 fail-closed + 明确告警，避免静默降级。
6. **`ProductGateway` 组织目录**：推荐本立项**不接入**，按 `scope_dimension=PLAN` 精确查询绕开；CUSTOMER/PRODUCT 继承展开列为后续。
7. **权限与数据范围**：跨服务「角色→用户」属敏感数据，是否需额外的调用方白名单 / 数据范围（`@DataScope`）校验，还是仅靠 `@RequirePermission`？

---

## 7. 不在本立项范围

- `ProductGateway` 真实组织目录接入（CUSTOMER/PRODUCT 继承展开）
- auth-service 用户 CRUD、角色模板管理、授权引擎改造
- approval 审批流定义配置模型的重构（roleIds → 强类型 RoleCode）
- `business-core-kernel`、annuity-service、intranet-bff（除非开放点 1 触发契约变更需要同步）
- `startApproval` 发起人身份治理等既有遗留

---

## 8. 维护约定

- 本文只做核实与决策；用户确认 §6 后按 §5 顺序逐批开工，每批独立验收、独立提交。
- 「暂不做 / 待立项」项不是遗忘：结论与触发条件留在本文与优先级清单，避免后续会话重复评估。
- 跨服务契约新增须遵循 01/04/05/09 规范；涉及 DDL 的批次须同步 PG/MySQL 双库。
- 若核实发现某项已完成或描述过时，直接移入主清单第 2 节纠偏表。
