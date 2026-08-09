package com.example.bff.intranet;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 内网/专线渠道 BFF 启动类
 *
 * <p>scanBasePackages 同时包含 {@code com.example.bff.intranet}（本服务）和 {@code com.example.bff.shared}
 * （BFF 公共组件：BffAutoConfiguration 注册 BusinessTypeRouter / KernelApiRegistry）。
 *
 * <p>{@code @MapperScan} 显式扫描 bff-shared 的 mapper 包（路由配置表访问），
 * 使 MyBatis-Flex 注册 BffRouteConfigMapper 为 Bean（与项目其他服务保持一致）。
 *
 * @author bff
 */
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = {
  "com.example.bff.intranet",
  "com.example.bff.shared"
})
@MapperScan("com.example.bff.shared.infrastructure.mapper")
public class IntranetBffApplication {
  public static void main(String[] args) {
    SpringApplication.run(IntranetBffApplication.class, args);
  }
}
