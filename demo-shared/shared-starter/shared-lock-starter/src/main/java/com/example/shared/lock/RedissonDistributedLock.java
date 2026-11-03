package com.example.shared.lock;

import com.example.shared.exception.SystemException;
import com.example.shared.lock.errorcode.LockErrorDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@RequiredArgsConstructor
public class RedissonDistributedLock implements DistributedLock {

  private final RedissonClient redissonClient;

  @Override
  public boolean tryLock(String key, long waitTime, long leaseTime, TimeUnit unit) {
    RLock lock = redissonClient.getLock(key);
    try {
      return lock.tryLock(waitTime, leaseTime, unit);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  @Override
  public void unlock(String key) {
    RLock lock = redissonClient.getLock(key);
    if (lock.isHeldByCurrentThread()) {
      lock.unlock();
    }
  }

  @Override
  public <T> T lockAndRun(String key, long waitTime, long leaseTime, TimeUnit unit, Supplier<T> task) {
    RLock lock = redissonClient.getLock(key);
    boolean locked = false;
    try {
      locked = lock.tryLock(waitTime, leaseTime, unit);
      if (locked) {
        return task.get();
      } else {
        throw new SystemException(LockErrorDefinition.GET_LOCK_FAILED).withLogDetail("资源已被锁定, key:" + key);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new SystemException(LockErrorDefinition.GET_LOCK_FAILED, e).withLogDetail("获取锁失败, key:" + key);
    } finally {
      if (locked && lock.isHeldByCurrentThread()) {
        lock.unlock();
      }
    }
  }
}
