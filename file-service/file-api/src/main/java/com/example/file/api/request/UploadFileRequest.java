package com.example.file.api.request;

import jakarta.validation.constraints.NotBlank;

public record UploadFileRequest(
  @NotBlank String bizType,
  /**
   * 文件品类（批次3 A1：FORM=解析路由触发 / MATERIAL=直存；空则按配置存在性兼容判断）
   */
  String category,
  /**
   * 业务上下文（批次3 A3：FORM 品类的模板路由维度）
   */
  java.util.Map<String, String> routingContext,
  String templateCode,
  @NotBlank String fileName,
  long fileSize,
  @NotBlank String uploader,
  String clientRequestNo
) {
}
