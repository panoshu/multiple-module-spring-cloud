package com.example.file.domain.model.aggregate.root;

import com.example.file.domain.model.enums.ErrorPolicy;
import com.example.file.domain.model.enums.TaskStatus;
import com.example.file.domain.model.valueobject.SubTaskSummary;
import com.example.file.domain.model.valueobject.TaskError;
import com.example.file.types.BizType;
import com.example.file.types.FileTaskId;
import com.example.file.types.SubTaskId;
import com.example.file.types.TemplateCode;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParseTaskTest {

  @Test
  void should_create_pending_task() {
    ParseTask task = ParseTask.create(FileTaskId.of("tsk1"), BizType.of("import_declare"),
        "sample.xlsx", new FileId("01H8SAMPLEFILE001"), ErrorPolicy.COLLECT_ALL,
        List.of("detailList.deptCode"), UserNo.of("u1"));

    assertThat(task.status()).isEqualTo(TaskStatus.PENDING);
    assertThat(task.totalRows()).isZero();
    assertThat(task.subTaskSummaries()).isEmpty();
  }

  @Test
  void should_transition_through_parsing_to_success() {
    ParseTask task = newTask();
    task.markParsing();
    assertThat(task.status()).isEqualTo(TaskStatus.PARSING);

    task.markSplitting();
    assertThat(task.status()).isEqualTo(TaskStatus.SPLITTING);

    task.markValidating();
    assertThat(task.status()).isEqualTo(TaskStatus.VALIDATING);

    task.recordSubTask(new SubTaskSummary(SubTaskId.of("sub1"), "RD_DEPT", 10, 10, 0,
        com.example.file.domain.model.enums.SubTaskStatus.VALID));

    task.markSuccess();
    assertThat(task.status()).isEqualTo(TaskStatus.SUCCESS);
    assertThat(task.subTaskSummaries()).hasSize(1);
    assertThat(task.finishedAt()).isNotNull();
  }

  @Test
  void should_throw_when_mark_success_without_subtasks() {
    ParseTask task = newTask();
    assertThatThrownBy(task::markSuccess).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void markPartialSuccess_should_set_status_and_failed_count() {
    ParseTask task = newTask();
    task.markPartialSuccess(2);
    assertThat(task.status()).isEqualTo(TaskStatus.PARTIAL_SUCCESS);
    assertThat(task.invalidCount()).isEqualTo(2);
  }

  @Test
  void markFailed_should_record_error() {
    ParseTask task = newTask();
    TaskError err = new TaskError("PARSE_ERROR", "parse failed", "detail");
    task.markFailed(err);
    assertThat(task.status()).isEqualTo(TaskStatus.FAILED);
    assertThat(task.errors()).contains(err);
  }

  @Test
  void recordSubTask_should_aggregate_counts() {
    ParseTask task = newTask();
    task.recordSubTask(new SubTaskSummary(SubTaskId.of("s1"), "A", 10, 8, 2,
        com.example.file.domain.model.enums.SubTaskStatus.INVALID));
    task.recordSubTask(new SubTaskSummary(SubTaskId.of("s2"), "B", 5, 5, 0,
        com.example.file.domain.model.enums.SubTaskStatus.VALID));

    assertThat(task.subTaskCount()).isEqualTo(2);
    assertThat(task.totalRows()).isEqualTo(15);
    assertThat(task.validCount()).isEqualTo(13);
    assertThat(task.invalidCount()).isEqualTo(2);
  }

  private ParseTask newTask() {
    return ParseTask.create(FileTaskId.of("tsk1"), BizType.of("import_declare"),
        "sample.xlsx", new FileId("01H8SAMPLEFILE001"), ErrorPolicy.COLLECT_ALL,
        List.of("detailList.deptCode"), UserNo.of("u1"));
  }
}
