package com.example.core.infrastructure.business.repository;

import com.example.core.domain.business.aggregate.root.BusinessBatch;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.aggregate.valueobject.enums.status.BatchStatus;
import com.example.core.infrastructure.business.converter.BusinessBatchConverter;
import com.example.core.infrastructure.business.entity.BusinessBatchDO;
import com.example.core.infrastructure.business.mapper.BusinessBatchMapper;
import com.example.core.types.BatchId;
import com.example.core.types.Channel;
import com.example.core.types.CustomerNo;
import com.example.core.types.OperatingMode;
import com.example.core.types.PlanNo;
import com.example.core.types.ProductNo;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.domain.event.EventBus;
import com.example.shared.exception.SystemException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * §21.2 第 3 条乐观锁并发写冲突验收（批次 43-2）。
 * <p>
 * 用状态化 fake mapper 模拟数据库的乐观锁：{@code selectOneById} 返回当前持久化版本，
 * {@code update} 以 DO 版本做 CAS（等价于 MyBatis-Flex 的 {@code @Column(version=true)}
 * 用版本做 WHERE 条件）。{@code bothRead} 屏障保证两个节点都读到同一版本后才继续，
 * 结果不依赖线程调度顺序。
 * <p>
 * 场景：两个节点各基于 version=3 修改批次后并发 {@code save}。CAS 保证只有一个 update
 * 生效，另一个得到 0 行更新并抛 {@code OPTIMISTIC_LOCK_FAILURE}；最终数据库版本只 +1，
 * 先写入的新状态不会被后写入的旧副本覆盖（无丢失更新）。
 */
@DisplayName("乐观锁并发写冲突不覆盖新状态（批次 43-2）")
class OptimisticLockConcurrencyTest {

  private BusinessBatchRepositoryImpl repository;
  private AtomicInteger dbVersion;
  private CountDownLatch bothRead;

  @BeforeEach
  void setUp() {
    BusinessBatchMapper mapper = mock(BusinessBatchMapper.class);
    BusinessBatchConverter converter = mock(BusinessBatchConverter.class);
    EventBus eventBus = mock(EventBus.class);
    repository = new BusinessBatchRepositoryImpl(eventBus, mapper, converter);

    dbVersion = new AtomicInteger(3);
    bothRead = new CountDownLatch(2);

    // 两个节点都在 update 之前读到同一版本（屏障在 select 内）
    when(mapper.selectOneById(any())).thenAnswer(inv -> {
      BusinessBatchDO existing = new BusinessBatchDO();
      existing.setId("B-1");
      existing.setVersion(dbVersion.get());
      bothRead.countDown();
      bothRead.await(5, TimeUnit.SECONDS);
      return existing;
    });

    when(converter.toDO(any(BusinessBatch.class))).thenAnswer(inv -> {
      BusinessBatchDO aDo = new BusinessBatchDO();
      aDo.setId("B-1");
      aDo.setVersion(3);
      return aDo;
    });

    // 数据库侧乐观锁：仅当 DO 版本等于当前库版本才 +1 并返回 1，否则 0 行更新
    when(mapper.update(any(BusinessBatchDO.class))).thenAnswer(inv -> {
      BusinessBatchDO aDo = inv.getArgument(0);
      int expected = aDo.getVersion();
      return dbVersion.compareAndSet(expected, expected + 1) ? 1 : 0;
    });
  }

  @Test
  @DisplayName("两节点基于同一版本并发保存：一个成功一个冲突，新状态不被覆盖")
  void concurrentSave_sameVersion_conflictsWithoutOverwrite() throws Exception {
    BusinessBatch nodeA = batchWithVersion(3);
    BusinessBatch nodeB = batchWithVersion(3);
    nodeA.receiveApplications(1, operator());
    nodeB.receiveApplications(2, operator());

    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Throwable>> futures = new ArrayList<>();
    for (BusinessBatch batch : List.of(nodeA, nodeB)) {
      futures.add(pool.submit(() -> {
        try {
          start.await();
          repository.save(batch);
          return null;
        } catch (Throwable t) {
          return t;
        }
      }));
    }
    start.countDown();

    List<Throwable> failures = new ArrayList<>();
    for (Future<Throwable> future : futures) {
      Throwable failure = future.get(5, TimeUnit.SECONDS);
      if (failure != null) {
        failures.add(failure);
      }
    }
    pool.shutdownNow();

    assertThat(failures).hasSize(1);
    assertThat(failures.get(0)).isInstanceOf(SystemException.class);
    // 只发生一次有效写入：先写入的新状态未被后写入的旧副本覆盖
    assertThat(dbVersion.get()).isEqualTo(4);
  }

  private BusinessBatch batchWithVersion(long version) {
    LocalDateTime now = LocalDateTime.now();
    return BusinessBatch.reconstitute(
      BatchId.of("B-1"), "U-C", "U-U", now.minusDays(1), now, Version.of(version),
      context(), operator(), BatchStatus.PROCESSING, 1, 0, 0, List.of()
    );
  }

  private BusinessContext context() {
    return new BusinessContext(
      BusinessType.ACC_PLAN_CREATE,
      CustomerNo.of("C-001"), "客户",
      ProductNo.of("P-001"), "产品",
      PlanNo.of("PL-001"), "方案",
      OperatingMode.TRUSTEE_ONLY, AccountManager.CJP
    );
  }

  private OperatorInfo operator() {
    return new OperatorInfo(Channel.NETAPP, "U-TEST", "操作人", false);
  }
}
