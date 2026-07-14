package com.example.shared.file.types.dto;

/**
 * 文件元数据响应
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/1/20 22:02
 */
public record FileMetaResp(
  String fileId,
  String originalName,
  String mimeType,
  long size,
  String url, // 下载/预览链接
  String bizType,
  String status // TEMP, PERSISTENT
) {
}
