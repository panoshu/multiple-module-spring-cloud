package com.example.file.infrastructure.engine.local;

import com.example.file.infrastructure.engine.StorageEngine;
import com.example.file.infrastructure.errorcode.FileErrorCode;
import com.example.shared.exception.SystemException;
import com.example.shared.file.types.constant.StorageType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Slf4j
@Component
public class LocalStorageEngine implements StorageEngine {

  @Value("${shared.file.local.base-path:/data/files}")
  private String basePath;

  @Value("${shared.file.service-url:http://localhost:8080}")
  private String serviceUrl;

  @Override
  public StorageType getType() {
    return StorageType.LOCAL;
  }

  @Override
  public void store(String path, InputStream stream, long size, String contentType) {
    try {
      Path targetPath = Paths.get(basePath, path);
      Files.createDirectories(targetPath.getParent()); // 确保目录存在
      Files.copy(stream, targetPath, StandardCopyOption.REPLACE_EXISTING);
      log.info("File stored locally: {}", targetPath);
    } catch (IOException e) {
      throw new SystemException(FileErrorCode.FILE_IO_ERROR, e)
        .withLogDetail("Failed to store file locally");
    }
  }

  @Override
  public InputStream openStream(String path) {
    try {
      return Files.newInputStream(Paths.get(basePath, path));
    } catch (IOException e) {
      throw new SystemException(FileErrorCode.FILE_NOT_FOUND, e)
        .withLogDetail("File not found locally: %s".formatted(path));
    }
  }

  @Override
  public String getAccessUrl(String path, int expireSeconds) {
    // Local 模式下，返回文件服务的代理下载接口
    // 例如: http://file-service/api/v1/files/proxy/{path}
    // 这里只是示意，实际需要配合 Controller 的下载接口
    return "%s/api/v1/files/stream/by-path?path=%s".formatted(serviceUrl, path);
  }

  @Override
  public void delete(String path) {
    try {
      Files.deleteIfExists(Paths.get(basePath, path));
    } catch (IOException e) {
      log.warn("Failed to delete local file: {}", path);
    }
  }

  @Override
  public String getPresignedPutUrl(String path, int expireSeconds) {
    return "";
  }
}
