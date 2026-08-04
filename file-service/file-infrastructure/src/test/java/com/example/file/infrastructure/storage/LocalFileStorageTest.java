package com.example.file.infrastructure.storage;

import com.example.file.domain.model.aggregate.valueobject.StorageTarget;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import com.tencent.kona.crypto.KonaCryptoProvider;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.Security;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFileStorageTest {

  private final LocalFileStorage storage = new LocalFileStorage();

  @TempDir
  Path tempDir;

  @BeforeAll
  static void registerKonaProvider() {
    if (Security.getProvider("KonaCrypto") == null) {
      Security.addProvider(new KonaCryptoProvider());
    }
  }

  @Test
  @DisplayName("store 应将文件写入本地路径并创建父目录")
  void store_should_write_file_and_create_parent_dirs() throws IOException {
    StorageTarget target = newTarget();
    // 使用真实 ULID 格式 (26 字符)，避免 Windows 文件系统对 trailing dots 的特殊处理
    String storageKey = "annuity/2026-07-19/BATCH_001/01H8K3VY5R8Q9J6X4N2S7M0ZQB/test.txt";

    storage.store(target, storageKey, new ByteArrayInputStream("hello".getBytes()), 5);

    Path expected = tempDir.resolve(storageKey);
    assertThat(Files.exists(expected)).isTrue();
    assertThat(Files.readString(expected)).isEqualTo("hello");
  }

  @Test
  @DisplayName("open 应返回可读的流")
  void open_should_return_readable_stream() throws IOException {
    StorageTarget target = newTarget();
    storage.store(target, "file.txt", new ByteArrayInputStream("content".getBytes()), 7);

    try (InputStream in = storage.open(target, "file.txt")) {
      assertThat(new String(in.readAllBytes())).isEqualTo("content");
    }
  }

  @Test
  @DisplayName("exists 在文件存在时应返回 true")
  void exists_should_return_true_when_file_exists() {
    StorageTarget target = newTarget();
    storage.store(target, "file.txt", new ByteArrayInputStream("x".getBytes()), 1);

    assertThat(storage.exists(target, "file.txt")).isTrue();
    assertThat(storage.exists(target, "missing.txt")).isFalse();
  }

  @Test
  @DisplayName("copy 应复制文件到新 key")
  void copy_should_duplicate_file() throws IOException {
    StorageTarget target = newTarget();
    storage.store(target, "src.txt", new ByteArrayInputStream("data".getBytes()), 4);

    storage.copy(target, "src.txt", "dst.txt");

    assertThat(Files.readString(tempDir.resolve("dst.txt"))).isEqualTo("data");
  }

  @Test
  @DisplayName("computeDigest 应返回正确的 SM3")
  void computeDigest_should_return_correct_sm3() throws Exception {
    StorageTarget target = newTarget();
    byte[] data = "hello world".getBytes();
    storage.store(target, "file.txt", new ByteArrayInputStream(data), data.length);

    String digest = storage.computeDigest(target, "file.txt");
    MessageDigest sm3 = MessageDigest.getInstance("SM3", "KonaCrypto");
    String expected = Hex.encodeHexString(sm3.digest(data));
    assertThat(digest).isEqualTo(expected);
  }

  @Test
  @DisplayName("supportedType 应返回 LOCAL")
  void supportedType_should_return_LOCAL() {
    assertThat(storage.supportedType()).isEqualTo(StorageType.LOCAL);
  }

  private StorageTarget newTarget() {
    return new StorageTarget(
      "local-test", StorageType.LOCAL, null, null,
      tempDir.toString(), null, null, null, java.util.Map.of()
    );
  }
}
