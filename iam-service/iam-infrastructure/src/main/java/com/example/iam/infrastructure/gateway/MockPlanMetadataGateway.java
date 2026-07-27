package com.example.iam.infrastructure.gateway;

import com.example.iam.domain.authorization.aggregate.valueobject.OperationMode;
import com.example.iam.domain.authorization.aggregate.valueobject.PlanMetadata;
import com.example.iam.domain.authorization.gateway.PlanMetadataGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 计划元数据查询网关 - Mock 实现。
 *
 * <p>设计文档 3.6 节:外部计划接口未对接前使用内存 Mock 数据,
 * 待外部接口确定后替换为 Retrofit/HTTP 客户端实现。
 *
 * <p>PermissionResolver 计算流程步骤 1 通过本网关获取计划上下文,
 * 提供权限规则匹配所需的客户/产品/运作模式/账管人维度。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Component
public class MockPlanMetadataGateway implements PlanMetadataGateway {

    private static final Map<String, PlanMetadata> MOCK_PLANS = new ConcurrentHashMap<>();

    static {
        // 预置 Mock 数据(后续通过外部接口替换)
        putMock("P-001", "C-001", "PRD-ANN-001", OperationMode.SINGLE_TRUSTEE, "AM-001");
        putMock("P-002", "C-001", "PRD-ANN-002", OperationMode.SINGLE_ACCOUNT_MANAGER, "AM-002");
        putMock("P-003", "C-002", "PRD-ANN-001", OperationMode.TRUSTEE_AND_ACCOUNT_MANAGER, "AM-003");
        putMock("P-004", "C-002", "PRD-ANN-003", OperationMode.SINGLE_TRUSTEE, "AM-001");
        putMock("P-005", "C-003", "PRD-ANN-002", OperationMode.SINGLE_ACCOUNT_MANAGER, "AM-002");
    }

    private static void putMock(String planNo, String customerNo, String productNo,
                                OperationMode operationMode, String accountManagerCode) {
        MOCK_PLANS.put(planNo, new PlanMetadata(planNo, customerNo, productNo, operationMode, accountManagerCode));
    }

    @Override
    public Optional<PlanMetadata> findByPlanNo(String planNo) {
        if (planNo == null || planNo.isBlank()) {
            return Optional.empty();
        }
        log.debug("MockPlanMetadataGateway.findByPlanNo: planNo={}", planNo);
        return Optional.ofNullable(MOCK_PLANS.get(planNo));
    }

    @Override
    public List<PlanMetadata> findSelectablePlansByCustomer(String customerNo) {
        if (customerNo == null || customerNo.isBlank()) {
            return List.of();
        }
        log.debug("MockPlanMetadataGateway.findSelectablePlansByCustomer: customerNo={}", customerNo);
        return MOCK_PLANS.values().stream()
                .filter(plan -> customerNo.equals(plan.customerNo()))
                .collect(Collectors.toList());
    }
}
