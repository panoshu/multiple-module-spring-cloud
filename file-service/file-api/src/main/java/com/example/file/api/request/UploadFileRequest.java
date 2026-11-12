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
  /**
   * 外部协作幂等键（spec §11.4）：kernel 传入 {@code ExternalInteraction.requestNo}，
   * 同一键重复上传只产生一个解析任务。过渡期允许为空，由业务引用兼容。
   */
  String clientRequestNo
) {
}
