package com.example.file.infrastructure.engine.s3;

import com.example.file.infrastructure.engine.StorageEngine;
import com.example.shared.file.types.constant.StorageType;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "shared.file.provider", havingValue = "s3")
public class S3StorageEngine implements StorageEngine {

  private final MinioClient minioClient;

  @Value("${shared.file.s3.bucket}")
  private String bucket;

  // 允许配置覆盖，默认 -1 让 MinIO 自动判断分片
  @Value("${shared.file.s3.part-size:-1}")
  private long partSize;

  @Override
  public StorageType getType() {
    return StorageType.S3;
  }

  @Override
  @SneakyThrows
  public void store(String path, InputStream stream, long size, String contentType) {
    // -1 表示未知大小 (MinIO 客户端会自动分片，但建议尽可能传 size)
    long objectSize = size > 0 ? size : -1;

    minioClient.putObject(
      PutObjectArgs.builder()
        .bucket(bucket)
        .object(path)
        .stream(stream, objectSize, partSize)
        .contentType(contentType)
        .build()
    );
  }

  @Override
  @SneakyThrows
  public String getPresignedPutUrl(String path, int expireSeconds) {
    return minioClient.getPresignedObjectUrl(
      GetPresignedObjectUrlArgs.builder()
        .method(Method.PUT) // 直传必须是 PUT
        .bucket(bucket)
        .object(path)
        .expiry(expireSeconds, TimeUnit.SECONDS)
        .build()
    );
  }

  @Override
  @SneakyThrows
  public InputStream openStream(String path) {
    return minioClient.getObject(
      GetObjectArgs.builder()
        .bucket(bucket)
        .object(path)
        .build()
    );
  }

  @Override
  @SneakyThrows
  public String getAccessUrl(String path, int expireSeconds) {
    return minioClient.getPresignedObjectUrl(
      GetPresignedObjectUrlArgs.builder()
        .method(Method.GET)
        .bucket(bucket)
        .object(path)
        .expiry(expireSeconds, TimeUnit.SECONDS)
        .build()
    );
  }

  @Override
  @SneakyThrows
  public void delete(String path) {
    minioClient.removeObject(
      RemoveObjectArgs.builder()
        .bucket(bucket)
        .object(path)
        .build()
    );
  }
}
