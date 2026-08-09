package com.example.bff.internet.adapter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * adapter 测试专用启动类
 *
 * <p>提供 {@code @SpringBootConfiguration} 供 {@code @WebMvcTest} 引导 Spring 上下文。
 * adapter 模块本身是库模块，不含启动类，因此测试需要独立的配置入口。
 *
 * @author bff
 */
@SpringBootApplication
public class TestApplication {
  public static void main(String[] args) {
    SpringApplication.run(TestApplication.class, args);
  }
}
