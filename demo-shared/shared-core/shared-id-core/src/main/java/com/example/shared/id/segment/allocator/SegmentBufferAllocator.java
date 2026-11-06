package com.example.shared.id.segment.allocator;

import com.example.shared.id.segment.model.IdSegment;
import com.example.shared.id.segment.repository.SegmentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 双 Buffer + 异步预加载的号段分配器（美团 Leaf segment 模式，2026-11-06 去锁重构）.
 *
 * <p><b>设计要点</b>（对照被替换的"分布式锁 + 同步取段"版本）：
 * <ul>
 *   <li><b>正确性不依赖锁</b>：号段不重叠由 DB 原子 UPDATE
 *       （{@code max_id = max_id + step}）保证——锁从来不是正确性来源，
 *       只是防重复取段的优化，如今由预加载+单写者状态机替代</li>
 *   <li><b>取号路径纯内存</b>：游标 CAS 递增（纳秒级），不碰锁不碰 DB；
 *       Redis 抖动不再影响发号</li>
 *   <li><b>异步预加载</b>：当前段剩余 ≤ 10% 时后台线程（CompletableFuture）
 *       预取下一段——同步路径几乎不会号尽，消除取号毛刺</li>
 *   <li><b>同步兜底无锁</b>：极端情况下预加载未完成即号尽，
 *       进程内 synchronized 取段（DB 原子已保证跨实例正确）</li>
 * </ul></p>
 *
 * <p><b>一致性模型</b>：单写者状态机——next 段的加载与 current 切换只发生在
 * 预加载线程 / 持有 monitor 的兜底路径；取号线程只读 current + CAS 游标。
 * 跳号可能（段切换瞬间的浪费），发号器允许跳号不允许重复.</p>
 *
 * @author shared-id-core
 */
@Slf4j
public class SegmentBufferAllocator implements SegmentAllocator {

  /**
   * 预加载触发阈值：当前段剩余号量占比.
   */
  private static final double PRELOAD_THRESHOLD = 0.10;

  private final SegmentRepository repository;
  private final Cache bufferCache;

  public SegmentBufferAllocator(SegmentRepository repository, CacheManager cacheManager) {
    this.repository = repository;
    this.bufferCache = cacheManager.getCache("segment_buffer");
  }

  @Override
  public long nextRawId(String sequenceKey) {
    SegmentBuffer buffer = bufferCache.get(sequenceKey, SegmentBuffer::new);
    if (buffer == null) {
      throw new IllegalStateException("SegmentBuffer unavailable for key: " + sequenceKey);
    }
    return buffer.nextIdOrLoad(sequenceKey);
  }

  /**
   * 双缓冲（单写者状态机）.
   */
  private final class SegmentBuffer {

    /** 当前消费段（volatile 读：取号线程无锁访问）. */
    private volatile Segment current;
    /** 预加载段（仅预加载线程 / synchronized 兜底路径写入）. */
    private volatile Segment next;
    /** 预加载任务防重复提交（CAS）. */
    private final AtomicBoolean preloadScheduled = new AtomicBoolean(false);

    long nextIdOrLoad(String key) {
      Segment seg = current;
      if (seg != null) {
        Long id = seg.nextId();
        if (id != null) {
          maybePreload(key, seg);
          return id;
        }
      }
      return loadSync(key);
    }

    /**
     * 低水位异步预加载（取号路径顺手触发，非阻塞）.
     */
    private void maybePreload(String key, Segment consuming) {
      if (!consuming.lowWater(PRELOAD_THRESHOLD) || next != null) {
        return;
      }
      if (!preloadScheduled.compareAndSet(false, true)) {
        return; // 已有预加载在途
      }
      CompletableFuture.runAsync(() -> {
        try {
          Segment seg = toSegment(repository.fetchNextSegment(key));
          synchronized (this) {
            if (current == null) {
              current = seg; // 首段（竞争兜底路径先行加载的场景）
            } else {
              next = seg;
            }
          }
        } catch (Exception e) {
          log.warn("号段预加载失败（下次取号走同步兜底）, key={}", key, e);
        } finally {
          preloadScheduled.set(false);
        }
      });
    }

    /**
     * 同步兜底取段（进程内互斥；跨实例正确性由 DB 原子 UPDATE 保证）.
     */
    private synchronized long loadSync(String key) {
      // Double-Check：预加载线程可能已就位 next
      if (current != null) {
        Long retry = current.nextId();
        if (retry != null) {
          return retry;
        }
      }
      Segment ready = next;
      if (ready != null) {
        current = ready;
        next = null;
        Long id = ready.nextId();
        if (id != null) {
          return id;
        }
      }
      // DB 取新段（原子 UPDATE 保证跨实例不重叠）
      Segment fresh = toSegment(repository.fetchNextSegment(key));
      current = fresh;
      Long id = fresh.nextId();
      if (id == null) {
        throw new IllegalStateException("Fresh segment yields no id, key: " + key);
      }
      return id;
    }
  }

  private static Segment toSegment(IdSegment seg) {
    return new Segment(seg.minId(), seg.maxId());
  }

  /**
   * 号段（不可变）+ 取号游标.
   */
  private record Segment(long minId, long maxId, AtomicLong cursor) {

    Segment(long minId, long maxId) {
      this(minId, maxId, new AtomicLong(minId));
    }

    /**
     * CAS 递增取号；null = 本段已尽.
     */
    Long nextId() {
      long val = cursor.incrementAndGet();
      return val <= maxId ? val : null;
    }

    /**
     * 低水位判断（剩余号量 ≤ 段长 × threshold）.
     */
    boolean lowWater(double threshold) {
      long remaining = maxId - cursor.get();
      return remaining <= Math.max(1, (long) ((maxId - minId + 1) * threshold));
    }
  }
}
