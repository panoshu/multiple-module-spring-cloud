# Annuity Service 演示服务 + Kernel 架构升级 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新建 annuity-service 演示服务，全方位演示如何基于 business-core-kernel 实现业务功能，同时升级 kernel 集成 file-service 和 approval-service 的通用步骤处理器，并补齐 approval-service 的集成事件缺口。

**Architecture:**
- Phase 1: 补齐 approval-service 集成事件（8 个领域事件 → 4 个关键集成事件 DTO + Converter + 领域事件业务字段补齐）
- Phase 2: business-core-kernel 架构升级（新增 ApprovalIntegrationGateway SPI + 4 个通用 Handler + 2 个 Gateway 实现 + Retrofit 客户端）
- Phase 3: kernel 事件监听器（FileParsedEvent + ApprovalResultEvent，演示用 Spring ApplicationEvent 模拟）
- Phase 4: annuity-service 演示服务（7 层骨架 + BusinessExtension + BusinessFactExtractor + Repository 实现）
- Phase 5: 配置 JSON + schema + 端到端集成测试

**Tech Stack:** Java 25, Spring Boot 3.5.14, MyBatis-Flex 1.11.5, MapStruct 1.6.3, Retrofit 3.3.0, H2（测试）, PostgreSQL（生产）

## Global Constraints

- 遵循 DDD 七层架构：types → domain → api → application → adapter → infrastructure → starter
- domain 层禁止依赖 application/infrastructure 层，禁止依赖外部 API 模块
- 所有 API 接口使用 `@HttpExchange`，方法使用 `@GetExchange`/`@PostExchange`
- 所有 DTO 转换通过 MapStruct Converter
- Repository save 方法必须发布领域事件
- 集成事件 DTO 定义在 api 层，Converter 实现在 infrastructure 层
- 测试使用 H2 内存数据库，不依赖外部 MySQL/PostgreSQL
- 演示用 Spring ApplicationEvent 模拟 RocketMQ 跨服务事件传递
- 单个方法 ≤50 行，单个类 ≤500 行，接口方法 ≤7 个
- 禁止魔法数字，禁止 `Util/Helper/Manager` 后缀

## 关键设计决策

### 事件传递机制（演示 vs 生产）

**问题**：`SpringEventDispatcher` 只发布领域事件，不发布集成事件 DTO。演示环境无法依赖 RocketMQ。

**解决方案**：
- **演示环境**：新增 `IntegrationEventSimulator` 组件，直接通过 `ApplicationEventPublisher` 发布集成事件 DTO（如 `FileParsedEventDTO`、`ApprovalInstanceApprovedEventDTO`）。监听器用 `@EventListener` 监听 DTO 类型。
- **生产环境**：RocketMQ 消费者接收同样的 DTO 类型，调用同样的处理方法。
- **监听器设计**：核心处理逻辑抽取到 `handleXxx(DTO)` 方法，`@EventListener` 和 RocketMQ `@RocketMQMessageListener` 都调用该方法。

### 领域事件业务字段补齐

**问题**：`ApprovalInstanceApproved/Rejected/Withdrawn` 只含 `instanceId`，消费方无法知道是哪个业务单。

**解决方案**：在 3 个领域事件中增加 `businessNo`、`businessType` 字段，从 `ApprovalInstance` 聚合根获取。同步更新 `of()` 工厂方法签名。

---

## Phase 1: 补齐 approval-service 集成事件

### Task 1.1: 补齐 approval-domain 领域事件业务字段

**Files:**
- Modify: `approval-service/approval-domain/src/main/java/com/example/approval/domain/event/ApprovalInstanceCreated.java`
- Modify: `approval-service/approval-domain/src/main/java/com/example/approval/domain/event/ApprovalInstanceApproved.java`
- Modify: `approval-service/approval-domain/src/main/java/com/example/approval/domain/event/ApprovalInstanceRejected.java`
- Modify: `approval-service/approval-domain/src/main/java/com/example/approval/domain/event/ApprovalInstanceWithdrawn.java`
- Modify: `approval-service/approval-domain/src/main/java/com/example/approval/domain/aggregate/root/ApprovalInstance.java`（更新事件派发处）
- Test: `approval-service/approval-domain/src/test/java/com/example/approval/domain/event/ApprovalInstanceEventTest.java`

**Interfaces:**
- Produces: 4 个领域事件新增 `String businessNo`、`String businessType` 字段

**设计**：

```java
// ApprovalInstanceApproved.java 修改后
public record ApprovalInstanceApproved(
        EventId eventId,
        LocalDateTime occurredOn,
        ApprovalInstanceId instanceId,
        String businessNo,        // 新增
        String businessType       // 新增
) implements DomainEvent {
    public static ApprovalInstanceApproved of(ApprovalInstanceId instanceId,
                                               String businessNo, String businessType) {
        return new ApprovalInstanceApproved(EventId.generate(), LocalDateTime.now(),
                instanceId, businessNo, businessType);
    }
}
```

同理修改 `ApprovalInstanceCreated`、`ApprovalInstanceRejected`、`ApprovalInstanceWithdrawn`。

`ApprovalInstance` 聚合根中派发事件处更新调用签名，传入 `businessNo()`、`businessType()`。

### Task 1.2: 新增 approval-api 集成事件 DTO

**Files:**
- Create: `approval-service/approval-api/src/main/java/com/example/approval/api/event/IntegrationEventTypes.java`
- Create: `approval-service/approval-api/src/main/java/com/example/approval/api/event/ApprovalInstanceCreatedEventDTO.java`
- Create: `approval-service/approval-api/src/main/java/com/example/approval/api/event/ApprovalInstanceApprovedEventDTO.java`
- Create: `approval-service/approval-api/src/main/java/com/example/approval/api/event/ApprovalInstanceRejectedEventDTO.java`
- Create: `approval-service/approval-api/src/main/java/com/example/approval/api/event/ApprovalInstanceWithdrawnEventDTO.java`

**设计**：

```java
// IntegrationEventTypes.java
package com.example.approval.api.event;
public final class IntegrationEventTypes {
    public static final String APPROVAL_INSTANCE_CREATED = "ApprovalInstanceCreatedEvent";
    public static final String APPROVAL_INSTANCE_APPROVED = "ApprovalInstanceApprovedEvent";
    public static final String APPROVAL_INSTANCE_REJECTED = "ApprovalInstanceRejectedEvent";
    public static final String APPROVAL_INSTANCE_WITHDRAWN = "ApprovalInstanceWithdrawnEvent";
    private IntegrationEventTypes() {}
}

// ApprovalInstanceApprovedEventDTO.java
package com.example.approval.api.event;
import java.time.LocalDateTime;
public record ApprovalInstanceApprovedEventDTO(
    String eventId,
    String instanceId,
    String businessNo,
    String businessType,
    LocalDateTime occurredOn
) {}

// 其余 3 个 DTO 类似
```

### Task 1.3: 新增 approval-infrastructure 集成事件 Converter

**Files:**
- Modify: `approval-service/approval-infrastructure/pom.xml`（新增 shared-event-starter 依赖）
- Create: `approval-service/approval-infrastructure/src/main/java/com/example/approval/infrastructure/event/ApprovalInstanceCreatedEventConverter.java`
- Create: `approval-service/approval-infrastructure/src/main/java/com/example/approval/infrastructure/event/ApprovalInstanceApprovedEventConverter.java`
- Create: `approval-service/approval-infrastructure/src/main/java/com/example/approval/infrastructure/event/ApprovalInstanceRejectedEventConverter.java`
- Create: `approval-service/approval-infrastructure/src/main/java/com/example/approval/infrastructure/event/ApprovalInstanceWithdrawnEventConverter.java`

**设计**：

```java
package com.example.approval.infrastructure.event;

import com.example.approval.api.event.ApprovalInstanceApprovedEventDTO;
import com.example.approval.api.event.IntegrationEventTypes;
import com.example.approval.domain.event.ApprovalInstanceApproved;
import com.example.shared.domain.event.IntegrationEventConverter;
import org.springframework.stereotype.Component;

@Component
public class ApprovalInstanceApprovedEventConverter
        implements IntegrationEventConverter<ApprovalInstanceApproved> {

    @Override
    public Class<ApprovalInstanceApproved> supportedEventType() {
        return ApprovalInstanceApproved.class;
    }

    @Override
    public Object toIntegrationEvent(ApprovalInstanceApproved event) {
        return new ApprovalInstanceApprovedEventDTO(
            event.eventId().toString(),
            event.instanceId().toString(),
            event.businessNo(),
            event.businessType(),
            event.occurredOn()
        );
    }

    @Override
    public String integrationEventType() {
        return IntegrationEventTypes.APPROVAL_INSTANCE_APPROVED;
    }
}
```

其余 3 个 Converter 类似。

### Task 1.4: approval-service 集成测试验证

**Files:**
- Test: `approval-service/approval-infrastructure/src/test/java/com/example/approval/infrastructure/event/ApprovalEventConverterTest.java`

验证 4 个 Converter 正确转换领域事件为集成事件 DTO。

---

## Phase 2: business-core-kernel 架构升级

### Task 2.1: 新增 ApprovalIntegrationGateway SPI

**Files:**
- Create: `business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/gateway/ApprovalIntegrationGateway.java`

**设计**：

```java
package com.example.core.domain.gateway;

import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.aggregate.valueobject.BusinessContext;

/**
 * 审批集成网关 SPI
 * 业务模块在 infrastructure 层实现，调用 approval-service 发起审批
 */
public interface ApprovalIntegrationGateway {

    /**
     * 匹配审批流并启动审批实例
     * @return 审批实例ID（用于后续状态查询）
     */
    String startApproval(BusinessApplication application);

    /**
     * 查询审批实例状态
     * @return 审批状态：PENDING / APPROVED / REJECTED / WITHDRAWN
     */
    String queryApprovalStatus(String instanceId);
}
```

### Task 2.2: kernel pom 依赖调整

**Files:**
- Modify: `business-core-kernel/business-core-application/pom.xml`（新增 file-api、approval-api 依赖）
- Modify: `business-core-kernel/business-core-infrastructure/pom.xml`（新增 file-api、approval-api、shared-client-starter 已有）
- Modify: `business-core-kernel/pom.xml`（dependencyManagement 新增 file-api、approval-api、approval-service-api 引用）

**business-core-application/pom.xml 新增**：

```xml
<dependency>
  <groupId>com.example</groupId>
  <artifactId>file-api</artifactId>
</dependency>
<dependency>
  <groupId>com.example</groupId>
  <artifactId>approval-api</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework</groupId>
  <artifactId>spring-web</artifactId>
</dependency>
```

### Task 2.3: 新增通用步骤处理器 - FileServiceParseHandler

**Files:**
- Create: `business-core-kernel/business-core-application/src/main/java/com/example/core/application/handler/FileServiceParseHandler.java`

**设计**：

```java
package com.example.core.application.handler;

import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.enums.workflow.ApplicationFlowStep;
import com.example.core.domain.spi.StepActionHandler;
import com.example.core.domain.enums.status.StepExecutionStatus;
import com.example.file.api.FileTaskApi;
import com.example.file.api.request.UploadFileRequest;
import com.example.file.api.response.FileTaskIdResponse;
import com.example.shared.api.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 通用步骤处理器：调用 file-service 触发表单异步解析
 * 用于 FORM_DETAIL_INGESTION 步骤
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileServiceParseHandler implements StepActionHandler {

    private final FileTaskApi fileTaskApi;

    @Override
    public String handlerName() {
        return "fileServiceParseHandler";
    }

    @Override
    public StepExecutionStatus execute(BusinessApplication app, BusinessMetaContext context) {
        var fileId = app.requireFileForParsing();
        var request = new UploadFileRequest(
            "FORM_DETAIL",
            resolveTemplateCode(context),
            app.businessContext().planName(),
            0L,
            app.operatorInfo().operatorId().value(),
            app.id().toString()
        );
        ApiResult<FileTaskIdResponse> result = fileTaskApi.upload(request);
        if (result == null || !result.isSuccess()) {
            log.error("触发文件解析失败, applicationId: {}", app.id());
            return StepExecutionStatus.FAILED;
        }
        return StepExecutionStatus.SUSPEND_ASYNC_WAIT;
    }

    private String resolveTemplateCode(BusinessMetaContext context) {
        return context.businessType().name() + "_TEMPLATE";
    }
}
```

### Task 2.4: 新增通用步骤处理器 - ApprovalSubmissionHandler

**Files:**
- Create: `business-core-kernel/business-core-application/src/main/java/com/example/core/application/handler/ApprovalSubmissionHandler.java`

**设计**：

```java
package com.example.core.application.handler;

import com.example.approval.api.ApprovalFlowApi;
import com.example.approval.api.ApprovalInstanceApi;
import com.example.approval.api.request.MatchApprovalFlowRequest;
import com.example.approval.api.request.StartApprovalRequest;
import com.example.approval.api.response.ApprovalInstanceIdResponse;
import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.enums.status.StepExecutionStatus;
import com.example.core.domain.spi.StepActionHandler;
import com.example.shared.api.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 通用步骤处理器：调用 approval-service 发起审批
 * 用于 APPROVAL 步骤
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalSubmissionHandler implements StepActionHandler {

    private final ApprovalFlowApi approvalFlowApi;
    private final ApprovalInstanceApi approvalInstanceApi;

    @Override
    public String handlerName() {
        return "approvalSubmissionHandler";
    }

    @Override
    public StepExecutionStatus execute(BusinessApplication app, BusinessMetaContext context) {
        var matchReq = new MatchApprovalFlowRequest(
            app.businessContext().businessType().name(),
            app.businessContext().accountManager().value(),
            null
        );
        var matchResult = approvalFlowApi.match(matchReq);
        if (matchResult == null || !matchResult.isSuccess() || matchResult.getData() == null) {
            log.error("匹配审批流失败, applicationId: {}", app.id());
            return StepExecutionStatus.FAILED;
        }
        var flowId = matchResult.getData().flowId();
        var startReq = new StartApprovalRequest(
            flowId,
            app.id().toString(),
            app.businessContext().businessType().name(),
            app.operatorInfo().operatorId().value()
        );
        ApiResult<ApprovalInstanceIdResponse> startResult = approvalInstanceApi.start(startReq);
        if (startResult == null || !startResult.isSuccess()) {
            log.error("启动审批实例失败, applicationId: {}", app.id());
            return StepExecutionStatus.FAILED;
        }
        return StepExecutionStatus.SUSPEND_ASYNC_WAIT;
    }
}
```

### Task 2.5: 新增 FileServiceIntegrationGateway 默认实现

**Files:**
- Create: `business-core-kernel/business-core-infrastructure/src/main/java/com/example/core/infrastructure/gateway/FileServiceIntegrationGateway.java`

**设计**：

```java
package com.example.core.infrastructure.gateway;

import com.example.core.domain.gateway.FileIntegrationGateway;
import com.example.file.api.FileAccessApi;
import com.example.file.api.FileTaskApi;
import com.example.file.api.request.ApplyUploadTokenRequest;
import com.example.file.api.request.UploadFileRequest;
import com.example.file.api.response.ApplyUploadTokenResponse;
import com.example.file.api.response.FileTaskIdResponse;
import com.example.shared.api.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * FileIntegrationGateway 默认实现：调用 file-service
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileServiceIntegrationGateway implements FileIntegrationGateway {

    private final FileAccessApi fileAccessApi;
    private final FileTaskApi fileTaskApi;

    @Override
    public void triggerAsyncParsing(com.example.core.domain.aggregate.root.BusinessForm form,
                                     com.example.core.domain.aggregate.valueobject.BusinessMetaContext ctx) {
        // 委托给 FileServiceParseHandler 处理
    }

    @Override
    public void triggerAsyncParsing(com.example.core.domain.aggregate.valueobject.reference.FormId formId,
                                     com.example.shared.primitives.identity.FileId sourceFileId,
                                     String parseTemplateId,
                                     java.util.Map<String, Object> splitRules) {
        var request = new UploadFileRequest(
            "FORM_DETAIL", parseTemplateId, "form.xlsx", 0L, "system", formId.toString()
        );
        fileTaskApi.upload(request);
    }

    @Override
    public InputStream downloadStream(com.example.shared.primitives.identity.FileId fileId) {
        throw new UnsupportedOperationException("演示环境不支持文件流下载");
    }

    @Override
    public String applyUploadToken(String clientIp, String userId, long maxSize) {
        var request = new ApplyUploadTokenRequest(
            "MATERIAL", "business-core", null, null, null,
            new com.example.shared.primitives.identity.UserNo(userId),
            LocalDateTime.now().plusDays(7),
            List.of("application/pdf", "image/*"), maxSize, Duration.ofMinutes(30)
        );
        ApplyUploadTokenResponse resp = fileAccessApi.applyUploadToken(request);
        return resp != null ? resp.token() : null;
    }
}
```

### Task 2.6: 新增 ApprovalServiceIntegrationGateway 默认实现

**Files:**
- Create: `business-core-kernel/business-core-infrastructure/src/main/java/com/example/core/infrastructure/gateway/ApprovalServiceIntegrationGateway.java`

**设计**：

```java
package com.example.core.infrastructure.gateway;

import com.example.approval.api.ApprovalFlowApi;
import com.example.approval.api.ApprovalInstanceApi;
import com.example.approval.api.request.GetApprovalInstanceRequest;
import com.example.approval.api.request.MatchApprovalFlowRequest;
import com.example.approval.api.request.StartApprovalRequest;
import com.example.approval.api.response.ApprovalInstanceIdResponse;
import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.gateway.ApprovalIntegrationGateway;
import com.example.shared.api.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalServiceIntegrationGateway implements ApprovalIntegrationGateway {

    private final ApprovalFlowApi approvalFlowApi;
    private final ApprovalInstanceApi approvalInstanceApi;

    @Override
    public String startApproval(BusinessApplication application) {
        var matchReq = new MatchApprovalFlowRequest(
            application.businessContext().businessType().name(),
            application.businessContext().accountManager().value(),
            null
        );
        var matchResult = approvalFlowApi.match(matchReq);
        if (matchResult == null || !matchResult.isSuccess() || matchResult.getData() == null) {
            throw new IllegalStateException("匹配审批流失败");
        }
        var flowId = matchResult.getData().flowId();
        var startReq = new StartApprovalRequest(
            flowId, application.id().toString(),
            application.businessContext().businessType().name(),
            application.operatorInfo().operatorId().value()
        );
        ApiResult<ApprovalInstanceIdResponse> result = approvalInstanceApi.start(startReq);
        if (result == null || !result.isSuccess() || result.getData() == null) {
            throw new IllegalStateException("启动审批实例失败");
        }
        return result.getData().instanceId().toString();
    }

    @Override
    public String queryApprovalStatus(String instanceId) {
        var req = new GetApprovalInstanceRequest(
            new com.example.approval.types.ApprovalInstanceId(instanceId)
        );
        var result = approvalInstanceApi.get(req);
        if (result == null || !result.isSuccess() || result.getData() == null) {
            return "UNKNOWN";
        }
        return result.getData().status();
    }
}
```

### Task 2.7: kernel 单元测试

**Files:**
- Test: `business-core-kernel/business-core-application/src/test/java/com/example/core/application/handler/FileServiceParseHandlerTest.java`
- Test: `business-core-kernel/business-core-application/src/test/java/com/example/core/application/handler/ApprovalSubmissionHandlerTest.java`

使用 Mockito mock FileTaskApi、ApprovalFlowApi、ApprovalInstanceApi，验证 Handler 逻辑。

---

## Phase 3: kernel 事件监听器

### Task 3.1: 新增 IntegrationEventSimulator（演示用事件模拟器）

**Files:**
- Create: `business-core-kernel/business-core-infrastructure/src/main/java/com/example/core/infrastructure/event/IntegrationEventSimulator.java`

**设计**：

```java
package com.example.core.infrastructure.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 集成事件模拟器（演示环境用）
 * 生产环境由 RocketMQ 消费者替代，直接调用监听器的处理方法
 */
@Component
@RequiredArgsConstructor
public class IntegrationEventSimulator {

    private final ApplicationEventPublisher publisher;

    public void publish(Object integrationEventDTO) {
        publisher.publishEvent(integrationEventDTO);
    }
}
```

### Task 3.2: 新增 FileParsedEventListener

**Files:**
- Create: `business-core-kernel/business-core-application/src/main/java/com/example/core/application/listener/FileParsedEventListener.java`

**设计**：

```java
package com.example.core.application.listener;

import com.example.core.application.service.BusinessOrchestrationAppService;
import com.example.file.api.event.FileParsedEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 文件解析完成事件监听器
 * 演示环境：通过 Spring ApplicationEvent 触发
 * 生产环境：通过 RocketMQ @RocketMQMessageListener 触发，调用同样的 handleFileParsed 方法
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileParsedEventListener {

    private final BusinessOrchestrationAppService orchestrationService;

    @EventListener
    public void onFileParsed(FileParsedEventDTO event) {
        handleFileParsed(event);
    }

    /**
     * 核心处理逻辑（Spring 和 RocketMQ 共用）
     */
    public void handleFileParsed(FileParsedEventDTO event) {
        log.info("收到文件解析完成事件, fileTaskId: {}, status: {}",
                 event.fileTaskId(), event.status());
        if ("SUCCESS".equals(event.status()) || "PARTIAL".equals(event.status())) {
            orchestrationService.advanceByFileTaskId(event.fileTaskId());
        } else {
            log.error("文件解析失败, fileTaskId: {}, reason: {}",
                      event.fileTaskId(), event.failureReason());
        }
    }
}
```

### Task 3.3: 新增 ApprovalResultEventListener

**Files:**
- Create: `business-core-kernel/business-core-application/src/main/java/com/example/core/application/listener/ApprovalResultEventListener.java`

**设计**：

```java
package com.example.core.application.listener;

import com.example.approval.api.event.ApprovalInstanceApprovedEventDTO;
import com.example.approval.api.event.ApprovalInstanceRejectedEventDTO;
import com.example.approval.api.event.ApprovalInstanceWithdrawnEventDTO;
import com.example.core.application.service.BusinessOrchestrationAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalResultEventListener {

    private final BusinessOrchestrationAppService orchestrationService;

    @EventListener
    public void onApproved(ApprovalInstanceApprovedEventDTO event) {
        handleApproved(event);
    }

    @EventListener
    public void onRejected(ApprovalInstanceRejectedEventDTO event) {
        handleRejected(event);
    }

    @EventListener
    public void onWithdrawn(ApprovalInstanceWithdrawnEventDTO event) {
        handleWithdrawn(event);
    }

    public void handleApproved(ApprovalInstanceApprovedEventDTO event) {
        log.info("审批通过, instanceId: {}, businessNo: {}", event.instanceId(), event.businessNo());
        orchestrationService.advanceByApprovalResult(event.businessNo(), "APPROVED");
    }

    public void handleRejected(ApprovalInstanceRejectedEventDTO event) {
        log.info("审批驳回, instanceId: {}, businessNo: {}", event.instanceId(), event.businessNo());
        orchestrationService.advanceByApprovalResult(event.businessNo(), "REJECTED");
    }

    public void handleWithdrawn(ApprovalInstanceWithdrawnEventDTO event) {
        log.info("审批撤回, instanceId: {}, businessNo: {}", event.instanceId(), event.businessNo());
        orchestrationService.advanceByApprovalResult(event.businessNo(), "WITHDRAWN");
    }
}
```

### Task 3.4: BusinessOrchestrationAppService 扩展方法

**Files:**
- Modify: `business-core-kernel/business-core-application/src/main/java/com/example/core/application/service/BusinessOrchestrationAppService.java`

新增方法：
- `advanceByFileTaskId(String fileTaskId)` — 根据 fileTaskId 反查业务申请单并推进
- `advanceByApprovalResult(String businessNo, String result)` — 根据审批结果推进或终止业务申请单

### Task 3.5: kernel 事件监听器单元测试

**Files:**
- Test: `business-core-kernel/business-core-application/src/test/java/com/example/core/application/listener/FileParsedEventListenerTest.java`
- Test: `business-core-kernel/business-core-application/src/test/java/com/example/core/application/listener/ApprovalResultEventListenerTest.java`

---

## Phase 4: annuity-service 演示服务

### Task 4.1: 创建 annuity-service 骨架（父 pom + 7 个子模块）

**Files:**
- Modify: `pom.xml`（根 pom 新增 annuity-service 模块）
- Modify: `pom.xml`（根 pom dependencyManagement 新增 annuity-api）
- Create: `annuity-service/pom.xml`（父 pom）
- Create: `annuity-service/annuity-types/pom.xml`
- Create: `annuity-service/annuity-domain/pom.xml`
- Create: `annuity-service/annuity-api/pom.xml`
- Create: `annuity-service/annuity-application/pom.xml`
- Create: `annuity-service/annuity-adapter/pom.xml`
- Create: `annuity-service/annuity-infrastructure/pom.xml`
- Create: `annuity-service/annuity-starter/pom.xml`

### Task 4.2: annuity-domain - BusinessExtension 实现

**Files:**
- Create: `annuity-service/annuity-domain/src/main/java/com/example/annuity/domain/extension/AnnuityApplicationExtension.java`
- Create: `annuity-service/annuity-domain/src/main/java/com/example/annuity/domain/errorcode/AnnuityDomainErrorCode.java`

**设计**：

```java
package com.example.annuity.domain.extension;

import com.example.core.domain.aggregate.valueobject.BusinessExtension;
import com.example.core.domain.aggregate.valueobject.business.BusinessType;

/**
 * 年金业务申请扩展字段
 */
public record AnnuityApplicationExtension(
    BusinessType businessType,
    String planType,              // 计划类型：NEW / MODIFY / DELETE
    Long initialContribution,    // 初始缴费金额（分）
    boolean hasForeignInvestment  // 是否含外资
) implements BusinessExtension {

    @Override
    public BusinessType businessType() {
        return businessType;
    }
}
```

### Task 4.3: annuity-domain - BusinessFactExtractor 实现

**Files:**
- Create: `annuity-service/annuity-domain/src/main/java/com/example/annuity/domain/extractor/AnnuityFactExtractor.java`

**设计**：

```java
package com.example.annuity.domain.extractor;

import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.spi.BusinessFactExtractor;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class AnnuityFactExtractor implements BusinessFactExtractor {

    @Override
    public String extractorName() {
        return "annuityFactExtractor";
    }

    @Override
    public Map<String, Object> extractBusinessFacts(BusinessApplication app) {
        if (!(app.businessExtension() instanceof AnnuityApplicationExtension ext)) {
            return Map.of();
        }
        return Map.of(
            "hasForeignInvestment", ext.hasForeignInvestment(),
            "initialContribution", ext.initialContribution(),
            "planType", ext.planType()
        );
    }
}
```

### Task 4.4: annuity-api - REST API 接口

**Files:**
- Create: `annuity-service/annuity-api/src/main/java/com/example/annuity/api/AnnuityApi.java`
- Create: `annuity-service/annuity-api/src/main/java/com/example/annuity/api/dto/UploadFormRequest.java`
- Create: `annuity-service/annuity-api/src/main/java/com/example/annuity/api/dto/ApplicationResponse.java`
- Create: `annuity-service/annuity-api/src/main/java/com/example/annuity/api/dto/BatchStatusResponse.java`
- Create: `annuity-service/annuity-api/src/main/java/com/example/annuity/api/command/UploadFormCommand.java`

**设计**：

```java
package com.example.annuity.api;

import com.example.annuity.api.dto.ApplicationResponse;
import com.example.annuity.api.dto.BatchStatusResponse;
import com.example.annuity.api.dto.UploadFormRequest;
import com.example.shared.api.ApiResult;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;

@org.springframework.web.service.annotation.HttpExchange("/api/annuity")
public interface AnnuityApi {

    @org.springframework.web.service.annotation.PostExchange("/upload")
    ApiResult<BatchStatusResponse> uploadForm(@Valid @RequestBody UploadFormRequest request);

    @org.springframework.web.service.annotation.PostExchange("/applications/get")
    ApiResult<ApplicationResponse> getApplication(@RequestBody String applicationId);

    @org.springframework.web.service.annotation.PostExchange("/batches/get")
    ApiResult<BatchStatusResponse> getBatchStatus(@RequestBody String batchId);
}
```

### Task 4.5: annuity-application - 应用服务

**Files:**
- Create: `annuity-service/annuity-application/src/main/java/com/example/annuity/application/service/AnnuityAppService.java`
- Create: `annuity-service/annuity-application/src/main/java/com/example/annuity/application/command/UploadFormCommand.java`

**设计**：

```java
package com.example.annuity.application.service;

import com.example.annuity.application.command.UploadFormCommand;
import com.example.annuity.api.dto.BatchStatusResponse;
import com.example.core.application.service.BusinessFormAppService;
import com.example.core.application.service.BusinessOrchestrationAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnnuityAppService {

    private final BusinessFormAppService formAppService;
    private final BusinessOrchestrationAppService orchestrationService;

    @Transactional
    public BatchStatusResponse uploadForm(UploadFormCommand command) {
        var batchId = formAppService.createBatchAndForm(
            command.businessContext(), command.operatorInfo(),
            command.fileName(), command.fileSize()
        );
        orchestrationService.startBatchProcessing(batchId);
        return new BatchStatusResponse(batchId.toString(), "PROCESSING");
    }
}
```

### Task 4.6: annuity-adapter - Controller + Converter

**Files:**
- Create: `annuity-service/annuity-adapter/src/main/java/com/example/annuity/adapter/controller/AnnuityController.java`
- Create: `annuity-service/annuity-adapter/src/main/java/com/example/annuity/adapter/converter/AnnuityApiConverter.java`

### Task 4.7: annuity-infrastructure - Repository 实现

**Files:**
- Create: `annuity-service/annuity-infrastructure/src/main/java/com/example/annuity/infrastructure/repository/ApplicationRepositoryImpl.java`
- Create: `annuity-service/annuity-infrastructure/src/main/java/com/example/annuity/infrastructure/repository/BatchRepositoryImpl.java`
- Create: `annuity-service/annuity-infrastructure/src/main/java/com/example/annuity/infrastructure/repository/FormRepositoryImpl.java`

**设计**（以 ApplicationRepositoryImpl 为例）：

```java
package com.example.annuity.infrastructure.repository;

import com.example.annuity.infrastructure.entity.ApplicationDO;
import com.example.annuity.infrastructure.mapper.ApplicationMapper;
import com.example.annuity.infrastructure.converter.ApplicationDataConverter;
import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.repository.ApplicationRepository;
import com.example.shared.domain.event.EventBus;
import com.example.shared.primitives.identity.ApplicationId;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public class ApplicationRepositoryImpl implements ApplicationRepository {

    private final ApplicationMapper mapper;
    private final ApplicationDataConverter converter;
    private final EventBus eventBus;

    // 构造函数注入

    @Override
    public Optional<BusinessApplication> load(ApplicationId id) {
        ApplicationDO dbDo = mapper.findById(id.value());
        return dbDo != null ? Optional.of(converter.toEntity(dbDo)) : Optional.empty();
    }

    @Override
    public void save(BusinessApplication app) {
        ApplicationDO dbDo = converter.toDO(app);
        if (mapper.existsById(app.id().value())) {
            mapper.update(dbDo);
        } else {
            mapper.insert(dbDo);
        }
        app.getDomainEvents().forEach(eventBus::publish);
        app.clearDomainEvents();
    }
    // ... 其余方法
}
```

### Task 4.8: annuity-infrastructure - DO + Mapper + Converter

**Files:**
- Create: `annuity-service/annuity-infrastructure/src/main/java/com/example/annuity/infrastructure/entity/ApplicationDO.java`
- Create: `annuity-service/annuity-infrastructure/src/main/java/com/example/annuity/infrastructure/entity/BatchDO.java`
- Create: `annuity-service/annuity-infrastructure/src/main/java/com/example/annuity/infrastructure/entity/FormDO.java`
- Create: `annuity-service/annuity-infrastructure/src/main/java/com/example/annuity/infrastructure/mapper/ApplicationMapper.java`
- Create: `annuity-service/annuity-infrastructure/src/main/java/com/example/annuity/infrastructure/mapper/BatchMapper.java`
- Create: `annuity-service/annuity-infrastructure/src/main/java/com/example/annuity/infrastructure/mapper/FormMapper.java`
- Create: `annuity-service/annuity-infrastructure/src/main/java/com/example/annuity/infrastructure/converter/ApplicationDataConverter.java`
- Create: `annuity-service/annuity-infrastructure/src/main/java/com/example/annuity/infrastructure/converter/BatchDataConverter.java`
- Create: `annuity-service/annuity-infrastructure/src/main/java/com/example/annuity/infrastructure/converter/FormDataConverter.java`
- Create: `annuity-service/annuity-infrastructure/src/main/java/com/example/annuity/infrastructure/configuration/JacksonConfiguration.java`

### Task 4.9: annuity-infrastructure - schema DDL

**Files:**
- Create: `annuity-service/annuity-infrastructure/src/main/resources/schema-pg.sql`
- Create: `annuity-service/annuity-infrastructure/src/main/resources/schema-mysql.sql`

**schema-pg.sql 设计**：

```sql
CREATE TABLE IF NOT EXISTS t_annuity_application (
    id BIGINT PRIMARY KEY,
    batch_id BIGINT,
    form_id BIGINT,
    business_type VARCHAR(64) NOT NULL,
    customer_no VARCHAR(64),
    product_no VARCHAR(64),
    plan_no VARCHAR(64),
    operation_model VARCHAR(64),
    account_manager VARCHAR(64),
    operator_id VARCHAR(64),
    operator_name VARCHAR(128),
    channel VARCHAR(64),
    business_extension JSONB,
    parsed_json_file_id BIGINT,
    expected_detail_count INT DEFAULT 0,
    application_status VARCHAR(32) NOT NULL,
    current_step VARCHAR(64) NOT NULL,
    package_file_id BIGINT,
    acceptance_file_id BIGINT,
    created_by VARCHAR(64),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT FALSE,
    version INT DEFAULT 0
);
CREATE INDEX idx_annuity_application_batch ON t_annuity_application(batch_id);
CREATE INDEX idx_annuity_application_status ON t_annuity_application(application_status);

-- t_annuity_batch、t_annuity_form 类似
```

### Task 4.10: annuity-starter - 启动类 + 配置

**Files:**
- Create: `annuity-service/annuity-starter/src/main/java/com/example/annuity/AnnuityApplication.java`
- Create: `annuity-service/annuity-starter/src/main/resources/application.yml`
- Create: `annuity-service/annuity-starter/src/main/resources/application-local.yml`

**AnnuityApplication.java 设计**：

```java
package com.example.annuity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = {"com.example.annuity", "com.example.core"})
public class AnnuityApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnnuityApplication.class, args);
    }
}
```

---

## Phase 5: 配置 + 端到端集成测试

### Task 5.1: 配置 JSON 文件

**Files:**
- Create: `annuity-service/annuity-infrastructure/src/main/resources/config/step-routes.json`
- Create: `annuity-service/annuity-infrastructure/src/main/resources/config/material-rules.json`
- Create: `annuity-service/annuity-infrastructure/src/main/resources/config/extractor-config.json`

**step-routes.json 设计**：

```json
[
  {
    "currentStep": "FORM_DETAIL_INGESTION",
    "nextStep": "DATA_VERIFICATION",
    "taskType": "SYSTEM_TASK",
    "preValidations": [],
    "mainProcessor": "fileServiceParseHandler",
    "detailProcessors": [],
    "sideEffects": []
  },
  {
    "currentStep": "DATA_VERIFICATION",
    "nextStep": "MATERIAL_PREPARATION",
    "taskType": "SYSTEM_TASK",
    "preValidations": [],
    "mainProcessor": null,
    "detailProcessors": [],
    "sideEffects": []
  },
  {
    "currentStep": "MATERIAL_PREPARATION",
    "nextStep": "APPROVAL",
    "taskType": "SYSTEM_TASK",
    "mainProcessor": "planMaterialPreparationHandler",
    "detailProcessors": []
  },
  {
    "currentStep": "APPROVAL",
    "nextStep": "COMPLETED",
    "taskType": "USER_TASK",
    "mainProcessor": "approvalSubmissionHandler",
    "detailProcessors": []
  }
]
```

### Task 5.2: annuity-infrastructure - BusinessConfigGateway 实现

**Files:**
- Create: `annuity-service/annuity-infrastructure/src/main/java/com/example/annuity/infrastructure/gateway/JsonBusinessConfigGateway.java`

从 classpath:config/*.json 读取配置。

### Task 5.3: annuity-infrastructure - FileIntegrationGateway 演示实现

**Files:**
- Create: `annuity-service/annuity-infrastructure/src/main/java/com/example/annuity/infrastructure/gateway/MockFileIntegrationGateway.java`

**设计**：演示用 mock 实现，触发解析后直接通过 `IntegrationEventSimulator` 发布 `FileParsedEventDTO` 模拟回调。

```java
@Component
@RequiredArgsConstructor
public class MockFileIntegrationGateway implements FileIntegrationGateway {

    private final IntegrationEventSimulator eventSimulator;

    @Override
    public void triggerAsyncParsing(BusinessForm form, BusinessMetaContext ctx) {
        // 演示：立即模拟解析完成事件
        var event = new FileParsedEventDTO(
            UUID.randomUUID().toString(),
            form.id().toString(),
            "FORM_DETAIL",
            "SUCCESS",
            1,
            List.of(new FileParsedEventDTO.SubTaskSummaryDTO(
                "sub-1", "default", 10, 10, 0, "SUCCESS"
            )),
            null,
            LocalDateTime.now()
        );
        eventSimulator.publish(event);
    }
    // ... 其余方法返回默认值
}
```

### Task 5.4: 端到端集成测试

**Files:**
- Test: `annuity-service/annuity-starter/src/test/java/com/example/annuity/AnnuityEndToEndTest.java`

**测试场景**：
1. 上传表单 → 创建 Batch + Form
2. 触发 FORM_DETAIL_INGESTION → MockFileIntegrationGateway 模拟解析完成事件
3. FileParsedEventListener 接收事件 → 推进到 DATA_VERIFICATION
4. 自动推进到 MATERIAL_PREPARATION
5. 推进到 APPROVAL → ApprovalSubmissionHandler 调用（mock approval-api）
6. 模拟审批通过事件 → 推进到 COMPLETED
7. 验证 BusinessApplication 状态为 COMPLETED

使用 H2 内存数据库 + Spring Boot Test + Mockito mock 外部 API 客户端。

### Task 5.5: 全量构建验证

**Files:**
- 验证 `mvn clean compile -DskipTests` 全项目编译通过
- 验证 `mvn test` 全项目测试通过
- 验证 `mvn -pl annuity-service/annuity-starter -am package -DskipTests` 可打包

---

## 文件清单汇总

| Phase | 模块 | 文件数 | 说明 |
|-------|------|--------|------|
| Phase 1 | approval-domain + approval-api + approval-infrastructure | 12 | 领域事件业务字段 + 4 个集成事件 DTO + 4 个 Converter + 测试 |
| Phase 2 | business-core-domain + business-core-application + business-core-infrastructure | 10 | SPI + 4 个通用 Handler + 2 个 Gateway 实现 + pom 调整 + 测试 |
| Phase 3 | business-core-application + business-core-infrastructure | 5 | 事件模拟器 + 2 个监听器 + AppService 扩展 + 测试 |
| Phase 4 | annuity-service 全 7 层 | 20 | 骨架 + Extension + Extractor + API + AppService + Controller + Repository + DO + Mapper + Converter + schema |
| Phase 5 | annuity-infrastructure + annuity-starter | 8 | 配置 JSON + ConfigGateway + MockGateway + 端到端测试 |
| **合计** | | **55** | |

---

## 执行顺序建议

1. **Phase 1**（approval-service 集成事件）→ 独立，可先完成并验证
2. **Phase 2**（kernel 通用 Handler）→ 依赖 Phase 1 的 approval-api
3. **Phase 3**（kernel 事件监听器）→ 依赖 Phase 1 的集成事件 DTO + Phase 2 的 AppService 扩展
4. **Phase 4**（annuity-service 骨架）→ 依赖 Phase 2-3 的 kernel
5. **Phase 5**（配置 + 集成测试）→ 依赖 Phase 4 完成

每个 Phase 完成后提交一次 commit，确保可回滚。

---

## 验证清单

- [ ] Phase 1 完成：approval-service 4 个 Converter 单元测试通过
- [ ] Phase 2 完成：kernel 通用 Handler 单元测试通过
- [ ] Phase 3 完成：kernel 事件监听器单元测试通过
- [ ] Phase 4 完成：annuity-service 编译通过
- [ ] Phase 5 完成：端到端集成测试通过
- [ ] 全项目 `mvn clean compile` 通过
- [ ] 全项目 `mvn test` 通过
- [ ] annuity-service 可打包为可执行 jar
