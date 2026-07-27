package com.example.iam.infrastructure.gateway;

import com.example.iam.domain.authorization.gateway.OrganizationGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 组织架构信息查询网关 - Mock 实现。
 *
 * <p>设计文档 3.6 节:外部组织架构接口未对接前使用内存 Mock 数据,
 * 待外部接口确定后替换为 Retrofit/HTTP 客户端实现。
 *
 * <p>用于账管人级权限规则匹配前的账管人存在性校验。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Component
public class MockOrganizationGateway implements OrganizationGateway {

    /**
     * Mock 数据:预置的账管人编号集合。
     */
    private static final Set<String> VALID_ACCOUNT_MANAGERS = Set.of("AM-001", "AM-002", "AM-003", "AM-004", "AM-005");

    @Override
    public boolean isAccountManagerValid(String accountManagerCode) {
        if (accountManagerCode == null || accountManagerCode.isBlank()) {
            return false;
        }
        log.debug("MockOrganizationGateway.isAccountManagerValid: accountManagerCode={}", accountManagerCode);
        return VALID_ACCOUNT_MANAGERS.contains(accountManagerCode);
    }
}
