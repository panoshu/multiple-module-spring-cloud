package com.example.approval.domain.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

/**
 * 审批意见值对象
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
public record ApprovalOpinion(String value) implements ValueObject {

    public ApprovalOpinion {
        if (value != null && value.length() > 500) {
            throw new IllegalArgumentException("审批意见长度不能超过500字符");
        }
    }

    /**
     * 静态工厂方法
     *
     * @param value 意见内容
     * @return ApprovalOpinion 实例
     */
    public static ApprovalOpinion of(String value) {
        return new ApprovalOpinion(value);
    }

    /**
     * 创建空意见
     *
     * @return 空意见实例
     */
    public static ApprovalOpinion empty() {
        return new ApprovalOpinion(null);
    }

    /**
     * 是否为空
     *
     * @return true 如果意见为空
     */
    public boolean isEmpty() {
        return value == null || value.isBlank();
    }
}