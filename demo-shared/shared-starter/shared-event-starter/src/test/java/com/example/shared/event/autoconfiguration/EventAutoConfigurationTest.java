package com.example.shared.event.autoconfiguration;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.event.contract.EventStore;
import com.example.shared.identifier.id.EventId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * {@link EventAutoConfiguration} 配置元数据验证测试。
 * <p>
 * 验证自动配置类正确启用了 Spring 调度支持，
 * 使 {@code EventRecoveryJob} 的 {@code @Scheduled} 补偿任务能被容器调度执行。
 *
 * @author panoshu
 * @since 2026/7/24
 */
@DisplayName("EventAutoConfiguration 配置验证")
class EventAutoConfigurationTest {

  @Test
  @DisplayName("应标注 @EnableScheduling 以激活 EventRecoveryJob 的 @Scheduled 补偿任务")
  void should_enable_scheduling() {
    assertThat(EventAutoConfiguration.class.isAnnotationPresent(EnableScheduling.class))
      .as("EventAutoConfiguration 必须标注 @EnableScheduling，否则 @Scheduled 补偿任务永不执行")
      .isTrue();
  }

  @Test
  @DisplayName("eventStore 应自持 ObjectMapper，不注入宿主的 @Primary mapper")
  void eventStore_should_not_depend_on_host_object_mapper() {
    // 业务服务可能提供 @Primary 的裸 ObjectMapper（未注册 JavaTimeModule），
    // 注入它会让含 LocalDateTime 的领域事件序列化失败、Outbox 落库被上层 catch 吞掉。
    Method[] methods = Arrays.stream(EventAutoConfiguration.class.getDeclaredMethods())
      .filter(m -> m.getName().equals("eventStore"))
      .toArray(Method[]::new);
    assertThat(methods).hasSize(1);
    assertThat(methods[0].getParameterTypes()).containsExactly(JdbcClient.class);
  }

  @Test
  @DisplayName("配置产出的 EventStore 应能落库含 LocalDateTime 的领域事件")
  void eventStore_should_persist_event_with_local_date_time() throws Exception {
    DriverManagerDataSource ds = new DriverManagerDataSource(
      "jdbc:h2:mem:event_autoconfig_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    JdbcClient jdbcClient = JdbcClient.create(ds);
    jdbcClient.sql("""
      CREATE TABLE sys_event_store (
        event_id VARCHAR(64) PRIMARY KEY,
        event_type VARCHAR(255) NOT NULL,
        integration_type VARCHAR(64),
        occurred_on TIMESTAMP NOT NULL,
        domain_payload TEXT NOT NULL,
        integration_payload TEXT,
        created_at TIMESTAMP
      )""").update();

    EventStore store = new EventAutoConfiguration().eventStore(jdbcClient);
    TestEvent event = new TestEvent(EventId.generate(), LocalDateTime.of(2026, 11, 10, 9, 30), "payload");

    assertThatCode(() -> store.save(event, null, null)).doesNotThrowAnyException();

    Long count = jdbcClient.sql("SELECT COUNT(*) FROM sys_event_store")
      .query(Long.class).single();
    assertThat(count).isEqualTo(1L);

    String payload = jdbcClient.sql("SELECT domain_payload FROM sys_event_store")
      .query(String.class).single();
    JsonNode json = new ObjectMapper().registerModule(new JavaTimeModule()).readTree(payload);
    assertThat(json.has("occurredOn")).isTrue();
    assertThat(json.get("eventId").isTextual())
      .as("DddJacksonModule 应把强类型 ID 序列化为字符串，而非嵌套对象")
      .isTrue();
    assertThat(json.get("eventId").asText()).isEqualTo(event.eventId().value());
  }

  record TestEvent(EventId eventId, LocalDateTime occurredOn, String data) implements DomainEvent {
  }
}
