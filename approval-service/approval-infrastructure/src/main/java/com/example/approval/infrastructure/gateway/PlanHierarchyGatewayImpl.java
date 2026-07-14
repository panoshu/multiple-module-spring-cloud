package com.example.approval.infrastructure.gateway;

import com.example.approval.domain.gateway.PlanHierarchyGateway;
import com.example.shared.primitives.identity.PlanNo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 计划层级网关实现（伪实现）
 *
 * TODO: 待用户服务API提供后实现
 *
 * @author approval-service
 */
@Component
@RequiredArgsConstructor
public class PlanHierarchyGatewayImpl implements PlanHierarchyGateway {

    @Override
    public Optional<PlanNo> getParentPlan(PlanNo planId) {
        // TODO: 待用户服务API提供后实现
        // 实际实现应该调用用户服务API获取父级计划ID
        return Optional.empty();
    }

    @Override
    public int getPlanLevel(PlanNo planId) {
        // TODO: 待用户服务API提供后实现
        // 实际实现应该调用用户服务API获取计划层级深度
        return 0;
    }
}