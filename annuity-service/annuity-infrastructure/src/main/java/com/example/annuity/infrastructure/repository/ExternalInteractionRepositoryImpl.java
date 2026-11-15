package com.example.annuity.infrastructure.repository;

import com.example.core.infrastructure.business.converter.ExternalInteractionConverter;
import com.example.core.infrastructure.business.mapper.ExternalInteractionMapper;
import com.example.shared.domain.event.EventBus;
import org.springframework.stereotype.Repository;

/**
 * 年金服务外部协作仓储注册类。
 * <p>
 * kernel 的 {@link com.example.core.infrastructure.business.repository.ExternalInteractionRepositoryImpl}
 * 不标注 {@code @Repository}，由本类注册为 Spring Bean，使编排器的
 * SUSPEND_ASYNC_WAIT 分支能把外部协作记录真实落库。
 */
@Repository
public class ExternalInteractionRepositoryImpl
  extends com.example.core.infrastructure.business.repository.ExternalInteractionRepositoryImpl {

  public ExternalInteractionRepositoryImpl(EventBus eventBus,
                                            ExternalInteractionMapper mapper,
                                            ExternalInteractionConverter converter) {
    super(eventBus, mapper, converter);
  }
}
