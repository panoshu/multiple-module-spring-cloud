package com.example.core.domain.spi;

import com.example.core.domain.business.aggregate.root.BusinessApplication;

import java.util.Map;

/**
 * 业务事实提取器接口, 用于提取业务申请聚合根中的个性化数据, 由各业务类型聚合根自行实现
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/14 11:03
 */
public interface BusinessFactExtractor {

  /**
   * 提取器的语义化ID (必须全局唯一，与配置中心对应的名称一致)
   */
  String extractorName();

  /**
   * 从聚合根中提取业务事实，供规则引擎使用
   */
  Map<String, Object> extractBusinessFacts(BusinessApplication businessApplication);
}
