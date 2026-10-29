package com.example.bff.internet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 互联网 BFF 启动模块集成测试
 *
 * @author bff
 */
@SpringBootTest
class InternetBffApplicationTest {

  @Autowired
  private ApplicationContext applicationContext;

  @Test
  @DisplayName("应用上下文加载成功")
  void contextLoads() {
    assertNotNull(applicationContext);
  }

  @Test
  @DisplayName("BffBusinessController 已注册")
  void controllerRegistered() {
    assertTrue(applicationContext.containsBean("bffBusinessController"));
  }
}
