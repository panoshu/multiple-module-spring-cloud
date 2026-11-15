package com.example.core.application.engine.inbound;

/**
 * 出站文件生成完成入站消息（kernel 自有契约）。
 * <p>
 * adapter 层把 file-service 的 {@code OutboundFileGeneratedEvent} 转换为本消息，
 * 应用层据此按 fileTaskId 关联外部协作并推进流程。
 *
 * @param eventId          事件幂等 ID
 * @param fileTaskId       文件任务 ID
 * @param requestNo        外部协作幂等键（可为空）
 * @param targetTemplateId 目标模板 ID
 * @param generatedFileCount 生成文件数
 * @author business-core-kernel
 */
public record OutboundFileGeneratedMessage(
  String eventId,
  String fileTaskId,
  String requestNo,
  String targetTemplateId,
  int generatedFileCount
) {
}
