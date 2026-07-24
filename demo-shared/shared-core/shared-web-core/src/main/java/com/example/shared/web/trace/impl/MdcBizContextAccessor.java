package com.example.shared.web.trace.impl;

import com.example.shared.web.trace.spi.BizContextAccessor;
import com.example.shared.web.trace.spi.ThrowableSupplier;
import org.slf4j.MDC;

import java.util.Map;

/**
 * 基于纯 MDC 的实现，无 Micrometer 依赖
 */
public class MdcBizContextAccessor implements BizContextAccessor {

  @Override
  public <T> T withContext(Map<String, String> contextMap, ThrowableSupplier<T> action) throws Throwable {
    if (contextMap == null || contextMap.isEmpty()) {
      return action.get();
    }

    // 1. 备份旧的 Context (为了 Scope 结束时恢复)
    Map<String, String> oldContext = MDC.getCopyOfContextMap();

    try {
      // 2. 写入新的 Context
      // 注意：这里是叠加模式，不是覆盖模式
      for (Map.Entry<String, String> entry : contextMap.entrySet()) {
        if (entry.getValue() != null) {
          MDC.put(entry.getKey(), entry.getValue());
        }
      }

      // 3. 执行业务
      return action.get();

    } finally {
      // 4. 恢复现场
      if (oldContext != null) {
        MDC.setContextMap(oldContext);
      } else {
        MDC.clear(); // 如果原来是空的，就清空
      }
    }
  }
}
