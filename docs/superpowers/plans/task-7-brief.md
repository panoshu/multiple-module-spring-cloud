# Task 7: BusinessApplicationApi 接口与实现

**Files:**
- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/application/BusinessApplicationApi.java`
- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/application/command/AdvanceStepCommand.java`
- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/application/command/SubmitApplicationCommand.java`
- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/application/query/FindApplicationListQuery.java`
- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/application/query/GetApplicationDetailQuery.java`
- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/application/response/ApplicationSummaryResponse.java`
- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/application/response/ApplicationDetailResponse.java`
- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/application/response/AdvanceStepResponse.java`
- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/application/response/SubmitResponse.java`
- Modify: `business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/business/repository/ApplicationRepository.java` (新增 `findByBatchId` default 方法)
- Modify: `business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/business/aggregate/root/BusinessApplication.java` (新增 `getStatus`/`getPackageFile`/`getApplyTime`/`getCompleteTime` getter)
- Create: `business-core-kernel/business-core-application/src/main/java/com/example/core/application/business/service/BusinessApplicationAppService.java`
- Create: `business-core-kernel/business-core-application/src/test/java/com/example/core/application/business/service/BusinessApplicationAppServiceTest.java`
- Create: `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/application/BusinessApplicationController.java`
- Create: `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/application/converter/ApplicationConverter.java`

**Interfaces:**
- Consumes: `FlowOrchestrationService`(已存在), `ApplicationRepository`(已存在,需扩展)
- Produces: `BusinessApplicationApi` 接口及配套 DTO, `BusinessApplicationController` bean, `BusinessApplicationAppService` bean

## 关键设计决策(必读)

> 以下是对原 plan brief 的修正,实现时以本节为准:

1. **ID 类型**: `ApplicationId` 是 String-based record。所有 DTO 中的 `Long applicationId`/`Long batchId` 改为 `String`。

2. **ApplicationRepository 扩展**: 新增 `findByBatchId(BatchId)` default 方法(抛 UnsupportedOperationException),留给业务服务覆写。遵循已有的 `findByFileTaskId` 模式。

3. **不留 TODO**: plan brief 的 `submit` 方法有 TODO,本任务需完整实现:submit 直接复用 `flowOrchestrationService.advanceStep()`,审批判断由管道 preValidation 中的 handler 负责。

4. **ApplicationConverter**: 使用 `default` 方法(同 Task 5/6 模式),因 `BusinessApplication` 继承泛型基类,MapStruct `@Mapping` 无法解析继承的访问器。

5. **SubmitResponse**: 当前无审批实例 ID 反查能力,`approvalInstanceId` 设为 null,`needApproval` 设为 false,字段保留供后续扩展。

6. **ApplicationDetailResponse**: `forms`/`materials` 等复杂嵌套字段当前不可得(需联表查询),本次返回 null/空列表,字段保留供后续扩展。

7. **list 接口**: 按 batchId 查询申请单列表,若业务服务未覆写 `findByBatchId` 则抛 UnsupportedOperationException。

8. **advance 接口**: `AdvanceStepCommand` 含 `actionPayload: Map<String,Object>?` 字段,当前 AppService 暂不消费此字段(由管道 handler 自行从聚合根获取业务事实),保留供后续扩展。

## Step 1: 编写 BusinessApplicationApi 接口与 DTO

### BusinessApplicationApi.java
```java
package com.example.core.api.application;

import com.example.core.api.application.command.AdvanceStepCommand;
import com.example.core.api.application.command.SubmitApplicationCommand;
import com.example.core.api.application.query.FindApplicationListQuery;
import com.example.core.api.application.query.GetApplicationDetailQuery;
import com.example.core.api.application.response.AdvanceStepResponse;
import com.example.core.api.application.response.ApplicationDetailResponse;
import com.example.core.api.application.response.ApplicationSummaryResponse;
import com.example.core.api.application.response.SubmitResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * 业务申请单管理 API
 *
 * <p>提供申请单的列表查询、详情查询、推进、提交等公共接口。
 * 路径前缀 {@code /core/application}。
 *
 * <p>后续新增接口流程:
 * <ol>
 *   <li>在 API 层新增方法到本接口</li>
 *   <li>在 application 层扩展 AppService 方法</li>
 *   <li>在 adapter 层实现 Controller,通过 ApplicationConverter 完成 DTO 转换</li>
 * </ol>
 *
 * @author panoshu
 */
@HttpExchange("/core/application")
public interface BusinessApplicationApi {

    /**
     * 查询申请单列表(按批次 ID)。
     */
    @PostExchange("/list")
    ApiResult<List<ApplicationSummaryResponse>> list(@Valid @RequestBody FindApplicationListQuery query);

    /**
     * 查询申请单详情。
     */
    @PostExchange("/detail")
    ApiResult<ApplicationDetailResponse> detail(@Valid @RequestBody GetApplicationDetailQuery query);

    /**
     * 推进申请单到下一节点。
     */
    @PostExchange("/advance")
    ApiResult<AdvanceStepResponse> advance(@Valid @RequestBody AdvanceStepCommand command);

    /**
     * 提交申请单(触发审批判断)。
     */
    @PostExchange("/submit")
    ApiResult<SubmitResponse> submit(@Valid @RequestBody SubmitApplicationCommand command);
}
```

### AdvanceStepCommand.java
```java
package com.example.core.api.application.command;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * 推进申请单命令
 *
 * <p>{@code actionPayload} 为可选的业务参数,当前版本暂不消费,保留供后续扩展。
 *
 * @author panoshu
 */
public record AdvanceStepCommand(
    @NotBlank(message = "申请单ID不能为空") String applicationId,
    Map<String, Object> actionPayload
) {
}
```

### SubmitApplicationCommand.java
```java
package com.example.core.api.application.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 提交申请单命令
 *
 * @author panoshu
 */
public record SubmitApplicationCommand(
    @NotBlank(message = "申请单ID不能为空") String applicationId
) {
}
```

### FindApplicationListQuery.java
```java
package com.example.core.api.application.query;

import jakarta.validation.constraints.NotBlank;

/**
 * 查询申请单列表
 *
 * @author panoshu
 */
public record FindApplicationListQuery(
    @NotBlank(message = "批次ID不能为空") String batchId,
    String status
) {
}
```

### GetApplicationDetailQuery.java
```java
package com.example.core.api.application.query;

import jakarta.validation.constraints.NotBlank;

/**
 * 查询申请单详情
 *
 * @author panoshu
 */
public record GetApplicationDetailQuery(
    @NotBlank(message = "申请单ID不能为空") String applicationId
) {
}
```

### ApplicationSummaryResponse.java
```java
package com.example.core.api.application.response;

import java.time.LocalDateTime;

/**
 * 申请单摘要响应
 *
 * @author panoshu
 */
public record ApplicationSummaryResponse(
    String applicationId,
    String batchId,
    String status,
    String currentStep,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
```

### ApplicationDetailResponse.java
```java
package com.example.core.api.application.response;

import java.time.LocalDateTime;

/**
 * 申请单详情响应
 *
 * <p>{@code jsonFileId}/{@code packageFileId} 等嵌套字段当前仅返回 ID 字符串,
 * 完整的文件/材料明细需由前端另行调用文件/材料接口查询。
 *
 * @author panoshu
 */
public record ApplicationDetailResponse(
    String applicationId,
    String batchId,
    String status,
    String currentStep,
    String jsonFileId,
    String packageFileId,
    LocalDateTime applyTime,
    LocalDateTime completeTime,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
```

### AdvanceStepResponse.java
```java
package com.example.core.api.application.response;

/**
 * 推进申请单响应
 *
 * @author panoshu
 */
public record AdvanceStepResponse(
    String applicationId,
    String nextStep,
    String status
) {
}
```

### SubmitResponse.java
```java
package com.example.core.api.application.response;

/**
 * 提交申请单响应
 *
 * <p>{@code needApproval} 当前固定为 false,{@code approvalInstanceId} 设为 null,
 * 审批判断由管道 preValidation 中的 handler 完成,本响应字段保留供后续扩展。
 *
 * @author panoshu
 */
public record SubmitResponse(
    String applicationId,
    boolean needApproval,
    String approvalInstanceId
) {
}
```

## Step 2: 扩展 ApplicationRepository 与 BusinessApplication

### ApplicationRepository
文件: `business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/business/repository/ApplicationRepository.java`

在现有接口中新增以下方法(保留所有已有方法不变):

```java
  /**
   * 通过批次 ID 查询该批次下所有业务申请单。
   * <p>
   * 用于 {@code BusinessApplicationApi.list} 接口按批次聚合查询申请单列表。
   * <p>
   * <b>注意:</b>本方法为 {@code default} 实现,抛出 {@link UnsupportedOperationException},
   * 具体业务服务需按需覆写本方法以提供真实的查询能力。
   *
   * @param batchId 批次 ID
   * @return 该批次下的所有业务申请单列表;若未覆写则抛出异常
   */
  default java.util.List<BusinessApplication> findByBatchId(com.example.shared.primitives.identity.BatchId batchId) {
    throw new UnsupportedOperationException(
      "ApplicationRepository.findByBatchId 尚未实现,具体业务服务需覆写本方法");
  }
```

### BusinessApplication
文件: `business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/business/aggregate/root/BusinessApplication.java`

在现有类中新增以下 getter(保留所有已有方法不变):

```java
  public ApplicationStatus getStatus() {
    return this.status;
  }

  public BusinessFile getPackageFile() {
    return this.packageFile;
  }

  public LocalDateTime getApplyTime() {
    return this.applyTime;
  }

  public LocalDateTime getCompleteTime() {
    return this.completeTime;
  }
```

## Step 3: 编写 BusinessApplicationAppService

文件: `business-core-kernel/business-core-application/src/main/java/com/example/core/application/business/service/BusinessApplicationAppService.java`

```java
package com.example.core.application.business.service;

import com.example.core.application.engine.service.FlowOrchestrationService;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.repository.ApplicationRepository;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.BatchId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 业务申请单应用服务
 *
 * <p>编排申请单的列表查询、详情查询、推进、提交等业务流程,
 * 复用 {@link FlowOrchestrationService} 完成流程编排。
 *
 * <p>后续新增 AppService 方法流程:
 * <ol>
 *   <li>在 API 层接口新增方法签名</li>
 *   <li>在本类实现方法,管理事务边界</li>
 *   <li>通过 ApplicationConverter 完成 DTO 转换</li>
 * </ol>
 *
 * @author panoshu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessApplicationAppService {

    private final FlowOrchestrationService flowOrchestrationService;
    private final ApplicationRepository applicationRepository;

    /**
     * 查询批次下的申请单列表。
     *
     * @param batchId 批次 ID
     * @return 申请单列表
     */
    @Transactional(readOnly = true)
    public List<BusinessApplication> findByBatchId(BatchId batchId) {
        return applicationRepository.findByBatchId(batchId);
    }

    /**
     * 加载申请单或抛出异常。
     *
     * @param applicationId 申请单 ID
     * @return 申请单聚合根
     */
    @Transactional(readOnly = true)
    public BusinessApplication loadOrThrow(ApplicationId applicationId) {
        return applicationRepository.loadOrThrow(applicationId);
    }

    /**
     * 推进申请单到下一节点。
     *
     * <p>委托给 {@link FlowOrchestrationService#advanceStep(ApplicationId)},
     * 由管道 preValidation 中的 handler 完成业务数据校验,
     * 校验失败则抛出业务异常,事务回滚。
     *
     * @param applicationId 申请单 ID
     */
    @Transactional
    public void advanceStep(ApplicationId applicationId) {
        flowOrchestrationService.advanceStep(applicationId);
        log.info("推进申请单: applicationId={}", applicationId.value());
    }

    /**
     * 提交申请单。
     *
     * <p>当前实现直接复用 {@link FlowOrchestrationService#advanceStep(ApplicationId)},
     * 审批判断由管道 preValidation 中的 handler 完成(如配置了审批判断 handler)。
     * 若需要审批,handler 内部会触发审批流创建。
     *
     * @param applicationId 申请单 ID
     */
    @Transactional
    public void submit(ApplicationId applicationId) {
        flowOrchestrationService.advanceStep(applicationId);
        log.info("提交申请单: applicationId={}", applicationId.value());
    }
}
```

## Step 4: 编写 BusinessApplicationAppService 单元测试

文件: `business-core-kernel/business-core-application/src/test/java/com/example/core/application/business/service/BusinessApplicationAppServiceTest.java`

```java
package com.example.core.application.business.service;

import com.example.core.application.engine.service.FlowOrchestrationService;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.repository.ApplicationRepository;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.BatchId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BusinessApplicationAppService 单元测试
 *
 * @author panoshu
 */
class BusinessApplicationAppServiceTest {

    private FlowOrchestrationService flowOrchestrationService;
    private ApplicationRepository applicationRepository;
    private BusinessApplicationAppService appService;

    @BeforeEach
    void setUp() {
        flowOrchestrationService = mock(FlowOrchestrationService.class);
        applicationRepository = mock(ApplicationRepository.class);
        appService = new BusinessApplicationAppService(flowOrchestrationService, applicationRepository);
    }

    @Test
    void should_find_applications_by_batch_id() {
        BatchId batchId = new BatchId("BATCH001");
        BusinessApplication app = mock(BusinessApplication.class);
        when(applicationRepository.findByBatchId(batchId)).thenReturn(List.of(app));

        List<BusinessApplication> result = appService.findByBatchId(batchId);

        assertThat(result).hasSize(1);
        verify(applicationRepository).findByBatchId(batchId);
    }

    @Test
    void should_load_or_throw_application() {
        ApplicationId appId = new ApplicationId("APP001");
        BusinessApplication app = mock(BusinessApplication.class);
        when(applicationRepository.loadOrThrow(appId)).thenReturn(app);

        BusinessApplication result = appService.loadOrThrow(appId);

        assertThat(result).isSameAs(app);
        verify(applicationRepository).loadOrThrow(appId);
    }

    @Test
    void should_advance_step_via_orchestration() {
        ApplicationId appId = new ApplicationId("APP001");

        appService.advanceStep(appId);

        verify(flowOrchestrationService).advanceStep(appId);
    }

    @Test
    void should_submit_via_orchestration() {
        ApplicationId appId = new ApplicationId("APP001");

        appService.submit(appId);

        verify(flowOrchestrationService).advanceStep(appId);
    }
}
```

## Step 5: 编写 ApplicationConverter

文件: `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/application/converter/ApplicationConverter.java`

```java
package com.example.core.adapter.application.converter;

import com.example.core.api.application.response.AdvanceStepResponse;
import com.example.core.api.application.response.ApplicationDetailResponse;
import com.example.core.api.application.response.ApplicationSummaryResponse;
import com.example.core.api.application.response.SubmitResponse;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.shared.primitives.identity.ApplicationId;
import org.mapstruct.Mapper;

/**
 * 申请单 DTO 转换器
 *
 * <p>通过 MapStruct 完成聚合根到响应 DTO 的转换。
 * 使用 default 方法,因 {@link BusinessApplication} 继承泛型基类,
 * MapStruct @Mapping 无法解析继承的访问器(同 BatchConverter/FormConverter 模式)。
 *
 * <p>后续新增 DTO 转换流程:
 * <ol>
 *   <li>在 API 层定义 Response DTO</li>
 *   <li>在本接口新增转换方法</li>
 *   <li>复杂字段(如嵌套 List)可添加 default 方法辅助</li>
 * </ol>
 *
 * @author panoshu
 */
@Mapper(componentModel = "spring")
public interface ApplicationConverter {

    /**
     * 聚合根 → 摘要响应
     */
    default ApplicationSummaryResponse toSummaryResponse(BusinessApplication app) {
        if (app == null) {
            return null;
        }
        return new ApplicationSummaryResponse(
            app.id().value(),
            app.getBatchId() != null ? app.getBatchId().value() : null,
            app.getStatus() != null ? app.getStatus().name() : null,
            app.currentStep() != null ? app.currentStep().name() : null,
            app.createdAt(),
            app.updatedAt()
        );
    }

    /**
     * 聚合根 → 详情响应
     */
    default ApplicationDetailResponse toDetailResponse(BusinessApplication app) {
        if (app == null) {
            return null;
        }
        return new ApplicationDetailResponse(
            app.id().value(),
            app.getBatchId() != null ? app.getBatchId().value() : null,
            app.getStatus() != null ? app.getStatus().name() : null,
            app.currentStep() != null ? app.currentStep().name() : null,
            app.getParsedJsonFileId() != null ? app.getParsedJsonFileId().value() : null,
            app.getPackageFile() != null && app.getPackageFile().fileId() != null
                ? app.getPackageFile().fileId().value() : null,
            app.getApplyTime(),
            app.getCompleteTime(),
            app.createdAt(),
            app.updatedAt()
        );
    }

    /**
     * 申请单 ID → 推进响应
     *
     * <p>{@code nextStep}/{@code status} 需在推进后重新加载聚合根获取,
     * 由 Controller 负责调用 AppService.loadOrThrow 后转换。
     */
    default AdvanceStepResponse toAdvanceStepResponse(BusinessApplication app) {
        if (app == null) {
            return null;
        }
        return new AdvanceStepResponse(
            app.id().value(),
            app.currentStep() != null ? app.currentStep().name() : null,
            app.getStatus() != null ? app.getStatus().name() : null
        );
    }

    /**
     * 申请单 ID → 提交响应
     *
     * <p>当前 {@code needApproval} 固定为 false,{@code approvalInstanceId} 为 null,
     * 审批判断由管道 handler 完成。
     */
    default SubmitResponse toSubmitResponse(ApplicationId applicationId) {
        if (applicationId == null) {
            return null;
        }
        return new SubmitResponse(applicationId.value(), false, null);
    }
}
```

## Step 6: 编写 BusinessApplicationController

文件: `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/application/BusinessApplicationController.java`

```java
package com.example.core.adapter.application;

import com.example.core.adapter.application.converter.ApplicationConverter;
import com.example.core.adapter.context.SessionContextResolver;
import com.example.core.adapter.security.RequireBusinessPermission;
import com.example.core.api.application.BusinessApplicationApi;
import com.example.core.api.application.command.AdvanceStepCommand;
import com.example.core.api.application.command.SubmitApplicationCommand;
import com.example.core.api.application.query.FindApplicationListQuery;
import com.example.core.api.application.query.GetApplicationDetailQuery;
import com.example.core.api.application.response.AdvanceStepResponse;
import com.example.core.api.application.response.ApplicationDetailResponse;
import com.example.core.api.application.response.ApplicationSummaryResponse;
import com.example.core.api.application.response.SubmitResponse;
import com.example.core.api.context.SessionContext;
import com.example.core.application.business.service.BusinessApplicationAppService;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 业务申请单管理 Controller
 *
 * <p>实现 {@link BusinessApplicationApi},入口完成会话解析与功能权限校验,
 * 调用 {@link BusinessApplicationAppService} 进行申请单处理。
 *
 * <p>后续新增 Controller 方法流程:
 * <ol>
 *   <li>在 API 层接口新增方法签名</li>
 *   <li>在本类实现方法,标注 @RequireBusinessPermission(功能权限码)</li>
 *   <li>通过 ApplicationConverter 完成 DTO 转换</li>
 * </ol>
 *
 * @author panoshu
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class BusinessApplicationController implements BusinessApplicationApi {

    private final BusinessApplicationAppService applicationAppService;
    private final ApplicationConverter converter;
    private final SessionContextResolver sessionResolver;

    @Override
    @RequireBusinessPermission("APPLICATION_VIEW")
    public ApiResult<List<ApplicationSummaryResponse>> list(@Valid @RequestBody FindApplicationListQuery query) {
        SessionContext session = sessionResolver.require();
        log.info("查询申请单列表: batchId={}, status={}, userNo={}",
            query.batchId(), query.status(), session.userNo());

        List<BusinessApplication> apps = applicationAppService.findByBatchId(new BatchId(query.batchId()));
        List<ApplicationSummaryResponse> responses = apps.stream()
            .map(converter::toSummaryResponse)
            .toList();
        return ApiResult.success(responses);
    }

    @Override
    @RequireBusinessPermission("APPLICATION_VIEW")
    public ApiResult<ApplicationDetailResponse> detail(@Valid @RequestBody GetApplicationDetailQuery query) {
        SessionContext session = sessionResolver.require();
        log.info("查询申请单详情: applicationId={}, userNo={}", query.applicationId(), session.userNo());

        BusinessApplication app = applicationAppService.loadOrThrow(new ApplicationId(query.applicationId()));
        return ApiResult.success(converter.toDetailResponse(app));
    }

    @Override
    @RequireBusinessPermission("APPLICATION_ADVANCE")
    public ApiResult<AdvanceStepResponse> advance(@Valid @RequestBody AdvanceStepCommand command) {
        SessionContext session = sessionResolver.require();
        log.info("推进申请单: applicationId={}, userNo={}", command.applicationId(), session.userNo());

        ApplicationId appId = new ApplicationId(command.applicationId());
        applicationAppService.advanceStep(appId);
        // 推进后重新加载聚合根以获取最新步骤
        BusinessApplication app = applicationAppService.loadOrThrow(appId);
        return ApiResult.success(converter.toAdvanceStepResponse(app));
    }

    @Override
    @RequireBusinessPermission("APPLICATION_SUBMIT")
    public ApiResult<SubmitResponse> submit(@Valid @RequestBody SubmitApplicationCommand command) {
        SessionContext session = sessionResolver.require();
        log.info("提交申请单: applicationId={}, userNo={}", command.applicationId(), session.userNo());

        ApplicationId appId = new ApplicationId(command.applicationId());
        applicationAppService.submit(appId);
        return ApiResult.success(converter.toSubmitResponse(appId));
    }
}
```

## Step 7: 编译验证

Run: `mvn compile -pl business-core-kernel/business-core-adapter -am -q`
Expected: BUILD SUCCESS

## Step 8: 运行测试

Run: `mvn test -pl business-core-kernel/business-core-application -am -Dtest=BusinessApplicationAppServiceTest "-Dsurefire.failIfNoSpecifiedTests=false" -q`
Expected: PASS (4 tests)

## Step 9: 提交

```bash
git add business-core-kernel/business-core-api/src/main/java/com/example/core/api/application/ \
        business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/business/repository/ApplicationRepository.java \
        business-core-kernel/business-core-application/src/main/java/com/example/core/application/business/service/BusinessApplicationAppService.java \
        business-core-kernel/business-core-application/src/test/java/com/example/core/application/business/service/BusinessApplicationAppServiceTest.java \
        business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/application/
git commit -m "feat(core-adapter): 实现 BusinessApplicationApi 接口与 Controller" \
  -m "1. BusinessApplicationApi 定义列表/详情/推进/提交 4 个公共接口" \
  -m "2. BusinessApplicationAppService 编排申请单推进,复用 FlowOrchestrationService" \
  -m "3. BusinessApplicationController 入口完成会话解析与功能权限校验" \
  -m "4. ApplicationRepository 新增 findByBatchId default 方法供业务服务覆写" \
  -m "5. ApplicationConverter 使用 default 方法转换(同 Batch/Form 模式)" \
  -m "6. DTO 使用 String 类型 ID 与领域 ApplicationId/BatchId 一致"
```
