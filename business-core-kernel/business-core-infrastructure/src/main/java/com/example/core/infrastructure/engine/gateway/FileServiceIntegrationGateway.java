package com.example.core.infrastructure.engine.gateway;

import com.example.core.domain.business.aggregate.root.BusinessForm;
import com.example.core.domain.business.aggregate.valueobject.BusinessFile;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.gateway.FileIntegrationGateway;
import com.example.core.infrastructure.engine.errorcode.CoreInfraErrorCode;
import com.example.file.api.FileAccessApi;
import com.example.file.api.FileTaskApi;
import com.example.file.api.request.ApplyDownloadTokenRequest;
import com.example.file.api.request.ApplyUploadTokenRequest;
import com.example.file.api.request.UploadFileRequest;
import com.example.file.api.response.ApplyDownloadTokenResponse;
import com.example.file.api.response.ApplyUploadTokenResponse;
import com.example.file.api.response.FileTaskIdResponse;
import com.example.shared.exception.SystemException;
import com.example.shared.identifier.id.FileId;
import com.example.shared.identifier.id.FormId;
import com.example.shared.identifier.id.UserNo;
import com.example.shared.web.core.api.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * FileIntegrationGateway 默认实现：调用 file-service 完成文件集成
 * <p>
 * 本实现属于核心编排域 (kernel) 的基础设施层，通过 {@link FileTaskApi} 和 {@link FileAccessApi}
 * 这两个 @HttpExchange 接口向 file-service 发起远程调用。
 * <p>
 * {@link #downloadStream(FileId, BusinessMetaContext)} 采用两阶段令牌下载:
 * 先用业务上下文申请下载令牌,再以令牌换取流式响应,通过管道流将
 * {@link StreamingResponseBody} 桥接为 {@link InputStream} 供上游 Jackson 流式解析消费。
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
  private static final int STREAM_BUFFER_SIZE = 8192;
  private static final UserNo SYSTEM_USER = UserNo.of("SYSTEM");
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
  public InputStream downloadStream(FileId fileId, BusinessMetaContext context) {
    String token = applyDownloadToken(fileId, context);
    ResponseEntity<StreamingResponseBody> response = fileAccessApi.download(token);
    if (response == null || response.getBody() == null) {
      throw new SystemException(CoreInfraErrorCode.FILE_DOWNLOAD_FAILED)
        .withLogDetail("fileId=" + fileId + ", 下载响应体为空");
    }

    return bridgeToInputStream(response.getBody(), fileId);
  }

  /**
   * 申请下载令牌
   * <p>
   * 使用业务上下文中的客户/产品标识与系统账号作为下载人,向 file-service 申请下载令牌。
   *
   * @param fileId  目标文件 ID
   * @param context 业务上下文
   * @return 下载令牌
   */
  private String applyDownloadToken(FileId fileId, BusinessMetaContext context) {
    ApplyDownloadTokenRequest request = new ApplyDownloadTokenRequest(
      fileId,
      SOURCE_APP,
      context.customerNo(),
      context.productNo(),
      SYSTEM_USER,
      Duration.ofMinutes(DEFAULT_TTL_MINUTES)
    );
    ApplyDownloadTokenResponse response = fileAccessApi.applyDownloadToken(request);
    if (response == null || response.token() == null) {
      throw new SystemException(CoreInfraErrorCode.FILE_TOKEN_APPLY_FAILED)
        .withLogDetail("fileId=" + fileId);
    }
    return response.token();
  }

  /**
   * 将 {@link StreamingResponseBody} 桥接为 {@link InputStream}
   * <p>
   * {@link StreamingResponseBody#writeTo(java.io.OutputStream)} 向输出流写入数据,
   * 而上游消费方(Jackson 流式解析器)需要从输入流读取。通过 {@link PipedOutputStream} /
   * {@link PipedInputStream} 管道对连接两者,并在虚拟线程中执行写入,避免阻塞调用线程。
   *
   * @param body   文件服务返回的流式响应体
   * @param fileId 文件 ID(仅用于日志)
   * @return 可供读取的输入流
   */
  private InputStream bridgeToInputStream(StreamingResponseBody body, FileId fileId) {
    PipedOutputStream pipedOut = new PipedOutputStream();
    PipedInputStream pipedIn;
    try {
      pipedIn = new PipedInputStream(pipedOut, STREAM_BUFFER_SIZE);
    } catch (IOException e) {
      throw new SystemException(CoreInfraErrorCode.FILE_DOWNLOAD_FAILED, e)
        .withLogDetail("fileId=" + fileId + ", 创建管道流失败");
    }

    Thread.ofVirtual().name("file-download-" + fileId.value()).start(() -> {
      try {
        body.writeTo(pipedOut);
      } catch (Exception e) {
        log.error("流式下载写入管道失败, fileId={}", fileId, e);
      } finally {
        try {
          pipedOut.close();
        } catch (IOException ignored) {
          // 关闭异常无需传播,上游 PipedInputStream 会收到 EOF
        }
      }
    });

    return pipedIn;
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
