package com.example.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
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
  @DisplayName("集成测试：验证 /server/xxx/xxx 路径应放行")
  void testAllowPathServer() {
    webClient.get().uri("/server/xxx/xxx")
      .exchange()
      .expectStatus().isNotFound()
      .expectBody()
      .jsonPath("$.status").isEqualTo(404)
      .jsonPath("$.path").isEqualTo("/server/xxx/xxx");

  }

  @Test
  @DisplayName("集成测试：验证普通路径应放行")
  void testAllowNormalPath() {
    // 请求一个未配置拦截的路径，例如 /user/1
    // 由于没有真实的后端服务，网关可能会返回 503 Service Unavailable 或者 404 (No Route)
    // 但关键是：它不应该返回我们在 Filter 中定义的 403。

    webClient.get().uri("/user/1")
      .exchange()
      .expectStatus().is5xxServerError(); // 通常是 503，因为 lb://user-service 连不上
  }
}
