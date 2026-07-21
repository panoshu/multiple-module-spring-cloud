package com.example.core.infrastructure.configuration;

import com.example.core.domain.aggregate.valueobject.BusinessExtension;
import com.example.core.infrastructure.json.BusinessExtensionMixIn;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * core 模块的 Jackson 配置辅助类
 * 应用模块可选择性调用，或自行配置
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/14 09:50
 */
public final class LibJacksonModule {

  private LibJacksonModule() {
  }

  /**
   * 注册 lib 模块的基础 Mix-in
   */
  public static void registerMixIns(ObjectMapper mapper) {
    mapper.addMixIn(BusinessExtension.class, BusinessExtensionMixIn.class);
  }
}
