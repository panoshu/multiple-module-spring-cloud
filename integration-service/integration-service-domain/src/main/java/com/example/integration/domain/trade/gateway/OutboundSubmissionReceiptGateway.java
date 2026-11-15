package com.example.integration.domain.trade.gateway;

/**
 * 出站文件提交幂等回执（I-02）。
 * <p>以 (eventId, outboundFileId) 为唯一键：首次抢占成功才允许调用外部系统；
 * 成功保留 SUCCESS 回执，失败释放以便 RocketMQ 重投时重试。</p>
 */
public interface OutboundSubmissionReceiptGateway {

  /**
   * 尝试抢占处理权。
   *
   * @return true=首次处理；false=已成功处理过，应跳过
   */
  boolean tryAcquire(String eventId, String outboundFileId);

  void markSuccess(String eventId, String outboundFileId);

  /**
   * 提交失败时释放回执，允许消息重投后重试。
   */
  void release(String eventId, String outboundFileId);
}
