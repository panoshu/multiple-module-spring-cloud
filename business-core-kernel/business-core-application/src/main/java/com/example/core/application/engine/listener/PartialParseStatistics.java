package com.example.core.application.engine.listener;

import com.example.file.api.event.FileParsedEventDTO;

import java.util.List;

/**
 * PARTIAL 文件解析的失败明细统计（批次 64，§21.3 端到端对账）。
 * <p>
 * PARTIAL 与 SUCCESS 一样表示「解析任务整体完成、流程可继续推进」，但失败明细不能
 * 折叠成「全部成功」。本值对象把 file-service 事件里的子任务行数汇总为紧凑字符串，
 * 经 {@code CallbackCommand.resultRef} 写入 {@code ExternalInteraction.resultRef}，
 * 使业务侧可按协作记录审计「哪些行无效、多少个失败子任务」，无需新增 DDL 或列。
 *
 * @param totalSubTasks  顶层声明的子任务数
 * @param failedSubTasks 非 SUCCESS 状态的子任务数
 * @param totalRows      子任务总行数
 * @param validRows      有效行数
 * @param invalidRows    无效行数
 * @author core-application
 */
public record PartialParseStatistics(
  int totalSubTasks,
  int failedSubTasks,
  int totalRows,
  int validRows,
  int invalidRows
) {

  private static final String SUCCESS_STATUS = "SUCCESS";

  public static PartialParseStatistics from(FileParsedEventDTO event) {
    List<FileParsedEventDTO.SubTaskSummaryDTO> subTasks =
      event.subTasks() == null ? List.of() : event.subTasks();
    return new PartialParseStatistics(
      event.totalSubTasks(),
      (int) subTasks.stream().filter(s -> !SUCCESS_STATUS.equals(s.status())).count(),
      subTasks.stream().mapToInt(FileParsedEventDTO.SubTaskSummaryDTO::totalRows).sum(),
      subTasks.stream().mapToInt(FileParsedEventDTO.SubTaskSummaryDTO::validRows).sum(),
      subTasks.stream().mapToInt(FileParsedEventDTO.SubTaskSummaryDTO::invalidRows).sum());
  }

  /** 紧凑、可读、可解析的结果引用，长度远小于 resultRef 列宽。 */
  public String toResultRef() {
    return "PARTIAL:subTasks=%d,failed=%d,rows=%d,valid=%d,invalid=%d"
      .formatted(totalSubTasks, failedSubTasks, totalRows, validRows, invalidRows);
  }
}
