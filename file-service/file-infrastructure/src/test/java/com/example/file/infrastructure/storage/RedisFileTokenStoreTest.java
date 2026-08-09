package com.example.file.infrastructure.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("RedisFileTokenStore 一次性 token 标记")
class RedisFileTokenStoreTest {

  private RedissonClient redissonClient;
  private RedisFileTokenStore store;

  @BeforeEach
  void setUp() {
    redissonClient = mock(RedissonClient.class);
    FileTokenProperties properties = new FileTokenProperties();
    store = new RedisFileTokenStore(redissonClient, properties);
  }

  @Test
  @DisplayName("markUsed 首次调用返回 true")
  void should_return_true_when_first_mark() {
    RBucket<String> bucket = mock();
    doReturn(bucket).when(redissonClient).getBucket("file:token:used:tok-001");
    when(bucket.setIfAbsent(eq("1"), any(Duration.class))).thenReturn(true);

    boolean result = store.markUsed("tok-001", Duration.ofMinutes(15));
    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("markUsed 重复调用返回 false")
  void should_return_false_when_repeat_mark() {
    RBucket<String> bucket = mock();
    doReturn(bucket).when(redissonClient).getBucket("file:token:used:tok-001");
    when(bucket.setIfAbsent(eq("1"), any(Duration.class))).thenReturn(false);

    boolean result = store.markUsed("tok-001", Duration.ofMinutes(15));
    assertThat(result).isFalse();
  }

  @Test
  @DisplayName("isUsed 检查 key 是否存在")
  void should_check_is_used() {
    RBucket<String> bucket = mock();
    doReturn(bucket).when(redissonClient).getBucket("file:token:used:tok-001");
    when(bucket.isExists()).thenReturn(true);

    assertThat(store.isUsed("tok-001")).isTrue();
    verify(bucket).isExists();
  }
}
