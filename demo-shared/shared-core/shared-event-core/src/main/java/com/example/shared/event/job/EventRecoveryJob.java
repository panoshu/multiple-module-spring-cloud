package com.example.shared.event.job;

import com.example.shared.event.contract.EventDispatcher;
import com.example.shared.event.contract.EventStore;
import com.example.shared.event.deliverer.EventDeliverer;
import com.example.shared.lock.DistributedLock;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class EventRecoveryJob {

  private final EventStore eventStore;
  private final EventDeliverer eventDeliverer;
  private final List<EventDispatcher> dispatchers;
  private final DistributedLock distributedLock;

  private Map<String, EventDispatcher> dispatcherMap;

  @PostConstruct
  public void init() {
    this.dispatcherMap = dispatchers.stream()
      .filter(EventDispatcher::isRemote)
      .collect(Collectors.toMap(EventDispatcher::getChannelName, d -> d));
  }

  @Scheduled(fixedDelay = 30_000)
  public void recover() {
    // 契约 v2：显式句柄（try-with-resources 保证释放）
    try (var ignored = distributedLock
        .tryLock("job:event-recovery", 0, 20, TimeUnit.SECONDS)
        .orElse(null)) {
      if (ignored == null) {
        return; // 其他实例持有锁，本轮跳过
      }
      List<EventStore.PendingEntry> entries = eventStore.findPendingLogs(100);
      for (EventStore.PendingEntry entry : entries) {
        EventDispatcher dispatcher = dispatcherMap.get(entry.channel());
        if (dispatcher != null) {
          // 补偿流：直接用反序列化好的 integrationEvent
          eventDeliverer.deliverRecovered(dispatcher, entry.integrationEvent(),
            entry.integrationType(), entry.logId());
        }
      }
    }
  }
}
