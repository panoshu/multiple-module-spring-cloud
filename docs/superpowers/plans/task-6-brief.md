# Task 6: BusinessFormApi 接口与实现

**Files:**
- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/form/BusinessFormApi.java`
- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/form/command/ApplyUploadTokenCommand.java`
- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/form/command/ConfirmUploadCommand.java`
- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/form/command/DeleteFormCommand.java`
- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/form/query/GetFormStatusQuery.java`
- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/form/response/UploadTokenResponse.java`
- Create: `business-core-kernel/business-core-api/src/main/java/com/example/core/api/form/response/FormStatusResponse.java`
- Modify: `business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/business/aggregate/root/BusinessForm.java` (新增 markAsDeleted、getFormStatus getter)
- Modify: `business-core-kernel/business-core-application/src/main/java/com/example/core/application/engine/step/service/BusinessFormAppService.java` (新增 applyUploadToken、deleteForm、getFormStatus 方法)
- Create: `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/form/BusinessFormController.java`
- Create: `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/form/converter/FormConverter.java`
- Test: `business-core-kernel/business-core-application/src/test/java/com/example/core/application/engine/step/service/BusinessFormAppServiceTest.java`

**Interfaces:**
- Consumes: `BusinessFormAppService`(已存在,需扩展), `SessionContextResolver`, `FileIntegrationGateway`(已存在于 AppService)
- Produces: `BusinessFormApi` 接口及配套 DTO, `BusinessFormController` bean, `FormConverter` bean

## 关键设计决策(必读)

> 以下是对原 plan brief 的修正,实现时以本节为准:

1. **ID 类型**: `BatchId` 和 `FormId` 都是 String-based record。所有 DTO 中的 `Long batchId`/`Long formId` 改为 `String`。

2. **不留 TODO**: plan brief 的 Controller 有 4 处 TODO,本任务需完整实现所有方法,不留占位代码。

3. **ConfirmUploadCommand 字段扩展**: plan 的 `{batchId, formId, fileMd5}` 不足以构造 `BusinessFile`。扩展为 `{batchId, formId, fileId, fileName, fileMd5}`,因为前端直传文件服务后获得 `fileId`,确认上传时需回传。

4. **BusinessFormAppService 扩展**: 在现有的 `engine.step.service.BusinessFormAppService` 中新增 3 个方法(`applyUploadToken`/`deleteForm`/`getFormStatus`),复用其已有的 `FileIntegrationGateway` 依赖。

5. **BusinessForm 扩展**: 新增 `markAsDeleted()` 行为(设状态为 DELETED)和 `getFormStatus()` getter。

6. **FormConverter**: 使用 `default` 方法(同 Task 5 的 BatchConverter 模式),因 `BusinessForm` 继承泛型基类,MapStruct `@Mapping` 无法解析。

7. **UploadTokenResponse**: `FileIntegrationGateway.applyUploadToken()` 只返回 String token,无 expireTime/uploadUrl。DTO 中这两个字段设为可空(null)。

8. **FormStatusResponse**: `parseProgress`/`applicationCount`/`errorMsg` 在当前聚合根中不可得(需查询解析流水),本次返回 0/null,字段保留供后续扩展。

## Step 1: 编写 BusinessFormApi 接口与 DTO

### BusinessFormApi.java
```java
package com.example.core.api.form;

import com.example.core.api.form.command.ApplyUploadTokenCommand;
import com.example.core.api.form.command.ConfirmUploadCommand;
import com.example.core.api.form.command.DeleteFormCommand;
import com.example.core.api.form.query.GetFormStatusQuery;
import com.example.core.api.form.response.FormStatusResponse;
import com.example.core.api.form.response.UploadTokenResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 业务表单管理 API
 *
 * <p>提供表单的上传 token 申请、上传确认、删除、状态查询等公共接口。
 * 路径前缀 {@code /core/form}。
 *
 * <p>后续新增接口流程:
 * <ol>
 *   <li>在 API 层新增方法到本接口</li>
 *   <li>在 application 层扩展 AppService 方法</li>
 *   <li>在 adapter 层实现 Controller,通过 FormConverter 完成 DTO 转换</li>
 * </ol>
 *
 * @author panoshu
 */
@HttpExchange("/core/form")
public interface BusinessFormApi {

    /**
     * 申请文件上传临时凭证。
     */
    @PostExchange("/upload-token")
    ApiResult<UploadTokenResponse> applyUploadToken(@Valid @RequestBody ApplyUploadTokenCommand command);

    /**
     * 确认文件已上传(前端直传文件服务后回调)。
     */
    @PostExchange("/confirm-upload")
    ApiResult<Void> confirmUpload(@Valid @RequestBody ConfirmUploadCommand command);

    /**
     * 删除已上传的表单。
     */
    @PostExchange("/delete")
    ApiResult<Void> delete(@Valid @RequestBody DeleteFormCommand command);

    /**
     * 查询表单状态。
     */
    @PostExchange("/status")
    ApiResult<FormStatusResponse> status(@Valid @RequestBody GetFormStatusQuery query);
}
```

### ApplyUploadTokenCommand.java
```java
package com.example.core.api.form.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 申请上传 token 命令
 *
 * @author panoshu
 */
public record ApplyUploadTokenCommand(
    @NotBlank(message = "批次ID不能为空") String batchId,
    @NotBlank(message = "文件名不能为空") String fileName,
    @NotNull(message = "文件大小不能为空") @Positive(message = "文件大小必须为正数") Long fileSize,
    @NotBlank(message = "文件类型不能为空") String contentType
) {
}
```

### ConfirmUploadCommand.java
```java
package com.example.core.api.form.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 确认上传命令
 *
 * <p>前端直传文件服务后,携带文件服务返回的 fileId 回调本接口。
 *
 * @author panoshu
 */
public record ConfirmUploadCommand(
    @NotBlank(message = "批次ID不能为空") String batchId,
    @NotBlank(message = "表单ID不能为空") String formId,
    @NotBlank(message = "文件ID不能为空") String fileId,
    @NotBlank(message = "文件名不能为空") String fileName,
    String fileMd5
) {
}
```

### DeleteFormCommand.java
```java
package com.example.core.api.form.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 删除表单命令
 *
 * @author panoshu
 */
public record DeleteFormCommand(
    @NotBlank(message = "批次ID不能为空") String batchId,
    @NotBlank(message = "表单ID不能为空") String formId
) {
}
```

### GetFormStatusQuery.java
```java
package com.example.core.api.form.query;

import jakarta.validation.constraints.NotBlank;

/**
 * 查询表单状态
 *
 * @author panoshu
 */
public record GetFormStatusQuery(
    @NotBlank(message = "表单ID不能为空") String formId
) {
}
```

### UploadTokenResponse.java
```java
package com.example.core.api.form.response;

import java.time.LocalDateTime;

/**
 * 上传 token 响应
 *
 * @author panoshu
 */
public record UploadTokenResponse(
    String token,
    LocalDateTime expireTime,
    String uploadUrl
) {
}
```

### FormStatusResponse.java
```java
package com.example.core.api.form.response;

/**
 * 表单状态响应
 *
 * @author panoshu
 */
public record FormStatusResponse(
    String formId,
    String status,
    int parseProgress,
    int applicationCount,
    String errorMsg
) {
}
```

## Step 2: 扩展 BusinessForm 聚合根

文件: `business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/business/aggregate/root/BusinessForm.java`

在现有类中新增以下内容(保留所有已有方法不变):

```java
  /**
   * 行为:标记表单为已删除
   *
   * <p>只有 UPLOADED/PARSED 状态的表单才能删除。
   *
   * @throws DomainException 当表单状态不允许删除时
   */
  public void markAsDeleted() {
    if (this.formStatus == FormStatus.DELETED) {
      return;
    }
    if (this.formStatus != FormStatus.UPLOADED
        && this.formStatus != FormStatus.PARSED
        && this.formStatus != FormStatus.WAITING_UPLOAD) {
      throw new DomainException(CoreDomainErrorCode.INVALID_STATUS)
        .withLogDetail("只有已上传/已解析/待上传的表单才能删除, FormId: %s, status: %s"
          .formatted(this.id().value(), this.formStatus));
    }
    this.formStatus = FormStatus.DELETED;
  }

  /**
   * 获取表单状态
   */
  public FormStatus getFormStatus() {
    return this.formStatus;
  }
```

**注意**:
- 需要新增 import: `com.example.core.domain.business.errorcode.CoreDomainErrorCode` 和 `com.example.shared.exception.DomainException`(如未导入)
- `FormStatus` 已导入
- 保留所有已有方法不变

## Step 3: 扩展 BusinessFormAppService

文件: `business-core-kernel/business-core-application/src/main/java/com/example/core/application/engine/step/service/BusinessFormAppService.java`

在现有类中新增以下方法(保留已有方法不变):

```java
  /**
   * 申请文件上传临时凭证。
   *
   * <p>调用底层文件集成网关,获取直传 token,前端使用该 token 直接上传文件到文件服务。
   *
   * @param clientIp 客户端 IP
   * @param userId 用户 ID
   * @param fileSize 文件大小(字节)
   * @return 上传 token 字符串
   */
  public String applyUploadToken(String clientIp, String userId, long fileSize) {
    String token = fileIntegrationGateway.applyUploadToken(clientIp, userId, fileSize);
    log.info("申请上传 token 成功: userId={}, fileSize={}", userId, fileSize);
    return token;
  }

  /**
   * 删除表单。
   *
   * @param formId 表单 ID
   */
  @Transactional
  public void deleteForm(FormId formId) {
    BusinessForm form = formRepository.loadOrThrow(formId);
    form.markAsDeleted();
    formRepository.save(form);
    form.getDomainEvents().forEach(eventBus::publish);
    form.clearDomainEvents();
    log.info("删除表单: formId={}", formId.value());
  }

  /**
   * 查询表单状态。
   *
   * @param formId 表单 ID
   * @return 表单聚合根
   */
  @Transactional(readOnly = true)
  public BusinessForm getFormStatus(FormId formId) {
    return formRepository.loadOrThrow(formId);
  }
```

**注意**:
- `fileIntegrationGateway` 已是现有类的依赖,直接使用
- `formRepository` 已是现有类的依赖
- `eventBus` 已是现有类的依赖
- 保留所有已有方法不变

## Step 4: 编写 FormConverter

文件: `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/form/converter/FormConverter.java`

```java
package com.example.core.adapter.form.converter;

import com.example.core.api.form.response.FormStatusResponse;
import com.example.core.api.form.response.UploadTokenResponse;
import com.example.core.domain.business.aggregate.root.BusinessForm;
import org.mapstruct.Mapper;

/**
 * 表单 DTO 转换器
 *
 * <p>通过 MapStruct 完成聚合根到响应 DTO 的转换。
 * 使用 default 方法,因 {@link BusinessForm} 继承泛型基类,
 * MapStruct @Mapping 无法解析继承的访问器(同 BatchConverter 模式)。
 *
 * @author panoshu
 */
@Mapper(componentModel = "spring")
public interface FormConverter {

    /**
     * 上传 token 字符串 → 响应 DTO
     */
    default UploadTokenResponse toUploadTokenResponse(String token) {
        if (token == null) {
            return null;
        }
        return new UploadTokenResponse(token, null, null);
    }

    /**
     * 表单聚合根 → 状态响应 DTO
     */
    default FormStatusResponse toStatusResponse(BusinessForm form) {
        if (form == null) {
            return null;
        }
        return new FormStatusResponse(
            form.id().value(),
            form.getFormStatus() != null ? form.getFormStatus().name() : null,
            0,
            0,
            null
        );
    }
}
```

## Step 5: 编写 BusinessFormController

文件: `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/form/BusinessFormController.java`

```java
package com.example.core.adapter.form;

import com.example.core.adapter.context.SessionContextResolver;
import com.example.core.adapter.form.converter.FormConverter;
import com.example.core.adapter.security.RequireBusinessPermission;
import com.example.core.api.context.SessionContext;
import com.example.core.api.form.BusinessFormApi;
import com.example.core.api.form.command.ApplyUploadTokenCommand;
import com.example.core.api.form.command.ConfirmUploadCommand;
import com.example.core.api.form.command.DeleteFormCommand;
import com.example.core.api.form.query.GetFormStatusQuery;
import com.example.core.api.form.response.FormStatusResponse;
import com.example.core.api.form.response.UploadTokenResponse;
import com.example.core.application.engine.step.service.BusinessFormAppService;
import com.example.core.domain.business.aggregate.root.BusinessForm;
import com.example.core.domain.business.aggregate.valueobject.BusinessFile;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.FormId;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 业务表单管理 Controller
 *
 * <p>实现 {@link BusinessFormApi},入口完成会话解析与功能权限校验,
 * 调用 {@link BusinessFormAppService} 进行表单处理。
 *
 * <p>后续新增 Controller 方法流程:
 * <ol>
 *   <li>在 API 层接口新增方法签名</li>
 *   <li>在本类实现方法,标注 @RequireBusinessPermission(功能权限码)</li>
 *   <li>通过 FormConverter 完成 DTO 转换</li>
 * </ol>
 *
 * @author panoshu
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class BusinessFormController implements BusinessFormApi {

    private final BusinessFormAppService formAppService;
    private final FormConverter converter;
    private final SessionContextResolver sessionResolver;

    @Override
    @RequireBusinessPermission("FORM_UPLOAD")
    public ApiResult<UploadTokenResponse> applyUploadToken(@Valid @RequestBody ApplyUploadTokenCommand command) {
        SessionContext session = sessionResolver.require();
        log.info("申请上传 token: batchId={}, fileName={}, userNo={}",
            command.batchId(), command.fileName(), session.userNo());

        String token = formAppService.applyUploadToken(
            session.clientIp(),
            session.userNo(),
            command.fileSize()
        );
        return ApiResult.success(converter.toUploadTokenResponse(token));
    }

    @Override
    @RequireBusinessPermission("FORM_UPLOAD")
    public ApiResult<Void> confirmUpload(@Valid @RequestBody ConfirmUploadCommand command) {
        SessionContext session = sessionResolver.require();
        log.info("确认上传: batchId={}, formId={}, fileId={}, userNo={}",
            command.batchId(), command.formId(), command.fileId(), session.userNo());

        BusinessFile uploadedFile = new BusinessFile(
            new FileId(command.fileId()),
            command.fileName(),
            extractExtension(command.fileName()),
            null
        );
        formAppService.confirmUpload(new FormId(command.formId()), uploadedFile);
        return ApiResult.success();
    }

    @Override
    @RequireBusinessPermission("FORM_DELETE")
    public ApiResult<Void> delete(@Valid @RequestBody DeleteFormCommand command) {
        SessionContext session = sessionResolver.require();
        log.info("删除表单: batchId={}, formId={}, userNo={}",
            command.batchId(), command.formId(), session.userNo());

        formAppService.deleteForm(new FormId(command.formId()));
        return ApiResult.success();
    }

    @Override
    public ApiResult<FormStatusResponse> status(@Valid @RequestBody GetFormStatusQuery query) {
        SessionContext session = sessionResolver.require();
        log.info("查询表单状态: formId={}, userNo={}", query.formId(), session.userNo());

        BusinessForm form = formAppService.getFormStatus(new FormId(query.formId()));
        return ApiResult.success(converter.toStatusResponse(form));
    }

    /**
     * 从文件名提取扩展名(不含点号)。
     */
    private String extractExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }
}
```

## Step 6: 编写 BusinessFormAppService 单元测试

文件: `business-core-kernel/business-core-application/src/test/java/com/example/core/application/engine/step/service/BusinessFormAppServiceTest.java`

```java
package com.example.core.application.engine.step.service;

import com.example.core.domain.business.aggregate.root.BusinessForm;
import com.example.core.domain.business.aggregate.valueobject.BusinessFile;
import com.example.core.domain.business.aggregate.valueobject.enums.status.FormStatus;
import com.example.core.domain.engine.gateway.BusinessConfigGateway;
import com.example.core.domain.engine.gateway.FileIntegrationGateway;
import com.example.core.domain.business.repository.ApplicationRepository;
import com.example.core.domain.business.repository.FormRepository;
import com.example.shared.domain.event.EventBus;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.FormId;
import com.example.shared.primitives.identity.IdService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * BusinessFormAppService 单元测试
 *
 * <p>覆盖 Task 6 新增的 applyUploadToken / deleteForm / getFormStatus 方法。
 * confirmUpload / triggerParsing / handleParsingResult 已有方法不在本测试范围。
 *
 * @author panoshu
 */
class BusinessFormAppServiceTest {

    private FormRepository formRepository;
    private ApplicationRepository applicationRepository;
    private BusinessConfigGateway configGateway;
    private FileIntegrationGateway fileIntegrationGateway;
    private EventBus eventBus;
    private IdService idService;
    private BusinessFormAppService appService;

    @BeforeEach
    void setUp() {
        formRepository = mock(FormRepository.class);
        applicationRepository = mock(ApplicationRepository.class);
        configGateway = mock(BusinessConfigGateway.class);
        fileIntegrationGateway = mock(FileIntegrationGateway.class);
        eventBus = mock(EventBus.class);
        idService = mock(IdService.class);
        appService = new BusinessFormAppService(
            formRepository, applicationRepository, configGateway,
            fileIntegrationGateway, eventBus, idService
        );
    }

    @Test
    void should_apply_upload_token_via_gateway() {
        String clientIp = "127.0.0.1";
        String userId = "U001";
        long fileSize = 1024L;
        when(fileIntegrationGateway.applyUploadToken(clientIp, userId, fileSize))
            .thenReturn("token-abc-123");

        String token = appService.applyUploadToken(clientIp, userId, fileSize);

        assertThat(token).isEqualTo("token-abc-123");
        verify(fileIntegrationGateway).applyUploadToken(clientIp, userId, fileSize);
    }

    @Test
    void should_delete_form_and_save() {
        FormId formId = new FormId("FORM001");
        BusinessForm form = mock(BusinessForm.class);
        when(formRepository.loadOrThrow(formId)).thenReturn(form);
        when(form.getDomainEvents()).thenReturn(java.util.List.of());

        appService.deleteForm(formId);

        verify(form).markAsDeleted();
        verify(formRepository).save(form);
    }

    @Test
    void should_get_form_status() {
        FormId formId = new FormId("FORM001");
        BusinessForm form = mock(BusinessForm.class);
        when(formRepository.loadOrThrow(formId)).thenReturn(form);

        BusinessForm result = appService.getFormStatus(formId);

        assertThat(result).isSameAs(form);
        verify(formRepository).loadOrThrow(formId);
    }
}
```

## Step 7: 编译验证

Run: `mvn compile -pl business-core-kernel/business-core-adapter -am -q`
Expected: BUILD SUCCESS

## Step 8: 运行测试

Run: `mvn test -pl business-core-kernel/business-core-application -am -Dtest=BusinessFormAppServiceTest "-Dsurefire.failIfNoSpecifiedTests=false" -q`
Expected: PASS (3 tests)

## Step 9: 提交

```bash
git add business-core-kernel/business-core-api/src/main/java/com/example/core/api/form/ \
        business-core-kernel/business-core-domain/src/main/java/com/example/core/domain/business/aggregate/root/BusinessForm.java \
        business-core-kernel/business-core-application/src/main/java/com/example/core/application/engine/step/service/BusinessFormAppService.java \
        business-core-kernel/business-core-application/src/test/java/com/example/core/application/engine/step/service/BusinessFormAppServiceTest.java \
        business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/form/
git commit -m "feat(core-adapter): 实现 BusinessFormApi 接口与 Controller" -m "1. BusinessFormApi 定义上传token/确认上传/删除/状态查询 4 个公共接口" -m "2. BusinessFormController 入口完成会话解析与功能权限校验" -m "3. BusinessFormAppService 新增 applyUploadToken/deleteForm/getFormStatus 方法" -m "4. BusinessForm 新增 markAsDeleted 行为与 getFormStatus getter" -m "5. FormConverter 使用 default 方法转换(同 BatchConverter 模式)" -m "6. DTO 使用 String 类型 ID 与领域 FormId/BatchId 一致"
```
