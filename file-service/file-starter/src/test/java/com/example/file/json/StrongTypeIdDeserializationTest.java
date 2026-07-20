package com.example.file.json;

import com.example.file.api.request.ApplyDownloadTokenRequest;
import com.example.file.api.request.ApplyUploadTokenRequest;
import com.example.shared.event.jackson.DddJacksonModule;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 file-service 中 ObjectMapper 配置链路能正确反序列化强类型 ID。
 *
 * <p>CV1 follow-up：Task 14 仅注册了 Serializer，RequestBody 反序列化会失败。
 * 本测试模拟 Spring Boot 自动配置链路中 ObjectMapper 的最终状态：
 * <ul>
 *   <li>{@link DddJacksonModule}（由 EventAutoConfiguration 创建 Bean）</li>
 *   <li>{@link JavaTimeModule}（由 JacksonAutoConfiguration 通过 spring.jackson.serialization.<*> 配置创建）</li>
 * </ul>
 *
 * <p>不使用 @JsonTest/@SpringBootTest 是因为 EventAutoConfiguration 还会创建 EventStore 等
 * 需要 JdbcClient/DataSource 的 Bean，导致测试上下文过重。
 * DddJacksonModule 本身的反序列化逻辑由 {@code DddJacksonModuleTest} 覆盖。
 */
@DisplayName("强类型 ID 在 file-service ObjectMapper 配置下的反序列化（CV1 验证）")
class StrongTypeIdDeserializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // 模拟 Spring Boot 自动配置链路中 ObjectMapper 的最终状态：
        // JacksonAutoConfiguration 自动注册所有 Module Bean（DddJacksonModule + JavaTimeModule）
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new DddJacksonModule());
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("ApplyUploadTokenRequest 从 JSON 反序列化，强类型 ID 字段类型正确")
    void should_deserialize_apply_upload_token_request_with_strong_typed_ids() throws Exception {
        String json = """
            {
              "bizType": "annuity",
              "sourceApp": "approval-service",
              "businessBatchId": "BATCH_001",
              "customerNo": "C001",
              "productNo": "P001",
              "uploader": "u1",
              "expiresAt": "2026-12-31T23:59:59",
              "allowedContentTypes": ["application/xlsx"],
              "allowedMaxSize": 10485760,
              "ttl": "PT15M"
            }
            """;

        ApplyUploadTokenRequest result = objectMapper.readValue(json, ApplyUploadTokenRequest.class);

        assertThat(result.bizType()).isEqualTo("annuity");
        assertThat(result.sourceApp()).isEqualTo("approval-service");
        assertThat(result.businessBatchId()).isEqualTo("BATCH_001");
        assertThat(result.customerNo()).isEqualTo(CustomerNo.of("C001"));
        assertThat(result.productNo()).isEqualTo(ProductNo.of("P001"));
        assertThat(result.uploader()).isEqualTo(UserNo.of("u1"));
        assertThat(result.allowedContentTypes()).containsExactly("application/xlsx");
        assertThat(result.allowedMaxSize()).isEqualTo(10L * 1024 * 1024);
        assertThat(result.ttl()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("ApplyDownloadTokenRequest 从 JSON 反序列化，FileId 字段正确")
    void should_deserialize_apply_download_token_request_with_file_id() throws Exception {
        String json = """
            {
              "fileId": "01H8FILE001",
              "sourceApp": "approval-service",
              "customerNo": "C001",
              "productNo": "P001",
              "downloader": "u1",
              "ttl": "PT10M"
            }
            """;

        ApplyDownloadTokenRequest result = objectMapper.readValue(json, ApplyDownloadTokenRequest.class);

        assertThat(result.fileId()).isEqualTo(new FileId("01H8FILE001"));
        assertThat(result.sourceApp()).isEqualTo("approval-service");
        assertThat(result.customerNo()).isEqualTo(CustomerNo.of("C001"));
        assertThat(result.productNo()).isEqualTo(ProductNo.of("P001"));
        assertThat(result.downloader()).isEqualTo(UserNo.of("u1"));
        assertThat(result.ttl()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("ApplyUploadTokenRequest round-trip: 序列化后反序列化保持一致")
    void should_round_trip_apply_upload_token_request() throws Exception {
        ApplyUploadTokenRequest original = new ApplyUploadTokenRequest(
            "annuity",
            "approval-service",
            "BATCH_001",
            CustomerNo.of("C001"),
            ProductNo.of("P001"),
            UserNo.of("u1"),
            LocalDateTime.of(2026, 12, 31, 23, 59, 59),
            List.of("application/xlsx"),
            10L * 1024 * 1024,
            Duration.ofMinutes(15)
        );

        String json = objectMapper.writeValueAsString(original);
        ApplyUploadTokenRequest result = objectMapper.readValue(json, ApplyUploadTokenRequest.class);

        assertThat(result).isEqualTo(original);
    }
}
