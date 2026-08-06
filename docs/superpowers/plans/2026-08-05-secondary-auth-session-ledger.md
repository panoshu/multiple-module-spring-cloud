# SecondaryAuthSession 实施进度账本

## 已完成任务

### Task 1-24: SecondaryAuthSession 聚合根实施 ✅

- **状态**: 全部 Task 已完成
- **测试**: auth-domain 17/17 通过

---

## 技术债处理（第二批）

### 规则对齐

#### 规则 08 补充 AUTH 模块缩写 ✅

- **Commit**: 规则文件更新
- **变更**: 在 `08-错误码规范.md` 的 SERVICE 域补充 `AUTH` 模块缩写及内部码段分配表（01xx-07xx）
- **原因**: auth-service 错误码缺少正式注册的模块缩写，导致历史代码使用 `AUTH-XXX` 短格式与设计文档要求的 `SERVICE.AUTH.XXXX` 格式不一致

#### 规则 09 补充 auth-* scope ✅

- **Commit**: 规则文件更新
- **变更**: 在 `09-提交信息规范.md` 补充 `auth-types`/`auth-domain`/`auth-application`/`auth-infrastructure`/`auth-api`/`auth-adapter`/`auth-service` scope
- **原因**: 提交信息规范缺少 auth-service 相关 scope 定义

### 错误码统一迁移 ✅

- **变更**: 将 auth-domain 全部错误码枚举从旧格式迁移到 `SERVICE.AUTH.XXXX` 新格式
- **涉及文件**:
  - `RoleError.java`: `AUTH-001` → `SERVICE.AUTH.0301`
  - `CredentialError.java`: `CREDENTIAL-XXX` → `SERVICE.AUTH.05XX`
  - `UserError.java`: `USER-XXX` → `SERVICE.AUTH.06XX`
  - `ProductError.java`: `PRODUCT-XXX` → `SERVICE.AUTH.07XX`
  - `SecondaryAuthErrorCode.java`: 已使用 `SERVICE.AUTH.01XX` 格式
- **修复撞码**: 删除零引用的 `AuthError.java`（与 `RoleError` 撞码 `AUTH-001`）

### 代码质量修复 ✅

#### M1: 移除死代码 `recordFailedAttempt` ✅

- **文件**: `SecondaryAuthSession.java`
- **原因**: `recordFailedAttempt` 方法从未被调用，`authorize` 方法内部已处理验证码失败逻辑

#### M2: 方法重命名 `revokeAllByApprover` → `revokeAllAuthorizedByApprover` ✅

- **文件**: `SecondaryAuthAppService.java`
- **原因**: 方法名暗示撤销所有会话，实际只撤销 AUTHORIZED 状态的会话，重命名以准确表达语义

#### M3: `confirm` 方法按状态分别抛出错误码 ✅

- **文件**: `SecondaryAuthAppService.java`
- **原因**: 原实现对终态统一抛 `SESSION_EXPIRED`，语义不准确。改为 switch 按状态分别抛出：
  - AUTHORIZED → `SESSION_NOT_PENDING`（已授权，请勿重复确认）
  - REJECTED → `SESSION_NOT_PENDING`（已被拒绝）
  - EXPIRED → `SESSION_EXPIRED`
  - REVOKED → `SESSION_NOT_AUTHORIZED`（已被撤销）
  - CLOSED → `SESSION_NOT_AUTHORIZED`（已关闭）

#### M4: 引入 `SYSTEM_OPERATOR` 常量 ✅

- **文件**: `SecondaryAuthSession.java`
- **原因**: `expireIfTimeout` 使用 `tellerAccountId` 作为操作人，系统触发的超时操作应使用系统用户标识
- **实现**: 新增 `private static final UserNo SYSTEM_OPERATOR = UserNo.of("SYSTEM")` 静态常量

#### M5: CQE 模式合规 ✅

- **文件**: `InitiateSecondaryAuthCommand.java`, `ConfirmSecondaryAuthCommand.java`
- **原因**: `initiate`/`confirm` 方法入参包含非 CQE 对象（`approverMobile`、`snapshot` 作为独立参数），违反规则 04 §一
- **实现**:
  - `InitiateSecondaryAuthCommand` 新增 `approverMobile` 字段
  - `ConfirmSecondaryAuthCommand` 新增 `snapshot` 字段

### 测试补充 ✅

#### M6: AUTHORIZED 状态 expireIfTimeout 快照过期测试 ✅

- **文件**: `SecondaryAuthSessionTest.java`
- **新增测试**:
  - `should_expire_when_snapshot_expired`: 快照过期但会话未过期时 → EXPIRED
  - `should_expire_when_session_timeout`: 会话超时 → EXPIRED
  - `should_not_expire_when_authorized_not_timeout`: 均未过期 → 保持 AUTHORIZED

#### M7: 具体错误码断言测试 ✅

- **文件**: `SecondaryAuthSessionTest.java`
- **变更**: 为 4 个异常测试补充 `isInstanceOfSatisfying` + `code()` 断言：
  - `should_throw_when_code_not_match` → 断言 `INVALID_VERIFICATION_CODE`
  - `should_throw_when_not_pending` → 断言 `SESSION_NOT_PENDING`
  - `should_throw_when_revoke_not_authorized` → 断言 `SESSION_NOT_AUTHORIZED`
  - `should_throw_when_close_not_authorized` → 断言 `SESSION_NOT_AUTHORIZED`（新增测试）

### 验证结果 ✅

- **auth-domain**: 17/17 测试通过（BUILD SUCCESS）
- **auth-application**: 编译通过（BUILD SUCCESS，无测试文件）

---

## 推迟事项

1. **infrastructure 层实现**: `SecondaryAuthSessionRepositoryImpl`、`SecondaryAuthSessionDO`、`SecondaryAuthSessionMapper`、`SecondaryAuthSessionConverter`、`BCryptVerificationCodeHasher` 需补充 auth-infrastructure 依赖
2. **PermissionResolver 端口**: 应用层需补充 `PermissionResolver` 端口和实现，当前 `confirm` 方法接受外部传入的 `PermissionSnapshot`
3. **权限判定策略**: 需在业务服务侧实现 `CachingPermissionClient`，涉及 permission-sdk 模块改造
4. **集成测试**: 缺少端到端集成测试，后续补充
