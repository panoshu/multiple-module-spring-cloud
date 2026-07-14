package com.example.approval.domain.gateway;

import com.example.shared.primitives.identity.PlanNo;

import java.util.Optional;

/**
 * 计划层级网关接口（防腐层）
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
public interface PlanHierarchyGateway {

    /**
     * 获取企业计划的父级计划ID
     *
     * TODO: 待用户服务API提供后实现
     *
     * @param planId 计划ID
     * @return 父级计划ID（可能为空）
     */
    Optional<PlanNo> getParentPlan(PlanNo planId);

    /**
     * 获取企业计划的层级深度
     *
     * TODO: 待用户服务API提供后实现
     *
     * @param planId 计划ID
     * @return 层级深度
     */
    int getPlanLevel(PlanNo planId);
}