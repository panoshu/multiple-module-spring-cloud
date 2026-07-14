package com.example.shared.event.job;

import com.example.shared.cache.lock.DistributedLock;
import com.example.shared.domain.event.EventDispatcher;
import com.example.shared.domain.event.EventStore;
import com.example.shared.event.deliverer.EventDeliverer;
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
  private final EventDeliverer eventDeliverer; // 注入投递器
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
    if (!distributedLock.tryLock("job:event-recovery", 0, 20, TimeUnit.SECONDS)) {
      return;
    }

    try {
      List<EventStore.PendingEntry> entries = eventStore.findPendingLogs(100);
      for (EventStore.PendingEntry entry : entries) {
        EventDispatcher dispatcher = dispatcherMap.get(entry.channel());
        if (dispatcher != null) {
          // 复用同一套投递逻辑
          eventDeliverer.deliver(dispatcher, entry.event(), entry.logId());
        }
      }
    } finally {
      distributedLock.unlock("job:event-recovery");
    }
  }
}
