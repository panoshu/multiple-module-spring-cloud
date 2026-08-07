package com.example.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@ActiveProfiles("test")
@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayIntegrationTest {

  @Autowired
  private WebTestClient webClient;

  @Test
  @DisplayName("集成测试：验证 /aaa/** 路径应被拦截并返回 404 (根据 application.yml 配置)")
  void testBlockPathAAA() {
    webClient.get().uri("/aaa/something")
      .exchange()
      .expectStatus().isNotFound() // 404
      .expectBody().isEmpty();
  }

  @Test
  @DisplayName("集成测试：验证 /internal/** 路径应被拦截并返回 403 (默认状态码)")
  void testBlockPathInternal() {
    webClient.get().uri("/internal/secret")
      .exchange()
      .expectStatus().isForbidden() // 403
      .expectBody().isEmpty();
  }

  @Test
  @DisplayName("集成测试：验证 /server/* 路径应被拦截并返回 401")
  void testBlockPathServer() {
    webClient.get().uri("/server/secret")
      .exchange()
      .expectStatus().isUnauthorized() // 401
      .expectBody().isEmpty();
  }

  @Test
  @DisplayName("集成测试：验证 /server/xxx/xxx 路径需登录（非白名单，未被 exclude-routes 拦截）")
  void testAllowPathServer() {
    // /server/* 仅匹配单段路径，/server/xxx/xxx 不被 ExcludeRouteFilter 拦截，
    // 进入 SaReactorFilter 后因非白名单路径要求登录，未登录返回 401。
    webClient.get().uri("/server/xxx/xxx")
      .exchange()
      .expectStatus().isUnauthorized()
      .expectBody()
      .jsonPath("$.code").isEqualTo("COMMON.0002")
      .jsonPath("$.message").isEqualTo("未登录或登录已过期");
  }

  @Test
  @DisplayName("集成测试：验证普通路径需登录（非白名单路径）")
  void testAllowNormalPath() {
    // 网关改造后所有非白名单路径均要求登录，/user/1 未在白名单中，未登录返回 401。
    webClient.get().uri("/user/1")
      .exchange()
      .expectStatus().isUnauthorized()
      .expectBody()
      .jsonPath("$.code").isEqualTo("COMMON.0002")
      .jsonPath("$.message").isEqualTo("未登录或登录已过期");
  }
}
