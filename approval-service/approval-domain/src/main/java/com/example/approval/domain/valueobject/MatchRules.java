package com.example.approval.domain.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

import java.util.Optional;

/**
 * 匹配规则值对象
 * 用于匹配业务申请与审批流
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
public record MatchRules(
        String productNo,
        String customerNo,
        String accountManager,
        String operationMode,
        String businessType,
        String annuityChannel
) implements ValueObject {

    /**
     * 静态工厂方法
     *
     * @param productNo      产品编号
     * @param customerNo     客户编号
     * @param accountManager 客户经理
     * @param operationMode  运营模式
     * @param businessType   业务类型
     * @param annuityChannel 年金渠道
     * @return MatchRules 实例
     */
    public static MatchRules of(
            String productNo,
            String customerNo,
            String accountManager,
            String operationMode,
            String businessType,
            String annuityChannel
    ) {
        return new MatchRules(productNo, customerNo, accountManager, operationMode, businessType, annuityChannel);
    }

    /**
     * 获取产品编号
     *
     * @return 产品编号（可能为空）
     */
    public Optional<String> getProductNo() {
        return Optional.ofNullable(productNo);
    }

    /**
     * 获取客户编号
     *
     * @return 客户编号（可能为空）
     */
    public Optional<String> getCustomerNo() {
        return Optional.ofNullable(customerNo);
    }

    /**
     * 获取客户经理
     *
     * @return 客户经理（可能为空）
     */
    public Optional<String> getAccountManager() {
        return Optional.ofNullable(accountManager);
    }

    /**
     * 获取运营模式
     *
     * @return 运营模式（可能为空）
     */
    public Optional<String> getOperationMode() {
        return Optional.ofNullable(operationMode);
    }

    /**
     * 获取业务类型
     *
     * @return 业务类型（可能为空）
     */
    public Optional<String> getBusinessType() {
        return Optional.ofNullable(businessType);
    }

    /**
     * 获取年金渠道
     *
     * @return 年金渠道（可能为空）
     */
    public Optional<String> getAnnuityChannel() {
        return Optional.ofNullable(annuityChannel);
    }
}