package com.example.shared.file.types.constant;

/**
 * 存储引擎类型
 */
public enum StorageType {
  /**
   * 本地磁盘/NAS (开发环境/私有化内网)
   */
  LOCAL,

  /**
   * S3 协议兼容存储 (MinIO, AWS S3, Ceph)
   */
  S3,

  /**
   * 阿里云 OSS (使用特定 SDK)
   */
  ALIYUN_OSS,

  /**
   * 华为云 OBS
   */
  HUAWEI_OBS
}
