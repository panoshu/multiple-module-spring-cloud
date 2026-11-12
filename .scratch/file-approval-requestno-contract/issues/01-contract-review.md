---
Status: ready-for-agent
---

# 独立评审：file/approval 的 requestNo 幂等与查询契约

> **性质**：§20 阶段 0 遗留决策，spec §11.4 / §12.3 / §9.2 阶段 B/C / §21.3。
> **门禁**：按 §22「file/approval 契约变更需要独立评审和契约测试」，评审通过前不进入编码。
> **发起**：批次 19 收尾后；关联台账 §23.1 阶段 0、§23.20。
> **范围**：`business-core-kernel`、`file-service`、`approval-service` 及其事件契约。
> **annuity-service**：按既定约定冻结，仅作为只读消费者；本材料不要求修改其业务代码（见 §6 待决问题 6）。

## 1. 评审目标

为 kernel §9.2 阶段 B「事务外派发」与阶段 C「回调关联」建立跨服务幂等基础，落实 §3.2 中「HTTP 超时不等于外部任务未创建」「MQ/HTTP 重试可能造成重复通知」「响应丢失」三条约束：

1. 同一外部请求重试不重复创建外部任务（创建幂等）。
2. 响应丢失后可按 requestNo 查询/对账，恢复 `externalTaskId`。
3. 回调事件可唯一关联回 `ExternalInteraction`（优先 requestNo、其次 externalTaskId，§7.3）。
4. 重复/乱序事件按 `eventId + consumer` 消费回执幂等（批次 15 已具备）。

## 2. 现状与差距（证据）

| 契约点 | 现状 | spec 要求 | 差距 |
|---|---|---|---|
| 文件解析创建幂等 | `UploadFileRequest` 已有 `clientRequestNo`；kernel `FileServiceIntegrationGateway.triggerAsyncParsing` 传 `formId.value()` | §11.4 创建任务携带稳定 clientRequestNo/requestNo、服务端按 requestNo 幂等 | 键与 `ExternalInteraction.requestNo` 不一致；`t_file_parse_task` DDL 未见 `client_request_no` 唯一约束 |
| 文件出站创建幂等 | `GenerateOutboundFileRequest(fileTaskId, targetTemplateId, supplementalData, operator)` 无 requestNo | 同上 | 完全缺失 |
| 审批启动幂等 | `StartApprovalRequest(flowId, businessNo, businessType, initiator)`，`businessNo = application.id().value()`；`ApprovalServiceIntegrationGateway.startApproval` 直接调 `approvalInstanceApi.start` | §12.3 增加 requestNo 或等价唯一约束；同一流程实例同一审批步骤禁止多个有效实例 | 无独立幂等键，重试会创建多个实例 |
| 完成事件关联 | `FileParsedEventDTO` / `OutboundFileGeneratedEventDTO` 只有 `eventId + fileTaskId`；approval 4 个 DTO 只有 `eventId + instanceId + businessNo` | §11.4 完成事件携带 requestNo 或提供按 requestNo 查询；§12.3 结果事件携带 requestNo 或保证 businessNo 一一对应 | 6 个 DTO 均无 requestNo，只能靠 fileTaskId/businessNo 间接关联 |
| 事件 ID | `EventId.generate()` + converter 用 `.value()` | §11.4 稳定 eventId | **已满足**，无需变更 |
| 按 requestNo 查询 | kernel 侧 `ExternalInteractionRepository.findByRequestNo` 已有；file/approval 侧未见 | §11.4/§12.3 提供按 requestNo 查询任务的 API | 跨服务查询缺失，`UNKNOWN` 对账无法完成（§23.17 仅对 APPROVAL 且有 externalTaskId 时走 `queryApprovalStatus`） |
| 失败/部分成功语义 | `FileParsedEventDTO` 有 `status` / `subTasks` / `failureReason` | §11.4 明确失败事件与部分成功语义 | 字段具备，但「部分成功如何映射到 StepExecution / ExternalInteraction 状态」未定义 |
| kernel 两套 requestNo | `StepExecution.requestNo = appId:stepCode:attempt`（VARCHAR(128)）、`ExternalInteraction.requestNo = SE-<stepExecutionId>`（VARCHAR(64)） | §7.3 单一稳定 requestNo | 两套键并存、列宽不一致，需统一对外的那一套 |
| 消费回执 | `EventReceipt` + `(event_id, consumer)` 唯一约束，3 个入站监听器短路（批次 15） | §9.2 阶段 C 第 2 步 | **已满足** |

## 3. 建议契约（提案，待评审拍板）

### 3.1 统一键

以 **`ExternalInteraction.requestNo`**（`SE-<stepExecutionId>`，§7.3 规定的稳定派生）作为**唯一跨服务幂等键**：

- 由 kernel 在事务内创建 `ExternalInteraction` 时生成、重试沿用（批次 12 已实现）；
- 所有外部创建请求（解析 / 出站 / 审批）携带该值作为 `requestNo` 或 `clientRequestNo`；
- 所有完成 / 结果事件回传该值；
- `StepExecution.requestNo` 保留为步骤尝试的审计键，不用于跨服务关联；
- 建议统一 `request_no` 列宽为 128（现有 64 不足以容纳 `申请单:步骤:attempt` 形态）。

### 3.2 file-service

- `UploadFileRequest.clientRequestNo` 语义明确为「外部协作 requestNo」，kernel 改传 `ExternalInteraction.requestNo` 而非 `formId.value()`。
- `GenerateOutboundFileRequest` 增加 `requestNo`。
- `t_file_parse_task` 与出站任务表增加 `request_no` 列 + 唯一索引 `uk_*_request_no`；创建任务按该键幂等（重复返回原 `fileTaskId`）。
- `FileParsedEventDTO` / `OutboundFileGeneratedEventDTO` 增加 `requestNo` 字段（兼容策略见 §4）。
- 新增按 `requestNo` 查询任务的 `@GetExchange` 接口（如 `FileTaskApi.getByRequestNo`）。

### 3.3 approval-service

- `StartApprovalRequest` 增加 `requestNo`（§12.3 首选方案）。
- `t_approval_instance` 增加 `request_no` 唯一约束；`ApprovalInstanceService.startApproval` 按该键幂等（重复返回原 `instanceId`）。
- 4 个 `ApprovalInstance*EventDTO` 增加 `requestNo`。
- 新增按 `requestNo` 查询实例的 `@GetExchange` 接口。

### 3.4 kernel 侧

- `FileIntegrationGateway.triggerAsyncParsing` / `triggerOutboundGeneration`、`ApprovalIntegrationGateway.startApproval` 增加 `requestNo` 入参（或引入 Parameter Object，遵守 04 §10.1 参数上限）。
- `FileServiceIntegrationGateway` / `ApprovalServiceIntegrationGateway` 透传该值；响应丢失时按 requestNo 查询恢复 `externalTaskId`。
- 阶段 B 事务外派发器（尚未建）以 `ExternalInteraction.requestNo` 为唯一派发键；超时进 `UNKNOWN` 后按 requestNo 对账（§18.3）。
- 阶段 C 回调按 `requestNo` 优先、`externalTaskId` 回退加载协作记录（§7.3；批次 12 `matches()` 已实现）。

### 3.5 事件契约

- 6 个 DTO 增加 `requestNo`（可空，兼容期）；`eventId` 保持 `EventId.value()` 字符串不变。
- 明确「部分成功」语义：`FileParsedEventDTO.status = PARTIAL` 时协作记录进入何种状态、是否触发重试，需给出映射表。

## 4. 兼容与迁移策略（待评审）

- 事件 DTO 新增字段为**破坏性变更**（构造签名变化）：影响 file/approval 转换器、kernel 3 个入站监听器、E2E 事件构造点与全部相关单测。建议一次性同步（无存量事件重放）或提供过渡重载。
- `StartApprovalRequest` / `GenerateOutboundFileRequest` 新字段可空过渡：kernel 侧先传值，服务侧旧版本忽略；唯一约束在存量数据清理后启用。
- `clientRequestNo` 语义收窄后，需评估现有按 `formId` 幂等的调用方是否有依赖。
- 无生产数据的新表可直接加列 + 唯一索引；file/approval 存量表需迁移脚本。
- DDL 需同步 kernel PG/MySQL 与 annuity 三份复制脚本 —— **与「冻结 annuity」冲突**，见 §6 待决问题 6。

## 5. 契约测试要求（§21.3 / §22）

评审通过后，编码前先写失败测试：

1. 相同 requestNo 重复创建解析 / 出站 / 审批任务 → 只产生 1 个外部任务，返回原 `fileTaskId` / `instanceId`。
2. 响应丢失后按 requestNo 查询 → 恢复 `externalTaskId`，协作进入 `WAITING_CALLBACK`。
3. 请求超时 → `UNKNOWN`，对账确认后才进 `SUCCEEDED` / `FAILED`，不重复创建任务。
4. 重复 / 乱序完成事件 → 按 `eventId + consumer` 回执短路，流程状态不变。
5. 文件部分成功 → 不误判为全部成功，协作状态按映射表落位。
6. 服务重启后 `PENDING_DISPATCH` 协作可恢复并派发。
7. 审批通过 / 驳回 / 撤回分别进入正确流程状态；同一流程实例同一审批步骤不得创建多个有效实例。
8. 架构不变量：domain 不依赖 file/approval DTO；application 不依赖 file/approval DTO（外部 DTO 只在 infrastructure 或入站适配器出现）。

## 6. 待决问题（需评审拍板）

1. 对外统一键选 `ExternalInteraction.requestNo`（建议）还是 `StepExecution.requestNo`？两者是否都保留、分别承担什么语义？
2. `StartApprovalRequest` 采用「新增 requestNo」还是「`businessNo` + 步骤 唯一约束」等价方案（§12.3 允许二选一）？
3. 事件 DTO 新增字段是一次性破坏性变更，还是提供可空过渡期 + 双读？
4. 按 requestNo 查询 API 的路径 / 返回体 / 权限边界；是否复用现有 `queryApprovalStatus` 模式。
5. 「部分成功」到 `StepExecution` / `ExternalInteraction` 状态的精确映射表。
6. **annuity 冻结的处理**：`MockFileIntegrationGateway` 与 E2E 事件构造点在 annuity；契约改动后该测试替身会编译失败。选项：(a) 评审批准临时解冻 annuity 的测试替身（仅测试代码，不算业务适配）；(b) 在 kernel 侧新建自带 E2E 基座替代 annuity E2E；(c) 保持 annuity 不变、契约变更只在 kernel 默认实现 + 单测验证，接受 E2E 覆盖缺口。需明确选哪条。
7. `request_no` 列宽统一为 128 是否纳入本次（涉及 5 份 DDL）。
8. 阶段 B 派发器与阶段 C 回调是否随契约一并实现，还是拆为独立批次（建议拆：契约 → 派发器 → 回调）。

## 7. 影响面清单（文件级，供评审估算）

- `file-service/file-api`：`UploadFileRequest`、`GenerateOutboundFileRequest`、4 个事件 DTO、`FileTaskApi` / 出站 API
- `file-service/file-domain` / `file-infrastructure`：`FileParsedEvent` / `OutboundFileGeneratedEvent`、两个 EventConverter、`t_file_parse_task` DDL 与幂等实现
- `approval-service/approval-api`：`StartApprovalRequest`、4 个事件 DTO、`ApprovalInstanceApi`
- `approval-service/approval-application` / `approval-infrastructure`：`ApprovalInstanceService.startApproval` 幂等、`t_approval_instance` DDL
- `business-core-kernel/business-core-domain`：`FileIntegrationGateway` / `ApprovalIntegrationGateway` 签名
- `business-core-kernel/business-core-infrastructure`：两个 IntegrationGateway 实现
- `business-core-kernel/business-core-application`：4 个挂起 Handler、阶段 B 派发器（待建）、3 个入站监听器
- 测试：上述各模块契约测试 + kernel 编排器 / 监听器单测 + 架构不变量

## 8. 验收标准

- [ ] §6 的 8 个待决问题全部有结论，记录到本文 `## Comments` 或台账
- [ ] §5 的 8 项契约测试先写失败、再实现、全绿
- [ ] 架构不变量 7 项通过，全仓 `test-compile` 通过
- [ ] 契约变更不破坏 annuity E2E（或按 §6.6 决议替换验证基座）
- [ ] 台账 §23.1 阶段 0 更新为「契约已冻结」

## Comments

- 2026-11-12：由批次 19 收尾发起，状态 `needs-triage`，等待评审。评审通过前不进入编码（§22）。
- 2026-11-12（评审结论）：**提案 §3 整体接受**，状态转为 `ready-for-agent`，门禁解除。8 个待决问题结论如下：
  - #1 统一键：以 `ExternalInteraction.requestNo`（`SE-<stepExecutionId>`）为唯一跨服务幂等键；`StepExecution.requestNo` 保留为步骤尝试审计键，不用于跨服务关联。
  - #2 `StartApprovalRequest` **新增 `requestNo`**（不采用 `businessNo + 步骤` 等价约束）。
  - #3 事件 DTO 新增 `requestNo` 采用**可空过渡**（无存量事件重放）；契约落地后收紧为非空。
  - 2026-11-12（实现注记）：批次 20 按提案 §4 的「**过渡重载**」选项落地——8 个 DTO 保留旧参数列表的辅助构造器（委托 canonical 构造并传 `null`），全仓 30 处既有构造点零改动。原「一次性同步构造签名」措辞据此修正为「可空过渡 + 旧签名兼容重载」。服务侧幂等 / 查询 API / 事件转换器透传 / kernel Gateway 签名因与派发器强耦合，并入批次 21（见台账 §23.21）。
  - #4 新增按 `requestNo` 查询 API，沿用 `queryApprovalStatus` 的「按外部标识查询」模式；具体路径 / 返回体在实现批次确定。
  - #5 部分成功默认映射：`PARTIAL` → `StepExecution.RETRY_WAIT` + `ExternalInteraction.RETRY_WAIT`，保留子任务结果引用、不推进流程；只有全部成功才 `SUCCEEDED`。实现批次可细化。
  - #6 annuity 冻结：**临时解冻测试替身**——仅允许改 `MockFileIntegrationGateway` 与 E2E 事件构造点（测试代码），不改 annuity 业务实现；DDL 复制脚本本次无需动（kernel 侧列宽统一只改 kernel PG/MySQL，annuity 写入值短不受影响）。
  - #7 `request_no` 列宽统一为 128，纳入本次。
  - #8 拆三批：**批次 20 契约变更 → 批次 21 阶段 B 派发器 → 批次 22 阶段 C 回调**。
  - 编码仍按「先写失败测试 → 实现 → 跑测试」小步提交，每批跑契约测试与架构不变量。
