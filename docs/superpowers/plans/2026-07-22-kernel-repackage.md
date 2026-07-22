# business-core-kernel 重新分包实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 business-core-kernel 的 domain/application/infrastructure 三层按 `business`(业务领域模型)和 `engine`(流程编排引擎)两个子包重新组织,不拆分 Maven 模块,不修改业务逻辑。

**Architecture:** 纯包路径重构。`business` 包只含 3 个聚合根及其直接关联的值对象/事件/Repository/错误码(~30 类);`engine` 包含引擎核心 SPI/Registry/Pipeline + 公共步骤实现 Handler/Extension + 集成网关(~50 类)。同时将 `BusinessOrchestrationAppService` 重命名为 `FlowOrchestrationService`。同步更新 annuity-service 的 import。

**Tech Stack:** Java 25, Spring Boot 3.5, MyBatis-Flex, Maven 多模块

## Global Constraints

- 不拆分 Maven 模块,仅 Java 包级重构
- 不修改任何业务逻辑,仅移动文件 + 更新 package 声明 + 更新 import
- `BusinessConfigGateway` 不拆分,整体归入 `engine.gateway`
- `StepPipelineExecutor` 不修改实现,仅移动包路径
- 每个任务结束后必须编译通过 + 测试通过
- 频繁提交,每个任务一个 commit
- 测试文件随主代码一起移动(保持同包)

## 基线

- **分支**: 从 `fix/phase1-blocking-fixes` 创建 `refactor/kernel-repackage`
- **基线测试**: kernel 测试 + annuity-service 47 测试全部通过
- **设计文档**: `docs/superpowers/specs/2026-07-22-kernel-repackage-design.md`

## 文件移动映射总览

### domain/business(30 主代码 + 3 测试)

| 原路径 | 目标路径 |
|--------|----------|
| `domain/aggregate/root/BusinessApplication.java` | `domain/business/aggregate/root/BusinessApplication.java` |
| `domain/aggregate/root/BusinessBatch.java` | `domain/business/aggregate/root/BusinessBatch.java` |
| `domain/aggregate/root/BusinessForm.java` | `domain/business/aggregate/root/BusinessForm.java` |
| `domain/aggregate/valueobject/BusinessContext.java` | `domain/business/aggregate/valueobject/BusinessContext.java` |
| `domain/aggregate/valueobject/BusinessExtension.java` | `domain/business/aggregate/valueobject/BusinessExtension.java` |
| `domain/aggregate/valueobject/BusinessFile.java` | `domain/business/aggregate/valueobject/BusinessFile.java` |
| `domain/aggregate/valueobject/MaterialItem.java` | `domain/business/aggregate/valueobject/MaterialItem.java` |
| `domain/aggregate/valueobject/MaterialConditionContext.java` | `domain/business/aggregate/valueobject/MaterialConditionContext.java` |
| `domain/aggregate/valueobject/OperatorInfo.java` | `domain/business/aggregate/valueobject/OperatorInfo.java` |
| `domain/aggregate/valueobject/ParsedPlanResult.java` | `domain/business/aggregate/valueobject/ParsedPlanResult.java` |
| `domain/aggregate/valueobject/ValidationResult.java` | `domain/business/aggregate/valueobject/ValidationResult.java` |
| `domain/aggregate/valueobject/reference/BusinessFormRef.java` | `domain/business/aggregate/valueobject/reference/BusinessFormRef.java` |
| `domain/aggregate/valueobject/reference/PlanBizApplicationRef.java` | `domain/business/aggregate/valueobject/reference/PlanBizApplicationRef.java` |
| `domain/aggregate/valueobject/business/AccountManager.java` | `domain/business/aggregate/valueobject/business/AccountManager.java` |
| `domain/aggregate/valueobject/business/AnnuityChannel.java` | `domain/business/aggregate/valueobject/business/AnnuityChannel.java` |
| `domain/aggregate/valueobject/business/BusinessLevel.java` | `domain/business/aggregate/valueobject/business/BusinessLevel.java` |
| `domain/aggregate/valueobject/business/BusinessType.java` | `domain/business/aggregate/valueobject/business/BusinessType.java` |
| `domain/aggregate/valueobject/business/OperationModel.java` | `domain/business/aggregate/valueobject/business/OperationModel.java` |
| `domain/aggregate/valueobject/enums/status/ApplicationStatus.java` | `domain/business/aggregate/valueobject/enums/status/ApplicationStatus.java` |
| `domain/aggregate/valueobject/enums/status/BatchStatus.java` | `domain/business/aggregate/valueobject/enums/status/BatchStatus.java` |
| `domain/aggregate/valueobject/enums/status/FormStatus.java` | `domain/business/aggregate/valueobject/enums/status/FormStatus.java` |
| `domain/aggregate/valueobject/enums/material/RequirementType.java` | `domain/business/aggregate/valueobject/enums/material/RequirementType.java` |
| `domain/aggregate/valueobject/enums/validate/ValidationType.java` | `domain/business/aggregate/valueobject/enums/validate/ValidationType.java` |
| `domain/event/ApplicationSpawnedEvent.java` | `domain/business/event/ApplicationSpawnedEvent.java` |
| `domain/event/BatchStatusChangedEvent.java` | `domain/business/event/BatchStatusChangedEvent.java` |
| `domain/event/FormUploadedEvent.java` | `domain/business/event/FormUploadedEvent.java` |
| `domain/repository/ApplicationRepository.java` | `domain/business/repository/ApplicationRepository.java` |
| `domain/repository/BatchRepository.java` | `domain/business/repository/BatchRepository.java` |
| `domain/repository/FormRepository.java` | `domain/business/repository/FormRepository.java` |
| `domain/errorcode/CoreDomainErrorCode.java` | `domain/business/errorcode/CoreDomainErrorCode.java` |
| **测试** `domain/aggregate/root/BusinessApplicationAccessorTest.java` | `domain/business/aggregate/root/BusinessApplicationAccessorTest.java` |
| **测试** `domain/aggregate/valueobject/MaterialItemTest.java` | `domain/business/aggregate/valueobject/MaterialItemTest.java` |
| **测试** `domain/errorcode/CoreDomainErrorCodeTest.java` | `domain/business/errorcode/CoreDomainErrorCodeTest.java` |

### domain/engine(25 主代码)

| 原路径 | 目标路径 |
|--------|----------|
| `domain/spi/StepActionHandler.java` | `domain/engine/spi/StepActionHandler.java` |
| `domain/spi/StepExtensionAction.java` | `domain/engine/spi/StepExtensionAction.java` |
| `domain/spi/BusinessFactExtractor.java` | `domain/engine/spi/BusinessFactExtractor.java` |
| `domain/service/registry/AbstractStrategyRegistry.java` | `domain/engine/service/registry/AbstractStrategyRegistry.java` |
| `domain/service/registry/StepActionHandlerRegistry.java` | `domain/engine/service/registry/StepActionHandlerRegistry.java` |
| `domain/service/registry/ExtensionActionRegistry.java` | `domain/engine/service/registry/ExtensionActionRegistry.java` |
| `domain/service/registry/BusinessFactExtractorRegistry.java` | `domain/engine/service/registry/BusinessFactExtractorRegistry.java` |
| `domain/service/engine/MaterialRuleEngine.java` | `domain/engine/service/step/MaterialRuleEngine.java` |
| `domain/gateway/ConditionEvaluationGateway.java` | `domain/engine/gateway/ConditionEvaluationGateway.java` |
| `domain/gateway/BusinessConfigGateway.java` | `domain/engine/gateway/BusinessConfigGateway.java` |
| `domain/gateway/ApprovalIntegrationGateway.java` | `domain/engine/gateway/ApprovalIntegrationGateway.java` |
| `domain/gateway/FileIntegrationGateway.java` | `domain/engine/gateway/FileIntegrationGateway.java` |
| `domain/aggregate/valueobject/BusinessMetaContext.java` | `domain/engine/aggregate/valueobject/BusinessMetaContext.java` |
| `domain/aggregate/valueobject/PipelineExecutionResult.java` | `domain/engine/aggregate/valueobject/PipelineExecutionResult.java` |
| `domain/aggregate/valueobject/ExtensionExecutionResult.java` | `domain/engine/aggregate/valueobject/ExtensionExecutionResult.java` |
| `domain/aggregate/valueobject/config/StepRouteConfig.java` | `domain/engine/aggregate/valueobject/config/StepRouteConfig.java` |
| `domain/aggregate/valueobject/config/StepExtensionConfig.java` | `domain/engine/aggregate/valueobject/config/StepExtensionConfig.java` |
| `domain/aggregate/valueobject/config/ExtractorConfig.java` | `domain/engine/aggregate/valueobject/config/ExtractorConfig.java` |
| `domain/aggregate/valueobject/config/FormParsingConfig.java` | `domain/engine/aggregate/valueobject/config/FormParsingConfig.java` |
| `domain/aggregate/valueobject/config/MaterialRuleConfig.java` | `domain/engine/aggregate/valueobject/config/MaterialRuleConfig.java` |
| `domain/aggregate/valueobject/enums/status/StepExecutionStatus.java` | `domain/engine/aggregate/valueobject/enums/status/StepExecutionStatus.java` |
| `domain/aggregate/valueobject/enums/workflow/ApplicationFlowStep.java` | `domain/engine/aggregate/valueobject/enums/workflow/ApplicationFlowStep.java` |
| `domain/event/StepEnteredEvent.java` | `domain/engine/event/StepEnteredEvent.java` |
| `domain/event/PipelineExecutedEvent.java` | `domain/engine/event/PipelineExecutedEvent.java` |
| `domain/annotation/DomainService.java` | `domain/engine/annotation/DomainService.java` |

### application/engine(17 主代码 + 4 测试,含 1 个重命名)

| 原路径 | 目标路径 |
|--------|----------|
| `application/service/BusinessOrchestrationAppService.java` | `application/engine/service/FlowOrchestrationService.java` (**重命名**) |
| `application/pipeline/StepPipelineExecutor.java` | `application/engine/pipeline/StepPipelineExecutor.java` |
| `application/handler/FormParsingHandler.java` | `application/engine/step/handler/FormParsingHandler.java` |
| `application/handler/DefaultFormParsingHandler.java` | `application/engine/step/handler/DefaultFormParsingHandler.java` |
| `application/handler/FileServiceParseHandler.java` | `application/engine/step/handler/FileServiceParseHandler.java` |
| `application/handler/ApprovalSubmissionHandler.java` | `application/engine/step/handler/ApprovalSubmissionHandler.java` |
| `application/handler/PlanMaterialPreparationHandler.java` | `application/engine/step/handler/PlanMaterialPreparationHandler.java` |
| `application/extension/AbstractJsonStreamIngestionAction.java` | `application/engine/step/extension/AbstractJsonStreamIngestionAction.java` |
| `application/extension/AbstractStreamingDetailAction.java` | `application/engine/step/extension/AbstractStreamingDetailAction.java` |
| `application/extension/AbstractCursorPagingDetailAction.java` | `application/engine/step/extension/AbstractCursorPagingDetailAction.java` |
| `application/service/BusinessFormAppService.java` | `application/engine/step/service/BusinessFormAppService.java` |
| `application/service/MaterialAppService.java` | `application/engine/step/service/MaterialAppService.java` |
| `application/listener/ApplicationSpawnedListener.java` | `application/engine/listener/ApplicationSpawnedListener.java` |
| `application/listener/StepAutoAdvanceListener.java` | `application/engine/listener/StepAutoAdvanceListener.java` |
| `application/listener/ApprovalResultEventListener.java` | `application/engine/listener/ApprovalResultEventListener.java` |
| `application/listener/FileParsedEventListener.java` | `application/engine/listener/FileParsedEventListener.java` |
| `application/errorcode/CoreAppErrorCode.java` | `application/engine/errorcode/CoreAppErrorCode.java` |
| **测试** `application/handler/ApprovalSubmissionHandlerTest.java` | `application/engine/step/handler/ApprovalSubmissionHandlerTest.java` |
| **测试** `application/handler/FileServiceParseHandlerTest.java` | `application/engine/step/handler/FileServiceParseHandlerTest.java` |
| **测试** `application/listener/ApprovalResultEventListenerTest.java` | `application/engine/listener/ApprovalResultEventListenerTest.java` |
| **测试** `application/listener/FileParsedEventListenerTest.java` | `application/engine/listener/FileParsedEventListenerTest.java` |

### infrastructure/engine(7 主代码)

| 原路径 | 目标路径 |
|--------|----------|
| `infrastructure/evaluation/SpelConditionEvaluationGateway.java` | `infrastructure/engine/evaluation/SpelConditionEvaluationGateway.java` |
| `infrastructure/configuration/DomainServiceConfiguration.java` | `infrastructure/engine/configuration/DomainServiceConfiguration.java` |
| `infrastructure/configuration/LibJacksonModule.java` | `infrastructure/engine/configuration/LibJacksonModule.java` |
| `infrastructure/gateway/ApprovalServiceIntegrationGateway.java` | `infrastructure/engine/gateway/ApprovalServiceIntegrationGateway.java` |
| `infrastructure/gateway/FileServiceIntegrationGateway.java` | `infrastructure/engine/gateway/FileServiceIntegrationGateway.java` |
| `infrastructure/json/BusinessExtensionMixIn.java` | `infrastructure/engine/json/BusinessExtensionMixIn.java` |
| `infrastructure/event/IntegrationEventSimulator.java` | `infrastructure/engine/event/IntegrationEventSimulator.java` |

---

## Task 1: 创建分支 + 基线验证

**Files:**
- 无文件修改

- [ ] **Step 1: 创建重构分支**

```bash
git checkout -b refactor/kernel-repackage
```

- [ ] **Step 2: 运行基线测试,确认全部通过**

```bash
mvn clean test -pl business-core-kernel/business-core-domain,business-core-kernel/business-core-application,annuity-service/annuity-domain,annuity-service/annuity-application,annuity-service/annuity-starter -am
```

Expected: BUILD SUCCESS,所有测试通过(kernel + annuity-service 47 测试)

- [ ] **Step 3: 记录基线测试数量**

确认当前测试总数,作为后续任务的回归基准。

---

## Task 2: 移动 domain/business 层

**Files:**
- 移动 30 个主代码文件 + 3 个测试文件(见上方映射表)
- 更新所有移动文件的 `package` 声明
- 更新 kernel 内部引用这些类的 `import` 语句

**Interfaces:**
- Consumes: 无(第一批移动,无前置依赖)
- Produces: `com.example.core.domain.business.*` 包路径下的所有业务领域模型类

**策略**: 由于移动文件量大,使用 `git mv` 保留历史。移动后批量更新 package 声明和 import。

- [ ] **Step 1: 创建目标目录结构**

```bash
cd business-core-kernel/business-core-domain/src/main/java/com/example/core/domain
mkdir -p business/aggregate/root
mkdir -p business/aggregate/valueobject/reference
mkdir -p business/aggregate/valueobject/business
mkdir -p business/aggregate/valueobject/enums/status
mkdir -p business/aggregate/valueobject/enums/material
mkdir -p business/aggregate/valueobject/enums/validate
mkdir -p business/event
mkdir -p business/repository
mkdir -p business/errorcode
```

- [ ] **Step 2: 用 git mv 移动聚合根**

```bash
cd business-core-kernel/business-core-domain/src/main/java/com/example/core/domain
git mv aggregate/root/BusinessApplication.java business/aggregate/root/
git mv aggregate/root/BusinessBatch.java business/aggregate/root/
git mv aggregate/root/BusinessForm.java business/aggregate/root/
```

- [ ] **Step 3: 用 git mv 移动业务值对象**

```bash
cd business-core-kernel/business-core-domain/src/main/java/com/example/core/domain
git mv aggregate/valueobject/BusinessContext.java business/aggregate/valueobject/
git mv aggregate/valueobject/BusinessExtension.java business/aggregate/valueobject/
git mv aggregate/valueobject/BusinessFile.java business/aggregate/valueobject/
git mv aggregate/valueobject/MaterialItem.java business/aggregate/valueobject/
git mv aggregate/valueobject/MaterialConditionContext.java business/aggregate/valueobject/
git mv aggregate/valueobject/OperatorInfo.java business/aggregate/valueobject/
git mv aggregate/valueobject/ParsedPlanResult.java business/aggregate/valueobject/
git mv aggregate/valueobject/ValidationResult.java business/aggregate/valueobject/
```

- [ ] **Step 4: 用 git mv 移动引用值对象和业务枚举**

```bash
cd business-core-kernel/business-core-domain/src/main/java/com/example/core/domain
git mv aggregate/valueobject/reference/BusinessFormRef.java business/aggregate/valueobject/reference/
git mv aggregate/valueobject/reference/PlanBizApplicationRef.java business/aggregate/valueobject/reference/
git mv aggregate/valueobject/business/AccountManager.java business/aggregate/valueobject/business/
git mv aggregate/valueobject/business/AnnuityChannel.java business/aggregate/valueobject/business/
git mv aggregate/valueobject/business/BusinessLevel.java business/aggregate/valueobject/business/
git mv aggregate/valueobject/business/BusinessType.java business/aggregate/valueobject/business/
git mv aggregate/valueobject/business/OperationModel.java business/aggregate/valueobject/business/
```

- [ ] **Step 5: 用 git mv 移动枚举**

```bash
cd business-core-kernel/business-core-domain/src/main/java/com/example/core/domain
git mv aggregate/valueobject/enums/status/ApplicationStatus.java business/aggregate/valueobject/enums/status/
git mv aggregate/valueobject/enums/status/BatchStatus.java business/aggregate/valueobject/enums/status/
git mv aggregate/valueobject/enums/status/FormStatus.java business/aggregate/valueobject/enums/status/
git mv aggregate/valueobject/enums/material/RequirementType.java business/aggregate/valueobject/enums/material/
git mv aggregate/valueobject/enums/validate/ValidationType.java business/aggregate/valueobject/enums/validate/
```

- [ ] **Step 6: 用 git mv 移动事件、Repository、错误码**

```bash
cd business-core-kernel/business-core-domain/src/main/java/com/example/core/domain
git mv event/ApplicationSpawnedEvent.java business/event/
git mv event/BatchStatusChangedEvent.java business/event/
git mv event/FormUploadedEvent.java business/event/
git mv repository/ApplicationRepository.java business/repository/
git mv repository/BatchRepository.java business/repository/
git mv repository/FormRepository.java business/repository/
git mv errorcode/CoreDomainErrorCode.java business/errorcode/
```

- [ ] **Step 7: 移动测试文件**

```bash
cd business-core-kernel/business-core-domain/src/test/java/com/example/core/domain
mkdir -p business/aggregate/root
mkdir -p business/aggregate/valueobject
mkdir -p business/errorcode
git mv aggregate/root/BusinessApplicationAccessorTest.java business/aggregate/root/
git mv aggregate/valueobject/MaterialItemTest.java business/aggregate/valueobject/
git mv errorcode/CoreDomainErrorCodeTest.java business/errorcode/
```

- [ ] **Step 8: 批量更新 package 声明**

对所有移动到 `business/` 下的文件,将 `package com.example.core.domain.aggregate.root` 等旧包名替换为新包名。

替换规则(在 `business-core-domain/src/main/java/com/example/core/domain/business` 目录下递归执行):
- `package com.example.core.domain.aggregate.root` → `package com.example.core.domain.business.aggregate.root`
- `package com.example.core.domain.aggregate.valueobject` → `package com.example.core.domain.business.aggregate.valueobject`
- `package com.example.core.domain.aggregate.valueobject.reference` → `package com.example.core.domain.business.aggregate.valueobject.reference`
- `package com.example.core.domain.aggregate.valueobject.business` → `package com.example.core.domain.business.aggregate.valueobject.business`
- `package com.example.core.domain.aggregate.valueobject.enums.status` → `package com.example.core.domain.business.aggregate.valueobject.enums.status`
- `package com.example.core.domain.aggregate.valueobject.enums.material` → `package com.example.core.domain.business.aggregate.valueobject.enums.material`
- `package com.example.core.domain.aggregate.valueobject.enums.validate` → `package com.example.core.domain.business.aggregate.valueobject.enums.validate`
- `package com.example.core.domain.event` → `package com.example.core.domain.business.event`
- `package com.example.core.domain.repository` → `package com.example.core.domain.business.repository`
- `package com.example.core.domain.errorcode` → `package com.example.core.domain.business.errorcode`

测试文件同理(在 `src/test/java` 对应目录下)。

- [ ] **Step 9: 更新 kernel 内部 import**

在 kernel 三个模块中,搜索并替换所有引用已移动类的 import 语句。替换规则:
- `import com.example.core.domain.aggregate.root.BusinessApplication` → `import com.example.core.domain.business.aggregate.root.BusinessApplication`
- `import com.example.core.domain.aggregate.valueobject.BusinessContext` → `import com.example.core.domain.business.aggregate.valueobject.BusinessContext`
- `import com.example.core.domain.aggregate.valueobject.business.` → `import com.example.core.domain.business.aggregate.valueobject.business.`
- `import com.example.core.domain.aggregate.valueobject.enums.status.ApplicationStatus` → `import com.example.core.domain.business.aggregate.valueobject.enums.status.ApplicationStatus`
- `import com.example.core.domain.aggregate.valueobject.enums.status.BatchStatus` → `import com.example.core.domain.business.aggregate.valueobject.enums.status.BatchStatus`
- `import com.example.core.domain.aggregate.valueobject.enums.status.FormStatus` → `import com.example.core.domain.business.aggregate.valueobject.enums.status.FormStatus`
- `import com.example.core.domain.aggregate.valueobject.enums.material.` → `import com.example.core.domain.business.aggregate.valueobject.enums.material.`
- `import com.example.core.domain.aggregate.valueobject.enums.validate.` → `import com.example.core.domain.business.aggregate.valueobject.enums.validate.`
- `import com.example.core.domain.aggregate.valueobject.reference.` → `import com.example.core.domain.business.aggregate.valueobject.reference.`
- `import com.example.core.domain.aggregate.valueobject.BusinessExtension` → `import com.example.core.domain.business.aggregate.valueobject.BusinessExtension`
- `import com.example.core.domain.aggregate.valueobject.BusinessFile` → `import com.example.core.domain.business.aggregate.valueobject.BusinessFile`
- `import com.example.core.domain.aggregate.valueobject.MaterialItem` → `import com.example.core.domain.business.aggregate.valueobject.MaterialItem`
- `import com.example.core.domain.aggregate.valueobject.MaterialConditionContext` → `import com.example.core.domain.business.aggregate.valueobject.MaterialConditionContext`
- `import com.example.core.domain.aggregate.valueobject.OperatorInfo` → `import com.example.core.domain.business.aggregate.valueobject.OperatorInfo`
- `import com.example.core.domain.aggregate.valueobject.ParsedPlanResult` → `import com.example.core.domain.business.aggregate.valueobject.ParsedPlanResult`
- `import com.example.core.domain.aggregate.valueobject.ValidationResult` → `import com.example.core.domain.business.aggregate.valueobject.ValidationResult`
- `import com.example.core.domain.event.ApplicationSpawnedEvent` → `import com.example.core.domain.business.event.ApplicationSpawnedEvent`
- `import com.example.core.domain.event.BatchStatusChangedEvent` → `import com.example.core.domain.business.event.BatchStatusChangedEvent`
- `import com.example.core.domain.event.FormUploadedEvent` → `import com.example.core.domain.business.event.FormUploadedEvent`
- `import com.example.core.domain.repository.` → `import com.example.core.domain.business.repository.`
- `import com.example.core.domain.errorcode.` → `import com.example.core.domain.business.errorcode.`

**注意**: 通配符 import `import com.example.core.domain.aggregate.valueobject.*` 需要拆分为 business 和 engine 两部分(本任务只处理 business 部分,engine 部分在 Task 3 处理)。检查 `BusinessApplication.java` 等文件是否有 `import com.example.core.domain.aggregate.valueobject.*` 通配符,如果有则替换为逐个显式 import。

- [ ] **Step 10: 编译验证(仅 domain 模块)**

```bash
mvn clean compile -pl business-core-kernel/business-core-domain -DskipTests
```

Expected: BUILD SUCCESS。如果有编译错误,说明有遗漏的 import 更新,逐一修复。

- [ ] **Step 11: 编译验证(kernel 全量)**

```bash
mvn clean compile -pl business-core-kernel/business-core-domain,business-core-kernel/business-core-application,business-core-kernel/business-core-infrastructure -am -DskipTests
```

Expected: BUILD SUCCESS。application/infrastructure 层会有编译错误(因为它们引用的 domain 类包路径变了),此步骤用于发现需要更新的 import,逐一修复。

- [ ] **Step 12: 运行 domain 测试**

```bash
mvn test -pl business-core-kernel/business-core-domain
```

Expected: 全部通过

- [ ] **Step 13: 提交**

```bash
git add -A
git commit -m "refactor(kernel): move domain/business package - aggregates, valueobjects, events, repositories, errorcode"
```

---

## Task 3: 移动 domain/engine 层

**Files:**
- 移动 25 个主代码文件(见上方映射表)
- 更新所有移动文件的 `package` 声明
- 更新 kernel 内部引用这些类的 `import` 语句

**Interfaces:**
- Consumes: Task 2 的 business 包已完成
- Produces: `com.example.core.domain.engine.*` 包路径下的所有引擎核心类

- [ ] **Step 1: 创建目标目录结构**

```bash
cd business-core-kernel/business-core-domain/src/main/java/com/example/core/domain
mkdir -p engine/spi
mkdir -p engine/service/registry
mkdir -p engine/service/step
mkdir -p engine/gateway
mkdir -p engine/aggregate/valueobject/config
mkdir -p engine/aggregate/valueobject/enums/status
mkdir -p engine/aggregate/valueobject/enums/workflow
mkdir -p engine/event
mkdir -p engine/annotation
```

- [ ] **Step 2: 用 git mv 移动 SPI 接口**

```bash
cd business-core-kernel/business-core-domain/src/main/java/com/example/core/domain
git mv spi/StepActionHandler.java engine/spi/
git mv spi/StepExtensionAction.java engine/spi/
git mv spi/BusinessFactExtractor.java engine/spi/
```

- [ ] **Step 3: 用 git mv 移动 Registry 和 MaterialRuleEngine**

```bash
cd business-core-kernel/business-core-domain/src/main/java/com/example/core/domain
git mv service/registry/AbstractStrategyRegistry.java engine/service/registry/
git mv service/registry/StepActionHandlerRegistry.java engine/service/registry/
git mv service/registry/ExtensionActionRegistry.java engine/service/registry/
git mv service/registry/BusinessFactExtractorRegistry.java engine/service/registry/
git mv service/engine/MaterialRuleEngine.java engine/service/step/
```

- [ ] **Step 4: 用 git mv 移动 Gateway 接口**

```bash
cd business-core-kernel/business-core-domain/src/main/java/com/example/core/domain
git mv gateway/ConditionEvaluationGateway.java engine/gateway/
git mv gateway/BusinessConfigGateway.java engine/gateway/
git mv gateway/ApprovalIntegrationGateway.java engine/gateway/
git mv gateway/FileIntegrationGateway.java engine/gateway/
```

- [ ] **Step 5: 用 git mv 移动引擎值对象**

```bash
cd business-core-kernel/business-core-domain/src/main/java/com/example/core/domain
git mv aggregate/valueobject/BusinessMetaContext.java engine/aggregate/valueobject/
git mv aggregate/valueobject/PipelineExecutionResult.java engine/aggregate/valueobject/
git mv aggregate/valueobject/ExtensionExecutionResult.java engine/aggregate/valueobject/
git mv aggregate/valueobject/config/StepRouteConfig.java engine/aggregate/valueobject/config/
git mv aggregate/valueobject/config/StepExtensionConfig.java engine/aggregate/valueobject/config/
git mv aggregate/valueobject/config/ExtractorConfig.java engine/aggregate/valueobject/config/
git mv aggregate/valueobject/config/FormParsingConfig.java engine/aggregate/valueobject/config/
git mv aggregate/valueobject/config/MaterialRuleConfig.java engine/aggregate/valueobject/config/
```

- [ ] **Step 6: 用 git mv 移动枚举、事件、注解**

```bash
cd business-core-kernel/business-core-domain/src/main/java/com/example/core/domain
git mv aggregate/valueobject/enums/status/StepExecutionStatus.java engine/aggregate/valueobject/enums/status/
git mv aggregate/valueobject/enums/workflow/ApplicationFlowStep.java engine/aggregate/valueobject/enums/workflow/
git mv event/StepEnteredEvent.java engine/event/
git mv event/PipelineExecutedEvent.java engine/event/
git mv annotation/DomainService.java engine/annotation/
```

- [ ] **Step 7: 批量更新 package 声明**

替换规则(在 `business-core-domain/src/main/java/com/example/core/domain/engine` 目录下递归执行):
- `package com.example.core.domain.spi` → `package com.example.core.domain.engine.spi`
- `package com.example.core.domain.service.registry` → `package com.example.core.domain.engine.service.registry`
- `package com.example.core.domain.service.engine` → `package com.example.core.domain.engine.service.step`
- `package com.example.core.domain.gateway` → `package com.example.core.domain.engine.gateway`
- `package com.example.core.domain.aggregate.valueobject` → `package com.example.core.domain.engine.aggregate.valueobject`(**仅对 engine 目录下的文件**)
- `package com.example.core.domain.aggregate.valueobject.config` → `package com.example.core.domain.engine.aggregate.valueobject.config`
- `package com.example.core.domain.aggregate.valueobject.enums.status` → `package com.example.core.domain.engine.aggregate.valueobject.enums.status`(**仅对 StepExecutionStatus**)
- `package com.example.core.domain.aggregate.valueobject.enums.workflow` → `package com.example.core.domain.engine.aggregate.valueobject.enums.workflow`
- `package com.example.core.domain.event` → `package com.example.core.domain.engine.event`(**仅对 StepEnteredEvent/PipelineExecutedEvent**)
- `package com.example.core.domain.annotation` → `package com.example.core.domain.engine.annotation`

- [ ] **Step 8: 更新 kernel 内部 import**

在 kernel 三个模块中,搜索并替换所有引用已移动引擎类的 import 语句。替换规则:
- `import com.example.core.domain.spi.` → `import com.example.core.domain.engine.spi.`
- `import com.example.core.domain.service.registry.` → `import com.example.core.domain.engine.service.registry.`
- `import com.example.core.domain.service.engine.` → `import com.example.core.domain.engine.service.step.`
- `import com.example.core.domain.gateway.` → `import com.example.core.domain.engine.gateway.`
- `import com.example.core.domain.aggregate.valueobject.BusinessMetaContext` → `import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext`
- `import com.example.core.domain.aggregate.valueobject.PipelineExecutionResult` → `import com.example.core.domain.engine.aggregate.valueobject.PipelineExecutionResult`
- `import com.example.core.domain.aggregate.valueobject.ExtensionExecutionResult` → `import com.example.core.domain.engine.aggregate.valueobject.ExtensionExecutionResult`
- `import com.example.core.domain.aggregate.valueobject.config.` → `import com.example.core.domain.engine.aggregate.valueobject.config.`
- `import com.example.core.domain.aggregate.valueobject.enums.status.StepExecutionStatus` → `import com.example.core.domain.engine.aggregate.valueobject.enums.status.StepExecutionStatus`
- `import com.example.core.domain.aggregate.valueobject.enums.workflow.` → `import com.example.core.domain.engine.aggregate.valueobject.enums.workflow.`
- `import com.example.core.domain.event.StepEnteredEvent` → `import com.example.core.domain.engine.event.StepEnteredEvent`
- `import com.example.core.domain.event.PipelineExecutedEvent` → `import com.example.core.domain.engine.event.PipelineExecutedEvent`
- `import com.example.core.domain.annotation.` → `import com.example.core.domain.engine.annotation.`

**注意**: 处理通配符 import。`BusinessApplication.java` 中的 `import com.example.core.domain.aggregate.valueobject.*` 需要拆分为:
- business 部分: `import com.example.core.domain.business.aggregate.valueobject.*`(已在 Task 2 处理)
- engine 部分: 显式 import `BusinessMetaContext`、`PipelineExecutionResult` 等(本步骤处理)

同时检查 `import com.example.core.domain.event.*` 通配符,拆分为 business 事件和 engine 事件两部分。

- [ ] **Step 9: 清理空目录**

```bash
cd business-core-kernel/business-core-domain/src/main/java/com/example/core/domain
# 删除已清空的旧目录(如果存在)
rmdir aggregate/root 2>nul
rmdir aggregate/valueobject/reference 2>nul
rmdir aggregate/valueobject/business 2>nul
rmdir aggregate/valueobject/config 2>nul
rmdir aggregate/valueobject/enums/status 2>nul
rmdir aggregate/valueobject/enums/material 2>nul
rmdir aggregate/valueobject/enums/validate 2>nul
rmdir aggregate/valueobject/enums/workflow 2>nul
rmdir aggregate/valueobject/enums 2>nul
rmdir aggregate/valueobject 2>nul
rmdir aggregate 2>nul
rmdir spi 2>nul
rmdir service/registry 2>nul
rmdir service/engine 2>nul
rmdir service 2>nul
rmdir gateway 2>nul
rmdir event 2>nul
rmdir annotation 2>nul
rmdir repository 2>nul
rmdir errorcode 2>nul
```

- [ ] **Step 10: 编译验证(kernel 全量)**

```bash
mvn clean compile -pl business-core-kernel/business-core-domain,business-core-kernel/business-core-application,business-core-kernel/business-core-infrastructure -am -DskipTests
```

Expected: BUILD SUCCESS。如有错误,逐一修复遗漏的 import。

- [ ] **Step 11: 运行 kernel 测试**

```bash
mvn test -pl business-core-kernel/business-core-domain,business-core-kernel/business-core-application
```

Expected: 全部通过

- [ ] **Step 12: 提交**

```bash
git add -A
git commit -m "refactor(kernel): move domain/engine package - spi, registry, gateway, valueobjects, events, annotation"
```

---

## Task 4: 移动 application/engine 层 + 重命名

**Files:**
- 移动 17 个主代码文件 + 4 个测试文件(见上方映射表)
- 重命名 `BusinessOrchestrationAppService.java` → `FlowOrchestrationService.java`
- 更新所有 package 声明和 import

**Interfaces:**
- Consumes: Task 3 的 domain/engine 包已完成
- Produces: `com.example.core.application.engine.*` 包路径下的所有应用层类

- [ ] **Step 1: 创建目标目录结构**

```bash
cd business-core-kernel/business-core-application/src/main/java/com/example/core/application
mkdir -p engine/service
mkdir -p engine/pipeline
mkdir -p engine/step/handler
mkdir -p engine/step/extension
mkdir -p engine/step/service
mkdir -p engine/listener
mkdir -p engine/errorcode
```

- [ ] **Step 2: 用 git mv 移动并重命名 BusinessOrchestrationAppService**

```bash
cd business-core-kernel/business-core-application/src/main/java/com/example/core/application
git mv service/BusinessOrchestrationAppService.java engine/service/FlowOrchestrationService.java
```

- [ ] **Step 3: 用 git mv 移动 pipeline 和 handler**

```bash
cd business-core-kernel/business-core-application/src/main/java/com/example/core/application
git mv pipeline/StepPipelineExecutor.java engine/pipeline/
git mv handler/FormParsingHandler.java engine/step/handler/
git mv handler/DefaultFormParsingHandler.java engine/step/handler/
git mv handler/FileServiceParseHandler.java engine/step/handler/
git mv handler/ApprovalSubmissionHandler.java engine/step/handler/
git mv handler/PlanMaterialPreparationHandler.java engine/step/handler/
```

- [ ] **Step 4: 用 git mv 移动 extension 和 service**

```bash
cd business-core-kernel/business-core-application/src/main/java/com/example/core/application
git mv extension/AbstractJsonStreamIngestionAction.java engine/step/extension/
git mv extension/AbstractStreamingDetailAction.java engine/step/extension/
git mv extension/AbstractCursorPagingDetailAction.java engine/step/extension/
git mv service/BusinessFormAppService.java engine/step/service/
git mv service/MaterialAppService.java engine/step/service/
```

- [ ] **Step 5: 用 git mv 移动 listener 和 errorcode**

```bash
cd business-core-kernel/business-core-application/src/main/java/com/example/core/application
git mv listener/ApplicationSpawnedListener.java engine/listener/
git mv listener/StepAutoAdvanceListener.java engine/listener/
git mv listener/ApprovalResultEventListener.java engine/listener/
git mv listener/FileParsedEventListener.java engine/listener/
git mv errorcode/CoreAppErrorCode.java engine/errorcode/
```

- [ ] **Step 6: 移动测试文件**

```bash
cd business-core-kernel/business-core-application/src/test/java/com/example/core/application
mkdir -p engine/step/handler
mkdir -p engine/listener
git mv handler/ApprovalSubmissionHandlerTest.java engine/step/handler/
git mv handler/FileServiceParseHandlerTest.java engine/step/handler/
git mv listener/ApprovalResultEventListenerTest.java engine/listener/
git mv listener/FileParsedEventListenerTest.java engine/listener/
```

- [ ] **Step 7: 批量更新 package 声明**

替换规则:
- `package com.example.core.application.service` → `package com.example.core.application.engine.service`(**BusinessFormAppService/MaterialAppService**)
- `package com.example.core.application.pipeline` → `package com.example.core.application.engine.pipeline`
- `package com.example.core.application.handler` → `package com.example.core.application.engine.step.handler`
- `package com.example.core.application.extension` → `package com.example.core.application.engine.step.extension`
- `package com.example.core.application.listener` → `package com.example.core.application.engine.listener`
- `package com.example.core.application.errorcode` → `package com.example.core.application.engine.errorcode`

测试文件同理。

- [ ] **Step 8: 更新 FlowOrchestrationService 类名**

将 `FlowOrchestrationService.java` 中的 `class BusinessOrchestrationAppService` 改为 `class FlowOrchestrationService`。

- [ ] **Step 9: 更新 kernel 内部 import 和引用**

搜索 kernel 中所有引用 `BusinessOrchestrationAppService` 的地方,替换为 `FlowOrchestrationService`:
- import 语句: `import com.example.core.application.service.BusinessOrchestrationAppService` → `import com.example.core.application.engine.service.FlowOrchestrationService`
- 类型引用: `BusinessOrchestrationAppService` → `FlowOrchestrationService`
- 字段声明: `private final BusinessOrchestrationAppService` → `private final FlowOrchestrationService`

同时更新其他 application 层 import:
- `import com.example.core.application.pipeline.` → `import com.example.core.application.engine.pipeline.`
- `import com.example.core.application.handler.` → `import com.example.core.application.engine.step.handler.`
- `import com.example.core.application.extension.` → `import com.example.core.application.engine.step.extension.`
- `import com.example.core.application.service.BusinessFormAppService` → `import com.example.core.application.engine.step.service.BusinessFormAppService`
- `import com.example.core.application.service.MaterialAppService` → `import com.example.core.application.engine.step.service.MaterialAppService`
- `import com.example.core.application.listener.` → `import com.example.core.application.engine.listener.`
- `import com.example.core.application.errorcode.` → `import com.example.core.application.engine.errorcode.`

- [ ] **Step 10: 清理空目录**

```bash
cd business-core-kernel/business-core-application/src/main/java/com/example/core/application
rmdir service 2>nul
rmdir pipeline 2>nul
rmdir handler 2>nul
rmdir extension 2>nul
rmdir listener 2>nul
rmdir errorcode 2>nul
```

- [ ] **Step 11: 编译验证(kernel 全量)**

```bash
mvn clean compile -pl business-core-kernel/business-core-domain,business-core-kernel/business-core-application,business-core-kernel/business-core-infrastructure -am -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 12: 运行 kernel 测试**

```bash
mvn test -pl business-core-kernel/business-core-domain,business-core-kernel/business-core-application
```

Expected: 全部通过

- [ ] **Step 13: 提交**

```bash
git add -A
git commit -m "refactor(kernel): move application/engine package + rename BusinessOrchestrationAppService to FlowOrchestrationService"
```

---

## Task 5: 移动 infrastructure/engine 层

**Files:**
- 移动 7 个主代码文件(见上方映射表)
- 更新所有 package 声明和 import

**Interfaces:**
- Consumes: Task 3/4 的 domain/engine 和 application/engine 包已完成
- Produces: `com.example.core.infrastructure.engine.*` 包路径下的所有基础设施类

- [ ] **Step 1: 创建目标目录结构**

```bash
cd business-core-kernel/business-core-infrastructure/src/main/java/com/example/core/infrastructure
mkdir -p engine/evaluation
mkdir -p engine/configuration
mkdir -p engine/gateway
mkdir -p engine/json
mkdir -p engine/event
```

- [ ] **Step 2: 用 git mv 移动所有文件**

```bash
cd business-core-kernel/business-core-infrastructure/src/main/java/com/example/core/infrastructure
git mv evaluation/SpelConditionEvaluationGateway.java engine/evaluation/
git mv configuration/DomainServiceConfiguration.java engine/configuration/
git mv configuration/LibJacksonModule.java engine/configuration/
git mv gateway/ApprovalServiceIntegrationGateway.java engine/gateway/
git mv gateway/FileServiceIntegrationGateway.java engine/gateway/
git mv json/BusinessExtensionMixIn.java engine/json/
git mv event/IntegrationEventSimulator.java engine/event/
```

- [ ] **Step 3: 批量更新 package 声明**

替换规则:
- `package com.example.core.infrastructure.evaluation` → `package com.example.core.infrastructure.engine.evaluation`
- `package com.example.core.infrastructure.configuration` → `package com.example.core.infrastructure.engine.configuration`
- `package com.example.core.infrastructure.gateway` → `package com.example.core.infrastructure.engine.gateway`
- `package com.example.core.infrastructure.json` → `package com.example.core.infrastructure.engine.json`
- `package com.example.core.infrastructure.event` → `package com.example.core.infrastructure.engine.event`

- [ ] **Step 4: 更新 infrastructure 内部 import**

搜索 infrastructure 模块中的 import 语句:
- `import com.example.core.infrastructure.evaluation.` → `import com.example.core.infrastructure.engine.evaluation.`
- `import com.example.core.infrastructure.configuration.` → `import com.example.core.infrastructure.engine.configuration.`
- `import com.example.core.infrastructure.gateway.` → `import com.example.core.infrastructure.engine.gateway.`
- `import com.example.core.infrastructure.json.` → `import com.example.core.infrastructure.engine.json.`
- `import com.example.core.infrastructure.event.` → `import com.example.core.infrastructure.engine.event.`

- [ ] **Step 5: 清理空目录**

```bash
cd business-core-kernel/business-core-infrastructure/src/main/java/com/example/core/infrastructure
rmdir evaluation 2>nul
rmdir configuration 2>nul
rmdir gateway 2>nul
rmdir json 2>nul
rmdir event 2>nul
```

- [ ] **Step 6: 编译验证(kernel 全量)**

```bash
mvn clean compile -pl business-core-kernel/business-core-domain,business-core-kernel/business-core-application,business-core-kernel/business-core-infrastructure -am -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 7: 运行 kernel 全量测试**

```bash
mvn test -pl business-core-kernel/business-core-domain,business-core-kernel/business-core-application,business-core-kernel/business-core-infrastructure
```

Expected: 全部通过

- [ ] **Step 8: 提交**

```bash
git add -A
git commit -m "refactor(kernel): move infrastructure/engine package - evaluation, configuration, gateway, json, event"
```

---

## Task 6: 更新 annuity-service 的 import

**Files:**
- 更新 annuity-service 中所有引用 kernel 类的 import 语句(~166 处 import,涉及 ~30 个文件)

**Interfaces:**
- Consumes: Task 2-5 的 kernel 包重构已完成
- Produces: annuity-service 编译通过且测试通过

**影响范围**: annuity-service 的 adapter/application/domain/infrastructure/starter 模块中所有 import `com.example.core.*` 的文件。

- [ ] **Step 1: 搜索所有需要更新的 import**

```bash
# 统计影响范围
# 在 annuity-service 目录下搜索所有 import com.example.core 的文件
```

- [ ] **Step 2: 批量替换 domain 层 import**

在 `annuity-service` 目录下,对以下 import 模式执行全局替换:

**business 包替换**:
- `import com.example.core.domain.aggregate.root.BusinessApplication` → `import com.example.core.domain.business.aggregate.root.BusinessApplication`
- `import com.example.core.domain.aggregate.root.BusinessBatch` → `import com.example.core.domain.business.aggregate.root.BusinessBatch`
- `import com.example.core.domain.aggregate.root.BusinessForm` → `import com.example.core.domain.business.aggregate.root.BusinessForm`
- `import com.example.core.domain.aggregate.valueobject.BusinessContext` → `import com.example.core.domain.business.aggregate.valueobject.BusinessContext`
- `import com.example.core.domain.aggregate.valueobject.BusinessExtension` → `import com.example.core.domain.business.aggregate.valueobject.BusinessExtension`
- `import com.example.core.domain.aggregate.valueobject.OperatorInfo` → `import com.example.core.domain.business.aggregate.valueobject.OperatorInfo`
- `import com.example.core.domain.aggregate.valueobject.business.` → `import com.example.core.domain.business.aggregate.valueobject.business.`
- `import com.example.core.domain.repository.` → `import com.example.core.domain.business.repository.`
- `import com.example.core.domain.errorcode.` → `import com.example.core.domain.business.errorcode.`

**engine 包替换**:
- `import com.example.core.domain.aggregate.valueobject.BusinessMetaContext` → `import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext`
- `import com.example.core.domain.aggregate.valueobject.ExtensionExecutionResult` → `import com.example.core.domain.engine.aggregate.valueobject.ExtensionExecutionResult`
- `import com.example.core.domain.aggregate.valueobject.enums.status.StepExecutionStatus` → `import com.example.core.domain.engine.aggregate.valueobject.enums.status.StepExecutionStatus`
- `import com.example.core.domain.aggregate.valueobject.enums.workflow.ApplicationFlowStep` → `import com.example.core.domain.engine.aggregate.valueobject.enums.workflow.ApplicationFlowStep`
- `import com.example.core.domain.spi.` → `import com.example.core.domain.engine.spi.`
- `import com.example.core.domain.service.registry.` → `import com.example.core.domain.engine.service.registry.`
- `import com.example.core.domain.service.engine.` → `import com.example.core.domain.engine.service.step.`
- `import com.example.core.domain.annotation.` → `import com.example.core.domain.engine.annotation.`

- [ ] **Step 3: 批量替换 application 层 import**

- `import com.example.core.application.service.BusinessOrchestrationAppService` → `import com.example.core.application.engine.service.FlowOrchestrationService`
- `import com.example.core.application.handler.` → `import com.example.core.application.engine.step.handler.`
- `import com.example.core.application.listener.` → `import com.example.core.application.engine.listener.`

**同时更新类型引用**: `BusinessOrchestrationAppService` → `FlowOrchestrationService`(字段声明、构造函数参数、方法参数等)。

- [ ] **Step 4: 批量替换 infrastructure 层 import**

- `import com.example.core.infrastructure.event.` → `import com.example.core.infrastructure.engine.event.`

- [ ] **Step 5: 检查通配符 import**

搜索 annuity-service 中是否有 `import com.example.core.domain.aggregate.valueobject.*` 或 `import com.example.core.domain.event.*` 等通配符 import,逐一替换为显式 import。

- [ ] **Step 6: 编译验证(annuity-service 全量)**

```bash
mvn clean compile -pl annuity-service/annuity-types,annuity-service/annuity-domain,annuity-service/annuity-api,annuity-service/annuity-application,annuity-service/annuity-adapter,annuity-service/annuity-infrastructure,annuity-service/annuity-starter -am -DskipTests
```

Expected: BUILD SUCCESS。如有错误,逐一修复遗漏的 import 或类型引用。

- [ ] **Step 7: 运行 annuity-service 测试**

```bash
mvn test -pl annuity-service/annuity-domain,annuity-service/annuity-application,annuity-service/annuity-starter
```

Expected: 47 测试全部通过

- [ ] **Step 8: 提交**

```bash
git add -A
git commit -m "refactor(annuity): update imports to match kernel business/engine package structure"
```

---

## Task 7: 最终验证 + 包结构检查

**Files:**
- 无文件修改,仅验证

- [ ] **Step 1: 全量编译**

```bash
mvn clean compile -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 2: 全量测试**

```bash
mvn test
```

Expected: 全部通过,测试数量与基线一致

- [ ] **Step 3: 验证包结构**

检查以下目录结构是否存在:
- `business-core-domain/.../domain/business/aggregate/root/` — 含 3 聚合根
- `business-core-domain/.../domain/business/event/` — 含 3 事件
- `business-core-domain/.../domain/business/repository/` — 含 3 Repository
- `business-core-domain/.../domain/engine/spi/` — 含 3 SPI
- `business-core-domain/.../domain/engine/gateway/` — 含 4 网关
- `business-core-domain/.../domain/engine/service/registry/` — 含 4 Registry
- `business-core-application/.../application/engine/service/` — 含 FlowOrchestrationService
- `business-core-application/.../application/engine/pipeline/` — 含 StepPipelineExecutor
- `business-core-application/.../application/engine/step/handler/` — 含 5 Handler
- `business-core-infrastructure/.../infrastructure/engine/` — 含 7 实现类

- [ ] **Step 4: 验证无残留旧包**

检查以下旧目录是否已清空(不含 .java 文件):
- `business-core-domain/.../domain/aggregate/`
- `business-core-domain/.../domain/spi/`
- `business-core-domain/.../domain/gateway/`
- `business-core-domain/.../domain/event/`(旧位置)
- `business-core-domain/.../domain/repository/`(旧位置)
- `business-core-domain/.../domain/errorcode/`(旧位置)
- `business-core-domain/.../domain/annotation/`(旧位置)
- `business-core-domain/.../domain/service/`(旧位置)
- `business-core-application/.../application/handler/`
- `business-core-application/.../application/extension/`
- `business-core-application/.../application/service/`(旧位置)
- `business-core-application/.../application/pipeline/`(旧位置)
- `business-core-application/.../application/listener/`(旧位置)
- `business-core-application/.../application/errorcode/`(旧位置)
- `business-core-infrastructure/.../infrastructure/evaluation/`
- `business-core-infrastructure/.../infrastructure/configuration/`
- `business-core-infrastructure/.../infrastructure/gateway/`(旧位置)
- `business-core-infrastructure/.../infrastructure/json/`
- `business-core-infrastructure/.../infrastructure/event/`(旧位置)

- [ ] **Step 5: 验证 FlowOrchestrationService 重命名**

确认:
- `BusinessOrchestrationAppService` 类名不再存在于任何文件中
- `FlowOrchestrationService` 类存在于 `application/engine/service/` 目录
- 所有引用已更新为 `FlowOrchestrationService`

- [ ] **Step 6: 最终提交(如有修复)**

如果 Step 1-5 发现问题并修复:

```bash
git add -A
git commit -m "fix(kernel): resolve remaining import issues from repackaging"
```

如果无问题,跳过此步骤。

- [ ] **Step 7: 更新进度账本**

更新 `.superpowers/sdd/progress.md`,记录 kernel 重新分包完成。

---

## 风险与注意事项

1. **通配符 import**: `BusinessApplication.java` 等文件可能使用 `import com.example.core.domain.aggregate.valueobject.*` 通配符。移动后需拆分为 business 和 engine 两个显式 import 列表,否则编译会失败。

2. **Spring ComponentScan**: `DomainServiceConfiguration.java` 中的 `@ComponentScan` basePackages 可能引用旧包路径,需同步更新。

3. **MyBatis-Flex 注解**: kernel domain 层不使用 `@Table`/`@Column` 注解(符合 DDD 约束),但 annuity-infrastructure 中的 DO 实体引用 kernel 类作为转换目标,需确认 import 更新完整。

4. **JSON 配置文件**: `annuity-infrastructure/src/main/resources/config/` 下的 `step-routes.json` 等配置文件可能引用 Bean 名称(如 `"handler": "planMaterialPreparationHandler"`),Bean 名称不变不受影响,但需验证。

5. **测试配置类**: `KernelDomainServiceTestConfiguration.java` 引用多个 kernel SPI 和 Registry,需确保 import 更新完整。

6. **PowerShell 兼容**: `rmdir ... 2>nul` 是 PowerShell 语法,如使用 cmd 需改为 `rmdir ... 2>nul` 或 `rd /q ...`。
