package com.example.file.infrastructure.service;

import com.example.file.infrastructure.engine.StorageEngine;
import com.example.file.infrastructure.entity.FileRecord;
import com.example.file.infrastructure.manager.FileRecordManager;
import com.example.shared.file.types.constant.FileStatus;
import com.example.shared.file.types.dto.FileMetaResp;
import com.example.shared.file.types.dto.PresignUploadResp;
import com.example.shared.primitives.identity.IdService;
import com.example.shared.primitives.identity.IdType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

  private final StorageEngine storageEngine;
  private final FileRecordManager fileRecordManager;
  private final IdService idService;

  /**
   * 处理 MultipartFile 上传 (Controller 入口)
   */
  public FileMetaResp upload(MultipartFile file, String bizType, String ownerId) {
    try {
      return uploadStream(
        file.getInputStream(),
        file.getOriginalFilename(),
        file.getSize(),
        file.getContentType(),
        bizType,
        ownerId
      );
    } catch (IOException e) {
      throw new RuntimeException("Failed to read file stream", e);
    }
  }

  /**
   * 核心流式上传逻辑
   * 方案 A：代理流式上传 (网关 -> FileService -> OSS)
   */
  public FileMetaResp uploadStream(InputStream stream, String originalName, long size, String contentType, String bizType, String ownerId) {
    String fileId = idService.nextId(IdType.ULID);
    String storageKey = generateStorageKey(originalName, bizType);

    // 阶段 1: 预写 DB (Manager 的内部开启了新事务)
    FileRecord record = fileRecordManager.createUploadingRecord(
      fileId, originalName, size, contentType, bizType, ownerId, storageKey, storageEngine.getType()
    );

    // 阶段 2: 耗时的 OSS 网络 I/O (此时完全释放了 DB 连接池！)
    try {
      storageEngine.store(storageKey, stream, size, contentType);
    } catch (Exception e) {
      log.error("Failed to upload file to OSS. FileId: {}", fileId, e);
      fileRecordManager.updateStatus(fileId, FileStatus.UPLOAD_FAILED);
      throw new RuntimeException("Storage engine upload failed", e);
    }

    // 阶段 3: 更新成功状态
    fileRecordManager.updateStatus(fileId, FileStatus.TEMP);
    record.setStatus(FileStatus.TEMP);

    return convertToMetaResp(record);
  }

  public PresignUploadResp createPresignedUpload(String originalName, String bizType, String ownerId) {
    String fileId = idService.nextId(IdType.ULID);
    String storageKey = generateStorageKey(originalName, bizType);

    // 阶段 1: 预写 DB
    fileRecordManager.createUploadingRecord(
      fileId, originalName, 0, "application/octet-stream", bizType, ownerId, storageKey, storageEngine.getType()
    );

    // 阶段 2: 获取 OSS 上传 URL (有效期 1 小时)
    String putUrl = storageEngine.getPresignedPutUrl(storageKey, 3600);

    return new PresignUploadResp(fileId, putUrl, Collections.emptyMap());
  }

  public void confirmFile(String fileId) {
    fileRecordManager.confirmFile(fileId);
  }

  public InputStream getFileStream(String fileId) {
    FileRecord record = getFileRecord(fileId);
    return storageEngine.openStream(record.getStorageKey());
  }

  public FileMetaResp getMeta(String fileId) {
    return convertToMetaResp(getFileRecord(fileId));
  }


  private FileRecord getFileRecord(String fileId) {
    return fileRecordManager.findById(fileId)
      .orElseThrow(() -> new RuntimeException("File not found: " + fileId));
  }

  private String generateStorageKey(String originalName, String bizType) {
    String ext = StringUtils.getFilenameExtension(originalName);
    String safeExt = StringUtils.hasText(ext) ? ext : "dat";
    String uniqueFileName = idService.nextId(IdType.ULID);

    return String.format("%s/%s/%s.%s",
      bizType,
      LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE),
      uniqueFileName,
      safeExt
    );
  }

  private FileMetaResp convertToMetaResp(FileRecord r) {
    String accessUrl = storageEngine.getAccessUrl(r.getStorageKey(), 3600);
    return new FileMetaResp(
      r.getId(),
      r.getOriginalName(),
      r.getMimeType(),
      r.getSize(),
      accessUrl,
      r.getBizType(),
      r.getStatus().name()
    );
  }
}
