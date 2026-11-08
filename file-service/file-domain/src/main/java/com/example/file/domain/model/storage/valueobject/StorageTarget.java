package com.example.file.domain.model.storage.valueobject;

import com.example.shared.exception.CommonError;
import com.example.shared.exception.DomainException;
import com.example.shared.domain.aggregate.valueobject.ValueObject;

import java.util.Map;

/**
 * 存储目标值对象 (不可变)
 * <p>
 * 描述一个具体的存储后端配置，不持久化到 DB，由 application.yml 加载。
 */
public record StorageTarget(
  String targetId,
  StorageType type,
  String endpoint,
  String bucket,
  String basePath,
  String mountRoot,
  String accessKeyId,
  String accessKeySecret,
  Map<String, String> options
) implements ValueObject {

  public StorageTarget {
    if (targetId == null || targetId.isBlank()) {
      throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("targetId 不能为空");
    }
    if (type == null) {
      throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("type 不能为空");
    }
    options = options != null ? Map.copyOf(options) : Map.of();

    switch (type) {
      case LOCAL -> {
        if (basePath == null || basePath.isBlank()) {
          throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("LOCAL 类型必须配置 basePath").withLogDetail("targetId=" + targetId);
        }
      }
      case OSS -> {
        if (endpoint == null || endpoint.isBlank()) {
          throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("OSS 类型必须配置 endpoint").withLogDetail("targetId=" + targetId);
        }
        if (bucket == null || bucket.isBlank()) {
          throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("OSS 类型必须配置 bucket").withLogDetail("targetId=" + targetId);
        }
        if (accessKeyId == null || accessKeyId.isBlank()) {
          throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("OSS 类型必须配置 accessKeyId").withLogDetail("targetId=" + targetId);
        }
        if (accessKeySecret == null || accessKeySecret.isBlank()) {
          throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("OSS 类型必须配置 accessKeySecret").withLogDetail("targetId=" + targetId);
        }
      }
      case NAS -> {
        if (bucket == null || bucket.isBlank()) {
          throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("NAS 类型必须配置 bucket(共享名)").withLogDetail("targetId=" + targetId);
        }
        if (basePath == null || basePath.isBlank()) {
          throw new DomainException(CommonError.BAD_REQUEST).withUserDetail("NAS 类型必须配置 basePath").withLogDetail("targetId=" + targetId);
        }
      }
    }
  }
}
