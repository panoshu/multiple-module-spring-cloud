package com.pension.permission;

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
@MapperScan("com.pension.permission.infrastructure")
public class AuthApplication {

  public static void main(String[] args) {
    SpringApplication.run(AuthApplication.class, args);
  }
}
