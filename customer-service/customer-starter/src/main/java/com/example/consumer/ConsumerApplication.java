package com.example.consumer;

import io.github.danielliu1123.httpexchange.EnableExchangeClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * ConsumerApplication
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/1/2 14:19
 */
@EnableAsync
@EnableDiscoveryClient
@SpringBootApplication
@EnableExchangeClients(basePackages = {"com.example.consumer", "com.example.outbound.api"})
public class ConsumerApplication {

  public static void main(String[] args) {
    SpringApplication.run(ConsumerApplication.class, args);
  }
}
