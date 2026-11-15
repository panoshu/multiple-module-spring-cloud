package com.example.core.application.engine.inbound;

import java.util.List;

/**
 * 文件解析完成入站消息（kernel 自有契约）。
 * <p>
 * 由 adapter 层把 file-service 的集成事件 DTO 转换为本消息后再交给应用层，
 * 应用层不直接依赖 file-service 的 API 模块，从而保证 kernel 可作为独立
 * 内核对不同文件服务实现复用。
 *
 * @param eventId       事件幂等 ID
 * @param fileTaskId    文件任务 ID
 * @param requestNo     外部协作幂等键（可为空）
 * @param status        解析状态：SUCCESS / PARTIAL / FAILED 等
 * @param totalSubTasks 顶层声明的子任务数
 * @param subTasks      子任务摘要
 * @param failureReason 失败原因
 * @author business-core-kernel
 */
public record FileParsedMessage(
  String eventId,
  String fileTaskId,
  String requestNo,
  String status,
  int totalSubTasks,
  List<ParsedSubTaskMessage> subTasks,
  String failureReason
) {

  public FileParsedMessage {
    subTasks = subTasks == null ? List.of() : List.copyOf(subTasks);
  }

  /**
   * 文件解析子任务摘要。
   *
   * @param subTaskId  子任务 ID（表单裂变后作为明细文件引用）
   * @param splitKey   拆分键（表单裂变后作为计划号）
   * @param totalRows  总行数
   * @param validRows  有效行数
   * @param invalidRows 无效行数
   * @param status     子任务状态
   */
  public record ParsedSubTaskMessage(
    String subTaskId,
    String splitKey,
    int totalRows,
    int validRows,
    int invalidRows,
    String status
  ) {
  }
}
