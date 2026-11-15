package com.example.shared.event.dispatcher;

/**
 * RocketMQ 5.x 事件通道线格式。
 * <p>使用显式事件类型 + JSON 载荷，避免 Jackson default typing 的安全风险；
 * 消费端仅允许加载 {@code com.example.} 下的类型。</p>
 *
 * @param eventType   集成事件 DTO 全限定类名
 * @param payloadJson 载荷 JSON
 */
public record RocketMQEventEnvelope(String eventType, String payloadJson) {
}
