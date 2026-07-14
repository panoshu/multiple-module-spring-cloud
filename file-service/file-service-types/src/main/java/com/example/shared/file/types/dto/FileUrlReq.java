package com.example.shared.file.types.dto;

import java.io.Serializable;

/**
 * 文件 Token 申请请求 (使用 Record)
 * Record 自动生成构造函数、getter (无 get 前缀)、equals、hashCode、toString
 */
public record FileUrlReq(
  String fileId,   // 下载时必填
  String userId,   // 必填
  String bizType,  // 必填
  FileAction action,   // "UPLOAD" or "DOWNLOAD"
  String clientIp
) implements Serializable {

  // 可以添加紧凑构造函数进行校验
  public FileUrlReq {
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("UserId cannot be empty");
    }
    if (bizType == null || bizType.isBlank()) {
      throw new IllegalArgumentException("BizType cannot be empty");
    }
    if (action == FileAction.DOWNLOAD && fileId == null || fileId.isBlank()) {
      throw new IllegalArgumentException("FileId cannot be empty when downloading file");
    }
    if (clientIp == null) {
      clientIp = "";
    }
  }

  public enum FileAction {DOWNLOAD, UPLOAD}
}
