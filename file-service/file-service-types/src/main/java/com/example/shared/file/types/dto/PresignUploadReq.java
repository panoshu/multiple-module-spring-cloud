package com.example.shared.file.types.dto;

/**
 * 上传预签名请求 (用于大文件直传)
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/1/20 22:02
 */
public record PresignUploadReq(
  String originalName,
  long size,
  String md5,
  String bizType
) {
}
