package com.example.shared.event.dispatcher;

/**
 * Redis 事件通道信封（2026-11-08 批次1：跨服务事件收发闭环）.
 *
 * <p>包装集成事件载荷，携带发布方实例标识——订阅端据此跳过自己发布的
 * 消息（防回声：发布方本地的 SpringEventDispatcher 已同步分发过一次）.</p>
 *
 * @param sourceInstanceId 发布方 JVM 实例标识（UUID，进程级单例）
 * @param payload          集成事件载荷（DTO；经 GenericJackson2Json 序列化，
 *                         消费端按 @class 还原——要求 payload 为消费方可见的 DTO 类型）
 */
public record EventEnvelope(String sourceInstanceId, Object payload) {

  public boolean from(String instanceId) {
    return sourceInstanceId != null && sourceInstanceId.equals(instanceId);
  }
}
