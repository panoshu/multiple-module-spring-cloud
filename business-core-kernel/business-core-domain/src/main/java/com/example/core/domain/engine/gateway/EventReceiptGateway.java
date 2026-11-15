package com.example.core.domain.engine.gateway;

/**
 * 事件消费回执 SPI（spec §7.4 / §9.2 阶段 C）。
 * <p>
 * 按 {@code eventId + consumer} 登记首次消费，重复事件直接短路，防止 MQ / Redis /
 * HTTP 重投造成重复推进。实现位于 infrastructure，domain 不依赖具体存储。
 */
public interface EventReceiptGateway {

  /**
   * 尝试登记一次事件消费。
   *
   * @return true 表示首次消费、可继续执行；false 表示重复事件，应直接返回成功
   */
  boolean tryConsume(String eventId, String consumer);
}
