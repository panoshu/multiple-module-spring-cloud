package com.example.file.infrastructure.contract;

import com.example.file.api.event.FileParsedEventDTO;
import com.example.file.api.event.OutboundFileGeneratedEventDTO;
import com.example.file.api.request.GenerateOutboundFileRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("文件服务 requestNo 契约形状")
class FileRequestNoContractTest {

  @Test
  @DisplayName("GenerateOutboundFileRequest 携带 requestNo")
  void generateRequest_carriesRequestNo() {
    GenerateOutboundFileRequest request = new GenerateOutboundFileRequest(
      "FT-1", "TPL-1", Map.of(), "U-1", "SE-9");

    assertThat(request.requestNo()).isEqualTo("SE-9");
  }

  @Test
  @DisplayName("旧 4 参构造仍可用且 requestNo 为空（可空过渡）")
  void generateRequest_legacyConstructorDefaultsToNull() {
    GenerateOutboundFileRequest request = new GenerateOutboundFileRequest(
      "FT-1", "TPL-1", Map.of(), "U-1");

    assertThat(request.requestNo()).isNull();
  }

  @Test
  @DisplayName("文件解析完成事件 DTO 携带 requestNo")
  void fileParsedEvent_carriesRequestNo() {
    FileParsedEventDTO dto = new FileParsedEventDTO(
      "EVT-1", "FT-1", "FORM", "SUCCESS", 0, List.of(), null,
      LocalDateTime.now(), "SE-9");

    assertThat(dto.requestNo()).isEqualTo("SE-9");
  }

  @Test
  @DisplayName("旧 8 参事件构造仍可用且 requestNo 为空")
  void fileParsedEvent_legacyConstructorDefaultsToNull() {
    FileParsedEventDTO dto = new FileParsedEventDTO(
      "EVT-1", "FT-1", "FORM", "SUCCESS", 0, List.of(), null, LocalDateTime.now());

    assertThat(dto.requestNo()).isNull();
  }

  @Test
  @DisplayName("出站文件生成事件 DTO 携带 requestNo")
  void outboundEvent_carriesRequestNo() {
    OutboundFileGeneratedEventDTO dto = new OutboundFileGeneratedEventDTO(
      "EVT-1", "FT-1", "TPL-1", List.of(), LocalDateTime.now(), "SE-9");

    assertThat(dto.requestNo()).isEqualTo("SE-9");
  }

  @Test
  @DisplayName("2 个文件事件 DTO 均声明 requestNo")
  void fileEventDtos_declareRequestNo() {
    assertThat(List.of(FileParsedEventDTO.class, OutboundFileGeneratedEventDTO.class))
      .allSatisfy(type -> assertThat(
        Arrays.stream(type.getRecordComponents())
          .anyMatch(component -> component.getName().equals("requestNo")))
        .as(type.getSimpleName() + " 应声明 requestNo").isTrue());
  }
}
