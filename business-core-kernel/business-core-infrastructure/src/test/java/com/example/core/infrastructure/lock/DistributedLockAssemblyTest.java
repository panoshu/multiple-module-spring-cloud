package com.example.core.infrastructure.lock;

import com.baomidou.lock.LockTemplate;
import com.example.shared.lock.DistributedLock;
import com.example.shared.lock.autoconfigure.DistributedLockAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 批次 71：kernel 分布式锁装配契约。
 * <p>
 * kernel infrastructure 引入 {@code shared-lock-starter} 后，生产上下文应能注册
 * {@link DistributedLock} Bean（Redis 通道），使 {@code FlowOrchestrationService}
 * 的 {@code lockAndRun} 短临界区真正生效，而不是永远走「无锁直通」。
 */
@DisplayName("kernel 分布式锁装配契约（批次 71）")
class DistributedLockAssemblyTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
    .withConfiguration(AutoConfigurations.of(DistributedLockAutoConfiguration.class))
    .withBean(LockTemplate.class, () -> mock(LockTemplate.class))
    .withPropertyValues("shared.lock.connection-check=false");

  @Test
  @DisplayName("引入 starter 后上下文注册唯一的 DistributedLock Bean")
  void lockStarter_registersDistributedLockBean() {
    contextRunner.run(context -> {
      assertThat(context).hasNotFailed();
      assertThat(context).hasSingleBean(DistributedLock.class);
    });
  }

  @Test
  @DisplayName("锁实现为 Lock4jDistributedLock（Redis 通道，非本地降级）")
  void lockImplementation_isRedisBacked() {
    contextRunner.run(context ->
      assertThat(context.getBean(DistributedLock.class).getClass().getName())
        .isEqualTo("com.example.shared.lock.Lock4jDistributedLock"));
  }
}
