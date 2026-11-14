package com.example.annuity.infrastructure.gateway;

import com.example.core.domain.business.gateway.ParsedFileReferenceGateway;
import com.example.core.types.ApplicationId;
import com.example.file.types.FileId;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 解析 JSON 文件引用的内存态实现（spec §6.2，批次 37c-1）。
 * <p>
 * annuity 是早期示例服务，进程内存储即可满足 E2E；真实业务服务应实现为
 * 持久化的自有存储或按规格关联到外部协作记录。
 *
 * @author annuity-service
 */
@Component
public class InMemoryParsedFileReferenceGateway implements ParsedFileReferenceGateway {

  private final Map<ApplicationId, FileId> references = new ConcurrentHashMap<>();

  @Override
  public void save(ApplicationId applicationId, FileId jsonFileId) {
    if (applicationId == null || jsonFileId == null) {
      return;
    }
    references.put(applicationId, jsonFileId);
  }

  @Override
  public Optional<FileId> load(ApplicationId applicationId) {
    if (applicationId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(references.get(applicationId));
  }
}
