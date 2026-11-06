package com.example.shared.event.job;

import com.example.shared.event.contract.EventDispatcher;
import com.example.shared.event.contract.EventStore;
import com.example.shared.event.deliverer.EventDeliverer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 事件投递补偿任务（2026-11-06 去分布式锁重构）.
 *
 * <p><b>互斥方式</b>：DB 行级认领（{@link EventStore#claimPendingLogs}——
 * FOR UPDATE SKIP LOCKED + PROCESSING 状态标记）。多实例并发安全且
 * <b>天然并行</b>（各自认领互不重叠的行），无单点瓶颈；处理方崩溃的行
 * 由 PROCESSING 超时回收兜底（默认 5 分钟）.
 *
 * <p><b>原方案为何被替换</b>：分布式锁保护"整个扫描任务"是粗粒度互斥，
 * 且为 DB 里的行做互斥却引入第三个系统（Redis）——锁的可靠性反而低于
 * 被保护的数据本身。互斥资源在 DB，就该用 DB 的行锁.</p>
 *
 * @author shared-event-core
 */
@Slf4j
@RequiredArgsConstructor
public class EventRecoveryJob {

  private static final int BATCH_SIZE = 100;

  private final EventStore eventStore;
  private final EventDeliverer eventDeliverer;
  private final List<EventDispatcher> dispatchers;

  private Map<String, EventDispatcher> dispatcherMap;

  @PostConstruct
  public void init() {
    this.dispatcherMap = dispatchers.stream()
      .filter(EventDispatcher::isRemote)
      .collect(Collectors.toMap(EventDispatcher::getChannelName, d -> d));
  }

  @Scheduled(fixedDelay = 30_000)
  public void recover() {
    List<EventStore.PendingEntry> entries = eventStore.claimPendingLogs(BATCH_SIZE);
    for (EventStore.PendingEntry entry : entries) {
      EventDispatcher dispatcher = dispatcherMap.get(entry.channel());
      if (dispatcher == null) {
        // 无对应远端渠道（如 rocketmq 未配置）：回写失败，走重试节奏
        eventStore.markFailure(entry.logId(),
          new IllegalStateException("No remote dispatcher for channel: " + entry.channel()));
        continue;
      }
      // 补偿流：直接用反序列化好的 integrationEvent
      // （deliverRecovered 内部处理 markSuccess/markFailure，异常不外抛）
      eventDeliverer.deliverRecovered(dispatcher, entry.integrationEvent(),
        entry.integrationType(), entry.logId());
    }
  }
}
