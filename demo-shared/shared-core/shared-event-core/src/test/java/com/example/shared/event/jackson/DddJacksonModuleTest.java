package com.example.shared.event.jackson;

import com.example.shared.identifier.id.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link DddJacksonModule} 的序列化和反序列化行为。
 *
 * <p>CV1 follow-up：Task 14 仅注册了 Serializer，强类型 ID 字段在 RequestBody 反序列化时会失败。
 * 本测试覆盖完整 round-trip 行为，确保 file-api 的 ApplyUploadTokenRequest/ApplyDownloadTokenRequest
 * 在 HTTP 端点可用。
 */
@DisplayName("DddJacksonModule 强类型 ID 序列化/反序列化")
class DddJacksonModuleTest {

  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new ObjectMapper();
    mapper.registerModule(new DddJacksonModule());
  }

  @Test
  @DisplayName("单个 CustomerNo 序列化为字符串")
  void should_serialize_customer_no_to_string() throws Exception {
    String json = mapper.writeValueAsString(CustomerNo.of("C001"));

    assertThat(json).isEqualTo("\"C001\"");
  }

  @Test
  @DisplayName("单个 CustomerNo 从字符串反序列化")
  void should_deserialize_customer_no_from_string() throws Exception {
    CustomerNo result = mapper.readValue("\"C001\"", CustomerNo.class);

    assertThat(result).isEqualTo(CustomerNo.of("C001"));
  }

  @Test
  @DisplayName("包含多个强类型 ID 的 record 可完整 round-trip")
  void should_round_trip_record_with_multiple_ids() throws Exception {
    SampleRequest original = new SampleRequest(
      CustomerNo.of("C001"),
      ProductNo.of("P001"),
      UserNo.of("u1"),
      new FileId("01H8FILE001"),
      new BatchId("BATCH_001")
    );

    String json = mapper.writeValueAsString(original);

    // 序列化后所有 ID 都是字符串形式
    assertThat(json).contains("\"customerNo\":\"C001\"")
      .contains("\"productNo\":\"P001\"")
      .contains("\"uploader\":\"u1\"")
      .contains("\"fileId\":\"01H8FILE001\"")
      .contains("\"batchId\":\"BATCH_001\"");

    SampleRequest result = mapper.readValue(json, SampleRequest.class);

    assertThat(result).isEqualTo(original);
  }

  @Test
  @DisplayName("从 JSON 字符串反序列化为 record 时各 ID 类型正确")
  void should_create_correct_types_when_deserializing() throws Exception {
    String json = """
      {
        "customerNo": "C001",
        "productNo": "P001",
        "uploader": "u1",
        "fileId": "01H8FILE001",
        "batchId": "BATCH_001"
      }
      """;

    SampleRequest result = mapper.readValue(json, SampleRequest.class);

    assertThat(result.customerNo()).isInstanceOf(CustomerNo.class);
    assertThat(result.productNo()).isInstanceOf(ProductNo.class);
    assertThat(result.uploader()).isInstanceOf(UserNo.class);
    assertThat(result.fileId()).isInstanceOf(FileId.class);
    assertThat(result.batchId()).isInstanceOf(BatchId.class);
  }

  @Test
  @DisplayName("null ID 字段正确处理")
  void should_handle_null_id_field() throws Exception {
    String json = "{\"customerNo\":null,\"productNo\":\"P001\",\"uploader\":\"u1\",\"fileId\":\"01H8FILE001\",\"batchId\":\"BATCH_001\"}";

    SampleRequest result = mapper.readValue(json, SampleRequest.class);

    assertThat(result.customerNo()).isNull();
    assertThat(result.productNo()).isEqualTo(ProductNo.of("P001"));
  }

  @Test
  @DisplayName("空字符串反序列化抛 IllegalArgumentException（由 ID 构造函数校验）")
  void should_throw_when_deserializing_empty_string() {
    assertThatThrownBy(() -> mapper.readValue("\"\"", CustomerNo.class))
      .isInstanceOf(Exception.class);
  }

  /**
   * 测试用 record，模拟 file-api 中的 Request DTO。
   */
  record SampleRequest(
    CustomerNo customerNo,
    ProductNo productNo,
    UserNo uploader,
    FileId fileId,
    BatchId batchId
  ) {
  }
}
