package com.example.shared.id;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class DistributedIdGenerator {

  private final SequenceRepository repository;

  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
  // 序列号长度，如 0001
  private static final int SEQ_LEN = 4;
  // 子单后缀长度，如 01
  private static final int SUB_ORDER_LEN = 2;

  // 本地缓存：Key = biz:date:cust, Value = SegmentBuffer
  // 设置 1 小时过期，防止不活跃客户的 Buffer 占用内存
  private final Cache<String, SegmentBuffer> bufferCache = Caffeine.newBuilder()
    .expireAfterAccess(1, TimeUnit.HOURS)
    .maximumSize(50_000) // 根据活跃用户数调整
    .build();

  public DistributedIdGenerator(SequenceRepository repository) {
    this.repository = repository;
  }

  // ==========================================
  // 1. 有状态：生成 Submission ID (依赖 DB/Cache)
  // ==========================================

  /**
   * 生成全局唯一的提交号 (Submission ID)
   * 格式：BIZ + CUST + yyyyMMdd + 0001
   */
  public String nextSubmissionId(String bizCode, String custNo) {
    Assert.hasText(bizCode, "BizCode must not be empty");
    Assert.hasText(custNo, "CustomerNo must not be empty");

    String dateStr = LocalDate.now().format(DATE_FMT);
    String cacheKey = bizCode + ":" + dateStr + ":" + custNo;

    // 从 Caffeine 获取 Buffer (利用 computeIfAbsent 保证只创建一次)
    SegmentBuffer buffer = bufferCache.get(cacheKey,
      k -> new SegmentBuffer(bizCode, custNo, repository));

    long seq = buffer.nextSeq();

    // 拼接：PAY + CUST001 + 20251122 + 0001
    return bizCode + custNo + dateStr + String.format("%0" + SEQ_LEN + "d", seq);
  }

  // ==========================================
  // 2. 无状态：派生 Order ID (纯内存计算)
  // ==========================================

  /**
   * 基于 Submission ID 派生 Order ID
   * 可以在任何服务中调用，无需访问数据库
   * * @param submissionId 原始提交号
   * @param index 子单索引 (从 1 开始)
   * @return 单据号 (SubmissionId + 01)
   */
  public String deriveOrderId(String submissionId, int index) {
    Assert.hasText(submissionId, "SubmissionId must not be empty");
    if (index < 1 || index > 99) {
      throw new IllegalArgumentException("Index must be between 1 and 99");
    }
    // 格式：...0001 + 01
    return submissionId + String.format("%0" + SUB_ORDER_LEN + "d", index);
  }

  // ==========================================
  // 3. 内部缓冲类 (利用 JDK 21 Virtual Threads 优势)
  // ==========================================
  static class SegmentBuffer {
    private final String bizCode;
    private final String custNo;
    private final SequenceRepository repository;

    private long currentCursor = 0;
    private long maxCursor = -1;

    // JDK 21 对 ReentrantLock 有很好的优化
    private final ReentrantLock lock = new ReentrantLock();

    public SegmentBuffer(String bizCode, String custNo, SequenceRepository repo) {
      this.bizCode = bizCode;
      this.custNo = custNo;
      this.repository = repo;
    }

    public long nextSeq() {
      lock.lock();
      try {
        // 双重检查或简单判断
        if (currentCursor > maxCursor) {
          loadNextBatch();
        }
        return currentCursor++;
      } finally {
        lock.unlock();
      }
    }

    private void loadNextBatch() {
      // 这里会触发 DB IO，在虚拟线程下，只会挂起虚拟线程，不阻塞 OS 线程
      IdSegment segment = repository.nextSegment(bizCode, custNo);
      this.currentCursor = segment.start();
      this.maxCursor = segment.end();
    }
  }
}
