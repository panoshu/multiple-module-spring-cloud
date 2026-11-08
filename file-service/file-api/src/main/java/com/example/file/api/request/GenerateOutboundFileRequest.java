package com.example.file.api.request;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * 出站文件生成请求
 * <p>
 * supplementalData 中的字段须以 {@code biz_} 为前缀。
 */
public record GenerateOutboundFileRequest(
  @NotBlank String fileTaskId,
  @NotBlank String targetTemplateId,
  Map<String, Object> supplementalData,
  String operator
) {
}
