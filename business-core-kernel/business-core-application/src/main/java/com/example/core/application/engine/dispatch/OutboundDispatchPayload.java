package com.example.core.application.engine.dispatch;

import com.example.core.domain.business.errorcode.CoreDomainErrorCode;
import com.example.shared.exception.DomainException;

/**
 * FILE_OUTBOUND 派发载荷（spec §9.2 阶段 B / §16.4 检查点，批次 30）。
 * <p>
 * 出站凭证（{@code fileTaskId}/{@code targetTemplateId}）来自运行时业务事实，
 * 事务外不可重建，故在挂起登记时编码为紧凑字符串持久化到
 * {@code StepExecution.checkpoint_ref}（VARCHAR(255)）。派发时解码还原。
 * <p>
 * 采用单元分隔符 {@code \u001F} 拼接而非 JSON：仅两个短 ID、纯 JDK 无框架依赖；
 * 分隔符不会出现在 ID 中，编码前显式校验，避免串字段。
 */
public record OutboundDispatchPayload(String fileTaskId, String targetTemplateId) {

  private static final String SEP = "\u001F";

  public String encode() {
    if (!hasText(fileTaskId) || !hasText(targetTemplateId)) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("出站派发凭证不完整");
    }
    if (fileTaskId.contains(SEP) || targetTemplateId.contains(SEP)) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("出站派发凭证含非法分隔符");
    }
    return fileTaskId + SEP + targetTemplateId;
  }

  public static OutboundDispatchPayload decode(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("出站派发载荷为空");
    }
    String[] parts = raw.split(SEP, -1);
    if (parts.length != 2 || !hasText(parts[0]) || !hasText(parts[1])) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("出站派发载荷格式错误");
    }
    return new OutboundDispatchPayload(parts[0], parts[1]);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
