package com.example.iam.infrastructure.gateway;

import com.example.iam.domain.authorization.aggregate.valueobject.CustomerInfo;
import com.example.iam.domain.authorization.gateway.CustomerGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户信息查询网关 - Mock 实现。
 *
 * <p>设计文档 3.6 节:外部客户接口未对接前使用内存 Mock 数据,
 * 待外部接口确定后替换为 Retrofit/HTTP 客户端实现。
 *
 * <p>Mock 数据通过静态 Map 预置,仅用于本地开发与集成测试场景。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Component
public class MockCustomerGateway implements CustomerGateway {

    private static final Map<String, CustomerInfo> MOCK_CUSTOMERS = new ConcurrentHashMap<>();

    static {
        // 预置 Mock 数据(后续通过外部接口替换)
        putMock("C-001", "示例企业年金客户A", "ENTERPRISE");
        putMock("C-002", "示例企业年金客户B", "ENTERPRISE");
        putMock("C-003", "示例金融机构客户", "FINANCIAL");
        putMock("BRANCH-001", "示例分行", "BRANCH");
    }

    private static void putMock(String customerNo, String name, String type) {
        MOCK_CUSTOMERS.put(customerNo, new CustomerInfo(customerNo, name, type));
    }

    @Override
    public Optional<CustomerInfo> findByCustomerNo(String customerNo) {
        if (customerNo == null || customerNo.isBlank()) {
            return Optional.empty();
        }
        log.debug("MockCustomerGateway.findByCustomerNo: customerNo={}", customerNo);
        return Optional.ofNullable(MOCK_CUSTOMERS.get(customerNo));
    }
}
