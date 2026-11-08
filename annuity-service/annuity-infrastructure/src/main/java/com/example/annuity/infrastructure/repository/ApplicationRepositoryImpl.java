package com.example.annuity.infrastructure.repository;

import com.example.core.infrastructure.business.converter.BusinessApplicationConverter;
import com.example.core.infrastructure.business.mapper.BusinessApplicationMapper;
import com.example.core.infrastructure.business.repository.BusinessApplicationRepositoryImpl;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;

/**
 * 年金业务申请单仓储实现
 * <p>
 * 继承 kernel 的 {@link BusinessApplicationRepositoryImpl}，复用通用的 CRUD、
 * 领域事件发布、{@code findByBatchId}、{@code findByFileTaskId} 等查询逻辑。
 * kernel 基类不标注 {@code @Repository}，由本类注册为 Spring Bean。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
@Repository
public class ApplicationRepositoryImpl extends BusinessApplicationRepositoryImpl {

  public ApplicationRepositoryImpl(ApplicationEventPublisher eventPublisher,
                                   BusinessApplicationMapper mapper,
                                   BusinessApplicationConverter converter) {
    super(eventPublisher, mapper, converter);
  }
}
