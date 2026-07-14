package com.example.core.domain.service.registry;

import com.example.core.domain.annotation.DomainService;
import com.example.core.domain.spi.StepActionHandler;

import java.util.List;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/14 16:46
 */
@DomainService
public class StepActionHandlerRegistry extends AbstractStrategyRegistry<StepActionHandler> {
  public StepActionHandlerRegistry(List<StepActionHandler> handlers) {
    super(handlers, StepActionHandler::handlerName);
  }
}
