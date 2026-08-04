# 第一阶段阻塞修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:
> executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 business-core-kernel、approval-service、integration-service 三个不可交付模块的 P0 阻塞问题，使其达到可启动状态。

**Architecture:** 按"简单 bug 修复 → 设计 bug 修复 → 结构性补齐"顺序推进。每个 bug 修复遵循
TDD（先写复现测试，再修复代码）。结构性补齐只创建最小可启动的模块骨架，不实现业务逻辑。

**Tech Stack:** JDK 25（--enable-preview）、Spring Boot 3.5.14、MyBatis-Flex 1.11.5、JUnit 5、H2 内存数据库（测试用）

## Global Constraints

- 严格遵循 `.trae/rules/` 下 8 条规则
- 修复前先写复现测试（红），再修复代码（绿），最后重构（蓝）
- 每个任务结束后执行 `mvn -pl <module> -am test` 验证
- 修复 commit message 格式：`fix(<module>): <问题描述>`
- 结构性补齐 commit message 格式：`feat(<module>): <补齐内容>`
- 禁止修改与本次修复无关的代码
- 禁止删除已有测试

---

## 文件结构概览

### 修改的文件

- `business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/errorcode/CoreDomainErrorCode.java` —
  修复 message () 返回空串
-
`business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/aggregate/vauleobject/MaterialItem.java` —
修复 removeUpload 类型比较 bug
- `business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/aggregate/vauleobject/` 55 个文件 —
  包名 vauleobject → valueobject
- `approval-service/approval-domain/src/main/java/com/example/approval/domain/aggregate/root/ApprovalFlow.java` — 修复
  update () 调用链断裂
- `approval-service/approval-domain/src/main/java/com/example/approval/domain/aggregate/root/ApprovalInstance.java` — 修复
  allApproversApproved 角色审批立即返回 true
- `approval-service/approval-infrastructure/pom.xml` — 添加 MySQL 驱动依赖
-
`integration-service/integration-service-infrastructure/src/main/java/com/example/integration/infrastructure/core/common/model/TradeRootResponse.java` —
修复 Optional.of (null) NPE
- `integration-service/integration-service-starter/pom.xml` — 修复 finalName 错误
- `integration-service/pom.xml` — 添加 integration-service-types 模块声明
- `pom.xml`（根） — 添加 integration-service-types 依赖管理

### 新增的文件

- `business-core-kernel/business-core-starter/pom.xml` — starter 模块骨架
- `business-core-kernel/business-core-starter/src/main/java/com/example/core/CoreApplication.java` — 启动类
- `business-core-kernel/business-core-starter/src/main/resources/application.yml` — 最小配置
- `business-core-kernel/business-core-types/src/main/java/com/example/core/types/package-info.java` — 包骨架
- `business-core-kernel/business-core-api/src/main/java/com/example/core/api/package-info.java` — 包骨架
- `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/package-info.java` — 包骨架
- `integration-service/integration-service-types/pom.xml` — types 模块骨架
- `integration-service/integration-service-types/src/main/java/com/example/integration/types/package-info.java` — 包骨架
- 各模块的测试文件（详见每个任务）

### 测试文件

- `business-core-domain/src/test/java/.../errorcode/CoreDomainErrorCodeTest.java`
- `business-core-domain/src/test/java/.../aggregate/valueobject/MaterialItemTest.java`
- `approval-domain/src/test/java/.../aggregate/root/ApprovalFlowTest.java`
- `approval-domain/src/test/java/.../aggregate/root/ApprovalInstanceTest.java`
- `integration-service-infrastructure/src/test/java/.../core/common/model/TradeRootResponseTest.java`

---

## Task 1: 修复 CoreDomainErrorCode.message () 返回空串 bug

**Files:**

- Modify:
  `business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/errorcode/CoreDomainErrorCode.java:34-36`
- Test:
  `business-core-kernel/business-core-domain/src/test/java/com/example/core/domain/errorcode/CoreDomainErrorCodeTest.java`

**Interfaces:**

- Consumes: `com.example.shared.exception.ErrorDefinition` 接口
- Produces: 修复后的 `CoreDomainErrorCode.message()` 返回实际 message 字段值

**Bug 描述：** 第 35 行 `return "";` 硬编码返回空串，导致所有领域错误码消息丢失。

- [ ] **Step 1: 编写失败测试**

```java
package com.example.core.domain.errorcode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CoreDomainErrorCode 错误码测试")
class CoreDomainErrorCodeTest {

    @Test
    @DisplayName("message() 应返回构造函数传入的消息，而非空串")
    void message_shouldReturnActualMessage_notEmptyString() {
        // given
        CoreDomainErrorCode errorCode = CoreDomainErrorCode.INVALID_STATUS;

        // when
        String message = errorCode.message();

        // then
        assertEquals("[状态有误]{}", message);
        assertFalse(message.isEmpty(), "错误码消息不应为空");
    }

    @Test
    @DisplayName("code() 应返回构造函数传入的编码")
    void code_shouldReturnActualCode() {
        assertEquals("200001", CoreDomainErrorCode.INVALID_STATUS.code());
        assertEquals("200002", CoreDomainErrorCode.INVALID_DATA.code());
        assertEquals("200003", CoreDomainErrorCode.INVALID_OPERATION.code());
    }

    @Test
    @DisplayName("所有错误码的 message 都不应为空串")
    void allErrorCodes_messageShouldNotBeEmpty() {
        for (CoreDomainErrorCode errorCode : CoreDomainErrorCode.values()) {
            assertFalse(errorCode.message().isEmpty(),
                "错误码 " + errorCode.name() + " 的消息不应为空串");
        }
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl business-core-kernel/business-core-domain -am test -Dtest=CoreDomainErrorCodeTest`
Expected: FAIL — `message_shouldReturnActualMessage_notEmptyString` 失败，期望 "[状态有误]{}" 实际 ""

- [ ] **Step 3: 修复代码**

修改 `CoreDomainErrorCode.java` 第 34-36 行：

```java
@Override
public String message() {
    return this.message;
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -pl business-core-kernel/business-core-domain -am test -Dtest=CoreDomainErrorCodeTest`
Expected: PASS — 3 个测试全部通过

- [ ] **Step 5: 提交**

```bash
git add business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/errorcode/CoreDomainErrorCode.java
git add business-core-kernel/business-core-domain/src/test/java/com/example/core/domain/errorcode/CoreDomainErrorCodeTest.java
git commit -m "fix(business-core-domain): CoreDomainErrorCode.message() 返回空串导致错误消息丢失"
```

---

## Task 2: 修复 MaterialItem.removeUpload 类型比较 bug

**Files:**

- Modify:
  `business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/aggregate/vauleobject/MaterialItem.java:61-63`
- Test:
  `business-core-kernel/business-core-domain/src/test/java/com/example/core/domain/aggregate/valueobject/MaterialItemTest.java`

**Bug 描述：** 第 62 行 `filter(f -> !f.equals(fileId))` 中 `f` 是 `BusinessFile` 类型，`fileId` 是 `FileId` 类型，
`BusinessFile.equals(FileId)` 永远返回 false（record 的 equals 基于同类型比较），导致 removeUpload 永远不会移除任何文件。

- [ ] **Step 1: 编写失败测试**

```java
package com.example.core.domain.aggregate.valueobject;

import com.example.core.domain.aggregate.vauleobject.MaterialItem;
import com.example.core.domain.aggregate.vauleobject.BusinessFile;
import com.example.core.domain.aggregate.vauleobject.enums.material.RequirementType;
import com.example.core.domain.aggregate.vauleobject.business.BusinessLevel;
import com.example.shared.primitives.identity.FileId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MaterialItem 材料项测试")
class MaterialItemTest {

    @Test
    @DisplayName("removeUpload 应根据 fileId 移除对应文件")
    void removeUpload_shouldRemoveFileByFileId() {
        // given
        FileId fileId1 = new FileId(1L);
        FileId fileId2 = new FileId(2L);
        BusinessFile file1 = new BusinessFile(fileId1, "file1.pdf", "pdf", 1024L);
        BusinessFile file2 = new BusinessFile(fileId2, "file2.pdf", "pdf", 2048L);

        MaterialItem item = new MaterialItem(
            "M001", "材料1", BusinessLevel.PLAN, RequirementType.REQUIRED, null,
            java.util.Optional.empty()
        );
        item = item.withUpload(file1).withUpload(file2);

        // when
        MaterialItem result = item.removeUpload(fileId1);

        // then
        assertEquals(1, result.uploadInfo().get().files().size());
        assertFalse(result.uploadInfo().get().files().contains(file1));
        assertTrue(result.uploadInfo().get().files().contains(file2));
    }

    @Test
    @DisplayName("removeUpload 移除最后一个文件后 uploadInfo 应为空")
    void removeUpload_lastFileRemoved_uploadInfoShouldBeEmpty() {
        // given
        FileId fileId = new FileId(1L);
        BusinessFile file = new BusinessFile(fileId, "file.pdf", "pdf", 1024L);

        MaterialItem item = new MaterialItem(
            "M001", "材料1", BusinessLevel.PLAN, RequirementType.REQUIRED, null,
            java.util.Optional.empty()
        );
        item = item.withUpload(file);

        // when
        MaterialItem result = item.removeUpload(fileId);

        // then
        assertTrue(result.uploadInfo().isEmpty());
    }

    @Test
    @DisplayName("removeUpload 传入不存在的 fileId 应保持原样")
    void removeUpload_nonExistentFileId_shouldReturnUnchanged() {
        // given
        FileId fileId1 = new FileId(1L);
        FileId fileIdNotExist = new FileId(999L);
        BusinessFile file1 = new BusinessFile(fileId1, "file1.pdf", "pdf", 1024L);

        MaterialItem item = new MaterialItem(
            "M001", "材料1", BusinessLevel.PLAN, RequirementType.REQUIRED, null,
            java.util.Optional.empty()
        );
        item = item.withUpload(file1);

        // when
        MaterialItem result = item.removeUpload(fileIdNotExist);

        // then
        assertEquals(1, result.uploadInfo().get().files().size());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl business-core-kernel/business-core-domain -am test -Dtest=MaterialItemTest`
Expected: FAIL — `removeUpload_shouldRemoveFileByFileId` 失败，移除后文件数量仍为 2

- [ ] **Step 3: 修复代码**

修改 `MaterialItem.java` 第 61-63 行：

```java
var remaining = uploadInfo.get().files().stream()
    .filter(f -> !f.fileId().equals(fileId))
    .toList();
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -pl business-core-kernel/business-core-domain -am test -Dtest=MaterialItemTest`
Expected: PASS — 3 个测试全部通过

- [ ] **Step 5: 提交**

```bash
git add business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/aggregate/vauleobject/MaterialItem.java
git add business-core-kernel/business-core-domain/src/test/java/com/example/core/domain/aggregate/valueobject/MaterialItemTest.java
git commit -m "fix(business-core-domain): MaterialItem.removeUpload 类型比较错误导致文件无法移除"
```

---

## Task 3: 修复 TradeRootResponse 的 Optional.of (null) NPE 风险

**Files:**

- Modify:
  `integration-service/integration-service-infrastructure/src/main/java/com/example/integration/infrastructure/core/common/model/TradeRootResponse.java:43-45,58-60,73-76`
- Test:
  `integration-service/integration-service-infrastructure/src/test/java/com/example/integration/infrastructure/core/common/model/TradeRootResponseTest.java`

**Bug 描述：** `isSuccess()`、`getErrorCode()`、`getErrorMsg()` 三处使用 `Optional.of(statusInfo())`，当 `statusInfo()` 返回
null 时，`Optional.of(null)` 会抛出 NPE。应使用 `Optional.ofNullable()`。

- [ ] **Step 1: 编写失败测试**

```java
package com.example.integration.infrastructure.core.common.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TradeRootResponse 交易响应测试")
class TradeRootResponseTest {

    @Test
    @DisplayName("当 appResponse 为 null 时 isSuccess 不应抛出 NPE")
    void isSuccess_whenAppResponseNull_shouldNotThrowNPE() {
        // given
        TradeRootResponse<String> response = new TradeRootResponse<>(null);

        // when & then
        assertDoesNotThrow(() -> response.isSuccess());
        assertFalse(response.isSuccess());
    }

    @Test
    @DisplayName("当 statusInfo 为 null 时 getErrorCode 不应抛出 NPE")
    void getErrorCode_whenStatusInfoNull_shouldNotThrowNPE() {
        // given
        TradeRootResponse<String> response = new TradeRootResponse<>(null);

        // when & then
        assertDoesNotThrow(() -> response.getErrorCode());
        assertEquals("MISSING", response.getErrorCode());
    }

    @Test
    @DisplayName("当 statusInfo 为 null 时 getErrorMsg 不应抛出 NPE")
    void getErrorMsg_whenStatusInfoNull_shouldNotThrowNPE() {
        // given
        TradeRootResponse<String> response = new TradeRootResponse<>(null);

        // when & then
        assertDoesNotThrow(() -> response.getErrorMsg());
        assertEquals("Unknown error", response.getErrorMsg());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl integration-service/integration-service-infrastructure -am test -Dtest=TradeRootResponseTest`
Expected: FAIL — 3 个测试因 NPE 失败

- [ ] **Step 3: 修复代码**

修改 `TradeRootResponse.java`：

第 42-46 行 `isSuccess()`：

```java
@Override
public boolean isSuccess() {
    return "0000".equals(Optional.ofNullable(statusInfo())
      .map(TradeRspHead.StatusInfo::msgCode)
      .orElse(null));
}
```

第 53-61 行 `getErrorCode()`：

```java
@Override
public String getErrorCode() {
    if (isSuccess()) {
        return null;
    }
    return Optional.ofNullable(statusInfo())
      .map(TradeRspHead.StatusInfo::msgCode)
      .orElse("MISSING");
}
```

第 68-77 行 `getErrorMsg()`：

```java
@Override
public String getErrorMsg() {
    if (isSuccess()) {
        return null;
    }
    return Optional.ofNullable(statusInfo())
      .map(TradeRspHead.StatusInfo::msgInfo)
      .filter(s -> !s.isBlank())
      .orElse("Unknown error");
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -pl integration-service/integration-service-infrastructure -am test -Dtest=TradeRootResponseTest`
Expected: PASS — 3 个测试全部通过

- [ ] **Step 5: 提交**

```bash
git add integration-service/integration-service-infrastructure/src/main/java/com/example/integration/infrastructure/core/common/model/TradeRootResponse.java
git add integration-service/integration-service-infrastructure/src/test/java/com/example/integration/infrastructure/core/common/model/TradeRootResponseTest.java
git commit -m "fix(integration-service-infrastructure): TradeRootResponse 使用 Optional.of(null) 导致 NPE 风险"
```

---

## Task 4: 修复 integration-service-starter finalName 复制粘贴错误

**Files:**

- Modify: `integration-service/integration-service-starter/pom.xml:32`

**Bug 描述：** 第 32 行 `<finalName>demo-consumer</finalName>` 是从其他模块复制粘贴的遗留，应为 `integration-service`。

- [ ] **Step 1: 修复代码**

修改 `integration-service-starter/pom.xml` 第 32 行：

```xml
<finalName>integration-service</finalName>
```

- [ ] **Step 2: 验证修改**

Run: `mvn -pl integration-service/integration-service-starter -am package -DskipTests`
Expected: 构建成功，生成 `integration-service.jar`

- [ ] **Step 3: 提交**

```bash
git add integration-service/integration-service-starter/pom.xml
git commit -m "fix(integration-service-starter): finalName 复制粘贴错误 demo-consumer 改为 integration-service"
```

---

## Task 5: 修复 ApprovalInstance.allApproversApproved 角色审批立即返回 true bug

**Files:**

- Modify:
  `approval-service/approval-domain/src/main/java/com/example/approval/domain/aggregate/root/ApprovalInstance.java:340-347`
- Test:
  `approval-service/approval-domain/src/test/java/com/example/approval/domain/aggregate/root/ApprovalInstanceTest.java`

**Bug 描述：** `allApproversApproved` 方法第 342-344 行：当 `approverIds.isEmpty()` 时立即返回 true。但角色审批（SPECIFIED_ROLE）时
approverIds 本就为空（使用 roleIds），导致角色审批节点第一次审批就立即被标记为完成，跳过了其他审批人。

**修复策略：** 区分两种情况：

1. SPECIFIED_USER：检查 approverIds 是否都已审批
2. SPECIFIED_ROLE：approverIds 为空是正常的，但不能立即返回 true，应返回 false（等待实际审批人审批）。角色审批需要等到所有实际审批人完成后才返回
   true，但 domain 层无法解析角色对应的用户列表，所以保守返回 false（即每次角色审批只标记当前审批人完成，需要外部判断是否所有角色审批人都已审批）。

实际上，更合理的设计是：approve 方法中已经记录了审批记录，allApproversApproved
应该检查的是"当前节点是否还有未审批的审批人"。对于角色审批，approverIds 为空意味着没有指定具体用户，此时应该基于审批记录判断。

**简化修复：** 当 approverIds 为空且 roleIds 不为空时（角色审批），返回 false（不能立即通过）。

- [ ] **Step 1: 编写失败测试**

```java
package com.example.approval.domain.aggregate.root;

import com.example.approval.domain.aggregate.entity.ApprovalNode;
import com.example.approval.domain.aggregate.entity.NodeExecution;
import com.example.approval.domain.valueobject.NodeOrder;
import com.example.approval.domain.valueobject.TerminalLevel;
import com.example.approval.types.NodeId;
import com.example.approval.types.enums.ApproverType;
import com.example.approval.types.enums.NodeType;
import com.example.approval.types.enums.SignMode;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ApprovalInstance 审批实例测试")
class ApprovalInstanceTest {

    @Test
    @DisplayName("角色审批节点不应在 approverIds 为空时立即返回 true")
    void allApproversApproved_roleApproval_shouldNotReturnTrueWhenApproverIdsEmpty() {
        // given - 创建角色审批节点（approverIds 为空，roleIds 不为空）
        ApprovalNode roleNode = ApprovalNode.createSamePlanNode(
            NodeId.of(1L),
            NodeOrder.first(),
            ApproverType.SPECIFIED_ROLE,
            List.of(),  // approverIds 为空
            List.of("ROLE_ADMIN", "ROLE_MANAGER"),  // roleIds 不为空
            SignMode.AND_SIGN,
            new UserNo("operator001")
        );

        // 创建一个空的 NodeExecution
        NodeExecution execution = NodeExecution.create(
            com.example.approval.types.ExecutionId.of(1L),
            roleNode.id(),
            NodeOrder.first(),
            new UserNo("operator001")
        );

        // 使用反射调用 private 方法，或通过 public 行为验证
        // 这里通过 approve 行为验证：角色审批不应在一次审批后立即完成节点

        // given - 创建审批实例
        ApprovalInstance instance = ApprovalInstance.create(
            com.example.approval.types.ApprovalInstanceId.of(1L),
            com.example.approval.types.ApprovalFlowId.of(1L),
            com.example.approval.domain.valueobject.FlowVersion.initial(),
            com.example.shared.primitives.identity.ApplicationId.of(1L),
            "plan001",
            new UserNo("initiator001")
        );
        instance.start(new UserNo("initiator001"));

        // when - 第一个角色审批人审批
        instance.approve(roleNode, new UserNo("roleUser001"),
            new com.example.approval.domain.valueobject.ApprovalOpinion("同意"),
            new UserNo("roleUser001"));

        // then - 节点不应被标记为完成（因为还有其他角色审批人未审批）
        // 由于 allApproversApproved 在 approve 内部调用，我们通过检查节点是否移动来验证
        // 如果节点立即完成，currentNodeOrder 会变为 next()
        assertEquals(NodeOrder.first(), instance.currentNodeOrder(),
            "角色审批节点不应在第一个审批人审批后立即完成");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl approval-service/approval-domain -am test -Dtest=ApprovalInstanceTest`
Expected: FAIL — `allApproversApproved_roleApproval_shouldNotReturnTrueWhenApproverIdsEmpty` 失败，currentNodeOrder 已变为
next ()

- [ ] **Step 3: 修复代码**

修改 `ApprovalInstance.java` 第 340-347 行 `allApproversApproved` 方法：

```java
private boolean allApproversApproved(ApprovalNode node, NodeExecution execution) {
    List<UserNo> approverIds = node.approverIds();
    if (approverIds.isEmpty()) {
        // 角色审批场景：approverIds 为空，使用 roleIds
        // domain 层无法解析角色对应的用户列表，保守返回 false
        // 实际完成判断应由应用层基于 RoleUserGateway 解析后决定
        return node.roleIds().isEmpty();
    }
    return approverIds.stream()
            .allMatch(execution::hasApprovedBy);
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -pl approval-service/approval-domain -am test -Dtest=ApprovalInstanceTest`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add approval-service/approval-domain/src/main/java/com/example/approval/domain/aggregate/root/ApprovalInstance.java
git add approval-service/approval-domain/src/test/java/com/example/approval/domain/aggregate/root/ApprovalInstanceTest.java
git commit -m "fix(approval-domain): allApproversApproved 角色审批时 approverIds 为空立即返回 true 跳过审批"
```

---

## Task 6: 修复 ApprovalFlow.update () 调用链断裂 bug

**Files:**

- Modify:
  `approval-service/approval-domain/src/main/java/com/example/approval/domain/aggregate/root/ApprovalFlow.java:131-153`
- Test:
  `approval-service/approval-domain/src/test/java/com/example/approval/domain/aggregate/root/ApprovalFlowTest.java`

**Bug 描述：** `update()` 方法第 136-145 行：当传入 `flowName != null` 或 `matchRules != null` 时直接抛异常"不可修改"，导致
update 方法永远无法完成正常流程。

**修复策略：** 根据 DDD 原则，`flowName` 和 `matchRules` 是 `final` 字段（不可变）。正确的做法是：

1. 移除 `update` 方法中的 `flowName` 和 `matchRules` 参数（既然不可变就不应作为更新参数）
2. 或者将 `flowName` 和 `matchRules` 改为非 final，允许修改

从业务角度看，审批流名称和匹配规则应该可以修改（否则 update 方法没有意义）。采用方案 2：去掉 final，允许修改。

- [ ] **Step 1: 编写失败测试**

```java
package com.example.approval.domain.aggregate.root;

import com.example.approval.domain.aggregate.entity.ApprovalNode;
import com.example.approval.domain.valueobject.FlowName;
import com.example.approval.domain.valueobject.FlowVersion;
import com.example.approval.domain.valueobject.MatchRules;
import com.example.approval.domain.valueobject.NodeOrder;
import com.example.approval.domain.valueobject.TerminalLevel;
import com.example.approval.types.ApprovalFlowId;
import com.example.approval.types.NodeId;
import com.example.approval.types.enums.ApproverType;
import com.example.approval.types.enums.NodeType;
import com.example.approval.types.enums.SignMode;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ApprovalFlow 审批流测试")
class ApprovalFlowTest {

    @Test
    @DisplayName("update 应能修改审批流名称")
    void update_shouldUpdateFlowName() {
        // given
        ApprovalFlow flow = createTestFlow();
        FlowName newName = new FlowName("更新后的审批流名称");

        // when
        flow.update(newName, null, null, new UserNo("operator001"));

        // then
        assertEquals(newName, flow.flowName());
        assertTrue(flow.flowVersion().value() > 1, "版本号应递增");
    }

    @Test
    @DisplayName("update 应能修改匹配规则")
    void update_shouldUpdateMatchRules() {
        // given
        ApprovalFlow flow = createTestFlow();
        MatchRules newRules = MatchRules.of("新规则");

        // when
        flow.update(null, newRules, null, new UserNo("operator001"));

        // then
        assertEquals(newRules, flow.matchRules());
    }

    @Test
    @DisplayName("update 应能修改审批节点列表")
    void update_shouldUpdateNodes() {
        // given
        ApprovalFlow flow = createTestFlow();
        List<ApprovalNode> newNodes = List.of(
            createTestNode(NodeOrder.first()),
            createTestNode(NodeOrder.first().next())
        );

        // when
        flow.update(null, null, newNodes, new UserNo("operator001"));

        // then
        assertEquals(2, flow.getNodes().size());
    }

    @Test
    @DisplayName("废弃状态的审批流不能更新")
    void update_deprecatedFlow_shouldThrow() {
        // given
        ApprovalFlow flow = createTestFlow();
        flow.deprecate(new UserNo("operator001"));

        // when & then
        assertThrows(com.example.shared.exception.DomainException.class,
            () -> flow.update(new FlowName("新名称"), null, null, new UserNo("operator001")));
    }

    private ApprovalFlow createTestFlow() {
        return ApprovalFlow.create(
            ApprovalFlowId.of(1L),
            new FlowName("测试审批流"),
            MatchRules.of("规则1"),
            List.of(createTestNode(NodeOrder.first())),
            new UserNo("creator001")
        );
    }

    private ApprovalNode createTestNode(NodeOrder order) {
        return ApprovalNode.createSamePlanNode(
            NodeId.of(System.currentTimeMillis()),
            order,
            ApproverType.SPECIFIED_USER,
            List.of(new UserNo("approver001")),
            List.of(),
            SignMode.OR_SIGN,
            new UserNo("creator001")
        );
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -pl approval-service/approval-domain -am test -Dtest=ApprovalFlowTest`
Expected: FAIL — `update_shouldUpdateFlowName` 和 `update_shouldUpdateMatchRules` 抛出 DomainException

- [ ] **Step 3: 修复代码**

修改 `ApprovalFlow.java`：

1. 第 36 行 `private final FlowName flowName;` 改为 `private FlowName flowName;`
2. 第 42 行 `private final MatchRules matchRules;` 改为 `private MatchRules matchRules;`
3. 第 131-153 行 `update` 方法改为：

```java
public void update(FlowName flowName, MatchRules matchRules, List<ApprovalNode> nodes, UserNo operator) {
    if (this.status == FlowStatus.DEPRECATED) {
        throw new DomainException(ApprovalDomainErrorCode.APPROVAL_FLOW_DEPRECATED)
                .withLogDetail("废弃状态的审批流不能更新, ApprovalFlowId: %s".formatted(this.id()));
    }
    if (flowName != null) {
        this.flowName = flowName;
    }
    if (matchRules != null) {
        this.matchRules = matchRules;
    }
    if (nodes != null && !nodes.isEmpty()) {
        this.nodes.clear();
        this.nodes.addAll(nodes);
        validateNodesOrder();
    }
    this.flowVersion = this.flowVersion.increment();
    this.markUpdated(operator);
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -pl approval-service/approval-domain -am test -Dtest=ApprovalFlowTest`
Expected: PASS — 4 个测试全部通过

- [ ] **Step 5: 提交**

```bash
git add approval-service/approval-domain/src/main/java/com/example/approval/domain/aggregate/root/ApprovalFlow.java
git add approval-service/approval-domain/src/test/java/com/example/approval/domain/aggregate/root/ApprovalFlowTest.java
git commit -m "fix(approval-domain): ApprovalFlow.update() 调用链断裂导致无法修改名称和规则"
```

---

## Task 7: 添加 approval-service MySQL 驱动依赖

**Files:**

- Modify: `approval-service/approval-infrastructure/pom.xml`

**Bug 描述：** `application.yml` 第 28-31 行配置了 MySQL 数据源（`jdbc:mysql://localhost:3306/approval`，
`com.mysql.cj.jdbc.Driver`），但 `approval-infrastructure/pom.xml` 只引入了 PostgreSQL 驱动，没有 MySQL 驱动，导致启动时找不到驱动类。

- [ ] **Step 1: 添加 MySQL 驱动依赖**

修改 `approval-service/approval-infrastructure/pom.xml`，在第 54 行（PostgreSQL 依赖之后）添加：

```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

- [ ] **Step 2: 验证编译**

Run: `mvn -pl approval-service/approval-infrastructure -am compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add approval-service/approval-infrastructure/pom.xml
git commit -m "fix(approval-infrastructure): 缺少 MySQL 驱动依赖导致服务无法启动"
```

---

## Task 8: 修复 business-core-kernel 包名拼写错误 vauleobject → valueobject

**Files:**

- Modify: `business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/aggregate/vauleobject/`
  下所有文件（55 个）
- Modify: 引用 `vauleobject` 包的所有其他文件（application/infrastructure 模块）

**Bug 描述：** 包名 `vauleobject` 是 `valueobject` 的拼写错误，贯穿 55 个文件。

**修复策略：** 使用 IDE 全局重命名或脚本批量替换。由于 Windows 环境下文件系统大小写不敏感，需要分两步：

1. 先重命名包目录 `vauleobject` → `valueobject_tmp` → `valueobject`
2. 再批量替换 Java 文件中的 `vauleobject` → `valueobject`

- [ ] **Step 1: 使用 PowerShell 脚本批量重命名和替换**

```powershell
# 1. 重命名目录（Windows 文件系统大小写不敏感，需要两步）
$base = "d:\WorkSpace\Trae\multiple-module-spring-cloud\business-core-kernel"
$oldDir = "$base\business-core-domain\src\main\java\com\example\core\domain\aggregate\vauleobject"
$tmpDir = "$base\business-core-domain\src\main\java\com\example\core\domain\aggregate\valueobject_tmp"
$newDir = "$base\business-core-domain\src\main\java\com\example\core\domain\aggregate\valueobject"

# 先改为临时名
Rename-Item -Path $oldDir -NewName "valueobject_tmp"
# 再改为正确名
Rename-Item -Path $tmpDir -NewName "valueobject"

# 2. 批量替换 Java 文件中的包引用
Get-ChildItem -Path $base -Recurse -Filter *.java | ForEach-Object {
    $content = Get-Content $_.FullName -Raw
    if ($content -match 'vauleobject') {
        $newContent = $content -replace 'vauleobject', 'valueobject'
        Set-Content -Path $_.FullName -Value $newContent -NoNewline
        Write-Host "Updated: $($_.FullName)"
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `mvn -pl business-core-kernel/business-core-domain -am compile`
Expected: BUILD SUCCESS

Run: `mvn -pl business-core-kernel/business-core-application -am compile`
Expected: BUILD SUCCESS

Run: `mvn -pl business-core-kernel/business-core-infrastructure -am compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: 运行已有测试验证**

Run: `mvn -pl business-core-kernel -am test`
Expected: 所有已有测试通过（CoreDomainErrorCodeTest、MaterialItemTest）

- [ ] **Step 4: 提交**

```bash
git add -A business-core-kernel/
git commit -m "fix(business-core-kernel): 包名拼写错误 vauleobject 改为 valueobject（55 个文件）"
```

---

## Task 9: 补齐 business-core-types 空壳模块

**Files:**

- Create: `business-core-kernel/business-core-types/src/main/java/com/example/core/types/package-info.java`
- Modify: `business-core-kernel/business-core-types/pom.xml`（如果需要补充依赖）

**Bug 描述：** `business-core-types` 模块只有 `pom.xml`，没有任何 Java 文件，是一个空壳模块。

**修复策略：** 创建最小可用的包结构，让模块能够正常编译。不实现任何业务逻辑（业务逻辑由后续迭代补齐）。

- [ ] **Step 1: 创建 package-info.java**

创建文件 `business-core-kernel/business-core-types/src/main/java/com/example/core/types/package-info.java`：

```java
/**
 * 业务核心服务 - 领域原语层
 * <p>
 * 定义业务核心服务的 ID 类型和领域原语。
 * 后续迭代将补齐具体的 ID 类型定义。
 *
 * @author business-core-kernel
 * @since 2026/7/21
 */
package com.example.core.types;
```

- [ ] **Step 2: 验证编译**

Run: `mvn -pl business-core-kernel/business-core-types -am compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add business-core-kernel/business-core-types/src/
git commit -m "feat(business-core-types): 补齐空壳模块的包结构"
```

---

## Task 10: 补齐 business-core-api 空壳模块

**Files:**

- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/package-info.java`

- [ ] **Step 1: 创建 package-info.java**

创建文件 `business-core-kernel/business-core-api/src/main/java/com/example/core/api/package-info.java`：

```java
/**
 * 业务核心服务 - API 层
 * <p>
 * 定义对外接口协议、DTO、Command、Query。
 * 后续迭代将补齐具体的 API 定义。
 *
 * @author business-core-kernel
 * @since 2026/7/21
 */
package com.example.core.api;
```

- [ ] **Step 2: 验证编译**

Run: `mvn -pl business-core-kernel/business-core-api -am compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add business-core-kernel/business-core-api/src/
git commit -m "feat(business-core-api): 补齐空壳模块的包结构"
```

---

## Task 11: 补齐 business-core-adapter 空壳模块

**Files:**

- Create: `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/package-info.java`

- [ ] **Step 1: 创建 package-info.java**

创建文件 `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/package-info.java`：

```java
/**
 * 业务核心服务 - Adapter 层
 * <p>
 * 实现 API 层定义的接口，提供 REST 端点。
 * 后续迭代将补齐具体的 Controller 实现。
 *
 * @author business-core-kernel
 * @since 2026/7/21
 */
package com.example.core.adapter;
```

- [ ] **Step 2: 验证编译**

Run: `mvn -pl business-core-kernel/business-core-adapter -am compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add business-core-kernel/business-core-adapter/src/
git commit -m "feat(business-core-adapter): 补齐空壳模块的包结构"
```

---

## Task 12: 创建 business-core-starter 模块

**Files:**

- Create: `business-core-kernel/business-core-starter/pom.xml`
- Create: `business-core-kernel/business-core-starter/src/main/java/com/example/core/CoreApplication.java`
- Create: `business-core-kernel/business-core-starter/src/main/resources/application.yml`
- Modify: `business-core-kernel/pom.xml`（添加 starter 模块声明）
- Modify: `business-core-kernel/pom.xml`（dependencyManagement 添加 starter）

**Bug 描述：** `business-core-kernel` 没有 starter 模块，服务无法启动。

- [ ] **Step 1: 创建 starter pom.xml**

创建文件 `business-core-kernel/business-core-starter/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xmlns="http://maven.apache.org/POM/4.0.0"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.example</groupId>
    <artifactId>business-core-kernel</artifactId>
    <version>1.0-SNAPSHOT</version>
  </parent>

  <artifactId>business-core-starter</artifactId>

  <dependencies>
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>business-core-adapter</artifactId>
    </dependency>
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>business-core-infrastructure</artifactId>
    </dependency>

    <dependency>
      <groupId>com.alibaba.cloud</groupId>
      <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
    </dependency>
  </dependencies>

  <build>
    <finalName>business-core-service</finalName>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: 创建启动类**

创建文件 `business-core-kernel/business-core-starter/src/main/java/com/example/core/CoreApplication.java`：

```java
package com.example.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 业务核心服务启动类
 *
 * @author business-core-kernel
 * @since 2026/7/21
 */
@SpringBootApplication
public class CoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreApplication.class, args);
    }
}
```

- [ ] **Step 3: 创建 application.yml**

创建文件 `business-core-kernel/business-core-starter/src/main/resources/application.yml`：

```yaml
server:
  port: 18083

spring:
  application:
    name: business-core-service
  threads:
    virtual:
      enabled: true
  profiles:
    include: local

mybatis-flex:
  mapper-locations: classpath*:/mapper/**/*.xml

logging:
  file:
    path: /applog/${spring.application.name}
    name: ${spring.application.name}.log
```

- [ ] **Step 4: 修改 business-core-kernel/pom.xml 添加 starter 模块**

在 `business-core-kernel/pom.xml` 第 22 行 `</modules>` 之前添加：

```xml
<module>business-core-starter</module>
```

在 `dependencyManagement/dependencies` 中添加：

```xml
<dependency>
  <groupId>com.example</groupId>
  <artifactId>business-core-starter</artifactId>
  <version>${project.version}</version>
</dependency>
```

- [ ] **Step 5: 验证编译**

Run: `mvn -pl business-core-kernel/business-core-starter -am compile`
Expected: BUILD SUCCESS

- [ ] **Step 6: 提交**

```bash
git add business-core-kernel/business-core-starter/
git add business-core-kernel/pom.xml
git commit -m "feat(business-core-starter): 创建启动模块，服务可启动"
```

---

## Task 13: 补齐 integration-service-types 模块

**Files:**

- Create: `integration-service/integration-service-types/pom.xml`
- Create: `integration-service/integration-service-types/src/main/java/com/example/integration/types/package-info.java`
- Modify: `integration-service/pom.xml`（添加 types 模块声明和 dependencyManagement）
- Modify: `pom.xml`（根，添加 integration-service-types 依赖管理）

**Bug 描述：** `integration-service` 缺少 `integration-service-types` 子模块（其他服务都有 xxx-types 模块）。

- [ ] **Step 1: 创建 types pom.xml**

创建文件 `integration-service/integration-service-types/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xmlns="http://maven.apache.org/POM/4.0.0"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.example</groupId>
    <artifactId>integration-service</artifactId>
    <version>1.0-SNAPSHOT</version>
  </parent>

  <artifactId>integration-service-types</artifactId>

  <dependencies>
    <dependency>
      <groupId>com.example</groupId>
      <artifactId>shared-types</artifactId>
    </dependency>
  </dependencies>
</project>
```

- [ ] **Step 2: 创建 package-info.java**

创建文件 `integration-service/integration-service-types/src/main/java/com/example/integration/types/package-info.java`：

```java
/**
 * 集成服务 - 领域原语层
 * <p>
 * 定义集成服务的 ID 类型和领域原语。
 * 后续迭代将补齐具体的 ID 类型定义。
 *
 * @author integration-service
 * @since 2026/7/21
 */
package com.example.integration.types;
```

- [ ] **Step 3: 修改 integration-service/pom.xml**

在 `integration-service/pom.xml` 的 `<modules>` 中第 17 行之前添加：

```xml
<module>integration-service-types</module>
```

在 `dependencyManagement/dependencies` 中添加：

```xml
<dependency>
  <groupId>com.example</groupId>
  <artifactId>integration-service-types</artifactId>
  <version>${project.version}</version>
</dependency>
```

- [ ] **Step 4: 修改根 pom.xml**

在根 `pom.xml` 的 `dependencyManagement` → `<!-- 2nd Dependencies-->` 部分添加（第 120 行附近）：

```xml
<dependency>
  <groupId>com.example</groupId>
  <artifactId>integration-service-types</artifactId>
  <version>${project.version}</version>
</dependency>
```

- [ ] **Step 5: 验证编译**

Run: `mvn -pl integration-service/integration-service-types -am compile`
Expected: BUILD SUCCESS

- [ ] **Step 6: 提交**

```bash
git add integration-service/integration-service-types/
git add integration-service/pom.xml
git add pom.xml
git commit -m "feat(integration-service-types): 补齐缺失的 types 子模块"
```

---

## Task 14: 全量验证

**Files:** 无修改

- [ ] **Step 1: 全项目编译**

Run: `mvn clean compile -DskipTests`
Expected: BUILD SUCCESS，所有模块编译通过

- [ ] **Step 2: 运行所有测试**

Run: `mvn test`
Expected: 所有测试通过，包括新增的测试和已有测试

- [ ] **Step 3: 验证三个目标模块可启动**

Run: `mvn -pl business-core-kernel/business-core-starter -am package -DskipTests`
Expected: 生成 `business-core-service.jar`

Run: `mvn -pl approval-service/approval-starter -am package -DskipTests`
Expected: 生成 `approval-service.jar`（或对应 finalName）

Run: `mvn -pl integration-service/integration-service-starter -am package -DskipTests`
Expected: 生成 `integration-service.jar`

- [ ] **Step 4: 生成修复总结**

更新 `docs/review/2026-07-21-full-project-review.md`，在末尾添加"第一阶段修复记录"章节，记录所有修复内容和验证结果。

- [ ] **Step 5: 提交修复总结**

```bash
git add docs/review/2026-07-21-full-project-review.md
git commit -m "docs: 第一阶段阻塞修复完成，更新 review 报告"
```

---

## Self-Review

### Spec coverage

- ✅ business-core-kernel 包名拼写错误（Task 8）
- ✅ business-core-kernel 3 个空壳模块（Task 9/10/11）
- ✅ business-core-kernel starter 缺失（Task 12）
- ✅ business-core-kernel CoreDomainErrorCode.message () bug（Task 1）
- ✅ business-core-kernel MaterialItem.removeUpload bug（Task 2）
- ✅ approval-service ApprovalFlow.update () bug（Task 6）
- ✅ approval-service ApprovalInstance.allApproversApproved bug（Task 5）
- ✅ approval-service 缺 MySQL 驱动（Task 7）
- ✅ integration-service types 模块缺失（Task 13）
- ✅ integration-service TradeRootResponse NPE（Task 3）
- ✅ integration-service finalName 错误（Task 4）

### Placeholder scan

- 无 TODO/TBD/"implement later"
- 所有代码块都包含完整代码
- 所有命令都包含具体路径和预期输出

### Type consistency

- `CoreDomainErrorCode.message()` 修复后返回 `this.message`（String 类型一致）
- `MaterialItem.removeUpload` 修复后 `f.fileId().equals(fileId)`（FileId.equals (FileId) 类型一致）
- `TradeRootResponse` 修复后 `Optional.ofNullable(statusInfo())`（一致）
- `ApprovalFlow.update` 修复后 flowName/matchRules 去掉 final（类型一致）
- `ApprovalInstance.allApproversApproved` 修复后返回 boolean（类型一致）

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-07-21-phase1-blocking-fixes.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - 每个 Task 分派独立 subagent，Task 间 review，快速迭代

**2. Inline Execution** - 当前会话顺序执行，批量 checkpoint review

**Which approach?**
