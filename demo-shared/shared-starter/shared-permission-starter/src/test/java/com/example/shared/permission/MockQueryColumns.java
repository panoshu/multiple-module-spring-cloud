package com.example.shared.permission;

import com.mybatisflex.core.query.QueryColumn;

/**
 * 测试用 QueryColumn 占位（避免依赖具体 DO 表定义）.
 */
public final class MockQueryColumns {

    private MockQueryColumns() {
    }

    public static final QueryColumn PLAN_NO = new QueryColumn("plan_no");
    public static final QueryColumn CUSTOMER_NO = new QueryColumn("customer_no");
}
