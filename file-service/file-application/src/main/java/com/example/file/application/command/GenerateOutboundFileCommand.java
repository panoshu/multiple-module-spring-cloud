package com.example.file.application.command;

import java.util.Map;

/**
 * 出站文件生成命令。
 * <p>
 * 由 kernel 的 ArchiveSubmissionHandler 通过 FileIntegrationGateway 触发。
 * supplementalData 中的字段须以 {@code biz_} 为前缀，避免与标准属性冲突。
 */
public record GenerateOutboundFileCommand(
  String fileTaskId,
  String targetTemplateId,
  Map<String, Object> supplementalData,
  String operator,
  /** 外部协作幂等键（spec §11.4），由 GenerateOutboundFileRequest 透传。 */
  String requestNo
) {
}
