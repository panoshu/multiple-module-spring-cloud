# Task 4: BusinessBatchApi 接口定义与 DTO

**Files:**
- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/batch/BusinessBatchApi.java`
- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/batch/command/CreateBatchCommand.java`
- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/batch/command/CancelBatchCommand.java`
- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/batch/query/FindActiveBatchQuery.java`
- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/batch/query/GetBatchDetailQuery.java`
- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/batch/response/BatchSummaryResponse.java`
- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/batch/response/BatchCreatedResponse.java`
- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/batch/response/BatchDetailResponse.java`

**Interfaces:**
- Consumes: `ApiResult`, `@HttpExchange`, `@PostExchange`, `@Valid`, `@RequestBody`
- Produces: `BusinessBatchApi` 接口及配套 Command/Query/Response DTO

## Step 1: 编写 Command/Query/Response DTO

```java
// CreateBatchCommand.java
package com.example.core.api.batch.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建业务批次命令
 *
 * <p>前端只传办理意图(businessType + planNo),客户/产品/账管人等敏感字段
 * 由后端从 SessionContext 组装,杜绝前端伪造。
 *
 * @author panoshu
 */
public record CreateBatchCommand(
    @NotBlank(message = "业务类型不能为空") String businessType,
    @NotBlank(message = "计划编号不能为空") String planNo,
    String operatorRemark
) {
}
```

```java
// CancelBatchCommand.java
package com.example.core.api.batch.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 取消业务批次命令
 *
 * @author panoshu
 */
public record CancelBatchCommand(
    @NotNull(message = "批次ID不能为空") Long batchId,
    String reason
) {
}
```

```java
// FindActiveBatchQuery.java
package com.example.core.api.batch.query;

import jakarta.validation.constraints.NotBlank;

/**
 * 查询未完成/处理中业务批次
 *
 * @author panoshu
 */
public record FindActiveBatchQuery(
    @NotBlank(message = "计划编号不能为空") String planNo,
    @NotBlank(message = "业务类型不能为空") String businessType
) {
}
```

```java
// GetBatchDetailQuery.java
package com.example.core.api.batch.query;

import jakarta.validation.constraints.NotNull;

/**
 * 查询批次详情
 *
 * @author panoshu
 */
public record GetBatchDetailQuery(
    @NotNull(message = "批次ID不能为空") Long batchId
) {
}
```

```java
// BatchSummaryResponse.java
package com.example.core.api.batch.response;

import java.time.LocalDateTime;

/**
 * 批次摘要响应
 *
 * @author panoshu
 */
public record BatchSummaryResponse(
    Long batchId,
    String batchNo,
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

```java
// BatchCreatedResponse.java
package com.example.core.api.batch.response;

import java.time.LocalDateTime;

/**
 * 批次创建响应
 *
 * @author panoshu
 */
public record BatchCreatedResponse(
    Long batchId,
    String batchNo,
    String status,
    LocalDateTime createTime
) {
}
```

```java
// BatchDetailResponse.java
package com.example.core.api.batch.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 批次详情响应
 *
 * @author panoshu
 */
public record BatchDetailResponse(
    Long batchId,
    String batchNo,
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
        Long formId,
        String fileName,
        String status,
        int applicationCount,
        LocalDateTime uploadTime
    ) {
    }
}
```

## Step 2: 编写 BusinessBatchApi 接口

```java
package com.example.core.api.batch;

import com.example.core.api.batch.command.CancelBatchCommand;
import com.example.core.api.batch.command.CreateBatchCommand;
import com.example.core.api.batch.query.FindActiveBatchQuery;
import com.example.core.api.batch.query.GetBatchDetailQuery;
import com.example.core.api.batch.response.BatchCreatedResponse;
import com.example.core.api.batch.response.BatchDetailResponse;
import com.example.core.api.batch.response.BatchSummaryResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Optional;

/**
 * 业务批次管理 API
 *
 * <p>提供批次的查询未完成、创建、详情、取消等公共接口,所有业务类型共用。
 * 路径前缀 {@code /core/batch}。
 *
 * <p>后续新增接口流程:
 * <ol>
 *   <li>在 API 层新增方法到本接口(或新建 Api 接口),路径前缀 /core</li>
 *   <li>在 application 层扩展 AppService 方法</li>
 *   <li>在 adapter 层实现 Controller,入口完成业务类型校验→会话解析→权限校验→调用 AppService</li>
 *   <li>通过 MapStruct Converter 完成 DTO 转换</li>
 * </ol>
 *
 * @author panoshu
 */
@HttpExchange("/core/batch")
public interface BusinessBatchApi {

    /**
     * 查询指定计划+业务类型的未完成/处理中批次。
     */
    @PostExchange("/active")
    ApiResult<Optional<BatchSummaryResponse>> findActive(@Valid @RequestBody FindActiveBatchQuery query);

    /**
     * 创建新批次。
     *
     * <p>前端只传 businessType + planNo,后端从 SessionContext 组装完整元数据。
     */
    @PostExchange("/create")
    ApiResult<BatchCreatedResponse> create(@Valid @RequestBody CreateBatchCommand command);

    /**
     * 查询批次详情(含表单/申请单摘要)。
     */
    @PostExchange("/detail")
    ApiResult<BatchDetailResponse> detail(@Valid @RequestBody GetBatchDetailQuery query);

    /**
     * 取消未提交批次。
     */
    @PostExchange("/cancel")
    ApiResult<Void> cancel(@Valid @RequestBody CancelBatchCommand command);
}
```

## Step 3: 编译验证

Run: `mvn compile -pl business-core-kernel/business-core-api`
Expected: BUILD SUCCESS

## Step 4: 提交

```bash
git add business-core-kernel/business-core-api/src/main/java/com/example/core/api/batch/
git commit -m "feat(core-api): 新增 BusinessBatchApi 接口与配套 DTO

1. BusinessBatchApi 定义批次查询/创建/详情/取消 4 个公共接口
2. CreateBatchCommand 仅含 businessType+planNo+operatorRemark,敏感字段由后端组装
3. 配套 FindActiveBatchQuery/GetBatchDetailQuery/CancelBatchCommand
4. 配套 BatchSummaryResponse/BatchCreatedResponse/BatchDetailResponse"
```
