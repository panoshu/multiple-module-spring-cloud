package com.example.shared.lock;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public interface DistributedLock {
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
      throw new RuntimeException("Failed to acquire lock: %s".formatted(key));
    }
  }

  default <T> T lockAndRun(String key, Supplier<T> task) {
    return lockAndRun(key, 5, -1, TimeUnit.SECONDS, task);
  }
}
