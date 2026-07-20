package com.example.file.infrastructure.storage;

import com.example.file.domain.model.aggregate.valueobject.FileTokenPayload;
import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tencent.kona.crypto.KonaCryptoProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.Security;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("KonaFileTokenGateway SM4 加解密")
class KonaFileTokenGatewayTest {

    private KonaFileTokenGateway gateway;

    @BeforeAll
    static void registerKonaProvider() {
        if (Security.getProvider("KonaCrypto") == null) {
            Security.addProvider(new KonaCryptoProvider());
        }
    }

    @BeforeEach
    void setUp() {
        FileTokenProperties props = new FileTokenProperties();
        // 测试密钥：16 字节 "0123456789abcdef" 的 Base64
        props.setSecretKey("MDEyMzQ1Njc4OWFiY2RlZg==");
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        gateway = new KonaFileTokenGateway(objectMapper, props);
    }

    @Test
    @DisplayName("加解密 round-trip 成功")
    void should_encrypt_and_decrypt() {
        FileTokenPayload payload = new FileTokenPayload(
            "tok-001", new FileId("f001"), FileUsage.SOURCE, "biz",
            CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
            List.of("application/xlsx"), 10L * 1024 * 1024, LocalDateTime.now().plusMinutes(15)
        );

        String token = gateway.encrypt(payload);
        assertThat(token).isNotBlank();

        FileTokenPayload decrypted = gateway.decrypt(token);
        assertThat(decrypted.tokenId()).isEqualTo("tok-001");
        assertThat(decrypted.fileId()).isEqualTo(new FileId("f001"));
    }

    @Test
    @DisplayName("错误 token 解密抛异常")
    void should_throw_when_decrypt_invalid_token() {
        assertThatThrownBy(() -> gateway.decrypt("invalid-token-string"))
            .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("每次加密生成不同密文（随机 IV）")
    void should_produce_different_ciphertext_each_time() {
        FileTokenPayload payload = new FileTokenPayload(
            "tok-001", new FileId("f001"), FileUsage.SOURCE, "biz",
            CustomerNo.of("C001"), ProductNo.of("P001"), UserNo.of("u1"),
            List.of("application/xlsx"), 10L * 1024 * 1024, LocalDateTime.now().plusMinutes(15)
        );

        String token1 = gateway.encrypt(payload);
        String token2 = gateway.encrypt(payload);
        assertThat(token1).isNotEqualTo(token2);  // 因 IV 随机
    }
}
