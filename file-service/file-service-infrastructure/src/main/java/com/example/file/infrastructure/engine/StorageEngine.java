package com.example.file.infrastructure.engine;

import com.example.shared.file.types.constant.StorageType;

import java.io.InputStream;

public interface StorageEngine {

  StorageType getType();

  /**
   * 服务端流式存储
   *
   * @param path        存储路径 (key)
   * @param stream      输入流
   * @param size        大小 (S3 某些 SDK 需要预知大小，Local 不需要)
   * @param contentType MIME类型
   */
  void store(String path, InputStream stream, long size, String contentType);

  /**
   * 读取文件流
   */
  InputStream openStream(String path);

  /**
   * 获取访问 URL (Local 返回代理地址，S3 返回签名地址)
   */
  String getAccessUrl(String path, int expireSeconds);

  /**
   * 删除文件
   */
  void delete(String path);

  String getPresignedPutUrl(String path, int expireSeconds);
}
