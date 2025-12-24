package com.example.shared.id;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
// 扫描 core 模块下的 ID 生成器相关组件 (Repository, Service, Component)
@ComponentScan(basePackages = "com.demo.shared.id")
public class IdGeneratorAutoConfig {
  // 这个类的作用就是告诉 Spring："请把 com.example.core.id 下的 Bean 都加载进来"
}
