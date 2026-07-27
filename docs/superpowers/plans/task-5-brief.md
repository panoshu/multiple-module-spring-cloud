# Task 5: BusinessBatchAppService 与 Controller 实现

**Files:**
- Modify: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/batch/response/BatchSummaryResponse.java` (Long → String batchId)
- Modify: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/batch/response/BatchCreatedResponse.java` (Long → String batchId)
- Modify: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/batch/response/BatchDetailResponse.java` (Long → String batchId)
- Modify: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/batch/command/CancelBatchCommand.java` (Long → String batchId)
- Modify: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/batch/query/GetBatchDetailQuery.java` (Long → String batchId)
- Modify: `business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/business/aggregate/root/BusinessBatch.java` (新增 create 工厂方法、cancel 行为、getters)
- Modify: `business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/business/repository/BatchRepository.java` (新增 findActive 方法)
- Create: `business-core-kernel/business-core-application/src/main/java/com/example/core/application/business/service/BusinessBatchAppService.java`
- Create: `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/batch/BusinessBatchController.java`
- Create: `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/batch/converter/BatchConverter.java`
- Test: `business-core-kernel/business-core-application/src/test/java/com/example/core/application/business/service/BusinessBatchAppServiceTest.java`

**Interfaces:**
- Consumes: `BusinessBatchApi`, `SessionContextResolver`, `BusinessMetaContextAssembler`, `SupportedBusinessTypeValidator`, `BusinessAccessGuard`, `BatchRepository`, `BusinessBatch` 聚合根, `BusinessContext` 值对象, `OperatorInfo` 值对象
- Produces: `BusinessBatchAppService` bean, `BusinessBatchController` bean, `BatchConverter` bean

## 关键设计决策(必读)

> 以下是对原 plan brief 的修正,实现时以本节为准:

1. **ID 类型修正**: `BatchId` 是 `record BatchId(String value)`,格式 `%p%d%s` 生成形如 `BATCH20260726001` 的字符串。Task 4 的 DTO 误用 `Long batchId`,本任务需将所有 DTO 中的 `Long batchId` 改为 `String batchId`,以保持与领域类型一致。

2. **IdService 方法名**: 正确方法是 `idService.nextId(BatchId.class)`,不是 `generateId`。

3. **OperatorInfo 构造函数**: 实际签名为 `OperatorInfo(AnnuityChannel channel, UserNo operatorId, String operatorName, boolean isProxy)`,4 个参数,不是 2 个。

4. **BusinessBatch 需补充**:
   - 静态工厂方法 `create(BatchId, BusinessContext, OperatorInfo)`
   - `cancel(String reason)` 行为方法,修改状态为 CANCELLED 并注册领域事件
   - 各字段的 getter(`getBusinessContext()`, `getOperatorInfo()`, `getStatus()`, `getTotalApplicationCount()`, `getSuccessCount()`, `getFailedCount()`, `getBusinessFormRefs()`)

5. **BatchRepository 需补充**: `findActive(PlanNo planNo, BusinessType businessType)` 方法,返回 `Optional<BusinessBatch>`,查询未完成/处理中的批次。

6. **BatchStatus 枚举需补充**: 新增 `CANCELLED` 终态状态,并更新 `isTerminal()`。

7. **Controller findActive/cancel 方法**: 不留 TODO,完整实现。

## Step 1: 修正 Task 4 的 DTO(ID 类型 Long → String)

将以下 5 个文件中的 `Long batchId` 改为 `String batchId`,注解从 `@NotNull` 改为 `@NotBlank`:

### BatchSummaryResponse.java
```java
package com.example.core.api.batch.response;

import java.time.LocalDateTime;

/**
 * 批次摘要响应
 *
 * @author panoshu
 */
public record BatchSummaryResponse(
    String batchId,
    String businessType,
    String planNo,
    String status,
    int totalFormCount,
    int totalApplicationCount,
    int successCount,
    int failedCount,
    LocalDateTime createTime
) {
}
```

### BatchCreatedResponse.java
```java
package com.example.core.api.batch.response;

import java.time.LocalDateTime;

/**
 * 批次创建响应
 *
 * @author panoshu
 */
public record BatchCreatedResponse(
    String batchId,
    String status,
    LocalDateTime createTime
) {
}
```

### BatchDetailResponse.java
```java
package com.example.core.api.batch.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 批次详情响应
 *
 * @author panoshu
 */
public record BatchDetailResponse(
    String batchId,
    String businessType,
    String planNo,
    String customerNo,
    String customerName,
    String status,
    int totalFormCount,
    int totalApplicationCount,
    int successCount,
    int failedCount,
    LocalDateTime createTime,
    LocalDateTime updateTime,
    List<FormSummary> forms
) {
    /**
     * 批次下表单摘要
     */
    public record FormSummary(
        String formId,
        String fileName,
        String status,
        int applicationCount,
        LocalDateTime uploadTime
    ) {
    }
}
```

### CancelBatchCommand.java
```java
package com.example.core.api.batch.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 取消业务批次命令
 *
 * @author panoshu
 */
public record CancelBatchCommand(
    @NotBlank(message = "批次ID不能为空") String batchId,
    String reason
) {
}
```

### GetBatchDetailQuery.java
```java
package com.example.core.api.batch.query;

import jakarta.validation.constraints.NotBlank;

/**
 * 查询批次详情
 *
 * @author panoshu
 */
public record GetBatchDetailQuery(
    @NotBlank(message = "批次ID不能为空") String batchId
) {
}
```

## Step 2: 扩展 BatchStatus 枚举(新增 CANCELLED)

文件: `business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/business/aggregate/valueobject/enums/status/BatchStatus.java`

```java
package com.example.core.domain.business.aggregate.valueobject.enums.status;

/**
 * 业务批次状态
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/12 12:59
 */
public enum BatchStatus {
  CREATED, PROCESSING, PARTIAL_FAILED, FAILED, COMPLETED, CANCELLED;

  public static BatchStatus determine(int failedCount, int totalCount) {

    if (failedCount < 0 || failedCount > totalCount || totalCount == 0) {
      throw new IllegalArgumentException(String.format("计数参数非法: failedCount=%d, totalCount=%d", failedCount, totalCount));
    }

    if (failedCount == totalCount) {
      return FAILED;
    }
    if (failedCount > 0) {
      return PARTIAL_FAILED;
    }
    return COMPLETED;
  }

  public boolean isTerminal() {
    return this == COMPLETED || this == FAILED || this == PARTIAL_FAILED || this == CANCELLED;
  }

  public boolean isActive() {
    return this == CREATED || this == PROCESSING;
  }
}
```

## Step 3: 扩展 BusinessBatch 聚合根

文件: `business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/business/aggregate/root/BusinessBatch.java`

在现有类中新增以下内容(保留所有已有方法不变):

```java
  /**
   * 工厂方法:创建新业务批次
   *
   * @param batchId 批次ID
   * @param context 业务上下文
   * @param operator 操作人信息
   * @return 新创建的批次聚合根
   */
  public static BusinessBatch create(BatchId batchId, BusinessContext context, OperatorInfo operator) {
    BusinessBatch batch = new BusinessBatch(batchId, operator.operatorId());
    batch.businessContext = context;
    batch.operatorInfo = operator;
    batch.status = BatchStatus.CREATED;
    return batch;
  }

  /**
   * 行为:取消批次
   *
   * <p>只有 CREATED 或 PROCESSING 状态的批次才能取消。
   *
   * @param reason 取消原因
   * @throws DomainException 当批次状态不允许取消时
   */
  public void cancel(String reason) {
    if (this.status == null || !this.status.isActive()) {
      throw new DomainException(CoreDomainErrorCode.INVALID_STATUS)
        .withLogDetail("只有未完成/处理中的批次才能取消, BatchId: %s, status: %s".formatted(this.id().value(), this.status));
    }
    BatchStatus oldStatus = this.status;
    this.status = BatchStatus.CANCELLED;
    this.registerDomainEvent(BatchStatusChangedEvent.of(this.id(), oldStatus, BatchStatus.CANCELLED));
  }

  // ============ Getters ============

  public BusinessContext getBusinessContext() {
    return this.businessContext;
  }

  public OperatorInfo getOperatorInfo() {
    return this.operatorInfo;
  }

  public BatchStatus getStatus() {
    return this.status;
  }

  public int getTotalApplicationCount() {
    return this.totalApplicationCount;
  }

  public int getSuccessCount() {
    return this.successCount;
  }

  public int getFailedCount() {
    return this.failedCount;
  }

  public List<BusinessFormRef> getBusinessFormRefs() {
    return this.businessFormRefs;
  }
```

**注意**:
- `businessFormRefs` 字段当前是包级私有 `List<BusinessFormRef> businessFormRefs;`,保持不变,通过 getter 暴露
- `cancel` 方法注册 `BatchStatusChangedEvent`(已存在于 `com.example.core.domain.business.event`)
- 修改 `validateStatusConsistency()` 逻辑:当 status 为 CANCELLED 时不应该再更新计数,但创建时 status=null 需要跳过校验。原方法在 status=null 时 `isTerminalStatus(null)` 返回 false,所以创建场景不会误抛。保持原逻辑不变。

## Step 4: 扩展 BatchRepository 接口

文件: `business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/business/repository/BatchRepository.java`

```java
package com.example.core.domain.business.repository;

import com.example.core.domain.business.aggregate.root.BusinessBatch;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.shared.domain.repository.Repository;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.FormId;
import com.example.shared.primitives.identity.PlanNo;

import java.util.Optional;

/**
 * 业务批次聚合根仓库
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/14 10:20
 */
public interface BatchRepository extends Repository<BusinessBatch, BatchId> {
  Optional<BusinessBatch> findByFormId(FormId formId);

  Optional<BusinessBatch> findByApplicationId(ApplicationId applicationId);

  /**
   * 查询指定计划+业务类型的未完成/处理中批次(活跃批次)。
   *
   * @param planNo 计划编号
   * @param businessType 业务类型
   * @return 活跃批次(若存在),只返回 CREATED 或 PROCESSING 状态的批次
   */
  Optional<BusinessBatch> findActive(PlanNo planNo, BusinessType businessType);
}
```

## Step 5: 编写 BusinessBatchAppService 失败测试

文件: `business-core-kernel/business-core-application/src/test/java/com/example/core/application/business/service/BusinessBatchAppServiceTest.java`

```java
package com.example.core.application.business.service;

import com.example.core.domain.business.aggregate.root.BusinessBatch;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.AnnuityChannel;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.aggregate.valueobject.business.OperationModel;
import com.example.core.domain.business.aggregate.valueobject.enums.status.BatchStatus;
import com.example.core.domain.business.repository.BatchRepository;
import com.example.shared.domain.event.EventBus;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.FormId;
import com.example.shared.primitives.identity.IdService;
import com.example.shared.primitives.identity.PlanNo;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * BusinessBatchAppService 单元测试
 *
 * @author panoshu
 */
class BusinessBatchAppServiceTest {

    private BatchRepository batchRepository;
    private EventBus eventBus;
    private IdService idService;
    private BusinessBatchAppService appService;

    @BeforeEach
    void setUp() {
        batchRepository = mock(BatchRepository.class);
        eventBus = mock(EventBus.class);
        idService = mock(IdService.class);
        when(idService.nextId(BatchId.class)).thenReturn(new BatchId("BATCH20260726001"));
        appService = new BusinessBatchAppService(batchRepository, eventBus, idService);
    }

    private BusinessContext sampleContext() {
        return new BusinessContext(
            BusinessType.ACC_PLAN_CREATE,
            new CustomerNo("C001"), "Customer A",
            new ProductNo("PRD001"), "Product A",
            new PlanNo("P001"), "Plan A",
            OperationModel.Single_Trustee,
            AccountManager.CJP
        );
    }

    private OperatorInfo sampleOperator() {
        return new OperatorInfo(
            AnnuityChannel.CJ_TELLER,
            new UserNo("U001"),
            "alice",
            false
        );
    }

    @Test
    void should_create_batch_with_context_and_operator() {
        BusinessContext context = sampleContext();
        OperatorInfo operator = sampleOperator();

        BusinessBatch batch = appService.createBatch(context, operator);

        ArgumentCaptor<BusinessBatch> captor = ArgumentCaptor.forClass(BusinessBatch.class);
        verify(batchRepository).save(captor.capture());
        BusinessBatch saved = captor.getValue();
        assertThat(saved.id()).isNotNull();
        assertThat(saved.id().value()).isEqualTo("BATCH20260726001");
        assertThat(saved.getBusinessContext()).isEqualTo(context);
        assertThat(saved.getOperatorInfo()).isEqualTo(operator);
        assertThat(saved.getStatus()).isEqualTo(BatchStatus.CREATED);
    }

    @Test
    void should_find_active_batch_by_plan_and_business_type() {
        BusinessBatch batch = mock(BusinessBatch.class);
        PlanNo planNo = new PlanNo("P001");
        BusinessType businessType = BusinessType.ACC_PLAN_CREATE;
        when(batchRepository.findActive(eq(planNo), eq(businessType)))
            .thenReturn(Optional.of(batch));

        Optional<BusinessBatch> result = appService.findActive(planNo, businessType);

        assertThat(result).isPresent();
        verify(batchRepository).findActive(planNo, businessType);
    }

    @Test
    void should_load_batch_or_throw() {
        BatchId batchId = new BatchId("BATCH20260726001");
        BusinessBatch batch = mock(BusinessBatch.class);
        when(batchRepository.loadOrThrow(batchId)).thenReturn(batch);

        BusinessBatch result = appService.loadOrThrow(batchId);

        assertThat(result).isSameAs(batch);
        verify(batchRepository).loadOrThrow(batchId);
    }

    @Test
    void should_cancel_batch_and_publish_event() {
        BatchId batchId = new BatchId("BATCH20260726001");
        BusinessBatch batch = mock(BusinessBatch.class);
        when(batchRepository.loadOrThrow(batchId)).thenReturn(batch);
        when(batch.getDomainEvents()).thenReturn(java.util.List.of());

        appService.cancel(batchId, "用户主动取消");

        verify(batch).cancel("用户主动取消");
        verify(batchRepository).save(batch);
    }

    @Test
    void should_find_batch_by_form_id() {
        FormId formId = new FormId("FORM001");
        BusinessBatch batch = mock(BusinessBatch.class);
        when(batchRepository.findByFormId(formId)).thenReturn(Optional.of(batch));

        Optional<BusinessBatch> result = appService.findByFormId(formId);

        assertThat(result).isPresent();
        verify(batchRepository).findByFormId(formId);
    }
}
```

## Step 6: 运行测试确认失败

Run: `mvn test -pl business-core-kernel/business-core-application -Dtest=BusinessBatchAppServiceTest`
Expected: FAIL (BusinessBatchAppService 不存在)

## Step 7: 编写 BusinessBatchAppService 实现

文件: `business-core-kernel/business-core-application/src/main/java/com/example/core/application/business/service/BusinessBatchAppService.java`

```java
package com.example.core.application.business.service;

import com.example.core.domain.business.aggregate.root.BusinessBatch;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.repository.BatchRepository;
import com.example.shared.domain.event.EventBus;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.FormId;
import com.example.shared.primitives.identity.IdService;
import com.example.shared.primitives.identity.PlanNo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 业务批次应用服务
 *
 * <p>编排批次的创建、查询、取消等业务流程,管理事务边界。
 *
 * <p>后续新增应用服务方法流程:
 * <ol>
 *   <li>在领域层聚合根/Repository 中定义行为/查询方法</li>
 *   <li>在本类中编排:加载聚合根 → 调用领域行为 → 保存 → 发布事件</li>
 *   <li>事务边界由本类管理(@Transactional)</li>
 * </ol>
 *
 * @author panoshu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessBatchAppService {

    private final BatchRepository batchRepository;
    private final EventBus eventBus;
    private final IdService idService;

    /**
     * 创建业务批次。
     *
     * @param context 业务上下文(从 SessionContext 组装)
     * @param operator 操作人信息
     * @return 创建的批次聚合根
     */
    @Transactional
    public BusinessBatch createBatch(BusinessContext context, OperatorInfo operator) {
        BatchId batchId = idService.nextId(BatchId.class);
        BusinessBatch batch = BusinessBatch.create(batchId, context, operator);
        batchRepository.save(batch);
        batch.getDomainEvents().forEach(eventBus::publish);
        batch.clearDomainEvents();
        log.info("创建业务批次成功: batchId={}, businessType={}", batchId.value(), context.businessType());
        return batch;
    }

    /**
     * 查询指定计划+业务类型的活跃批次(未完成/处理中)。
     *
     * @param planNo 计划编号
     * @param businessType 业务类型
     * @return 活跃批次(若存在)
     */
    @Transactional(readOnly = true)
    public Optional<BusinessBatch> findActive(PlanNo planNo, BusinessType businessType) {
        return batchRepository.findActive(planNo, businessType);
    }

    /**
     * 通过表单 ID 反查批次。
     */
    @Transactional(readOnly = true)
    public Optional<BusinessBatch> findByFormId(FormId formId) {
        return batchRepository.findByFormId(formId);
    }

    /**
     * 加载批次(不存在时抛异常)。
     */
    @Transactional(readOnly = true)
    public BusinessBatch loadOrThrow(BatchId batchId) {
        return batchRepository.loadOrThrow(batchId);
    }

    /**
     * 取消批次。
     *
     * @param batchId 批次ID
     * @param reason 取消原因
     */
    @Transactional
    public void cancel(BatchId batchId, String reason) {
        BusinessBatch batch = batchRepository.loadOrThrow(batchId);
        batch.cancel(reason);
        batchRepository.save(batch);
        batch.getDomainEvents().forEach(eventBus::publish);
        batch.clearDomainEvents();
        log.info("取消业务批次: batchId={}, reason={}", batchId.value(), reason);
    }
}
```

## Step 8: 运行测试确认通过

Run: `mvn test -pl business-core-kernel/business-core-application -Dtest=BusinessBatchAppServiceTest`
Expected: PASS (5 tests)

## Step 9: 编写 BatchConverter(MapStruct)

文件: `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/batch/converter/BatchConverter.java`

```java
package com.example.core.adapter.batch.converter;

import com.example.core.api.batch.response.BatchCreatedResponse;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.core.api.batch.response.BatchSummaryResponse;
import com.example.core.domain.business.aggregate.root.BusinessBatch;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 批次 DTO 转换器
 *
 * <p>通过 MapStruct 完成聚合根到响应 DTO 的转换,禁止在 Controller 中直接转换。
 *
 * <p>后续新增 DTO 转换流程:
 * <ol>
 *   <li>在 API 层定义 Response DTO</li>
 *   <li>在本接口新增转换方法,通过 @Mapping 指定字段映射</li>
 *   <li>复杂字段(如嵌套 List)可添加 default 方法辅助</li>
 * </ol>
 *
 * @author panoshu
 */
@Mapper(componentModel = "spring")
public interface BatchConverter {

    @Mapping(target = "batchId", source = "id.value")
    @Mapping(target = "businessType", source = "businessContext.businessType.name")
    @Mapping(target = "planNo", source = "businessContext.planNo.value")
    @Mapping(target = "status", source = "status.name")
    @Mapping(target = "totalFormCount", source = "businessFormRefs.size")
    @Mapping(target = "totalApplicationCount", source = "totalApplicationCount")
    @Mapping(target = "successCount", source = "successCount")
    @Mapping(target = "failedCount", source = "failedCount")
    @Mapping(target = "createTime", source = "createdAt")
    BatchSummaryResponse toSummaryResponse(BusinessBatch batch);

    @Mapping(target = "batchId", source = "id.value")
    @Mapping(target = "status", source = "status.name")
    @Mapping(target = "createTime", source = "createdAt")
    BatchCreatedResponse toCreatedResponse(BusinessBatch batch);

    @Mapping(target = "batchId", source = "id.value")
    @Mapping(target = "businessType", source = "businessContext.businessType.name")
    @Mapping(target = "planNo", source = "businessContext.planNo.value")
    @Mapping(target = "customerNo", source = "businessContext.customerNo.value")
    @Mapping(target = "customerName", source = "businessContext.customerName")
    @Mapping(target = "status", source = "status.name")
    @Mapping(target = "totalFormCount", source = "businessFormRefs.size")
    @Mapping(target = "totalApplicationCount", source = "totalApplicationCount")
    @Mapping(target = "successCount", source = "successCount")
    @Mapping(target = "failedCount", source = "failedCount")
    @Mapping(target = "createTime", source = "createdAt")
    @Mapping(target = "updateTime", source = "updatedAt")
    @Mapping(target = "forms", ignore = true)
    BatchDetailResponse toDetailResponse(BusinessBatch batch);
}
```

## Step 10: 编写 BusinessBatchController

文件: `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/batch/BusinessBatchController.java`

```java
package com.example.core.adapter.batch;

import com.example.core.adapter.batch.converter.BatchConverter;
import com.example.core.adapter.context.BusinessMetaContextAssembler;
import com.example.core.adapter.context.SessionContextResolver;
import com.example.core.adapter.security.RequireBusinessPermission;
import com.example.core.adapter.validator.SupportedBusinessTypeValidator;
import com.example.core.api.batch.BusinessBatchApi;
import com.example.core.api.batch.command.CancelBatchCommand;
import com.example.core.api.batch.command.CreateBatchCommand;
import com.example.core.api.batch.query.FindActiveBatchQuery;
import com.example.core.api.batch.query.GetBatchDetailQuery;
import com.example.core.api.batch.response.BatchCreatedResponse;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.core.api.batch.response.BatchSummaryResponse;
import com.example.core.api.context.BusinessMetaContext;
import com.example.core.api.context.SessionContext;
import com.example.core.application.business.guard.BusinessAccessGuard;
import com.example.core.application.business.service.BusinessBatchAppService;
import com.example.core.domain.business.aggregate.root.BusinessBatch;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.AnnuityChannel;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.aggregate.valueobject.business.OperationModel;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.PlanNo;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 业务批次管理 Controller
 *
 * <p>实现 {@link BusinessBatchApi},入口完成五步:
 * <ol>
 *   <li>业务类型校验({@link SupportedBusinessTypeValidator})</li>
 *   <li>会话解析({@link SessionContextResolver})</li>
 *   <li>BusinessMetaContext 组装({@link BusinessMetaContextAssembler})</li>
 *   <li>权限校验({@link BusinessAccessGuard})</li>
 *   <li>调用 AppService({@link BusinessBatchAppService})</li>
 * </ol>
 *
 * <p>方法签名与 API 接口完全一致,会话通过 {@link SessionContextResolver} 内部
 * 使用 {@code RequestContextHolder} 获取当前请求解析。
 *
 * <p>后续新增 Controller 方法流程:
 * <ol>
 *   <li>在 API 层接口新增方法签名</li>
 *   <li>在本类实现方法,标注 @RequireBusinessPermission(功能权限码)</li>
 *   <li>入口完成上述五步,通过 MapStruct Converter 完成 DTO 转换</li>
 * </ol>
 *
 * @author panoshu
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class BusinessBatchController implements BusinessBatchApi {

    private final BusinessBatchAppService batchAppService;
    private final BatchConverter converter;
    private final SupportedBusinessTypeValidator typeValidator;
    private final SessionContextResolver sessionResolver;
    private final BusinessMetaContextAssembler metaAssembler;
    private final BusinessAccessGuard accessGuard;

    @Override
    @RequireBusinessPermission("BATCH_VIEW")
    public ApiResult<Optional<BatchSummaryResponse>> findActive(@Valid @RequestBody FindActiveBatchQuery query) {
        typeValidator.validate(query.businessType());
        SessionContext session = sessionResolver.require();
        log.info("查询未完成批次: planNo={}, businessType={}, userNo={}",
            query.planNo(), query.businessType(), session.userNo());

        Optional<BusinessBatch> batch = batchAppService.findActive(
            new PlanNo(query.planNo()),
            BusinessType.valueOf(query.businessType())
        );
        return ApiResult.success(batch.map(converter::toSummaryResponse));
    }

    @Override
    @RequireBusinessPermission("BATCH_CREATE")
    public ApiResult<BatchCreatedResponse> create(@Valid @RequestBody CreateBatchCommand command) {
        typeValidator.validate(command.businessType());
        SessionContext session = sessionResolver.require();
        BusinessMetaContext meta = metaAssembler.assemble(command.businessType(), command.planNo(), session);
        accessGuard.checkCanHandle(session, meta);

        log.info("创建业务批次: businessType={}, planNo={}, userNo={}",
            command.businessType(), command.planNo(), session.userNo());

        BusinessContext domainContext = toDomainContext(meta, session);
        OperatorInfo operator = toOperatorInfo(session);
        BusinessBatch batch = batchAppService.createBatch(domainContext, operator);
        return ApiResult.success(converter.toCreatedResponse(batch));
    }

    @Override
    @RequireBusinessPermission("BATCH_VIEW")
    public ApiResult<BatchDetailResponse> detail(@Valid @RequestBody GetBatchDetailQuery query) {
        SessionContext session = sessionResolver.require();
        log.info("查询批次详情: batchId={}, userNo={}", query.batchId(), session.userNo());
        BusinessBatch batch = batchAppService.loadOrThrow(new BatchId(query.batchId()));
        return ApiResult.success(converter.toDetailResponse(batch));
    }

    @Override
    @RequireBusinessPermission("BATCH_CANCEL")
    public ApiResult<Void> cancel(@Valid @RequestBody CancelBatchCommand command) {
        SessionContext session = sessionResolver.require();
        log.info("取消批次: batchId={}, userNo={}", command.batchId(), session.userNo());
        batchAppService.cancel(new BatchId(command.batchId()), command.reason());
        return ApiResult.success();
    }

    /**
     * 将 BusinessMetaContext + SessionContext 转换为领域 BusinessContext。
     */
    private BusinessContext toDomainContext(BusinessMetaContext meta, SessionContext session) {
        return new BusinessContext(
            BusinessType.valueOf(meta.businessType()),
            new CustomerNo(meta.customerNo()),
            meta.customerName(),
            new ProductNo(meta.productNo()),
            meta.productName(),
            new PlanNo(meta.planNo()),
            meta.planName(),
            OperationModel.valueOf(meta.operationModel()),
            AccountManager.valueOf(meta.accountManager())
        );
    }

    /**
     * 从 SessionContext 组装 OperatorInfo。
     *
     * <p>渠道映射:SessionContext.channelType(String) → AnnuityChannel 枚举。
     * isProxy 直接取自 SessionContext(仅 INTERNET 渠道为 true)。
     */
    private OperatorInfo toOperatorInfo(SessionContext session) {
        AnnuityChannel channel = mapChannel(session.channelType());
        return new OperatorInfo(
            channel,
            new UserNo(session.userNo()),
            session.loginName(),
            session.isProxy()
        );
    }

    /**
     * 渠道字符串映射为 AnnuityChannel 枚举。
     *
     * <p>映射规则:
     * <ul>
     *   <li>INTERNET → NETAPP(网上渠道,含代办)</li>
     *   <li>BRANCH → CJ_TELLER(网点渠道,需二次授权)</li>
     *   <li>HQ → REGIONAL_CENTER(总部渠道)</li>
     * </ul>
     */
    private AnnuityChannel mapChannel(String channelType) {
        if (channelType == null) {
            return AnnuityChannel.NETAPP;
        }
        return switch (channelType) {
            case "INTERNET" -> AnnuityChannel.NETAPP;
            case "BRANCH" -> AnnuityChannel.CJ_TELLER;
            case "HQ" -> AnnuityChannel.REGIONAL_CENTER;
            default -> AnnuityChannel.NETAPP;
        };
    }
}
```

## Step 11: 编译验证

Run: `mvn compile -pl business-core-kernel/business-core-adapter -am`
Expected: BUILD SUCCESS

## Step 12: 运行所有相关测试

Run: `mvn test -pl business-core-kernel/business-core-application -Dtest=BusinessBatchAppServiceTest`
Expected: PASS (5 tests)

## Step 13: 提交

```bash
git add business-core-kernel/business-core-api/src/main/java/com/example/core/api/batch/ \
        business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/business/aggregate/root/BusinessBatch.java \
        business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/business/aggregate/valueobject/enums/status/BatchStatus.java \
        business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/business/repository/BatchRepository.java \
        business-core-kernel/business-core-application/src/main/java/com/example/core/application/business/service/BusinessBatchAppService.java \
        business-core-kernel/business-core-application/src/test/java/com/example/core/application/business/service/BusinessBatchAppServiceTest.java \
        business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/batch/
git commit -m "feat(core-adapter): 实现 BusinessBatchApi 与应用服务" -m "1. BusinessBatchAppService 编排批次创建/查询/取消,管理事务边界" -m "2. BusinessBatchController 入口完成业务类型校验→会话解析→权限校验→调用 AppService" -m "3. BatchConverter 通过 MapStruct 完成聚合根到响应 DTO 的转换" -m "4. BusinessBatch 新增 create 工厂方法、cancel 行为、getters" -m "5. BatchRepository 新增 findActive 方法" -m "6. BatchStatus 新增 CANCELLED 终态" -m "7. 修正 Task 4 DTO 的 batchId 类型 Long → String 与领域 BatchId 一致"
```
