package com.example.shared.lock;

import com.example.shared.exception.SystemException;
import com.example.shared.lock.errorcode.LockErrorDefinition;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public interface DistributedLock {

  String LOCAL_BEAN_NAME = "localDistributedLock";
  String REDISSON_BEAN_NAME = "redissonDistributedLock";

  boolean tryLock(String key, long waitTime, long leaseTime, TimeUnit unit);

  void unlock(String key);

  // 模板方法
  default <T> T lockAndRun(String key, long waitTime, long leaseTime, TimeUnit unit, Supplier<T> task) {
    if (tryLock(key, waitTime, leaseTime, unit)) {
      try {
        return task.get();
      } finally {
        unlock(key);
      }
    } else {
      throw new SystemException(LockErrorDefinition.GET_LOCK_FAILED).withLogDetail("Failed to acquire lock: " + key);
    }
  }

  default <T> T lockAndRun(String key, Supplier<T> task) {
    return lockAndRun(key, 5, -1, TimeUnit.SECONDS, task);
  }
}
