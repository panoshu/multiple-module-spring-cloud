package com.example.annuity.adapter.controller.kernel;

import com.example.shared.security.annotation.RequirePermission;
import com.example.core.adapter.context.SessionContextResolver;
import com.example.core.adapter.form.converter.FormConverter;
import com.example.shared.security.SessionClaims;
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
import com.example.file.types.FileId;
import com.example.core.types.FormId;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 年金服务 - 表单管理 Controller（服务级路径前缀版）
 *
 * <p>实现 {@link BusinessFormApi}（纯契约接口），路径前缀为 {@code /annuity/core/form}。
 *
 * @author panoshu
 */
@Slf4j
@RestController
@RequestMapping("/annuity/core/form")
@RequiredArgsConstructor
public class AnnuityKernelFormController implements BusinessFormApi {

  private final BusinessFormAppService formAppService;
  private final FormConverter converter;
  private final SessionContextResolver sessionResolver;

  @Override
  @PostMapping("/upload-token")
  @RequirePermission(business = "FORM", action = "UPLOAD")
  public ApiResult<UploadTokenResponse> applyUploadToken(@Valid @RequestBody ApplyUploadTokenCommand command) {
    SessionClaims session = sessionResolver.require();
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
  @PostMapping("/confirm-upload")
  @RequirePermission(business = "FORM", action = "UPLOAD")
  public ApiResult<Void> confirmUpload(@Valid @RequestBody ConfirmUploadCommand command) {
    SessionClaims session = sessionResolver.require();
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
  @PostMapping("/delete")
  @RequirePermission(business = "FORM", action = "DELETE")
  public ApiResult<Void> delete(@Valid @RequestBody DeleteFormCommand command) {
    SessionClaims session = sessionResolver.require();
    log.info("删除表单: batchId={}, formId={}, userNo={}",
      command.batchId(), command.formId(), session.userNo());

    formAppService.deleteForm(new FormId(command.formId()));
    return ApiResult.success();
  }

  @Override
  @PostMapping("/status")
  @RequirePermission(business = "FORM", action = "VIEW")
  public ApiResult<FormStatusResponse> status(@Valid @RequestBody GetFormStatusQuery query) {
    SessionClaims session = sessionResolver.require();
    log.info("查询表单状态: formId={}, userNo={}", query.formId(), session.userNo());

    BusinessForm form = formAppService.getFormStatus(new FormId(query.formId()));
    return ApiResult.success(converter.toStatusResponse(form));
  }

  private String extractExtension(String fileName) {
    if (fileName == null || !fileName.contains(".")) {
      return "";
    }
    return fileName.substring(fileName.lastIndexOf('.') + 1);
  }
}
