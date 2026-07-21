package com.example.core.infrastructure.gateway;

import com.example.core.domain.aggregate.root.BusinessForm;
import com.example.core.domain.aggregate.valueobject.BusinessFile;
import com.example.core.domain.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.gateway.FileIntegrationGateway;
import com.example.file.api.FileAccessApi;
import com.example.file.api.FileTaskApi;
import com.example.file.api.request.ApplyUploadTokenRequest;
import com.example.file.api.request.UploadFileRequest;
import com.example.file.api.response.ApplyUploadTokenResponse;
import com.example.file.api.response.FileTaskIdResponse;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.FormId;
import com.example.shared.primitives.identity.UserNo;
import com.example.shared.web.core.api.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * FileIntegrationGateway 默认实现：调用 file-service 完成文件集成
 * <p>
 * 本实现属于核心编排域 (kernel) 的基础设施层，通过 {@link FileTaskApi} 和 {@link FileAccessApi}
 * 这两个 @HttpExchange 接口向 file-service 发起远程调用。
 *
 * <b>【演示环境约束】</b>
 * <p>{@link #downloadStream(FileId)} 在演示环境不支持流式下载，会抛出 {@link UnsupportedOperationException}。
 * 生产环境应替换为基于 token 的流式下载实现。
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/14 23:34
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileServiceIntegrationGateway implements FileIntegrationGateway {

  private static final String BIZ_TYPE_FORM_DETAIL = "FORM_DETAIL";
  private static final String BIZ_TYPE_MATERIAL = "MATERIAL";
  private static final String SOURCE_APP = "business-core";
  private static final long DEFAULT_TTL_MINUTES = 30L;
  private static final long TOKEN_EXPIRE_DAYS = 7L;
  private static final List<String> DEFAULT_ALLOWED_CONTENT_TYPES =
    List.of("application/pdf", "image/*");

  private final FileAccessApi fileAccessApi;
  private final FileTaskApi fileTaskApi;

  @Override
  public void triggerAsyncParsing(BusinessForm businessForm, BusinessMetaContext businessMetaContext) {
    BusinessFile formFile = businessForm.getFormFile();
    if (formFile == null) {
      log.warn("表单尚未上传文件，跳过异步解析, formId={}", businessForm.id());
      return;
    }
    UploadFileRequest request = new UploadFileRequest(
      BIZ_TYPE_FORM_DETAIL,
      resolveTemplateCode(businessMetaContext),
      formFile.fileName(),
      formFile.fileSizeBytes() != null ? formFile.fileSizeBytes() : 0L,
      resolveUploader(businessForm),
      businessForm.id().value()
    );
    invokeUpload(request, businessForm.id().value());
  }

  @Override
  public void triggerAsyncParsing(FormId formId, FileId sourceFileId, String parseTemplateId,
                                   Map<String, Object> splitRules) {
    UploadFileRequest request = new UploadFileRequest(
      BIZ_TYPE_FORM_DETAIL,
      parseTemplateId,
      resolveFileName(sourceFileId),
      0L,
      "system",
      formId.value()
    );
    invokeUpload(request, formId.value());
  }

  @Override
  public InputStream downloadStream(FileId fileId) {
    throw new UnsupportedOperationException("演示环境不支持文件流下载");
  }

  @Override
  public String applyUploadToken(String clientIp, String userId, long maxSize) {
    ApplyUploadTokenRequest request = new ApplyUploadTokenRequest(
      BIZ_TYPE_MATERIAL,
      SOURCE_APP,
      null,
      null,
      null,
      UserNo.of(userId),
      LocalDateTime.now().plusDays(TOKEN_EXPIRE_DAYS),
      DEFAULT_ALLOWED_CONTENT_TYPES,
      maxSize,
      Duration.ofMinutes(DEFAULT_TTL_MINUTES)
    );
    ApplyUploadTokenResponse response = fileAccessApi.applyUploadToken(request);
    return response != null ? response.token() : null;
  }

  /**
   * 调用 file-service 派发上传/解析任务，记录日志
   */
  private void invokeUpload(UploadFileRequest request, String businessRef) {
    ApiResult<FileTaskIdResponse> result = fileTaskApi.upload(request);
    if (result == null || !result.isSuccess() || result.data() == null) {
      log.error("file-service 派发解析任务失败, businessRef={}, result={}", businessRef, result);
      return;
    }
    log.info("已派发文件解析任务, businessRef={}, fileTaskId={}", businessRef, result.data().fileTaskId());
  }

  private String resolveTemplateCode(BusinessMetaContext context) {
    return context.businessType() != null ? context.businessType().name() + "_TEMPLATE" : "DEFAULT_TEMPLATE";
  }

  private String resolveUploader(BusinessForm businessForm) {
    return businessForm.getOperatorInfo() != null
      ? businessForm.getOperatorInfo().operatorId().value()
      : "system";
  }

  private String resolveFileName(FileId fileId) {
    return fileId != null ? fileId.value() + ".json" : "form-detail.json";
  }
}
