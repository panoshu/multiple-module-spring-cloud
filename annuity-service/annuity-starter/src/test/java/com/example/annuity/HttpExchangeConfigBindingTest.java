package com.example.annuity;

import io.github.danielliu1123.httpexchange.HttpExchangeProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验 application-local.yml 中的 http-exchange 配置能被 3.5.5 库正确绑定。
 *
 * <p>背景：旧写法 {@code httpexchange.clients.<package>.url} 在 3.5.5 中不存在，
 * 配置静默失效。本测试锁定新写法与通道数量，防止回退。</p>
 */
@DisplayName("http-exchange 配置绑定")
class HttpExchangeConfigBindingTest {

  @Test
  @DisplayName("application-local.yml 绑定出 approval/file/integration/auth 四个通道")
  void bindsChannelsFromLocalYaml() throws Exception {
    List<PropertySource<?>> sources = new YamlPropertySourceLoader()
      .load("application-local", new ClassPathResource("application-local.yml"));
    Binder binder = new Binder(ConfigurationPropertySources.from(sources));
    HttpExchangeProperties properties = binder
      .bind("http-exchange", Bindable.of(HttpExchangeProperties.class))
      .orElseThrow(() -> new IllegalStateException("http-exchange 配置未绑定"));

    assertThat(properties.getChannels()).hasSize(4);
    assertThat(properties.getChannels())
      .extracting(HttpExchangeProperties.Channel::getName)
      .containsExactly("approval", "file", "integration", "auth");
    assertThat(properties.getChannels())
      .allSatisfy(channel -> {
        assertThat(channel.getBaseUrl()).startsWith("lb://");
        assertThat(channel.getClients()).isNotEmpty();
      });
  }
}
