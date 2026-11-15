package com.example.annuity.infrastructure.gateway;

import com.example.core.domain.business.gateway.ParsedSubTaskReferenceGateway;
import com.example.core.types.ApplicationId;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 解析结果子任务引用的内存态实现（annuity 示例服务）。
 *
 * <p>真实业务服务应实现为持久化存储，按 applicationId 关联 subTaskId；
 * 本实现仅满足示例/E2E 的单进程运行。</p>
 *
 * @author annuity-service
 */
@Component
public class InMemoryParsedSubTaskReferenceGateway implements ParsedSubTaskReferenceGateway {

  private final Map<ApplicationId, String> references = new ConcurrentHashMap<>();

  @Override
  public void save(ApplicationId applicationId, String subTaskId) {
    if (applicationId == null || subTaskId == null || subTaskId.isBlank()) {
      return;
    }
    references.put(applicationId, subTaskId);
  }

  @Override
  public Optional<String> load(ApplicationId applicationId) {
    if (applicationId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(references.get(applicationId));
  }
}
