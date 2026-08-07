package com.example.file;

import io.github.danielliu1123.httpexchange.EnableExchangeClients;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@EnableDiscoveryClient
@EnableExchangeClients(basePackages = "com.example.auth.api")
@SpringBootApplication
@MapperScan("com.example.file.infrastructure.mapper")
public class FileApplication {

  public static void main(String[] args) {
    SpringApplication.run(FileApplication.class, args);
  }
}
