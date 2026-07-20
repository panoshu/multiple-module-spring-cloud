package com.example.file.infrastructure.storage;

import com.example.file.domain.model.aggregate.valueobject.StorageTarget;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.tencent.kona.crypto.KonaCryptoProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.Security;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LocalFileStorage SM3 摘要")
class LocalFileStorageDigestTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void registerKonaProvider() {
        if (Security.getProvider("KonaCrypto") == null) {
            Security.addProvider(new KonaCryptoProvider());
        }
    }

    @Test
    @DisplayName("计算文件 SM3 摘要")
    void should_compute_sm3_digest() throws Exception {
        // 准备测试文件
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello world");

        StorageTarget target = new StorageTarget(
            "local-test", StorageType.LOCAL, null, null,
            tempDir.toString(), null, null, null, Map.of()
        );

        LocalFileStorage storage = new LocalFileStorage();
        String digest = storage.computeDigest(target, "test.txt");

        // SM3 输出 32 字节 = 64 hex 字符
        assertThat(digest).isNotNull().hasSize(64);

        // 与独立计算的 SM3 比对，确保算法正确（非任意 64 字符串）
        MessageDigest sm3 = MessageDigest.getInstance("SM3", "KonaCrypto");
        byte[] expected = sm3.digest("hello world".getBytes());
        String expectedHex = org.apache.commons.codec.binary.Hex.encodeHexString(expected);
        assertThat(digest).isEqualTo(expectedHex);
    }
}
