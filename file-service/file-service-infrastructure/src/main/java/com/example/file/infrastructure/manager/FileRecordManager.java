package com.example.file.infrastructure.manager;

import com.example.file.infrastructure.entity.FileRecord;
import com.example.file.infrastructure.repository.FileRecordRepository;
import com.example.shared.file.types.constant.FileStatus;
import com.example.shared.file.types.constant.StorageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileRecordManager {

  private final FileRecordRepository fileRepository;

  /**
   * 创建上传中的初始化记录 (开启独立的新事务，保证立刻落库)
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public FileRecord createUploadingRecord(String fileId, String originalName, long size, String contentType, String bizType, String ownerId, String storageKey, StorageType storageType) {
    FileRecord record = new FileRecord();
    record.setId(fileId);
    record.setOriginalName(originalName);
    record.setSize(size);
    record.setMimeType(contentType);
    record.setStorageKey(storageKey);
    record.setStorageType(storageType);
    record.setBucket("default");
    record.setStatus(FileStatus.UPLOADING); // 初始为 UPLOADING
    record.setBizType(bizType);
    record.setOwnerId(ownerId);

    fileRepository.save(record);
    return record;
  }

  /**
   * 更新状态
   */
  @Transactional
  public void updateStatus(String fileId, FileStatus newStatus) {
    fileRepository.findById(fileId).ifPresent(record -> {
      record.setStatus(newStatus);
      fileRepository.update(record);
    });
  }

  /**
   * 确认文件为永久
   */
  @Transactional
  public void confirmFile(String fileId) {
    fileRepository.findById(fileId).ifPresent(record -> {
      record.setStatus(FileStatus.PERSISTENT);
      fileRepository.update(record);
    });
  }

  public Optional<FileRecord> findById(String fileId) {
    return fileRepository.findById(fileId);
  }
}
