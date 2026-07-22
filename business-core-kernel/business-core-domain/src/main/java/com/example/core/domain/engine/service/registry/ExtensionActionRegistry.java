package com.example.core.domain.engine.service.registry;

import com.example.core.domain.engine.annotation.DomainService;
import com.example.core.domain.engine.spi.StepExtensionAction;

import java.util.List;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/14 16:47
 */
@DomainService
public class ExtensionActionRegistry extends AbstractStrategyRegistry<StepExtensionAction> {
  public ExtensionActionRegistry(List<StepExtensionAction> validators) {
    super(validators, StepExtensionAction::actionName);
  }
}
