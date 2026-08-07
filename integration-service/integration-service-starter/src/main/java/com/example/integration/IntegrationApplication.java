package com.example.integration;

import io.github.danielliu1123.httpexchange.EnableExchangeClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;


@EnableDiscoveryClient
@EnableExchangeClients(basePackages = "com.example.auth.api")
@SpringBootApplication
public class IntegrationApplication {

  public static void main(String[] args) {
    SpringApplication.run(IntegrationApplication.class, args);
  }
}
