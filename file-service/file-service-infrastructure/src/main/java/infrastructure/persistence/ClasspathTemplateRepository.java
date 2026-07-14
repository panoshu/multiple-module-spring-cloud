// 文件: src/main/java/infrastructure/persistence/ClasspathTemplateRepository.java
package infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import core.domain.model.InboundTemplate;
import core.domain.model.OutboundTemplate;
import core.domain.outbound.TemplateRepositoryPort;
import org.springframework.stereotype.Repository;

import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
public class ClasspathTemplateRepository implements TemplateRepositoryPort {

  private final ObjectMapper mapper = new ObjectMapper();
  private final ConcurrentMap<String, InboundTemplate> inboundCache = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, OutboundTemplate> outboundCache = new ConcurrentHashMap<>();

  private final String INBOUND_PATH_PREFIX = "templates/inbound_";
  private final String OUTBOUND_PATH_PREFIX = "templates/outbound_";

  @Override
  public Optional<InboundTemplate> loadInbound(String templateId) {
    return Optional.ofNullable(inboundCache.computeIfAbsent(templateId, id -> {
      try {
        // 约定大于配置：文件名映射为 templates/inbound_CORP_PLAN_IN_V1.json
        String fileName = INBOUND_PATH_PREFIX + id.toLowerCase().replace("_in_v1", "") + ".json";
        InputStream stream = getStream(fileName);
        if (stream == null) {
          return null;
        }
        return mapper.readValue(stream, InboundTemplate.class);
      } catch (Exception e) {
        throw new RuntimeException("读取输入模板失败: " + templateId, e);
      }
    }));
  }

  @Override
  public Optional<OutboundTemplate> loadOutbound(String templateId) {
    return Optional.ofNullable(outboundCache.computeIfAbsent(templateId, id -> {
      try {
        String fileName = OUTBOUND_PATH_PREFIX + id.toLowerCase().replace("_out_std_v1", "_std") + ".json";
        InputStream stream = getStream(fileName);
        if (stream == null) {
          return null;
        }
        return mapper.readValue(stream, OutboundTemplate.class);
      } catch (Exception e) {
        throw new RuntimeException("读取输出模板失败: " + templateId, e);
      }
    }));
  }

  @Override
  public void clearCache(String templateId) {
    inboundCache.remove(templateId);
    outboundCache.remove(templateId);
  }

  private InputStream getStream(String fileName) {
    return Thread.currentThread().getContextClassLoader().getResourceAsStream(fileName);
  }
}
