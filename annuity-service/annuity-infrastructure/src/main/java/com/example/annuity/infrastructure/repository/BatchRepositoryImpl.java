package com.example.annuity.infrastructure.repository;

import com.example.core.infrastructure.business.converter.BusinessBatchConverter;
import com.example.core.infrastructure.business.mapper.BusinessBatchMapper;
import com.example.core.infrastructure.business.repository.BusinessBatchRepositoryImpl;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;

/**
 * 年金业务批次仓储实现
 * <p>
 * 继承 kernel 的 {@link BusinessBatchRepositoryImpl}，复用通用的 CRUD、
 * 领域事件发布、{@code findByFormId}、{@code findByApplicationId}、{@code findActive} 等查询逻辑。
 * kernel 基类不标注 {@code @Repository}，由本类注册为 Spring Bean。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
@Repository
public class BatchRepositoryImpl extends BusinessBatchRepositoryImpl {

  public BatchRepositoryImpl(ApplicationEventPublisher eventPublisher,
                             BusinessBatchMapper mapper,
                             BusinessBatchConverter converter) {
    super(eventPublisher, mapper, converter);
  }
}
