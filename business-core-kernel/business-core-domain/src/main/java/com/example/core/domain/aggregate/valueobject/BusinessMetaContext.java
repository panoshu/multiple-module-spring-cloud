package com.example.core.domain.aggregate.valueobject;

import com.example.core.domain.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.aggregate.valueobject.business.OperationModel;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.PlanNo;
import com.example.shared.primitives.identity.ProductNo;

import java.util.Map;

/**
 * 配置查询上下文
 * 各业务可以实现 BusinessFactExtractor 领域服务从自身的聚合根中提取扩展信息注入 extensionFacts
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/14 10:52
 */
public record BusinessMetaContext(
  CustomerNo customerNo,
  ProductNo productNo,
  OperationModel operationModel,
  PlanNo planNo,
  BusinessType businessType,
  AccountManager accountManager,
  Map<String, Object> extensionFacts
) {

  public static BusinessMetaContext of(BusinessContext businessContext) {
    return new BusinessMetaContext(
      businessContext.customerNo(), businessContext.productNo(), businessContext.operationModel(), businessContext.planNo(),
      businessContext.businessType(), businessContext.accountManager(), null);
  }

  public static BusinessMetaContext withExtensionFacts(BusinessMetaContext configQueryContext, Map<String, Object> extensionFacts) {
    return new BusinessMetaContext(
      configQueryContext.customerNo,
      configQueryContext.productNo,
      configQueryContext.operationModel,
      configQueryContext.planNo,
      configQueryContext.businessType,
      configQueryContext.accountManager,
      extensionFacts
    );
  }

}
