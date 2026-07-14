package com.example.shared.id.segment.allocator;

import com.example.shared.cache.lock.DistributedLock;
import com.example.shared.id.segment.model.IdSegment;
import com.example.shared.id.segment.repository.SegmentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 带有双 Buffer 缓冲的分配器
 */
@Slf4j
public class SegmentBufferAllocator implements SegmentAllocator {

  private final SegmentRepository repository;
  private final DistributedLock lock;
  private final Cache bufferCache; // 使用 CacheManager 管理 Buffer 实例

  public SegmentBufferAllocator(SegmentRepository repository, DistributedLock lock, CacheManager cacheManager) {
    this.repository = repository;
    this.lock = lock;
    this.bufferCache = cacheManager.getCache("segment_buffer");
  }

  @Override
  public long nextRawId(String sequenceKey) {
    // 1. 获取 Buffer (Caffeine 本地缓存)
    SegmentBuffer buffer = bufferCache.get(sequenceKey, SegmentBuffer::new);

    // 2. 尝试拿号
    Long id = Objects.requireNonNull(buffer).nextId();
    if (id != null) {
      return id;
    }

    // 3. 没号了，去 DB 加载 (加分布式锁)
    return loadFromDb(sequenceKey, buffer);
  }

  private long loadFromDb(String key, SegmentBuffer buffer) {
    return lock.lockAndRun("lock:segment:" + key, 3, -1, TimeUnit.SECONDS, () -> {
      // Double Check
      Long retry = buffer.nextId();
      if (retry != null) {
        return retry;
      }

      // Load & Refresh
      IdSegment segment = repository.fetchNextSegment(key);
      buffer.refresh(segment);

      return buffer.nextId();
    });
  }

  // 内部 Buffer 类
  private static class SegmentBuffer {
    private final AtomicLong current = new AtomicLong(0);
    private volatile long max = 0;
    private volatile boolean ready = false;

    public Long nextId() {
      if (!ready) {
        return null;
      }
      long val = current.incrementAndGet();
      return val > max ? null : val;
    }

    public void refresh(IdSegment seg) {
      this.current.set(seg.minId()); // minId 是上一段 max，所以 incrementAndGet 正好是 min+1
      this.max = seg.maxId();
      this.ready = true;
    }
  }
}
