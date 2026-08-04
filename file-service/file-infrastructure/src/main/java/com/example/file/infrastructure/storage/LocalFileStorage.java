package com.example.file.infrastructure.storage;

import com.example.file.domain.errorcode.FileErrorCodes;
import com.example.file.domain.model.aggregate.valueobject.StorageTarget;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.example.shared.exception.SystemException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.security.MessageDigest;

@Slf4j
@Component
public class LocalFileStorage implements FileStorageBackend {

  @Override
  public StorageType supportedType() {
    return StorageType.LOCAL;
  }

  @Override
  public void store(StorageTarget target, String storageKey,
                    InputStream content, long contentLength) {
    Path fullPath = resolvePath(target, storageKey);
    try {
      Files.createDirectories(fullPath.getParent());
      try (OutputStream out = Files.newOutputStream(fullPath,
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
        content.transferTo(out);
      }
      log.debug("本地存储成功: path={}", fullPath);
    } catch (IOException e) {
      throw new SystemException(FileErrorCodes.FILE_STORAGE_FAILED, e)
        .withLogDetail("path=" + fullPath);
    }
  }

  @Override
  public InputStream open(StorageTarget target, String storageKey) {
    Path fullPath = resolvePath(target, storageKey);
    try {
      return Files.newInputStream(fullPath, StandardOpenOption.READ);
    } catch (IOException e) {
      throw new SystemException(FileErrorCodes.FILE_METADATA_NOT_FOUND, e)
        .withLogDetail("path=" + fullPath);
    }
  }

  @Override
  public boolean exists(StorageTarget target, String storageKey) {
    return Files.exists(resolvePath(target, storageKey));
  }

  @Override
  public void copy(StorageTarget target, String srcKey, String dstKey) {
    Path src = resolvePath(target, srcKey);
    Path dst = resolvePath(target, dstKey);
    try {
      Files.createDirectories(dst.getParent());
      Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new SystemException(FileErrorCodes.FILE_COPY_FAILED, e)
        .withLogDetail("src=" + src + ", dst=" + dst);
    }
  }

  @Override
  public String computeDigest(StorageTarget target, String storageKey) {
    Path fullPath = resolvePath(target, storageKey);
    try (InputStream in = Files.newInputStream(fullPath)) {
      MessageDigest sm3 = MessageDigest.getInstance("SM3", "KonaCrypto");
      byte[] buffer = new byte[8192];
      int bytesRead;
      while ((bytesRead = in.read(buffer)) != -1) {
        sm3.update(buffer, 0, bytesRead);
      }
      return Hex.encodeHexString(sm3.digest());
    } catch (Exception e) {
      throw new SystemException(FileErrorCodes.FILE_STORAGE_FAILED, e)
        .withLogDetail("SM3 摘要计算失败, path=" + fullPath);
    }
  }

  private Path resolvePath(StorageTarget target, String storageKey) {
    return Paths.get(target.basePath(), storageKey);
  }
}
