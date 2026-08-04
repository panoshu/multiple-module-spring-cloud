package com.example.file.adapter.access;

import com.example.file.adapter.access.converter.FileAccessConverter;
import com.example.file.api.FileAccessApi;
import com.example.file.api.request.ApplyDownloadTokenRequest;
import com.example.file.api.request.ApplyUploadTokenRequest;
import com.example.file.api.response.ApplyDownloadTokenResponse;
import com.example.file.api.response.ApplyUploadTokenResponse;
import com.example.file.api.response.UploadFileResponse;
import com.example.file.application.usecase.ApplyDownloadTokenUseCase;
import com.example.file.application.usecase.ApplyUploadTokenUseCase;
import com.example.file.application.usecase.ApplyUploadTokenUseCase.ApplyUploadTokenResult;
import com.example.file.application.usecase.DownloadFileWithTokenUseCase;
import com.example.file.application.usecase.DownloadFileWithTokenUseCase.DownloadContext;
import com.example.file.application.usecase.UploadFileWithTokenUseCase;
import com.example.file.application.util.TokenHashUtil;
import com.example.file.domain.errorcode.FileErrorCodes;
import com.example.file.domain.model.aggregate.valueobject.SessionUser;
import com.example.file.infrastructure.storage.FileTokenProperties;
import com.example.shared.exception.BusinessException;
import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.ProductNo;
import com.example.shared.identifier.id.UserNo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 文件访问 Token 适配器（{@link FileAccessApi} 实现）。
 *
 * <p>四个端点：
 * <ul>
 *   <li>POST /api/file/access/upload-tokens — 申请上传 token</li>
 *   <li>POST /api/file/access/download-tokens — 申请下载 token</li>
 *   <li>POST /api/file/access/upload — 使用 token 上传文件</li>
 *   <li>GET  /api/file/access/download — 使用 token 下载文件（流式响应）</li>
 * </ul>
 *
 * <p>设计要点：
 * <ul>
 *   <li>session 用户信息（X-User-No / X-Customer-No / X-Product-No）从 HTTP Header 提取，
 *       不在 {@link FileAccessApi} 接口中声明（接口仅声明 token + file，由适配层负责
 *       请求上下文装配）。使用 {@link RequestContextHolder} 在方法体内获取
 *       {@link HttpServletRequest}，保持接口方法签名不变。</li>
 *   <li>ttl 由 {@link FileTokenProperties} 提供默认值，通过 {@link FileAccessConverter}
 *       显式传入 Command（Task 15 fix: Command 必传 ttl）。</li>
 *   <li>download 采用两阶段：prepareDownload（事务内消费 token + 写审计）→
 *       openStream（事务外读取流）→ {@link StreamingResponseBody} 异步写出，
 *       避免长事务持有 IO 流。</li>
 *   <li>upload 返回的 digest 字段为 null：{@link UploadFileWithTokenUseCase#upload}
 *       当前仅返回 FileId，digest 需从 FileMetadata 单独查询（后续 follow-up 可扩展）。</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/file/access")
@RequiredArgsConstructor
public class FileAccessAdapter implements FileAccessApi {

  private final ApplyUploadTokenUseCase applyUploadTokenUseCase;
  private final UploadFileWithTokenUseCase uploadFileWithTokenUseCase;
  private final ApplyDownloadTokenUseCase applyDownloadTokenUseCase;
  private final DownloadFileWithTokenUseCase downloadFileWithTokenUseCase;
  private final FileAccessConverter converter;
  private final FileTokenProperties tokenProperties;

  /**
   * 计算 token 的 SHA-256 短摘要（前 8 位）用于日志脱敏。
   *
   * <p>复用 {@link TokenHashUtil#sha256} 避免重复实现 SHA-256 逻辑；
   * 截取前 8 位以满足日志脱敏的最小需要（M3 follow-up）。
   * 失败时返回 8 位占位串 {@code "sha256-e"}（来源于 {@code TokenHashUtil} 的
   * {@code "sha256-error"} 截断），仅用于日志，不影响主流程。
   */
  private static String sha256Short(String token) {
    return TokenHashUtil.sha256(token).substring(0, 8);
  }

  @Override
  public ApplyUploadTokenResponse applyUploadToken(ApplyUploadTokenRequest request) {
    log.info("申请上传 Token: bizType={}, sourceApp={}, uploader={}",
      request.bizType(), request.sourceApp(), request.uploader());
    var cmd = converter.toCommand(request, tokenProperties.getDefaultUploadTtl());
    ApplyUploadTokenResult result = applyUploadTokenUseCase.apply(cmd);
    return new ApplyUploadTokenResponse(result.token(), result.fileId());
  }

  @Override
  public ApplyDownloadTokenResponse applyDownloadToken(ApplyDownloadTokenRequest request) {
    log.info("申请下载 Token: fileId={}, downloader={}", request.fileId(), request.downloader());
    var cmd = converter.toCommand(request, tokenProperties.getDefaultDownloadTtl());
    String token = applyDownloadTokenUseCase.apply(cmd);
    return new ApplyDownloadTokenResponse(token);
  }

  @Override
  public UploadFileResponse upload(String token, MultipartFile file) {
    HttpServletRequest httpRequest = currentRequest();
    SessionUser session = extractSession(httpRequest);
    String clientIp = extractClientIp(httpRequest);
    log.info("使用 Token 上传文件: tokenHash={}, fileName={}, size={}",
      sha256Short(token), file.getOriginalFilename(), file.getSize());

    var fileId = uploadFileWithTokenUseCase.upload(token, session, file, clientIp);
    // digest 当前 UseCase 未返回，传 null；后续若需返回可通过 UploadResult 扩展
    return new UploadFileResponse(fileId, file.getOriginalFilename(), file.getSize(), null);
  }

  @Override
  public ResponseEntity<StreamingResponseBody> download(String token) {
    HttpServletRequest httpRequest = currentRequest();
    SessionUser session = extractSession(httpRequest);
    String clientIp = extractClientIp(httpRequest);
    log.info("使用 Token 下载文件: tokenHash={}", sha256Short(token));

    DownloadContext ctx = downloadFileWithTokenUseCase.prepareDownload(token, session, clientIp);
    // openStream 在 prepareDownload 事务提交后调用，避免长事务持有 IO 流
    InputStream stream = downloadFileWithTokenUseCase.openStream(ctx.fileId());

    StreamingResponseBody body = outputStream -> {
      try (InputStream in = stream) {
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
          outputStream.write(buffer, 0, bytesRead);
        }
        outputStream.flush();
      }
    };

    String fileName = ctx.originalName() != null ? ctx.originalName() : "download";
    String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
    String contentType = ctx.contentType() != null ? ctx.contentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
    long contentLength = ctx.size() != null ? ctx.size() : -1L;

    return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType(contentType))
      .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"")
      .contentLength(contentLength)
      .body(body);
  }

  /**
   * 从当前线程上下文获取 HttpServletRequest（@Override 方法签名不可变，无法直接注入）。
   */
  private HttpServletRequest currentRequest() {
    ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
    return attrs.getRequest();
  }

  /**
   * 从 HTTP Header 提取 SessionUser（X-User-No / X-Customer-No / X-Product-No）。
   *
   * <p>显式校验 header 存在性：{@link FileAccessApi} 接口仅声明 token + file，
   * Spring MVC 不会强制这些 header 存在。缺失时若直接调用 {@link UserNo#of} 等方法
   * 会抛 {@link IllegalArgumentException}，被 {@code GlobalExceptionHandler} 兜底为 500；
   * 此处提前校验，缺失时抛 {@link BusinessException}，由 {@code handleBaseException}
   * 返回业务错误码 + 明确错误信息（fix I1）。
   */
  private SessionUser extractSession(HttpServletRequest request) {
    String userNo = request.getHeader("X-User-No");
    String customerNo = request.getHeader("X-Customer-No");
    String productNo = request.getHeader("X-Product-No");
    if (userNo == null || userNo.isBlank()
      || customerNo == null || customerNo.isBlank()
      || productNo == null || productNo.isBlank()) {
      throw new BusinessException(FileErrorCodes.FILE_SESSION_HEADER_MISSING)
        .withUserDetail("X-User-No / X-Customer-No / X-Product-No Header 不能为空")
        .withLogDetail("缺失的 Header: X-User-No=" + (userNo == null || userNo.isBlank())
          + ", X-Customer-No=" + (customerNo == null || customerNo.isBlank())
          + ", X-Product-No=" + (productNo == null || productNo.isBlank()));
    }
    return new SessionUser(
      UserNo.of(userNo),
      CustomerNo.of(customerNo),
      ProductNo.of(productNo)
    );
  }

  /**
   * 提取客户端 IP（优先 X-Forwarded-For 首段，回退 remoteAddr）。
   */
  private String extractClientIp(HttpServletRequest request) {
    String xff = request.getHeader("X-Forwarded-For");
    if (xff != null && !xff.isBlank()) {
      return xff.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
