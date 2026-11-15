package com.example.core.application.engine.listener;

import com.example.file.api.event.FileParsedEventDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 批次 64：PARTIAL 文件解析失败明细统计（端到端对账的可审计载体）。 */
@DisplayName("文件部分成功统计（批次 64）")
class PartialParseStatisticsTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 10, 0);

  @Test
  @DisplayName("汇总子任务行数与失败子任务数，并编码为紧凑 resultRef")
  void from_sumsRowsAndEncodesResultRef() {
    List<FileParsedEventDTO.SubTaskSummaryDTO> subTasks = List.of(
      new FileParsedEventDTO.SubTaskSummaryDTO("ST-1", "K1", 10, 10, 0, "SUCCESS"),
      new FileParsedEventDTO.SubTaskSummaryDTO("ST-2", "K2", 10, 7, 3, "PARTIAL"));
    FileParsedEventDTO event = new FileParsedEventDTO(
      "evt-1", "task-1", "FORM_DETAIL", "PARTIAL", 2, subTasks, null, NOW);

    PartialParseStatistics stats = PartialParseStatistics.from(event);

    assertThat(stats.totalSubTasks()).isEqualTo(2);
    assertThat(stats.failedSubTasks()).isEqualTo(1);
    assertThat(stats.totalRows()).isEqualTo(20);
    assertThat(stats.validRows()).isEqualTo(17);
    assertThat(stats.invalidRows()).isEqualTo(3);
    assertThat(stats.toResultRef())
      .isEqualTo("PARTIAL:subTasks=2,failed=1,rows=20,valid=17,invalid=3");
  }

  @Test
  @DisplayName("subTasks 为 null 时防御性按 0 汇总")
  void from_nullSubTasksIsDefensive() {
    FileParsedEventDTO event = new FileParsedEventDTO(
      "evt-2", "task-2", "FORM_DETAIL", "PARTIAL", 3, null, null, NOW);

    PartialParseStatistics stats = PartialParseStatistics.from(event);

    assertThat(stats.totalSubTasks()).isEqualTo(3);
    assertThat(stats.failedSubTasks()).isZero();
    assertThat(stats.totalRows()).isZero();
    assertThat(stats.invalidRows()).isZero();
  }
}
