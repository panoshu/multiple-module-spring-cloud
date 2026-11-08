package com.example.file.api.response;

/**
 * 出站文件生成响应
 */
public record OutboundFileIdResponse(
  String fileTaskId,
  int generatedFileCount
) {
}
