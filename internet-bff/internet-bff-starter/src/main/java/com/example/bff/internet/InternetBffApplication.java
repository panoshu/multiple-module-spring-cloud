package com.example.bff.internet;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 互联网 BFF 启动类
 *
 * <p>scanBasePackages 同时包含 {@code com.example.bff.internet}（本服务）和 {@code com.example.bff.shared}
 * （BFF 公共组件：BffAutoConfiguration 注册 BusinessTypeRouter / KernelApiRegistry）。
 *
 * <p>{@code @MapperScan} 显式扫描 internet-bff-infrastructure 的 mapper 包，
 * 使 MyBatis-Flex 注册 BffRouteConfigMapper 为 Bean（与项目其他服务保持一致）。
 *
 * @author bff
 */
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = {
        "com.example.bff.internet",
        "com.example.bff.shared"
})
@MapperScan("com.example.bff.internet.infrastructure.mapper")
public class InternetBffApplication {
    public static void main(String[] args) {
        SpringApplication.run(InternetBffApplication.class, args);
    }
}
