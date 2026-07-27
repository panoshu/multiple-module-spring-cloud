package com.example.iam.infrastructure.gateway;

import com.example.iam.domain.authorization.gateway.ProductGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 产品信息查询网关 - Mock 实现。
 *
 * <p>设计文档 3.6 节:外部产品接口未对接前使用内存 Mock 数据,
 * 待外部接口确定后替换为 Retrofit/HTTP 客户端实现。
 *
 * <p>用于产品级权限规则匹配前的产品存在性校验。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Component
public class MockProductGateway implements ProductGateway {

    /**
     * Mock 数据:预置的产品编号集合。
     */
    private static final Set<String> VALID_PRODUCTS = Set.of(
            "PRD-ANN-001",
            "PRD-ANN-002",
            "PRD-ANN-003",
            "PRD-ANN-004"
    );

    @Override
    public boolean existsByProductNo(String productNo) {
        if (productNo == null || productNo.isBlank()) {
            return false;
        }
        log.debug("MockProductGateway.existsByProductNo: productNo={}", productNo);
        return VALID_PRODUCTS.contains(productNo);
    }
}
