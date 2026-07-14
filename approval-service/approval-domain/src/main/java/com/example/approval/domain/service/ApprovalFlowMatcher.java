package com.example.approval.domain.service;

import com.example.approval.domain.aggregate.root.ApprovalFlow;
import com.example.approval.domain.valueobject.MatchRules;
import com.example.shared.domain.annotation.DomainService;

import java.util.List;
import java.util.Optional;

/**
 * 审批流匹配服务
 * 根据匹配规则找到最合适的审批流
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
@DomainService
public class ApprovalFlowMatcher {

    /**
     * 根据匹配规则找到最合适的审批流
     *
     * @param rules 匹配规则
     * @param flows 审批流列表
     * @return 最合适的审批流（可能为空）
     */
    public Optional<ApprovalFlow> match(MatchRules rules, List<ApprovalFlow> flows) {
        if (rules == null || flows == null || flows.isEmpty()) {
            return Optional.empty();
        }

        // 筛选激活状态的审批流
        List<ApprovalFlow> activeFlows = flows.stream()
                .filter(ApprovalFlow::isActive)
                .toList();

        if (activeFlows.isEmpty()) {
            return Optional.empty();
        }

        // 按匹配度排序，返回最合适的
        return activeFlows.stream()
                .filter(flow -> isMatch(rules, flow))
                .findFirst();
    }

    /**
     * 判断审批流是否匹配规则
     *
     * @param rules 匹配规则
     * @param flow  审批流
     * @return true 如果匹配
     */
    private boolean isMatch(MatchRules rules, ApprovalFlow flow) {
        MatchRules flowRules = flow.matchRules();

        // 产品编号匹配
        if (rules.productNo() != null && !rules.productNo().equals(flowRules.productNo())) {
            return false;
        }

        // 客户编号匹配
        if (rules.customerNo() != null && !rules.customerNo().equals(flowRules.customerNo())) {
            return false;
        }

        // 客户经理匹配
        if (rules.accountManager() != null && !rules.accountManager().equals(flowRules.accountManager())) {
            return false;
        }

        // 运营模式匹配
        if (rules.operationMode() != null && !rules.operationMode().equals(flowRules.operationMode())) {
            return false;
        }

        // 业务类型匹配
        if (rules.businessType() != null && !rules.businessType().equals(flowRules.businessType())) {
            return false;
        }

        // 年金渠道匹配
        if (rules.annuityChannel() != null && !rules.annuityChannel().equals(flowRules.annuityChannel())) {
            return false;
        }

        return true;
    }
}