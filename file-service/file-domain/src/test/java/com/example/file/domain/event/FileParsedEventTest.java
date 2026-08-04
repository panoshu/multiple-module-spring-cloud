package com.example.file.domain.event;

import com.example.file.domain.model.aggregate.root.ParseTask;
import com.example.file.domain.model.enums.ErrorPolicy;
import com.example.file.domain.model.enums.TaskStatus;
import com.example.file.types.BizType;
import com.example.file.types.FileTaskId;
import com.example.shared.identifier.id.FileId;
import com.example.shared.identifier.id.UserNo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FileParsedEventTest {

  @Test
  void should_build_event_from_task() {
    ParseTask task = ParseTask.create(FileTaskId.of("tsk1"), BizType.of("import_declare"),
      "f.xlsx", new FileId("01H8SAMPLEFILE001"), ErrorPolicy.COLLECT_ALL, List.of("detailList.deptCode"), UserNo.of("u1"));
    task.markParsing();
    task.markSplitting();
    task.markValidating();
    task.recordSubTask(new com.example.file.domain.model.valueobject.SubTaskSummary(
      com.example.file.types.SubTaskId.of("sub1"), "RD", 5, 5, 0,
      com.example.file.domain.model.enums.SubTaskStatus.VALID));
    task.markSuccess();

    FileParsedEvent event = FileParsedEvent.of(task);

    assertThat(event.fileTaskId()).isEqualTo(FileTaskId.of("tsk1"));
    assertThat(event.bizType()).isEqualTo(BizType.of("import_declare"));
    assertThat(event.status()).isEqualTo(TaskStatus.SUCCESS);
    assertThat(event.totalSubTasks()).isEqualTo(1);
    assertThat(event.subTasks()).hasSize(1);
    assertThat(event.failureReason()).isNull();
    assertThat(event.eventId()).isNotNull();
    assertThat(event.occurredOn()).isNotNull();
  }
}
