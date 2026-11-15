package com.example.file.infrastructure.event;

import com.example.file.api.event.FileParsedEventDTO;
import com.example.file.api.event.OutboundFileGeneratedEventDTO;
import com.example.file.domain.event.FileParsedEvent;
import com.example.file.domain.event.OutboundFileGeneratedEvent;
import com.example.file.domain.model.enums.ErrorPolicy;
import com.example.file.domain.model.enums.SubTaskStatus;
import com.example.file.domain.model.parse.aggregate.ParseTask;
import com.example.file.domain.model.parse.valueobject.SubTaskSummary;
import com.example.file.types.BizType;
import com.example.file.types.FileId;
import com.example.file.types.FileTaskId;
import com.example.file.types.SubTaskId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 2 个文件集成事件 Converter 透传真实 requestNo（spec §11.4）。 */
@DisplayName("文件集成事件 requestNo 透传")
class FileEventRequestNoConverterTest {

  @Test
  @DisplayName("FileParsedEventConverter 透传 ParseTask 的 requestNo")
  void fileParsedConverter_passesThroughRequestNo() {
    ParseTask task = ParseTask.create(FileTaskId.of("tsk1"), BizType.of("import_declare"),
      "f.xlsx", new FileId("01H8SAMPLEFILE001"), ErrorPolicy.COLLECT_ALL,
      List.of("detailList.deptCode"), "u1", "SE-9");
    task.recordSubTask(new SubTaskSummary(
      SubTaskId.of("sub1"), "RD", 5, 5, 0, SubTaskStatus.VALID));
    task.markSuccess();
    FileParsedEvent event = FileParsedEvent.of(task);

    Object result = new FileParsedEventConverter().toIntegrationEvent(event);

    assertThat(result).isInstanceOf(FileParsedEventDTO.class);
    assertThat(((FileParsedEventDTO) result).requestNo()).isEqualTo("SE-9");
  }

  @Test
  @DisplayName("OutboundFileGeneratedEventConverter 透传 requestNo")
  void outboundConverter_passesThroughRequestNo() {
    OutboundFileGeneratedEvent event = OutboundFileGeneratedEvent.of(
      FileTaskId.of("tsk1"), List.of(), "TPL-1", "SE-9");

    Object result = new OutboundFileGeneratedEventConverter().toIntegrationEvent(event);

    assertThat(result).isInstanceOf(OutboundFileGeneratedEventDTO.class);
    assertThat(((OutboundFileGeneratedEventDTO) result).requestNo()).isEqualTo("SE-9");
  }
}
