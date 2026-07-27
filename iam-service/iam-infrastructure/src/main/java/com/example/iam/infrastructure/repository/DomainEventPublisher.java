package com.example.iam.infrastructure.repository;

import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.Identifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 领域事件发布工具 - 统一处理聚合根内部注册的领域事件发布。
 *
 * <p>设计文档 5.3 节:Repository 实现在 save 方法中通过本工具发布领域事件,
 * 避免在每个 RepositoryImpl 中复制粘贴事件发布逻辑(DRY 原则)。
 *
 * <p>发布失败不中断主流程,仅记录日志,确保持久化操作不受事件发布影响。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 发布聚合根内部注册的所有领域事件,发布完成后清理事件列表。
     *
     * @param aggregate 聚合根(可能为 null,此时直接返回)
     * @param <ID>      聚合根 ID 类型
     */
    public <ID extends Identifier<?>> void publishFor(AggregateRoot<ID> aggregate) {
        if (aggregate == null) {
            return;
        }
        List<DomainEvent> events = aggregate.getDomainEvents();
        if (events.isEmpty()) {
            return;
        }
        for (DomainEvent event : events) {
            try {
                eventPublisher.publishEvent(event);
                log.debug("发布领域事件: eventId={}, type={}",
                        event.eventId(), event.getClass().getSimpleName());
            } catch (Exception e) {
                log.error("发布领域事件失败: eventId={}, type={}",
                        event.eventId(), event.getClass().getSimpleName(), e);
            }
        }
        aggregate.clearDomainEvents();
    }
}
