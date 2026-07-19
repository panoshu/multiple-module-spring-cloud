package com.example.file.domain.model.aggregate.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageTargetTest {

    @Test
    @DisplayName("LOCAL 类型必须有 basePath")
    void constructor_should_validate_LOCAL_required_fields() {
        assertThatThrownBy(() -> new StorageTarget(
            "local-1", StorageType.LOCAL, null, null, null, null, null, null, Map.of()
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("basePath");
    }

    @Test
    @DisplayName("OSS 类型必须有 endpoint+bucket+AK/SK")
    void constructor_should_validate_OSS_required_fields() {
        assertThatThrownBy(() -> new StorageTarget(
            "oss-1", StorageType.OSS, null, "bucket", "base", null, null, null, Map.of()
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("endpoint");
    }

    @Test
    @DisplayName("NAS 类型必须有 bucket 和 basePath")
    void constructor_should_validate_NAS_required_fields() {
        assertThatThrownBy(() -> new StorageTarget(
            "nas-1", StorageType.NAS, null, null, null, null, null, null, Map.of()
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("bucket");
    }

    @Test
    @DisplayName("合法的 LOCAL 配置应创建成功")
    void constructor_should_pass_when_LOCAL_valid() {
        StorageTarget target = new StorageTarget(
            "local-1", StorageType.LOCAL, null, null, "/data/files", null, null, null, Map.of()
        );
        assertThat(target.targetId()).isEqualTo("local-1");
        assertThat(target.type()).isEqualTo(StorageType.LOCAL);
        assertThat(target.basePath()).isEqualTo("/data/files");
    }

    @Test
    @DisplayName("合法的 OSS 配置应创建成功")
    void constructor_should_pass_when_OSS_valid() {
        StorageTarget target = new StorageTarget(
            "oss-1", StorageType.OSS, "https://oss.example.com", "bucket", "base",
            null, "ak", "sk", Map.of("region", "cn-hangzhou")
        );
        assertThat(target.endpoint()).isEqualTo("https://oss.example.com");
        assertThat(target.accessKeyId()).isEqualTo("ak");
    }
}
