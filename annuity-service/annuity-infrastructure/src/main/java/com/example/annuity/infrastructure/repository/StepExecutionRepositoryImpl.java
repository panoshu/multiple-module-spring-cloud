package com.example.annuity.infrastructure.repository;

import com.example.core.infrastructure.business.converter.StepExecutionConverter;
import com.example.core.infrastructure.business.mapper.StepExecutionMapper;
import com.example.shared.domain.event.EventBus;
import org.springframework.stereotype.Repository;

/**
 * 年金服务步骤执行仓储注册类。
 * <p>
 * kernel 的 {@link com.example.core.infrastructure.business.repository.StepExecutionRepositoryImpl}
 * 不标注 {@code @Repository}，由本类注册为 Spring Bean，使编排器接线后的步骤执行记录
 * 能在 E2E 与运行期真实落库。
 */
@Repository
public class StepExecutionRepositoryImpl
  extends com.example.core.infrastructure.business.repository.StepExecutionRepositoryImpl {

  public StepExecutionRepositoryImpl(EventBus eventBus,
                                     StepExecutionMapper mapper,
                                     StepExecutionConverter converter) {
    super(eventBus, mapper, converter);
  }
}
