package com.example.annuity.infrastructure.repository;

import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.gateway.ParsedFileReferenceGateway;
import com.example.core.infrastructure.business.converter.BusinessApplicationConverter;
import com.example.core.infrastructure.business.mapper.BusinessApplicationMapper;
import com.example.core.infrastructure.business.repository.BusinessApplicationRepositoryImpl;
import com.example.file.types.FileId;
import com.example.shared.domain.event.EventBus;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 年金业务申请单仓储实现
 * <p>
 * 继承 kernel 的 {@link BusinessApplicationRepositoryImpl}，复用通用的 CRUD、
 * 领域事件发布与 {@code findByBatchId} 等查询逻辑。按文件凭证反查申请单改由业务服务
 * 自有存储 {@link ParsedFileReferenceGateway} 提供（§6.2，批次 37c-2）。
 * kernel 基类不标注 {@code @Repository}，由本类注册为 Spring Bean。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
@Repository
public class ApplicationRepositoryImpl extends BusinessApplicationRepositoryImpl {

  private final ParsedFileReferenceGateway parsedFileGateway;

  public ApplicationRepositoryImpl(EventBus eventBus,
                                   BusinessApplicationMapper mapper,
                                   BusinessApplicationConverter converter,
                                   ParsedFileReferenceGateway parsedFileGateway) {
    super(eventBus, mapper, converter);
    this.parsedFileGateway = parsedFileGateway;
  }

  @Override
  public Optional<BusinessApplication> findByFileTaskId(String fileTaskId) {
    if (fileTaskId == null || fileTaskId.isBlank()) {
      return Optional.empty();
    }
    return parsedFileGateway.findByFileId(new FileId(fileTaskId)).flatMap(this::load);
  }
}
