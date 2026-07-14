package com.example.shared.file.types.dto;

import java.util.Map;

/**
 * 上传预签名响应 (包含 S3 Post Policy)
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/1/20 22:03
 */
public record PresignUploadResp(
  String fileId,
  String uploadUrl,        // 提交地址
  Map<String, String> formData // 必须携带的 Form 表单参数 (AWS Signature, Policy等)
) {
}
