package com.example.file.adapter.controller;

import com.example.file.infrastructure.service.FileService;
import com.example.file.infrastructure.service.FileTokenService;
import com.example.shared.file.api.FileApi;
import com.example.shared.file.types.dto.*;
import com.example.shared.web.util.ClientIpUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequiredArgsConstructor
public class FileController implements FileApi {

  private final FileService fileService;
  private final FileTokenService tokenService;
  private final HttpServletRequest request;

  @Override
  @SneakyThrows
  public FileMetaResp proxyUpload(MultipartFile file, String bizType, String ownerId) {
    // MultipartFile.getInputStream() 是关键，实现了流式处理
    return fileService.uploadStream(
      file.getInputStream(),
      file.getOriginalFilename(),
      file.getSize(),
      file.getContentType(),
      bizType,
      ownerId
    );
  }

  @Override
  public void confirmFile(ConfirmUploadReq req) {
    fileService.confirmFile(req.fileId());
  }

  @Override
  public ResponseEntity<Resource> downloadStream(String fileId) {
    FileMetaResp meta = fileService.getMeta(fileId);

    return ResponseEntity.ok()
      .contentType(MediaType.APPLICATION_OCTET_STREAM)
      .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + meta.originalName())
      .body(new InputStreamResource(fileService.getFileStream(fileId)));
  }

  @Override
  public PresignUploadResp createPresignedUpload(PresignUploadReq req) {
    // 暂未实现完整 Presigned 逻辑，预留
    throw new UnsupportedOperationException("Presign not supported yet");
  }

  @Override
  public FileMetaResp getMeta(String fileId) {
    return fileService.getMeta(fileId);
  }

  // 1. 生成链接 (内部服务调用)
  @Override
  public FileUrlResp generatePresignedUrl(@RequestBody FileUrlReq req) {
    return tokenService.generatePresignedUrl(req);
  }

  // 2. 安全下载 (前端调用)
  @Override
  public ResponseEntity<Resource> downloadSecure(String fileId, String token) {
    // A. 校验 Token
    tokenService.verifyToken(token, fileId, FileUrlReq.FileAction.DOWNLOAD, ClientIpUtils.getRemoteIp(request));

    // B. 复用 Service 逻辑获取流
    // FileService 需要有 getFileStream(String fileId) 和 getMeta(String fileId) 方法
    InputStream inputStream = fileService.getFileStream(fileId);
    FileMetaResp meta = fileService.getMeta(fileId);

    // C. 返回响应
    return ResponseEntity.ok()
      .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + meta.originalName())
      .contentType(MediaType.parseMediaType(meta.mimeType()))
      .body(new InputStreamResource(inputStream));
  }

  // 3. 安全上传 (前端调用)
  @Override
  public FileMetaResp uploadSecure(MultipartFile file, String token) {
    // A. 校验 Token (上传时 Token 里 fileId 可能为空，传 null 忽略校验)
    FileTokenService.TokenPayload payload = tokenService.verifyToken(token, null, FileUrlReq.FileAction.UPLOAD, ClientIpUtils.getRemoteIp(request));

    // B. 转换并调用 Service
    try {
      // 调用 FileService.uploadStream
      // 完整参数: (InputStream, String originalName, long size, String contentType, String bizType, String ownerId)
      return fileService.uploadStream(
        file.getInputStream(),      // 流
        file.getOriginalFilename(), // 原文件名
        file.getSize(),             // 大小
        file.getContentType(),      // 类型
        payload.bizType(),          // 从 Token 中获取业务类型，确保篡改无效
        payload.userId()            // 从 Token 中获取用户ID
      );
    } catch (IOException e) {
      throw new RuntimeException("Failed to read upload stream", e);
    }
  }
}
