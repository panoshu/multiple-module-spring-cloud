package com.example.core.domain.engine.service.registry;

import com.example.core.domain.engine.annotation.DomainService;
import com.example.core.domain.engine.spi.BusinessFactExtractor;

import java.util.List;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/14 16:47
 */
@DomainService
public class BusinessFactExtractorRegistry extends AbstractStrategyRegistry<BusinessFactExtractor> {
  public BusinessFactExtractorRegistry(List<BusinessFactExtractor> extractors) {
    super(extractors, BusinessFactExtractor::extractorName);
  }
}
