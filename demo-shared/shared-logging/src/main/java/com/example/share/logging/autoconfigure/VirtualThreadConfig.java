package com.example.share.logging.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Configuration
public class VirtualThreadConfig {

  @Bean
  @ConditionalOnProperty(prefix = "spring.threads.virtual", name = "enabled", havingValue = "true")
  public Executor virtualThreadExecutor() {
    ThreadFactory threadFactory = Thread.ofVirtual()
      .name("vt-", 1)
      .inheritInheritableThreadLocals(true)
      .factory();

    return Executors.newThreadPerTaskExecutor(threadFactory);
  }
}
