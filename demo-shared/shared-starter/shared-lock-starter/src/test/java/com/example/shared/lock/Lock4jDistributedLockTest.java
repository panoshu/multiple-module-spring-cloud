package com.example.shared.lock;

import com.baomidou.lock.LockInfo;
import com.baomidou.lock.LockTemplate;
import com.baomidou.lock.executor.RedisTemplateLockExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link Lock4jDistributedLock} 契约测试。
 *
 * <p>回归：lockAndRun 的 Supplier 返回 null（void 语义的编排方法）时，
 * Optional.map 会塌缩成 empty，导致任务成功执行并释放锁后仍抛「获取锁失败」。</p>
 */
class Lock4jDistributedLockTest {

  @Test
  @DisplayName("任务返回 null 时不应误报获取锁失败，且必须释放锁")
  void lockAndRun_allowsNullTaskResult() {
    LockTemplate template = mock(LockTemplate.class);
    LockInfo info = new LockInfo(
      "kernel:application:APP-1", "owner", 10_000L, 3_000L, 1, null,
      mock(RedisTemplateLockExecutor.class));
    when(template.lock(anyString(), anyLong(), anyLong(), any())).thenReturn(info);
    when(template.releaseLock(any())).thenReturn(true);
    Lock4jDistributedLock lock = new Lock4jDistributedLock(template);
    AtomicBoolean executed = new AtomicBoolean();

    assertThatCode(() -> lock.lockAndRun(
      "kernel:application:APP-1", 3, 10, TimeUnit.SECONDS, () -> {
        executed.set(true);
        return null;
      })).doesNotThrowAnyException();

    assertThat(executed).isTrue();
    verify(template).releaseLock(info);
  }
}
