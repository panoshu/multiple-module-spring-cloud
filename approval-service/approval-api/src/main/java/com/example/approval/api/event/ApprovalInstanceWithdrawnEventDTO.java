package com.example.approval.api.event;

import java.time.LocalDateTime;

/**
 * 审批实例已撤回集成事件 DTO。
 * 对应 approval-domain 的 ApprovalInstanceWithdrawn 领域事件。
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
public record ApprovalInstanceWithdrawnEventDTO(
        String eventId,
        String instanceId,
        String businessNo,
        String businessType,
        LocalDateTime occurredOn
) {
}
