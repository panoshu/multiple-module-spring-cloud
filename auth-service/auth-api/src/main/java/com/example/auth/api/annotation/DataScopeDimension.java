package com.example.auth.api.annotation;

/**
 * 行级数据过滤维度.
 *
 * @author auth-api
 */
public enum DataScopeDimension {
    /** 按 plan_no 过滤 */
    PLAN,
    /** 按 customer_no 过滤 */
    CUSTOMER
}
