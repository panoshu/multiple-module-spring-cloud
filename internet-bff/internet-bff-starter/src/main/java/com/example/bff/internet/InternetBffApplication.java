package com.example.bff.internet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 互联网 BFF 启动类
 *
 * <p>scanBasePackages 同时包含 {@code com.example.bff.internet}（本服务）和 {@code com.example.bff.shared}
 * （BFF 公共组件）。
 *
 * @author bff
 */
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = {
  "com.example.bff.internet",
  "com.example.bff.shared"
})
public class InternetBffApplication {
  public static void main(String[] args) {
    SpringApplication.run(InternetBffApplication.class, args);
  }
}
