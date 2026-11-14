# 组织目录接入与 SAME_PLAN / LEVEL_UP 节点立项设计

> 生成日期：2026-11-14
> 依据：`2026-11-14-remaining-work-inventory.md` #1/#2（用户选择「立项组织目录」）、设计文档 §23.69/§23.72、当前代码逐项核实
> 性质：**立项方案，不是实施计划**。按「方案需与用户确认后才能进入编码阶段」，本文只产出核实结论、契约草案、分批设计与开放点，不写生产代码
> 约束：延续「不要太大超级计划」——每批 3–8 文件、独立 TDD 与验收

---

## 1. 结论先行

| 维度 | 结论 |
|---|---|
| 现状 | 组织目录的**领域模型与防腐层端口齐备**：`ProductGateway`（`findPlan/findProduct/findCustomer/ancestorsOf/descendantsOf/plansOfCustomer/plansOfProduct`）、`PlanSnapshot`（含 `parentPlanNo`）、`PlanReachabilityService.listAssignmentsForPlan`。唯一实现是 `StubProductGateway`（全部返回空/empty，仅供容器启动验证） |
| 真正缺口 | ① `StubProductGateway` 无真实数据来源；② auth-api 无对外组织目录查询契约；③ `ApprovalNodeResolver` 的 SAME_PLAN / LEVEL_UP 分支仍是 TODO（回退 `node.approverIds()`）；④ `RoleUserGatewayImpl.getUsersInSamePlan` 是未接线 stub |
| 外部依赖 | **仓库内没有外部组织目录主数据系统的任何线索**（无 yml 配置、无客户端、无 API）。「真实接入」需要提供方可对接的服务，或先落地可替换的契约+内存实现 |
| 建议 | 分 4 批（66–69）：先扩 auth-api 契约 → 补 auth 侧查询能力与可替换 ProductGateway → approval 侧接 `getUsersInSamePlan` 与 SAME_PLAN → LEVEL_UP 层级解析。**每批都能独立编译与验收**，Stub 保留兜底不破坏现有 E2E |

---

## 2. 现状核实（代码证据）

### 2.1 auth-service（供给侧，已具备大部分能力）

| 事实 | 位置 |
|---|---|
| `ProductGateway` ACL 端口，方法齐备，`requirePlan/requireProduct/requireCustomer` 默认方法抛 `ProductError` | `auth-domain/domain/product/ProductGateway.java` |
| `PlanSnapshot(planNo, productNo, customerNo, parentPlanNo, name, syncedAt)`——**`parentPlanNo` 是 LEVEL_UP 的关键** | `auth-domain/domain/product/PlanSnapshot.java` |
| `StubProductGateway` 全部返回 `Optional.empty()`/`List.of()`；Javadoc 已写明替换策略：真实实现标 `@Primary` 覆盖，或删除桩 | `auth-infrastructure/infrastructure/product/gateway/StubProductGateway.java` |
| `PlanReachabilityService.listAssignmentsForPlan(planId)` 已实现 PLAN/PRODUCT/CUSTOMER 继承匹配，但依赖 `orgDirectory.requirePlan()` + `ancestorsOf()`，且用 `assignmentRepository.findAllActive()` 全表过滤 | `auth-domain/domain/assignment/service/PlanReachabilityService.java` |
| `t_auth_assignment` 有 `user_no/role_code/scope_dimension/scope_value/status/deleted`，`AssignmentRepository.findAllActive()` 可用；批次 59 已新增 `findActiveByRoleCodesAndPlan` | `auth-infrastructure` / `auth-domain` |
| `RoleUserQueryApi`（批次 58）只有 `/by-roles`，无计划成员/计划层级端点 | `auth-api/api/RoleUserQueryApi.java` |
| auth-api 无组织/计划相关 `@HttpExchange` 接口 | 全仓核实 |

### 2.2 approval-service（消费侧）

| 事实 | 位置 |
|---|---|
| `approval-infrastructure` **已依赖 auth-api**（批次 60），`RoleUserGatewayImpl` 已用 `RoleUserQueryApi` | `approval-infrastructure/pom.xml` / `gateway/RoleUserGatewayImpl.java` |
| `RoleUserGateway` SPI 已有 `getUsersInSamePlan(PlanNo, UserNo)` 但实现是 stub（返回 `List.of()` + warn） | `approval-domain/gateway/RoleUserGateway.java` / `RoleUserGatewayImpl.java:60` |
| `ApprovalNodeResolver.SAME_PLAN` 分支取 `instance.initiatorPlan()` 后直接 `return node.approverIds()`（TODO） | `approval-domain/service/ApprovalNodeResolver.java:68` |
| `ApprovalNodeResolver.LEVEL_UP` 分支直接 `return node.approverIds()`（TODO，注释「需通过计划层级网关获取上级计划的用户」） | 同上 `:79` |
| `ApprovalNode.createSamePlanNode` / `createLevelUpNode` 已支持节点配置；`TerminalLevel` 值对象存在 | `approval-domain/aggregate/entity/ApprovalNode.java` |
| 批次 62 已把 Resolver 接入审批主链路（聚合根 `effectiveApprovers`），**本立项的解析结果会直接进入身份校验与会签判断，语义敏感** | 设计文档 §23.69 |

### 2.3 跨服务依赖规则

- 01 架构规则：**跨服务只依赖 API 模块**。approval-service 不能依赖 `auth-domain`/`auth-application`，只能经 `auth-api` 的 `@HttpExchange` 契约查询
- `httpexchange-spring-boot-autoconfigure` 按 `approval-starter/application-local.yml` 已配置的 `com.example.auth.api → lb://auth-service` 装配客户端（批次 60 已验证可行）
- `ProductGateway` 在 auth-domain，其真实实现属 auth-infrastructure；approval 侧**不应**复制该端口

---

## 3. 关键设计难点（均需决策）

1. **「真实组织目录」在仓库外**：没有任何外部主数据系统可调用。可选路径：① 本立项只定义契约 + 提供**可替换的内存实现**（从配置加载计划层级，让 SAME_PLAN/LEVEL_UP 在示例环境可工作），真实 `HttpProductGateway` 留待有提供方时接入；② 先立项「外部主数据系统对接」本身（超出本仓库）。推荐 ①。
2. **数据来源二选一**：`getUsersInSamePlan` 用 auth `t_auth_assignment` 按 PLAN/CUSTOMER/PRODUCT 继承匹配（`PlanReachabilityService` 已有），还是用外部组织目录的「计划成员表」？推荐前者（已有数据、无需新 DDL），外部目录只提供**计划层级**（`parentPlanNo`/`ancestorsOf`）用于 LEVEL_UP 与 CUSTOMER 继承。
3. **LEVEL_UP 语义**：`parentPlanNo` 只给一级父计划；「上一级审批」是取直接父计划的用户，还是沿 `ancestorsOf` 整条链？推荐：先取直接父计划（`parentPlanNo`），整条链列为后续。
4. **契约形态**：扩展现有 `RoleUserQueryApi`（新增 `/plan-members`、`/plan-parent`）还是新建 `PlanDirectoryApi`？推荐新建独立 `PlanDirectoryApi`（职责单一，避免角色查询接口膨胀）。
5. **安全与降级**：计划成员属敏感数据，需 `@RequirePermission`；外部目录不可用时 fail-closed 返回空（与批次 60/62 的 fail-closed 一致），避免误把他人任务展开为审批人。
6. **性能**：`PlanReachabilityService.listAssignmentsForPlan` 用 `findAllActive()` 全表过滤，数据量大时应补按 scope 的索引查询（06 规范），本立项可列为边界或顺带小批。

---

## 4. 建议的跨服务契约草案（待确认）

新建 `auth-api/PlanDirectoryApi`（`@HttpExchange("/plan-directory")`）：

| 端点 | 请求 CQE | 响应 | 说明 |
|---|---|---|---|
| `/members` | `ListPlanMembersRequest`（`@NotBlank planNo`、`excludeUserNo` 可选） | `ApiResult<PlanMembersResponse>`（`List<String> userNos`） | 对应 `RoleUserGateway.getUsersInSamePlan`，数据取 `t_auth_assignment` 的 PLAN 精确 + CUSTOMER/PRODUCT 继承匹配 |
| `/parent` | `GetParentPlanRequest`（`@NotBlank planNo`） | `ApiResult<ParentPlanResponse>`（`parentPlanNo` 可空） | 对应 LEVEL_UP，取 `PlanSnapshot.parentPlanNo` |

约束：响应只含编号不携敏感字段；`@RequirePermission` 保护；空/未知计划 fail-closed 返回空；`ProductGateway` 真实实现标 `@Primary` 覆盖 `StubProductGateway`（桩的 Javadoc 已给出替换策略）。

**approval 侧**：`RoleUserGatewayImpl` 注入 `PlanDirectoryApi` 实现 `getUsersInSamePlan`；`ApprovalNodeResolver` 的 SAME_PLAN 分支调 `getUsersInSamePlan`、LEVEL_UP 分支调 `/parent` 后按父计划取成员 —— Resolver 需新增一个 domain 层小端口（如 `PlanHierarchyGateway`）承接，避免直接依赖 auth-api（domain 层纯净性）。

---

## 5. 分批设计（编号接 65，开工时按实际顺延）

| 批次 | 内容 | 文件 | 验收 |
|---|---|---|---|
| **66（auth-api 契约）** | 新建 `PlanDirectoryApi` + `ListPlanMembersRequest`/`PlanMembersResponse`/`GetParentPlanRequest`/`ParentPlanResponse`；补契约测试 | 5–6 | `@HttpExchange`/`@PostExchange`/CQE 校验注解齐备 |
| **67（auth 供给侧）** | `PlanDirectoryQueryService`（复用 `PlanReachabilityService` + `ProductGateway`）、`PlanDirectoryController`；提供 `InMemoryProductGateway`（`@Primary`，从配置加载计划层级）替换 Stub 的**示例数据**；补仓储/服务/Controller 测试 | 6–8 | 计划成员按 scope 继承正确、父计划取 `parentPlanNo`、无数据时 fail-closed；auth 全模块与启动测试全绿 |
| **68（approval SAME_PLAN）** | domain 层新增 `PlanHierarchyGateway` 端口；`RoleUserGatewayImpl.getUsersInSamePlan` 接 `/members`；`ApprovalNodeResolver.SAME_PLAN` 分支改为调端口；补 Resolver/网关测试 | 5–7 | SAME_PLAN 命中同计划用户、排除发起人、auth 不可用 fail-closed；批次 62 的会签/身份校验零回归 |
| **69（approval LEVEL_UP）** | `PlanHierarchyGateway` 增加父计划查询；`ApprovalNodeResolver.LEVEL_UP` 分支按 `parentPlanNo` 取用户；`TerminalLevel` 边界；补测试 | 4–6 | 有父计划取父级成员、无父计划 fail-closed 回退、`TerminalLevel` 到顶不越级 |

**顺序**：66 → 67 → 68 → 69 串行（契约→供给→消费→层级）。每批独立 TDD、全仓验证、台账、自动提交推送；**Stub 保留兜底**，任一中间批次不破坏 annuity E2E。

---

## 6. 开放点（需用户确认，确认前不开工）

1. **范围**：是否接受「定义契约 + 可替换内存实现」作为「真实接入」的第一阶段（仓库外系统另行立项）？还是本立项必须对接一个真实外部服务（需提供地址/契约）？
2. **数据来源**：`getUsersInSamePlan` 用 auth `t_auth_assignment`（推荐）还是外部计划成员表？
3. **LEVEL_UP**：只取直接父计划（推荐）还是整条 `ancestorsOf` 链？`TerminalLevel` 到顶如何处置？
4. **契约归属**：新建 `PlanDirectoryApi`（推荐）还是扩展 `RoleUserQueryApi`？
5. **SAME_PLAN 的发起人**：`getUsersInSamePlan` 是否排除 `instance.initiatorPlan()` 对应的发起人？excludeUser 传谁？
6. **domain 端口**：`ApprovalNodeResolver` 是否允许新增 `PlanHierarchyGateway`（domain SPI）以隔绝 auth-api，还是把解析上移到 application 层？
7. **内存实现的数据**：`InMemoryProductGateway` 的计划层级从 `application.yml` 配置读取还是测试内固定 fixture？生产未接入时 fall-through 到 Stub 的 warn 是否可接受？

---

## 7. 不在本立项范围

- 外部组织目录主数据系统本身的建设/部署（仓库外）
- auth-service 用户/角色 CRUD、`t_auth_assignment` 写入侧改造
- `Map<String,Object>` 强类型化、真 Redis 集成测试等其它清单项
- `business-core-kernel`、annuity-service、intranet-bff 代码（除非开放点 4 触发契约同步）
- 新 DDL（优先复用 `t_auth_assignment`；若必须新增计划层级表，须同步 PG/MySQL 双库并单独立项）

---

## 8. 维护约定

- 本文只做核实与决策；用户确认 §6 后按 §5 顺序逐批开工，每批独立验收、独立提交。
- 「暂不做 / 待外部系统」项不是遗忘：结论与触发条件留在本文与 `remaining-work-inventory.md` #1/#2。
- 跨服务契约新增遵循 01/04/05/09 规范；零 DDL 为硬约束，确需建表须单独立项。
- 若核实发现某项已完成或描述过时，直接移入清单第 2 节纠偏表。
