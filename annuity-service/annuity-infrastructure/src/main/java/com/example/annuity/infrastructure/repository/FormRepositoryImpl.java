package com.example.annuity.infrastructure.repository;

import com.example.core.infrastructure.business.converter.BusinessFormConverter;
import com.example.core.infrastructure.business.mapper.BusinessFormMapper;
import com.example.core.infrastructure.business.repository.BusinessFormRepositoryImpl;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Repository;

/**
 * 年金业务表单仓储实现
 * <p>
 * 继承 kernel 的 {@link BusinessFormRepositoryImpl}，复用通用的 CRUD、
 * 领域事件发布、{@code findByApplicationId} 等查询逻辑。
 * kernel 基类不标注 {@code @Repository}，由本类注册为 Spring Bean。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
@Repository
public class FormRepositoryImpl extends BusinessFormRepositoryImpl {

  public FormRepositoryImpl(ApplicationEventPublisher eventPublisher,
                            BusinessFormMapper mapper,
                            BusinessFormConverter converter) {
    super(eventPublisher, mapper, converter);
  }
}
