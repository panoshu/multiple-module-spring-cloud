package core.domain.outbound;

import core.domain.model.InboundTemplate;
import core.domain.model.OutboundTemplate;

import java.util.Optional;

/**
 * 模板仓储端口 (SPI)
 * 由基础设施层实现 (DB + Caffeine Cache)
 */
public interface TemplateRepositoryPort {
  // 根据输入模板ID获取输入规则
  Optional<InboundTemplate> loadInbound(String templateId);

  // 根据输出模板ID获取输出规则
  Optional<OutboundTemplate> loadOutbound(String templateId);

  // 清理缓存
  void clearCache(String templateId);
}
