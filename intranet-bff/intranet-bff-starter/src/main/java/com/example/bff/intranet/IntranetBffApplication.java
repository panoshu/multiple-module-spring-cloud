package com.example.bff.intranet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 内网/专线渠道 BFF 启动类
 *
 * <p>scanBasePackages 同时包含 {@code com.example.bff.intranet}（本服务）和 {@code com.example.bff.shared}
 * （BFF 公共组件）。
 *
 * @author bff
 */
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = {
  "com.example.bff.intranet",
  "com.example.bff.shared"
})
public class IntranetBffApplication {
  public static void main(String[] args) {
    SpringApplication.run(IntranetBffApplication.class, args);
  }
}
