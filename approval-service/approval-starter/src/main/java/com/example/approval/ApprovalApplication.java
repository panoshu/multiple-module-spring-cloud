package com.example.approval;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 审批服务启动类
 *
 * @author approval-service
 */
@EnableAsync
@EnableDiscoveryClient
@SpringBootApplication
@MapperScan("com.example.approval.infrastructure.mapper")
public class ApprovalApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApprovalApplication.class, args);
    }
}