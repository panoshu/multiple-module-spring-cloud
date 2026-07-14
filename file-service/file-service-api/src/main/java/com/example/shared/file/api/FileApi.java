package com.example.shared.file.api;

import com.example.shared.file.types.dto.*;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/api/v1/files")
public interface FileApi {

  // 1. [核心] 代理上传 (流式) - 适用于小文件或内网策略
  // Consumes multipart/form-data
  @PostExchange(value = "/upload", contentType = "multipart/form-data")
  FileMetaResp proxyUpload(
    @RequestPart("file") MultipartFile file,
    @RequestParam("bizType") String bizType,
    @RequestParam(value = "ownerId", required = false) String ownerId
  );

  // 2. [核心] 申请预签名上传 (S3 POST Policy) - 适用于大文件直传
  @PostExchange("/presign/upload")
  PresignUploadResp createPresignedUpload(@RequestBody PresignUploadReq req);

  // 3. [核心] 确认文件 (临时转永久)
  @PostExchange("/confirm")
  void confirmFile(@RequestBody ConfirmUploadReq req);

  // 4. [核心] 内网流式下载 (供业务服务解析用)
  // 业务服务调用时，返回 Resource (InputStream)
  @GetExchange("/stream/{fileId}")
  ResponseEntity<Resource> downloadStream(@PathVariable("fileId") String fileId);

  // 5. 获取文件元数据
  @GetExchange("/{fileId}/meta")
  FileMetaResp getMeta(@PathVariable("fileId") String fileId);

  /**
   * [新增] 申请带签名的安全访问链接 (由服务端拼接好 gatewayUrl + token)
   */
  @PostExchange("/secure/presign-url")
  FileUrlResp generatePresignedUrl(@RequestBody FileUrlReq req);

  /**
   * [安全下载] 前端拿着 URL 访问此接口
   */
  @GetExchange("/secure/stream/{fileId}")
  ResponseEntity<Resource> downloadSecure(
    @PathVariable("fileId") String fileId,
    @RequestParam("token") String token
  );

  /**
   * [安全上传] 前端拿着 URL 访问此接口
   */
  @PostExchange(value = "/secure/upload", contentType = MediaType.MULTIPART_FORM_DATA_VALUE)
  FileMetaResp uploadSecure(
    @RequestPart("file") MultipartFile file,
    @RequestParam("token") String token
  );
}
