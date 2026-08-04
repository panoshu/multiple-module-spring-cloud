package com.example.file.infrastructure.storage;

import com.example.file.domain.gateway.FileTokenGateway;
import com.example.file.domain.gateway.FileTokenStore;
import com.example.file.domain.model.aggregate.valueobject.FileTokenPayload;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.infrastructure.FileAccessIntegrationTestConfiguration;
import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.FileId;
import com.example.shared.identifier.id.ProductNo;
import com.example.shared.identifier.id.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文件访问 Token 端到端集成测试。
 *
 * <p>测试焦点：
 * <ul>
 *   <li>KonaFileTokenGateway 真实 SM4 加密 → 解密 round-trip</li>
 *   <li>RedisFileTokenStore 真实 SETNX 一次性语义（通过 mock RedissonClient
 *       模拟 Redis 行为，{@link com.example.file.infrastructure.FileAccessIntegrationTestConfiguration}
 *       提供）</li>
 *   <li>同一 tokenId 首次 markUsed 返回 true，重复返回 false</li>
 *   <li>不同 tokenId 互不影响</li>
 * </ul>
 *
 * <p>不测试内容（超出本测试范围）：
 * <ul>
 *   <li>FileAccessAdapter 的 HTTP 端到端（需 MockMvc + 完整 Spring 上下文 + 数据库）</li>
 *   <li>ApplyUploadTokenUseCase / UploadFileWithTokenUseCase 完整流程（需 FileMetadata 数据库）</li>
 *   <li>FileTokenService 业务校验（已在 file-domain 单元测试覆盖）</li>
 * </ul>
 *
 * <p>测试配置：{@link com.example.file.infrastructure.FileAccessIntegrationTestConfiguration}
 * 显式 @Import KonaAutoConfiguration 加载 KonaFileTokenGateway，手动注册 RedisFileTokenStore
 * Bean（绑定 mock RedissonClient 模拟 SETNX 语义），不依赖真实 Redis。
 *
 * <p>配置类包路径说明：配置类位于 {@code com.example.file.infrastructure} 包（非 storage 子包），
 * 避免被 {@link StorageTestConfiguration} 的 {@code @ComponentScan(basePackages = "...storage")}
 * 误加载到 StorageIntegrationTest 上下文中。
 */
@SpringBootTest(classes = FileAccessIntegrationTestConfiguration.class)
@TestPropertySource(properties = {
  "file.token.secret-key=MDEyMzQ1Njc4OWFiY2RlZg==",
  "file.token.default-upload-ttl=15m",
  "file.token.default-download-ttl=15m",
  "file.token.redis.key-prefix=test:file:token:used:"
})
@DisplayName("文件访问 Token 端到端集成测试")
class FileAccessIntegrationTest {

  @Autowired
  private FileTokenGateway tokenGateway;

  @Autowired
  private FileTokenStore tokenStore;

  @Test
  @DisplayName("Token 加密 → 解密 → 一次性使用 完整流程")
  void should_full_flow_token_lifecycle() {
    // 1. 构造 payload 并加密
    FileTokenPayload payload = new FileTokenPayload(
      "tok-e2e-001", new FileId("f-e2e-001"), FileUsage.SOURCE, "biz",
      CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
      List.of("application/xlsx"), 10L * 1024 * 1024,
      LocalDateTime.now().plusMinutes(15)
    );
    String token = tokenGateway.encrypt(payload);
    assertThat(token).isNotBlank();

    // 2. 解密并验证字段一致性
    FileTokenPayload decrypted = tokenGateway.decrypt(token);
    assertThat(decrypted.tokenId()).isEqualTo("tok-e2e-001");
    assertThat(decrypted.fileId()).isEqualTo(new FileId("f-e2e-001"));
    assertThat(decrypted.usage()).isEqualTo(FileUsage.SOURCE);
    assertThat(decrypted.customerNo()).isEqualTo(CustomerNo.of("C001"));
    assertThat(decrypted.productNo()).isEqualTo(ProductNo.of("P001"));
    assertThat(decrypted.operator()).isEqualTo(UserNo.of("u1"));

    // 3. 一次性语义：首次 markUsed 返回 true，重复返回 false
    boolean firstUse = tokenStore.markUsed("tok-e2e-001", Duration.ofMinutes(15));
    assertThat(firstUse).as("首次 markUsed 应返回 true").isTrue();

    boolean secondUse = tokenStore.markUsed("tok-e2e-001", Duration.ofMinutes(15));
    assertThat(secondUse).as("重复 markUsed 应返回 false").isFalse();

    // 4. isUsed 检查：已使用的 token 应返回 true
    assertThat(tokenStore.isUsed("tok-e2e-001")).isTrue();
  }

  @Test
  @DisplayName("不同 tokenId 的 markUsed 互不影响")
  void should_independent_token_marking() {
    boolean firstA = tokenStore.markUsed("tok-e2e-A", Duration.ofMinutes(15));
    boolean firstB = tokenStore.markUsed("tok-e2e-B", Duration.ofMinutes(15));

    assertThat(firstA).isTrue();
    assertThat(firstB).isTrue();

    // 重复使用各自的 token
    assertThat(tokenStore.markUsed("tok-e2e-A", Duration.ofMinutes(15))).isFalse();
    assertThat(tokenStore.markUsed("tok-e2e-B", Duration.ofMinutes(15))).isFalse();
  }

  @Test
  @DisplayName("每次加密生成不同密文（随机 IV）")
  void should_produce_different_ciphertext_each_time() {
    FileTokenPayload payload = new FileTokenPayload(
      "tok-e2e-002", new FileId("f-e2e-002"), FileUsage.EXPORT, "biz-export",
      CustomerNo.of("C002"), ProductNo.of("P002"), UserNo.of("u2"),
      null, null,
      LocalDateTime.now().plusMinutes(30)
    );

    String token1 = tokenGateway.encrypt(payload);
    String token2 = tokenGateway.encrypt(payload);

    assertThat(token1).isNotEqualTo(token2);
    // 两个密文都应能正确解密回原 payload
    assertThat(tokenGateway.decrypt(token1).tokenId()).isEqualTo("tok-e2e-002");
    assertThat(tokenGateway.decrypt(token2).tokenId()).isEqualTo("tok-e2e-002");
  }

  @Test
  @DisplayName("未使用的 tokenId 在 isUsed 检查时返回 false")
  void should_return_false_when_token_not_used() {
    assertThat(tokenStore.isUsed("tok-never-used")).isFalse();
  }
}
