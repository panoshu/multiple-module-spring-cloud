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
  String operator,
  /** 外部协作幂等键（spec §11.4）：kernel 传入 {@code ExternalInteraction.requestNo}；过渡期可空。 */
  String requestNo
) {

  public GenerateOutboundFileRequest(String fileTaskId, String targetTemplateId,
                                     Map<String, Object> supplementalData, String operator) {
    this(fileTaskId, targetTemplateId, supplementalData, operator, null);
  }
}
