package com.example.file.infrastructure.storage;

import com.example.file.domain.model.aggregate.valueobject.FileUsage;
import com.example.file.domain.model.aggregate.valueobject.StorageTarget;
import com.example.file.domain.model.aggregate.valueobject.StorageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PropertiesBasedStorageTargetResolverTest {

  @Test
  @DisplayName("resolveByUsage 应返回正确的 StorageTarget")
  void resolveByUsage_should_return_correct_target() {
    PropertiesBasedStorageTargetResolver resolver = newResolver(validProperties());

    StorageTarget source = resolver.resolveByUsage(FileUsage.SOURCE, null);
    assertThat(source.targetId()).isEqualTo("local-source");

    StorageTarget export = resolver.resolveByUsage(FileUsage.EXPORT, null);
    assertThat(export.targetId()).isEqualTo("local-export");
  }

  @Test
  @DisplayName("resolveById 在 target 不存在时应抛异常")
  void resolveById_should_throw_when_target_not_found() {
    PropertiesBasedStorageTargetResolver resolver = newResolver(validProperties());

    assertThatThrownBy(() -> resolver.resolveById("non-existent"))
      .isInstanceOf(RuntimeException.class);
  }

  @Test
  @DisplayName("构造时 routing.target 缺失应抛 IllegalStateException")
  void validate_should_throw_when_routing_target_missing() {
    StorageTargetProperties props = validProperties();
    props.getRouting().setSource("non-existent-target");

    assertThatThrownBy(() -> new PropertiesBasedStorageTargetResolver(props))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("routing.source");
  }

  @Test
  @DisplayName("构造时 OSS target 缺 endpoint 应抛 IllegalArgumentException")
  void validate_should_throw_when_OSS_missing_endpoint() {
    StorageTargetProperties props = validProperties();
    StorageTargetProperties.StorageTargetConfig ossConfig = new StorageTargetProperties.StorageTargetConfig();
    ossConfig.setId("oss-bad");
    ossConfig.setType(StorageType.OSS);
    ossConfig.setBucket("bucket");
    // endpoint 未设置
    props.getTargets().add(ossConfig);
    props.getRouting().setParsed("oss-bad");

    assertThatThrownBy(() -> new PropertiesBasedStorageTargetResolver(props))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("endpoint");
  }

  @Test
  @DisplayName("listAll 应返回所有配置的 target")
  void listAll_should_return_all_targets() {
    PropertiesBasedStorageTargetResolver resolver = newResolver(validProperties());
    List<StorageTarget> all = resolver.listAll();
    assertThat(all).hasSize(2);
  }

  private StorageTargetProperties validProperties() {
    StorageTargetProperties props = new StorageTargetProperties();
    List<StorageTargetProperties.StorageTargetConfig> targets = new ArrayList<>();

    StorageTargetProperties.StorageTargetConfig source = new StorageTargetProperties.StorageTargetConfig();
    source.setId("local-source");
    source.setType(StorageType.LOCAL);
    source.setBasePath("/data/source");
    targets.add(source);

    StorageTargetProperties.StorageTargetConfig export = new StorageTargetProperties.StorageTargetConfig();
    export.setId("local-export");
    export.setType(StorageType.LOCAL);
    export.setBasePath("/data/export");
    targets.add(export);

    props.setTargets(targets);

    StorageTargetProperties.RoutingConfig routing = new StorageTargetProperties.RoutingConfig();
    routing.setSource("local-source");
    routing.setParsed("local-source");
    routing.setExport("local-export");
    routing.setArchive("local-export");
    props.setRouting(routing);

    return props;
  }

  private PropertiesBasedStorageTargetResolver newResolver(StorageTargetProperties props) {
    return new PropertiesBasedStorageTargetResolver(props);
  }
}
