package com.example.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 认证授权服务启动类.
 *
 * <p>auth-service 提供权限判定引擎（AuthorizationEngine 两层 AND + DENY 优先）、
 * 用户/凭证/角色/授权管理、多渠道会话管理（sa-token）、客户渠道开通管理、
 * 以及供业务服务调用的权限校验 API（{@code /internal/permissions/check}）。</p>
 *
 * @author auth-service
 */
@EnableAsync
@EnableDiscoveryClient
@SpringBootApplication
// 只扫描真实 MyBatis mapper 包：扫 infrastructure 全包会把 MapStruct 转换器接口等
// 也注册成 MapperFactoryBean，与生成的 Impl 冲突（sessionConverter 双 bean 实测）
@MapperScan({
  "com.example.auth.infrastructure.assignment.mapper",
  "com.example.auth.infrastructure.authorization.mapper",
  "com.example.auth.infrastructure.channel.mapper",
  "com.example.auth.infrastructure.credential.mapper",
  "com.example.auth.infrastructure.permission.mapper",
  "com.example.auth.infrastructure.role.mapper",
  "com.example.auth.infrastructure.user.mapper"
})
public class AuthApplication {

  public static void main(String[] args) {
    SpringApplication.run(AuthApplication.class, args);
  }
}
