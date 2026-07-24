package com.example.shared.lock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DistributedLockFactory} 单元测试。
 * <p>
 * 验证锁的查找契约：
 * <ul>
 *   <li>{@code LockType.REDIS} 查找 bean 名 {@code redissonDistributedLock}</li>
 *   <li>{@code LockType.LOCAL} 查找 bean 名 {@code localDistributedLock}</li>
 *   <li>REDIS 锁不存在时降级到 LOCAL 锁</li>
 * </ul>
 * <p>
 * 此测试锁定的契约要求 {@code DistributedLockAutoConfiguration} 注册的 bean 名必须与上述一致，
 * 否则 {@code getLock(REDIS)} 会静默降级为本地锁，导致分布式防击穿失效。
 *
 * @author panoshu
 * @since 2026/7/24
 */
@DisplayName("DistributedLockFactory 锁查找测试")
class DistributedLockFactoryTest {

    /** 标记用的 LOCAL 锁实现 */
    private static DistributedLock localLock() {
        return new LocalDistributedLock();
    }

    /** 标记用的 REDIS 锁实现（用 LocalDistributedLock 代替以区分实例引用） */
    private static DistributedLock redisLock() {
        return new LocalDistributedLock();
    }

    @Test
    @DisplayName("getLock(REDIS) 应返回 redissonDistributedLock bean")
    void getLock_redis_should_return_redisson_lock() {
        DistributedLock local = localLock();
        DistributedLock redis = redisLock();
        Map<String, DistributedLock> lockMap = new HashMap<>();
        lockMap.put("localDistributedLock", local);
        lockMap.put("redissonDistributedLock", redis);

        DistributedLockFactory factory = new DistributedLockFactory(lockMap);

        assertThat(factory.getLock(DistributedLockFactory.LockType.REDIS)).isSameAs(redis);
    }

    @Test
    @DisplayName("getLock(LOCAL) 应返回 localDistributedLock bean")
    void getLock_local_should_return_local_lock() {
        DistributedLock local = localLock();
        DistributedLock redis = redisLock();
        Map<String, DistributedLock> lockMap = new HashMap<>();
        lockMap.put("localDistributedLock", local);
        lockMap.put("redissonDistributedLock", redis);

        DistributedLockFactory factory = new DistributedLockFactory(lockMap);

        assertThat(factory.getLock(DistributedLockFactory.LockType.LOCAL)).isSameAs(local);
    }

    @Test
    @DisplayName("getLock(REDIS) 在 redissonDistributedLock 不存在时应降级到 localDistributedLock")
    void getLock_redis_should_fallback_to_local_when_redis_missing() {
        DistributedLock local = localLock();
        Map<String, DistributedLock> lockMap = new HashMap<>();
        lockMap.put("localDistributedLock", local);

        DistributedLockFactory factory = new DistributedLockFactory(lockMap);

        assertThat(factory.getLock(DistributedLockFactory.LockType.REDIS)).isSameAs(local);
    }

    @Test
    @DisplayName("getLock(REDIS) 在两个锁都不存在时应返回 null")
    void getLock_redis_should_return_null_when_both_missing() {
        Map<String, DistributedLock> lockMap = new HashMap<>();

        DistributedLockFactory factory = new DistributedLockFactory(lockMap);

        assertThat(factory.getLock(DistributedLockFactory.LockType.REDIS)).isNull();
    }

    @Test
    @DisplayName("使用错误 bean 名（如 redissonLockL2）时 getLock(REDIS) 应降级而非返回该锁")
    void getLock_redis_should_not_match_wrong_bean_name() {
        // 模拟当前 bug：配置注册了错误 bean 名 redissonLockL2 而非 redissonDistributedLock
        DistributedLock wrongNamed = redisLock();
        DistributedLock local = localLock();
        Map<String, DistributedLock> lockMap = new HashMap<>();
        lockMap.put("redissonLockL2", wrongNamed);  // 错误的 bean 名
        lockMap.put("localDistributedLock", local);

        DistributedLockFactory factory = new DistributedLockFactory(lockMap);

        // getLock(REDIS) 找不到 redissonDistributedLock，降级到 local
        assertThat(factory.getLock(DistributedLockFactory.LockType.REDIS))
            .as("错误 bean 名应导致降级到 local，而非返回错误命名的锁")
            .isSameAs(local)
            .isNotSameAs(wrongNamed);
    }
}
