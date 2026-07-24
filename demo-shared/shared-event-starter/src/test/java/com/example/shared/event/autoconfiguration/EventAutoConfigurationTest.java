package com.example.shared.event.autoconfiguration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.EnableScheduling;

import static org.assertj.core.api.Assertions.assertThat;

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
}
