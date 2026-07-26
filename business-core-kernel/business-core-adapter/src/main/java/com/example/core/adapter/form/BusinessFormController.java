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
